package com.example.data.workspace

import com.example.data.local.McpToolEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * `read_url`: the page reader for the built-in tool family — fetch one web
 * page and hand the model its readable text (title + tag-stripped body).
 * Deliberately NOT part of Live Info: that family caps model responses at
 * 2 KB for search snippets, while a docs page needs more room. Plain regex
 * extraction (no HTML parser dependency): script/style/nav blocks are
 * dropped, tags stripped, entities decoded, whitespace collapsed, output
 * capped. Good enough for documentation and reference pages; it is not a
 * browser and runs no JavaScript.
 */
object WorkspaceWeb {

    private const val MAX_PAGE_BYTES = 512_000
    private const val MAX_OUTPUT_CHARS = 12_000

    fun buildToolEntity(): McpToolEntity {
        return McpToolEntity(
            id = "${WorkspaceTools.WORKSPACE_SERVER_ID}:${WorkspaceTools.READ_URL_TOOL_NAME}",
            serverId = WorkspaceTools.WORKSPACE_SERVER_ID,
            serverName = WorkspaceTools.WORKSPACE_SERVER_NAME,
            toolName = WorkspaceTools.READ_URL_TOOL_NAME,
            description = "Fetch one web page and read its main text — documentation, a README, " +
                "an error reference, an article. Give the full URL. JavaScript-heavy pages may " +
                "come back sparse; search results are better found with web_search first, then " +
                "read the promising page with this.",
            inputSchemaJson = """{"type":"object","properties":{"url":{"type":"string","description":"The full http(s) URL to read."}},"required":["url"]}""",
            isReadOnlyHint = true,
            isEnabled = true
        )
    }

    fun execute(client: OkHttpClient, argumentsJson: String): Pair<String, Boolean> {
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrNull()
            ?: return "Error: arguments were not valid JSON." to false
        val url = args.optString("url").trim()
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return "Error: read_url needs a full http(s) URL." to false
        }
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Aerodock")
                .header("Accept", "text/html, text/plain")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return "Error: the page returned HTTP ${response.code}." to false
                }
                val contentType = response.header("Content-Type") ?: ""
                if (!contentType.contains("text/html", ignoreCase = true) &&
                    !contentType.contains("text/plain", ignoreCase = true)
                ) {
                    return "Error: '$url' is not a readable page (Content-Type: $contentType)." to false
                }
                val body = response.body ?: return "Error: the page had no content." to false
                val bytes = body.source().readByteArray(MAX_PAGE_BYTES.toLong())
                val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val raw = String(bytes, charset)
                val text = if (contentType.contains("text/html", ignoreCase = true)) {
                    extractReadableText(raw)
                } else {
                    raw.trim()
                }
                if (text.isBlank()) {
                    "The page at $url returned no readable text (it may need JavaScript)." to true
                } else {
                    val capped = if (text.length > MAX_OUTPUT_CHARS) {
                        text.substring(0, MAX_OUTPUT_CHARS) +
                            "\n[Page text truncated at $MAX_OUTPUT_CHARS characters.]"
                    } else {
                        text
                    }
                    capped to true
                }
            }
        }.getOrElse { e ->
            "Error: could not read the page: ${e.message ?: e.javaClass.simpleName}" to false
        }
    }

    private fun extractReadableText(html: String): String {
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let { decodeEntities(it.trim()) } ?: ""
        var text = html
        for (tag in listOf("script", "style", "noscript", "nav", "header", "footer", "svg")) {
            text = text.replace(
                Regex("<$tag\\b[^>]*>.*?</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
                " "
            )
        }
        text = text.replace(
            Regex("<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>", RegexOption.IGNORE_CASE),
            "\n"
        )
        text = text.replace(Regex("<[^>]+>"), " ")
        text = decodeEntities(text)
        val collapsed = text.lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return if (title.isNotBlank()) "Title: $title\n\n$collapsed" else collapsed
    }

    private fun decodeEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
            }
    }
}
