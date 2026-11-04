package com.example.data.provider

import com.example.data.local.ProviderInstanceEntity
import kotlinx.coroutines.CancellationException
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * Represents a single entry in the user-defined ordered Provider Failover chain.
 */
data class FailoverChainEntry(
    val providerInstanceId: String,
    val modelId: String,
    val providerDisplayName: String = "",
    val modelDisplayName: String = "",
    val id: String = "$providerInstanceId:$modelId"
) {
    val compositeKey: String
        get() = "$providerInstanceId:$modelId"
}

/**
 * Classification result for an error encountered during a chat send/stream attempt.
 */
enum class FailoverErrorAction {
    /**
     * Do NOT fail over. Surface the error directly to the user
     * (e.g., HTTP 401/403 bad API key, HTTP 400 caused by the request itself, content policy, or user cancellation).
     */
    DO_NOT_FAILOVER,

    /**
     * Context-overflow error on the first attempt for the current provider:
     * re-run the payload through [SmartContextTrimmer] and retry once on the SAME provider before failing over.
     */
    RETRY_SAME_PROVIDER_AFTER_CONTEXT_TRIM,

    /**
     * Fail over to the next provider + model pair in the user-defined fallback chain
     * (network errors/timeouts, HTTP 429, HTTP 5xx, provider-side stream interruptions,
     * or context overflow after the same-provider trimmed retry has already been attempted).
     */
    FAILOVER_TO_NEXT_PROVIDER
}

/**
 * Snapshot of a streaming model call's output state across failover retries.
 * Ensures that:
 * - Failure BEFORE the first token switches silently (no note, empty partial output).
 * - Failure MID-STREAM after partial text discards the partial output completely (never stitching
 *   two half-answers together) and marks the message with `"restarted on <provider>"`.
 */
class FailoverStreamSession(
    initialCommittedText: String = ""
) {
    private val committedBuilder = StringBuilder(initialCommittedText)
    private val currentAttemptBuilder = StringBuilder()

    var restartedOnProviderName: String? = null
        private set

    var discardedPartialAttemptsCount: Int = 0
        private set

    val hasReceivedFirstTokenInCurrentAttempt: Boolean
        get() = currentAttemptBuilder.isNotEmpty()

    val currentAttemptText: String
        get() = currentAttemptBuilder.toString()

    val visibleText: String
        get() = committedBuilder.toString() + currentAttemptBuilder.toString()

    val restartedNoteText: String?
        get() = restartedOnProviderName?.takeIf { it.isNotBlank() }?.let {
            FailoverResolver.formatRestartedNote(it)
        }

    fun onDelta(chunk: String): String {
        if (chunk.isNotEmpty()) {
            currentAttemptBuilder.append(chunk)
        }
        return visibleText
    }

    /**
     * Called when the current model call attempt fails and failover switches to [nextProviderDisplayName].
     *
     * - If failure occurred BEFORE the first token: switches silently (`restartedOnProviderName` unchanged).
     * - If failure occurred MID-STREAM after partial text: discards `currentAttemptBuilder` so partial
     *   output is never stitched with the fallback provider's response, and records
     *   `"restarted on <provider>"`.
     */
    fun onFailoverToProvider(nextProviderDisplayName: String): FailoverRestartOutcome {
        val hadPartialText = currentAttemptBuilder.isNotEmpty()
        val discardedText = currentAttemptBuilder.toString()
        currentAttemptBuilder.setLength(0)

        if (hadPartialText) {
            discardedPartialAttemptsCount++
            restartedOnProviderName = nextProviderDisplayName.trim().ifBlank { "fallback provider" }
        }

        return FailoverRestartOutcome(
            switchedSilently = !hadPartialText,
            discardedPartialText = discardedText,
            visibleTextAfterReset = visibleText,
            restartedNote = restartedNoteText
        )
    }

    /**
     * Called when a same-provider retry occurs (e.g. after context-overflow trimming before any tokens).
     */
    fun onRetrySameProvider() {
        currentAttemptBuilder.setLength(0)
    }

    /**
     * Commits the current attempt's text once the model call finishes without error.
     */
    fun commitCurrentAttempt(): String {
        committedBuilder.append(currentAttemptBuilder)
        currentAttemptBuilder.setLength(0)
        return committedBuilder.toString()
    }

    fun appendSeparatorIfNeeded(separator: String = "\n\n") {
        if (committedBuilder.isNotEmpty() && !committedBuilder.endsWith(separator)) {
            committedBuilder.append(separator)
        }
    }

    fun appendCommittedText(text: String): String {
        committedBuilder.append(text)
        return visibleText
    }
}

