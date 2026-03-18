package com.claudecode.jetbrains.ui.sessions

import com.claudecode.jetbrains.ui.common.AnimationManager
import com.claudecode.jetbrains.ui.theme.ClaudeColors
import com.claudecode.jetbrains.ui.theme.ClaudeCornerRadius
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton

/**
 * Session button — Warm Ember design system.
 * Ghost button with elastic press, truncated text, max-width 300px.
 */
class SessionButton(
    text: String,
    icon: Icon? = null
) : JButton(text, icon) {

    private var hovered = false

    init {
        font = font.deriveFont(Font.BOLD, 13f)
        iconTextGap = JBUI.scale(6)
        maximumSize = Dimension(JBUI.scale(300), Short.MAX_VALUE.toInt())
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = JBUI.Borders.empty(4)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                hovered = true
                repaint()
            }
            override fun mouseExited(e: MouseEvent?) {
                hovered = false
                repaint()
            }
        })

        AnimationManager.installElasticPress(this)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        val savedTransform = AnimationManager.applyAnimationTransform(g2, this)

        if (hovered) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = ClaudeColors.GHOST_BUTTON_HOVER
            val r = ClaudeCornerRadius.SMALL
            g2.fillRoundRect(0, 0, width, height, r, r)
        }
        super.paintComponent(g)

        if (savedTransform != null) {
            g2.transform = savedTransform
        }
    }
}
