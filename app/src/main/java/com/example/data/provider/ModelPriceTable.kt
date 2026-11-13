package com.example.data.provider

import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Single centralized per-model price table for Runway.
 *
 * Prices are stored in USD per 1,000,000 (1M) input/output/cached-input tokens so the table is easy
 * to read and update in one place, with helper properties converting to per-1,000 (1K) tokens.
 * Also marks top-tier models and their cheaper alternatives for the Cheaper Model Suggestion card.
 */
data class ModelPriceEntry(
    val modelPattern: String,
    val displayName: String,
    val inputUsdPer1M: Double,
    val outputUsdPer1M: Double,
    val cachedInputUsdPer1M: Double = inputUsdPer1M * 0.5,
    val isTopTier: Boolean = false,
    val suggestedCheaperModelId: String? = null
) {
    val inputUsdPer1K: Double get() = inputUsdPer1M / 1000.0
    val outputUsdPer1K: Double get() = outputUsdPer1M / 1000.0
    val cachedInputUsdPer1K: Double get() = cachedInputUsdPer1M / 1000.0
}

/**
 * Dismissible cheaper model suggestion shown after a reply when the chat has used over 50,000 tokens
 * and the active model is top-tier.
 */
data class CheaperModelSuggestion(
    val currentModelId: String,
    val suggestedModelId: String,
    val suggestedDisplayName: String = suggestedModelId,
    val savingsPercent: Int = 50,
    val cardText: String = "Try $suggestedDisplayName to save about $savingsPercent%",
    val suggestedModelDisplayName: String = suggestedDisplayName
) {
    val bannerText: String get() = cardText
}

/**
 * Token usage extracted from a provider response (OpenAI, Anthropic, or Gemini),
 * including cached input tokens when reported.
 */
data class ProviderTokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val isPresent: Boolean,
    val cachedInputTokens: Int = 0
)

/**
 * Resolved usage and cost for a single assistant message or turn.
 */
data class ResolvedMessageUsage(
    val modelId: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val isTokenCountEstimated: Boolean,
    val estimatedCostUsd: Double,
    val cachedInputTokens: Int = 0
) {
    val totalTokens: Int get() = inputTokens + outputTokens

    fun formatMessageFooter(currency: String = "USD"): String {
        return ModelPriceTable.formatMessageUsageFooter(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            estimatedCostUsd = estimatedCostUsd,
            isTokenCountEstimated = isTokenCountEstimated,
            currency = currency,
            cachedInputTokens = cachedInputTokens
        )
    }
}

/**
 * Aggregated usage summary for an entire conversation, including assistant turns,
 * MCP tool calls, sandboxed JavaScript (`run_code`) runs, and cached tokens.
 */
data class ConversationUsageSummary(
    val conversationId: String,
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalEstimatedCostUsd: Double = 0.0,
    val toolCallsCount: Int = 0,
    val codeRunsCount: Int = 0,
    val hasEstimatedTokens: Boolean = false,
    val totalCachedInputTokens: Int = 0
) {
    val totalTokens: Int get() = totalInputTokens + totalOutputTokens

    fun formatConversationFooter(currency: String = "USD"): String {
        return ModelPriceTable.formatConversationUsageSummary(
            totalInputTokens = totalInputTokens,
            totalOutputTokens = totalOutputTokens,
            totalEstimatedCostUsd = totalEstimatedCostUsd,
            toolCallsCount = toolCallsCount,
            codeRunsCount = codeRunsCount,
            hasEstimatedTokens = hasEstimatedTokens,
            currency = currency,
            totalCachedInputTokens = totalCachedInputTokens
        )
    }
}

object ModelPriceTable {

    private const val USD_TO_INR_RATE = 86.5
    const val CHEAPER_MODEL_SUGGESTION_TOKEN_THRESHOLD: Int = 50_000

