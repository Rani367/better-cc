package com.claudecode.jetbrains.ui.diff

import com.claudecode.jetbrains.cli.PermissionRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

data class DiffData(
    val filePath: String,
    val originalContent: String,
    val proposedContent: String,
    val isNewFile: Boolean,
    val virtualFile: VirtualFile?
)

object DiffPreviewPanel {

    private val logger = Logger.getInstance(DiffPreviewPanel::class.java)

    fun buildDiffData(project: Project, request: PermissionRequest): DiffData? {
        val input = request.input
        val filePath = input["file_path"]?.toString() ?: return null

        val resolvedPath = resolveFilePath(project, filePath)
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(resolvedPath)

        if (virtualFile != null && isBinaryFile(virtualFile)) {
            // Return diff data with empty content — dialog will show binary warning
            return DiffData(
                filePath = resolvedPath,
                originalContent = "",
                proposedContent = "",
                isNewFile = false,
                virtualFile = virtualFile
            )
        }

        val originalContent = if (virtualFile != null && virtualFile.exists()) {
            try {
                String(virtualFile.contentsToByteArray(), virtualFile.charset)
            } catch (e: Exception) {
                logger.warn("Failed to read file: $resolvedPath", e)
                ""
            }
        } else {
            ""
        }

        val isNewFile = virtualFile == null || !virtualFile.exists()

        val proposedContent = when (request.toolName) {
            "Write" -> {
                input["content"]?.toString() ?: originalContent
            }
            "Edit" -> {
                val oldString = input["old_string"]?.toString()
                val newString = input["new_string"]?.toString()
                if (oldString != null && newString != null && originalContent.contains(oldString)) {
                    originalContent.replaceFirst(oldString, newString)
                } else {
                    // old_string not found — show original unchanged
                    originalContent
                }
            }
            else -> return null
        }

        return DiffData(
            filePath = resolvedPath,
            originalContent = originalContent,
            proposedContent = proposedContent,
            isNewFile = isNewFile,
            virtualFile = virtualFile
        )
    }

    fun isBinaryFile(virtualFile: VirtualFile): Boolean {
        return virtualFile.fileType.isBinary
    }

    private fun resolveFilePath(project: Project, filePath: String): String {
        val file = File(filePath)
        if (file.isAbsolute) return filePath

        val basePath = project.basePath ?: return filePath
        return File(basePath, filePath).absolutePath
    }
}
