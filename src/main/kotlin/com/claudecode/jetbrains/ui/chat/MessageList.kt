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

    // Empty state
    private val emptyLabel = JLabel(
        "Send a message to start chatting with Claude",
        SwingConstants.CENTER
    ).apply {
        foreground = JBColor.GRAY
    }
    private val emptyPanel = JPanel(BorderLayout()).apply {
        add(emptyLabel, BorderLayout.CENTER)
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
                font = Font(Font.MONOSPACED, Font.PLAIN, 13)
                border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
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
        return if (isDark) {
            mapOf(
                "--bg-primary" to colorToHex(Color(0x2B, 0x2D, 0x30)),
                "--bg-secondary" to colorToHex(Color(0x38, 0x3A, 0x3D)),
                "--bg-user" to colorToHex(Color(0x1E, 0x3A, 0x5F)),
                "--bg-assistant" to colorToHex(Color(0x36, 0x39, 0x3D)),
                "--bg-error" to colorToHex(Color(0x50, 0x20, 0x20)),
                "--bg-code" to colorToHex(Color(0x1E, 0x1F, 0x22)),
                "--text-primary" to colorToHex(Color(0xBC, 0xBC, 0xBC)),
                "--text-secondary" to colorToHex(Color(0x80, 0x80, 0x80)),
                "--text-user-name" to colorToHex(Color(0x6B, 0xB8, 0xFF)),
                "--text-assistant-name" to colorToHex(Color(0xE8, 0xA5, 0x50)),
                "--text-error-name" to colorToHex(Color(0xFF, 0x6B, 0x6B)),
                "--border-color" to colorToHex(Color(0x46, 0x48, 0x4A)),
                "--code-header-bg" to colorToHex(Color(0x38, 0x3A, 0x3D)),
                "--scrollbar-thumb" to colorToHex(Color(0x55, 0x57, 0x59)),
                "--link-color" to colorToHex(Color(0x6B, 0xB8, 0xFF)),
                "--thinking-dot" to colorToHex(Color(0x70, 0x70, 0x70)),
                "--tool-bg" to colorToHex(Color(0x2F, 0x31, 0x34)),
                "--tool-border" to colorToHex(Color(0x46, 0x48, 0x4A)),
                "--tool-header-bg" to colorToHex(Color(0x35, 0x37, 0x3A)),
                "--tool-read-accent" to colorToHex(Color(0x6B, 0xB8, 0xFF)),
                "--tool-write-accent" to colorToHex(Color(0xE8, 0xA5, 0x50)),
                "--tool-bash-accent" to colorToHex(Color(0x6A, 0xC4, 0x6A)),
                "--tool-other-accent" to colorToHex(Color(0x80, 0x80, 0x80)),
                "--perm-bg" to colorToHex(Color(0x3A, 0x35, 0x20)),
                "--perm-border" to colorToHex(Color(0x6B, 0x5B, 0x20)),
                "--perm-header-bg" to "rgba(255,255,255,0.04)",
                "--perm-icon-color" to colorToHex(Color(0xE8, 0xC8, 0x50)),
                "--perm-allowed-bg" to colorToHex(Color(0x1B, 0x3A, 0x1B)),
                "--perm-allowed-border" to colorToHex(Color(0x4C, 0xAF, 0x50)),
                "--perm-denied-bg" to colorToHex(Color(0x50, 0x20, 0x20)),
                "--perm-denied-border" to colorToHex(Color(0xE5, 0x39, 0x35)),
                "--perm-risk-normal-bg" to colorToHex(Color(0x46, 0x48, 0x4A))
            )
        } else {
            mapOf(
                "--bg-primary" to "#ffffff",
                "--bg-secondary" to "#f5f5f5",
                "--bg-user" to "#dbe9f7",
                "--bg-assistant" to "#f0f0f0",
                "--bg-error" to "#fde0e0",
                "--bg-code" to "#f6f8fa",
                "--text-primary" to "#1a1a1a",
                "--text-secondary" to "#666666",
                "--text-user-name" to "#1a56b0",
                "--text-assistant-name" to "#c46e00",
                "--text-error-name" to "#c62828",
                "--border-color" to "#e0e0e0",
                "--code-header-bg" to "#e8eaed",
                "--scrollbar-thumb" to "#c1c1c1",
                "--link-color" to "#1a56b0",
                "--thinking-dot" to "#999999",
                "--tool-bg" to "#f8f9fa",
                "--tool-border" to "#e0e0e0",
                "--tool-header-bg" to "#f0f1f3",
                "--tool-read-accent" to "#1a73e8",
                "--tool-write-accent" to "#e8a317",
                "--tool-bash-accent" to "#2e7d32",
                "--tool-other-accent" to "#757575",
                "--perm-bg" to "#fef9e7",
                "--perm-border" to "#d4a017",
                "--perm-header-bg" to "rgba(0,0,0,0.04)",
                "--perm-icon-color" to "#d4a017",
                "--perm-allowed-bg" to "#e8f5e9",
                "--perm-allowed-border" to "#4caf50",
                "--perm-denied-bg" to "#fde0e0",
                "--perm-denied-border" to "#e53935",
                "--perm-risk-normal-bg" to "#e0e0e0"
            )
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
