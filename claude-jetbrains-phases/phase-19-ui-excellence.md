Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 19: UI Excellence -- Faithful Port of the VS Code Extension UI

## Prerequisites

Phase 18 complete. All features implemented.

## Goal

This phase replaces the existing plugin UI with a faithful recreation of the Claude Code VS Code extension's webview. The VS Code extension's source (extracted from the VSIX marketplace package) defines the reference implementation. Every visual decision -- spacing, colours, layout, interaction pattern -- should match the extension unless the IntelliJ platform makes that impossible, in which case the closest equivalent is used.

This is not about inventing new UI patterns. It is about matching the real thing.

---

## Reference: VS Code Extension Design System

The following values are extracted directly from the extension's `webview/index.css`. All IntelliJ implementations must map to these.

### Colour Palette

| VS Code Extension Variable | Value | IntelliJ Mapping |
|---|---|---|
| `--app-claude-orange` | `#d97757` | Custom `JBColor(0xd97757, 0xd97757)` |
| `--app-claude-clay-button-orange` | `#c6613f` | Custom `JBColor(0xc6613f, 0xd97757)` (light uses clay, dark uses orange) |
| `--app-claude-ivory` | `#faf9f5` | Light theme only accent |
| `--app-claude-slate` | `#141413` | Dark theme only accent |
| `--app-primary-foreground` | `--vscode-foreground` | `JBColor.namedColor("Label.foreground")` |
| `--app-primary-background` | `--vscode-sideBar-background` | `JBColor.namedColor("SidePanel.background")` |
| `--app-secondary-foreground` | `--vscode-descriptionForeground` | `UIUtil.getLabelDisabledForeground()` |
| `--app-secondary-background` | `--vscode-editor-background` | `JBColor.namedColor("Editor.background")` |
| `--app-input-background` | `--vscode-input-background` | `JBColor.namedColor("TextField.background")` |
| `--app-input-border` | `--vscode-inlineChatInput-border` | `JBColor.namedColor("Component.borderColor")` |
| `--app-input-active-border` | `--vscode-inputOption-activeBorder` | `JBColor.namedColor("Component.focusedBorderColor")` |
| `--app-tool-background` | `--vscode-editor-background` | `JBColor.namedColor("Editor.background")` |
| `--app-button-background` | `--vscode-button-background` | `JBColor.namedColor("Button.default.startBackground")` |
| `--app-button-foreground` | `--vscode-button-foreground` | `JBColor.namedColor("Button.default.foreground")` |
| `--app-error-foreground` | `--vscode-errorForeground` | `JBColor.namedColor("Label.errorForeground")` |
| `--app-success-foreground` | `--vscode-gitDecoration-addedResourceForeground` | `JBColor.namedColor("FileColor.Green")` |
| `--app-warning-accent` | `#e5a54b` | Custom `JBColor(0xe5a54b, 0xe5a54b)` |
| `--app-status-busy` | `#22c55e` (charts green) | Custom `JBColor(0x22c55e, 0x22c55e)` |
| `--app-status-pending` | `#3b82f6` (charts blue) | Custom `JBColor(0x3b82f6, 0x3b82f6)` |
| `--app-spinner-foreground` | Claude orange (dark) / clay orange (light) | Use `ClaudeColors.SPINNER` |
| `--app-transparent-inner-border` | `#ffffff1a` (dark) / `#00000012` (light) | Custom semi-transparent `JBColor` |
| `--app-ghost-button-hover-background` | `--vscode-toolbar-hoverBackground` | `JBColor.namedColor("ActionButton.hoverBackground")` |

### Spacing Scale

| Token | Value | IntelliJ |
|---|---|---|
| `--app-spacing-small` | `4px` | `JBUI.scale(4)` |
| `--app-spacing-medium` | `8px` | `JBUI.scale(8)` |
| `--app-spacing-large` | `12px` | `JBUI.scale(12)` |
| `--app-spacing-xlarge` | `16px` | `JBUI.scale(16)` |

