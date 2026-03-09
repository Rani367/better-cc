package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.cli.PermissionRequest
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
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingConstants
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

    // Empty state (emptyState_07S1Yg) — centered, offset 30px up, with ASCII art
    @Suppress("SpellCheckingInspection")
    private val asciiArt = """
        |     ╭───────╮
        |    ╱  ●   ●  ╲
        |   │     ◡     │
        |    ╲           ╱
        |     ╰─────────╯
    """.trimMargin()

    private val emptyPanel = JPanel(java.awt.GridBagLayout()).apply {
        val gbc = java.awt.GridBagConstraints().apply {
            anchor = java.awt.GridBagConstraints.CENTER
            gridx = 0
            insets = java.awt.Insets(-JBUI.scale(30), 0, 0, 0)
        }

        // ASCII art
        gbc.gridy = 0
        add(JLabel(
            "<html><pre style='text-align:center;color:#d97757;font-size:12px;'>" +
                asciiArt.replace("\n", "<br>") + "</pre></html>"
        ).apply {
            horizontalAlignment = SwingConstants.CENTER
        }, gbc)

        // Text
        gbc.gridy = 1
        gbc.insets = java.awt.Insets(JBUI.scale(8), 0, 0, 0)
        add(JLabel(
            "<html><div style='text-align:center;font-family:monospace;font-size:10px;" +
                "color:gray;'>What can I help you with?</div></html>"
        ).apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = JBColor.GRAY
        }, gbc)

        isOpaque = true
    }

    private var hasMessages = false

    init {
        add(emptyPanel, CARD_EMPTY)

        if (JBCefApp.isSupported()) {
            browser = JBCefBrowser()
            fallbackTextArea = null
            fallbackScrollPane = null

            // Make browser not steal focus
            browser.component.isFocusable = false

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

        showCard(CARD_EMPTY)
    }

    fun addMessage(message: ChatMessage) {
        if (!hasMessages) {
            hasMessages = true
            showCard(CARD_MESSAGES)
        }

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
            if (!hasMessages) {
                hasMessages = true
                showCard(CARD_MESSAGES)
            }
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
            if (visible && !hasMessages) {
                hasMessages = true
                showCard(CARD_MESSAGES)
            }
            executeJS("showThinking($visible)")
        } else {
            if (visible) {
                fallbackTextArea?.append("Thinking...\n")
            }
        }
    }

    fun addToolBlock(tool: ToolUseState) {
        if (!hasMessages) {
            hasMessages = true
            showCard(CARD_MESSAGES)
        }
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

    fun addPermissionCard(request: PermissionRequest) {
        if (!hasMessages) {
            hasMessages = true
            showCard(CARD_MESSAGES)
        }
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
        showCard(CARD_EMPTY)
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

    private fun buildHtml(): String {
        val markedJs = readResource("/chat/marked.min.js")
        val highlightJs = readResource("/chat/highlight.min.js")
        val highlightLightCss = readResource("/chat/highlight-light.css")
        val highlightDarkCss = readResource("/chat/highlight-dark.css")
        val chatCss = readResource("/chat/chat.css")
        val chatJs = readResource("/chat/chat.js")

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
                <div id="messages"></div>
                <div id="thinking" class="thinking-indicator" style="display:none">
                    <span class="thinking-dot"></span>
                    <span class="thinking-dot"></span>
                    <span class="thinking-dot"></span>
                </div>
                <script>$markedJs</script>
                <script>$highlightJs</script>
                <script>$chatJs</script>
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
            // Scrollbar
            put("--scrollbar-thumb", if (isDark) "#555759" else "#c1c1c1")
            // Monospace font
            put("--app-monospace-font-family", "\"${monospaceFontFamily()}\", \"JetBrains Mono\", \"Fira Code\", \"Consolas\", monospace")
            put("--app-monospace-font-size", "${monospaceFontSize()}px")
            put("--app-monospace-font-size-small", "${(monospaceFontSize() - 2).coerceAtLeast(9)}px")
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
        private const val CARD_EMPTY = "empty"
        private const val CARD_MESSAGES = "messages"
    }
}
