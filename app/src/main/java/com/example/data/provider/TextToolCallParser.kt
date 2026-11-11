package com.example.data.provider

import org.json.JSONObject
import java.util.UUID

/**
 * Recovers tool calls that a model printed as TEXT inside its reply instead of
 * returning structured tool_calls. Free routes (e.g. LiteRouter ":free" models)
 * do this intermittently, in two Qwen dialects:
 *
 *  JSON: <tool_call> {"name": "web_search", "arguments": {"query": "..."}} </tool_call>
 *  XML:  <tool_call> <function=web_search> <parameter=query> ... </parameter> </function> </tool_call>
 *
 * A block is honoured only when its name matches a tool that was offered for
 * the request; anything else (unknown names, malformed or unclosed blocks)
 * stays in the content as plain text. Honoured blocks are removed from the
 * returned content and handed back as ordinary [ModelToolCallRequest]s, so the
 * normal execution path (approvals, subagent inheritance filter) applies.
 */
object TextToolCallParser {

    const val MAX_EXTRACTED_CALLS = 8

    private val BLOCK_REGEX = Regex("""<tool_call>(.*?)</tool_call>""", RegexOption.DOT_MATCHES_ALL)
    private val FUNCTION_REGEX = Regex("""<function=([A-Za-z0-9_.\-]+)>""")
    private val PARAMETER_REGEX =
        Regex("""<parameter=([A-Za-z0-9_.\-]+)>(.*?)</parameter>""", RegexOption.DOT_MATCHES_ALL)
    private val INTEGER_REGEX = Regex("""-?\d+""")
    private val DECIMAL_REGEX = Regex("""-?(\d+\.\d*|\.\d+|\d+)([eE][+-]?\d+)?""")

    data class Extraction(
        val cleanedContent: String,
        val calls: List<ModelToolCallRequest>
    )

    fun extract(content: String, offeredToolNames: Set<String>): Extraction {
        if (content.isBlank() || offeredToolNames.isEmpty() || !content.contains("<tool_call>")) {
            return Extraction(content, emptyList())
        }
        val calls = mutableListOf<ModelToolCallRequest>()
        val cleaned = StringBuilder()
        var copiedUpTo = 0
        for (match in BLOCK_REGEX.findAll(content)) {
            if (calls.size >= MAX_EXTRACTED_CALLS) break
            // A block that does not parse to an offered tool is left in the text:
            // copiedUpTo is not advanced past it, so it is copied verbatim later.
            val call = parseBlock(match.groupValues[1], offeredToolNames) ?: continue
            cleaned.append(content, copiedUpTo, match.range.first)
            copiedUpTo = match.range.last + 1
            calls.add(call)
        }
        if (calls.isEmpty()) return Extraction(content, emptyList())
        cleaned.append(content, copiedUpTo, content.length)
        return Extraction(cleaned.toString(), calls)
    }

    private fun parseBlock(rawInner: String, offeredToolNames: Set<String>): ModelToolCallRequest? {
        val inner = rawInner.trim()
        if (inner.isEmpty()) return null
        return if (inner.startsWith("{")) {
            parseJsonBlock(inner, offeredToolNames)
        } else {
            parseXmlBlock(inner, offeredToolNames)
        }
    }

    private fun parseJsonBlock(inner: String, offeredToolNames: Set<String>): ModelToolCallRequest? {
        val json = runCatching { JSONObject(inner) }.getOrNull() ?: return null
        val name = json.optString("name").trim()
        if (name.isEmpty() || name !in offeredToolNames) return null
        val argumentsJson = when (val args = json.opt("arguments")) {
            null, JSONObject.NULL -> "{}"
            is JSONObject -> args.toString()
            // Some models nest the arguments object as a JSON string.
            is String -> runCatching { JSONObject(args).toString() }.getOrNull() ?: return null
            else -> return null
        }
        return newCall(name, argumentsJson)
    }

    private fun parseXmlBlock(inner: String, offeredToolNames: Set<String>): ModelToolCallRequest? {
        val name = FUNCTION_REGEX.find(inner)?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isEmpty() || name !in offeredToolNames) return null
        val args = JSONObject()
        for (param in PARAMETER_REGEX.findAll(inner)) {
            args.put(param.groupValues[1], coerceValue(param.groupValues[2].trim()))
        }
        return newCall(name, args.toString())
    }

    // XML parameter values are raw text: keep them as strings, except clean
    // booleans and numbers, which tools expect typed.
    private fun coerceValue(raw: String): Any {
        return when {
            raw.equals("true", ignoreCase = true) -> true
            raw.equals("false", ignoreCase = true) -> false
            INTEGER_REGEX.matches(raw) -> raw.toLongOrNull() ?: raw
            DECIMAL_REGEX.matches(raw) -> raw.toDoubleOrNull() ?: raw
            else -> raw
        }
    }

    private fun newCall(name: String, argumentsJson: String): ModelToolCallRequest {
        return ModelToolCallRequest(
            id = "call_text_" + UUID.randomUUID().toString().take(8),
            toolName = name,
            argumentsJson = argumentsJson
        )
    }
}
