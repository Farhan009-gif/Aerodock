package com.example.data.mcp

import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.McpTransportType
import com.example.data.provider.ErrorNormalizer
import com.example.data.provider.ProviderNormalizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class McpDiscoveryResult(
    val serverProtocolVersion: String,
    val serverTitle: String,
    val tools: List<McpToolEntity>,
    val resourceNames: List<String>,
    val promptNames: List<String>
)

data class McpToolCallResult(
    val outputText: String,
    val isError: Boolean
)

/**
 * Remote Model Context Protocol (MCP) JSON-RPC 2.0 client over OkHttp.
 *
 * Supports:
 * - Streamable HTTP transport (JSON-RPC 2.0 POST with `Accept: application/json, text/event-stream`
 *   and `Mcp-Session-Id` propagation).
 * - SSE fallback transport (handles event-stream `data:` frames or SSE endpoint discovery).
 * - Custom Authorization / Bearer token header decrypted from Tink Keystore vault.
 * - Cleartext HTTP host guard enforcement via the shared OkHttpClient interceptor.
 */
class McpClient(
    private val okHttpClient: OkHttpClient,
    /**
     * For OAuth-signed-in servers: given a server ID, force-refreshes the
     * access token and returns the new one (null when the server has no OAuth
     * record or the refresh failed). Consulted once when a request meets a
     * 401, so an expired token renews itself instead of failing the call.
     */
    private val oauthRefresher: (suspend (serverId: String) -> String?)? = null
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val requestIdCounter = AtomicInteger(1)

    private val sessionIdsByServer = ConcurrentHashMap<String, String>()

    /**
     * Tests the connection to an MCP server and discovers its `tools/list`,
     * `resources/list`, and `prompts/list`.
     */
    suspend fun testAndDiscoverServer(
        server: McpServerEntity,
        rawAuthToken: String
    ): Result<McpDiscoveryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val initParams = JSONObject().apply {
                put("protocolVersion", "2025-03-26")
                put("capabilities", JSONObject())
                put(
                    "clientInfo",
                    JSONObject().apply {
                        put("name", "Runway Android")
                        put("version", "1.0.0")
                    }
                )
            }

            // 1. Send initialize request (tolerate servers that allow stateless tools/list directly)
            val initResult = runCatching {
                sendJsonRpcRequest(
                    server = server,
                    rawAuthToken = rawAuthToken,
                    method = "initialize",
                    params = initParams
                )
            }.getOrNull()

            val protocolVersion = initResult?.optString("protocolVersion", "2025-03-26") ?: "2025-03-26"
            val serverInfo = initResult?.optJSONObject("serverInfo")
            val serverTitle = serverInfo?.optString("name", server.name)?.ifBlank { server.name } ?: server.name

            // Optional notifications/initialized
            runCatching {
                sendJsonRpcNotification(
                    server = server,
                    rawAuthToken = rawAuthToken,
                    method = "notifications/initialized"
                )
            }

            // 2. Discover tools/list (required primary capability)
            val toolsResultJson = sendJsonRpcRequest(
                server = server,
                rawAuthToken = rawAuthToken,
                method = "tools/list",
                params = JSONObject()
            )
            val tools = parseToolsList(server, toolsResultJson)

            // 3. Discover resources/list (optional in MCP servers)
            val resourceNames = runCatching {
                val resJson = sendJsonRpcRequest(
                    server = server,
                    rawAuthToken = rawAuthToken,
                    method = "resources/list",
                    params = JSONObject()
                )
                parseResourcesList(resJson)
            }.getOrDefault(emptyList())

            // 4. Discover prompts/list (optional in MCP servers)
            val promptNames = runCatching {
                val prmJson = sendJsonRpcRequest(
                    server = server,
                    rawAuthToken = rawAuthToken,
                    method = "prompts/list",
                    params = JSONObject()
                )
                parsePromptsList(prmJson)
            }.getOrDefault(emptyList())

            McpDiscoveryResult(
                serverProtocolVersion = protocolVersion,
                serverTitle = serverTitle,
                tools = tools,
                resourceNames = resourceNames,
                promptNames = promptNames
            )
        }.recoverCatching { throwable ->
            if (throwable is ProviderNormalizedException) throw throwable
            throw ProviderNormalizedException(
                ErrorNormalizer.normalizeException(
                    throwable = throwable,
                    providerName = "MCP Server (${server.name})",
                    baseUrl = server.url
                )
            )
        }
    }

    /**
     * Executes a single MCP tool via JSON-RPC `tools/call`.
     */
    suspend fun callTool(
        server: McpServerEntity,
        rawAuthToken: String,
        toolName: String,
        argumentsJson: String
    ): Result<McpToolCallResult> = withContext(Dispatchers.IO) {
        runCatching {
            val argsObj = runCatching {
                if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
            }.getOrElse {
                JSONObject().put("raw_input", argumentsJson)
            }

            val params = JSONObject().apply {
                put("name", toolName)
                put("arguments", argsObj)
            }

            val resultObj = sendJsonRpcRequest(
                server = server,
                rawAuthToken = rawAuthToken,
                method = "tools/call",
                params = params
            )
            parseToolCallResult(resultObj)
        }.recoverCatching { throwable ->
            if (throwable is ProviderNormalizedException) throw throwable
            throw ProviderNormalizedException(
                ErrorNormalizer.normalizeException(
                    throwable = throwable,
                    providerName = "MCP Server (${server.name})",
                    baseUrl = server.url
                )
            )
        }
    }

    internal fun buildJsonRpcPayload(
        id: Int,
        method: String,
        params: JSONObject? = null
    ): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) {
                put("params", params)
            }
        }
    }

    private fun sendJsonRpcNotification(
        server: McpServerEntity,
        rawAuthToken: String,
        method: String
    ) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
        }
        val reqBuilder = buildHttpPostRequest(server, rawAuthToken, payload)
        okHttpClient.newCall(reqBuilder.build()).execute().close()
    }

    private fun sendJsonRpcRequest(
        server: McpServerEntity,
        rawAuthToken: String,
        method: String,
        params: JSONObject?
    ): JSONObject {
        var tokenToUse = rawAuthToken
        var attempt = 0
        while (true) {
            val reqId = requestIdCounter.getAndIncrement()
            val payload = buildJsonRpcPayload(reqId, method, params)
            val reqBuilder = buildHttpPostRequest(server, tokenToUse, payload)

            val failure = okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val sessionHeader = response.header("Mcp-Session-Id")
                    ?: response.header("mcp-session-id")
                if (!sessionHeader.isNullOrBlank()) {
                    sessionIdsByServer[server.id] = sessionHeader.trim()
                }

                val rawBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return extractJsonRpcResultOrThrow(rawBody, server.name)
                }
                response.code to rawBody
            }

            // One OAuth retry: a 401 on a signed-in server usually means the
            // access token expired mid-session — refresh it and try once more.
            val (statusCode, rawBody) = failure
            if (statusCode == 401 && attempt == 0 && oauthRefresher != null) {
                val refreshed = runCatching {
                    kotlinx.coroutines.runBlocking { oauthRefresher.invoke(server.id) }
                }.getOrNull()
                if (!refreshed.isNullOrBlank() && refreshed != tokenToUse) {
                    tokenToUse = refreshed
                    attempt++
                    continue
                }
            }

            throw ProviderNormalizedException(
                ErrorNormalizer.normalizeHttpError(
                    statusCode = statusCode,
                    responseBody = rawBody,
                    providerName = "MCP (${server.name})"
                )
            )
        }
    }

    internal fun buildHttpPostRequest(
        server: McpServerEntity,
        rawAuthToken: String,
        payload: JSONObject
    ): Request.Builder {
        val cleanUrl = server.url.trim()
        val acceptHeader = if (server.transportEnum() == McpTransportType.SSE) {
            "text/event-stream, application/json"
        } else {
            "application/json, text/event-stream"
        }

        val builder = Request.Builder()
            .url(cleanUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("Accept", acceptHeader)

        val sessionId = sessionIdsByServer[server.id]
        if (!sessionId.isNullOrBlank()) {
            builder.header("Mcp-Session-Id", sessionId)
        }

        val cleanToken = rawAuthToken.trim()
        if (cleanToken.isNotEmpty()) {
            val headerName = server.authHeaderName.trim().ifBlank { "Authorization" }
            val headerValue = if (headerName.equals("Authorization", ignoreCase = true) &&
                !cleanToken.startsWith("Bearer ", ignoreCase = true) &&
                !cleanToken.startsWith("Basic ", ignoreCase = true)
            ) {
                "Bearer $cleanToken"
            } else {
                cleanToken
            }
            builder.header(headerName, headerValue)
        }

        return builder
    }

    /**
     * Parses either a standard JSON-RPC response body or a Streamable HTTP / SSE `data: {...}` stream
     * and returns the `result` object (or throws if `error` is present).
     */
    internal fun extractJsonRpcResultOrThrow(
        rawBody: String,
        serverName: String
    ): JSONObject {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) {
            return JSONObject()
        }

        val jsonEnvelope: JSONObject = if (trimmed.startsWith("{")) {
            JSONObject(trimmed)
        } else {
            // Streamable HTTP or SSE response containing `event: message` and `data: {...}` lines
            val dataJsons = trimmed.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .filter { it.startsWith("{") }
                .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
                .toList()

            dataJsons.firstOrNull { it.has("result") || it.has("error") }
                ?: dataJsons.lastOrNull()
                ?: throw IllegalStateException("MCP server '$serverName' returned invalid JSON-RPC payload: ${trimmed.take(160)}")
        }

        val errObj = jsonEnvelope.optJSONObject("error")
        if (errObj != null) {
            val code = errObj.optInt("code", -32000)
            val msg = errObj.optString("message", "MCP JSON-RPC error ($code)")
            throw IllegalStateException("MCP Error ($code): $msg")
        }

        return jsonEnvelope.optJSONObject("result") ?: jsonEnvelope
    }

    internal fun parseToolsList(
        server: McpServerEntity,
        resultJson: JSONObject
    ): List<McpToolEntity> {
        val toolsArr = resultJson.optJSONArray("tools") ?: return emptyList()
        val parsed = mutableListOf<McpToolEntity>()

        for (i in 0 until toolsArr.length()) {
            val item = toolsArr.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue
            val description = item.optString("description", "MCP tool on ${server.name}").trim()
            val inputSchema = item.optJSONObject("inputSchema")
                ?: item.optJSONObject("parameters")
                ?: JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                }
            val annotations = item.optJSONObject("annotations")
            val isReadOnly = SkillMarkdownParser.isReadOnlyTool(
                toolName = name,
                description = description,
                annotationsJson = annotations
            )

            parsed.add(
                McpToolEntity(
                    id = "${server.id}:$name",
                    serverId = server.id,
                    serverName = server.name,
                    toolName = name,
                    description = description,
                    inputSchemaJson = inputSchema.toString(),
                    isReadOnlyHint = isReadOnly,
                    isEnabled = true
                )
            )
        }
        return parsed
    }

    internal fun parseResourcesList(resultJson: JSONObject): List<String> {
        val arr = resultJson.optJSONArray("resources") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").ifBlank { obj.optString("uri") }.trim()
            if (name.isNotEmpty()) list.add(name)
        }
        return list
    }

    internal fun parsePromptsList(resultJson: JSONObject): List<String> {
        val arr = resultJson.optJSONArray("prompts") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isNotEmpty()) list.add(name)
        }
        return list
    }

    internal fun parseToolCallResult(resultObj: JSONObject): McpToolCallResult {
        val isError = resultObj.optBoolean("isError", false)
        val contentArr = resultObj.optJSONArray("content")
        if (contentArr != null && contentArr.length() > 0) {
            val parts = mutableListOf<String>()
            for (i in 0 until contentArr.length()) {
                val block = contentArr.optJSONObject(i) ?: continue
                when (block.optString("type", "text")) {
                    "text" -> {
                        val text = block.optString("text", "")
                        if (text.isNotBlank()) parts.add(text)
                    }
                    "resource" -> {
                        val res = block.optJSONObject("resource")
                        val uri = res?.optString("uri", "").orEmpty()
                        val text = res?.optString("text", "").orEmpty()
                        parts.add("[Resource $uri]\n$text".trim())
                    }
                    else -> {
                        parts.add(block.toString())
                    }
                }
            }
            val joined = parts.joinToString("\n\n").ifBlank { resultObj.toString() }
            return McpToolCallResult(outputText = joined, isError = isError)
        }

        val fallbackText = resultObj.optString("output")
            .ifBlank { resultObj.optString("text") }
            .ifBlank { resultObj.toString() }
        return McpToolCallResult(outputText = fallbackText, isError = isError)
    }
}
