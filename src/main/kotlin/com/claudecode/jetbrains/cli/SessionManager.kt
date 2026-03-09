package com.claudecode.jetbrains.cli

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SessionInfo(
    val id: String,
    val startTime: Long,
    val projectPath: String
)

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project) : Disposable {

    private val sessions = ConcurrentHashMap<String, SessionInfo>()
    private val activeProcesses = ConcurrentHashMap<String, ClaudeProcess>()

    fun createSession(): SessionInfo {
        val id = UUID.randomUUID().toString()
        val info = SessionInfo(
            id = id,
            startTime = System.currentTimeMillis(),
            projectPath = project.basePath ?: ""
        )
        sessions[id] = info
        return info
    }

    fun getSession(id: String): SessionInfo? = sessions[id]

    fun listSessions(): List<SessionInfo> =
        sessions.values.sortedByDescending { it.startTime }

    fun removeSession(id: String) {
        sessions.remove(id)
        activeProcesses.remove(id)?.destroy()
    }

    fun registerProcess(sessionId: String, process: ClaudeProcess) {
        activeProcesses[sessionId] = process
    }

    fun getActiveProcess(sessionId: String): ClaudeProcess? = activeProcesses[sessionId]

    fun unregisterProcess(sessionId: String) {
        activeProcesses.remove(sessionId)
    }

    override fun dispose() {
        for (process in activeProcesses.values) {
            process.destroy()
        }
        activeProcesses.clear()
        sessions.clear()
    }

    companion object {
        fun getInstance(project: Project): SessionManager =
            project.getService(SessionManager::class.java)

        fun buildResumeArgs(sessionId: String): List<String> = listOf("--resume", sessionId)

        fun buildContinueArgs(): List<String> = listOf("--continue")
    }
}
