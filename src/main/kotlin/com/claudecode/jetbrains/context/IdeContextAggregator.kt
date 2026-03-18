package com.claudecode.jetbrains.context

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Aggregates IDE context (diagnostics, file info, git state) into
 * a structured block that gets appended to user messages before
 * sending to the CLI. This gives Claude awareness of IDE state
 * that the standalone CLI cannot access.
 */
@Service(Service.Level.PROJECT)
class IdeContextAggregator(private val project: Project) {

    /**
     * Gather all available IDE context. Must be called off-EDT.
     * Returns null if no meaningful context is available.
     */
    fun gatherContext(): String? {
        if (project.isDisposed) return null

        val sections = mutableListOf<String>()

        // Active file + cursor
        val fileSection = gatherFileContext()
        if (fileSection != null) sections.add(fileSection)

        // Diagnostics for current file
        val diagSection = gatherDiagnosticsContext()
        if (diagSection != null) sections.add(diagSection)

        // Git status
        val gitSection = gatherGitContext()
        if (gitSection != null) sections.add(gitSection)

        if (sections.isEmpty()) return null

        return buildString {
            appendLine()
            appendLine("---")
            appendLine("[IDE Context]")
            for (section in sections) {
                appendLine(section)
            }
        }.trimEnd()
    }

    private fun gatherFileContext(): String? {
        val fileContext = ReadAction.compute<FileContext?, RuntimeException> {
            FileContextProvider.getInstance(project)
                .getCurrentFileContext()
        } ?: return null

        return buildString {
            append("Active file: `${fileContext.relativePath}`")
            append(" (${fileContext.language})")
            if (fileContext.currentLine != null) {
                append(", cursor at line ${fileContext.currentLine}")
            }
            if (fileContext.selectionStartLine != null &&
                fileContext.selectionEndLine != null
            ) {
                append(
                    ", selection lines " +
                        "${fileContext.selectionStartLine}" +
                        "-${fileContext.selectionEndLine}"
                )
            }
        }
    }

    private fun gatherDiagnosticsContext(): String? {
        val diagnostics = ReadAction.compute<List<DiagnosticEntry>,
            RuntimeException> {
            val editor = FileEditorManager.getInstance(project)
                .selectedTextEditor ?: return@compute emptyList()
            val file = editor.virtualFile
                ?: return@compute emptyList()
            DiagnosticsProvider.getInstance(project)
                .getDiagnostics(file)
        }

        if (diagnostics.isEmpty()) return null

        val errors = diagnostics.count { it.severity == "ERROR" }
        val warnings = diagnostics.count { it.severity == "WARNING" }

        return buildString {
            appendLine(
                "Diagnostics: $errors error(s), $warnings warning(s)"
            )
            for (diag in diagnostics.take(15)) {
                appendLine(
                    "  [${diag.severity}] ${diag.filePath}:" +
                        "${diag.line}: ${diag.message}"
                )
            }
            if (diagnostics.size > 15) {
                appendLine(
                    "  ... and ${diagnostics.size - 15} more"
                )
            }
        }.trimEnd()
    }

    private fun gatherGitContext(): String? {
        return try {
            GitContextProvider.getInstance(project).getGitContext()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun getInstance(project: Project): IdeContextAggregator =
            project.getService(IdeContextAggregator::class.java)
    }
}
