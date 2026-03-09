Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 20: VS Code Extension Clone Parity

## Prerequisites

Phase 19 complete. UI styling matches the VS Code extension.

## Goal

This phase brings better-cc to **exact feature parity** with the Claude Code VS Code extension. After Phase 19 handled visual fidelity, this phase handles every behavioural feature, protocol message, interaction flow, and capability that the VS Code extension provides. The result should be a JetBrains plugin that is, from the user's perspective, indistinguishable from the VS Code extension in what it can do.

**Reference source:** `/root/better-cc/reference/vscode-extension/` (extracted from marketplace VSIX, version 2.1.71)

---

## Reference: VS Code Extension Architecture

### Component Overview
- **Backend** (`extension.js`): Manages Claude CLI processes, sessions, diffs, MCP servers, auth, telemetry
- **Frontend** (`webview/index.js`): React app with full chat UI, session list, model selector, permissions, settings
- **Communication**: Bidirectional postMessage protocol between webview and extension backend
- **Rendering modes**: Sidebar, Panel (editor tab), Session List sidebar, Primary Editor, New Window
- **CLI interaction**: Spawns `claude` binary with `--output-format stream-json`

### Window Configuration Variables
The webview checks these flags to adapt its layout:
- `window.IS_FULL_EDITOR` — Panel/editor tab mode (wider layout)
- `window.IS_SIDEBAR` — Sidebar mode (narrow layout)
- `window.IS_SESSION_LIST_ONLY` — Session list sidebar (no chat, just session list)
- `window.IS_ANT` — Internal Anthropic build flag

---

## 1. Thinking Content Display

The VS Code extension displays actual thinking content from Claude, not just a spinner.

### Requirements

1. When Claude streams thinking blocks (`thinking` content type in the streaming protocol), capture the text content.
2. Display thinking text in a **collapsible section** (`thinking_aHyQPQ`):
   - Summary line: "Thinking..." with a toggle chevron (`thinkingToggle_aHyQPQ`)
   - Content: `thinkingContent_aHyQPQ` — secondary foreground, `font-size:0.9em`, `opacity:0.8`
   - Default state: collapsed (summary only visible)
   - Click summary to expand/collapse the thinking text
3. Thinking blocks appear as timeline items with a progress dot (blinking).
4. When thinking completes, the dot changes to success (green).
5. Thinking summary (`thinkingSummary_aHyQPQ`): A brief auto-generated summary of the thinking, shown even when collapsed.

### Protocol
The streaming JSON protocol sends thinking content as:
```json
{"type": "content_block_start", "content_block": {"type": "thinking", "thinking": ""}}
{"type": "content_block_delta", "delta": {"type": "thinking_delta", "thinking": "Let me analyze..."}}
{"type": "content_block_stop"}
```

---

## 2. Thinking Level Selector

### Requirements

1. Add a thinking level control accessible from the toolbar/input area.
2. Options: `off`, `low`, `medium`, `high` (matching Claude CLI `--thinking-level` flag).
3. The current thinking level should be visible in the UI.
4. Sends `set_thinking_level` message to backend, which passes `--thinking-level` to the CLI.
5. Changing thinking level takes effect on the next message (does not restart the session).

---

## 3. Model Selector

### Requirements

1. Implement a **model picker dropdown** (`modelList_G8AMvA`) accessible from the header/toolbar area.
2. Display available models with:
   - Model label (`modelLabel_G8AMvA`): The display name
   - Model description (`modelDescription_G8AMvA`): Brief capability description
   - Active model indicator (`activeModelItem_G8AMvA`): Check icon on the selected model
   - Section headers (`sectionHeader_G8AMvA`): Group models by category
3. Model indicator in the input footer (`modelIndicator_cKsPxg`): Shows current model name.
4. Sends `set_model` message to backend, which passes `--model` to the CLI or uses the settings API.
5. The model list should be fetched dynamically from the Claude CLI (`claude config list-models` or similar).
6. Maps to the existing `claudeCode.selectedModel` setting.

