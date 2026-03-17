package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.cli.PermissionRequest
import com.claudecode.jetbrains.ui.commands.SlashCommandRegistry
import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class MessageList(private val project: Project, parentDisposable: Disposable) : JPanel(CardLayout()) {

    private val logger = Logger.getInstance(MessageList::class.java)
    private val gson = Gson()

    // JCEF browser (null if JCEF is not supported)
    private val browser: JBCefBrowser?
    private var codeBlockRenderer: CodeBlockRenderer? = null
    private var browserReady = false
    private val pendingCalls = mutableListOf<String>()

    // Fallback plain-text display
    private val fallbackTextArea: JTextArea?
    private val fallbackScrollPane: JScrollPane?

    private var hasMessages = false

    init {
        if (JBCefApp.isSupported()) {
            browser = JBCefBrowser()
            fallbackTextArea = null
            fallbackScrollPane = null

            val browserPanel = JPanel(BorderLayout()).apply {
                add(browser.component, BorderLayout.CENTER)
            }
            add(browserPanel, CARD_MESSAGES)

            Disposer.register(parentDisposable, browser)

            // Set up code block renderer (copy + file open handlers)
            codeBlockRenderer = CodeBlockRenderer(project, browser)
            Disposer.register(parentDisposable, codeBlockRenderer!!)

            // Register load handler to know when browser is ready
            browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == true) {
                        onBrowserReady()
                    }
                }
            }, browser.cefBrowser)

            // Build and load the HTML
            val html = buildHtml()
            browser.loadHTML(html)
        } else {
            browser = null

            // Fallback: plain text area
            fallbackTextArea = JTextArea().apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                font = JBUI.Fonts.create(Font.MONOSPACED, 13)
                border = JBUI.Borders.empty(8)
            }
            fallbackScrollPane = JScrollPane(fallbackTextArea).apply {
                border = BorderFactory.createEmptyBorder()
            }
            add(fallbackScrollPane, CARD_MESSAGES)
        }

        showCard(CARD_MESSAGES)
    }

    fun addMessage(message: ChatMessage) {
        hasMessages = true

        if (browser != null) {
            val escapedText = jsStringEscape(message.text)
            executeJS("addMessage(${jsStringEscape(message.id)}, ${jsStringEscape(message.sender.name)}, $escapedText)")
        } else {
            // Fallback
            val prefix = when (message.sender) {
                MessageSender.USER -> "You: "
                MessageSender.ASSISTANT -> "Claude: "
                MessageSender.ERROR -> "Error: "
                MessageSender.SYSTEM -> "System: "
            }
            fallbackTextArea?.append("$prefix${message.text.trim()}\n\n")
            scrollFallbackToBottom()
        }
    }

    fun updateStreamingMessage(id: String, markdown: String) {
        if (browser != null) {
            hasMessages = true
            executeJS("updateStreamingMessage(${jsStringEscape(id)}, ${jsStringEscape(markdown)})")
        } else {
            // Fallback: just show the latest text
            // This is imperfect but works for the no-JCEF case
        }
    }

    fun finalizeMessage(id: String) {
        if (browser != null) {
            executeJS("finalizeMessage(${jsStringEscape(id)})")
        }
    }

    fun showThinking(visible: Boolean) {
        if (browser != null) {
            hasMessages = true
            executeJS("showThinking($visible)")
        } else {
            if (visible) {
                fallbackTextArea?.append("Thinking...\n")
            }
        }
    }

    fun addToolBlock(tool: ToolUseState) {
        hasMessages = true
        if (browser != null) {
            val id = jsStringEscape(tool.toolUseId)
            val name = jsStringEscape(tool.toolName)
            val category = jsStringEscape(tool.category.name)
            val summary = jsStringEscape(tool.summary())
            val isRunning = tool.status == ToolUseStatus.RUNNING
            executeJS("addToolBlock($id, $name, $category, $summary, $isRunning)")
        }
    }

    fun updateToolBlock(tool: ToolUseState) {
        if (browser != null) {
            val id = jsStringEscape(tool.toolUseId)
            val summary = jsStringEscape(tool.summary())
            val isRunning = tool.status == ToolUseStatus.RUNNING
            executeJS("updateToolBlock($id, $summary, $isRunning)")
        }
    }

    fun setToolBlockBody(tool: ToolUseState) {
        if (browser != null) {
            val id = jsStringEscape(tool.toolUseId)
            val body = jsStringEscape(tool.inputJson.toString())
            executeJS("setToolBlockBody($id, $body)")
        }
    }

    fun setToolDiffStats(
        toolId: String,
        additions: Int,
        deletions: Int,
        filePath: String?
    ) {
        if (browser != null) {
            val id = jsStringEscape(toolId)
            val path = if (filePath != null) {
                jsStringEscape(filePath)
            } else {
                "null"
            }
            executeJS(
                "setToolDiffStats($id, $additions, $deletions, $path)"
            )
        }
    }

    fun addPermissionCard(request: PermissionRequest) {
        hasMessages = true
        if (browser != null) {
            val id = jsStringEscape(request.requestId)
            val toolName = jsStringEscape(request.toolName)
            val category = jsStringEscape(ToolCategory.categorize(request.toolName).name)
            val argsSummary = jsStringEscape(formatPermissionArgs(request))
            val risk = jsStringEscape(request.riskLevel)
            executeJS("addPermissionCard($id, $toolName, $category, $argsSummary, $risk)")
        }
    }

    fun setPermissionResponseHandler(handler: (String, String) -> Unit) {
        codeBlockRenderer?.permissionResponseHandler = handler
    }

    fun setRewindHandler(handler: (String, String) -> Unit) {
        codeBlockRenderer?.rewindHandler = handler
    }

    fun setRetryHandler(handler: (String) -> Unit) {
        codeBlockRenderer?.retryHandler = handler
    }

    fun setForkHandler(handler: (String) -> Unit) {
        codeBlockRenderer?.forkHandler = handler
    }

    // ── Header bridge methods ──────────────────────────────────────

    fun setSendMessageHandler(handler: (String) -> Unit) {
        codeBlockRenderer?.sendMessageHandler = handler
    }

    fun setNewConversationHandler(handler: () -> Unit) {
        codeBlockRenderer?.newConversationHandler = handler
    }

    fun setSessionsClickHandler(handler: () -> Unit) {
        codeBlockRenderer?.sessionsClickHandler = handler
    }

    fun setSettingsHandler(handler: () -> Unit) {
        codeBlockRenderer?.settingsHandler = handler
    }

    fun setSlashCommandHandler(handler: (String) -> Unit) {
        codeBlockRenderer?.slashCommandHandler = handler
    }

    fun setModelClickHandler(handler: () -> Unit) {
        codeBlockRenderer?.modelClickHandler = handler
    }

    fun setPermissionModeClickHandler(handler: () -> Unit) {
        codeBlockRenderer?.permissionModeClickHandler = handler
    }

    fun setThinkingClickHandler(handler: () -> Unit) {
        codeBlockRenderer?.thinkingClickHandler = handler
    }

    fun setTeleportHandler(handler: (String) -> Unit) {
        codeBlockRenderer?.teleportHandler = handler
    }

    // ── Header/input state updates (JS bridge) ─────────────────────

    fun setSessionTitle(title: String) {
        executeJS("setSessionTitle(${jsStringEscape(title)})")
    }

    fun setStatusDot(state: String) {
        executeJS("setStatusDot(${jsStringEscape(state)})")
    }

    fun setInputEnabled(enabled: Boolean) {
        executeJS("setInputEnabled($enabled)")
    }

    fun focusInput() {
        executeJS("focusInput()")
    }

    fun setInputText(text: String) {
        executeJS("setInputText(${jsStringEscape(text)})")
    }

    fun insertTextAtCursor(text: String) {
        executeJS("insertTextAtCursor(${jsStringEscape(text)})")
    }

    fun setModelLabel(text: String) {
        executeJS("setModelLabel(${jsStringEscape(text)})")
    }

    fun setCostLabel(text: String) {
        executeJS("setCostLabel(${jsStringEscape(text)})")
    }

    fun setPermissionModeLabel(text: String) {
        executeJS("setPermissionModeLabel(${jsStringEscape(text)})")
    }

    fun setThinkingLabel(text: String) {
        executeJS("setThinkingLabel(${jsStringEscape(text)})")
    }

    fun setSpinnerColor(cssColor: String) {
        executeJS("setSpinnerColor(${jsStringEscape(cssColor)})")
    }

    fun setSendHint(useCtrlEnter: Boolean) {
        executeJS("setSendHint($useCtrlEnter)")
    }

    fun setBranchPill(branchName: String?) {
        if (branchName != null) {
            executeJS("setBranchPill(${jsStringEscape(branchName)})")
        } else {
            executeJS("setBranchPill(null)")
        }
    }

    fun setTeleportLabel(target: String?) {
        if (target != null) {
            executeJS("setTeleportLabel(${jsStringEscape(target)})")
        } else {
            executeJS("setTeleportLabel(null)")
        }
    }

    // ── Thinking block bridge methods ────────────────────────────

    fun addThinkingBlock(id: String) {
        if (!hasMessages) {
            hasMessages = true
            showCard(CARD_MESSAGES)
        }
        if (browser != null) {
            executeJS("addThinkingBlock(${jsStringEscape(id)})")
        }
    }

    fun updateThinkingBlock(id: String, text: String) {
        if (browser != null) {
            executeJS("updateThinkingBlock(${jsStringEscape(id)}, ${jsStringEscape(text)})")
        }
    }

    fun finalizeThinkingBlock(id: String) {
        if (browser != null) {
            executeJS("finalizeThinkingBlock(${jsStringEscape(id)})")
        }
    }

    // ── Usage bar bridge ─────────────────────────────────────────

    fun updateUsageBar(percent: Int, label: String) {
        if (browser != null) {
            executeJS("updateUsageBar($percent, ${jsStringEscape(label)})")
        }
    }

    /**
     * Removes all message DOM elements from the given message ID onwards.
     */
    fun removeMessagesFrom(messageId: String) {
        if (browser != null) {
            executeJS(
                "removeMessagesFrom(${jsStringEscape(messageId)})"
            )
        }
    }

    /**
     * Replaces messages from the given ID with a summary.
     */
    fun replaceMessagesWithSummary(
        messageId: String,
        summaryText: String
    ) {
        if (browser != null) {
            executeJS(
                "replaceMessagesWithSummary(" +
                    "${jsStringEscape(messageId)}, " +
                    "${jsStringEscape(summaryText)})"
            )
        }
    }

    /**
     * Shows a badge on the rewind button for the given message.
     */
    fun setRewindBadge(messageId: String, fileCount: Int) {
        if (browser != null) {
            executeJS(
                "setRewindBadge(${jsStringEscape(messageId)}, $fileCount)"
            )
        }
    }

    private fun formatPermissionArgs(request: PermissionRequest): String {
        val input = request.input
        return when (request.toolName) {
            "Bash" -> input["command"]?.toString() ?: ""
            "Read", "Write", "Edit" -> input["file_path"]?.toString() ?: ""
            "Glob" -> input["pattern"]?.toString() ?: ""
            "Grep" -> {
                val pattern = input["pattern"]?.toString() ?: ""
                val path = input["path"]?.toString() ?: ""
                if (path.isNotEmpty()) "$pattern in $path" else pattern
            }
            else -> {
                // Generic: show key=value pairs
                input.entries
                    .filter { it.value != null }
                    .joinToString("\n") { (k, v) ->
                        val str = v.toString()
                        val display = if (str.length > 100) str.take(97) + "..." else str
                        "$k: $display"
                    }
            }
        }
    }

    fun clearMessages() {
        hasMessages = false
        if (browser != null) {
            executeJS("clearMessages()")
        } else {
            fallbackTextArea?.text = ""
        }
    }

    fun applyTheme() {
        if (browser == null) return
        val vars = buildThemeVars()
        executeJS("setThemeVars(${jsStringEscape(gson.toJson(vars))})")
    }

    // ── Private ──────────────────────────────────────────────────────

    private fun onBrowserReady() {
        // Inject the CefQuery callback bridge
        val rendererJs = codeBlockRenderer?.injectionJs
        if (rendererJs != null) {
            browser!!.cefBrowser.executeJavaScript(rendererJs, browser.cefBrowser.url, 0)
        }

        // Apply current theme
        val themeVars = gson.toJson(buildThemeVars())
        browser!!.cefBrowser.executeJavaScript(
            "setThemeVars(${jsStringLiteral(themeVars)})",
            browser.cefBrowser.url,
            0
        )

        // Flush queued JS calls
        synchronized(pendingCalls) {
            browserReady = true
            for (call in pendingCalls) {
                browser.cefBrowser.executeJavaScript(call, browser.cefBrowser.url, 0)
            }
            pendingCalls.clear()
        }
    }

    private fun executeJS(js: String) {
        if (browser == null) return
        synchronized(pendingCalls) {
            if (!browserReady) {
                pendingCalls.add(js)
                return
            }
        }
        browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
    }

    @Suppress("LongMethod")
    private fun buildHtml(): String {
        val markedJs = readResource("/chat/marked.min.js")
        val highlightJs = readResource("/chat/highlight.min.js")
        val highlightLightCss = readResource("/chat/highlight-light.css")
        val highlightDarkCss = readResource("/chat/highlight-dark.css")
        val chatCss = readResource("/chat/chat.css")
        val chatJs = readResource("/chat/chat.js")

        // Build slash commands JSON for the dropdown
        val slashCommandsJson = gson.toJson(
            SlashCommandRegistry.ALL_COMMANDS.map { cmd ->
                mapOf("name" to cmd.name, "description" to cmd.description)
            }
        )

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style id="highlight-light-theme">$highlightLightCss</style>
                <style id="highlight-dark-theme">$highlightDarkCss</style>
                <style>$chatCss</style>
            </head>
            <body>
                <div id="chat-container">
                    <!-- Header -->
                    <header id="header">
                        <button id="sessions-button">
                            <span class="status-dot" id="status-dot"></span>
                            <span class="sessions-button-text" id="session-title">New Conversation</span>
                            <span class="sessions-button-icon">
                                <svg width="10" height="10" viewBox="0 0 16 16" fill="currentColor">
                                    <path d="M4 6l4 4 4-4"/>
                                </svg>
                            </span>
                        </button>
                        <div class="header-spacer"></div>
                        <span id="branch-pill" class="branch-pill" style="display:none"></span>
                        <button class="header-btn" id="teleport-btn" title="Move to Tab" style="display:none">
                            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                                <rect x="1" y="1" width="14" height="14" rx="2"/>
                                <path d="M6 4l4 4-4 4"/>
                            </svg>
                        </button>
                        <button class="header-btn" id="new-conversation-btn" title="New Conversation">
                            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                                <path d="M8 3v10M3 8h10"/>
                            </svg>
                        </button>
                        <button class="header-btn" id="settings-btn" title="Settings">
                            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                                <circle cx="8" cy="8" r="2.5"/>
                                <path d="M8 1v2M8 13v2M1 8h2M13 8h2M2.9 2.9l1.4 1.4M11.7 11.7l1.4 1.4M13.1 2.9l-1.4 1.4M4.3 11.7l-1.4 1.4"/>
                            </svg>
                        </button>
                    </header>
                    <!-- Banners -->
                    <div id="banner-container"></div>
                    <!-- Empty state -->
                    <div id="empty-state">
                        <div class="empty-state-content">
                            <div class="empty-ascii">  ╭───────╮
 ╱  ●   ●  ╲
│     ◡     │
 ╲           ╱
  ╰─────────╯</div>
                            <div class="empty-prompt">What can I help you with?</div>
                        </div>
                    </div>
                    <!-- Messages -->
                    <div id="messages"></div>
                    <!-- Thinking indicator -->
                    <div id="thinking" class="thinking-indicator" style="display:none">
                        <span class="spinner-icon">
                            <svg width="16" height="16" viewBox="0 0 16 16">
                                <circle cx="8" cy="8" r="6" fill="none" stroke="currentColor"
                                    stroke-width="1.5" stroke-dasharray="28" stroke-dashoffset="8">
                                    <animateTransform attributeName="transform" type="rotate"
                                        from="0 8 8" to="360 8 8" dur="0.8s" repeatCount="indefinite"/>
                                </circle>
                            </svg>
                        </span>
                        <span class="spinner-text">Generating…</span>
                    </div>
                    <!-- Gradient overlay -->
                    <div class="message-gradient"></div>
                    <!-- Input area -->
                    <div id="input-area">
                        <div class="input-wrapper">
                            <div id="slash-dropdown" class="slash-dropdown"></div>
                            <div class="input-box" id="input-box">
                                <div class="input-box-bg"></div>
                                <div class="message-input-container">
                                    <div id="message-input"
                                         contenteditable="true"
                                         data-placeholder="What can I help you with?"
                                         role="textbox"></div>
                                </div>
                                <div id="attachment-chips" class="attachment-chips" style="display:none"></div>
                                <div id="usage-bar" class="usage-bar" style="display:none">
                                    <div class="usage-fill"></div>
                                </div>
                                <div class="input-footer">
                                    <button class="footer-btn" id="permission-mode-btn">
                                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                                            <path d="M8 1L2 4v4c0 3.3 2.6 6.4 6 7 3.4-.6 6-3.7 6-7V4L8 1z"/>
                                        </svg>
                                        <span id="permission-mode-label">Default</span>
                                    </button>
                                    <button class="footer-btn" id="model-btn">
                                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                                            <rect x="2" y="2" width="12" height="12" rx="2"/>
                                            <path d="M5 8h6M8 5v6"/>
                                        </svg>
                                        <span id="model-label">Default</span>
                                    </button>
                                    <button class="footer-btn" id="thinking-btn">
                                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                                            <circle cx="8" cy="6" r="4"/>
                                            <path d="M5.5 10c-.5 1-.5 2.5 0 4h5c.5-1.5.5-3 0-4"/>
                                            <path d="M6 12h4"/>
                                        </svg>
                                        <span id="thinking-label">Auto</span>
                                    </button>
                                    <div class="footer-spacer"></div>
                                    <span class="send-hint" id="send-hint"></span>
                                    <span class="footer-btn" id="cost-label">${'$'}0.00</span>
                                    <button id="send-btn" disabled>
                                        <svg viewBox="0 0 16 16" fill="currentColor">
                                            <path d="M3 13V3l10 5L3 13z"/>
                                        </svg>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <script>$markedJs</script>
                <script>$highlightJs</script>
                <script>$chatJs</script>
                <script>
                _initSlashCommands('${slashCommandsJson.replace("'", "\\'")}');
                _initPage();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun readResource(path: String): String {
        return try {
            javaClass.getResourceAsStream(path)?.bufferedReader()?.readText() ?: ""
        } catch (e: Exception) {
            logger.warn("Failed to read resource: $path", e)
            ""
        }
    }

    private fun buildThemeVars(): Map<String, String> {
        val isDark = !JBColor.isBright()
        // VS Code extension design system colours
        return buildMap {
            // Primary colours — extracted from IntelliJ theme
            put("--app-primary-foreground", resolveColor("Label.foreground", isDark, "#bcbcbc", "#1a1a1a"))
            put("--app-primary-background", resolveColor("SidePanel.background", isDark, "#2b2d30", "#f5f5f5"))
            put("--app-secondary-foreground", resolveColor("Label.disabledForeground", isDark, "#808080", "#666666"))
            put("--app-secondary-background", resolveColor("Editor.background", isDark, "#1e1f22", "#ffffff"))
            put("--app-input-background", resolveColor("TextField.background", isDark, "#2b2d30", "#ffffff"))
            put("--app-input-border", resolveColor("Component.borderColor", isDark, "#464648", "#c4c4c4"))
            put("--app-input-active-border", resolveColor("Component.focusedBorderColor", isDark, "#4e82c5", "#2675bf"))
            put("--app-tool-background", resolveColor("Editor.background", isDark, "#1e1f22", "#ffffff"))
            put("--app-button-background", resolveColor("Button.default.startBackground", isDark, "#365880", "#528bff"))
            put("--app-button-foreground", resolveColor("Button.default.foreground", isDark, "#bbbbbb", "#ffffff"))
            put("--app-error-foreground", resolveColor("Label.errorForeground", isDark, "#ff6b6b", "#c62828"))
            put("--app-success-foreground", if (isDark) "#74c991" else "#4caf50")
            put("--app-warning-accent", "#e5a54b")
            put("--app-status-busy", "#22c55e")
            put("--app-status-pending", "#3b82f6")
            put("--app-claude-orange", "#d97757")
            put("--app-spinner-foreground", if (isDark) "#d97757" else "#c6613f")
            put("--app-primary-border-color", resolveColor("Borders.color", isDark, "#464648", "#e0e0e0"))
            put("--app-link-color", resolveColor("Link.activeForeground", isDark, "#6bb8ff", "#1a56b0"))
            put("--app-transparent-inner-border", if (isDark) "rgba(255,255,255,0.1)" else "rgba(0,0,0,0.07)")
            put("--app-ghost-button-hover-background", resolveColor("ActionButton.hoverBackground", isDark, "#4c5052", "#dfdfdf"))
            put("--app-menu-background", resolveColor("PopupMenu.background", isDark, "#2b2d30", "#f5f5f5"))
            // VS Code parity vars
            put("--app-claude-clay-button-orange", if (isDark) "#d97757" else "#c6613f")
            put("--app-claude-ivory", "#faf9f5")
            put("--app-input-secondary-background", resolveColor("PopupMenu.background", isDark, "#2b2d30", "#f5f5f5"))
            put("--app-header-background", resolveColor("SidePanel.background", isDark, "#2b2d30", "#f5f5f5"))
            put("--app-list-hover-background", resolveColor("ActionButton.hoverBackground", isDark, "#4c5052", "#dfdfdf"))
            put("--app-list-active-background", resolveColor("Button.default.startBackground", isDark, "#365880", "#528bff"))
            put("--app-progressbar-background", "#74c991")
            // Scrollbar
            put("--scrollbar-thumb", if (isDark) "#555759" else "#c1c1c1")
            // Monospace/code font — use settings override or IDE default
            val fontSettings =
                com.claudecode.jetbrains.settings.ClaudeSettings
                    .getInstance()
            val codeFamily = fontSettings.editorFontFamily
                .ifBlank { monospaceFontFamily() }
            val codeSize = if (fontSettings.editorFontSize > 0) {
                fontSettings.editorFontSize
            } else {
                monospaceFontSize()
            }
            put("--app-monospace-font-family", "\"$codeFamily\", \"JetBrains Mono\", \"Fira Code\", \"Consolas\", monospace")
            put("--app-monospace-font-size", "${codeSize}px")
            put("--app-monospace-font-size-small", "${(codeSize - 2).coerceAtLeast(9)}px")
            // Chat font — use settings override or system default
            val chatFamily = fontSettings.chatFontFamily
                .ifBlank { "system-ui, -apple-system, sans-serif" }
            val chatSize = if (fontSettings.chatFontSize > 0) {
                fontSettings.chatFontSize
            } else {
                13
            }
            put("--app-chat-font-family", chatFamily)
            put("--app-chat-font-size", "${chatSize}px")
        }
    }

    private fun resolveColor(key: String, isDark: Boolean, darkFallback: String, lightFallback: String): String {
        return try {
            val color = JBColor.namedColor(key, if (isDark) Color.GRAY else Color.LIGHT_GRAY)
            colorToHex(color)
        } catch (_: Exception) {
            if (isDark) darkFallback else lightFallback
        }
    }

    private fun monospaceFontFamily(): String {
        return try {
            com.intellij.openapi.editor.colors.EditorColorsManager.getInstance()
                .globalScheme
                .getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN)
                .family
        } catch (_: Exception) {
            "JetBrains Mono"
        }
    }

    private fun monospaceFontSize(): Int {
        return try {
            com.intellij.openapi.editor.colors.EditorColorsManager.getInstance()
                .globalScheme.editorFontSize
        } catch (_: Exception) {
            13
        }
    }

    private fun colorToHex(color: Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }

    /**
     * Escapes a string for safe injection into JavaScript as a string literal.
     * Returns the quoted string: 'escaped content'
     */
    private fun jsStringEscape(text: String): String {
        return jsStringLiteral(text)
    }

    /**
     * Produces a JavaScript string literal (with quotes) from a Kotlin string.
     * Uses Gson to get proper JSON string escaping, which is also valid JS.
     */
    private fun jsStringLiteral(text: String): String {
        return gson.toJson(JsonPrimitive(text))
    }

    private fun scrollFallbackToBottom() {
        SwingUtilities.invokeLater {
            fallbackScrollPane?.let { sp ->
                sp.verticalScrollBar.value = sp.verticalScrollBar.maximum
            }
        }
    }

    private fun showCard(name: String) {
        (layout as CardLayout).show(this, name)
    }

    companion object {
        private const val CARD_MESSAGES = "messages"
    }
}
