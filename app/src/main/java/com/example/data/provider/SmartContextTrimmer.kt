package com.example.data.provider

import kotlin.math.roundToInt

/**
 * Smart Context Trimming:
 * - When input reaches about 60% of the model's context window (default 32,000 tokens if unknown),
 *   summarises older messages while keeping the last 6 turns, using the selected model.
 * - Keeps the summary under 800 tokens.
 * - Summaries are INCREMENTAL: once a summary exists, later sends reuse it and only the
 *   messages that aged out since then are folded into an updated summary, instead of
 *   re-summarising (and re-sending) the whole history on every message.
 * - Coverage bookkeeping rides inside the stored summary text itself as a plain footer
 *   line ("[Summary covers the first N messages of this chat.]"). The footer is stripped
 *   before anything is sent to a model, and it doubles as useful information in the
 *   Context Summary dialog, which displays the stored summary text.
 * - Token estimation counts media attachments (images/videos) as well as text, so chats
 *   full of screenshots are not invisible to the trimmer.
 * - [compactToolLoopMessages] bounds provider payloads (notably the agent/tool loop,
 *   which re-sends its working payload on every iteration) before they are serialised.
 * - Keeps the full message history visible in the app; only the outbound request uses the summary.
 * - If summarising fails, sends the full history and shows nothing extra.
 */
object SmartContextTrimmer {

    const val DEFAULT_CONTEXT_WINDOW_TOKENS: Int = 32_000
    const val TRIMMING_THRESHOLD_RATIO: Double = 0.60
    const val KEEP_RECENT_TURNS: Int = 6
    const val MAX_SUMMARY_TOKENS: Int = 800
    const val COMPOSER_NOTICE_LINE: String = "Older messages summarised to save context."
    const val COMPOSER_NOTICE_TEXT: String = COMPOSER_NOTICE_LINE

    // Flat per-item token estimates for media. Providers count images roughly in the
    // 85–6,000 token range depending on size and detail level; these mid-range values
    // keep the estimator honest without needing image dimensions at estimate time.
    const val IMAGE_TOKENS_ESTIMATE: Int = 1_600
    const val VIDEO_TOKENS_ESTIMATE: Int = 3_000

    // Payload compaction: tool results older than the most recent few exchanges are
    // stubbed once they pass this size, and any remaining oversized message is truncated.
    const val TOOL_RESULT_STUB_MAX_CHARS: Int = 600
    const val TOOL_RESULT_STUB_PREVIEW_CHARS: Int = 160
    const val LOOP_TRUNCATE_TRIGGER_CHARS: Int = 4_000
    const val LOOP_TRUNCATE_KEEP_CHARS: Int = 1_000
    const val LOOP_PROTECTED_RECENT_MESSAGES: Int = 4

    private val COVERAGE_FOOTER_REGEX =
        Regex("""\n*\[Summary covers the first (\d+) messages? of this chat\.\]\s*$""")

    data class ContextTrimResult(
        val outboundMessages: List<ChatMessagePayload>,
        val wasSummarised: Boolean,
        val summaryText: String?,
        val estimatedInputTokens: Int,
        val thresholdTokens: Int,
        /** How many leading history messages the summary covers (0 when there is no summary). */
        val summaryCoveredCount: Int = 0
    ) {
        val requestMessages: List<ChatMessagePayload>
            get() = outboundMessages
    }

    /**
     * Resolves a model's context window in tokens, defaulting to 32,000 if unknown.
     */
    fun resolveContextWindowTokens(contextWindowTokens: Int?): Int {
        return if (contextWindowTokens != null && contextWindowTokens > 0) {
            contextWindowTokens
        } else {
            DEFAULT_CONTEXT_WINDOW_TOKENS
        }
    }

