package com.claudecode.jetbrains.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Provides terminal output as context for Claude. Accesses the
 * IntelliJ Terminal plugin's API to read recent terminal content.
 */
@Service(Service.Level.PROJECT)
class TerminalContextProvider(private val project: Project) {

    private val logger =
        Logger.getInstance(TerminalContextProvider::class.java)

    /**
     * Returns the recent terminal output as a string, or null
     * if the terminal plugin is not available or has no content.
     * Must be called off-EDT.
     */
    fun getTerminalContents(): String? {
        return try {
            val cls = Class.forName(
                "org.jetbrains.plugins.terminal.TerminalView"
            )
            val getInstance = cls.getMethod(
                "getInstance",
                Project::class.java
            )
            val terminalView = getInstance.invoke(null, project)
                ?: return null
            val getWidgets = cls.getMethod("getWidgets")
            @Suppress("UNCHECKED_CAST")
            val widgets = getWidgets.invoke(terminalView)
                as? Collection<*> ?: return null
            if (widgets.isEmpty()) return null

            // Get the most recent widget's text content
            val widget = widgets.last() ?: return null
            val getTermText = widget.javaClass.getMethod(
                "getText"
            )
            val text = getTermText.invoke(widget) as? String
            if (text.isNullOrBlank()) null else text.takeLast(5000)
        } catch (e: Exception) {
            logger.debug(
                "Terminal content not available: ${e.message}"
            )
            null
        }
    }

    companion object {
        fun getInstance(project: Project): TerminalContextProvider =
            project.getService(TerminalContextProvider::class.java)
    }
}
