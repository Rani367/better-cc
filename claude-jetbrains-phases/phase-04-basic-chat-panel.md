Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 4: Basic Chat Panel

## Prerequisites

Phase 3 complete (CLI process spawning and JSON parsing work).

## Goal

Create a dockable tool window with a basic chat interface. User can type a message, send it, and see Claude's response. No streaming yet -- just display the final response. This is the first visible GUI.

## Requirements

1. Create `ChatToolWindowFactory.kt`:
   - Register as a `toolWindow` in plugin.xml
   - Default anchor: RIGHT
   - Icon: claude.svg / claude_dark.svg
   - ID: "Claude Code"
2. Create `ChatToolWindow.kt`:
   - Split layout: message display area (top, scrollable) + input area (bottom)
   - Message display: use a `JBScrollPane` with a vertical `JPanel` that holds message components
   - Each message shows: sender label ("You" / "Claude"), message text, timestamp
   - User messages: left-aligned or distinct background color
   - Claude messages: different background color
   - Input area: `JBTextArea` with a "Send" button
   - Enter sends the message (no Shift+Enter multi-line yet, keep it simple for this phase)
   - Disable input while waiting for response
   - Show a "Thinking..." label while waiting
3. Create `InputPanel.kt`:
   - Text area + Send button in a horizontal layout
   - Clear input after sending
   - Focus management: auto-focus input when tool window opens
4. Create `MessageList.kt`:
   - Scrollable panel that holds message bubbles
   - Auto-scroll to bottom when new message arrives
   - Handle empty state: show "Start a conversation with Claude" placeholder
5. Create `MessageBubble.kt`:
   - Simple panel for a single message
   - Display sender, plain text content, timestamp
   - Different styling for user vs assistant messages
   - Use JBColor for theme-aware colors
6. Wire it all together:
   - When user sends a message, spawn a `ClaudeProcess` with the prompt
   - Collect all events until `ResultEvent`
   - Display the final `AssistantEvent` content as Claude's response
   - Handle errors: show error message in chat if process fails
7. Update "Open Claude Code" action to focus the tool window instead of showing a notification
8. Add keyboard shortcut: Cmd+Esc (Mac) / Ctrl+Esc to toggle focus between editor and chat input

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/
├── ui/chat/
│   ├── ChatToolWindowFactory.kt
│   ├── ChatToolWindow.kt
│   ├── InputPanel.kt
│   ├── MessageList.kt
│   └── MessageBubble.kt
├── actions/OpenClaudeAction.kt (modified)

src/main/resources/META-INF/plugin.xml (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde` and open a project
2. Tell me if the "Claude Code" tool window tab appears on the right side of the IDE
3. Click it -- tell me if the chat panel opens with the empty state placeholder
4. Type "Hello, what is your name?" and press Enter (or click Send)
5. Tell me:
   - Does the input clear after sending?
   - Does a "Thinking..." indicator appear?
   - Does Claude's response appear in the chat?
   - Are user and Claude messages visually distinct?
   - Does the chat auto-scroll to the latest message?
6. Send a second message -- tell me if both messages and responses are visible in the history
7. Try Cmd+Esc (Mac) or Ctrl+Esc -- tell me if focus toggles between the editor and chat input
8. Try sending a message with the CLI not available -- tell me if an error message appears in the chat
9. Resize the tool window to be very narrow -- tell me if the content still looks reasonable