---

## 4. Permission Mode Selector

### Requirements

1. Add a **permission mode toggle** in the input footer area.
2. Options match `claudeCode.initialPermissionMode`:
   - `default` — Ask for permission on each action
   - `acceptEdits` — Auto-accept file edits, ask for other actions
   - `plan` — Planning mode, no execution
   - `bypassPermissions` — Skip all permission prompts (sandboxed only)
3. Visual indicator of current mode in the input footer.
4. Mode change spinner colour:
   - `default`: Claude orange
   - `acceptEdits`: primary foreground
   - `plan`: button background / focus border
   - `bypassPermissions`: error foreground (red)
5. Sends `set_permission_mode` message to backend.

---

## 5. Usage Tracking Display

### Requirements

1. Display API usage information (`usage_P2QnnQ`, `usageContainer_P2QnnQ`):
   - Usage bar (`usageBarContainer_JuUW3A`): Visual progress bar showing consumption
   - Usage fill (`usageFill_JuUW3A`): The filled portion of the bar
   - High usage state (`usageFillHigh_JuUW3A`): Changes colour when usage is high
   - Usage label (`usageLabel_JuUW3A`): Text description
   - Usage percent (`usagePercent_JuUW3A`): Percentage display
2. Usage data is fetched via `request_usage_update` message and received as usage update broadcasts.
3. Display in the account/settings area and optionally in the status bar widget.
4. The extension fetches usage via `fetchUsageData()` method on the communication channel.

---

## 6. Message Actions (Hover)

### Requirements

1. When hovering over a message, show action buttons (`messageActions`):
   - **Copy** — Copy message content to clipboard
   - **Retry** — Re-send the previous user message (regenerate response)
   - **Fork** — Fork the conversation at this point
2. Actions container (`NJ.container`): Positioned relative to the message, appears on hover.
3. Action buttons (`NJ.actionButton`): Small icon buttons that fade in (`NJ.visible` when `NJ.messageHovered`).
4. Popup (`NJ.popup`): Additional actions in a popup menu if needed.
5. The hover state is tracked per-message.

---

## 7. Fork Conversation

### Requirements

1. Allow forking a conversation at any message point.
2. Sends `fork_conversation` message to backend with the message index or ID.
3. Backend creates a new Claude session branched from that point.
4. Opens the forked conversation in a new tab/panel.
5. The forked session inherits all context up to the fork point.

---

## 8. Session Teleport

### Requirements

1. Allow moving a session between different rendering locations:
   - Sidebar → Panel (editor tab)
   - Panel → Sidebar
   - Panel → New Window
2. Sends `teleport_session` message to backend.
3. Backend responds with `teleport_session_response`.
4. The session state, history, and context are preserved during teleport.
5. The original view closes and the session opens in the new location.

---

## 9. Multi-Session Tab Management

### Requirements

1. Support **multiple concurrent Claude sessions** in separate editor tabs.
2. Each tab is an independent Claude CLI process.
3. Session tabs show:
   - Session title (editable via rename)
   - Status dot (indicating session state: idle, busy, waiting for input)
   - Claude logo icon
4. New sessions can be created from:
   - Command palette
   - Header bar "New Chat" button
   - Keyboard shortcut (Ctrl/Cmd+Shift+Escape equivalent)
5. Session management messages:
   - `new_conversation_tab` — Create new session in a new tab
   - `rename_session` / `rename_tab` — Rename a session
   - `delete_session` — Delete a session and its history
   - `list_sessions_request` — Get all sessions
   - `get_session_request` — Get specific session details

---

## 10. Session List Sidebar

### Requirements

1. Implement a dedicated **session list sidebar view** (`IS_SESSION_LIST_ONLY` mode).
2. Shows all sessions with:
   - Session name (`sessionName_OOQiHg`)
   - Session time (`sessionTime_OOQiHg`)
   - Session subtext (`sessionSubtext_OOQiHg`)
   - Status dot (`statusDot_OOQiHg`): Coloured dot indicating session state
   - Active session highlight (`sessionItem active`)