    fun resolveContextWindowTokens(
        modelId: String,
        explicitContextWindow: Int? = null
    ): Int {
        if (explicitContextWindow != null && explicitContextWindow > 0) {
            return explicitContextWindow
        }
        val lower = modelId.trim().lowercase().substringAfterLast("/")
        return when {
            lower.isEmpty() || lower == "unknown" || lower == "default" -> DEFAULT_CONTEXT_WINDOW_TOKENS
            lower.startsWith("gpt-4.1") -> 128_000
            lower.startsWith("gpt-4o") -> 128_000
            lower.startsWith("gpt-4-turbo") -> 128_000
            lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4") -> 128_000
            lower.startsWith("claude-3") || lower.startsWith("claude-sonnet") || lower.startsWith("claude-opus") -> 200_000
            lower.startsWith("gemini-2.5") || lower.startsWith("gemini-1.5") || lower.startsWith("gemini-2.0") -> 128_000
            lower.startsWith("deepseek-") -> 64_000
            lower.startsWith("llama-3") -> 128_000
            lower.startsWith("mistral-large") -> 128_000
            else -> DEFAULT_CONTEXT_WINDOW_TOKENS
        }
    }

    /**
     * Computes the 60% token threshold for a given context window (default 32,000 -> 19,200 tokens).
     */
    fun calculateTrimmingThresholdTokens(
        contextWindowTokens: Int? = DEFAULT_CONTEXT_WINDOW_TOKENS
    ): Int {
        val window = resolveContextWindowTokens(contextWindowTokens)
        return (window * TRIMMING_THRESHOLD_RATIO).roundToInt()
    }

    /**
     * Returns true when [estimatedInputTokens] reaches at least 60% of [contextWindowTokens]
     * (defaulting to 32,000 tokens if unknown or <= 0).
     */
    fun shouldTrimContext(
        estimatedInputTokens: Int,
        contextWindowTokens: Int? = DEFAULT_CONTEXT_WINDOW_TOKENS
    ): Boolean {
        val threshold = calculateTrimmingThresholdTokens(contextWindowTokens)
        return estimatedInputTokens >= threshold
    }

    /**
     * Estimates the tokens a single message contributes: its text, any inline image,
     * media attachments (flat per-item estimates) and tool calls. Media MUST be counted —
     * a screenshot-heavy chat is mostly media, and text-only estimation let such chats
     * sail far past the trimming threshold unnoticed.
     */
    fun estimateMessageTokens(msg: ChatMessagePayload): Int {
        var total = ModelPriceTable.estimateTokensFromText(msg.content)
        if (!msg.imageDataUri.isNullOrBlank()) {
            total += IMAGE_TOKENS_ESTIMATE
        }
        for (media in msg.mediaAttachments) {
            total += if (media.isVideo) VIDEO_TOKENS_ESTIMATE else IMAGE_TOKENS_ESTIMATE
        }
        for (tc in msg.toolCalls) {
            total += ModelPriceTable.estimateTokensFromText(tc.toolName + " " + tc.argumentsJson)
        }
        return total
    }

    /**
     * Estimates total input tokens across the system prompt and conversation messages.
     */
    fun estimateInputTokens(
        systemPrompt: String,
        messages: List<ChatMessagePayload>
    ): Int {
        var total = if (systemPrompt.isNotBlank()) {
            ModelPriceTable.estimateTokensFromText(systemPrompt)
        } else 0
        for (msg in messages) {
            total += estimateMessageTokens(msg)
        }
        return total
    }

    /**
     * Splits conversation history into (olderMessagesToSummarise, recentMessagesToKeep),
     * keeping the last [keepTurns] turns (default 6 turns).
     */
    fun splitOlderAndRecentTurns(
        messages: List<ChatMessagePayload>,
        keepTurns: Int = KEEP_RECENT_TURNS
    ): Pair<List<ChatMessagePayload>, List<ChatMessagePayload>> {
        if (messages.isEmpty() || keepTurns <= 0) {
            return emptyList<ChatMessagePayload>() to messages
        }

        val userIndices = messages.mapIndexedNotNull { index, msg ->
            if (msg.role.equals("user", ignoreCase = true)) index else null
        }

        val splitIndex = when {
            userIndices.size > keepTurns -> userIndices[userIndices.size - keepTurns]
            messages.size > keepTurns * 2 -> messages.size - (keepTurns * 2)
            messages.size > keepTurns -> messages.size - keepTurns
            else -> 0
        }

        if (splitIndex <= 0) {
            return emptyList<ChatMessagePayload>() to messages
        }

        val older = messages.subList(0, splitIndex)
        val recent = messages.subList(splitIndex, messages.size)
        return older to recent
    }

