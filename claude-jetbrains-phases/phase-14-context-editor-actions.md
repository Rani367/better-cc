Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 14: Context Providers & Editor Actions

## Prerequisites

Phase 13 complete (checkpoints work).

## Goal

Share IDE diagnostics (errors, warnings) with Claude automatically, and add right-click context menu actions for common tasks: ask about code, fix errors, refactor, generate tests, explain, and document.

## Requirements

1. Create `DiagnosticsProvider.kt`:
   - Subscribe to `DaemonCodeAnalyzer` for real-time error/warning updates
   - Collect diagnostics for the current file and project
   - Format diagnostics as structured context: file path, line number, severity, message
   - Automatically include relevant diagnostics when the user asks Claude to fix something
   - Share compiler output when available
2. Create `FileContextProvider.kt`:
   - Track the currently open file in the editor
   - Provide file path, language, content to Claude as context
   - Update when the user switches files
3. Create `ProjectContextProvider.kt`:
   - Auto-detect project type (language, framework, build tool)
   - Read `.claude/settings.json` and `.claude/commands/` if present
   - Respect `.gitignore` and `.claudeignore` for context boundaries
   - Provide project structure summary
4. Create right-click context menu actions (registered as `intentionAction` and/or editor popup menu items):
   - **"Ask Claude about this"** (`AskClaudeAction.kt`):
     - Available on any text selection
     - Opens Claude Code panel with the selection as context
     - Prompts: "What does this code do?" or lets user type their question
   - **"Fix this error with Claude"** (`FixErrorAction.kt`):
     - Available in error gutter (red squiggly lines)
     - Sends the error message, file context, and surrounding code to Claude
     - Asks Claude to fix the error
   - **"Refactor with Claude"** (`RefactorAction.kt`):
     - Available on any selection
     - Asks Claude to refactor the selected code
   - **"Generate tests for this"** (`GenerateTestsAction.kt`):
     - Available on functions/classes
     - Asks Claude to generate unit tests
   - **"Explain this code"** (`ExplainCodeAction.kt`):
     - Available on any selection
     - Asks Claude for an explanation
   - **"Add documentation"** (`DocumentCodeAction.kt`):
     - Available on functions/classes
     - Asks Claude to add docstrings/JSDoc/KDoc
5. All actions open the Claude Code panel (or focus it if already open) and pre-fill the prompt with the relevant context

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/context/
├── DiagnosticsProvider.kt
├── FileContextProvider.kt
└── ProjectContextProvider.kt

src/main/kotlin/com/claudecode/jetbrains/actions/
├── AskClaudeAction.kt
├── FixErrorAction.kt
├── RefactorAction.kt
├── GenerateTestsAction.kt
├── ExplainCodeAction.kt
└── DocumentCodeAction.kt

src/main/resources/META-INF/plugin.xml (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open a project with some code files
2. Open a file with syntax errors (or introduce one intentionally)
3. Right-click on the error squiggly -- tell me:
   - Does "Fix this error with Claude" appear in the context menu?
   - Click it -- does the Claude panel open with the error context pre-filled?
   - Does Claude receive the error message and file context?
   - Does it suggest a fix?
4. Select some code, right-click -- tell me if these actions appear:
   - "Ask Claude about this"
   - "Refactor with Claude"
   - "Generate tests for this"
   - "Explain this code"
   - "Add documentation"
5. Click "Explain this code" -- tell me:
   - Does Claude receive the selected code?
   - Does it provide a relevant explanation?
6. Click "Generate tests for this" on a function -- tell me:
   - Does Claude generate relevant unit tests?
   - Are they in the appropriate testing framework for the project?
7. Click "Add documentation" on a function -- tell me:
   - Does Claude generate appropriate documentation comments?
8. Introduce a compilation error, then send a message in Claude chat asking to "fix the errors in this file" -- tell me if Claude automatically receives the diagnostic information (error messages, line numbers)
9. Switch between files in the editor -- tell me if the context provider tracks the active file correctly
