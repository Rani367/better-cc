package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.context.FileContextProvider
import com.claudecode.jetbrains.context.ProjectContextProvider
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class GenerateTestsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val fileContext = FileContextProvider.getInstance(project)
            .getFileContext(editor) ?: return

        val selectedText = fileContext.selectedText
        val projectContext = ProjectContextProvider.getInstance(project)
            .getProjectContext()

        val prompt = buildString {
            appendLine(
                "Generate unit tests for the following code in " +
                    "`${fileContext.relativePath}`:"
            )
            appendLine()

            if (selectedText != null) {
                appendLine("```${fileContext.language.lowercase()}")
                appendLine(selectedText)
                appendLine("```")
            } else {
                // No selection — use the whole file name as context
                appendLine(
                    "(Please generate tests for the functions/classes " +
                        "in this file.)"
                )
            }

            appendLine()

            // Add project context for framework detection
            val languages = projectContext.languages
            if (languages.isNotEmpty()) {
                val testFramework = suggestTestFramework(languages)
                if (testFramework != null) {
                    appendLine(
                        "Use the **$testFramework** testing framework."
                    )
                }
            }

            appendLine()
            appendLine(
                "Please generate comprehensive unit tests with " +
                    "appropriate assertions, edge cases, and descriptive " +
                    "test names."
            )
        }

        openClaudeAndSend(project, prompt)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null &&
            e.getData(CommonDataKeys.EDITOR) != null
    }

    private fun suggestTestFramework(languages: List<String>): String? {
        return when {
            languages.any {
                it.contains("Kotlin", ignoreCase = true)
            } -> "JUnit 5 / kotlin.test"
            languages.any {
                it.contains("Java", ignoreCase = true)
            } -> "JUnit 5"
            languages.any {
                it.contains("TypeScript", ignoreCase = true) ||
                    it.contains("JavaScript", ignoreCase = true)
            } -> "Jest"
            languages.any {
                it.contains("Python", ignoreCase = true)
            } -> "pytest"
            languages.any {
                it.contains("Rust", ignoreCase = true)
            } -> "Rust built-in (#[test])"
            languages.any {
                it.contains("Go", ignoreCase = true)
            } -> "Go testing package"
            languages.any {
                it.contains("Ruby", ignoreCase = true)
            } -> "RSpec"
            else -> null
        }
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
