package com.claudecode.jetbrains

import com.claudecode.jetbrains.cli.AuthStatus
import com.claudecode.jetbrains.cli.ClaudeCliManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ClaudeCodePlugin : ProjectActivity {
    private val logger = Logger.getInstance(ClaudeCodePlugin::class.java)

    private fun logPluginVersion() {
        try {
            val pluginId = PluginId.getId(PLUGIN_ID)
            val descriptor = PluginManagerCore.getPlugin(pluginId)
            if (descriptor != null) {
                logger.info(
                    "Better Claude Code plugin version: ${descriptor.version}"
                )
            }
        } catch (e: Exception) {
            logger.debug("Could not read plugin version", e)
        }
    }

    companion object {
        private const val PLUGIN_ID = "com.claudecode.jetbrains"
    }

    override suspend fun execute(project: Project) {
        logger.info("Better Claude Code plugin loaded for project: ${project.name}")
        logPluginVersion()

        val manager = ClaudeCliManager.getInstance(project)
        manager.detect()

        if (project.isDisposed) {
            return
        }

        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("Better Claude Code Notifications")

        when (manager.getAuthStatus()) {
            AuthStatus.CLI_NOT_FOUND -> {
                notificationGroup.createNotification(
                    "Better Claude Code",
                    "Claude Code CLI not found. Install it from https://claude.ai/install.sh",
                    NotificationType.ERROR
                )
                    .addAction(NotificationAction.createSimple("Configure Path") {
                        // TODO: Open settings dialog in phase 16
                    })
                    .notify(project)
            }

            AuthStatus.NOT_AUTHENTICATED -> {
                notificationGroup.createNotification(
                    "Better Claude Code",
                    "Claude Code CLI found but not authenticated. Run 'claude auth login' in your terminal.",
                    NotificationType.WARNING
                )
                    .notify(project)
            }

            AuthStatus.AUTHENTICATED -> {
                val properties = PropertiesComponent.getInstance(project)
                val key = "com.claudecode.hasShownReadyNotification"
                if (!properties.getBoolean(key, false)) {
                    notificationGroup.createNotification(
                        "Better Claude Code",
                        "Better Claude Code ready",
                        NotificationType.INFORMATION
                    )
                        .notify(project)
                    properties.setValue(key, true)
                }
            }
        }
    }
}
