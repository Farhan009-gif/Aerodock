package com.example.data.provider

import com.example.data.security.CleartextHostNotApprovedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.ArrayDeque
import javax.net.ssl.SSLException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * 1. ERROR TAXONOMY
 * Explicitly classifies provider failures and defines strict retryability rules.
 *
 * Retryability contract:
 * - [Transient] (502/503/504, socket timeout, connection reset): RETRYABLE (true)
 * - [RateLimited] (429 Too Many Requests): RETRYABLE (true, honors Retry-After)
 * - [Auth] (401/403 invalid/missing key): NEVER RETRY (false)
 * - [Route] (404 Not Found, 405 Method Not Allowed, wrong path/method): NEVER RETRY (false)
 * - [QuotaExceeded] (402 / insufficient_quota): NEVER RETRY (false)
 * - [CleartextBlocked] (unapproved HTTP LAN host): NEVER RETRY (false)
 * - [Unknown] (400 Bad Request, schema/validation error): NEVER RETRY (false)
 */
sealed class ProviderError(
    open val message: String,
    open val httpStatus: Int? = null,
    val isRetryable: Boolean
) {
    data class Auth(
        override val message: String,
        override val httpStatus: Int? = 401,
        val providerName: String = ""
    ) : ProviderError(message = message, httpStatus = httpStatus, isRetryable = false)

    data class Route(
        override val message: String,
        override val httpStatus: Int? = 405,
        val attemptedMethod: String = "POST",
        val attemptedUrl: String = "",
        val correctedUrlHint: String = ""
    ) : ProviderError(message = message, httpStatus = httpStatus, isRetryable = false)

    data class RateLimited(
        override val message: String,
        val retryAfterMs: Long? = null,
        override val httpStatus: Int? = 429
    ) : ProviderError(message = message, httpStatus = httpStatus, isRetryable = true)

    data class Transient(
        override val message: String,
        override val httpStatus: Int? = null,
        val causeClass: String = "IOException"
    ) : ProviderError(message = message, httpStatus = httpStatus, isRetryable = true)

    data class QuotaExceeded(
        override val message: String,
        override val httpStatus: Int? = 402
    ) : ProviderError(message = message, httpStatus = httpStatus, isRetryable = false)

    data class CleartextBlocked(
        val host: String,
        override val message: String
    ) : ProviderError(message = message, httpStatus = null, isRetryable = false)

    data class Unknown(
        override val message: String,
        override val httpStatus: Int? = null
    ) : ProviderError(message = message, httpStatus = httpStatus, isRetryable = false)

    fun fingerprint(providerId: String): String =
        "$providerId:${this::class.simpleName}:${httpStatus ?: 0}:${message.take(64)}"

    val signatureKey: String
        get() = "${this::class.simpleName}:${httpStatus ?: 0}:${message.take(64)}"

    val badgeLabel: String
        get() = when (this) {
            is Auth -> "AUTH ${httpStatus ?: 401}"
            is Route -> "ROUTE ${httpStatus ?: 405}"
            is RateLimited -> "RATE LIMIT 429"
            is Transient -> "TRANSIENT"
            is QuotaExceeded -> "QUOTA 402"
            is CleartextBlocked -> "HTTP BLOCKED"
            is Unknown -> "ERROR${httpStatus?.let { " $it" }.orEmpty()}"
        }

    val title: String
        get() = when (this) {
            is Auth -> "Authentication Rejected (${httpStatus ?: 401})"
            is Route -> "Endpoint Route / Model Error (${httpStatus ?: 405})"
            is RateLimited -> "Rate Limited (HTTP 429)"
            is Transient -> if (EndpointUrlNormalizer.isOmniRouteUpstreamModelError(message) || httpStatus in listOf(502, 503, 504)) {
                "Upstream Model Failed (HTTP ${httpStatus ?: 502})"
            } else {
                "Endpoint Unreachable / Timed Out"
            }
            is QuotaExceeded -> "Quota or Credits Exhausted"
            is CleartextBlocked -> "Cleartext HTTP Host Blocked ($host)"
            is Unknown -> "Provider Request Failed"
        }

    val suggestedFix: String
        get() = when (this) {
            is Auth -> "Open Keys -> verify your API key -> tap Test Connection."
            is Route -> correctedUrlHint.ifBlank {
                "Ensure Base URL ends with /v1 (e.g. http://<host>:20128/v1) so POST hits /v1/chat/completions."
            }
            is RateLimited -> if (retryAfterMs != null) {
                "Server requested retry after ${retryAfterMs / 1000}s. Try again shortly or switch providers."
            } else {
                "Wait a moment and retry, or switch to another provider/model."
            }
            is Transient -> if (EndpointUrlNormalizer.isOmniRouteUpstreamModelError(message) || httpStatus in listOf(502, 503)) {
                "Your OmniRoute server is reachable, but this specific model's upstream provider closed the stream or is not configured in OmniRoute. Tap 'Switch to Working Model' below or choose a provider you configured in OmniRoute (e.g. Gemini, Groq, DeepSeek, OpenAI)."
            } else {
                "Verify the host is reachable on your network/Tailscale or use your PC's LAN IP instead of localhost."
            }
            is QuotaExceeded -> "Switch to another provider or top up your account billing balance."
            is CleartextBlocked -> "Tap 'Approve Host' to allow cleartext HTTP traffic to $host."
            is Unknown -> "Inspect the Network Diagnostics Ring-Buffer in Keys for the exact HTTP status and URL."
        }
}

