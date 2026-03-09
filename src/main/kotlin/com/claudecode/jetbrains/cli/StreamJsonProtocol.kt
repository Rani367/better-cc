package com.claudecode.jetbrains.cli

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

// ── Top-level stream events ─────────────────────────────────────────

sealed class StreamEvent {
    abstract val sessionId: String?
}

data class SystemEvent(
    val subtype: String?,
    override val sessionId: String?
) : StreamEvent()

data class UserMessageEvent(
    val message: MessageContent?,
    override val sessionId: String?
) : StreamEvent()

data class AssistantMessageEvent(
    val message: MessageContent?,
    override val sessionId: String?
) : StreamEvent()

data class StreamEventWrapper(
    val event: InnerEvent,
    override val sessionId: String?,
    val parentToolUseId: String?,
    val uuid: String?
) : StreamEvent()

data class ResultEvent(
    val content: List<ContentItem>,
    val usage: Usage?,
    val costUsd: Double?,
    override val sessionId: String?,
    val subtype: String?,
    val numTurns: Int?
) : StreamEvent()

data class UnknownEvent(
    val type: String?,
    val rawJson: String
) : StreamEvent() {
    override val sessionId: String? = null
}

// ── Inner events (inside stream_event wrapper) ──────────────────────

sealed class InnerEvent {
    abstract val type: String
}

data class MessageStart(
    val message: MessageInfo?
) : InnerEvent() {
    override val type = "message_start"
}

data class MessageDelta(
    val delta: MessageDeltaInfo?,
    val usage: Usage?
) : InnerEvent() {
    override val type = "message_delta"
}

object MessageStop : InnerEvent() {
    override val type = "message_stop"
}

data class ContentBlockStart(
    val index: Int,
    val contentBlock: ContentBlock?
) : InnerEvent() {
    override val type = "content_block_start"
}

data class ContentBlockDelta(
    val index: Int,
    val delta: Delta?
) : InnerEvent() {
    override val type = "content_block_delta"
}

data class ContentBlockStop(
    val index: Int
) : InnerEvent() {
    override val type = "content_block_stop"
}

data class UnknownInnerEvent(
    override val type: String,
    val rawJson: String
) : InnerEvent()

// ── Content blocks ──────────────────────────────────────────────────

sealed class ContentBlock {
    abstract val type: String
}

data class TextBlock(
    val text: String
) : ContentBlock() {
    override val type = "text"
}

data class ToolUseBlock(
    val id: String,
    val name: String,
    val input: String
) : ContentBlock() {
    override val type = "tool_use"
}

data class ThinkingBlock(
    val thinking: String
) : ContentBlock() {
    override val type = "thinking"
}

data class UnknownContentBlock(
    override val type: String
) : ContentBlock()

// ── Deltas ──────────────────────────────────────────────────────────

sealed class Delta {
    abstract val type: String
}

data class TextDelta(
    val text: String
) : Delta() {
    override val type = "text_delta"
}

data class InputJsonDelta(
    val partialJson: String
) : Delta() {
    override val type = "input_json_delta"
}

data class ThinkingDelta(
    val thinking: String
) : Delta() {
    override val type = "thinking_delta"
}

data class UnknownDelta(
    override val type: String
) : Delta()

// ── Supporting data classes ─────────────────────────────────────────

data class MessageContent(
    val role: String?,
    val content: String?,
    val contentBlocks: List<ContentItem>? = null
) {
    fun getTextContent(): String {
        if (content != null) return content
        return contentBlocks
            ?.filter { it.type == "text" }
            ?.mapNotNull { it.text }
            ?.joinToString("\n")
            ?: ""
    }
}

data class MessageInfo(
    val id: String?,
    val role: String?,
    val model: String?,
    val usage: Usage?
)

data class MessageDeltaInfo(
    val stopReason: String?,
    val stopSequence: String?
)

data class Usage(
    val inputTokens: Int?,
    val outputTokens: Int?,
    val cacheCreationInputTokens: Int?,
    val cacheReadInputTokens: Int?
)

data class ContentItem(
    val type: String?,
    val text: String?,
    val toolUseId: String?,
    val toolName: String?,
    val input: String?
)

// ── Parser ──────────────────────────────────────────────────────────

