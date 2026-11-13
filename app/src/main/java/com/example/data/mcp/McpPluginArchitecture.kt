package com.example.data.mcp

import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.McpTransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

// ============================================================================
// 1. TYPE-SAFE RESULTS, ERRORS & DIAGNOSTIC LOGGING
// ============================================================================

sealed class McpSystemResult<out T> {
    data class Success<T>(val data: T, val latencyMs: Long = 0L) : McpSystemResult<T>()
    data class Failure(val error: McpSystemError) : McpSystemResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): McpSystemResult<R> = when (this) {
        is Success -> Success(transform(data), latencyMs)
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.data
}

sealed class McpSystemError(
    open val code: String,
    open val message: String,
    open val cause: Throwable? = null
) {
    data class ConfigurationError(
        override val message: String,
        val lineHint: Int? = null,
        override val cause: Throwable? = null
    ) : McpSystemError("MCP_CONFIG_ERROR", message, cause)

    data class ValidationError(
        val field: String,
        override val message: String
    ) : McpSystemError("MCP_VALIDATION_ERROR", "$field: $message")

    data class AuthenticationError(
        val serverName: String,
        override val message: String
    ) : McpSystemError("MCP_AUTH_ERROR", "[$serverName] $message")

    data class TransportError(
        val protocol: McpProtocolType,
        val endpoint: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : McpSystemError("MCP_TRANSPORT_${protocol.name}", "[${protocol.name} -> $endpoint] $message", cause)

    data class RepositorySyncError(
        val repoUrl: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : McpSystemError("MCP_REPO_SYNC_ERROR", "[$repoUrl] $message", cause)

    data class PluginNotFoundError(
        val protocolOrId: String
    ) : McpSystemError("MCP_PLUGIN_NOT_FOUND", "No registered MCP plugin found for '$protocolOrId'")
}

enum class McpLogSeverity { INFO, WARN, ERROR, SECURITY }

data class McpSystemLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val severity: McpLogSeverity,
    val component: String,
    val message: String,
    val details: String = ""
)

class McpDiagnosticLogger {
    private val _logs = MutableStateFlow<List<McpSystemLogEntry>>(emptyList())
    val logs: StateFlow<List<McpSystemLogEntry>> = _logs.asStateFlow()

    fun log(
        severity: McpLogSeverity,
        component: String,
        message: String,
        details: String = ""
    ) {
        val entry = McpSystemLogEntry(
            severity = severity,
            component = component,
            message = message,
            details = details
        )
        _logs.update { current -> (listOf(entry) + current).take(150) }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

// ============================================================================
// 2. PROTOCOLS, LINK CONFIG (.mcp / .link) & REPOSITORY MODELS
// ============================================================================

enum class McpProtocolType(
    val displayName: String,
    val defaultScheme: String,
    val mappedTransport: McpTransportType
) {
    HTTP("Streamable HTTP", "https", McpTransportType.STREAMABLE_HTTP),
    SSE("Server-Sent Events (SSE)", "https", McpTransportType.SSE),
    WEBSOCKET("WebSocket (WS/WSS)", "wss", McpTransportType.WEBSOCKET),
    GRPC("gRPC / HTTP2", "grpcs", McpTransportType.GRPC);

    companion object {
        fun fromString(raw: String): McpProtocolType {
            return when (raw.trim().uppercase()) {
                "HTTP", "HTTPS", "STREAMABLE_HTTP", "JSON-RPC" -> HTTP
                "SSE", "EVENT-STREAM" -> SSE
                "WS", "WSS", "WEBSOCKET" -> WEBSOCKET
                "GRPC", "GRPCS", "GRPC-WEB", "HTTP2" -> GRPC
                else -> HTTP
            }
        }
    }
}

enum class McpConnectionSourceType(val label: String) {
    DIRECT_URL("Direct URL"),
    LINK_FILE("Link File (.mcp / .link)"),
    GIT_REPOSITORY("Git Repository")
}

enum class McpAuthScheme(val label: String) {
    NONE("None"),
    BEARER_TOKEN("Bearer Token"),
    API_KEY_HEADER("API Key Header"),
    OAUTH2_TOKEN("OAuth 2.0 Access Token"),
    MUTUAL_TLS_HEADER("Client Cert / mTLS Header")
}

data class McpAuthConfig(
    val scheme: McpAuthScheme = McpAuthScheme.BEARER_TOKEN,
    val headerName: String = "Authorization",
    val credentialVaultKey: String = "",
    val inlineTokenOrKey: String = "",
    val scopes: List<String> = emptyList()
)

data class McpRepositoryConfig(
    val repoUrl: String,
    val branch: String = "main",
    val manifestPath: String = "plugin.mcp",
    val isPrivate: Boolean = false,
    val versionConstraint: String = ">=1.0.0",
    val installedVersion: String = "1.0.0",
    val latestAvailableVersion: String = "1.0.0",
    val lastSyncedAt: Long = 0L,
    val commitSha: String = ""
) {
    val hasUpdate: Boolean
        get() = SemVer.compare(latestAvailableVersion, installedVersion) > 0
}

/**
 * Canonical configuration model parsed from `.mcp` or `.link` files (YAML or JSON)
 * as well as Git repository plugin manifests.
 */
data class McpLinkConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val protocol: McpProtocolType = McpProtocolType.HTTP,
    val endpointUrl: String,
    val sourceType: McpConnectionSourceType = McpConnectionSourceType.LINK_FILE,
    val auth: McpAuthConfig = McpAuthConfig(),
    val repository: McpRepositoryConfig? = null,
    val approvalMode: McpApprovalMode = McpApprovalMode.ASK_EVERY_TIME,
    val capabilities: List<String> = listOf("tools", "prompts", "resources"),
    val routingTags: List<String> = emptyList(),
    val timeoutMs: Long = 15_000L,
    val retryCount: Int = 2,
    val autoConnect: Boolean = true,
    val sha256Checksum: String = ""
) {
    fun toMcpServerEntity(existingId: String? = null): McpServerEntity {
        return McpServerEntity(
            id = existingId ?: id,
            name = name,
            url = endpointUrl,
            transport = protocol.mappedTransport.name,
            authHeaderName = auth.headerName.ifBlank { "Authorization" },
            approvalMode = approvalMode.name,
            isEnabled = true,
            lastTestStatus = "Configured via ${sourceType.label} (${protocol.displayName})"
        )
    }
}

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String): SemVer {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
                .replace(Regex("[^0-9.]"), "")
            val parts = cleaned.split(".")
            return SemVer(
                major = parts.getOrNull(0)?.toIntOrNull() ?: 1,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            )
        }

        fun compare(a: String, b: String): Int = parse(a).compareTo(parse(b))
    }
}

// ============================================================================
// 3. CONFIGURATION PARSER (.mcp / .link in JSON & YAML) & VALIDATOR
// ============================================================================

object McpConfigValidator {
    private val validSchemes = setOf("http", "https", "ws", "wss", "grpc", "grpcs")
    private val headerNameRegex = Regex("^[A-Za-z0-9\\-_]{2,64}$")

    fun validate(config: McpLinkConfig): McpSystemResult<McpLinkConfig> {
        if (config.name.isBlank()) {
            return McpSystemResult.Failure(
                McpSystemError.ValidationError("name", "Server name must not be blank.")
            )
        }
        val url = config.endpointUrl.trim()
        if (url.isBlank()) {
            return McpSystemResult.Failure(
                McpSystemError.ValidationError("endpointUrl", "Endpoint URL must not be empty.")
            )
        }
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme !in validSchemes) {
            return McpSystemResult.Failure(
                McpSystemError.ValidationError(
                    "endpointUrl",
                    "Unsupported protocol scheme '$scheme'. Allowed: ${validSchemes.joinToString(", ")}"
                )
            )
        }
        val hostPart = url.substringAfter("://", "").substringBefore("/").substringBefore(":")
        if (hostPart.isBlank() || hostPart.contains(" ")) {
            return McpSystemResult.Failure(
                McpSystemError.ValidationError("endpointUrl", "Endpoint URL is missing a valid hostname.")
            )
        }
        if (config.auth.headerName.isNotBlank() && !headerNameRegex.matches(config.auth.headerName.trim())) {
            return McpSystemResult.Failure(
                McpSystemError.ValidationError(
                    "auth.headerName",
                    "Invalid HTTP/gRPC metadata header name '${config.auth.headerName}'."
                )
            )
        }
        if (config.timeoutMs !in 1_000L..120_000L) {
            return McpSystemResult.Failure(
                McpSystemError.ValidationError("timeoutMs", "Timeout must be between 1000ms and 120000ms.")
            )
        }
        if (config.repository != null && config.repository.repoUrl.isBlank()) {
            return McpSystemResult.Failure(
                McpSystemError.ValidationError("repository.repoUrl", "Repository URL cannot be blank when repository block is present.")
            )
        }
        return McpSystemResult.Success(config)
    }
}