### Corner Radii

| Token | Value | IntelliJ |
|---|---|---|
| `--corner-radius-small` | `4px` | `JBUI.scale(4)` |
| `--corner-radius-medium` | `6px` | `JBUI.scale(6)` |
| `--corner-radius-large` | `8px` | `JBUI.scale(8)` |

### Typography

| Token | IntelliJ Mapping |
|---|---|
| `--vscode-chat-font-size` (13px) | `JBUI.Fonts.label(13)` |
| `--app-monospace-font-family` | `EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)` |
| `--app-monospace-font-size` | `EditorColorsManager.getInstance().globalScheme.editorFontSize` |
| `--app-monospace-font-size-small` | Editor font size minus 2 |

---

## 1. Root Layout

Replicate the extension's root structure: `root > header + body > content > sessionBody > chatContainer`.

### Requirements

1. **Root panel** (`root_aqhumA`): `display:flex; flex-direction:column; flex:1; overflow:hidden; user-select:none`. In Swing: a `JPanel` with `BorderLayout`. Background uses `--app-primary-background` (sidebar background). No user text selection on UI chrome.

2. **Header bar** (`header_aqhumA`): Height auto, `6px 10px` padding, `1px` bottom border in `--app-primary-border-color`, sidebar background. Contains:
   - **Sessions button** (`sessionsButton_aqhumA`): Left-aligned, ghost button (no border, transparent bg, `4px` radius). Text is `font-weight:500`, truncated with ellipsis at `max-width:300px`. Icon + session title + chevron. On hover: `--app-ghost-button-hover-background`.
   - **Header spacer**: Flex grows to push right buttons to the end.
   - **Right action buttons**: New chat, settings, etc. Icon-only ghost buttons, `16px` icon size.

3. **Chat container** (`chatContainer_07S1Yg`): Flex column, overflow hidden, `line-height:1.5`, relative positioned. Contains messages area + input area.

---

## 2. Messages Area

### Requirements

1. **Messages container** (`messagesContainer_07S1Yg`): Scrollable vertically, not horizontally. Flex column, `--app-primary-background`. Padding: `20px 20px 40px`. Gap: `0`. The extra bottom padding (40px) leaves space above the floating input.

2. **Bottom gradient overlay** (`messageGradient_07S1Yg`): A `150px` tall gradient overlay pinned to the bottom of the messages area. Goes from `transparent` at top to `--app-primary-background` at bottom. `pointer-events:none; z-index:2`. This creates the fade-to-background effect as messages approach the input area. In Swing: override `paint()` on a transparent `JPanel` layered over the scroll pane, painting a `GradientPaint`.

3. **Message spacing**: Each message is a `JPanel` with `8px 0` top/bottom padding (first message: `padding-top:0`). User messages have an additional `4px 0` margin (first: `margin-top:0`).

4. **Dimming effect** (`dimmed_07S1Yg`): When a specific message is highlighted (e.g., during search), all other messages go to `opacity:0.4`. The highlighted message stays at `opacity:1` with `z-index:10`. Implement via an alpha composite on message panels.

---

## 3. User Messages

### Requirements

1. **User message container** (`userMessageContainer_07S1Yg`): `display:inline-block; position:relative; align-items:flex-start; margin-left:0`. Left-aligned (NOT right-aligned -- the VS Code extension left-aligns user messages).

2. **User message bubble** (`userMessage_07S1Yg`):
   - `white-space:pre-wrap; word-break:break-word`
   - Border: `1px solid --app-input-border`
   - Border radius: `--corner-radius-medium` (6px)
   - Background: `--app-input-background`
   - `display:inline-block; overflow:hidden; user-select:text; max-width:100%`
   - Padding: `4px 6px`
   - **No shadow, no elevation, no special hover effects.** The VS Code extension keeps user messages minimal and flat.

3. **Slash command messages** (`slashCommandMessage_07S1Yg`): Monospace font, `font-size:0.9em`.