    /**
     * Centralized per-model pricing table (USD per 1M input, output, and cached-input tokens).
     * Order goes from most specific model IDs to broader family prefixes.
     */
    val PRICE_TABLE: List<ModelPriceEntry> = listOf(
        // OpenAI Models
        ModelPriceEntry(
            modelPattern = "gpt-4.1-nano",
            displayName = "GPT-4.1 Nano",
            inputUsdPer1M = 0.10,
            outputUsdPer1M = 0.40,
            cachedInputUsdPer1M = 0.025,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "gpt-4.1-mini",
            displayName = "GPT-4.1 Mini",
            inputUsdPer1M = 0.40,
            outputUsdPer1M = 1.60,
            cachedInputUsdPer1M = 0.10,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "gpt-4.1",
            displayName = "GPT-4.1",
            inputUsdPer1M = 2.00,
            outputUsdPer1M = 8.00,
            cachedInputUsdPer1M = 0.50,
            isTopTier = true,
            suggestedCheaperModelId = "gpt-4.1-mini"
        ),
        ModelPriceEntry(
            modelPattern = "gpt-4o-mini",
            displayName = "GPT-4o Mini",
            inputUsdPer1M = 0.15,
            outputUsdPer1M = 0.60,
            cachedInputUsdPer1M = 0.075,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "gpt-4o",
            displayName = "GPT-4o",
            inputUsdPer1M = 2.50,
            outputUsdPer1M = 10.00,
            cachedInputUsdPer1M = 1.25,
            isTopTier = true,
            suggestedCheaperModelId = "gpt-4o-mini"
        ),
        ModelPriceEntry(
            modelPattern = "o3-mini",
            displayName = "OpenAI o3-mini",
            inputUsdPer1M = 1.10,
            outputUsdPer1M = 4.40,
            cachedInputUsdPer1M = 0.55,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "o1-mini",
            displayName = "OpenAI o1-mini",
            inputUsdPer1M = 1.10,
            outputUsdPer1M = 4.40,
            cachedInputUsdPer1M = 0.55,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "o1",
            displayName = "OpenAI o1",
            inputUsdPer1M = 15.00,
            outputUsdPer1M = 60.00,
            cachedInputUsdPer1M = 7.50,
            isTopTier = true,
            suggestedCheaperModelId = "o3-mini"
        ),
        ModelPriceEntry(
            modelPattern = "gpt-4-turbo",
            displayName = "GPT-4 Turbo",
            inputUsdPer1M = 10.00,
            outputUsdPer1M = 30.00,
            cachedInputUsdPer1M = 5.00,
            isTopTier = true,
            suggestedCheaperModelId = "gpt-4o-mini"
        ),
        ModelPriceEntry(
            modelPattern = "gpt-3.5-turbo",
            displayName = "GPT-3.5 Turbo",
            inputUsdPer1M = 0.50,
            outputUsdPer1M = 1.50,
            cachedInputUsdPer1M = 0.25,
            isTopTier = false
        ),

        // Anthropic Claude Models
        ModelPriceEntry(
            modelPattern = "claude-3-7-sonnet",
            displayName = "Claude 3.7 Sonnet",
            inputUsdPer1M = 3.00,
            outputUsdPer1M = 15.00,
            cachedInputUsdPer1M = 0.30,
            isTopTier = true,
            suggestedCheaperModelId = "claude-3-5-haiku"
        ),
        ModelPriceEntry(
            modelPattern = "claude-3-5-sonnet",
            displayName = "Claude 3.5 Sonnet",
            inputUsdPer1M = 3.00,
            outputUsdPer1M = 15.00,
            cachedInputUsdPer1M = 0.30,
            isTopTier = true,
            suggestedCheaperModelId = "claude-3-5-haiku"
        ),
        ModelPriceEntry(
            modelPattern = "claude-3-5-haiku",
            displayName = "Claude 3.5 Haiku",
            inputUsdPer1M = 0.80,
            outputUsdPer1M = 4.00,
            cachedInputUsdPer1M = 0.08,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "claude-3-haiku",
            displayName = "Claude 3 Haiku",
            inputUsdPer1M = 0.25,
            outputUsdPer1M = 1.25,
            cachedInputUsdPer1M = 0.03,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "claude-3-opus",
            displayName = "Claude 3 Opus",
            inputUsdPer1M = 15.00,
            outputUsdPer1M = 75.00,
            cachedInputUsdPer1M = 1.50,
            isTopTier = true,
            suggestedCheaperModelId = "claude-3-5-haiku"
        ),
        ModelPriceEntry(
            modelPattern = "claude-sonnet-4",
            displayName = "Claude Sonnet 4",
            inputUsdPer1M = 3.00,
            outputUsdPer1M = 15.00,
            cachedInputUsdPer1M = 0.30,
            isTopTier = true,
            suggestedCheaperModelId = "claude-3-5-haiku"
        ),
        ModelPriceEntry(
            modelPattern = "claude-opus-4",
            displayName = "Claude Opus 4",
            inputUsdPer1M = 15.00,
            outputUsdPer1M = 75.00,
            cachedInputUsdPer1M = 1.50,
            isTopTier = true,
            suggestedCheaperModelId = "claude-3-5-haiku"
        ),

        // Google Gemini Models
        ModelPriceEntry(
            modelPattern = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            inputUsdPer1M = 1.25,
            outputUsdPer1M = 10.00,
            cachedInputUsdPer1M = 0.3125,
            isTopTier = true,
            suggestedCheaperModelId = "gemini-2.5-flash"
        ),
        ModelPriceEntry(
            modelPattern = "gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash Lite",
            inputUsdPer1M = 0.075,
            outputUsdPer1M = 0.30,
            cachedInputUsdPer1M = 0.01875,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            inputUsdPer1M = 0.15,
            outputUsdPer1M = 0.60,
            cachedInputUsdPer1M = 0.0375,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            inputUsdPer1M = 0.10,
            outputUsdPer1M = 0.40,
            cachedInputUsdPer1M = 0.025,
            isTopTier = false
        ),
        ModelPriceEntry(
            modelPattern = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            inputUsdPer1M = 1.25,
            outputUsdPer1M = 5.00,
            cachedInputUsdPer1M = 0.3125,
            isTopTier = true,
            suggestedCheaperModelId = "gemini-2.5-flash"
        ),
        ModelPriceEntry(
            modelPattern = "gemini-1.5-flash",
            displayName = "Gemini 1.5 Flash",
            inputUsdPer1M = 0.075,
            outputUsdPer1M = 0.30,
            cachedInputUsdPer1M = 0.01875,
            isTopTier = false
        ),

        // Open-Weights & Router Models (Groq, OpenRouter, DeepSeek, Llama, Qwen, Mistral)
        ModelPriceEntry("deepseek-r1", "DeepSeek R1", inputUsdPer1M = 0.55, outputUsdPer1M = 2.19, cachedInputUsdPer1M = 0.14),
        ModelPriceEntry("deepseek-chat", "DeepSeek V3", inputUsdPer1M = 0.27, outputUsdPer1M = 1.10, cachedInputUsdPer1M = 0.07),
        ModelPriceEntry("deepseek", "DeepSeek", inputUsdPer1M = 0.27, outputUsdPer1M = 1.10, cachedInputUsdPer1M = 0.07),
        ModelPriceEntry("llama-3.3-70b", "Llama 3.3 70B", inputUsdPer1M = 0.59, outputUsdPer1M = 0.79),
        ModelPriceEntry("llama-3.1-8b", "Llama 3.1 8B", inputUsdPer1M = 0.05, outputUsdPer1M = 0.08),
        ModelPriceEntry("llama", "Llama Family", inputUsdPer1M = 0.20, outputUsdPer1M = 0.80),
        ModelPriceEntry("mixtral", "Mixtral", inputUsdPer1M = 0.24, outputUsdPer1M = 0.24),
        ModelPriceEntry(
            modelPattern = "mistral-large",
            displayName = "Mistral Large",
            inputUsdPer1M = 2.00,
            outputUsdPer1M = 6.00,
            isTopTier = true,
            suggestedCheaperModelId = "gpt-4o-mini"
        ),
        ModelPriceEntry("qwen", "Qwen Family", inputUsdPer1M = 0.20, outputUsdPer1M = 0.80),

        // OmniRoute & Combo Fallbacks
        ModelPriceEntry("omniroute/", "OmniRoute Combo", inputUsdPer1M = 1.00, outputUsdPer1M = 3.00),
        ModelPriceEntry("combo", "Router Combo", inputUsdPer1M = 1.00, outputUsdPer1M = 3.00)
    )

