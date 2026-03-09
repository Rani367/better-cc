package com.claudecode.jetbrains.checkpoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointManagerTest {

    @Test
    fun `FileSnapshot captures new file flag`() {
        val snapshot = FileSnapshot(
            filePath = "/tmp/test.kt",
            originalContent = null,
            isNewFile = true
        )
        assertTrue(snapshot.isNewFile)
        assertNull(snapshot.originalContent)
    }

    @Test
    fun `FileSnapshot captures existing file content`() {
        val content = "fun main() {}"
        val snapshot = FileSnapshot(
            filePath = "/tmp/existing.kt",
            originalContent = content,
            isNewFile = false
        )
        assertFalse(snapshot.isNewFile)
        assertEquals(content, snapshot.originalContent)
    }

    @Test
    fun `Checkpoint tracks file snapshots`() {
        val cp = Checkpoint(
            sessionId = "session-1",
            messageIndex = 0,
            userMessageId = "msg-1",
            userPromptText = "Fix the bug"
        )

        assertEquals(0, cp.changedFileCount)

        cp.fileSnapshots.add(
            FileSnapshot("/tmp/a.kt", "old", false)
        )
        assertEquals(1, cp.changedFileCount)

        cp.fileSnapshots.add(
            FileSnapshot("/tmp/b.kt", null, true)
        )
        assertEquals(2, cp.changedFileCount)
    }

    @Test
    fun `Checkpoint preserves user prompt text`() {
        val prompt = "Add logging to the service"
        val cp = Checkpoint(
            sessionId = "s1",
            messageIndex = 3,
            userMessageId = "m3",
            userPromptText = prompt
        )
        assertEquals(prompt, cp.userPromptText)
        assertEquals(3, cp.messageIndex)
    }

    @Test
    fun `RewindResult reports skipped files`() {
        val result = RewindResult(
            restoredFileCount = 3,
            skippedFiles = listOf("/tmp/locked.kt"),
            warnings = listOf("File was modified externally")
        )
        assertEquals(3, result.restoredFileCount)
        assertEquals(1, result.skippedFiles.size)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `RewindResult with no issues`() {
        val result = RewindResult(restoredFileCount = 5)
        assertEquals(5, result.restoredFileCount)
        assertTrue(result.skippedFiles.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `Checkpoint createdAt is set`() {
        val before = System.currentTimeMillis()
        val cp = Checkpoint(
            sessionId = "s1",
            messageIndex = 0,
            userMessageId = "m1",
            userPromptText = "test"
        )
        val after = System.currentTimeMillis()
        assertTrue(
            "createdAt should be between before and after",
            cp.createdAt in before..after
        )
    }

    @Test
    fun `Multiple snapshots for same checkpoint`() {
        val cp = Checkpoint(
            sessionId = "s1",
            messageIndex = 0,
            userMessageId = "m1",
            userPromptText = "refactor"
        )
        val files = listOf("a.kt", "b.kt", "c.kt")
        files.forEach { f ->
            cp.fileSnapshots.add(
                FileSnapshot(f, "content-$f", false)
            )
        }
        assertEquals(3, cp.changedFileCount)
        assertEquals(
            files,
            cp.fileSnapshots.map { it.filePath }
        )
    }
}
