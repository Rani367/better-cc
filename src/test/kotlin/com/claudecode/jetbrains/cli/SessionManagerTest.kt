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
        // Test the core tracking logic using a simple map (same data structure as SessionManager)
        val sessions = java.util.concurrent.ConcurrentHashMap<String, SessionInfo>()

        // Create sessions
        val session1 = SessionInfo("id-1", System.currentTimeMillis(), "/project1")
        val session2 = SessionInfo("id-2", System.currentTimeMillis() + 100, "/project2")
        sessions["id-1"] = session1
        sessions["id-2"] = session2

        // Get by ID
        assertEquals(session1, sessions["id-1"])
        assertEquals(session2, sessions["id-2"])
        assertNull(sessions["nonexistent"])

        // List sorted by start time descending
        val sorted = sessions.values.sortedByDescending { it.startTime }
        assertEquals("id-2", sorted[0].id) // newer first
        assertEquals("id-1", sorted[1].id)

        // Remove
        sessions.remove("id-1")
        assertNull(sessions["id-1"])
        assertNotNull(sessions["id-2"])
        assertEquals(1, sessions.size)
    }

    @Test
    fun `UUID generation produces unique IDs`() {
        val ids = (1..100).map { java.util.UUID.randomUUID().toString() }.toSet()
        assertEquals(100, ids.size) // All unique
        ids.forEach { id ->
            // UUID format: 8-4-4-4-12 hex chars
            assertTrue("UUID has correct format: $id", id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
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
        val original = SessionInfo("id-1", 5000L, "/path/to/project")
        val copy = original.copy(id = "id-2")
        assertEquals("id-2", copy.id)
        assertEquals(5000L, copy.startTime)
        assertEquals("/path/to/project", copy.projectPath)
    }
}