    /**
     * Enforces that the summary stays strictly under [maxTokens] (default 800 tokens).
     */
    fun enforceSummaryTokenLimit(
        rawSummary: String,
        maxTokens: Int = MAX_SUMMARY_TOKENS
    ): String {
        val trimmed = rawSummary.trim()
        if (trimmed.isEmpty()) return ""
        val maxSummaryBudget = (maxTokens - 1).coerceAtLeast(1) // strictly under 800 tokens
        val currentTokens = ModelPriceTable.estimateTokensFromText(trimmed)
        if (currentTokens <= maxSummaryBudget) {
            return trimmed
        }
        val maxChars = (maxSummaryBudget * 4).coerceAtLeast(4)
        return trimmed.take(maxChars).trimEnd()
    }

    /**
     * Builds the prompt sent to the selected model to summarise older messages under 800 tokens.
     */
    fun buildSummarisationPrompt(olderMessages: List<ChatMessagePayload>): String {
        return buildString {
            append("Summarise the following earlier conversation messages concisely in under 600 tokens. ")
            append("Preserve key facts, decisions, user preferences, file names, and code context:\n\n")
            olderMessages.forEach { msg ->
                append(msg.role.uppercase()).append(": ").append(msg.content.take(4000)).append("\n\n")
            }
        }
    }

    /**
     * Builds the prompt for an INCREMENTAL summary update: the model receives the existing
     * summary of the earliest messages plus only the messages that aged out since that
     * summary was made, and merges them into one updated summary under 800 tokens.
     */
    fun buildIncrementalSummarisationPrompt(
        existingSummary: String,
        newlyOlderMessages: List<ChatMessagePayload>
    ): String {
        return buildString {
            append("You are updating a running summary of a long conversation. ")
            append("Merge the existing summary and the newer messages below into ONE updated summary, ")
            append("concisely, in under 600 tokens. Preserve key facts, decisions, user preferences, ")
            append("file names, and code context. Drop anything the newer messages supersede.\n\n")
            append("EXISTING SUMMARY:\n").append(existingSummary.trim()).append("\n\n")
            append("NEWER MESSAGES TO FOLD IN:\n")
            newlyOlderMessages.forEach { msg ->
                append(msg.role.uppercase()).append(": ").append(msg.content.take(4000)).append("\n\n")
            }
        }
    }

    /**
     * Splits a stored summary into (cleanSummaryText, coveredMessageCount) by removing the
     * coverage footer. Summaries stored before footers existed return a count of 0.
     */
    fun parseStoredSummary(storedSummary: String?): Pair<String, Int> {
        val raw = storedSummary?.trim().orEmpty()
        if (raw.isEmpty()) return "" to 0
        val match = COVERAGE_FOOTER_REGEX.find(raw) ?: return raw to 0
        val covered = match.groupValues[1].toIntOrNull() ?: 0
        val clean = raw.substring(0, match.range.first).trim()
        return clean to covered
    }

    /** The stored form of a summary: clean text plus its coverage footer line. */
    fun buildStoredSummary(cleanSummary: String, coveredCount: Int): String {
        val clean = cleanSummary.trim()
        if (clean.isEmpty() || coveredCount <= 0) return clean
        val noun = if (coveredCount == 1) "message" else "messages"
        return "$clean\n\n[Summary covers the first $coveredCount $noun of this chat.]"
    }

    private fun summaryPayload(summaryText: String): ChatMessagePayload {
        return ChatMessagePayload(
            role = "user",
            content = "[Summary of earlier messages]\n$summaryText"
        )
    }

