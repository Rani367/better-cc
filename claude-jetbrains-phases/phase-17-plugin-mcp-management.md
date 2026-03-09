Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 17: Plugin & MCP Management

## Prerequisites

Phase 16 complete (settings page works).

## Goal

Implement GUI for managing Claude Code plugins (install, enable/disable, marketplaces) and MCP servers (view, enable/disable, reconnect, add/remove).

## Requirements

### Plugin Management

1. Create `PluginManagerDialog.kt`:
   - Triggered by `/plugins` slash command
   - Modal dialog with two tabs: "Plugins" and "Marketplaces"
   - **Plugins tab:**
     - Installed plugins at top with toggle switches (enable/disable)
     - Available plugins from configured marketplaces below
     - Search bar to filter by name or description
     - "Install" button on each available plugin
     - Install scope chooser: "Install for you" (user) / "Install for this project" (project) / "Install locally" (local)
   - **Marketplaces tab:**
     - List of configured marketplace sources
     - Each entry shows: source URL/path, refresh icon, delete icon
     - "Add marketplace" input field (GitHub repo, URL, or local path)
     - Refresh icon updates the marketplace's plugin list
   - All operations delegate to CLI commands (`claude plugins ...` or equivalent)
   - Show "Restart required" banner after changes

### MCP Server Management

2. Create `McpManagerDialog.kt`:
   - Triggered by `/mcp` slash command
   - Modal dialog showing all configured MCP servers
   - Each server shows:
     - Name
     - Status indicator (connected/disconnected/error)
     - Enable/disable toggle
     - "Reconnect" button
     - OAuth status (if applicable)
   - "Add Server" section at bottom:
     - Server name input
     - Transport type dropdown (stdio, http, sse)
     - Command/URL input
     - Delegates to `claude mcp add` CLI command
   - "Remove" button on each server (with confirmation)
   - Operations delegate to CLI: `claude mcp add`, `claude mcp remove`, `claude mcp list`

### Integration

3. Update slash command palette:
   - `/plugins` opens `PluginManagerDialog`
   - `/mcp` opens `McpManagerDialog`
4. Both dialogs read current state from CLI and write changes back via CLI commands

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/plugins/
└── PluginManagerDialog.kt

src/main/kotlin/com/claudecode/jetbrains/ui/mcp/
└── McpManagerDialog.kt

src/main/kotlin/com/claudecode/jetbrains/ui/commands/
└── SlashCommandPalette.kt (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open Claude Code panel
2. Type `/plugins` -- tell me:
   - Does a plugin management dialog open?
   - Does it have "Plugins" and "Marketplaces" tabs?
   - Does the Plugins tab show installed plugins (if any)?
3. If there are installed plugins, toggle one off -- tell me if it shows "Restart required"
4. Switch to Marketplaces tab -- tell me:
   - Can you see configured marketplaces?
   - Try adding a marketplace URL -- does it appear in the list?
   - Click refresh on a marketplace -- does it update?
5. Type `/mcp` -- tell me:
   - Does an MCP management dialog open?
   - Does it list configured MCP servers (if any)?
   - Does each server show a status indicator?
6. If MCP servers are configured:
   - Toggle one off -- tell me if it disables
   - Click "Reconnect" -- tell me if it reconnects
7. Try "Add Server" with a test server:
   - Enter a name and command
   - Tell me if it delegates to `claude mcp add` successfully
8. Try removing a server -- tell me if it asks for confirmation and removes it
9. Close and reopen the dialogs -- tell me if changes persisted
