package com.example.data.provider

import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/**
 * Recovers tool calls that a model printed as TEXT inside its reply instead of
 * returning structured tool_calls. Free routes (e.g. LiteRouter ":free" models)
 * and abliterated local models do this often, in several dialects:
 *
 *  JSON: <tool_call> {"name": "web_search", "arguments": {"query": "..."}} </tool_call>
 *  XML:  <tool_call> <function=web_search> <parameter=query> ... </parameter> </function> </tool_call>
 *  INVOKE: the Anthropic/MiniMax spelling — the wrapper tag may carry any
 *        vendor prefix (<minimax:tool_call>, <antml:function_calls>, plain
 *        <tool_call> or <function_calls>) and holds one or more
 *        <invoke name="web_search"> <parameter name="query"> ... </parameter>
 *        </invoke> elements. Invokes are also recovered with no wrapper at
 *        all, because some models drop the wrapper mid-conversation.
 *  DSML: <｜DSML｜calls> <｜DSML｜invoke name="web_search"> <｜DSML｜parameter name="query" string="true"> ... </｜DSML｜parameter> </｜DSML｜invoke> </｜DSML｜calls>
 *        (DeepSeek's markup; the separator may be the fullwidth ｜ or an ASCII |.
 *        A parameter's string="false" means its content is raw JSON, not text.)
 *  BARE: a plain JSON object with no wrapper at all — {"name": ..., "arguments": ...},
 *        possibly inside a ``` fence. The name key may also be "tool" or
 *        "function", and the arguments key "parameters", "args" or "input".
 *
 * Sloppy generations get one repair pass before being given up on: a code
 * fence around the object, prose around the object inside its block, trailing
 * commas, and single-quoted strings are all repaired leniently.
 *
 * A block is honoured only when its name matches a tool that was offered for
 * the request; anything else (unknown names, malformed or unclosed blocks)
 * stays in the content as plain text. Honoured blocks are removed from the
 * returned content and handed back as ordinary [ModelToolCallRequest]s, so the
 * normal execution path (approvals, subagent inheritance filter) applies.
 */
object TextToolCallParser {

    const val MAX_EXTRACTED_CALLS = 8

