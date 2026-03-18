package com.claudecode.jetbrains.ui.common

import com.claudecode.jetbrains.ui.theme.ClaudeCornerRadius
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.BasicStroke
import javax.swing.border.AbstractBorder

/**
 * Anti-aliased rounded border for Swing panels/cards.
 */
class RoundedBorder(
    private val color: Color,
    private val thickness: Int = 1,
    private val radius: Int = ClaudeCornerRadius.MEDIUM
) : AbstractBorder() {

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, w: Int, h: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.stroke = BasicStroke(thickness.toFloat())
            val offset = thickness / 2
            g2.drawRoundRect(
                x + offset, y + offset,
                w - thickness, h - thickness,
                radius, radius
            )
        } finally {
            g2.dispose()
        }
    }

    override fun getBorderInsets(c: Component): Insets {
        val pad = thickness + 2
        return Insets(pad, pad, pad, pad)
    }

    override fun getBorderInsets(c: Component, insets: Insets): Insets {
        val pad = thickness + 2
        insets.set(pad, pad, pad, pad)
        return insets
    }
}