    val DEFAULT_PRICE_ENTRY = ModelPriceEntry(
        modelPattern = "default",
        displayName = "Standard LLM",
        inputUsdPer1M = 1.50,
        outputUsdPer1M = 5.00,
        cachedInputUsdPer1M = 0.75,
        isTopTier = false
    )

    /**
     * Looks up the per-model pricing entry for [rawModelId].
     */
    fun lookupPricing(rawModelId: String): ModelPriceEntry {
        val clean = rawModelId.trim().lowercase()
        if (clean.isEmpty()) return DEFAULT_PRICE_ENTRY

        val shortName = clean.substringAfterLast("/")

        PRICE_TABLE.firstOrNull { entry ->
            clean == entry.modelPattern || shortName == entry.modelPattern
        }?.let { return it }

        PRICE_TABLE.firstOrNull { entry ->
            clean.contains(entry.modelPattern) || shortName.contains(entry.modelPattern)
        }?.let { return it }

        return when {
            clean.contains("mini") || clean.contains("flash") || clean.contains("haiku") || clean.contains("nano") ->
                ModelPriceEntry(clean, rawModelId, inputUsdPer1M = 0.15, outputUsdPer1M = 0.60, cachedInputUsdPer1M = 0.075, isTopTier = false)
            clean.contains("sonnet") || clean.contains("gpt-4o") ->
                ModelPriceEntry(
                    modelPattern = clean,
                    displayName = rawModelId,
                    inputUsdPer1M = 2.50,
                    outputUsdPer1M = 10.00,
                    cachedInputUsdPer1M = 1.25,
                    isTopTier = true,
                    suggestedCheaperModelId = if (clean.contains("sonnet")) "claude-3-5-haiku" else "gpt-4o-mini"
                )
            clean.contains("opus") ->
                ModelPriceEntry(
                    modelPattern = clean,
                    displayName = rawModelId,
                    inputUsdPer1M = 15.00,
                    outputUsdPer1M = 75.00,
                    cachedInputUsdPer1M = 1.50,
                    isTopTier = true,
                    suggestedCheaperModelId = "claude-3-5-haiku"
                )
            else -> DEFAULT_PRICE_ENTRY
        }
    }

