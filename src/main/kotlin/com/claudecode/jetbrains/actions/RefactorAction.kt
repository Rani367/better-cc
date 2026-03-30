package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.context.DiagnosticsProvider
import com.claudecode.jetbrains.context.FileContextProvider
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class RefactorAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val fileContext = FileContextProvider.getInstance(project)
            .getFileContext(editor) ?: return

        val selectedText = fileContext.selectedText ?: return

        val prompt = buildString {
            appendLine(
                "Refactor the following code in " +
                    "`${fileContext.relativePath}`"
            )
            if (
                fileContext.selectionStartLine != null &&
                fileContext.selectionEndLine != null
            ) {
                appendLine(
                    "(lines ${fileContext.selectionStartLine}" +
                        "-${fileContext.selectionEndLine}):"
                )
            }
            appendLine()
            appendLine("```${fileContext.language.lowercase()}")
            appendLine(selectedText)
            appendLine("```")

            // Include diagnostics so Claude can fix issues while refactoring
            val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            if (virtualFile != null) {
                val diagnostics = DiagnosticsProvider
                    .getInstance(project)
                    .getDiagnostics(virtualFile)
                    .filter {
                        fileContext.selectionStartLine != null &&
                            fileContext.selectionEndLine != null &&
                            it.line >= fileContext.selectionStartLine &&
                            it.line <= fileContext.selectionEndLine
                    }
                if (diagnostics.isNotEmpty()) {
                    appendLine()
                    appendLine("**IDE diagnostics in this region:**")
                    for (diag in diagnostics) {
                        appendLine(
                            "- [${diag.severity}] Line ${diag.line}" +
                                ": ${diag.message}"
                        )
                    }
                    appendLine()
                    appendLine(
                        "Please also fix these issues as part of " +
                            "the refactoring."
                    )
                }
            }

            appendLine()
            appendLine(
                "Please refactor this code to improve readability, " +
                    "maintainability, and adherence to best practices."
            )
        }

        openClaudeAndSend(project, prompt)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible =
            e.project != null &&
            editor != null &&
            editor.selectionModel.hasSelection()
    }

    private fun openClaudeAndSend(project: Project, prompt: String) {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Better Claude Code") ?: return
        toolWindow.activate {
            val chatWindow = project.getUserData(ChatToolWindow.KEY)
            chatWindow?.sendPrefilledMessage(prompt)
        }
    }
}
