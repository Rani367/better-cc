package com.claudecode.jetbrains.ui.theme

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.util.ui.JBUI
import java.awt.Font

/**
 * Typography utilities matching the VS Code Claude extension.
 */
object ClaudeTypography {

    /** Chat font size (13px, matching --vscode-chat-font-size) */
    val CHAT_FONT: Font get() = JBUI.Fonts.label(13f)

    /** Editor monospace font (from user's IntelliJ editor settings) */
    val MONOSPACE_FONT: Font
        get() = EditorColorsManager.getInstance()
            .globalScheme.getFont(EditorFontType.PLAIN)

    /** Editor monospace font size */
    val MONOSPACE_FONT_SIZE: Int
        get() = EditorColorsManager.getInstance()
            .globalScheme.editorFontSize

    /** Smaller monospace font size (editor size - 2) */
    val MONOSPACE_FONT_SIZE_SMALL: Int
        get() = (MONOSPACE_FONT_SIZE - 2).coerceAtLeast(9)

    /** Monospace font family name for CSS */
    val MONOSPACE_FAMILY: String
        get() = MONOSPACE_FONT.family
}
