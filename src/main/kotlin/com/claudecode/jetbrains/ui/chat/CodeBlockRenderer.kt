package com.claudecode.jetbrains.ui.chat

import com.claudecode.jetbrains.settings.ClaudeSettings
import com.google.gson.JsonParser
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.datatransfer.StringSelection
import java.io.File

class CodeBlockRenderer(
    private val project: Project,
    browser: JBCefBrowserBase
) : Disposable {

    private val logger = Logger.getInstance(CodeBlockRenderer::class.java)
    private val jsQuery = JBCefJSQuery.create(browser)

    var permissionResponseHandler: ((String, String) -> Unit)? = null
    var rewindHandler: ((String, String) -> Unit)? = null
    var retryHandler: ((String) -> Unit)? = null
    var forkHandler: ((String) -> Unit)? = null

    // Header/input action handlers
    var sendMessageHandler: ((String) -> Unit)? = null
    var newConversationHandler: (() -> Unit)? = null
    var sessionsClickHandler: (() -> Unit)? = null
    var settingsHandler: (() -> Unit)? = null
    var slashCommandHandler: ((String) -> Unit)? = null
    var modelClickHandler: (() -> Unit)? = null
    var permissionModeClickHandler: (() -> Unit)? = null
    var thinkingClickHandler: (() -> Unit)? = null
    var attachFileHandler: ((String, String) -> Unit)? = null
    var checkoutBranchHandler: ((String) -> Unit)? = null

    /**
     * JavaScript code that defines window.sendToKotlin function.
     * Must be injected after the page loads.
     */
    val injectionJs: String

    init {
        jsQuery.addHandler { request ->
            handleRequest(request)
            null
        }

        val queryCall = jsQuery.inject("' + request + '")
        injectionJs = "window.sendToKotlin = function(request) { $queryCall };"
    }

    private fun handleRequest(request: String) {
        try {
            val json = JsonParser.parseString(request).asJsonObject
            val action = json.get("action")?.asString
            when (action) {
                "copy" -> {
                    val code = json.get("code")?.asString ?: return
                    CopyPasteManager.getInstance().setContents(StringSelection(code))
                }
                "openFile" -> {
                    val path = json.get("path")?.asString ?: return
                    openFile(path)
                }
                "permissionResponse" -> {
                    val requestId = json.get("requestId")?.asString ?: return
                    val decision = json.get("decision")?.asString ?: return
                    permissionResponseHandler?.invoke(requestId, decision)
                }
                "rewind" -> {
                    val messageId = json.get("messageId")?.asString ?: return
                    val rewindAction = json.get("rewindAction")?.asString ?: return
                    rewindHandler?.invoke(messageId, rewindAction)
                }
                "retryMessage" -> {
                    val messageId = json.get("messageId")?.asString ?: return
                    retryHandler?.invoke(messageId)
                }
                "forkMessage" -> {
                    val messageId = json.get("messageId")?.asString ?: return
                    forkHandler?.invoke(messageId)
                }
                "sendMessage" -> {
                    val text = json.get("text")?.asString ?: return
                    sendMessageHandler?.invoke(text)
                }
                "newConversation" -> {
                    newConversationHandler?.invoke()
                }
                "sessionsClick" -> {
                    sessionsClickHandler?.invoke()
                }
                "settings" -> {
                    settingsHandler?.invoke()
                }
                "slashCommand" -> {
                    val command = json.get("command")?.asString ?: return
                    slashCommandHandler?.invoke(command)
                }
                "modelClick" -> {
                    modelClickHandler?.invoke()
                }
                "permissionModeClick" -> {
                    permissionModeClickHandler?.invoke()
                }
                "thinkingClick" -> {
                    thinkingClickHandler?.invoke()
                }
                "copyMessage" -> {
                    val text = json.get("text")?.asString ?: return
                    CopyPasteManager.getInstance()
                        .setContents(StringSelection(text))
                }
                "openDiff" -> {
                    val path = json.get("path")?.asString ?: return
                    val oldContent =
                        json.get("oldContent")?.asString ?: ""
                    val newContent =
                        json.get("newContent")?.asString ?: ""
                    openDiff(path, oldContent, newContent)
                }
                "openUrl" -> {
                    val url = json.get("url")?.asString ?: return
                    BrowserUtil.browse(url)
                }
                "openTerminal" -> {
                    openTerminal(
                        json.get("workDir")?.asString
                    )
                }
                "openConfig" -> {
                    ApplicationManager.getApplication().invokeLater {
                        ShowSettingsUtil.getInstance()
                            .showSettingsDialog(project, "Claude Code")
                    }
                }
                "openConfigFile" -> {
                    val path = json.get("path")?.asString
                        ?: return
                    openFile(path)
                }
                "openFolder" -> {
                    val path = json.get("path")?.asString ?: return
                    openFolder(path)
                }
                "openContent" -> {
                    val content =
                        json.get("content")?.asString ?: return
                    val title =
                        json.get("title")?.asString ?: "Claude Output"
                    val ext = json.get("extension")?.asString ?: "txt"
                    openContent(content, title, ext)
                }
                "openHelp" -> {
                    BrowserUtil.browse(
                        "https://docs.anthropic.com/en/docs/" +
                            "claude-code/overview"
                    )
                }
                "openOutputPanel" -> {
                    openOutputPanel()
                }
                "openMarkdownPreview" -> {
                    val content =
                        json.get("content")?.asString ?: return
                    val title =
                        json.get("title")?.asString ?: "Preview"
                    openContent(content, title, "md")
                }
                "openInEditor" -> {
                    val content =
                        json.get("content")?.asString ?: return
                    val title =
                        json.get("title")?.asString ?: "Claude Output"
                    val ext = json.get("extension")?.asString ?: "txt"
                    openContent(content, title, ext)
                }
                "openFileDiffs" -> {
                    val filesArr =
                        json.getAsJsonArray("files") ?: return
                    for (fileEl in filesArr) {
                        val obj = fileEl.asJsonObject
                        openDiff(
                            obj.get("path")?.asString ?: continue,
                            obj.get("oldContent")?.asString ?: "",
                            obj.get("newContent")?.asString ?: ""
                        )
                    }
                }
                "openClaudeInTerminal" -> {
                    openClaudeInTerminal()
                }
                "checkoutBranch" -> {
                    val branch =
                        json.get("branch")?.asString ?: return
                    checkoutBranchHandler?.invoke(branch)
                }
                "attachFile" -> {
                    val name =
                        json.get("name")?.asString ?: return
                    val path =
                        json.get("path")?.asString ?: return
                    attachFileHandler?.invoke(name, path)
                }
                "showNotification" -> {
                    val message =
                        json.get("message")?.asString ?: return
                    val type = json.get("type")?.asString ?: "info"
                    showNotification(message, type)
                }
                "dismissBanner" -> {
                    val bannerId =
                        json.get("bannerId")?.asString ?: return
                    persistBannerDismissal(bannerId)
                }
                "dismiss_terminal_banner" -> {
                    persistBannerDismissal("terminal_banner")
                }
                "dismiss_review_upsell_banner" -> {
                    persistBannerDismissal("review_upsell_banner")
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle JS callback", e)
        }
    }

    private fun openFile(path: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val basePath = project.basePath ?: return@invokeLater
            val fullPath =
                if (path.startsWith("/")) path else "$basePath/$path"
            val vFile = LocalFileSystem.getInstance()
                .findFileByPath(fullPath) ?: return@invokeLater
            FileEditorManager.getInstance(project)
                .openFile(vFile, true)
        }
    }

    private fun openDiff(
        path: String,
        oldContent: String,
        newContent: String
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val factory = DiffContentFactory.getInstance()
            val request = SimpleDiffRequest(
                path,
                factory.create(oldContent),
                factory.create(newContent),
                "Before",
                "After"
            )
            DiffManager.getInstance().showDiff(project, request)
        }
    }

    private fun openTerminal(workDir: String?) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            try {
                val cls = Class.forName(
                    "org.jetbrains.plugins.terminal.TerminalView"
                )
                val getInstance = cls.getMethod(
                    "getInstance", Project::class.java
                )
                val terminalView = getInstance.invoke(null, project)
                val openMethod = cls.getMethod("openTerminalIn", Any::class.java)
                val dir = workDir ?: project.basePath
                openMethod.invoke(terminalView, dir)
            } catch (e: Exception) {
                logger.debug("Terminal plugin not available", e)
            }
        }
    }

    private fun openFolder(path: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val dir = File(path)
            if (dir.isDirectory) {
                val vFile = LocalFileSystem.getInstance()
                    .findFileByPath(path)
                if (vFile != null) {
                    FileEditorManager.getInstance(project)
                        .openFile(vFile, true)
                }
            }
        }
    }

    private fun openContent(
        content: String,
        title: String,
        extension: String
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val vFile = LightVirtualFile(
                "$title.$extension", content
            )
            FileEditorManager.getInstance(project)
                .openFile(vFile, true)
        }
    }

    private fun openOutputPanel() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val logFile = File(PathManager.getLogPath(), "idea.log")
            if (logFile.exists()) {
                val vFile = LocalFileSystem.getInstance()
                    .findFileByPath(logFile.absolutePath)
                if (vFile != null) {
                    FileEditorManager.getInstance(project)
                        .openFile(vFile, true)
                }
            }
        }
    }

    private fun openClaudeInTerminal() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            try {
                val cls = Class.forName(
                    "org.jetbrains.plugins.terminal." +
                        "ShellTerminalWidget"
                )
                // Fall back to just opening a terminal
                openTerminal(project.basePath)
            } catch (e: Exception) {
                logger.debug("Terminal plugin not available", e)
            }
        }
    }

    private fun persistBannerDismissal(bannerId: String) {
        ClaudeSettings.getInstance().dismissedBanners.add(bannerId)
    }

    private fun showNotification(message: String, type: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val notifType = when (type) {
                "error" -> NotificationType.ERROR
                "warning" -> NotificationType.WARNING
                else -> NotificationType.INFORMATION
            }
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Claude Code Notifications")
                .createNotification(message, notifType)
                .notify(project)
        }
    }

    override fun dispose() {
        // JBCefJSQuery is disposed when the browser is disposed
    }
}
