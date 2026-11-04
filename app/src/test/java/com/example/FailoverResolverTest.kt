package com.example

import com.example.data.local.ProviderInstanceEntity
import com.example.data.provider.ChatMessagePayload
import com.example.data.provider.ErrorCategory
import com.example.data.provider.FailoverChainEntry
import com.example.data.provider.FailoverErrorAction
import com.example.data.provider.FailoverResolver
import com.example.data.provider.FailoverStreamSession
import com.example.data.provider.NormalizedAiError
import com.example.data.provider.SmartContextTrimmer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FailoverResolverTest {

    // --- 1. Error Classification Tests ---

    @Test
    fun classifyError_failsOverOnNetworkTimeouts429And5xxAndStreamInterruptions() {
        // Network timeout / unreachable
        val timeoutAction = FailoverResolver.classifyError(
            throwable = SocketTimeoutException("Read timed out")
        )
        assertEquals(FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER, timeoutAction)

        val connectAction = FailoverResolver.classifyError(
            throwable = ConnectException("Failed to connect to api.openai.com")
        )
        assertEquals(FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER, connectAction)

        val dnsAction = FailoverResolver.classifyError(
            throwable = UnknownHostException("Unable to resolve host")
        )
        assertEquals(FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER, dnsAction)

        // HTTP 429 Rate Limit
        val rateLimitError = NormalizedAiError(
            category = ErrorCategory.RATE_LIMIT,
            title = "Rate-limited — retrying in 5s",
            message = "HTTP 429 Too Many Requests",
            suggestedFix = "Wait and retry",
            details = "HTTP 429: rate_limit_exceeded"
        )
        assertEquals(
            FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER,
            FailoverResolver.classifyError(error = rateLimitError)
        )

        // HTTP 5xx Server Errors
        for (statusCode in listOf(500, 502, 503, 504)) {
            val serverErr = NormalizedAiError(
                category = ErrorCategory.SERVER_ERROR,
                title = "OpenAI is having trouble — try again shortly",
                message = "Upstream error ($statusCode)",
                suggestedFix = "Retry shortly",
                details = "HTTP $statusCode Gateway Error"
            )
            assertEquals(
                "Expected HTTP $statusCode to fail over",
                FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER,
                FailoverResolver.classifyError(error = serverErr)
            )
        }

        // Provider-side stream interruption
        val streamDropError = NormalizedAiError(
            category = ErrorCategory.SERVER_ERROR,
            title = "Stream interrupted",
            message = "Stream ended before producing a non-ping SSE event",
            suggestedFix = "Retry",
            details = "EOFException: unexpected end of stream"
        )
        assertEquals(
            FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER,
            FailoverResolver.classifyError(error = streamDropError, throwable = EOFException("unexpected end of stream"))
        )
    }

    @Test
    fun classifyError_doesNotFailOverOn401_403_BadRequest400_OrUserCancellation() {
        // HTTP 401 / 403 Invalid API key
        val badKey401 = NormalizedAiError(
            category = ErrorCategory.INVALID_KEY,
            title = "API key rejected — check Settings",
            message = "HTTP 401 Unauthorized: Incorrect API key provided",
            suggestedFix = "Update API key",
            details = "HTTP 401"
        )
        assertEquals(
            FailoverErrorAction.DO_NOT_FAILOVER,
            FailoverResolver.classifyError(error = badKey401)
        )

        val forbidden403 = NormalizedAiError(
            category = ErrorCategory.INVALID_KEY,
            title = "API key rejected — check Settings",
            message = "HTTP 403 Forbidden",
            suggestedFix = "Check permissions",
            details = "HTTP 403"
        )
        assertEquals(
            FailoverErrorAction.DO_NOT_FAILOVER,
            FailoverResolver.classifyError(error = forbidden403)
        )

        // HTTP 400 caused by the request itself
        val badRequest400 = NormalizedAiError(
            category = ErrorCategory.SERVER_ERROR,
            title = "Bad Request",
            message = "HTTP 400: invalid_request_error - malformed JSON parameter",
            suggestedFix = "Fix request parameters",
            details = "HTTP 400 Bad Request"
        )
        assertEquals(
            FailoverErrorAction.DO_NOT_FAILOVER,
            FailoverResolver.classifyError(error = badRequest400, httpStatusCode = 400)
        )

        // User cancellation
        assertEquals(
            FailoverErrorAction.DO_NOT_FAILOVER,
            FailoverResolver.classifyError(
                error = NormalizedAiError(
                    category = ErrorCategory.NETWORK_UNREACHABLE,
                    title = "Cancelled",
                    message = "User stopped generation",
                    suggestedFix = ""
                ),
                isUserCancelled = true
            )
        )
        assertEquals(
            FailoverErrorAction.DO_NOT_FAILOVER,
            FailoverResolver.classifyError(throwable = CancellationException("Job was cancelled"))
        )
    }

    @Test
    fun classifyError_contextOverflowRetriesOnceWithSmartContextTrimmerBeforeFailingOver() = runBlocking {
        val contextOverflowErr = NormalizedAiError(
            category = ErrorCategory.SERVER_ERROR,
            title = "Context length exceeded",
            message = "HTTP 400: This model's maximum context length is 8192 tokens. Please reduce the length of the messages.",
            suggestedFix = "Shorten conversation",
            details = "context_length_exceeded"
        )

        // 1st time on the provider: must retry on the SAME provider after SmartContextTrimmer
        val firstAction = FailoverResolver.classifyError(
            error = contextOverflowErr,
            httpStatusCode = 400,
            alreadyRetriedContextOverflowOnSameProvider = false
        )
        assertEquals(FailoverErrorAction.RETRY_SAME_PROVIDER_AFTER_CONTEXT_TRIM, firstAction)

        // Verify SmartContextTrimmer trims the payload for the same-provider retry
        val longHistory = (1..10).flatMap { turn ->
            listOf(
                ChatMessagePayload(role = "user", content = "User turn $turn " + "x".repeat(200)),
                ChatMessagePayload(role = "assistant", content = "Assistant turn $turn " + "y".repeat(200))
            )
        }
        val trimResult = SmartContextTrimmer.trimIfNeeded(
            fullMessages = longHistory,
            systemPrompt = "System prompt",
            modelContextWindowTokens = 100, // Force threshold trigger
            trimmingEnabledForChat = true,
            existingCachedSummary = null,
            summariseOlderMessages = { "Concise summary of earlier turns." }
        )
        assertTrue(trimResult.wasSummarised)
        assertTrue(trimResult.requestMessages.size < longHistory.size)

        // 2nd time on the same provider (if trimmed retry still overflows): fail over to next provider
        val secondAction = FailoverResolver.classifyError(
            error = contextOverflowErr,
            httpStatusCode = 400,
            alreadyRetriedContextOverflowOnSameProvider = true
        )
        assertEquals(FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER, secondAction)
    }

    // --- 2. Chain Ordering Tests ---

    @Test
    fun selectNextTarget_respectsChainOrderSkipsAttemptedAndDisabledAndHonorsToggle() {
        val providersById = mapOf(
            "prov_openai" to ProviderInstanceEntity(
                id = "prov_openai",
                displayName = "OpenAI",
                presetType = "OPENAI",
                baseUrl = "https://api.openai.com/v1",
                capabilitiesCsv = "CHAT",
                isEnabled = true
            ),
            "prov_anthropic" to ProviderInstanceEntity(
                id = "prov_anthropic",
                displayName = "Anthropic",
                presetType = "ANTHROPIC",
                baseUrl = "https://api.anthropic.com/v1",
                capabilitiesCsv = "CHAT",
                isEnabled = true
            ),
            "prov_gemini" to ProviderInstanceEntity(
                id = "prov_gemini",
                displayName = "Google Gemini",
                presetType = "GEMINI",
                baseUrl = "https://generativelanguage.googleapis.com",
                capabilitiesCsv = "CHAT",
                isEnabled = true
            ),
            "prov_disabled" to ProviderInstanceEntity(
                id = "prov_disabled",
                displayName = "Disabled Provider",
                presetType = "CUSTOM",
                baseUrl = "https://example.com/v1",
                capabilitiesCsv = "CHAT",
                isEnabled = false
            )
        )

        val chain = listOf(
            FailoverChainEntry(
                providerInstanceId = "prov_disabled",
                modelId = "disabled-model",
                providerDisplayName = "Disabled Provider"
            ),
            FailoverChainEntry(
                providerInstanceId = "prov_anthropic",
                modelId = "claude-3-5-sonnet-latest",
                providerDisplayName = "Anthropic"
            ),
            FailoverChainEntry(
                providerInstanceId = "prov_gemini",
                modelId = "gemini-2.5-flash",
                providerDisplayName = "Google Gemini"
            )
        )

        // When failover is OFF (default), returns null
        assertNull(
            FailoverResolver.selectNextTarget(
                failoverEnabled = false,
                chain = chain,
                attemptedCompositeKeys = setOf("prov_openai:gpt-4o"),
                availableProvidersById = providersById
            )
        )

        // When chain is empty, returns null
        assertNull(
            FailoverResolver.selectNextTarget(
                failoverEnabled = true,
                chain = emptyList(),
                attemptedCompositeKeys = setOf("prov_openai:gpt-4o"),
                availableProvidersById = providersById
            )
        )

        // 1st failover hop: skips disabled provider and selects Anthropic
        val attempted = mutableSetOf("prov_openai:gpt-4o")
        val firstFallback = FailoverResolver.selectNextTarget(
            failoverEnabled = true,
            chain = chain,
            attemptedCompositeKeys = attempted,
            availableProvidersById = providersById
        )
        assertNotNull(firstFallback)
        assertEquals("prov_anthropic", firstFallback!!.providerInstanceId)
        assertEquals("claude-3-5-sonnet-latest", firstFallback.modelId)

        // 2nd failover hop: after Anthropic is attempted, selects Gemini next in order
        attempted.add(firstFallback.compositeKey)
        val secondFallback = FailoverResolver.selectNextTarget(
            failoverEnabled = true,
            chain = chain,
            attemptedCompositeKeys = attempted,
            availableProvidersById = providersById
        )
        assertNotNull(secondFallback)
        assertEquals("prov_gemini", secondFallback!!.providerInstanceId)
        assertEquals("gemini-2.5-flash", secondFallback.modelId)

        // 3rd hop: chain exhausted -> returns null
        attempted.add(secondFallback.compositeKey)
        assertNull(
            FailoverResolver.selectNextTarget(
                failoverEnabled = true,
                chain = chain,
                attemptedCompositeKeys = attempted,
                availableProvidersById = providersById
            )
        )

        // Reordering chain up/down and round-trip JSON serialization
        val reordered = FailoverResolver.moveChainEntry(chain, fromIndex = 2, toIndex = 1)
        assertEquals("prov_gemini", reordered[1].providerInstanceId)
        assertEquals("prov_anthropic", reordered[2].providerInstanceId)

        val json = FailoverResolver.serializeChainToJson(reordered)
        val parsed = FailoverResolver.parseChainFromJson(json)
        assertEquals(reordered.map { it.compositeKey }, parsed.map { it.compositeKey })
    }

    // --- 3. Mid-Stream Restart Discarding Partial Output Tests ---

    @Test
    fun failoverStreamSession_switchesSilentlyBeforeFirstTokenAndDiscardsPartialOutputMidStream() {
        // Case A: Failure BEFORE the first token -> switch silently, no "restarted on" note
        val silentSession = FailoverStreamSession()
        assertFalse(silentSession.hasReceivedFirstTokenInCurrentAttempt)

        val preTokenOutcome = silentSession.onFailoverToProvider("Anthropic")
        assertTrue(preTokenOutcome.switchedSilently)
        assertEquals("", preTokenOutcome.discardedPartialText)
        assertEquals("", preTokenOutcome.visibleTextAfterReset)
        assertNull(preTokenOutcome.restartedNote)

        silentSession.onDelta("Complete answer from Anthropic.")
        val finalSilentText = silentSession.commitCurrentAttempt()
        assertEquals("Complete answer from Anthropic.", finalSilentText)
        assertNull(silentSession.restartedNoteText)

        // Case B: Failure MID-STREAM after partial text -> discard partial output, never stitch half-answers,
        // and mark message with "restarted on <provider>"
        val midStreamSession = FailoverStreamSession()
        midStreamSession.onDelta("Half of an answer from OpenAI that drops mid-")
        assertTrue(midStreamSession.hasReceivedFirstTokenInCurrentAttempt)
        assertEquals("Half of an answer from OpenAI that drops mid-", midStreamSession.visibleText)

        val midStreamOutcome = midStreamSession.onFailoverToProvider("Google Gemini")
        assertFalse(midStreamOutcome.switchedSilently)
        assertEquals("Half of an answer from OpenAI that drops mid-", midStreamOutcome.discardedPartialText)
        assertEquals("", midStreamOutcome.visibleTextAfterReset)
        assertEquals("restarted on Google Gemini", midStreamOutcome.restartedNote)

        // Now fallback provider streams its response — must NOT contain the discarded OpenAI text
        midStreamSession.onDelta("Fresh full response from Gemini.")
        val finalRestartedText = midStreamSession.commitCurrentAttempt()
        assertEquals("Fresh full response from Gemini.", finalRestartedText)
        assertFalse(finalRestartedText.contains("Half of an answer"))
        assertEquals("restarted on Google Gemini", midStreamSession.restartedNoteText)
    }
}
