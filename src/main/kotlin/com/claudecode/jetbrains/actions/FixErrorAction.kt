package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.context.DiagnosticsProvider
import com.claudecode.jetbrains.context.FileContextProvider
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class FixErrorAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val fileContext = FileContextProvider.getInstance(project)
            .getFileContext(editor) ?: return

        // Get diagnostics — either at current line or all errors in file
        val diagnosticsProvider = DiagnosticsProvider.getInstance(project)
        val currentLine = editor.document.getLineNumber(
            editor.caretModel.offset
        ) + 1

        val diagnostics = diagnosticsProvider
            .getDiagnosticsAtLine(virtualFile, currentLine)
            .ifEmpty { diagnosticsProvider.getErrors(virtualFile) }

        val prompt = buildString {
            appendLine(
                "Fix the errors in `${fileContext.relativePath}`:"
            )
            appendLine()

            if (diagnostics.isNotEmpty()) {
                appendLine("**Diagnostics:**")
                for (diag in diagnostics) {
                    appendLine(
                        "- [${diag.severity}] Line ${diag.line}: " +
                            diag.message
                    )
                }
                appendLine()
            }

            // Include surrounding code context
            val selectedText = fileContext.selectedText
            if (selectedText != null) {
                appendLine("**Selected code:**")
                appendLine("```${fileContext.language.lowercase()}")
                appendLine(selectedText)
                appendLine("```")
            } else {
                // Include lines around the error
                val doc = editor.document
                val contextStart = maxOf(0, currentLine - 6)
                val contextEnd = minOf(
                    doc.lineCount - 1,
                    currentLine + 4
                )
                val startOffset = doc.getLineStartOffset(contextStart)
                val endOffset = doc.getLineEndOffset(contextEnd)
                val surroundingCode = doc.getText(
                    com.intellij.openapi.util.TextRange(
                        startOffset, endOffset
                    )
                )

                appendLine(
                    "**Code around line $currentLine:**"
                )
                appendLine("```${fileContext.language.lowercase()}")
                appendLine(surroundingCode)
                appendLine("```")
            }

            appendLine()
            appendLine("Please fix these errors.")
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
