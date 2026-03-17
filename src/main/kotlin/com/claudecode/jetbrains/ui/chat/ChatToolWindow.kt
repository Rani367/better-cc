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
import com.claudecode.jetbrains.cli.ThinkingBlock
import com.claudecode.jetbrains.cli.ThinkingDelta
import com.claudecode.jetbrains.cli.ToolUseBlock
import com.claudecode.jetbrains.services.ClaudeState
import com.claudecode.jetbrains.services.ClaudeStateListener
import com.claudecode.jetbrains.services.ClaudeStateService
import com.claudecode.jetbrains.services.SessionUsage
import com.claudecode.jetbrains.settings.ClaudeSettings
import com.claudecode.jetbrains.settings.ClaudeSettingsChangeListener
import com.claudecode.jetbrains.settings.PermissionMode
import com.claudecode.jetbrains.settings.ThinkingMode
import com.claudecode.jetbrains.ui.commands.SlashCommand
import com.claudecode.jetbrains.ui.commands.SlashCommandRegistry
import com.claudecode.jetbrains.ui.mcp.McpManagerDialog
import com.claudecode.jetbrains.ui.plugins.PluginManagerDialog
import com.claudecode.jetbrains.ui.diff.DiffDecision
import com.claudecode.jetbrains.ui.diff.DiffPreviewPanel
import com.claudecode.jetbrains.ui.diff.DiffViewerDialog

import com.claudecode.jetbrains.ui.sessions.ClaudeVirtualFile
import com.claudecode.jetbrains.ui.sessions.SessionHistoryPanel
import com.claudecode.jetbrains.ui.sessions.SessionState
import com.claudecode.jetbrains.ui.sessions.SessionTabManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicReference

import javax.swing.JPanel

