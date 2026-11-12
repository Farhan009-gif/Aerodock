package com.example.data.provider

import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/**
 * Recovers tool calls that a model printed as TEXT inside its reply instead of
 * returning structured tool_calls. Free routes (e.g. LiteRouter ":free" models)
 * do this intermittently, in three dialects:
 *
 *  JSON: <tool_call> {"name": "web_search", "arguments": {"query": "..."}} </tool_call>
 *  XML:  <tool_call> <function=web_search> <parameter=query> ... </parameter> </function> </tool_call>
 *  DSML: <｜DSML｜calls> <｜DSML｜invoke name="web_search"> <｜DSML｜parameter name="query" string="true"> ... </｜DSML｜parameter> </｜DSML｜invoke> </｜DSML｜calls>
 *        (DeepSeek's markup; the separator may be the fullwidth ｜ or an ASCII |.
 *        A parameter's string="false" means its content is raw JSON, not text.)
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
    private val DSML_CALLS_REGEX =
        Regex("""<[|｜]DSML[|｜]calls>(.*?)</[|｜]DSML[|｜]calls>""", RegexOption.DOT_MATCHES_ALL)
    private val DSML_INVOKE_REGEX =
        Regex("""<[|｜]DSML[|｜]invoke\s+name="([^"]+)"\s*>(.*?)</[|｜]DSML[|｜]invoke>""", RegexOption.DOT_MATCHES_ALL)
    private val DSML_PARAMETER_REGEX =
        Regex(
            """<[|｜]DSML[|｜]parameter\s+name="([^"]+)"(?:\s+string="(true|false)")?\s*>(.*?)</[|｜]DSML[|｜]parameter>""",
            RegexOption.DOT_MATCHES_ALL
        )
    private val INTEGER_REGEX = Regex("""-?\d+""")
    private val DECIMAL_REGEX = Regex("""-?(\d+\.\d*|\.\d+|\d+)([eE][+-]?\d+)?""")

    data class Extraction(
        val cleanedContent: String,
        val calls: List<ModelToolCallRequest>
    )

    fun extract(content: String, offeredToolNames: Set<String>): Extraction {
        if (content.isBlank() || offeredToolNames.isEmpty()) {
            return Extraction(content, emptyList())
        }
        val calls = mutableListOf<ModelToolCallRequest>()
        var text = content
        if (text.contains("<tool_call>")) {
            val pass = extractBlocks(text, BLOCK_REGEX) { inner ->
                parseBlock(inner, offeredToolNames)?.let { listOf(it) } ?: emptyList()
            }
            text = pass.first
            calls.addAll(pass.second)
        }
        if (calls.size < MAX_EXTRACTED_CALLS && text.contains("DSML")) {
            val pass = extractBlocks(text, DSML_CALLS_REGEX) { inner ->
                parseDsmlBlock(inner, offeredToolNames)
            }
            text = pass.first
            calls.addAll(pass.second)
        }
        if (calls.isEmpty()) return Extraction(content, emptyList())
        return Extraction(text, calls.take(MAX_EXTRACTED_CALLS))
    }

    /**
     * Removes every block of one dialect that parses to at least one offered
     * tool and returns the cleaned text with the recovered calls. A block that
     * does not parse is left in the text: the copy cursor is not advanced past
     * it, so it is copied verbatim later.
     */
    private fun extractBlocks(
        content: String,
        blockRegex: Regex,
        parse: (String) -> List<ModelToolCallRequest>
    ): Pair<String, List<ModelToolCallRequest>> {
        val calls = mutableListOf<ModelToolCallRequest>()
        val cleaned = StringBuilder()
        var copiedUpTo = 0
        for (match in blockRegex.findAll(content)) {
            if (calls.size >= MAX_EXTRACTED_CALLS) break
            val parsed = parse(match.groupValues[1])
            if (parsed.isEmpty()) continue
            cleaned.append(content, copiedUpTo, match.range.first)
            copiedUpTo = match.range.last + 1
            calls.addAll(parsed)
        }
        if (calls.isEmpty()) return content to emptyList()
        cleaned.append(content, copiedUpTo, content.length)
        return cleaned.toString() to calls
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

    /**
     * One DSML <｜DSML｜calls> block can carry several invokes. An invoke whose
     * name was not offered is skipped; the block is still consumed as long as
     * one invoke parsed (the executed results ground the model's next turn).
     */
    private fun parseDsmlBlock(inner: String, offeredToolNames: Set<String>): List<ModelToolCallRequest> {
        val calls = mutableListOf<ModelToolCallRequest>()
        for (invoke in DSML_INVOKE_REGEX.findAll(inner)) {
            if (calls.size >= MAX_EXTRACTED_CALLS) break
            val name = invoke.groupValues[1].trim()
            if (name.isEmpty() || name !in offeredToolNames) continue
            val args = JSONObject()
            for (param in DSML_PARAMETER_REGEX.findAll(invoke.groupValues[2])) {
                val rawValue = param.groupValues[3].trim()
                // string="false" marks a raw-JSON value; "true" or absent is text.
                val value = if (param.groupValues[2] == "false") parseJsonValue(rawValue) else rawValue
                args.put(param.groupValues[1], value)
            }
            calls.add(newCall(name, args.toString()))
        }
        return calls
    }

    // DSML string="false" values are raw JSON (arrays, objects, numbers, booleans).
    private fun parseJsonValue(raw: String): Any {
        val parsed = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        return parsed ?: coerceValue(raw)
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
