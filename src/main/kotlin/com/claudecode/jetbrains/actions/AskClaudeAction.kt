package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.context.DiagnosticsProvider
import com.claudecode.jetbrains.context.FileContextProvider
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

class AskClaudeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val fileContext = FileContextProvider.getInstance(project)
            .getFileContext(editor) ?: return

        val selectedText = fileContext.selectedText ?: return

        val prompt = buildString {
            appendLine(
                "I have a question about this code in " +
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

            // Include diagnostics if any exist in the selected region
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
                }
            }

            appendLine()
            appendLine("What does this code do?")
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

    private fun openClaudeAndSend(
        project: com.intellij.openapi.project.Project,
        prompt: String
    ) {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Better Claude Code") ?: return
        toolWindow.activate {
            val chatWindow = project.getUserData(ChatToolWindow.KEY)
            chatWindow?.sendPrefilledMessage(prompt)
        }
    }
}