class ProviderErrorException(
    val providerError: ProviderError,
    val normalizedError: NormalizedAiError
) : IOException(providerError.message)

/**
 * 5. SECURITY REDACTOR
 * Scrubs Authorization headers, Bearer tokens, sk-/AIza/ghp_ keys, and sensitive query parameters
 * before any URL, header, or error body is stored in Room, UI state, or diagnostic logs.
 */
object SecretRedactor {
    private val bearerRegex = Regex("""(?i)(Bearer\s+)[A-Za-z0-9._\-+/=]{6,}""")
    private val knownKeyPrefixRegex = Regex("""\b(sk-[A-Za-z0-9_\-]{6,}|AIza[0-9A-Za-z_\-]{10,}|ghp_[0-9A-Za-z]{10,}|xox[baprs]-[0-9A-Za-z\-]{10,})""")
    private val queryParamSecretRegex = Regex("""(?i)([?&](?:api_?key|key|token|access_token|secret|password|auth)=)([^&#\s]+)""")
    private val jsonSecretFieldRegex = Regex("""(?i)("(?:api_?key|access_token|refresh_token|secret|authorization)"\s*:\s*")([^"]+)(")""")

    fun redactUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return rawUrl.replace(queryParamSecretRegex, "$1[REDACTED]")
    }

    fun redactText(raw: String): String {
        if (raw.isBlank()) return raw
        return raw
            .replace(bearerRegex, "$1[REDACTED]")
            .replace(knownKeyPrefixRegex, "[REDACTED_KEY]")
            .replace(queryParamSecretRegex, "$1[REDACTED]")
            .replace(jsonSecretFieldRegex, "$1[REDACTED]$3")
    }
}

/**
 * 4. URL NORMALIZER & 405 FIXER
 * Prevents HTTP 405 Method Not Allowed and 404 Not Found caused by:
 * - Missing `/v1` on OmniRoute / OpenAI-compatible gateway root URLs (hitting the web UI router instead of `/v1/chat/completions`)
 * - User pasting `/v1/models` or `/chat/completions` directly into the Base URL field
 * - Missing `http://` or `https://` scheme
 */
object EndpointUrlNormalizer {