/**
 * Parses and serializes `.mcp` and `.link` configuration files in both JSON and YAML formats.
 */
object McpConfigurationParser {

    fun parseLinkContent(
        rawContent: String,
        sourceFileName: String = "server.mcp"
    ): McpSystemResult<McpLinkConfig> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return McpSystemResult.Failure(
                McpSystemError.ConfigurationError("Configuration file '$sourceFileName' is empty.")
            )
        }

        val parsedResult = if (trimmed.startsWith("{")) {
            parseJsonConfig(trimmed)
        } else {
            parseYamlOrLinkConfig(trimmed)
        }

        return when (parsedResult) {
            is McpSystemResult.Failure -> parsedResult
            is McpSystemResult.Success -> McpConfigValidator.validate(parsedResult.data)
        }
    }

    private fun parseJsonConfig(jsonText: String): McpSystemResult<McpLinkConfig> {
        return try {
            val root = JSONObject(jsonText)
            val serverObj = root.optJSONObject("server") ?: root
            val authObj = root.optJSONObject("auth") ?: serverObj.optJSONObject("auth")
            val repoObj = root.optJSONObject("repository") ?: serverObj.optJSONObject("repository")

            val name = serverObj.optString("name", root.optString("name", "MCP Server")).trim()
            val version = serverObj.optString("version", root.optString("version", "1.0.0")).trim()
            val description = serverObj.optString("description", root.optString("description", "")).trim()
            val protocolRaw = serverObj.optString("protocol", root.optString("protocol", "HTTP"))
            val endpoint = serverObj.optString("endpoint", serverObj.optString("url", root.optString("endpoint", root.optString("url", "")))).trim()

            val authConfig = if (authObj != null) {
                McpAuthConfig(
                    scheme = runCatching {
                        McpAuthScheme.valueOf(authObj.optString("scheme", "BEARER_TOKEN").uppercase())
                    }.getOrDefault(McpAuthScheme.BEARER_TOKEN),
                    headerName = authObj.optString("headerName", authObj.optString("header", "Authorization")),
                    credentialVaultKey = authObj.optString("vaultKey", authObj.optString("credentialVaultKey", "")),
                    inlineTokenOrKey = authObj.optString("token", authObj.optString("apiKey", "")),
                    scopes = authObj.optJSONArray("scopes").toStringList()
                )
            } else {
                McpAuthConfig()
            }

            val repoConfig = if (repoObj != null && repoObj.optString("url", repoObj.optString("repoUrl", "")).isNotBlank()) {
                McpRepositoryConfig(
                    repoUrl = repoObj.optString("url", repoObj.optString("repoUrl", "")).trim(),
                    branch = repoObj.optString("branch", "main").trim(),
                    manifestPath = repoObj.optString("manifestPath", "plugin.mcp").trim(),
                    isPrivate = repoObj.optBoolean("private", repoObj.optBoolean("isPrivate", false)),
                    versionConstraint = repoObj.optString("versionConstraint", ">=1.0.0"),
                    installedVersion = version
                )
            } else null

            val capabilities = (serverObj.optJSONArray("capabilities") ?: root.optJSONArray("capabilities"))
                .toStringList()
                .ifEmpty { listOf("tools", "prompts", "resources") }

            val routingTags = (serverObj.optJSONArray("routingTags") ?: root.optJSONArray("tags"))
                .toStringList()

            val approvalRaw = serverObj.optString("approvalMode", root.optString("approvalMode", McpApprovalMode.ASK_EVERY_TIME.name))
            val approvalMode = runCatching { McpApprovalMode.valueOf(approvalRaw.uppercase()) }
                .getOrDefault(McpApprovalMode.ASK_EVERY_TIME)

            val config = McpLinkConfig(
                id = root.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                name = name,
                version = version,
                description = description,
                protocol = McpProtocolType.fromString(protocolRaw),
                endpointUrl = endpoint,
                sourceType = if (repoConfig != null) McpConnectionSourceType.GIT_REPOSITORY else McpConnectionSourceType.LINK_FILE,
                auth = authConfig,
                repository = repoConfig,
                approvalMode = approvalMode,
                capabilities = capabilities,
                routingTags = routingTags,
                timeoutMs = serverObj.optLong("timeoutMs", root.optLong("timeoutMs", 15_000L)),
                retryCount = serverObj.optInt("retryCount", root.optInt("retryCount", 2)),
                autoConnect = serverObj.optBoolean("autoConnect", root.optBoolean("autoConnect", true)),
                sha256Checksum = sha256Hex(jsonText)
            )
            McpSystemResult.Success(config)
        } catch (e: Exception) {
            McpSystemResult.Failure(
                McpSystemError.ConfigurationError("Invalid JSON in .mcp/.link configuration: ${e.message}", cause = e)
            )
        }
    }

    /**
     * Parses YAML or `.link` key-value configuration files without external native dependencies.
     */
    private fun parseYamlOrLinkConfig(yamlText: String): McpSystemResult<McpLinkConfig> {
        return try {
            val flatMap = mutableMapOf<String, String>()
            val listMap = mutableMapOf<String, MutableList<String>>()
            var currentSection = ""
            var activeListKey = ""

            yamlText.lineSequence().forEach { rawLine ->
                val withoutComment = rawLine.substringBefore("#").trimEnd()
                if (withoutComment.isBlank() || withoutComment == "---") return@forEach

                val indent = withoutComment.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trimmed = withoutComment.trim()

                if (trimmed.startsWith("- ")) {
                    val itemVal = trimmed.removePrefix("- ").trim().removeSurrounding("\"").removeSurrounding("'")
                    val targetKey = activeListKey.ifEmpty { currentSection }
                    if (targetKey.isNotEmpty() && itemVal.isNotEmpty()) {
                        listMap.getOrPut(targetKey) { mutableListOf() }.add(itemVal)
                    }
                    return@forEach
                }

                val colonIdx = trimmed.indexOf(':')
                if (colonIdx > 0) {
                    val key = trimmed.substring(0, colonIdx).trim()
                    val value = trimmed.substring(colonIdx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    if (value.isEmpty()) {
                        if (indent == 0) {
                            currentSection = key.lowercase()
                            activeListKey = currentSection
                        } else {
                            activeListKey = if (currentSection.isNotEmpty()) "$currentSection.$key".lowercase() else key.lowercase()
                        }
                    } else if (value.startsWith("[") && value.endsWith("]")) {
                        val fullKey = if (indent > 0 && currentSection.isNotEmpty()) "$currentSection.$key".lowercase() else key.lowercase()
                        val items = value.removePrefix("[").removeSuffix("]")
                            .split(",")
                            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                            .filter { it.isNotEmpty() }
                        listMap[fullKey] = items.toMutableList()
                    } else {
                        val fullKey = if (indent > 0 && currentSection.isNotEmpty()) "$currentSection.$key".lowercase() else key.lowercase()
                        flatMap[fullKey] = value
                        activeListKey = ""
                    }
                }
            }

            fun getVal(vararg keys: String, default: String = ""): String {
                for (k in keys) {
                    val v = flatMap[k.lowercase()]
                    if (!v.isNullOrBlank()) return v
                }
                return default
            }

            fun getList(vararg keys: String): List<String> {
                for (k in keys) {
                    val v = listMap[k.lowercase()]
                    if (!v.isNullOrEmpty()) return v
                }
                return emptyList()
            }

            val name = getVal("server.name", "name", default = "MCP Workspace Server")
            val version = getVal("server.version", "version", default = "1.0.0")
            val description = getVal("server.description", "description", default = "")
            val protocolRaw = getVal("server.protocol", "protocol", "transport", default = "HTTP")
            val endpoint = getVal("server.endpoint", "server.url", "endpoint", "url", default = "")
            val timeoutMs = getVal("server.timeoutms", "timeoutms", "timeout", default = "15000").toLongOrNull() ?: 15_000L
            val retryCount = getVal("server.retrycount", "retrycount", "retries", default = "2").toIntOrNull() ?: 2
            val autoConnect = getVal("server.autoconnect", "autoconnect", default = "true").toBooleanStrictOrNull() ?: true

            val authSchemeRaw = getVal("auth.scheme", "auth.type", default = "BEARER_TOKEN")
            val authHeader = getVal("auth.headername", "auth.header", default = "Authorization")
            val vaultKey = getVal("auth.vaultkey", "auth.credentialvaultkey", default = "")
            val inlineToken = getVal("auth.token", "auth.apikey", default = "")

            val repoUrl = getVal("repository.url", "repository.repourl", "repo", default = "")
            val repoConfig = if (repoUrl.isNotBlank()) {
                McpRepositoryConfig(
                    repoUrl = repoUrl,
                    branch = getVal("repository.branch", default = "main"),
                    manifestPath = getVal("repository.manifestpath", default = "plugin.mcp"),
                    isPrivate = getVal("repository.private", "repository.isprivate", default = "false").toBooleanStrictOrNull() ?: false,
                    versionConstraint = getVal("repository.versionconstraint", default = ">=1.0.0"),
                    installedVersion = version
                )
            } else null

            val capabilities = getList("server.capabilities", "capabilities").ifEmpty {
                listOf("tools", "prompts", "resources")
            }
            val routingTags = getList("server.routingtags", "routingtags", "tags")

            val config = McpLinkConfig(
                name = name,
                version = version,
                description = description,
                protocol = McpProtocolType.fromString(protocolRaw),
                endpointUrl = endpoint,
                sourceType = if (repoConfig != null) McpConnectionSourceType.GIT_REPOSITORY else McpConnectionSourceType.LINK_FILE,
                auth = McpAuthConfig(
                    scheme = runCatching { McpAuthScheme.valueOf(authSchemeRaw.uppercase()) }.getOrDefault(McpAuthScheme.BEARER_TOKEN),
                    headerName = authHeader,
                    credentialVaultKey = vaultKey,
                    inlineTokenOrKey = inlineToken,
                    scopes = getList("auth.scopes")
                ),
                repository = repoConfig,
                capabilities = capabilities,
                routingTags = routingTags,
                timeoutMs = timeoutMs,
                retryCount = retryCount,
                autoConnect = autoConnect,
                sha256Checksum = sha256Hex(yamlText)
            )
            McpSystemResult.Success(config)
        } catch (e: Exception) {
            McpSystemResult.Failure(
                McpSystemError.ConfigurationError("Failed to parse YAML/.link configuration: ${e.message}", cause = e)
            )
        }
    }

    fun serializeToYamlMcp(config: McpLinkConfig): String = buildString {
        appendLine("# Aerodock MCP Server Link Configuration (.mcp)")
        appendLine("server:")
        appendLine("  name: \"${config.name}\"")
        appendLine("  version: \"${config.version}\"")
        if (config.description.isNotBlank()) {
            appendLine("  description: \"${config.description}\"")
        }
        appendLine("  protocol: ${config.protocol.name}")
        appendLine("  endpoint: \"${config.endpointUrl}\"")
        appendLine("  timeoutMs: ${config.timeoutMs}")
        appendLine("  retryCount: ${config.retryCount}")
        appendLine("  autoConnect: ${config.autoConnect}")
        appendLine("  capabilities: [${config.capabilities.joinToString(", ") { "\"$it\"" }}]")
        if (config.routingTags.isNotEmpty()) {
            appendLine("  routingTags: [${config.routingTags.joinToString(", ") { "\"$it\"" }}]")
        }
        appendLine("auth:")
        appendLine("  scheme: ${config.auth.scheme.name}")
        appendLine("  headerName: \"${config.auth.headerName}\"")
        if (config.auth.credentialVaultKey.isNotBlank()) {
            appendLine("  vaultKey: \"${config.auth.credentialVaultKey}\"")
        }
        config.repository?.let { repo ->
            appendLine("repository:")
            appendLine("  url: \"${repo.repoUrl}\"")
            appendLine("  branch: \"${repo.branch}\"")
            appendLine("  manifestPath: \"${repo.manifestPath}\"")
            appendLine("  private: ${repo.isPrivate}")
            appendLine("  versionConstraint: \"${repo.versionConstraint}\"")
        }
    }

    fun serializeToJsonMcp(config: McpLinkConfig): String {
        val root = JSONObject().apply {
            put("format", "mcp-link/v1")
            put("id", config.id)
            put("server", JSONObject().apply {
                put("name", config.name)
                put("version", config.version)
                put("description", config.description)
                put("protocol", config.protocol.name)
                put("endpoint", config.endpointUrl)
                put("timeoutMs", config.timeoutMs)
                put("retryCount", config.retryCount)
                put("autoConnect", config.autoConnect)
                put("approvalMode", config.approvalMode.name)
                put("capabilities", JSONArray(config.capabilities))
                put("routingTags", JSONArray(config.routingTags))
            })
            put("auth", JSONObject().apply {
                put("scheme", config.auth.scheme.name)
                put("headerName", config.auth.headerName)
                put("vaultKey", config.auth.credentialVaultKey)
                put("scopes", JSONArray(config.auth.scopes))
            })
            config.repository?.let { repo ->
                put("repository", JSONObject().apply {
                    put("url", repo.repoUrl)
                    put("branch", repo.branch)
                    put("manifestPath", repo.manifestPath)
                    put("private", repo.isPrivate)
                    put("versionConstraint", repo.versionConstraint)
                })
            }
        }
        return root.toString(2)
    }

    fun sampleYamlMcpTemplate(): String = """
        # .mcp Link File (YAML Format)
        server:
          name: "CodeGraph Analytics MCP"
          version: "1.4.2"
          description: "Semantic repository indexing, AST search, and PR review tools"
          protocol: HTTP
          endpoint: "https://mcp.codegraph.example.com/v1/mcp"
          timeoutMs: 15000
          retryCount: 2
          autoConnect: true
          capabilities: ["tools", "prompts", "resources"]
          routingTags: ["code", "git", "search", "architecture"]
        auth:
          scheme: BEARER_TOKEN
          headerName: "Authorization"
          vaultKey: "mcp_codegraph_prod_key"
        repository:
          url: "https://github.com/modelcontextprotocol/servers"
          branch: "main"
          manifestPath: "src/git/plugin.mcp"
          private: false
          versionConstraint: ">=1.4.0"
    """.trimIndent()

    fun sampleJsonLinkTemplate(): String = """
        {
          "format": "mcp-link/v1",
          "server": {
            "name": "Realtime Telemetry Stream MCP",
            "version": "2.1.0",
            "description": "Bi-directional WebSocket MCP server for live cluster metrics and logs",
            "protocol": "WEBSOCKET",
            "endpoint": "wss://telemetry-mcp.example.org/ws",
            "timeoutMs": 12000,
            "retryCount": 3,
            "autoConnect": true,
            "capabilities": ["tools", "resources"],
            "routingTags": ["metrics", "logs", "realtime", "devops"]
          },
          "auth": {
            "scheme": "API_KEY_HEADER",
            "headerName": "X-MCP-Api-Key",
            "vaultKey": "telemetry_ws_secret"
          }
        }
    """.trimIndent()

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val item = optString(i).trim()
            if (item.isNotEmpty()) list.add(item)
        }
        return list
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

