package com.claudecode.jetbrains.ui

import com.claudecode.jetbrains.services.ClaudeState
import com.claudecode.jetbrains.services.ClaudeStateListener
import com.claudecode.jetbrains.services.ClaudeStateService
import com.claudecode.jetbrains.services.SessionUsage
import com.claudecode.jetbrains.ui.chat.ChatToolWindow
import com.claudecode.jetbrains.ui.theme.ClaudeColors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Factory that registers the Claude Code status bar widget.
 */
class ClaudeStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = "Better Claude Code"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return ClaudeStatusBarWidget(project)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    companion object {
        const val WIDGET_ID = "ClaudeCode.StatusBar"
    }
}

/**
 * Status bar widget that shows Claude Code state in the IDE bottom bar.
 * Displays "Better Claude Code" with status: Ready, Thinking..., or
 * Waiting for permission. Click to open/focus the Claude Code panel.
 */
class ClaudeStatusBarWidget(
    private val project: Project
) : StatusBarWidget, StatusBarWidget.TextPresentation, ClaudeStateListener {

    private var statusBar: StatusBar? = null
    private val stateService = ClaudeStateService.getInstance(project)

    @Volatile
    private var currentState = ClaudeState.READY

    @Volatile
    private var currentUsage: SessionUsage? = null

    init {
        stateService.addListener(this)
    }

    override fun ID(): String = ClaudeStatusBarWidgetFactory.WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun dispose() {
        stateService.removeListener(this)
        statusBar = null
    }

    // -- TextPresentation --

    override fun getText(): String {
        val usageSuffix = currentUsage?.let { u ->
            if (u.contextPercent > 0) " (${u.contextPercent}%)" else ""
        } ?: ""
        return when (currentState) {
            ClaudeState.READY -> "Claude: Ready$usageSuffix"
            ClaudeState.THINKING -> "Claude: Thinking...$usageSuffix"
            ClaudeState.WAITING_FOR_PERMISSION ->
                "Claude: Permission$usageSuffix"
        }
    }

    override fun getAlignment(): Float = Component.LEFT_ALIGNMENT

    override fun getTooltipText(): String {
        return when (currentState) {
            ClaudeState.READY -> "Better Claude Code - Ready"
            ClaudeState.THINKING -> "Better Claude Code - Processing your request..."
            ClaudeState.WAITING_FOR_PERMISSION -> {
                "Better Claude Code - Waiting for permission approval"
            }
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer {
            if (project.isDisposed) return@Consumer
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Better Claude Code") ?: return@Consumer
            toolWindow.activate {
                project.getUserData(ChatToolWindow.KEY)?.focusInput()
            }
        }
    }

    // -- ClaudeStateListener --

    override fun onStateChanged(state: ClaudeState) {
        currentState = state
        ApplicationManager.getApplication().invokeLater {
            statusBar?.updateWidget(ID())
        }
    }

    override fun onUsageUpdated(usage: SessionUsage) {
        currentUsage = usage
        ApplicationManager.getApplication().invokeLater {
            statusBar?.updateWidget(ID())
        }
    }
}
