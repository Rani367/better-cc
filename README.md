# Claude Code JetBrains Plugin

A JetBrains IDE plugin that integrates Claude Code CLI, providing an AI-powered coding assistant directly within your IDE.

## Requirements

- JetBrains IDE 2024.3 or later (IntelliJ IDEA, WebStorm, PyCharm, etc.)
- Claude Code CLI installed (`npm install -g @anthropic-ai/claude-code`)
- Active Claude authentication (`claude auth login`)

## Building

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build
```

## Running (Development)

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runIde
```

## Project Structure

```
src/main/kotlin/com/claudecode/jetbrains/
├── ClaudeCodePlugin.kt          — Plugin startup activity
├── actions/
│   └── OpenClaudeAction.kt      — "Open Claude Code" action
├── cli/
│   └── ClaudeCliManager.kt      — CLI detection and auth checking
└── settings/
    └── ClaudeSettings.kt        — Persistent plugin settings
```
