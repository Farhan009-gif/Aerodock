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
 * Dedicated adapter for Google Gemini REST API (https://generativelanguage.googleapis.com/v1beta).
 * Sends the API key strictly in the `x-goog-api-key` HTTP header (never in URLs).
 */
class GeminiProvider(
    private val okHttpClient: OkHttpClient
) : AiProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

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

            if (apiKey.isNotBlank()) {
                reqBuilder.header("x-goog-api-key", apiKey.trim())
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

                val parsed = parseGeminiModels(provider.id, bodyStr)
                parsed.ifEmpty { defaultGeminiModels(provider.id) }
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

    internal fun parseGeminiModels(
        providerInstanceId: String,
        rawJson: String
    ): List<ModelDescriptorEntity> {
        val list = mutableListOf<ModelDescriptorEntity>()
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
        val modelsArr = root.optJSONArray("models") ?: return emptyList()
        for (i in 0 until modelsArr.length()) {
            val obj = modelsArr.optJSONObject(i) ?: continue
            val rawName = obj.optString("name").removePrefix("models/").trim()
            if (rawName.isEmpty()) continue
            val display = obj.optString("displayName").ifBlank { rawName }
            list.add(
                ModelCapabilityRegistry.buildDescriptor(
                    providerInstanceId = providerInstanceId,
                    rawModelId = rawName,
                    displayNameOverride = display
                )
            )
        }
        return list
    }

    fun defaultGeminiModels(providerInstanceId: String): List<ModelDescriptorEntity> = listOf(
        ModelCapabilityRegistry.buildDescriptor(
            providerInstanceId = providerInstanceId,
            rawModelId = "gemini-2.5-pro",
            displayNameOverride = "Gemini 2.5 Pro",
            preserveFavorite = true
        ),
        ModelCapabilityRegistry.buildDescriptor(
            providerInstanceId = providerInstanceId,
            rawModelId = "gemini-2.5-flash",
            displayNameOverride = "Gemini 2.5 Flash",
            preserveFavorite = true
        ),
        ModelCapabilityRegistry.buildDescriptor(
            providerInstanceId = providerInstanceId,
            rawModelId = "gemini-2.0-flash",
            displayNameOverride = "Gemini 2.0 Flash"
        )
    )

    override fun streamChat(request: ChatCompletionRequest): Flow<ChatStreamEvent> = flow {
        val cleanBase = request.provider.baseUrl.trim().trimEnd('/')
        val cleanModel = request.modelId.removePrefix("models/").trim()
        val streamUrl = "$cleanBase/models/$cleanModel:streamGenerateContent?alt=sse"

        val payloadJson = buildGeminiPayloadJson(request)
        val reqBuilder = Request.Builder()
            .url(streamUrl)
            .post(payloadJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "text/event-stream, application/json")

        if (request.apiKey.isNotBlank()) {
            reqBuilder.header("x-goog-api-key", request.apiKey.trim())
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

                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data:")) continue
                    val dataStr = trimmed.removePrefix("data:").trim()
                    if (dataStr.isEmpty() || dataStr == "[DONE]") continue

                    val chunkJson = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue
                    val candidates = chunkJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val contentObj = candidates.optJSONObject(0)?.optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts")
                        if (parts != null) {
                            for (p in 0 until parts.length()) {
                                val partObj = parts.optJSONObject(p) ?: continue
                                val text = partObj.optString("text", "")
                                if (text.isNotEmpty()) {
                                    emit(ChatStreamEvent.Delta(text))
                                }
                                val fnCall = partObj.optJSONObject("functionCall")
                                if (fnCall != null) {
                                    val fnName = fnCall.optString("name", "").trim()
                                    if (fnName.isNotEmpty()) {
                                        val argsObj = fnCall.optJSONObject("args") ?: JSONObject()
                                        emit(
                                            ChatStreamEvent.ToolCall(
                                                ModelToolCallRequest(
                                                    id = "gem_call_${p}_${System.currentTimeMillis()}",
                                                    toolName = fnName,
                                                    argumentsJson = argsObj.toString()
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val usageMeta = chunkJson.optJSONObject("usageMetadata")
                    if (usageMeta != null) {
                        val parsed = ModelPriceTable.parseGeminiUsage(chunkJson)
                        val pTokens = parsed?.inputTokens ?: usageMeta.optInt("promptTokenCount", 0)
                        val cTokens = parsed?.outputTokens ?: usageMeta.optInt("candidatesTokenCount", 0)
                        val cachedTokens = parsed?.cachedInputTokens ?: usageMeta.optInt("cachedContentTokenCount", 0)
                        if (pTokens > 0 || cTokens > 0 || cachedTokens > 0) {
                            emit(ChatStreamEvent.Usage(pTokens, cTokens, cachedTokens))
                        }
                    }
                }
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

    internal fun buildGeminiPayloadJson(request: ChatCompletionRequest): JSONObject {
        val root = JSONObject()

        val stableSystemText = if (request.baseSystemPrompt.isNotBlank() || request.enabledSkillsBlock.isNotBlank()) {
            PromptCacheManager.buildStableSystemPrefix(
                baseSystemPrompt = request.baseSystemPrompt.ifBlank { request.systemPrompt },
                enabledSkillsBlock = request.enabledSkillsBlock
            )
        } else {
            request.systemPrompt.trim()
        }
        if (request.supportsSystemPrompt && stableSystemText.isNotBlank()) {
            val sysParts = JSONArray().put(JSONObject().put("text", stableSystemText))
            root.put("systemInstruction", JSONObject().put("parts", sysParts))
        }

        // Bound the outbound payload (see SmartContextTrimmer.compactToolLoopMessages).
        val outboundMessages = SmartContextTrimmer.compactToolLoopMessages(
            request.messages,
            SmartContextTrimmer.calculateTrimmingThresholdTokens(
                SmartContextTrimmer.resolveContextWindowTokens(request.modelId)
            ),
            request.systemPrompt
        )

        val contentsArr = JSONArray()
        for (msg in outboundMessages) {
            if (msg.role.equals("system", ignoreCase = true)) continue
            val roleLower = msg.role.lowercase()

            when {
                roleLower == "tool" -> {
                    val fnRespPart = JSONObject().put(
                        "functionResponse",
                        JSONObject().apply {
                            put("name", msg.toolName ?: "tool")
                            put("response", JSONObject().put("result", msg.content))
                        }
                    )
                    contentsArr.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(fnRespPart))
                        }
                    )
                }
                msg.toolCalls.isNotEmpty() -> {
                    val partsArr = JSONArray()
                    if (msg.content.isNotBlank()) {
                        partsArr.put(JSONObject().put("text", msg.content))
                    }
                    for (tc in msg.toolCalls) {
                        val argsObj = runCatching { JSONObject(tc.argumentsJson) }.getOrElse { JSONObject() }
                        partsArr.put(
                            JSONObject().put(
                                "functionCall",
                                JSONObject().apply {
                                    put("name", tc.toolName)
                                    put("args", argsObj)
                                }
                            )
                        )
                    }
                    contentsArr.put(
                        JSONObject().apply {
                            put("role", "model")
                            put("parts", partsArr)
                        }
                    )
                }
                else -> {
                    val geminiRole = if (roleLower == "assistant") "model" else "user"
                    val partsArr = JSONArray()

                    if (msg.content.isNotBlank()) {
                        partsArr.put(JSONObject().put("text", msg.content))
                    }
                    if (msg.mediaAttachments.isNotEmpty() && geminiRole == "user") {
                        for (media in msg.mediaAttachments) {
                            val base64Data = media.dataUri.substringAfter("base64,", "")
                            if (base64Data.isNotBlank()) {
                                partsArr.put(
                                    JSONObject().put(
                                        "inline_data",
                                        JSONObject().apply {
                                            put("mime_type", media.mimeType.ifBlank { if (media.isVideo) "video/mp4" else "image/jpeg" })
                                            put("data", base64Data)
                                        }
                                    )
                                )
                            }
                        }
                    } else if (!msg.imageDataUri.isNullOrBlank() && geminiRole == "user") {
                        val base64Data = msg.imageDataUri.substringAfter("base64,", "")
                        if (base64Data.isNotBlank()) {
                            partsArr.put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Data)
                                    }
                                )
                            )
                        }
                    }
                    if (partsArr.length() > 0) {
                        contentsArr.put(
                            JSONObject().apply {
                                put("role", geminiRole)
                                put("parts", partsArr)
                            }
                        )
                    }
                }
            }
        }
        root.put("contents", contentsArr)

        if (request.tools.isNotEmpty()) {
            val sortedTools = PromptCacheManager.sortToolsDeterministically(request.tools)
            val fnDecls = JSONArray()
            for (tool in sortedTools) {
                val paramsSchema = runCatching { JSONObject(tool.inputSchemaJson) }.getOrElse {
                    JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    }
                }
                fnDecls.put(
                    JSONObject().apply {
                        put("name", tool.toolName)
                        put("description", tool.description)
                        // Gemini accepts only a restricted schema subset; MCP
                        // schemas (e.g. Composio's x-mcp-header) must be cleaned
                        // first or the whole request fails with HTTP 400.
                        put("parameters", sanitizeGeminiSchema(paramsSchema))
                    }
                )
            }
            root.put(
                "tools",
                JSONArray().put(JSONObject().put("functionDeclarations", fnDecls))
            )
        }

        val genConfig = JSONObject()
        if (request.supportsTemperature) {
            genConfig.put("temperature", request.temperature.toDouble())
        }
        if (request.supportsTopP) {
            genConfig.put("topP", request.topP.toDouble())
        }
        if (request.maxTokens > 0) {
            genConfig.put("maxOutputTokens", request.maxTokens)
        }
        root.put("generationConfig", genConfig)
        return root
    }

    override suspend fun generateImage(
        request: ImageGenerationRequest
    ): Result<ImageGenerationOutput> = withContext(Dispatchers.IO) {
        try {
            val base = request.provider.baseUrl.trim().trimEnd('/')
            val cleanModel = request.modelId.removePrefix("models/")
            val enrichedPrompt = ModelCapabilityRegistry.enrichPromptWithStyle(request.prompt, request.stylePreset)

            if (cleanModel.startsWith("imagen", ignoreCase = true)) {
                val endpoint = "$base/models/$cleanModel:predict"
                val root = JSONObject().apply {
                    put("instances", JSONArray().put(JSONObject().put("prompt", enrichedPrompt)))
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("sampleCount", request.count.coerceIn(1, 4))
                            put("aspectRatio", request.aspectRatio.ifBlank { "1:1" })
                        }
                    )
                }
                val reqBuilder = Request.Builder()
                    .url(endpoint)
                    .post(root.toString().toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                if (request.apiKey.isNotBlank()) {
                    reqBuilder.header("x-goog-api-key", request.apiKey.trim())
                }
                okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val normError = ErrorNormalizer.normalizeHttpError(
                            statusCode = response.code,
                            responseBody = rawBody,
                            providerName = request.provider.displayName
                        )
                        return@withContext Result.failure(ProviderNormalizedException(normError))
                    }
                    val json = JSONObject(rawBody)
                    val preds = json.optJSONArray("predictions") ?: JSONArray()
                    val payloads = mutableListOf<String>()
                    for (i in 0 until preds.length()) {
                        val b64 = preds.optJSONObject(i)?.optString("bytesBase64Encoded", "").orEmpty()
                        val mime = preds.optJSONObject(i)?.optString("mimeType", "image/png") ?: "image/png"
                        if (b64.isNotBlank()) {
                            payloads.add("data:$mime;base64,$b64")
                        }
                    }
                    if (payloads.isEmpty()) {
                        return@withContext Result.failure(IllegalStateException("Gemini Imagen returned 0 images."))
                    }
                    return@withContext Result.success(ImageGenerationOutput(imagePayloads = payloads))
                }
            } else {
                // Gemini multimodal generateContent image synthesis
                val endpoint = "$base/models/$cleanModel:generateContent"
                val root = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().put(JSONObject().put("text", enrichedPrompt)))
                            }
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                    )
                }
                val reqBuilder = Request.Builder()
                    .url(endpoint)
                    .post(root.toString().toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                if (request.apiKey.isNotBlank()) {
                    reqBuilder.header("x-goog-api-key", request.apiKey.trim())
                }
                okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val normError = ErrorNormalizer.normalizeHttpError(
                            statusCode = response.code,
                            responseBody = rawBody,
                            providerName = request.provider.displayName
                        )
                        return@withContext Result.failure(ProviderNormalizedException(normError))
                    }
                    val json = JSONObject(rawBody)
                    val candidates = json.optJSONArray("candidates") ?: JSONArray()
                    val payloads = mutableListOf<String>()
                    for (i in 0 until candidates.length()) {
                        val parts = candidates.optJSONObject(i)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                        for (p in 0 until parts.length()) {
                            val inline = parts.optJSONObject(p)?.optJSONObject("inlineData")
                                ?: parts.optJSONObject(p)?.optJSONObject("inline_data")
                            if (inline != null) {
                                val mime = inline.optString("mimeType", inline.optString("mime_type", "image/png"))
                                val data = inline.optString("data", "")
                                if (data.isNotBlank()) {
                                    payloads.add("data:$mime;base64,$data")
                                }
                            }
                        }
                    }
                    if (payloads.isEmpty()) {
                        return@withContext Result.failure(
                            IllegalStateException("Model $cleanModel did not return inline image data. Try imagen-3.0-generate-002.")
                        )
                    }
                    return@withContext Result.success(ImageGenerationOutput(imagePayloads = payloads))
                }
            }
        } catch (e: Exception) {
            val normError = ErrorNormalizer.normalizeException(
                throwable = e,
                providerName = request.provider.displayName,
                baseUrl = request.provider.baseUrl
            )
            Result.failure(ProviderNormalizedException(normError))
        }
    }

    override suspend fun submitVideoGeneration(
        request: VideoGenerationRequest
    ): Result<VideoSubmissionResult> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = request.provider.baseUrl.trim().trimEnd('/')
            val cleanModel = request.modelId.removePrefix("models/")
                .ifBlank { "veo-2.0-generate-001" }
            val endpoint = "$cleanBase/models/$cleanModel:predictLongRunning"
            val enrichedPrompt = ModelCapabilityRegistry.enrichVideoPromptWithMotion(
                prompt = request.prompt,
                cameraMotion = request.cameraMotion
            )

            val payload = JSONObject().apply {
                put(
                    "instances",
                    JSONArray().put(
                        JSONObject().apply {
                            put("prompt", enrichedPrompt)
                        }
                    )
                )
                put(
                    "parameters",
                    JSONObject().apply {
                        put("aspectRatio", request.aspectRatio)
                        put("durationSeconds", request.durationSeconds)
                        if (request.negativePrompt.isNotBlank()) {
                            put("negativePrompt", request.negativePrompt.trim())
                        }
                    }
                )
            }

            val reqBuilder = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
            if (request.apiKey.isNotBlank()) {
                reqBuilder.header("x-goog-api-key", request.apiKey.trim())
            }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val normError = ErrorNormalizer.normalizeHttpError(
                        statusCode = response.code,
                        responseBody = rawBody,
                        providerName = request.provider.displayName
                    )
                    return@withContext Result.failure(ProviderNormalizedException(normError))
                }
                val root = JSONObject(rawBody)
                val opName = root.optString("name", "").ifBlank {
                    return@withContext Result.failure(
                        IllegalStateException("Veo 2 response did not contain a long-running operation name.")
                    )
                }
                Result.success(
                    VideoSubmissionResult(
                        remoteJobId = opName,
                        status = "QUEUED"
                    )
                )
            }
        } catch (e: Exception) {
            val normError = ErrorNormalizer.normalizeException(
                throwable = e,
                providerName = request.provider.displayName,
                baseUrl = request.provider.baseUrl
            )
            Result.failure(ProviderNormalizedException(normError))
        }
    }

    override suspend fun pollVideoGeneration(
        provider: ProviderInstanceEntity,
        apiKey: String,
        modelId: String,
        remoteJobId: String
    ): Result<VideoPollResult> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = provider.baseUrl.trim().trimEnd('/')
            val pollUrl = if (remoteJobId.startsWith("http")) {
                remoteJobId
            } else {
                "$cleanBase/${remoteJobId.trimStart('/')}"
            }

            val reqBuilder = Request.Builder()
                .url(pollUrl)
                .get()
                .header("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                reqBuilder.header("x-goog-api-key", apiKey.trim())
            }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val normError = ErrorNormalizer.normalizeHttpError(
                        statusCode = response.code,
                        responseBody = rawBody,
                        providerName = provider.displayName
                    )
                    return@withContext Result.failure(ProviderNormalizedException(normError))
                }
                val root = JSONObject(rawBody)
                val done = root.optBoolean("done", false)
                if (!done) {
                    return@withContext Result.success(
                        VideoPollResult(
                            status = "PROCESSING",
                            progressPercent = 65
                        )
                    )
                }
                val errObj = root.optJSONObject("error")
                if (errObj != null) {
                    return@withContext Result.success(
                        VideoPollResult(
                            status = "FAILED",
                            progressPercent = 100,
                            errorMessage = errObj.optString("message", "Veo 2 operation failed")
                        )
                    )
                }
                val respObj = root.optJSONObject("response")
                val videoUri = respObj?.optJSONObject("generateVideoResponse")
                    ?.optJSONArray("generatedSamples")
                    ?.optJSONObject(0)
                    ?.optJSONObject("video")
                    ?.optString("uri", "")
                    ?.takeIf { it.isNotBlank() }

                Result.success(
                    VideoPollResult(
                        status = "COMPLETED",
                        progressPercent = 100,
                        videoUrl = videoUri
                    )
                )
            }
        } catch (e: Exception) {
            val normError = ErrorNormalizer.normalizeException(
                throwable = e,
                providerName = provider.displayName,
                baseUrl = provider.baseUrl
            )
            Result.failure(ProviderNormalizedException(normError))
        }
    }
}

