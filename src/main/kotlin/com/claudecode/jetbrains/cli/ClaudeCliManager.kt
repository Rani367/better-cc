package com.claudecode.jetbrains.cli

import com.claudecode.jetbrains.settings.ClaudeSettings
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

enum class AuthStatus {
    CLI_NOT_FOUND,
    NOT_AUTHENTICATED,
    AUTHENTICATED
}

data class AuthAccountInfo(
    val email: String?,
    val authMethod: String?,
    val apiProvider: String?,
    val orgName: String?,
    val subscriptionType: String?
)

@Service(Service.Level.PROJECT)
class ClaudeCliManager(private val project: Project) {
    private val logger = Logger.getInstance(ClaudeCliManager::class.java)

    @Volatile
    private var resolvedCliPath: String? = null

    @Volatile
    private var authenticated: Boolean = false

    @Volatile
    private var authAccountInfo: AuthAccountInfo? = null

    fun isCliAvailable(): Boolean = resolvedCliPath != null

    fun isAuthenticated(): Boolean = authenticated

    fun getCliPath(): String? = resolvedCliPath

    fun getAccountInfo(): AuthAccountInfo? = authAccountInfo

    fun getAuthStatus(): AuthStatus {
        return when {
            resolvedCliPath == null -> AuthStatus.CLI_NOT_FOUND
            !authenticated -> AuthStatus.NOT_AUTHENTICATED
            else -> AuthStatus.AUTHENTICATED
        }
    }

    fun detect() {
        val settings = ClaudeSettings.getInstance()
        val command = settings.claudeCommand

        // Try to resolve CLI path
        resolvedCliPath = resolvePath(command)
        if (resolvedCliPath == null) {
            authenticated = false
            authAccountInfo = null
            return
        }

        // Check authentication
        val authOutput = runProcess(listOf(resolvedCliPath!!, "auth", "status"), timeout = 10000)
        authenticated = authOutput != null
        if (authOutput != null) {
            authAccountInfo = parseAuthJson(authOutput)
        }
    }

    private fun resolvePath(command: String): String? {
        // If it's an explicit path (not just "claude"), check if it's executable
        if (command != "claude") {
            val file = File(command)
            if (file.canExecute()) {
                logger.info("Using explicit Claude CLI path: $command")
                return command
            }
            logger.warn("Explicit Claude path not executable: $command")
            return null
        }

        // Probe common locations
        val probePaths = listOf(
            "${System.getProperty("user.home")}/.local/bin/claude",
            "${System.getProperty("user.home")}/.claude/bin/claude",
            "/opt/homebrew/bin/claude",
            "/usr/local/bin/claude",
            "/usr/bin/claude"
        )

        for (path in probePaths) {
            val file = File(path)
            if (file.canExecute()) {
                logger.info("Found Claude CLI at: $path")
                return path
            }
        }

        // Probe PATH environment variable
        val pathEnv = System.getenv("PATH") ?: ""
        for (dir in pathEnv.split(File.pathSeparator)) {
            val clauFile = File(dir, "claude")
            if (clauFile.canExecute()) {
                logger.info("Found Claude CLI in PATH: ${clauFile.absolutePath}")
                return clauFile.absolutePath
            }
        }

        // Try npm global bin directory
        val npmGlobalPath = resolveFromNpm()
        if (npmGlobalPath != null) {
            return npmGlobalPath
        }

        // Try bash -l "which claude" as last resort (handles mise/nvm/asdf)
        val whichOutput = runProcess(listOf("bash", "-l", "-c", "which claude"), timeout = 5000)
        if (whichOutput != null) {
            val path = whichOutput.lines()
                .firstOrNull { it.startsWith("/") }
            if (path != null) {
                val file = File(path)
                if (file.canExecute()) {
                    logger.info("Found Claude CLI via which: $path")
                    return path
                }
            }
        }

        logger.warn("Could not locate Claude CLI")
        return null
    }

