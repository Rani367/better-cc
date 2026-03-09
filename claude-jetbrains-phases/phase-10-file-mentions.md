Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 10: @ File Mentions

## Prerequisites

Phase 9 complete (slash commands work).

## Goal

When the user types `@` in the prompt box, show a fuzzy file picker that lets them reference project files by name. Support line ranges (`@file.ts#5-10`) and folder references (`@src/components/`).

## Requirements

1. Create `FileMentionPicker.kt`:
   - Popup triggered when user types `@` in the input
   - Shows a searchable list of project files
   - Fuzzy matching: typing `@auth` matches `auth.js`, `AuthService.ts`, `src/auth/index.ts`, etc.
   - Respect `.gitignore` (if `respectGitIgnore` setting is true)
   - Show file icons from IntelliJ's file type system
   - Show relative path from project root
   - Folder support: trailing slash (`@src/components/`) references a directory
   - Navigate with arrow keys, select with Enter or click
   - On selection: insert `@path/to/file` into the input text
   - Escape dismisses the popup
2. Support line ranges:
   - After selecting a file, user can type `#5-10` to reference specific lines
   - Format: `@file.ts#5-10` (lines 5 through 10)
   - Single line: `@file.ts#5`
   - Open-ended: `@file.ts#5-` (line 5 onward)
3. Add keyboard shortcut for inserting file reference from current selection:
   - Cmd+Option+K (Mac) / Alt+Ctrl+K (Windows/Linux)
   - Inserts `@current-file.kt#selectedStartLine-selectedEndLine` into the prompt box
   - Matches the existing JetBrains plugin behavior
4. Selection context indicator:
   - When text is selected in the editor, show an indicator in the prompt box footer: "3 lines selected in Main.kt"
   - Toggle button (eye/eye-slash icon) to hide selection from Claude
   - Selected text is automatically shared with Claude as context (unless hidden)
5. Update `InputPanel.kt`:
   - Detect `@` character and trigger `FileMentionPicker`
   - Handle the keyboard shortcut for file reference insertion

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/commands/
└── FileMentionPicker.kt

src/main/kotlin/com/claudecode/jetbrains/ui/chat/
├── InputPanel.kt (modified)
└── ChatToolWindow.kt (modified)

src/main/kotlin/com/claudecode/jetbrains/context/
└── SelectionContextProvider.kt

src/main/kotlin/com/claudecode/jetbrains/actions/
└── InsertFileRefAction.kt
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open a project with multiple files
2. In the Claude Code panel, type `@` -- tell me:
   - Does a file picker popup appear?
   - Does it show project files with icons and relative paths?
3. Type `@build` -- tell me if it fuzzy-matches to `build.gradle.kts` (and any other matching files)
4. Select a file -- tell me if `@build.gradle.kts` (or similar) is inserted into the input
5. Type `@build.gradle.kts#1-5` and send a prompt like "explain these lines" -- tell me if Claude understands the line range reference
6. Type `@src/` (with trailing slash) -- tell me if it shows the src directory contents
7. Open a file in the editor, select some lines, then press Cmd+Option+K / Alt+Ctrl+K -- tell me:
   - Does a file reference with line numbers appear in the prompt box?
   - Is the format correct (e.g., `@src/Main.kt#3-7`)?
8. With text selected in the editor, tell me if the prompt box footer shows "X lines selected in filename"
9. Click the eye/eye-slash toggle -- tell me if the selection indicator changes
10. Send a message with a file mention -- tell me if Claude's response shows awareness of that file's content
