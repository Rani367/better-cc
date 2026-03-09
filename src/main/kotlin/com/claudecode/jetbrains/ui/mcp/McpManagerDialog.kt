package com.claudecode.jetbrains.ui.mcp

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

data class McpServerInfo(
    val name: String,
    val type: String,
    val command: String?,
    val url: String?,
    val args: List<String>,
    val status: McpServerStatus
)

enum class McpServerStatus(val displayName: String) {
    CONNECTED("Connected"),
    DISCONNECTED("Disconnected"),
    ERROR("Error"),
    UNKNOWN("Unknown")
}

enum class McpTransportType(val cliValue: String, val displayName: String) {
    STDIO("stdio", "stdio"),
    HTTP("http", "http"),
    SSE("sse", "sse");

    override fun toString(): String = displayName
}

// ── Dialog ──────────────────────────────────────────────────────

class McpManagerDialog(
    private val project: Project
) : DialogWrapper(project, true) {

    private val logger = Logger.getInstance(McpManagerDialog::class.java)
    private val serverListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val statusLabel = JBLabel(" ")
    private var servers: List<McpServerInfo> = emptyList()

    init {
        title = "MCP Server Management"
        setOKButtonText("Close")
        setCancelButtonText("Close")
        init()
        loadServers()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, 12))
        mainPanel.preferredSize = Dimension(600, 500)
        mainPanel.border = JBUI.Borders.empty(8)

        // Header
        val headerLabel = JBLabel("Configured MCP Servers").apply {
            font = font.deriveFont(Font.BOLD, 14f)
        }

        // Server list area
        val scrollPane = JBScrollPane(serverListPanel).apply {
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(580, 250)
        }

        // Top section: header + refresh
        val topRow = JPanel(BorderLayout()).apply {
            add(headerLabel, BorderLayout.WEST)
            val refreshButton = JButton("Refresh").apply {
                addActionListener { loadServers() }
            }
            add(refreshButton, BorderLayout.EAST)
        }

        // Add Server section
        val addPanel = createAddServerPanel()

        // Status bar
        statusLabel.border = JBUI.Borders.emptyTop(4)

        // Assemble
        val topSection = JPanel(BorderLayout(0, 8)).apply {
            add(topRow, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        mainPanel.add(topSection, BorderLayout.CENTER)

        val bottomSection = JPanel(BorderLayout(0, 4)).apply {
            add(addPanel, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }
        mainPanel.add(bottomSection, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun createAddServerPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("Add Server")

        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
        }

        // Name
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        panel.add(JBLabel("Name:"), gbc)

        val nameField = JBTextField().apply {
            columns = 20
        }
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        panel.add(nameField, gbc)

        // Transport type
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.gridwidth = 1
        panel.add(JBLabel("Transport:"), gbc)

        val transportCombo = JComboBox(
            DefaultComboBoxModel(McpTransportType.entries.toTypedArray())
        )
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        panel.add(transportCombo, gbc)

        // Command/URL
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.gridwidth = 1
        val commandLabel = JBLabel("Command:")
        panel.add(commandLabel, gbc)

        val commandField = JBTextField().apply {
            columns = 30
        }
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        panel.add(commandField, gbc)

        // Update label when transport changes
        transportCombo.addActionListener {
            val selected = transportCombo.selectedItem as? McpTransportType
            commandLabel.text = when (selected) {
                McpTransportType.STDIO -> "Command:"
                McpTransportType.HTTP, McpTransportType.SSE -> "URL:"
                null -> "Command:"
            }
        }

        // Add button
        val addButton = JButton("Add Server")
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.EAST
        panel.add(addButton, gbc)

        addButton.addActionListener {
            val name = nameField.text?.trim() ?: ""
            val transport = transportCombo.selectedItem as? McpTransportType
                ?: McpTransportType.STDIO
            val commandOrUrl = commandField.text?.trim() ?: ""

            if (name.isEmpty()) {
                setStatus("Server name is required.")
                return@addActionListener
            }
            if (commandOrUrl.isEmpty()) {
                setStatus("Command/URL is required.")
                return@addActionListener
            }

            addServer(name, transport, commandOrUrl)
            nameField.text = ""
            commandField.text = ""
        }

        return panel
    }

    private fun renderServerList() {
        serverListPanel.removeAll()

        if (servers.isEmpty()) {
            val emptyLabel = JBLabel("No MCP servers configured.").apply {
                alignmentX = Component.CENTER_ALIGNMENT
                border = JBUI.Borders.empty(20, 0, 20, 0)
            }
            serverListPanel.add(emptyLabel)
        } else {
            for (server in servers) {
                serverListPanel.add(createServerCard(server))
                serverListPanel.add(Box.createVerticalStrut(4))
            }
        }

        serverListPanel.revalidate()
        serverListPanel.repaint()
    }

    private fun createServerCard(server: McpServerInfo): JPanel {
        val card = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(8, 12, 8, 12)
            )
            maximumSize = Dimension(Int.MAX_VALUE, 60)
        }

        // Left: status indicator + name + type
        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))

        val statusDot = JBLabel(getStatusSymbol(server.status)).apply {
            foreground = getStatusColor(server.status)
            toolTipText = server.status.displayName
            font = font.deriveFont(Font.BOLD)
        }
        infoPanel.add(statusDot)

        val nameLabel = JBLabel(server.name).apply {
            font = font.deriveFont(Font.BOLD)
        }
        infoPanel.add(nameLabel)

        val typeLabel = JBLabel("(${server.type})").apply {
            foreground = JBColor.GRAY
        }
        infoPanel.add(typeLabel)

        // Show command or URL
        val detailText = server.command ?: server.url ?: ""
        if (detailText.isNotEmpty()) {
            val detailLabel = JBLabel(truncate(detailText, 40)).apply {
                foreground = JBColor.GRAY
                toolTipText = detailText
            }
            infoPanel.add(detailLabel)
        }

        card.add(infoPanel, BorderLayout.CENTER)

        // Right: action buttons
        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))

        val reconnectButton = JButton("Reconnect").apply {
            addActionListener { reconnectServer(server.name) }
        }
        actionsPanel.add(reconnectButton)

        val removeButton = JButton("Remove").apply {
            addActionListener { removeServer(server.name) }
        }
        actionsPanel.add(removeButton)

        card.add(actionsPanel, BorderLayout.EAST)

        return card
    }

    private fun getStatusSymbol(status: McpServerStatus): String {
        return when (status) {
            McpServerStatus.CONNECTED -> "\u25CF"    // filled circle
            McpServerStatus.DISCONNECTED -> "\u25CB"  // empty circle
            McpServerStatus.ERROR -> "\u25CF"         // filled circle (red)
            McpServerStatus.UNKNOWN -> "\u25CB"       // empty circle
        }
    }

    private fun getStatusColor(status: McpServerStatus): JBColor {
        return when (status) {
            McpServerStatus.CONNECTED -> JBColor(
                java.awt.Color(0, 150, 0),
                java.awt.Color(80, 200, 80)
            )
            McpServerStatus.DISCONNECTED -> JBColor.GRAY
            McpServerStatus.ERROR -> JBColor(
                java.awt.Color(200, 0, 0),
                java.awt.Color(255, 80, 80)
            )
            McpServerStatus.UNKNOWN -> JBColor.GRAY
        }
    }

    private fun truncate(text: String, maxLen: Int): String {
        return if (text.length <= maxLen) text
        else text.substring(0, maxLen - 3) + "..."
    }

    // ── CLI Operations (run off-EDT) ────────────────────────────

    private fun loadServers() {
        setStatus("Loading MCP servers...")
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val result = cliManager.runCliCommand(listOf("mcp", "list"))

            val parsed = if (result != null) {
                parseMcpServerList(result)
            } else {
                emptyList()
            }

            SwingUtilities.invokeLater {
                servers = parsed
                renderServerList()
                setStatus(
                    if (parsed.isEmpty()) "No MCP servers found."
                    else "${parsed.size} server(s) loaded."
                )
            }
        }
    }

    private fun addServer(
        name: String,
        transport: McpTransportType,
        commandOrUrl: String
    ) {
        setStatus("Adding server '$name'...")
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)

            val args = mutableListOf("mcp", "add", name)

            when (transport) {
                McpTransportType.STDIO -> {
                    // For stdio, the remaining args are the command and its arguments
                    // Split commandOrUrl by spaces to get command + args
                    val parts = commandOrUrl.split("\\s+".toRegex())
                    args.addAll(parts)
                }
                McpTransportType.HTTP -> {
                    args.addAll(listOf("--transport", "http", commandOrUrl))
                }
                McpTransportType.SSE -> {
                    args.addAll(listOf("--transport", "sse", commandOrUrl))
                }
            }

            val result = cliManager.runCliCommandWithExitCode(args)

            SwingUtilities.invokeLater {
                if (result != null && result.first == 0) {
                    setStatus("Server '$name' added successfully.")
                    loadServers()
                } else {
                    val errorMsg = result?.second?.trim() ?: "Unknown error"
                    setStatus("Failed to add server: $errorMsg")
                }
            }
        }
    }

    private fun removeServer(name: String) {
        val confirmed = Messages.showOkCancelDialog(
            project,
            "Are you sure you want to remove MCP server '$name'?",
            "Remove MCP Server",
            "Remove",
            "Cancel",
            Messages.getQuestionIcon()
        )
        if (confirmed != Messages.OK) return

        setStatus("Removing server '$name'...")
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val result = cliManager.runCliCommandWithExitCode(
                listOf("mcp", "remove", name)
            )

            SwingUtilities.invokeLater {
                if (result != null && result.first == 0) {
                    setStatus("Server '$name' removed.")
                    loadServers()
                } else {
                    val errorMsg = result?.second?.trim() ?: "Unknown error"
                    setStatus("Failed to remove server: $errorMsg")
                }
            }
        }
    }

    private fun reconnectServer(name: String) {
        // Reconnect is done by sending the command to the CLI via the chat
        // For now, we indicate it's been requested
        setStatus("Reconnect requested for '$name'. Use /mcp in chat to reconnect.")
    }

    private fun setStatus(text: String) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.text = text
        } else {
            SwingUtilities.invokeLater { statusLabel.text = text }
        }
    }

    // ── JSON Parsing ────────────────────────────────────────────

    private fun parseMcpServerList(output: String): List<McpServerInfo> {
        val result = mutableListOf<McpServerInfo>()
        try {
            val json = JsonParser.parseString(output)
            if (json.isJsonObject) {
                // Format: { "serverName": { "type": "...", "command": "...", ... } }
                for ((name, value) in json.asJsonObject.entrySet()) {
                    if (!value.isJsonObject) continue
                    val obj = value.asJsonObject
                    val type = obj.get("type")?.asString ?: "stdio"
                    val command = obj.get("command")?.asString
                    val url = obj.get("url")?.asString
                    val args = mutableListOf<String>()
                    val argsArr = obj.getAsJsonArray("args")
                    if (argsArr != null) {
                        for (arg in argsArr) {
                            args.add(arg.asString)
                        }
                    }

                    // Status is typically not in the list output;
                    // default to UNKNOWN
                    result.add(
                        McpServerInfo(
                            name = name,
                            type = type,
                            command = command,
                            url = url,
                            args = args,
                            status = McpServerStatus.UNKNOWN
                        )
                    )
                }
            } else if (json.isJsonArray) {
                // Alternate format: array of objects
                for (element in json.asJsonArray) {
                    if (!element.isJsonObject) continue
                    val obj = element.asJsonObject
                    val name = obj.get("name")?.asString ?: continue
                    val type = obj.get("type")?.asString ?: "stdio"
                    val command = obj.get("command")?.asString
                    val url = obj.get("url")?.asString
                    val args = mutableListOf<String>()
                    val argsArr = obj.getAsJsonArray("args")
                    if (argsArr != null) {
                        for (arg in argsArr) {
                            args.add(arg.asString)
                        }
                    }
                    val statusStr = obj.get("status")?.asString
                    val status = when (statusStr) {
                        "connected" -> McpServerStatus.CONNECTED
                        "disconnected" -> McpServerStatus.DISCONNECTED
                        "error" -> McpServerStatus.ERROR
                        else -> McpServerStatus.UNKNOWN
                    }
                    result.add(
                        McpServerInfo(
                            name = name,
                            type = type,
                            command = command,
                            url = url,
                            args = args,
                            status = status
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse MCP server list", e)
            // Try line-by-line parsing as fallback
            parseLineBased(output, result)
        }
        return result
    }

    private fun parseLineBased(output: String, result: MutableList<McpServerInfo>) {
        // Fallback: parse simple line-based output
        // Example: "server-name (stdio): command args"
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Try to extract server name from line
            val parenIdx = trimmed.indexOf('(')
            if (parenIdx > 0) {
                val name = trimmed.substring(0, parenIdx).trim()
                val rest = trimmed.substring(parenIdx)
                val closeParen = rest.indexOf(')')
                val type = if (closeParen > 1) {
                    rest.substring(1, closeParen)
                } else {
                    "stdio"
                }
                val detail = if (closeParen + 1 < rest.length) {
                    rest.substring(closeParen + 1).trimStart(':', ' ')
                } else {
                    ""
                }
                result.add(
                    McpServerInfo(
                        name = name,
                        type = type,
                        command = detail.ifEmpty { null },
                        url = null,
                        args = emptyList(),
                        status = McpServerStatus.UNKNOWN
                    )
                )
            }
        }
    }

    override fun createActions(): Array<javax.swing.Action> {
        return arrayOf(okAction)
    }

    override fun getDimensionServiceKey(): String = "ClaudeCode.McpManagerDialog"
}
