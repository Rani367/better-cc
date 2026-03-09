package com.claudecode.jetbrains.checkpoint

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a snapshot of a single file's content before a Claude edit.
 */
data class FileSnapshot(
    val filePath: String,
    val originalContent: String?,
    val isNewFile: Boolean
)

/**
 * A checkpoint captures the state of all files before edits triggered by
 * a single user prompt. Each user message gets one checkpoint that
 * accumulates all file snapshots produced before the next user turn.
 */
data class Checkpoint(
    val sessionId: String,
    val messageIndex: Int,
    val userMessageId: String,
    val userPromptText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val fileSnapshots: MutableList<FileSnapshot> = mutableListOf()
) {
    val changedFileCount: Int get() = fileSnapshots.size
}

/**
 * Describes the result of a rewind operation so the UI can report it.
 */
data class RewindResult(
    val restoredFileCount: Int,
    val skippedFiles: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Project-level service that manages file checkpoints for rewind.
 *
 * How it works:
 * - When a user sends a message, [beginCheckpoint] is called to start
 *   a new checkpoint associated with that message.
 * - When Claude's Write/Edit tool is about to modify a file,
 *   [snapshotBeforeEdit] is called to capture the file's current content.
 * - Only the first snapshot per file per checkpoint is kept (the original
 *   state before any edits in this turn).
 * - Rewind operations restore files to the snapshot state and/or truncate
 *   conversation history.
 *
 * Persistence: checkpoints are stored as JSON in the plugin data directory.
 * A 30-day cleanup runs on initialization.
 */
@Service(Service.Level.PROJECT)
class CheckpointManager(private val project: Project) {

    private val logger =
        Logger.getInstance(CheckpointManager::class.java)
    private val gson: Gson =
        GsonBuilder().setPrettyPrinting().create()

    /** All checkpoints for the current project, keyed by userMessageId. */
    private val checkpoints =
        ConcurrentHashMap<String, Checkpoint>()

    /** The checkpoint currently being built (for the in-progress turn). */
    @Volatile
    private var activeCheckpoint: Checkpoint? = null

    init {
        loadCheckpoints()
        cleanUpOldCheckpoints()
    }

    // ── Checkpoint creation ──────────────────────────────────

    /**
     * Begins a new checkpoint for the given user message.
     * Call this when a user message is sent to Claude.
     */
    fun beginCheckpoint(
        sessionId: String,
        messageIndex: Int,
        userMessageId: String,
        userPromptText: String
    ) {
        val checkpoint = Checkpoint(
            sessionId = sessionId,
            messageIndex = messageIndex,
            userMessageId = userMessageId,
            userPromptText = userPromptText
        )
        activeCheckpoint = checkpoint
        checkpoints[userMessageId] = checkpoint
        saveCheckpoints()
    }

    /**
     * Captures a snapshot of the file at [filePath] before Claude edits it.
     * Only the first snapshot per file per checkpoint is kept.
     */
    fun snapshotBeforeEdit(filePath: String) {
        val checkpoint = activeCheckpoint ?: return

        // Skip if we already have a snapshot for this file in this checkpoint
        if (checkpoint.fileSnapshots.any { it.filePath == filePath }) {
            return
        }

        val resolvedPath = resolveFilePath(filePath)
        val vFile = LocalFileSystem.getInstance()
            .findFileByPath(resolvedPath)

        val snapshot = if (vFile != null && vFile.exists()) {
            try {
                val content = String(
                    vFile.contentsToByteArray(), vFile.charset
                )
                FileSnapshot(
                    filePath = resolvedPath,
                    originalContent = content,
                    isNewFile = false
                )
            } catch (e: Exception) {
                logger.warn(
                    "Failed to snapshot file: $resolvedPath", e
                )
                return
            }
        } else {
            // File does not exist yet -- Claude is creating it
            FileSnapshot(
                filePath = resolvedPath,
                originalContent = null,
                isNewFile = true
            )
        }

        checkpoint.fileSnapshots.add(snapshot)
        saveCheckpoints()
    }

    /**
     * Finalizes the active checkpoint. Call when the assistant turn ends.
     */
    fun finalizeCheckpoint() {
        activeCheckpoint = null
        saveCheckpoints()
    }

    // ── Queries ──────────────────────────────────────────────

    /**
     * Returns the checkpoint for the given user message ID, or null.
     */
    fun getCheckpoint(userMessageId: String): Checkpoint? {
        return checkpoints[userMessageId]
    }

    /**
     * Returns true if there are file snapshots recorded for the
     * given user message (i.e., Claude edited files in that turn).
     */
    fun hasFileChanges(userMessageId: String): Boolean {
        val cp = checkpoints[userMessageId] ?: return false
        return cp.fileSnapshots.isNotEmpty()
    }

    /**
     * Returns checkpoints for the given session sorted by
     * message index (ascending).
     */
    fun getSessionCheckpoints(sessionId: String): List<Checkpoint> {
        return checkpoints.values
            .filter { it.sessionId == sessionId }
            .sortedBy { it.messageIndex }
    }

    /**
     * Returns the number of files changed between the given
     * checkpoint and now (i.e., in all later checkpoints).
     */
    fun filesChangedSince(
        sessionId: String,
        fromMessageIndex: Int
    ): Int {
        return checkpoints.values
            .filter {
                it.sessionId == sessionId &&
                    it.messageIndex > fromMessageIndex
            }
            .sumOf { it.fileSnapshots.size }
    }

    // ── Restore operations ───────────────────────────────────

    /**
     * Restores files to the state they were in at the given checkpoint.
     * This reverts all file changes made from this checkpoint onwards.
     *
     * "Restore code only" -- called with keepConversation=true.
     * "Restore code and conversation" -- caller also truncates messages.
     */
    fun restoreFiles(
        sessionId: String,
        fromMessageIndex: Int
    ): RewindResult {
        val laterCheckpoints = checkpoints.values
            .filter {
                it.sessionId == sessionId &&
                    it.messageIndex >= fromMessageIndex
            }
            .sortedByDescending { it.messageIndex }

        var restoredCount = 0
        val skipped = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val processedPaths = mutableSetOf<String>()

        for (cp in laterCheckpoints) {
            for (snapshot in cp.fileSnapshots) {
                // Only process each file once (use the earliest
                // snapshot, which is the original state)
                if (snapshot.filePath in processedPaths) continue
                processedPaths.add(snapshot.filePath)

                try {
                    if (snapshot.isNewFile) {
                        // File was created by Claude -- delete it
                        val file = File(snapshot.filePath)
                        if (file.exists()) {
                            file.delete()
                            restoredCount++
                        }
                    } else if (snapshot.originalContent != null) {
                        val file = File(snapshot.filePath)
                        file.parentFile?.mkdirs()
                        file.writeText(snapshot.originalContent)
                        restoredCount++
                    }
                } catch (e: Exception) {
                    logger.warn(
                        "Failed to restore: ${snapshot.filePath}", e
                    )
                    skipped.add(snapshot.filePath)
                }
            }
        }

        // Refresh VFS to pick up changes
        try {
            LocalFileSystem.getInstance().refresh(true)
        } catch (e: Exception) {
            logger.warn("VFS refresh failed after restore", e)
        }

        return RewindResult(
            restoredFileCount = restoredCount,
            skippedFiles = skipped,
            warnings = warnings
        )
    }

    /**
     * Removes checkpoint data for messages at or after the given index.
     * Called when conversation is truncated.
     */
    fun removeCheckpointsFrom(
        sessionId: String,
        fromMessageIndex: Int
    ) {
        val toRemove = checkpoints.values
            .filter {
                it.sessionId == sessionId &&
                    it.messageIndex >= fromMessageIndex
            }
            .map { it.userMessageId }

        toRemove.forEach { checkpoints.remove(it) }
        saveCheckpoints()
    }

    // ── Cleanup ──────────────────────────────────────────────

    private fun cleanUpOldCheckpoints() {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val cutoff = System.currentTimeMillis() - thirtyDaysMs
        val toRemove = checkpoints.values
            .filter { it.createdAt < cutoff }
            .map { it.userMessageId }

        if (toRemove.isNotEmpty()) {
            toRemove.forEach { checkpoints.remove(it) }
            saveCheckpoints()
            logger.info(
                "Cleaned up ${toRemove.size} checkpoint(s) " +
                    "older than 30 days"
            )
        }
    }

    // ── Persistence ──────────────────────────────────────────

    private fun getStorageDir(): File {
        val pluginDir = File(PathManager.getPluginsPath(), "claude-code")
        val checkpointsDir = File(pluginDir, "checkpoints")
        if (!checkpointsDir.exists()) {
            checkpointsDir.mkdirs()
        }
        return checkpointsDir
    }

    private fun getStorageFile(): File {
        val projectId = project.basePath
            ?.replace(File.separator, "_")
            ?.replace(":", "_")
            ?: "unknown"
        return File(getStorageDir(), "checkpoints-$projectId.json")
    }

    private fun loadCheckpoints() {
        try {
            val file = getStorageFile()
            if (!file.exists()) return

            val json = file.readText()
            if (json.isBlank()) return

            val type =
                object : TypeToken<List<Checkpoint>>() {}.type
            val loaded: List<Checkpoint> =
                gson.fromJson(json, type) ?: return

            for (cp in loaded) {
                checkpoints[cp.userMessageId] = cp
            }
            logger.info(
                "Loaded ${loaded.size} checkpoint(s) from disk"
            )
        } catch (e: Exception) {
            logger.warn(
                "Failed to load checkpoints from disk", e
            )
        }
    }

    private fun saveCheckpoints() {
        try {
            val file = getStorageFile()
            val all = checkpoints.values.toList()
            file.writeText(gson.toJson(all))
        } catch (e: Exception) {
            logger.warn("Failed to save checkpoints to disk", e)
        }
    }

    private fun resolveFilePath(filePath: String): String {
        val file = File(filePath)
        if (file.isAbsolute) return filePath
        val basePath = project.basePath ?: return filePath
        return File(basePath, filePath).absolutePath
    }

    companion object {
        fun getInstance(project: Project): CheckpointManager =
            project.getService(CheckpointManager::class.java)
    }
}