3. Search/filter sessions (`searchInput_OOQiHg`).
4. Context actions per session: Rename, Delete.
5. Click a session to open it (or switch to its existing tab).
6. Session list updates via `broadcastSessionStates` from backend.
7. Badge on the sidebar icon showing count of sessions waiting for input.

---

## 11. File Attachments in Input

### Requirements

1. Allow **dragging and dropping files** into the input area.
2. Allow **pasting images** from clipboard into the input.
3. Show attached files as chips above the text input (`attachedFilesContainer_cKsPxg`):
   - File name
   - File icon
   - Remove button (X)
4. Attached files are sent as context with the next message.
5. Supported file types: text files, images, PDFs.
6. Drop overlay (`dropInfoOverlay_07S1Yg`): Visual feedback during drag (Claude orange dashed border).

---

## 12. Speech-to-Text Input

### Requirements

1. Add a **microphone button** (`micButton_cKsPxg`) adjacent to the send button.
2. On click, start recording audio (`start_speech_to_text` message to backend).
3. Show recording state:
   - Mic button highlighted with recording indicator (`l3.recording`)
   - Audio waveform visualisation (`audioWaveform_cKsPxg`)
   - Audio popup (`audioPopup_cKsPxg`) with recording controls
4. On stop (`stop_speech_to_text`), transcribed text is inserted into the input area.
5. Backend handles the actual speech-to-text processing.

---

## 13. MCP Server Management

### Requirements

1. Implement **MCP server management UI** accessible from settings or a dedicated panel.
2. Display list of configured MCP servers with:
   - Server name
   - Status badge (`serverStatusBadge`): connected, disconnected, error, authenticating
   - Enable/disable toggle
3. Actions per server:
   - `reconnect_mcp_server` — Reconnect a disconnected server
   - `authenticate_mcp_server` — Trigger OAuth/auth flow
   - `clear_mcp_server_auth` — Clear stored auth tokens
   - `set_mcp_server_enabled` — Enable/disable a server
   - `submit_mcp_oauth_callback_url` — Complete OAuth callback flow
4. MCP marketplace integration:
   - `list_marketplaces` — List available MCP marketplaces
   - `add_marketplace` / `remove_marketplace` — Manage marketplace sources
   - `refresh_marketplace` — Refresh marketplace listings
5. Special integrations:
   - `ensure_chrome_mcp_enabled` / `disable_chrome_mcp` — Chrome browser MCP
   - `enable_jupyter_mcp` / `disable_jupyter_mcp` — Jupyter notebook MCP
6. Backend handles MCP server lifecycle through the Claude CLI's MCP APIs.

---

## 14. Plugin Management

### Requirements

1. Implement a **plugin management UI** (`PluginManagerDialog` already exists, needs feature parity).
2. List installed plugins with:
   - Plugin name
   - Status (enabled/disabled)
   - Scope badge
   - Official badge
3. Actions:
   - `install_plugin` — Install a new plugin
   - `uninstall_plugin` — Remove a plugin
   - `set_plugin_enabled` — Toggle plugin on/off
   - `list_plugins` — Refresh plugin list
4. Plugin data comes from the Claude CLI's plugin API.

---

## 15. Git Branch Integration

### Requirements

1. Show current **git branch** in the UI (`branchPill_5FHdxw` / `branchPill_LBSAWQ`).
2. Track branch changes during a session.
3. When Claude changes branches or files suggest branch changes:
   - Show branch pill with branch name
   - Warning banner (`warningBanner_LBSAWQ`) if on an unexpected branch
   - Checkout button (`checkoutButton_LBSAWQ`) to switch branches
4. Messages:
   - `check_git_status` — Check current git state
   - `checkout_branch` — Switch to a branch (response: `checkout_branch_response`)
   - `update_skipped_branch` — Mark a branch switch as skipped (response: `update_skipped_branch_response`)
