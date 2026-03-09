package com.claudecode.jetbrains.ui.chat

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

class MessageBubble(private val message: ChatMessage) : JPanel(BorderLayout()) {

    private val bubbleBackground = backgroundForSender(message.sender)

    init {
        isOpaque = false
        border = JBUI.Borders.empty(4, 8, 4, 8)
        alignmentX = Component.LEFT_ALIGNMENT

        // Top row: sender label + timestamp
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false

            val senderLabel = JLabel(senderDisplayName(message.sender)).apply {
                font = font.deriveFont(Font.BOLD)
                foreground = senderColor(message.sender)
            }
            add(senderLabel)
            add(Box.createHorizontalStrut(JBUI.scale(6)))

            val timestampLabel = JLabel(
                message.timestamp.format(TIME_FORMAT)
            ).apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(font.size2D - 1f)
            }
            add(timestampLabel)
            add(Box.createHorizontalGlue())
        }
        add(headerPanel, BorderLayout.NORTH)

        // Message text -- trim leading/trailing whitespace
        val textArea = JTextArea(message.text.trim()).apply {
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
            isOpaque = false
            border = JBUI.Borders.empty(4, 0, 0, 0)
        }
        add(textArea, BorderLayout.CENTER)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = bubbleBackground
        val insets = insets
        val arc = JBUI.scale(12)
        g2.fillRoundRect(
            insets.left, insets.top,
            width - insets.left - insets.right,
            height - insets.top - insets.bottom,
            arc, arc
        )
        g2.dispose()
        super.paintComponent(g)
    }

    override fun getMaximumSize(): java.awt.Dimension {
        val pref = preferredSize
        return java.awt.Dimension(Int.MAX_VALUE, pref.height)
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        private val USER_BG = JBColor(Color(0xDB, 0xE9, 0xF7), Color(0x1E, 0x3A, 0x5F))
        private val ASSISTANT_BG = JBColor(Color(0xE8, 0xE8, 0xE8), Color(0x36, 0x39, 0x3D))
        private val ERROR_BG = JBColor(Color(0xFD, 0xE0, 0xE0), Color(0x50, 0x20, 0x20))

        private val USER_NAME_COLOR = JBColor(Color(0x1A, 0x56, 0xB0), Color(0x6B, 0xB8, 0xFF))
        private val ASSISTANT_NAME_COLOR = JBColor(Color(0xC4, 0x6E, 0x00), Color(0xE8, 0xA5, 0x50))
        private val ERROR_NAME_COLOR = JBColor(Color(0xC6, 0x28, 0x28), Color(0xFF, 0x6B, 0x6B))

        private val SYSTEM_BG = JBColor(Color(0xF5, 0xF5, 0xF5), Color(0x38, 0x3A, 0x3D))
        private val SYSTEM_NAME_COLOR = JBColor(Color(0x66, 0x66, 0x66), Color(0x80, 0x80, 0x80))

        private fun backgroundForSender(sender: MessageSender): JBColor = when (sender) {
            MessageSender.USER -> USER_BG
            MessageSender.ASSISTANT -> ASSISTANT_BG
            MessageSender.ERROR -> ERROR_BG
            MessageSender.SYSTEM -> SYSTEM_BG
        }

        private fun senderColor(sender: MessageSender): JBColor = when (sender) {
            MessageSender.USER -> USER_NAME_COLOR
            MessageSender.ASSISTANT -> ASSISTANT_NAME_COLOR
            MessageSender.ERROR -> ERROR_NAME_COLOR
            MessageSender.SYSTEM -> SYSTEM_NAME_COLOR
        }

        private fun senderDisplayName(sender: MessageSender): String = when (sender) {
            MessageSender.USER -> "You"
            MessageSender.ASSISTANT -> "Claude"
            MessageSender.ERROR -> "Error"
            MessageSender.SYSTEM -> "System"
        }
    }
}
