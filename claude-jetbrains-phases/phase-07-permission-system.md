Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 7: Permission System

## Prerequisites

Phase 6 complete (tool use blocks display in chat).

## Goal

Implement a GUI-based permission system. When Claude wants to execute a tool (bash command, file write, file read), show an inline permission card in the chat. The user explicitly approves or denies. No tool executes without permission (unless in auto-accept mode).

## Requirements

1. Create `PermissionMcpServer.kt`:
   - Implement a local MCP server that listens for permission prompt requests from the CLI
   - The CLI's `--permission-prompt-tool` flag routes permission requests to this MCP tool
   - The MCP server receives: tool name, arguments, risk assessment
   - The MCP server waits for the GUI user's response before returning allow/deny to the CLI
   - Use a local HTTP or stdio-based MCP transport
2. Create `PermissionCard.kt`:
   - Inline card rendered in the chat when a permission prompt arrives
   - Shows:
     - Tool name and icon
     - Arguments summary (command text for bash, file path + content preview for writes)
     - Risk indicator (normal operations vs potentially destructive)
   - Three buttons:
     - "Allow" -- approve this single action
     - "Allow for Session" -- approve all future uses of this tool type in this session
     - "Deny" -- reject this action
   - Card is visually distinct from regular messages (bordered, highlighted background)
   - Buttons are large enough to not be accidentally clicked
   - After action: card updates to show "Allowed" or "Denied" status (greyed out, non-interactive)
3. Add permission mode selector to the input panel:
   - Small indicator/button at the bottom of the prompt box
   - Click to cycle or dropdown to select from 5 modes:
     - **Default** -- prompts for permission on first use of each tool; read-only is automatic
     - **Plan** -- read-only analysis mode; Claude generates a plan for review, no file mods or command execution
     - **Auto-accept (acceptEdits)** -- auto-accepts file edits but still prompts for bash/commands
     - **Don't Ask (dontAsk)** -- auto-denies tools unless pre-approved via /permissions or allow rules
     - **Bypass (bypassPermissions)** -- skips all prompts; requires `allowDangerouslySkipPermissions` setting
   - Mode is passed to CLI via `--permission-mode` flag when spawning process
   - In "Auto-accept" mode, skip permission cards for file edits (still show for bash)
   - In "Plan" mode, Claude describes its plan first before executing
   - In "Don't Ask" mode, tools are denied unless explicitly whitelisted
4. Update `ClaudeProcess.kt`:
   - Add `--permission-prompt-tool` flag pointing to the local MCP server
   - Pass `--permission-mode` based on current setting
5. Update `ChatToolWindow.kt`:
   - Insert `PermissionCard` when a permission prompt event arrives
   - Block further processing until user responds
   - Resume CLI execution after user's choice

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/
├── cli/
│   ├── PermissionMcpServer.kt
│   └── ClaudeProcess.kt (modified)
├── ui/chat/
│   ├── PermissionCard.kt
│   ├── InputPanel.kt (modified)
│   └── ChatToolWindow.kt (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open the Claude Code panel
2. Ensure permission mode is set to "Normal" (default)
3. Send: "Create a file called permission-test.txt with some content"
4. Tell me:
   - Does a permission card appear asking to allow the file write?
   - Does it show the file path and content preview?
   - Are the Allow / Allow for Session / Deny buttons visible?
5. Click "Allow" -- tell me if:
   - The card updates to show "Allowed"
   - Claude proceeds and creates the file
   - A tool use block appears showing the result
6. Send: "Run the command 'ls -la'"
7. Tell me if a permission card appears for the bash command
8. Click "Deny" -- tell me if:
   - The card updates to show "Denied"
   - Claude responds acknowledging it couldn't run the command
9. Test "Allow for Session": send another bash command, click "Allow for Session", then send a third bash command -- tell me if the third one executes without asking
10. Switch permission mode to "Auto-accept" using the selector -- send a command that would normally require permission -- tell me if it executes without a permission card
11. Switch to "Plan" mode -- send a complex request -- tell me if Claude describes its plan before executing