    /**
     * Checks whether input tokens reach ~60% of the context window and, if so and [trimmingEnabledForChat]
     * is true, summarises older messages keeping the last 6 turns.
     *
     * Incremental behaviour: a previously stored summary (passed as [existingCachedSummary])
     * carries how many leading messages it covers, either via [cachedSummaryCoveredCount]
     * or via its own coverage footer. While the not-yet-summarised tail stays under the
     * threshold, the cached summary is reused untouched — no summarisation request and no
     * history re-send. When the tail itself grows past the threshold, only the newly
     * aged-out messages are folded into an updated summary.
     *
     * If summarising fails or returns blank, sends the full history and shows nothing extra.
     */
    suspend fun trimIfNeeded(
        fullMessages: List<ChatMessagePayload>,
        systemPrompt: String = "",
        modelContextWindowTokens: Int? = DEFAULT_CONTEXT_WINDOW_TOKENS,
        trimmingEnabledForChat: Boolean = true,
        existingCachedSummary: String? = null,
        cachedSummaryCoveredCount: Int = 0,
        keepTurns: Int = KEEP_RECENT_TURNS,
        summariseOlderMessages: suspend (String) -> String
    ): ContextTrimResult {
        val windowTokens = resolveContextWindowTokens(modelContextWindowTokens)
        val threshold = calculateTrimmingThresholdTokens(windowTokens)
        val estimatedInput = estimateInputTokens(systemPrompt, fullMessages)

        if (!trimmingEnabledForChat) {
            return ContextTrimResult(
                outboundMessages = fullMessages,
                wasSummarised = false,
                summaryText = null,
                estimatedInputTokens = estimatedInput,
                thresholdTokens = threshold
            )
        }

        val (cleanCachedSummary, footerCoveredCount) = parseStoredSummary(existingCachedSummary)
        val effectiveCoveredCount = when {
            cachedSummaryCoveredCount in 1..fullMessages.size -> cachedSummaryCoveredCount
            footerCoveredCount in 1..fullMessages.size -> footerCoveredCount
            else -> 0
        }
        val hasValidCache = cleanCachedSummary.isNotBlank() && effectiveCoveredCount > 0

        if (hasValidCache) {
            val tail = fullMessages.drop(effectiveCoveredCount)
            val cachedPayload = summaryPayload(cleanCachedSummary)
            val tailTokens = estimateInputTokens(systemPrompt, listOf(cachedPayload) + tail)
            val storedForm = buildStoredSummary(cleanCachedSummary, effectiveCoveredCount)

            if (tailTokens < threshold) {
                // Steady state: reuse the cached summary untouched and send only the
                // not-yet-summarised tail. No summarisation call, no history re-send.
                return ContextTrimResult(
                    outboundMessages = listOf(cachedPayload) + tail,
                    wasSummarised = true,
                    summaryText = storedForm,
                    estimatedInputTokens = estimatedInput,
                    thresholdTokens = threshold,
                    summaryCoveredCount = effectiveCoveredCount
                )
            }

            // The tail itself overflowed: fold its older part into the cached summary.
            val (olderTail, recentTail) = splitOlderAndRecentTurns(tail, keepTurns)
            if (olderTail.isEmpty()) {
                return ContextTrimResult(
                    outboundMessages = listOf(cachedPayload) + tail,
                    wasSummarised = true,
                    summaryText = storedForm,
                    estimatedInputTokens = estimatedInput,
                    thresholdTokens = threshold,
                    summaryCoveredCount = effectiveCoveredCount
                )
            }

            val mergePrompt = buildIncrementalSummarisationPrompt(cleanCachedSummary, olderTail)
            val mergedResult = runCatching { summariseOlderMessages(mergePrompt) }
            val rawMerged = mergedResult.getOrNull()?.trim().orEmpty()
            if (mergedResult.isFailure || rawMerged.isBlank()) {
                // Keep serving the cached summary + tail rather than the full history.
                return ContextTrimResult(
                    outboundMessages = listOf(cachedPayload) + tail,
                    wasSummarised = true,
                    summaryText = storedForm,
                    estimatedInputTokens = estimatedInput,
                    thresholdTokens = threshold,
                    summaryCoveredCount = effectiveCoveredCount
                )
            }

            val cappedMerged = enforceSummaryTokenLimit(rawMerged, MAX_SUMMARY_TOKENS)
            val newCoveredCount = effectiveCoveredCount + olderTail.size
            return ContextTrimResult(
                outboundMessages = listOf(summaryPayload(cappedMerged)) + recentTail,
                wasSummarised = true,
                summaryText = buildStoredSummary(cappedMerged, newCoveredCount),
                estimatedInputTokens = estimatedInput,
                thresholdTokens = threshold,
                summaryCoveredCount = newCoveredCount
            )
        }

        if (!shouldTrimContext(estimatedInput, windowTokens)) {
            return ContextTrimResult(
                outboundMessages = fullMessages,
                wasSummarised = false,
                summaryText = null,
                estimatedInputTokens = estimatedInput,
                thresholdTokens = threshold
            )
        }

        val (older, recent) = splitOlderAndRecentTurns(fullMessages, keepTurns)
        if (older.isEmpty()) {
            return ContextTrimResult(
                outboundMessages = fullMessages,
                wasSummarised = false,
                summaryText = null,
                estimatedInputTokens = estimatedInput,
                thresholdTokens = threshold
            )
        }

        val prompt = buildSummarisationPrompt(older)
        val summaryResult = runCatching { summariseOlderMessages(prompt) }

        val rawSummary = summaryResult.getOrNull()?.trim().orEmpty()
        if (summaryResult.isFailure || rawSummary.isBlank()) {
            // If summarising fails, send the full history and show nothing extra
            return ContextTrimResult(
                outboundMessages = fullMessages,
                wasSummarised = false,
                summaryText = null,
                estimatedInputTokens = estimatedInput,
                thresholdTokens = threshold
            )
        }

        val cappedSummary = enforceSummaryTokenLimit(rawSummary, MAX_SUMMARY_TOKENS)
        return ContextTrimResult(
            outboundMessages = listOf(summaryPayload(cappedSummary)) + recent,
            wasSummarised = true,
            summaryText = buildStoredSummary(cappedSummary, older.size),
            estimatedInputTokens = estimatedInput,
            thresholdTokens = threshold,
            summaryCoveredCount = older.size
        )
    }