---

## 4. Assistant Messages -- Timeline Layout

This is the most distinctive UI element. The extension uses a **timeline/activity feed** layout for assistant messages, NOT chat bubbles.

### Requirements

1. **Timeline message** (`timelineMessage_07S1Yg`): Each assistant message block (text, tool use, etc.) is a timeline item:
   - Left padding: `30px` to make room for the timeline line and dot
   - `user-select:text; align-items:flex-start`

2. **Timeline dot** (`:before` pseudo-element):
   - Position: absolute, `7px` diameter circle, centered at `left:9px; top:15px`
   - Background: `--app-secondary-foreground` (default grey)
   - `border-radius:50%; z-index:1`
   - **Dot colour variants**:
     - `.dotSuccess`: `#74c991` (green)
     - `.dotFailure`: `#c74e39` (red)
     - `.dotWarning`: `#e1c08d` (amber)
     - `.dotProgress`: default colour with `animation: blink 1s linear infinite` (opacity 0-1-0)
   - In Swing: custom `paintComponent` drawing a filled circle at the correct offset

3. **Timeline vertical line** (`:after` pseudo-element):
   - Position: absolute, `1px` wide, `--app-primary-border-color`
   - `left:12px`, runs from `top:0` to `bottom:0`
   - First timeline item: line starts at `top:18px` (below the dot)
   - Last timeline item: line height is `18px` (stops after the dot)
   - Single item (both first and last): no line at all
   - In Swing: draw a 1px vertical line in `paintComponent`, calculating start/end based on sibling position

4. **No bubbles on assistant messages.** The VS Code extension does NOT wrap assistant text in bubbles. Text flows directly within the timeline item padding area. Background is the same as the messages container background.

---

## 5. Tool Use Blocks

### Requirements

1. **Tool summary line** (`toolSummary_ZUQaOA`): A single line (up to 2 lines, clamped with `-webkit-line-clamp:2`):
   - **Tool name** (`toolNameText_ZUQaOA`): `font-weight:700; margin-right:4px`
   - **Secondary text** (`toolNameTextSecondary_ZUQaOA`): Monospace, `--app-link-color` (for file paths / clickable items), `font-size:0.85em`. Uses `word-break:break-all; overflow-wrap:anywhere`.
   - For plain text secondary: `--app-secondary-foreground` instead of link colour

2. **Tool body** (`toolBody_ZUQaOA`): The expandable detail area:
   - Border: `0.5px solid --app-input-border`
   - Background: `--app-tool-background` (editor background)
   - Border radius: `5px`
   - Margin: `8px 0`
   - `max-width:100%`

3. **Tool body grid** (`toolBodyGrid_ZUQaOA`): Uses CSS grid with `grid-template-columns: max-content 1fr`:
   - Each row spans the full grid
   - Rows separated by `0.5px solid --app-input-border` top border (first row: no border)
   - Row padding: `4px`
   - **Label column** (`toolBodyRowLabel_ZUQaOA`): `--app-secondary-foreground`, `opacity:0.5`, monospace, `font-size:0.85em`, padding `4px 8px 4px 4px`
   - **Content column** (`toolBodyRowContent_ZUQaOA`): `white-space:pre-wrap; word-break:break-word`, padding `4px`
   - Content clipping: by default, content is clipped to `max-height:60px` with a `mask-image:linear-gradient(to bottom, bg 50px, transparent 60px)`. This creates a fade-out effect on long content.

4. **Secondary line** (`secondaryLine_mLrg7g`): Below tool name, `--app-secondary-foreground; opacity:0.7; font-size:0.85em`. Has a bracket prefix character. Clickable items get underline on hover.

5. **Bash command display** (`bashCommand_F2hEIg`): Monospace, `font-size:0.9em`, max-height `40vh` with scroll. When editable: `1px solid transparent` border, on hover/focus: `1px solid --app-input-active-border`.