fun parseStreamEvent(jsonLine: String): StreamEvent? {
    val trimmed = jsonLine.trim()
    if (trimmed.isEmpty()) return null

    return try {
        val root = JsonParser.parseString(trimmed).asJsonObject
        val type = root.getString("type")

        when (type) {
            "system" -> SystemEvent(
                subtype = root.getString("subtype"),
                sessionId = root.getString("session_id")
            )

            "user" -> UserMessageEvent(
                message = root.getAsJsonObject("message")?.let { parseMessageContent(it) },
                sessionId = root.getString("session_id")
            )

            "assistant" -> AssistantMessageEvent(
                message = root.getAsJsonObject("message")?.let { parseMessageContent(it) },
                sessionId = root.getString("session_id")
            )

            "stream_event" -> {
                val eventObj = root.getAsJsonObject("event")
                val innerEvent = if (eventObj != null) parseInnerEvent(eventObj) else UnknownInnerEvent("unknown", trimmed)
                StreamEventWrapper(
                    event = innerEvent,
                    sessionId = root.getString("session_id"),
                    parentToolUseId = root.getString("parent_tool_use_id"),
                    uuid = root.getString("uuid")
                )
            }

            "result" -> parseResultEvent(root, trimmed)

            else -> UnknownEvent(type, trimmed)
        }
    } catch (e: Exception) {
        UnknownEvent(null, trimmed)
    }
}

fun serializeUserMessage(text: String, sessionId: String? = null): String {
    val obj = JsonObject()
    obj.addProperty("type", "user")

    val message = JsonObject()
    message.addProperty("role", "user")
    message.addProperty("content", text)
    obj.add("message", message)

    if (sessionId != null) {
        obj.addProperty("session_id", sessionId)
    }

    return obj.toString()
}

// ── Private helpers ─────────────────────────────────────────────────

private fun parseInnerEvent(obj: JsonObject): InnerEvent {
    val type = obj.getString("type") ?: return UnknownInnerEvent("unknown", obj.toString())

    return when (type) {
        "message_start" -> MessageStart(
            message = obj.getAsJsonObject("message")?.let { parseMessageInfo(it) }
        )

        "message_delta" -> MessageDelta(
            delta = obj.getAsJsonObject("delta")?.let { parseMessageDeltaInfo(it) },
            usage = obj.getAsJsonObject("usage")?.let { parseUsage(it) }
        )

        "message_stop" -> MessageStop

        "content_block_start" -> ContentBlockStart(
            index = obj.getInt("index") ?: 0,
            contentBlock = obj.getAsJsonObject("content_block")?.let { parseContentBlock(it) }
        )

        "content_block_delta" -> ContentBlockDelta(
            index = obj.getInt("index") ?: 0,
            delta = obj.getAsJsonObject("delta")?.let { parseDelta(it) }
        )

        "content_block_stop" -> ContentBlockStop(
            index = obj.getInt("index") ?: 0
        )

        else -> UnknownInnerEvent(type, obj.toString())
    }
}

private fun parseContentBlock(obj: JsonObject): ContentBlock {
    return when (val type = obj.getString("type")) {
        "text" -> TextBlock(text = obj.getString("text") ?: "")
        "tool_use" -> ToolUseBlock(
            id = obj.getString("id") ?: "",
            name = obj.getString("name") ?: "",
            input = obj.get("input")?.toString() ?: "{}"
        )
        "thinking" -> ThinkingBlock(thinking = obj.getString("thinking") ?: "")
        else -> UnknownContentBlock(type ?: "unknown")
    }
}

private fun parseDelta(obj: JsonObject): Delta {
    return when (val type = obj.getString("type")) {
        "text_delta" -> TextDelta(text = obj.getString("text") ?: "")
        "input_json_delta" -> InputJsonDelta(partialJson = obj.getString("partial_json") ?: "")
        "thinking_delta" -> ThinkingDelta(thinking = obj.getString("thinking") ?: "")
        else -> UnknownDelta(type ?: "unknown")
    }
}

private fun parseMessageContent(obj: JsonObject): MessageContent {
    val contentElement = obj.get("content")
    return when {
        contentElement == null || contentElement.isJsonNull -> MessageContent(
            role = obj.getString("role"),
            content = null,
            contentBlocks = null
        )
        contentElement.isJsonPrimitive -> MessageContent(
            role = obj.getString("role"),
            content = contentElement.asString,
            contentBlocks = null
        )
        contentElement.isJsonArray -> {
            val blocks = mutableListOf<ContentItem>()
            for (element in contentElement.asJsonArray) {
                if (element.isJsonObject) {
                    val item = element.asJsonObject
                    blocks.add(
                        ContentItem(
                            type = item.getString("type"),
                            text = item.getString("text"),
                            toolUseId = item.getString("tool_use_id"),
                            toolName = item.getString("tool_name"),
                            input = item.get("input")?.toString()
                        )
                    )
                }
            }
            MessageContent(
                role = obj.getString("role"),
                content = null,
                contentBlocks = blocks
            )
        }
        else -> MessageContent(
            role = obj.getString("role"),
            content = null,
            contentBlocks = null
        )
    }
}

