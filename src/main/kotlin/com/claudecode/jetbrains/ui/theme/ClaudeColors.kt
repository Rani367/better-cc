package com.claudecode.jetbrains.ui.theme

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Colour constants from the VS Code Claude extension's design system.
 * Maps CSS custom properties to JBColor for light/dark theme support.
 */
object ClaudeColors {

    // ── Brand ────────────────────────────────────────────────────
    val CLAUDE_ORANGE = JBColor(0xd97757, 0xd97757)
    val CLAUDE_CLAY_BUTTON = JBColor(0xc6613f, 0xd97757)
    val CLAUDE_IVORY = Color(0xfa, 0xf9, 0xf5)
    val CLAUDE_SLATE = Color(0x14, 0x14, 0x13)

    // ── Foreground ───────────────────────────────────────────────
    val PRIMARY_FOREGROUND = JBColor.namedColor("Label.foreground", JBColor(0x1a1a1a, 0xbcbcbc))
    val SECONDARY_FOREGROUND = JBColor.namedColor(
        "Label.disabledForeground",
        JBColor(0x666666, 0x808080)
    )
    val ERROR_FOREGROUND = JBColor.namedColor("Label.errorForeground", JBColor(0xc62828, 0xff6b6b))

    // ── Background ───────────────────────────────────────────────
    val PRIMARY_BACKGROUND = JBColor.namedColor("SidePanel.background", JBColor(0xf5f5f5, 0x2b2d30))
    val SECONDARY_BACKGROUND = JBColor.namedColor("Editor.background", JBColor(0xffffff, 0x1e1f22))
    val INPUT_BACKGROUND = JBColor.namedColor("TextField.background", JBColor(0xffffff, 0x2b2d30))
    val TOOL_BACKGROUND = JBColor.namedColor("Editor.background", JBColor(0xffffff, 0x1e1f22))

    // ── Borders ──────────────────────────────────────────────────
    val INPUT_BORDER = JBColor.namedColor("Component.borderColor", JBColor(0xc4c4c4, 0x464648))
    val INPUT_ACTIVE_BORDER = JBColor.namedColor(
        "Component.focusedBorderColor",
        JBColor(0x2675bf, 0x4e82c5)
    )
    val PRIMARY_BORDER = JBColor.namedColor("Borders.color", JBColor(0xe0e0e0, 0x464648))
    val TRANSPARENT_INNER_BORDER_LIGHT = Color(0x00, 0x00, 0x00, 0x12)
    val TRANSPARENT_INNER_BORDER_DARK = Color(0xff, 0xff, 0xff, 0x1a)

    // ── Buttons ──────────────────────────────────────────────────
    val BUTTON_BACKGROUND = JBColor.namedColor(
        "Button.default.startBackground",
        JBColor(0x528bff, 0x365880)
    )
    val BUTTON_FOREGROUND = JBColor.namedColor(
        "Button.default.foreground",
        JBColor(0xffffff, 0xbbbbbb)
    )
    val GHOST_BUTTON_HOVER = JBColor.namedColor(
        "ActionButton.hoverBackground",
        JBColor(0xdfdfdf, 0x4c5052)
    )

    // ── Status ───────────────────────────────────────────────────
    val STATUS_BUSY = JBColor(0x22c55e, 0x22c55e)
    val STATUS_PENDING = JBColor(0x3b82f6, 0x3b82f6)
    val WARNING_ACCENT = JBColor(0xe5a54b, 0xe5a54b)
    val SUCCESS_FOREGROUND = JBColor.namedColor("FileColor.Green", JBColor(0x4caf50, 0x74c991))

    // ── Spinner ──────────────────────────────────────────────────
    val SPINNER = CLAUDE_ORANGE

    // ── Timeline dots ────────────────────────────────────────────
    val DOT_DEFAULT = SECONDARY_FOREGROUND
    val DOT_SUCCESS = JBColor(0x74c991, 0x74c991)
    val DOT_FAILURE = JBColor(0xc74e39, 0xc74e39)
    val DOT_WARNING = JBColor(0xe1c08d, 0xe1c08d)

    // ── Links ────────────────────────────────────────────────────
    val LINK_COLOR = JBColor.namedColor("Link.activeForeground", JBColor(0x1a56b0, 0x6bb8ff))

    // ── Diff ─────────────────────────────────────────────────────
    val DIFF_ADDITION = JBColor.namedColor("FileColor.Green", JBColor(0x4caf50, 0x74c991))
    val DIFF_DELETION = JBColor.namedColor("FileColor.Rose", JBColor(0xe53935, 0xc74e39))

    // ── Badges ───────────────────────────────────────────────────
    val BADGE_FOREGROUND = JBColor.namedColor("Badge.foreground", JBColor(0xffffff, 0xffffff))
    val BADGE_BACKGROUND = JBColor.namedColor("Badge.background", JBColor(0x528bff, 0x365880))

    // ── Lists ────────────────────────────────────────────────────
    val LIST_HOVER_BACKGROUND = JBColor.namedColor(
        "List.hoverBackground",
        JBColor(0xedf5ff, 0x2e3033)
    )
    val LIST_ACTIVE_BACKGROUND = JBColor.namedColor(
        "List.selectionBackground",
        JBColor(0x2675bf, 0x2d5c8a)
    )
    val LIST_ACTIVE_FOREGROUND = JBColor.namedColor(
        "List.selectionForeground",
        JBColor(0xffffff, 0xffffff)
    )

    // ── Menus ────────────────────────────────────────────────────
    val MENU_BACKGROUND = JBColor.namedColor("PopupMenu.background", JBColor(0xf5f5f5, 0x2b2d30))
    val MENU_BORDER = JBColor.namedColor("Popup.borderColor", JBColor(0xc4c4c4, 0x464648))
    val MENU_FOREGROUND = JBColor.namedColor("PopupMenu.foreground", JBColor(0x1a1a1a, 0xbcbcbc))
    val MENU_SELECTION_BG = JBColor.namedColor(
        "MenuItem.selectionBackground",
        JBColor(0x2675bf, 0x2d5c8a)
    )
    val MENU_SELECTION_FG = JBColor.namedColor(
        "MenuItem.selectionForeground",
        JBColor(0xffffff, 0xffffff)
    )

    // ── Progress bar ─────────────────────────────────────────────
    val PROGRESSBAR_COLOR = JBColor.namedColor(
        "ProgressBar.progressColor",
        JBColor(0x528bff, 0x365880)
    )

    // ── Additional VS Code parity vars ──────────────────────────
    val CLAUDE_CLAY_BUTTON_ORANGE = JBColor(0xc6613f, 0xd97757)
}
