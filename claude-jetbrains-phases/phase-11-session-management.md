Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 11: Session Management

## Prerequisites

Phase 10 complete (file mentions work).

## Goal

Implement conversation history. Users can browse past sessions grouped by time, search by keyword, resume any session, and rename or delete sessions.

## Requirements

1. Create `SessionHistoryDialog.kt`:
   - Dropdown at the top of the chat panel (click to open)
   - Shows header: "Past Conversations"
   - Sessions grouped by time: "Today", "Yesterday", "Last 7 Days", "Older"
   - Each session shows: title (first prompt or custom name), timestamp, model used
   - Search bar at top: filter sessions by keyword
   - Click a session to resume it (uses `--resume <session-id>`)
   - Hover actions on each session:
     - Rename (pencil icon) -- inline edit of session title
     - Remove (trash icon) -- delete from history with confirmation
   - "New Conversation" button at the top
2. Update `SessionManager.kt`:
   - Persist session metadata to disk (JSON file in plugin data directory)
   - Store: session ID, title, first prompt, start time, last active time, model, project path
   - Load sessions on startup
   - Support rename and delete operations
   - Auto-title sessions based on the first user prompt (truncated to ~50 chars)
3. Update `ChatToolWindow.kt`:
   - Add session dropdown trigger at the top of the panel
   - When resuming a session, load the conversation history from CLI (`--resume`)
   - Show "Resumed session" indicator when continuing a past conversation
   - "New Conversation" clears the chat and starts with a fresh session ID
4. Handle session state:
   - Save session metadata when conversation starts and on each message
   - Update "last active" timestamp on each interaction
   - Clean up sessions older than 30 days (configurable, or match CLI behavior)

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/sessions/
└── SessionHistoryDialog.kt

src/main/kotlin/com/claudecode/jetbrains/cli/
└── SessionManager.kt (modified)

src/main/kotlin/com/claudecode/jetbrains/ui/chat/
└── ChatToolWindow.kt (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open Claude Code panel
2. Have a conversation (send 2-3 messages)
3. Click the session dropdown at the top -- tell me:
   - Does it open?
   - Does the current session appear in the list under "Today"?
   - Does it show the first prompt as the title?
4. Start a new conversation (click "New Conversation")
5. Have another short conversation, then open the dropdown again -- tell me if both sessions appear
6. Click the first session -- tell me:
   - Does it resume with the previous conversation history?
   - Does a "Resumed session" indicator appear?
   - Can you continue the conversation?
7. Hover over a session in the list -- tell me if rename and delete icons appear
8. Click rename on a session, change the title -- tell me if it persists after closing and reopening the dropdown
9. Click delete on a session, confirm -- tell me if it's removed from the list
10. Type a search term in the search bar -- tell me if sessions filter correctly
11. Close and reopen the IDE -- tell me if sessions persist across restarts
