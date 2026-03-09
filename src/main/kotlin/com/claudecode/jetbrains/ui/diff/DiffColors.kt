package com.claudecode.jetbrains.ui.diff

import com.claudecode.jetbrains.ui.theme.ClaudeColors

/**
 * Diff colour constants matching the VS Code Claude extension.
 */
object DiffColors {
    /** Addition foreground — green */
    val ADDITION get() = ClaudeColors.DIFF_ADDITION

    /** Deletion foreground — red/rose */
    val DELETION get() = ClaudeColors.DIFF_DELETION
}
