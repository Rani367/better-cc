package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.context.FileContextProvider
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class ExplainCodeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val fileContext = FileContextProvider.getInstance(project)
            .getFileContext(editor) ?: return

        val selectedText = fileContext.selectedText ?: return

        val prompt = buildString {
            appendLine(
                "Explain the following code from " +
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
            appendLine()
            appendLine(
                "Please provide a clear, detailed explanation of " +
                    "what this code does, including its purpose, logic " +
                    "flow, and any important patterns or concepts used."
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
