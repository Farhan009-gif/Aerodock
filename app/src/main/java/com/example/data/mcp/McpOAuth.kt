package com.example.data.mcp

import android.net.Uri
import com.example.data.integrations.YouTubeIntegrationClient
import com.example.data.local.McpServerEntity
import com.example.data.security.TinkSecretVault
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject

/**
 * OAuth 2.0 sign-in for remote MCP servers (MCP authorization spec), for the
 * growing class of servers that refuse static API keys and only accept a
 * Bearer token minted by their own sign-in — Onshape's FeatureScript MCP
 * server is the first one Runway meets.
 *
 * Flow: probe the server (401 + WWW-Authenticate) -> protected-resource
 * metadata (RFC 9728) -> authorization-server metadata (RFC 8414) -> dynamic
 * client registration (RFC 7591) -> authorization code + PKCE in the browser
 * -> redirect to [REDIRECT_URI], received by OAuthCallbackActivity -> code
 * exchange. Tokens live ONLY in the Tink vault (record JSON per server);
 * refresh tokens renew access tokens without user interaction, and
 * [McpClient] asks this manager for a fresh token once when a call meets a
 * 401. All flow state (pending PKCE verifier, last error) is vault-backed so
 * the sign-in survives the app being killed while the browser is open.
 */

data class McpOAuthRecord(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val tokenEndpoint: String,
    val clientId: String,
    val clientSecret: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("access_token", accessToken)
        put("refresh_token", refreshToken)
        put("expires_at_ms", expiresAtMs)
        put("token_endpoint", tokenEndpoint)
        put("client_id", clientId)
        put("client_secret", clientSecret)
    }

    companion object {
        fun fromJson(json: JSONObject): McpOAuthRecord? {
            val access = json.optString("access_token")
            val endpoint = json.optString("token_endpoint")
            val clientId = json.optString("client_id")
            if (access.isBlank() || endpoint.isBlank() || clientId.isBlank()) return null
            return McpOAuthRecord(
                accessToken = access,
                refreshToken = json.optString("refresh_token"),
                expiresAtMs = json.optLong("expires_at_ms", 0L),
                tokenEndpoint = endpoint,
                clientId = clientId,
                clientSecret = json.optString("client_secret")
            )
        }
    }
}

data class McpOAuthPending(
    val serverId: String,
    val state: String,
    val codeVerifier: String,
    val tokenEndpoint: String,
    val clientId: String,
    val clientSecret: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("server_id", serverId)
        put("state", state)
        put("code_verifier", codeVerifier)
        put("token_endpoint", tokenEndpoint)
        put("client_id", clientId)
        put("client_secret", clientSecret)
    }

    companion object {
        fun fromJson(json: JSONObject): McpOAuthPending? {
            val serverId = json.optString("server_id")
            val state = json.optString("state")
            val verifier = json.optString("code_verifier")
            val endpoint = json.optString("token_endpoint")
            val clientId = json.optString("client_id")
            if (serverId.isBlank() || state.isBlank() || verifier.isBlank() ||
                endpoint.isBlank() || clientId.isBlank()
            ) {
                return null
            }
            return McpOAuthPending(
                serverId = serverId,
                state = state,
                codeVerifier = verifier,
                tokenEndpoint = endpoint,
                clientId = clientId,
                clientSecret = json.optString("client_secret")
            )
        }
    }
}

/** Hands the redirect URI from OAuthCallbackActivity to the running app. */
object McpOAuthCallbacks {
    @Volatile
    private var pendingCallback: String? = null

    fun deliver(uri: String) {
        pendingCallback = uri
    }

    fun consume(): String? {
        val value = pendingCallback
        pendingCallback = null
        return value
    }
}

