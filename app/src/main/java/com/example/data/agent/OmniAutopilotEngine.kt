package com.example.data.agent

import com.example.data.local.CapabilityTag
import com.example.data.local.ConversationEntity
import com.example.data.local.McpApprovalMode
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.OmniDao
import com.example.data.local.PromptTemplateEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.ProviderPresetType
import com.example.data.local.SkillEntity
import com.example.data.provider.ChatCompletionRequest
import com.example.data.provider.ChatMessagePayload
import com.example.data.provider.EmbeddedOmniRouteServer
import com.example.data.repository.OmniRepository
import com.example.data.security.TinkSecretVault
import com.example.ui.viewmodel.OmniStudioViewModel
import com.example.ui.viewmodel.ProvidersViewModel
import com.example.ui.viewmodel.WorkspaceTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Structured representation of an atomic real-time action that the integrated
 * Omni Autopilot Agent can execute inside the application.
 */
sealed class OmniAgentAction {
    data class NavigateTab(val tab: WorkspaceTab) : OmniAgentAction()
    data class SetCompareMode(val enabled: Boolean) : OmniAgentAction()
    data class OpenModalSheet(val sheetName: String) : OmniAgentAction()
    data class SetThemeMode(val mode: String, val dynamicColor: Boolean? = null) : OmniAgentAction()
    data class SetSpendCurrency(val currency: String) : OmniAgentAction()
    data class SetEnterToSend(val enabled: Boolean) : OmniAgentAction()
    data class SetBiometricLock(val enabled: Boolean) : OmniAgentAction()
    data class SwitchActiveModel(val modelQuery: String, val providerQuery: String? = null) : OmniAgentAction()
    data class TuneChatParameters(
        val temperature: Float? = null,
        val topP: Float? = null,
        val maxTokens: Int? = null,
        val systemPrompt: String? = null
    ) : OmniAgentAction()
    data class CreateConversation(
        val title: String,
        val systemPrompt: String? = null,
        val initialPrompt: String? = null
    ) : OmniAgentAction()
    data class SendChatMessage(val prompt: String) : OmniAgentAction()
    data class SaveProviderApiKey(val providerQuery: String, val rawApiKey: String) : OmniAgentAction()
    data class ClearProviderApiKey(val providerQuery: String) : OmniAgentAction()
    data class AddOrUpdateProvider(
        val presetName: String,
        val displayName: String,
        val baseUrl: String? = null,
        val rawApiKey: String? = null
    ) : OmniAgentAction()
    data class ToggleProviderEnabled(val providerQuery: String, val enabled: Boolean? = null) : OmniAgentAction()
    data class SetDefaultProvider(val providerQuery: String, val capability: CapabilityTag) : OmniAgentAction()
    data class AddManualModel(val providerQuery: String, val modelId: String, val isCombo: Boolean = false) : OmniAgentAction()
    data class GenerateImageAction(
        val prompt: String,
        val aspectRatio: String = "1:1",
        val stylePreset: String = "Cinematic",
        val autoExecute: Boolean = true
    ) : OmniAgentAction()
    data class GenerateVideoAction(
        val prompt: String,
        val aspectRatio: String = "16:9",
        val resolution: String = "720p",
        val durationSeconds: Int = 5,
        val autoExecute: Boolean = true
    ) : OmniAgentAction()
    data class CreateSkillAction(
        val name: String,
        val description: String,
        val instructions: String
    ) : OmniAgentAction()
    data class CreatePromptTemplateAction(
        val title: String,
        val category: String,
        val content: String,
        val applyImmediately: Boolean = true
    ) : OmniAgentAction()
    data class AddMcpServerAction(
        val name: String,
        val endpointUrl: String,
        val approvalMode: String = "AUTO_APPROVE_READ_ONLY"
    ) : OmniAgentAction()
    data class ControlLocalhostServer(val start: Boolean) : OmniAgentAction()
    data class RunHealthAndLatencyScan(val runLatencyBenchmark: Boolean = true) : OmniAgentAction()
    data class ClearDataAction(val target: String) : OmniAgentAction()
}

data class OmniAgentStepResult(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val title: String,
    val detail: String,
    val isSuccess: Boolean = true,
    val durationMs: Long = 1L,
    val timestampMs: Long = System.currentTimeMillis()
)

data class OmniAgentExecutionRecord(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val steps: List<OmniAgentStepResult>,
    val agentSummary: String,
    val usedLlmPlanner: Boolean = false,
    val totalDurationMs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Captures reversible state before an agent command executes so the user can undo in real time.
 */
data class OmniAgentUndoSnapshot(
    val command: String,
    val previousTab: WorkspaceTab,
    val previousCompareMode: Boolean,
    val previousThemeMode: String,
    val previousDynamicColor: Boolean,
    val previousCurrency: String,
    val previousEnterToSend: Boolean,
    val previousActiveConvId: String?,
    val previousProviderId: String?,
    val previousModelId: String?,
    val previousTemperature: Float?,
    val previousTopP: Float?,
    val previousMaxTokens: Int?,
    val previousSystemPrompt: String?
)

data class OmniAgentMacroPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val command: String
)

data class OmniAgentUiState(
    val isSheetOpen: Boolean = false,
    val isExecuting: Boolean = false,
    val liveStatusBanner: String? = null,
    val commandInput: String = "",
    val useAiLlmPlanner: Boolean = false,
    val liveStepsInProgress: List<OmniAgentStepResult> = emptyList(),
    val executionHistory: List<OmniAgentExecutionRecord> = emptyList(),
    val lastUndoSnapshot: OmniAgentUndoSnapshot? = null,
    val macroPresets: List<OmniAgentMacroPreset> = defaultMacroPresets()
) {
    companion object {
        fun defaultMacroPresets(): List<OmniAgentMacroPreset> = listOf(
            OmniAgentMacroPreset(
                id = "macro_coding_workspace",
                title = "Architect Coding Mode",
                subtitle = "Dark theme • Temp 0.15 • MaxTokens 8192 • System Prompt • New Chat",
                badge = "5 ACTIONS",
                command = "Switch to dark theme, set temperature to 0.15, set max tokens to 8192, set system prompt to You are a Principal Kotlin & Systems Architect. Write clean production code., and create a new conversation titled Architecture Session"
            ),
            OmniAgentMacroPreset(
                id = "macro_multi_model_arena",
                title = "Launch Compare Arena",
                subtitle = "Enable Compare Mode • Temp 0.6 • Navigate to Compare Studio",
                badge = "3 ACTIONS",
                command = "Enable compare mode, set temperature to 0.6, and switch to compare tab"
            ),
            OmniAgentMacroPreset(
                id = "macro_cyberpunk_image",
                title = "Image Studio Auto-Setup",
                subtitle = "Switch to Image Studio • 16:9 Aspect • Cyberpunk Style • Draft Prompt",
                badge = "IMAGE STUDIO",
                command = "Go to image studio and prepare image prompt A futuristic neon-lit Tokyo skybridge in rain, ultra detailed 8k with aspect ratio 16:9 and style Cyberpunk"
            ),
            OmniAgentMacroPreset(
                id = "macro_cinematic_video",
                title = "Video Studio 1080p Setup",
                subtitle = "Switch to Video Studio • 16:9 • 1080p • 5s Drone Orbit Prompt",
                badge = "VIDEO STUDIO",
                command = "Go to video studio and prepare video prompt Aerial drone orbit around a bioluminescent waterfall at twilight with resolution 1080p and duration 5s"
            ),
            OmniAgentMacroPreset(
                id = "macro_health_benchmark",
                title = "Full System Health & Speed Audit",
                subtitle = "Start Localhost OmniRoute • Scan Provider Health • Run Latency Probe • Usage Tab",
                badge = "DIAGNOSTICS",
                command = "Start localhost server, scan provider health, run latency benchmark, and go to usage tab"
            ),
            OmniAgentMacroPreset(
                id = "macro_security_skill",
                title = "Install Security Auditor Skill",
                subtitle = "Create OWASP Skill in Room DB • Apply to Chat • Switch to Integrations",
                badge = "SKILL + DB",
                command = "Create skill named OWASP Security Auditor with instructions Always audit code for injection, auth bypass, hardcoded secrets, and cryptographic flaws. and go to integrations tab"
            )
        )
    }
}

