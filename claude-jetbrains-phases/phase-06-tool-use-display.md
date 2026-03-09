Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 6: Tool Use Display

## Prerequisites

Phase 5 complete (streaming and markdown rendering work).

## Goal

When Claude uses tools (reads files, writes files, runs bash commands), display them as collapsible cards in the chat instead of hiding them. The user should see what Claude is doing.

## Requirements

1. Create `ToolUseBlock.kt`:
   - A collapsible/expandable panel for each tool use event
   - Header shows: tool icon, tool name, brief summary (e.g., "Read src/main.kt", "Bash: npm test", "Write src/app.kt")
   - Body shows: full arguments and output (from `ToolResultEvent`)
   - Collapsed by default, click to expand
   - Different icons/colors per tool type:
     - Read: file icon, neutral color
     - Write/Edit: pencil icon, yellow/warning color
     - Bash: terminal icon, blue color
   - For Bash tool uses: show command in a monospace styled block, output below it
   - For Write tool uses: show file path and a preview of the content (truncated if very long)
   - For Read tool uses: show file path
2. Update `ChatToolWindow.kt`:
   - When a `ToolUseEvent` arrives during streaming, insert a `ToolUseBlock` into the message flow
   - When the corresponding `ToolResultEvent` arrives, update the block with the output
   - Tool use blocks appear inline between assistant message segments
   - Show a spinner/loading indicator on the tool block while waiting for the result
3. Update `MessageList.kt`:
   - Support mixed content: message bubbles and tool use blocks interleaved
   - Maintain correct ordering as events arrive

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/chat/
├── ToolUseBlock.kt
├── ChatToolWindow.kt (modified)
└── MessageList.kt (modified)

src/main/resources/icons/
├── tool-read.svg
├── tool-write.svg
└── tool-bash.svg
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde` and open the Claude Code panel
2. Send: "Read the build.gradle.kts file and tell me what plugins are configured"
3. Tell me:
   - Does a "Read build.gradle.kts" tool use block appear in the chat?
   - Is it collapsed by default?
   - Can you click to expand it?
   - Does the expanded view show the file content or a summary?
4. Send: "Create a new file called test-output.txt with the text 'hello world' in it"
5. Tell me:
   - Does a "Write test-output.txt" tool use block appear?
   - Does it show the file path and content preview?
6. Send: "Run the command 'echo hello && echo world'"
7. Tell me:
   - Does a "Bash: echo hello && echo world" tool use block appear?
   - Does the output show "hello\nworld"?
   - Is the command displayed in monospace?
8. Send a complex prompt that triggers multiple tool uses -- tell me if all of them appear as separate blocks in the correct order
9. While Claude is executing a tool, tell me if a loading spinner appears on the tool block before the result arrives
