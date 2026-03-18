package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.context.DiagnosticsProvider
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class FixAllErrorsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val allIssues = ReadAction.compute<
            Map<String, List<com.claudecode.jetbrains.context.DiagnosticEntry>>,
            RuntimeException> {
            DiagnosticsProvider.getInstance(project).getProjectDiagnostics()
        }

        if (allIssues.isEmpty()) {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "No issues found in open files.",
                "Fix All Errors"
            )
            return
        }

        val totalIssues = allIssues.values.sumOf { it.size }

        val prompt = buildString {
            appendLine(
                "Fix all $totalIssues issue(s) across " +
                    "${allIssues.size} file(s):"
            )
            appendLine()

            for ((filePath, issues) in allIssues) {
                appendLine("### `$filePath`")
                for (diag in issues) {
                    appendLine(
                        "- [${diag.severity}] Line ${diag.line}: " +
                            diag.message
                    )
                }
                appendLine()
            }

            appendLine("Please fix all these issues.")
        }

        openClaudeAndSend(project, prompt)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
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