    fun isLikelyLocalOrLanHost(hostOrUrl: String): Boolean {
        val clean = hostOrUrl.lowercase().trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":")
        return clean == "localhost" ||
            clean == "127.0.0.1" ||
            clean == "10.0.2.2" ||
            clean.startsWith("192.168.") ||
            clean.startsWith("10.") ||
            clean.startsWith("100.") ||
            clean.endsWith(".local") ||
            clean.endsWith(".lan") ||
            clean.endsWith(".tailnet") ||
            clean.contains("tailscale") ||
            Regex("""^172\.(1[6-9]|2[0-9]|3[0-1])\.""").containsMatchIn(clean)
    }

    /**
     * Normalizes a user-configured base URL into a clean root base URL with `/v1` when appropriate.
     */
    fun normalizeOpenAiBaseUrl(rawBaseUrl: String): String {
        var url = rawBaseUrl.trim()
        if (url.isEmpty()) return "https://api.openai.com/v1"

        // Add scheme if missing
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = if (isLikelyLocalOrLanHost(url)) "http://$url" else "https://$url"
        }

        url = url.trimEnd('/')

        // Strip endpoint suffixes if the user accidentally pasted a full endpoint path into Base URL
        val suffixesToStrip = listOf(
            "/v1/chat/completions",
            "/chat/completions",
            "/v1/models",
            "/models",
            "/v1/images/generations",
            "/images/generations",
            "/v1/videos/generations",
            "/videos/generations"
        )
        for (suffix in suffixesToStrip) {
            if (url.endsWith(suffix, ignoreCase = true)) {
                url = url.dropLast(suffix.length).trimEnd('/')
                if (suffix.startsWith("/v1", ignoreCase = true)) {
                    url = "$url/v1"
                }
                break
            }
        }

        // If URL has no version segment at all (e.g. http://192.168.1.100:20128), append /v1 as primary
        val pathPart = url.substringAfter("://", "").substringAfter("/", "")
        if (pathPart.isEmpty()) {
            url = "$url/v1"
        }
        return url
    }

    fun normalizeOpenAiCompatibleBaseUrl(rawBaseUrl: String): String =
        normalizeOpenAiBaseUrl(rawBaseUrl)

    /**
     * Returns candidate URLs for `/v1/chat/completions` in priority order so that whether a gateway
     * mounts routes at `/v1/chat/completions` or `/chat/completions`, the first or fallback path succeeds.
     */
    fun buildChatCompletionCandidateUrls(rawBaseUrl: String): List<String> {
        val normalized = normalizeOpenAiBaseUrl(rawBaseUrl)
        val candidates = mutableListOf<String>()
        candidates.add("$normalized/chat/completions")

        val withoutV1 = normalized.removeSuffix("/v1").trimEnd('/')
        if (withoutV1.isNotEmpty() && withoutV1 != normalized) {
            candidates.add("$withoutV1/v1/chat/completions")
            candidates.add("$withoutV1/chat/completions")
        } else {
            candidates.add("$normalized/v1/chat/completions")
        }
        return candidates.distinct()
    }

    /**
     * Returns candidate URLs for `/v1/models` discovery across OpenAI, OmniRoute, and Ollama-compatible servers.
     */
    fun buildModelsCandidateUrls(rawBaseUrl: String): List<String> {
        val normalized = normalizeOpenAiBaseUrl(rawBaseUrl)
        val candidates = mutableListOf<String>()
        candidates.add("$normalized/models")

        val withoutV1 = normalized.removeSuffix("/v1").trimEnd('/')
        if (withoutV1.isNotEmpty() && withoutV1 != normalized) {
            candidates.add("$withoutV1/v1/models")
            candidates.add("$withoutV1/models")
            candidates.add("$withoutV1/api/models")
            candidates.add("$withoutV1/api/tags")
        } else {
            candidates.add("$normalized/v1/models")
        }
        return candidates.distinct()
    }

    /**
     * Returns candidate URLs for `/v1/videos/generations` across OmniRoute and OpenAI-compatible video gateways.
     */
    fun buildVideoGenerationCandidateUrls(rawBaseUrl: String): List<String> {
        val normalized = normalizeOpenAiBaseUrl(rawBaseUrl)
        val candidates = mutableListOf<String>()
        candidates.add("$normalized/videos/generations")

        val withoutV1 = normalized.removeSuffix("/v1").trimEnd('/')
        if (withoutV1.isNotEmpty() && withoutV1 != normalized) {
            candidates.add("$withoutV1/v1/videos/generations")
            candidates.add("$withoutV1/videos/generations")
            candidates.add("$withoutV1/v1/video/generations")
        } else {
            candidates.add("$normalized/v1/videos/generations")
            candidates.add("$normalized/video/generations")
        }
        return candidates.distinct()
    }

    private val scraperOrIdePrefixes = listOf(
        "cfp/",
        "aug/",
        "dva/",
        "aihorde/",
        "cu/",
        "cursor/",
        "ws/",
        "windsurf/",
        "qoder/",
        "blackbox/",
        "bb/",
        "gh/",
        "copilot/",
        "gc/",
        "kr/",
        "kiro/",
        "if/",
        "iflow/",
        "cc/",
        "claude-code/",
        "cx/",
        "codex/"
    )

    private val zeroConfigWorkingPrefixes = listOf(
        "ddgw/",
        "duckduckgo/"
    )

    private val preferredChatKeywords = listOf(
        "gemini",
        "gpt-4",
        "gpt-3.5",
        "o1",
        "o3",
        "deepseek",
        "claude",
        "llama",
        "qwen",
        "mistral",
        "mixtral",
        "gemma",
        "command",
        "phi-",
        "nemotron",
        "codestral",
        "pixtral",
        "glm",
        "minimax",
        "kimi",
        "groq/",
        "openai/",
        "openrouter/",
        "pollinations/",
        "together/",
        "cerebras/",
        "sambanova/",
        "fireworks/",
        "nvidia/"
    )

    val defaultWorkingOmniRouteModels: List<Pair<String, String>> = listOf(
        "ddgw/gpt-4o-mini" to "DDGW • GPT-4o Mini (Zero-Config)",
        "ddgw/claude-3-haiku" to "DDGW • Claude 3 Haiku (Zero-Config)",
        "ddgw/llama-3.3-70b" to "DDGW • Llama 3.3 70B (Zero-Config)",
        "ddgw/o3-mini" to "DDGW • o3-mini (Zero-Config)",
        "ddgw/mistral-small" to "DDGW • Mistral Small (Zero-Config)"
    )

    val defaultOmniRouteVideoModels: List<Pair<String, String>> = listOf(
        "omniroute/video-combo" to "OmniRoute • Video Router Combo (Wan 2.1 → Kling 1.6 → Luma Ray 2)",
        "omniroute/cinema-director" to "OmniRoute • Cinema Director Combo (Luma Ray 2 → Veo 2 → Gen-4)",
        "omniroute/wan-2.1-t2v" to "OmniRoute • Wan 2.1 Text-to-Video (720p/1080p)",
        "omniroute/kling-v1.6-pro" to "OmniRoute • Kling 1.6 Pro Video",
        "omniroute/luma-ray-2" to "OmniRoute • Luma Ray 2 Cinema",
        "omniroute/veo-2-cinema" to "OmniRoute • Google Veo 2 Cinema",
        "omniroute/hunyuan-video" to "OmniRoute • HunyuanVideo Open-Weights",
        "omniroute/seedance-v1" to "OmniRoute • Seedance 1.0 Fast Motion",
        "omniroute/gen4-turbo" to "OmniRoute • Runway Gen-4 Turbo"
    )

    val defaultOmniRouteImageModels: List<Pair<String, String>> = listOf(
        "omniroute/flux-combo" to "OmniRoute • Flux Image Router Combo",
        "omniroute/flux-schnell" to "OmniRoute • Flux.1 Schnell (Zero-Config)",
        "omniroute/sdxl-lightning" to "OmniRoute • SDXL Lightning (Zero-Config)"
    )

    val defaultOmniRouteChatCombos: List<Pair<String, String>> = listOf(
        "omniroute/auto-combo" to "OmniRoute • Smart Chat Router (GPT-4o Mini → Claude 3 Haiku → Llama 3.3)",
        "omniroute/coding-fallback" to "OmniRoute • Coding Fallback Combo (o3-mini → GPT-4o Mini → Llama 3.3)",
        "omniroute/vision-pro" to "OmniRoute • Vision Multimodal Combo"
    )

    fun isBrowserScrapedOrOAuthIdeModel(modelId: String): Boolean {
        val lower = modelId.lowercase().trim()
        return scraperOrIdePrefixes.any { lower.startsWith(it) } || lower.contains("cloudflare")
    }

    /**
     * Ranks models so zero-config working OmniRoute models (`ddgw/`) appear first (-1),
     * OmniRoute custom combos & direct LLM/video models appear next (0), other standard models third (1),
     * `auto/` combos fourth (2), and scraper/CLI/OAuth/image-only sub-providers (`cfp/`, `aug/`, `dva/`, `cu/`, `ws/`, `gc/`, `aihorde/`) last (3).
     */
    fun modelPriorityRank(modelId: String, isCombo: Boolean = false): Int {
        val lower = modelId.lowercase().trim()
        return when {
            zeroConfigWorkingPrefixes.any { lower.startsWith(it) } -> -1
            isBrowserScrapedOrOAuthIdeModel(lower) -> 3
            lower.startsWith("omniroute/") || isCombo -> 0
            lower.startsWith("auto/") -> 2
            preferredChatKeywords.any { lower.contains(it) } -> 0
            else -> 1
        }
    }

    fun isOmniRouteUpstreamModelError(rawMessage: String): Boolean {
        val lower = rawMessage.lowercase()
        return lower.contains("playwright") ||
            lower.contains("cloudflare playground") ||
            lower.contains("chromium browser") ||
            lower.contains("cfp/") ||
            lower.contains("stream ended before producing") ||
            lower.contains("non-ping sse event") ||
            lower.contains("provider — [") ||
            lower.contains("all providers failed") ||
            lower.contains("no available provider") ||
            lower.contains("temporary gateway/upstream error")
    }

    fun isPlaywrightOrAutoComboError(rawMessage: String): Boolean =
        isOmniRouteUpstreamModelError(rawMessage)
}