/**
 * Integrated App-Wide AI Agent Engine ("Omni Autopilot").
 * Capable of decomposing a single natural-language command (containing one or many instructions)
 * into atomic real-time state mutations across Chat, Compare, Image Studio, Video Studio,
 * Providers & Tink AES256-GCM Vault, Skills, MCP Servers, and App Settings.
 */
class OmniAutopilotEngine(
    private val repository: OmniRepository,
    private val dao: OmniDao,
    private val secretVault: TinkSecretVault
) {
    private val _state = MutableStateFlow(OmniAgentUiState())
    val state: StateFlow<OmniAgentUiState> = _state.asStateFlow()

    fun setSheetOpen(open: Boolean) {
        _state.update { it.copy(isSheetOpen = open) }
    }

    fun updateCommandInput(text: String) {
        _state.update { it.copy(commandInput = text) }
    }

    fun setUseAiLlmPlanner(enabled: Boolean) {
        _state.update { it.copy(useAiLlmPlanner = enabled) }
    }

    fun dismissLiveBanner() {
        _state.update { it.copy(liveStatusBanner = null) }
    }

    fun clearExecutionHistory() {
        _state.update { it.copy(executionHistory = emptyList()) }
    }

    /**
     * Parses a natural language command (single or compound multi-step) into a list of
     * executable [OmniAgentAction] items. Deterministic and zero-latency.
     */
    fun parseNaturalLanguageCommand(rawCommand: String): List<OmniAgentAction> {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Split compound commands separated by ";", " and then ", ", then ", " then ", ", and ", " and "
        // while keeping clauses that belong to prompt content intact.
        val clauses = splitCompoundCommand(trimmed)
        val actions = mutableListOf<OmniAgentAction>()

        for (clause in clauses) {
            val parsedFromClause = parseSingleClause(clause.trim())
            actions.addAll(parsedFromClause)
        }

        // If splitting didn't yield specific actions, try parsing the whole string as a single rich command
        if (actions.isEmpty()) {
            actions.addAll(parseSingleClause(trimmed))
        }

        return actions.distinct()
    }

    private fun splitCompoundCommand(input: String): List<String> {
        // Protect quoted strings during splitting
        val delimiterRegex = Regex(
            pattern = """(?:\s*;\s*|\s+and\s+then\s+|\s*,\s+then\s+|\s+then\s+|\s*,\s+and\s+|\s+and\s+(?=(?:switch|go|open|set|change|enable|disable|turn|create|new|add|save|clear|delete|remove|update|configure|prepare|generate|run|scan|start|stop|use|select|apply|install|toggle|send|ask)\b)|\s*,\s+(?=(?:switch|go|open|set|change|enable|disable|turn|create|new|add|save|clear|delete|remove|update|configure|prepare|generate|run|scan|start|stop|use|select|apply|install|toggle|send|ask)\b))""",
            option = RegexOption.IGNORE_CASE
        )
        return input.split(delimiterRegex).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseSingleClause(clause: String): List<OmniAgentAction> {
        val lower = clause.lowercase(Locale.US).trim()
        val result = mutableListOf<OmniAgentAction>()

        // 1. Theme & Dynamic Color
        if (lower.contains("dark mode") || lower.contains("dark theme") || lower == "dark") {
            val dyn = if (lower.contains("dynamic color on") || lower.contains("enable dynamic color")) true
            else if (lower.contains("dynamic color off") || lower.contains("disable dynamic color")) false
            else null
            result.add(OmniAgentAction.SetThemeMode("DARK", dyn))
        } else if (lower.contains("light mode") || lower.contains("light theme") || lower == "light") {
            val dyn = if (lower.contains("dynamic color on") || lower.contains("enable dynamic color")) true
            else if (lower.contains("dynamic color off") || lower.contains("disable dynamic color")) false
            else null
            result.add(OmniAgentAction.SetThemeMode("LIGHT", dyn))
        } else if (lower.contains("system theme") || lower.contains("system mode")) {
            result.add(OmniAgentAction.SetThemeMode("SYSTEM", null))
        } else if (lower.contains("dynamic color")) {
            val enable = !lower.contains("off") && !lower.contains("disable")
            result.add(OmniAgentAction.SetThemeMode("", enable))
        }

        // 2. Currency (INR / USD)
        if (lower.contains("currency") || lower.contains("switch to usd") || lower.contains("switch to inr") ||
            lower.contains("spend in usd") || lower.contains("spend in inr")
        ) {
            if (lower.contains("usd") || lower.contains("dollar") || lower.contains("$")) {
                result.add(OmniAgentAction.SetSpendCurrency("USD"))
            } else if (lower.contains("inr") || lower.contains("rupee") || lower.contains("₹")) {
                result.add(OmniAgentAction.SetSpendCurrency("INR"))
            }
        }

        // 3. Enter-to-send & Biometric Lock
        if (lower.contains("enter to send") || lower.contains("enter-to-send")) {
            val enable = !lower.contains("disable") && !lower.contains("off") && !lower.contains("false")
            result.add(OmniAgentAction.SetEnterToSend(enable))
        }
        if (lower.contains("biometric")) {
            val enable = !lower.contains("disable") && !lower.contains("off") && !lower.contains("unlock")
            result.add(OmniAgentAction.SetBiometricLock(enable))
        }

        // 4. Compare Mode
        if (lower.contains("compare mode") || lower.contains("side-by-side") || lower.contains("side by side")) {
            val enable = !lower.contains("disable") && !lower.contains("off") && !lower.contains("exit")
            result.add(OmniAgentAction.SetCompareMode(enable))
        }

        // 5. Parameter Tuning (Temperature, Top-P, Max Tokens, System Prompt)
        var tempVal: Float? = null
        var topPVal: Float? = null
        var maxTokVal: Int? = null
        var sysPromptVal: String? = null

        val tempMatch = Regex("""(?:temperature|temp)\s*(?:to|=|:|\s)\s*([0-2](?:\.\d+)?|\.\d+)""", RegexOption.IGNORE_CASE).find(clause)
        if (tempMatch != null) {
            tempVal = tempMatch.groupValues[1].toFloatOrNull()?.coerceIn(0f, 2f)
        }
        val topPMatch = Regex("""(?:top[_\-\s]?p)\s*(?:to|=|:|\s)\s*(0?\.\d+|1(?:\.0)?)""", RegexOption.IGNORE_CASE).find(clause)
        if (topPMatch != null) {
            topPVal = topPMatch.groupValues[1].toFloatOrNull()?.coerceIn(0.05f, 1f)
        }
        val maxTokMatch = Regex("""(?:max[_\-\s]?tokens?|token\s*limit)\s*(?:to|=|:|\s)\s*(\d{2,6})""", RegexOption.IGNORE_CASE).find(clause)
        if (maxTokMatch != null) {
            maxTokVal = maxTokMatch.groupValues[1].toIntOrNull()?.coerceIn(64, 65536)
        }
        val sysPromptMatch = Regex("""(?:system\s+prompt|persona|system\s+instruction)\s*(?:to|=|:)\s*["']?(.+?)["']?$""", RegexOption.IGNORE_CASE).find(clause)
        if (sysPromptMatch != null) {
            sysPromptVal = sysPromptMatch.groupValues[1].trim()
        }

        if (tempVal != null || topPVal != null || maxTokVal != null || sysPromptVal != null) {
            result.add(
                OmniAgentAction.TuneChatParameters(
                    temperature = tempVal,
                    topP = topPVal,
                    maxTokens = maxTokVal,
                    systemPrompt = sysPromptVal
                )
            )
        }

        // 6. Save / Clear API Key in Tink Encrypted DataStore Vault
        val saveKeyMatch = Regex(
            """(?:save|set|update|store|encrypt|add)\s+(?:api\s+)?key\s+(?:for\s+)?([a-zA-Z0-9_\-.]+)\s*(?:to|as|=|:|\s)\s*([^\s"']+)""",
            RegexOption.IGNORE_CASE
        ).find(clause) ?: Regex(
            """(?:save|set|update|store|encrypt)\s+([a-zA-Z0-9_\-.]+)\s+(?:api\s+)?key\s*(?:to|as|=|:|\s)\s*([^\s"']+)""",
            RegexOption.IGNORE_CASE
        ).find(clause)

        if (saveKeyMatch != null) {
            val provName = saveKeyMatch.groupValues[1].trim()
            val rawKey = saveKeyMatch.groupValues[2].trim()
            if (rawKey.isNotBlank() && !provName.equals("api", ignoreCase = true)) {
                result.add(OmniAgentAction.SaveProviderApiKey(provName, rawKey))
            }
        }

        val clearKeyMatch = Regex(
            """(?:clear|delete|remove|wipe)\s+(?:api\s+)?key\s+(?:for\s+)?([a-zA-Z0-9_\-.]+)""",
            RegexOption.IGNORE_CASE
        ).find(clause) ?: Regex(
            """(?:clear|delete|remove|wipe)\s+([a-zA-Z0-9_\-.]+)\s+(?:api\s+)?key""",
            RegexOption.IGNORE_CASE
        ).find(clause)

        if (clearKeyMatch != null && saveKeyMatch == null) {
            val provName = clearKeyMatch.groupValues[1].trim()
            result.add(OmniAgentAction.ClearProviderApiKey(provName))
        }

        // 7. Add / Configure Provider Instance
        val addProviderMatch = Regex(
            """(?:add|create|register)\s+(?:a\s+)?(?:new\s+)?([a-zA-Z0-9_.\-]+)\s+provider(?:\s+named\s+["']?([^"']+?)["']?)?(?:\s+with\s+(?:url|base\s*url)\s+(https?://[^\s]+))?(?:\s+(?:with\s+)?key\s+([^\s]+))?""",
            RegexOption.IGNORE_CASE
        ).find(clause)
        if (addProviderMatch != null) {
            val presetRaw = addProviderMatch.groupValues[1].trim()
            val customName = addProviderMatch.groupValues[2].trim().ifBlank { "$presetRaw Provider" }
            val url = addProviderMatch.groupValues[3].trim().takeIf { it.isNotBlank() }
            val key = addProviderMatch.groupValues[4].trim().takeIf { it.isNotBlank() }
            result.add(OmniAgentAction.AddOrUpdateProvider(presetRaw, customName, url, key))
        }

        // 8. Enable / Disable Provider
        val toggleProvMatch = Regex(
            """(enable|disable)\s+provider\s+([a-zA-Z0-9_\-\s]+)""",
            RegexOption.IGNORE_CASE
        ).find(clause)
        if (toggleProvMatch != null) {
            val enable = toggleProvMatch.groupValues[1].lowercase(Locale.US) == "enable"
            val provQuery = toggleProvMatch.groupValues[2].trim()
            result.add(OmniAgentAction.ToggleProviderEnabled(provQuery, enable))
        }

        // 9. Switch Active Model / Provider
        val switchModelMatch = Regex(
            """(?:switch|change|set|use|select)\s+(?:active\s+)?(?:chat\s+)?model\s+(?:to\s+)?([a-zA-Z0-9_.\-/:\s]+?)(?:\s+on\s+([a-zA-Z0-9_.\-]+))?$""",
            RegexOption.IGNORE_CASE
        ).find(clause) ?: Regex(
            """(?:switch\s+to|use)\s+(gpt-[a-zA-Z0-9.\-]+|claude-[a-zA-Z0-9.\-]+|gemini-[a-zA-Z0-9.\-]+|deepseek[a-zA-Z0-9.\-/]+|llama[a-zA-Z0-9.\-/]+|omniroute/[a-zA-Z0-9.\-]+|ddgw/[a-zA-Z0-9.\-]+)""",
            RegexOption.IGNORE_CASE
        ).find(clause)

        if (switchModelMatch != null) {
            val modelQuery = switchModelMatch.groupValues[1].trim()
            val provQuery = switchModelMatch.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            result.add(OmniAgentAction.SwitchActiveModel(modelQuery, provQuery))
        }

        // 10. Add Manual Model
        val addModelMatch = Regex(
            """(?:add|register)\s+model\s+([^\s]+)\s+(?:to|on|for)\s+([a-zA-Z0-9_\-.]+)""",
            RegexOption.IGNORE_CASE
        ).find(clause)
        if (addModelMatch != null) {
            val modelId = addModelMatch.groupValues[1].trim()
            val provQuery = addModelMatch.groupValues[2].trim()
            result.add(OmniAgentAction.AddManualModel(provQuery, modelId, modelId.contains("combo")))
        }

        // 11. Image Studio Automation
        if (lower.contains("image") && (lower.contains("generate") || lower.contains("create") || lower.contains("prepare") || lower.contains("draw") || lower.contains("render"))) {
            val ratio = when {
                lower.contains("16:9") || lower.contains("landscape") || lower.contains("widescreen") -> "16:9"
                lower.contains("9:16") || lower.contains("portrait") || lower.contains("vertical") -> "9:16"
                else -> "1:1"
            }
            val style = when {
                lower.contains("cyberpunk") -> "Cyberpunk"
                lower.contains("anime") -> "Anime"
                lower.contains("photorealistic") || lower.contains("photo") -> "Photorealistic"
                lower.contains("3d") -> "3D Render"
                lower.contains("cinematic") -> "Cinematic"
                else -> "Cinematic"
            }
            val autoExec = !lower.contains("prepare") && !lower.contains("draft")
            val promptClean = clause
                .replace(Regex("""^(?:go\s+to\s+image\s+studio\s+and\s+)?(?:please\s+)?(?:generate|create|prepare|draw|render)\s+(?:an?\s+)?(?:image\s+(?:of|prompt)\s+|image\s+)?""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+with\s+aspect\s+ratio\s+(?:16:9|9:16|1:1)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+(?:and\s+)?style\s+(?:Cyberpunk|Anime|Photorealistic|Cinematic|3D Render)""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { "Futuristic AI command center overlooking a neon metropolis, 8k cinematic" }
            result.add(OmniAgentAction.GenerateImageAction(promptClean, ratio, style, autoExec))
        }

        // 12. Video Studio Automation
        if (lower.contains("video") && (lower.contains("generate") || lower.contains("create") || lower.contains("prepare") || lower.contains("render"))) {
            val ratio = if (lower.contains("9:16") || lower.contains("vertical")) "9:16" else "16:9"
            val res = if (lower.contains("1080p")) "1080p" else "720p"
            val dur = if (lower.contains("10s") || lower.contains("10 sec")) 10 else 5
            val autoExec = !lower.contains("prepare") && !lower.contains("draft")
            val promptClean = clause
                .replace(Regex("""^(?:go\s+to\s+video\s+studio\s+and\s+)?(?:please\s+)?(?:generate|create|prepare|render)\s+(?:a\s+)?(?:video\s+(?:of|prompt)\s+|video\s+)?""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+with\s+resolution\s+(?:720p|1080p)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+(?:and\s+)?duration\s+(?:5s|10s|5\s*sec|10\s*sec)""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { "Aerial drone hyperlapse over a glowing futuristic harbor at sunset" }
            result.add(OmniAgentAction.GenerateVideoAction(promptClean, ratio, res, dur, autoExec))
        }

        // 13. Create Skill in Room DB
        val skillMatch = Regex(
            """(?:create|add|install)\s+skill\s+(?:named|called|titled)\s+(.+?)\s+with\s+instructions\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(clause)
        if (skillMatch != null) {
            val name = skillMatch.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
            val instructions = skillMatch.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
            result.add(
                OmniAgentAction.CreateSkillAction(
                    name = name,
                    description = "Created live by Aerodock Autopilot",
                    instructions = instructions
                )
            )
        }

        // 14. Create Prompt Template
        val templateMatch = Regex(
            """(?:create|save|add)\s+prompt\s+template\s+(?:named|titled)\s+(.+?)\s+with\s+content\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(clause)
        if (templateMatch != null) {
            val title = templateMatch.groupValues[1].trim().removeSurrounding("\"")
            val content = templateMatch.groupValues[2].trim().removeSurrounding("\"")
            result.add(OmniAgentAction.CreatePromptTemplateAction(title, "Agent Presets", content, true))
        }

        // 15. Add MCP Server
        val mcpMatch = Regex(
            """(?:add|register|connect)\s+mcp\s+server\s+(.+?)\s+(?:at|url)\s+(https?://[^\s]+)""",
            RegexOption.IGNORE_CASE
        ).find(clause)
        if (mcpMatch != null) {
            val srvName = mcpMatch.groupValues[1].trim().removeSurrounding("\"")
            val srvUrl = mcpMatch.groupValues[2].trim()
            result.add(OmniAgentAction.AddMcpServerAction(srvName, srvUrl))
        }

        // 16. Create New Conversation
        if (lower.contains("new conversation") || lower.contains("new chat") || lower.startsWith("create conversation") || lower.startsWith("create a new conversation")) {
            val titleMatch = Regex("""(?:titled|named|called|about)\s+["']?([^"']+?)["']?$""", RegexOption.IGNORE_CASE).find(clause)
            val title = titleMatch?.groupValues?.get(1)?.trim() ?: "Agent Session"
            result.add(OmniAgentAction.CreateConversation(title = title))
        }

        // 17. Send Chat Message
        val sendChatMatch = Regex("""^(?:send\s+message|ask\s+chat|send\s+prompt|chat)\s*[:\-]?\s+(.+)$""", RegexOption.IGNORE_CASE).find(clause)
        if (sendChatMatch != null) {
            result.add(OmniAgentAction.SendChatMessage(sendChatMatch.groupValues[1].trim()))
        }

        // 18. Localhost OmniRoute Server Control
        if (lower.contains("localhost") || lower.contains("omniroute server") || lower.contains("20128")) {
            val start = !lower.contains("stop") && !lower.contains("kill")
            result.add(OmniAgentAction.ControlLocalhostServer(start))
        }

        // 19. Health Scan & Latency Benchmark
        if (lower.contains("health") && (lower.contains("scan") || lower.contains("check") || lower.contains("probe") || lower.contains("audit"))) {
            result.add(OmniAgentAction.RunHealthAndLatencyScan(runLatencyBenchmark = lower.contains("latency") || lower.contains("benchmark")))
        } else if (lower.contains("latency") || lower.contains("benchmark") || lower.contains("speed test")) {
            result.add(OmniAgentAction.RunHealthAndLatencyScan(runLatencyBenchmark = true))
        }

        // 20. Clear Data / Logs
        if (lower.startsWith("clear ") || lower.startsWith("wipe ") || lower.startsWith("reset ")) {
            when {
                lower.contains("usage") -> result.add(OmniAgentAction.ClearDataAction("USAGE"))
                lower.contains("latency") -> result.add(OmniAgentAction.ClearDataAction("LATENCY"))
                lower.contains("diagnostic") -> result.add(OmniAgentAction.ClearDataAction("DIAGNOSTICS"))
                lower.contains("tool") && lower.contains("log") -> result.add(OmniAgentAction.ClearDataAction("TOOL_LOGS"))
            }
        }

        // 21. Open Modal Sheets
        if (lower.contains("open ") || lower.contains("show ")) {
            when {
                lower.contains("model picker") -> result.add(OmniAgentAction.OpenModalSheet("MODEL_PICKER"))
                lower.contains("parameter") -> result.add(OmniAgentAction.OpenModalSheet("PARAMETERS"))
                lower.contains("prompt library") -> result.add(OmniAgentAction.OpenModalSheet("PROMPT_LIBRARY"))
                lower.contains("terminal") -> result.add(OmniAgentAction.OpenModalSheet("TERMINAL_HEALTH"))
                lower.contains("safety") || lower.contains("approval") -> result.add(OmniAgentAction.OpenModalSheet("SAFETY_APPROVAL"))
                lower.contains("onboarding") -> result.add(OmniAgentAction.OpenModalSheet("ONBOARDING"))
            }
        }

        // 22. Workspace Tab Navigation
        if (lower.contains("go to ") || lower.contains("switch to ") || lower.contains("open ") || lower.contains("navigate to ") || lower.endsWith(" tab")) {
            when {
                lower.contains("autopilot") || lower.contains("agent") ->
                    result.add(OmniAgentAction.NavigateTab(WorkspaceTab.AUTOPILOT))
                lower.contains("provider") || lower.contains("keys") || lower.contains("vault") || lower.contains("settings") || lower.contains("more") ->
                    result.add(OmniAgentAction.NavigateTab(WorkspaceTab.SETTINGS))
                lower.contains("image") || lower.contains("create") ->
                    result.add(OmniAgentAction.NavigateTab(WorkspaceTab.IMAGES))
                lower.contains("video") ->
                    result.add(OmniAgentAction.NavigateTab(WorkspaceTab.VIDEOS))
                lower.contains("integration") || lower.contains("connect") || lower.contains("mcp") ->
                    result.add(OmniAgentAction.NavigateTab(WorkspaceTab.INTEGRATIONS))
                lower.contains("usage") || lower.contains("analytics") || lower.contains("cost") ->
                    result.add(OmniAgentAction.NavigateTab(WorkspaceTab.USAGE))
                lower.contains("compare") ->
                    result.add(OmniAgentAction.NavigateTab(WorkspaceTab.COMPARE))
                lower.contains("chat") ->
                    result.add(OmniAgentAction.NavigateTab(WorkspaceTab.CHAT))
            }
        }

        return result
    }

    /**
     * Optional LLM-assisted planner that translates arbitrary natural language into structured JSON actions,
     * merging with deterministic parsing so it works both online and offline.
     */
    private suspend fun tryPlanWithLlm(
        rawCommand: String,
        providers: List<ProviderInstanceEntity>,
        models: List<ModelDescriptorEntity>
    ): List<OmniAgentAction> {
        val enabledProv = providers.firstOrNull {
            it.isEnabled && (it.presetEnum() == ProviderPresetType.OMNIROUTE || secretVault.getApiKey(it.id).isNotBlank())
        } ?: return emptyList()

        val apiKey = secretVault.getApiKey(enabledProv.id)
        val modelId = enabledProv.defaultChatModelId.ifBlank {
            models.firstOrNull { it.providerInstanceId == enabledProv.id }?.modelId ?: "gpt-4o-mini"
        }

        val systemInstruction = """
            You are the Runway App Controller Agent. Convert the user's command into a JSON array of actions.
            Return ONLY valid JSON array, no markdown fences.
            Supported action objects:
            - {"type":"NAVIGATE_TAB","tab":"CHAT|COMPARE|IMAGES|VIDEOS|INTEGRATIONS|USAGE|SETTINGS"}
            - {"type":"SET_COMPARE_MODE","enabled":true}
            - {"type":"SET_THEME","mode":"DARK|LIGHT|SYSTEM","dynamicColor":false}
            - {"type":"SET_CURRENCY","currency":"INR|USD"}
            - {"type":"TUNE_PARAMS","temperature":0.3,"topP":0.9,"maxTokens":4096,"systemPrompt":"..."}
            - {"type":"SWITCH_MODEL","model":"gemini-2.5-pro"}
            - {"type":"CREATE_CONVERSATION","title":"...","initialPrompt":"..."}
            - {"type":"SAVE_API_KEY","provider":"OPENAI","apiKey":"sk-..."}
            - {"type":"GENERATE_IMAGE","prompt":"...","aspectRatio":"16:9","style":"Cyberpunk","autoExecute":false}
            - {"type":"GENERATE_VIDEO","prompt":"...","aspectRatio":"16:9","resolution":"1080p","duration":5,"autoExecute":false}
            - {"type":"CREATE_SKILL","name":"...","instructions":"..."}
            - {"type":"SCAN_HEALTH","benchmark":true}
        """.trimIndent()

        return try {
            val adapter = repository.resolveAdapter(enabledProv)
            val req = ChatCompletionRequest(
                provider = enabledProv,
                apiKey = apiKey,
                modelId = modelId,
                messages = listOf(ChatMessagePayload(role = "user", content = rawCommand)),
                systemPrompt = systemInstruction,
                temperature = 0.0f,
                maxTokens = 512,
                topP = 1.0f,
                supportsTemperature = true,
                supportsTopP = true,
                supportsSystemPrompt = true
            )
            val responseBuilder = StringBuilder()
            adapter.streamChat(req).collect { ev ->
                if (ev is com.example.data.provider.ChatStreamEvent.Delta) {
                    responseBuilder.append(ev.textChunk)
                }
            }
            parseJsonActionArray(responseBuilder.toString())
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseJsonActionArray(rawText: String): List<OmniAgentAction> {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val startIdx = cleaned.indexOf('[')
        val endIdx = cleaned.lastIndexOf(']')
        if (startIdx < 0 || endIdx <= startIdx) return emptyList()

        return try {
            val arr = JSONArray(cleaned.substring(startIdx, endIdx + 1))
            val list = mutableListOf<OmniAgentAction>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                when (obj.optString("type").uppercase(Locale.US)) {
                    "NAVIGATE_TAB" -> {
                        val tabName = obj.optString("tab", "CHAT").uppercase(Locale.US)
                        val tab = WorkspaceTab.entries.firstOrNull { it.name == tabName } ?: WorkspaceTab.CHAT
                        list.add(OmniAgentAction.NavigateTab(tab))
                    }
                    "SET_COMPARE_MODE" -> {
                        list.add(OmniAgentAction.SetCompareMode(obj.optBoolean("enabled", true)))
                    }
                    "SET_THEME" -> {
                        val mode = obj.optString("mode", "DARK").uppercase(Locale.US)
                        val dyn = if (obj.has("dynamicColor")) obj.optBoolean("dynamicColor") else null
                        list.add(OmniAgentAction.SetThemeMode(mode, dyn))
                    }
                    "SET_CURRENCY" -> {
                        list.add(OmniAgentAction.SetSpendCurrency(obj.optString("currency", "INR").uppercase(Locale.US)))
                    }
                    "TUNE_PARAMS" -> {
                        val temp = if (obj.has("temperature")) obj.optDouble("temperature").toFloat() else null
                        val topP = if (obj.has("topP")) obj.optDouble("topP").toFloat() else null
                        val maxT = if (obj.has("maxTokens")) obj.optInt("maxTokens") else null
                        val sys = obj.optString("systemPrompt").takeIf { it.isNotBlank() }
                        list.add(OmniAgentAction.TuneChatParameters(temp, topP, maxT, sys))
                    }
                    "SWITCH_MODEL" -> {
                        val m = obj.optString("model")
                        if (m.isNotBlank()) list.add(OmniAgentAction.SwitchActiveModel(m))
                    }
                    "CREATE_CONVERSATION" -> {
                        val title = obj.optString("title", "Agent Conversation")
                        val initPrompt = obj.optString("initialPrompt").takeIf { it.isNotBlank() }
                        list.add(OmniAgentAction.CreateConversation(title = title, initialPrompt = initPrompt))
                    }
                    "SAVE_API_KEY" -> {
                        val p = obj.optString("provider")
                        val k = obj.optString("apiKey")
                        if (p.isNotBlank() && k.isNotBlank()) list.add(OmniAgentAction.SaveProviderApiKey(p, k))
                    }
                    "GENERATE_IMAGE" -> {
                        val p = obj.optString("prompt", "Futuristic cityscape")
                        val ratio = obj.optString("aspectRatio", "1:1")
                        val style = obj.optString("style", "Cinematic")
                        val exec = obj.optBoolean("autoExecute", false)
                        list.add(OmniAgentAction.GenerateImageAction(p, ratio, style, exec))
                    }
                    "GENERATE_VIDEO" -> {
                        val p = obj.optString("prompt", "Cinematic aerial view")
                        val ratio = obj.optString("aspectRatio", "16:9")
                        val res = obj.optString("resolution", "720p")
                        val dur = obj.optInt("duration", 5)
                        val exec = obj.optBoolean("autoExecute", false)
                        list.add(OmniAgentAction.GenerateVideoAction(p, ratio, res, dur, exec))
                    }
                    "CREATE_SKILL" -> {
                        val n = obj.optString("name", "Custom Agent Skill")
                        val inst = obj.optString("instructions", "Follow best engineering practices.")
                        list.add(OmniAgentAction.CreateSkillAction(n, "Created by Aerodock Autopilot", inst))
                    }
                    "SCAN_HEALTH" -> {
                        list.add(OmniAgentAction.RunHealthAndLatencyScan(obj.optBoolean("benchmark", true)))
                    }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Executes a single or multi-action natural language command in real time across
     * [OmniStudioViewModel], [ProvidersViewModel], [OmniDao], and [TinkSecretVault].
     */
    suspend fun executeCommandInRealTime(
        rawCommand: String,
        studioViewModel: OmniStudioViewModel,
        providersViewModel: ProvidersViewModel? = null
    ): OmniAgentExecutionRecord {
        val startMs = System.currentTimeMillis()
        val uiBefore = studioViewModel.uiState.value
        val allConvsBefore = dao.getConversationsOnce()
        val activeConvBefore = allConvsBefore.firstOrNull { it.id == uiBefore.activeConversationId }
            ?: allConvsBefore.firstOrNull()

        // Capture undo snapshot before mutating state
        val undoSnapshot = OmniAgentUndoSnapshot(
            command = rawCommand,
            previousTab = uiBefore.currentTab,
            previousCompareMode = uiBefore.compareModeEnabled,
            previousThemeMode = uiBefore.themeMode,
            previousDynamicColor = uiBefore.dynamicColorEnabled,
            previousCurrency = uiBefore.spendCurrency,
            previousEnterToSend = uiBefore.enterToSendEnabled,
            previousActiveConvId = activeConvBefore?.id,
            previousProviderId = activeConvBefore?.providerInstanceId,
            previousModelId = activeConvBefore?.modelId,
            previousTemperature = activeConvBefore?.temperature,
            previousTopP = activeConvBefore?.topP,
            previousMaxTokens = activeConvBefore?.maxTokens,
            previousSystemPrompt = activeConvBefore?.systemPrompt
        )

        _state.update {
            it.copy(
                isExecuting = true,
                liveStepsInProgress = emptyList(),
                liveStatusBanner = "Aerodock Autopilot executing: \"$rawCommand\"",
                lastUndoSnapshot = undoSnapshot
            )
        }

        val deterministicActions = parseNaturalLanguageCommand(rawCommand)
        var usedLlm = false
        val actionsToRun = if (deterministicActions.isNotEmpty() && !_state.value.useAiLlmPlanner) {
            deterministicActions
        } else if (_state.value.useAiLlmPlanner) {
            val llmActions = tryPlanWithLlm(
                rawCommand = rawCommand,
                providers = dao.getProvidersOnce(),
                models = dao.getModelsOnce()
            )
            if (llmActions.isNotEmpty()) {
                usedLlm = true
                (deterministicActions + llmActions).distinct()
            } else {
                deterministicActions
            }
        } else {
            deterministicActions
        }

        // If the user typed a general question/instruction that didn't match an app control pattern,
        // treat it as a real-time chat prompt in the active conversation!
        val finalActions = if (actionsToRun.isEmpty()) {
            listOf(
                OmniAgentAction.NavigateTab(WorkspaceTab.CHAT),
                OmniAgentAction.SendChatMessage(rawCommand)
            )
        } else {
            actionsToRun
        }

        val completedSteps = mutableListOf<OmniAgentStepResult>()

        for (action in finalActions) {
            val stepStart = System.currentTimeMillis()
            val stepResult = executeSingleAction(
                action = action,
                studioViewModel = studioViewModel,
                providersViewModel = providersViewModel,
                stepStartMs = stepStart
            )
            completedSteps.add(stepResult)
            _state.update {
                it.copy(
                    liveStepsInProgress = completedSteps.toList(),
                    liveStatusBanner = "✓ ${stepResult.title}: ${stepResult.detail}"
                )
            }
            delay(40) // Brief frame pacing so real-time UI transitions are visibly smooth
        }

        val totalMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val summary = "Executed ${completedSteps.size} real-time action(s) in ${totalMs}ms: " +
            completedSteps.joinToString(" • ") { it.title }

        val record = OmniAgentExecutionRecord(
            command = rawCommand,
            steps = completedSteps,
            agentSummary = summary,
            usedLlmPlanner = usedLlm,
            totalDurationMs = totalMs
        )

        _state.update {
            it.copy(
                isExecuting = false,
                commandInput = "",
                liveStatusBanner = summary,
                executionHistory = listOf(record) + it.executionHistory.take(29)
            )
        }

        return record
    }

    private suspend fun executeSingleAction(
        action: OmniAgentAction,
        studioViewModel: OmniStudioViewModel,
        providersViewModel: ProvidersViewModel?,
        stepStartMs: Long
    ): OmniAgentStepResult {
        val elapsed = { (System.currentTimeMillis() - stepStartMs).coerceAtLeast(1L) }

        return when (action) {
            is OmniAgentAction.NavigateTab -> {
                studioViewModel.selectTab(action.tab)
                OmniAgentStepResult(
                    category = "NAVIGATION",
                    title = "Switched Workspace Tab",
                    detail = "Navigated live to ${action.tab.name} workspace",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.SetCompareMode -> {
                studioViewModel.setCompareModeEnabled(action.enabled)
                OmniAgentStepResult(
                    category = "COMPARE",
                    title = if (action.enabled) "Enabled Compare Mode" else "Disabled Compare Mode",
                    detail = if (action.enabled) "Side-by-side multi-model arena active" else "Returned to single-model chat",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.OpenModalSheet -> {
                when (action.sheetName) {
                    "MODEL_PICKER" -> studioViewModel.setShowModelPickerSheet(true)
                    "PARAMETERS" -> studioViewModel.setShowParameterSheet(true)
                    "PROMPT_LIBRARY" -> studioViewModel.setShowPromptLibrarySheet(true)
                    "SKILLS_TOOLS" -> studioViewModel.setShowSkillsAndToolsSheet(true)
                    "TERMINAL_HEALTH" -> studioViewModel.setShowTerminalAndHealthSheet(true)
                    "SAFETY_APPROVAL" -> studioViewModel.setShowSafetyApprovalSheet(true)
                    "ONBOARDING" -> studioViewModel.setShowOnboardingSheet(true)
                }
                OmniAgentStepResult(
                    category = "UI SHEET",
                    title = "Opened ${action.sheetName} Panel",
                    detail = "Modal bottom sheet displayed in real time",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.SetThemeMode -> {
                if (action.mode.isNotBlank()) {
                    studioViewModel.setThemeMode(action.mode)
                }
                if (action.dynamicColor != null) {
                    studioViewModel.setDynamicColor(action.dynamicColor)
                }
                val desc = buildString {
                    if (action.mode.isNotBlank()) append("Theme=${action.mode}")
                    if (action.dynamicColor != null) {
                        if (isNotEmpty()) append(" • ")
                        append("DynamicColor=${action.dynamicColor}")
                    }
                }
                OmniAgentStepResult(
                    category = "THEME",
                    title = "Updated App Theme & Palette",
                    detail = desc,
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.SetSpendCurrency -> {
                studioViewModel.setSpendCurrency(action.currency)
                OmniAgentStepResult(
                    category = "SETTINGS",
                    title = "Changed Spend Currency",
                    detail = "Live cost analytics switched to ${action.currency}",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.SetEnterToSend -> {
                studioViewModel.setEnterToSend(action.enabled)
                OmniAgentStepResult(
                    category = "SETTINGS",
                    title = "Updated Enter-to-Send",
                    detail = "Enter-to-send set to ${action.enabled}",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.SetBiometricLock -> {
                studioViewModel.setBiometricGate(action.enabled)
                OmniAgentStepResult(
                    category = "SECURITY",
                    title = "Updated Biometric Vault Gate",
                    detail = "Biometric gate set to ${action.enabled}",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.SwitchActiveModel -> {
                val allModels = dao.getModelsOnce()
                val allProviders = dao.getProvidersOnce()
                val queryLower = action.modelQuery.lowercase(Locale.US)
                val provQueryLower = action.providerQuery?.lowercase(Locale.US)

                val matchedModel = allModels.firstOrNull { m ->
                    val provMatches = provQueryLower == null || allProviders.any { p ->
                        p.id == m.providerInstanceId && (
                            p.displayName.lowercase(Locale.US).contains(provQueryLower) ||
                                p.presetType.lowercase(Locale.US).contains(provQueryLower)
                            )
                    }
                    provMatches && (
                        m.modelId.lowercase(Locale.US) == queryLower ||
                            m.modelId.lowercase(Locale.US).contains(queryLower) ||
                            m.displayName.lowercase(Locale.US).contains(queryLower)
                        )
                }

                if (matchedModel != null) {
                    studioViewModel.switchActiveConversationModel(
                        matchedModel.providerInstanceId,
                        matchedModel.modelId
                    )
                    OmniAgentStepResult(
                        category = "MODEL",
                        title = "Switched Active Model",
                        detail = "Now using ${matchedModel.displayName} (${matchedModel.modelId})",
                        durationMs = elapsed()
                    )
                } else {
                    val targetProv = if (provQueryLower != null) {
                        allProviders.firstOrNull {
                            it.displayName.lowercase(Locale.US).contains(provQueryLower) ||
                                it.presetType.lowercase(Locale.US).contains(provQueryLower)
                        }
                    } else {
                        allProviders.firstOrNull { it.isDefaultForChat } ?: allProviders.firstOrNull()
                    }
                    if (targetProv != null) {
                        val added = repository.addManualModel(targetProv.id, action.modelQuery, false)
                        studioViewModel.switchActiveConversationModel(targetProv.id, added.modelId)
                        OmniAgentStepResult(
                            category = "MODEL",
                            title = "Registered & Switched Model",
                            detail = "Bound ${added.modelId} on ${targetProv.displayName}",
                            durationMs = elapsed()
                        )
                    } else {
                        OmniAgentStepResult(
                            category = "MODEL",
                            title = "Model Switch Skipped",
                            detail = "No matching provider found for ${action.modelQuery}",
                            isSuccess = false,
                            durationMs = elapsed()
                        )
                    }
                }
            }

            is OmniAgentAction.TuneChatParameters -> {
                val convs = dao.getConversationsOnce()
                val activeId = studioViewModel.uiState.value.activeConversationId
                val currentConv = convs.firstOrNull { it.id == activeId } ?: convs.firstOrNull()
                val baseConv = currentConv ?: run {
                    val prov = dao.getProvidersOnce().firstOrNull()
                    val created = ConversationEntity(
                        id = UUID.randomUUID().toString(),
                        title = "Main Workspace",
                        providerInstanceId = prov?.id ?: "provider_omniroute_default",
                        modelId = prov?.defaultChatModelId ?: "ddgw/gpt-4o-mini"
                    )
                    dao.upsertConversation(created)
                    studioViewModel.selectConversation(created.id)
                    created
                }

                val newTemp = action.temperature ?: baseConv.temperature
                val newTopP = action.topP ?: baseConv.topP
                val newMaxTokens = action.maxTokens ?: baseConv.maxTokens
                val newSysPrompt = action.systemPrompt ?: baseConv.systemPrompt

                val updatedConv = baseConv.copy(
                    temperature = newTemp,
                    topP = newTopP,
                    maxTokens = newMaxTokens,
                    systemPrompt = newSysPrompt,
                    updatedAt = System.currentTimeMillis()
                )
                dao.upsertConversation(updatedConv)

                val changes = mutableListOf<String>()
                if (action.temperature != null) changes.add("Temp=${action.temperature}")
                if (action.topP != null) changes.add("TopP=${action.topP}")
                if (action.maxTokens != null) changes.add("MaxTokens=${action.maxTokens}")
                if (action.systemPrompt != null) changes.add("SystemPrompt=\"${action.systemPrompt.take(36)}...\"")

                OmniAgentStepResult(
                    category = "PARAMETERS",
                    title = "Tuned Model Parameters",
                    detail = changes.joinToString(" • "),
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.CreateConversation -> {
                val provs = dao.getProvidersOnce()
                val defaultProv = provs.firstOrNull { it.isDefaultForChat && it.isEnabled }
                    ?: provs.firstOrNull()
                val newConv = ConversationEntity(
                    id = UUID.randomUUID().toString(),
                    title = action.title.ifBlank { "Agent Session" },
                    providerInstanceId = defaultProv?.id ?: "provider_omniroute_default",
                    modelId = defaultProv?.defaultChatModelId?.ifBlank { "ddgw/gpt-4o-mini" } ?: "ddgw/gpt-4o-mini",
                    systemPrompt = action.systemPrompt ?: "You are a helpful, accurate AI engineering assistant."
                )
                dao.upsertConversation(newConv)
                studioViewModel.selectConversation(newConv.id)

                if (!action.initialPrompt.isNullOrBlank()) {
                    studioViewModel.updateDraftMessage(action.initialPrompt)
                    studioViewModel.sendMessage()
                }

                OmniAgentStepResult(
                    category = "CHAT",
                    title = "Created New Conversation",
                    detail = "Active chat: \"${newConv.title}\"",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.SendChatMessage -> {
                studioViewModel.updateDraftMessage(action.prompt)
                studioViewModel.sendMessage()
                OmniAgentStepResult(
                    category = "CHAT",
                    title = "Dispatched Chat Message",
                    detail = "Streaming response for: \"${action.prompt.take(48)}\"",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.SaveProviderApiKey -> {
                val provs = dao.getProvidersOnce()
                val q = action.providerQuery.lowercase(Locale.US)
                val target = provs.firstOrNull {
                    it.id.lowercase(Locale.US) == q ||
                        it.presetType.lowercase(Locale.US) == q ||
                        it.displayName.lowercase(Locale.US).contains(q) ||
                        it.presetType.lowercase(Locale.US).contains(q)
                } ?: provs.firstOrNull()

                if (target != null) {
                    if (providersViewModel != null) {
                        providersViewModel.saveApiKeyAwait(target.id, action.rawApiKey)
                    } else {
                        secretVault.saveApiKey(target.id, action.rawApiKey)
                    }
                    studioViewModel.refreshMaskedKeys()
                    val masked = secretVault.getMaskedApiKey(target.id)
                    OmniAgentStepResult(
                        category = "TINK VAULT",
                        title = "Encrypted & Stored API Key",
                        detail = "${target.displayName} key encrypted via Tink AES256-GCM ($masked)",
                        durationMs = elapsed()
                    )
                } else {
                    OmniAgentStepResult(
                        category = "TINK VAULT",
                        title = "Provider Not Found",
                        detail = "Could not match provider '${action.providerQuery}'",
                        isSuccess = false,
                        durationMs = elapsed()
                    )
                }
            }

            is OmniAgentAction.ClearProviderApiKey -> {
                val provs = dao.getProvidersOnce()
                val q = action.providerQuery.lowercase(Locale.US)
                val target = provs.firstOrNull {
                    it.id.lowercase(Locale.US) == q ||
                        it.presetType.lowercase(Locale.US).contains(q) ||
                        it.displayName.lowercase(Locale.US).contains(q)
                }
                if (target != null) {
                    if (providersViewModel != null) {
                        providersViewModel.clearApiKeyAwait(target.id)
                    } else {
                        secretVault.deleteApiKey(target.id)
                    }
                    studioViewModel.refreshMaskedKeys()
                    OmniAgentStepResult(
                        category = "TINK VAULT",
                        title = "Cleared API Key from Vault",
                        detail = "Removed encrypted key for ${target.displayName}",
                        durationMs = elapsed()
                    )
                } else {
                    OmniAgentStepResult(
                        category = "TINK VAULT",
                        title = "Clear Key Skipped",
                        detail = "No provider matched '${action.providerQuery}'",
                        isSuccess = false,
                        durationMs = elapsed()
                    )
                }
            }

            is OmniAgentAction.AddOrUpdateProvider -> {
                val preset = ProviderPresetType.entries.firstOrNull {
                    it.name.equals(action.presetName, ignoreCase = true) ||
                        it.displayName.contains(action.presetName, ignoreCase = true)
                } ?: ProviderPresetType.CUSTOM

                val newId = "provider_${preset.name.lowercase(Locale.US)}_${UUID.randomUUID().toString().take(5)}"
                val entity = ProviderInstanceEntity(
                    id = newId,
                    presetType = preset.name,
                    displayName = action.displayName.ifBlank { preset.displayName },
                    baseUrl = action.baseUrl?.takeIf { it.isNotBlank() } ?: preset.defaultBaseUrl,
                    capabilitiesCsv = preset.defaultCapabilities.joinToString(",") { it.name },
                    isEnabled = true,
                    defaultChatModelId = "gpt-4o-mini"
                )
                dao.upsertProvider(entity)
                if (!action.rawApiKey.isNullOrBlank()) {
                    secretVault.saveApiKey(newId, action.rawApiKey)
                }
                providersViewModel?.loadInitialState()
                OmniAgentStepResult(
                    category = "PROVIDERS",
                    title = "Added Provider Instance",
                    detail = "${entity.displayName} (${entity.baseUrl})",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.ToggleProviderEnabled -> {
                val provs = dao.getProvidersOnce()
                val q = action.providerQuery.lowercase(Locale.US)
                val target = provs.firstOrNull {
                    it.displayName.lowercase(Locale.US).contains(q) ||
                        it.presetType.lowercase(Locale.US).contains(q)
                }
                if (target != null) {
                    val desired = action.enabled ?: !target.isEnabled
                    val updated = target.copy(isEnabled = desired)
                    dao.upsertProvider(updated)
                    providersViewModel?.loadInitialState()
                    OmniAgentStepResult(
                        category = "PROVIDERS",
                        title = if (desired) "Enabled Provider" else "Disabled Provider",
                        detail = "${target.displayName} is now ${if (desired) "ENABLED" else "DISABLED"}",
                        durationMs = elapsed()
                    )
                } else {
                    OmniAgentStepResult(
                        category = "PROVIDERS",
                        title = "Provider Toggle Skipped",
                        detail = "No provider matched '${action.providerQuery}'",
                        isSuccess = false,
                        durationMs = elapsed()
                    )
                }
            }

            is OmniAgentAction.SetDefaultProvider -> {
                val provs = dao.getProvidersOnce()
                val q = action.providerQuery.lowercase(Locale.US)
                val target = provs.firstOrNull {
                    it.displayName.lowercase(Locale.US).contains(q) ||
                        it.presetType.lowercase(Locale.US).contains(q)
                }
                if (target != null) {
                    studioViewModel.setDefaultProviderForFeature(target.id, action.capability)
                    OmniAgentStepResult(
                        category = "PROVIDERS",
                        title = "Updated Default ${action.capability.name} Provider",
                        detail = "${target.displayName} set as default for ${action.capability.name}",
                        durationMs = elapsed()
                    )
                } else {
                    OmniAgentStepResult(
                        category = "PROVIDERS",
                        title = "Default Provider Skipped",
                        detail = "Could not find provider '${action.providerQuery}'",
                        isSuccess = false,
                        durationMs = elapsed()
                    )
                }
            }

            is OmniAgentAction.AddManualModel -> {
                val provs = dao.getProvidersOnce()
                val q = action.providerQuery.lowercase(Locale.US)
                val target = provs.firstOrNull {
                    it.displayName.lowercase(Locale.US).contains(q) ||
                        it.presetType.lowercase(Locale.US).contains(q)
                } ?: provs.firstOrNull()

                if (target != null) {
                    val descriptor = repository.addManualModel(target.id, action.modelId, action.isCombo)
                    providersViewModel?.loadInitialState()
                    OmniAgentStepResult(
                        category = "MODEL",
                        title = "Added Model Descriptor",
                        detail = "Registered '${descriptor.modelId}' on ${target.displayName}",
                        durationMs = elapsed()
                    )
                } else {
                    OmniAgentStepResult(
                        category = "MODEL",
                        title = "Add Model Failed",
                        detail = "No providers available",
                        isSuccess = false,
                        durationMs = elapsed()
                    )
                }
            }

            is OmniAgentAction.GenerateImageAction -> {
                studioViewModel.selectTab(WorkspaceTab.IMAGES)
                studioViewModel.updateImagePrompt(action.prompt)
                val sizeLabel = when (action.aspectRatio) {
                    "16:9" -> "1344x768"
                    "9:16" -> "768x1344"
                    else -> "1024x1024"
                }
                studioViewModel.selectImageAspectRatio(action.aspectRatio, sizeLabel)
                studioViewModel.selectImageStylePreset(action.stylePreset)
                if (action.autoExecute) {
                    studioViewModel.generateStudioImage()
                }
                OmniAgentStepResult(
                    category = "IMAGE STUDIO",
                    title = if (action.autoExecute) "Triggered Image Generation" else "Configured Image Studio",
                    detail = "Prompt: \"${action.prompt.take(40)}\" (${action.aspectRatio}, ${action.stylePreset})",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.GenerateVideoAction -> {
                studioViewModel.selectTab(WorkspaceTab.VIDEOS)
                studioViewModel.updateVideoPrompt(action.prompt)
                studioViewModel.selectVideoAspectRatio(action.aspectRatio)
                studioViewModel.selectVideoResolution(action.resolution)
                studioViewModel.selectVideoDuration(action.durationSeconds)
                if (action.autoExecute) {
                    studioViewModel.submitStudioVideoJob()
                }
                OmniAgentStepResult(
                    category = "VIDEO STUDIO",
                    title = if (action.autoExecute) "Queued Video Generation Job" else "Configured Video Studio",
                    detail = "Prompt: \"${action.prompt.take(40)}\" (${action.resolution}, ${action.durationSeconds}s)",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.CreateSkillAction -> {
                val skillEntity = SkillEntity(
                    id = "skill_${UUID.randomUUID().toString().take(8)}",
                    name = action.name,
                    description = action.description,
                    rawMarkdown = "---\nname: \"${action.name}\"\ndescription: \"${action.description}\"\n---\n\n${action.instructions}",
                    bodyInstructions = action.instructions,
                    isEnabled = true
                )
                dao.upsertSkill(skillEntity)
                OmniAgentStepResult(
                    category = "SKILLS DB",
                    title = "Created & Enabled AI Skill",
                    detail = "Skill '${action.name}' persisted to Room DB",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.CreatePromptTemplateAction -> {
                val tpl = PromptTemplateEntity(
                    id = UUID.randomUUID().toString(),
                    title = action.title,
                    category = action.category,
                    promptText = action.content,
                    isFavorite = true
                )
                dao.upsertPromptTemplate(tpl)
                if (action.applyImmediately) {
                    studioViewModel.applyPromptTemplate(tpl)
                }
                OmniAgentStepResult(
                    category = "PROMPT LIBRARY",
                    title = "Created Prompt Template",
                    detail = "Saved '${action.title}' and applied to active workspace",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.AddMcpServerAction -> {
                val mode = McpApprovalMode.entries.firstOrNull {
                    it.name.equals(action.approvalMode, ignoreCase = true)
                } ?: McpApprovalMode.ALWAYS_ALLOW_READ_ONLY
                studioViewModel.saveMcpServer(
                    existingId = null,
                    name = action.name,
                    url = action.endpointUrl,
                    transport = com.example.data.local.McpTransportType.STREAMABLE_HTTP,
                    authHeaderName = "Authorization",
                    rawToken = null,
                    approvalMode = mode,
                    isEnabled = true
                )
                OmniAgentStepResult(
                    category = "MCP SERVER",
                    title = "Registered MCP Server",
                    detail = "${action.name} (${action.endpointUrl})",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.ControlLocalhostServer -> {
                if (action.start) {
                    EmbeddedOmniRouteServer.startServer(20128)
                } else {
                    EmbeddedOmniRouteServer.stopServer()
                }
                OmniAgentStepResult(
                    category = "LOCALHOST DAEMON",
                    title = if (action.start) "Started OmniRoute Daemon" else "Stopped OmniRoute Daemon",
                    detail = "Localhost 127.0.0.1:20128 ${if (action.start) "ONLINE" else "STOPPED"}",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.RunHealthAndLatencyScan -> {
                studioViewModel.scanHealthyProvidersAndModels()
                if (action.runLatencyBenchmark) {
                    studioViewModel.runLatencyBenchmarkProbe()
                }
                OmniAgentStepResult(
                    category = "DIAGNOSTICS",
                    title = "Launched Live Health & Latency Probe",
                    detail = "Scanning provider endpoints and updating D3 telemetry chart",
                    durationMs = elapsed()
                )
            }

            is OmniAgentAction.ClearDataAction -> {
                when (action.target) {
                    "USAGE" -> studioViewModel.clearUsageHistory()
                    "LATENCY" -> studioViewModel.clearLatencyHistory()
                    "DIAGNOSTICS" -> studioViewModel.clearDiagnostics()
                    "TOOL_LOGS" -> studioViewModel.clearToolCallLogs()
                }
                OmniAgentStepResult(
                    category = "MAINTENANCE",
                    title = "Cleared ${action.target} Data",
                    detail = "Reset ${action.target.lowercase(Locale.US)} records in real time",
                    durationMs = elapsed()
                )
            }
        }
    }

    /**
     * Reverts the most recent agent command execution in real time using [OmniAgentUndoSnapshot].
     */
    suspend fun undoLastExecution(studioViewModel: OmniStudioViewModel): Boolean {
        val snap = _state.value.lastUndoSnapshot ?: return false
        studioViewModel.setThemeMode(snap.previousThemeMode)
        studioViewModel.setDynamicColor(snap.previousDynamicColor)
        studioViewModel.setSpendCurrency(snap.previousCurrency)
        studioViewModel.setEnterToSend(snap.previousEnterToSend)
        studioViewModel.setCompareModeEnabled(snap.previousCompareMode)
        studioViewModel.selectTab(snap.previousTab)

        if (snap.previousActiveConvId != null) {
            studioViewModel.selectConversation(snap.previousActiveConvId)
            if (snap.previousProviderId != null && snap.previousModelId != null) {
                studioViewModel.switchActiveConversationModel(snap.previousProviderId, snap.previousModelId)
            }
            if (snap.previousTemperature != null &&
                snap.previousTopP != null &&
                snap.previousMaxTokens != null &&
                snap.previousSystemPrompt != null
            ) {
                studioViewModel.updateConversationParameters(
                    temperature = snap.previousTemperature,
                    topP = snap.previousTopP,
                    maxTokens = snap.previousMaxTokens,
                    systemPrompt = snap.previousSystemPrompt
                )
            }
        }

        _state.update {
            it.copy(
                lastUndoSnapshot = null,
                liveStatusBanner = "↺ Reverted changes from: \"${snap.command.take(40)}\""
            )
        }
        return true
    }
}
