package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.context.SelectionContextProvider
import com.claudecode.jetbrains.ui.commands.SlashCommand
import com.claudecode.jetbrains.cli.AssistantMessageEvent
import com.claudecode.jetbrains.cli.ClaudeCliManager
import com.claudecode.jetbrains.cli.ClaudeProcess
import com.claudecode.jetbrains.cli.ContentBlockDelta
import com.claudecode.jetbrains.cli.ContentBlockStart
import com.claudecode.jetbrains.cli.ContentBlockStop
import com.claudecode.jetbrains.cli.InputJsonDelta
import com.claudecode.jetbrains.cli.PermissionMcpServer
import com.claudecode.jetbrains.cli.PermissionRequest
import com.claudecode.jetbrains.cli.PermissionResponse
import com.claudecode.jetbrains.cli.ResultEvent
import com.claudecode.jetbrains.cli.SessionManager
import com.claudecode.jetbrains.cli.StreamEventWrapper
import com.claudecode.jetbrains.cli.SystemEvent
import com.claudecode.jetbrains.cli.TextDelta
import com.claudecode.jetbrains.cli.ToolUseBlock
import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.settings.PermissionMode
import com.claudecode.jetbrains.ui.diff.DiffDecision
import com.claudecode.jetbrains.ui.diff.DiffPreviewPanel
import com.claudecode.jetbrains.ui.diff.DiffViewerDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.intellij.ide.ui.LafManagerListener
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel

