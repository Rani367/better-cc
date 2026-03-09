package com.claudecode.jetbrains.ui.chat

import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.datatransfer.StringSelection

class CodeBlockRenderer(
    private val project: Project,
    browser: JBCefBrowserBase
) : Disposable {

    private val logger = Logger.getInstance(CodeBlockRenderer::class.java)
    private val jsQuery = JBCefJSQuery.create(browser)

    var permissionResponseHandler: ((String, String) -> Unit)? = null

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
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle JS callback", e)
        }
    }

    private fun openFile(path: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val basePath = project.basePath ?: return@invokeLater
            val fullPath = if (path.startsWith("/")) path else "$basePath/$path"
            val vFile = LocalFileSystem.getInstance().findFileByPath(fullPath) ?: return@invokeLater
            FileEditorManager.getInstance(project).openFile(vFile, true)
        }
    }

    override fun dispose() {
        // JBCefJSQuery is disposed when the browser is disposed
    }
}
