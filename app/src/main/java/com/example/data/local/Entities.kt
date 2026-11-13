package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CapabilityTag(val label: String) {
    CHAT("Chat"),
    IMAGE("Image"),
    VIDEO("Video")
}

enum class ProviderPresetType(
    val displayName: String,
    val badgeShort: String,
    val defaultBaseUrl: String,
    val defaultCapabilities: Set<CapabilityTag>,
    val isOpenAiCompatible: Boolean,
    val phaseTag: String
) {
    OMNIROUTE(
        displayName = "OmniRoute Gateway",
        badgeShort = "OMNI",
        defaultBaseUrl = "http://localhost:20128/v1",
        defaultCapabilities = setOf(CapabilityTag.CHAT, CapabilityTag.IMAGE, CapabilityTag.VIDEO),
        isOpenAiCompatible = true,
        phaseTag = "Localhost Gateway"
    ),
    OPENAI(
        displayName = "OpenAI",
        badgeShort = "OAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultCapabilities = setOf(CapabilityTag.CHAT, CapabilityTag.IMAGE),
        isOpenAiCompatible = true,
        phaseTag = "Active"
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        badgeShort = "ANT",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultCapabilities = setOf(CapabilityTag.CHAT),
        isOpenAiCompatible = false,
        phaseTag = "Active (Dedicated)"
    ),
    GEMINI(
        displayName = "Google Gemini",
        badgeShort = "GEM",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultCapabilities = setOf(CapabilityTag.CHAT, CapabilityTag.IMAGE, CapabilityTag.VIDEO),
        isOpenAiCompatible = false,
        phaseTag = "Active (Dedicated)"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        badgeShort = "OR",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultCapabilities = setOf(CapabilityTag.CHAT, CapabilityTag.IMAGE),
        isOpenAiCompatible = true,
        phaseTag = "Active"
    ),
    GROQ(
        displayName = "Groq",
        badgeShort = "GROQ",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultCapabilities = setOf(CapabilityTag.CHAT),
        isOpenAiCompatible = true,
        phaseTag = "Active"
    ),
    TOGETHER(
        displayName = "Together AI",
        badgeShort = "TOG",
        defaultBaseUrl = "https://api.together.xyz/v1",
        defaultCapabilities = setOf(CapabilityTag.CHAT, CapabilityTag.IMAGE),
        isOpenAiCompatible = true,
        phaseTag = "Active"
    ),
    MISTRAL(
        displayName = "Mistral AI",
        badgeShort = "MIS",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        defaultCapabilities = setOf(CapabilityTag.CHAT),
        isOpenAiCompatible = true,
        phaseTag = "Active"
    ),
    CUSTOM(
        displayName = "Custom (OpenAI-Compatible)",
        badgeShort = "CUST",
        defaultBaseUrl = "https://api.example.com/v1",
        defaultCapabilities = setOf(CapabilityTag.CHAT),
        isOpenAiCompatible = true,
        phaseTag = "Active"
    ),
    REPLICATE(
        displayName = "Replicate",
        badgeShort = "REP",
        defaultBaseUrl = "https://api.replicate.com/v1",
        defaultCapabilities = setOf(CapabilityTag.IMAGE, CapabilityTag.VIDEO),
        isOpenAiCompatible = false,
        phaseTag = "Active (Dedicated)"
    ),
    STABILITY(
        displayName = "Stability AI",
        badgeShort = "STAB",
        defaultBaseUrl = "https://api.stability.ai/v2beta",
        defaultCapabilities = setOf(CapabilityTag.IMAGE, CapabilityTag.VIDEO),
        isOpenAiCompatible = false,
        phaseTag = "Active (Dedicated)"
    ),
    FAL_AI(
        displayName = "fal.ai",
        badgeShort = "FAL",
        defaultBaseUrl = "https://fal.run",
        defaultCapabilities = setOf(CapabilityTag.IMAGE, CapabilityTag.VIDEO),
        isOpenAiCompatible = false,
        phaseTag = "Active (Dedicated)"
    ),
    RUNWAY(
        displayName = "Runway",
        badgeShort = "RUN",
        defaultBaseUrl = "https://api.dev.runwayml.com/v1",
        defaultCapabilities = setOf(CapabilityTag.VIDEO),
        isOpenAiCompatible = false,
        phaseTag = "Active (Dedicated)"
    ),
    LUMA(
        displayName = "Luma Dream Machine",
        badgeShort = "LUMA",
        defaultBaseUrl = "https://api.lumalabs.ai/dream-machine/v1",
        defaultCapabilities = setOf(CapabilityTag.VIDEO),
        isOpenAiCompatible = false,
        phaseTag = "Active (Dedicated)"
    )
}

