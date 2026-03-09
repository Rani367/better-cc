Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 9: Slash Command Menu

## Prerequisites

Phase 8 complete (diff viewer works).

## Goal

When the user types `/` in the prompt box, show a searchable dropdown of all available slash commands. Selecting a command executes it via the CLI and renders the result in the chat.

## Requirements

1. Create `SlashCommandPalette.kt`:
   - Popup that appears when user types `/` as the first character in the input
   - Searchable list: typing further filters commands (e.g., `/mo` filters to `/model`, `/memory`)
   - Each item shows: command name, brief description
   - Navigate with arrow keys, select with Enter
   - Escape or backspace past `/` dismisses the popup
   - Commands organized in sections (matching VS Code):
     - **Common:** `/model`, `/compact`, `/clear`, `/usage`
     - **Context:** `/memory`, `/permissions`
     - **Customize:** `/mcp`, `/plugins`, `/agents`
     - **Other:** all remaining CLI commands
2. Command list (hardcoded initially, can be made dynamic later). These are the real CLI slash commands:
   **Project Management:**
   - `/init` -- "Generate CLAUDE.md memory file"
   - `/memory` -- "Edit CLAUDE.md project conventions" -- opens file in editor
   - `/context` -- "Visualize context window usage"
   - `/compact` -- "Compress context, specify what to retain"
   - `/clear` -- "Reset conversation history"
   - `/resume` -- "Resume a past session" -- opens session picker
   - `/rename` -- "Rename current session"
   - `/rewind` -- "Rewind conversation or code changes" -- opens rewind menu
   **Information & Status:**
   - `/usage` -- "Check token usage against plan limits"
   - `/cost` -- "Show session cost in tokens and dollars"
   - `/help` -- "List all available commands"
   - `/tasks` -- "Monitor background tasks"
   - `/doctor` -- "Run environment diagnostics"
   - `/stats` -- "Generate usage statistics report"
   **Mode & Model Control:**
   - `/model` -- "Switch AI model" -- opens model picker (Phase 15)
   - `/fast` -- "Toggle Fast Mode"
   - `/plan` -- "Toggle read-only Plan Mode"
   - `/vim` -- "Enable Vim-style editing"
   - `/output-style` -- "Change response formatting style"
   **Feature Management:**
   - `/hooks` -- "Configure and manage hooks"
   - `/agents` -- "Create and manage sub-agents"
   - `/permissions` -- "Adjust tool and access permissions"
   - `/sandbox` -- "Activate sandboxed execution"
   - `/review` -- "Security review of changes"
   - `/config` -- "Open configuration settings"
   - `/login` -- "Re-authenticate session"
   - `/mcp` -- "Manage MCP servers" -- placeholder for Phase 17
   - `/plugins` -- "Manage plugins" -- placeholder for Phase 17
3. Update `InputPanel.kt`:
   - Detect `/` as first character and trigger the palette
   - When a command is selected, either:
     - Execute it immediately (for simple commands like `/clear`, `/compact`)
     - Replace the input with the command for commands that need arguments
   - After command execution, render the result as a system message in the chat
4. Write tests:
   - `SlashCommandPaletteTest.kt`: test filtering logic, command matching

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/commands/
└── SlashCommandPalette.kt

src/main/kotlin/com/claudecode/jetbrains/ui/chat/
├── InputPanel.kt (modified)
└── ChatToolWindow.kt (modified)

src/test/kotlin/com/claudecode/jetbrains/ui/
└── SlashCommandPaletteTest.kt
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open the Claude Code panel
2. Type `/` in the prompt box -- tell me:
   - Does a dropdown popup appear?
   - Does it show a list of commands with descriptions?
   - Are commands organized in sections?
3. Type `/mo` -- tell me if the list filters to show `/model`
4. Press Escape -- tell me if the popup dismisses
5. Type `/` again, arrow down to `/clear`, press Enter -- tell me:
   - Does the chat history clear?
   - Does a system message appear confirming the action?
6. Type `/usage` and select it -- tell me if token/cost info appears in the chat
7. Type `/compact` and select it -- tell me if Claude compacts the conversation and shows a confirmation
8. Type `/memory` and select it -- tell me if CLAUDE.md opens in the editor (or a message appears if it doesn't exist)
9. Type `/agents` and select it -- tell me what output appears
10. Run `./gradlew test` -- tell me if the slash command tests pass
