package com.claudecode.jetbrains.ui

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that verify CSS/JS resources contain expected structures and functions.
 */
class UiParityTest {

    private lateinit var css: String
    private lateinit var js: String

    @Before
    fun setUp() {
        css = readResource("/chat/chat.css")
        js = readResource("/chat/chat.js")
        assertTrue("chat.css should not be empty", css.isNotEmpty())
        assertTrue("chat.js should not be empty", js.isNotEmpty())
    }

    // ── CSS Structural Tests ────────────────────────────────────

    @Test
    fun `thinking label is italic`() {
        assertCssBlockContains(".thinking-label", "font-style: italic")
    }

    @Test
    fun `tool name is semibold`() {
        assertCssBlockContains(".tool-name", "font-weight: 600")
    }

    @Test
    fun `empty prompt uses 10px font`() {
        assertCssBlockContains(".empty-prompt", "font-size: 10px")
    }

    @Test
    fun `empty state content wrapper exists`() {
        assertTrue("CSS should contain .empty-state-content", css.contains(".empty-state-content"))
    }

    @Test
    fun `new CSS variables exist`() {
        val requiredVars = listOf(
            "--app-claude-clay-button-orange",
            "--app-claude-ivory",
            "--app-input-secondary-background",
            "--app-header-background",
            "--app-list-hover-background",
            "--app-list-active-background",
            "--app-progressbar-background"
        )
        for (v in requiredVars) {
            assertTrue("CSS should contain variable $v", css.contains(v))
        }
    }

    // ── JS Function Tests ───────────────────────────────────────

    @Test
    fun `header and input CSS selectors exist`() {
        val requiredSelectors = listOf(
            "#chat-container", "#header", "#sessions-button",
            ".status-dot", ".header-btn", "#input-area",
            ".input-wrapper", ".input-box", "#message-input",
            ".input-footer", ".footer-btn", "#send-btn"
        )
        for (sel in requiredSelectors) {
            assertTrue("CSS should contain selector $sel", css.contains(sel))
        }
    }

    @Test
    fun `all required JS functions exist`() {
        val requiredFunctions = listOf(
            // Original functions
            "addMessage", "updateStreamingMessage", "finalizeMessage",
            "showThinking", "setThemeVars", "clearMessages",
            "addToolBlock", "updateToolBlock", "setToolBlockBody",
            "addPermissionCard", "respondPermission",
            "showEmptyState", "updateUsageBar",
            "createHoverActions", "copyMessageContent", "retryFromMessage",
            "forkFromMessage", "forkIcon",
            "addThinkingBlock", "updateThinkingBlock", "finalizeThinkingBlock",
            // Header/input functions (JCEF refactor)
            "handleSessionsClick", "handleNewConversation", "handleSettings",
            "setSessionTitle", "setStatusDot",
            "handleSend", "handleInputKeyDown", "setInputEnabled", "focusInput",
            "setInputText", "insertTextAtCursor",
            "setModelLabel", "setCostLabel", "setPermissionModeLabel", "setThinkingLabel",
            "handleModelClick", "handlePermissionModeClick", "handleThinkingClick"
        )
        for (fn in requiredFunctions) {
            assertTrue(
                "JS should contain function $fn",
                js.contains("function $fn(") || js.contains("function $fn (")
            )
        }
    }

    @Test
    fun `hover actions include Copy Fork and Retry`() {
        val fnStart = js.indexOf("function createHoverActions(")
        assertTrue("createHoverActions should exist", fnStart >= 0)
        val fnBody = js.substring(fnStart, js.indexOf("\n}", fnStart) + 2)
        assertTrue("createHoverActions should have Copy", fnBody.contains("\"Copy\""))
        assertTrue("createHoverActions should have Fork", fnBody.contains("\"Fork\""))
        assertTrue("createHoverActions should have Retry", fnBody.contains("\"Retry\""))
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun readResource(path: String): String {
        return javaClass.getResourceAsStream(path)?.bufferedReader()?.readText() ?: ""
    }

    private fun assertCssBlockContains(selector: String, property: String) {
        val pattern = Regex("""\n${Regex.escape(selector)}\s*\{""")
        val match = pattern.find(css)
        assertTrue("CSS should contain standalone selector '$selector'", match != null)
        val blockStart = css.indexOf('{', match!!.range.first)
        val blockEnd = css.indexOf('}', blockStart)
        val block = css.substring(blockStart, blockEnd)
        assertTrue(
            "CSS block for $selector should contain '$property'",
            block.contains(property)
        )
    }
}
