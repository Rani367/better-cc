Use plan mode, 2026 web research, and multiple choice questions to build a JetBrains IDE plugin that provides a first-class graphical Claude Code integration, bringing VS Code extension parity to JetBrains IDEs.

## Context: The Problem

The official Claude Code JetBrains plugin (beta, marketplace ID 27310) is a terminal wrapper. Per the official docs (https://code.claude.com/docs/en/jetbrains), it provides:

1. Quick launch shortcut (Cmd+Esc / Ctrl+Esc) that opens Claude Code **in the IDE's terminal**
2. Diff viewing piped to the IDE's diff viewer
3. Selection context sharing
4. File reference shortcuts (@File#L1-99)
5. Diagnostic sharing

That's the entire feature set. There is no graphical chat panel. No session management UI. No permission mode controls. No command menu. No checkpoint UI.

Meanwhile, the VS Code extension (https://code.claude.com/docs/en/vs-code) is a full graphical application with:

- Native chat panel with streaming markdown rendering
- Permission mode selector (Normal / Plan / Auto-accept / Bypass) in the prompt box
- `/` command menu (model switching, extended thinking, MCP, plugins, usage, hooks, memory, permissions)
- `@`-mentions with fuzzy file matching and line range support (@file.ts#5-10)
- Context window usage indicator
- Side-by-side inline diff viewer with explicit accept/reject prompts
- Conversation history browser (search, time-grouped: Today/Yesterday/Last 7 days)
- Resume remote sessions from claude.ai
- Multiple simultaneous conversations in tabs/windows (blue dot = pending permission, orange dot = finished)
- Draggable/repositionable panel (sidebar, editor area, secondary sidebar)
- Plugin management UI (`/plugins` with marketplaces tab)
- Chrome browser integration (`@browser`)
- Checkpoints with rewind (fork conversation / rewind code / fork + rewind)
- Onboarding walkthrough
- Status bar indicator
- Rich settings (selectedModel, initialPermissionMode, preferredLocation, autosave, useCtrlEnterToSend, respectGitIgnore, environmentVariables, claudeProcessWrapper)

The gap is enormous. This project closes it.

## Goal

Build a JetBrains plugin with a full graphical interface that communicates with the Claude Code CLI programmatically. The user never sees a terminal. The plugin should match (and where possible improve on) every VS Code extension feature listed above.

## Tech Stack

- **Language:** Kotlin
- **Build:** Gradle with `org.jetbrains.intellij.platform` plugin v2.10.5 (config block: `intellijPlatform { }`)
- **Template:** https://github.com/JetBrains/intellij-platform-plugin-template
- **Target IDEs:** IntelliJ IDEA, WebStorm, PyCharm, GoLand, Rider, PhpStorm, CLion, RustRover, Android Studio
- **Min platform version:** 2024.3+
- **CLI communication:** `claude -p --output-format stream-json --input-format stream-json --include-partial-messages`

## Architecture

### CLI Communication Layer

The plugin talks to Claude Code exclusively through its programmatic interface. No terminal emulation.

**Process spawning:**
```
claude -p --output-format stream-json --input-format stream-json --include-partial-messages --session-id <uuid>
```

Key CLI flags to leverage:
- `--output-format stream-json` -- newline-delimited JSON events streamed to stdout
- `--input-format stream-json` -- structured JSON input via stdin
- `--include-partial-messages` -- partial streaming events for token-by-token rendering
- `--session-id <uuid>` -- explicit session ID for persistence
- `--continue` / `--resume <id>` -- session resume
- `--model <name>` -- model switching (supports aliases: `sonnet`, `opus`, `haiku`)
- `--permission-mode <mode>` -- set permission mode (`default`, `plan`, `acceptEdits`, `dontAsk`, `bypassPermissions`)
- `--permission-prompt-tool <mcp-tool>` -- route permission prompts to an MCP tool (critical for GUI permission handling)
- `--allowedTools` / `--disallowedTools` -- tool restrictions
- `--worktree <name>` -- git worktree isolation for parallel sessions
- `--agents <json>` -- custom subagent definitions
- `--max-budget-usd <amount>` -- cost cap
- `--mcp-config <path>` -- MCP server configuration
- `--chrome` / `--no-chrome` -- browser integration toggle

**CLI detection:**
- Check PATH for `claude`
- Check common locations: npm global (`npm root -g`), brew, mise
- Allow manual override in settings (matching JetBrains plugin's "Claude command" setting)
- Verify with `claude auth status` (exits 0 if authenticated, 1 if not)

**Permission handling in GUI:**
The `--permission-prompt-tool` flag is key. The plugin should implement a local MCP server that receives permission requests from the CLI and renders them as GUI prompts. When the user approves/denies, the MCP tool responds back to the CLI. This is how permission prompts work without a terminal.

### Core Features

#### 1. Graphical Chat Panel (Tool Window)

Primary interface. Replaces the terminal entirely. Must match VS Code's panel capabilities.

**Message area:**
- Streaming token-by-token rendering using `--include-partial-messages` events
- Markdown with syntax-highlighted code blocks (language detection from fenced blocks)
- User messages visually distinct from Claude's responses (like VS Code's color coding)
- Hover-to-copy on code blocks
- Clickable file paths that open in the IDE editor (resolve against project root)
- Collapsible tool-use blocks showing what Claude did (file reads, writes, bash commands, their output)
- Typing/thinking indicator while streaming

**Prompt box (matching VS Code's prompt box features):**
- Multi-line input with Shift+Enter for newlines, Enter to send (configurable: Ctrl+Enter to send option)
- Auto-resizing as you type
- `@`-mention support: typing `@` opens a fuzzy file/folder picker (matching VS Code's behavior, supports `@file.ts#5-10` line ranges)
- `/` command menu: typing `/` opens a searchable dropdown with all CLI slash commands
- Context indicator: show context window usage percentage (from CLI events)
- Permission mode indicator at bottom of prompt box (click to switch between Normal/Plan/Auto-accept/Bypass)
- File attachment via button or Shift+drag
- Selected text in editor automatically visible to Claude (with toggle to hide, matching VS Code's eye/eye-slash indicator)

**Toolbar:**
- Extended thinking toggle ("Think Harder" / "Ultrathink")
- Model selector (dropdown using `--model` flag, supports aliases: sonnet, opus, haiku, or full model names)
- Session controls: "New Session", "Continue Last", session history browser
- Cost/usage display (from `/usage` or CLI events)

#### 2. Permission Prompts

When Claude wants to execute a tool (bash, file write, file read), the plugin shows an inline card in the chat:

- Tool name and icon
- Arguments (command text for bash, file path + diff preview for writes)
- Buttons: "Allow", "Allow for Session", "Deny"
- In Plan mode: Claude describes plan first, opens as markdown document for inline comments (matching VS Code behavior), then executes after approval

Implementation: Use `--permission-prompt-tool` pointing to the plugin's local MCP server that bridges CLI permission requests to GUI prompts.

#### 3. Diff Viewer Integration

When Claude proposes file edits:
- Show in IntelliJ's native side-by-side diff viewer (using `DiffManager` / `SimpleDiffRequest`)
- Green/red highlighting for additions/deletions
- Permission prompt asking to accept or reject
- Accept applies the edit; reject tells Claude and it can try again
- All accepted changes grouped as a single undo operation

**CRITICAL:** Never auto-accept changes. Always require explicit user action. This is the #1 complaint about the current plugin.

#### 4. Checkpoints / Rewind

Claude Code's checkpointing is NOT git-based. It is an internal snapshot system:
- Automatically captures file state before each edit made by Claude's file editing tools
- Every user prompt creates a new checkpoint
- Hover over any user message to reveal rewind button with options:
  - "Restore code and conversation" -- revert both to that point
  - "Restore conversation only" -- rewind to that message, keep current code
  - "Restore code only" -- revert file changes, keep conversation
  - "Summarize from here" -- compress subsequent messages into a summary
- After restore, the original prompt is placed back in the input field
- Checkpoints persist across sessions, auto-clean after 30 days
- Limitation: bash command changes (rm, mv, cp) are NOT tracked
- Visual indicator of files changed since last checkpoint

#### 5. Session Management

- Conversation history browser (dropdown at top of chat panel)
- Search by keyword, browse by time (Today, Yesterday, Last 7 days)
- Click to resume any session (via `--resume <session-id>`)
- Rename/remove sessions on hover
- Multiple simultaneous conversations in separate editor tabs (via "Open in New Tab")
- Tab status indicators: blue dot = pending permission, orange = finished while hidden
- Resume remote sessions from claude.ai (Remote tab, via `--teleport`)

#### 6. Command Menu (/ commands)

Triggered by typing `/` in the prompt box. Searchable dropdown including:
- `/model` -- switch model
- `/compact` -- compact conversation context
- `/usage` -- token/cost usage
- `/memory` -- view/edit CLAUDE.md
- `/permissions` -- view/change permission mode
- `/mcp` -- manage MCP servers
- `/plugins` -- plugin management
- `/agents` -- list configured subagents
- `/clear` -- clear chat
- All other CLI slash commands

Commands execute via CLI and results render in the chat.

#### 7. @-Mention File References

- Typing `@` opens fuzzy file picker (project files, respecting .gitignore)
- Supports line ranges: `@auth.ts#5-10`
- Folder references with trailing slash: `@src/components/`
- Keyboard shortcut to insert reference from current selection: Cmd+Option+K (Mac) / Alt+Ctrl+K (matching current JetBrains plugin)
- Selected text in editor auto-shared with Claude (with visibility toggle)

#### 8. Diagnostics Sharing

- Subscribe to `DaemonCodeAnalyzer` for real-time errors/warnings
- Automatically share relevant diagnostics with Claude (matching current JetBrains plugin behavior)
- Include error messages, severity, file path, line numbers
- Share compiler output and test results when available

#### 9. Project Context

- Read `.claude/settings.json` and `.claude/commands/` if present
- Respect `.gitignore` and `.claudeignore`
- Auto-detect project type
- Share project structure summary on first interaction

#### 10. MCP Server Management

- UI for viewing/managing MCP servers (accessible via `/mcp` command)
- Enable/disable servers, reconnect, OAuth management
- Add servers (delegates to `claude mcp add` CLI command)
- Status indicators

#### 11. Plugin Management

- UI for installing/managing Claude Code plugins (via `/plugins`)
- Two tabs: Plugins (installed + available) and Marketplaces
- Install scopes: for user / for project / local only
- Toggle enable/disable

#### 12. Chrome Browser Integration

- Support `@browser` mentions in prompts
- Delegates to CLI's `--chrome` flag
- Attachment menu option for browser tools

#### 13. Git Worktrees

- Support `--worktree` flag for parallel sessions
- Each worktree gets its own isolated conversation and file state

### Settings Page

Accessible via IntelliJ Settings > Tools > Claude Code.

Match all VS Code extension settings:

| Setting | Default | Description |
|---------|---------|-------------|
| `claudeCommand` | `claude` | Path to Claude CLI executable |
| `selectedModel` | `default` | Model for new conversations |
| `initialPermissionMode` | `default` | Default permission mode (default/plan/acceptEdits/dontAsk/bypassPermissions) |
| `preferredLocation` | `sidebar` | Where chat opens (sidebar/tab) |
| `autosave` | `true` | Auto-save files before Claude reads/writes |
| `useCtrlEnterToSend` | `false` | Use Ctrl+Enter instead of Enter to send |
| `respectGitIgnore` | `true` | Exclude .gitignore patterns from file references |
| `environmentVariables` | `[]` | Environment variables for Claude process |
| `hideOnboarding` | `false` | Hide onboarding walkthrough |

### UX Requirements

- **Full GUI. No terminal.** User interacts with a graphical chat panel. CLI runs invisibly.
- **No accidental acceptance.** All code changes require explicit confirmation. Not Enter, not Tab, not any key that could be pressed while typing.
- **Fast startup.** Load lazily. Don't block IDE startup. Use `postStartupActivity` for CLI detection.
- **Streaming.** Token-by-token rendering using `--include-partial-messages`. Typing indicator immediately on send.
- **Keyboard-driven.** Cmd+Esc / Ctrl+Esc to toggle focus (matching current plugin). Escape returns to editor. All actions keyboard-accessible.
- **Non-intrusive.** Never steal editor focus unless user invoked an action.
- **Native theming.** JBColor, JBUI.scale() for HiDPI, standard IntelliJ components. Must look correct in Darcula, Light, and New UI themes.
- **Narrow-width usable.** Chat panel works well docked to side. Code blocks horizontal-scroll, don't wrap awkwardly.

### File Structure

```
claude-code-jetbrains/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/main/
│   ├── kotlin/com/claudecode/jetbrains/
│   │   ├── ClaudeCodePlugin.kt                     # Plugin lifecycle
│   │   ├── cli/
│   │   │   ├── ClaudeCliManager.kt                 # CLI detection, spawning, health check
│   │   │   ├── ClaudeProcess.kt                    # Single CLI process (stdin/stdout JSON)
│   │   │   ├── StreamJsonProtocol.kt               # Parse stream-json events
│   │   │   ├── SessionManager.kt                   # Session persistence, resume, history
│   │   │   └── PermissionMcpServer.kt              # Local MCP server for permission prompts
│   │   ├── ui/
│   │   │   ├── chat/
│   │   │   │   ├── ChatToolWindow.kt               # Main chat panel container
│   │   │   │   ├── ChatToolWindowFactory.kt         # Tool window registration
│   │   │   │   ├── MessageList.kt                   # Scrollable message area
│   │   │   │   ├── MessageBubble.kt                 # Individual message (user/assistant)
│   │   │   │   ├── CodeBlockRenderer.kt             # Syntax-highlighted code blocks
│   │   │   │   ├── ToolUseBlock.kt                  # Collapsible tool execution display
│   │   │   │   ├── PermissionCard.kt                # Inline permission prompt card
│   │   │   │   ├── InputPanel.kt                    # Prompt box with auto-resize
│   │   │   │   ├── ToolbarPanel.kt                  # Model, thinking, session controls
│   │   │   │   └── ContextIndicator.kt              # Context window usage display
│   │   │   ├── commands/
│   │   │   │   ├── SlashCommandPalette.kt           # / command searchable dropdown
│   │   │   │   └── FileMentionPicker.kt             # @ fuzzy file picker
│   │   │   ├── diff/
│   │   │   │   ├── DiffPreviewPanel.kt              # IntelliJ native diff integration
│   │   │   │   └── CheckpointManager.kt             # File snapshot checkpoint/rewind
│   │   │   ├── sessions/
│   │   │   │   ├── SessionHistoryDialog.kt          # Conversation history browser
│   │   │   │   └── SessionTabManager.kt             # Multiple conversation tabs
│   │   │   ├── plugins/
│   │   │   │   └── PluginManagerDialog.kt           # Plugin/marketplace management UI
│   │   │   ├── mcp/
│   │   │   │   └── McpManagerDialog.kt              # MCP server management UI
│   │   │   └── settings/
│   │   │       ├── ClaudeSettingsConfigurable.kt    # Settings page
│   │   │       └── ClaudeSettingsComponent.kt       # Settings UI form
│   │   ├── actions/
│   │   │   ├── OpenClaudeAction.kt                  # Open/focus chat panel
│   │   │   ├── AskClaudeAction.kt                   # Ask about selection
│   │   │   ├── FixErrorAction.kt                    # Fix error from gutter
│   │   │   ├── RefactorAction.kt                    # Refactor with Claude
│   │   │   ├── GenerateTestsAction.kt               # Generate tests
│   │   │   ├── ExplainCodeAction.kt                 # Explain selection
│   │   │   ├── DocumentCodeAction.kt                # Add documentation
│   │   │   └── InsertFileRefAction.kt               # Cmd+Option+K file reference
│   │   ├── context/
│   │   │   ├── ProjectContextProvider.kt            # Project structure, type detection
│   │   │   ├── DiagnosticsProvider.kt               # IDE errors/warnings
│   │   │   ├── SelectionContextProvider.kt          # Editor selection tracking
│   │   │   ├── FileContextProvider.kt               # Current file info
│   │   │   └── GitContextProvider.kt                # Git status, branch, worktree
│   │   └── settings/
│   │       └── ClaudeSettings.kt                    # Persistent settings state
│   └── resources/
│       ├── META-INF/plugin.xml                      # Plugin descriptor
│       └── icons/
│           ├── claude.svg
│           ├── claude_dark.svg
│           ├── thinking.svg
│           ├── permission_pending.svg
│           └── session_done.svg
├── src/test/kotlin/com/claudecode/jetbrains/
│   ├── cli/
│   │   ├── ClaudeCliManagerTest.kt
│   │   ├── StreamJsonProtocolTest.kt
│   │   └── SessionManagerTest.kt
│   ├── ui/
│   │   └── SlashCommandPaletteTest.kt
│   └── context/
│       └── DiagnosticsProviderTest.kt
└── README.md
```

### plugin.xml Extension Points

- `com.intellij.toolWindow` -- Chat panel
- `com.intellij.projectService` -- ClaudeCliManager, SessionManager, CheckpointManager
- `com.intellij.applicationConfigurable` -- Settings page
- `com.intellij.intentionAction` -- Inline code actions (Ask, Fix, Refactor, etc.)
- `com.intellij.editorActionHandler` -- Keyboard shortcuts
- `com.intellij.postStartupActivity` -- Lazy CLI detection on project open
- `com.intellij.notificationGroup` -- Notifications (CLI not found, auth required, etc.)
- `com.intellij.statusBarWidgetFactory` -- Status bar indicator

### Stream JSON Protocol

With `--output-format stream-json --include-partial-messages`, the CLI outputs NDJSON. Each line is wrapped in an outer envelope:

```json
{"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello "}},"session_id":"msg_abc123","parent_tool_use_id":null,"uuid":"evt-xyz789"}
{"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"world!"}},"session_id":"msg_abc123","parent_tool_use_id":null,"uuid":"evt-def456"}
{"type":"stream_event","event":{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tool-uuid","name":"bash","input":{"command":"ls -la"}}},"session_id":"...","parent_tool_use_id":null,"uuid":"evt-ghi012"}
{"type":"stream_event","event":{"type":"content_block_stop","index":0,"content_block":{"type":"tool_result","tool_use_id":"tool-uuid","content":[{"type":"text","text":"file1.txt\nfile2.txt"}]}},"session_id":"...","parent_tool_use_id":null,"uuid":"evt-jkl345"}
```

Final result event:
```json
{"type":"result","result":{"content":[{"type":"text","text":"Done."}],"usage":{"input_tokens":1500,"output_tokens":800},"cost":{"input":0.003,"output":0.004,"total":0.007}},"session_id":"sess-uuid"}
```

Input format for `--input-format stream-json` (stdin):
```json
{"type":"user","message":{"role":"user","content":"Your prompt here"},"session_id":"optional-session-id"}
```

The plugin must:
- Parse the outer `stream_event` envelope and extract the inner `event` object
- Handle `content_block_delta` with `text_delta` for token-by-token text display
- Handle `content_block_delta` with `input_json_delta` for streaming tool input
- Handle `content_block_delta` with `thinking_delta` for extended thinking display
- Handle `content_block_start`/`content_block_stop` for tool use lifecycle
- Handle `message_start`/`message_stop` for message boundaries
- Parse the `result` event for cost/usage data
- Route permission prompts through the PermissionMcpServer to GUI cards
- Track `cost` data for the usage indicator

### Testing

- Unit tests for stream-json parsing (mock CLI output)
- Unit tests for session management
- Unit tests for diagnostic collection
- Unit tests for slash command palette logic
- UI tests using IntelliJ test framework
- Integration test: spawn real CLI process, verify round-trip
- Manual test matrix: IntelliJ IDEA, WebStorm, PyCharm (minimum)

### Distribution

- Publish to JetBrains Marketplace
- README with screenshots, feature comparison vs official plugin, installation guide
- Auto-update via Marketplace

### Non-Goals (v1)

- Custom model hosting / direct API calls (rely on CLI's auth)
- Inline autocomplete / ghost text (focus on explicit chat interactions first)
- Remote development support (defer to v2)
- Agent teams / tmux mode (defer to v2)
- Git worktree UI (defer to v2, CLI flag works from terminal)

### Success Criteria

- Installs and detects Claude Code CLI without manual config
- Full graphical chat with zero terminal exposure
- Token-by-token streaming with <200ms time-to-first-render after CLI starts streaming
- All VS Code extension features listed in the Context section are implemented
- Permission prompts appear as GUI cards, never in terminal
- Diffs use IntelliJ's native diff viewer with explicit accept/reject
- Checkpoint rewind works
- Session history with search and resume
- Multiple simultaneous conversations in tabs
- Zero accidental code acceptance scenarios
- Works across all target IDEs
- Cold startup adds <500ms to IDE launch time
- Correct in Darcula, Light, and New UI themes

---

## Phase 20: VS Code Extension Clone Parity

**Goal:** After Phase 19 achieved visual fidelity, Phase 20 achieves complete functional parity with the Claude Code VS Code extension. The plugin should be indistinguishable from the VS Code extension in capability.

**Reference:** VS Code extension source extracted to `reference/vscode-extension/` from marketplace VSIX (v2.1.71).

**Gap analysis:** `CLONE-GAP-ANALYSIS.md`

**Phase spec:** `claude-jetbrains-phases/phase-20-vscode-clone-parity.md` (35 sections, 4 priority tiers, complete message protocol reference)