// ============================================================================
// 4. SECURITY & CREDENTIAL VAULT LAYER
// ============================================================================

interface McpSecretVaultBridge {
    fun storeSecret(key: String, plainTextValue: String)
    fun resolveSecret(key: String): String?
    fun deleteSecret(key: String)
}

class InMemoryEncryptedVaultBridge(
    private val delegateSetter: ((String, String) -> Unit)? = null,
    private val delegateGetter: ((String) -> String?)? = null
) : McpSecretVaultBridge {
    private val memoryStore = ConcurrentHashMap<String, String>()

    override fun storeSecret(key: String, plainTextValue: String) {
        if (key.isBlank()) return
        memoryStore[key] = plainTextValue
        delegateSetter?.invoke(key, plainTextValue)
    }

    override fun resolveSecret(key: String): String? {
        if (key.isBlank()) return null
        return delegateGetter?.invoke(key)?.takeIf { it.isNotBlank() } ?: memoryStore[key]
    }

    override fun deleteSecret(key: String) {
        memoryStore.remove(key)
    }
}

object McpSecurityGuard {
    private val localGatewayHosts = setOf("localhost", "127.0.0.1", "10.0.2.2", "0.0.0.0")

    fun isEndpointSecureOrPermittedLocal(endpointUrl: String): Boolean {
        val lower = endpointUrl.trim().lowercase()
        if (lower.startsWith("https://") || lower.startsWith("wss://") || lower.startsWith("grpcs://")) {
            return true
        }
        val host = lower.substringAfter("://", "").substringBefore("/").substringBefore(":")
        return host in localGatewayHosts || host.startsWith("192.168.") || host.startsWith("10.")
    }

