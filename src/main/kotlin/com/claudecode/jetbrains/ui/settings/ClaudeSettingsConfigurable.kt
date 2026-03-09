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
        c.setEnvironmentVariables(defaults.environmentVariables)
    }
}
