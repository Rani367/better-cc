package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.services.ClaudeStateListener
import com.claudecode.jetbrains.services.ClaudeStateService
import com.claudecode.jetbrains.services.SessionUsage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * Small bar showing context window usage as a progress bar with
 * percentage text. Colors change based on usage level:
 * green < 50%, yellow 50-80%, red > 80%.
 */
class ContextIndicator(
    private val project: Project,
    parentDisposable: Disposable
) : JPanel(BorderLayout()), ClaudeStateListener {

    private val stateService = ClaudeStateService.getInstance(project)

    private val progressBar = ContextProgressBar()
    private val label = JBLabel("0% of context used").apply {
        foreground = JBColor.GRAY
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(2, 8, 2, 8)

        val barWrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(progressBar, BorderLayout.CENTER)
            border = JBUI.Borders.emptyRight(8)
        }

        add(barWrapper, BorderLayout.CENTER)
        add(label, BorderLayout.EAST)

        stateService.addListener(this)
        updateDisplay(stateService.usage.contextPercent)
    }

    override fun onUsageUpdated(usage: SessionUsage) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                updateDisplay(usage.contextPercent)
            }
        }
    }

    private fun updateDisplay(percent: Int) {
        progressBar.percent = percent
        label.text = "$percent% of context used"
        label.foreground = colorForPercent(percent)
        progressBar.repaint()
    }

    fun dispose() {
        stateService.removeListener(this)
    }

    companion object {
        private val COLOR_GREEN = JBColor(
            Color(76, 175, 80),
            Color(76, 175, 80)
        )
        private val COLOR_YELLOW = JBColor(
            Color(255, 193, 7),
            Color(255, 193, 7)
        )
        private val COLOR_RED = JBColor(
            Color(244, 67, 54),
            Color(244, 67, 54)
        )

        fun colorForPercent(percent: Int): JBColor {
            return when {
                percent < 50 -> COLOR_GREEN
                percent < 80 -> COLOR_YELLOW
                else -> COLOR_RED
            }
        }
    }

    /**
     * A simple custom progress bar that draws a filled rectangle
     * proportional to the context percentage.
     */
    private class ContextProgressBar : JPanel() {
        var percent: Int = 0

        init {
            preferredSize = Dimension(JBUI.scale(100), JBUI.scale(6))
            minimumSize = Dimension(JBUI.scale(40), JBUI.scale(6))
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )

            val w = width
            val h = height
            val arc = h

            // Background track
            g2.color = JBColor(Color(220, 220, 220), Color(60, 63, 65))
            g2.fillRoundRect(0, 0, w, h, arc, arc)

            // Filled portion
            if (percent > 0) {
                val fillWidth = ((percent.toDouble() / 100.0) * w).toInt()
                    .coerceIn(arc, w)
                g2.color = colorForPercent(percent)
                g2.fillRoundRect(0, 0, fillWidth, h, arc, arc)
            }
        }
    }
}
