package com.example.data.provider

import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dedicated adapter for Replicate (/v1/models/{owner}/{name}/predictions with Prefer: wait=60 and status polling).
 * Supports Flux 1.1 Pro, Flux Schnell, Flux Dev, SDXL, and Real-ESRGAN 2x Upscaling.
 */
class ReplicateProvider(
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
        val curated = listOf(
            ModelCapabilityRegistry.buildDescriptor(
                providerInstanceId = provider.id,
                rawModelId = "black-forest-labs/flux-1.1-pro",
                displayNameOverride = "Flux 1.1 Pro (Replicate)",
                preserveFavorite = true
            ),
            ModelCapabilityRegistry.buildDescriptor(
                providerInstanceId = provider.id,
                rawModelId = "black-forest-labs/flux-schnell",
                displayNameOverride = "Flux Schnell (Fast)"
            ),
            ModelCapabilityRegistry.buildDescriptor(
                providerInstanceId = provider.id,
                rawModelId = "black-forest-labs/flux-dev",
                displayNameOverride = "Flux Dev (Img2Img)"
            ),
            ModelCapabilityRegistry.buildDescriptor(
                providerInstanceId = provider.id,
                rawModelId = "stability-ai/sdxl",
                displayNameOverride = "Stable Diffusion XL (Replicate)"
            ),
            ModelCapabilityRegistry.buildDescriptor(
                providerInstanceId = provider.id,
                rawModelId = "recraft-ai/recraft-v3",
                displayNameOverride = "Recraft V3"
            )
        )
        Result.success(curated)
    }

    override fun streamChat(request: ChatCompletionRequest): Flow<ChatStreamEvent> = flow {
        emit(
            ChatStreamEvent.Error(
                NormalizedAiError(
                    category = ErrorCategory.SERVER_ERROR,
                    title = "Image/Video Provider",
                    message = "Replicate is configured for Image and Video generation in Aerodock.",
                    suggestedFix = "Open the Images tab to generate with Replicate Flux/SDXL."
                )
            )
        )
    }

    override suspend fun generateImage(
        request: ImageGenerationRequest
    ): Result<ImageGenerationOutput> = withContext(Dispatchers.IO) {
        try {
            val base = request.provider.baseUrl.trim().trimEnd('/')
            val targetModel = if (request.isUpscaleRequest && !request.sourceImageDataUri.isNullOrBlank()) {
                "nightmareai/real-esrgan"
            } else {
                request.modelId.ifBlank { "black-forest-labs/flux-schnell" }
            }

            val endpoint = if (targetModel.contains("/")) {
                "$base/models/$targetModel/predictions"
            } else {
                "$base/predictions"
            }

            val payload = buildReplicateInputPayload(request, targetModel)
            val httpReqBuilder = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Prefer", "wait=60")

            if (request.apiKey.isNotBlank()) {
                httpReqBuilder.header("Authorization", "Bearer ${request.apiKey.trim()}")
            }

            var predictionJson: JSONObject
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
                predictionJson = JSONObject(rawBody)
            }

            // Poll if status is starting or processing
            var status = predictionJson.optString("status", "")
            val pollUrl = predictionJson.optJSONObject("urls")?.optString("get", "").orEmpty()
            var pollAttempt = 0
            while (status in listOf("starting", "processing") && pollUrl.isNotBlank() && pollAttempt < 25) {
                delay(1500)
                pollAttempt++
                val pollReq = Request.Builder()
                    .url(pollUrl)
                    .get()
                    .header("Accept", "application/json")
                    .apply {
                        if (request.apiKey.isNotBlank()) {
                            header("Authorization", "Bearer ${request.apiKey.trim()}")
                        }
                    }
                    .build()
                okHttpClient.newCall(pollReq).execute().use { pollResp ->
                    val pollBody = pollResp.body?.string().orEmpty()
                    if (pollResp.isSuccessful && pollBody.isNotBlank()) {
                        predictionJson = JSONObject(pollBody)
                        status = predictionJson.optString("status", "")
                    }
                }
            }

            if (status == "failed" || status == "canceled") {
                val errDetail = predictionJson.optString("error", "Replicate prediction $status")
                return@withContext Result.failure(IllegalStateException(errDetail))
            }

            val outputs = extractReplicateOutputs(predictionJson)
            if (outputs.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Replicate prediction completed without output URLs.")
                )
            }

            Result.success(
                ImageGenerationOutput(
                    imagePayloads = outputs,
                    seedUsed = request.seed
                )
            )
        } catch (e: Exception) {
            val normError = ErrorNormalizer.normalizeException(
                throwable = e,
                providerName = request.provider.displayName,
                baseUrl = request.provider.baseUrl
            )
            Result.failure(ProviderNormalizedException(normError))
        }
    }

    internal fun buildReplicateInputPayload(
        request: ImageGenerationRequest,
        targetModel: String
    ): JSONObject {
        val inputObj = JSONObject()
        if (request.isUpscaleRequest && !request.sourceImageDataUri.isNullOrBlank()) {
            inputObj.put("image", request.sourceImageDataUri)
            inputObj.put("scale", 2)
        } else {
            val enrichedPrompt = ModelCapabilityRegistry.enrichPromptWithStyle(request.prompt, request.stylePreset)
            inputObj.put("prompt", enrichedPrompt)
            inputObj.put("aspect_ratio", request.aspectRatio.ifBlank { "1:1" })
            inputObj.put("num_outputs", request.count.coerceIn(1, 4))
            if (request.negativePrompt.isNotBlank() && !targetModel.contains("flux")) {
                inputObj.put("negative_prompt", request.negativePrompt.trim())
            }
            if (request.seed != null) {
                inputObj.put("seed", request.seed)
            }
            if (!targetModel.contains("schnell")) {
                inputObj.put("guidance", request.guidanceScale.toDouble())
                inputObj.put("num_inference_steps", request.steps.coerceIn(1, 50))
            }
            if (!request.sourceImageDataUri.isNullOrBlank()) {
                inputObj.put("image", request.sourceImageDataUri)
                inputObj.put("prompt_strength", request.imageToImageStrength.toDouble())
            }
        }
        return JSONObject().put("input", inputObj)
    }

    internal fun extractReplicateOutputs(predictionJson: JSONObject): List<String> {
        val out = predictionJson.opt("output") ?: return emptyList()
        val urls = mutableListOf<String>()
        when (out) {
            is String -> if (out.isNotBlank()) urls.add(out)
            is JSONArray -> {
                for (i in 0 until out.length()) {
                    val item = out.optString(i, "")
                    if (item.isNotBlank()) urls.add(item)
                }
            }
        }
        return urls
    }

    override suspend fun submitVideoGeneration(
        request: VideoGenerationRequest
    ): Result<VideoSubmissionResult> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = request.provider.baseUrl.trim().trimEnd('/')
            val targetModel = request.modelId.trim().ifBlank { "wan-video/wan-2.1-t2v-480p" }
            val endpoint = if (targetModel.contains("/")) {
                val parts = targetModel.split("/", limit = 2)
                "$cleanBase/models/${parts[0]}/${parts[1]}/predictions"
            } else {
                "$cleanBase/predictions"
            }

            val enrichedPrompt = ModelCapabilityRegistry.enrichVideoPromptWithMotion(
                prompt = request.prompt,
                cameraMotion = request.cameraMotion
            )
            val inputObj = JSONObject().apply {
                put("prompt", enrichedPrompt)
                put("aspect_ratio", request.aspectRatio)
                if (request.negativePrompt.isNotBlank()) {
                    put("negative_prompt", request.negativePrompt.trim())
                }
                if (!request.sourceImageDataUri.isNullOrBlank()) {
                    put("image", request.sourceImageDataUri)
                }
            }
            val bodyJson = JSONObject().put("input", inputObj)

            val httpReqBuilder = Request.Builder()
                .url(endpoint)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
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
                val root = JSONObject(rawBody)
                val pollUrl = root.optJSONObject("urls")?.optString("get", "").orEmpty()
                    .ifBlank { root.optString("id", "") }
                val outputs = extractReplicateOutputs(root)
                Result.success(
                    VideoSubmissionResult(
                        remoteJobId = pollUrl,
                        status = if (outputs.isNotEmpty()) "COMPLETED" else "QUEUED",
                        videoUrl = outputs.firstOrNull()
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
                "$cleanBase/predictions/$remoteJobId"
            }

            val req = Request.Builder()
                .url(pollUrl)
                .get()
                .header("Accept", "application/json")
                .apply {
                    if (apiKey.isNotBlank()) {
                        header("Authorization", "Bearer ${apiKey.trim()}")
                    }
                }
                .build()

            okHttpClient.newCall(req).execute().use { response ->
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
                val status = root.optString("status", "processing").lowercase()
                val outputs = extractReplicateOutputs(root)
                when (status) {
                    "succeeded" -> Result.success(
                        VideoPollResult(
                            status = "COMPLETED",
                            progressPercent = 100,
                            videoUrl = outputs.firstOrNull()
                        )
                    )
                    "failed", "canceled" -> Result.success(
                        VideoPollResult(
                            status = "FAILED",
                            progressPercent = 100,
                            errorMessage = root.optString("error", "Replicate video prediction $status")
                        )
                    )
                    "starting" -> Result.success(
                        VideoPollResult(status = "QUEUED", progressPercent = 25)
                    )
                    else -> Result.success(
                        VideoPollResult(status = "PROCESSING", progressPercent = 65)
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
