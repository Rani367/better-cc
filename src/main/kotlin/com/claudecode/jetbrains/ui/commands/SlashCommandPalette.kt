package com.claudecode.jetbrains.ui.commands

import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.BorderFactory

// ── Data Model ──────────────────────────────────────────────────

data class SlashCommand(
    val name: String,
    val description: String
)

// ── Registry ────────────────────────────────────────────────────

object SlashCommandRegistry {

    val ALL_COMMANDS: List<SlashCommand> = listOf(
        // Common
        SlashCommand("/model", "Switch AI model"),
        SlashCommand("/compact", "Compress context, specify what to retain"),
        SlashCommand("/clear", "Reset conversation history"),
        SlashCommand("/usage", "Check token usage against plan limits"),
        // Context
        SlashCommand("/memory", "Edit CLAUDE.md project conventions"),
        SlashCommand("/permissions", "Adjust tool and access permissions"),
        // Customize
        SlashCommand("/mcp", "Manage MCP servers"),
        SlashCommand("/plugins", "Manage plugins"),
        SlashCommand("/agents", "Create and manage sub-agents"),
        // Project Management
        SlashCommand("/init", "Generate CLAUDE.md memory file"),
        SlashCommand("/context", "Visualize context window usage"),
        SlashCommand("/resume", "Resume a past session"),
        SlashCommand("/rename", "Rename current session"),
        SlashCommand("/rewind", "Rewind conversation or code changes"),
        // Information & Status
        SlashCommand("/cost", "Show session cost in tokens and dollars"),
        SlashCommand("/help", "List all available commands"),
        SlashCommand("/tasks", "Monitor background tasks"),
        SlashCommand("/doctor", "Run environment diagnostics"),
        SlashCommand("/stats", "Generate usage statistics report"),
        // Mode & Model Control
        SlashCommand("/fast", "Toggle Fast Mode"),
        SlashCommand("/plan", "Toggle read-only Plan Mode"),
        SlashCommand("/vim", "Enable Vim-style editing"),
        SlashCommand("/output-style", "Change response formatting style"),
        // Feature Management
        SlashCommand("/hooks", "Configure and manage hooks"),
        SlashCommand("/sandbox", "Activate sandboxed execution"),
        SlashCommand("/review", "Security review of changes"),
        SlashCommand("/config", "Open configuration settings"),
        SlashCommand("/settings", "Open plugin settings page"),
        SlashCommand("/login", "Re-authenticate session")
    )

    fun filterCommands(query: String): List<SlashCommand> {
        if (query.isBlank()) return ALL_COMMANDS
        val normalized = query.removePrefix("/").lowercase()
        if (normalized.isEmpty()) return ALL_COMMANDS
        return ALL_COMMANDS.filter { cmd ->
            cmd.name.removePrefix("/").lowercase().startsWith(normalized)
        }
    }
}

// ── Popup ───────────────────────────────────────────────────────

class SlashCommandPalette(
    private val textArea: JBTextArea,
    private val onCommandSelected: (SlashCommand) -> Unit
) {

    private val listModel = CollectionListModel(SlashCommandRegistry.ALL_COMMANDS)
    private val list = JBList(listModel).apply {
        cellRenderer = SlashCommandCellRenderer()
        visibleRowCount = 10
    }
    private var popup: JBPopup? = null

    val isVisible: Boolean get() = popup?.isVisible == true

    fun show() {
        if (isVisible) return

        listModel.replaceAll(SlashCommandRegistry.ALL_COMMANDS)
        if (listModel.size > 0) {
            list.selectedIndex = 0
        }

        val scrollPane = JBScrollPane(list).apply {
            preferredSize = JBUI.size(340, 260)
            border = BorderFactory.createEmptyBorder()
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, list)
            .setRequestFocus(false)
            .setFocusable(false)
            .setResizable(false)
            .setMovable(false)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setCancelKeyEnabled(false) // we handle Escape ourselves
            .createPopup()

        popup?.showUnderneathOf(textArea)
    }

    fun updateFilter(query: String) {
        val filtered = SlashCommandRegistry.filterCommands(query)
        listModel.replaceAll(filtered)
        if (listModel.size > 0) {
            list.selectedIndex = 0
        }
    }

    fun dismiss() {
        popup?.cancel()
        popup = null
    }

    fun selectCurrent() {
        val selected = list.selectedValue
        if (selected != null) {
            onCommandSelected(selected)
        }
        dismiss()
    }

    fun moveUp() {
        val idx = list.selectedIndex
        if (idx > 0) {
            list.selectedIndex = idx - 1
            list.ensureIndexIsVisible(idx - 1)
        }
    }

    fun moveDown() {
        val idx = list.selectedIndex
        if (idx < listModel.size - 1) {
            list.selectedIndex = idx + 1
            list.ensureIndexIsVisible(idx + 1)
        }
    }

    // ── Cell Renderer ───────────────────────────────────────────

    private class SlashCommandCellRenderer : ListCellRenderer<SlashCommand> {

        override fun getListCellRendererComponent(
            list: JList<out SlashCommand>,
            value: SlashCommand?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val panel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                border = JBUI.Borders.empty(4, 8, 4, 8)
            }

            val nameLabel = JLabel(value?.name ?: "").apply {
                font = font.deriveFont(Font.BOLD)
            }
            val descLabel = JLabel(value?.description ?: "").apply {
                foreground = if (isSelected) {
                    list.selectionForeground
                } else {
                    JBColor.GRAY
                }
                font = font.deriveFont(Font.PLAIN)
            }

            panel.add(nameLabel, BorderLayout.WEST)
            panel.add(descLabel, BorderLayout.CENTER)

            if (isSelected) {
                panel.background = list.selectionBackground
                nameLabel.foreground = list.selectionForeground
            } else {
                panel.background = list.background
                nameLabel.foreground = list.foreground
            }

            panel.isOpaque = true
            return panel
        }
    }
}