    // The wrapper tag name is captured so the closer must repeat it: plain
    // <tool_call> and prefixed <minimax:tool_call> both match, with any prefix.
    private val BLOCK_REGEX =
        Regex("""<([A-Za-z0-9_.\-]*:?tool_call)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
    private val FUNCTION_CALLS_REGEX =
        Regex("""<([A-Za-z0-9_.\-]*:?function_calls)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
    private val INVOKE_REGEX = Regex(
        """<(?:[A-Za-z0-9_.\-]+:)?invoke\s+name="([^"]+)"[^>]*>(.*?)</(?:[A-Za-z0-9_.\-]+:)?invoke>""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val INVOKE_PARAMETER_REGEX = Regex(
        """<(?:[A-Za-z0-9_.\-]+:)?parameter\s+name="([^"]+)"[^>]*>(.*?)</(?:[A-Za-z0-9_.\-]+:)?parameter>""",
        RegexOption.DOT_MATCHES_ALL
    )
    // Wrapper debris left behind when a model drops or double-writes a tag;
    // stripped from the visible reply once at least one call was recovered.
    private val STRAY_WRAPPER_REGEX =
        Regex("""</?[A-Za-z0-9_.\-]*:?(tool_call|function_calls)>""")
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
    private val TRAILING_COMMA_REGEX = Regex(""",\s*([}\]])""")

    // Key spellings different model families use for the same two fields.
    private val NAME_KEYS = listOf("name", "tool", "function")
    private val ARGUMENT_KEYS = listOf("arguments", "parameters", "args", "input")

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
        if (text.contains("tool_call>")) {
            val pass = extractBlocks(text, BLOCK_REGEX, innerGroup = 2) { inner ->
                parseBlock(inner, offeredToolNames)
            }
            text = pass.first
            calls.addAll(pass.second)
        }
        if (calls.size < MAX_EXTRACTED_CALLS && text.contains("function_calls>")) {
            val pass = extractBlocks(text, FUNCTION_CALLS_REGEX, innerGroup = 2) { inner ->
                parseInvokeCalls(inner, offeredToolNames)
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
        if (calls.size < MAX_EXTRACTED_CALLS && text.contains("invoke")) {
            val pass = extractBareInvokes(text, offeredToolNames, MAX_EXTRACTED_CALLS - calls.size)
            text = pass.first
            calls.addAll(pass.second)
        }
        if (calls.size < MAX_EXTRACTED_CALLS && text.contains("{")) {
            val pass = extractBareJsonCalls(text, offeredToolNames, MAX_EXTRACTED_CALLS - calls.size)
            text = pass.first
            calls.addAll(pass.second)
        }
        if (calls.isEmpty()) return Extraction(content, emptyList())
        // Any wrapper tags still left are debris from blocks that were
        // recovered (double-written or unclosed tags); hide them from the user.
        text = STRAY_WRAPPER_REGEX.replace(text, "")
        return Extraction(text, calls.take(MAX_EXTRACTED_CALLS))
    }

    /**
     * True when a reply LOOKS like a failed tool-call attempt — it names an
     * offered tool and carries call-shaped markup — yet [extract] recovered
     * nothing from it. The tool loops use this to send one corrective retry
     * ("reply with ONLY the call, in exactly this shape") instead of ending
     * the turn on the model's broken text.
     */
    fun looksLikeAttemptedCall(content: String, offeredToolNames: Set<String>): Boolean {
        if (content.isBlank() || offeredToolNames.isEmpty()) return false
        if (offeredToolNames.none { content.contains(it) }) return false
        return content.contains("{") || content.contains("<function=") ||
            content.contains("tool_call") || content.contains("<invoke") ||
            content.contains("```")
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
        innerGroup: Int = 1,
        parse: (String) -> List<ModelToolCallRequest>
    ): Pair<String, List<ModelToolCallRequest>> {
        val calls = mutableListOf<ModelToolCallRequest>()
        val cleaned = StringBuilder()
        var copiedUpTo = 0
        for (match in blockRegex.findAll(content)) {
            if (calls.size >= MAX_EXTRACTED_CALLS) break
            val parsed = parse(match.groupValues[innerGroup])
            if (parsed.isEmpty()) continue
            cleaned.append(content, copiedUpTo, match.range.first)
            copiedUpTo = match.range.last + 1
            calls.addAll(parsed)
        }
        if (calls.isEmpty()) return content to emptyList()
        cleaned.append(content, copiedUpTo, content.length)
        return cleaned.toString() to calls
    }

    /**
     * The BARE dialect: scan the text for balanced {...} objects and honour
     * each one that reads as a call to an offered tool. An arguments key is
     * required here (unlike inside an explicit <tool_call> block): a bare
     * object in prose that merely names a tool is far more likely to be an
     * example the model is showing the user than a call it wants run.
     */
    private fun extractBareJsonCalls(
        content: String,
        offeredToolNames: Set<String>,
        limit: Int
    ): Pair<String, List<ModelToolCallRequest>> {
        val calls = mutableListOf<ModelToolCallRequest>()
        val cleaned = StringBuilder()
        var copiedUpTo = 0
        var searchFrom = 0
        while (calls.size < limit) {
            val span = findBalancedObject(content, searchFrom) ?: break
            val call = parseCallObject(
                content.substring(span.first, span.last + 1),
                offeredToolNames,
                requireArguments = true
            )
            if (call == null) {
                searchFrom = span.first + 1
                continue
            }
            cleaned.append(content, copiedUpTo, span.first)
            copiedUpTo = span.last + 1
            searchFrom = span.last + 1
            calls.add(call)
        }
        if (calls.isEmpty()) return content to emptyList()
        cleaned.append(content, copiedUpTo, content.length)
        return cleaned.toString() to calls
    }

    // String-aware scan for the next balanced {...} span at or after [from].
    // Only double-quoted strings are tracked; single-quoted (broken) JSON with
    // braces inside its strings can mis-split, and the parse then simply fails
    // and the span is skipped.
    private fun findBalancedObject(text: String, from: Int): IntRange? {
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        var i = from
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> if (depth > 0) inString = true
                    '{' -> {
                        if (depth == 0) start = i
                        depth++
                    }
                    '}' -> {
                        if (depth > 0) {
                            depth--
                            if (depth == 0 && start >= 0) return start..i
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun parseBlock(rawInner: String, offeredToolNames: Set<String>): List<ModelToolCallRequest> {
        val inner = rawInner.trim()
        if (inner.isEmpty()) return emptyList()
        if (inner.startsWith("{") || inner.startsWith("```")) {
            return parseJsonBlock(inner, offeredToolNames)?.let { listOf(it) } ?: emptyList()
        }
        if (inner.contains("<invoke") || inner.contains(":invoke")) {
            val invokes = parseInvokeCalls(inner, offeredToolNames)
            if (invokes.isNotEmpty()) return invokes
        }
        return parseXmlBlock(inner, offeredToolNames)?.let { listOf(it) } ?: emptyList()
    }

    // Every <invoke name="..."> element in [text] that names an offered tool,
    // with its <parameter name="..."> children as the arguments.
    private fun parseInvokeCalls(text: String, offeredToolNames: Set<String>): List<ModelToolCallRequest> {
        val calls = mutableListOf<ModelToolCallRequest>()
        for (match in INVOKE_REGEX.findAll(text)) {
            if (calls.size >= MAX_EXTRACTED_CALLS) break
            parseInvokeMatch(match, offeredToolNames)?.let { calls.add(it) }
        }
        return calls
    }

    private fun parseInvokeMatch(
        match: MatchResult,
        offeredToolNames: Set<String>
    ): ModelToolCallRequest? {
        val name = match.groupValues[1].trim()
        if (name.isEmpty() || name !in offeredToolNames) return null
        val args = JSONObject()
        for (param in INVOKE_PARAMETER_REGEX.findAll(match.groupValues[2])) {
            args.put(param.groupValues[1], coerceValue(unescapeXml(param.groupValues[2].trim())))
        }
        return newCall(name, args.toString())
    }

    /**
     * Invokes with no wrapper around them: some models emit the wrapper for
     * the first call of a turn, then bare <invoke> elements for the rest.
     * Only elements naming an offered tool are removed from the text.
     */
    private fun extractBareInvokes(
        content: String,
        offeredToolNames: Set<String>,
        limit: Int
    ): Pair<String, List<ModelToolCallRequest>> {
        val calls = mutableListOf<ModelToolCallRequest>()
        val cleaned = StringBuilder()
        var copiedUpTo = 0
        for (match in INVOKE_REGEX.findAll(content)) {
            if (calls.size >= limit) break
            val call = parseInvokeMatch(match, offeredToolNames) ?: continue
            cleaned.append(content, copiedUpTo, match.range.first)
            copiedUpTo = match.range.last + 1
            calls.add(call)
        }
        if (calls.isEmpty()) return content to emptyList()
        cleaned.append(content, copiedUpTo, content.length)
        return cleaned.toString() to calls
    }

    // Models writing calls as XML escape markup characters inside parameter
    // values (most often code containing < or &). Undo that one layer.
    private fun unescapeXml(raw: String): String {
        if (!raw.contains('&')) return raw
        return raw.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    private fun parseJsonBlock(inner: String, offeredToolNames: Set<String>): ModelToolCallRequest? {
        parseCallObject(inner, offeredToolNames, requireArguments = false)?.let { return it }
        // Prose may surround the object inside the block; try each balanced
        // object in turn before giving up on the block.
        var searchFrom = 0
        while (true) {
            val span = findBalancedObject(inner, searchFrom) ?: return null
            parseCallObject(
                inner.substring(span.first, span.last + 1),
                offeredToolNames,
                requireArguments = false
            )?.let { return it }
            searchFrom = span.first + 1
        }
    }

    /**
     * Reads one JSON object as a tool call: the first non-blank of the name
     * keys must name an offered tool, and the first present arguments key
     * supplies the arguments (an object, or a string holding one). With
     * [requireArguments] a missing arguments key rejects the object; without
     * it the call runs with no arguments.
     */
    private fun parseCallObject(
        raw: String,
        offeredToolNames: Set<String>,
        requireArguments: Boolean
    ): ModelToolCallRequest? {
        val json = parseJsonObjectLenient(raw) ?: return null
        val name = NAME_KEYS.firstNotNullOfOrNull { key ->
            json.optString(key).trim().takeIf { it.isNotEmpty() }
        } ?: return null
        if (name !in offeredToolNames) return null
        val argsKey = ARGUMENT_KEYS.firstOrNull { json.has(it) && !json.isNull(it) }
        if (argsKey == null) {
            return if (requireArguments) null else newCall(name, "{}")
        }
        val argumentsJson = when (val args = json.opt(argsKey)) {
            is JSONObject -> args.toString()
            // Some models nest the arguments object as a JSON string.
            is String -> parseJsonObjectLenient(args)?.toString() ?: return null
            else -> return null
        }
        return newCall(name, argumentsJson)
    }

    /**
     * Strict JSON first, then the repairs sloppy generations need: a wrapping
     * code fence stripped, trailing commas removed, and — only when the text
     * has no double quotes at all — single quotes treated as string quotes.
     */
    private fun parseJsonObjectLenient(raw: String): JSONObject? {
        var trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            // Drop the opening fence and its language tag, up to the newline
            // or the start of the object, whichever comes first.
            trimmed = trimmed.replaceFirst(Regex("""^```[^\n{]*\n?"""), "").trim()
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.dropLast(3).trim()
        }
        if (trimmed.isEmpty()) return null
        runCatching { JSONObject(trimmed) }.getOrNull()?.let { return it }
        val noTrailingCommas = TRAILING_COMMA_REGEX.replace(trimmed, "$1")
        runCatching { JSONObject(noTrailingCommas) }.getOrNull()?.let { return it }
        if (!trimmed.contains('"')) {
            val requoted = TRAILING_COMMA_REGEX.replace(trimmed.replace('\'', '"'), "$1")
            runCatching { JSONObject(requoted) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun parseXmlBlock(inner: String, offeredToolNames: Set<String>): ModelToolCallRequest? {
        val name = FUNCTION_REGEX.find(inner)?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isEmpty() || name !in offeredToolNames) return null
        val args = JSONObject()
        for (param in PARAMETER_REGEX.findAll(inner)) {
            args.put(param.groupValues[1], coerceValue(unescapeXml(param.groupValues[2].trim())))
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