    /**
     * Bounds a provider-bound payload to [maxEstimatedTokens].
     *
     * trimIfNeeded runs once per user message, but the agent/tool loop then re-sends its
     * working payload on every iteration while tool results keep growing it — with high or
     * unlimited iteration settings that multiplies into runaway token counts. Providers call
     * this right before serialising a request, so every iteration is bounded. Message order,
     * roles and tool-call structure are preserved (provider tool-call pairing stays valid);
     * only content shrinks, in stages, re-checking the estimate after each stage:
     *  1. Oversized tool results outside the most recent messages are replaced with a stub.
     *  2. Media attachments on older messages are dropped (already seen in earlier turns).
     *  3. Remaining oversized message bodies are truncated.
     */
    fun compactToolLoopMessages(
        messages: List<ChatMessagePayload>,
        maxEstimatedTokens: Int,
        systemPrompt: String = ""
    ): List<ChatMessagePayload> {
        if (messages.isEmpty() || maxEstimatedTokens <= 0) return messages
        if (estimateInputTokens(systemPrompt, messages) <= maxEstimatedTokens) {
            return messages
        }

        val protectedFromIndex = (messages.size - LOOP_PROTECTED_RECENT_MESSAGES).coerceAtLeast(0)

        // Stage 1: stub oversized tool results on unprotected messages.
        var compacted = messages.mapIndexed { index, msg ->
            if (index < protectedFromIndex &&
                msg.role.equals("tool", ignoreCase = true) &&
                msg.content.length > TOOL_RESULT_STUB_MAX_CHARS
            ) {
                val toolLabel = msg.toolName?.takeIf { it.isNotBlank() } ?: "tool"
                msg.copy(
                    content = "[Tool result trimmed to save context — $toolLabel output, " +
                        "${msg.content.length} chars]\n" +
                        msg.content.take(TOOL_RESULT_STUB_PREVIEW_CHARS)
                )
            } else {
                msg
            }
        }
        if (estimateInputTokens(systemPrompt, compacted) <= maxEstimatedTokens) {
            return compacted
        }

        // Stage 2: drop media attachments on unprotected messages.
        compacted = compacted.mapIndexed { index, msg ->
            if (index < protectedFromIndex &&
                (msg.imageDataUri != null || msg.mediaAttachments.isNotEmpty())
            ) {
                msg.copy(imageDataUri = null, mediaAttachments = emptyList())
            } else {
                msg
            }
        }
        if (estimateInputTokens(systemPrompt, compacted) <= maxEstimatedTokens) {
            return compacted
        }

        // Stage 3: truncate any remaining oversized bodies outside the last two messages.
        val hardProtectFrom = (compacted.size - 2).coerceAtLeast(0)
        return compacted.mapIndexed { index, msg ->
            if (index >= hardProtectFrom || msg.content.length <= LOOP_TRUNCATE_TRIGGER_CHARS) {
                msg
            } else {
                msg.copy(
                    content = msg.content.take(LOOP_TRUNCATE_KEEP_CHARS) +
                        "\n[…trimmed to save context…]"
                )
            }
        }
    }
}
