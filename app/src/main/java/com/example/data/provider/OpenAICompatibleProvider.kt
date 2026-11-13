package com.example.data.provider

import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.ProviderPresetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * LiteRouter throttles clients that fire requests back-to-back (agent tool
 * turns, retries, model fetches). This gate reserves request-start slots at
 * least 7 seconds apart for any provider whose name or base URL mentions
 * LiteRouter, so concurrent callers queue up instead of bursting.
 */
/**
 * Pool providers (today: LiteRouter) front many upstream keys behind one
 * endpoint. Shared test so the request gate and the error mapper agree on
 * what counts as a pool provider. Round 11.
 */
internal fun isPoolProviderNameOrUrl(name: String, url: String): Boolean =
    name.contains("literouter", ignoreCase = true) || url.contains("literouter", ignoreCase = true)

object LiteRouterRequestGate {
    private const val MIN_GAP_MS = 7_000L
    private val mutex = Mutex()
    private var nextSlotMs = 0L

    private fun appliesTo(provider: ProviderInstanceEntity): Boolean =
        isPoolProviderNameOrUrl(provider.displayName, provider.baseUrl)

    suspend fun awaitTurn(provider: ProviderInstanceEntity) {
        if (!appliesTo(provider)) return
        val waitMs = mutex.withLock {
            val now = System.currentTimeMillis()
            val start = maxOf(now, nextSlotMs)
            nextSlotMs = start + MIN_GAP_MS
            start - now
        }
        if (waitMs > 0) delay(waitMs)
    }
}

/**
 * Generic OpenAI-Compatible Adapter reused by:
 * - OmniRoute (default local/LAN/Tailscale gateway, including /models combos & fallbacks)
 * - OpenAI
 * - OpenRouter
 * - Groq
 * - Together AI
 * - Mistral AI
 * - Custom (OpenAI-compatible)
 */
