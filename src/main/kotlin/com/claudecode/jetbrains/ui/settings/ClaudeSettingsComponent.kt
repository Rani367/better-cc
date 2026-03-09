package com.claudecode.jetbrains.ui.settings

import com.claudecode.jetbrains.settings.PermissionMode
import com.claudecode.jetbrains.settings.PreferredLocation
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultCellEditor
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

/**
 * The settings form component containing all Claude Code settings fields.
 */
class ClaudeSettingsComponent {

    val panel: JPanel

    // ── Fields ───────────────────────────────────────────────────

    val claudeCommandField = TextFieldWithBrowseButton().apply {
        @Suppress("DEPRECATION")
        addBrowseFolderListener(
            "Select Claude CLI Executable",
            "Choose the path to the Claude CLI executable",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
        toolTipText = "Path to the Claude CLI executable. " +
            "Default: 'claude' (found on PATH)."
    }

    val selectedModelField = JBTextField().apply {
        toolTipText = "Model to use for new conversations. " +
            "Leave blank to use the default model."
        emptyText.text = "default"
    }

    val permissionModeCombo = ComboBox(
        DefaultComboBoxModel(PermissionMode.entries.toTypedArray())
    ).apply {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
                )
                text = (value as? PermissionMode)?.displayName
                    ?: value.toString()
                return this
            }
        }
        toolTipText = "Initial permission mode for new sessions. " +
            "'Default' asks for permission on each action."
    }

    val preferredLocationCombo = ComboBox(
        DefaultComboBoxModel(PreferredLocation.entries.toTypedArray())
    ).apply {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
                )
                text = (value as? PreferredLocation)?.displayName
                    ?: value.toString()
                return this
            }
        }
        toolTipText = "Where the Claude Code panel opens: " +
            "as a sidebar tool window or as an editor tab."
    }

    val autoSaveCheckbox = JBCheckBox("Auto-save files").apply {
        toolTipText = "Automatically save files before Claude " +
            "reads or writes them."
    }

    val useCtrlEnterCheckbox = JBCheckBox(
        "Use Ctrl+Enter to send (Enter inserts newline)"
    ).apply {
        toolTipText = "When enabled, press Ctrl+Enter (Cmd+Enter " +
            "on macOS) to send messages. Enter inserts a newline."
    }

    val respectGitIgnoreCheckbox = JBCheckBox(
        "Respect .gitignore"
    ).apply {
        toolTipText = "Exclude files matching .gitignore patterns " +
            "from file references and mentions."
    }

    val hideOnboardingCheckbox = JBCheckBox(
        "Hide onboarding walkthrough"
    ).apply {
        toolTipText = "Hide the onboarding walkthrough shown to " +
            "new users."
    }

    // Environment variables table
    private val envTableModel = object : DefaultTableModel(
        arrayOf("Variable", "Value"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }

    val envTable = JBTable(envTableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        tableHeader.reorderingAllowed = false
        columnModel.getColumn(0).cellEditor = DefaultCellEditor(
            JBTextField()
        )
        columnModel.getColumn(1).cellEditor = DefaultCellEditor(
            JBTextField()
        )
        toolTipText = "Environment variables passed to the Claude " +
            "CLI process."
    }

    private val envTablePanel: JPanel

    val resetButton = JButton("Reset to Defaults").apply {
        toolTipText = "Reset all settings to their default values."
    }

    init {
        // Build the environment variables table with add/remove toolbar
        envTablePanel = ToolbarDecorator.createDecorator(envTable)
            .setAddAction {
                envTableModel.addRow(arrayOf("", ""))
                val row = envTableModel.rowCount - 1
                envTable.editCellAt(row, 0)
                envTable.transferFocus()
            }
            .setRemoveAction {
                val selected = envTable.selectedRow
                if (selected >= 0) {
                    if (envTable.isEditing) {
                        envTable.cellEditor.stopCellEditing()
                    }
                    envTableModel.removeRow(selected)
                }
            }
            .disableUpDownActions()
            .createPanel().apply {
                preferredSize = Dimension(0, 150)
            }

        panel = buildPanel()
    }

    private fun buildPanel(): JPanel {
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
        }

        var row = 0

        // Claude command
        addLabeledField(
            formPanel, gbc, row++,
            "Claude command:",
            claudeCommandField
        )

        // Selected model
        addLabeledField(
            formPanel, gbc, row++,
            "Selected model:",
            selectedModelField
        )

        // Permission mode
        addLabeledField(
            formPanel, gbc, row++,
            "Initial permission mode:",
            permissionModeCombo
        )

        // Preferred location
        addLabeledField(
            formPanel, gbc, row++,
            "Preferred location:",
            preferredLocationCombo
        )

        // Checkboxes — span both columns
        addCheckbox(formPanel, gbc, row++, autoSaveCheckbox)
        addCheckbox(formPanel, gbc, row++, useCtrlEnterCheckbox)
        addCheckbox(formPanel, gbc, row++, respectGitIgnoreCheckbox)
        addCheckbox(formPanel, gbc, row++, hideOnboardingCheckbox)

        // Environment variables label
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.insets = Insets(12, 4, 4, 4)
        formPanel.add(
            JBLabel("Environment variables:").apply {
                toolTipText = "Environment variables passed to the " +
                    "Claude CLI process."
            },
            gbc
        )
        row++

        // Environment variables table
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = Insets(2, 4, 4, 4)
        formPanel.add(envTablePanel, gbc)
        row++

        // Reset button at the bottom
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = Insets(12, 4, 4, 4)
        formPanel.add(resetButton, gbc)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(formPanel, BorderLayout.NORTH)
        // Let the env table expand vertically
        wrapper.add(JPanel(), BorderLayout.CENTER)
        wrapper.border = JBUI.Borders.empty(8)
        return wrapper
    }

    private fun addLabeledField(
        panel: JPanel,
        gbc: GridBagConstraints,
        row: Int,
        labelText: String,
        field: javax.swing.JComponent
    ) {
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.insets = Insets(4, 4, 4, 8)
        panel.add(JBLabel(labelText), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(4, 0, 4, 4)
        panel.add(field, gbc)
    }

    private fun addCheckbox(
        panel: JPanel,
        gbc: GridBagConstraints,
        row: Int,
        checkbox: JBCheckBox
    ) {
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(4, 4, 4, 4)
        panel.add(checkbox, gbc)
    }

    // ── Environment Variables Accessors ──────────────────────────

    fun getEnvironmentVariables(): Map<String, String> {
        // Stop any active editing first
        if (envTable.isEditing) {
            envTable.cellEditor.stopCellEditing()
        }
        val result = mutableMapOf<String, String>()
        for (row in 0 until envTableModel.rowCount) {
            val key = (envTableModel.getValueAt(row, 0) as? String)
                ?.trim() ?: ""
            val value = (envTableModel.getValueAt(row, 1) as? String)
                ?.trim() ?: ""
            if (key.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    fun setEnvironmentVariables(vars: Map<String, String>) {
        envTableModel.rowCount = 0
        for ((key, value) in vars) {
            envTableModel.addRow(arrayOf(key, value))
        }
    }
}