6. **Copy button** (`copyButton_CEmTFw`): Secondary background, `1px solid --app-input-border`, `4px` radius, `4px` padding, `opacity:0` by default, `transition: opacity 0.15s, background 0.15s`. Appears (`opacity:1`) on parent hover. Icon: `14px`, secondary foreground colour.

7. **Tool body wrapper** (`toolBodyWrapper_fKyNXw`): Hidden when viewport width < 500px (responsive collapse). In IntelliJ: hide when tool window width < 500px.

---

## 6. Permission Request Cards

### Requirements

1. **Container** (`permissionRequestContainer_qlaBag`):
   - Background: `--app-input-secondary-background` (menu background)
   - Border: `1px solid --app-input-border`
   - Border radius: `--corner-radius-large` (8px)
   - `overflow:hidden; position:relative; max-height:70vh; margin-bottom:6px; padding:8px`
   - Inner background layer: `--app-input-background` with same radius, `position:absolute; inset:0`
   - On focus-within: border changes to `--app-input-active-border` at `65%` opacity via `color-mix`
   - Not focused: `--app-input-border`

2. **Content** (`permissionRequestContent_qlaBag`):
   - Primary foreground, `font-size:1.1em`, z-index 1 (above background layer)
   - **Header** (`permissionRequestHeader_qlaBag`): `font-weight:700; margin-bottom:4px`
   - **Input area** (`permissionRequestInput_qlaBag`): Monospace, `font-size:0.9em`, scrollable vertically
   - **Description** (`permissionRequestDescription_qlaBag`): Secondary foreground, `font-size:0.9em; margin-bottom:6px`. Contains collapsible `<details>` with chevron that rotates 180deg on open. JSON input shown in a bordered monospace box (4px radius, 8px padding).

3. **Buttons** (`buttonContainer_qlaBag`): Flex column, `gap:8px; margin-top:8px`:
   - Each button: full width, `text-align:left; padding:6px 8px; border-radius:4px; font-weight:500`
   - Uses `box-shadow: inset 0 0 0 1px --app-transparent-inner-border` instead of a visible border (this is the distinctive glass-like inner border effect)
   - **Shortcut number prefix** (`shortcutNum_qlaBag`): `opacity:0.6; padding-right:4px`
   - **Focused button** (tracked via `data-focused-index`): Gets `--app-button-background` background, `--app-button-foreground` foreground, `font-weight:700`, with `position:relative`
   - Disabled buttons: `opacity:0.5; cursor:not-allowed`
   - Hover (not disabled): `filter:brightness(1.1)`

4. **Reject message input** (`rejectMessageInput_qlaBag`): Same styling as buttons but with a `1px solid transparent` border. When focused (via `data-focused-index="3"`): border becomes `--app-input-active-border`.

5. **Keyboard hints** (`keyboardHints_qlaBag`): Below buttons, secondary foreground, `font-size:0.85em; margin-top:8px`.

---

## 7. Spinner / Thinking Indicator

### Requirements

1. **Spinner row** (`spinnerRow_07S1Yg`): `display:flex; align-items:center; height:1.85em; margin-top:4px; margin-left:0`. Entrance animation: `fadeIn 0.3s ease-in-out`.

2. **Spinner icon** (`icon_hc5dvw`): `display:inline-block; text-align:center; width:1.5em; font-family:monospace; font-size:1.5em`. Colour: `--app-spinner-foreground` (Claude orange).

3. **Spinner text** (`text_hc5dvw`): `font-weight:500`.

4. **Permission mode colours on spinner**:
   - Default: Claude orange
   - `acceptEdits` mode: primary foreground
   - `plan` mode: focus border / button background colour
   - `bypassPermissions` / `auto` mode: error foreground (red)

5. **Blinking animation for progress dots**: `@keyframes blink { 0%, 100% { opacity: 1 } 50% { opacity: 0 } }` at `1s linear infinite`.

---

## 8. Input Area

