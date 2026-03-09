package com.claudecode.jetbrains.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SessionManager logic without requiring IntelliJ Platform APIs.
 * Tests the companion object methods and SessionInfo data class directly.
 */
class SessionManagerTest {

    @Test
    fun `SessionInfo stores properties correctly`() {
        val info = SessionInfo(
            id = "test-id",
            startTime = 1000L,
            projectPath = "/my/project"
        )
        assertEquals("test-id", info.id)
        assertEquals(1000L, info.startTime)
        assertEquals("/my/project", info.projectPath)
        assertEquals("", info.title)
        assertEquals("", info.firstPrompt)
        assertEquals(1000L, info.lastActiveTime)
        assertEquals("", info.model)
    }

    @Test
    fun `SessionInfo instances with different IDs are not equal`() {
        val info1 = SessionInfo("id-1", 1000L, "/path")
        val info2 = SessionInfo("id-2", 1000L, "/path")
        assertNotEquals(info1, info2)
    }

    @Test
    fun `buildResumeArgs returns correct flags`() {
        val args = SessionManager.buildResumeArgs("sess-abc-123")
        assertEquals(listOf("--resume", "sess-abc-123"), args)
    }

    @Test
    fun `buildContinueArgs returns correct flag`() {
        val args = SessionManager.buildContinueArgs()
        assertEquals(listOf("--continue"), args)
    }

    @Test
    fun `session tracking with ConcurrentHashMap directly`() {
        val sessions =
            java.util.concurrent.ConcurrentHashMap<String, SessionInfo>()

        val session1 = SessionInfo(
            "id-1", System.currentTimeMillis(), "/project1"
        )
        val session2 = SessionInfo(
            "id-2", System.currentTimeMillis() + 100, "/project2"
        )
        sessions["id-1"] = session1
        sessions["id-2"] = session2

        assertEquals(session1, sessions["id-1"])
        assertEquals(session2, sessions["id-2"])
        assertNull(sessions["nonexistent"])

        val sorted = sessions.values
            .sortedByDescending { it.startTime }
        assertEquals("id-2", sorted[0].id)
        assertEquals("id-1", sorted[1].id)

        sessions.remove("id-1")
        assertNull(sessions["id-1"])
        assertNotNull(sessions["id-2"])
        assertEquals(1, sessions.size)
    }

    @Test
    fun `UUID generation produces unique IDs`() {
        val ids = (1..100).map {
            java.util.UUID.randomUUID().toString()
        }.toSet()
        assertEquals(100, ids.size)
        ids.forEach { id ->
            assertTrue(
                "UUID has correct format: $id",
                id.matches(
                    Regex(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}" +
                            "-[0-9a-f]{4}-[0-9a-f]{12}"
                    )
                )
            )
        }
    }

    @Test
    fun `buildResumeArgs with different session IDs`() {
        val args1 = SessionManager.buildResumeArgs("session-1")
        val args2 = SessionManager.buildResumeArgs("session-2")
        assertEquals("session-1", args1[1])
        assertEquals("session-2", args2[1])
        assertEquals("--resume", args1[0])
        assertEquals("--resume", args2[0])
    }

    @Test
    fun `SessionInfo copy works correctly`() {
        val original = SessionInfo(
            "id-1", 5000L, "/path/to/project"
        )
        val copy = original.copy(id = "id-2")
        assertEquals("id-2", copy.id)
        assertEquals(5000L, copy.startTime)
        assertEquals("/path/to/project", copy.projectPath)
    }

    // ── displayTitle tests ──────────────────────────────────────

    @Test
    fun `displayTitle returns custom title when set`() {
        val session = SessionInfo("id", 1000L, "/path")
        session.title = "My Custom Title"
        session.firstPrompt = "This is my first prompt"
        assertEquals("My Custom Title", session.displayTitle())
    }

    @Test
    fun `displayTitle returns truncated first prompt when no title`() {
        val session = SessionInfo("id", 1000L, "/path")
        session.firstPrompt = "This is a very long prompt " +
            "that should be truncated because it exceeds fifty characters"
        val title = session.displayTitle()
        assertTrue(
            "Title should end with ...",
            title.endsWith("...")
        )
        assertTrue(
            "Title should be at most 50 chars",
            title.length <= SessionInfo.MAX_AUTO_TITLE_LENGTH
        )
    }

    @Test
    fun `displayTitle returns short first prompt untruncated`() {
        val session = SessionInfo("id", 1000L, "/path")
        session.firstPrompt = "Fix the bug"
        assertEquals("Fix the bug", session.displayTitle())
    }

    @Test
    fun `displayTitle returns Untitled when no title or prompt`() {
        val session = SessionInfo("id", 1000L, "/path")
        assertEquals("Untitled session", session.displayTitle())
    }

    @Test
    fun `displayTitle replaces newlines in first prompt`() {
        val session = SessionInfo("id", 1000L, "/path")
        session.firstPrompt = "Line one\nLine two\nLine three"
        val title = session.displayTitle()
        assertTrue(
            "Title should not contain newlines",
            !title.contains('\n')
        )
        assertEquals("Line one Line two Line three", title)
    }

    // ── lastActiveTime defaults ────────────────────────────────

    @Test
    fun `lastActiveTime defaults to startTime`() {
        val session = SessionInfo("id", 42000L, "/path")
        assertEquals(42000L, session.lastActiveTime)
    }

    @Test
    fun `lastActiveTime can be updated`() {
        val session = SessionInfo("id", 1000L, "/path")
        session.lastActiveTime = 5000L
        assertEquals(5000L, session.lastActiveTime)
    }

    // ── model field ────────────────────────────────────────────

    @Test
    fun `model defaults to empty`() {
        val session = SessionInfo("id", 1000L, "/path")
        assertEquals("", session.model)
    }

    @Test
    fun `model can be set`() {
        val session = SessionInfo("id", 1000L, "/path")
        session.model = "claude-3-opus"
        assertEquals("claude-3-opus", session.model)
    }

    // ── SessionInfo with all fields ────────────────────────────

    @Test
    fun `SessionInfo can be created with all fields`() {
        val session = SessionInfo(
            id = "full-id",
            startTime = 1000L,
            projectPath = "/full/path",
            title = "Full Title",
            firstPrompt = "Hello Claude",
            lastActiveTime = 2000L,
            model = "claude-3-sonnet"
        )
        assertEquals("full-id", session.id)
        assertEquals(1000L, session.startTime)
        assertEquals("/full/path", session.projectPath)
        assertEquals("Full Title", session.title)
        assertEquals("Hello Claude", session.firstPrompt)
        assertEquals(2000L, session.lastActiveTime)
        assertEquals("claude-3-sonnet", session.model)
    }
}