@Entity(tableName = "provider_instances")
data class ProviderInstanceEntity(
    @PrimaryKey val id: String,
    val presetType: String,
    val displayName: String,
    val baseUrl: String,
    val capabilitiesCsv: String,
    val isEnabled: Boolean = true,
    val isDefaultForChat: Boolean = false,
    val isDefaultForImage: Boolean = false,
    val isDefaultForVideo: Boolean = false,
    val defaultChatModelId: String = "",
    val defaultImageModelId: String = "",
    val defaultVideoModelId: String = "",
    val lastTestStatus: String = "Untested",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun capabilitySet(): Set<CapabilityTag> =
        capabilitiesCsv.split(",")
            .mapNotNull { raw -> runCatching { CapabilityTag.valueOf(raw.trim()) }.getOrNull() }
            .toSet()

    fun presetEnum(): ProviderPresetType =
        runCatching { ProviderPresetType.valueOf(presetType) }.getOrDefault(ProviderPresetType.CUSTOM)
}

@Entity(
    tableName = "model_descriptors",
    indices = [Index(value = ["providerInstanceId"])]
)
data class ModelDescriptorEntity(
    @PrimaryKey val id: String,
    val providerInstanceId: String,
    val modelId: String,
    val displayName: String,
    val capabilitiesCsv: String = "CHAT",
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val supportsSystemPrompt: Boolean = true,
    val supportsTemperature: Boolean = true,
    val supportsTopP: Boolean = true,
    val supportsFunctionCalling: Boolean = true,
    val isCombo: Boolean = false,
    val isFavorite: Boolean = false,
    val inputCostPer1k: Double = 0.0015,
    val outputCostPer1k: Double = 0.0060
) {
    val contextWindow: Int
        get() = com.example.data.provider.SmartContextTrimmer.resolveContextWindowTokens(modelId)

    fun capabilitySet(): Set<CapabilityTag> =
        capabilitiesCsv.split(",")
            .mapNotNull { raw -> runCatching { CapabilityTag.valueOf(raw.trim()) }.getOrNull() }
            .toSet()
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isPinned: Boolean = false,
    val providerInstanceId: String,
    val modelId: String,
    val systemPrompt: String = "You are a helpful, precise AI assistant in Aerodock.",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 1.0f,
    val enabledSkillIdsCsv: String? = null, // null = use global enabled skills; non-null = per-conversation skill IDs
    val autoSelectSkills: Boolean = false, // true = only inject skills matching user request keywords
    val mcpToolsEnabled: Boolean = true,
    val maxToolIterations: Int = 12,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["conversationId", "createdAt"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "USER" | "ASSISTANT" | "SYSTEM"
    val content: String,
    val providerInstanceId: String,
    val modelId: String,
    val attachmentUri: String? = null,
    val attachmentMimeType: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val isStreaming: Boolean = false,
    val errorText: String? = null,
    val comparisonGroupId: String? = null, // Groups 2-3 side-by-side Compare Mode replies
    val latencyMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatMediaAttachment(
    val uri: String,
    val mimeType: String = "image/jpeg",
    val isVideo: Boolean = false,
    val name: String? = null,
    val durationMs: Long = 0L,
    val isDocument: Boolean = false,
    val extractedText: String? = null,
    val sizeBytes: Long = 0L
) {
    val fileName: String
        get() = name ?: ""

    fun toSerializedString(): String {
        val typeFlag = when {
            isVideo -> "v"
            isDocument -> "d"
            else -> "i"
        }
        val safeName = (name ?: "").replace("|", "_").replace(";", "_")
        return if (!isDocument && extractedText.isNullOrBlank() && sizeBytes == 0L) {
            "$uri;$mimeType;$typeFlag;$safeName;$durationMs"
        } else {
            val encodedText = java.util.Base64.getEncoder().encodeToString(
                (extractedText ?: "").toByteArray(Charsets.UTF_8)
            )
            "$uri;$mimeType;$typeFlag;$safeName;$durationMs;$sizeBytes;$encodedText"
        }
    }

    companion object {
        fun fromSerializedString(str: String): ChatMediaAttachment {
            val parts = str.split(";")
            return if (parts.size >= 3) {
                val flag = parts.getOrNull(2).orEmpty()
                val decodedText = parts.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { b64 ->
                    runCatching {
                        String(java.util.Base64.getDecoder().decode(b64), Charsets.UTF_8)
                    }.getOrNull()
                }
                ChatMediaAttachment(
                    uri = parts[0],
                    mimeType = parts.getOrNull(1)?.ifBlank { "image/jpeg" } ?: "image/jpeg",
                    isVideo = flag == "v",
                    name = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                    durationMs = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
                    isDocument = flag == "d",
                    extractedText = decodedText,
                    sizeBytes = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                )
            } else {
                val isVid = str.contains("video", ignoreCase = true) ||
                    str.endsWith(".mp4", ignoreCase = true) ||
                    str.endsWith(".webm", ignoreCase = true) ||
                    str.endsWith(".mov", ignoreCase = true)
                ChatMediaAttachment(
                    uri = str,
                    mimeType = if (isVid) "video/mp4" else "image/jpeg",
                    isVideo = isVid
                )
            }
        }

        fun parseList(serializedOrDelimited: String?): List<ChatMediaAttachment> {
            if (serializedOrDelimited.isNullOrBlank()) return emptyList()
            return serializedOrDelimited.split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { fromSerializedString(it) }
        }

        fun serializeList(list: List<ChatMediaAttachment>): String {
            return list.joinToString("|") { it.toSerializedString() }
        }
    }
}

fun MessageEntity.getAttachments(): List<ChatMediaAttachment> {
    if (attachmentUri.isNullOrBlank()) return emptyList()
    return ChatMediaAttachment.parseList(attachmentUri)
}

@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String, // "Coding", "Architecture", "Writing", "Vision", "Custom"
    val promptText: String,
    val systemPromptOverride: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["providerInstanceId"])]
)
data class UsageRecordEntity(
    @PrimaryKey val id: String,
    val providerInstanceId: String,
    val providerDisplayName: String,
    val modelId: String,
    val modality: String = "CHAT", // "CHAT", "COMPARE", "IMAGE", "VIDEO"
    val promptTokens: Int,
    val completionTokens: Int,
    val estimatedCostUsd: Double,
    val latencyMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "generated_images",
    indices = [Index(value = ["providerInstanceId"])]
)
data class GeneratedImageEntity(
    @PrimaryKey val id: String,
    val providerInstanceId: String,
    val providerDisplayName: String,
    val modelId: String,
    val prompt: String,
    val negativePrompt: String = "",
    val aspectRatio: String = "1:1",
    val sizeLabel: String = "1024x1024",
    val stylePreset: String = "None",
    val seed: Long? = null,
    val guidanceScale: Float = 7.5f,
    val steps: Int = 28,
    val quality: String = "standard",
    val sourceImageUri: String? = null,
    val imageToImageStrength: Float = 0.75f,
    val isUpscaled: Boolean = false,
    val imageUri: String, // Local file:// path or remote/data URI
    val estimatedCostUsd: Double = 0.02,
    val latencyMs: Long = 0L,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_jobs",
    indices = [Index(value = ["providerInstanceId"]), Index(value = ["status"])]
)
data class VideoJobEntity(
    @PrimaryKey val id: String,
    val providerInstanceId: String,
    val providerDisplayName: String,
    val modelId: String,
    val prompt: String,
    val negativePrompt: String = "",
    val aspectRatio: String = "16:9",
    val resolution: String = "720p",
    val durationSeconds: Int = 5,
    val cameraMotion: String = "None",
    val sourceImageUri: String? = null,
    val remoteJobId: String = "",
    val status: String = "QUEUED", // "QUEUED", "PROCESSING", "COMPLETED", "FAILED", "CANCELED"
    val progressPercent: Int = 10,
    val videoUri: String? = null, // Local file path or playable remote MP4 URI
    val errorMessage: String? = null,
    val estimatedCostUsd: Double = 0.25,
    val pollAttempts: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "integration_audit_logs",
    indices = [Index(value = ["service"])]
)
data class IntegrationAuditLogEntity(
    @PrimaryKey val id: String,
    val service: String, // "GITHUB" | "YOUTUBE"
    val actionType: String, // "GIST" | "REPO_COMMIT" | "YOUTUBE_UPLOAD"
    val title: String,
    val targetDestination: String, // e.g. "gist:conv_export.md", "owner/repo:docs/chat.md", "YouTube (unlisted)"
    val externalUrl: String,
    val status: String = "SUCCESS", // "SUCCESS" | "FAILED"
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// --- Phase 6: Skills (SKILL.md) & Remote MCP Servers ---

enum class McpTransportType(val label: String) {
    STREAMABLE_HTTP("Streamable HTTP"),
    SSE("SSE (Fallback)"),
    WEBSOCKET("WebSocket (WS/WSS)"),
    GRPC("gRPC / HTTP2")
}

enum class McpApprovalMode(val label: String, val subtitle: String) {
    ASK_EVERY_TIME("Ask Every Time", "Prompt before every tool call (default)"),
    ASK_ONCE_PER_CONVERSATION("Ask Once / Thread", "Remember approval for read-only tools in this chat"),
    ALWAYS_ALLOW_READ_ONLY("Auto Read-Only", "Auto-run read-only tools; always ask for write/delete")
}

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val rawMarkdown: String,
    val bodyInstructions: String,
    val sourceUrl: String = "",
    val isEnabled: Boolean = true,
    val estimatedTokens: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val transport: String = McpTransportType.STREAMABLE_HTTP.name, // "STREAMABLE_HTTP" | "SSE"
    val authHeaderName: String = "Authorization",
    val approvalMode: String = McpApprovalMode.ASK_EVERY_TIME.name,
    val isEnabled: Boolean = true,
    val lastTestStatus: String = "Untested",
    val cachedResourceCount: Int = 0,
    val cachedPromptCount: Int = 0,
    val cachedResourcesSummary: String = "",
    val cachedPromptsSummary: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun transportEnum(): McpTransportType =
        runCatching { McpTransportType.valueOf(transport) }.getOrDefault(McpTransportType.STREAMABLE_HTTP)

    fun approvalModeEnum(): McpApprovalMode =
        runCatching { McpApprovalMode.valueOf(approvalMode) }.getOrDefault(McpApprovalMode.ASK_EVERY_TIME)
}

@Entity(
    tableName = "mcp_tools",
    indices = [Index(value = ["serverId"])]
)
data class McpToolEntity(
    @PrimaryKey val id: String, // "$serverId:$toolName"
    val serverId: String,
    val serverName: String,
    val toolName: String,
    val description: String,
    val inputSchemaJson: String = """{"type":"object","properties":{}}""",
    val isReadOnlyHint: Boolean = true,
    val isEnabled: Boolean = true
)

@Entity(
    tableName = "tool_call_logs",
    indices = [Index(value = ["conversationId"]), Index(value = ["messageId"]), Index(value = ["serverId"])]
)
data class ToolCallLogEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageId: String,
    val serverId: String,
    val serverName: String,
    val toolName: String,
    val argumentsJson: String,
    val resultText: String,
    val isSuccess: Boolean,
    val isDestructiveOrWrite: Boolean = false,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)