/**
 * Maps HTTP responses and exceptions to [ProviderError] and [NormalizedAiError].
 */
object ProviderErrorMapper {

    fun parseRetryAfterMs(headers: Headers?, responseBody: String): Long? {
        val headerVal = headers?.get("Retry-After")?.trim()
            ?: headers?.get("retry-after")?.trim()
            ?: headers?.get("x-ratelimit-reset-requests")?.trim()
        if (!headerVal.isNullOrEmpty()) {
            headerVal.toLongOrNull()?.let { seconds ->
                return (seconds * 1000L).coerceIn(500L, 60_000L)
            }
        }
        return runCatching {
            val root = JSONObject(responseBody)
            val errObj = root.optJSONObject("error") ?: root
            val retrySec = errObj.optDouble("retry_after", Double.NaN)
            if (!retrySec.isNaN() && retrySec > 0) {
                (retrySec * 1000.0).toLong().coerceIn(500L, 60_000L)
            } else null
        }.getOrNull()
    }

    private fun extractCleanMessage(redactedBody: String): String {
        val trimmed = redactedBody.trim()
        if (trimmed.startsWith("{")) {
            runCatching {
                val json = JSONObject(trimmed)
                val nestedErr = json.optJSONObject("error")
                if (nestedErr != null) {
                    val m = nestedErr.optString("message").trim()
                    if (m.isNotEmpty()) return m
                }
                val topMsg = json.optString("message").trim()
                if (topMsg.isNotEmpty()) return topMsg
            }
        }
        return trimmed
    }

