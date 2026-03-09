package com.claudecode.jetbrains.ui

import com.claudecode.jetbrains.context.SelectionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SelectionContext data class behavior.
 */
class SelectionContextTest {

    @Test
    fun `selection context stores correct properties`() {
        val ctx = SelectionContext(
            fileName = "Main.kt",
            relativePath = "src/main/Main.kt",
            startLine = 5,
            endLine = 10,
            lineCount = 6,
            selectedText = "fun main() { ... }"
        )
        assertEquals("Main.kt", ctx.fileName)
        assertEquals("src/main/Main.kt", ctx.relativePath)
        assertEquals(5, ctx.startLine)
        assertEquals(10, ctx.endLine)
        assertEquals(6, ctx.lineCount)
        assertEquals("fun main() { ... }", ctx.selectedText)
    }

    @Test
    fun `single line selection has lineCount 1`() {
        val ctx = SelectionContext(
            fileName = "App.kt",
            relativePath = "src/App.kt",
            startLine = 3,
            endLine = 3,
            lineCount = 1,
            selectedText = "val x = 1"
        )
        assertEquals(1, ctx.lineCount)
        assertEquals(ctx.startLine, ctx.endLine)
    }

    @Test
    fun `selection context equality works`() {
        val ctx1 = SelectionContext("A.kt", "src/A.kt", 1, 5, 5, "text")
        val ctx2 = SelectionContext("A.kt", "src/A.kt", 1, 5, 5, "text")
        assertEquals(ctx1, ctx2)
    }

    @Test
    fun `selection context inequality on different lines`() {
        val ctx1 = SelectionContext("A.kt", "src/A.kt", 1, 5, 5, "text")
        val ctx2 = SelectionContext("A.kt", "src/A.kt", 2, 6, 5, "text")
        assertTrue(ctx1 != ctx2)
    }

    @Test
    fun `file reference format with line range`() {
        val ctx = SelectionContext(
            fileName = "Main.kt",
            relativePath = "src/main/Main.kt",
            startLine = 3,
            endLine = 7,
            lineCount = 5,
            selectedText = "some code"
        )
        val ref = "@${ctx.relativePath}#${ctx.startLine}-${ctx.endLine}"
        assertEquals("@src/main/Main.kt#3-7", ref)
    }

    @Test
    fun `file reference format with single line`() {
        val ctx = SelectionContext(
            fileName = "Main.kt",
            relativePath = "src/main/Main.kt",
            startLine = 5,
            endLine = 5,
            lineCount = 1,
            selectedText = "val x = 1"
        )
        val ref = if (ctx.startLine == ctx.endLine) {
            "@${ctx.relativePath}#${ctx.startLine}"
        } else {
            "@${ctx.relativePath}#${ctx.startLine}-${ctx.endLine}"
        }
        assertEquals("@src/main/Main.kt#5", ref)
    }

    @Test
    fun `selection label format for single line`() {
        val ctx = SelectionContext("Main.kt", "src/Main.kt", 5, 5, 1, "x")
        val label = if (ctx.lineCount == 1) "1 line" else "${ctx.lineCount} lines"
        assertEquals("1 line", label)
    }

    @Test
    fun `selection label format for multiple lines`() {
        val ctx = SelectionContext("Main.kt", "src/Main.kt", 5, 8, 4, "code")
        val label = if (ctx.lineCount == 1) "1 line" else "${ctx.lineCount} lines"
        assertEquals("4 lines", label)
    }
}
