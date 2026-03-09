package com.claudecode.jetbrains.ui.onboarding

import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Onboarding checklist panel displayed to first-time users.
 * Shows a list of suggested actions with "Show me" buttons.
 * Can be dismissed and re-shown from settings.
 */
class OnboardingPanel(
    private val project: Project,
    private val onDismiss: () -> Unit
) : JPanel(BorderLayout()) {

    private val checklistItems = listOf(
        OnboardingItem(
            title = "Send your first message",
            description = "Type a message and press Enter to chat with Claude.",
            action = ::focusInput
        ),
        OnboardingItem(
            title = "Use @-mentions to reference files",
            description = "Type @ followed by a filename to include it as context.",
            action = ::showMentionHint
        ),
        OnboardingItem(
            title = "Try slash commands",
            description = "Type / to see available commands like /compact, /model, /clear.",
            action = ::showSlashHint
        ),
        OnboardingItem(
            title = "Review a code change",
            description = "When Claude edits files, you can review diffs before accepting.",
            action = null
        )
    )

    private companion object {
        private val HEADER_BG = JBColor(
            Color(0xE3, 0xF2, 0xFD),
            Color(0x1A, 0x3A, 0x52)
        )
        private val ITEM_HOVER_BG = JBColor(
            Color(0xF5, 0xF5, 0xF5),
            Color(0x35, 0x37, 0x39)
        )
    }

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            JBUI.Borders.empty(0)
        )

        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            background = HEADER_BG
            isOpaque = true
            border = JBUI.Borders.empty(10, 12, 10, 12)

            val titleLabel = JBLabel("Welcome to Claude Code").apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }
            add(titleLabel, BorderLayout.CENTER)

            val closeButton = JButton(AllIcons.Actions.Close).apply {
                isBorderPainted = false
                isContentAreaFilled = false
                toolTipText = "Dismiss onboarding"
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener { dismiss() }
            }
            add(closeButton, BorderLayout.EAST)
        }
        add(headerPanel, BorderLayout.NORTH)

        // Checklist
        val listPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4, 0)
        }

        for (item in checklistItems) {
            listPanel.add(createItemRow(item))
        }

        add(listPanel, BorderLayout.CENTER)
    }

    private fun createItemRow(item: OnboardingItem): JPanel {
        val row = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.empty(8, 12, 8, 12)
            isOpaque = false

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    background = ITEM_HOVER_BG
                    isOpaque = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent?) {
                    isOpaque = false
                    repaint()
                }
            })
        }

        // Left: bullet + text
        val textPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val titleLabel = JBLabel(item.title).apply {
            font = font.deriveFont(Font.BOLD)
        }
        textPanel.add(titleLabel)

        val descLabel = JBLabel(item.description).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }
        textPanel.add(descLabel)

        row.add(textPanel, BorderLayout.CENTER)

        // Right: "Show me" button (if action exists)
        if (item.action != null) {
            val showMePanel = JPanel(
                FlowLayout(FlowLayout.RIGHT, 0, 0)
            ).apply {
                isOpaque = false
            }
            val showMeButton = JButton("Show me").apply {
                putClientProperty("JButton.buttonType", "borderless")
                foreground = JBColor.namedColor(
                    "Link.activeForeground",
                    JBColor(Color(0x1A, 0x73, 0xE8), Color(0x6B, 0xB8, 0xFF))
                )
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener { item.action.invoke() }
            }
            showMePanel.add(showMeButton)
            row.add(showMePanel, BorderLayout.EAST)
        }

        return row
    }

    private fun dismiss() {
        ClaudeSettings.getInstance().hideOnboarding = true
        onDismiss()
    }

    private fun focusInput() {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Claude Code") ?: return
        toolWindow.activate {
            project.getUserData(ChatToolWindow.KEY)?.focusInput()
        }
    }

    private fun showMentionHint() {
        val chat = project.getUserData(ChatToolWindow.KEY)
        if (chat != null) {
            chat.prefillInput("@")
            chat.focusInput()
        }
    }

    private fun showSlashHint() {
        val chat = project.getUserData(ChatToolWindow.KEY)
        if (chat != null) {
            chat.prefillInput("/")
            chat.focusInput()
        }
    }

    private data class OnboardingItem(
        val title: String,
        val description: String,
        val action: (() -> Unit)?
    )
}
