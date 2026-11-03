package com.example.data.provider

import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dedicated adapter for Anthropic Messages API (https://api.anthropic.com/v1).
 * Uses x-api-key and anthropic-version headers; keys are never placed in URLs.
 */
class AnthropicProvider(
    private val okHttpClient: OkHttpClient
) : AiProvider {

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    override suspend fun testConnection(
        provider: ProviderInstanceEntity,
        apiKey: String
    ): Result<List<ModelDescriptorEntity>> = listModels(provider, apiKey)

    override suspend fun listModels(
        provider: ProviderInstanceEntity,
        apiKey: String
    ): Result<List<ModelDescriptorEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = provider.baseUrl.trim().trimEnd('/')
            val modelsUrl = "$cleanBase/models"

            val reqBuilder = Request.Builder()
                .url(modelsUrl)
                .get()
                .header("Accept", "application/json")
                .header("anthropic-version", ANTHROPIC_VERSION)

            if (apiKey.isNotBlank()) {
                reqBuilder.header("x-api-key", apiKey.trim())
            }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val norm = ErrorNormalizer.normalizeHttpError(
                        statusCode = response.code,
                        responseBody = bodyStr,
                        providerName = provider.displayName
                    )
                    throw ProviderNormalizedException(norm)
                }

                val parsed = parseAnthropicModels(provider.id, bodyStr)
                parsed.ifEmpty { defaultAnthropicModels(provider.id) }
            }
        }.recoverCatching { throwable ->
            if (throwable is ProviderNormalizedException) throw throwable
            throw ProviderNormalizedException(
                ErrorNormalizer.normalizeException(
                    throwable = throwable,
                    providerName = provider.displayName,
                    baseUrl = provider.baseUrl
                )
            )
        }
    }

    internal fun parseAnthropicModels(
        providerInstanceId: String,
        rawJson: String
    ): List<ModelDescriptorEntity> {
        val list = mutableListOf<ModelDescriptorEntity>()
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
        val dataArr = root.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArr.length()) {
            val obj = dataArr.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            if (id.isEmpty()) continue
            val display = obj.optString("display_name").ifBlank { id }
            list.add(
                ModelCapabilityRegistry.buildDescriptor(
                    providerInstanceId = providerInstanceId,
                    rawModelId = id,
                    displayNameOverride = display
                )
            )
        }
        return list
    }

    fun defaultAnthropicModels(providerInstanceId: String): List<ModelDescriptorEntity> = listOf(
        ModelCapabilityRegistry.buildDescriptor(
            providerInstanceId = providerInstanceId,
            rawModelId = "claude-3-7-sonnet-latest",
            displayNameOverride = "Claude 3.7 Sonnet",
            preserveFavorite = true
        ),
        ModelCapabilityRegistry.buildDescriptor(
            providerInstanceId = providerInstanceId,
            rawModelId = "claude-3-5-sonnet-latest",
            displayNameOverride = "Claude 3.5 Sonnet"
        ),
        ModelCapabilityRegistry.buildDescriptor(
            providerInstanceId = providerInstanceId,
            rawModelId = "claude-3-5-haiku-latest",
            displayNameOverride = "Claude 3.5 Haiku"
        )
    )

    override fun streamChat(request: ChatCompletionRequest): Flow<ChatStreamEvent> = flow {
        val cleanBase = request.provider.baseUrl.trim().trimEnd('/')
        val messagesUrl = "$cleanBase/messages"
        val supportsCaching = request.enablePromptCaching ||
            PromptCacheManager.supportsAnthropicPromptCaching(request.provider, request.modelId)

        val payloadJson = buildAnthropicPayloadJson(request.copy(enablePromptCaching = supportsCaching))
        val reqBuilder = Request.Builder()
            .url(messagesUrl)
            .post(payloadJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "text/event-stream, application/json")
            .header("anthropic-version", ANTHROPIC_VERSION)

        if (supportsCaching) {
            reqBuilder.header("anthropic-beta", "prompt-caching-2024-07-31")
        }

        if (request.apiKey.isNotBlank()) {
            reqBuilder.header("x-api-key", request.apiKey.trim())
        }

        try {
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    emit(
                        ChatStreamEvent.Error(
                            ErrorNormalizer.normalizeHttpError(
                                statusCode = response.code,
                                responseBody = errBody,
                                providerName = request.provider.displayName
                            )
                        )
                    )
                    return@flow
                }

                val source = response.body?.source() ?: run {
                    emit(ChatStreamEvent.Done)
                    return@flow
                }

                var inputTokens = 0
                var outputTokens = 0
                var cachedInputTokens = 0
                var activeToolId: String? = null
                var activeToolName: String? = null
                val activeToolJsonBuilder = StringBuilder()

                suspend fun flushActiveAnthropicTool() {
                    val toolName = activeToolName?.trim().orEmpty()
                    if (toolName.isNotEmpty()) {
                        val callId = activeToolId?.ifBlank { "toolu_${System.currentTimeMillis()}" }
                            ?: "toolu_${System.currentTimeMillis()}"
                        val argsStr = activeToolJsonBuilder.toString().trim().ifBlank { "{}" }
                        emit(
                            ChatStreamEvent.ToolCall(
                                ModelToolCallRequest(
                                    id = callId,
                                    toolName = toolName,
                                    argumentsJson = argsStr
                                )
                            )
                        )
                    }
                    activeToolId = null
                    activeToolName = null
                    activeToolJsonBuilder.clear()
                }

                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data:")) continue
                    val dataStr = trimmed.removePrefix("data:").trim()
                    if (dataStr.isEmpty() || dataStr == "[DONE]") continue

                    val eventJson = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue
                    when (eventJson.optString("type")) {
                        "message_start" -> {
                            val usage = eventJson.optJSONObject("message")?.optJSONObject("usage")
                            if (usage != null) {
                                val parsed = ModelPriceTable.parseAnthropicUsage(eventJson)
                                if (parsed != null) {
                                    inputTokens = parsed.inputTokens
                                    cachedInputTokens = parsed.cachedInputTokens
                                } else {
                                    inputTokens = usage.optInt("input_tokens", inputTokens)
                                    cachedInputTokens = usage.optInt("cache_read_input_tokens", cachedInputTokens)
                                }
                            }
                        }
                        "content_block_start" -> {
                            flushActiveAnthropicTool()
                            val block = eventJson.optJSONObject("content_block")
                            if (block?.optString("type") == "tool_use") {
                                activeToolId = block.optString("id", "")
                                activeToolName = block.optString("name", "")
                                val initialInput = block.optJSONObject("input")
                                if (initialInput != null && initialInput.length() > 0) {
                                    activeToolJsonBuilder.append(initialInput.toString())
                                }
                            }
                        }
                        "content_block_delta" -> {
                            val delta = eventJson.optJSONObject("delta")
                            when (delta?.optString("type")) {
                                "text_delta" -> {
                                    val text = delta.optString("text", "")
                                    if (text.isNotEmpty()) {
                                        emit(ChatStreamEvent.Delta(text))
                                    }
                                }
                                "input_json_delta" -> {
                                    val partialJson = delta.optString("partial_json", "")
                                    if (partialJson.isNotEmpty()) {
                                        activeToolJsonBuilder.append(partialJson)
                                    }
                                }
                            }
                        }
                        "content_block_stop" -> {
                            flushActiveAnthropicTool()
                        }
                        "message_delta" -> {
                            val usage = eventJson.optJSONObject("usage")
                            if (usage != null) {
                                outputTokens = usage.optInt("output_tokens", outputTokens)
                                val deltaCached = usage.optInt("cache_read_input_tokens", 0)
                                if (deltaCached > 0) {
                                    cachedInputTokens = deltaCached
                                }
                                emit(ChatStreamEvent.Usage(inputTokens, outputTokens, cachedInputTokens))
                            }
                        }
                        "error" -> {
                            val errObj = eventJson.optJSONObject("error")
                            emit(
                                ChatStreamEvent.Error(
                                    ErrorNormalizer.normalizeHttpError(
                                        statusCode = 400,
                                        responseBody = errObj?.toString() ?: dataStr,
                                        providerName = request.provider.displayName
                                    )
                                )
                            )
                            return@flow
                        }
                        "message_stop" -> {
                            flushActiveAnthropicTool()
                            if (inputTokens > 0 || outputTokens > 0 || cachedInputTokens > 0) {
                                emit(ChatStreamEvent.Usage(inputTokens, outputTokens, cachedInputTokens))
                            }
                            emit(ChatStreamEvent.Done)
                            return@flow
                        }
                    }
                }
                flushActiveAnthropicTool()
                emit(ChatStreamEvent.Done)
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            emit(
                ChatStreamEvent.Error(
                    ErrorNormalizer.normalizeException(
                        throwable = e,
                        providerName = request.provider.displayName,
                        baseUrl = request.provider.baseUrl
                    )
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    internal fun buildAnthropicPayloadJson(request: ChatCompletionRequest): JSONObject {
        val root = JSONObject()
        root.put("model", request.modelId)
        root.put("max_tokens", request.maxTokens.coerceAtLeast(256))
        root.put("stream", true)

        if (request.supportsTemperature) {
            root.put("temperature", request.temperature.toDouble().coerceIn(0.0, 1.0))
        }
        if (request.supportsTopP) {
            root.put("top_p", request.topP.toDouble().coerceIn(0.05, 1.0))
        }
        val effectiveBasePrompt = request.baseSystemPrompt.ifBlank { request.systemPrompt }
        if (request.supportsSystemPrompt && (effectiveBasePrompt.isNotBlank() || request.enabledSkillsBlock.isNotBlank())) {
            if (request.enablePromptCaching || request.enabledSkillsBlock.isNotBlank()) {
                val cachedBlocks = PromptCacheManager.buildAnthropicCachedSystemBlocks(
                    baseSystemPrompt = effectiveBasePrompt,
                    enabledSkillsBlock = request.enabledSkillsBlock
                )
                root.put("system", cachedBlocks)
            } else {
                root.put("system", request.systemPrompt.trim())
            }
        }

        // Bound the outbound payload (see SmartContextTrimmer.compactToolLoopMessages).
        val outboundMessages = SmartContextTrimmer.compactToolLoopMessages(
            request.messages,
            SmartContextTrimmer.calculateTrimmingThresholdTokens(
                SmartContextTrimmer.resolveContextWindowTokens(request.modelId)
            ),
            request.systemPrompt
        )

        val messagesArr = JSONArray()
        for (msg in outboundMessages) {
            if (msg.role.equals("system", ignoreCase = true)) continue
            val roleLower = msg.role.lowercase()

            when {
                roleLower == "tool" -> {
                    val toolResultBlock = JSONObject().apply {
                        put("type", "tool_result")
                        put("tool_use_id", msg.toolCallId ?: "toolu_0")
                        put("content", msg.content)
                    }
                    messagesArr.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().put(toolResultBlock))
                        }
                    )
                }
                msg.toolCalls.isNotEmpty() -> {
                    val contentArr = JSONArray()
                    if (msg.content.isNotBlank()) {
                        contentArr.put(
                            JSONObject().apply {
                                put("type", "text")
                                put("text", msg.content)
                            }
                        )
                    }
                    for (tc in msg.toolCalls) {
                        val inputObj = runCatching { JSONObject(tc.argumentsJson) }.getOrElse { JSONObject() }
                        contentArr.put(
                            JSONObject().apply {
                                put("type", "tool_use")
                                put("id", tc.id)
                                put("name", tc.toolName)
                                put("input", inputObj)
                            }
                        )
                    }
                    messagesArr.put(
                        JSONObject().apply {
                            put("role", "assistant")
                            put("content", contentArr)
                        }
                    )
                }
                else -> {
                    val role = if (roleLower == "assistant") "assistant" else "user"
                    val msgObj = JSONObject()
                    msgObj.put("role", role)

                    if ((msg.mediaAttachments.isNotEmpty() || !msg.imageDataUri.isNullOrBlank()) && role == "user") {
                        val contentArr = JSONArray()
                        if (msg.mediaAttachments.isNotEmpty()) {
                            for (media in msg.mediaAttachments) {
                                val base64Data = media.dataUri.substringAfter("base64,", "")
                                if (base64Data.isNotBlank()) {
                                    val imgBlock = JSONObject().apply {
                                        put("type", "image")
                                        put(
                                            "source",
                                            JSONObject().apply {
                                                put("type", "base64")
                                                put("media_type", media.mimeType.ifBlank { "image/jpeg" })
                                                put("data", base64Data)
                                            }
                                        )
                                    }
                                    contentArr.put(imgBlock)
                                }
                            }
                        } else if (!msg.imageDataUri.isNullOrBlank()) {
                            val base64Data = msg.imageDataUri.substringAfter("base64,", "")
                            if (base64Data.isNotBlank()) {
                                val imgBlock = JSONObject().apply {
                                    put("type", "image")
                                    put(
                                        "source",
                                        JSONObject().apply {
                                            put("type", "base64")
                                            put("media_type", "image/jpeg")
                                            put("data", base64Data)
                                        }
                                    )
                                }
                                contentArr.put(imgBlock)
                            }
                        }
                        contentArr.put(
                            JSONObject().apply {
                                put("type", "text")
                                put("text", msg.content.ifBlank { "Describe these attachments." })
                            }
                        )
                        msgObj.put("content", contentArr)
                    } else {
                        msgObj.put("content", msg.content)
                    }
                    messagesArr.put(msgObj)
                }
            }
        }
        root.put("messages", messagesArr)

        if (request.tools.isNotEmpty()) {
            val sortedTools = PromptCacheManager.sortToolsDeterministically(request.tools)
            val markToolsCacheable = request.enablePromptCaching ||
                PromptCacheManager.supportsAnthropicPromptCaching(request.provider, request.modelId)
            val toolsArr = JSONArray()
            for ((index, tool) in sortedTools.withIndex()) {
                val inputSchema = runCatching { JSONObject(tool.inputSchemaJson) }.getOrElse {
                    JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    }
                }
                toolsArr.put(
                    JSONObject().apply {
                        put("name", tool.toolName)
                        put("description", tool.description)
                        put("input_schema", inputSchema)
                        if (markToolsCacheable && index == sortedTools.lastIndex) {
                            put("cache_control", JSONObject().put("type", "ephemeral"))
                        }
                    }
                )
            }
            root.put("tools", toolsArr)
        }
        return root
    }

    override suspend fun generateImage(
        request: ImageGenerationRequest
    ): Result<ImageGenerationOutput> = Result.failure(
        UnsupportedOperationException("Anthropic does not support image generation.")
    )

    override suspend fun submitVideoJob(
        provider: ProviderInstanceEntity,
        apiKey: String,
        modelId: String,
        prompt: String
    ): Result<String> = Result.failure(UnsupportedOperationException("Anthropic does not support video generation."))
}
