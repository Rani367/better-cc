package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.context.SelectionContext
import com.claudecode.jetbrains.services.ClaudeStateListener
import com.claudecode.jetbrains.services.ClaudeStateService
import com.claudecode.jetbrains.services.SessionUsage
import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.settings.ClaudeSettingsChangeListener
import com.claudecode.jetbrains.settings.PermissionMode
import com.claudecode.jetbrains.settings.ThinkingMode
import com.claudecode.jetbrains.ui.commands.FileMentionEntry
import com.claudecode.jetbrains.ui.commands.FileMentionPicker
import com.claudecode.jetbrains.ui.commands.SlashCommand
import com.claudecode.jetbrains.ui.commands.SlashCommandPalette
import com.claudecode.jetbrains.ui.theme.ClaudeColors
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class InputPanel(
    private val project: Project,
    private val onSend: (String) -> Unit
) : JPanel(BorderLayout()), Disposable, ClaudeStateListener {

    private var sendingInProgress = false
    private var onSlashCommand: ((SlashCommand) -> Unit)? = null
    private var inputFocused = false
    private val stateService = ClaudeStateService.getInstance(project)

    private val textArea = JBTextArea(2, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(10, 14, 10, 14)
        isOpaque = false
    }

    private val palette = SlashCommandPalette(textArea, ::handleCommandSelected)
    private val filePicker = FileMentionPicker(project, textArea, ::handleFileSelected)

    // Send button: rounded 26x26 orange button (sendButton_gGYT1w)
    private val sendButton = object : JButton() {
        override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(26), JBUI.scale(26))
        override fun getMinimumSize(): Dimension = preferredSize

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            // Background
            val bgColor = if (isEnabled) ClaudeColors.CLAUDE_CLAY_BUTTON else {
                val c = ClaudeColors.CLAUDE_CLAY_BUTTON
                Color(c.red, c.green, c.blue, 100)
            }
            g2.color = bgColor
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(5), JBUI.scale(5))
            // Arrow icon (▲ rotated 90° = ▶ pointing right, like a send arrow)
            g2.color = Color(0xfa, 0xf9, 0xf5) // claude-ivory
            val cx = width / 2
            val cy = height / 2
            val s = JBUI.scale(4)
            val xPoints = intArrayOf(cx - s, cx + s, cx - s)
            val yPoints = intArrayOf(cy - s, cy, cy + s)
            g2.fillPolygon(xPoints, yPoints, 3)
        }
    }.apply {
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Send message"
        addActionListener { doSend() }
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                if (isEnabled) repaint()
            }
        })
    }

    // Selection context indicator
    private val selectionLabel = JBLabel("").apply {
        border = JBUI.Borders.emptyLeft(4)
        font = font.deriveFont(font.size2D * 0.85f)
        foreground = ClaudeColors.SECONDARY_FOREGROUND
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

    private val selectionPanel = JPanel(
        FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)
    ).apply {
        isOpaque = false
        add(selectionToggle)
        add(selectionLabel)
        isVisible = false
    }

    private var onSelectionToggle: ((Boolean) -> Unit)? = null

    // Model indicator — clickable label
    private val modelLabel = JBLabel(
        modelDisplayText(ClaudeSettings.getInstance().selectedModel)
    ).apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size2D * 0.9f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Click to change model"
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                showModelPicker()
            }
        })
    }

    // Thinking level indicator — clickable label
    private val thinkingLabel = JBLabel(
        ClaudeSettings.getInstance().thinkingMode.displayName
    ).apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size2D * 0.9f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Click to change thinking mode"
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                showThinkingPicker()
            }
        })
    }

    // Cost display label
    private val costLabel = JBLabel("$0.00").apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size2D * 0.9f)
        toolTipText = "Session cost"
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
            ClaudeSettings.getInstance().permissionMode = selected
        }
    }

    init {
        val scrollPane = JBScrollPane(textArea).apply {
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
        }

        // Track focus for border colour change
        textArea.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                inputFocused = true
                this@InputPanel.repaint()
            }
            override fun focusLost(e: FocusEvent?) {
                inputFocused = false
                this@InputPanel.repaint()
            }
        })

        // Footer bar: [PermMode] [Model] [Thinking] ── [$cost] [Send]
        val footerBar = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                    ClaudeColors.INPUT_BORDER),
                JBUI.Borders.empty(5)
            )

            val leftFooter = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
                isOpaque = false
                add(permissionModeCombo)
                add(modelLabel)
                add(thinkingLabel)
                add(selectionPanel)
            }
            val rightFooter = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
                isOpaque = false
                add(costLabel)
                add(sendButton)
            }
            add(leftFooter, BorderLayout.CENTER)
            add(rightFooter, BorderLayout.EAST)
        }

        // Main input container with rounded border (inputContainer_cKsPxg)
        val inputContainer = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
                val radius = JBUI.scale(8)

                // Inner background layer (inputContainerBackground_cKsPxg)
                g2.color = ClaudeColors.INPUT_BACKGROUND
                g2.fillRoundRect(0, 0, width, height, radius, radius)

                // Border — Claude orange on focus
                val borderColor = if (inputFocused) {
                    ClaudeColors.CLAUDE_ORANGE
                } else {
                    ClaudeColors.INPUT_BORDER
                }
                g2.color = borderColor
                g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius)

                // Box shadow hint on focus
                if (inputFocused) {
                    val shadowColor = Color(
                        ClaudeColors.CLAUDE_ORANGE.red,
                        ClaudeColors.CLAUDE_ORANGE.green,
                        ClaudeColors.CLAUDE_ORANGE.blue,
                        30
                    )
                    g2.color = shadowColor
                    g2.drawRoundRect(-1, -1, width + 1, height + 1, radius + 2, radius + 2)
                }
            }
        }.apply {
            isOpaque = false
            add(scrollPane, BorderLayout.CENTER)
            add(footerBar, BorderLayout.SOUTH)
        }

        // Outer wrapper with max-width 680px centering (inputWrapper_cKsPxg)
        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(inputContainer, BorderLayout.CENTER)
            maximumSize = Dimension(JBUI.scale(680), Short.MAX_VALUE.toInt())
        }

        add(wrapper, BorderLayout.CENTER)
        isOpaque = false
        border = JBUI.Borders.empty(0, JBUI.scale(16), JBUI.scale(16), JBUI.scale(16))

        // Set up key bindings based on current setting
        setupKeyBindings()

        val upKey = KeyStroke.getKeyStroke("UP")
        val downKey = KeyStroke.getKeyStroke("DOWN")
        val escapeKey = KeyStroke.getKeyStroke("ESCAPE")

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
                } else {
                    returnFocusToEditor()
                }
            }
        })

        // Document listener for slash command and @ mention detection
        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = checkTriggers()
            override fun removeUpdate(e: DocumentEvent?) = checkTriggers()
            override fun changedUpdate(e: DocumentEvent?) = checkTriggers()
        })

        // Listen for settings changes to rebind keys
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(
                ClaudeSettings.SETTINGS_CHANGED,
                object : ClaudeSettingsChangeListener {
                    override fun settingsChanged(settings: ClaudeSettings) {
                        setupKeyBindings()
                        modelLabel.text = modelDisplayText(
                            settings.selectedModel
                        )
                        thinkingLabel.text = settings.thinkingMode
                            .displayName
                    }
                }
            )

        // Subscribe to state changes for live cost updates
        stateService.addListener(this)
        onUsageUpdated(stateService.usage)
    }

    override fun dispose() {
        stateService.removeListener(this)
        // MessageBus connection is auto-disposed via parent Disposable
    }

    // ── ClaudeStateListener ──────────────────────────────────────

    override fun onUsageUpdated(usage: SessionUsage) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                costLabel.text = usage.formatCost()
                costLabel.toolTipText = buildString {
                    append("Input: ${usage.totalInputTokens} tokens\n")
                    append("Output: ${usage.totalOutputTokens} tokens\n")
                    append("Total: ${usage.formatCost()}")
                }
            }
        }
    }

    override fun onModelChanged(model: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                modelLabel.text = modelDisplayText(model)
            }
        }
    }

    // ── Model/thinking pickers ───────────────────────────────────

    private fun modelDisplayText(model: String): String {
        return when {
            model.isBlank() -> "Default"
            model == "sonnet" -> "Sonnet"
            model == "opus" -> "Opus"
            model == "haiku" -> "Haiku"
            else -> model
        }
    }

    private fun showModelPicker() {
        val options = listOf("Default", "sonnet", "opus", "haiku")
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(options)
            .setTitle("Select Model")
            .setItemChosenCallback { selected ->
                val modelValue =
                    if (selected == "Default") "" else selected
                ClaudeSettings.getInstance().selectedModel = modelValue
                stateService.setActiveModel(modelValue)
                modelLabel.text = modelDisplayText(modelValue)
            }
            .createPopup()
        popup.showUnderneathOf(modelLabel)
    }

    private fun showThinkingPicker() {
        val options = ThinkingMode.entries.toList()
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(options)
            .setTitle("Thinking Mode")
            .setRenderer(object : javax.swing.DefaultListCellRenderer() {
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
                    text = (value as? ThinkingMode)?.displayName
                        ?: value.toString()
                    return this
                }
            })
            .setItemChosenCallback { selected ->
                ClaudeSettings.getInstance().thinkingMode = selected
                thinkingLabel.text = selected.displayName
            }
            .createPopup()
        popup.showUnderneathOf(thinkingLabel)
    }

    private fun returnFocusToEditor() {
        val editorManager = com.intellij.openapi.fileEditor.FileEditorManager
            .getInstance(project)
        val editor = editorManager.selectedTextEditor ?: return
        editor.contentComponent.requestFocusInWindow()
    }

    fun setInputEnabled(enabled: Boolean) {
        sendingInProgress = !enabled
        sendButton.isEnabled = enabled
        sendButton.repaint()
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

    fun insertTextAtCursor(text: String) {
        val caretPos = textArea.caretPosition
        textArea.insert(text, caretPos)
        textArea.caretPosition = caretPos + text.length
    }

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

        if (text.startsWith("/") && !text.contains("\n")) {
            if (!palette.isVisible) {
                palette.show()
            }
            palette.updateFilter(text)
            return
        } else if (palette.isVisible) {
            palette.dismiss()
        }

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

    private fun findActiveMention(text: String): String? {
        if (text.isEmpty()) return null

        val caretPos = try {
            textArea.caretPosition.coerceAtMost(text.length)
        } catch (_: Exception) {
            text.length
        }

        var atIndex = -1
        for (i in (caretPos - 1) downTo 0) {
            val ch = text[i]
            if (ch == '@') {
                atIndex = i
                break
            }
            if (ch == '\n') break
        }

        if (atIndex < 0) return null
        if (atIndex > 0 && !text[atIndex - 1].isWhitespace()) return null

        val query = text.substring(atIndex, caretPos)
        if (!query.startsWith("@")) return null

        val afterCaret = text.substring(caretPos)
        val nextNewline = afterCaret.indexOf('\n')
        val restOfLine = if (nextNewline >= 0) afterCaret.substring(0, nextNewline) else afterCaret

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

    private fun setupKeyBindings() {
        val useCtrlEnter = ClaudeSettings.getInstance().useCtrlEnterToSend

        val enterKey = KeyStroke.getKeyStroke("ENTER")
        val shiftEnterKey = KeyStroke.getKeyStroke("shift ENTER")
        val ctrlEnterKey = KeyStroke.getKeyStroke("ctrl ENTER")
        val metaEnterKey = KeyStroke.getKeyStroke("meta ENTER")

        val sendAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (palette.isVisible) {
                    palette.selectCurrent()
                } else if (filePicker.isVisible) {
                    filePicker.selectCurrent()
                } else {
                    doSend()
                }
            }
        }

        val newlineAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                textArea.append("\n")
            }
        }

        if (useCtrlEnter) {
            textArea.inputMap.put(enterKey, "insert-newline")
            textArea.actionMap.put("insert-newline", newlineAction)
            textArea.inputMap.put(ctrlEnterKey, "send")
            textArea.inputMap.put(metaEnterKey, "send")
            textArea.actionMap.put("send", sendAction)
            textArea.inputMap.put(shiftEnterKey, "insert-newline")
        } else {
            textArea.inputMap.put(enterKey, "send")
            textArea.actionMap.put("send", sendAction)
            textArea.inputMap.put(shiftEnterKey, "insert-newline")
            textArea.actionMap.put("insert-newline", newlineAction)
            textArea.inputMap.put(ctrlEnterKey, "send")
            textArea.inputMap.put(metaEnterKey, "send")
        }
    }
}
