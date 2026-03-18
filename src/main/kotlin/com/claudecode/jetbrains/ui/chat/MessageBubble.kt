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
        val arc = com.claudecode.jetbrains.ui.theme.ClaudeCornerRadius.LARGE
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

        // Warm Ember palette
        private val USER_BG = JBColor(Color(0xF0, 0xE4, 0xDA), Color(0x30, 0x28, 0x24))
        private val ASSISTANT_BG = JBColor(Color(0xED, 0xE8, 0xE0), Color(0x2E, 0x2E, 0x32))
        private val ERROR_BG = JBColor(Color(0xF7, 0xDD, 0xDA), Color(0x40, 0x20, 0x22))

        private val USER_NAME_COLOR = JBColor(Color(0xD4, 0x62, 0x3A), Color(0xE8, 0x73, 0x4A))
        private val ASSISTANT_NAME_COLOR = JBColor(Color(0xC9, 0x8B, 0x1A), Color(0xF5, 0xA6, 0x23))
        private val ERROR_NAME_COLOR = JBColor(Color(0xC6, 0x28, 0x28), Color(0xE0, 0x6C, 0x75))

        private val SYSTEM_BG = JBColor(Color(0xFA, 0xF8, 0xF5), Color(0x2A, 0x2A, 0x2E))
        private val SYSTEM_NAME_COLOR = JBColor(Color(0x8A, 0x7E, 0x74), Color(0x9A, 0x94, 0x90))

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