    /**
     * Returns true if [rawModelId] is classified as a top-tier model in [PRICE_TABLE].
     */
    fun isTopTierModel(rawModelId: String): Boolean {
        return lookupPricing(rawModelId).isTopTier
    }

    /**
     * Evaluates whether to suggest a cheaper model after a reply:
     * - Triggers only if the chat has used over 50,000 tokens ([CHEAPER_MODEL_SUGGESTION_TOKEN_THRESHOLD])
     *   AND the current model is top-tier.
     * - Returns a [CheaperModelSuggestion] with `cardText = "Try <model> to save about X%"`.
     * - Never switches automatically.
     */
    fun evaluateCheaperModelSuggestion(
        currentModelId: String,
        totalConversationTokens: Int,
        availableModelIds: List<String> = emptyList(),
        inputTokens: Int = (totalConversationTokens * 3) / 4,
        outputTokens: Int = (totalConversationTokens - ((totalConversationTokens * 3) / 4)).coerceAtLeast(1)
    ): CheaperModelSuggestion? {
        if (totalConversationTokens <= CHEAPER_MODEL_SUGGESTION_TOKEN_THRESHOLD) return null
        val currentEntry = lookupPricing(currentModelId)
        if (!currentEntry.isTopTier) return null

        val defaultTargetId = currentEntry.suggestedCheaperModelId ?: "gpt-4o-mini"
        val matchedFromAvailable = availableModelIds.firstOrNull { candidate ->
            val shortCandidate = candidate.substringAfterLast("/").lowercase()
            shortCandidate == defaultTargetId.lowercase() || shortCandidate.contains(defaultTargetId.lowercase())
        }
        val targetId = matchedFromAvailable ?: defaultTargetId
        val cheaperEntry = lookupPricing(targetId)

        val currentCost = calculateCostUsd(
            modelId = currentModelId,
            inputTokens = inputTokens.coerceAtLeast(1),
            outputTokens = outputTokens.coerceAtLeast(1)
        )
        val cheaperCost = calculateCostUsd(
            modelId = targetId,
            inputTokens = inputTokens.coerceAtLeast(1),
            outputTokens = outputTokens.coerceAtLeast(1)
        )

        if (currentCost <= 0.0 || cheaperCost >= currentCost) return null
        val savingsPercent = ((1.0 - (cheaperCost / currentCost)) * 100.0)
            .roundToInt()
            .coerceIn(1, 99)

        val prefix = if (currentModelId.contains("/")) {
            currentModelId.substringBeforeLast("/") + "/"
        } else ""
        val resolvedSuggestedId = if (matchedFromAvailable != null) {
            matchedFromAvailable
        } else if (prefix.isNotEmpty() && !targetId.contains("/")) {
            "$prefix$targetId"
        } else {
            targetId
        }

        return CheaperModelSuggestion(
            currentModelId = currentModelId,
            suggestedModelId = resolvedSuggestedId,
            suggestedDisplayName = cheaperEntry.displayName,
            savingsPercent = savingsPercent,
            cardText = "Try ${cheaperEntry.displayName} to save about $savingsPercent%"
        )
    }

