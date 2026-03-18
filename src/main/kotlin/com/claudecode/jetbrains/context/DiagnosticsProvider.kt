package com.claudecode.jetbrains.context

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.ReadAction

data class DiagnosticEntry(
    val filePath: String,
    val line: Int,
    val column: Int,
    val severity: String,
    val message: String
)

@Service(Service.Level.PROJECT)
class DiagnosticsProvider(private val project: Project) {

    private val logger = Logger.getInstance(DiagnosticsProvider::class.java)

    /**
     * Collect diagnostics for a given file. Must be called from a read action
     * or will wrap itself in one.
     */
    fun getDiagnostics(virtualFile: VirtualFile): List<DiagnosticEntry> {
        if (project.isDisposed) return emptyList()

        return ReadAction.compute<List<DiagnosticEntry>, RuntimeException> {
            collectDiagnostics(virtualFile)
        }
    }

    /**
     * Collect diagnostics for the file open in the given editor.
     */
    fun getDiagnostics(editor: Editor): List<DiagnosticEntry> {
        val virtualFile = editor.virtualFile ?: return emptyList()
        return getDiagnostics(virtualFile)
    }

    /**
     * Get diagnostics at a specific line in a file.
     */
    fun getDiagnosticsAtLine(
        virtualFile: VirtualFile,
        line: Int
    ): List<DiagnosticEntry> {
        return getDiagnostics(virtualFile).filter { it.line == line }
    }

    /**
     * Format diagnostics as a human-readable context string for Claude.
     */
    fun formatDiagnostics(diagnostics: List<DiagnosticEntry>): String {
        if (diagnostics.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("## Diagnostics")
        for (diag in diagnostics) {
            sb.appendLine(
                "- [${diag.severity}] ${diag.filePath}:${diag.line}: ${diag.message}"
            )
        }
        return sb.toString()
    }

    /**
     * Get errors only (not warnings/info).
     */
    fun getErrors(virtualFile: VirtualFile): List<DiagnosticEntry> {
        return getDiagnostics(virtualFile).filter { it.severity == "ERROR" }
    }

    private fun collectDiagnostics(virtualFile: VirtualFile): List<DiagnosticEntry> {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return emptyList()

        val document = com.intellij.openapi.fileEditor.FileDocumentManager
            .getInstance().getDocument(virtualFile)
            ?: return emptyList()

        val filePath = project.basePath?.let { base ->
            virtualFile.path.removePrefix(base).removePrefix("/")
        } ?: virtualFile.path

        return try {
            val highlights = DaemonCodeAnalyzerImpl.getHighlights(
                document, null, project
            )
            highlights.mapNotNull { info ->
                mapHighlightInfo(info, document, filePath)
            }
        } catch (e: Exception) {
            logger.debug("Could not collect diagnostics for $filePath", e)
            emptyList()
        }
    }

    private fun mapHighlightInfo(
        info: HighlightInfo,
        document: com.intellij.openapi.editor.Document,
        filePath: String
    ): DiagnosticEntry? {
        val severity = when {
            info.severity == HighlightSeverity.ERROR -> "ERROR"
            info.severity == HighlightSeverity.WARNING -> "WARNING"
            info.severity.myVal >= HighlightSeverity.WEAK_WARNING.myVal ->
                "WEAK_WARNING"
            else -> return null // skip info-level
        }

        val message = info.description ?: return null
        val line = document.getLineNumber(info.startOffset) + 1
        val lineStart = document.getLineStartOffset(line - 1)
        val column = info.startOffset - lineStart + 1

        return DiagnosticEntry(
            filePath = filePath,
            line = line,
            column = column,
            severity = severity,
            message = message
        )
    }

    /**
     * Collect diagnostics across all open editor files in the project.
     */
    fun getProjectDiagnostics(): Map<String, List<DiagnosticEntry>> {
        if (project.isDisposed) return emptyMap()

        return ReadAction.compute<Map<String, List<DiagnosticEntry>>,
            RuntimeException> {
            val editorManager = com.intellij.openapi.fileEditor
                .FileEditorManager.getInstance(project)
            val result = mutableMapOf<String, List<DiagnosticEntry>>()
            for (file in editorManager.openFiles) {
                val diagnostics = collectDiagnostics(file)
                if (diagnostics.isNotEmpty()) {
                    val relativePath = project.basePath?.let { base ->
                        file.path.removePrefix(base).removePrefix("/")
                    } ?: file.path
                    result[relativePath] = diagnostics
                }
            }
            result
        }
    }

    /**
     * Get all errors across all open editor files.
     */
    fun getAllErrors(): Map<String, List<DiagnosticEntry>> {
        return getProjectDiagnostics().mapValues { (_, entries) ->
            entries.filter { it.severity == "ERROR" }
        }.filterValues { it.isNotEmpty() }
    }

    companion object {
        fun getInstance(project: Project): DiagnosticsProvider =
            project.getService(DiagnosticsProvider::class.java)
    }
}
