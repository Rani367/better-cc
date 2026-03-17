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
 * Onboarding walkthrough panel matching VS Code extension steps.
 * Tracks completed milestones via [ClaudeSettings.completedMilestones].
 * Each milestone shows a checkmark when completed.
 */
class OnboardingPanel(
    private val project: Project,
    private val onDismiss: () -> Unit
) : JPanel(BorderLayout()) {

    /** Milestone IDs must be stable strings for persistence. */
    private val milestones = listOf(
        Milestone(
            id = "welcome",
            title = "Your AI coding partner",
            description = "Claude Code is your AI pair programmer " +
                "right inside JetBrains.",
            action = null
        ),
        Milestone(
            id = "open_claude",
            title = "Open Claude Code",
            description = "Open the Claude Code tool window " +
                "to start a conversation.",
            action = ::openClaudeToolWindow
        ),
        Milestone(
            id = "chat_with_claude",
            title = "Chat with Claude",
            description = "Type a message and press Enter " +
                "to get help with your code.",
            action = ::focusInput
        ),
        Milestone(
            id = "past_conversations",
            title = "Past conversations",
            description = "Access your conversation history " +
                "from the sessions dropdown.",
            action = ::showSessionsHint
        )
    )

    private val listPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4, 0)
    }

    private companion object {
        private val HEADER_BG = JBColor(
            Color(0xE3, 0xF2, 0xFD),
            Color(0x1A, 0x3A, 0x52)
        )
        private val ITEM_HOVER_BG = JBColor(
            Color(0xF5, 0xF5, 0xF5),
            Color(0x35, 0x37, 0x39)
        )
        private val CHECK_COLOR = JBColor(
            Color(0x4C, 0xAF, 0x50),
            Color(0x66, 0xBB, 0x6A)
        )
        private val PENDING_COLOR = JBColor.GRAY
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
                cursor = Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )
                addActionListener { dismiss() }
            }
            add(closeButton, BorderLayout.EAST)
        }
        add(headerPanel, BorderLayout.NORTH)

        // Progress summary
        val completed = ClaudeSettings.getInstance().completedMilestones
        val completedCount = milestones.count {
            completed.contains(it.id)
        }
        val progressText = "$completedCount / ${milestones.size} completed"
        val progressPanel = JPanel(
            FlowLayout(FlowLayout.LEFT, 12, 0)
        ).apply {
            border = JBUI.Borders.empty(6, 12, 2, 12)
            isOpaque = false
        }
        progressPanel.add(JBLabel(progressText).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        })
        add(progressPanel, BorderLayout.SOUTH)

        // Milestone list
        renderMilestones()
        add(listPanel, BorderLayout.CENTER)

        // Auto-mark "welcome" as completed on view
        markCompleted("welcome")
    }

    private fun renderMilestones() {
        listPanel.removeAll()
        val completed = ClaudeSettings.getInstance().completedMilestones
        // Find first incomplete milestone
        val currentId = milestones.firstOrNull {
            !completed.contains(it.id)
        }?.id

        for (milestone in milestones) {
            val isCompleted = completed.contains(milestone.id)
            val isCurrent = milestone.id == currentId
            listPanel.add(
                createMilestoneRow(milestone, isCompleted, isCurrent)
            )
        }
        listPanel.revalidate()
        listPanel.repaint()
    }

    private fun createMilestoneRow(
        milestone: Milestone,
        isCompleted: Boolean,
        isCurrent: Boolean
    ): JPanel {
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

        // Left: checkmark or circle indicator
        val checkLabel = if (isCompleted) {
            JBLabel("\u2713").apply { // checkmark
                foreground = CHECK_COLOR
                font = font.deriveFont(Font.BOLD, 14f)
            }
        } else {
            JBLabel("\u25CB").apply { // empty circle
                foreground = PENDING_COLOR
                font = font.deriveFont(14f)
            }
        }
        val checkPanel = JPanel(
            FlowLayout(FlowLayout.CENTER, 0, 0)
        ).apply {
            isOpaque = false
            preferredSize = java.awt.Dimension(24, 24)
            add(checkLabel)
        }
        row.add(checkPanel, BorderLayout.WEST)

        // Center: text
        val textPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val titleLabel = JBLabel(milestone.title).apply {
            font = if (isCurrent) {
                font.deriveFont(Font.BOLD)
            } else {
                font.deriveFont(Font.PLAIN)
            }
            if (isCompleted && !isCurrent) {
                foreground = JBColor.GRAY
            }
        }
        textPanel.add(titleLabel)

        val descLabel = JBLabel(milestone.description).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }
        textPanel.add(descLabel)

        row.add(textPanel, BorderLayout.CENTER)

        // Right: "Show me" button (if action exists and not completed)
        if (milestone.action != null && !isCompleted) {
            val showMePanel = JPanel(
                FlowLayout(FlowLayout.RIGHT, 0, 0)
            ).apply {
                isOpaque = false
            }
            val showMeButton = JButton("Show me").apply {
                putClientProperty("JButton.buttonType", "borderless")
                foreground = JBColor.namedColor(
                    "Link.activeForeground",
                    JBColor(
                        Color(0x1A, 0x73, 0xE8),
                        Color(0x6B, 0xB8, 0xFF)
                    )
                )
                cursor = Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )
                addActionListener {
                    markCompleted(milestone.id)
                    milestone.action.invoke()
                }
            }
            showMePanel.add(showMeButton)
            row.add(showMePanel, BorderLayout.EAST)
        }

        return row
    }

    /**
     * Marks a milestone as completed and refreshes the display.
     */
    fun markCompleted(milestoneId: String) {
        val settings = ClaudeSettings.getInstance()
        if (settings.completedMilestones.add(milestoneId)) {
            renderMilestones()
        }
    }

    private fun dismiss() {
        ClaudeSettings.getInstance().hideOnboarding = true
        onDismiss()
    }

    private fun openClaudeToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Claude Code") ?: return
        toolWindow.activate {
            markCompleted("open_claude")
        }
    }

    private fun focusInput() {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Claude Code") ?: return
        toolWindow.activate {
            project.getUserData(ChatToolWindow.KEY)?.focusInput()
            markCompleted("chat_with_claude")
        }
    }

    private fun showSessionsHint() {
        val chat = project.getUserData(ChatToolWindow.KEY)
        if (chat != null) {
            // The sessions dropdown is triggered from the header in JCEF
            // We just focus the tool window so the user can click it
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Claude Code") ?: return
            toolWindow.activate {
                markCompleted("past_conversations")
            }
        }
    }

    private data class Milestone(
        val id: String,
        val title: String,
        val description: String,
        val action: (() -> Unit)?
    )
}