5. File change list (`fileList_5FHdxw`):
   - Changed files with status icons
   - File names with diff stats
   - Warning icons for conflicts

---

## 16. Inline Diff Viewer

### Requirements

1. When Claude proposes file changes, show an **inline diff viewer** within the chat.
2. Use JetBrains native `DiffManager` for rendering diffs.
3. Diff actions:
   - `open_diff` — Open a diff view for a specific file
   - `open_file_diffs` — Open diffs for all changed files
   - Accept/Reject buttons on diff views
4. Diff stats display (`diffStats_oblbPg`): Show additions/deletions count.
5. The diff viewer should support:
   - Side-by-side view
   - Unified view
   - Expand/collapse unchanged regions

---

## 17. Plan Mode

### Requirements

1. When in `plan` permission mode, Claude operates in planning mode.
2. Plan display:
   - Structured plan with steps
   - `close_plan_preview` — Close the plan preview panel
   - `remove_plan_comment` — Remove a comment from the plan
3. Plan preview panel: Separate view showing the proposed plan before execution.

---

## 18. Onboarding Walkthrough

### Requirements

1. Implement a **getting started walkthrough** (`dismiss_onboarding` message to dismiss).
2. Steps (from VS Code walkthrough):
   - "Your AI coding partner" — Welcome message
   - "Open Claude Code" — How to open the chat
   - "Chat with Claude" — How to interact
   - "Past conversations" — How to access session history
3. Show on first launch unless `claudeCode.hideOnboarding` is true.
4. Milestone-style checklist (`milestoneText`, `checkmark`):
   - Completed milestones: checked with checkmark
   - Current milestone: bold text
   - Future milestones: greyed out

---

## 19. Keyboard Shortcuts

### Requirements

Adapt VS Code keybindings to JetBrains equivalents:

| VS Code | JetBrains Equivalent | Action |
|---------|---------------------|--------|
| `Cmd+Escape` / `Ctrl+Escape` | `Alt+C` (or configurable) | Focus/blur Claude input |
| `Cmd+Shift+Escape` | `Alt+Shift+C` (or configurable) | Open Claude in new tab |
| `Alt+K` | `Alt+K` | Insert @-mention reference |
| `Cmd+Alt+K` | `Ctrl+Alt+K` | Insert @-mention (terminal mode) |
| `Cmd+N` (when Claude focused) | `Ctrl+N` (when Claude focused) | New conversation |

All shortcuts should be configurable via JetBrains keymap settings.

---

## 20. Font Configuration

### Requirements

1. Support separate **editor font** and **chat font** configuration (from VS Code `chat.editor.*` and `chat.*` settings):
   - `chat.editor.fontFamily` — Font for code blocks (default: monospace)
   - `chat.editor.fontSize` — Font size for code blocks (default: 12)
   - `chat.editor.fontWeight` — Font weight for code blocks (default: normal)
   - `chat.fontSize` — Font size for chat text (default: 13)
   - `chat.fontFamily` — Font family for chat text (default: system sans-serif)
2. Font changes broadcast to all open webviews via `notifyFontConfigurationChange`.
3. In JetBrains: sync editor font from `EditorColorsManager`, chat font from plugin settings.

---

## 21. Ctrl+Enter to Send Mode

### Requirements

1. Setting: `claudeCode.useCtrlEnterToSend` (default: false).
2. When enabled:
   - `Enter` creates a new line in the input
   - `Ctrl+Enter` (or `Cmd+Enter` on Mac) sends the message
3. When disabled (default):
   - `Enter` sends the message
   - `Shift+Enter` creates a new line
4. Display the current mode hint in the input footer.

---

## 22. Auto-Save Before Claude Actions

### Requirements

1. Setting: `claudeCode.autosave` (default: true).
2. When enabled, automatically save dirty files before Claude reads or writes them.
3. Triggered on `PreToolUse` hooks for `Edit`, `Write`, and `Read` tools.
4. Uses JetBrains `FileDocumentManager.getInstance().saveDocument()`.

