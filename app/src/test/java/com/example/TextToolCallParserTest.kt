package com.example

import com.example.data.provider.TextToolCallParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric runner so org.json behaves like it does on a device.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TextToolCallParserTest {

    private val offered = setOf("web_search", "news_headlines", "search_code")

    @Test
    fun `json dialect block is extracted and removed from content`() {
        val content = "Let me look that up. " +
            """<tool_call> {"name": "search_code", "arguments": {"query": "agent tool language:java"}} </tool_call>""" +
            " One moment."
        val result = TextToolCallParser.extract(content, offered)
        assertEquals(1, result.calls.size)
        assertEquals("search_code", result.calls[0].toolName)
        assertEquals(
            "agent tool language:java",
            JSONObject(result.calls[0].argumentsJson).getString("query")
        )
        assertFalse(result.cleanedContent.contains("<tool_call>"))
        assertTrue(result.cleanedContent.contains("Let me look that up."))
        assertTrue(result.cleanedContent.contains("One moment."))
    }

    @Test
    fun `json dialect with arguments as a json string is normalised`() {
        val content = """<tool_call>{"name":"web_search","arguments":"{\"query\":\"monsoon news\"}"}</tool_call>"""
        val result = TextToolCallParser.extract(content, offered)
        assertEquals(1, result.calls.size)
        assertEquals("web_search", result.calls[0].toolName)
        assertEquals("monsoon news", JSONObject(result.calls[0].argumentsJson).getString("query"))
        assertEquals("", result.cleanedContent)
    }

    @Test
    fun `xml dialect parameters are typed`() {
        val content = """
            Searching now.
            <tool_call>
            <function=web_search>
            <parameter=query> Google Cloud TTS free tier </parameter>
            <parameter=limit>5</parameter>
            <parameter=ratio>1.5</parameter>
            <parameter=deep>true</parameter>
            </function>
            </tool_call>
        """.trimIndent()
        val result = TextToolCallParser.extract(content, offered)
        assertEquals(1, result.calls.size)
        assertEquals("web_search", result.calls[0].toolName)
        val args = JSONObject(result.calls[0].argumentsJson)
        assertEquals("Google Cloud TTS free tier", args.getString("query"))
        assertEquals(5L, args.getLong("limit"))
        assertEquals(1.5, args.getDouble("ratio"), 0.001)
        assertEquals(true, args.getBoolean("deep"))
        assertFalse(result.cleanedContent.contains("<tool_call>"))
        assertTrue(result.cleanedContent.contains("Searching now."))
    }

    @Test
    fun `unknown tool name stays as plain text`() {
        val jsonContent = """<tool_call> {"name": "delete_everything", "arguments": {}} </tool_call>"""
        val jsonResult = TextToolCallParser.extract(jsonContent, offered)
        assertTrue(jsonResult.calls.isEmpty())
        assertEquals(jsonContent, jsonResult.cleanedContent)

        val xmlContent = "<tool_call> <function=delete_everything> </function> </tool_call>"
        val xmlResult = TextToolCallParser.extract(xmlContent, offered)
        assertTrue(xmlResult.calls.isEmpty())
        assertEquals(xmlContent, xmlResult.cleanedContent)
    }

    @Test
    fun `malformed and unclosed blocks stay as plain text`() {
        val malformed = """<tool_call> {"name": "web_search", "arguments": { </tool_call>"""
        val malformedResult = TextToolCallParser.extract(malformed, offered)
        assertTrue(malformedResult.calls.isEmpty())
        assertEquals(malformed, malformedResult.cleanedContent)

        val unclosed = """<tool_call> {"name": "web_search", "arguments": {}}"""
        val unclosedResult = TextToolCallParser.extract(unclosed, offered)
        assertTrue(unclosedResult.calls.isEmpty())
        assertEquals(unclosed, unclosedResult.cleanedContent)
    }

    @Test
    fun `multiple calls in one response are all extracted in order`() {
        val content = "First: " +
            """<tool_call> {"name": "web_search", "arguments": {"query": "a"}} </tool_call>""" +
            " then " +
            "<tool_call> <function=news_headlines> <parameter=topic>india</parameter> </function> </tool_call>" +
            " done."
        val result = TextToolCallParser.extract(content, offered)
        assertEquals(2, result.calls.size)
        assertEquals("web_search", result.calls[0].toolName)
        assertEquals("news_headlines", result.calls[1].toolName)
        assertEquals("india", JSONObject(result.calls[1].argumentsJson).getString("topic"))
        assertTrue(result.cleanedContent.contains("First:"))
        assertTrue(result.cleanedContent.contains("then"))
        assertTrue(result.cleanedContent.contains("done."))
        assertFalse(result.cleanedContent.contains("<tool_call>"))
    }

    @Test
    fun `no tools offered means nothing is extracted`() {
        val content = """<tool_call> {"name": "web_search", "arguments": {"query": "a"}} </tool_call>"""
        val result = TextToolCallParser.extract(content, emptySet())
        assertTrue(result.calls.isEmpty())
        assertEquals(content, result.cleanedContent)
    }

    @Test
    fun `extraction is capped per response`() {
        val block = """<tool_call> {"name": "web_search", "arguments": {"query": "a"}} </tool_call>"""
        val content = (1..10).joinToString(" ") { block }
        val result = TextToolCallParser.extract(content, offered)
        assertEquals(TextToolCallParser.MAX_EXTRACTED_CALLS, result.calls.size)
    }
}