    /**
     * Returns (inputCostPer1K, outputCostPer1K) in USD for compatibility with [ModelDescriptorEntity].
     */
    fun lookupPricingPer1K(rawModelId: String, isCombo: Boolean = false): Pair<Double, Double> {
        if (isCombo && !rawModelId.contains("/")) {
            return 0.0010 to 0.0030
        }
        val entry = lookupPricing(rawModelId)
        return entry.inputUsdPer1K to entry.outputUsdPer1K
    }

    /**
     * Computes estimated cost in USD from the centralized model price table.
     * Uses [ModelPriceEntry.cachedInputUsdPer1M] for cached input tokens when [cachedInputTokens] > 0.
     */
    fun calculateCostUsd(
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        cachedInputTokens: Int = 0
    ): Double {
        val safeIn = inputTokens.coerceAtLeast(0)
        val safeOut = outputTokens.coerceAtLeast(0)
        val safeCached = cachedInputTokens.coerceAtLeast(0).coerceAtMost(safeIn)
        val uncachedIn = (safeIn - safeCached).coerceAtLeast(0)
        val entry = lookupPricing(modelId)

        val inputCost = ((uncachedIn / 1_000_000.0) * entry.inputUsdPer1M) +
            ((safeCached / 1_000_000.0) * entry.cachedInputUsdPer1M)
        val outputCost = (safeOut / 1_000_000.0) * entry.outputUsdPer1M
        return inputCost + outputCost
    }

    /**
     * Estimates token count from text (~4 characters per token) when a provider
     * does not return token usage metadata.
     */
    fun estimateTokensFromText(text: String): Int {
        if (text.isBlank()) return 0
        return ((text.trim().length + 3) / 4).coerceAtLeast(1)
    }