---

## 23. Terminal Fallback Mode

### Requirements

1. Setting: `claudeCode.useTerminal` (default: false).
2. When enabled, open Claude Code in a JetBrains terminal tab instead of the native UI.
3. `open_claude_in_terminal` message opens a terminal with the `claude` command.
4. Terminal mode uses the CLI's native TUI instead of the webview.
5. @-mention insertion in terminal mode works via `claude-code.insertAtMentioned`.

---

## 24. Logging and Diagnostics

### Requirements

1. `open_output_panel` — Open a log output panel showing Claude Code extension logs.
2. `showLogs` command — Show detailed logs for debugging.
3. Log all webview messages: `Received message from webview: ${JSON.stringify(msg)}`.
4. Use JetBrains `Logger` infrastructure, viewable in "Help > Show Log in..." or a dedicated tool window.

---

## 25. Authentication Flow

### Requirements

1. Login flow: `login` message triggers authentication.
2. `logout` command clears authentication.
3. Auth status display (`authStatusContainer`).
4. `disableLoginPrompt` setting suppresses auth prompts.
5. OAuth callback handling for MCP servers (`submit_oauth_code`).
6. Support for multiple auth methods:
   - API key
   - Claude.ai session
   - Claude Max subscription
   - Enterprise SSO

---

## 26. Extension Update Mechanism

### Requirements

1. `claude-vscode.update` command to update the extension.
2. `claude-vscode.installPlugin` command to install Claude Code plugins.
3. Check for updates on startup.
4. In JetBrains: use the standard plugin update mechanism or a custom check against npm/GitHub releases.

---

## 27. Context Providers

### Requirements

1. **IDE Selection** (`ideSelection`): Push current editor selection to Claude context.
2. **IDE Opened File** (`ideOpenedFile`): Push currently opened file path.
3. **IDE Diagnostics** (`ideDiagnostics`): Push compiler errors/warnings from the IDE.
4. **Terminal Contents** (`get_terminal_contents`): Fetch terminal output for context.
5. **Git Status** (`check_git_status`): Current branch, dirty files, etc.
6. These are sent as context messages to the Claude CLI process.

---

## 28. Notification System

### Requirements

1. `show_notification` message triggers IDE notifications.
2. Notification types: info, warning, error.
3. Use JetBrains `NotificationGroup` and `Notifications.Bus.notify()`.
4. Notifications may include action links.

---

## 29. Review Upsell Banner

### Requirements

1. `dismiss_review_upsell_banner` — Dismiss promotional banners.
2. Banners (`banner_Vt7lOA`, `bannerVertical_Vt7lOA`) for various promotions:
   - Close button (`closeButton_Vt7lOA`)
   - Banner content
3. Terminal banner: `dismiss_terminal_banner` — Dismiss terminal-related banners.

---

## 30. Open Actions

### Requirements

Implement all `open_*` message handlers:

| Message | JetBrains Action |
|---------|-----------------|
| `open_file` | `FileEditorManager.openFile()` |
| `open_diff` | `DiffManager.showDiff()` |
| `open_url` | `BrowserUtil.browse()` |
| `open_terminal` | `TerminalView.openTerminalIn()` |
| `open_config` | Open Claude settings JSON |
| `open_config_file` | Open specific config file |
| `open_folder` | `ProjectUtil.openOrImport()` |
| `open_content` | Open content in a new editor tab |
| `open_help` | Open Claude Code documentation |
| `open_output_panel` | Open log panel |
| `open_markdown_preview` | Open markdown preview |
| `open_in_editor` | Open content in primary editor |
| `open_file_diffs` | Open multi-file diff view |
| `open_claude_in_terminal` | Open Claude CLI in terminal |

---

## 31. Rewind / Code Checkpoint

### Requirements

1. `rewind_code` message triggers code rewind to a previous checkpoint.
2. The `CheckpointManager` (already exists) should handle:
   - Creating checkpoints before Claude makes changes
   - Reverting to a specific checkpoint
   - Showing checkpoint diff before rewind
