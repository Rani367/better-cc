# Better-CC: VS Code Extension Clone — Gap Analysis

**Date:** 2026-03-09
**Goal:** Make better-cc an EXACT clone of the Claude Code VS Code extension for JetBrains IDEs.
**Reference source:** `/root/better-cc/reference/vscode-extension/` (extracted from marketplace VSIX)

---

## Architecture Comparison

### VS Code Extension
- **Backend:** `extension.js` (766 lines bundled) — spawns Claude CLI, manages sessions, handles diffs
- **Frontend:** React webview (`webview/index.js` 4.7MB bundled, `webview/index.css` 354KB)
- **Communication:** `postMessage` / `onDidReceiveMessage` between webview and extension
- **Rendering modes:** Sidebar view, Panel (editor tab), Session list sidebar, New Window
- **CLI interaction:** Spawns `claude` binary with `--output-format stream-json`, JSON streaming protocol

### Better-CC (Current)
- **Backend:** Kotlin/JVM — `ClaudeCliManager`, `ClaudeProcess`, `StreamJsonProtocol`, `SessionManager`
- **Frontend:** Hybrid — Swing panels + JCEF webview (`chat.html/css/js`) for messages
- **Communication:** `executeJavaScript()` calls from Kotlin into JCEF webview
- **Rendering:** Tool window (sidebar), Editor tabs via `ClaudeEditorProvider`
- **CLI interaction:** Similar JSON streaming, but custom implementation

---

## Feature-by-Feature Comparison

### ✅ Features Present in Both (needs UI parity check)
| Feature | VS Code | Better-CC | UI Match? |
|---------|---------|-----------|-----------|
| Chat messages (user/assistant) | React components | JCEF webview | **Partial** |
| Tool use display | Collapsible timeline | ToolUseBlock | **Partial** |
| Code blocks with syntax highlighting | Monaco-based | highlight.js | **Different** |
| Copy button on code blocks | ✅ | ✅ | Partial |
| Thinking indicator | Animated dots/content | Dots only | **Missing content** |
| Session management | Full list, rename, delete | SessionHistoryPanel | **Partial** |
| Slash commands | CommandPalette | SlashCommandPalette | **Partial** |
| File mentions (@) | FilePicker | FileMentionPicker | **Partial** |
| Permission requests | Inline approve/reject | PermissionMcpServer | **Different** |
| Diff preview | Monaco diff editor | DiffPreviewPanel | **Different** |
| Settings | VS Code settings UI | ClaudeSettingsComponent | **Different** |
| Status bar widget | ✅ | ClaudeStatusBarWidget | Partial |
| Error banners | ✅ | ErrorBanner | Partial |
| Context indicators | Selection/file context | ContextIndicator | Partial |
| Toolbar | Model selector, actions | ToolbarPanel | **Partial** |
| Input panel | Multi-line, @mentions | InputPanel | **Partial** |

### ❌ Features Missing from Better-CC
| Feature | Description |
|---------|-------------|
| **Session tabs/panels** | VS Code supports multiple concurrent Claude panels in different columns |
| **Session list sidebar** | Dedicated sidebar view listing all sessions with status dots |
| **Thinking content display** | VS Code shows actual thinking text, collapsible. Better-CC only shows dots |
| **Usage tracking UI** | Usage bars showing API consumption (usageFill, usagePercent) |
| **Model selector dropdown** | Interactive model picker with descriptions |
| **Permission mode selector** | Inline toggle for default/acceptEdits/plan/bypassPermissions |
| **Thinking level selector** | Toggle thinking level from UI |
| **MCP server management** | Full MCP server list with status badges, auth, enable/disable |
| **Plugin management** | Install/uninstall/enable plugins UI |
| **Onboarding walkthrough** | Step-by-step getting started flow |
| **Git branch integration** | Branch pills, checkout, skip branch warnings |
| **File attachment input** | Drag-and-drop files, paste images into input |
| **Audio/speech input** | Speech-to-text with waveform visualization |
| **Diff viewer inline** | Monaco-based diff viewer within the chat |
| **Fork conversation** | Fork a conversation at any point |
| **Session teleport** | Transfer session between sidebar/panel/window |
| **Terminal fallback** | "Open in Terminal" for users who prefer CLI |
| **Plan preview** | Plan mode with structured plan display |
| **Tool annotations** | Destructive tool warnings |
| **Collapsible tool calls** | Grouped tool calls with expand/collapse |
| **Message actions** | Hover actions on messages (copy, retry, etc.) |
| **Message highlighting** | Highlight specific messages |
| **Keyboard shortcuts** | Cmd+Esc focus/blur, Cmd+Shift+Esc new tab, Alt+K @mention |
| **New Window mode** | Open Claude in a separate VS Code window |
| **Jupyter MCP integration** | Notebook integration via MCP |
| **Chrome MCP integration** | Browser debugging via MCP |
| **Debugger integration** | Active debug session awareness |
| **Context from selections** | IDE selection pushed to Claude context |
| **Font configuration** | Editor font + chat font separate configuration |
| **Ctrl+Enter mode** | Optional Ctrl+Enter to send instead of Enter |
| **Review upsell banners** | Promotional banners |
| **Todo list rendering** | Checkbox-style todo items in messages |
| **ASCII art display** | Special rendering for ASCII art content |
| **Marketplace integration** | MCP marketplace browsing |

---

## UI Layout Differences (Critical)

