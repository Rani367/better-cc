package com.claudecode.jetbrains.ui.sessions

import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * FileEditorProvider that creates Claude Code chat panels
 * inside normal editor tabs. Only accepts [ClaudeVirtualFile]s.
 */
class ClaudeEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is ClaudeVirtualFile
    }

    override fun createEditor(
        project: Project,
        file: VirtualFile
    ): FileEditor {
        val claudeFile = file as ClaudeVirtualFile
        return ClaudeFileEditor(project, claudeFile)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy =
        FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "ClaudeCodeEditor"
    }
}

/**
 * FileEditor wrapping a [ChatToolWindow] inside an editor tab.
 * Manages lifecycle: the ChatToolWindow is disposed when the
 * editor tab is closed.
 */
class ClaudeFileEditor(
    private val project: Project,
    private val claudeFile: ClaudeVirtualFile
) : UserDataHolderBase(), FileEditor, Disposable {

    private val logger = Logger.getInstance(ClaudeFileEditor::class.java)

    val chatPanel: ChatToolWindow = ChatToolWindow(project).also { panel ->
        Disposer.register(this, panel)

        // Wire status callback so tab indicators update
        panel.setStatusChangeListener { status ->
            claudeFile.tabStatus = status
            SessionTabManager.getInstance(project)
                .notifyTabStatusChanged(claudeFile)
        }
    }

    override fun getComponent(): JComponent = chatPanel.getContent()

    override fun getPreferredFocusedComponent(): JComponent? {
        return chatPanel.getContent()
    }

    override fun getName(): String = claudeFile.name

    override fun getFile(): VirtualFile = claudeFile

    override fun setState(state: FileEditorState) {
        // No persistent state
    }

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = !project.isDisposed

    override fun addPropertyChangeListener(
        listener: PropertyChangeListener
    ) {
        // No property changes to observe
    }

    override fun removePropertyChangeListener(
        listener: PropertyChangeListener
    ) {
        // No property changes to observe
    }

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        logger.info(
            "Disposing ClaudeFileEditor for tab ${claudeFile.tabId}"
        )
        SessionTabManager.getInstance(project)
            .onTabClosed(claudeFile.tabId)
    }
}