3. UI: Rewind button on tool use blocks that made file changes.

---

## 32. Settings Sync

### Requirements

1. `apply_settings` message applies settings changes.
2. `open_config` / `open_config_file` messages open settings files.
3. Settings include:
   - `claudeCode.selectedModel`
   - `claudeCode.environmentVariables`
   - `claudeCode.useTerminal`
   - `claudeCode.allowDangerouslySkipPermissions`
   - `claudeCode.claudeProcessWrapper`
   - `claudeCode.respectGitIgnore`
   - `claudeCode.initialPermissionMode`
   - `claudeCode.disableLoginPrompt`
   - `claudeCode.autosave`
   - `claudeCode.useCtrlEnterToSend`
   - `claudeCode.preferredLocation`
   - `claudeCode.enableNewConversationShortcut`
   - `claudeCode.hideOnboarding`
   - `claudeCode.usePythonEnvironment`
4. Settings should persist via JetBrains `PersistentStateComponent`.

---

## 33. Python Environment Integration

### Requirements

1. Setting: `claudeCode.usePythonEnvironment` (default: true).
2. When enabled, detect and activate the workspace's Python virtual environment.
3. Pass environment activation to the Claude CLI process.
4. In JetBrains: Use the Python plugin's SDK detection if available.

---

## 34. JSON Schema Validation

### Requirements

1. The VS Code extension provides JSON schema validation for:
   - `.claude/settings.json`
   - `.claude/settings.local.json`
   - `ClaudeCode/managed-settings.json`
   - `claude-code/managed-settings.json`
2. Schema file: `claude-code-settings.schema.json` (bundled with extension).
3. In JetBrains: Register the JSON schema via `JsonSchemaProviderFactory`.

---

## 35. Event Logging / Telemetry

### Requirements

1. `log_event` message sends telemetry events.
2. Events include: session start, message sent, tool used, error occurred, etc.
3. In JetBrains: Use `FUCounterUsageLogger` or a custom analytics sink.
4. Respect user opt-out settings.

---

## Implementation Priority

### Tier 1 — Core UX (Must Have)
1. Thinking content display (Section 1)
2. Model selector (Section 3)
3. Permission mode selector (Section 4)
4. Message actions / hover (Section 6)
5. Multi-session tabs (Section 9)
6. Keyboard shortcuts (Section 19)
7. Ctrl+Enter mode (Section 21)
8. Open actions (Section 30)

### Tier 2 — Important Features
9. Thinking level selector (Section 2)
10. Usage tracking (Section 5)
11. Session list sidebar (Section 10)
12. File attachments (Section 11)
13. Inline diff viewer (Section 16)
14. Git branch integration (Section 15)
15. Auto-save (Section 22)
16. Font configuration (Section 20)
17. Context providers (Section 27)

### Tier 3 — Advanced Features
18. Fork conversation (Section 7)
19. Session teleport (Section 8)
20. MCP server management (Section 13)
21. Plugin management (Section 14)
22. Speech-to-text (Section 12)
23. Plan mode (Section 17)
24. Onboarding (Section 18)
25. Settings sync (Section 32)
26. Rewind / checkpoints (Section 31)

### Tier 4 — Polish
27. Terminal fallback (Section 23)
28. Logging / diagnostics (Section 24)
29. Authentication flow (Section 25)
30. Update mechanism (Section 26)
31. Notification system (Section 28)
32. Review banners (Section 29)
33. Python environment (Section 33)
34. JSON schema validation (Section 34)
35. Event logging (Section 35)

---

## Webview ↔ Backend Message Protocol (Complete Reference)