class ChatToolWindow(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(ChatToolWindow::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val messageList = MessageList(project, this)
    private val inputPanel = InputPanel(project, ::sendMessage)
    private val selectionContextProvider = SelectionContextProvider(project, this)
    private val rootPanel = JPanel(BorderLayout()).apply {
        add(messageList, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)
    }

    // Process management
    private var claudeProcess: ClaudeProcess? = null
    private var eventCollectorJob: Job? = null
    private var currentSessionId: String? = null

    // Streaming update — each delta schedules invokeLater; redundant ones are no-ops
    private val pendingUpdate = AtomicReference<Pair<String, String>?>(null)

    // Track current streaming message
    private var streamingMessageId: String? = null
    private val accumulatedText = StringBuilder()

    // Tool use tracking
    private val activeToolUses = mutableMapOf<Int, ToolUseState>()
    private val pendingToolUpdate = AtomicReference<ToolUseState?>(null)

    // Permission system
    private var permissionServer: PermissionMcpServer? = null

    init {
        project.putUserData(KEY, this)

        // Wire permission response handler
        messageList.setPermissionResponseHandler { requestId, decision ->
            handlePermissionResponse(requestId, decision)
        }

        // Wire slash command handler
        inputPanel.setSlashCommandHandler(::handleSlashCommand)

        // Wire selection context
        selectionContextProvider.setOnChangeListener { context ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    inputPanel.updateSelectionContext(context)
                }
            }
        }
        inputPanel.setSelectionToggleHandler { visible ->
            selectionContextProvider.isSelectionVisible = visible
        }

        // Listen for theme changes
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener {
                messageList.applyTheme()
            })
    }

    fun getContent(): JPanel = rootPanel

    fun focusInput() {
        inputPanel.focus()
    }

    /**
     * Inserts text at the current cursor position in the input panel.
     * Used by InsertFileRefAction to inject @file references.
     */
    fun insertTextAtCursor(text: String) {
        inputPanel.insertTextAtCursor(text)
    }

    /**
     * Set text in the input panel without sending. The user can review and
     * press Enter.
     */
    fun prefillInput(text: String) {
        inputPanel.setText(text)
        inputPanel.focus()
    }

    /**
     * Send a message programmatically (e.g., from editor context actions).
     */
    fun sendPrefilledMessage(text: String) {
        sendMessage(text)
    }

    private fun handleSlashCommand(command: SlashCommand) {
        if (command.name == "/clear") {
            messageList.clearMessages()
        }
        if (command.name == "/settings" || command.name == "/config") {
            openSettings()
            return
        }
        sendMessage(command.name)
    }

    private fun openSettings() {
        com.intellij.openapi.options.ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, "Claude Code")
    }

    private fun sendMessage(text: String) {
        val userMessage = ChatMessage(MessageSender.USER, text)
        messageList.addMessage(userMessage)
        inputPanel.setInputEnabled(false)
        messageList.showThinking(true)

        scope.launch {
            try {
                val cliManager = ClaudeCliManager.getInstance(project)
                val cliPath = cliManager.getCliPath()

                if (cliPath == null) {
                    showError("Claude Code CLI not found. Install it or configure the path in settings.")
                    return@launch
                }

                val workingDir = project.basePath ?: System.getProperty("user.dir")

                // Create or reuse process
                val process = getOrCreateProcess(cliPath, workingDir) ?: return@launch

                // Prepare streaming message
                val assistantMessage = ChatMessage(MessageSender.ASSISTANT, "")
                streamingMessageId = assistantMessage.id
                accumulatedText.clear()

                // Send the message
                try {
                    process.sendMessage(text)
                } catch (e: Exception) {
                    if (project.isDisposed) return@launch
                    logger.warn("Failed to send message to Claude", e)
                    showError("Failed to send message: ${e.message}")
                    destroyProcess()
                }
            } catch (e: Exception) {
                if (project.isDisposed) return@launch
                logger.warn("Error in sendMessage", e)
                showError("Failed to communicate with Claude: ${e.message}")
            }
        }
    }

    private suspend fun getOrCreateProcess(cliPath: String, workingDir: String): ClaudeProcess? {
        val existing = claudeProcess
        if (existing != null && existing.isRunning) {
            return existing
        }

        // Clean up dead process
        if (existing != null) {
            destroyProcess()
        }

        // Create a new session
        val sessionManager = SessionManager.getInstance(project)
        val session = sessionManager.createSession()
        currentSessionId = session.id

        return try {
            // Start permission MCP server
            val server = PermissionMcpServer()
            server.onPermissionRequest = { request ->
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        messageList.addPermissionCard(request)
                    }
                }
            }
            server.start()
            permissionServer = server

            val settings = ClaudeSettings.getInstance()
            val mode = settings.permissionMode

            val model = settings.selectedModel
            val envVars = settings.environmentVariables

            val process = ClaudeProcess.start(
                cliPath,
                workingDir,
                session.id,
                permissionMode = mode.cliValue,
                mcpServerPort = server.port,
                model = model,
                environmentVariables = envVars
            )
            claudeProcess = process
            sessionManager.registerProcess(session.id, process)

            // Start collecting events
            launchEventCollector(process)

            process
        } catch (e: Exception) {
            logger.warn("Failed to start Claude process", e)
            showError("Failed to start Claude: ${e.message}")
            null
        }
    }

    private fun launchEventCollector(process: ClaudeProcess) {
        eventCollectorJob?.cancel()
        eventCollectorJob = scope.launch {
            process.events()
                .catch { e ->
                    if (!project.isDisposed) {
                        logger.warn("Event stream error", e)
                    }
                }
                .collect { event ->
                    if (project.isDisposed) return@collect

                    when (event) {
                        is SystemEvent -> {
                            if (event.sessionId != null) {
                                currentSessionId = event.sessionId
                            }
                        }

                        is StreamEventWrapper -> {
                            val inner = event.event

                            when (inner) {
                                is ContentBlockStart -> {
                                    val block = inner.contentBlock
                                    if (block is ToolUseBlock) {
                                        // Finalize any in-progress text segment before inserting tool block
                                        val currentMsgId = streamingMessageId
                                        val currentText = accumulatedText.toString()

                                        streamingMessageId = null
                                        accumulatedText.clear()

                                        val tool = ToolUseState(
                                            toolUseId = block.id,
                                            toolName = block.name
                                        )
                                        activeToolUses[inner.index] = tool

                                        ApplicationManager.getApplication().invokeLater {
                                            if (project.isDisposed) return@invokeLater
                                            // Finalize prior text segment
                                            if (currentMsgId != null && currentText.isNotBlank()) {
                                                messageList.updateStreamingMessage(currentMsgId, currentText)
                                                messageList.finalizeMessage(currentMsgId)
                                            }
                                            messageList.showThinking(false)
                                            messageList.addToolBlock(tool)
                                        }
                                    }
                                }

                                is ContentBlockDelta -> {
                                    val delta = inner.delta
                                    when (delta) {
                                        is TextDelta -> {
                                            if (delta.text.isNotEmpty()) {
                                                accumulatedText.append(delta.text)

                                                // Lazily create a new streaming message after a tool block
                                                if (streamingMessageId == null) {
                                                    val newMsg = ChatMessage(MessageSender.ASSISTANT, "")
                                                    streamingMessageId = newMsg.id
                                                }
                                                val msgId = streamingMessageId ?: return@collect

                                                // Hide thinking on first content
                                                if (accumulatedText.length == delta.text.length) {
                                                    ApplicationManager.getApplication().invokeLater {
                                                        if (!project.isDisposed) {
                                                            messageList.showThinking(false)
                                                        }
                                                    }
                                                }

                                                scheduleStreamingUpdate(msgId, accumulatedText.toString())
                                            }
                                        }

                                        is InputJsonDelta -> {
                                            val tool = activeToolUses[inner.index]
                                            if (tool != null) {
                                                tool.inputJson.append(delta.partialJson)
                                                scheduleToolUpdate(tool)
                                            }
                                        }

                                        else -> { /* ignore other deltas */ }
                                    }
                                }

                                is ContentBlockStop -> {
                                    val tool = activeToolUses.remove(inner.index)
                                    if (tool != null) {
                                        tool.status = ToolUseStatus.COMPLETE
                                        pendingToolUpdate.set(null) // clear any pending throttled update
                                        ApplicationManager.getApplication().invokeLater {
                                            if (!project.isDisposed) {
                                                messageList.updateToolBlock(tool)
                                                messageList.setToolBlockBody(tool)
                                            }
                                        }
                                    }
                                }

                                else -> { /* ignore other inner events */ }
                            }
                        }

                        is AssistantMessageEvent -> {
                            // Use cumulative text from assistant events as correction
                            val text = event.message?.getTextContent() ?: return@collect
                            if (text.isBlank()) return@collect
                            val msgId = streamingMessageId ?: return@collect

                            accumulatedText.clear()
                            accumulatedText.append(text)

                            ApplicationManager.getApplication().invokeLater {
                                if (!project.isDisposed) {
                                    messageList.showThinking(false)
                                }
                            }

                            scheduleStreamingUpdate(msgId, text)
                        }

                        is ResultEvent -> {
                            if (event.sessionId != null) {
                                currentSessionId = event.sessionId
                            }

                            val msgId = streamingMessageId
                            streamingMessageId = null
                            accumulatedText.clear()

                            // Complete any remaining active tool blocks
                            val remainingTools = activeToolUses.values.toList()
                            activeToolUses.clear()
                            pendingToolUpdate.set(null)

                            // Extract final text
                            val finalText = event.content
                                .filter { it.type == "text" }
                                .mapNotNull { it.text }
                                .joinToString("\n")

                            // Flush any pending update and finalize
                            pendingUpdate.set(null)

                            ApplicationManager.getApplication().invokeLater {
                                if (project.isDisposed) return@invokeLater

                                // Mark remaining tools as complete
                                for (tool in remainingTools) {
                                    tool.status = ToolUseStatus.COMPLETE
                                    messageList.updateToolBlock(tool)
                                    messageList.setToolBlockBody(tool)
                                }

                                messageList.showThinking(false)

                                if (msgId != null && finalText.isNotBlank()) {
                                    messageList.updateStreamingMessage(msgId, finalText)
                                    messageList.finalizeMessage(msgId)
                                } else if (finalText.isNotBlank()) {
                                    messageList.addMessage(
                                        ChatMessage(MessageSender.ASSISTANT, finalText)
                                    )
                                }

                                inputPanel.setInputEnabled(true)
                                inputPanel.focus()
                            }
                        }

                        else -> {
                            // Ignore other events for now
                        }
                    }
                }

            // Flow completed — process ended
            if (!project.isDisposed) {
                val msgId = streamingMessageId
                streamingMessageId = null

                // If we were streaming, finalize
                if (msgId != null) {
                    val lastUpdate = pendingUpdate.getAndSet(null)
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        if (lastUpdate != null) {
                            messageList.updateStreamingMessage(lastUpdate.first, lastUpdate.second)
                        }
                        messageList.finalizeMessage(msgId)
                        messageList.showThinking(false)
                        inputPanel.setInputEnabled(true)
                        inputPanel.focus()
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        messageList.showThinking(false)
                        inputPanel.setInputEnabled(true)
                    }
                }

                claudeProcess = null
            }
        }
    }

    /**
     * Schedules a streaming UI update. Each delta posts an invokeLater.
     * Multiple pending invokeLaters naturally deduplicate via getAndSet(null) —
     * the first one renders the latest text, the rest find null and return immediately.
     */
    private fun scheduleStreamingUpdate(msgId: String, text: String) {
        pendingUpdate.set(Pair(msgId, text))
        ApplicationManager.getApplication().invokeLater {
            val update = pendingUpdate.getAndSet(null) ?: return@invokeLater
            if (!project.isDisposed) {
                messageList.updateStreamingMessage(update.first, update.second)
            }
        }
    }

    private fun scheduleToolUpdate(tool: ToolUseState) {
        pendingToolUpdate.set(tool)
        ApplicationManager.getApplication().invokeLater {
            val update = pendingToolUpdate.getAndSet(null) ?: return@invokeLater
            if (!project.isDisposed) {
                messageList.updateToolBlock(update)
            }
        }
    }

    private fun showError(text: String) {
        streamingMessageId = null
        pendingUpdate.set(null)
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                messageList.showThinking(false)
                messageList.addMessage(ChatMessage(MessageSender.ERROR, text))
                inputPanel.setInputEnabled(true)
                inputPanel.focus()
            }
        }
    }

    private fun handlePermissionResponse(requestId: String, decision: String) {
        val server = permissionServer ?: return
        when (decision) {
            "allow", "allowSession" -> {
                val addSessionApproval = decision == "allowSession"
                val request = server.getOriginalRequest(requestId)
                if (request != null && isFileModificationTool(request.toolName)) {
                    showDiffAndResolve(requestId, request, addSessionApproval)
                } else {
                    if (addSessionApproval) server.addSessionApproval(requestId)
                    server.resolvePermission(requestId, PermissionResponse("allow"))
                }
            }
            "deny" -> server.resolvePermission(requestId, PermissionResponse("deny", "User denied permission"))
        }
    }

    private fun showDiffAndResolve(requestId: String, request: PermissionRequest, addSessionApproval: Boolean) {
        val server = permissionServer ?: return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val diffData = DiffPreviewPanel.buildDiffData(project, request)
            if (diffData == null) {
                // Can't build diff — fall back to direct allow
                if (addSessionApproval) server.addSessionApproval(requestId)
                server.resolvePermission(requestId, PermissionResponse("allow"))
                return@invokeLater
            }

            val dialog = DiffViewerDialog(project, diffData)
            dialog.show()

            when (dialog.decision) {
                DiffDecision.ACCEPT -> {
                    if (addSessionApproval) server.addSessionApproval(requestId)
                    server.resolvePermission(requestId, PermissionResponse("allow"))
                }
                DiffDecision.REJECT -> {
                    server.resolvePermission(requestId, PermissionResponse("deny", "User rejected changes in diff viewer"))
                }
            }
        }
    }

    private fun isFileModificationTool(toolName: String): Boolean {
        return toolName == "Write" || toolName == "Edit"
    }

    private fun destroyProcess() {
        val process = claudeProcess
        claudeProcess = null
        eventCollectorJob?.cancel()
        eventCollectorJob = null

        // Stop permission server
        permissionServer?.stop()
        permissionServer = null

        if (process != null) {
            val sessionId = currentSessionId
            process.destroy()
            if (sessionId != null) {
                try {
                    SessionManager.getInstance(project).unregisterProcess(sessionId)
                } catch (_: Exception) {
                    // Project may be disposed
                }
            }
        }
    }

    override fun dispose() {
        project.putUserData(KEY, null)
        destroyProcess()
        scope.cancel()
    }

    companion object {
        val KEY: Key<ChatToolWindow> = Key.create("ClaudeCode.ChatToolWindow")
    }
}