    private fun resolveFromNpm(): String? {
        val npmRoot = runProcess(listOf("npm", "root", "-g"), timeout = 5000) ?: return null
        val rootDir = npmRoot.lines().firstOrNull { it.startsWith("/") } ?: return null
        val prefix = File(rootDir).parentFile?.parentFile ?: return null
        val claudeFile = File(prefix, "bin/claude")
        if (claudeFile.canExecute()) {
            logger.info("Found Claude CLI via npm global: ${claudeFile.absolutePath}")
            return claudeFile.absolutePath
        }
        return null
    }

    private fun runProcess(command: List<String>, timeout: Long): String? {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Read output on a background thread so waitFor can enforce timeout
            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }

            val completed = process.waitFor(timeout, TimeUnit.MILLISECONDS)

            if (!completed) {
                process.destroyForcibly()
                logger.warn("Process timed out: ${command.joinToString(" ")}")
                return null
            }

            val output = outputFuture.get(1, TimeUnit.SECONDS)

            if (process.exitValue() == 0) {
                output
            } else {
                logger.debug("Process exited with code ${process.exitValue()}: ${command.joinToString(" ")}")
                null
            }
        } catch (e: Exception) {
            logger.debug("Error running process: ${command.joinToString(" ")}", e)
            null
        }
    }

    private fun parseAuthJson(output: String): AuthAccountInfo? {
        return try {
            val json = JsonParser.parseString(output).asJsonObject
            AuthAccountInfo(
                email = json.get("email")?.takeIf { !it.isJsonNull }?.asString,
                authMethod = json.get("authMethod")?.takeIf { !it.isJsonNull }?.asString,
                apiProvider = json.get("apiProvider")?.takeIf { !it.isJsonNull }?.asString,
                orgName = json.get("orgName")?.takeIf { !it.isJsonNull }?.asString,
                subscriptionType = json.get("subscriptionType")?.takeIf { !it.isJsonNull }?.asString
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse auth JSON, falling back to basic info", e)
            AuthAccountInfo(
                email = null,
                authMethod = null,
                apiProvider = null,
                orgName = null,
                subscriptionType = null
            )
        }
    }

    /**
     * Runs a Claude CLI subcommand and returns stdout, or null on failure.
     * Must be called off-EDT (background thread or coroutine with Dispatchers.IO).
     */
    fun runCliCommand(args: List<String>, timeout: Long = 15000): String? {
        val cliPath = resolvedCliPath ?: return null
        val command = mutableListOf(cliPath)
        command.addAll(args)
        return runProcess(command, timeout)
    }

    /**
     * Runs a Claude CLI subcommand and returns stdout even on non-zero exit.
     * Returns a Pair of (exitCode, stdout). Returns null only on exception/timeout.
     * Must be called off-EDT.
     */
    fun runCliCommandWithExitCode(
        args: List<String>,
        timeout: Long = 15000
    ): Pair<Int, String>? {
        val cliPath = resolvedCliPath ?: return null
        val command = mutableListOf(cliPath)
        command.addAll(args)
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }

            val completed = process.waitFor(timeout, TimeUnit.MILLISECONDS)

            if (!completed) {
                process.destroyForcibly()
                logger.warn("Process timed out: ${command.joinToString(" ")}")
                return null
            }

            val output = outputFuture.get(1, TimeUnit.SECONDS)
            Pair(process.exitValue(), output)
        } catch (e: Exception) {
            logger.debug("Error running process: ${command.joinToString(" ")}", e)
            null
        }
    }

    /**
     * Fetches available models from the Claude CLI.
     * Returns a list of model identifier strings, or a default
     * fallback list if the CLI command fails.
     * Must be called off-EDT.
     */
    fun fetchModels(): List<String> {
        val output = runCliCommand(listOf("models"), timeout = 10000)
        if (output != null) {
            val models = output.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            if (models.isNotEmpty()) return models
        }
        // Fallback: try config-based approach
        val configOutput = runCliCommand(
            listOf("config", "list-models"),
            timeout = 10000
        )
        if (configOutput != null) {
            val models = configOutput.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            if (models.isNotEmpty()) return models
        }
        // Default fallback
        return listOf(
            "claude-sonnet-4-6",
            "claude-opus-4-6",
            "claude-haiku-4-5"
        )
    }

    companion object {
        fun getInstance(project: Project): ClaudeCliManager =
            project.getService(ClaudeCliManager::class.java)
    }
}