### Outgoing (Webview → Backend)
```
add_marketplace          — Add an MCP marketplace source
apply_settings           — Apply settings changes
authenticate_mcp_server  — Trigger MCP server auth flow
cancel_request           — Cancel a pending request
check_git_status         — Get current git status
checkout_branch          — Switch git branch
clear_mcp_server_auth    — Clear MCP server auth tokens
close_plan_preview       — Close plan preview panel
create_new_browser_tab   — Open new browser tab (Chrome MCP)
delete_session           — Delete a session
disable_chrome_mcp       — Disable Chrome MCP
disable_jupyter_mcp      — Disable Jupyter MCP
dismiss_onboarding       — Dismiss onboarding
dismiss_review_upsell_banner — Dismiss review banner
dismiss_terminal_banner  — Dismiss terminal banner
enable_jupyter_mcp       — Enable Jupyter MCP
ensure_chrome_mcp_enabled — Ensure Chrome MCP is on
fork_conversation        — Fork at a message point
get_asset_uris           — Get extension asset URIs
get_claude_state         — Get current Claude state
get_current_selection    — Get IDE editor selection
get_mcp_servers          — Get MCP server list
get_session_request      — Get specific session info
get_terminal_contents    — Get terminal output
init                     — Initialise webview
install_plugin           — Install a Claude plugin
interrupt                — Interrupt current operation
interrupt_claude         — Force interrupt Claude process
launch_claude            — Start Claude CLI process
list_files_request       — List project files
list_marketplaces        — List MCP marketplaces
list_plugins             — List installed plugins
list_remote_sessions     — List remote/teleported sessions
list_sessions_request    — List all sessions
log_event                — Log a telemetry event
login                    — Trigger login flow
new_conversation_tab     — Open new conversation in tab
open_claude_in_terminal  — Open Claude in terminal
open_config              — Open settings
open_config_file         — Open specific config file
open_content             — Open content in editor
open_diff                — Open diff view
open_file                — Open a file
open_file_diffs          — Open multi-file diffs
open_folder              — Open a folder
open_help                — Open documentation
open_in_editor           — Open in primary editor
open_markdown_preview    — Open markdown preview
open_output_panel        — Open log panel
open_terminal            — Open terminal
open_url                 — Open URL in browser
reconnect_mcp_server     — Reconnect MCP server
refresh_marketplace      — Refresh marketplace listings
remove_marketplace       — Remove marketplace source
remove_plan_comment      — Remove plan comment
rename_session           — Rename a session
rename_tab               — Rename a tab
request_usage_update     — Request usage data
rewind_code              — Rewind to checkpoint
set_mcp_server_enabled   — Toggle MCP server
set_model                — Change AI model
set_permission_mode      — Change permission mode
set_plugin_enabled       — Toggle plugin
set_thinking_level       — Change thinking level
show_claude_terminal_setting — Show terminal setting
show_notification        — Show IDE notification
start_speech_to_text     — Start voice recording
stop_speech_to_text      — Stop voice recording
submit_mcp_oauth_callback_url — Complete OAuth
submit_oauth_code        — Submit OAuth code
teleport_session         — Move session to new location
tool_permission_response — Respond to permission prompt
uninstall_plugin         — Remove a plugin
update_session_state     — Update session state
update_skipped_branch    — Mark branch skip
user                     — Send user message
```

### Incoming (Backend → Webview)
```
from-extension           — Wrapped Claude CLI output
update_state             — State change notification
session_states           — All session states broadcast
usage_update             — Usage data
font_config_change       — Font configuration change
checkout_branch_response — Branch checkout result
teleport_session_response — Teleport result
update_skipped_branch_response — Branch skip result
update_session_state_response — Session state result
set_mcp_server_enabled_response — MCP toggle result
authenticate_mcp_server  — Auth flow result
clear_mcp_server_auth    — Auth clear result
submit_mcp_oauth_callback_url — OAuth callback result
reconnect_mcp_server_response — Reconnect result
enable_jupyter_mcp_response — Jupyter MCP result
disable_jupyter_mcp_response — Jupyter MCP result
```

