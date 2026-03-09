Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 15: Toolbar & Status Bar

## Prerequisites

Phase 14 complete (context providers and editor actions work).

## Goal

Add a toolbar to the chat panel with model selector, extended thinking toggle, and cost/usage display. Add a status bar widget at the bottom of the IDE.

## Requirements

1. Create `ToolbarPanel.kt`:
   - Horizontal toolbar above the input area (or at the top of the chat panel)
   - Components:
     - **Model selector:** dropdown showing current model, click to switch
       - Options: "Default", "Sonnet", "Opus", "Haiku", or full model names
       - Passes `--model` flag to CLI when spawning process
       - Shows currently active model
     - **Extended thinking toggle:** button/dropdown with options
       - "Normal" (default)
       - "Think Hard" (extended thinking enabled)
       - "Ultrathink" (maximum thinking)
       - Visual indicator when thinking mode is active (e.g., brain icon highlighted)
     - **Cost/usage display:** small text showing token usage and cost for current session
       - Format: "1.2K tokens | $0.03" or similar
       - Updated from CLI result events (cost field)
       - Click to show detailed breakdown (input tokens, output tokens, cost)
     - **Session info:** current session name/ID (clickable to open session history)
2. Create `ContextIndicator.kt`:
   - Small bar at the bottom of the prompt box (above or below the permission mode selector)
   - Shows context window usage: "12% of context used" with a progress bar
   - Changes color as usage increases (green < 50%, yellow 50-80%, red > 80%)
   - Updated from CLI events
3. Add status bar widget:
   - Register via `statusBarWidgetFactory` in plugin.xml
   - Shows "Claude Code" with a spark icon in the IDE's bottom status bar
   - Click to open/focus the Claude Code panel
   - Show status: "Ready", "Thinking...", "Waiting for permission"
   - Color-coded: normal (default), blue (thinking), orange (permission pending)
4. Update `ClaudeProcess.kt`:
   - Extract token/cost data from `ResultEvent` and expose to toolbar
   - Pass model and thinking mode flags when spawning

## File Structure (new/modified files)

```
src/main/kotlin/com/claudecode/jetbrains/ui/chat/
├── ToolbarPanel.kt
└── ContextIndicator.kt

src/main/kotlin/com/claudecode/jetbrains/ui/
└── ClaudeStatusBarWidget.kt

src/main/kotlin/com/claudecode/jetbrains/cli/
└── ClaudeProcess.kt (modified)

src/main/resources/META-INF/plugin.xml (modified)
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew runIde`, open Claude Code panel
2. Tell me if the toolbar is visible at the top or above the input area
3. Click the model selector -- tell me:
   - Does a dropdown appear with model options?
   - Select a different model and send a message -- does Claude respond using the new model?
   - Does the selector update to show the current model?
4. Click the thinking mode toggle -- tell me:
   - Do the options (Normal, Think Hard, Ultrathink) appear?
   - Select "Think Hard" and send a complex prompt -- does Claude take longer/think more?
   - Is there a visual indicator that thinking mode is active?
5. After a conversation, check the cost/usage display -- tell me:
   - Does it show token count and cost?
   - Click it -- does a breakdown appear?
6. Check the context indicator -- tell me:
   - Does it show a percentage and progress bar?
   - After a long conversation, does it increase?
7. Check the bottom status bar -- tell me:
   - Does "Claude Code" appear with an icon?
   - Click it -- does it focus the Claude Code panel?
   - During a response, does it show "Thinking..."?
   - During a permission prompt, does it show "Waiting for permission"?
8. Test in both Darcula and Light theme -- tell me if all toolbar elements are visible and readable
