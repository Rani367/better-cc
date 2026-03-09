package com.claudecode.jetbrains.context

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Describes the current text selection in an editor, used as context for Claude.
 */
data class SelectionContext(
    val fileName: String,
    val relativePath: String,
    val startLine: Int,
    val endLine: Int,
    val lineCount: Int,
    val selectedText: String
)

/**
 * Tracks the current editor selection and provides context information.
 * Notifies listeners when the selection changes so the UI can update.
 */
class SelectionContextProvider(
    private val project: Project,
    private val parentDisposable: Disposable
) {

    private var currentContext: SelectionContext? = null
    private var selectionVisible = true
    private var onChange: ((SelectionContext?) -> Unit)? = null
    private val trackedEditors = mutableSetOf<Editor>()

    init {
        // Listen for editor changes
        project.messageBus.connect(parentDisposable)
            .subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        updateFromCurrentEditor()
                    }
                }
            )

        // Track the initial editor
        updateFromCurrentEditor()
    }

    /**
     * Sets a callback that fires whenever the selection context changes.
     */
    fun setOnChangeListener(listener: (SelectionContext?) -> Unit) {
        onChange = listener
    }

    /**
     * Returns the current selection context, or null if nothing is selected
     * or the user has hidden the selection.
     */
    fun getContext(): SelectionContext? {
        return if (selectionVisible) currentContext else null
    }

    /**
     * Returns the current selection context regardless of visibility toggle.
     */
    fun getRawContext(): SelectionContext? = currentContext

    /**
     * Whether selection sharing with Claude is currently enabled.
     */
    var isSelectionVisible: Boolean
        get() = selectionVisible
        set(value) {
            selectionVisible = value
            onChange?.invoke(getContext())
        }

    /**
     * Refreshes the selection state from the currently active editor.
     * Safe to call from any thread; reads happen on EDT.
     */
    fun updateFromCurrentEditor() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor != null) {
                trackEditor(editor)
                updateContext(editor)
            } else {
                setContext(null)
            }
        }
    }

    private fun trackEditor(editor: Editor) {
        if (trackedEditors.contains(editor)) return
        trackedEditors.add(editor)

        editor.selectionModel.addSelectionListener(
            object : SelectionListener {
                override fun selectionChanged(e: SelectionEvent) {
                    updateContext(editor)
                }
            },
            parentDisposable
        )
    }

    private fun updateContext(editor: Editor) {
        val selectionModel: SelectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            setContext(null)
            return
        }

        val document = editor.document
        val virtualFile: VirtualFile? = FileDocumentManager.getInstance().getFile(document)
        val fileName = virtualFile?.name ?: "Untitled"

        val basePath = project.basePath
        val baseDir = if (basePath != null) {
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath)
        } else {
            null
        }

        val relativePath = if (virtualFile != null && baseDir != null) {
            VfsUtilCore.getRelativePath(virtualFile, baseDir) ?: virtualFile.path
        } else {
            virtualFile?.path ?: fileName
        }

        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd
        val startLine = document.getLineNumber(startOffset) + 1
        val endLine = document.getLineNumber(endOffset) + 1
        val lineCount = endLine - startLine + 1
        val selectedText = selectionModel.selectedText ?: ""

        setContext(
            SelectionContext(
                fileName = fileName,
                relativePath = relativePath,
                startLine = startLine,
                endLine = endLine,
                lineCount = lineCount,
                selectedText = selectedText
            )
        )
    }

    private fun setContext(ctx: SelectionContext?) {
        if (currentContext == ctx) return
        currentContext = ctx
        onChange?.invoke(getContext())
    }
}
