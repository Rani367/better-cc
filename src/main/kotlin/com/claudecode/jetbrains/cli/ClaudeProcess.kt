package com.claudecode.jetbrains.cli

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ClaudeProcess private constructor(
    val sessionId: String,
    private val process: Process
) {
    private val logger = Logger.getInstance(ClaudeProcess::class.java)

    private val stderrBuilder = StringBuilder()
    private val stdinWriter: BufferedWriter = BufferedWriter(
        OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
    )

    val isRunning: Boolean
        get() = process.isAlive

    val exitCode: Int?
        get() = if (process.isAlive) null else process.exitValue()

    val stderrOutput: String
        get() = synchronized(stderrBuilder) { stderrBuilder.toString() }

    /**
     * Returns a Flow of StreamEvents read from the process stdout.
     * Also starts stderr reading in a separate coroutine.
     * The flow completes when stdout is exhausted (process ends).
     *
     * @param inactivityTimeoutMs If no stdout activity for this duration, the process is killed.
     *        Pass 0 to disable the watchdog. Default: [DEFAULT_INACTIVITY_TIMEOUT_MS] (5 minutes).
     */
    fun events(inactivityTimeoutMs: Long = DEFAULT_INACTIVITY_TIMEOUT_MS): Flow<StreamEvent> = callbackFlow {
        val lastActivity = AtomicLong(System.currentTimeMillis())

        // Read stderr in a separate coroutine
        launch(Dispatchers.IO) {
            try {
                process.errorStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.forEachLine { line ->
                        synchronized(stderrBuilder) {
                            if (stderrBuilder.isNotEmpty()) stderrBuilder.append('\n')
                            stderrBuilder.append(line)
                        }
                        logger.debug("Claude CLI stderr: $line")
                    }
                }
            } catch (e: Exception) {
                logger.debug("Stderr reading ended", e)
            }
        }

        // Read stdout line-by-line and parse events
        launch(Dispatchers.IO) {
            try {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.forEachLine { line ->
                        lastActivity.set(System.currentTimeMillis())
                        val event = parseStreamEvent(line)
                        if (event != null) {
                            trySend(event)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.debug("Stdout reading ended", e)
            } finally {
                channel.close()
            }
        }

        // Inactivity watchdog
        if (inactivityTimeoutMs > 0) {
            launch {
                while (true) {
                    delay(WATCHDOG_CHECK_INTERVAL_MS)
                    val idle = System.currentTimeMillis() - lastActivity.get()
                    if (idle >= inactivityTimeoutMs) {
                        logger.warn("Claude CLI inactivity timeout (${idle}ms idle). Killing process.")
                        channel.close()
                        break
                    }
                }
            }
        }

        awaitClose {
            destroy()
        }
    }

    /**
     * Sends a user message to the process stdin as stream-json formatted JSON.
     */
    suspend fun sendMessage(text: String) {
        val json = serializeUserMessage(text, sessionId)
        withContext(Dispatchers.IO) {
            try {
                stdinWriter.write(json)
                stdinWriter.newLine()
                stdinWriter.flush()
            } catch (e: Exception) {
                logger.warn("Failed to write to Claude CLI stdin", e)
                throw e
            }
        }
    }

    /**
     * Kills the process and cleans up resources. Idempotent.
     */
    fun destroy() {
        try {
            stdinWriter.close()
        } catch (_: Exception) {
        }
        if (process.isAlive) {
            process.destroy()
            try {
                // Give SIGTERM up to 3 seconds before force-killing
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            } catch (_: InterruptedException) {
                process.destroyForcibly()
            }
        }
    }

    companion object {
        private val logger = Logger.getInstance(ClaudeProcess::class.java)

        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 300_000L
        const val WATCHDOG_CHECK_INTERVAL_MS = 30_000L

        /**
         * Builds the CLI command list for spawning Claude.
         */
        fun buildCommand(
            cliPath: String,
            sessionId: String,
            permissionMode: String? = null,
            mcpConfigPath: String? = null,
            model: String? = null,
            additionalArgs: List<String> = emptyList()
        ): List<String> {
            val args = mutableListOf(
                cliPath,
                "-p",
                "--verbose",
                "--output-format", "stream-json",
                "--input-format", "stream-json",
                "--include-partial-messages",
                "--session-id", sessionId
            )

            if (permissionMode != null) {
                args.addAll(listOf("--permission-mode", permissionMode))
            }

            if (!model.isNullOrBlank()) {
                args.addAll(listOf("--model", model))
            }

            if (mcpConfigPath != null) {
                args.addAll(listOf(
                    "--permission-prompt-tool", "mcp__jetbrains_perms__permission_prompt",
                    "--mcp-config", mcpConfigPath
                ))
            }

            args.addAll(additionalArgs)
            return args
        }

        /**
         * Creates an MCP config temp file for the permission server.
         * Returns the path to the temp file.
         */
        fun writeMcpConfig(port: Int): java.io.File {
            val config = """{"mcpServers":{"jetbrains_perms":{"type":"http","url":"http://127.0.0.1:$port/mcp"}}}"""
            val tempFile = java.io.File.createTempFile("claude-mcp-config-", ".json")
            tempFile.writeText(config)
            tempFile.deleteOnExit()
            return tempFile
        }

        /**
         * Spawns a new Claude CLI process.
         */
        fun start(
            cliPath: String,
            workingDir: String,
            sessionId: String,
            permissionMode: String? = null,
            mcpServerPort: Int? = null,
            model: String? = null,
            environmentVariables: Map<String, String> = emptyMap(),
            additionalArgs: List<String> = emptyList()
        ): ClaudeProcess {
            val mcpConfigPath = if (mcpServerPort != null) {
                writeMcpConfig(mcpServerPort).absolutePath
            } else {
                null
            }

            val command = buildCommand(
                cliPath, sessionId, permissionMode,
                mcpConfigPath, model, additionalArgs
            )
            logger.info("Starting Claude CLI: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
                .directory(java.io.File(workingDir))
                .redirectErrorStream(false)

            // Remove Claude env vars to avoid nested-session detection
            processBuilder.environment().remove("CLAUDECODE")
            processBuilder.environment().remove("CLAUDE_CODE_ENTRYPOINT")

            // Add user-configured environment variables
            if (environmentVariables.isNotEmpty()) {
                processBuilder.environment().putAll(environmentVariables)
            }

            val process = processBuilder.start()
            return ClaudeProcess(sessionId, process)
        }

        /**
         * Creates a ClaudeProcess wrapping an existing Process (for testing).
         */
        internal fun fromProcess(sessionId: String, process: Process): ClaudeProcess {
            return ClaudeProcess(sessionId, process)
        }
    }
}
