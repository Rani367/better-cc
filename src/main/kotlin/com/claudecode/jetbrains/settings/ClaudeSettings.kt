package com.claudecode.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic

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

enum class PreferredLocation(val displayName: String) {
    SIDEBAR("Sidebar"),
    TAB("Editor Tab");

    companion object {
        fun fromName(name: String): PreferredLocation =
            entries.firstOrNull { it.name == name } ?: SIDEBAR
    }
}

/**
 * Listener interface for settings change notifications.
 */
interface ClaudeSettingsChangeListener {
    fun settingsChanged(settings: ClaudeSettings)
}

@State(name = "ClaudeCodeSettings", storages = [Storage("claude-code.xml")])
@Service(Service.Level.APP)
class ClaudeSettings : PersistentStateComponent<ClaudeSettings.State> {
    data class State(
        var claudeCommand: String = "claude",
        var permissionMode: String = "default",
        var allowDangerouslySkipPermissions: Boolean = false,
        var selectedModel: String = "",
        var preferredLocation: String = "SIDEBAR",
        var autoSave: Boolean = true,
        var useCtrlEnterToSend: Boolean = false,
        var respectGitIgnore: Boolean = true,
        var hideOnboarding: Boolean = false,
        var environmentVariables: MutableMap<String, String> = mutableMapOf()
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

    var preferredLocation: PreferredLocation
        get() = PreferredLocation.fromName(state.preferredLocation)
        set(value) {
            state.preferredLocation = value.name
        }

    var autoSave: Boolean
        get() = state.autoSave
        set(value) {
            state.autoSave = value
        }

    var useCtrlEnterToSend: Boolean
        get() = state.useCtrlEnterToSend
        set(value) {
            state.useCtrlEnterToSend = value
        }

    var respectGitIgnore: Boolean
        get() = state.respectGitIgnore
        set(value) {
            state.respectGitIgnore = value
        }

    var hideOnboarding: Boolean
        get() = state.hideOnboarding
        set(value) {
            state.hideOnboarding = value
        }

    var environmentVariables: MutableMap<String, String>
        get() = state.environmentVariables
        set(value) {
            state.environmentVariables = value
        }

    /**
     * Fires a change notification so that other components can react.
     */
    fun fireSettingsChanged() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(SETTINGS_CHANGED)
            .settingsChanged(this)
    }

    /**
     * Resets all settings to their defaults.
     */
    fun resetToDefaults() {
        state = State()
    }

    companion object {
        val SETTINGS_CHANGED: Topic<ClaudeSettingsChangeListener> = Topic.create(
            "ClaudeCodeSettingsChanged",
            ClaudeSettingsChangeListener::class.java
        )

        fun getInstance(): ClaudeSettings =
            ApplicationManager.getApplication().getService(ClaudeSettings::class.java)
    }
}
