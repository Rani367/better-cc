package com.claudecode.jetbrains.ui.theme

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Colour constants — Warm Ember design system.
 * Deep charcoal backgrounds with warm coral/amber/gold accents.
 */
object ClaudeColors {

    // ── Brand ────────────────────────────────────────────────────
    val CLAUDE_ORANGE = JBColor(0xD4623A, 0xE8734A)
    val CLAUDE_CLAY_BUTTON = JBColor(0xBF5530, 0xE8734A)
    val CLAUDE_IVORY = Color(0xFA, 0xF8, 0xF5)
    val CLAUDE_SLATE = Color(0x1A, 0x1A, 0x1E)

    // ── Foreground ───────────────────────────────────────────────
    val PRIMARY_FOREGROUND = JBColor.namedColor("Label.foreground", JBColor(0x2A2420, 0xE8E0D8))
    val SECONDARY_FOREGROUND = JBColor.namedColor(
        "Label.disabledForeground",
        JBColor(0x8A7E74, 0x9A9490)
    )
    val ERROR_FOREGROUND = JBColor.namedColor("Label.errorForeground", JBColor(0xC62828, 0xE06C75))

    // ── Background ───────────────────────────────────────────────
    val PRIMARY_BACKGROUND = JBColor.namedColor("SidePanel.background", JBColor(0xFAF8F5, 0x1A1A1E))
    val SECONDARY_BACKGROUND = JBColor.namedColor("Editor.background", JBColor(0xFFFFFF, 0x242428))
    val INPUT_BACKGROUND = JBColor.namedColor("TextField.background", JBColor(0xFFFFFF, 0x242428))
    val TOOL_BACKGROUND = JBColor.namedColor("Editor.background", JBColor(0xFFFFFF, 0x242428))

    // ── Borders ──────────────────────────────────────────────────
    val INPUT_BORDER = JBColor.namedColor("Component.borderColor", JBColor(0xDDD8D0, 0x3A3A40))
    val INPUT_ACTIVE_BORDER = JBColor.namedColor(
        "Component.focusedBorderColor",
        JBColor(0xD4623A, 0xE8734A)
    )
    val PRIMARY_BORDER = JBColor.namedColor("Borders.color", JBColor(0xDDD8D0, 0x3A3A40))
    val TRANSPARENT_INNER_BORDER_LIGHT = Color(0x2A, 0x24, 0x20, 0x12)
    val TRANSPARENT_INNER_BORDER_DARK = Color(0xE8, 0xE0, 0xD8, 0x1a)

    // ── Buttons ──────────────────────────────────────────────────
    val BUTTON_BACKGROUND = JBColor.namedColor(
        "Button.default.startBackground",
        JBColor(0xD4623A, 0xE8734A)
    )
    val BUTTON_FOREGROUND = JBColor.namedColor(
        "Button.default.foreground",
        JBColor(0xFFFFFF, 0xFAF8F5)
    )
    val GHOST_BUTTON_HOVER = JBColor.namedColor(
        "ActionButton.hoverBackground",
        JBColor(0xEDE8E0, 0x3A3A40)
    )

    // ── Status ───────────────────────────────────────────────────
    val STATUS_BUSY = JBColor(0x6ECB8B, 0x6ECB8B)
    val STATUS_PENDING = JBColor(0x7CB3D4, 0x7CB3D4)
    val WARNING_ACCENT = JBColor(0xE5C07B, 0xE5C07B)
    val SUCCESS_FOREGROUND = JBColor.namedColor("FileColor.Green", JBColor(0x6ECB8B, 0x6ECB8B))

    // ── Spinner ──────────────────────────────────────────────────
    val SPINNER = CLAUDE_ORANGE

    // ── Timeline dots ────────────────────────────────────────────
    val DOT_DEFAULT = SECONDARY_FOREGROUND
    val DOT_SUCCESS = JBColor(0x6ECB8B, 0x6ECB8B)
    val DOT_FAILURE = JBColor(0xE06C75, 0xE06C75)
    val DOT_WARNING = JBColor(0xE5C07B, 0xE5C07B)

    // ── Links ────────────────────────────────────────────────────
    val LINK_COLOR = JBColor.namedColor("Link.activeForeground", JBColor(0xD4623A, 0x7CB3D4))

    // ── Diff ─────────────────────────────────────────────────────
    val DIFF_ADDITION = JBColor.namedColor("FileColor.Green", JBColor(0x6ECB8B, 0x6ECB8B))
    val DIFF_DELETION = JBColor.namedColor("FileColor.Rose", JBColor(0xE06C75, 0xE06C75))

    // ── Badges ───────────────────────────────────────────────────
    val BADGE_FOREGROUND = JBColor.namedColor("Badge.foreground", JBColor(0xFFFFFF, 0xFFFFFF))
    val BADGE_BACKGROUND = JBColor.namedColor("Badge.background", JBColor(0xD4623A, 0xE8734A))

    // ── Lists ────────────────────────────────────────────────────
    val LIST_HOVER_BACKGROUND = JBColor.namedColor(
        "List.hoverBackground",
        JBColor(0xEDE8E0, 0x2E2E32)
    )
    val LIST_ACTIVE_BACKGROUND = JBColor.namedColor(
        "List.selectionBackground",
        JBColor(0xD4623A, 0xE8734A)
    )
    val LIST_ACTIVE_FOREGROUND = JBColor.namedColor(
        "List.selectionForeground",
        JBColor(0xFFFFFF, 0xFFFFFF)
    )

    // ── Menus ────────────────────────────────────────────────────
    val MENU_BACKGROUND = JBColor.namedColor("PopupMenu.background", JBColor(0xFAF8F5, 0x242428))
    val MENU_BORDER = JBColor.namedColor("Popup.borderColor", JBColor(0xDDD8D0, 0x3A3A40))
    val MENU_FOREGROUND = JBColor.namedColor("PopupMenu.foreground", JBColor(0x2A2420, 0xE8E0D8))
    val MENU_SELECTION_BG = JBColor.namedColor(
        "MenuItem.selectionBackground",
        JBColor(0xD4623A, 0xE8734A)
    )
    val MENU_SELECTION_FG = JBColor.namedColor(
        "MenuItem.selectionForeground",
        JBColor(0xFFFFFF, 0xFFFFFF)
    )

    // ── Progress bar ─────────────────────────────────────────────
    val PROGRESSBAR_COLOR = JBColor.namedColor(
        "ProgressBar.progressColor",
        JBColor(0xF5A623, 0xF5A623)
    )

    // ── Secondary accent (golden amber) ─────────────────────────
    val SECONDARY_ACCENT = JBColor(0xC98B1A, 0xF5A623)

    // ── Additional vars ─────────────────────────────────────────
    val CLAUDE_CLAY_BUTTON_ORANGE = JBColor(0xBF5530, 0xE8734A)
}