private fun parseMessageInfo(obj: JsonObject): MessageInfo {
    return MessageInfo(
        id = obj.getString("id"),
        role = obj.getString("role"),
        model = obj.getString("model"),
        usage = obj.getAsJsonObject("usage")?.let { parseUsage(it) }
    )
}

private fun parseMessageDeltaInfo(obj: JsonObject): MessageDeltaInfo {
    return MessageDeltaInfo(
        stopReason = obj.getString("stop_reason"),
        stopSequence = obj.getString("stop_sequence")
    )
}

private fun parseUsage(obj: JsonObject): Usage {
    return Usage(
        inputTokens = obj.getInt("input_tokens"),
        outputTokens = obj.getInt("output_tokens"),
        cacheCreationInputTokens = obj.getInt("cache_creation_input_tokens"),
        cacheReadInputTokens = obj.getInt("cache_read_input_tokens")
    )
}

private fun parseResultEvent(root: JsonObject, rawJson: String): ResultEvent {
    val contentItems = mutableListOf<ContentItem>()

    // "result" can be a string (plain text response) or an object with "content" array
    val resultElement = root.get("result")
    val resultObj = when {
        resultElement == null || resultElement.isJsonNull -> root
        resultElement.isJsonPrimitive -> {
            // Result is a plain string — wrap it as a text content item
            contentItems.add(
                ContentItem(type = "text", text = resultElement.asString, toolUseId = null, toolName = null, input = null)
            )
            root // fall through to parse usage/cost from root
        }
        resultElement.isJsonObject -> resultElement.asJsonObject
        else -> root
    }

    // Parse content array if present and we didn't already extract from string
    if (contentItems.isEmpty()) {
        val contentArray = resultObj.getAsJsonArray("content")
        if (contentArray != null) {
            for (element in contentArray) {
                if (element.isJsonObject) {
                    val item = element.asJsonObject
                    contentItems.add(
                        ContentItem(
                            type = item.getString("type"),
                            text = item.getString("text"),
                            toolUseId = item.getString("tool_use_id"),
                            toolName = item.getString("tool_name"),
                            input = item.get("input")?.toString()
                        )
                    )
                }
            }
        }
    }

    val usage = resultObj.getAsJsonObject("usage")?.let { parseUsage(it) }
        ?: root.getAsJsonObject("usage")?.let { parseUsage(it) }

    // Cost can be a nested object or a flat number — check both resultObj and root
    val costUsd = when {
        resultObj.has("cost") && resultObj.get("cost").isJsonObject -> {
            resultObj.getAsJsonObject("cost")?.getDouble("total")
        }
        resultObj.has("cost_usd") -> resultObj.getDouble("cost_usd")
        root.has("total_cost_usd") -> root.getDouble("total_cost_usd")
        root.has("cost_usd") -> root.getDouble("cost_usd")
        else -> null
    }

    return ResultEvent(
        content = contentItems,
        usage = usage,
        costUsd = costUsd,
        sessionId = root.getString("session_id"),
        subtype = root.getString("subtype"),
        numTurns = root.getInt("num_turns")
    )
}

// ── JsonObject extension helpers ────────────────────────────────────

private fun JsonObject.getString(key: String): String? {
    val element = get(key) ?: return null
    if (element.isJsonNull) return null
    return try {
        element.asString
    } catch (e: Exception) {
        null
    }
}

private fun JsonObject.getInt(key: String): Int? {
    val element = get(key) ?: return null
    if (element.isJsonNull) return null
    return try {
        element.asInt
    } catch (e: Exception) {
        null
    }
}

private fun JsonObject.getDouble(key: String): Double? {
    val element = get(key) ?: return null
    if (element.isJsonNull) return null
    return try {
        element.asDouble
    } catch (e: Exception) {
        null
    }
}

@Suppress("unused")
private fun JsonElement.isNullOrMissing(): Boolean = this.isJsonNull
