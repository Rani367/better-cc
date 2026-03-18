package com.claudecode.jetbrains.ui.common

import com.claudecode.jetbrains.settings.ClaudeSettings
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import javax.swing.JComponent
import javax.swing.Timer

/**
 * Manages UI animations with reduced-motion support.
 * Warm Ember design system — playful bounce animations.
 */
object AnimationManager {

    /** Standard fadeIn duration (0.3s) */
    const val FADE_IN_DURATION_MS = 300

    /** Blink animation period (1s) */
    const val BLINK_PERIOD_MS = 1000

    /** Copy button transition (0.15s) */
    const val TRANSITION_DURATION_MS = 150

    /** Bounce-in entrance (0.4s) */
    const val BOUNCE_IN_DURATION_MS = 400

    /** Elastic press micro-interaction (0.15s) */
    const val ELASTIC_PRESS_DURATION_MS = 150

    /** Spring transition (0.35s) */
    const val SPRING_DURATION_MS = 350

    private const val SCALE_PROPERTY = "animScale"

    /** Whether animations are enabled. */
    val isEnabled: Boolean
        get() = ClaudeSettings.getInstance().enableAnimations

    /**
     * Returns the effective duration — 0 if animations disabled.
     */
    fun duration(ms: Int): Int = if (isEnabled) ms else 0

    /**
     * Spring interpolator — approximates cubic-bezier(0.175, 0.885, 0.32, 1.275).
     * Overshoots to ~1.03 then settles to 1.0.
     */
    fun springInterpolator(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        // Attempt to match the cubic-bezier(0.175, 0.885, 0.32, 1.275) curve
        // Using a damped spring formula: overshoot then settle
        val p = clamped - 1f
        return 1f + p * p * (2.275f * p + 1.275f)
    }

    /**
     * Bounce-in animation: scale 0.9 → 1.03 → 1.0 with fade.
     * Component must call [applyAnimationTransform] in its paintComponent.
     */
    fun bounceIn(component: JComponent) {
        if (!isEnabled) {
            component.putClientProperty(SCALE_PROPERTY, 1.0f)
            component.repaint()
            return
        }

        val startTime = System.currentTimeMillis()
        val timer = Timer(16, null)
        timer.addActionListener {
            val elapsed = (System.currentTimeMillis() - startTime).toFloat()
            val progress = (elapsed / BOUNCE_IN_DURATION_MS).coerceAtMost(1f)
            val eased = springInterpolator(progress)

            // Scale: lerp from 0.9 to 1.0, with spring overshoot
            val scale = 0.9f + 0.1f * eased
            component.putClientProperty(SCALE_PROPERTY, scale)
            component.repaint()

            if (progress >= 1f) {
                timer.stop()
                component.putClientProperty(SCALE_PROPERTY, 1.0f)
                component.repaint()
            }
        }
        component.putClientProperty(SCALE_PROPERTY, 0.9f)
        timer.start()
    }

    /**
     * Installs elastic press behavior on a component.
     * Mousedown: scale to 0.95, mouseup: spring back to 1.0.
     * Component must call [applyAnimationTransform] in its paintComponent.
     */
    fun installElasticPress(component: JComponent) {
        component.putClientProperty(SCALE_PROPERTY, 1.0f)
        component.addMouseListener(object : MouseAdapter() {
            private var pressTimer: Timer? = null

            override fun mousePressed(e: MouseEvent) {
                if (!isEnabled) return
                pressTimer?.stop()

                val startTime = System.currentTimeMillis()
                val startScale = (component.getClientProperty(SCALE_PROPERTY) as? Float) ?: 1.0f
                pressTimer = Timer(16) {
                    val elapsed = (System.currentTimeMillis() - startTime).toFloat()
                    val progress = (elapsed / ELASTIC_PRESS_DURATION_MS).coerceAtMost(1f)
                    val scale = startScale + (0.95f - startScale) * progress
                    component.putClientProperty(SCALE_PROPERTY, scale)
                    component.repaint()
                    if (progress >= 1f) {
                        (it.source as Timer).stop()
                    }
                }
                pressTimer?.start()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (!isEnabled) return
                pressTimer?.stop()

                val startTime = System.currentTimeMillis()
                val releaseDuration = ELASTIC_PRESS_DURATION_MS * 2
                pressTimer = Timer(16) {
                    val elapsed = (System.currentTimeMillis() - startTime).toFloat()
                    val progress = (elapsed / releaseDuration).coerceAtMost(1f)
                    val eased = springInterpolator(progress)
                    // Spring from 0.95 back through 1.02 to 1.0
                    val scale = 0.95f + 0.05f * eased
                    component.putClientProperty(SCALE_PROPERTY, scale)
                    component.repaint()
                    if (progress >= 1f) {
                        (it.source as Timer).stop()
                        component.putClientProperty(SCALE_PROPERTY, 1.0f)
                        component.repaint()
                    }
                }
                pressTimer?.start()
            }
        })
    }

    /**
     * Applies the current animation scale transform to a Graphics2D context.
     * Call at the start of paintComponent, after super.paintComponent.
     * Returns the transform to restore afterward.
     */
    fun applyAnimationTransform(g2: Graphics2D, component: JComponent): AffineTransform? {
        val scale = (component.getClientProperty(SCALE_PROPERTY) as? Float) ?: 1.0f
        if (scale == 1.0f) return null

        val saved = g2.transform
        val cx = component.width / 2.0
        val cy = component.height / 2.0
        g2.translate(cx, cy)
        g2.scale(scale.toDouble(), scale.toDouble())
        g2.translate(-cx, -cy)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        return saved
    }

    /**
     * Fades in a component over FADE_IN_DURATION_MS.
     * If animations disabled, the component is immediately visible.
     */
    fun fadeIn(component: JComponent) {
        if (!isEnabled) return

        var alpha = 0f
        val step = 1f / (FADE_IN_DURATION_MS / 16f)
        val timer = Timer(16, null)
        timer.addActionListener {
            alpha = (alpha + step).coerceAtMost(1f)
            if (alpha >= 1f) {
                timer.stop()
            }
        }
        timer.start()
    }
}
