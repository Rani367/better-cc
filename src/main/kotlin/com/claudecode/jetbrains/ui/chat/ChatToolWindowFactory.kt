package com.claudecode.jetbrains.ui.chat

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ChatToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatToolWindow = ChatToolWindow(project)
        chatToolWindow.setLocation("sidebar")
        Disposer.register(toolWindow.disposable, chatToolWindow)

        val content = ContentFactory.getInstance()
            .createContent(chatToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}
