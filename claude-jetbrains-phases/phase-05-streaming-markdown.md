Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 5: Streaming & Markdown Rendering

## Prerequisites

Phase 4 complete (basic chat panel works with final responses).

## Goal

Upgrade the chat panel to stream Claude's response token-by-token as it arrives, and render markdown with syntax-highlighted code blocks instead of plain text.

## Requirements

1. Update `ChatToolWindow.kt` to handle `AssistantPartialEvent` events:
   - As partial events arrive, update the current Claude message in real-time
   - Each partial event contains the cumulative text so far -- replace the message content, don't append
   - Show a typing indicator (blinking cursor or animated dots) while streaming
   - When the final `AssistantEvent` arrives, finalize the message
2. Create `CodeBlockRenderer.kt`:
   - Detect fenced code blocks (``` language ... ```) in markdown
   - Render code with syntax highlighting using IntelliJ's `EditorHighlighter` or a simpler approach with `JBColor`-based token coloring
   - Show language label in the code block header
   - Hover-to-copy button on each code block (copies code content to clipboard)
   - Code blocks should have a distinct background, rounded corners, and horizontal scroll for long lines
3. Update `MessageBubble.kt` to render markdown:
   - Bold, italic, inline code, links, lists, headings
   - Can use JCEF (embedded Chromium) for rich rendering, or a Swing-based markdown renderer
   - If using JCEF: create a lightweight HTML template with CSS that matches IntelliJ themes
   - If using Swing: use a library or manual parsing for basic markdown
   - Clickable file paths: detect paths like `src/main.kt` and make them clickable (open in editor)
4. Performance:
   - Partial event updates should not cause layout thrashing
   - Use `invokeLater` for UI updates from background threads
   - Buffer rapid partial events (e.g., update UI at most every 50ms)

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/chat/
├── ChatToolWindow.kt (modified)
├── MessageBubble.kt (modified)
└── CodeBlockRenderer.kt

src/main/resources/chat/ (if using JCEF)
├── chat.html
├── chat.css
└── chat.js
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde` and open the Claude Code panel
2. Send: "Write a hello world function in Python, then explain it step by step"
3. Tell me:
   - Does the response stream in token-by-token (text appearing progressively)?
   - Or does it wait and show everything at once?
   - Is there a visible typing/streaming indicator?
4. Check the code block:
   - Is the Python code in a distinct styled block with a background?
   - Is there a language label (e.g., "python") on the block?
   - Does hovering show a copy button?
   - Click the copy button -- does it copy the code to clipboard?
   - Is the code syntax-highlighted (keywords in different colors)?
5. Send: "Give me a bullet list of 5 items with **bold** and *italic* text"
   - Tell me if bold, italic, and list formatting render correctly
6. Send: "Show me an example with the file src/main/kotlin/com/claudecode/jetbrains/ClaudeCodePlugin.kt"
   - Tell me if the file path is clickable and opens the file
7. Send a prompt that produces a very long code block -- tell me if horizontal scroll works instead of awkward wrapping
8. Test in both Darcula (dark) and Light theme -- tell me if colors look correct in both