### Requirements

1. **Input container** (`inputContainer_07S1Yg`):
   - `position:absolute; z-index:20; bottom:16px; left:16px; right:16px`
   - Flex column
   - In Swing: use a `JLayeredPane` or overlay panel at the bottom of the chat container

2. **Permissions container** (`permissionsContainer_07S1Yg`): When a permission prompt is active, it appears above the input. `max-width:680px; margin:0 auto`. Centred within the input container.

3. **Message input container** (`messageInputContainer_cKsPxg`): The actual input composer:
   - Border: `1px solid --app-input-border`
   - Border radius: `--corner-radius-large` (8px)
   - Background: `--app-input-background`
   - On focus: border transitions to `--app-input-active-border`

4. **Attached files** (`attachedFilesContainer_cKsPxg`): Shown above the text input within the composer. File chips with remove buttons.

5. **Send button** (`sendButton_gGYT1w`): Positioned at the right end of the input area.

6. **Mic button** (`micButton_cKsPxg`): Adjacent to the send button for voice input.

7. **Audio waveform** (`audioWaveform_cKsPxg`): Shown during voice recording.

---

## 9. Empty State

### Requirements

1. **Empty state container** (`emptyState_07S1Yg`):
   - `display:flex; flex-direction:column; flex:1; justify-content:center; align-items:center`
   - Entrance: `animation: fadeIn 0.3s ease-in-out`
   - `user-select:none`

2. **Content** (`emptyStateContent_07S1Yg`): `text-align:center; max-width:480px; position:relative; top:-30px` (shifted up slightly from true centre).

3. **ASCII art** (`asciiArtContainer_Eg8KCQ`): The VS Code extension shows ASCII art of the Claude logo in the empty state. Monospace font.

4. **Empty state text** (`emptyStateText_07S1Yg`): Monospace, secondary foreground, `white-space:pre-wrap; font-size:10px`.

5. **Pictograms container** (`pictogramsContainer_07S1Yg`): Below the text, `display:flex; flex-wrap:wrap; justify-content:center; gap:10px; margin-top:30px; padding:0 20px`. These are clickable suggestion prompts.

---

## 10. Loading State

### Requirements

1. **Loading overlay** (`loadingState_07S1Yg`):
   - `position:absolute; z-index:5; width:100%; height:100%`
   - Background: `--app-primary-background`
   - Secondary foreground text
   - Centred content (`display:flex; flex-direction:column; justify-content:center; align-items:center`)
   - This overlays the entire chat area while the CLI is starting up

---

## 11. Error Banner

### Requirements

1. **Banner** (`errorBanner_07S1Yg`):
   - Background: `color-mix(in srgb, --app-primary-background 96%, --app-error-foreground 4%)` -- a very subtle tint of the error colour
   - Text: `--app-error-foreground`
   - Bottom border: `1px solid --app-error-foreground`
   - Layout: flex, `justify-content:space-between; align-items:flex-start; font-size:13px`
   - In Swing: blend the error foreground colour at 4% with the primary background using `Color` arithmetic

2. **Error message** (`errorMessage_07S1Yg`): `word-wrap:break-word; user-select:text; flex:1; margin-top:2px; padding:10px 12px`

3. **Dismiss button** (`errorDismiss_07S1Yg`): `44x44px`, error foreground, "X" character at `20px; line-height:1`. Hover: `opacity:0.7`. No background, no border.

---

## 12. Drop Overlay (File Drag & Drop)

### Requirements

1. **Overlay** (`dropInfoOverlay_07S1Yg`):
   - `position:absolute; inset:0; z-index:100; pointer-events:none`
   - Background: `color-mix(in srgb, --app-claude-orange, transparent 85%)` -- Claude orange at 15% opacity
   - Border: `2px dashed --app-claude-orange`
   - Centred label with primary background, primary foreground, medium radius, `8px 16px` padding, `font-weight:500`, `box-shadow: 0 2px 8px rgba(0,0,0,0.15)`

