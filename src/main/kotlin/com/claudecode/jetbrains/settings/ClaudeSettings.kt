package com.claudecode.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class PermissionMode(val cliValue: String, val displayName: String) {
    DEFAULT("default", "Default"),
    PLAN("plan", "Plan"),
    ACCEPT_EDITS("acceptEdits", "Auto-accept Edits"),
    DONT_ASK("dontAsk", "Don't Ask"),
    BYPASS("bypassPermissions", "Bypass Permissions");

    companion object {
        fun fromCliValue(value: String): PermissionMode =
            entries.firstOrNull { it.cliValue == value } ?: DEFAULT
    }
}

/**
 * Extended thinking mode options. These map to CLI arguments
 * that control Claude's reasoning depth.
 */
enum class ThinkingMode(
    val displayName: String,
    val budgetTokens: Int?
) {
    NORMAL("Normal", null),
    THINK_HARD("Think Hard", 10000),
    ULTRATHINK("Ultrathink", 50000);

    companion object {
        fun fromName(name: String): ThinkingMode =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

@State(name = "ClaudeCodeSettings", storages = [Storage("claude-code.xml")])
@Service(Service.Level.APP)
class ClaudeSettings : PersistentStateComponent<ClaudeSettings.State> {
    data class State(
        var claudeCommand: String = "claude",
        var permissionMode: String = "default",
        var allowDangerouslySkipPermissions: Boolean = false,
        var selectedModel: String = "",
        var thinkingMode: String = "NORMAL"
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var claudeCommand: String
        get() = state.claudeCommand
        set(value) {
            state.claudeCommand = value
        }

    var permissionMode: PermissionMode
        get() = PermissionMode.fromCliValue(state.permissionMode)
        set(value) {
            state.permissionMode = value.cliValue
        }

    var allowDangerouslySkipPermissions: Boolean
        get() = state.allowDangerouslySkipPermissions
        set(value) {
            state.allowDangerouslySkipPermissions = value
        }

    var selectedModel: String
        get() = state.selectedModel
        set(value) {
            state.selectedModel = value
        }

    var thinkingMode: ThinkingMode
        get() = ThinkingMode.fromName(state.thinkingMode)
        set(value) {
            state.thinkingMode = value.name
        }

    companion object {
        fun getInstance(): ClaudeSettings =
            ApplicationManager.getApplication()
                .getService(ClaudeSettings::class.java)
    }
}
