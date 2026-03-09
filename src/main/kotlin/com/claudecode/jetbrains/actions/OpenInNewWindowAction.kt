package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.WindowConstants

/**
 * Opens Claude Code in a separate, standalone IDE window.
 * The window hosts a full [ChatToolWindow] with its own session.
 *
 * Accessible via Command Palette:
 *   "Claude Code: Open in New Window"
 */
class OpenInNewWindowAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        openClaudeWindow(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    companion object {
        private fun openClaudeWindow(project: Project) {
            val chatPanel = ChatToolWindow(project)

            val frame = JFrame("Claude Code").apply {
                defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                contentPane.layout = BorderLayout()
                contentPane.add(chatPanel.getContent(), BorderLayout.CENTER)
                preferredSize = Dimension(600, 800)
                minimumSize = Dimension(400, 500)
                pack()
            }

            // Position near the IDE window
            val ideFrame = WindowManager.getInstance()
                .getFrame(project)
            if (ideFrame != null) {
                frame.setLocationRelativeTo(ideFrame)
            } else {
                frame.setLocationRelativeTo(null)
            }

            // Dispose ChatToolWindow when the frame closes
            frame.addWindowListener(object :
                java.awt.event.WindowAdapter() {
                override fun windowClosed(
                    e: java.awt.event.WindowEvent?
                ) {
                    Disposer.dispose(chatPanel)
                }
            })

            frame.isVisible = true
            chatPanel.focusInput()
        }
    }
}