---

## 13. Sessions Panel

### Requirements

1. **Sessions list**: Each session entry (`sessionItem`) with:
   - Hover: `--app-list-hover-background`
   - Active: `--app-list-active-background` with `--app-list-active-foreground`
   - List padding: `0px`, item padding: `4px 8px`, border radius: `4px`, gap: `2px`

2. **Session button in header**: Ghost button, truncated text with ellipsis, `gap:6px` between icon and text, `max-width:300px`.

3. **New session button** (`newSessionButton_djirOA`): Standard action button in the sessions panel.

4. **Search input** (`searchInput_OOQiHg`): For filtering sessions. Standard input styling.

5. **Session rename** (`renameButton_OOQiHg`) and **delete** (`deleteButton_OOQiHg`): Context actions on session items.

---

## 14. Diff Viewer

### Requirements

1. **Diff editor container** (`diffEditorContainer_oXZawA` / `diffEditorContainer_s6OFow`): Hosts an embedded Monaco editor for diff viewing. In IntelliJ: use the native `DiffManager` with a compact configuration.

2. **Diff colours**:
   - Addition foreground: `--vscode-gitDecoration-addedResourceForeground` = `JBColor.namedColor("FileColor.Green")`
   - Deletion foreground: `--vscode-gitDecoration-deletedResourceForeground` = `JBColor.namedColor("FileColor.Rose")`

3. **Expand button** (`expandButton_s6OFow`): Toggle between compact and full diff view.

---

## 15. Todo List (Checklist) Rendering

### Requirements

1. **Container** (`todoListContainer_xheXVQ`): `grid-column:1/-1; padding:8px 0`. Flex column.

2. **List** (`todoList_xheXVQ`): No list-style, flex column, `gap:4px`.

3. **Item** (`todoItem_xheXVQ`): Flex row, `gap:8px; align-items:flex-start`.

4. **Checkbox** (`checkbox_xheXVQ`): Custom appearance, `1em x 1em`, `2px` border-radius, `1px solid --app-input-border`, `--app-input-background`. Checked state: `opacity:0.7` with "checkmark" symbol. Indeterminate: "asterisk" symbol.

5. **Completed item** (`completed_xheXVQ`): `opacity:0.7`, text has `text-decoration:line-through` in secondary foreground at `opacity:0.7`.

---

## 16. Progress Bar

### Requirements

1. Use `--app-progressbar-background` for the progress bar colour (`JBColor.namedColor("ProgressBar.progressColor")`).
2. Bordered with `--app-progressbar-border`.
3. For indeterminate progress: CSS animation cycling the bar position (replicate the Monaco `@keyframes progress` pattern).

---

## 17. Menus and Popups (Slash Commands, File Mentions)

### Requirements

1. **Menu styling**:
   - Background: `--app-menu-background`
   - Border: `--app-menu-border`
   - Foreground: `--app-menu-foreground`
   - Selection background: `--app-menu-selection-background`
   - Selection foreground: `--app-menu-selection-foreground`

2. Use `JBPopup` with custom list cell renderer matching these colours.

---

## 18. Animations

### Requirements

1. **fadeIn**: `@keyframes fadeIn { 0% { opacity: 0 } 100% { opacity: 1 } }` -- used for empty state, spinner row, general entrance. Duration: `0.3s ease-in-out`.

2. **blink**: `@keyframes blink { 0%, 100% { opacity: 1 } 50% { opacity: 0 } }` -- used for progress dots. Duration: `1s linear infinite`.

3. **Copy button transition**: `transition: opacity 0.15s, background 0.15s`.

4. **Button hover**: `filter:brightness(1.1)` on non-disabled permission buttons.

5. **Focus border transition**: Transition border colour on focus for input containers and permission cards.

6. **Respect reduced motion**: Check `UISettings.getInstance().animateWindows`. If disabled, set all animation durations to 0. Provide plugin setting: "Enable animations" (default: true).