/**
 * Round 12: Gemini's function-declaration `parameters` accepts only a restricted
 * OpenAPI subset. Any other key — e.g. the `x-mcp-header` Composio embeds in its
 * MCP tool schemas — makes the whole request fail with HTTP 400 "Unknown name".
 * Rebuild the schema keeping only allowed keys, recursing into nested schemas
 * (properties values, items, anyOf/allOf). Scalars, enums and required lists
 * pass through untouched.
 */
internal fun sanitizeGeminiSchema(node: Any?): Any? {
    val allowed = setOf(
        "type", "format", "description", "nullable", "enum", "items", "properties",
        "required", "anyOf", "allOf", "minimum", "maximum", "exclusiveMinimum",
        "exclusiveMaximum", "multipleOf", "minLength", "maxLength", "pattern",
        "minItems", "maxItems", "minProperties", "maxProperties"
    )
    return when (node) {
        is JSONObject -> {
            val out = JSONObject()
            val keys = node.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in allowed) continue
                val value = node.opt(key)
                val cleaned: Any? = when (key) {
                    "properties" -> {
                        val props = value as? JSONObject
                        if (props == null) {
                            value
                        } else {
                            val cleanedProps = JSONObject()
                            val propKeys = props.keys()
                            while (propKeys.hasNext()) {
                                val propName = propKeys.next()
                                cleanedProps.put(propName, sanitizeGeminiSchema(props.opt(propName)))
                            }
                            cleanedProps
                        }
                    }
                    "items", "anyOf", "allOf" -> sanitizeGeminiSchema(value)
                    else -> value
                }
                out.put(key, cleaned)
            }
            if (out.has("properties") && !out.has("type")) out.put("type", "object")
            out
        }
        is JSONArray -> {
            val out = JSONArray()
            for (i in 0 until node.length()) out.put(sanitizeGeminiSchema(node.opt(i)))
            out
        }
        else -> node
    }
}
