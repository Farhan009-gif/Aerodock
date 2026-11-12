package com.example

import com.example.data.mcp.McpOAuthPending
import com.example.data.mcp.McpOAuthRecord
import com.example.data.mcp.fallbackResourceMetadataUrl
import com.example.data.mcp.parseResourceMetadataUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric runner so org.json behaves like it does on a device.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class McpOAuthHelpersTest {

    @Test
    fun `resource metadata url is parsed from the authenticate header`() {
        // The exact header shape Onshape's MCP server returns (probed 2026-11-12).
        val header = "Bearer error=\"invalid_token\", " +
            "error_description=\"Authentication failed\", " +
            "resource_metadata=\"https://fs-mcp.labs.onshape.app/.well-known/oauth-protected-resource/mcp\""
        assertEquals(
            "https://fs-mcp.labs.onshape.app/.well-known/oauth-protected-resource/mcp",
            parseResourceMetadataUrl(header)
        )
    }

    @Test
    fun `missing or blank header yields null`() {
        assertNull(parseResourceMetadataUrl(null))
        assertNull(parseResourceMetadataUrl(""))
        assertNull(parseResourceMetadataUrl("Bearer error=\"invalid_token\""))
    }

    @Test
    fun `fallback metadata url appends the resource path`() {
        assertEquals(
            "https://fs-mcp.labs.onshape.app/.well-known/oauth-protected-resource/mcp",
            fallbackResourceMetadataUrl("https://fs-mcp.labs.onshape.app/mcp")
        )
        assertEquals(
            "https://example.com/.well-known/oauth-protected-resource",
            fallbackResourceMetadataUrl("https://example.com")
        )
        assertNull(fallbackResourceMetadataUrl("not a url"))
    }

    @Test
    fun `oauth record survives a json round trip`() {
        val record = McpOAuthRecord(
            accessToken = "access-123",
            refreshToken = "refresh-456",
            expiresAtMs = 1_900_000_000_000L,
            tokenEndpoint = "https://mcp-auth.labs.onshape.app/token",
            clientId = "client-abc",
            clientSecret = ""
        )
        val restored = McpOAuthRecord.fromJson(JSONObject(record.toJson().toString()))
        assertEquals(record, restored)
    }

    @Test
    fun `oauth record with missing fields is rejected`() {
        assertNull(McpOAuthRecord.fromJson(JSONObject().put("access_token", "x")))
    }

    @Test
    fun `pending flow survives a json round trip`() {
        val pending = McpOAuthPending(
            serverId = "srv-1",
            state = "state-xyz",
            codeVerifier = "verifier-123",
            tokenEndpoint = "https://auth.example.com/token",
            clientId = "client-abc",
            clientSecret = "secret-1"
        )
        val restored = McpOAuthPending.fromJson(JSONObject(pending.toJson().toString()))
        assertEquals(pending, restored)
        assertNull(McpOAuthPending.fromJson(JSONObject().put("server_id", "srv-1")))
    }
}
