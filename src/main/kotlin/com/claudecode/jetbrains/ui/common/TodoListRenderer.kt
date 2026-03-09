package com.claudecode.jetbrains.ui.common

import com.claudecode.jetbrains.ui.theme.ClaudeColors
import com.claudecode.jetbrains.ui.theme.ClaudeCornerRadius
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Todo/checklist rendering matching the VS Code extension's todoListContainer_xheXVQ.
 */
object TodoListRenderer {

    data class TodoItem(
        val text: String,
        val completed: Boolean = false,
        val indeterminate: Boolean = false
    )

    fun createTodoPanel(items: List<TodoItem>): JPanel {
        val panel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 0)
            isOpaque = false
        }

        for (item in items) {
            panel.add(createItemPanel(item))
            panel.add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)))
        }

        return panel
    }

    private fun createItemPanel(item: TodoItem): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
        }

        // Custom checkbox
        val checkbox = object : JPanel() {
            override fun getPreferredSize(): Dimension {
                val size = JBUI.scale(16)
                return Dimension(size, size)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                val r = ClaudeCornerRadius.SMALL / 2
                g2.color = ClaudeColors.INPUT_BACKGROUND
                g2.fillRoundRect(0, 0, width - 1, height - 1, r, r)
                g2.color = ClaudeColors.INPUT_BORDER
                g2.drawRoundRect(0, 0, width - 1, height - 1, r, r)

                if (item.completed) {
                    g2.color = ClaudeColors.PRIMARY_FOREGROUND
                    // Checkmark
                    g2.drawLine(width / 4, height / 2, width / 2 - 1, height * 3 / 4 - 1)
                    g2.drawLine(width / 2 - 1, height * 3 / 4 - 1, width * 3 / 4, height / 4)
                } else if (item.indeterminate) {
                    g2.color = ClaudeColors.PRIMARY_FOREGROUND
                    // Asterisk-like dash
                    g2.drawLine(width / 4, height / 2, width * 3 / 4, height / 2)
                }
            }
        }.apply {
            isOpaque = false
            if (item.completed) {
                // Reduced opacity for completed
            }
        }

        row.add(checkbox)

        // Text label
        val label = JLabel(item.text).apply {
            if (item.completed) {
                foreground = ClaudeColors.SECONDARY_FOREGROUND
                font = font.deriveFont(java.util.Collections.singletonMap(
                    java.awt.font.TextAttribute.STRIKETHROUGH,
                    java.awt.font.TextAttribute.STRIKETHROUGH_ON
                ))
            } else {
                foreground = ClaudeColors.PRIMARY_FOREGROUND
            }
        }
        row.add(label)

        return row
    }
}
