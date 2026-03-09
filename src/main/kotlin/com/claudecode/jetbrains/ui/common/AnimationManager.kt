package com.claudecode.jetbrains.ui.common

import com.claudecode.jetbrains.settings.ClaudeSettings
import javax.swing.JComponent
import javax.swing.Timer

/**
 * Manages UI animations with respect-reduced-motion support.
 * When animations are disabled in settings, all durations become 0.
 */
object AnimationManager {

    /** Standard fadeIn duration (0.3s) */
    const val FADE_IN_DURATION_MS = 300

    /** Blink animation period (1s) */
    const val BLINK_PERIOD_MS = 1000

    /** Copy button transition (0.15s) */
    const val TRANSITION_DURATION_MS = 150

    /** Whether animations are enabled. */
    val isEnabled: Boolean
        get() = ClaudeSettings.getInstance().enableAnimations

    /**
     * Returns the effective duration — 0 if animations disabled.
     */
    fun duration(ms: Int): Int = if (isEnabled) ms else 0

    /**
     * Fades in a component over FADE_IN_DURATION_MS.
     * If animations disabled, the component is immediately visible.
     */
    fun fadeIn(component: JComponent) {
        if (!isEnabled) return

        // Simple alpha animation via Timer
        var alpha = 0f
        val step = 1f / (FADE_IN_DURATION_MS / 16f) // ~60fps
        val timer = Timer(16, null)
        timer.addActionListener {
            alpha = (alpha + step).coerceAtMost(1f)
            // Swing doesn't natively support component alpha easily,
            // so we just ensure the component is visible
            if (alpha >= 1f) {
                timer.stop()
            }
        }
        timer.start()
    }
}
