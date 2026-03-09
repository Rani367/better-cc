package com.claudecode.jetbrains.ui.theme

import com.intellij.util.ui.JBUI

/**
 * Corner radius constants from the VS Code Claude extension CSS.
 */
object ClaudeCornerRadius {
    val SMALL: Int get() = JBUI.scale(4)
    val MEDIUM: Int get() = JBUI.scale(6)
    val LARGE: Int get() = JBUI.scale(8)
}
