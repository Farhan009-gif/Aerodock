package com.example.data.provider

/**
 * Structured user-friendly error representation for Runway chat.
 * Replaces cryptic raw errors with a clear title, plain cause, one actionable next step,
 * and an expandable details view for troubleshooting.
 */
data class FriendlyError(
    val title: String,
    val cause: String,
    val nextStep: String,
    val rawDetails: String
)

object FriendlyErrorMapper {

    fun map(
        rawMessage: String,
        statusCode: Int? = null,
        providerName: String = "Provider"
    ): FriendlyError {
        val trimmed = rawMessage.trim()
        val lower = trimmed.lowercase()
        val code = statusCode ?: extractStatusCode(trimmed)

        return when {
            // Cleartext HTTP blocked
            lower.contains("cleartext") ||
                lower.contains("http not allowed") ||
                lower.contains("cleartext http traffic") -> {
                FriendlyError(
                    title = "Connection Security Block",
                    cause = "Plain HTTP to this address isn't allowed.",
                    nextStep = "Approve the host in Settings.",
                    rawDetails = trimmed
                )
            }

            // Rate-limit wording beats the status code: some providers (e.g.
            // LiteRouter) answer a cooldown with HTTP 403, which is not an
            // auth failure.
            lower.contains("rate limit") ||
                lower.contains("rate_limit") ||
                lower.contains("too many requests") -> {
                FriendlyError(
                    title = "Rate Limit Reached",
                    cause = "The provider asked for a pause between requests.",
                    nextStep = "Wait a few seconds and retry.",
                    rawDetails = trimmed
                )
            }

            // 401 / 403 API key or auth issues
            code == 401 ||
                code == 403 ||
                lower.contains("401") ||
                lower.contains("403") ||
                lower.contains("unauthorized") ||
                lower.contains("forbidden") ||
                lower.contains("invalid api key") ||
                lower.contains("invalid_api_key") ||
                lower.contains("incorrect api key") ||
                lower.contains("authentication failed") -> {
                FriendlyError(
                    title = "Authentication Error",
                    cause = "Your API key was rejected.",
                    nextStep = "Check the key in Providers.",
                    rawDetails = trimmed
                )
            }

            // 404 Model not found
            code == 404 ||
                lower.contains("404") ||
                lower.contains("model_not_found") ||
                lower.contains("not found") ||
                lower.contains("does not exist") ||
                lower.contains("unrecognized model") -> {
                FriendlyError(
                    title = "Model Unavailable",
                    cause = "This model isn't available on this provider.",
                    nextStep = "Pick another model.",
                    rawDetails = trimmed
                )
            }

            // 413 or TPM / RPM text
            code == 413 ||
                lower.contains("413") ||
                lower.contains("tpm") ||
                lower.contains("rpm") ||
                (lower.contains("rate limit") && (lower.contains("token") || lower.contains("minute") || lower.contains("request per"))) ||
                lower.contains("tokens per minute") ||
                lower.contains("requests per minute") -> {
                FriendlyError(
                    title = "Rate Limit Reached",
                    cause = "The provider is rate-limited right now.",
                    nextStep = "Wait or switch models.",
                    rawDetails = trimmed
                )
            }

            // 429 Too many requests
            code == 429 ||
                lower.contains("429") ||
                lower.contains("too many requests") ||
                lower.contains("quota exceeded") ||
                lower.contains("insufficient_quota") -> {
                FriendlyError(
                    title = "Too Many Requests",
                    cause = "Too many requests.",
                    nextStep = "Wait or switch providers.",
                    rawDetails = trimmed
                )
            }

            // 500 / 502 / 503 / 504 Provider troubles
            (code in 500..504) ||
                lower.contains("500") ||
                lower.contains("502") ||
                lower.contains("503") ||
                lower.contains("504") ||
                lower.contains("internal server error") ||
                lower.contains("bad gateway") ||
                lower.contains("service unavailable") ||
                lower.contains("gateway timeout") ||
                lower.contains("overloaded") -> {
                FriendlyError(
                    title = "Provider Outage",
                    cause = "The provider is having trouble.",
                    nextStep = "Retry or switch providers.",
                    rawDetails = trimmed
                )
            }

            // Network / Timeout
            lower.contains("timeout") ||
                lower.contains("sockettimeoutexception") ||
                lower.contains("connectexception") ||
                lower.contains("no connection") ||
                lower.contains("failed to connect") ||
                lower.contains("network unreachable") ||
                lower.contains("connection reset") ||
                lower.contains("connection refused") -> {
                FriendlyError(
                    title = "Connection Failed",
                    cause = "No connection to the provider.",
                    nextStep = "Check your network or gateway address.",
                    rawDetails = trimmed
                )
            }

            else -> {
                FriendlyError(
                    title = "Request Failed",
                    cause = trimmed.take(120),
                    nextStep = "Check connection or switch providers.",
                    rawDetails = trimmed
                )
            }
        }
    }

    private fun extractStatusCode(text: String): Int? {
        val m = Regex("""\b([45]\d\d)\b""").find(text)
        return m?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
