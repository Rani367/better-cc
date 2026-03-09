Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 13: Checkpoints & Rewind

## Prerequisites

Phase 12 complete (multiple conversations work).

## Goal

Implement a checkpoint and rewind system matching Claude Code's built-in checkpointing. Users can hover over any message to reveal a rewind button with options to restore code, conversation, or both.

## Important Context

Claude Code's checkpointing system is **NOT git-based**. It is Claude Code's own internal snapshot system that:
- Automatically captures file state before each edit made by Claude's file editing tools
- Creates a new checkpoint with every user prompt
- Persists across sessions (accessible in resumed conversations)
- Auto-cleans after 30 days
- Does NOT track changes made by bash commands (e.g., `rm`, `mv`, `cp`)
- Does NOT track external/manual file changes

The CLI exposes this via the `/rewind` command (or double-Esc). The VS Code extension provides a GUI overlay on top of this system.

## Requirements

1. Create `CheckpointManager.kt`:
   - Track Claude's file edits at the plugin level by intercepting Write/Edit tool use events
   - Before each file edit is applied, save a snapshot of the original file content
   - Associate snapshots with the user prompt message that triggered them
   - Store snapshots locally (plugin data directory), keyed by session ID and message index
   - Support restore operations:
     - **Restore code and conversation**: revert files AND conversation to a checkpoint
     - **Restore conversation only**: rewind to that message, keep current code
     - **Restore code only**: revert file changes, keep conversation history
     - **Summarize from here**: compress conversation from this point forward into a summary (frees context window space)
   - Track which files were changed at each checkpoint
   - Clean up old checkpoints (30 day retention, matching CLI behavior)
   - Handle edge cases:
     - Files that no longer exist (deleted outside Claude)
     - Files modified both by Claude and manually
     - New files created by Claude (restore = delete them)

2. Also integrate with the CLI's built-in `/rewind` command:
   - When user triggers rewind in the GUI, send `/rewind` to the CLI process if possible
   - Fall back to the plugin's own snapshot system if the CLI doesn't support programmatic rewind

3. Update `MessageBubble.kt`:
   - Add a rewind button that appears on hover over any user message
   - Small icon (e.g., rewind/clock icon) in the message corner
   - Click opens a popup with four options (matching the CLI's `/rewind` menu):
     - "Restore code and conversation" -- revert both to this point
     - "Restore conversation only" -- rewind conversation, keep current code
     - "Restore code only" -- revert file changes, keep conversation
     - "Summarize from here" -- compress subsequent messages into a summary
     - "Never mind" -- dismiss
   - After restore, the original prompt text is placed back in the input field for re-sending or editing

4. Update `ChatToolWindow.kt`:
   - Handle each rewind option:
     - "Restore code and conversation": restore file snapshots + truncate message history to that point
     - "Restore conversation only": truncate message history, leave files as-is
     - "Restore code only": restore file snapshots, keep full message history
     - "Summarize from here": keep messages before the point, replace subsequent with a compact summary
   - Show visual indicator of how many files were changed since the selected checkpoint
   - After any restore, re-populate the input field with the original prompt

5. Update the file write flow:
   - After each accepted file write, automatically snapshot the pre-edit state
   - Associate the snapshot with the current user prompt/message

## Important Limitations to Document

- Bash command changes (rm, mv, cp, etc.) are NOT tracked and cannot be undone through rewind
- External/manual file changes are not captured
- This is session-level recovery, not a replacement for git
- Summarize keeps the same session and only compresses context; for branching, use fork (`--continue --fork-session`)

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/
├── checkpoint/
│   └── CheckpointManager.kt
├── ui/chat/
│   ├── MessageBubble.kt (modified)
│   ├── ChatToolWindow.kt (modified)
│   └── RewindPopup.kt
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open a project
2. Send a prompt that causes Claude to modify a file (e.g., "Add a comment to build.gradle.kts")
3. Accept the changes via the diff viewer
4. Hover over your user message that triggered the change -- tell me:
   - Does a rewind button appear?
   - Does it show a rewind/clock icon?
5. Send another prompt that modifies a different file
6. Accept the changes
7. Click the rewind button on the FIRST user message, select "Restore code and conversation" -- tell me:
   - Are the second set of file changes reverted?
   - Is the first set of changes still intact (since that checkpoint captured state before the first edit)?
   - Is the conversation truncated to just the first exchange?
   - Is the original prompt placed back in the input field?
8. Test "Restore code only" on a message -- tell me:
   - Are files reverted to that point?
   - Is the full conversation history preserved?
9. Test "Restore conversation only" -- tell me:
   - Is the conversation rewound?
   - Are current file contents left unchanged?
10. Test "Summarize from here" -- tell me:
    - Are messages before the point kept intact?
    - Are subsequent messages replaced with a compact summary?
    - Are files on disk unchanged?
11. Test with a bash command that deletes a file -- then try to rewind past it -- tell me if the plugin correctly warns that bash changes cannot be undone