---

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/
├── cli/
│   ├── StreamJsonProtocol.kt        — (modify) Handle thinking content blocks
│   └── MessageProtocol.kt           — (new) Full message protocol implementation
│
├── ui/
│   ├── chat/
│   │   ├── ThinkingBlock.kt         — (new) Collapsible thinking content display
│   │   ├── MessageActions.kt        — (new) Hover actions (copy, retry, fork)
│   │   ├── ModelSelector.kt         — (new) Model picker dropdown
│   │   ├── PermissionModeSelector.kt — (new) Permission mode toggle
│   │   ├── ThinkingLevelSelector.kt — (new) Thinking level control
│   │   ├── UsageDisplay.kt          — (new) Usage bars and percentages
│   │   ├── FileAttachmentPanel.kt   — (new) File drag/drop and paste
│   │   ├── SpeechInput.kt           — (new) Microphone and waveform
│   │   └── DropOverlay.kt           — (new) File drag overlay
│   │
│   ├── sessions/
│   │   ├── SessionListPanel.kt      — (new) Full session list sidebar
│   │   ├── SessionTabManager.kt     — (modify) Multi-tab management
│   │   └── SessionStateTracker.kt   — (new) State broadcasting
│   │
│   ├── diff/
│   │   └── InlineDiffViewer.kt      — (new) Inline diff within chat
│   │
│   ├── git/
│   │   ├── BranchPill.kt            — (new) Branch name display
│   │   ├── BranchWarning.kt         — (new) Warning banner for branch changes
│   │   └── FileChangeList.kt        — (new) Changed files display
│   │
│   ├── mcp/
│   │   ├── McpServerList.kt         — (new) Server list with status
│   │   ├── McpAuthFlow.kt           — (new) OAuth authentication
│   │   └── McpMarketplace.kt        — (new) Marketplace browser
│   │
│   ├── onboarding/
│   │   └── WalkthroughPanel.kt      — (new) Getting started walkthrough
│   │
│   └── plan/
│       └── PlanPreview.kt           — (new) Plan mode display
│
├── context/
│   ├── TerminalContextProvider.kt   — (new) Terminal output for context
│   └── DiagnosticsProvider.kt       — (modify) IDE diagnostics push
│
├── settings/
│   └── ClaudeSettings.kt            — (modify) Add all missing settings
│
└── schema/
    └── ClaudeSettingsSchemaProvider.kt — (new) JSON schema for .claude/settings.json
```

---

## Manual Testing Checklist

### Thinking
- [ ] Thinking text is captured and displayed (not just dots)
- [ ] Thinking blocks are collapsible
- [ ] Thinking dot blinks during progress, turns green on completion
- [ ] Thinking level can be changed from UI

### Models & Modes
- [ ] Model selector shows available models with descriptions
- [ ] Changing model takes effect on next message
- [ ] Permission mode selector works (default/acceptEdits/plan/bypass)
- [ ] Spinner colour changes with permission mode

### Sessions
- [ ] Multiple sessions can be open in separate tabs
- [ ] Session list sidebar shows all sessions with status dots
- [ ] Sessions can be renamed and deleted
- [ ] Session teleport works between sidebar and panel
- [ ] Fork conversation creates a new branched session

### Messages
- [ ] Hover actions appear on messages
- [ ] Copy message works
- [ ] Retry regenerates the response

### Input
- [ ] File drag-and-drop shows overlay and attaches files
- [ ] Clipboard image paste works
- [ ] Ctrl+Enter mode can be toggled
- [ ] @mentions and /commands still work

### Git
- [ ] Branch pill shows current branch
- [ ] Branch change warnings appear
- [ ] Checkout button works

### MCP
- [ ] MCP servers are listed with status badges
- [ ] Servers can be enabled/disabled
- [ ] OAuth auth flow works
- [ ] Marketplace browsing works

### Usage
- [ ] Usage bars display correctly
- [ ] High usage state changes bar colour

### General
- [ ] All open_* actions work correctly
- [ ] Keyboard shortcuts are configurable
- [ ] Settings persist across restarts
- [ ] JSON schema validation works for .claude/settings.json
- [ ] Notifications appear correctly