    fun fromHttpResponse(
        statusCode: Int,
        responseBody: String,
        headers: Headers? = null,
        providerName: String,
        attemptedMethod: String = "POST",
        attemptedUrl: String = ""
    ): ProviderError {
        val redactedBody = SecretRedactor.redactText(responseBody)
        val cleanMsg = extractCleanMessage(redactedBody)
        val redactedUrl = SecretRedactor.redactUrl(attemptedUrl)
        val lower = cleanMsg.lowercase()

        return when {
            lower.contains("playwright") || lower.contains("cloudflare playground") || lower.contains("chromium browser") -> {
                ProviderError.Route(
                    message = cleanMsg,
                    httpStatus = statusCode,
                    attemptedMethod = attemptedMethod,
                    attemptedUrl = redactedUrl,
                    correctedUrlHint = "Your OmniRoute combo routed to 'cfp/*' (Cloudflare Playground), which requires Playwright + Chromium on the server. Tap 'Switch to Working Model' below or disable Cloudflare Playground in OmniRoute."
                )
            }
            lower.contains("rate limit") || lower.contains("rate_limit") ||
                lower.contains("too many requests") -> {
                // Rate-limit wording beats the status code: some providers (e.g.
                // LiteRouter) answer a cooldown with HTTP 403, which is not an
                // auth failure and must stay retryable.
                val retryMs = parseRetryAfterMs(headers, redactedBody)
                ProviderError.RateLimited(
                    message = cleanMsg.ifBlank { "$providerName rate limit reached (HTTP $statusCode)." },
                    retryAfterMs = retryMs,
                    httpStatus = statusCode
                )
            }
            statusCode == 401 || statusCode == 403 ||
                lower.contains("invalid_api_key") || lower.contains("incorrect api key") ||
                lower.contains("unauthorized") -> {
                ProviderError.Auth(
                    message = cleanMsg.ifBlank { "HTTP $statusCode" },
                    httpStatus = statusCode,
                    providerName = providerName
                )
            }
            statusCode == 404 -> {
                ProviderError.Route(
                    message = cleanMsg.ifBlank { "HTTP 404 on $attemptedMethod $redactedUrl" },
                    httpStatus = 404,
                    attemptedMethod = attemptedMethod,
                    attemptedUrl = redactedUrl,
                    correctedUrlHint = "Pick another model."
                )
            }
            statusCode == 405 -> {
                val hint = if (!redactedUrl.contains("/v1/")) {
                    "Ensure Base URL ends with /v1 (e.g. http://<host>:20128/v1) so POST hits /v1/chat/completions instead of the web root."
                } else {
                    "Verify the endpoint supports $attemptedMethod on $redactedUrl and does not redirect HTTP POST to HTTPS GET."
                }
                ProviderError.Route(
                    message = "$providerName returned HTTP $statusCode on $attemptedMethod $redactedUrl. $hint",
                    httpStatus = statusCode,
                    attemptedMethod = attemptedMethod,
                    attemptedUrl = redactedUrl,
                    correctedUrlHint = hint
                )
            }
            statusCode == 402 || lower.contains("insufficient_quota") || lower.contains("credit balance") -> {
                ProviderError.QuotaExceeded(
                    message = "$providerName reported insufficient quota or billing credit (HTTP $statusCode).",
                    httpStatus = statusCode
                )
            }
            statusCode == 413 || ErrorNormalizer.hasTpmOrRpmIndicator(redactedBody) -> {
                val retryMs = parseRetryAfterMs(headers, redactedBody)
                ProviderError.RateLimited(
                    message = cleanMsg.ifBlank { "Rate-limited ($statusCode)" },
                    retryAfterMs = retryMs,
                    httpStatus = statusCode
                )
            }
            statusCode == 429 || lower.contains("rate_limit") || lower.contains("too many requests") -> {
                val retryMs = parseRetryAfterMs(headers, redactedBody)
                ProviderError.RateLimited(
                    message = cleanMsg.ifBlank { "$providerName rate limit reached (HTTP 429)." },
                    retryAfterMs = retryMs,
                    httpStatus = 429
                )
            }
            statusCode in listOf(408, 500, 502, 503, 504, 522, 524) -> {
                ProviderError.Transient(
                    message = "$providerName temporary gateway/upstream error (HTTP $statusCode): ${cleanMsg.take(180)}",
                    httpStatus = statusCode,
                    causeClass = "HTTP_$statusCode"
                )
            }
            else -> {
                val snippet = cleanMsg.take(180).ifBlank { "HTTP $statusCode" }
                ProviderError.Unknown(
                    message = "$providerName error (HTTP $statusCode): $snippet",
                    httpStatus = statusCode
                )
            }
        }
    }

    fun fromThrowable(
        throwable: Throwable,
        providerName: String,
        baseUrl: String
    ): ProviderError {
        if (throwable is ProviderErrorException) return throwable.providerError
        if (throwable is ProviderNormalizedException) {
            val norm = throwable.normalizedError
            return fromNormalizedError(norm, baseUrl)
        }
        return when (throwable) {
            is CleartextHostNotApprovedException -> ProviderError.CleartextBlocked(
                host = throwable.host,
                message = throwable.message ?: "Unapproved HTTP host ${throwable.host}"
            )
            is SocketTimeoutException, is ConnectException, is UnknownHostException, is EOFException -> {
                ProviderError.Transient(
                    message = "Cannot reach $providerName (${SecretRedactor.redactUrl(baseUrl)}): ${throwable.message ?: "Connection timed out"}",
                    httpStatus = null,
                    causeClass = throwable::class.simpleName ?: "IOException"
                )
            }
            is SSLException -> ProviderError.Unknown(
                message = "TLS/SSL handshake failed for $providerName: ${throwable.message}"
            )
            else -> ProviderError.Unknown(
                message = SecretRedactor.redactText(throwable.message ?: "Unexpected error from $providerName")
            )
        }
    }

