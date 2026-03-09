package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.checkpoint.CheckpointManager
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
import com.claudecode.jetbrains.cli.SessionInfo
import com.claudecode.jetbrains.cli.SessionManager
import com.claudecode.jetbrains.cli.StreamEventWrapper
import com.claudecode.jetbrains.cli.SystemEvent
import com.claudecode.jetbrains.cli.TextDelta
import com.claudecode.jetbrains.cli.ToolUseBlock
import com.claudecode.jetbrains.context.SelectionContextProvider
import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.ui.commands.SlashCommand
import com.claudecode.jetbrains.ui.diff.DiffDecision
import com.claudecode.jetbrains.ui.diff.DiffPreviewPanel
import com.claudecode.jetbrains.ui.diff.DiffViewerDialog
import com.claudecode.jetbrains.ui.sessions.SessionHistoryPanel
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class ChatToolWindow(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(ChatToolWindow::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val messageList = MessageList(project, this)
    private val inputPanel = InputPanel(project, ::sendMessage)
    private val selectionContextProvider =
        SelectionContextProvider(project, this)

    // ── Session header bar ────────────────────────────────────
    private val sessionTitleLabel = JBLabel("New Conversation").apply {
        font = font.deriveFont(Font.BOLD, 12f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Click to browse past conversations"
    }

    private val resumedIndicator = JBLabel("Resumed").apply {
        font = font.deriveFont(Font.ITALIC, 11f)
        foreground = JBColor.namedColor(
            "Label.disabledForeground",
            JBColor.GRAY
        )
        isVisible = false
        border = BorderFactory.createEmptyBorder(0, 6, 0, 0)
    }

    private val newConversationButton = JButton(AllIcons.General.Add).apply {
        toolTipText = "New Conversation"
        isBorderPainted = false
        isContentAreaFilled = false
        addActionListener { startNewConversation() }
    }

    private val headerPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(6, 10, 6, 6)
        )
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(sessionTitleLabel)
            add(resumedIndicator)
        }
        add(leftPanel, BorderLayout.CENTER)
        add(newConversationButton, BorderLayout.EAST)
    }

    private val rootPanel = JPanel(BorderLayout()).apply {
        add(headerPanel, BorderLayout.NORTH)
        add(messageList, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)
    }

    // Process management
    private var claudeProcess: ClaudeProcess? = null
    private var eventCollectorJob: Job? = null
    private var currentSessionId: String? = null
    private var isResumedSession = false

    // Streaming update
    private val pendingUpdate =
        AtomicReference<Pair<String, String>?>(null)

    // Track current streaming message
    private var streamingMessageId: String? = null
    private val accumulatedText = StringBuilder()

    // Tool use tracking
    private val activeToolUses = mutableMapOf<Int, ToolUseState>()
    private val pendingToolUpdate = AtomicReference<ToolUseState?>(null)

    // Permission system
    private var permissionServer: PermissionMcpServer? = null

    // Checkpoint / rewind
    private val checkpointManager =
        CheckpointManager.getInstance(project)
    private var messageIndexCounter = 0
    private val conversationMessages =
        mutableListOf<ChatMessage>()

    init {
        project.putUserData(KEY, this)

        // Wire permission response handler
        messageList.setPermissionResponseHandler { requestId, decision ->
            handlePermissionResponse(requestId, decision)
        }

        // Wire slash command handler
        inputPanel.setSlashCommandHandler(::handleSlashCommand)

        // Wire rewind handler
        messageList.setRewindHandler { messageId, action ->
            handleRewind(messageId, action)
        }

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

        // Session history dropdown trigger
        sessionTitleLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                showSessionHistory()
            }
        })

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

    // ── Session management ───────────────────────────────────

    private fun showSessionHistory() {
        val panel = SessionHistoryPanel(
            project,
            onNewConversation = { startNewConversation() },
            onResumeSession = { session -> resumeSession(session) }
        )
        panel.show(sessionTitleLabel)
    }

    /**
     * Starts a fresh conversation: destroys any running process,
     * clears the chat, and resets session state.
     */
    fun startNewConversation() {
        destroyProcess()
        currentSessionId = null
        isResumedSession = false
        messageIndexCounter = 0
        conversationMessages.clear()

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            messageList.clearMessages()
            sessionTitleLabel.text = "New Conversation"
            resumedIndicator.isVisible = false
            inputPanel.setInputEnabled(true)
            inputPanel.focus()
        }
    }

    /**
     * Resumes a past session by its ID using --resume flag.
     */
    private fun resumeSession(session: SessionInfo) {
        destroyProcess()
        currentSessionId = session.id
        isResumedSession = true

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            messageList.clearMessages()
            sessionTitleLabel.text = session.displayTitle()
            resumedIndicator.isVisible = true

            // Show a system message indicating session resumption
            messageList.addMessage(
                ChatMessage(
                    MessageSender.SYSTEM,
                    "Resumed session: ${session.displayTitle()}"
                )
            )

            inputPanel.setInputEnabled(true)
            inputPanel.focus()
        }
    }

    private fun updateSessionTitle() {
        val sessionId = currentSessionId ?: return
        val session = SessionManager.getInstance(project)
            .getSession(sessionId) ?: return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                sessionTitleLabel.text = session.displayTitle()
            }
        }
    }

    private fun handleSlashCommand(command: SlashCommand) {
        if (command.name == "/clear") {
            messageList.clearMessages()
        }
        sendMessage(command.name)
    }

    private fun sendMessage(text: String) {
        val msgIndex = messageIndexCounter++
        val userMessage = ChatMessage(
            MessageSender.USER, text, messageIndex = msgIndex
        )
        conversationMessages.add(userMessage)
        messageList.addMessage(userMessage)
        inputPanel.setInputEnabled(false)
        messageList.showThinking(true)

        scope.launch {
            try {
                val cliManager = ClaudeCliManager.getInstance(project)
                val cliPath = cliManager.getCliPath()

                if (cliPath == null) {
                    showError(
                        "Claude Code CLI not found. " +
                            "Install it or configure the path in settings."
                    )
                    return@launch
                }

                val workingDir = project.basePath
                    ?: System.getProperty("user.dir")

                // Create or reuse process
                val process = getOrCreateProcess(
                    cliPath, workingDir
                ) ?: return@launch

                // Track session metadata
                val sessionId = currentSessionId
                if (sessionId != null) {
                    SessionManager.getInstance(project).onMessageSent(
                        sessionId,
                        text
                    )
                    updateSessionTitle()

                    // Begin checkpoint for this user turn
                    checkpointManager.beginCheckpoint(
                        sessionId = sessionId,
                        messageIndex = msgIndex,
                        userMessageId = userMessage.id,
                        userPromptText = text
                    )
                }

                // Prepare streaming message
                val assistantMessage = ChatMessage(
                    MessageSender.ASSISTANT, ""
                )
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
                showError(
                    "Failed to communicate with Claude: ${e.message}"
                )
            }
        }
    }

    @Suppress("LongMethod")
    private suspend fun getOrCreateProcess(
        cliPath: String,
        workingDir: String
    ): ClaudeProcess? {
        val existing = claudeProcess
        if (existing != null && existing.isRunning) {
            return existing
        }

        // Clean up dead process
        if (existing != null) {
            destroyProcess()
        }

        val sessionManager = SessionManager.getInstance(project)
        val resumeSessionId = if (isResumedSession) currentSessionId else null

        // Create a new session or reuse the resumed one
        if (!isResumedSession || currentSessionId == null) {
            val session = sessionManager.createSession()
            currentSessionId = session.id
        }

        val sessionId = currentSessionId ?: return null

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
            server.onFileEditAutoApproved = { request ->
                snapshotFileForRequest(request)
            }
            server.start()
            permissionServer = server

            val settings = ClaudeSettings.getInstance()
            val mode = settings.permissionMode

            // Build additional args for resume
            val additionalArgs = if (resumeSessionId != null) {
                SessionManager.buildResumeArgs(resumeSessionId)
            } else {
                emptyList()
            }

            val process = ClaudeProcess.start(
                cliPath,
                workingDir,
                sessionId,
                permissionMode = mode.cliValue,
                mcpServerPort = server.port,
                additionalArgs = additionalArgs
            )
            claudeProcess = process
            sessionManager.registerProcess(sessionId, process)

            // After first resume, clear the flag so subsequent
            // messages don't re-pass --resume
            isResumedSession = false

            // Start collecting events
            launchEventCollector(process)

            process
        } catch (e: Exception) {
            logger.warn("Failed to start Claude process", e)
            showError("Failed to start Claude: ${e.message}")
            null
        }
    }

    @Suppress("LongMethod")
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
                            handleStreamEvent(event)
                        }

                        is AssistantMessageEvent -> {
                            handleAssistantMessage(event)
                        }

                        is ResultEvent -> {
                            handleResultEvent(event)
                        }

                        else -> {
                            // Ignore other events
                        }
                    }
                }

            // Flow completed -- process ended
            handleProcessEnd()
        }
    }

    private fun handleStreamEvent(event: StreamEventWrapper) {
        val inner = event.event

        when (inner) {
            is ContentBlockStart -> {
                val block = inner.contentBlock
                if (block is ToolUseBlock) {
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
                        if (currentMsgId != null
                            && currentText.isNotBlank()
                        ) {
                            messageList.updateStreamingMessage(
                                currentMsgId, currentText
                            )
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

                            if (streamingMessageId == null) {
                                val newMsg = ChatMessage(
                                    MessageSender.ASSISTANT, ""
                                )
                                streamingMessageId = newMsg.id
                            }
                            val msgId =
                                streamingMessageId ?: return

                            if (accumulatedText.length
                                == delta.text.length
                            ) {
                                ApplicationManager.getApplication()
                                    .invokeLater {
                                        if (!project.isDisposed) {
                                            messageList.showThinking(
                                                false
                                            )
                                        }
                                    }
                            }

                            scheduleStreamingUpdate(
                                msgId,
                                accumulatedText.toString()
                            )
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
                    pendingToolUpdate.set(null)
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

    private fun handleAssistantMessage(event: AssistantMessageEvent) {
        val text = event.message?.getTextContent() ?: return
        if (text.isBlank()) return
        val msgId = streamingMessageId ?: return

        accumulatedText.clear()
        accumulatedText.append(text)

        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                messageList.showThinking(false)
            }
        }

        scheduleStreamingUpdate(msgId, text)
    }

    private fun handleResultEvent(event: ResultEvent) {
        if (event.sessionId != null) {
            currentSessionId = event.sessionId
        }

        // Update last-active time
        val sessionId = currentSessionId
        if (sessionId != null) {
            try {
                SessionManager.getInstance(project)
                    .updateLastActive(sessionId)
            } catch (_: Exception) {
                // Project may be disposed
            }
        }

        // Finalize checkpoint
        checkpointManager.finalizeCheckpoint()

        val msgId = streamingMessageId
        streamingMessageId = null
        accumulatedText.clear()

        val remainingTools = activeToolUses.values.toList()
        activeToolUses.clear()
        pendingToolUpdate.set(null)

        val finalText = event.content
            .filter { it.type == "text" }
            .mapNotNull { it.text }
            .joinToString("\n")

        pendingUpdate.set(null)

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            for (tool in remainingTools) {
                tool.status = ToolUseStatus.COMPLETE
                messageList.updateToolBlock(tool)
                messageList.setToolBlockBody(tool)
            }

            messageList.showThinking(false)

            if (msgId != null && finalText.isNotBlank()) {
                messageList.updateStreamingMessage(msgId, finalText)
                messageList.finalizeMessage(msgId)
                trackAssistantMessage(msgId, finalText)
            } else if (finalText.isNotBlank()) {
                val assistMsg = ChatMessage(
                    MessageSender.ASSISTANT, finalText
                )
                messageList.addMessage(assistMsg)
                trackAssistantMessage(assistMsg.id, finalText)
            }

            // Update rewind badges for all user messages
            updateRewindBadges()

            inputPanel.setInputEnabled(true)
            inputPanel.focus()
        }
    }

    private fun handleProcessEnd() {
        if (project.isDisposed) return

        val msgId = streamingMessageId
        streamingMessageId = null

        if (msgId != null) {
            val lastUpdate = pendingUpdate.getAndSet(null)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (lastUpdate != null) {
                    messageList.updateStreamingMessage(
                        lastUpdate.first, lastUpdate.second
                    )
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

    private fun scheduleStreamingUpdate(msgId: String, text: String) {
        pendingUpdate.set(Pair(msgId, text))
        ApplicationManager.getApplication().invokeLater {
            val update = pendingUpdate.getAndSet(null)
                ?: return@invokeLater
            if (!project.isDisposed) {
                messageList.updateStreamingMessage(
                    update.first, update.second
                )
            }
        }
    }

    private fun scheduleToolUpdate(tool: ToolUseState) {
        pendingToolUpdate.set(tool)
        ApplicationManager.getApplication().invokeLater {
            val update = pendingToolUpdate.getAndSet(null)
                ?: return@invokeLater
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
                messageList.addMessage(
                    ChatMessage(MessageSender.ERROR, text)
                )
                inputPanel.setInputEnabled(true)
                inputPanel.focus()
            }
        }
    }

    private fun handlePermissionResponse(
        requestId: String,
        decision: String
    ) {
        val server = permissionServer ?: return
        when (decision) {
            "allow", "allowSession" -> {
                val addSession = decision == "allowSession"
                val request = server.getOriginalRequest(requestId)
                if (request != null
                    && isFileModificationTool(request.toolName)
                ) {
                    showDiffAndResolve(requestId, request, addSession)
                } else {
                    if (addSession) {
                        server.addSessionApproval(requestId)
                    }
                    server.resolvePermission(
                        requestId, PermissionResponse("allow")
                    )
                }
            }
            "deny" -> server.resolvePermission(
                requestId,
                PermissionResponse("deny", "User denied permission")
            )
        }
    }

    private fun showDiffAndResolve(
        requestId: String,
        request: PermissionRequest,
        addSessionApproval: Boolean
    ) {
        val server = permissionServer ?: return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val diffData = DiffPreviewPanel.buildDiffData(
                project, request
            )
            if (diffData == null) {
                // Snapshot even without diff data
                snapshotFileForRequest(request)
                if (addSessionApproval) {
                    server.addSessionApproval(requestId)
                }
                server.resolvePermission(
                    requestId, PermissionResponse("allow")
                )
                return@invokeLater
            }

            val dialog = DiffViewerDialog(project, diffData)
            dialog.show()

            when (dialog.decision) {
                DiffDecision.ACCEPT -> {
                    // Snapshot before allowing the edit
                    snapshotFileForRequest(request)
                    if (addSessionApproval) {
                        server.addSessionApproval(requestId)
                    }
                    server.resolvePermission(
                        requestId, PermissionResponse("allow")
                    )
                }
                DiffDecision.REJECT -> {
                    server.resolvePermission(
                        requestId,
                        PermissionResponse(
                            "deny",
                            "User rejected changes in diff viewer"
                        )
                    )
                }
            }
        }
    }

    /**
     * Snapshots the file referenced in a permission request
     * before the edit is applied.
     */
    private fun snapshotFileForRequest(request: PermissionRequest) {
        val filePath = request.input["file_path"]?.toString()
            ?: return
        checkpointManager.snapshotBeforeEdit(filePath)
    }

    private fun isFileModificationTool(toolName: String): Boolean {
        return toolName == "Write" || toolName == "Edit"
    }

    // ── Rewind / checkpoint ────────────────────────────────────

    private fun trackAssistantMessage(id: String, text: String) {
        val msgIndex = messageIndexCounter++
        conversationMessages.add(
            ChatMessage(
                MessageSender.ASSISTANT, text,
                id = id, messageIndex = msgIndex
            )
        )
    }

    private fun updateRewindBadges() {
        val sessionId = currentSessionId ?: return
        for (msg in conversationMessages) {
            if (msg.sender != MessageSender.USER) continue
            val fileCount = checkpointManager.filesChangedSince(
                sessionId, msg.messageIndex
            )
            messageList.setRewindBadge(msg.id, fileCount)
        }
    }

    @Suppress("LongMethod")
    private fun handleRewind(messageId: String, action: String) {
        val sessionId = currentSessionId ?: return
        val targetMsg = conversationMessages.find {
            it.id == messageId
        } ?: return

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            when (action) {
                "codeAndConversation" -> {
                    // Restore files from this point onwards
                    val result = checkpointManager.restoreFiles(
                        sessionId, targetMsg.messageIndex
                    )
                    // Remove later checkpoints
                    checkpointManager.removeCheckpointsFrom(
                        sessionId, targetMsg.messageIndex
                    )
                    // Truncate conversation in UI
                    messageList.removeMessagesFrom(messageId)
                    // Truncate in-memory conversation
                    truncateConversation(targetMsg.messageIndex)
                    // Re-populate input with original prompt
                    inputPanel.setText(targetMsg.text)
                    inputPanel.setInputEnabled(true)
                    inputPanel.focus()
                    // Show system notification
                    messageList.addMessage(
                        ChatMessage(
                            MessageSender.SYSTEM,
                            "Rewound code and conversation. " +
                                "${result.restoredFileCount} file(s) " +
                                "restored." +
                                formatRewindWarnings(result)
                        )
                    )
                    // Restart process for next turn
                    destroyProcess()
                }
                "conversationOnly" -> {
                    // Truncate conversation, keep files as-is
                    messageList.removeMessagesFrom(messageId)
                    truncateConversation(targetMsg.messageIndex)
                    inputPanel.setText(targetMsg.text)
                    inputPanel.setInputEnabled(true)
                    inputPanel.focus()
                    messageList.addMessage(
                        ChatMessage(
                            MessageSender.SYSTEM,
                            "Conversation rewound. " +
                                "Files left unchanged."
                        )
                    )
                    destroyProcess()
                }
                "codeOnly" -> {
                    // Restore files, keep full conversation
                    val result = checkpointManager.restoreFiles(
                        sessionId, targetMsg.messageIndex
                    )
                    messageList.addMessage(
                        ChatMessage(
                            MessageSender.SYSTEM,
                            "Code restored to checkpoint. " +
                                "${result.restoredFileCount} file(s) " +
                                "reverted. Conversation preserved." +
                                formatRewindWarnings(result)
                        )
                    )
                    updateRewindBadges()
                }
                "summarize" -> {
                    // Build summary of messages after this point
                    val summary = buildSummary(
                        targetMsg.messageIndex
                    )
                    messageList.replaceMessagesWithSummary(
                        messageId, summary
                    )
                    truncateConversation(targetMsg.messageIndex)
                    // Add the summary as a system message
                    // in the internal list
                    conversationMessages.add(
                        ChatMessage(
                            MessageSender.SYSTEM,
                            summary,
                            messageIndex = messageIndexCounter++
                        )
                    )
                    inputPanel.setInputEnabled(true)
                    inputPanel.focus()
                }
            }
        }
    }

    private fun truncateConversation(fromIndex: Int) {
        conversationMessages.removeAll { it.messageIndex >= fromIndex }
        messageIndexCounter = (conversationMessages.maxOfOrNull {
            it.messageIndex
        } ?: -1) + 1
    }

    private fun buildSummary(fromMessageIndex: Int): String {
        val laterMessages = conversationMessages.filter {
            it.messageIndex > fromMessageIndex
        }.sortedBy { it.messageIndex }

        if (laterMessages.isEmpty()) {
            return "*No subsequent messages to summarize.*"
        }

        val sb = StringBuilder()
        sb.append("**Summary of ${laterMessages.size} message(s):**\n\n")
        for (msg in laterMessages) {
            val sender = when (msg.sender) {
                MessageSender.USER -> "You"
                MessageSender.ASSISTANT -> "Claude"
                MessageSender.ERROR -> "Error"
                MessageSender.SYSTEM -> "System"
            }
            val preview = msg.text.take(120).replace('\n', ' ')
            sb.append("- **$sender**: $preview")
            if (msg.text.length > 120) sb.append("...")
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun formatRewindWarnings(
        result: com.claudecode.jetbrains.checkpoint.RewindResult
    ): String {
        val parts = mutableListOf<String>()
        if (result.skippedFiles.isNotEmpty()) {
            parts.add(
                " ${result.skippedFiles.size} file(s) could not " +
                    "be restored."
            )
        }
        if (result.warnings.isNotEmpty()) {
            parts.addAll(result.warnings.map { " $it" })
        }
        return parts.joinToString("")
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
                    SessionManager.getInstance(project)
                        .unregisterProcess(sessionId)
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
        val KEY: Key<ChatToolWindow> =
            Key.create("ClaudeCode.ChatToolWindow")
    }
}
