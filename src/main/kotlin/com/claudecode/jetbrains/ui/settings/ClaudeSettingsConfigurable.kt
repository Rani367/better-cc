package com.claudecode.jetbrains.ui.settings

import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.settings.PermissionMode
import com.claudecode.jetbrains.settings.PreferredLocation
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

/**
 * Configurable entry for Settings > Tools > Claude Code.
 */
class ClaudeSettingsConfigurable : Configurable {

    private var component: ClaudeSettingsComponent? = null

    override fun getDisplayName(): String = "Claude Code"

    override fun createComponent(): JComponent {
        val c = ClaudeSettingsComponent()
        c.resetButton.addActionListener {
            resetFormToDefaults(c)
        }
        component = c
        reset()
        return c.panel
    }

    override fun isModified(): Boolean {
        val c = component ?: return false
        val s = ClaudeSettings.getInstance()
        return c.claudeCommandField.text != s.claudeCommand ||
            c.selectedModelField.text != s.selectedModel ||
            c.permissionModeCombo.selectedItem != s.permissionMode ||
            c.preferredLocationCombo.selectedItem != s.preferredLocation ||
            c.autoSaveCheckbox.isSelected != s.autoSave ||
            c.useCtrlEnterCheckbox.isSelected != s.useCtrlEnterToSend ||
            c.respectGitIgnoreCheckbox.isSelected != s.respectGitIgnore ||
            c.hideOnboardingCheckbox.isSelected != s.hideOnboarding ||
            c.enableAnimationsCheckbox.isSelected != s.enableAnimations ||
            c.useTerminalCheckbox.isSelected != s.useTerminal ||
            c.disableLoginPromptCheckbox.isSelected != s.disableLoginPrompt ||
            c.enableNewConversationShortcutCheckbox.isSelected !=
                s.enableNewConversationShortcut ||
            c.usePythonEnvironmentCheckbox.isSelected !=
                s.usePythonEnvironment ||
            c.claudeProcessWrapperField.text != s.claudeProcessWrapper ||
            c.chatFontFamilyField.text != s.chatFontFamily ||
            (c.chatFontSizeField.text.toIntOrNull() ?: 13) !=
                s.chatFontSize ||
            c.editorFontFamilyField.text != s.editorFontFamily ||
            (c.editorFontSizeField.text.toIntOrNull() ?: 12) !=
                s.editorFontSize ||
            c.enableIdeContextCheckbox.isSelected !=
                s.enableIdeContext ||
            c.getEnvironmentVariables() != s.environmentVariables.toMap()
    }

    override fun apply() {
        val c = component ?: return
        val s = ClaudeSettings.getInstance()

        s.claudeCommand = c.claudeCommandField.text
        s.selectedModel = c.selectedModelField.text
        s.permissionMode = c.permissionModeCombo.selectedItem
            as? PermissionMode ?: PermissionMode.DEFAULT
        s.preferredLocation = c.preferredLocationCombo.selectedItem
            as? PreferredLocation ?: PreferredLocation.SIDEBAR
        s.autoSave = c.autoSaveCheckbox.isSelected
        s.useCtrlEnterToSend = c.useCtrlEnterCheckbox.isSelected
        s.respectGitIgnore = c.respectGitIgnoreCheckbox.isSelected
        s.hideOnboarding = c.hideOnboardingCheckbox.isSelected
        s.enableAnimations = c.enableAnimationsCheckbox.isSelected
        s.useTerminal = c.useTerminalCheckbox.isSelected
        s.disableLoginPrompt = c.disableLoginPromptCheckbox.isSelected
        s.enableNewConversationShortcut =
            c.enableNewConversationShortcutCheckbox.isSelected
        s.usePythonEnvironment = c.usePythonEnvironmentCheckbox.isSelected
        s.claudeProcessWrapper = c.claudeProcessWrapperField.text
        s.chatFontFamily = c.chatFontFamilyField.text
        s.chatFontSize = c.chatFontSizeField.text.toIntOrNull() ?: 13
        s.editorFontFamily = c.editorFontFamilyField.text
        s.editorFontSize = c.editorFontSizeField.text.toIntOrNull() ?: 12
        s.enableIdeContext = c.enableIdeContextCheckbox.isSelected
        s.environmentVariables = c.getEnvironmentVariables().toMutableMap()

        s.fireSettingsChanged()
    }

