package com.claudecode.jetbrains.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Tracks the current git branch and changed files for the project.
 */
@Service(Service.Level.PROJECT)
class GitBranchTracker(private val project: Project) {

    private val logger = Logger.getInstance(GitBranchTracker::class.java)

    /**
     * Returns the current git branch name, or null if not in a git repo.
     * Must be called off-EDT.
     */
    fun getCurrentBranch(): String? {
        val basePath = project.basePath ?: return null
        return runGit(basePath, "rev-parse", "--abbrev-ref", "HEAD")
            ?.trim()
    }

    /**
     * Returns a list of changed files (status short format).
     * Must be called off-EDT.
     */
    fun getChangedFiles(): List<GitFileChange> {
        val basePath = project.basePath ?: return emptyList()
        val output = runGit(basePath, "status", "--porcelain")
            ?: return emptyList()
        return output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                if (line.length < 4) return@mapNotNull null
                val status = line.substring(0, 2).trim()
                val path = line.substring(3)
                GitFileChange(
                    path = path,
                    status = when {
                        status.startsWith("A") -> FileChangeStatus.ADDED
                        status.startsWith("M") || status == "MM" ->
                            FileChangeStatus.MODIFIED
                        status.startsWith("D") -> FileChangeStatus.DELETED
                        status.startsWith("R") -> FileChangeStatus.RENAMED
                        status == "??" -> FileChangeStatus.UNTRACKED
                        else -> FileChangeStatus.MODIFIED
                    }
                )
            }
    }

    /**
     * Checks out a branch. Returns true on success.
     * Must be called off-EDT.
     */
    fun checkoutBranch(branchName: String): Boolean {
        val basePath = project.basePath ?: return false
        val output = runGit(basePath, "checkout", branchName)
        return output != null
    }

    private fun runGit(workDir: String, vararg args: String): String? {
        return try {
            val command = mutableListOf("git")
            command.addAll(args)
            val process = ProcessBuilder(command)
                .directory(File(workDir))
                .redirectErrorStream(true)
                .start()

            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }

            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return null
            }

            val output = outputFuture.get(1, TimeUnit.SECONDS)
            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            logger.debug("Git command failed: ${args.toList()}", e)
            null
        }
    }

    companion object {
        fun getInstance(project: Project): GitBranchTracker =
            project.getService(GitBranchTracker::class.java)
    }
}

enum class FileChangeStatus {
    ADDED, MODIFIED, DELETED, RENAMED, UNTRACKED
}

data class GitFileChange(
    val path: String,
    val status: FileChangeStatus
)