    fun fromNormalizedError(
        norm: NormalizedAiError,
        baseUrl: String = ""
    ): ProviderError {
        val lower = (norm.title + " " + norm.message + " " + norm.suggestedFix + " " + norm.details).lowercase()
        val inferredStatus = when {
            lower.contains("500") -> 500
            lower.contains("502") -> 502
            lower.contains("503") -> 503
            lower.contains("504") -> 504
            lower.contains("413") -> 413
            lower.contains("404") -> 404
            lower.contains("403") -> 403
            lower.contains("401") -> 401
            else -> null
        }
        return when {
            norm.category == ErrorCategory.RATE_LIMIT || lower.contains("rate limit") ->
                ProviderError.RateLimited(message = norm.details.ifBlank { norm.message }, httpStatus = inferredStatus ?: 429)
            norm.category == ErrorCategory.INVALID_KEY || lower.contains("401") || lower.contains("403") ->
                ProviderError.Auth(message = norm.details.ifBlank { norm.message }, httpStatus = inferredStatus ?: 401)
            lower.contains("405") || lower.contains("404") || lower.contains("method not allowed") ||
                lower.contains("playwright") || lower.contains("cloudflare playground") ->
                ProviderError.Route(
                    message = norm.details.ifBlank { norm.message },
                    httpStatus = inferredStatus ?: if (lower.contains("404")) 404 else 405,
                    attemptedUrl = SecretRedactor.redactUrl(baseUrl),
                    correctedUrlHint = norm.suggestedFix
                )
            norm.category == ErrorCategory.RATE_LIMIT || lower.contains("429") || lower.contains("413") ->
                ProviderError.RateLimited(message = norm.details.ifBlank { norm.message }, httpStatus = inferredStatus ?: 429)
            norm.category == ErrorCategory.QUOTA_EXCEEDED || lower.contains("402") ->
                ProviderError.QuotaExceeded(message = norm.details.ifBlank { norm.message }, httpStatus = 402)
            norm.category == ErrorCategory.CLEARTEXT_UNAPPROVED ->
                ProviderError.CleartextBlocked(host = norm.unapprovedHost.orEmpty(), message = norm.details.ifBlank { norm.message })
            norm.category == ErrorCategory.NETWORK_UNREACHABLE || inferredStatus in listOf(500, 502, 503, 504) ->
                ProviderError.Transient(message = norm.details.ifBlank { norm.message }, httpStatus = inferredStatus)
            else -> ProviderError.Unknown(message = norm.details.ifBlank { norm.message }, httpStatus = inferredStatus)
        }
    }

