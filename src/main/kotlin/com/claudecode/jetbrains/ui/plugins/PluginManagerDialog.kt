package com.claudecode.jetbrains.ui.plugins

import com.claudecode.jetbrains.cli.ClaudeCliManager
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

// ── Data Model ──────────────────────────────────────────────────

data class PluginInfo(
    val name: String,
    val description: String,
    val version: String,
    val enabled: Boolean,
    val installed: Boolean,
    val source: String?
)

data class MarketplaceSource(
    val url: String,
    val name: String?
)

enum class InstallScope(val cliValue: String, val displayName: String) {
    USER("user", "Install for you"),
    PROJECT("project", "Install for this project"),
    LOCAL("local", "Install locally");

    override fun toString(): String = displayName
}

// ── Dialog ──────────────────────────────────────────────────────

class PluginManagerDialog(
    private val project: Project
) : DialogWrapper(project, true) {

    private val logger = Logger.getInstance(PluginManagerDialog::class.java)

    // Plugins tab
    private val installedListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val availableListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val searchField = JBTextField().apply {
        columns = 25
        toolTipText = "Search plugins by name or description"
    }
    private val pluginsStatusLabel = JBLabel(" ")

    // Marketplaces tab
    private val marketplaceListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val marketplacesStatusLabel = JBLabel(" ")

    // State
    private var installedPlugins: List<PluginInfo> = emptyList()
    private var availablePlugins: List<PluginInfo> = emptyList()
    private var marketplaces: List<MarketplaceSource> = emptyList()
    private var restartRequired = false

    // Restart banner
    private val restartBanner = JPanel(BorderLayout()).apply {
        background = JBColor(
            java.awt.Color(255, 243, 205),
            java.awt.Color(80, 70, 30)
        )
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                JBColor(
                    java.awt.Color(255, 220, 130),
                    java.awt.Color(120, 100, 40)
                )
            ),
            JBUI.Borders.empty(6, 12, 6, 12)
        )
        add(
            JBLabel("Restart required for changes to take effect.").apply {
                foreground = JBColor(
                    java.awt.Color(120, 90, 0),
                    java.awt.Color(220, 190, 100)
                )
            },
            BorderLayout.CENTER
        )
        isVisible = false
    }

    init {
        title = "Plugin Management"
        setOKButtonText("Close")
        init()
        loadPlugins()
        loadMarketplaces()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, 8))
        mainPanel.preferredSize = Dimension(700, 550)
        mainPanel.border = JBUI.Borders.empty(8)

        // Restart banner at top
        mainPanel.add(restartBanner, BorderLayout.NORTH)

        // Tabbed pane
        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("Plugins", createPluginsTab())
        tabbedPane.addTab("Marketplaces", createMarketplacesTab())

        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        return mainPanel
    }

    // ── Plugins Tab ─────────────────────────────────────────────

    private fun createPluginsTab(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = JBUI.Borders.emptyTop(8)

        // Search bar
        val searchRow = JPanel(BorderLayout(8, 0))
        searchRow.add(JBLabel("Search:"), BorderLayout.WEST)
        searchRow.add(searchField, BorderLayout.CENTER)
        val refreshButton = JButton("Refresh").apply {
            addActionListener { loadPlugins() }
        }
        searchRow.add(refreshButton, BorderLayout.EAST)

        // Wire search filtering
        searchField.document.addDocumentListener(
            object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filterPlugins()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filterPlugins()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filterPlugins()
            }
        )

        panel.add(searchRow, BorderLayout.NORTH)

        // Installed section
        val installedSection = JPanel(BorderLayout(0, 4))
        installedSection.border = BorderFactory.createTitledBorder("Installed")
        val installedScroll = JBScrollPane(installedListPanel).apply {
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(660, 150)
        }
        installedSection.add(installedScroll, BorderLayout.CENTER)

        // Available section
        val availableSection = JPanel(BorderLayout(0, 4))
        availableSection.border = BorderFactory.createTitledBorder("Available")
        val availableScroll = JBScrollPane(availableListPanel).apply {
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(660, 150)
        }
        availableSection.add(availableScroll, BorderLayout.CENTER)

        // Content area with both sections
        val contentPanel = JPanel(BorderLayout(0, 8))
        val splitPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(installedSection)
            add(Box.createVerticalStrut(4))
            add(availableSection)
        }
        contentPanel.add(splitPanel, BorderLayout.CENTER)
        contentPanel.add(pluginsStatusLabel, BorderLayout.SOUTH)

        panel.add(contentPanel, BorderLayout.CENTER)

        return panel
    }

    // ── Marketplaces Tab ────────────────────────────────────────

    private fun createMarketplacesTab(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = JBUI.Borders.emptyTop(8)

        // Header
        val headerRow = JPanel(BorderLayout())
        headerRow.add(
            JBLabel("Configured Marketplace Sources").apply {
                font = font.deriveFont(Font.BOLD, 14f)
            },
            BorderLayout.WEST
        )

        panel.add(headerRow, BorderLayout.NORTH)

        // Marketplace list
        val listScroll = JBScrollPane(marketplaceListPanel).apply {
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(660, 250)
        }

        panel.add(listScroll, BorderLayout.CENTER)

        // Add marketplace section
        val addPanel = JPanel(GridBagLayout())
        addPanel.border = BorderFactory.createTitledBorder("Add Marketplace")
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        addPanel.add(JBLabel("Source:"), gbc)

        val sourceField = JBTextField().apply {
            columns = 35
            toolTipText = "GitHub repo URL, HTTP URL, or local path"
        }
        gbc.gridx = 1; gbc.weightx = 1.0
        addPanel.add(sourceField, gbc)

        val addButton = JButton("Add")
        gbc.gridx = 2; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE
        addPanel.add(addButton, gbc)

        addButton.addActionListener {
            val source = sourceField.text?.trim() ?: ""
            if (source.isEmpty()) {
                setMarketplacesStatus("Source URL or path is required.")
                return@addActionListener
            }
            addMarketplace(source)
            sourceField.text = ""
        }

        val bottomSection = JPanel(BorderLayout(0, 4))
        bottomSection.add(addPanel, BorderLayout.CENTER)
        bottomSection.add(marketplacesStatusLabel, BorderLayout.SOUTH)

        panel.add(bottomSection, BorderLayout.SOUTH)

        return panel
    }

    // ── Plugin Rendering ────────────────────────────────────────

    private fun renderPluginLists() {
        val query = searchField.text?.trim()?.lowercase() ?: ""

        // Installed plugins
        installedListPanel.removeAll()
        val filteredInstalled = if (query.isEmpty()) {
            installedPlugins
        } else {
            installedPlugins.filter { matchesSearch(it, query) }
        }

        if (filteredInstalled.isEmpty()) {
            installedListPanel.add(
                JBLabel("No installed plugins found.").apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    border = JBUI.Borders.empty(12, 0, 12, 0)
                }
            )
        } else {
            for (plugin in filteredInstalled) {
                installedListPanel.add(createInstalledPluginCard(plugin))
                installedListPanel.add(Box.createVerticalStrut(2))
            }
        }

        // Available plugins
        availableListPanel.removeAll()
        val filteredAvailable = if (query.isEmpty()) {
            availablePlugins
        } else {
            availablePlugins.filter { matchesSearch(it, query) }
        }

        if (filteredAvailable.isEmpty()) {
            availableListPanel.add(
                JBLabel("No available plugins found.").apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    border = JBUI.Borders.empty(12, 0, 12, 0)
                }
            )
        } else {
            for (plugin in filteredAvailable) {
                availableListPanel.add(createAvailablePluginCard(plugin))
                availableListPanel.add(Box.createVerticalStrut(2))
            }
        }

        installedListPanel.revalidate()
        installedListPanel.repaint()
        availableListPanel.revalidate()
        availableListPanel.repaint()
    }

    private fun matchesSearch(plugin: PluginInfo, query: String): Boolean {
        return plugin.name.lowercase().contains(query)
            || plugin.description.lowercase().contains(query)
    }

    private fun createInstalledPluginCard(plugin: PluginInfo): JPanel {
        val card = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(6, 10, 6, 10)
            )
            maximumSize = Dimension(Int.MAX_VALUE, 50)
        }

        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        infoPanel.add(JBLabel(plugin.name).apply {
            font = font.deriveFont(Font.BOLD)
        })
        if (plugin.version.isNotEmpty()) {
            infoPanel.add(JBLabel("v${plugin.version}").apply {
                foreground = JBColor.GRAY
            })
        }
        if (plugin.description.isNotEmpty()) {
            infoPanel.add(JBLabel("- ${truncate(plugin.description, 50)}").apply {
                foreground = JBColor.GRAY
            })
        }

        card.add(infoPanel, BorderLayout.CENTER)

        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        val toggleLabel = if (plugin.enabled) "Disable" else "Enable"
        val toggleButton = JButton(toggleLabel).apply {
            addActionListener {
                togglePlugin(plugin.name, !plugin.enabled)
            }
        }
        actionsPanel.add(toggleButton)

        card.add(actionsPanel, BorderLayout.EAST)

        return card
    }

    private fun createAvailablePluginCard(plugin: PluginInfo): JPanel {
        val card = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(6, 10, 6, 10)
            )
            maximumSize = Dimension(Int.MAX_VALUE, 50)
        }

        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        infoPanel.add(JBLabel(plugin.name).apply {
            font = font.deriveFont(Font.BOLD)
        })
        if (plugin.version.isNotEmpty()) {
            infoPanel.add(JBLabel("v${plugin.version}").apply {
                foreground = JBColor.GRAY
            })
        }
        if (plugin.description.isNotEmpty()) {
            infoPanel.add(JBLabel("- ${truncate(plugin.description, 50)}").apply {
                foreground = JBColor.GRAY
            })
        }

        card.add(infoPanel, BorderLayout.CENTER)

        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))

        val scopeCombo = JComboBox(
            DefaultComboBoxModel(InstallScope.entries.toTypedArray())
        )
        actionsPanel.add(scopeCombo)

        val installButton = JButton("Install").apply {
            addActionListener {
                val scope = scopeCombo.selectedItem as? InstallScope
                    ?: InstallScope.USER
                installPlugin(plugin.name, scope)
            }
        }
        actionsPanel.add(installButton)

        card.add(actionsPanel, BorderLayout.EAST)

        return card
    }

    // ── Marketplace Rendering ───────────────────────────────────

    private fun renderMarketplaceList() {
        marketplaceListPanel.removeAll()

        if (marketplaces.isEmpty()) {
            marketplaceListPanel.add(
                JBLabel("No marketplace sources configured.").apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    border = JBUI.Borders.empty(20, 0, 20, 0)
                }
            )
        } else {
            for (marketplace in marketplaces) {
                marketplaceListPanel.add(createMarketplaceCard(marketplace))
                marketplaceListPanel.add(Box.createVerticalStrut(4))
            }
        }

        marketplaceListPanel.revalidate()
        marketplaceListPanel.repaint()
    }

    private fun createMarketplaceCard(
        marketplace: MarketplaceSource
    ): JPanel {
        val card = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(8, 12, 8, 12)
            )
            maximumSize = Dimension(Int.MAX_VALUE, 50)
        }

        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        val displayText = marketplace.name ?: marketplace.url
        infoPanel.add(JBLabel(displayText).apply {
            font = font.deriveFont(Font.BOLD)
        })
        if (marketplace.name != null) {
            infoPanel.add(JBLabel(truncate(marketplace.url, 50)).apply {
                foreground = JBColor.GRAY
                toolTipText = marketplace.url
            })
        }

        card.add(infoPanel, BorderLayout.CENTER)

        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))

        val refreshButton = JButton("Refresh").apply {
            addActionListener { refreshMarketplace(marketplace.url) }
        }
        actionsPanel.add(refreshButton)

        val deleteButton = JButton("Delete").apply {
            addActionListener { removeMarketplace(marketplace.url) }
        }
        actionsPanel.add(deleteButton)

        card.add(actionsPanel, BorderLayout.EAST)

        return card
    }

    // ── CLI Operations ──────────────────────────────────────────

    private fun loadPlugins() {
        setPluginsStatus("Loading plugins...")
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val result = cliManager.runCliCommand(listOf("plugins", "list"))

            val allPlugins = if (result != null) {
                parsePluginList(result)
            } else {
                emptyList()
            }

            SwingUtilities.invokeLater {
                installedPlugins = allPlugins.filter { it.installed }
                availablePlugins = allPlugins.filter { !it.installed }
                renderPluginLists()
                setPluginsStatus(
                    if (allPlugins.isEmpty()) "No plugins found."
                    else "${installedPlugins.size} installed, " +
                        "${availablePlugins.size} available."
                )
            }
        }
    }

    private fun installPlugin(name: String, scope: InstallScope) {
        setPluginsStatus("Installing '$name'...")
        showRestartBanner()
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val args = mutableListOf("plugins", "install", name)
            when (scope) {
                InstallScope.USER -> args.addAll(listOf("--scope", "user"))
                InstallScope.PROJECT -> args.addAll(listOf("--scope", "project"))
                InstallScope.LOCAL -> args.addAll(listOf("--scope", "local"))
            }

            val result = cliManager.runCliCommandWithExitCode(args)

            SwingUtilities.invokeLater {
                if (result != null && result.first == 0) {
                    setPluginsStatus("'$name' installed successfully.")
                    loadPlugins()
                } else {
                    val errorMsg = result?.second?.trim() ?: "Unknown error"
                    setPluginsStatus("Failed to install '$name': $errorMsg")
                }
            }
        }
    }

    private fun togglePlugin(name: String, enable: Boolean) {
        val action = if (enable) "enable" else "disable"
        setPluginsStatus("${action.replaceFirstChar { it.uppercase() }}ing '$name'...")
        showRestartBanner()
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val result = cliManager.runCliCommandWithExitCode(
                listOf("plugins", action, name)
            )

            SwingUtilities.invokeLater {
                if (result != null && result.first == 0) {
                    setPluginsStatus(
                        "'$name' ${action}d successfully."
                    )
                    loadPlugins()
                } else {
                    val errorMsg = result?.second?.trim() ?: "Unknown error"
                    setPluginsStatus("Failed to $action '$name': $errorMsg")
                }
            }
        }
    }

    private fun loadMarketplaces() {
        setMarketplacesStatus("Loading marketplaces...")
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val result = cliManager.runCliCommand(
                listOf("plugins", "marketplaces", "list")
            )

            val parsed = if (result != null) {
                parseMarketplaceList(result)
            } else {
                emptyList()
            }

            SwingUtilities.invokeLater {
                marketplaces = parsed
                renderMarketplaceList()
                setMarketplacesStatus(
                    if (parsed.isEmpty()) "No marketplaces configured."
                    else "${parsed.size} marketplace(s) loaded."
                )
            }
        }
    }

    private fun addMarketplace(source: String) {
        setMarketplacesStatus("Adding marketplace '$source'...")
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val result = cliManager.runCliCommandWithExitCode(
                listOf("plugins", "marketplaces", "add", source)
            )

            SwingUtilities.invokeLater {
                if (result != null && result.first == 0) {
                    setMarketplacesStatus("Marketplace added successfully.")
                    loadMarketplaces()
                    loadPlugins() // Refresh available plugins
                } else {
                    val errorMsg = result?.second?.trim() ?: "Unknown error"
                    setMarketplacesStatus("Failed to add marketplace: $errorMsg")
                }
            }
        }
    }

    private fun refreshMarketplace(url: String) {
        setMarketplacesStatus("Refreshing marketplace...")
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val result = cliManager.runCliCommandWithExitCode(
                listOf("plugins", "marketplaces", "refresh", url)
            )

            SwingUtilities.invokeLater {
                if (result != null && result.first == 0) {
                    setMarketplacesStatus("Marketplace refreshed.")
                    loadPlugins() // Refresh available plugins
                } else {
                    val errorMsg = result?.second?.trim() ?: "Unknown error"
                    setMarketplacesStatus("Failed to refresh: $errorMsg")
                }
            }
        }
    }

    private fun removeMarketplace(url: String) {
        val confirmed = Messages.showOkCancelDialog(
            project,
            "Remove marketplace source?\n$url",
            "Remove Marketplace",
            "Remove",
            "Cancel",
            Messages.getQuestionIcon()
        )
        if (confirmed != Messages.OK) return

        setMarketplacesStatus("Removing marketplace...")
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val result = cliManager.runCliCommandWithExitCode(
                listOf("plugins", "marketplaces", "remove", url)
            )

            SwingUtilities.invokeLater {
                if (result != null && result.first == 0) {
                    setMarketplacesStatus("Marketplace removed.")
                    loadMarketplaces()
                    loadPlugins()
                } else {
                    val errorMsg = result?.second?.trim() ?: "Unknown error"
                    setMarketplacesStatus("Failed to remove: $errorMsg")
                }
            }
        }
    }

    private fun filterPlugins() {
        renderPluginLists()
    }

    // ── Status Helpers ──────────────────────────────────────────

    private fun setPluginsStatus(text: String) {
        if (SwingUtilities.isEventDispatchThread()) {
            pluginsStatusLabel.text = text
        } else {
            SwingUtilities.invokeLater { pluginsStatusLabel.text = text }
        }
    }

    private fun setMarketplacesStatus(text: String) {
        if (SwingUtilities.isEventDispatchThread()) {
            marketplacesStatusLabel.text = text
        } else {
            SwingUtilities.invokeLater { marketplacesStatusLabel.text = text }
        }
    }

    private fun showRestartBanner() {
        restartRequired = true
        if (SwingUtilities.isEventDispatchThread()) {
            restartBanner.isVisible = true
        } else {
            SwingUtilities.invokeLater { restartBanner.isVisible = true }
        }
    }

    private fun truncate(text: String, maxLen: Int): String {
        return if (text.length <= maxLen) text
        else text.substring(0, maxLen - 3) + "..."
    }

    // ── JSON Parsing ────────────────────────────────────────────

    private fun parsePluginList(output: String): List<PluginInfo> {
        val result = mutableListOf<PluginInfo>()
        try {
            val json = JsonParser.parseString(output)
            if (json.isJsonArray) {
                for (element in json.asJsonArray) {
                    if (!element.isJsonObject) continue
                    val obj = element.asJsonObject
                    result.add(
                        PluginInfo(
                            name = obj.get("name")?.asString ?: continue,
                            description = obj.get("description")?.asString
                                ?: "",
                            version = obj.get("version")?.asString ?: "",
                            enabled = obj.get("enabled")?.asBoolean ?: true,
                            installed = obj.get("installed")?.asBoolean
                                ?: false,
                            source = obj.get("source")?.asString
                        )
                    )
                }
            } else if (json.isJsonObject) {
                // Format: { "pluginName": { ... } }
                for ((name, value) in json.asJsonObject.entrySet()) {
                    if (!value.isJsonObject) continue
                    val obj = value.asJsonObject
                    result.add(
                        PluginInfo(
                            name = name,
                            description = obj.get("description")?.asString
                                ?: "",
                            version = obj.get("version")?.asString ?: "",
                            enabled = obj.get("enabled")?.asBoolean ?: true,
                            installed = obj.get("installed")?.asBoolean
                                ?: true,
                            source = obj.get("source")?.asString
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse plugin list", e)
            // Fallback: treat each line as a plugin name
            for (line in output.lines()) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    result.add(
                        PluginInfo(
                            name = trimmed,
                            description = "",
                            version = "",
                            enabled = true,
                            installed = true,
                            source = null
                        )
                    )
                }
            }
        }
        return result
    }

    private fun parseMarketplaceList(output: String): List<MarketplaceSource> {
        val result = mutableListOf<MarketplaceSource>()
        try {
            val json = JsonParser.parseString(output)
            if (json.isJsonArray) {
                for (element in json.asJsonArray) {
                    if (element.isJsonPrimitive) {
                        result.add(MarketplaceSource(
                            url = element.asString,
                            name = null
                        ))
                    } else if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        result.add(MarketplaceSource(
                            url = obj.get("url")?.asString
                                ?: obj.get("source")?.asString ?: continue,
                            name = obj.get("name")?.asString
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse marketplace list", e)
            // Fallback: treat each line as a URL
            for (line in output.lines()) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    result.add(MarketplaceSource(url = trimmed, name = null))
                }
            }
        }
        return result
    }

    override fun createActions(): Array<javax.swing.Action> {
        return arrayOf(okAction)
    }

    override fun getDimensionServiceKey(): String =
        "ClaudeCode.PluginManagerDialog"
}