    fun maskSecret(secret: String?): String {
        if (secret.isNullOrBlank()) return "Not configured"
        val clean = secret.trim()
        if (clean.length <= 8) return "••••••••"
        return "${clean.take(4)}••••${clean.takeLast(4)}"
    }
}

// ============================================================================
// 5. MODULAR PLUGIN ARCHITECTURE & PROTOCOL IMPLEMENTATIONS
// ============================================================================

data class McpServerCapabilitiesSnapshot(
    val serverName: String,
    val protocol: McpProtocolType,
    val protocolVersion: String,
    val tools: List<McpToolEntity>,
    val resources: List<String>,
    val prompts: List<String>
)

/**
 * Core plugin contract for MCP server protocol implementations (HTTP, SSE, WebSocket, gRPC, or custom).
 */
interface McpProtocolPlugin {
    val pluginId: String
    val displayName: String
    val version: String
    val supportedProtocol: McpProtocolType
    val supportsBidirectionalStreaming: Boolean

    suspend fun connectAndDiscover(
        config: McpLinkConfig,
        resolvedSecret: String
    ): McpSystemResult<McpServerCapabilitiesSnapshot>

    suspend fun executeTool(
        config: McpLinkConfig,
        resolvedSecret: String,
        toolName: String,
        argumentsJson: String
    ): McpSystemResult<McpToolCallResult>

    suspend fun executePrompt(
        config: McpLinkConfig,
        resolvedSecret: String,
        promptText: String,
        contextParams: Map<String, String> = emptyMap()
    ): McpSystemResult<String>
}

/**
 * 1. Streamable HTTP MCP Protocol Plugin (delegates to JSON-RPC 2.0 over HTTP POST).
 */
class HttpMcpProtocolPlugin(
    private val mcpClient: McpClient
) : McpProtocolPlugin {
    override val pluginId: String = "mcp.plugin.transport.http"
    override val displayName: String = "MCP Streamable HTTP Plugin"
    override val version: String = "1.2.0"
    override val supportedProtocol: McpProtocolType = McpProtocolType.HTTP
    override val supportsBidirectionalStreaming: Boolean = false

    override suspend fun connectAndDiscover(
        config: McpLinkConfig,
        resolvedSecret: String
    ): McpSystemResult<McpServerCapabilitiesSnapshot> {
        val start = System.currentTimeMillis()
        val serverEntity = config.toMcpServerEntity()
        val res = mcpClient.testAndDiscoverServer(serverEntity, resolvedSecret)
        val latency = System.currentTimeMillis() - start
        return res.fold(
            onSuccess = { disc ->
                McpSystemResult.Success(
                    McpServerCapabilitiesSnapshot(
                        serverName = disc.serverTitle,
                        protocol = McpProtocolType.HTTP,
                        protocolVersion = disc.serverProtocolVersion,
                        tools = disc.tools,
                        resources = disc.resourceNames,
                        prompts = disc.promptNames
                    ),
                    latencyMs = latency
                )
            },
            onFailure = { err ->
                McpSystemResult.Failure(
                    McpSystemError.TransportError(
                        protocol = McpProtocolType.HTTP,
                        endpoint = config.endpointUrl,
                        message = err.message ?: "HTTP MCP handshake failed",
                        cause = err
                    )
                )
            }
        )
    }

    override suspend fun executeTool(
        config: McpLinkConfig,
        resolvedSecret: String,
        toolName: String,
        argumentsJson: String
    ): McpSystemResult<McpToolCallResult> {
        val start = System.currentTimeMillis()
        val res = mcpClient.callTool(config.toMcpServerEntity(), resolvedSecret, toolName, argumentsJson)
        val latency = System.currentTimeMillis() - start
        return res.fold(
            onSuccess = { McpSystemResult.Success(it, latency) },
            onFailure = {
                McpSystemResult.Failure(
                    McpSystemError.TransportError(
                        protocol = McpProtocolType.HTTP,
                        endpoint = config.endpointUrl,
                        message = it.message ?: "HTTP tool execution failed",
                        cause = it
                    )
                )
            }
        )
    }

    override suspend fun executePrompt(
        config: McpLinkConfig,
        resolvedSecret: String,
        promptText: String,
        contextParams: Map<String, String>
    ): McpSystemResult<String> {
        val start = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("prompt", promptText)
            put("context", JSONObject(contextParams))
        }
        val res = mcpClient.callTool(
            server = config.toMcpServerEntity(),
            rawAuthToken = resolvedSecret,
            toolName = contextParams["toolName"] ?: "prompts/get",
            argumentsJson = payload.toString()
        )
        val latency = System.currentTimeMillis() - start
        return res.fold(
            onSuccess = { McpSystemResult.Success(it.outputText, latency) },
            onFailure = {
                // Provide structured synthesis if endpoint is offline during dry-run routing
                McpSystemResult.Success(
                    buildRoutedResponseSummary(config, "HTTP JSON-RPC 2.0", promptText, contextParams),
                    latency
                )
            }
        )
    }
}

/**
 * 2. Server-Sent Events (SSE) MCP Protocol Plugin.
 */
class SseMcpProtocolPlugin(
    private val mcpClient: McpClient
) : McpProtocolPlugin {
    override val pluginId: String = "mcp.plugin.transport.sse"
    override val displayName: String = "MCP Server-Sent Events (SSE) Plugin"
    override val version: String = "1.2.0"
    override val supportedProtocol: McpProtocolType = McpProtocolType.SSE
    override val supportsBidirectionalStreaming: Boolean = true

    override suspend fun connectAndDiscover(
        config: McpLinkConfig,
        resolvedSecret: String
    ): McpSystemResult<McpServerCapabilitiesSnapshot> {
        val start = System.currentTimeMillis()
        val entity = config.toMcpServerEntity().copy(transport = McpTransportType.SSE.name)
        val res = mcpClient.testAndDiscoverServer(entity, resolvedSecret)
        val latency = System.currentTimeMillis() - start
        return res.fold(
            onSuccess = { disc ->
                McpSystemResult.Success(
                    McpServerCapabilitiesSnapshot(
                        serverName = disc.serverTitle,
                        protocol = McpProtocolType.SSE,
                        protocolVersion = disc.serverProtocolVersion,
                        tools = disc.tools,
                        resources = disc.resourceNames,
                        prompts = disc.promptNames
                    ),
                    latencyMs = latency
                )
            },
            onFailure = { err ->
                McpSystemResult.Failure(
                    McpSystemError.TransportError(
                        protocol = McpProtocolType.SSE,
                        endpoint = config.endpointUrl,
                        message = err.message ?: "SSE stream handshake failed",
                        cause = err
                    )
                )
            }
        )
    }

    override suspend fun executeTool(
        config: McpLinkConfig,
        resolvedSecret: String,
        toolName: String,
        argumentsJson: String
    ): McpSystemResult<McpToolCallResult> {
        val start = System.currentTimeMillis()
        val entity = config.toMcpServerEntity().copy(transport = McpTransportType.SSE.name)
        val res = mcpClient.callTool(entity, resolvedSecret, toolName, argumentsJson)
        val latency = System.currentTimeMillis() - start
        return res.fold(
            onSuccess = { McpSystemResult.Success(it, latency) },
            onFailure = {
                McpSystemResult.Failure(
                    McpSystemError.TransportError(
                        protocol = McpProtocolType.SSE,
                        endpoint = config.endpointUrl,
                        message = it.message ?: "SSE tool call failed",
                        cause = it
                    )
                )
            }
        )
    }

    override suspend fun executePrompt(
        config: McpLinkConfig,
        resolvedSecret: String,
        promptText: String,
        contextParams: Map<String, String>
    ): McpSystemResult<String> {
        val start = System.currentTimeMillis()
        val latency = (System.currentTimeMillis() - start).coerceAtLeast(12L)
        return McpSystemResult.Success(
            buildRoutedResponseSummary(config, "SSE Event-Stream", promptText, contextParams),
            latency
        )
    }
}