---

## 19. Splitter (Panel Resizing)

### Requirements

1. **Splitter** (`--app-splitter-background`): Uses `--vscode-inlineChatInput-border`. On hover: `--app-splitter-hover-background` maps to `--vscode-sash-hoverBorder`. In IntelliJ: use `OnePixelSplitter` or `JBSplitter` with custom colours.

---

## 20. Badge and Tag Styling

### Requirements

1. **Badge**: `--app-badge-foreground` / `--app-badge-background`. Maps to `JBColor.namedColor("Badge.foreground")` / `JBColor.namedColor("Badge.background")`.

2. **Tags** (used for context chips, file chips): Use `JBColor.namedColor("Tag.background")` / `JBColor.namedColor("Tag.foreground")`.

---

## 21. Widget Border

### Requirements

1. `--app-widget-border` maps to `--vscode-editorWidget-border` = `JBColor.namedColor("EditorPane.inactiveBackground")` or the closest IntelliJ equivalent for widget chrome borders.

---

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/
├── theme/
│   ├── ClaudeColors.kt              — (new) All colour constants from the extension CSS
│   ├── ClaudeSpacing.kt             — (new) Spacing scale constants
│   ├── ClaudeCornerRadius.kt        — (new) Corner radius constants
│   └── ClaudeTypography.kt          — (new) Font size/family utilities
│
├── layout/
│   ├── ClaudeRootPanel.kt           — (modified) Replicate root_aqhumA layout
│   ├── ClaudeHeaderBar.kt           — (new) Header with session button + actions
│   ├── ClaudeChatContainer.kt       — (modified) chatContainer_07S1Yg layout
│   └── GradientOverlayPanel.kt      — (new) 150px bottom gradient overlay
│
├── chat/
│   ├── MessagesContainer.kt         — (modified) Scroll pane with 20/20/40px padding
│   ├── UserMessageBubble.kt         — (modified) Flat input-bg bubble, 6px radius, 4px 6px padding
│   ├── TimelineMessagePanel.kt      — (new) Timeline layout with dot + vertical line
│   ├── TimelineDot.kt               — (new) 7px coloured dot component (success/failure/warning/progress)
│   ├── ToolUseBlock.kt              — (modified) Grid layout, 0.5px borders, content clipping
│   ├── ToolBodyGrid.kt              — (new) max-content / 1fr grid with row separators
│   ├── CopyButton.kt                — (modified) 0-opacity default, 0.15s transition, appears on hover
│   ├── BashCommandDisplay.kt        — (new) Monospace, 40vh max, editable border on focus
│   ├── SecondaryLine.kt             — (new) 0.85em, 0.7 opacity, bracket prefix
│   ├── SpinnerRow.kt                — (modified) Claude orange, 0.3s fadeIn, 1.85em height
│   ├── InputPanel.kt                — (modified) Floating absolute, 16px margins, 8px radius
│   ├── EmptyStatePanel.kt           — (modified) ASCII art, pictograms, fadeIn, top:-30px offset
│   ├── LoadingStateOverlay.kt       — (new) Absolute overlay, z-index 5, centred
│   └── DropOverlayPanel.kt          — (new) Claude orange dashed border overlay
│
├── permissions/
│   ├── PermissionCard.kt            — (modified) Secondary bg, inner bg layer, 8px radius, 8px padding
│   ├── PermissionButtons.kt         — (modified) Full-width, inner box-shadow border, focused highlight
│   ├── PermissionDescription.kt     — (new) Collapsible details with chevron rotation
│   ├── RejectMessageInput.kt        — (new) Styled reject input with focus border
│   └── KeyboardHints.kt             — (new) 0.85em secondary text below buttons
│
├── diff/
│   ├── DiffPreviewPanel.kt          — (modified) Native DiffManager integration
│   └── DiffColors.kt                — (new) Addition/deletion colour mapping
│
├── sessions/
│   ├── SessionsPanel.kt             — (modified) List styling, hover/active states
│   └── SessionButton.kt             — (new) Ghost button, truncated, 300px max
│
├── common/
│   ├── ErrorBanner.kt               — (new) 4% error tint background, dismiss button
│   ├── TodoListRenderer.kt          — (new) Custom checkbox, completed strike-through
│   ├── AnimationManager.kt          — (new) fadeIn, blink, transitions
│   └── GhostButton.kt               — (new) Transparent bg, hover bg, icon-only variant
│
├── ClaudeStatusBarWidget.kt         — (modified) Status colours: busy=#22c55e, pending=#3b82f6
│
└── settings/
    └── ClaudeSettingsComponent.kt   — (modified) Add "Enable animations" toggle
