package com.claudecode.jetbrains.ui.common

import com.claudecode.jetbrains.ui.theme.ClaudeColors
import com.claudecode.jetbrains.ui.theme.ClaudeCornerRadius
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Error banner — Warm Ember design system.
 * Blended background with rounded corners.
 */
class ErrorBanner(
    private val message: String,
    private val onDismiss: () -> Unit
) : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        border = JBUI.Borders.empty(4)

        val messageLabel = JLabel(
            "<html><div style='padding:10px 12px;'>${escapeHtml(message)}</div></html>"
        ).apply {
            foreground = ClaudeColors.ERROR_FOREGROUND
        }
        add(messageLabel, BorderLayout.CENTER)

        val dismissButton = JButton("\u00D7").apply {
            preferredSize = Dimension(JBUI.scale(44), JBUI.scale(44))
            foreground = ClaudeColors.ERROR_FOREGROUND
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(20f)
            addActionListener { onDismiss() }
        }
        add(dismissButton, BorderLayout.EAST)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // 96% primary background + 4% error foreground
        val bg = ClaudeColors.PRIMARY_BACKGROUND
        val err = ClaudeColors.ERROR_FOREGROUND
        val blended = Color(
            (bg.red * 0.96 + err.red * 0.04).toInt().coerceIn(0, 255),
            (bg.green * 0.96 + err.green * 0.04).toInt().coerceIn(0, 255),
            (bg.blue * 0.96 + err.blue * 0.04).toInt().coerceIn(0, 255)
        )
        g2.color = blended
        val r = ClaudeCornerRadius.MEDIUM
        g2.fillRoundRect(0, 0, width, height, r, r)

        // Bottom border line
        g2.color = ClaudeColors.ERROR_FOREGROUND
        g2.drawLine(0, height - 1, width, height - 1)
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