class OpenAICompatibleProvider(
    private val okHttpClient: OkHttpClient
) : AiProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun testConnection(
        provider: ProviderInstanceEntity,
        apiKey: String
    ): Result<List<ModelDescriptorEntity>> = listModels(provider, apiKey)

    private suspend fun ensureLocalOmniRouteListening(baseUrl: String) {
        if (baseUrl.contains("localhost:20128", ignoreCase = true) ||
            baseUrl.contains("127.0.0.1:20128", ignoreCase = true)
        ) {
            if (!EmbeddedOmniRouteServer.statusFlow.value.isListening) {
                EmbeddedOmniRouteServer.startServer(20128)
            }
        }
    }

    override suspend fun listModels(
        provider: ProviderInstanceEntity,
        apiKey: String
    ): Result<List<ModelDescriptorEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureLocalOmniRouteListening(provider.baseUrl)
            LiteRouterRequestGate.awaitTurn(provider)
            val candidateUrls = EndpointUrlNormalizer.buildModelsCandidateUrls(provider.baseUrl)
            var lastException: Throwable? = null

            for (modelsUrl in candidateUrls) {
                try {
                    val models = RetryPolicy.withRetry(
                        maxAttempts = 3,
                        providerName = provider.displayName,
                        baseUrl = provider.baseUrl
                    ) {
                        val requestBuilder = Request.Builder()
                            .url(modelsUrl)
                            .get()
                            .header("Accept", "application/json")

                        if (apiKey.isNotBlank()) {
                            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
                        }
                        if (provider.presetEnum() == ProviderPresetType.OPENROUTER) {
                            requestBuilder.header("HTTP-Referer", "https://omniai.studio")
                            requestBuilder.header("X-Title", "Aerodock")
                        }

                        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                            val bodyStr = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                val provErr = ProviderErrorMapper.fromHttpResponse(
                                    statusCode = response.code,
                                    responseBody = bodyStr,
                                    headers = response.headers,
                                    providerName = provider.displayName,
                                    attemptedMethod = "GET",
                                    attemptedUrl = modelsUrl
                                )
                                val norm = ProviderErrorMapper.toNormalizedAiError(
                                    error = provErr,
                                    providerName = provider.displayName,
                                    baseUrl = provider.baseUrl
                                )
                                throw ProviderErrorException(provErr, norm)
                            }

                            val parsedModels = parseModelsJson(provider.id, bodyStr)
                            if (parsedModels.isEmpty()) {
                                throw IllegalStateException("Endpoint responded with 200 OK but returned 0 models.")
                            }
                            parsedModels
                        }
                    }
                    return@runCatching models
                } catch (e: Throwable) {
                    lastException = e
                    val isRouteMismatch = (e as? ProviderErrorException)?.providerError is ProviderError.Route ||
                        e is IllegalStateException
                    if (!isRouteMismatch) {
                        throw e
                    }
                }
            }
            throw (lastException ?: IllegalStateException("Unable to fetch models from ${provider.displayName}"))
        }.recoverCatching { throwable ->
            if (throwable is ProviderNormalizedException) throw throwable
            if (throwable is ProviderErrorException) {
                throw ProviderNormalizedException(throwable.normalizedError)
            }
            throw ProviderNormalizedException(
                ErrorNormalizer.normalizeException(
                    throwable = throwable,
                    providerName = provider.displayName,
                    baseUrl = provider.baseUrl
                )
            )
        }
    }

    internal fun parseModelsJson(
        providerInstanceId: String,
        rawJson: String
    ): List<ModelDescriptorEntity> {
        val result = mutableListOf<ModelDescriptorEntity>()
        val seenIds = mutableSetOf<String>()
        var detectedOmniRouteCatalog = providerInstanceId.contains("omniroute", ignoreCase = true)

        fun addRawModelId(rawId: String, explicitCombo: Boolean = false) {
            val clean = rawId.trim()
            if (clean.isEmpty()) return
            if (EndpointUrlNormalizer.isBrowserScrapedOrOAuthIdeModel(clean)) {
                detectedOmniRouteCatalog = true
                return
            }
            if (clean.startsWith("ddgw/", ignoreCase = true) || clean.startsWith("auto/", ignoreCase = true)) {
                detectedOmniRouteCatalog = true
            }
            if (!seenIds.add(clean)) return
            result.add(
                ModelCapabilityRegistry.buildDescriptor(
                    providerInstanceId = providerInstanceId,
                    rawModelId = clean,
                    displayNameOverride = clean,
                    explicitCombo = explicitCombo
                )
            )
        }

        fun addModelItem(obj: JSONObject, explicitCombo: Boolean = false) {
            val modelId = obj.optString("id")
                .ifBlank { obj.optString("name") }
                .ifBlank { obj.optString("model") }
                .ifBlank { obj.optString("slug") }
                .ifBlank { obj.optString("alias") }
                .trim()
            if (modelId.isEmpty()) return
            if (EndpointUrlNormalizer.isBrowserScrapedOrOAuthIdeModel(modelId)) {
                detectedOmniRouteCatalog = true
                return
            }
            if (modelId.startsWith("ddgw/", ignoreCase = true) || modelId.startsWith("auto/", ignoreCase = true)) {
                detectedOmniRouteCatalog = true
            }
            if (!seenIds.add(modelId)) return

            val displayName = obj.optString("display_name")
                .ifBlank { obj.optString("title") }
                .ifBlank { obj.optString("name") }
                .ifBlank { modelId }
            val typeStr = obj.optString("type").lowercase()
            val ownedBy = obj.optString("owned_by").lowercase()
            val modalityRaw = obj.optString("modality").uppercase()
            val registeredCombo = EmbeddedOmniRouteServer.findCombo(modelId)
            val isCombo = explicitCombo ||
                registeredCombo != null ||
                obj.optBoolean("is_combo", false) ||
                obj.optBoolean("combo", false) ||
                typeStr == "combo" ||
                typeStr == "fallback" ||
                typeStr == "router" ||
                ownedBy.contains("combo")

            val explicitCaps = when (modalityRaw) {
                "VIDEO" -> setOf( com.example.data.local.CapabilityTag.VIDEO )
                "IMAGE" -> setOf( com.example.data.local.CapabilityTag.IMAGE )
                "CHAT" -> setOf( com.example.data.local.CapabilityTag.CHAT )
                else -> null
            }

            result.add(
                ModelCapabilityRegistry.buildDescriptor(
                    providerInstanceId = providerInstanceId,
                    rawModelId = modelId,
                    displayNameOverride = displayName,
                    explicitCombo = isCombo,
                    explicitCapabilities = explicitCaps
                )
            )
        }

        fun parseArray(arr: JSONArray, explicitCombo: Boolean = false) {
            for (i in 0 until arr.length()) {
                val itemObj = arr.optJSONObject(i)
                if (itemObj != null) {
                    addModelItem(itemObj, explicitCombo = explicitCombo)
                } else {
                    val strVal = arr.optString(i, "").trim()
                    if (strVal.isNotEmpty() && !strVal.startsWith("{")) {
                        addRawModelId(strVal, explicitCombo = explicitCombo)
                    }
                }
            }
        }

        val trimmed = rawJson.trim()
        if (trimmed.startsWith("[")) {
            parseArray(JSONArray(trimmed))
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val dataArr = root.optJSONArray("data")
                ?: root.optJSONArray("models")
                ?: root.optJSONArray("items")
                ?: root.optJSONArray("result")
            if (dataArr != null) {
                parseArray(dataArr)
            } else {
                // Support nested data.models or map-keyed models object
                val dataObj = root.optJSONObject("data") ?: root.optJSONObject("models")
                if (dataObj != null) {
                    val nestedArr = dataObj.optJSONArray("models") ?: dataObj.optJSONArray("data")
                    if (nestedArr != null) {
                        parseArray(nestedArr)
                    } else {
                        val keys = dataObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val childObj = dataObj.optJSONObject(key)
                            if (childObj != null && (childObj.has("id") || childObj.has("name") || childObj.has("model"))) {
                                addModelItem(childObj)
                            } else {
                                addRawModelId(key)
                            }
                        }
                    }
                }
            }

            // OmniRoute / gateway combo, fallback, and route arrays if returned alongside /models
            val comboKeys = listOf("combos", "fallbacks", "routes", "aliases")
            for (comboKey in comboKeys) {
                val combosArr = root.optJSONArray(comboKey)
                if (combosArr != null) {
                    parseArray(combosArr, explicitCombo = true)
                }
            }
        }

        if (detectedOmniRouteCatalog) {
            val omniDescriptors = mutableListOf<ModelDescriptorEntity>()
            for (combo in EmbeddedOmniRouteServer.combosFlow.value) {
                if (seenIds.add(combo.id)) {
                    val cap = when (combo.modality.uppercase()) {
                        "VIDEO" -> setOf(com.example.data.local.CapabilityTag.VIDEO)
                        "IMAGE" -> setOf(com.example.data.local.CapabilityTag.IMAGE)
                        else -> setOf(com.example.data.local.CapabilityTag.CHAT)
                    }
                    omniDescriptors.add(
                        ModelCapabilityRegistry.buildDescriptor(
                            providerInstanceId = providerInstanceId,
                            rawModelId = combo.id,
                            displayNameOverride = combo.displayName,
                            explicitCombo = true,
                            preserveFavorite = true,
                            explicitCapabilities = cap
                        )
                    )
                }
            }
            for ((id, label) in EndpointUrlNormalizer.defaultWorkingOmniRouteModels) {
                if (seenIds.add(id)) {
                    omniDescriptors.add(
                        ModelCapabilityRegistry.buildDescriptor(
                            providerInstanceId = providerInstanceId,
                            rawModelId = id,
                            displayNameOverride = label,
                            preserveFavorite = true,
                            explicitCapabilities = setOf(com.example.data.local.CapabilityTag.CHAT)
                        )
                    )
                }
            }
            for ((id, label) in EndpointUrlNormalizer.defaultOmniRouteVideoModels) {
                if (seenIds.add(id)) {
                    omniDescriptors.add(
                        ModelCapabilityRegistry.buildDescriptor(
                            providerInstanceId = providerInstanceId,
                            rawModelId = id,
                            displayNameOverride = label,
                            explicitCombo = id.contains("combo") || id.contains("director"),
                            preserveFavorite = true,
                            explicitCapabilities = setOf(com.example.data.local.CapabilityTag.VIDEO)
                        )
                    )
                }
            }
            for ((id, label) in EndpointUrlNormalizer.defaultOmniRouteImageModels) {
                if (seenIds.add(id)) {
                    omniDescriptors.add(
                        ModelCapabilityRegistry.buildDescriptor(
                            providerInstanceId = providerInstanceId,
                            rawModelId = id,
                            displayNameOverride = label,
                            explicitCombo = id.contains("combo"),
                            preserveFavorite = true,
                            explicitCapabilities = setOf(com.example.data.local.CapabilityTag.IMAGE)
                        )
                    )
                }
            }
            result.addAll(0, omniDescriptors)
        }
        return result
    }

    override fun streamChat(request: ChatCompletionRequest): Flow<ChatStreamEvent> = flow {
        ensureLocalOmniRouteListening(request.provider.baseUrl)
        LiteRouterRequestGate.awaitTurn(request.provider)
        val candidateUrls = EndpointUrlNormalizer.buildChatCompletionCandidateUrls(request.provider.baseUrl)
        val isOmniRouteGateway = request.provider.presetEnum() == ProviderPresetType.OMNIROUTE ||
            request.provider.baseUrl.contains("20128") ||
            request.modelId.contains("/")

        // If the conversation is still pointing at a known browser-scraped or unauthenticated OAuth model on OmniRoute,
        // automatically promote the primary request to ddgw/gpt-4o-mini so it never asks for Playwright or OAuth.
        val effectivePrimaryRequest = if (isOmniRouteGateway && EndpointUrlNormalizer.isBrowserScrapedOrOAuthIdeModel(request.modelId)) {
            request.copy(modelId = "ddgw/gpt-4o-mini")
        } else {
            request
        }

        val streamingPayloadBytes = buildChatPayloadJson(effectivePrimaryRequest, stream = true, minimalCompatMode = false).toString().toRequestBody(jsonMediaType)
        val nonStreamingPayloadBytes = buildChatPayloadJson(effectivePrimaryRequest, stream = false, minimalCompatMode = true).toString().toRequestBody(jsonMediaType)

        try {
            var activeResponse: Response? = null
            var lastError: NormalizedAiError? = null

            for ((urlIdx, chatUrl) in candidateUrls.withIndex()) {
                try {
                    val resp = RetryPolicy.withRetry(
                        maxAttempts = 2,
                        providerName = request.provider.displayName,
                        baseUrl = request.provider.baseUrl
                    ) { attempt ->
                        val bodyToSend = if (attempt == 1) streamingPayloadBytes else nonStreamingPayloadBytes
                        val acceptHeader = if (attempt == 1) "text/event-stream, application/json" else "application/json"
                        val reqBuilder = Request.Builder()
                            .url(chatUrl)
                            .post(bodyToSend)
                            .header("Content-Type", "application/json; charset=utf-8")
                            .header("Accept", acceptHeader)

                        if (request.apiKey.isNotBlank()) {
                            reqBuilder.header("Authorization", "Bearer ${request.apiKey.trim()}")
                        }
                        if (request.provider.presetEnum() == ProviderPresetType.OPENROUTER) {
                            reqBuilder.header("HTTP-Referer", "https://omniai.studio")
                            reqBuilder.header("X-Title", "Aerodock")
                        }

                        val r = okHttpClient.newCall(reqBuilder.build()).execute()
                        if (!r.isSuccessful) {
                            val errBody = r.body?.string().orEmpty()
                            val code = r.code
                            val headers = r.headers
                            r.close()
                            val mappedErr = ProviderErrorMapper.fromHttpResponse(
                                statusCode = code,
                                responseBody = errBody,
                                headers = headers,
                                providerName = request.provider.displayName,
                                attemptedMethod = "POST",
                                attemptedUrl = chatUrl
                            )
                            val provErr = if (attempt == 1 && (code == 400 || code == 422) && mappedErr is ProviderError.Unknown) {
                                ProviderError.Transient(
                                    message = mappedErr.message,
                                    httpStatus = code,
                                    causeClass = "HTTP_${code}_COMPAT_RETRY"
                                )
                            } else {
                                mappedErr
                            }
                            val norm = ProviderErrorMapper.toNormalizedAiError(
                                error = provErr,
                                providerName = request.provider.displayName,
                                baseUrl = request.provider.baseUrl
                            )
                            throw ProviderErrorException(provErr, norm)
                        }
                        r
                    }
                    activeResponse = resp
                    break
                } catch (e: ProviderErrorException) {
                    lastError = e.normalizedError
                    // If 404/405 Route mismatch and we have another candidate URL (e.g. with/without /v1), try next candidate
                    if (e.providerError is ProviderError.Route &&
                        !EndpointUrlNormalizer.isOmniRouteUpstreamModelError(e.providerError.message) &&
                        urlIdx < candidateUrls.lastIndex
                    ) {
                        continue
                    }

                    // Transparent Zero-Config Fallback for OmniRoute: if the selected model failed upstream
                    // (e.g. Playwright required, OAuth session missing, or non-ping SSE timeout), automatically
                    // fall back through zero-config ddgw/* models on the same gateway before emitting any error.
                    if (isOmniRouteGateway && e.providerError !is ProviderError.Auth) {
                        val fallbackIds = EndpointUrlNormalizer.defaultWorkingOmniRouteModels
                            .map { it.first }
                            .filter { !it.equals(effectivePrimaryRequest.modelId, ignoreCase = true) }

                        for (fallbackModelId in fallbackIds) {
                            val fallbackReq = effectivePrimaryRequest.copy(modelId = fallbackModelId)
                            val fallbackBody = buildChatPayloadJson(
                                fallbackReq,
                                stream = false,
                                minimalCompatMode = true
                            ).toString().toRequestBody(jsonMediaType)
                            val fbBuilder = Request.Builder()
                                .url(chatUrl)
                                .post(fallbackBody)
                                .header("Content-Type", "application/json; charset=utf-8")
                                .header("Accept", "application/json")
                            if (request.apiKey.isNotBlank()) {
                                fbBuilder.header("Authorization", "Bearer ${request.apiKey.trim()}")
                            }
                            val fbResp = runCatching { okHttpClient.newCall(fbBuilder.build()).execute() }.getOrNull()
                            if (fbResp != null) {
                                if (fbResp.isSuccessful) {
                                    activeResponse = fbResp
                                    break
                                } else {
                                    fbResp.close()
                                }
                            }
                        }
                        if (activeResponse != null) {
                            break
                        }
                    }

                    emit(ChatStreamEvent.Error(e.normalizedError))
                    return@flow
                }
            }

            val response = activeResponse
            if (response == null) {
                emit(
                    ChatStreamEvent.Error(
                        lastError ?: NormalizedAiError(
                            category = ErrorCategory.SERVER_ERROR,
                            title = "Route Error",
                            message = "Could not connect to chat completions endpoint on ${request.provider.displayName}.",
                            suggestedFix = "Ensure Base URL ends with /v1."
                        )
                    )
                )
                return@flow
            }

            response.use { resp ->
                val source = resp.body?.source()
                if (source == null) {
                    emit(
                        ChatStreamEvent.Error(
                            NormalizedAiError(
                                category = ErrorCategory.SERVER_ERROR,
                                title = "Empty Response",
                                message = "${request.provider.displayName} returned an empty response body.",
                                suggestedFix = "Try regenerating or selecting another model."
                            )
                        )
                    )
                    return@flow
                }

                // Accumulators for streamed OpenAI tool_calls deltas indexed by `index`
                val toolCallIds = mutableMapOf<Int, String>()
                val toolCallNames = mutableMapOf<Int, StringBuilder>()
                val toolCallArgs = mutableMapOf<Int, StringBuilder>()
                val nonSseLinesBuffer = StringBuilder()
                var sawSseDataLine = false

                suspend fun flushStreamedToolCalls() {
                    for ((idx, nameBuilder) in toolCallNames.entries.sortedBy { it.key }) {
                        val toolName = nameBuilder.toString().trim()
                        if (toolName.isNotEmpty()) {
                            val callId = toolCallIds[idx]?.ifBlank { "call_$idx" } ?: "call_$idx"
                            val argsStr = toolCallArgs[idx]?.toString()?.trim()?.ifBlank { "{}" } ?: "{}"
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
                    }
                    toolCallIds.clear()
                    toolCallNames.clear()
                    toolCallArgs.clear()
                }

                // Resilient line-by-line reader that supports both SSE (`data: ...`) regardless of Content-Type
                // and standard JSON / NDJSON responses when a proxy ignores `stream: true`.
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue

                    if (trimmed.startsWith("data:")) {
                        sawSseDataLine = true
                        val dataStr = trimmed.removePrefix("data:").trim()
                        if (dataStr == "[DONE]") {
                            flushStreamedToolCalls()
                            emit(ChatStreamEvent.Done)
                            return@flow
                        }
                        if (dataStr.isEmpty()) continue

                        val chunkJson = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue
                        val errObj = chunkJson.optJSONObject("error")
                        if (errObj != null) {
                            emit(
                                ChatStreamEvent.Error(
                                    ErrorNormalizer.normalizeHttpError(
                                        statusCode = 400,
                                        responseBody = errObj.toString(),
                                        providerName = request.provider.displayName
                                    )
                                )
                            )
                            return@flow
                        }

                        val choices = chunkJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val first = choices.optJSONObject(0)
                            val delta = first?.optJSONObject("delta") ?: first?.optJSONObject("message")
                            val textDelta = delta?.optString("content", "")
                                ?.takeIf { !delta.isNull("content") }
                                .orEmpty()
                            if (textDelta.isNotEmpty()) {
                                emit(ChatStreamEvent.Delta(textDelta))
                            }

                            val deltaToolCalls = delta?.optJSONArray("tool_calls")
                            if (deltaToolCalls != null) {
                                for (i in 0 until deltaToolCalls.length()) {
                                    val tc = deltaToolCalls.optJSONObject(i) ?: continue
                                    val idx = tc.optInt("index", i)
                                    val idPart = tc.optString("id", "")
                                    if (idPart.isNotBlank()) {
                                        toolCallIds[idx] = idPart
                                    }
                                    val fn = tc.optJSONObject("function")
                                    if (fn != null) {
                                        val namePart = fn.optString("name", "")
                                        if (namePart.isNotEmpty()) {
                                            toolCallNames.getOrPut(idx) { StringBuilder() }.append(namePart)
                                        }
                                        val argsPart = fn.optString("arguments", "")
                                        if (argsPart.isNotEmpty()) {
                                            toolCallArgs.getOrPut(idx) { StringBuilder() }.append(argsPart)
                                        }
                                    }
                                }
                            }
                        }

                        val usageObj = chunkJson.optJSONObject("usage")
                        if (usageObj != null) {
                            val parsed = ModelPriceTable.parseOpenAiUsage(chunkJson)
                            val pTokens = parsed?.inputTokens ?: usageObj.optInt("prompt_tokens", 0)
                            val cTokens = parsed?.outputTokens ?: usageObj.optInt("completion_tokens", 0)
                            val cachedTokens = parsed?.cachedInputTokens ?: 0
                            if (pTokens > 0 || cTokens > 0 || cachedTokens > 0) {
                                emit(ChatStreamEvent.Usage(pTokens, cTokens, cachedTokens))
                            }
                        }
                    } else if (!sawSseDataLine) {
                        nonSseLinesBuffer.appendLine(line)
                    }
                }

                if (!sawSseDataLine && nonSseLinesBuffer.isNotBlank()) {
                    val fullBody = nonSseLinesBuffer.toString().trim()
                    val json = JSONObject(fullBody)
                    val choices = json.optJSONArray("choices")
                    val firstChoice = choices?.optJSONObject(0)
                    val messageObj = firstChoice?.optJSONObject("message") ?: firstChoice?.optJSONObject("delta")
                    val content = messageObj?.optString("content", "")
                        ?.takeIf { !messageObj.isNull("content") }
                        .orEmpty()
                    if (content.isNotEmpty()) {
                        emit(ChatStreamEvent.Delta(content))
                    }
                    val toolCallsArr = messageObj?.optJSONArray("tool_calls")
                    if (toolCallsArr != null) {
                        for (i in 0 until toolCallsArr.length()) {
                            val tc = toolCallsArr.optJSONObject(i) ?: continue
                            val fn = tc.optJSONObject("function") ?: continue
                            val name = fn.optString("name", "").trim()
                            if (name.isNotEmpty()) {
                                emit(
                                    ChatStreamEvent.ToolCall(
                                        ModelToolCallRequest(
                                            id = tc.optString("id", "call_$i").ifBlank { "call_$i" },
                                            toolName = name,
                                            argumentsJson = fn.optString("arguments", "{}").ifBlank { "{}" }
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        val fnCall = messageObj?.optJSONObject("function_call")
                        if (fnCall != null) {
                            val name = fnCall.optString("name", "").trim()
                            if (name.isNotEmpty()) {
                                emit(
                                    ChatStreamEvent.ToolCall(
                                        ModelToolCallRequest(
                                            id = "call_0",
                                            toolName = name,
                                            argumentsJson = fnCall.optString("arguments", "{}").ifBlank { "{}" }
                                        )
                                    )
                                )
                            }
                        }
                    }
                    val usageObj = json.optJSONObject("usage")
                    if (usageObj != null) {
                        val parsed = ModelPriceTable.parseOpenAiUsage(json)
                        emit(
                            ChatStreamEvent.Usage(
                                promptTokens = parsed?.inputTokens ?: usageObj.optInt("prompt_tokens", 0),
                                completionTokens = parsed?.outputTokens ?: usageObj.optInt("completion_tokens", 0),
                                cachedInputTokens = parsed?.cachedInputTokens ?: 0
                            )
                        )
                    }
                    emit(ChatStreamEvent.Done)
                    return@flow
                }

                flushStreamedToolCalls()
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

    internal suspend fun parseSseLines(
        lines: Sequence<String>,
        emitEvent: suspend (ChatStreamEvent) -> Unit
    ) {
        val toolCallIds = mutableMapOf<Int, String>()
        val toolCallNames = mutableMapOf<Int, StringBuilder>()
        val toolCallArgs = mutableMapOf<Int, StringBuilder>()

        suspend fun flush() {
            for ((idx, nameBuilder) in toolCallNames.entries.sortedBy { it.key }) {
                val toolName = nameBuilder.toString().trim()
                if (toolName.isNotEmpty()) {
                    emitEvent(
                        ChatStreamEvent.ToolCall(
                            ModelToolCallRequest(
                                id = toolCallIds[idx]?.ifBlank { "call_$idx" } ?: "call_$idx",
                                toolName = toolName,
                                argumentsJson = toolCallArgs[idx]?.toString()?.trim()?.ifBlank { "{}" } ?: "{}"
                            )
                        )
                    )
                }
            }
            toolCallIds.clear()
            toolCallNames.clear()
            toolCallArgs.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) continue
            val dataStr = trimmed.removePrefix("data:").trim()
            if (dataStr == "[DONE]") {
                flush()
                emitEvent(ChatStreamEvent.Done)
                return
            }
            val chunkJson = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue
            val choices = chunkJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val delta = choices.optJSONObject(0)?.optJSONObject("delta")
                val textDelta = delta?.optString("content", "")
                    ?.takeIf { !delta.isNull("content") }
                    .orEmpty()
                if (textDelta.isNotEmpty()) {
                    emitEvent(ChatStreamEvent.Delta(textDelta))
                }
                val deltaToolCalls = delta?.optJSONArray("tool_calls")
                if (deltaToolCalls != null) {
                    for (i in 0 until deltaToolCalls.length()) {
                        val tc = deltaToolCalls.optJSONObject(i) ?: continue
                        val idx = tc.optInt("index", i)
                        val idPart = tc.optString("id", "")
                        if (idPart.isNotBlank()) toolCallIds[idx] = idPart
                        val fn = tc.optJSONObject("function")
                        if (fn != null) {
                            val namePart = fn.optString("name", "")
                            if (namePart.isNotEmpty()) {
                                toolCallNames.getOrPut(idx) { StringBuilder() }.append(namePart)
                            }
                            val argsPart = fn.optString("arguments", "")
                            if (argsPart.isNotEmpty()) {
                                toolCallArgs.getOrPut(idx) { StringBuilder() }.append(argsPart)
                            }
                        }
                    }
                }
            }
        }
        flush()
        emitEvent(ChatStreamEvent.Done)
    }

    internal fun buildChatPayloadJson(
        request: ChatCompletionRequest,
        stream: Boolean = true,
        minimalCompatMode: Boolean = false
    ): JSONObject {
        val root = JSONObject()
        root.put("model", request.modelId)
        root.put("stream", stream)

        if (!minimalCompatMode) {
            if (request.supportsTemperature) {
                root.put("temperature", request.temperature.toDouble())
            }
            if (request.supportsTopP && request.topP < 0.999f) {
                root.put("top_p", request.topP.toDouble())
            }
            if (request.maxTokens > 0) {
                root.put("max_tokens", request.maxTokens)
            }
        }

        // Bound the outbound payload (notably the agent tool loop, which re-sends its
        // working history every iteration) before serialising it.
        val outboundMessages = SmartContextTrimmer.compactToolLoopMessages(
            request.messages,
            SmartContextTrimmer.calculateTrimmingThresholdTokens(
                SmartContextTrimmer.resolveContextWindowTokens(request.modelId)
            ),
            request.systemPrompt
        )

        val messagesArr = JSONArray()
        val stableSystemText = if (request.baseSystemPrompt.isNotBlank() || request.enabledSkillsBlock.isNotBlank()) {
            PromptCacheManager.buildStableSystemPrefix(
                baseSystemPrompt = request.baseSystemPrompt.ifBlank { request.systemPrompt },
                enabledSkillsBlock = request.enabledSkillsBlock
            )
        } else {
            request.systemPrompt.trim()
        }
        if (!minimalCompatMode && request.supportsSystemPrompt && stableSystemText.isNotBlank()) {
            val sysObj = JSONObject()
            sysObj.put("role", "system")
            sysObj.put("content", stableSystemText)
            messagesArr.put(sysObj)
        }

        for (msg in outboundMessages) {
            val msgObj = JSONObject()
            val roleLower = msg.role.lowercase()
            msgObj.put("role", roleLower)

            when {
                roleLower == "tool" -> {
                    msgObj.put("tool_call_id", msg.toolCallId ?: "call_0")
                    if (!msg.toolName.isNullOrBlank()) {
                        msgObj.put("name", msg.toolName)
                    }
                    msgObj.put("content", msg.content)
                }
                msg.toolCalls.isNotEmpty() -> {
                    msgObj.put("role", "assistant")
                    if (msg.content.isNotBlank()) {
                        msgObj.put("content", msg.content)
                    } else {
                        msgObj.put("content", JSONObject.NULL)
                    }
                    val tcArr = JSONArray()
                    for (tc in msg.toolCalls) {
                        tcArr.put(
                            JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put(
                                    "function",
                                    JSONObject().apply {
                                        put("name", tc.toolName)
                                        put("arguments", tc.argumentsJson)
                                    }
                                )
                            }
                        )
                    }
                    msgObj.put("tool_calls", tcArr)
                }
                (msg.mediaAttachments.isNotEmpty() || !msg.imageDataUri.isNullOrBlank()) && roleLower == "user" -> {
                    val contentArr = JSONArray()
                    if (msg.content.isNotBlank()) {
                        val textBlock = JSONObject().apply {
                            put("type", "text")
                            put("text", msg.content)
                        }
                        contentArr.put(textBlock)
                    }
                    if (msg.mediaAttachments.isNotEmpty()) {
                        for (media in msg.mediaAttachments) {
                            val mediaBlock = JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().put("url", media.dataUri))
                            }
                            contentArr.put(mediaBlock)
                        }
                    } else if (!msg.imageDataUri.isNullOrBlank()) {
                        val imageBlock = JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", msg.imageDataUri))
                        }
                        contentArr.put(imageBlock)
                    }
                    msgObj.put("content", contentArr)
                }
                else -> {
                    msgObj.put("content", msg.content)
                }
            }
            messagesArr.put(msgObj)
        }

        root.put("messages", messagesArr)

        if (!minimalCompatMode && request.tools.isNotEmpty()) {
            val sortedTools = PromptCacheManager.sortToolsDeterministically(request.tools)
            val toolsArr = JSONArray()
            for (tool in sortedTools) {
                val paramsSchema = runCatching { JSONObject(tool.inputSchemaJson) }.getOrElse {
                    JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    }
                }
                toolsArr.put(
                    JSONObject().apply {
                        put("type", "function")
                        put(
                            "function",
                            JSONObject().apply {
                                put("name", tool.toolName)
                                put("description", tool.description)
                                put("parameters", paramsSchema)
                            }
                        )
                    }
                )
            }
            root.put("tools", toolsArr)
            root.put("tool_choice", "auto")
        }
        return root
    }

    override suspend fun generateImage(
        request: ImageGenerationRequest
    ): Result<ImageGenerationOutput> = withContext(Dispatchers.IO) {
        try {
            ensureLocalOmniRouteListening(request.provider.baseUrl)
            LiteRouterRequestGate.awaitTurn(request.provider)
            val base = request.provider.baseUrl.trim().trimEnd('/')
            val endpoint = if (base.endsWith("/images/generations")) {
                base
            } else {
                "$base/images/generations"
            }

            val payload = buildImageGenerationPayloadJson(request)
            val body = payload.toString().toRequestBody(jsonMediaType)

            val httpReqBuilder = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")

            if (request.apiKey.isNotBlank()) {
                httpReqBuilder.header("Authorization", "Bearer ${request.apiKey.trim()}")
            }

            okHttpClient.newCall(httpReqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val normError = ErrorNormalizer.normalizeHttpError(
                        statusCode = response.code,
                        responseBody = rawBody,
                        providerName = request.provider.displayName
                    )
                    return@withContext Result.failure(ProviderNormalizedException(normError))
                }

                val parsed = parseImageGenerationResponseJson(rawBody, request.seed)
                if (parsed.imagePayloads.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("${request.provider.displayName} returned 0 images.")
                    )
                }
                Result.success(parsed)
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

    internal fun buildImageGenerationPayloadJson(request: ImageGenerationRequest): JSONObject {
        val lowerModel = request.modelId.lowercase()
        val isDalle3 = lowerModel.contains("dall-e-3")
        val enrichedPrompt = ModelCapabilityRegistry.enrichPromptWithStyle(
            prompt = if (request.isUpscaleRequest) {
                "High-resolution 2x upscale, ultra-sharp details: ${request.prompt}"
            } else {
                request.prompt
            },
            stylePreset = request.stylePreset
        )

        val root = JSONObject()
        root.put("model", request.modelId)
        root.put("prompt", enrichedPrompt)
        root.put("n", if (isDalle3) 1 else request.count.coerceIn(1, 4))

        // Normalize size for OpenAI-strict endpoints vs flexible diffusion gateways
        val normalizedSize = when {
            isDalle3 && request.aspectRatio == "16:9" -> "1792x1024"
            isDalle3 && request.aspectRatio == "9:16" -> "1024x1792"
            isDalle3 -> "1024x1024"
            else -> request.size.ifBlank { "1024x1024" }
        }
        root.put("size", normalizedSize)

        if (isDalle3 || lowerModel.contains("gpt-image")) {
            root.put("quality", request.quality.ifBlank { "standard" })
        }

        if (!isDalle3) {
            if (request.negativePrompt.isNotBlank()) {
                root.put("negative_prompt", request.negativePrompt.trim())
            }
            if (request.seed != null) {
                root.put("seed", request.seed)
            }
            if (lowerModel.contains("flux") || lowerModel.contains("sd") || request.provider.presetType == "TOGETHER" || request.provider.presetType == "OMNIROUTE") {
                root.put("steps", request.steps.coerceIn(1, 60))
                root.put("guidance_scale", request.guidanceScale.toDouble())
            }
            if (!request.sourceImageDataUri.isNullOrBlank()) {
                root.put("image_url", request.sourceImageDataUri)
                root.put("strength", request.imageToImageStrength.toDouble())
            }
        }
        return root
    }

    internal fun parseImageGenerationResponseJson(
        rawJson: String,
        requestedSeed: Long? = null
    ): ImageGenerationOutput {
        val root = JSONObject(rawJson)
        val payloads = mutableListOf<String>()
        var revisedPrompt: String? = null
        var seedUsed: Long? = requestedSeed

        val dataArr = root.optJSONArray("data") ?: root.optJSONArray("images")
        if (dataArr != null) {
            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i)
                if (item != null) {
                    val b64 = item.optString("b64_json", "").ifBlank {
                        item.optString("base64", "")
                    }
                    val url = item.optString("url", "")
                    when {
                        b64.isNotBlank() -> {
                            val clean = if (b64.startsWith("data:image")) b64 else "data:image/png;base64,$b64"
                            payloads.add(clean)
                        }
                        url.isNotBlank() -> payloads.add(url)
                    }
                    if (revisedPrompt == null && item.has("revised_prompt")) {
                        revisedPrompt = item.optString("revised_prompt").takeIf { it.isNotBlank() }
                    }
                    if (item.has("seed")) {
                        seedUsed = item.optLong("seed", seedUsed ?: 0L)
                    }
                } else {
                    val str = dataArr.optString(i, "")
                    if (str.isNotBlank()) payloads.add(str)
                }
            }
        }
        return ImageGenerationOutput(
            imagePayloads = payloads,
            revisedPrompt = revisedPrompt,
            seedUsed = seedUsed
        )
    }

    override suspend fun submitVideoGeneration(
        request: VideoGenerationRequest
    ): Result<VideoSubmissionResult> = withContext(Dispatchers.IO) {
        try {
            ensureLocalOmniRouteListening(request.provider.baseUrl)
            LiteRouterRequestGate.awaitTurn(request.provider)
            val candidateUrls = EndpointUrlNormalizer.buildVideoGenerationCandidateUrls(request.provider.baseUrl)
            val enrichedPrompt = ModelCapabilityRegistry.enrichVideoPromptWithMotion(
                prompt = request.prompt,
                cameraMotion = request.cameraMotion
            )
            val payload = JSONObject().apply {
                put("model", request.modelId.ifBlank { "omniroute/video-combo" })
                put("prompt", enrichedPrompt)
                put("aspect_ratio", request.aspectRatio)
                put("resolution", request.resolution)
                put("duration", request.durationSeconds)
                put("camera_motion", request.cameraMotion)
                if (request.negativePrompt.isNotBlank()) {
                    put("negative_prompt", request.negativePrompt.trim())
                }
                if (!request.sourceImageDataUri.isNullOrBlank()) {
                    put("image_url", request.sourceImageDataUri)
                }
            }

            var lastNormError: NormalizedAiError? = null
            for (videoEndpoint in candidateUrls) {
                val reqBuilder = Request.Builder()
                    .url(videoEndpoint)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                if (request.apiKey.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${request.apiKey.trim()}")
                }

                val attemptResult = runCatching {
                    okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                        val rawBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val normError = ErrorNormalizer.normalizeHttpError(
                                statusCode = response.code,
                                responseBody = rawBody,
                                providerName = request.provider.displayName
                            )
                            lastNormError = normError
                            if (response.code == 404 && candidateUrls.size > 1) {
                                return@use null
                            }
                            throw ProviderNormalizedException(normError)
                        }

                        val root = JSONObject(rawBody)
                        val jobId = root.optString("id", root.optString("job_id", "")).ifBlank {
                            "job_${System.currentTimeMillis()}"
                        }
                        val directUrl = root.optString("video_url", root.optString("url", ""))
                            .ifBlank {
                                root.optJSONObject("output")?.optString("url", "").orEmpty()
                            }
                            .ifBlank {
                                root.optJSONArray("data")?.optJSONObject(0)?.optString("url", "").orEmpty()
                            }
                            .takeIf { it.isNotBlank() }
                        val status = if (directUrl != null) "COMPLETED" else "QUEUED"

                        VideoSubmissionResult(
                            remoteJobId = jobId,
                            status = status,
                            videoUrl = directUrl
                        )
                    }
                }
                val value = attemptResult.getOrElse { throw it }
                if (value != null) {
                    return@withContext Result.success(value)
                }
            }

            Result.failure(
                ProviderNormalizedException(
                    lastNormError ?: ErrorNormalizer.normalizeHttpError(
                        statusCode = 404,
                        responseBody = "Video generation endpoint not found on ${request.provider.baseUrl}",
                        providerName = request.provider.displayName
                    )
                )
            )
        } catch (e: ProviderNormalizedException) {
            Result.failure(e)
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
            ensureLocalOmniRouteListening(provider.baseUrl)
            val cleanBase = provider.baseUrl.trim().trimEnd('/')
            val pollUrl = if (remoteJobId.startsWith("http")) {
                remoteJobId
            } else {
                "$cleanBase/videos/generations/$remoteJobId"
            }

            val reqBuilder = Request.Builder()
                .url(pollUrl)
                .get()
                .header("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
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
                val statusRaw = root.optString("status", root.optString("state", "processing")).lowercase()
                val videoUrl = root.optString("video_url", root.optString("url", ""))
                    .ifBlank {
                        root.optJSONArray("data")?.optJSONObject(0)?.optString("url", "").orEmpty()
                    }
                    .takeIf { it.isNotBlank() }
                val progress = root.optInt("progress", 60).coerceIn(10, 99)

                when {
                    statusRaw in listOf("completed", "succeeded", "done") || videoUrl != null -> Result.success(
                        VideoPollResult(
                            status = "COMPLETED",
                            progressPercent = 100,
                            videoUrl = videoUrl
                        )
                    )
                    statusRaw in listOf("failed", "error", "canceled", "cancelled") -> Result.success(
                        VideoPollResult(
                            status = "FAILED",
                            progressPercent = 100,
                            errorMessage = root.optString("error", "Video generation failed on ${provider.displayName}")
                        )
                    )
                    else -> Result.success(
                        VideoPollResult(
                            status = "PROCESSING",
                            progressPercent = progress
                        )
                    )
                }
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

class ProviderNormalizedException(val normalizedError: NormalizedAiError) :
    Exception(normalizedError.message)
