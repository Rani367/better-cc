package com.claudecode.jetbrains.ui.theme

import com.intellij.util.ui.JBUI

/**
 * Spacing scale constants from the VS Code Claude extension CSS.
 */
object ClaudeSpacing {
    val SMALL: Int get() = JBUI.scale(4)
    val MEDIUM: Int get() = JBUI.scale(8)
    val LARGE: Int get() = JBUI.scale(12)
    val XLARGE: Int get() = JBUI.scale(16)

    /** Messages container padding: 20px sides/top, 40px bottom */
    val MESSAGES_PAD_X: Int get() = JBUI.scale(20)
    val MESSAGES_PAD_TOP: Int get() = JBUI.scale(20)
    val MESSAGES_PAD_BOTTOM: Int get() = JBUI.scale(40)

    /** Input container margins */
    val INPUT_MARGIN: Int get() = JBUI.scale(16)

    /** Timeline left padding for assistant messages */
    val TIMELINE_INDENT: Int get() = JBUI.scale(30)

    /** Timeline dot position */
    val TIMELINE_DOT_LEFT: Int get() = JBUI.scale(9)
    val TIMELINE_DOT_TOP: Int get() = JBUI.scale(15)
    val TIMELINE_DOT_SIZE: Int get() = JBUI.scale(7)

    /** Timeline vertical line position */
    val TIMELINE_LINE_LEFT: Int get() = JBUI.scale(12)
    val TIMELINE_LINE_START: Int get() = JBUI.scale(18)

    /** Gradient overlay height */
    val GRADIENT_HEIGHT: Int get() = JBUI.scale(150)

    /** Header padding */
    val HEADER_PAD_V: Int get() = JBUI.scale(6)
    val HEADER_PAD_H: Int get() = JBUI.scale(10)
}