    /**
     * Resolves token counts and cost for an assistant turn, including any tool call arguments/results,
     * attached file tokens, or cached input tokens.
     */
    fun resolveTurnUsage(
        modelId: String,
        reportedInputTokens: Int,
        reportedOutputTokens: Int,
        fallbackInputText: String,
        fallbackOutputText: String,
        additionalToolInputTokens: Int = 0,
        additionalToolOutputTokens: Int = 0,
        reportedCachedInputTokens: Int = 0
    ): ResolvedMessageUsage {
        val hasProviderUsage = reportedInputTokens > 0 || reportedOutputTokens > 0
        val baseInput = if (reportedInputTokens > 0) {
            reportedInputTokens
        } else {
            estimateTokensFromText(fallbackInputText) + additionalToolInputTokens.coerceAtLeast(0)
        }
        val baseOutput = if (reportedOutputTokens > 0) {
            reportedOutputTokens
        } else {
            estimateTokensFromText(fallbackOutputText) + additionalToolOutputTokens.coerceAtLeast(0)
        }
        val safeCached = reportedCachedInputTokens.coerceAtLeast(0)
        val effectiveInput = baseInput.coerceAtLeast(safeCached)
        val cost = calculateCostUsd(
            modelId = modelId,
            inputTokens = effectiveInput,
            outputTokens = baseOutput,
            cachedInputTokens = safeCached
        )
        return ResolvedMessageUsage(
            modelId = modelId,
            inputTokens = effectiveInput,
            outputTokens = baseOutput,
            isTokenCountEstimated = !hasProviderUsage,
            estimatedCostUsd = cost,
            cachedInputTokens = safeCached
        )
    }

