package com.claudecode.jetbrains.ui.layout

import com.claudecode.jetbrains.ui.common.GhostButton
import com.claudecode.jetbrains.ui.theme.ClaudeColors
import com.claudecode.jetbrains.ui.theme.ClaudeSpacing
import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * Header bar matching the VS Code extension's header_aqhumA:
 * height auto, 6px 10px padding, 1px bottom border.
 * Contains sessions button (left) + action buttons (right).
 */
class ClaudeHeaderBar(
    private val onSessionClick: () -> Unit,
    private val onNewChat: () -> Unit,
    private val onSettings: () -> Unit
) : JPanel(BorderLayout()) {

    private val sessionButton = GhostButton("New Conversation").apply {
        addActionListener { onSessionClick() }
    }

    private val newChatButton = GhostButton(icon = AllIcons.General.Add).apply {
        toolTipText = "New Chat"
        addActionListener { onNewChat() }
    }

    private val settingsButton = GhostButton(icon = AllIcons.General.Settings).apply {
        toolTipText = "Settings"
        addActionListener { onSettings() }
    }

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ClaudeColors.PRIMARY_BORDER),
            JBUI.Borders.empty(ClaudeSpacing.HEADER_PAD_V, ClaudeSpacing.HEADER_PAD_H)
        )

        add(sessionButton, BorderLayout.CENTER)

        val rightActions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply {
            isOpaque = false
            add(newChatButton)
            add(settingsButton)
        }
        add(rightActions, BorderLayout.EAST)
    }

    fun setSessionTitle(title: String) {
        sessionButton.text = title
    }
}
