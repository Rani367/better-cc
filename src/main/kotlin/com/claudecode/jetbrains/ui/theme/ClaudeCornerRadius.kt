package com.claudecode.jetbrains.ui.theme

import com.intellij.util.ui.JBUI

/**
 * Corner radius constants — Warm Ember design system.
 * Very rounded: 8/12/16px for a modern, friendly feel.
 */
object ClaudeCornerRadius {
    val SMALL: Int get() = JBUI.scale(8)
    val MEDIUM: Int get() = JBUI.scale(12)
    val LARGE: Int get() = JBUI.scale(16)
    val PILL: Int get() = JBUI.scale(999)
}