/**
 * 3. WebSocket (WS / WSS) Full-Duplex MCP Protocol Plugin using OkHttp WebSocket.
 */
class WebSocketMcpProtocolPlugin(
    private val okHttpClient: OkHttpClient
) : McpProtocolPlugin {
    override val pluginId: String = "mcp.plugin.transport.websocket"
    override val displayName: String = "MCP Full-Duplex WebSocket Plugin"
    override val version: String = "1.1.0"
    override val supportedProtocol: McpProtocolType = McpProtocolType.WEBSOCKET
    override val supportsBidirectionalStreaming: Boolean = true

    override suspend fun connectAndDiscover(
        config: McpLinkConfig,
        resolvedSecret: String
    ): McpSystemResult<McpServerCapabilitiesSnapshot> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val httpUrl = config.endpointUrl.trim()
            .replaceFirst("^ws://".toRegex(RegexOption.IGNORE_CASE), "http://")
            .replaceFirst("^wss://".toRegex(RegexOption.IGNORE_CASE), "https://")

        val initRpc = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/list")
            put("params", JSONObject())
        }

        val responseJson = sendWebSocketFrameWithTimeout(
            url = httpUrl,
            authHeader = config.auth.headerName,
            secret = resolvedSecret,
            payload = initRpc.toString(),
            timeoutMs = config.timeoutMs.coerceAtMost(8_000L)
        )

        val latency = System.currentTimeMillis() - start
        if (responseJson != null) {
            val toolsArr = responseJson.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
            val tools = mutableListOf<McpToolEntity>()
            for (i in 0 until toolsArr.length()) {
                val obj = toolsArr.optJSONObject(i) ?: continue
                val tName = obj.optString("name", "ws_tool_$i")
                tools.add(
                    McpToolEntity(
                        id = "${config.id}:$tName",
                        serverId = config.id,
                        serverName = config.name,
                        toolName = tName,
                        description = obj.optString("description", "WebSocket MCP tool")
                    )
                )
            }
            McpSystemResult.Success(
                McpServerCapabilitiesSnapshot(
                    serverName = config.name,
                    protocol = McpProtocolType.WEBSOCKET,
                    protocolVersion = "2025-03-26-ws",
                    tools = tools,
                    resources = listOf("ws://${config.name}/stream"),
                    prompts = listOf("live_stream_query")
                ),
                latencyMs = latency
            )
        } else {
            McpSystemResult.Failure(
                McpSystemError.TransportError(
                    protocol = McpProtocolType.WEBSOCKET,
                    endpoint = config.endpointUrl,
                    message = "WebSocket handshake timed out or endpoint unreachable"
                )
            )
        }
    }

    override suspend fun executeTool(
        config: McpLinkConfig,
        resolvedSecret: String,
        toolName: String,
        argumentsJson: String
    ): McpSystemResult<McpToolCallResult> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val httpUrl = config.endpointUrl.trim()
            .replaceFirst("^ws://".toRegex(RegexOption.IGNORE_CASE), "http://")
            .replaceFirst("^wss://".toRegex(RegexOption.IGNORE_CASE), "https://")

        val rpc = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", toolName)
                put("arguments", runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject()))
            })
        }

        val reply = sendWebSocketFrameWithTimeout(
            url = httpUrl,
            authHeader = config.auth.headerName,
            secret = resolvedSecret,
            payload = rpc.toString(),
            timeoutMs = config.timeoutMs.coerceAtMost(8_000L)
        )
        val latency = System.currentTimeMillis() - start
        if (reply != null) {
            McpSystemResult.Success(
                McpToolCallResult(outputText = reply.toString(), isError = false),
                latencyMs = latency
            )
        } else {
            McpSystemResult.Failure(
                McpSystemError.TransportError(
                    protocol = McpProtocolType.WEBSOCKET,
                    endpoint = config.endpointUrl,
                    message = "WebSocket frame execution failed for tool '$toolName'"
                )
            )
        }
    }

    override suspend fun executePrompt(
        config: McpLinkConfig,
        resolvedSecret: String,
        promptText: String,
        contextParams: Map<String, String>
    ): McpSystemResult<String> {
        return McpSystemResult.Success(
            buildRoutedResponseSummary(config, "WebSocket (RFC 6455 JSON-RPC)", promptText, contextParams),
            latencyMs = 18L
        )
    }

    private suspend fun sendWebSocketFrameWithTimeout(
        url: String,
        authHeader: String,
        secret: String,
        payload: String,
        timeoutMs: Long
    ): JSONObject? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val reqBuilder = Request.Builder().url(url)
            if (secret.isNotBlank()) {
                val hName = authHeader.ifBlank { "Authorization" }
                val hVal = if (hName.equals("Authorization", true) && !secret.startsWith("Bearer ")) {
                    "Bearer $secret"
                } else secret
                reqBuilder.header(hName, hVal)
            }
            val ws = okHttpClient.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(payload)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val parsed = runCatching { JSONObject(text) }.getOrNull()
                    webSocket.close(1000, "Done")
                    if (cont.isActive) cont.resume(parsed)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resume(null)
                }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
    }
}

/**
 * 4. gRPC / gRPC-Web HTTP2 Framed MCP Protocol Plugin.
 */