/** Extracts the resource_metadata URL from a WWW-Authenticate Bearer header. */
internal fun parseResourceMetadataUrl(wwwAuthenticate: String?): String? {
    if (wwwAuthenticate.isNullOrBlank()) return null
    val match = Regex("""resource_metadata\s*=\s*"([^"]+)"""").find(wwwAuthenticate) ?: return null
    return match.groupValues[1].trim().ifBlank { null }
}

/**
 * RFC 9728 fallback when the server omits the header: the metadata lives at
 * the origin's well-known path plus the resource path (…/mcp).
 */
internal fun fallbackResourceMetadataUrl(serverUrl: String): String? {
    val uri = runCatching { java.net.URI(serverUrl.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme ?: return null
    val authority = uri.authority ?: return null
    val path = (uri.path ?: "").trimEnd('/')
    return "$scheme://$authority/.well-known/oauth-protected-resource$path"
}

class McpOAuthManager(
    private val okHttpClient: OkHttpClient,
    private val secretVault: TinkSecretVault
) {
    companion object {
        const val REDIRECT_URI = "runway://oauth-callback"
        private const val EXPIRY_SKEW_MS = 60_000L
        private const val SIGN_IN_TIMEOUT_MS = 300_000L
        private const val SIGN_IN_POLL_MS = 800L
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getRecord(serverId: String): McpOAuthRecord? = withContext(Dispatchers.IO) {
        val raw = secretVault.getApiKey(TinkSecretVault.mcpOAuthRecordId(serverId))
        if (raw.isBlank()) {
            null
        } else {
            runCatching { McpOAuthRecord.fromJson(JSONObject(raw)) }.getOrNull()
        }
    }

    private suspend fun saveRecord(serverId: String, record: McpOAuthRecord) {
        secretVault.saveApiKey(
            TinkSecretVault.mcpOAuthRecordId(serverId),
            record.toJson().toString()
        )
    }

    suspend fun disconnect(serverId: String) = withContext(Dispatchers.IO) {
        secretVault.deleteApiKey(TinkSecretVault.mcpOAuthRecordId(serverId))
        secretVault.deleteApiKey(TinkSecretVault.mcpOAuthErrorId(serverId))
        val pending = loadPending()
        if (pending?.serverId == serverId) {
            secretVault.deleteApiKey(TinkSecretVault.mcpOAuthPendingId())
        }
    }

    /** Access token for [serverId], refreshed first when it is about to expire. */
    suspend fun freshAccessToken(serverId: String): String? {
        val record = getRecord(serverId) ?: return null
        if (record.expiresAtMs - System.currentTimeMillis() > EXPIRY_SKEW_MS) {
            return record.accessToken
        }
        return forceRefreshAccessToken(serverId) ?: record.accessToken
    }

    suspend fun forceRefreshAccessToken(serverId: String): String? = withContext(Dispatchers.IO) {
        val record = getRecord(serverId) ?: return@withContext null
        if (record.refreshToken.isBlank()) return@withContext null
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", record.refreshToken)
            .add("client_id", record.clientId)
            .apply {
                if (record.clientSecret.isNotBlank()) add("client_secret", record.clientSecret)
            }
            .build()
        val updated = requestTokens(record.tokenEndpoint, form, record) ?: return@withContext null
        saveRecord(serverId, updated)
        updated.accessToken
    }

    /**
     * Runs the full browser sign-in for [server] and suspends until the
     * redirect lands (handled by [handleCallback]) or the wait times out.
     * [openBrowser] must open the given URL and report success.
     */
    suspend fun signIn(
        server: McpServerEntity,
        openBrowser: (String) -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            secretVault.deleteApiKey(TinkSecretVault.mcpOAuthErrorId(server.id))
            val endpoints = discover(server.url)
            val (clientId, clientSecret) = registerClient(endpoints)
            val codeVerifier = YouTubeIntegrationClient.generateCodeVerifier()
            val codeChallenge = YouTubeIntegrationClient.generateCodeChallengeS256(codeVerifier)
            val state = UUID.randomUUID().toString()
            savePending(
                McpOAuthPending(
                    serverId = server.id,
                    state = state,
                    codeVerifier = codeVerifier,
                    tokenEndpoint = endpoints.tokenEndpoint,
                    clientId = clientId,
                    clientSecret = clientSecret
                )
            )
            val authorizeUrl = Uri.parse(endpoints.authorizationEndpoint).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .apply {
                    if (endpoints.scope.isNotBlank()) {
                        appendQueryParameter("scope", endpoints.scope)
                    }
                }
                .build()
                .toString()
            if (!openBrowser(authorizeUrl)) {
                throw IOException("Could not open the browser for sign-in")
            }
            val deadline = System.currentTimeMillis() + SIGN_IN_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (getRecord(server.id) != null) {
                    return@runCatching "Signed in"
                }
                val error = secretVault.getApiKey(TinkSecretVault.mcpOAuthErrorId(server.id))
                if (error.isNotBlank()) {
                    throw IOException(error)
                }
                if (loadPending() == null) {
                    throw IOException("Sign-in was cancelled")
                }
                delay(SIGN_IN_POLL_MS)
            }
            throw IOException("Sign-in timed out — the browser step never came back")
        }
    }

    /**
     * Completes a sign-in from the redirect [callbackUri]. Safe to call for
     * any URI: anything that is not a matching answer to the pending flow is
     * ignored. Returns true when a token record was saved.
     */
    suspend fun handleCallback(callbackUri: String): Boolean = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(callbackUri) }.getOrNull() ?: return@withContext false
        if (uri.scheme != "runway" || uri.host != "oauth-callback") return@withContext false
        val pending = loadPending() ?: return@withContext false
        val state = uri.getQueryParameter("state").orEmpty()
        if (state.isBlank() || state != pending.state) return@withContext false

        val errorParam = uri.getQueryParameter("error").orEmpty()
        if (errorParam.isNotBlank()) {
            val description = uri.getQueryParameter("error_description").orEmpty()
            saveError(
                pending.serverId,
                "Sign-in was refused: $errorParam" +
                    if (description.isNotBlank()) " — $description" else ""
            )
            clearPending()
            return@withContext false
        }

        val code = uri.getQueryParameter("code").orEmpty()
        if (code.isBlank()) return@withContext false
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", pending.clientId)
            .add("code_verifier", pending.codeVerifier)
            .apply {
                if (pending.clientSecret.isNotBlank()) add("client_secret", pending.clientSecret)
            }
            .build()
        val base = McpOAuthRecord(
            accessToken = "",
            refreshToken = "",
            expiresAtMs = 0L,
            tokenEndpoint = pending.tokenEndpoint,
            clientId = pending.clientId,
            clientSecret = pending.clientSecret
        )
        val record = requestTokens(pending.tokenEndpoint, form, base)
        clearPending()
        if (record == null) {
            saveError(pending.serverId, "The sign-in server rejected the code exchange — try again")
            return@withContext false
        }
        saveRecord(pending.serverId, record)
        true
    }

    private suspend fun loadPending(): McpOAuthPending? {
        val raw = secretVault.getApiKey(TinkSecretVault.mcpOAuthPendingId())
        if (raw.isBlank()) return null
        return runCatching { McpOAuthPending.fromJson(JSONObject(raw)) }.getOrNull()
    }

    private suspend fun savePending(pending: McpOAuthPending) {
        secretVault.saveApiKey(
            TinkSecretVault.mcpOAuthPendingId(),
            pending.toJson().toString()
        )
    }

    private suspend fun clearPending() {
        secretVault.deleteApiKey(TinkSecretVault.mcpOAuthPendingId())
    }

    private suspend fun saveError(serverId: String, message: String) {
        secretVault.saveApiKey(TinkSecretVault.mcpOAuthErrorId(serverId), message)
    }

    /** One token-endpoint call; [base] supplies client identity + refresh fallback. */
    private fun requestTokens(
        tokenEndpoint: String,
        form: FormBody,
        base: McpOAuthRecord
    ): McpOAuthRecord? {
        val request = Request.Builder()
            .url(tokenEndpoint)
            .post(form)
            .header("Accept", "application/json")
            .build()
        val response = runCatching { okHttpClient.newCall(request).execute() }.getOrNull()
            ?: return null
        return response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val access = json.optString("access_token")
            if (access.isBlank()) return null
            val refresh = json.optString("refresh_token").ifBlank { base.refreshToken }
            val expiresInSeconds = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
            McpOAuthRecord(
                accessToken = access,
                refreshToken = refresh,
                expiresAtMs = System.currentTimeMillis() + expiresInSeconds * 1000L,
                tokenEndpoint = tokenEndpoint,
                clientId = base.clientId,
                clientSecret = base.clientSecret
            )
        }
    }

    private data class OAuthEndpoints(
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val registrationEndpoint: String,
        val scope: String
    )

    private fun discover(serverUrl: String): OAuthEndpoints {
        // Probe unauthenticated: OAuth servers answer 401 with the metadata URL.
        var prmUrl: String? = null
        val probePayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "initialize")
            put(
                "params",
                JSONObject().apply {
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
            )
        }
        val probeRequest = Request.Builder()
            .url(serverUrl.trim())
            .post(probePayload.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json, text/event-stream")
            .build()
        runCatching {
            okHttpClient.newCall(probeRequest).execute().use { response ->
                prmUrl = parseResourceMetadataUrl(response.header("WWW-Authenticate"))
            }
        }
        val metadataUrl = prmUrl?.takeIf { it.isNotBlank() }
            ?: fallbackResourceMetadataUrl(serverUrl)
            ?: throw IOException("This server did not advertise OAuth sign-in")
        val prm = getJson(metadataUrl)
            ?: throw IOException("Could not read the server's OAuth metadata")
        val issuer = prm.optJSONArray("authorization_servers")
            ?.optString(0)
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("The server did not name a sign-in server")

        // Least privilege: offer the server's read/write scopes, never delete/PII.
        val scopesArray = prm.optJSONArray("scopes_supported")
        val scope = buildString {
            if (scopesArray != null) {
                for (i in 0 until scopesArray.length()) {
                    val candidate = scopesArray.optString(i)
                    if (candidate.isBlank()) continue
                    if (candidate.contains("delete", ignoreCase = true)) continue
                    if (candidate.contains("pii", ignoreCase = true)) continue
                    if (isNotEmpty()) append(' ')
                    append(candidate)
                }
            }
        }

        val issuerBase = issuer.trimEnd('/')
        val asMetadata = getJson("$issuerBase/.well-known/oauth-authorization-server")
            ?: getJson("$issuerBase/.well-known/openid-configuration")
            ?: throw IOException("Could not read the sign-in server's metadata")
        val authorizationEndpoint = asMetadata.optString("authorization_endpoint")
        val tokenEndpoint = asMetadata.optString("token_endpoint")
        if (authorizationEndpoint.isBlank() || tokenEndpoint.isBlank()) {
            throw IOException("The sign-in server's metadata is incomplete")
        }
        return OAuthEndpoints(
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            registrationEndpoint = asMetadata.optString("registration_endpoint"),
            scope = scope
        )
    }

    private fun registerClient(endpoints: OAuthEndpoints): Pair<String, String> {
        if (endpoints.registrationEndpoint.isBlank()) {
            throw IOException("This sign-in server does not support automatic app registration")
        }
        val payload = JSONObject().apply {
            put("client_name", "Runway")
            put("redirect_uris", JSONArray().put(REDIRECT_URI))
            put("grant_types", JSONArray().put("authorization_code").put("refresh_token"))
            put("response_types", JSONArray().put("code"))
            put("token_endpoint_auth_method", "none")
        }
        val request = Request.Builder()
            .url(endpoints.registrationEndpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()
        val response = runCatching { okHttpClient.newCall(request).execute() }.getOrNull()
            ?: throw IOException("App registration failed — no response")
        return response.use {
            if (!it.isSuccessful) {
                throw IOException("App registration was refused (HTTP ${it.code})")
            }
            val json = runCatching { JSONObject(it.body?.string().orEmpty()) }.getOrNull()
                ?: throw IOException("App registration returned an unreadable answer")
            val clientId = json.optString("client_id")
            if (clientId.isBlank()) {
                throw IOException("App registration did not return a client ID")
            }
            clientId to json.optString("client_secret")
        }
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                }
            }
        }.getOrNull()
    }
}