### VS Code Extension Layout
```
┌─────────────────────────────────────────┐
│ [Sessions dropdown] [Model: X] [⚙]     │  ← Header bar
├─────────────────────────────────────────┤
│                                         │
│  ● You                           12:34  │  ← User message (right-aligned dot)
│  │ Message content                      │
│  │                                      │
│  ● Claude                        12:34  │  ← Assistant message (timeline dot)
│  │ Response with **markdown**           │
│  │                                      │
│  │ ▶ Read file (src/main.kt)           │  ← Collapsible tool use
│  │ ▶ Edit file (src/app.kt)            │
│  │                                      │
│  │ ┌─────────────────────────────┐      │  ← Code block
│  │ │ kotlin                 Copy │      │
│  │ │ fun main() { ... }         │      │
│  │ └─────────────────────────────┘      │
│  │                                      │
│  ● Thinking...                          │  ← Thinking with content
│  │ ▶ Show thinking                      │
│  │                                      │
│  ● ┌─Permission Request──────────┐      │  ← Permission inline
│  │ │ Edit: src/main.kt           │      │
│  │ │ [Accept] [Reject]           │      │
│  │ └─────────────────────────────┘      │
│                                         │
├─────────────────────────────────────────┤
│ [@mentions] [/commands]                 │  ← Input area
│ ┌───────────────────────────────────┐   │
│ │ Type a message...                 │   │
│ └───────────────────────────────────┘   │
│ [Mode: default ▾] [Cost: $0.12] [Send]  │  ← Footer
└─────────────────────────────────────────┘
```

### Key Visual Elements
1. **Timeline line:** Vertical line connecting assistant messages (left side)
2. **Timeline dots:** Colored dots (success=green, error=red, progress=blue, warning=orange)
3. **User messages:** Different styling, no timeline dot
4. **Tool calls:** Collapsible with chevron, summarized text
5. **Permission requests:** Inline cards with action buttons
6. **Status bar:** Model indicator, cost display
7. **Session dropdown:** Top-left, shows current session name
8. **Model selector:** Top area, clickable to change model

---

## Webview Message Protocol (VS Code Extension)

### Webview → Extension (outgoing)
```
launch_claude, cancel_request, interrupt, interrupt_claude,
tool_permission_response, set_model, set_permission_mode,
set_thinking_level, open_file, open_diff, open_url,
open_terminal, open_config, open_config_file, open_folder,
open_content, open_help, open_output_panel, open_markdown_preview,
open_in_editor, open_file_diffs, open_claude_in_terminal,
get_claude_state, get_current_selection, get_terminal_contents,
get_mcp_servers, get_asset_uris, get_session_request,
list_sessions_request, list_files_request, list_plugins,
list_marketplaces, list_remote_sessions,
rename_session, delete_session, rename_tab,
new_conversation_tab, fork_conversation, teleport_session,
login, log_event, request_usage_update,
show_notification, show_claude_terminal_setting,
dismiss_onboarding, dismiss_terminal_banner, dismiss_review_upsell_banner,
close_plan_preview, remove_plan_comment,
install_plugin, uninstall_plugin, set_plugin_enabled,
add_marketplace, remove_marketplace, refresh_marketplace,
authenticate_mcp_server, clear_mcp_server_auth,
submit_mcp_oauth_callback_url, submit_oauth_code,
reconnect_mcp_server, set_mcp_server_enabled, ensure_chrome_mcp_enabled,
enable_jupyter_mcp, disable_jupyter_mcp, disable_chrome_mcp,
check_git_status, checkout_branch, update_skipped_branch,
update_session_state, apply_settings, rewind_code,
start_speech_to_text, stop_speech_to_text,
create_new_browser_tab, init
```

### Extension → Webview (incoming)
```
from-extension (wraps all messages from Claude CLI)
update_state (state changes)
session states updates
usage updates
font configuration changes
```

---

## CSS Architecture

### VS Code Extension
- CSS Modules with hashed suffixes (e.g., `message_07S1Yg`, `sessionItem_OOQiHg`)
- VS Code CSS variables (`--vscode-editor-*`, `--vscode-foreground`, etc.)
- 354KB of CSS covering all components
- Responsive to sidebar vs panel mode (`IS_FULL_EDITOR`, `IS_SIDEBAR`)

### Better-CC
- Custom CSS variables (`--app-primary-*`, `--app-secondary-*`)
- 1227 lines of CSS
- Maps JetBrains theme colors to CSS variables via Kotlin

---

## Priority Implementation Order

### Phase 1: Core UI Parity (Critical)
1. Replace JCEF webview with exact VS Code webview CSS/JS (adapt React → vanilla JS or port CSS)
2. Timeline layout with dots and vertical line
3. Thinking content display (collapsible)
4. Collapsible tool use blocks
5. Permission request inline cards
6. Message actions (hover copy/retry)
7. Usage tracking display

### Phase 2: Feature Parity
8. Model selector dropdown
9. Permission mode selector
10. Thinking level selector
11. Session list with status dots
12. Multi-session tab management
13. Font configuration
14. Keyboard shortcuts (adapted for JetBrains)

### Phase 3: Advanced Features
15. MCP server management UI
16. Plugin management
17. Git branch integration
18. File attachments / paste images
19. Speech-to-text
20. Onboarding walkthrough
21. Diff viewer inline
22. Fork conversation
23. Session teleport

### Phase 4: Platform-Specific Adaptations
24. JetBrains-native diff viewer integration
25. JetBrains terminal integration
26. JetBrains debugger state awareness
27. JetBrains editor font/theme sync

---

## Files to Reference
- `reference/vscode-extension/package.json` — All commands, settings, keybindings, views
- `reference/vscode-extension/extension.js` — Backend logic (session management, CLI spawning)
- `reference/vscode-extension/webview/index.js` — React frontend (4.7MB bundled)
- `reference/vscode-extension/webview/index.css` — All component styles (354KB)
- `reference/vscode-extension/resources/` — Icons and assets
