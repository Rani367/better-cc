package com.claudecode.jetbrains.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Provides git status context for Claude. Reuses GitBranchTracker
 * to get branch and changed files info.
 */
@Service(Service.Level.PROJECT)
class GitContextProvider(private val project: Project) {

    /**
     * Returns a formatted git status string for context injection.
     * Must be called off-EDT.
     */
    fun getGitContext(): String? {
        val tracker = GitBranchTracker.getInstance(project)
        val branch = tracker.getCurrentBranch() ?: return null
        val changes = tracker.getChangedFiles()

        val sb = StringBuilder()
        sb.appendLine("Git branch: $branch")
        if (changes.isNotEmpty()) {
            sb.appendLine("Changed files (${changes.size}):")
            for (change in changes.take(20)) {
                sb.appendLine("  ${change.status.name[0]} ${change.path}")
            }
            if (changes.size > 20) {
                sb.appendLine("  ... and ${changes.size - 20} more")
            }
        }
        return sb.toString()
    }

    companion object {
        fun getInstance(project: Project): GitContextProvider =
            project.getService(GitContextProvider::class.java)
    }
}
