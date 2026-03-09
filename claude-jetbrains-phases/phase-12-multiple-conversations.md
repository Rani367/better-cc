Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 12: Multiple Conversations

## Prerequisites

Phase 11 complete (session management works).

## Goal

Support multiple simultaneous conversations in separate editor tabs. Each tab has its own session, history, and CLI process. Tab indicators show pending permissions (blue) and completed tasks (orange).

## Requirements

1. Create `SessionTabManager.kt`:
   - Manage multiple active Claude Code sessions, each in its own editor tab
   - Each tab is a full chat panel with its own `ClaudeProcess` and session ID
   - Track tab state: active, waiting for permission, completed
   - Clean up processes when tabs are closed
2. Add "Open in New Tab" action:
   - Opens a new Claude Code conversation as an editor tab (not tool window)
   - Accessible via Command Palette: "Claude Code: Open in New Tab"
   - Keyboard shortcut: Cmd+Shift+Esc (Mac) / Ctrl+Shift+Esc (Windows/Linux)
3. Add "Open in New Window" action:
   - Opens Claude Code in a separate IDE window
   - Accessible via Command Palette: "Claude Code: Open in New Window"
4. Tab status indicators:
   - Blue dot on the tab icon when a permission prompt is pending (user needs to respond)
   - Orange dot when Claude finished a task while the tab was in the background
   - Dots clear when the tab is focused
   - Use IntelliJ's `FileEditorManager` and custom `FileEditor` for tab management
5. Tab titles:
   - Default: "Claude Code" with a number for multiple tabs ("Claude Code (2)", etc.)
   - Update to session title if session is renamed
6. Keep the tool window as the "primary" session:
   - Tool window remains the quick-access sidebar panel
   - Tabs are for additional parallel conversations
   - Both use the same underlying components

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/sessions/
├── SessionTabManager.kt
├── ClaudeEditorProvider.kt
└── ClaudeVirtualFile.kt

src/main/kotlin/com/claudecode/jetbrains/actions/
├── OpenInNewTabAction.kt
└── OpenInNewWindowAction.kt

src/main/resources/META-INF/plugin.xml (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open Claude Code panel in the sidebar
2. Start a conversation in the sidebar
3. Open Command Palette, run "Claude Code: Open in New Tab" -- tell me:
   - Does a new Claude Code tab open in the editor area?
   - Is it a separate conversation (new session)?
   - Can you type and get responses independently from the sidebar?
4. Open a second tab -- tell me if it gets a unique title ("Claude Code (2)" or similar)
5. In one tab, send a prompt that triggers a permission request. Switch to another tab while it's pending -- tell me:
   - Does the pending tab show a blue dot indicator?
   - When you switch back and approve, does the blue dot clear?
6. In a tab, send a long-running prompt. Switch to another tab. Wait for it to finish -- tell me:
   - Does the completed tab show an orange dot?
   - Does the dot clear when you focus that tab?
7. Close a tab -- tell me if the CLI process for that session is properly terminated
8. Try Cmd+Shift+Esc / Ctrl+Shift+Esc shortcut -- tell me if a new tab opens
9. Test "Open in New Window" -- tell me if Claude Code opens in a separate IDE window