class ChatToolWindow(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(ChatToolWindow::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val stateService = ClaudeStateService.getInstance(project)
    private val messageList = MessageList(project, this)

    private val rootPanel = JPanel(BorderLayout()).apply {
        // The JCEF browser fills the entire panel (header + messages + input are all in JCEF)
        add(messageList, BorderLayout.CENTER)
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

    // Thinking block tracking
    private val activeThinkingBlocks = mutableMapOf<Int, ThinkingState>()
    private val pendingThinkingUpdate =
        AtomicReference<ThinkingState?>(null)

    // Permission system
    private var permissionServer: PermissionMcpServer? = null

    // Status change listener (used by editor tabs for indicators)
    private var statusChangeListener:
        ((ClaudeVirtualFile.TabStatus) -> Unit)? = null

    // Checkpoint / rewind
    private val checkpointManager =
        CheckpointManager.getInstance(project)
    private var messageIndexCounter = 0
    private val conversationMessages =
        mutableListOf<ChatMessage>()

    // Teleport location: "sidebar" or "tab"
    private var location: String = "sidebar"

    init {
        project.putUserData(KEY, this)

        // Wire permission response handler
        messageList.setPermissionResponseHandler { requestId, decision ->
            handlePermissionResponse(requestId, decision)
        }

        // Wire rewind handler
        messageList.setRewindHandler { messageId, action ->
            handleRewind(messageId, action)
        }

        // Wire retry handler (from hover actions)
        messageList.setRetryHandler { messageId ->
            // Find the user message that preceded this assistant message
            val msgIdx = conversationMessages.indexOfFirst {
                it.id == messageId
            }
            val userMsg = if (msgIdx > 0) {
                conversationMessages.subList(0, msgIdx)
                    .lastOrNull { it.sender == MessageSender.USER }
            } else {
                conversationMessages
                    .lastOrNull { it.sender == MessageSender.USER }
            }
            if (userMsg != null) {
                sendMessage(userMsg.text)
            }
        }

        // Wire fork handler (from hover actions)
        messageList.setForkHandler { messageId ->
            forkConversationAt(messageId)
        }

        // Wire send message handler (from JCEF input)
        messageList.setSendMessageHandler { text ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    sendMessage(text)
                }
            }
        }

        // Wire header handlers (from JCEF)
        messageList.setNewConversationHandler {
            ApplicationManager.getApplication().invokeLater {
                startNewConversation()
            }
        }
        messageList.setSessionsClickHandler {
            ApplicationManager.getApplication().invokeLater {
                showSessionHistory()
            }
        }
        messageList.setSettingsHandler {
            ApplicationManager.getApplication().invokeLater {
                openSettings()
            }
        }

        // Wire slash command handler (from JCEF input)
        messageList.setSlashCommandHandler { commandName ->
            ApplicationManager.getApplication().invokeLater {
                val command = SlashCommandRegistry.ALL_COMMANDS
                    .find { it.name == commandName }
                if (command != null) {
                    handleSlashCommand(command)
                } else {
                    sendMessage(commandName)
                }
            }
        }

        // Wire footer button handlers
        messageList.setModelClickHandler {
            ApplicationManager.getApplication().invokeLater {
                showModelPicker()
            }
        }
        messageList.setPermissionModeClickHandler {
            ApplicationManager.getApplication().invokeLater {
                showPermissionModePicker()
            }
        }
        messageList.setThinkingClickHandler {
            ApplicationManager.getApplication().invokeLater {
                showThinkingPicker()
            }
        }

        // Wire teleport handler
        messageList.setTeleportHandler { target ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    handleTeleport(target)
                }
            }
        }

        // Wire picker selection handler (JCEF dropdowns)
        messageList.setPickerSelectionHandler { pickerId, value ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    handlePickerSelection(pickerId, value)
                }
            }
        }

        // Listen for theme changes
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener {
                messageList.applyTheme()
            })

        // Listen for settings changes to update footer labels
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(
                ClaudeSettings.SETTINGS_CHANGED,
                object : ClaudeSettingsChangeListener {
                    override fun settingsChanged(settings: ClaudeSettings) {
                        messageList.setModelLabel(
                            modelDisplayText(settings.selectedModel)
                        )
                        messageList.setThinkingLabel(
                            settings.thinkingMode.displayName
                        )
                        messageList.setPermissionModeLabel(
                            settings.permissionMode.displayName
                        )
                    }
                }
            )

        // Listen for state changes to update status dot (via JCEF)
        stateService.addListener(object : ClaudeStateListener {
            override fun onStateChanged(state: ClaudeState) {
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    val dotState = when (state) {
                        ClaudeState.READY -> "ready"
                        ClaudeState.THINKING -> "thinking"
                        ClaudeState.WAITING_FOR_PERMISSION -> "waiting"
                    }
                    messageList.setStatusDot(dotState)
                }
            }

            override fun onUsageUpdated(usage: SessionUsage) {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        messageList.setCostLabel(usage.formatCost())
                        val percent = usage.contextPercent
                        if (percent > 0) {
                            messageList.updateUsageBar(
                                percent,
                                "$percent% context used"
                            )
                        }
                    }
                }
            }

            override fun onModelChanged(model: String) {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        messageList.setModelLabel(modelDisplayText(model))
                    }
                }
            }
        })

        // Set initial footer labels
        val settings = ClaudeSettings.getInstance()
        messageList.setModelLabel(modelDisplayText(settings.selectedModel))
        messageList.setThinkingLabel(settings.thinkingMode.displayName)
        messageList.setPermissionModeLabel(settings.permissionMode.displayName)
        messageList.setSendHint(settings.useCtrlEnterToSend)

        // Push picker dropdown options to JCEF
        pushPickerOptions()

        // Fetch git branch in background
        ApplicationManager.getApplication().executeOnPooledThread {
            val tracker =
                com.claudecode.jetbrains.context.GitBranchTracker
                    .getInstance(project)
            val branch = tracker.getCurrentBranch()
            if (branch != null) {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        messageList.setBranchPill(branch)
                    }
                }
            }
        }
    }

    fun getContent(): JPanel = rootPanel


    fun focusInput() {
        messageList.focusInput()
    }

    /**
     * Inserts text at the current cursor position in the input.
     * Used by InsertFileRefAction to inject @file references.
     */
    fun insertTextAtCursor(text: String) {
        messageList.insertTextAtCursor(text)
    }

    /**
     * Set text in the input without sending.
     */
    fun prefillInput(text: String) {
        messageList.setInputText(text)
        messageList.focusInput()
    }

    /**
     * Send a message programmatically (e.g., from editor context actions).
     */
    fun sendPrefilledMessage(text: String) {
        sendMessage(text)
    }

    /**
     * Registers a callback invoked when the conversation status
     * changes (e.g., permission pending, task complete). Used by
     * [ClaudeFileEditor] to drive tab status indicators.
     */
    fun setStatusChangeListener(
        listener: (ClaudeVirtualFile.TabStatus) -> Unit
    ) {
        statusChangeListener = listener
    }

    // ── Session management ───────────────────────────────────

    private fun showSessionHistory() {
        val panel = SessionHistoryPanel(
            project,
            onNewConversation = { startNewConversation() },
            onResumeSession = { session -> resumeSession(session) }
        )
        panel.show(rootPanel)
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
            messageList.setSessionTitle("New Conversation")
            messageList.setInputEnabled(true)
            messageList.focusInput()
        }
    }

    /**
     * Forks the conversation at the given message ID.
     * Finds the user message corresponding to the forked message,
     * copies conversation history up to that point, and opens
     * the fork in a new editor tab.
     */
    private fun forkConversationAt(messageId: String) {
        // Find the message being forked from
        val forkIdx = conversationMessages.indexOfFirst {
            it.id == messageId
        }

        // Collect history up to and including the fork point
        val historyUpToFork = if (forkIdx >= 0) {
            conversationMessages.subList(0, forkIdx + 1).toList()
        } else {
            conversationMessages.toList()
        }

        // Find the last user message at or before the fork point
        val userMsg = historyUpToFork.lastOrNull {
            it.sender == MessageSender.USER
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            // Open a new tab
            val tabManager = SessionTabManager.getInstance(project)
            val file = tabManager.openNewTab()
            tabManager.updateTabTitle(
                file.tabId, "Fork: ${userMsg?.text?.take(30) ?: "conversation"}"
            )

            // After tab opens, load the forked history
            // We need a small delay for the editor to initialize
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val editors =
                    com.intellij.openapi.fileEditor.FileEditorManager
                        .getInstance(project).getEditors(file)
                val claudeEditor = editors.firstOrNull {
                    it is com.claudecode.jetbrains.ui.sessions.ClaudeFileEditor
                } as? com.claudecode.jetbrains.ui.sessions.ClaudeFileEditor
                val forkedChat = claudeEditor?.chatPanel
                if (forkedChat != null) {
                    forkedChat.loadForkedHistory(historyUpToFork)
                    // Re-send the last user message to start the fork
                    if (userMsg != null) {
                        forkedChat.sendPrefilledMessage(userMsg.text)
                    }
                }
            }
        }
    }

    /**
     * Loads forked conversation history into this chat panel.
     * Used when a conversation is forked from another panel.
     */
    fun loadForkedHistory(history: List<ChatMessage>) {
        messageList.clearMessages()
        conversationMessages.clear()
        messageIndexCounter = 0

        for (msg in history) {
            if (msg.sender == MessageSender.USER ||
                msg.sender == MessageSender.ASSISTANT
            ) {
                val indexed = ChatMessage(
                    sender = msg.sender,
                    text = msg.text,
                    id = msg.id,
                    messageIndex = messageIndexCounter++
                )
                conversationMessages.add(indexed)
                messageList.addMessage(indexed)
            }
        }

        messageList.addMessage(
            ChatMessage(
                MessageSender.SYSTEM,
                "Forked from previous conversation"
            )
        )
        messageList.setSessionTitle("Forked Conversation")
    }

    /**
     * Sets the location of this chat panel (sidebar or tab).
     * This controls the teleport button label.
     */
    fun setLocation(loc: String) {
        location = loc
        val target = if (loc == "sidebar") "tab" else "sidebar"
        messageList.setTeleportLabel(target)
    }

    /**
     * Exports the current session state for teleporting.
     */
    fun exportSessionState(): SessionState {
        return SessionState(
            sessionId = currentSessionId,
            sessionTitle = "Claude Code",
            conversationHistory = conversationMessages.toList(),
            messageIndexCounter = messageIndexCounter
        )
    }

    /**
     * Loads a teleported session state.
     */
    fun loadTeleportedState(state: SessionState) {
        destroyProcess()
        currentSessionId = state.sessionId
        isResumedSession = state.sessionId != null
        messageIndexCounter = state.messageIndexCounter
        conversationMessages.clear()

        messageList.clearMessages()
        for (msg in state.conversationHistory) {
            conversationMessages.add(msg)
            messageList.addMessage(msg)
        }

        messageList.setSessionTitle(state.sessionTitle)
        messageList.setInputEnabled(true)
        messageList.focusInput()
    }

    /**
     * Handles a teleport request from the JS bridge.
     */
    private fun handleTeleport(target: String) {
        val state = exportSessionState()

        when (target) {
            "tab" -> {
                // Move from sidebar to editor tab
                val tabManager = SessionTabManager.getInstance(project)
                tabManager.teleportToTab(state)
                // Clear the sidebar
                startNewConversation()
            }
            "sidebar" -> {
                // Move from tab to sidebar
                val sidebarChat =
                    project.getUserData(KEY)
                if (sidebarChat != null && sidebarChat !== this) {
                    sidebarChat.loadTeleportedState(state)
                    // Activate sidebar tool window
                    val toolWindow =
                        com.intellij.openapi.wm.ToolWindowManager
                            .getInstance(project)
                            .getToolWindow("Claude Code")
                    toolWindow?.activate(null)
                }
                // Close the tab if we're in one
                destroyProcess()
            }
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
            messageList.setSessionTitle(session.displayTitle())

            messageList.addMessage(
                ChatMessage(
                    MessageSender.SYSTEM,
                    "Resumed session: ${session.displayTitle()}"
                )
            )

            messageList.setInputEnabled(true)
            messageList.focusInput()
        }
    }

    private fun updateSessionTitle() {
        val sessionId = currentSessionId ?: return
        val session = SessionManager.getInstance(project)
            .getSession(sessionId) ?: return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                messageList.setSessionTitle(session.displayTitle())
            }
        }
    }

    private fun handleSlashCommand(command: SlashCommand) {
        when (command.name) {
            "/clear" -> {
                messageList.clearMessages()
                sendMessage(command.name)
            }
            "/mcp" -> {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        McpManagerDialog(project).show()
                    }
                }
            }
            "/plugins" -> {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        PluginManagerDialog(project).show()
                    }
                }
            }
            "/settings", "/config" -> {
                openSettings()
            }
            else -> sendMessage(command.name)
        }
    }

    private fun openSettings() {
        com.intellij.openapi.options.ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, "Claude Code")
    }

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
        // Picker is shown in JCEF — just trigger it via JS
        // Options were already pushed on init
    }

    private fun showPermissionModePicker() {
        // Picker is shown in JCEF
    }

    private fun showThinkingPicker() {
        // Picker is shown in JCEF
    }

    /**
     * Pushes picker option data to the JCEF webview so dropdowns
     * can render instantly when the user clicks a footer button.
     */
    private fun pushPickerOptions() {
        val gson = com.google.gson.Gson()

        // Permission mode options (static)
        val permOptions = PermissionMode.entries.map { mode ->
            mapOf(
                "value" to mode.cliValue,
                "label" to mode.displayName,
                "description" to when (mode) {
                    PermissionMode.DEFAULT ->
                        "Ask before each action"
                    PermissionMode.ACCEPT_EDITS ->
                        "Auto-accept file edits"
                    PermissionMode.PLAN ->
                        "Planning only, no execution"
                    PermissionMode.DONT_ASK ->
                        "Don't ask for permission"
                    PermissionMode.BYPASS ->
                        "Skip all permission prompts"
                },
                "danger" to (mode == PermissionMode.BYPASS)
            )
        }
        messageList.setPickerOptions(
            "permissionMode", gson.toJson(permOptions)
        )
        messageList.setPickerCurrent(
            "permissionMode",
            ClaudeSettings.getInstance().permissionMode.cliValue
        )

        // Thinking mode options (static)
        val thinkOptions = ThinkingMode.entries.map { mode ->
            mapOf(
                "value" to mode.name,
                "label" to mode.displayName,
                "description" to when (mode) {
                    ThinkingMode.NORMAL -> "Standard responses"
                    ThinkingMode.THINK_HARD ->
                        "Extended reasoning (10K tokens)"
                    ThinkingMode.ULTRATHINK ->
                        "Deep reasoning (50K tokens)"
                }
            )
        }
        messageList.setPickerOptions(
            "thinkingMode", gson.toJson(thinkOptions)
        )
        messageList.setPickerCurrent(
            "thinkingMode",
            ClaudeSettings.getInstance().thinkingMode.name
        )

        // Model options (async from CLI)
        ApplicationManager.getApplication().executeOnPooledThread {
            val cliManager = ClaudeCliManager.getInstance(project)
            val cliModels = cliManager.fetchModels()
            val modelOptions = mutableListOf(
                mapOf(
                    "value" to "",
                    "label" to "Default",
                    "description" to "Use default model"
                )
            )
            for (m in cliModels) {
                modelOptions.add(
                    mapOf("value" to m, "label" to m)
                )
            }
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    messageList.setPickerOptions(
                        "model", gson.toJson(modelOptions)
                    )
                    messageList.setPickerCurrent(
                        "model",
                        ClaudeSettings.getInstance().selectedModel
                    )
                }
            }
        }
    }

    /**
     * Handles a picker selection from the JCEF dropdown.
     */
    fun handlePickerSelection(pickerId: String, value: String) {
        when (pickerId) {
            "model" -> {
                ClaudeSettings.getInstance().selectedModel = value
                stateService.setActiveModel(value)
                messageList.setModelLabel(modelDisplayText(value))
                messageList.setPickerCurrent("model", value)
            }
            "permissionMode" -> {
                val mode = PermissionMode.fromCliValue(value)
                if (mode == PermissionMode.BYPASS) {
                    if (!ClaudeSettings.getInstance()
                            .allowDangerouslySkipPermissions
                    ) {
                        return
                    }
                }
                ClaudeSettings.getInstance().permissionMode = mode
                messageList.setPermissionModeLabel(mode.displayName)
                messageList.setPickerCurrent(
                    "permissionMode", value
                )
                val spinnerColor = when (mode) {
                    PermissionMode.DEFAULT -> "#da7756"
                    PermissionMode.ACCEPT_EDITS ->
                        "var(--app-fg)"
                    PermissionMode.PLAN ->
                        "var(--app-button-background)"
                    PermissionMode.BYPASS ->
                        "var(--app-error-foreground)"
                    else ->
                        "var(--app-secondary-foreground)"
                }
                messageList.setSpinnerColor(spinnerColor)
            }
            "thinkingMode" -> {
                val mode = ThinkingMode.fromName(value)
                ClaudeSettings.getInstance().thinkingMode = mode
                messageList.setThinkingLabel(mode.displayName)
                messageList.setPickerCurrent("thinkingMode", value)
            }
        }
    }

    private fun sendMessage(text: String) {
        val msgIndex = messageIndexCounter++
        val userMessage = ChatMessage(
            MessageSender.USER, text, messageIndex = msgIndex
        )
        conversationMessages.add(userMessage)
        messageList.addMessage(userMessage)
        messageList.setInputEnabled(false)
        messageList.showThinking(true)
        stateService.setState(ClaudeState.THINKING)

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
        stateService.setSession(sessionId)

        return try {
            // Start permission MCP server
            val server = PermissionMcpServer()
            server.onPermissionRequest = { request ->
                stateService.setState(ClaudeState.WAITING_FOR_PERMISSION)
                statusChangeListener?.invoke(
                    ClaudeVirtualFile.TabStatus.PERMISSION_PENDING
                )
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
            val model = settings.selectedModel
            val thinkingBudget = settings.thinkingMode.budgetTokens
            val envVars = settings.environmentVariables

            if (model.isNotBlank()) {
                stateService.setActiveModel(model)
            }

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
                model = model,
                thinkingBudgetTokens = thinkingBudget,
                environmentVariables = envVars,
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
                } else if (block is ThinkingBlock) {
                    val thinkingId = "thinking-${inner.index}-${System.currentTimeMillis()}"
                    val thinking = ThinkingState(
                        id = thinkingId,
                        index = inner.index
                    )
                    if (block.thinking.isNotEmpty()) {
                        thinking.accumulatedText.append(block.thinking)
                    }
                    activeThinkingBlocks[inner.index] = thinking

                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        messageList.showThinking(false)
                        messageList.addThinkingBlock(thinkingId)
                        if (thinking.accumulatedText.isNotEmpty()) {
                            messageList.updateThinkingBlock(
                                thinkingId,
                                thinking.accumulatedText.toString()
                            )
                        }
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

                    is ThinkingDelta -> {
                        val thinking =
                            activeThinkingBlocks[inner.index]
                        if (thinking != null) {
                            thinking.accumulatedText.append(
                                delta.thinking
                            )
                            scheduleThinkingUpdate(thinking)
                        }
                    }

                    else -> { /* ignore other deltas */ }
                }
            }

            is ContentBlockStop -> {
                // Check thinking blocks first
                val thinking =
                    activeThinkingBlocks.remove(inner.index)
                if (thinking != null) {
                    thinking.status = ThinkingStatus.COMPLETE
                    pendingThinkingUpdate.set(null)
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            messageList.updateThinkingBlock(
                                thinking.id,
                                thinking.accumulatedText.toString()
                            )
                            messageList.finalizeThinkingBlock(
                                thinking.id
                            )
                        }
                    }
                }

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
            stateService.setSession(event.sessionId)
        }

        // Update usage from result event
        stateService.updateUsage(
            inputTokens = event.usage?.inputTokens,
            outputTokens = event.usage?.outputTokens,
            costUsd = event.costUsd
        )
        stateService.setState(ClaudeState.READY)

        // Notify tab that a task finished
        statusChangeListener?.invoke(
            ClaudeVirtualFile.TabStatus.TASK_COMPLETE
        )

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

        // Finalize any remaining thinking blocks
        val remainingThinking = activeThinkingBlocks.values.toList()
        activeThinkingBlocks.clear()
        pendingThinkingUpdate.set(null)

        val finalText = event.content
            .filter { it.type == "text" }
            .mapNotNull { it.text }
            .joinToString("\n")

        pendingUpdate.set(null)

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            for (thinking in remainingThinking) {
                messageList.finalizeThinkingBlock(thinking.id)
            }

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

            messageList.setInputEnabled(true)
            messageList.focusInput()
        }
    }

    private fun handleProcessEnd() {
        stateService.setState(ClaudeState.READY)
        if (project.isDisposed) return

        val msgId = streamingMessageId
        streamingMessageId = null
        val lastText = accumulatedText.toString()

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
                messageList.setInputEnabled(true)
                messageList.focusInput()

                // Show crash notice if stream ended mid-message
                // with very little content
                if (lastText.isBlank()) {
                    messageList.addMessage(
                        ChatMessage(
                            MessageSender.ERROR,
                            "Claude process ended unexpectedly. " +
                                "Try sending your message again."
                        )
                    )
                }
            }
        } else {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                messageList.showThinking(false)
                messageList.setInputEnabled(true)
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
        stateService.setState(ClaudeState.READY)

        // Enrich error messages with actionable hints
        val enrichedText = when {
            text.contains("not found", ignoreCase = true) ->
                "$text\n\nInstall Claude Code CLI: " +
                    "npm install -g @anthropic-ai/claude-code"
            text.contains("auth", ignoreCase = true) ||
                text.contains("API key", ignoreCase = true) ->
                "$text\n\nRun 'claude login' in a terminal " +
                    "to re-authenticate."
            text.contains("timed out", ignoreCase = true) ||
                text.contains("timeout", ignoreCase = true) ->
                "$text\n\nThe request timed out. " +
                    "Check your network connection and try again."
            else -> text
        }

        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                messageList.showThinking(false)
                messageList.addMessage(
                    ChatMessage(MessageSender.ERROR, enrichedText)
                )
                messageList.setInputEnabled(true)
                messageList.focusInput()
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
                // Auto-save before file operations
                if (ClaudeSettings.getInstance().autoSave) {
                    com.intellij.openapi.fileEditor
                        .FileDocumentManager.getInstance()
                        .saveAllDocuments()
                }
                stateService.setState(ClaudeState.THINKING)
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
            "deny" -> {
                stateService.setState(ClaudeState.THINKING)
                server.resolvePermission(
                    requestId,
                    PermissionResponse("deny", "User denied permission")
                )
            }
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
                    messageList.setInputText(targetMsg.text)
                    messageList.setInputEnabled(true)
                    messageList.focusInput()
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
                    messageList.setInputText(targetMsg.text)
                    messageList.setInputEnabled(true)
                    messageList.focusInput()
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
                    messageList.setInputEnabled(true)
                    messageList.focusInput()
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
        stateService.setState(ClaudeState.READY)

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

    private fun scheduleThinkingUpdate(thinking: ThinkingState) {
        pendingThinkingUpdate.set(thinking)
        ApplicationManager.getApplication().invokeLater {
            val update = pendingThinkingUpdate.getAndSet(null)
                ?: return@invokeLater
            if (!project.isDisposed) {
                messageList.updateThinkingBlock(
                    update.id, update.accumulatedText.toString()
                )
            }
        }
    }

    companion object {
        val KEY: Key<ChatToolWindow> =
            Key.create("ClaudeCode.ChatToolWindow")
    }
}

/**
 * Tracks the state of an active thinking block during streaming.
 */
data class ThinkingState(
    val id: String,
    val index: Int,
    val accumulatedText: StringBuilder = StringBuilder(),
    var status: ThinkingStatus = ThinkingStatus.STREAMING
)

enum class ThinkingStatus {
    STREAMING,
    COMPLETE
}
