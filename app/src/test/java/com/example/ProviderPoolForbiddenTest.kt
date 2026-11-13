package com.example

import com.example.data.provider.ProviderError
import com.example.data.provider.ProviderErrorMapper
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
class ProviderPoolForbiddenTest {

    // Auth-flavoured body with no rate-limit wording — the shape of the real
    // failure (2026-11-12): a background subagent died instantly on a pool 403
    // worded "Your API key was rejected" while the main chat, on the same key,
    // had just completed a long request. The pool drew a dead upstream key.
    private val authBody = """{"error":{"message":"Your API key was rejected"}}"""

    @Test
    fun `pool 403 by provider name is rate limited and retryable`() {
        val err = ProviderErrorMapper.fromHttpResponse(
            statusCode = 403,
            responseBody = authBody,
            providerName = "LiteRouter",
            attemptedUrl = "https://api.literouter.com/v1/chat/completions"
        )
        assertTrue(err is ProviderError.RateLimited)
        assertTrue(err.isRetryable)
        assertEquals(403, err.httpStatus)
    }

    @Test
    fun `pool 403 by url alone is rate limited`() {
        val err = ProviderErrorMapper.fromHttpResponse(
            statusCode = 403,
            responseBody = authBody,
            providerName = "My free models",
            attemptedUrl = "https://api.literouter.com/v1/chat/completions"
        )
        assertTrue(err is ProviderError.RateLimited)
    }

    @Test
    fun `non-pool 403 stays an auth failure`() {
        val err = ProviderErrorMapper.fromHttpResponse(
            statusCode = 403,
            responseBody = authBody,
            providerName = "OpenAI",
            attemptedUrl = "https://api.openai.com/v1/chat/completions"
        )
        assertTrue(err is ProviderError.Auth)
        assertFalse(err.isRetryable)
    }

    @Test
    fun `pool 401 still stays an auth failure`() {
        val err = ProviderErrorMapper.fromHttpResponse(
            statusCode = 401,
            responseBody = authBody,
            providerName = "LiteRouter",
            attemptedUrl = "https://api.literouter.com/v1/chat/completions"
        )
        assertTrue(err is ProviderError.Auth)
        assertFalse(err.isRetryable)
    }
}
