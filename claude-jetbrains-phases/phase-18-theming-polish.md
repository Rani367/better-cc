Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 18: Theming & Polish

## Prerequisites

Phase 17 complete (all features implemented).

## Goal

Final polish pass. Ensure the plugin looks native and correct in all IntelliJ themes (Darcula, Light, New UI), handles HiDPI displays, works well at narrow widths, has complete keyboard navigation, and includes an onboarding walkthrough for new users.

## Requirements

### Theme Correctness

1. Audit all UI components for theme compliance:
   - Use `JBColor` for all colors (never hardcode hex values)
   - Use `JBUI.scale()` for all pixel sizes (spacing, padding, borders)
   - Use `UIUtil.getFont()` or `JBUI.Fonts` for all font sizes
   - Test and fix rendering in:
     - Darcula (dark) theme
     - IntelliJ Light theme
     - New UI (both dark and light variants)
   - Icons: ensure all custom icons have `_dark.svg` variants or use `IconLoader` correctly
   - Code blocks: background colors should adapt to theme
   - Message bubbles: distinct but theme-appropriate colors for user vs assistant
   - Permission cards: border and background should be visible in all themes
   - Diff viewer: ensure it uses IntelliJ's native diff colors

### HiDPI & Scaling

2. Ensure all sizes use `JBUI.scale()`:
   - Icon sizes
   - Padding and margins
   - Border widths
   - Minimum/maximum component sizes
   - Font sizes in custom renderers
3. Test on Retina/HiDPI displays (or with scaling settings)

### Narrow Width Layout

4. Ensure the chat panel works at narrow widths (250px-400px when docked to side):
   - Code blocks: horizontal scroll, no wrapping
   - Tool use blocks: truncate long text with ellipsis, expand on click
   - Toolbar: collapse into a compact layout or overflow menu at narrow widths
   - Input area: remains usable
   - Message bubbles: text wraps properly, no overflow

### Keyboard Navigation

5. Complete keyboard accessibility:
   - Tab order through all interactive elements
   - Escape from chat panel returns focus to editor
   - Cmd+Esc / Ctrl+Esc toggles between editor and chat
   - Arrow keys navigate slash command palette and file mention picker
   - Enter/Space activate buttons and toggles
   - All actions have keyboard shortcuts listed in their tooltips
   - Permission cards: keyboard shortcuts for Allow (Enter), Deny (Escape)

### Onboarding Walkthrough

6. Create an onboarding checklist for first-time users:
   - Appears when plugin is first installed
   - Checklist items with "Show me" buttons:
     - "Send your first message"
     - "Use @-mentions to reference files"
     - "Try slash commands"
     - "Review a code change"
   - Dismissible with X button
   - Can be re-shown from settings (uncheck "Hide onboarding")

### Error Handling & Edge Cases

7. Final pass on error handling:
   - CLI process crashes mid-conversation: show error message, offer to retry
   - Network timeout: show timeout message
   - Authentication expired: show re-auth prompt
   - Very long messages: virtual scrolling or truncation with "Show more"
   - Empty responses: show appropriate message
   - Concurrent file edits (user edits file while Claude is editing): handle gracefully

### Performance

8. Performance audit:
   - Plugin startup: must add <500ms to IDE launch
   - Chat panel open: must render within 200ms
   - Streaming: no visible lag between receiving events and rendering
   - Memory: no leaks from unclosed processes or unused sessions
   - Test with 100+ messages in a single conversation

## File Structure (modified files)

```
(All UI files may be modified for theme/scaling fixes)
src/main/kotlin/com/claudecode/jetbrains/ui/chat/*.kt
src/main/kotlin/com/claudecode/jetbrains/ui/commands/*.kt
src/main/kotlin/com/claudecode/jetbrains/ui/diff/*.kt
src/main/kotlin/com/claudecode/jetbrains/ui/sessions/*.kt
src/main/kotlin/com/claudecode/jetbrains/ui/settings/*.kt
src/main/kotlin/com/claudecode/jetbrains/ui/*.kt
src/main/resources/icons/*.svg

(New)
src/main/kotlin/com/claudecode/jetbrains/ui/onboarding/
└── OnboardingPanel.kt
```

## Manual Testing

After completing this phase, do the FULL test pass and tell me the results for each:

**Theme testing:**
1. Switch to Darcula theme -- open Claude Code panel -- tell me if all elements (messages, code blocks, tool cards, permission cards, toolbar, input) look correct with proper contrast
2. Switch to Light theme -- same check -- tell me if everything is readable and properly styled
3. Switch to New UI (dark) -- same check
4. Switch to New UI (light) -- same check

**HiDPI testing:**
5. If possible, change display scaling to 150% or 200% -- tell me if all elements scale correctly without pixelation or overflow

**Narrow width testing:**
6. Dock the chat panel to the right side and resize it to ~300px wide -- tell me:
   - Can you still read messages?
   - Do code blocks have horizontal scroll?
   - Is the input area usable?
   - Does the toolbar adapt?

**Keyboard testing:**
7. Open the chat panel using only keyboard shortcuts -- tell me if you can:
   - Focus the input (Cmd+Esc / Ctrl+Esc)
   - Send a message (Enter or Ctrl+Enter depending on setting)
   - Open slash commands (type /)
   - Navigate commands with arrow keys and select with Enter
   - Dismiss popups with Escape
   - Navigate back to editor with Escape

**Onboarding testing:**
8. Reset the plugin (clear settings/data) -- tell me if the onboarding checklist appears on first open
9. Click "Show me" on each item -- tell me if they work
10. Dismiss with X -- tell me if it stays dismissed
11. Go to settings, uncheck "Hide onboarding" -- tell me if it reappears

**Error handling testing:**
12. Kill the Claude CLI process while a response is streaming -- tell me if the plugin shows an error and offers to retry
13. Set the Claude command to an invalid path -- try to send a message -- tell me if an appropriate error appears
14. Send a very long prompt (paste a large file) -- tell me if the input handles it

**Performance testing:**
15. Send 20+ messages in a conversation -- tell me if scrolling remains smooth
16. Restart the IDE -- tell me if startup time feels normal (not noticeably slower)
