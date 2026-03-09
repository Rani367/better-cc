package com.claudecode.jetbrains.ui.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

enum class DiffDecision {
    ACCEPT,
    REJECT
}

class DiffViewerDialog(
    private val project: Project,
    private val diffData: DiffData
) : DialogWrapper(project, true) {

    var decision: DiffDecision = DiffDecision.REJECT
        private set

    init {
        val fileName = File(diffData.filePath).name
        val action = if (diffData.isNewFile) "Create" else "Edit"
        title = "$action: $fileName"
        setOKButtonText("Accept All Changes")
        setCancelButtonText("Reject All Changes")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val virtualFile = diffData.virtualFile
        if (virtualFile != null && DiffPreviewPanel.isBinaryFile(virtualFile)) {
            return JPanel(BorderLayout()).apply {
                preferredSize = Dimension(500, 200)
                add(
                    JLabel(
                        "Binary file — cannot display diff preview. Accept or reject the change.",
                        SwingConstants.CENTER
                    ),
                    BorderLayout.CENTER
                )
            }
        }

        val contentFactory = DiffContentFactory.getInstance()
        val fileName = File(diffData.filePath).name
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

        val leftContent = if (diffData.isNewFile) {
            contentFactory.createEmpty()
        } else {
            contentFactory.create(diffData.originalContent, fileType)
        }

        val rightContent = contentFactory.create(diffData.proposedContent, fileType)

        val leftTitle = if (diffData.isNewFile) "(new file)" else "Original"
        val request = SimpleDiffRequest(
            title,
            leftContent,
            rightContent,
            leftTitle,
            "Proposed Changes"
        )

        val diffPanel: DiffRequestPanel = DiffManager.getInstance()
            .createRequestPanel(project, disposable, null)
        diffPanel.setRequest(request)

        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(900, 600)
            add(diffPanel.component, BorderLayout.CENTER)
        }
    }

    override fun doOKAction() {
        decision = DiffDecision.ACCEPT
        super.doOKAction()
    }

    override fun doCancelAction() {
        decision = DiffDecision.REJECT
        super.doCancelAction()
    }

    override fun getDimensionServiceKey(): String = "ClaudeCode.DiffViewerDialog"

    override fun createActions(): Array<Action> {
        return arrayOf(okAction, cancelAction)
    }
}
