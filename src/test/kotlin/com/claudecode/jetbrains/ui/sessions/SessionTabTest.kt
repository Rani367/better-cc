package com.claudecode.jetbrains.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ClaudeVirtualFile] and tab naming logic.
 * These tests do not require IntelliJ Platform APIs.
 */
class SessionTabTest {

    @Test
    fun `ClaudeVirtualFile has correct properties`() {
        val file = ClaudeVirtualFile("tab-1", "Better Claude Code")
        assertEquals("Better Claude Code", file.name)
        assertEquals("claude-code://tab-1", file.path)
        assertEquals("tab-1", file.tabId)
        assertFalse(file.isDirectory)
        assertFalse(file.isWritable)
        assertTrue(file.isValid)
    }

    @Test
    fun `ClaudeVirtualFile default status is NORMAL`() {
        val file = ClaudeVirtualFile("tab-1")
        assertEquals(
            ClaudeVirtualFile.TabStatus.NORMAL,
            file.tabStatus
        )
    }

    @Test
    fun `ClaudeVirtualFile status can be updated`() {
        val file = ClaudeVirtualFile("tab-1")
        file.tabStatus = ClaudeVirtualFile.TabStatus.PERMISSION_PENDING
        assertEquals(
            ClaudeVirtualFile.TabStatus.PERMISSION_PENDING,
            file.tabStatus
        )
        file.tabStatus = ClaudeVirtualFile.TabStatus.TASK_COMPLETE
        assertEquals(
            ClaudeVirtualFile.TabStatus.TASK_COMPLETE,
            file.tabStatus
        )
    }

    @Test
    fun `ClaudeVirtualFile display name can be changed`() {
        val file = ClaudeVirtualFile("tab-1", "Better Claude Code")
        assertEquals("Better Claude Code", file.name)
        file.setDisplayName("Fix the bug in parser")
        assertEquals("Fix the bug in parser", file.name)
    }

    @Test
    fun `ClaudeVirtualFile equality is based on tabId`() {
        val file1 = ClaudeVirtualFile("tab-1", "Better Claude Code")
        val file2 = ClaudeVirtualFile("tab-1", "Better Claude Code (2)")
        val file3 = ClaudeVirtualFile("tab-2", "Better Claude Code")

        assertEquals(file1, file2)
        assertNotEquals(file1, file3)
        assertEquals(file1.hashCode(), file2.hashCode())
    }

    @Test
    fun `ClaudeVirtualFile path includes tabId`() {
        val file = ClaudeVirtualFile("abc-123")
        assertTrue(file.path.contains("abc-123"))
    }

    @Test
    fun `ClaudeVirtualFile filesystem protocol is correct`() {
        val file = ClaudeVirtualFile("tab-1")
        assertEquals("claude-code", file.fileSystem.protocol)
    }

    @Test
    fun `ClaudeVirtualFile contentsToByteArray returns empty`() {
        val file = ClaudeVirtualFile("tab-1")
        assertEquals(0, file.contentsToByteArray().size)
    }

    @Test
    fun `ClaudeVirtualFile getInputStream returns empty stream`() {
        val file = ClaudeVirtualFile("tab-1")
        val stream = file.inputStream
        assertNotNull(stream)
        assertEquals(-1, stream.read())
    }

    @Test
    fun `ClaudeVirtualFile has no parent or children`() {
        val file = ClaudeVirtualFile("tab-1")
        assertTrue("parent should be null", file.parent == null)
        assertTrue("children should be null", file.children == null)
    }

    // ── Tab naming convention ───────────────────────────────

    @Test
    fun `tab numbering produces expected display names`() {
        // Simulate the naming logic from SessionTabManager
        val names = (1..5).map { number ->
            if (number == 1) "Better Claude Code"
            else "Better Claude Code ($number)"
        }
        assertEquals("Better Claude Code", names[0])
        assertEquals("Better Claude Code (2)", names[1])
        assertEquals("Better Claude Code (3)", names[2])
        assertEquals("Better Claude Code (4)", names[3])
        assertEquals("Better Claude Code (5)", names[4])
    }

    // ── TabStatus enum ──────────────────────────────────────

    @Test
    fun `TabStatus enum has expected values`() {
        val values = ClaudeVirtualFile.TabStatus.entries
        assertEquals(3, values.size)
        assertTrue(
            values.contains(ClaudeVirtualFile.TabStatus.NORMAL)
        )
        assertTrue(
            values.contains(
                ClaudeVirtualFile.TabStatus.PERMISSION_PENDING
            )
        )
        assertTrue(
            values.contains(ClaudeVirtualFile.TabStatus.TASK_COMPLETE)
        )
    }
}
