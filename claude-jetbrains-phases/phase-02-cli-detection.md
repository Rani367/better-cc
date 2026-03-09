Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 2: CLI Detection & Health

## Prerequisites

Phase 1 complete (project builds and runs).

## Goal

Detect the Claude Code CLI binary on the system, verify authentication status, and show the result to the user. If the CLI is not found or not authenticated, show a helpful notification with instructions.

## Requirements

1. Create `ClaudeCliManager.kt` as a project-level service:
   - Search for `claude` binary in: PATH, common brew location, npm global (`npm root -g`), mise
   - Allow override via settings (custom CLI path)
   - Run `claude auth status` to check authentication (exit code 0 = authenticated, 1 = not)
   - Parse the JSON output of `claude auth status` for account info
   - Expose methods: `isCliAvailable()`, `isAuthenticated()`, `getCliPath()`, `getAuthStatus()`
   - Run detection asynchronously (don't block UI thread)
2. Create `ClaudeSettings.kt` as application-level persistent state:
   - Store `claudeCommand` (String, default: "claude")
   - Use IntelliJ's `PersistentStateComponent` with `@State` annotation
3. Register `ClaudeCliManager` as a `projectService` in plugin.xml
4. Register `ClaudeSettings` as an `applicationService` in plugin.xml
5. Create a `postStartupActivity` that:
   - Runs CLI detection on project open
   - If CLI not found: show error notification with "Claude Code CLI not found. Install it from https://claude.ai/install.sh" and a "Configure Path" action button
   - If CLI found but not authenticated: show warning notification "Claude Code CLI found but not authenticated. Run 'claude auth login' in your terminal."
   - If CLI found and authenticated: show info notification "Claude Code ready" (only on first detection, not every project open)
6. Update the "Open Claude Code" action to check CLI availability before proceeding

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/
├── cli/
│   └── ClaudeCliManager.kt
├── settings/
│   └── ClaudeSettings.kt
└── actions/OpenClaudeAction.kt (modified)

src/main/resources/META-INF/plugin.xml (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde` and open any project in the sandboxed IDE
2. **If Claude CLI is installed on your system:** Tell me if a "Claude Code ready" notification appears. Tell me what account info it detected.
3. **If Claude CLI is NOT installed:** Tell me if an error notification appears with the install link and "Configure Path" button
4. Go to the action search (Cmd+Shift+A / Ctrl+Shift+A) and type "Open Claude Code" -- tell me if clicking it shows an appropriate message about CLI status
5. Test the custom path setting: change the Claude command to something invalid (e.g., `/nonexistent/claude`), restart the IDE, and tell me if the "CLI not found" notification appears
6. Change it back to the correct path and tell me if detection works again