    private fun extractJsonIntField(rawJson: String, fieldName: String): Int {
        val m = Regex(""""${Regex.escape(fieldName)}"\s*:\s*(\d+)""").find(rawJson)
        return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    /**
     * Parses OpenAI-compatible `usage` object (`prompt_tokens` / `completion_tokens` and
     * `prompt_tokens_details.cached_tokens` or `prompt_cache_hit_tokens`).
     */
    fun parseOpenAiUsage(rootJson: JSONObject): ProviderTokenUsage {
        val usage = rootJson.optJSONObject("usage")
            ?: return ProviderTokenUsage(0, 0, isPresent = false, cachedInputTokens = 0)
        val prompt = usage.optInt("prompt_tokens", usage.optInt("input_tokens", 0))
        val completion = usage.optInt("completion_tokens", usage.optInt("output_tokens", 0))
        val cached = usage.optJSONObject("prompt_tokens_details")?.optInt("cached_tokens", 0)
            ?.takeIf { it > 0 }
            ?: usage.optJSONObject("input_tokens_details")?.optInt("cached_tokens", 0)?.takeIf { it > 0 }
            ?: usage.optInt("prompt_cache_hit_tokens", usage.optInt("cached_tokens", 0))
        return ProviderTokenUsage(
            inputTokens = prompt.coerceAtLeast(0),
            outputTokens = completion.coerceAtLeast(0),
            isPresent = prompt > 0 || completion > 0 || cached > 0,
            cachedInputTokens = cached.coerceAtLeast(0)
        )
    }

    fun parseOpenAiUsage(rawJson: String): ProviderTokenUsage {
        runCatching { return parseOpenAiUsage(JSONObject(rawJson)) }
        val prompt = extractJsonIntField(rawJson, "prompt_tokens")
            .takeIf { it > 0 } ?: extractJsonIntField(rawJson, "input_tokens")
        val completion = extractJsonIntField(rawJson, "completion_tokens")
            .takeIf { it > 0 } ?: extractJsonIntField(rawJson, "output_tokens")
        val cached = extractJsonIntField(rawJson, "cached_tokens")
            .takeIf { it > 0 } ?: extractJsonIntField(rawJson, "prompt_cache_hit_tokens")
        return ProviderTokenUsage(
            inputTokens = prompt.coerceAtLeast(0),
            outputTokens = completion.coerceAtLeast(0),
            isPresent = prompt > 0 || completion > 0 || cached > 0,
            cachedInputTokens = cached.coerceAtLeast(0)
        )
    }

    /**
     * Parses Anthropic `usage` object from `message_start`, `message_delta`, or non-streaming `message` JSON,
     * including `cache_read_input_tokens` (and `cache_creation_input_tokens`).
     */
    fun parseAnthropicUsage(rootJson: JSONObject): ProviderTokenUsage {
        val messageUsage = rootJson.optJSONObject("message")?.optJSONObject("usage")
        val topUsage = rootJson.optJSONObject("usage")
        val rawIn = (messageUsage?.optInt("input_tokens", 0) ?: 0)
            .takeIf { it > 0 } ?: (topUsage?.optInt("input_tokens", 0) ?: 0)
        val outTokens = (topUsage?.optInt("output_tokens", 0) ?: 0)
            .takeIf { it > 0 } ?: (messageUsage?.optInt("output_tokens", 0) ?: 0)
        val cacheRead = (messageUsage?.optInt("cache_read_input_tokens", 0) ?: 0)
            .takeIf { it > 0 } ?: (topUsage?.optInt("cache_read_input_tokens", 0) ?: 0)
        val cacheCreate = (messageUsage?.optInt("cache_creation_input_tokens", 0) ?: 0)
            .takeIf { it > 0 } ?: (topUsage?.optInt("cache_creation_input_tokens", 0) ?: 0)
        val totalIn = if (rawIn > 0 && cacheRead > 0 && rawIn < cacheRead) {
            rawIn + cacheRead + cacheCreate
        } else if (rawIn == 0 && (cacheRead > 0 || cacheCreate > 0)) {
            cacheRead + cacheCreate
        } else {
            rawIn
        }
        return ProviderTokenUsage(
            inputTokens = totalIn.coerceAtLeast(0),
            outputTokens = outTokens.coerceAtLeast(0),
            isPresent = totalIn > 0 || outTokens > 0 || cacheRead > 0,
            cachedInputTokens = cacheRead.coerceAtLeast(0)
        )
    }

    fun parseAnthropicUsage(rawJson: String): ProviderTokenUsage {
        runCatching { return parseAnthropicUsage(JSONObject(rawJson)) }
        val inTokens = extractJsonIntField(rawJson, "input_tokens")
        val outTokens = extractJsonIntField(rawJson, "output_tokens")
        val cacheRead = extractJsonIntField(rawJson, "cache_read_input_tokens")
        return ProviderTokenUsage(
            inputTokens = inTokens.coerceAtLeast(cacheRead).coerceAtLeast(0),
            outputTokens = outTokens.coerceAtLeast(0),
            isPresent = inTokens > 0 || outTokens > 0 || cacheRead > 0,
            cachedInputTokens = cacheRead.coerceAtLeast(0)
        )
    }

    /**
     * Parses Google Gemini `usageMetadata` object (`promptTokenCount` / `candidatesTokenCount` / `cachedContentTokenCount`).
     */
    fun parseGeminiUsage(rootJson: JSONObject): ProviderTokenUsage {
        val meta = rootJson.optJSONObject("usageMetadata")
            ?: return ProviderTokenUsage(0, 0, isPresent = false, cachedInputTokens = 0)
        val prompt = meta.optInt("promptTokenCount", 0)
        val candidates = meta.optInt("candidatesTokenCount", 0)
        val cached = meta.optInt("cachedContentTokenCount", 0)
        return ProviderTokenUsage(
            inputTokens = prompt.coerceAtLeast(0),
            outputTokens = candidates.coerceAtLeast(0),
            isPresent = prompt > 0 || candidates > 0 || cached > 0,
            cachedInputTokens = cached.coerceAtLeast(0)
        )
    }

    fun parseGeminiUsage(rawJson: String): ProviderTokenUsage {
        runCatching { return parseGeminiUsage(JSONObject(rawJson)) }
        val prompt = extractJsonIntField(rawJson, "promptTokenCount")
        val candidates = extractJsonIntField(rawJson, "candidatesTokenCount")
        val cached = extractJsonIntField(rawJson, "cachedContentTokenCount")
        return ProviderTokenUsage(
            inputTokens = prompt.coerceAtLeast(0),
            outputTokens = candidates.coerceAtLeast(0),
            isPresent = prompt > 0 || candidates > 0 || cached > 0,
            cachedInputTokens = cached.coerceAtLeast(0)
        )
    }

    /**
     * Formats a currency amount with an explicit "estimated" label.
     */
    fun formatEstimatedCost(costUsd: Double, currency: String = "USD"): String {
        val isInr = currency.equals("INR", ignoreCase = true)
        val amount = if (isInr) costUsd * USD_TO_INR_RATE else costUsd
        val symbol = if (isInr) "₹" else "$"
        val formatted = when {
            amount == 0.0 -> "${symbol}0.0000"
            amount < 0.01 -> String.format(Locale.US, "%s%.4f", symbol, amount)
            else -> String.format(Locale.US, "%s%.3f", symbol, amount)
        }
        return "$formatted (estimated)"
    }

    /**
     * Formats the per-message usage footer shown under every assistant reply.
     * Shows cached tokens ("cached: N tokens") when [cachedInputTokens] > 0.
     */
    fun formatMessageUsageFooter(
        inputTokens: Int,
        outputTokens: Int,
        estimatedCostUsd: Double,
        isTokenCountEstimated: Boolean,
        currency: String = "USD",
        cachedInputTokens: Int = 0
    ): String {
        val tokenSuffix = if (isTokenCountEstimated) " (estimated)" else ""
        val cachedPart = if (cachedInputTokens > 0) " · cached: $cachedInputTokens tokens" else ""
        val costLabel = formatEstimatedCost(estimatedCostUsd, currency)
        return "$inputTokens in · $outputTokens out tokens$tokenSuffix$cachedPart · Cost: $costLabel"
    }

    /**
     * Formats the bottom-of-chat conversation summary line including total input tokens,
     * output tokens, cached tokens (when > 0), tool calls, code runs, and estimated cost.
     */
    fun formatConversationUsageSummary(
        totalInputTokens: Int,
        totalOutputTokens: Int,
        totalEstimatedCostUsd: Double,
        toolCallsCount: Int,
        codeRunsCount: Int,
        hasEstimatedTokens: Boolean,
        currency: String = "USD",
        totalCachedInputTokens: Int = 0
    ): String {
        val tokenSuffix = if (hasEstimatedTokens) " (estimated)" else ""
        val costLabel = formatEstimatedCost(totalEstimatedCostUsd, currency)
        return buildString {
            append("Chat total: $totalInputTokens in · $totalOutputTokens out tokens$tokenSuffix")
            if (totalCachedInputTokens > 0) {
                append(" · cached: $totalCachedInputTokens tokens")
            }
            if (toolCallsCount > 0 || codeRunsCount > 0) {
                append(" · $toolCallsCount tool ${if (toolCallsCount == 1) "call" else "calls"}")
                append(" · $codeRunsCount code ${if (codeRunsCount == 1) "run" else "runs"}")
            }
            append(" · Cost: $costLabel")
        }
    }

    /**
     * Round 14: one-line variant of [formatConversationUsageSummary] for the
     * slim chat footer — same numbers, terse labels, no prefix.
     */
    fun formatConversationUsageSummaryCompact(
        totalInputTokens: Int,
        totalOutputTokens: Int,
        totalEstimatedCostUsd: Double,
        toolCallsCount: Int,
        codeRunsCount: Int,
        hasEstimatedTokens: Boolean,
        currency: String = "USD",
        totalCachedInputTokens: Int = 0
    ): String {
        val costLabel = formatEstimatedCost(totalEstimatedCostUsd, currency)
        return buildString {
            append("$totalInputTokens in · $totalOutputTokens out")
            if (totalCachedInputTokens > 0) {
                append(" · cached $totalCachedInputTokens")
            }
            if (toolCallsCount > 0 || codeRunsCount > 0) {
                append(" · $toolCallsCount calls · $codeRunsCount runs")
            }
            append(" · $costLabel")
            if (hasEstimatedTokens) {
                append(" (est)")
            }
        }
    }
}
