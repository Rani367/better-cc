package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.services.ClaudeState
import com.claudecode.jetbrains.services.ClaudeStateListener
import com.claudecode.jetbrains.services.ClaudeStateService
import com.claudecode.jetbrains.services.SessionUsage
import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.settings.ThinkingMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel

/**
 * Toolbar panel displayed above the chat input area.
 * Contains: model selector, thinking mode toggle, cost/usage display,
 * and session info.
 */
class ToolbarPanel(
    private val project: Project,
    parentDisposable: Disposable
) : JPanel(BorderLayout()), ClaudeStateListener {

    private val stateService = ClaudeStateService.getInstance(project)

    // Model options — "Default" means empty string (CLI picks the model)
    private val modelOptions = arrayOf(
        "Default", "sonnet", "opus", "haiku"
    )

    private val modelCombo = ComboBox(
        DefaultComboBoxModel(modelOptions)
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
                text = modelDisplayName(value as? String ?: "Default")
                return this
            }
        }
        toolTipText = "Select Claude model"
        val current = ClaudeSettings.getInstance().selectedModel
        selectedItem = if (current.isBlank()) "Default" else current
        addActionListener {
            val selected = selectedItem as? String ?: return@addActionListener
            val modelValue = if (selected == "Default") "" else selected
            ClaudeSettings.getInstance().selectedModel = modelValue
            stateService.setActiveModel(modelValue)
        }
    }

    // Thinking mode selector
    private val thinkingCombo = ComboBox(
        DefaultComboBoxModel(ThinkingMode.entries.toTypedArray())
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
                val mode = value as? ThinkingMode ?: ThinkingMode.NORMAL
                text = mode.displayName
                return this
            }
        }
        toolTipText = "Extended thinking mode"
        selectedItem = ClaudeSettings.getInstance().thinkingMode
        addActionListener {
            val selected = selectedItem as? ThinkingMode ?: return@addActionListener
            ClaudeSettings.getInstance().thinkingMode = selected
        }
    }

    // Cost/usage display label
    private val usageLabel = JBLabel("0 tokens | $0.00").apply {
        foreground = JBColor.GRAY
        toolTipText = "Click for usage breakdown"
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                showUsageBreakdown()
            }
        })
    }

    // Session info label
    private val sessionLabel = JBLabel("").apply {
        foreground = JBColor.GRAY
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Current session"
    }

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            JBUI.Borders.empty(4, 8, 4, 8)
        )

        val leftPanel = JPanel(
            FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)
        ).apply {
            isOpaque = false
            add(JBLabel("Model:"))
            add(modelCombo)
            add(JBLabel("Thinking:"))
            add(thinkingCombo)
        }

        val rightPanel = JPanel(
            FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)
        ).apply {
            isOpaque = false
            add(usageLabel)
            add(sessionLabel)
        }

        add(leftPanel, BorderLayout.WEST)
        add(rightPanel, BorderLayout.EAST)

        stateService.addListener(this)
        // Update from current state
        onUsageUpdated(stateService.usage)
        updateSessionLabel(stateService.currentSessionId)
    }

    override fun onUsageUpdated(usage: SessionUsage) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                usageLabel.text = "${usage.formatTokens()} tokens | ${usage.formatCost()}"
                usageLabel.toolTipText = buildString {
                    append("Input: ${usage.totalInputTokens} tokens\n")
                    append("Output: ${usage.totalOutputTokens} tokens\n")
                    append("Total cost: ${usage.formatCost()}")
                }
            }
        }
    }

    override fun onSessionChanged(sessionId: String?) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                updateSessionLabel(sessionId)
            }
        }
    }

    private fun updateSessionLabel(sessionId: String?) {
        if (sessionId != null) {
            val shortId = if (sessionId.length > 8) {
                sessionId.take(8) + "..."
            } else {
                sessionId
            }
            sessionLabel.text = shortId
            sessionLabel.toolTipText = "Session: $sessionId"
        } else {
            sessionLabel.text = ""
            sessionLabel.toolTipText = "No active session"
        }
    }

    private fun showUsageBreakdown() {
        val usage = stateService.usage
        val content = buildString {
            append("Input tokens: ${usage.totalInputTokens}\n")
            append("Output tokens: ${usage.totalOutputTokens}\n")
            append("Total tokens: ${usage.totalTokens}\n")
            append("Total cost: ${usage.formatCost()}\n")
            if (usage.contextPercent > 0) {
                append("Context used: ${usage.contextPercent}%")
            }
        }

        JBPopupFactory.getInstance()
            .createMessage(content)
            .showUnderneathOf(usageLabel)
    }

    private fun modelDisplayName(model: String): String {
        return when (model) {
            "Default" -> "Default"
            "sonnet" -> "Sonnet"
            "opus" -> "Opus"
            "haiku" -> "Haiku"
            else -> model
        }
    }

    fun dispose() {
        stateService.removeListener(this)
    }
}
