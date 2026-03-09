package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.settings.PermissionMode
import com.claudecode.jetbrains.ui.commands.SlashCommand
import com.claudecode.jetbrains.ui.commands.SlashCommandPalette
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class InputPanel(private val onSend: (String) -> Unit) : JPanel(BorderLayout()) {

    private var sendingInProgress = false
    private var onSlashCommand: ((SlashCommand) -> Unit)? = null

    private val textArea = JBTextArea(2, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
    }

    private val palette = SlashCommandPalette(textArea, ::handleCommandSelected)

    private val sendButton = JButton("Send").apply {
        addActionListener { doSend() }
    }

    private val permissionModeCombo = ComboBox(
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
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? PermissionMode)?.displayName ?: value.toString()
                return this
            }
        }
        selectedItem = ClaudeSettings.getInstance().permissionMode
        addActionListener {
            val selected = selectedItem as? PermissionMode ?: return@addActionListener
            if (selected == PermissionMode.BYPASS) {
                val settings = ClaudeSettings.getInstance()
                if (!settings.allowDangerouslySkipPermissions) {
                    JOptionPane.showMessageDialog(
                        this@InputPanel,
                        "Bypass mode is not enabled.\n" +
                            "Enable 'allowDangerouslySkipPermissions' in settings first.",
                        "Bypass Not Allowed",
                        JOptionPane.WARNING_MESSAGE
                    )
                    this.selectedItem = settings.permissionMode
                    return@addActionListener
                }
            }
            ClaudeSettings.getInstance().permissionMode = selected
        }
    }

    init {
        val scrollPane = JBScrollPane(textArea).apply {
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        // Top row: text area + send button
        val inputRow = JPanel(BorderLayout()).apply {
            add(scrollPane, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        // Bottom row: permission mode selector
        val modeRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JBLabel("Permission:"))
            add(permissionModeCombo)
        }

        // Combine
        val wrapper = JPanel(BorderLayout()).apply {
            add(inputRow, BorderLayout.CENTER)
            add(modeRow, BorderLayout.SOUTH)
        }

        add(wrapper, BorderLayout.CENTER)
        border = BorderFactory.createEmptyBorder(4, 8, 8, 8)

        // Enter sends, Shift+Enter inserts newline
        val enterKey = KeyStroke.getKeyStroke("ENTER")
        val shiftEnterKey = KeyStroke.getKeyStroke("shift ENTER")
        val upKey = KeyStroke.getKeyStroke("UP")
        val downKey = KeyStroke.getKeyStroke("DOWN")
        val escapeKey = KeyStroke.getKeyStroke("ESCAPE")

        textArea.inputMap.put(enterKey, "send")
        textArea.actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (palette.isVisible) {
                    palette.selectCurrent()
                } else {
                    doSend()
                }
            }
        })

        textArea.inputMap.put(shiftEnterKey, "insert-newline")
        textArea.actionMap.put("insert-newline", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                textArea.append("\n")
            }
        })

        textArea.inputMap.put(upKey, "palette-up")
        textArea.actionMap.put("palette-up", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (palette.isVisible) {
                    palette.moveUp()
                }
            }
        })

        textArea.inputMap.put(downKey, "palette-down")
        textArea.actionMap.put("palette-down", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (palette.isVisible) {
                    palette.moveDown()
                }
            }
        })

        textArea.inputMap.put(escapeKey, "palette-dismiss")
        textArea.actionMap.put("palette-dismiss", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (palette.isVisible) {
                    palette.dismiss()
                }
            }
        })

        // Document listener for slash command detection
        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = checkSlashTrigger()
            override fun removeUpdate(e: DocumentEvent?) = checkSlashTrigger()
            override fun changedUpdate(e: DocumentEvent?) = checkSlashTrigger()
        })
    }

    fun setInputEnabled(enabled: Boolean) {
        sendingInProgress = !enabled
        sendButton.isEnabled = enabled
    }

    fun focus() {
        textArea.requestFocusInWindow()
    }

    fun getTextArea(): JBTextArea = textArea

    fun setSlashCommandHandler(handler: (SlashCommand) -> Unit) {
        onSlashCommand = handler
    }

    private fun doSend() {
        if (sendingInProgress) return
        val text = textArea.text?.trim() ?: return
        if (text.isEmpty()) return
        textArea.text = ""
        onSend(text)
    }

    private fun checkSlashTrigger() {
        val text = textArea.text ?: ""
        if (text.startsWith("/") && !text.contains("\n")) {
            if (!palette.isVisible) {
                palette.show()
            }
            palette.updateFilter(text)
        } else {
            palette.dismiss()
        }
    }

    private fun handleCommandSelected(command: SlashCommand) {
        palette.dismiss()
        textArea.text = ""
        onSlashCommand?.invoke(command)
    }
}