    fun toNormalizedAiError(
        error: ProviderError,
        providerName: String,
        baseUrl: String = ""
    ): NormalizedAiError {
        val redactedUrl = SecretRedactor.redactUrl(baseUrl)
        val detailsLine = buildString {
            error.httpStatus?.let { append("HTTP $it • ") }
            append(providerName)
            if (redactedUrl.isNotBlank()) append(" • $redactedUrl")
            if (error.message.isNotBlank()) append(" • ${SecretRedactor.redactText(error.message).take(240)}")
        }
        return when (error) {
            is ProviderError.Auth -> NormalizedAiError(
                category = ErrorCategory.INVALID_KEY,
                title = "Authentication Failed (${error.httpStatus ?: 401})",
                message = "Your API key was rejected.",
                suggestedFix = "Check the key in Providers.",
                details = detailsLine
            )
            is ProviderError.Route -> {
                if (error.httpStatus == 404 && !EndpointUrlNormalizer.isOmniRouteUpstreamModelError(error.message)) {
                    NormalizedAiError(
                        category = ErrorCategory.SERVER_ERROR,
                        title = "Model Not Available (404)",
                        message = "This model isn't available on this provider.",
                        suggestedFix = "Pick another model.",
                        details = detailsLine
                    )
                } else {
                    NormalizedAiError(
                        category = ErrorCategory.SERVER_ERROR,
                        title = "Route / Model Error (HTTP ${error.httpStatus ?: 405})",
                        message = error.message,
                        suggestedFix = error.correctedUrlHint.ifBlank {
                            "Ensure Base URL ends with /v1 (e.g. http://192.168.1.x:20128/v1) and uses the correct scheme."
                        },
                        details = detailsLine
                    )
                }
            }
            is ProviderError.RateLimited -> {
                val isTpmRpmOr413 = error.httpStatus == 413 || ErrorNormalizer.hasTpmOrRpmIndicator(error.message)
                if (isTpmRpmOr413) {
                    NormalizedAiError(
                        category = ErrorCategory.RATE_LIMIT,
                        title = "Rate Limited (${error.httpStatus ?: 413})",
                        message = "The provider is rate-limited right now.",
                        suggestedFix = "Wait or switch models.",
                        details = detailsLine
                    )
                } else {
                    NormalizedAiError(
                        category = ErrorCategory.RATE_LIMIT,
                        title = "Too Many Requests (429)",
                        message = "Too many requests.",
                        suggestedFix = "Wait or switch providers.",
                        details = detailsLine
                    )
                }
            }
            is ProviderError.Transient -> {
                val isServer5xx = error.httpStatus in listOf(500, 502, 503, 504)
                val isOmniPlaywright = EndpointUrlNormalizer.isOmniRouteUpstreamModelError(error.message)
                when {
                    isOmniPlaywright -> {
                        NormalizedAiError(
                            category = ErrorCategory.SERVER_ERROR,
                            title = "Upstream Model Failed (HTTP ${error.httpStatus ?: 502})",
                            message = error.message,
                            suggestedFix = "Retry or switch providers.",
                            details = detailsLine
                        )
                    }
                    isServer5xx -> {
                        NormalizedAiError(
                            category = ErrorCategory.SERVER_ERROR,
                            title = "Provider Trouble (${error.httpStatus})",
                            message = "The provider is having trouble.",
                            suggestedFix = "Retry or switch providers.",
                            details = detailsLine
                        )
                    }
                    else -> {
                        NormalizedAiError(
                            category = ErrorCategory.NETWORK_UNREACHABLE,
                            title = "Connection Timed Out",
                            message = "No connection to the provider.",
                            suggestedFix = "Check your network or gateway address.",
                            details = detailsLine
                        )
                    }
                }
            }
            is ProviderError.QuotaExceeded -> NormalizedAiError(
                category = ErrorCategory.QUOTA_EXCEEDED,
                title = "Quota or Credits Exhausted",
                message = error.message,
                suggestedFix = "Switch to another provider or top up your $providerName billing balance.",
                details = detailsLine
            )
            is ProviderError.CleartextBlocked -> NormalizedAiError(
                category = ErrorCategory.CLEARTEXT_UNAPPROVED,
                title = "Cleartext HTTP Blocked",
                message = "Plain HTTP to this address isn't allowed.",
                suggestedFix = "Approve the host in Settings.",
                unapprovedHost = error.host,
                details = detailsLine
            )
            is ProviderError.Unknown -> {
                if (error.httpStatus in listOf(500, 502, 503, 504)) {
                    NormalizedAiError(
                        category = ErrorCategory.SERVER_ERROR,
                        title = "Provider Trouble (${error.httpStatus})",
                        message = "The provider is having trouble.",
                        suggestedFix = "Retry or switch providers.",
                        details = detailsLine
                    )
                } else {
                    NormalizedAiError(
                        category = ErrorCategory.SERVER_ERROR,
                        title = "$providerName Error${error.httpStatus?.let { " ($it)" }.orEmpty()}",
                        message = error.message,
                        suggestedFix = "Check model compatibility and inspect the Request Diagnostics log in Keys.",
                        details = detailsLine
                    )
                }
            }
        }
    }
}

/**
 * 2. RETRY WITH EXPONENTIAL BACKOFF + FULL JITTER
 * - Runs strictly on [Dispatchers.IO].
 * - Retries ONLY when [ProviderError.isRetryable] is true ([ProviderError.Transient] or [ProviderError.RateLimited]).
 * - Honors server `retry_after` when present on [ProviderError.RateLimited].
 * - Never retries [ProviderError.Auth] (401/403) or [ProviderError.Route] (404/405).
 */
object RetryPolicy {
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 450L,
        maxDelayMs: Long = 6_000L,
        providerName: String = "Provider",
        baseUrl: String = "",
        block: suspend (attempt: Int) -> T
    ): T = withContext(Dispatchers.IO) {
        var lastThrowable: Throwable? = null
        val totalAttempts = maxAttempts.coerceAtLeast(1)
        for (attempt in 1..totalAttempts) {
            try {
                return@withContext block(attempt)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastThrowable = t
                val mappedError = ProviderErrorMapper.fromThrowable(t, providerName, baseUrl)
                val canRetry = mappedError.isRetryable && attempt < totalAttempts
                if (!canRetry) {
                    throw t
                }
                val computedBackoff = (initialDelayMs * 2.0.pow((attempt - 1).toDouble())).toLong()
                    .coerceAtMost(maxDelayMs)
                val jitteredBackoff = Random.nextLong(
                    from = (computedBackoff / 2).coerceAtLeast(150L),
                    until = computedBackoff.coerceAtLeast(300L) + 1L
                )
                val waitMs = when (mappedError) {
                    is ProviderError.RateLimited -> {
                        val serverWait = mappedError.retryAfterMs ?: jitteredBackoff
                        min(serverWait, maxDelayMs)
                    }
                    else -> jitteredBackoff
                }
                delay(waitMs)
            }
        }
        throw (lastThrowable ?: IOException("Retry attempts exhausted for $providerName"))
    }
}

