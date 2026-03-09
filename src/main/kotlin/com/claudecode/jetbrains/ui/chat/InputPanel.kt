package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.context.SelectionContext
import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.settings.PermissionMode
import com.claudecode.jetbrains.ui.commands.FileMentionEntry
import com.claudecode.jetbrains.ui.commands.FileMentionPicker
import com.claudecode.jetbrains.ui.commands.SlashCommand
import com.claudecode.jetbrains.ui.commands.SlashCommandPalette
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
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
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class InputPanel(
    private val project: Project,
    private val onSend: (String) -> Unit
) : JPanel(BorderLayout()) {

    private var sendingInProgress = false
    private var onSlashCommand: ((SlashCommand) -> Unit)? = null

    private val textArea = JBTextArea(2, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
    }

    private val palette = SlashCommandPalette(textArea, ::handleCommandSelected)
    private val filePicker = FileMentionPicker(project, textArea, ::handleFileSelected)

    private val sendButton = JButton("Send").apply {
        addActionListener { doSend() }
    }

    // Selection context indicator
    private val selectionLabel = JBLabel("").apply {
        border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
    }

    private val selectionToggle = JToggleButton().apply {
        icon = AllIcons.General.InspectionsEye
        isSelected = true
        toolTipText = "Toggle selection context sharing"
        isBorderPainted = false
        isContentAreaFilled = false
        addActionListener {
            onSelectionToggle?.invoke(isSelected)
            updateSelectionIcon()
        }
    }

    private val selectionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
        add(selectionToggle)
        add(selectionLabel)
        isVisible = false
    }

    private var onSelectionToggle: ((Boolean) -> Unit)? = null

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
                super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
                )
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

        // Bottom row: permission mode selector + selection context
        val modeRow = JPanel(BorderLayout()).apply {
            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(JBLabel("Permission:"))
                add(permissionModeCombo)
            }
            add(leftPanel, BorderLayout.WEST)
            add(selectionPanel, BorderLayout.EAST)
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
                } else if (filePicker.isVisible) {
                    filePicker.selectCurrent()
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
                } else if (filePicker.isVisible) {
                    filePicker.moveUp()
                }
            }
        })

        textArea.inputMap.put(downKey, "palette-down")
        textArea.actionMap.put("palette-down", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (palette.isVisible) {
                    palette.moveDown()
                } else if (filePicker.isVisible) {
                    filePicker.moveDown()
                }
            }
        })

        textArea.inputMap.put(escapeKey, "palette-dismiss")
        textArea.actionMap.put("palette-dismiss", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (palette.isVisible) {
                    palette.dismiss()
                } else if (filePicker.isVisible) {
                    filePicker.dismiss()
                }
            }
        })

        // Document listener for slash command and @ mention detection
        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = checkTriggers()
            override fun removeUpdate(e: DocumentEvent?) = checkTriggers()
            override fun changedUpdate(e: DocumentEvent?) = checkTriggers()
        })
    }

    fun setInputEnabled(enabled: Boolean) {
        sendingInProgress = !enabled
        sendButton.isEnabled = enabled
    }

    fun focus() {
        textArea.requestFocusInWindow()
    }

    fun setText(text: String) {
        textArea.text = text
        textArea.caretPosition = text.length
    }

    fun getTextArea(): JBTextArea = textArea

    fun setSlashCommandHandler(handler: (SlashCommand) -> Unit) {
        onSlashCommand = handler
    }

    fun setSelectionToggleHandler(handler: (Boolean) -> Unit) {
        onSelectionToggle = handler
    }

    /**
     * Inserts text at the current caret position in the text area.
     */
    fun insertTextAtCursor(text: String) {
        val caretPos = textArea.caretPosition
        textArea.insert(text, caretPos)
        textArea.caretPosition = caretPos + text.length
    }

    /**
     * Updates the selection context indicator in the footer.
     */
    fun updateSelectionContext(context: SelectionContext?) {
        if (context != null) {
            val lineText = if (context.lineCount == 1) "1 line" else "${context.lineCount} lines"
            selectionLabel.text = "$lineText selected in ${context.fileName}"
            selectionPanel.isVisible = true
        } else {
            selectionLabel.text = ""
            selectionPanel.isVisible = false
        }
    }

    private fun updateSelectionIcon() {
        if (selectionToggle.isSelected) {
            selectionToggle.icon = AllIcons.General.InspectionsEye
            selectionToggle.toolTipText = "Selection context is shared with Claude (click to hide)"
        } else {
            selectionToggle.icon = AllIcons.Actions.Show
            selectionToggle.toolTipText = "Selection context is hidden from Claude (click to share)"
        }
    }

    private fun doSend() {
        if (sendingInProgress) return
        val text = textArea.text?.trim() ?: return
        if (text.isEmpty()) return
        textArea.text = ""
        onSend(text)
    }

    private fun checkTriggers() {
        val text = textArea.text ?: ""

        // Slash command detection (only at start of text, single line)
        if (text.startsWith("/") && !text.contains("\n")) {
            if (!palette.isVisible) {
                palette.show()
            }
            palette.updateFilter(text)
            return
        } else if (palette.isVisible) {
            palette.dismiss()
        }

        // @ file mention detection
        val atMention = findActiveMention(text)
        if (atMention != null) {
            if (!filePicker.isVisible) {
                filePicker.show()
            }
            filePicker.updateFilter(atMention)
        } else if (filePicker.isVisible) {
            filePicker.dismiss()
        }
    }

    /**
     * Finds the active `@mention` query around the caret position.
     * Returns the query string (after `@`) or null if no mention is active.
     */
    private fun findActiveMention(text: String): String? {
        val caretPos = try {
            textArea.caretPosition
        } catch (_: Exception) {
            text.length
        }

        // Look backward from caret to find the nearest @
        var atIndex = -1
        for (i in (caretPos - 1) downTo 0) {
            val ch = text[i]
            if (ch == '@') {
                atIndex = i
                break
            }
            // Stop if we hit whitespace before finding @, unless it's part of a path
            if (ch == '\n') break
        }

        if (atIndex < 0) return null

        // The @ should be at start of line or preceded by whitespace
        if (atIndex > 0 && !text[atIndex - 1].isWhitespace()) return null

        // Extract the query from @ to caret
        val query = text.substring(atIndex, caretPos)
        if (!query.startsWith("@")) return null

        // Don't trigger for just "@" if there's content after the caret on the same line
        // that includes a space (completed mention)
        val afterCaret = text.substring(caretPos)
        val nextNewline = afterCaret.indexOf('\n')
        val restOfLine = if (nextNewline >= 0) afterCaret.substring(0, nextNewline) else afterCaret

        // If the mention portion (before next space) is already completed, don't show picker
        // This handles the case where user has already selected a file
        if (restOfLine.isNotEmpty() && !restOfLine[0].isWhitespace() && restOfLine.contains(' ')) {
            return null
        }

        return query
    }

    private fun handleCommandSelected(command: SlashCommand) {
        palette.dismiss()
        textArea.text = ""
        onSlashCommand?.invoke(command)
    }

    private fun handleFileSelected(entry: FileMentionEntry) {
        filePicker.dismiss()

        val text = textArea.text ?: ""
        val caretPos = try {
            textArea.caretPosition
        } catch (_: Exception) {
            text.length
        }

        // Find the @ position to replace the query
        var atIndex = -1
        for (i in (caretPos - 1) downTo 0) {
            val ch = text[i]
            if (ch == '@') {
                atIndex = i
                break
            }
            if (ch == '\n') break
        }

        if (atIndex < 0) {
            // Fallback: just append
            textArea.append("@${entry.relativePath} ")
            return
        }

        val suffix = if (entry.isDirectory) "/" else " "
        val replacement = "@${entry.relativePath}$suffix"
        val before = text.substring(0, atIndex)
        val after = text.substring(caretPos)
        val newText = before + replacement + after

        textArea.text = newText
        textArea.caretPosition = (before + replacement).length
    }
}
