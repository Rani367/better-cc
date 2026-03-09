# CLAUDE.md

**Keep this file lean.** Never add information here that can be found by searching the codebase (directory structure, code patterns, protocol formats, API usage, etc.). This file is only for things Claude cannot discover: rules, constraints, gotchas, and project status.

**Update this file as you work.** When you complete a phase, mark it done below. When you learn a hard-won lesson (a gotcha, a constraint), add it. When something listed here becomes wrong, fix it.

**Follow phase instructions carefully.** Each phase file starts with top-level instructions (e.g., "Use plan mode", "Use web research", "Ask user for decision"). Always read and follow those before proceeding.

## Build Phases

Implementation phases are in `claude-jetbrains-phases/`. Follow them in order. Full spec: `claude-jetbrains-plugin-spec.md`.

- `phase-01-project-scaffolding.md` — **COMPLETE**
- `phase-02-cli-detection.md` — **COMPLETE**
- `phase-03-cli-process-json.md` — **COMPLETE**
- `phase-04-basic-chat-panel.md` — **COMPLETE**
- `phase-05-streaming-markdown.md` — **COMPLETE**
- `phase-06-tool-use-display.md` — **COMPLETE**
- `phase-07-permission-system.md` — **COMPLETE**
- `phase-08-diff-viewer.md` — **COMPLETE**
- `phase-09-slash-commands.md` — **COMPLETE**
- `phase-10-file-mentions.md` — **COMPLETE**
- `phase-11-session-management.md`
- `phase-12-multiple-conversations.md`
- `phase-13-checkpoints-rewind.md`
- `phase-14-context-editor-actions.md`
- `phase-15-toolbar-statusbar.md` — **COMPLETE**
- `phase-16-settings-page.md`
- `phase-17-plugin-mcp-management.md`
- `phase-18-theming-polish.md`

## Rules That Will Bite You

### Gradle
- Plugin is `org.jetbrains.intellij.platform` — NOT the old `org.jetbrains.intellij`
- Config block is `intellijPlatform { }` — NOT `intellij { }`
- `instrumentationTools()` is deprecated and not needed — don't add it
- Do NOT change `pluginSinceBuild`, `pluginUntilBuild`, plugin IDs, or Gradle wrapper version without explicit instruction
- Do NOT add dependencies without explicit instruction

### Threading (CRITICAL — violations cause freezes/crashes)
- Never block EDT with IO, network, heavy computation, or `Thread.sleep`
- All Swing UI updates must happen on EDT (`invokeLater {}`)
- PSI/index access requires read action (`ReadAction.nonBlocking {}`)
- PSI mutation requires write command (`WriteCommandAction.runWriteCommandAction`)
- Background work: `Task.Backgroundable` or coroutines with `Dispatchers.Default`, never raw `Thread`
- Never use `invokeAndWait` unless strictly necessary

### Lifecycle (CRITICAL — violations cause leaks/corruption)
- Never cache `Project`, `PsiElement`, `Editor`, or `Document` in static fields or singletons
- Always check `project.isDisposed` before long operations
- Always tie listeners to a parent `Disposable`
- Use `@Service` mechanism, not custom singletons

### IntelliJ Platform API
- Use `VirtualFile`/VFS for project file operations, not `java.io.File`
- Use PSI APIs for code analysis, not string parsing
- Do NOT use `@ApiStatus.Internal` or `internal` package APIs
- Do NOT use APIs introduced after 2024.3 (minimum platform version)
- Do NOT use reflection on IntelliJ classes

### Kotlin Style
- `suspend` + coroutines for async. Never `runBlocking` on EDT.
- No wildcard imports. Max 120 char lines. Prefer `val` over `var`.

## Requires Caution / Human Review
- `plugin.xml` — extension point registrations, IDs, version ranges
- `build.gradle.kts` — dependencies, platform version, plugin configuration
- `ClaudeProcess.kt` — CLI spawning and JSON protocol (must match real CLI behavior)
- `PermissionMcpServer.kt` — security-critical permission handling
- `CheckpointManager.kt` — file snapshot/restore logic

## Do NOT
- Create files outside the project structure without explicit instruction
- Run `rm` or destructive commands on project files
- Use deprecated IntelliJ APIs when a replacement exists
- Hard-code absolute paths or environment-specific values
