package com.example.data.integrations

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class GitHubUserProfile(
    val login: String,
    val name: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val publicRepos: Int
)

data class GitHubRepoSummary(
    val fullName: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val htmlUrl: String
)

data class GitHubPublishResult(
    val actionType: String, // "GIST" | "REPO_COMMIT"
    val title: String,
    val targetDestination: String,
    val htmlUrl: String
)

/**
 * Manual-only GitHub REST API v3 client for exporting conversations, prompts, or code blocks
 * to GitHub Gists or committing directly to a repository file path.
 *
 * Security & Policy:
 * - Never executes in the background or automatically. Every network call requires an explicit
 *   user confirmation click inside the GitHub Push Dialog.
 * - Uses `Authorization: Bearer <token>` over HTTPS; tokens are stored exclusively in `TinkSecretVault`.
 */
class GitHubIntegrationClient(
    private val okHttpClient: OkHttpClient,
    private val apiBaseUrl: String = "https://api.github.com"
) {
    private val jsonMediaType = "application/vnd.github+json; charset=utf-8".toMediaType()

    suspend fun verifyUser(token: String): Result<GitHubUserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "GitHub Personal Access Token (PAT) is empty." }

            val request = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/user")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OmniAI-Studio-Android")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching { JSONObject(rawBody).optString("message") }
                        .getOrDefault("")
                        .ifBlank { "HTTP ${response.code}" }
                    throw IllegalStateException("GitHub authentication failed: $errMsg")
                }
                val json = JSONObject(rawBody)
                GitHubUserProfile(
                    login = json.optString("login", "unknown"),
                    name = json.optString("name", "").ifBlank { json.optString("login", "GitHub User") },
                    avatarUrl = json.optString("avatar_url", ""),
                    htmlUrl = json.optString("html_url", "https://github.com"),
                    publicRepos = json.optInt("public_repos", 0)
                )
            }
        }
    }

    suspend fun listUserRepos(token: String): Result<List<GitHubRepoSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "GitHub token is required to list repositories." }

            val request = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/user/repos?sort=updated&per_page=30")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OmniAI-Studio-Android")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching { JSONObject(rawBody).optString("message") }
                        .getOrDefault("")
                        .ifBlank { "HTTP ${response.code}" }
                    throw IllegalStateException("Unable to list GitHub repositories: $errMsg")
                }
                val arr = JSONArray(rawBody)
                val repos = mutableListOf<GitHubRepoSummary>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val fullName = item.optString("full_name")
                    if (fullName.isNotBlank()) {
                        repos.add(
                            GitHubRepoSummary(
                                fullName = fullName,
                                defaultBranch = item.optString("default_branch", "main").ifBlank { "main" },
                                isPrivate = item.optBoolean("private", false),
                                htmlUrl = item.optString("html_url", "https://github.com/$fullName")
                            )
                        )
                    }
                }
                repos
            }
        }
    }

    suspend fun createGist(
        token: String,
        description: String,
        fileName: String,
        content: String,
        isPublic: Boolean
    ): Result<GitHubPublishResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "Configure a GitHub token in Settings -> Integrations first." }
            val safeFileName = fileName.trim().ifBlank { "omniai_export.md" }
            require(content.isNotBlank()) { "Cannot publish an empty Gist." }

            val payload = buildGistPayloadJson(
                description = description.trim().ifBlank { "Exported from Aerodock" },
                fileName = safeFileName,
                content = content,
                isPublic = isPublic
            )

            val request = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/gists")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OmniAI-Studio-Android")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching { JSONObject(rawBody).optString("message") }
                        .getOrDefault("")
                        .ifBlank { "HTTP ${response.code}" }
                    throw IllegalStateException("GitHub Gist creation failed: $errMsg (Ensure token has 'gist' scope)")
                }
                val json = JSONObject(rawBody)
                val htmlUrl = json.optString("html_url", "https://gist.github.com")
                GitHubPublishResult(
                    actionType = "GIST",
                    title = description.trim().ifBlank { safeFileName },
                    targetDestination = "${if (isPublic) "Public" else "Secret"} Gist • $safeFileName",
                    htmlUrl = htmlUrl
                )
            }
        }
    }

    suspend fun commitFileToRepo(
        token: String,
        ownerRepo: String,
        filePath: String,
        content: String,
        commitMessage: String,
        branch: String = "main"
    ): Result<GitHubPublishResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty()) { "Configure a GitHub token in Settings -> Integrations first." }
            val cleanRepo = ownerRepo.trim().removePrefix("/").removeSuffix("/")
            require(cleanRepo.contains("/") && cleanRepo.split("/").size == 2) {
                "Repository must be in 'owner/repo' format (e.g. octocat/ai-notes)."
            }
            val cleanPath = filePath.trim().removePrefix("/")
            require(cleanPath.isNotEmpty()) { "File path inside the repository is required." }
            val cleanBranch = branch.trim().ifBlank { "main" }

            // Check if file already exists on target branch to include its blob SHA for update
            val existingSha = fetchExistingFileSha(
                token = cleanToken,
                ownerRepo = cleanRepo,
                filePath = cleanPath,
                branch = cleanBranch
            )

            val payload = buildRepoCommitPayloadJson(
                content = content,
                commitMessage = commitMessage.trim().ifBlank { "Add $cleanPath via Aerodock" },
                branch = cleanBranch,
                existingSha = existingSha
            )

            val url = "${apiBaseUrl.trimEnd('/')}/repos/$cleanRepo/contents/$cleanPath"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OmniAI-Studio-Android")
                .put(payload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching { JSONObject(rawBody).optString("message") }
                        .getOrDefault("")
                        .ifBlank { "HTTP ${response.code}" }
                    throw IllegalStateException("GitHub commit failed: $errMsg (Ensure token has 'repo' or 'contents:write' permission)")
                }
                val json = JSONObject(rawBody)
                val contentObj = json.optJSONObject("content")
                val htmlUrl = contentObj?.optString("html_url")
                    ?.takeIf { it.isNotBlank() }
                    ?: "https://github.com/$cleanRepo/blob/$cleanBranch/$cleanPath"

                GitHubPublishResult(
                    actionType = "REPO_COMMIT",
                    title = commitMessage.trim().ifBlank { "Commit $cleanPath" },
                    targetDestination = "$cleanRepo ($cleanBranch) • $cleanPath",
                    htmlUrl = htmlUrl
                )
            }
        }
    }

    private fun fetchExistingFileSha(
        token: String,
        ownerRepo: String,
        filePath: String,
        branch: String
    ): String? {
        return runCatching {
            val url = "${apiBaseUrl.trimEnd('/')}/repos/$ownerRepo/contents/$filePath?ref=$branch"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OmniAI-Studio-Android")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                JSONObject(body).optString("sha").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        internal fun buildGistPayloadJson(
            description: String,
            fileName: String,
            content: String,
            isPublic: Boolean
        ): JSONObject {
            val filesObj = JSONObject().apply {
                put(
                    fileName,
                    JSONObject().apply {
                        put("content", content)
                    }
                )
            }
            return JSONObject().apply {
                put("description", description)
                put("public", isPublic)
                put("files", filesObj)
            }
        }

        internal fun buildRepoCommitPayloadJson(
            content: String,
            commitMessage: String,
            branch: String,
            existingSha: String? = null
        ): JSONObject {
            val base64Content = Base64.encodeToString(
                content.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            return JSONObject().apply {
                put("message", commitMessage)
                put("content", base64Content)
                put("branch", branch)
                if (!existingSha.isNullOrBlank()) {
                    put("sha", existingSha)
                }
            }
        }
    }
}
