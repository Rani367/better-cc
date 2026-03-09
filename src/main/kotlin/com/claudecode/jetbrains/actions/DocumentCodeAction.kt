package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.context.FileContextProvider
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class DocumentCodeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val fileContext = FileContextProvider.getInstance(project)
            .getFileContext(editor) ?: return

        val selectedText = fileContext.selectedText
        val language = fileContext.language

        val docFormat = when {
            language.contains("Kotlin", ignoreCase = true) -> "KDoc"
            language.contains("Java", ignoreCase = true) -> "Javadoc"
            language.contains("Python", ignoreCase = true) -> "docstrings"
            language.contains("TypeScript", ignoreCase = true) ||
                language.contains("JavaScript", ignoreCase = true) ->
                "JSDoc"
            language.contains("Rust", ignoreCase = true) -> "/// doc comments"
            language.contains("Go", ignoreCase = true) -> "Go doc comments"
            language.contains("C#", ignoreCase = true) -> "XML doc comments"
            language.contains("Swift", ignoreCase = true) ->
                "Swift documentation comments"
            language.contains("Ruby", ignoreCase = true) -> "YARD"
            else -> "documentation comments"
        }

        val prompt = buildString {
            appendLine(
                "Add $docFormat to the following code in " +
                    "`${fileContext.relativePath}`:"
            )
            appendLine()

            if (selectedText != null) {
                appendLine("```${language.lowercase()}")
                appendLine(selectedText)
                appendLine("```")
            } else {
                // No selection — document the function/class at cursor
                val doc = editor.document
                val currentLine = doc.getLineNumber(
                    editor.caretModel.offset
                ) + 1
                appendLine(
                    "(Please add documentation to the function or " +
                        "class at line $currentLine.)"
                )
            }

            appendLine()
            appendLine(
                "Please add comprehensive $docFormat including " +
                    "parameter descriptions, return values, and any " +
                    "thrown exceptions or important notes."
            )
        }

        openClaudeAndSend(project, prompt)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null &&
            e.getData(CommonDataKeys.EDITOR) != null
    }

    private fun openClaudeAndSend(project: Project, prompt: String) {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Claude Code") ?: return
        toolWindow.activate {
            val chatWindow = project.getUserData(ChatToolWindow.KEY)
            chatWindow?.sendPrefilledMessage(prompt)
        }
    }
}
