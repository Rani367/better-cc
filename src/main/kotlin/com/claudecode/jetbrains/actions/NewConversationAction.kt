package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Starts a new Claude Code conversation in the current panel.
 *
 * Accessible via:
 * - Keyboard shortcut: Ctrl+N (when Claude panel is focused)
 */
class NewConversationAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Claude Code") ?: return
        val content = toolWindow.contentManager.selectedContent
            ?: return
        val chatWindow = content.component as? ChatToolWindow
        chatWindow?.startNewConversation()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
