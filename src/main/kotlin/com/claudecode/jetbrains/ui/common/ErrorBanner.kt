package com.claudecode.jetbrains.ui.common

import com.claudecode.jetbrains.ui.theme.ClaudeColors
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Error banner matching the VS Code extension's errorBanner_07S1Yg:
 * 96%/4% mix of primary-bg and error colour, 1px bottom border,
 * dismiss button at 44x44px.
 */
class ErrorBanner(
    private val message: String,
    private val onDismiss: () -> Unit
) : JPanel(BorderLayout()) {

    init {
        border = JBUI.Borders.customLine(ClaudeColors.ERROR_FOREGROUND, 0, 0, 1, 0)

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
        // 96% primary background + 4% error foreground
        val bg = ClaudeColors.PRIMARY_BACKGROUND
        val err = ClaudeColors.ERROR_FOREGROUND
        val blended = Color(
            (bg.red * 0.96 + err.red * 0.04).toInt().coerceIn(0, 255),
            (bg.green * 0.96 + err.green * 0.04).toInt().coerceIn(0, 255),
            (bg.blue * 0.96 + err.blue * 0.04).toInt().coerceIn(0, 255)
        )
        g.color = blended
        g.fillRect(0, 0, width, height)
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
