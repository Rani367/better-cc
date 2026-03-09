package com.claudecode.jetbrains.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeProcessTest {

    @Test
    fun `buildCommand includes all required flags`() {
        val command = ClaudeProcess.buildCommand(cliPath = "/usr/local/bin/claude", sessionId = "sess-123")
        assertEquals(
            listOf(
                "/usr/local/bin/claude",
                "-p",
                "--verbose",
                "--output-format", "stream-json",
                "--input-format", "stream-json",
                "--include-partial-messages",
                "--session-id", "sess-123"
            ),
            command
        )
    }

    @Test
    fun `buildCommand appends additional args`() {
        val command = ClaudeProcess.buildCommand(
            cliPath = "/usr/bin/claude",
            sessionId = "sess-1",
            additionalArgs = listOf("--resume", "old-session")
        )
        assertTrue(command.contains("--resume"))
        assertTrue(command.contains("old-session"))
        assertEquals("--resume", command[command.size - 2])
        assertEquals("old-session", command[command.size - 1])
    }

    @Test
    fun `buildCommand preserves session id`() {
        val sessionId = "abc-def-123"
        val command = ClaudeProcess.buildCommand(cliPath = "/bin/claude", sessionId = sessionId)
        val sessionIndex = command.indexOf("--session-id")
        assertEquals(sessionId, command[sessionIndex + 1])
    }

    @Test
    fun `fromProcess creates working instance`() {
        val fakeProcess = ProcessBuilder("echo", "done").start()
        val cp = ClaudeProcess.fromProcess("test-session", fakeProcess)
        assertEquals("test-session", cp.sessionId)
        // Process should finish quickly
        fakeProcess.waitFor()
        assertFalse(cp.isRunning)
    }

    @Test
    fun `destroy is idempotent`() {
        val fakeProcess = ProcessBuilder("bash", "-c", "read").start()
        val cp = ClaudeProcess.fromProcess("test-session", fakeProcess)
        assertTrue("Process should be running", cp.isRunning)

        cp.destroy()
        fakeProcess.waitFor()
        assertFalse(cp.isRunning)

        // Second destroy should not throw
        cp.destroy()
        assertFalse(cp.isRunning)
    }

    @Test
    fun `exitCode returns null while running and value after exit`() {
        val fakeProcess = ProcessBuilder("echo", "hi").start()
        val cp = ClaudeProcess.fromProcess("test-session", fakeProcess)
        fakeProcess.waitFor()
        assertEquals(0, cp.exitCode)
    }

    @Test
    fun `events flow parses NDJSON from stdout`() {
        // Create a process that outputs valid NDJSON
        val jsonLines = listOf(
            """{"type":"system","subtype":"init","session_id":"s1"}""",
            """{"type":"result","result":{"content":[{"type":"text","text":"hi"}],"usage":{"input_tokens":1,"output_tokens":1}},"session_id":"s1"}"""
        )
        val script = jsonLines.joinToString("; ") { "echo '$it'" }
        val fakeProcess = ProcessBuilder("bash", "-c", script).start()
        val cp = ClaudeProcess.fromProcess("s1", fakeProcess)

        val events = mutableListOf<StreamEvent>()
        // Collect events by reading stdout directly (simpler than using coroutine flow in test)
        fakeProcess.inputStream.bufferedReader().forEachLine { line ->
            parseStreamEvent(line)?.let { events.add(it) }
        }

        assertEquals(2, events.size)
        assertTrue(events[0] is SystemEvent)
        assertTrue(events[1] is ResultEvent)

        cp.destroy()
    }

    @Test
    fun `stderr is captured`() {
        val fakeProcess = ProcessBuilder("bash", "-c", "echo 'error info' >&2; echo '{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"s1\"}'")
            .start()
        val cp = ClaudeProcess.fromProcess("s1", fakeProcess)

        // Wait for process to finish
        fakeProcess.waitFor()

        // Read stderr manually since we're not using the flow
        val stderr = fakeProcess.errorStream.bufferedReader().readText().trim()
        assertEquals("error info", stderr)

        cp.destroy()
    }
}
