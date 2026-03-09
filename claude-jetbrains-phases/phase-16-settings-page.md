Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 16: Settings Page

## Prerequisites

Phase 15 complete (toolbar and status bar work).

## Goal

Create a full settings page under Settings > Tools > Claude Code with all configuration options matching the VS Code extension.

## Requirements

1. Create `ClaudeSettingsConfigurable.kt`:
   - Register as `applicationConfigurable` in plugin.xml
   - Parent: "tools" group
   - Display name: "Claude Code"
2. Create `ClaudeSettingsComponent.kt`:
   - Settings form with the following fields:

   | Setting | Type | Default | Description |
   |---------|------|---------|-------------|
   | Claude command | Text field with browse button | `claude` | Path to CLI executable |
   | Selected model | Dropdown | `default` | Model for new conversations |
   | Initial permission mode | Dropdown | `default` | Default: default, plan, acceptEdits, dontAsk, bypassPermissions |
   | Preferred location | Dropdown | `sidebar` | Where chat opens: sidebar or tab |
   | Auto-save | Checkbox | `true` | Auto-save files before Claude reads/writes |
   | Use Ctrl+Enter to send | Checkbox | `false` | Use Ctrl/Cmd+Enter instead of Enter |
   | Respect .gitignore | Checkbox | `true` | Exclude .gitignore patterns from file references |
   | Hide onboarding | Checkbox | `false` | Hide the onboarding walkthrough |
   | Environment variables | Key-value table | empty | Environment variables for the Claude process |

   - "Reset to Defaults" button
   - Each setting has a tooltip explaining what it does
3. Update `ClaudeSettings.kt`:
   - Add all settings fields with proper defaults
   - Implement `PersistentStateComponent` correctly
   - Emit change events so other components react to settings changes
4. Update components that depend on settings:
   - `InputPanel.kt`: respect `useCtrlEnterToSend` (change send key binding)
   - `ClaudeProcess.kt`: respect `claudeCommand`, `selectedModel`, `initialPermissionMode`, `environmentVariables`
   - `FileMentionPicker.kt`: respect `respectGitIgnore`
   - `ChatToolWindowFactory.kt`: respect `preferredLocation`
   - File write flow: respect `autosave`
5. Also accessible via `/` command menu: typing `/settings` or selecting "General Config" opens the settings page

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/settings/
├── ClaudeSettingsConfigurable.kt
└── ClaudeSettingsComponent.kt

src/main/kotlin/com/claudecode/jetbrains/settings/
└── ClaudeSettings.kt (modified)

src/main/resources/META-INF/plugin.xml (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`
2. Go to **Settings > Tools > Claude Code** -- tell me:
   - Does the settings page appear?
   - Are all settings listed with correct labels and tooltips?
   - Are default values populated?
3. Change "Claude command" to an invalid path, click Apply -- tell me if the CLI detection picks up the change (notification about CLI not found)
4. Change it back to the correct path -- tell me if it works again
5. Change "Use Ctrl+Enter to send" to true, click Apply -- go to Claude Code panel:
   - Press Enter -- tell me if it adds a new line instead of sending
   - Press Ctrl+Enter -- tell me if it sends the message
6. Change "Initial permission mode" to "Auto-accept" -- send a prompt that writes a file -- tell me if it skips the permission card
7. Change "Selected model" to a different model -- start a new conversation -- tell me if the new model is used
8. Add an environment variable in the table -- tell me if it persists after closing and reopening settings
9. Click "Reset to Defaults" -- tell me if all settings revert to defaults
10. Type `/settings` in the chat -- tell me if it opens the settings page
11. Close and reopen the IDE -- tell me if all settings persist
