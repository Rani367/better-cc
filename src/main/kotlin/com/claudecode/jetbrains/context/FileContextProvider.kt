package com.claudecode.jetbrains.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

data class FileContext(
    val relativePath: String,
    val absolutePath: String,
    val language: String,
    val selectedText: String? = null,
    val selectionStartLine: Int? = null,
    val selectionEndLine: Int? = null,
    val currentLine: Int? = null
)

@Service(Service.Level.PROJECT)
class FileContextProvider(private val project: Project) {

    private val logger = Logger.getInstance(FileContextProvider::class.java)

    /**
     * Get context for the currently active editor file.
     */
    fun getCurrentFileContext(): FileContext? {
        if (project.isDisposed) return null

        val editor = FileEditorManager.getInstance(project)
            .selectedTextEditor ?: return null
        val virtualFile = editor.virtualFile ?: return null

        return buildFileContext(editor, virtualFile)
    }

    /**
     * Get context from a specific editor instance.
     */
    fun getFileContext(editor: Editor): FileContext? {
        val virtualFile = editor.virtualFile ?: return null
        return buildFileContext(editor, virtualFile)
    }

    /**
     * Format file context for inclusion in a Claude prompt.
     */
    fun formatFileContext(context: FileContext): String {
        val sb = StringBuilder()
        sb.appendLine("File: `${context.relativePath}`")
        sb.appendLine("Language: ${context.language}")

        if (context.selectedText != null) {
            val lineRange = if (
                context.selectionStartLine != null &&
                context.selectionEndLine != null
            ) {
                " (lines ${context.selectionStartLine}-${context.selectionEndLine})"
            } else {
                ""
            }
            sb.appendLine("Selected code$lineRange:")
            sb.appendLine("```${context.language.lowercase()}")
            sb.appendLine(context.selectedText)
            sb.appendLine("```")
        } else if (context.currentLine != null) {
            sb.appendLine("Cursor at line ${context.currentLine}")
        }

        return sb.toString()
    }

    private fun buildFileContext(
        editor: Editor,
        virtualFile: VirtualFile
    ): FileContext {
        val relativePath = project.basePath?.let { base ->
            virtualFile.path.removePrefix(base).removePrefix("/")
        } ?: virtualFile.name

        val language = detectLanguage(virtualFile)

        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText
        val doc = editor.document

        val selectionStartLine = if (selectedText != null) {
            doc.getLineNumber(selectionModel.selectionStart) + 1
        } else {
            null
        }

        val selectionEndLine = if (selectedText != null) {
            doc.getLineNumber(selectionModel.selectionEnd) + 1
        } else {
            null
        }

        val currentLine = if (selectedText == null) {
            doc.getLineNumber(editor.caretModel.offset) + 1
        } else {
            null
        }

        return FileContext(
            relativePath = relativePath,
            absolutePath = virtualFile.path,
            language = language,
            selectedText = selectedText,
            selectionStartLine = selectionStartLine,
            selectionEndLine = selectionEndLine,
            currentLine = currentLine
        )
    }

    private fun detectLanguage(file: VirtualFile): String {
        return when (file.extension?.lowercase()) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "py" -> "Python"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "tsx" -> "TypeScript (React)"
            "jsx" -> "JavaScript (React)"
            "rs" -> "Rust"
            "go" -> "Go"
            "rb" -> "Ruby"
            "swift" -> "Swift"
            "c" -> "C"
            "cpp", "cc", "cxx" -> "C++"
            "h", "hpp" -> "C/C++ Header"
            "cs" -> "C#"
            "php" -> "PHP"
            "scala" -> "Scala"
            "groovy" -> "Groovy"
            "xml" -> "XML"
            "html", "htm" -> "HTML"
            "css" -> "CSS"
            "scss", "sass" -> "SCSS"
            "json" -> "JSON"
            "yaml", "yml" -> "YAML"
            "toml" -> "TOML"
            "md" -> "Markdown"
            "sql" -> "SQL"
            "sh", "bash", "zsh" -> "Shell"
            "gradle" -> "Gradle"
            else -> file.fileType.name
        }
    }

    companion object {
        fun getInstance(project: Project): FileContextProvider =
            project.getService(FileContextProvider::class.java)
    }
}
