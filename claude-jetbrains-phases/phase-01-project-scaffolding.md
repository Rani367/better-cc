Use plan mode, 2026 web research, and multiple choice questions to complete this phase.

# Phase 1: Project Scaffolding

## Prerequisites

None. This is the first phase.

## Goal

Set up a working JetBrains plugin project from scratch using Kotlin and Gradle. The plugin should build, install into a sandboxed IDE, and show a "Claude Code" entry in the Tools menu. Nothing functional yet -- just a verified build pipeline.

## Requirements

1. Initialize a new project based on the IntelliJ Platform Plugin Template (https://github.com/JetBrains/intellij-platform-plugin-template)
2. Configure `build.gradle.kts` with:
   - Gradle plugin: `id("org.jetbrains.intellij.platform") version "2.10.5"` (this is the 2.x series plugin, NOT the old `org.jetbrains.intellij` 1.x)
   - Configuration block: `intellijPlatform { }` (NOT the old `intellij { }`)
   - Kotlin JVM target
   - Plugin ID: `com.claudecode.jetbrains`
   - Plugin name: "Claude Code"
   - Min platform version: 2024.3
   - Compatible with: IntelliJ IDEA, WebStorm, PyCharm, GoLand, Rider, PhpStorm, CLion, RustRover, Android Studio
3. Configure `gradle.properties` with plugin metadata (version 0.1.0, group, etc.)
4. Create `src/main/resources/META-INF/plugin.xml` with:
   - Plugin ID, name, vendor, description
   - An empty action group under Tools menu: "Claude Code" with a placeholder "Open Claude Code" action
5. Create a minimal `ClaudeCodePlugin.kt` plugin lifecycle class (can be empty/logging-only)
6. Create a placeholder `OpenClaudeAction.kt` that shows a notification balloon saying "Claude Code coming soon!"
7. Add placeholder SVG icons: `claude.svg` and `claude_dark.svg` in resources/icons/
8. Ensure the project builds cleanly with `./gradlew build`
9. Ensure `./gradlew runIde` launches a sandboxed IDE with the plugin installed

## File Structure

```
claude-code-jetbrains/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/...
├── src/main/
│   ├── kotlin/com/claudecode/jetbrains/
│   │   ├── ClaudeCodePlugin.kt
│   │   └── actions/OpenClaudeAction.kt
│   └── resources/
│       ├── META-INF/plugin.xml
│       └── icons/
│           ├── claude.svg
│           └── claude_dark.svg
└── README.md
```

## Manual Testing

After completing this phase, do the following and tell me the results:

1. Run `./gradlew build` -- tell me if it succeeds or fails (and the error if it fails)
2. Run `./gradlew runIde` -- tell me if a sandboxed IDE opens
3. In the sandboxed IDE, go to **Tools** menu -- tell me if "Claude Code" submenu appears
4. Click "Open Claude Code" -- tell me if a notification balloon appears saying "Claude Code coming soon!"
5. Go to **Settings > Plugins > Installed** -- tell me if "Claude Code" appears in the list with the correct icon
6. Tell me the exact Gradle and IntelliJ Platform Plugin versions used
