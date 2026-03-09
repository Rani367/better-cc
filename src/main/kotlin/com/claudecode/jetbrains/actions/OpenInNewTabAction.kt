package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.ui.sessions.SessionTabManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Opens a new Claude Code conversation as an editor tab.
 * Each tab is an independent session with its own CLI process.
 *
 * Accessible via:
 * - Command Palette: "Claude Code: Open in New Tab"
 * - Keyboard shortcut: Cmd+Shift+Esc (Mac) / Ctrl+Shift+Esc (Win/Linux)
 */
class OpenInNewTabAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SessionTabManager.getInstance(project).openNewTab()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
