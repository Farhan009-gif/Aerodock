package com.example

import com.example.data.provider.sanitizeGeminiSchema
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
class GeminiSchemaSanitizeTest {

    private fun sanitize(json: String): JSONObject =
        sanitizeGeminiSchema(JSONObject(json)) as JSONObject

    @Test
    fun `composio x-mcp-header is stripped from a nested property`() {
        // Shape modelled on the real failure (2026-11-13): Gemini rejected the
        // request with 'Unknown name "x-mcp-header"' inside a tool's parameters.
        val out = sanitize(
            """
            {
              "type": "object",
              "properties": {
                "tool_slug": {"type": "string", "description": "Tool to run"},
                "arguments": {"type": "object", "description": "Args"},
                "headers": {
                  "type": "object",
                  "x-mcp-header": "x-consumer-api-key",
                  "properties": {
                    "value": {"type": "string", "x-mcp-header": "secret"}
                  }
                }
              },
              "required": ["tool_slug"]
            }
            """.trimIndent()
        )
        assertFalse(out.toString().contains("x-mcp-header"))
        val props = out.getJSONObject("properties")
        assertEquals("string", props.getJSONObject("tool_slug").getString("type"))
        assertEquals("Tool to run", props.getJSONObject("tool_slug").getString("description"))
        assertEquals("tool_slug", out.getJSONArray("required").getString(0))
        assertEquals(
            "string",
            props.getJSONObject("headers").getJSONObject("properties")
                .getJSONObject("value").getString("type")
        )
    }

    @Test
    fun `schema decorations are dropped and real fields kept`() {
        val out = sanitize(
            """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "title": "SearchInput",
              "type": "object",
              "additionalProperties": false,
              "default": {},
              "properties": {
                "query": {"type": "string", "title": "Query", "examples": ["cats"]}
              }
            }
            """.trimIndent()
        )
        assertFalse(out.has("$schema"))
        assertFalse(out.has("title"))
        assertFalse(out.has("additionalProperties"))
        assertFalse(out.has("default"))
        val query = out.getJSONObject("properties").getJSONObject("query")
        assertEquals("string", query.getString("type"))
        assertFalse(query.has("title"))
        assertFalse(query.has("examples"))
    }

    @Test
    fun `items and anyOf are cleaned recursively and enums survive`() {
        val out = sanitize(
            """
            {
              "type": "object",
              "properties": {
                "tags": {
                  "type": "array",
                  "items": {"type": "string", "x-mcp-header": "h", "maxLength": 40}
                },
                "mode": {
                  "anyOf": [
                    {"type": "string", "enum": ["fast", "slow"], "x-extra": 1},
                    {"type": "null", "title": "None"}
                  ]
                }
              }
            }
            """.trimIndent()
        )
        val props = out.getJSONObject("properties")
        val items = props.getJSONObject("tags").getJSONObject("items")
        assertEquals(40, items.getInt("maxLength"))
        assertFalse(items.has("x-mcp-header"))
        val anyOf = props.getJSONObject("mode").getJSONArray("anyOf")
        assertEquals("fast", anyOf.getJSONObject(0).getJSONArray("enum").getString(0))
        assertFalse(anyOf.getJSONObject(0).has("x-extra"))
        assertFalse(anyOf.getJSONObject(1).has("title"))
    }

    @Test
    fun `properties without a type gain object type`() {
        val out = sanitize("""{"properties": {"a": {"type": "string"}}}""")
        assertEquals("object", out.getString("type"))
        assertTrue(out.has("properties"))
    }
}
