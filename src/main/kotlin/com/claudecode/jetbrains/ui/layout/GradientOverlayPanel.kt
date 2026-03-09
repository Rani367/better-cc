package com.claudecode.jetbrains.ui.layout

import com.claudecode.jetbrains.ui.theme.ClaudeColors
import com.claudecode.jetbrains.ui.theme.ClaudeSpacing
import java.awt.Color
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

/**
 * 150px tall gradient overlay pinned to the bottom of the messages area.
 * Fades from transparent to --app-primary-background.
 * pointer-events:none equivalent — mouse events pass through.
 *
 * Matches messageGradient_07S1Yg from the VS Code extension.
 */
class GradientOverlayPanel : JPanel() {

    init {
        isOpaque = false
        preferredSize = Dimension(0, ClaudeSpacing.GRADIENT_HEIGHT)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        val bg = ClaudeColors.PRIMARY_BACKGROUND
        val transparent = Color(bg.red, bg.green, bg.blue, 0)
        val opaque = Color(bg.red, bg.green, bg.blue, 255)

        val gradient = GradientPaint(
            0f, 0f, transparent,
            0f, height.toFloat(), opaque
        )
        g2.paint = gradient
        g2.fillRect(0, 0, width, height)
    }

    override fun contains(x: Int, y: Int): Boolean = false // pass-through
}
