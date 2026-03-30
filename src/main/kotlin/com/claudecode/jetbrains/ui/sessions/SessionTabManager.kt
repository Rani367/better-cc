package com.claudecode.jetbrains.ui.sessions

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-level service that manages multiple Claude Code
 * editor tabs. Each tab gets its own [ClaudeVirtualFile] and
 * a unique [ChatToolWindow] with an independent session/process.
 *
 * Responsibilities:
 * - Open/close tabs
 * - Assign unique sequential display names
 * - Track tab status (permission pending / task complete)
 * - Clear status dots when a tab gains focus
 */
@Service(Service.Level.PROJECT)
class SessionTabManager(
    private val project: Project
) : Disposable {

    private val logger = Logger.getInstance(SessionTabManager::class.java)

    /** All open Claude tab files, keyed by tabId. */
    private val openTabs = ConcurrentHashMap<String, ClaudeVirtualFile>()

    /** Counter for display-name numbering. */
    @Volatile
    private var nextTabNumber = 1

    init {
        // Listen for editor selection changes so we can clear
        // status dots when a Claude tab gains focus.
        project.messageBus.connect(this)
            .subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(
                        event: FileEditorManagerEvent
                    ) {
                        val file = event.newFile
                        if (file is ClaudeVirtualFile) {
                            clearStatusOnFocus(file)
                        }
                    }
                }
            )
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Opens a new Claude Code conversation in an editor tab.
     * Returns the created [ClaudeVirtualFile].
     */
    fun openNewTab(): ClaudeVirtualFile {
        val tabId = UUID.randomUUID().toString()
        val number = nextTabNumber++
        val displayName = if (number == 1) {
            "Better Claude Code"
        } else {
            "Better Claude Code ($number)"
        }

        val file = ClaudeVirtualFile(tabId, displayName)
        openTabs[tabId] = file

        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                FileEditorManager.getInstance(project)
                    .openFile(file, true)
            }
        }

        logger.info("Opened new Claude tab: $displayName (id=$tabId)")
        return file
    }

    /**
     * Called by [ClaudeFileEditor.dispose] when a tab is closed.
     * Removes the tab from tracking.
     */
    fun onTabClosed(tabId: String) {
        openTabs.remove(tabId)
        logger.info("Claude tab closed: $tabId")
    }

    /**
     * Updates the tab title (e.g., when a session is renamed).
     */
    fun updateTabTitle(tabId: String, title: String) {
        val file = openTabs[tabId] ?: return
        file.setDisplayName(title)
        // Trigger tab title refresh by re-setting the virtual file
        // property change. FileEditorManager re-reads getName().
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                val fem = FileEditorManager.getInstance(project)
                val editors = fem.getEditors(file)
                for (editor in editors) {
                    // Touching the file causes the tab to re-read name
                    fem.openFile(file, false)
                }
            }
        }
    }

    /**
     * Called when a tab's status changes (permission pending,
     * task complete, etc.). Updates the tab icon if the tab is
     * not currently focused.
     */
    fun notifyTabStatusChanged(file: ClaudeVirtualFile) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val fem = FileEditorManager.getInstance(project)
            val selectedFile = fem.selectedFiles.firstOrNull()

            // Only show status dot if tab is not currently focused
            if (selectedFile == file) {
                file.tabStatus = ClaudeVirtualFile.TabStatus.NORMAL
            }
            // Force tab icon refresh
            fem.openFile(file, false)
        }
    }

    /**
     * Returns all currently open Claude tab files.
     */
    fun getOpenTabs(): List<ClaudeVirtualFile> = openTabs.values.toList()

    /**
     * Teleports a session from the sidebar to a new editor tab.
     * Returns the [ClaudeVirtualFile] for the new tab, or null on failure.
     */
    fun teleportToTab(
        state: SessionState
    ): ClaudeVirtualFile? {
        val file = openNewTab()
        updateTabTitle(file.tabId, state.sessionTitle)

        // Load state into the new tab's ChatToolWindow after it opens
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val fem = FileEditorManager.getInstance(project)
            val editors = fem.getEditors(file)
            val claudeEditor = editors.firstOrNull {
                it is ClaudeFileEditor
            } as? ClaudeFileEditor
            claudeEditor?.chatPanel?.loadTeleportedState(state)
        }
        logger.info("Teleported session to tab: ${state.sessionTitle}")
        return file
    }

    /**
     * Closes a tab by its [ClaudeVirtualFile] reference.
     * Used during teleport-to-sidebar.
     */
    fun closeTab(file: ClaudeVirtualFile) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                FileEditorManager.getInstance(project).closeFile(file)
            }
        }
    }

    // ── Internals ───────────────────────────────────────────────

    private fun clearStatusOnFocus(file: ClaudeVirtualFile) {
        if (file.tabStatus != ClaudeVirtualFile.TabStatus.NORMAL) {
            file.tabStatus = ClaudeVirtualFile.TabStatus.NORMAL
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    FileEditorManager.getInstance(project)
                        .openFile(file, false)
                }
            }
        }
    }

    override fun dispose() {
        openTabs.clear()
    }

    companion object {
        fun getInstance(project: Project): SessionTabManager =
            project.getService(SessionTabManager::class.java)
    }
}