data class FailoverRestartOutcome(
    val switchedSilently: Boolean,
    val discardedPartialText: String,
    val visibleTextAfterReset: String,
    val restartedNote: String?
)

/**
 * Dedicated, unit-testable resolver for Provider Failover error classification,
 * ordered fallback chain selection, and mid-stream partial output discarding.
 */
object FailoverResolver {

    private val httpStatusRegex = Regex("""\b(?:HTTP\s*|status\s*code\s*:?\s*|\()([45]\d\d)\b""", RegexOption.IGNORE_CASE)

    private val contextOverflowPatterns = listOf(
        "context_length_exceeded",
        "maximum context length",
        "max_context_length",
        "context length",
        "context window",
        "context_overflow",
        "context overflow",
        "prompt is too long",
        "input is too long",
        "messages are too long",
        "request too large for model",
        "reduce the length of the messages",
        "exceeds the model's maximum context",
        "exceeds the maximum number of tokens",
        "maximum number of tokens allowed",
        "token limit exceeded",
        "too many tokens"
    )

    private val streamInterruptionPatterns = listOf(
        "stream ended",
        "non-ping sse event",
        "stream interrupted",
        "unexpected end of stream",
        "prematurely closed",
        "incomplete chunked",
        "broken pipe",
        "connection reset",
        "connection closed",
        "connection aborted",
        "software caused connection abort",
        "eofexception",
        "all providers failed",
        "no available provider"
    )

    private val networkTimeoutPatterns = listOf(
        "timeout",
        "timed out",
        "sockettimeoutexception",
        "connectexception",
        "unknownhostexception",
        "no connection",
        "network unreachable",
        "failed to connect",
        "connection refused",
        "unable to resolve host",
        "unreachable",
        "dns"
    )

    private val authFailurePatterns = listOf(
        "invalid_api_key",
        "invalid api key",
        "incorrect api key",
        "authentication failed",
        "unauthorized",
        "forbidden",
        "api key was rejected",
        "permission_denied",
        "invalid x-api-key",
        "api_key_invalid"
    )

    private val badRequestPatterns = listOf(
        "invalid_request_error",
        "bad request",
        "malformed",
        "invalid argument",
        "invalid_argument",
        "invalid parameter",
        "missing required parameter",
        "unrecognized request argument",
        "schema validation"
    )

    fun formatRestartedNote(providerDisplayName: String): String {
        val cleanName = providerDisplayName.trim().ifBlank { "fallback provider" }
        return "restarted on $cleanName"
    }

