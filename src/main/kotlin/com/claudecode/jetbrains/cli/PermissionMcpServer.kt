package com.claudecode.jetbrains.cli

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class PermissionRequest(
    val requestId: String,
    val toolName: String,
    val input: Map<String, Any?>,
    val toolUseId: String,
    val riskLevel: String
)

data class PermissionResponse(
    val behavior: String,
    val message: String? = null
)

class PermissionMcpServer {

    private val logger = Logger.getInstance(PermissionMcpServer::class.java)
    private val gson = Gson()

    private var server: HttpServer? = null
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "PermissionMcpServer-worker").apply { isDaemon = true }
    }

    val port: Int
        get() = server?.address?.port ?: 0

    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<PermissionResponse>>()
    private val originalRequests = ConcurrentHashMap<String, PermissionRequest>()
    val sessionApprovals = ConcurrentHashMap.newKeySet<String>()

    var onPermissionRequest: ((PermissionRequest) -> Unit)? = null
    var onFileEditAutoApproved: ((PermissionRequest) -> Unit)? = null

    fun start() {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.executor = executor
        httpServer.createContext("/mcp") { exchange -> handleMcp(exchange) }
        httpServer.start()
        server = httpServer
        logger.info("PermissionMcpServer started on port ${httpServer.address.port}")
    }

    fun stop() {
        // Cancel all pending requests
        pendingRequests.values.forEach { future ->
            future.completeExceptionally(RuntimeException("Server stopped"))
        }
        pendingRequests.clear()
        originalRequests.clear()

        server?.stop(0)
        server = null
        executor.shutdownNow()
        logger.info("PermissionMcpServer stopped")
    }

    fun resolvePermission(requestId: String, response: PermissionResponse) {
        pendingRequests.remove(requestId)?.complete(response)
    }

    fun getOriginalRequest(requestId: String): PermissionRequest? = originalRequests[requestId]

    fun addSessionApproval(requestId: String) {
        val request = originalRequests[requestId]
        if (request != null) {
            sessionApprovals.add(request.toolName)
            logger.info("Added session approval for tool: ${request.toolName}")
        }
    }

    private fun handleMcp(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            sendResponse(exchange, 405, """{"error":"Method not allowed"}""")
            return
        }

        try {
            val body = exchange.requestBody.bufferedReader().readText()
            logger.debug("MCP request: $body")

            val json = JsonParser.parseString(body).asJsonObject
            val method = json.get("method")?.asString
            val id = json.get("id")

            when (method) {
                "initialize" -> handleInitialize(exchange, id)
                "notifications/initialized" -> sendResponse(exchange, 202, "")
                "tools/list" -> handleToolsList(exchange, id)
                "tools/call" -> handleToolsCall(exchange, json, id)
                else -> {
                    // Unknown methods: return generic success for forward compatibility
                    if (id != null && !id.isJsonNull) {
                        val result = JsonObject().apply {
                            addProperty("jsonrpc", "2.0")
                            add("id", id)
                            add("result", JsonObject())
                        }
                        sendResponse(exchange, 200, gson.toJson(result))
                    } else {
                        // Notification — no response needed
                        sendResponse(exchange, 202, "")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error handling MCP request", e)
            sendResponse(exchange, 500, """{"error":"Internal server error"}""")
        }
    }

    private fun handleInitialize(exchange: HttpExchange, id: JsonElement?) {
        val result = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id)
            add("result", JsonObject().apply {
                add("capabilities", JsonObject().apply {
                    add("tools", JsonObject())
                })
                add("serverInfo", JsonObject().apply {
                    addProperty("name", "jetbrains-perms")
                    addProperty("version", "1.0.0")
                })
                addProperty("protocolVersion", "2024-11-05")
            })
        }
        sendResponse(exchange, 200, gson.toJson(result))
    }

    private fun handleToolsList(exchange: HttpExchange, id: JsonElement?) {
        val result = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id)
            add("result", JsonObject().apply {
                add("tools", gson.toJsonTree(listOf(
                    mapOf(
                        "name" to "permission_prompt",
                        "description" to "Prompt the user for permission to run a tool",
                        "inputSchema" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "tool_name" to mapOf("type" to "string"),
                                "input" to mapOf("type" to "object"),
                                "tool_use_id" to mapOf("type" to "string"),
                                "risk_level" to mapOf("type" to "string")
                            ),
                            "required" to listOf("tool_name")
                        )
                    )
                )))
            })
        }
        sendResponse(exchange, 200, gson.toJson(result))
    }

    private fun handleToolsCall(exchange: HttpExchange, json: JsonObject, id: JsonElement?) {
        val params = json.getAsJsonObject("params") ?: JsonObject()
        val toolName = params.get("name")?.asString

        if (toolName != "permission_prompt") {
            val error = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                add("id", id)
                add("error", JsonObject().apply {
                    addProperty("code", -32601)
                    addProperty("message", "Unknown tool: $toolName")
                })
            }
            sendResponse(exchange, 200, gson.toJson(error))
            return
        }

        val arguments = params.getAsJsonObject("arguments") ?: JsonObject()
        val requestedTool = arguments.get("tool_name")?.asString ?: "unknown"
        val inputObj = arguments.getAsJsonObject("input")
        val toolUseId = arguments.get("tool_use_id")?.asString ?: ""
        val riskLevel = arguments.get("risk_level")?.asString ?: "normal"

        @Suppress("UNCHECKED_CAST")
        val inputMap = if (inputObj != null) {
            gson.fromJson(inputObj, Map::class.java) as Map<String, Any?>
        } else {
            emptyMap()
        }

        val requestId = "perm-${System.nanoTime()}"
        val request = PermissionRequest(
            requestId = requestId,
            toolName = requestedTool,
            input = inputMap,
            toolUseId = toolUseId,
            riskLevel = riskLevel
        )

        // Check session approvals — auto-allow if already approved
        if (sessionApprovals.contains(requestedTool)) {
            logger.info("Auto-approving $requestedTool (session approval)")
            // Notify for checkpoint snapshotting on file edits
            if (requestedTool == "Write" || requestedTool == "Edit") {
                onFileEditAutoApproved?.invoke(request)
            }
            val autoResponse = buildPermissionResult(
                id, PermissionResponse("allow")
            )
            sendResponse(exchange, 200, gson.toJson(autoResponse))
            return
        }

        val future = CompletableFuture<PermissionResponse>()
        pendingRequests[requestId] = future
        originalRequests[requestId] = request

        // Notify UI
        onPermissionRequest?.invoke(request)

        // Block until user responds or timeout
        try {
            val response = future.get(5, TimeUnit.MINUTES)
            originalRequests.remove(requestId)
            val result = buildPermissionResult(id, response)
            sendResponse(exchange, 200, gson.toJson(result))
        } catch (e: TimeoutException) {
            pendingRequests.remove(requestId)
            originalRequests.remove(requestId)
            logger.warn("Permission request timed out for $requestedTool")
            val result = buildPermissionResult(id, PermissionResponse("deny", "Timed out"))
            sendResponse(exchange, 200, gson.toJson(result))
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            originalRequests.remove(requestId)
            logger.warn("Permission request failed for $requestedTool", e)
            val result = buildPermissionResult(id, PermissionResponse("deny", "Error: ${e.message}"))
            sendResponse(exchange, 200, gson.toJson(result))
        }
    }

    private fun buildPermissionResult(id: JsonElement?, response: PermissionResponse): JsonObject {
        val responseJson = JsonObject().apply {
            addProperty("behavior", response.behavior)
            if (response.message != null) {
                addProperty("message", response.message)
            }
        }
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id)
            add("result", JsonObject().apply {
                add("content", gson.toJsonTree(listOf(
                    mapOf(
                        "type" to "text",
                        "text" to gson.toJson(responseJson)
                    )
                )))
            })
        }
    }

    private fun sendResponse(exchange: HttpExchange, statusCode: Int, body: String) {
        try {
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(statusCode, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) {
                exchange.responseBody.use { it.write(bytes) }
            }
        } catch (e: Exception) {
            logger.debug("Failed to send MCP response", e)
        }
    }
}
