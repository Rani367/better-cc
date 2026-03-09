package com.claudecode.jetbrains.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamJsonProtocolTest {

    @Test
    fun `parse system event`() {
        val json = """{"type":"system","subtype":"init","session_id":"sess-123"}"""
        val event = parseStreamEvent(json) as SystemEvent
        assertEquals("init", event.subtype)
        assertEquals("sess-123", event.sessionId)
    }

    @Test
    fun `parse user message event`() {
        val json = """{"type":"user","message":{"role":"user","content":"hello"},"session_id":"sess-1"}"""
        val event = parseStreamEvent(json) as UserMessageEvent
        assertEquals("user", event.message?.role)
        assertEquals("hello", event.message?.content)
        assertEquals("sess-1", event.sessionId)
    }

    @Test
    fun `parse assistant message event`() {
        val json = """{"type":"assistant","message":{"role":"assistant","content":"hi there"},"session_id":"sess-1"}"""
        val event = parseStreamEvent(json) as AssistantMessageEvent
        assertEquals("assistant", event.message?.role)
        assertEquals("hi there", event.message?.content)
    }

    @Test
    fun `parse stream_event with text_delta`() {
        val json = """{"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}},"session_id":"sess-1","parent_tool_use_id":null,"uuid":"evt-1"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper
        assertEquals("sess-1", event.sessionId)
        assertNull(event.parentToolUseId)
        assertEquals("evt-1", event.uuid)

        val inner = event.event as ContentBlockDelta
        assertEquals(0, inner.index)
        val delta = inner.delta as TextDelta
        assertEquals("Hello", delta.text)
    }

    @Test
    fun `parse stream_event with input_json_delta`() {
        val json = """{"type":"stream_event","event":{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"foo\":"}},"session_id":"s1","parent_tool_use_id":"tu-1","uuid":"evt-2"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper
        assertEquals("tu-1", event.parentToolUseId)

        val inner = event.event as ContentBlockDelta
        assertEquals(1, inner.index)
        val delta = inner.delta as InputJsonDelta
        assertEquals("{\"foo\":", delta.partialJson)
    }

    @Test
    fun `parse stream_event with thinking_delta`() {
        val json = """{"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Let me think..."}},"session_id":"s1","parent_tool_use_id":null,"uuid":"evt-3"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper

        val inner = event.event as ContentBlockDelta
        val delta = inner.delta as ThinkingDelta
        assertEquals("Let me think...", delta.thinking)
    }

    @Test
    fun `parse stream_event with content_block_start text`() {
        val json = """{"type":"stream_event","event":{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}},"session_id":"s1","parent_tool_use_id":null,"uuid":"evt-4"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper

        val inner = event.event as ContentBlockStart
        assertEquals(0, inner.index)
        val block = inner.contentBlock as TextBlock
        assertEquals("", block.text)
    }

    @Test
    fun `parse stream_event with content_block_start tool_use`() {
        val json = """{"type":"stream_event","event":{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"tu-abc","name":"Read","input":{}}},"session_id":"s1","parent_tool_use_id":null,"uuid":"evt-5"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper

        val inner = event.event as ContentBlockStart
        val block = inner.contentBlock as ToolUseBlock
        assertEquals("tu-abc", block.id)
        assertEquals("Read", block.name)
        assertEquals("{}", block.input)
    }

    @Test
    fun `parse stream_event with content_block_stop`() {
        val json = """{"type":"stream_event","event":{"type":"content_block_stop","index":0},"session_id":"s1","parent_tool_use_id":null,"uuid":"evt-6"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper

        val inner = event.event as ContentBlockStop
        assertEquals(0, inner.index)
    }

    @Test
    fun `parse stream_event with message_start`() {
        val json = """{"type":"stream_event","event":{"type":"message_start","message":{"id":"msg-1","role":"assistant","model":"claude-sonnet-4-20250514","usage":{"input_tokens":100,"output_tokens":0}}},"session_id":"s1","parent_tool_use_id":null,"uuid":"evt-7"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper

        val inner = event.event as MessageStart
        assertNotNull(inner.message)
        assertEquals("msg-1", inner.message?.id)
        assertEquals("assistant", inner.message?.role)
        assertEquals("claude-sonnet-4-20250514", inner.message?.model)
        assertEquals(100, inner.message?.usage?.inputTokens)
        assertEquals(0, inner.message?.usage?.outputTokens)
    }

    @Test
    fun `parse stream_event with message_delta`() {
        val json = """{"type":"stream_event","event":{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":42}},"session_id":"s1","parent_tool_use_id":null,"uuid":"evt-8"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper

        val inner = event.event as MessageDelta
        assertEquals("end_turn", inner.delta?.stopReason)
        assertNull(inner.delta?.stopSequence)
        assertEquals(42, inner.usage?.outputTokens)
    }

    @Test
    fun `parse stream_event with message_stop`() {
        val json = """{"type":"stream_event","event":{"type":"message_stop"},"session_id":"s1","parent_tool_use_id":null,"uuid":"evt-9"}"""
        val event = parseStreamEvent(json) as StreamEventWrapper

        assertTrue(event.event is MessageStop)
    }

    @Test
    fun `parse result event with nested structure`() {
        val json = """{"type":"result","result":{"content":[{"type":"text","text":"4"}],"usage":{"input_tokens":50,"output_tokens":10},"cost":{"input":0.001,"output":0.002,"total":0.003}},"session_id":"s1","subtype":"success","num_turns":1}"""
        val event = parseStreamEvent(json) as ResultEvent
        assertEquals("s1", event.sessionId)
        assertEquals("success", event.subtype)
        assertEquals(1, event.numTurns)
        assertEquals(1, event.content.size)
        assertEquals("text", event.content[0].type)
        assertEquals("4", event.content[0].text)
        assertEquals(50, event.usage?.inputTokens)
        assertEquals(10, event.usage?.outputTokens)
        assertEquals(0.003, event.costUsd!!, 0.0001)
    }

    @Test
    fun `parse result event with flat cost_usd`() {
        val json = """{"type":"result","result":{"content":[{"type":"text","text":"done"}],"usage":{"input_tokens":20,"output_tokens":5},"cost_usd":0.005},"session_id":"s2"}"""
        val event = parseStreamEvent(json) as ResultEvent
        assertEquals(0.005, event.costUsd!!, 0.0001)
    }

    @Test
    fun `parse result event with string result`() {
        val json = """{"type":"result","subtype":"success","result":"Hello! How can I help?","session_id":"s3","total_cost_usd":0.025,"usage":{"input_tokens":3,"output_tokens":13},"num_turns":1}"""
        val event = parseStreamEvent(json) as ResultEvent
        assertEquals("s3", event.sessionId)
        assertEquals("success", event.subtype)
        assertEquals(1, event.numTurns)
        assertEquals(1, event.content.size)
        assertEquals("text", event.content[0].type)
        assertEquals("Hello! How can I help?", event.content[0].text)
        assertEquals(3, event.usage?.inputTokens)
        assertEquals(13, event.usage?.outputTokens)
        assertEquals(0.025, event.costUsd!!, 0.001)
    }

    @Test
    fun `parse empty line returns null`() {
        assertNull(parseStreamEvent(""))
        assertNull(parseStreamEvent("   "))
        assertNull(parseStreamEvent("\n"))
    }

    @Test
    fun `parse malformed JSON returns UnknownEvent`() {
        val event = parseStreamEvent("{not valid json")
        assertTrue(event is UnknownEvent)
        assertNull((event as UnknownEvent).type)
    }

    @Test
    fun `parse unknown type returns UnknownEvent`() {
        val json = """{"type":"something_new","data":"value"}"""
        val event = parseStreamEvent(json) as UnknownEvent
        assertEquals("something_new", event.type)
    }

    @Test
    fun `serialize user message without session`() {
        val json = serializeUserMessage("What is 2+2?")
        assertTrue(json.contains(""""type":"user""""))
        assertTrue(json.contains(""""role":"user""""))
        assertTrue(json.contains(""""content":"What is 2+2?""""))
        // Should not contain session_id
        assertTrue(!json.contains("session_id"))
    }

    @Test
    fun `serialize user message with session`() {
        val json = serializeUserMessage("hello", "sess-abc")
        assertTrue(json.contains(""""session_id":"sess-abc""""))
        assertTrue(json.contains(""""content":"hello""""))
    }

    @Test
    fun `parse assistant message with array content blocks`() {
        val json = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"Hello world"}]},"session_id":"sess-1"}"""
        val event = parseStreamEvent(json) as AssistantMessageEvent
        assertEquals("assistant", event.message?.role)
        assertNull(event.message?.content)
        assertNotNull(event.message?.contentBlocks)
        assertEquals(1, event.message?.contentBlocks?.size)
        assertEquals("text", event.message?.contentBlocks?.get(0)?.type)
        assertEquals("Hello world", event.message?.contentBlocks?.get(0)?.text)
    }

    @Test
    fun `getTextContent returns string content when available`() {
        val mc = MessageContent(role = "assistant", content = "hello", contentBlocks = null)
        assertEquals("hello", mc.getTextContent())
    }

    @Test
    fun `getTextContent returns text from content blocks`() {
        val mc = MessageContent(
            role = "assistant",
            content = null,
            contentBlocks = listOf(
                ContentItem(type = "text", text = "part1", toolUseId = null, toolName = null, input = null),
                ContentItem(type = "tool_use", text = null, toolUseId = "tu-1", toolName = "Read", input = "{}"),
                ContentItem(type = "text", text = "part2", toolUseId = null, toolName = null, input = null)
            )
        )
        assertEquals("part1\npart2", mc.getTextContent())
    }

    @Test
    fun `getTextContent returns empty string when no content`() {
        val mc = MessageContent(role = "assistant", content = null, contentBlocks = null)
        assertEquals("", mc.getTextContent())
    }

    @Test
    fun `parse assistant message with null content`() {
        val json = """{"type":"assistant","message":{"role":"assistant","content":null},"session_id":"sess-1"}"""
        val event = parseStreamEvent(json) as AssistantMessageEvent
        assertNull(event.message?.content)
        assertNull(event.message?.contentBlocks)
        assertEquals("", event.message?.getTextContent())
    }
}