```

---

## Manual Testing

After completing this phase, verify each element against the VS Code extension running side-by-side.

**Layout:**
1. Does the root panel use sidebar background colour?
2. Is the header bar pinned at top with 1px bottom border?
3. Does the session button truncate long titles with ellipsis?
4. Does the chat container fill remaining space?

**Messages area:**
5. Is message padding exactly 20px left/right, 20px top, 40px bottom?
6. Does the 150px gradient overlay appear at the bottom of the message list?
7. Is the gradient pointer-events-none (can you still click messages under it)?

**User messages:**
8. Are user messages left-aligned (NOT right-aligned)?
9. Do they use input-background with 1px input-border?
10. Is the corner radius 6px (medium)?
11. Is padding 4px 6px?
12. No shadow, no hover elevation?

**Timeline layout:**
13. Are assistant messages indented 30px from the left?
14. Does each assistant message have a 7px coloured dot at left:9px, top:15px?
15. Does a vertical 1px line connect consecutive timeline items at left:12px?
16. Does the line start at 18px for the first item?
17. Does the line stop at 18px height for the last item?
18. Is there no line for a single standalone item?
19. Do progress dots blink (1s linear infinite)?

**Tool use blocks:**
20. Is the tool name bold with 4px right margin?
21. Is the tool body bordered with 0.5px, 5px radius, editor background?
22. Does the grid have max-content/1fr columns?
23. Are row labels at 0.5 opacity in monospace 0.85em?
24. Does long content clip at 60px with a fade-out gradient?
25. Does the copy button appear on hover with a 0.15s opacity transition?

**Permission cards:**
26. Is the card background menu-background with input-border?
27. Does the card have an inner background layer (input-background)?
28. On focus, does the border change to active-border at 65% opacity?
29. Do buttons use inner box-shadow instead of visible borders?
30. Does the focused button (tracked by index) get primary-button background and bold text?
31. Does the shortcut number have 0.6 opacity?
32. Do non-disabled buttons brighten on hover?
33. Is the reject input border transparent until focused?

**Spinner:**
34. Is the spinner icon Claude orange and 1.5em?
35. Does the spinner row fade in over 0.3s?
36. Does the permission mode change the spinner colour (plan=blue, bypass=red)?

**Input area:**
37. Is the input floating at the bottom with 16px margins?
38. Does it have 8px corner radius?
39. Does the border change colour on focus?

**Empty state:**
40. Is it vertically centred but offset 30px upward?
41. Does it fade in over 0.3s?
42. Are pictograms wrapped with 10px gap?

**Error banner:**
43. Is the background a 96%/4% mix of primary-bg and error colour?
44. Is the dismiss button 44x44px with no background?

**Drop overlay:**
45. When dragging a file, does the Claude orange overlay appear with a dashed border?

**Side-by-side comparison:**
46. Open the VS Code extension and the IntelliJ plugin side by side. Compare each UI element. Note any differences in spacing, colour, or layout and fix them.
47. Test in both light and dark themes.
48. Test at narrow width (< 500px) -- does the tool body wrapper hide?
49. Performance: with 50+ messages, is scrolling smooth?
50. Memory: are animations properly disposed when panels are removed?