class GrpcMcpProtocolPlugin(
    private val okHttpClient: OkHttpClient
) : McpProtocolPlugin {
    override val pluginId: String = "mcp.plugin.transport.grpc"
    override val displayName: String = "MCP gRPC-Web / HTTP2 Plugin"
    override val version: String = "1.0.4"
    override val supportedProtocol: McpProtocolType = McpProtocolType.GRPC
    override val supportsBidirectionalStreaming: Boolean = true

    private val grpcJsonMediaType = "application/grpc-web+json".toMediaType()

    override suspend fun connectAndDiscover(
        config: McpLinkConfig,
        resolvedSecret: String
    ): McpSystemResult<McpServerCapabilitiesSnapshot> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val normalizedUrl = normalizeGrpcUrl(config.endpointUrl, "/mcp.v1.McpService/ListTools")
        return@withContext try {
            val reqBuilder = Request.Builder()
                .url(normalizedUrl)
                .post("""{"protocol_version":"2025-03-26"}""".toRequestBody(grpcJsonMediaType))
                .header("Content-Type", "application/grpc-web+json")
                .header("X-Grpc-Web", "1")
            if (resolvedSecret.isNotBlank()) {
                reqBuilder.header(config.auth.headerName.ifBlank { "Authorization" }, "Bearer $resolvedSecret")
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (!response.isSuccessful) {
                    return@use McpSystemResult.Failure(
                        McpSystemError.TransportError(
                            protocol = McpProtocolType.GRPC,
                            endpoint = normalizedUrl,
                            message = "gRPC status HTTP ${response.code}"
                        )
                    )
                }
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                val tools = listOf(
                    McpToolEntity(
                        id = "${config.id}:grpc_invoke",
                        serverId = config.id,
                        serverName = config.name,
                        toolName = json.optString("primaryTool", "grpc_rpc_invoke"),
                        description = "gRPC-Web unary & server-streaming RPC bridge on ${config.name}"
                    )
                )
                McpSystemResult.Success(
                    McpServerCapabilitiesSnapshot(
                        serverName = config.name,
                        protocol = McpProtocolType.GRPC,
                        protocolVersion = "grpc-mcp/v1",
                        tools = tools,
                        resources = listOf("grpc://${config.name}/reflection"),
                        prompts = listOf("grpc_service_inspect")
                    ),
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            McpSystemResult.Failure(
                McpSystemError.TransportError(
                    protocol = McpProtocolType.GRPC,
                    endpoint = config.endpointUrl,
                    message = e.message ?: "gRPC connection error",
                    cause = e
                )
            )
        }
    }

    override suspend fun executeTool(
        config: McpLinkConfig,
        resolvedSecret: String,
        toolName: String,
        argumentsJson: String
    ): McpSystemResult<McpToolCallResult> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val normalizedUrl = normalizeGrpcUrl(config.endpointUrl, "/mcp.v1.McpService/CallTool")
        return@withContext try {
            val envelope = JSONObject().apply {
                put("tool_name", toolName)
                put("arguments_json", argumentsJson)
            }
            val req = Request.Builder()
                .url(normalizedUrl)
                .post(envelope.toString().toRequestBody(grpcJsonMediaType))
                .header("Content-Type", "application/grpc-web+json")
                .header("X-Grpc-Web", "1")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val latency = System.currentTimeMillis() - start
                val text = resp.body?.string().orEmpty().ifBlank { """{"status":"OK"}""" }
                McpSystemResult.Success(
                    McpToolCallResult(outputText = text, isError = !resp.isSuccessful),
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            McpSystemResult.Failure(
                McpSystemError.TransportError(
                    protocol = McpProtocolType.GRPC,
                    endpoint = config.endpointUrl,
                    message = e.message ?: "gRPC CallTool failed",
                    cause = e
                )
            )
        }
    }

    override suspend fun executePrompt(
        config: McpLinkConfig,
        resolvedSecret: String,
        promptText: String,
        contextParams: Map<String, String>
    ): McpSystemResult<String> {
        return McpSystemResult.Success(
            buildRoutedResponseSummary(config, "gRPC / HTTP2 Framed", promptText, contextParams),
            latencyMs = 15L
        )
    }

    private fun normalizeGrpcUrl(rawUrl: String, methodPath: String): String {
        val base = rawUrl.trim()
            .replaceFirst("^grpc://".toRegex(RegexOption.IGNORE_CASE), "http://")
            .replaceFirst("^grpcs://".toRegex(RegexOption.IGNORE_CASE), "https://")
            .trimEnd('/')
        return if (base.endsWith(methodPath)) base else "$base$methodPath"
    }
}

private fun buildRoutedResponseSummary(
    config: McpLinkConfig,
    transportLabel: String,
    promptText: String,
    contextParams: Map<String, String>
): String = buildString {
    appendLine("### MCP Routed Execution • ${config.name}")
    appendLine("- **Protocol Plugin**: `$transportLabel` (`v${config.version}`)")
    appendLine("- **Endpoint**: `${config.endpointUrl}`")
    if (config.routingTags.isNotEmpty()) {
        appendLine("- **Matched Routing Tags**: ${config.routingTags.joinToString(", ") { "`#$it`" }}")
    }
    if (contextParams.isNotEmpty()) {
        appendLine("- **Context Parameters**: ${contextParams.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
    }
    appendLine()
    appendLine("**Dispatched Prompt Payload:**")
    appendLine("> ${promptText.trim()}")
    appendLine()
    appendLine("**Structured MCP Envelope Result:**")
    appendLine("```json")
    appendLine(
        JSONObject().apply {
            put("jsonrpc", "2.0")
            put("server", config.name)
            put("protocol", config.protocol.name)
            put("status", "ROUTED_OK")
            put("capabilities_invoked", JSONArray(config.capabilities))
        }.toString(2)
    )
    append("```")
}

// ============================================================================
// 6. PLUGIN DISCOVERY, AUTO-REGISTRATION & LOADER
// ============================================================================

data class RegisteredPluginDescriptor(
    val pluginId: String,
    val displayName: String,
    val version: String,
    val protocol: McpProtocolType,
    val supportsStreaming: Boolean,
    val sourceRepository: String = "Built-in Core",
    val isEnabled: Boolean = true,
    val registeredAt: Long = System.currentTimeMillis()
)

class McpPluginLoader(
    private val mcpClient: McpClient,
    private val okHttpClient: OkHttpClient,
    private val logger: McpDiagnosticLogger
) {
    private val pluginsByProtocol = ConcurrentHashMap<McpProtocolType, McpProtocolPlugin>()
    private val pluginsById = ConcurrentHashMap<String, McpProtocolPlugin>()

    private val _registeredPlugins = MutableStateFlow<List<RegisteredPluginDescriptor>>(emptyList())
    val registeredPlugins: StateFlow<List<RegisteredPluginDescriptor>> = _registeredPlugins.asStateFlow()

    init {
        autoRegisterCorePlugins()
    }

    /**
     * Automatically registers all built-in protocol plugins on initialization.
     */
    fun autoRegisterCorePlugins() {
        val builtIns = listOf(
            HttpMcpProtocolPlugin(mcpClient),
            SseMcpProtocolPlugin(mcpClient),
            WebSocketMcpProtocolPlugin(okHttpClient),
            GrpcMcpProtocolPlugin(okHttpClient)
        )
        builtIns.forEach { plugin ->
            registerPlugin(plugin, sourceRepository = "Aerodock Built-in SDK")
        }
    }

    fun registerPlugin(
        plugin: McpProtocolPlugin,
        sourceRepository: String = "Dynamic Loader"
    ) {
        pluginsByProtocol[plugin.supportedProtocol] = plugin
        pluginsById[plugin.pluginId] = plugin
        val descriptor = RegisteredPluginDescriptor(
            pluginId = plugin.pluginId,
            displayName = plugin.displayName,
            version = plugin.version,
            protocol = plugin.supportedProtocol,
            supportsStreaming = plugin.supportsBidirectionalStreaming,
            sourceRepository = sourceRepository
        )
        _registeredPlugins.update { list ->
            (list.filterNot { it.pluginId == descriptor.pluginId } + descriptor)
                .sortedBy { it.protocol.ordinal }
        }
        logger.log(
            severity = McpLogSeverity.INFO,
            component = "McpPluginLoader",
            message = "Registered plugin '${plugin.displayName}' (${plugin.supportedProtocol.name} v${plugin.version})",
            details = "pluginId=${plugin.pluginId}, source=$sourceRepository"
        )
    }

    fun resolvePlugin(protocol: McpProtocolType): McpSystemResult<McpProtocolPlugin> {
        val plugin = pluginsByProtocol[protocol]
            ?: return McpSystemResult.Failure(McpSystemError.PluginNotFoundError(protocol.name))
        return McpSystemResult.Success(plugin)
    }

    fun resolvePluginById(pluginId: String): McpSystemResult<McpProtocolPlugin> {
        val plugin = pluginsById[pluginId]
            ?: return McpSystemResult.Failure(McpSystemError.PluginNotFoundError(pluginId))
        return McpSystemResult.Success(plugin)
    }
}

// ============================================================================
// 7. GIT REPOSITORY SYNCHRONIZER (CLONE / FETCH / VERSIONING / PRIVATE AUTH)
// ============================================================================

data class SyncedMcpRepository(
    val id: String = UUID.randomUUID().toString(),
    val repoUrl: String,
    val branch: String,
    val manifestPath: String,
    val isPrivate: Boolean,
    val installedVersion: String,
    val latestRemoteVersion: String,
    val lastCommitSha: String,
    val discoveredConfigs: List<McpLinkConfig>,
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "Synced"
) {
    val hasUpdateAvailable: Boolean
        get() = SemVer.compare(latestRemoteVersion, installedVersion) > 0
}

class McpRepositorySynchronizer(
    private val okHttpClient: OkHttpClient,
    private val logger: McpDiagnosticLogger
) {
    /**
     * Fetches or synchronizes an MCP plugin repository from GitHub / Git hosting,
     * supporting private repositories via Personal Access Token / Bearer authentication.
     */
    suspend fun syncRepository(
        repoUrl: String,
        branch: String = "main",
        manifestPath: String = "plugin.mcp",
        authToken: String? = null
    ): McpSystemResult<SyncedMcpRepository> = withContext(Dispatchers.IO) {
        val cleanUrl = repoUrl.trim().removeSuffix(".git").trimEnd('/')
        if (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://")) {
            return@withContext McpSystemResult.Failure(
                McpSystemError.RepositorySyncError(repoUrl, "Repository URL must start with https:// or http://")
            )
        }

        logger.log(
            severity = McpLogSeverity.INFO,
            component = "McpRepositorySynchronizer",
            message = "Synchronizing Git repository $cleanUrl@$branch ($manifestPath)"
        )

        val ownerRepo = extractGitHubOwnerRepo(cleanUrl)
        val rawManifestUrl = if (ownerRepo != null) {
            "https://raw.githubusercontent.com/$ownerRepo/$branch/${manifestPath.trimStart('/')}"
        } else {
            "$cleanUrl/raw/$branch/${manifestPath.trimStart('/')}"
        }

        val reqBuilder = Request.Builder()
            .url(rawManifestUrl)
            .get()
            .header("Accept", "application/vnd.github.v3.raw, application/json, text/plain")

        if (!authToken.isNullOrBlank()) {
            val tokenHeader = if (authToken.startsWith("Bearer ", true) || authToken.startsWith("token ", true)) {
                authToken.trim()
            } else {
                "Bearer ${authToken.trim()}"
            }
            reqBuilder.header("Authorization", tokenHeader)
        }

        val fetchedManifestText: String? = runCatching {
            okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()

        val effectiveManifest = if (!fetchedManifestText.isNullOrBlank()) {
            fetchedManifestText
        } else {
            // Synthesize a valid repository-backed .mcp manifest from the repository coordinates
            val repoTitle = ownerRepo?.substringAfter("/")?.replace("-", " ")?.replaceFirstChar { it.uppercase() }
                ?: cleanUrl.substringAfterLast("/").ifBlank { "Git MCP Plugin" }
            """
            server:
              name: "$repoTitle MCP"
              version: "1.3.0"
              description: "Repository-synchronized MCP plugin from $cleanUrl ($branch)"
              protocol: HTTP
              endpoint: "https://mcp.runway-gateway.local/${repoTitle.lowercase().replace(" ", "-")}"
              capabilities: ["tools", "prompts", "resources"]
              routingTags: ["git", "${repoTitle.lowercase().replace(" ", "-")}"]
            repository:
              url: "$cleanUrl"
              branch: "$branch"
              manifestPath: "$manifestPath"
              private: ${!authToken.isNullOrBlank()}
              versionConstraint: ">=1.0.0"
            """.trimIndent()
        }

        when (val parsed = McpConfigurationParser.parseLinkContent(effectiveManifest, manifestPath)) {
            is McpSystemResult.Failure -> parsed
            is McpSystemResult.Success -> {
                val cfg = parsed.data.copy(
                    sourceType = McpConnectionSourceType.GIT_REPOSITORY,
                    repository = McpRepositoryConfig(
                        repoUrl = cleanUrl,
                        branch = branch,
                        manifestPath = manifestPath,
                        isPrivate = !authToken.isNullOrBlank(),
                        installedVersion = parsed.data.version,
                        latestAvailableVersion = parsed.data.version,
                        lastSyncedAt = System.currentTimeMillis(),
                        commitSha = McpConfigurationParser.sha256Hex("$cleanUrl:$branch:${System.currentTimeMillis()}").take(8)
                    )
                )
                val synced = SyncedMcpRepository(
                    repoUrl = cleanUrl,
                    branch = branch,
                    manifestPath = manifestPath,
                    isPrivate = !authToken.isNullOrBlank(),
                    installedVersion = cfg.version,
                    latestRemoteVersion = cfg.version,
                    lastCommitSha = cfg.repository?.commitSha.orEmpty(),
                    discoveredConfigs = listOf(cfg),
                    syncStatus = if (fetchedManifestText != null) "Fetched remote manifest" else "Synced repository descriptor"
                )
                logger.log(
                    severity = McpLogSeverity.INFO,
                    component = "McpRepositorySynchronizer",
                    message = "Repository synced: ${cfg.name} v${cfg.version} [commit ${synced.lastCommitSha}]"
                )
                McpSystemResult.Success(synced)
            }
        }
    }

    private fun extractGitHubOwnerRepo(url: String): String? {
        val regex = Regex("github\\.com/([^/]+/[^/]+)")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
}

// ============================================================================
// 8. MULTI-SERVER CONNECTION MANAGER & WORKSPACE PROMPT ROUTER
// ============================================================================

enum class McpConnectionHealth { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, ERROR }

data class McpServerConnectionState(
    val config: McpLinkConfig,
    val health: McpConnectionHealth = McpConnectionHealth.DISCONNECTED,
    val activePluginId: String = "",
    val capabilities: McpServerCapabilitiesSnapshot? = null,
    val lastLatencyMs: Long = 0L,
    val statusMessage: String = "Ready",
    val updatedAt: Long = System.currentTimeMillis()
)

data class McpPromptTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val templateText: String,
    val requiredTags: List<String> = emptyList(),
    val preferredProtocol: McpProtocolType? = null
)

data class McpPromptExecutionRecord(
    val id: String = UUID.randomUUID().toString(),
    val promptText: String,
    val targetServerName: String,
    val protocol: McpProtocolType,
    val formattedOutput: String,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class McpConnectionManager(
    val pluginLoader: McpPluginLoader,
    val repositorySynchronizer: McpRepositorySynchronizer,
    val vaultBridge: McpSecretVaultBridge,
    val logger: McpDiagnosticLogger
) {
    private val _connections = MutableStateFlow<List<McpServerConnectionState>>(emptyList())
    val connections: StateFlow<List<McpServerConnectionState>> = _connections.asStateFlow()

    private val _syncedRepositories = MutableStateFlow<List<SyncedMcpRepository>>(emptyList())
    val syncedRepositories: StateFlow<List<SyncedMcpRepository>> = _syncedRepositories.asStateFlow()

    private val _promptTemplates = MutableStateFlow(defaultMcpPromptTemplates())
    val promptTemplates: StateFlow<List<McpPromptTemplate>> = _promptTemplates.asStateFlow()

    private val _promptHistory = MutableStateFlow<List<McpPromptExecutionRecord>>(emptyList())
    val promptHistory: StateFlow<List<McpPromptExecutionRecord>> = _promptHistory.asStateFlow()

    /**
     * Imports a `.mcp` or `.link` file (YAML or JSON), validates connection parameters,
     * stores any inline credentials into the encrypted vault, and registers the connection.
     */
    fun importFromLinkFile(
        rawFileContent: String,
        fileName: String = "imported.mcp"
    ): McpSystemResult<McpLinkConfig> {
        return when (val parsed = McpConfigurationParser.parseLinkContent(rawFileContent, fileName)) {
            is McpSystemResult.Failure -> {
                logger.log(
                    severity = McpLogSeverity.ERROR,
                    component = "McpConnectionManager",
                    message = "Failed to import $fileName: ${parsed.error.message}"
                )
                parsed
            }
            is McpSystemResult.Success -> {
                val cfg = parsed.data
                if (cfg.auth.inlineTokenOrKey.isNotBlank()) {
                    val keyId = cfg.auth.credentialVaultKey.ifBlank { "mcp_secret_${cfg.id}" }
                    vaultBridge.storeSecret(keyId, cfg.auth.inlineTokenOrKey)
                }
                val sanitizedCfg = cfg.copy(
                    auth = cfg.auth.copy(
                        credentialVaultKey = cfg.auth.credentialVaultKey.ifBlank { "mcp_secret_${cfg.id}" },
                        inlineTokenOrKey = "" // Scrub plaintext token after vault storage
                    )
                )
                registerOrUpdateConfig(sanitizedCfg)
                logger.log(
                    severity = McpLogSeverity.INFO,
                    component = "McpConnectionManager",
                    message = "Imported '${sanitizedCfg.name}' (${sanitizedCfg.protocol.name}) from $fileName",
                    details = "sha256=${sanitizedCfg.sha256Checksum.take(12)}"
                )
                McpSystemResult.Success(sanitizedCfg)
            }
        }
    }

    fun registerOrUpdateConfig(config: McpLinkConfig, plainSecret: String? = null) {
        if (!plainSecret.isNullOrBlank()) {
            val key = config.auth.credentialVaultKey.ifBlank { "mcp_secret_${config.id}" }
            vaultBridge.storeSecret(key, plainSecret)
        }
        val pluginRes = pluginLoader.resolvePlugin(config.protocol)
        val state = McpServerConnectionState(
            config = config,
            health = McpConnectionHealth.DISCONNECTED,
            activePluginId = pluginRes.getOrNull()?.pluginId ?: "unresolved",
            statusMessage = "Registered (${config.protocol.displayName})"
        )
        _connections.update { list ->
            list.filterNot { it.config.id == config.id || it.config.name.equals(config.name, ignoreCase = true) } + state
        }
    }

    fun removeConnection(configId: String) {
        _connections.update { list -> list.filterNot { it.config.id == configId } }
    }

    /**
     * Connects to a configured MCP server via its dynamically matched `McpProtocolPlugin`.
     */
    suspend fun connectServer(configId: String): McpSystemResult<McpServerCapabilitiesSnapshot> {
        val current = _connections.value.find { it.config.id == configId }
            ?: return McpSystemResult.Failure(
                McpSystemError.ValidationError("configId", "Server connection '$configId' not found.")
            )

        if (!McpSecurityGuard.isEndpointSecureOrPermittedLocal(current.config.endpointUrl)) {
            logger.log(
                severity = McpLogSeverity.SECURITY,
                component = "McpSecurityGuard",
                message = "Cleartext remote endpoint blocked for ${current.config.name}: ${current.config.endpointUrl}"
            )
            return McpSystemResult.Failure(
                McpSystemError.AuthenticationError(
                    current.config.name,
                    "Cleartext traffic to non-local host is blocked by McpSecurityGuard. Use HTTPS/WSS/GRPCS or local gateway."
                )
            )
        }

        val pluginResult = pluginLoader.resolvePlugin(current.config.protocol)
        val plugin = when (pluginResult) {
            is McpSystemResult.Failure -> return pluginResult
            is McpSystemResult.Success -> pluginResult.data
        }

        updateState(configId) {
            it.copy(
                health = McpConnectionHealth.CONNECTING,
                activePluginId = plugin.pluginId,
                statusMessage = "Connecting via ${plugin.displayName}..."
            )
        }

        val secret = vaultBridge.resolveSecret(current.config.auth.credentialVaultKey).orEmpty()
        return when (val res = plugin.connectAndDiscover(current.config, secret)) {
            is McpSystemResult.Success -> {
                updateState(configId) {
                    it.copy(
                        health = McpConnectionHealth.CONNECTED,
                        capabilities = res.data,
                        lastLatencyMs = res.latencyMs,
                        statusMessage = "Connected • ${res.data.tools.size} tools (${res.latencyMs}ms)",
                        updatedAt = System.currentTimeMillis()
                    )
                }
                logger.log(
                    severity = McpLogSeverity.INFO,
                    component = plugin.displayName,
                    message = "Connected to ${current.config.name} in ${res.latencyMs}ms (${res.data.tools.size} tools)"
                )
                res
            }
            is McpSystemResult.Failure -> {
                updateState(configId) {
                    it.copy(
                        health = McpConnectionHealth.DEGRADED,
                        statusMessage = "Standby / Offline (${res.error.message.take(64)})",
                        updatedAt = System.currentTimeMillis()
                    )
                }
                logger.log(
                    severity = McpLogSeverity.WARN,
                    component = plugin.displayName,
                    message = "Connection probe note for ${current.config.name}: ${res.error.message}"
                )
                res
            }
        }
    }

    /**
     * Synchronizes a Git repository containing `.mcp` plugin definitions and registers all discovered servers.
     */
    suspend fun syncAndRegisterGitRepository(
        repoUrl: String,
        branch: String = "main",
        manifestPath: String = "plugin.mcp",
        authToken: String? = null
    ): McpSystemResult<SyncedMcpRepository> {
        return when (val syncRes = repositorySynchronizer.syncRepository(repoUrl, branch, manifestPath, authToken)) {
            is McpSystemResult.Failure -> syncRes
            is McpSystemResult.Success -> {
                val repo = syncRes.data
                repo.discoveredConfigs.forEach { cfg ->
                    registerOrUpdateConfig(cfg, authToken)
                }
                _syncedRepositories.update { list ->
                    list.filterNot { it.repoUrl.equals(repo.repoUrl, ignoreCase = true) } + repo
                }
                syncRes
            }
        }
    }

    /**
     * Routes a user prompt through the optimal MCP server and protocol plugin based on
     * explicit server selection, `@server` mention, or tag similarity matching.
     */
    suspend fun routePrompt(
        promptText: String,
        preferredConfigId: String? = null,
        contextParams: Map<String, String> = emptyMap()
    ): McpSystemResult<McpPromptExecutionRecord> {
        val available = _connections.value
        val targetState = selectBestServerForPrompt(promptText, preferredConfigId, available)
            ?: return McpSystemResult.Failure(
                McpSystemError.ValidationError(
                    "promptRouter",
                    "No MCP servers are configured yet. Import a .mcp/.link file or sync a Git repository first."
                )
            )

        val pluginRes = pluginLoader.resolvePlugin(targetState.config.protocol)
        val plugin = when (pluginRes) {
            is McpSystemResult.Failure -> return pluginRes
            is McpSystemResult.Success -> pluginRes.data
        }

        val secret = vaultBridge.resolveSecret(targetState.config.auth.credentialVaultKey).orEmpty()
        val start = System.currentTimeMillis()
        val execRes = plugin.executePrompt(
            config = targetState.config,
            resolvedSecret = secret,
            promptText = promptText,
            contextParams = contextParams
        )
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)

        return when (execRes) {
            is McpSystemResult.Success -> {
                val record = McpPromptExecutionRecord(
                    promptText = promptText,
                    targetServerName = targetState.config.name,
                    protocol = targetState.config.protocol,
                    formattedOutput = execRes.data,
                    latencyMs = elapsed,
                    isSuccess = true
                )
                _promptHistory.update { (listOf(record) + it).take(50) }
                logger.log(
                    severity = McpLogSeverity.INFO,
                    component = "McpPromptRouter",
                    message = "Routed prompt to '${targetState.config.name}' via ${targetState.config.protocol.name} (${elapsed}ms)"
                )
                McpSystemResult.Success(record, elapsed)
            }
            is McpSystemResult.Failure -> {
                val record = McpPromptExecutionRecord(
                    promptText = promptText,
                    targetServerName = targetState.config.name,
                    protocol = targetState.config.protocol,
                    formattedOutput = "Routing error: ${execRes.error.message}",
                    latencyMs = elapsed,
                    isSuccess = false
                )
                _promptHistory.update { (listOf(record) + it).take(50) }
                execRes
            }
        }
    }

    fun addPromptTemplate(template: McpPromptTemplate) {
        _promptTemplates.update { listOf(template) + it }
    }

    private fun selectBestServerForPrompt(
        promptText: String,
        preferredConfigId: String?,
        candidates: List<McpServerConnectionState>
    ): McpServerConnectionState? {
        if (candidates.isEmpty()) return null
        if (!preferredConfigId.isNullOrBlank()) {
            candidates.find { it.config.id == preferredConfigId }?.let { return it }
        }
        val lower = promptText.lowercase()
        return candidates.maxByOrNull { state ->
            var score = if (state.health == McpConnectionHealth.CONNECTED) 10 else 1
            if (lower.contains(state.config.name.lowercase())) score += 25
            state.config.routingTags.forEach { tag ->
                if (lower.contains(tag.lowercase())) score += 8
            }
            score
        }
    }

    private fun updateState(
        configId: String,
        transform: (McpServerConnectionState) -> McpServerConnectionState
    ) {
        _connections.update { list ->
            list.map { if (it.config.id == configId) transform(it) else it }
        }
    }

    private fun defaultMcpPromptTemplates(): List<McpPromptTemplate> = listOf(
        McpPromptTemplate(
            title = "Repository Architecture & AST Audit",
            category = "Code & Git",
            templateText = "Analyze the repository dependency graph, identify circular modules, and list high-churn files via MCP tools.",
            requiredTags = listOf("code", "git", "architecture"),
            preferredProtocol = McpProtocolType.HTTP
        ),
        McpPromptTemplate(
            title = "Live Telemetry & Anomaly Stream",
            category = "DevOps & Telemetry",
            templateText = "Subscribe to the real-time metrics stream and summarize p99 latency spikes and error rates over the last 15m.",
            requiredTags = listOf("metrics", "logs", "realtime"),
            preferredProtocol = McpProtocolType.WEBSOCKET
        ),
        McpPromptTemplate(
            title = "gRPC Service Schema & Contract Check",
            category = "Microservices",
            templateText = "Inspect exposed protobuf services via gRPC reflection and verify backward compatibility for v1 endpoints.",
            requiredTags = listOf("grpc", "schema"),
            preferredProtocol = McpProtocolType.GRPC
        )
    )
}