    /**
     * Extracts an HTTP status code from [explicitStatusCode] or from [NormalizedAiError] fields.
     */
    fun extractHttpStatusCode(
        error: NormalizedAiError? = null,
        explicitStatusCode: Int? = null
    ): Int? {
        if (explicitStatusCode != null && explicitStatusCode in 100..599) {
            return explicitStatusCode
        }
        if (error == null) return null
        val combined = "${error.title} ${error.details} ${error.message}"
        val match = httpStatusRegex.find(combined)
        if (match != null) {
            return match.groupValues.getOrNull(1)?.toIntOrNull()
        }
        val bareCodeMatch = Regex("""\b(400|401|402|403|404|405|408|413|422|429|500|502|503|504)\b""").find(combined)
        return bareCodeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * Returns true if the error indicates that the outbound prompt/messages exceeded the model's
     * context window (distinct from TPM/RPM rate-limit errors).
     */
    fun isContextOverflowError(
        error: NormalizedAiError? = null,
        rawMessage: String = "",
        httpStatusCode: Int? = null
    ): Boolean {
        val combined = buildString {
            if (error != null) {
                append(error.title).append(' ')
                append(error.message).append(' ')
                append(error.details).append(' ')
            }
            append(rawMessage)
        }.lowercase(Locale.US)

        if (combined.isBlank()) return false

        // If it explicitly mentions TPM/RPM rate limits without context window phrases, it's a rate limit, not context overflow.
        val hasContextPhrase = contextOverflowPatterns.any { combined.contains(it) }
        if (hasContextPhrase) {
            return true
        }

        // HTTP 413 Payload Too Large when not a TPM/RPM rate limit is also a context/payload overflow
        val code = extractHttpStatusCode(error, httpStatusCode)
        if (code == 413 && !ErrorNormalizer.hasTpmOrRpmIndicator(combined) && !combined.contains("rate limit") && !combined.contains("rate-limit")) {
            return true
        }

        return false
    }

    /**
     * Classifies an error according to the Runway Provider Failover specification:
     * - FAIL OVER on: network errors/timeouts, HTTP 429, HTTP 5xx, provider-side stream interruptions.
     * - DO NOT fail over on: HTTP 401/403 (bad key), HTTP 400 caused by the request itself, or user cancellation.
     * - Context-overflow errors: first re-run the payload through [SmartContextTrimmer] and retry once
     *   on the same provider ([FailoverErrorAction.RETRY_SAME_PROVIDER_AFTER_CONTEXT_TRIM]) before failing over
     *   ([FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER]).
     */
    fun classifyError(
        error: NormalizedAiError? = null,
        throwable: Throwable? = null,
        httpStatusCode: Int? = null,
        isUserCancelled: Boolean = false,
        alreadyRetriedContextOverflowOnSameProvider: Boolean = false
    ): FailoverErrorAction {
        // 1. User cancellation -> NEVER fail over
        if (isUserCancelled || throwable is CancellationException) {
            return FailoverErrorAction.DO_NOT_FAILOVER
        }

        val statusCode = extractHttpStatusCode(error, httpStatusCode)
        val combinedText = buildString {
            if (error != null) {
                append(error.title).append(' ')
                append(error.message).append(' ')
                append(error.details).append(' ')
            }
            if (throwable != null) {
                append(throwable::class.simpleName.orEmpty()).append(' ')
                append(throwable.message.orEmpty())
            }
        }.lowercase(Locale.US)

        // 2. HTTP 401 / 403 (Bad API key / unauthorized) -> NEVER fail over
        if (statusCode == 401 ||
            statusCode == 403 ||
            error?.category == ErrorCategory.INVALID_KEY ||
            authFailurePatterns.any { combinedText.contains(it) }
        ) {
            return FailoverErrorAction.DO_NOT_FAILOVER
        }

        // 3. Unapproved cleartext host or content policy -> NEVER fail over
        if (error?.category == ErrorCategory.CLEARTEXT_UNAPPROVED ||
            error?.category == ErrorCategory.CONTENT_POLICY
        ) {
            return FailoverErrorAction.DO_NOT_FAILOVER
        }

        // 4. Context-overflow errors -> retry once on same provider after SmartContextTrimmer, then fail over
        if (isContextOverflowError(error = error, rawMessage = combinedText, httpStatusCode = statusCode)) {
            return if (!alreadyRetriedContextOverflowOnSameProvider) {
                FailoverErrorAction.RETRY_SAME_PROVIDER_AFTER_CONTEXT_TRIM
            } else {
                FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER
            }
        }

        // 5. HTTP 400 caused by the request itself -> NEVER fail over
        if (statusCode == 400 ||
            statusCode == 422 ||
            badRequestPatterns.any { combinedText.contains(it) }
        ) {
            return FailoverErrorAction.DO_NOT_FAILOVER
        }

        // 6. Network errors / timeouts -> FAIL OVER
        if (throwable is SocketTimeoutException ||
            throwable is ConnectException ||
            throwable is UnknownHostException ||
            throwable is EOFException ||
            throwable is SSLException ||
            error?.category == ErrorCategory.NETWORK_UNREACHABLE ||
            statusCode == 408 ||
            networkTimeoutPatterns.any { combinedText.contains(it) }
        ) {
            return FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER
        }

        // 7. HTTP 429 / Rate limit -> FAIL OVER
        if (statusCode == 429 ||
            statusCode == 413 ||
            error?.category == ErrorCategory.RATE_LIMIT ||
            combinedText.contains("429") ||
            combinedText.contains("too many requests") ||
            combinedText.contains("rate limit") ||
            combinedText.contains("rate_limit") ||
            combinedText.contains("rate-limited")
        ) {
            return FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER
        }

        // 8. HTTP 5xx -> FAIL OVER
        if ((statusCode != null && statusCode in 500..599) ||
            combinedText.contains("provider trouble") ||
            combinedText.contains("having trouble") ||
            combinedText.contains("internal server error") ||
            combinedText.contains("bad gateway") ||
            combinedText.contains("service unavailable") ||
            combinedText.contains("gateway timeout") ||
            combinedText.contains("overloaded")
        ) {
            return FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER
        }

        // 9. Provider-side stream interruptions -> FAIL OVER
        if (streamInterruptionPatterns.any { combinedText.contains(it) } ||
            throwable is IOException
        ) {
            return FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER
        }

        return FailoverErrorAction.DO_NOT_FAILOVER
    }

    /**
     * Selects the next unattempted [FailoverChainEntry] from the user's ordered [chain].
     *
     * Returns `null` if [failoverEnabled] is false, [chain] is empty, or all entries in [chain]
     * have already been attempted in this message turn.
     */
    fun selectNextTarget(
        failoverEnabled: Boolean,
        chain: List<FailoverChainEntry>,
        attemptedCompositeKeys: Set<String>,
        availableProvidersById: Map<String, ProviderInstanceEntity>? = null
    ): FailoverChainEntry? {
        if (!failoverEnabled || chain.isEmpty()) return null
        for (entry in chain) {
            if (entry.providerInstanceId.isBlank() || entry.modelId.isBlank()) continue
            if (entry.compositeKey in attemptedCompositeKeys) continue
            if (availableProvidersById != null) {
                val prov = availableProvidersById[entry.providerInstanceId] ?: continue
                if (!prov.isEnabled) continue
            }
            return entry
        }
        return null
    }

    /**
     * Moves an item at [fromIndex] to [toIndex] in the ordered fallback chain.
     */
    fun moveChainEntry(
        chain: List<FailoverChainEntry>,
        fromIndex: Int,
        toIndex: Int
    ): List<FailoverChainEntry> {
        if (fromIndex !in chain.indices || toIndex !in chain.indices || fromIndex == toIndex) {
            return chain
        }
        val mutable = chain.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    /**
     * Pure-Kotlin JSON serializer for persisting the ordered fallback chain in DataStore.
     */
    fun serializeChainToJson(chain: List<FailoverChainEntry>): String {
        if (chain.isEmpty()) return "[]"
        return chain.joinToString(separator = ",", prefix = "[", postfix = "]") { item ->
            buildString {
                append("{")
                append("\"id\":\"${escapeJson(item.id)}\",")
                append("\"providerInstanceId\":\"${escapeJson(item.providerInstanceId)}\",")
                append("\"modelId\":\"${escapeJson(item.modelId)}\",")
                append("\"providerDisplayName\":\"${escapeJson(item.providerDisplayName)}\",")
                append("\"modelDisplayName\":\"${escapeJson(item.modelDisplayName)}\"")
                append("}")
            }
        }
    }

    /**
     * Pure-Kotlin JSON parser for loading the ordered fallback chain from DataStore.
     */
    fun parseChainFromJson(json: String?): List<FailoverChainEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val trimmed = json.trim()
        if (trimmed == "[]") return emptyList()
        return runCatching {
            val objects = extractFlatJsonObjects(trimmed)
            val result = mutableListOf<FailoverChainEntry>()
            for (obj in objects) {
                val providerId = extractJsonString(obj, "providerInstanceId").trim()
                val modelId = extractJsonString(obj, "modelId").trim()
                if (providerId.isNotBlank() && modelId.isNotBlank()) {
                    val id = extractJsonString(obj, "id").trim().ifBlank { "$providerId:$modelId" }
                    val provName = extractJsonString(obj, "providerDisplayName").trim()
                    val modName = extractJsonString(obj, "modelDisplayName").trim()
                    result.add(
                        FailoverChainEntry(
                            id = id,
                            providerInstanceId = providerId,
                            modelId = modelId,
                            providerDisplayName = provName,
                            modelDisplayName = modName
                        )
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescapeJson(raw: String): String {
        if (!raw.contains('\\')) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun extractJsonString(json: String, key: String): String {
        val escapedKey = Regex.escape(key)
        val regex = Regex(""""$escapedKey"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val match = regex.find(json) ?: return ""
        return unescapeJson(match.groupValues[1])
    }

    private fun extractFlatJsonObjects(json: String): List<String> {
        val list = mutableListOf<String>()
        var inString = false
        var escaped = false
        val stack = ArrayDeque<Int>()
        for (i in json.indices) {
            val c = json[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> stack.addLast(i)
                    '}' -> {
                        if (stack.isNotEmpty()) {
                            val start = stack.removeLast()
                            list.add(json.substring(start, i + 1))
                        }
                    }
                }
            }
        }
        return list
    }
}