/**
 * 3. PROVIDER HEALTH STATE MACHINE & DEDUPLICATED ERROR BANNER STATE
 */
sealed class ProviderHealth {
    data object Unknown : ProviderHealth()
    data object Checking : ProviderHealth()
    data class Healthy(
        val latencyMs: Long,
        val modelCount: Int = 0,
        val checkedAtMs: Long = System.currentTimeMillis()
    ) : ProviderHealth()

    data class Unhealthy(
        val error: ProviderError,
        val normalizedError: NormalizedAiError? = null,
        val consecutiveFailures: Int = 1,
        val lastFailedAtMs: Long = System.currentTimeMillis()
    ) : ProviderHealth()
}

data class DeduplicatedErrorState(
    val fingerprint: String = "",
    val providerId: String,
    val providerName: String,
    val error: ProviderError,
    val normalizedError: NormalizedAiError? = null,
    val occurrenceCount: Int = 1,
    val firstSeenAtMs: Long = System.currentTimeMillis(),
    val lastSeenAtMs: Long = System.currentTimeMillis()
)

/**
 * 7. DIAGNOSTICS — RING-BUFFER REQUEST LOG
 * Thread-safe bounded ring buffer (max 100 entries) storing redacted HTTP diagnostics.
 */
data class RequestDiagnosticEntry(
    val id: Long,
    val timestampMs: Long,
    val method: String,
    val redactedUrl: String,
    val statusCode: Int?,
    val latencyMs: Long,
    val errorClass: String?,
    val errorSummary: String? = errorClass
)

object DiagnosticRingBuffer {
    private const val MAX_CAPACITY = 100
    private val lock = Any()
    private var nextId = 1L
    private val deque = ArrayDeque<RequestDiagnosticEntry>(MAX_CAPACITY)

    private val _entriesFlow = MutableStateFlow<List<RequestDiagnosticEntry>>(emptyList())
    val entriesFlow: StateFlow<List<RequestDiagnosticEntry>> = _entriesFlow.asStateFlow()

    fun record(
        method: String,
        rawUrl: String,
        statusCode: Int?,
        latencyMs: Long,
        errorClass: String? = null
    ) {
        val cleanUrl = SecretRedactor.redactUrl(rawUrl)
        val cleanErr = errorClass?.let { SecretRedactor.redactText(it).take(80) }
        synchronized(lock) {
            if (deque.size >= MAX_CAPACITY) {
                deque.removeFirst()
            }
            deque.addLast(
                RequestDiagnosticEntry(
                    id = nextId++,
                    timestampMs = System.currentTimeMillis(),
                    method = method.uppercase(),
                    redactedUrl = cleanUrl,
                    statusCode = statusCode,
                    latencyMs = latencyMs.coerceAtLeast(0L),
                    errorClass = cleanErr
                )
            )
            _entriesFlow.value = deque.toList().asReversed()
        }
    }

    fun snapshot(): List<RequestDiagnosticEntry> = _entriesFlow.value

    fun clear() {
        synchronized(lock) {
            deque.clear()
            _entriesFlow.value = emptyList()
        }
    }
}

/**
 * OkHttp Interceptor that populates [DiagnosticRingBuffer] for every request while preserving
 * HTTP method across 301/302 redirects (preventing OkHttp from silently turning POST /v1/chat/completions
 * into GET /v1/chat/completions and triggering HTTP 405 Method Not Allowed!).
 */
class DiagnosticAndRedirectPreservingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val startNs = System.nanoTime()
        try {
            var response = chain.proceed(originalRequest)
            // If server returns 301/302 on a POST request (e.g. http -> https redirect on OmniRoute),
            // OkHttp's default follow-up converts POST to GET, causing 405 Method Not Allowed.
            // Re-issue with the original method and body to the Location target if present.
            if (response.code in listOf(301, 302, 307, 308) &&
                originalRequest.method.equals("POST", ignoreCase = true)
            ) {
                val location = response.header("Location")
                if (!location.isNullOrBlank()) {
                    val redirectUrl = originalRequest.url.resolve(location)
                    if (redirectUrl != null) {
                        response.close()
                        val redirectedRequest = originalRequest.newBuilder()
                            .url(redirectUrl)
                            .method(originalRequest.method, originalRequest.body)
                            .build()
                        response = chain.proceed(redirectedRequest)
                    }
                }
            }
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            val errClass = if (!response.isSuccessful) "HTTP_${response.code}" else null
            DiagnosticRingBuffer.record(
                method = originalRequest.method,
                rawUrl = originalRequest.url.toString(),
                statusCode = response.code,
                latencyMs = elapsedMs,
                errorClass = errClass
            )
            return response
        } catch (t: Throwable) {
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            DiagnosticRingBuffer.record(
                method = originalRequest.method,
                rawUrl = originalRequest.url.toString(),
                statusCode = null,
                latencyMs = elapsedMs,
                errorClass = t::class.simpleName ?: "IOException"
            )
            throw t
        }
    }
}
