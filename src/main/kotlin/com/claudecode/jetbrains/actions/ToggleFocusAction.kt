package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class ToggleFocusAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude Code") ?: return

        if (toolWindow.isActive) {
            toolWindow.hide()
        } else {
            toolWindow.activate {
                project.getUserData(ChatToolWindow.KEY)?.focusInput()
            }
        }
    }
}
