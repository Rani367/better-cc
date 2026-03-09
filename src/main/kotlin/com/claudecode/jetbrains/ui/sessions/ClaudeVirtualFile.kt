package com.claudecode.jetbrains.ui.sessions

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * A lightweight virtual file representing a Claude Code editor tab.
 * Each instance corresponds to one conversation session.
 * This file is never persisted to disk -- it exists only so IntelliJ's
 * FileEditorManager can open/close/switch tabs for us.
 */
class ClaudeVirtualFile(
    val tabId: String,
    private var displayName: String = "Claude Code"
) : VirtualFile() {

    /** Tab status for visual indicators. */
    enum class TabStatus {
        NORMAL,
        PERMISSION_PENDING,
        TASK_COMPLETE
    }

    @Volatile
    var tabStatus: TabStatus = TabStatus.NORMAL

    fun setDisplayName(name: String) {
        displayName = name
    }

    // ── VirtualFile contract ────────────────────────────────────

    override fun getName(): String = displayName

    override fun getFileSystem(): VirtualFileSystem = ClaudeVirtualFileSystem

    override fun getPath(): String = "claude-code://$tabId"

    override fun isWritable(): Boolean = false

    override fun isDirectory(): Boolean = false

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? = null

    override fun getChildren(): Array<VirtualFile>? = null

    override fun getOutputStream(
        requestor: Any?,
        newModificationStamp: Long,
        newTimeStamp: Long
    ): OutputStream {
        throw UnsupportedOperationException(
            "ClaudeVirtualFile is not writable"
        )
    }

    override fun contentsToByteArray(): ByteArray = ByteArray(0)

    override fun getTimeStamp(): Long = 0L

    override fun getLength(): Long = 0L

    override fun refresh(
        asynchronous: Boolean,
        recursive: Boolean,
        postRunnable: Runnable?
    ) {
        // No-op -- virtual file has no backing store
    }

    override fun getInputStream(): InputStream =
        ByteArrayInputStream(ByteArray(0))

    override fun getModificationStamp(): Long = 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClaudeVirtualFile) return false
        return tabId == other.tabId
    }

    override fun hashCode(): Int = tabId.hashCode()
}

/**
 * Minimal VirtualFileSystem implementation required by the
 * VirtualFile contract. Since we never persist these files,
 * most operations are stubs.
 */
private object ClaudeVirtualFileSystem : VirtualFileSystem() {

    override fun getProtocol(): String = "claude-code"

    override fun findFileByPath(path: String): VirtualFile? = null

    override fun refreshAndFindFileByPath(
        path: String
    ): VirtualFile? = null

    override fun refresh(asynchronous: Boolean) {
        // No-op
    }

    override fun addVirtualFileListener(
        listener: com.intellij.openapi.vfs.VirtualFileListener
    ) {
        // No-op
    }

    override fun removeVirtualFileListener(
        listener: com.intellij.openapi.vfs.VirtualFileListener
    ) {
        // No-op
    }

    override fun deleteFile(
        requestor: Any?,
        vFile: VirtualFile
    ) {
        // No-op
    }

    override fun moveFile(
        requestor: Any?,
        vFile: VirtualFile,
        newParent: VirtualFile
    ) {
        // No-op
    }

    override fun renameFile(
        requestor: Any?,
        vFile: VirtualFile,
        newName: String
    ) {
        // No-op
    }

    override fun createChildFile(
        requestor: Any?,
        vDir: VirtualFile,
        fileName: String
    ): VirtualFile {
        throw UnsupportedOperationException()
    }

    override fun createChildDirectory(
        requestor: Any?,
        vDir: VirtualFile,
        dirName: String
    ): VirtualFile {
        throw UnsupportedOperationException()
    }

    override fun copyFile(
        requestor: Any?,
        virtualFile: VirtualFile,
        newParent: VirtualFile,
        copyName: String
    ): VirtualFile {
        throw UnsupportedOperationException()
    }

    override fun isReadOnly(): Boolean = true
}
