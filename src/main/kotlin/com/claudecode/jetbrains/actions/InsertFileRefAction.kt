package com.claudecode.jetbrains.actions

import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action that inserts an `@file#line-line` reference into the Claude prompt box
 * based on the current editor selection.
 *
 * Shortcut: Cmd+Option+K (Mac) / Alt+Ctrl+K (Windows/Linux)
 */
class InsertFileRefAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return

        val basePath = project.basePath
        val baseDir = if (basePath != null) {
            LocalFileSystem.getInstance().findFileByPath(basePath)
        } else {
            null
        }

        val relativePath = if (baseDir != null) {
            VfsUtilCore.getRelativePath(virtualFile, baseDir) ?: virtualFile.name
        } else {
            virtualFile.name
        }

        // Build the reference string
        val ref = buildString {
            append("@")
            append(relativePath)

            val selectionModel = editor.selectionModel
            if (selectionModel.hasSelection()) {
                val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
                val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
                append("#")
                if (startLine == endLine) {
                    append(startLine)
                } else {
                    append(startLine)
                    append("-")
                    append(endLine)
                }
            }
        }

        // Insert into Claude's input panel
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Claude Code") ?: return
        toolWindow.activate {
            val chatToolWindow = project.getUserData(ChatToolWindow.KEY) ?: return@activate
            chatToolWindow.insertTextAtCursor(ref)
            chatToolWindow.focusInput()
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }
}
