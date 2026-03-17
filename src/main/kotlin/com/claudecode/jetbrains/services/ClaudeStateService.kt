package com.claudecode.jetbrains.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Represents the current operational state of the Claude Code process.
 */
enum class ClaudeState {
    READY,
    THINKING,
    WAITING_FOR_PERMISSION
}

/**
 * Holds accumulated session usage data extracted from CLI events.
 */
data class SessionUsage(
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalCostUsd: Double = 0.0,
    val contextPercent: Int = 0
) {
    val totalTokens: Int get() = totalInputTokens + totalOutputTokens

    fun formatTokens(): String {
        val total = totalTokens
        return when {
            total >= 1_000_000 -> String.format("%.1fM", total / 1_000_000.0)
            total >= 1_000 -> String.format("%.1fK", total / 1_000.0)
            else -> total.toString()
        }
    }

    fun formatCost(): String {
        return String.format("$%.2f", totalCostUsd)
    }
}

/**
 * Listener for state/usage changes.
 */
interface ClaudeStateListener {
    fun onStateChanged(state: ClaudeState) {}
    fun onUsageUpdated(usage: SessionUsage) {}
    fun onModelChanged(model: String) {}
    fun onSessionChanged(sessionId: String?) {}
}

/**
 * Project-level service that tracks the current state, token usage,
 * and cost of the Claude Code session. UI components observe this
 * service to update their displays.
 */
@Service(Service.Level.PROJECT)
class ClaudeStateService(private val project: Project) : Disposable {

    private val listeners = CopyOnWriteArrayList<ClaudeStateListener>()

    @Volatile
    var state: ClaudeState = ClaudeState.READY
        private set

    @Volatile
    var usage: SessionUsage = SessionUsage()
        private set

    @Volatile
    var activeModel: String = ""
        private set

    @Volatile
    var currentSessionId: String? = null
        private set

    private val sessionStates =
        java.util.concurrent.ConcurrentHashMap<String, ClaudeState>()

    fun getSessionState(sessionId: String): ClaudeState =
        sessionStates[sessionId] ?: ClaudeState.READY

    fun getAllSessionStates(): Map<String, ClaudeState> =
        sessionStates.toMap()

    fun setSessionState(sessionId: String, newState: ClaudeState) {
        sessionStates[sessionId] = newState
    }

    fun removeSessionState(sessionId: String) {
        sessionStates.remove(sessionId)
    }

    fun addListener(listener: ClaudeStateListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ClaudeStateListener) {
        listeners.remove(listener)
    }

    fun setState(newState: ClaudeState) {
        if (state != newState) {
            state = newState
            currentSessionId?.let { setSessionState(it, newState) }
            for (l in listeners) {
                l.onStateChanged(newState)
            }
        }
    }

    fun updateUsage(
        inputTokens: Int?,
        outputTokens: Int?,
        costUsd: Double?
    ) {
        val newUsage = SessionUsage(
            totalInputTokens = usage.totalInputTokens + (inputTokens ?: 0),
            totalOutputTokens = usage.totalOutputTokens + (outputTokens ?: 0),
            totalCostUsd = if (costUsd != null) costUsd else usage.totalCostUsd,
            contextPercent = estimateContextPercent(
                usage.totalInputTokens + (inputTokens ?: 0)
            )
        )
        usage = newUsage
        for (l in listeners) {
            l.onUsageUpdated(newUsage)
        }
    }

    fun setActiveModel(model: String) {
        activeModel = model
        for (l in listeners) {
            l.onModelChanged(model)
        }
    }

    fun setSession(sessionId: String?) {
        currentSessionId = sessionId
        for (l in listeners) {
            l.onSessionChanged(sessionId)
        }
    }

    fun resetUsage() {
        usage = SessionUsage()
        for (l in listeners) {
            l.onUsageUpdated(usage)
        }
    }

    /**
     * Rough estimate of context window usage based on input tokens.
     * Claude's context window is ~200K tokens.
     */
    private fun estimateContextPercent(inputTokens: Int): Int {
        val contextWindowSize = 200_000
        val pct = ((inputTokens.toDouble() / contextWindowSize) * 100).toInt()
        return pct.coerceIn(0, 100)
    }

    override fun dispose() {
        listeners.clear()
    }

    companion object {
        fun getInstance(project: Project): ClaudeStateService =
            project.getService(ClaudeStateService::class.java)
    }
}
