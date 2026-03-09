Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 8: Diff Viewer Integration

## Prerequisites

Phase 7 complete (permission system works).

## Goal

When Claude writes or edits a file, show the proposed changes in IntelliJ's native side-by-side diff viewer. The user explicitly accepts or rejects the changes. All accepted changes are grouped as a single undo operation.

## Requirements

1. Create `DiffPreviewPanel.kt`:
   - When a `Write` or `Edit` tool use is approved via permission, intercept BEFORE applying
   - Read the original file content (or empty string if new file)
   - Show IntelliJ's native diff viewer using `DiffManager.getInstance().showDiff()` or `SimpleDiffRequest`
   - Side-by-side view: original (left) vs proposed (right)
   - Green highlighting for additions, red for deletions
   - For long diffs: show truncated preview with "Show Full Diff" button
2. Create `HunkActionPanel.kt`:
   - Overlay or toolbar on the diff viewer
   - Buttons: "Accept All Changes", "Reject All Changes"
   - After accept: apply the changes to the file
   - After reject: notify Claude that the edit was rejected
   - All accepted changes wrapped in `CommandProcessor.getInstance().executeCommand()` for single undo
3. Update the tool use flow:
   - When Claude's Write tool is permitted, don't immediately write the file
   - Instead, open the diff viewer
   - Only write the file after user clicks "Accept"
   - If user clicks "Reject", send rejection feedback to Claude via the CLI
4. Handle edge cases:
   - New file creation (no original to diff against -- show empty vs new content)
   - File deletion
   - Binary files (show warning, no diff)
   - Multiple file edits in sequence (queue them, show one at a time or stacked)

**CRITICAL RULE:** Changes are NEVER auto-applied. The diff viewer ALWAYS appears for file modifications. The only exception is if the user is in "Auto-accept" permission mode.

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/diff/
├── DiffPreviewPanel.kt
└── HunkActionPanel.kt

src/main/kotlin/com/claudecode/jetbrains/ui/chat/
└── ChatToolWindow.kt (modified)

src/main/kotlin/com/claudecode/jetbrains/cli/
└── ClaudeProcess.kt (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open a project with existing files
2. Send: "Add a comment to the top of build.gradle.kts saying '// Modified by Claude'"
3. After approving the permission, tell me:
   - Does a side-by-side diff viewer open?
   - Does the left side show the original file?
   - Does the right side show the proposed changes?
   - Are additions highlighted in green?
   - Are the "Accept All Changes" and "Reject All Changes" buttons visible?
4. Click "Accept All Changes" -- tell me:
   - Is the file actually modified on disk?
   - Can you undo the change with Ctrl+Z / Cmd+Z as a single operation?
5. Send another edit request, but this time click "Reject All Changes" -- tell me:
   - Is the file left unchanged?
   - Does Claude acknowledge the rejection in the chat?
6. Send: "Create a new file called diff-test.txt with 'hello world'" -- tell me if the diff viewer shows empty (left) vs new content (right)
7. Send a request that modifies multiple files -- tell me if each file gets its own diff viewer
8. Check that in "Auto-accept" mode, the diff viewer does NOT appear and changes are applied directly