    override fun reset() {
        val c = component ?: return
        val s = ClaudeSettings.getInstance()

        c.claudeCommandField.text = s.claudeCommand
        c.selectedModelField.text = s.selectedModel
        c.permissionModeCombo.selectedItem = s.permissionMode
        c.preferredLocationCombo.selectedItem = s.preferredLocation
        c.autoSaveCheckbox.isSelected = s.autoSave
        c.useCtrlEnterCheckbox.isSelected = s.useCtrlEnterToSend
        c.respectGitIgnoreCheckbox.isSelected = s.respectGitIgnore
        c.hideOnboardingCheckbox.isSelected = s.hideOnboarding
        c.enableAnimationsCheckbox.isSelected = s.enableAnimations
        c.useTerminalCheckbox.isSelected = s.useTerminal
        c.disableLoginPromptCheckbox.isSelected = s.disableLoginPrompt
        c.enableNewConversationShortcutCheckbox.isSelected =
            s.enableNewConversationShortcut
        c.usePythonEnvironmentCheckbox.isSelected = s.usePythonEnvironment
        c.enableIdeContextCheckbox.isSelected = s.enableIdeContext
        c.claudeProcessWrapperField.text = s.claudeProcessWrapper
        c.chatFontFamilyField.text = s.chatFontFamily
        c.chatFontSizeField.text = s.chatFontSize.toString()
        c.editorFontFamilyField.text = s.editorFontFamily
        c.editorFontSizeField.text = s.editorFontSize.toString()
        c.setEnvironmentVariables(s.environmentVariables)
    }

    override fun disposeUIResources() {
        component = null
    }

    private fun resetFormToDefaults(c: ClaudeSettingsComponent) {
        val defaults = ClaudeSettings.State()
        c.claudeCommandField.text = defaults.claudeCommand
        c.selectedModelField.text = defaults.selectedModel
        c.permissionModeCombo.selectedItem =
            PermissionMode.fromCliValue(defaults.permissionMode)
        c.preferredLocationCombo.selectedItem =
            PreferredLocation.fromName(defaults.preferredLocation)
        c.autoSaveCheckbox.isSelected = defaults.autoSave
        c.useCtrlEnterCheckbox.isSelected = defaults.useCtrlEnterToSend
        c.respectGitIgnoreCheckbox.isSelected = defaults.respectGitIgnore
        c.hideOnboardingCheckbox.isSelected = defaults.hideOnboarding
        c.enableAnimationsCheckbox.isSelected = defaults.enableAnimations
        c.useTerminalCheckbox.isSelected = defaults.useTerminal
        c.disableLoginPromptCheckbox.isSelected =
            defaults.disableLoginPrompt
        c.enableNewConversationShortcutCheckbox.isSelected =
            defaults.enableNewConversationShortcut
        c.usePythonEnvironmentCheckbox.isSelected =
            defaults.usePythonEnvironment
        c.enableIdeContextCheckbox.isSelected = defaults.enableIdeContext
        c.claudeProcessWrapperField.text = defaults.claudeProcessWrapper
        c.chatFontFamilyField.text = defaults.chatFontFamily
        c.chatFontSizeField.text = defaults.chatFontSize.toString()
        c.editorFontFamilyField.text = defaults.editorFontFamily
        c.editorFontSizeField.text = defaults.editorFontSize.toString()
        c.setEnvironmentVariables(defaults.environmentVariables)
    }
}
