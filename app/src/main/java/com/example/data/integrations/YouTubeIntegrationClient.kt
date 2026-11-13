package com.example.data.integrations

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

data class YouTubeTokenBundle(
    val accessToken: String,
    val refreshToken: String = "",
    val expiresInSeconds: Long = 3600L,
    val scope: String = ""
)

data class YouTubeChannelSummary(
    val channelId: String,
    val title: String,
    val customUrl: String,
    val subscriberCount: String
)

data class YouTubeUploadResult(
    val videoId: String,
    val title: String,
    val privacyStatus: String,
    val watchUrl: String,
    val studioUrl: String
)

/**
 * Manual-only YouTube Data API v3 & OAuth 2.0 PKCE client for publishing generated MP4 videos
 * from the OmniAI Studio Video Gallery to the user's YouTube channel.
 *
 * Security & Policy:
 * - Implements RFC 7636 OAuth 2.0 Proof Key for Code Exchange (PKCE) with S256 challenge.
 * - OAuth tokens are stored exclusively inside `TinkSecretVault` (AES256-GCM + Android Keystore).
 * - Strictly manual: uploads only happen when the user explicitly clicks "Upload to YouTube"
 *   inside the confirmation dialog. Defaults to "private" or "unlisted" privacy status.
 */
class YouTubeIntegrationClient(
    private val okHttpClient: OkHttpClient,
    private val oauthTokenUrl: String = "https://oauth2.googleapis.com/token",
    private val youtubeApiBaseUrl: String = "https://www.googleapis.com/youtube/v3",
    private val youtubeUploadBaseUrl: String = "https://www.googleapis.com/upload/youtube/v3"
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val mp4MediaType = "video/mp4".toMediaType()

    suspend fun exchangeAuthCodeForTokens(
        clientId: String,
        authCode: String,
        codeVerifier: String,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): Result<YouTubeTokenBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanClientId = clientId.trim()
            val cleanCode = authCode.trim()
            require(cleanClientId.isNotEmpty()) { "Google OAuth 2.0 Client ID is required." }
            require(cleanCode.isNotEmpty()) { "Authorization code is required." }
            require(codeVerifier.isNotBlank()) { "PKCE code_verifier is missing." }

            val formBody = FormBody.Builder()
                .add("client_id", cleanClientId)
                .add("code", cleanCode)
                .add("code_verifier", codeVerifier.trim())
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)
                .build()

            val request = Request.Builder()
                .url(oauthTokenUrl)
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errDesc = runCatching {
                        val j = JSONObject(rawBody)
                        j.optString("error_description").ifBlank { j.optString("error") }
                    }.getOrDefault("").ifBlank { "HTTP ${response.code}" }
                    throw IllegalStateException("OAuth 2.0 PKCE token exchange failed: $errDesc")
                }
                val json = JSONObject(rawBody)
                val accessToken = json.optString("access_token")
                require(accessToken.isNotBlank()) { "Token endpoint did not return an access_token." }
                YouTubeTokenBundle(
                    accessToken = accessToken,
                    refreshToken = json.optString("refresh_token", ""),
                    expiresInSeconds = json.optLong("expires_in", 3600L),
                    scope = json.optString("scope", "")
                )
            }
        }
    }

    suspend fun verifyChannel(accessToken: String): Result<YouTubeChannelSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = accessToken.trim()
            require(cleanToken.isNotEmpty()) { "YouTube OAuth 2.0 access token is empty." }

            val url = "${youtubeApiBaseUrl.trimEnd('/')}/channels?part=snippet,statistics&mine=true"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = extractGoogleApiError(rawBody, response.code)
                    throw IllegalStateException("YouTube channel verification failed: $errMsg")
                }
                val json = JSONObject(rawBody)
                val items = json.optJSONArray("items") ?: JSONArray()
                if (items.length() == 0) {
                    throw IllegalStateException("Authenticated Google account has no active YouTube channel.")
                }
                val first = items.getJSONObject(0)
                val snippet = first.optJSONObject("snippet") ?: JSONObject()
                val stats = first.optJSONObject("statistics") ?: JSONObject()
                YouTubeChannelSummary(
                    channelId = first.optString("id", ""),
                    title = snippet.optString("title", "YouTube Channel"),
                    customUrl = snippet.optString("customUrl", ""),
                    subscriberCount = stats.optString("subscriberCount", "0")
                )
            }
        }
    }

    /**
     * Performs a 2-step YouTube Data API v3 Resumable Upload:
     * 1) POST metadata to `/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status`
     * 2) PUT binary MP4 bytes to the session URI returned in the `Location` response header.
     */
    suspend fun uploadVideoResumable(
        accessToken: String,
        videoBytes: ByteArray,
        title: String,
        description: String,
        tags: List<String>,
        privacyStatus: String
    ): Result<YouTubeUploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = accessToken.trim()
            require(cleanToken.isNotEmpty()) {
                "Connect a YouTube OAuth 2.0 Access Token in Settings -> Integrations first."
            }
            require(videoBytes.isNotEmpty()) { "Video MP4 byte payload is empty." }

            val normalizedPrivacy = privacyStatus.trim().lowercase().let {
                if (it in listOf("private", "unlisted", "public")) it else "private"
            }
            val cleanTitle = title.trim().ifBlank { "Runway Generated Video" }.take(100)
            val metadataJson = buildVideoMetadataJson(
                title = cleanTitle,
                description = description.trim(),
                tags = tags,
                privacyStatus = normalizedPrivacy
            )

            // Step 1: Initiate resumable upload session
            val initUrl = "${youtubeUploadBaseUrl.trimEnd('/')}/videos?uploadType=resumable&part=snippet,status"
            val initRequest = Request.Builder()
                .url(initUrl)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/json")
                .header("X-Upload-Content-Length", videoBytes.size.toString())
                .header("X-Upload-Content-Type", "video/mp4")
                .post(metadataJson.toString().toRequestBody(jsonMediaType))
                .build()

            val uploadSessionUrl = okHttpClient.newCall(initRequest).execute().use { initResp ->
                val initBody = initResp.body?.string().orEmpty()
                if (!initResp.isSuccessful) {
                    val errMsg = extractGoogleApiError(initBody, initResp.code)
                    throw IllegalStateException("YouTube upload session init failed: $errMsg")
                }
                initResp.header("Location")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("YouTube resumable upload did not return a Location session URI.")
            }

            // Step 2: Upload MP4 video bytes to the resumable session URI
            val putRequest = Request.Builder()
                .url(uploadSessionUrl)
                .header("Authorization", "Bearer $cleanToken")
                .header("Content-Type", "video/mp4")
                .put(videoBytes.toRequestBody(mp4MediaType))
                .build()

            okHttpClient.newCall(putRequest).execute().use { putResp ->
                val putBody = putResp.body?.string().orEmpty()
                if (!putResp.isSuccessful) {
                    val errMsg = extractGoogleApiError(putBody, putResp.code)
                    throw IllegalStateException("YouTube MP4 byte transfer failed: $errMsg")
                }
                val resultJson = if (putBody.isNotBlank()) JSONObject(putBody) else JSONObject()
                val videoId = resultJson.optString("id").ifBlank { "yt_${System.currentTimeMillis()}" }
                YouTubeUploadResult(
                    videoId = videoId,
                    title = cleanTitle,
                    privacyStatus = normalizedPrivacy,
                    watchUrl = "https://youtu.be/$videoId",
                    studioUrl = "https://studio.youtube.com/video/$videoId/edit"
                )
            }
        }
    }

    private fun extractGoogleApiError(rawBody: String, httpCode: Int): String {
        return runCatching {
            val root = JSONObject(rawBody)
            val errObj = root.optJSONObject("error")
            errObj?.optString("message")?.takeIf { it.isNotBlank() }
                ?: root.optString("error_description").takeIf { it.isNotBlank() }
                ?: "HTTP $httpCode"
        }.getOrDefault("HTTP $httpCode")
    }

    companion object {
        const val DEFAULT_REDIRECT_URI = "com.aistudio.omniaistudio.rxkqpz:/oauth2redirect"
        const val YOUTUBE_UPLOAD_SCOPE =
            "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly"

        /**
         * Generates a high-entropy RFC 7636 PKCE `code_verifier` (43..128 URL-safe chars).
         */
        fun generateCodeVerifier(): String {
            val randomBytes = ByteArray(48)
            SecureRandom().nextBytes(randomBytes)
            return Base64.encodeToString(
                randomBytes,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

        /**
         * Computes the RFC 7636 S256 `code_challenge` = BASE64URL-ENCODE(SHA256(ASCII(code_verifier))).
         */
        fun generateCodeChallengeS256(codeVerifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
            return Base64.encodeToString(
                digest,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

        /**
         * Builds the Google OAuth 2.0 PKCE authorization URL that the user can open in their browser
         * to authorize `youtube.upload` access without embedding a client secret in the APK.
         */
        fun buildAuthorizationUrl(
            clientId: String,
            redirectUri: String = DEFAULT_REDIRECT_URI,
            codeChallenge: String,
            state: String = "omniai_yt_pkce"
        ): String {
            return Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
                .buildUpon()
                .appendQueryParameter("client_id", clientId.trim())
                .appendQueryParameter("redirect_uri", redirectUri.trim())
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", YOUTUBE_UPLOAD_SCOPE)
                .appendQueryParameter("code_challenge", codeChallenge.trim())
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent")
                .appendQueryParameter("state", state)
                .build()
                .toString()
        }

        internal fun buildVideoMetadataJson(
            title: String,
            description: String,
            tags: List<String>,
            privacyStatus: String,
            categoryId: String = "28" // Science & Technology
        ): JSONObject {
            val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
            val snippet = JSONObject().apply {
                put("title", title.trim().ifBlank { "Aerodock Video" }.take(100))
                put("description", description.trim())
                put("categoryId", categoryId)
                if (cleanTags.isNotEmpty()) {
                    put("tags", JSONArray(cleanTags))
                }
            }
            val status = JSONObject().apply {
                put("privacyStatus", privacyStatus.trim().lowercase())
                put("selfDeclaredMadeForKids", false)
            }
            return JSONObject().apply {
                put("snippet", snippet)
                put("status", status)
            }
        }
    }
}
