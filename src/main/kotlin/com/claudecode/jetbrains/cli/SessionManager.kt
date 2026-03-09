package com.claudecode.jetbrains.cli

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SessionInfo(
    val id: String,
    val startTime: Long,
    val projectPath: String,
    var title: String = "",
    var firstPrompt: String = "",
    var lastActiveTime: Long = startTime,
    var model: String = ""
) {
    /**
     * Returns a display title: custom title if set, otherwise
     * auto-generated from the first prompt (truncated to ~50 chars).
     */
    fun displayTitle(): String {
        if (title.isNotBlank()) return title
        if (firstPrompt.isNotBlank()) {
            val cleaned = firstPrompt
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
            return if (cleaned.length > MAX_AUTO_TITLE_LENGTH) {
                cleaned.take(MAX_AUTO_TITLE_LENGTH - 3) + "..."
            } else {
                cleaned
            }
        }
        return "Untitled session"
    }

    companion object {
        const val MAX_AUTO_TITLE_LENGTH = 50
    }
}

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(SessionManager::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val sessions = ConcurrentHashMap<String, SessionInfo>()
    private val activeProcesses = ConcurrentHashMap<String, ClaudeProcess>()

    init {
        loadSessions()
        cleanUpOldSessions()
    }

    fun createSession(): SessionInfo {
        val id = UUID.randomUUID().toString()
        val info = SessionInfo(
            id = id,
            startTime = System.currentTimeMillis(),
            projectPath = project.basePath ?: ""
        )
        sessions[id] = info
        saveSessions()
        return info
    }

    fun getSession(id: String): SessionInfo? = sessions[id]

    /**
     * Returns sessions for the current project, sorted by
     * most-recently-active first.
     */
    fun listSessions(): List<SessionInfo> =
        sessions.values
            .filter { it.projectPath == (project.basePath ?: "") }
            .sortedByDescending { it.lastActiveTime }

    fun removeSession(id: String) {
        sessions.remove(id)
        activeProcesses.remove(id)?.destroy()
        saveSessions()
    }

    fun renameSession(id: String, newTitle: String) {
        val session = sessions[id] ?: return
        session.title = newTitle
        saveSessions()
    }

    /**
     * Records that a user message was sent in this session.
     * Updates first prompt (if not set), last-active time, and model.
     */
    fun onMessageSent(
        sessionId: String,
        messageText: String,
        model: String = ""
    ) {
        val session = sessions[sessionId] ?: return
        session.lastActiveTime = System.currentTimeMillis()
        if (session.firstPrompt.isBlank()) {
            session.firstPrompt = messageText
        }
        if (model.isNotBlank()) {
            session.model = model
        }
        saveSessions()
    }

    fun updateLastActive(sessionId: String) {
        val session = sessions[sessionId] ?: return
        session.lastActiveTime = System.currentTimeMillis()
        saveSessions()
    }

    fun registerProcess(sessionId: String, process: ClaudeProcess) {
        activeProcesses[sessionId] = process
    }

    fun getActiveProcess(sessionId: String): ClaudeProcess? =
        activeProcesses[sessionId]

    fun unregisterProcess(sessionId: String) {
        activeProcesses.remove(sessionId)
    }

    /**
     * Removes sessions whose last activity is older than 30 days.
     */
    fun cleanUpOldSessions() {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val cutoff = System.currentTimeMillis() - thirtyDaysMs
        val toRemove = sessions.values
            .filter { it.lastActiveTime < cutoff }
            .map { it.id }

        if (toRemove.isNotEmpty()) {
            toRemove.forEach { sessions.remove(it) }
            saveSessions()
            logger.info(
                "Cleaned up ${toRemove.size} session(s) older than 30 days"
            )
        }
    }

    // ── Persistence ──────────────────────────────────────────

    private fun getStorageFile(): File {
        val pluginDir = File(PathManager.getPluginsPath(), "claude-code")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
        return File(pluginDir, "sessions.json")
    }

    private fun loadSessions() {
        try {
            val file = getStorageFile()
            if (!file.exists()) return

            val json = file.readText()
            if (json.isBlank()) return

            val type = object : TypeToken<List<SessionInfo>>() {}.type
            val loaded: List<SessionInfo> = gson.fromJson(json, type)
                ?: return

            for (session in loaded) {
                sessions[session.id] = session
            }
            logger.info("Loaded ${loaded.size} session(s) from disk")
        } catch (e: Exception) {
            logger.warn("Failed to load sessions from disk", e)
        }
    }

    private fun saveSessions() {
        try {
            val file = getStorageFile()
            val allSessions = sessions.values.toList()
            file.writeText(gson.toJson(allSessions))
        } catch (e: Exception) {
            logger.warn("Failed to save sessions to disk", e)
        }
    }

    override fun dispose() {
        for (process in activeProcesses.values) {
            process.destroy()
        }
        activeProcesses.clear()
        saveSessions()
    }

    companion object {
        fun getInstance(project: Project): SessionManager =
            project.getService(SessionManager::class.java)

        fun buildResumeArgs(sessionId: String): List<String> =
            listOf("--resume", sessionId)

        fun buildContinueArgs(): List<String> = listOf("--continue")
    }
}
