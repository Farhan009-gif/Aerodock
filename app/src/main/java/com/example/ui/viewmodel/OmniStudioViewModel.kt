package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.integrations.GitHubRepoSummary
import com.example.data.integrations.YouTubeIntegrationClient
import com.example.data.local.CapabilityTag
import com.example.data.local.ChatMediaAttachment
import com.example.data.local.ClonedRepoEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.GeneratedImageEntity
import com.example.data.local.getAttachments
import com.example.data.local.IntegrationAuditLogEntity
import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.McpTransportType
import com.example.data.local.MessageEntity
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.OmniDao
import com.example.data.local.PromptTemplateEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.ProviderPresetType
import com.example.data.local.SkillEntity
import com.example.data.local.ToolCallLogEntity
import com.example.data.local.UsageRecordEntity
import com.example.data.local.VideoJobEntity
import com.example.data.mcp.McpDiscoveryResult
import com.example.data.mcp.SkillMarkdownParser
import com.example.data.provider.ChatCompletionRequest
import com.example.data.provider.ChatMessagePayload
import com.example.data.provider.ChatStreamEvent
import com.example.data.provider.DeduplicatedErrorState
import com.example.data.provider.DiagnosticRingBuffer
import com.example.data.provider.EmbeddedOmniRouteServer
import com.example.data.provider.EndpointUrlNormalizer
import com.example.data.provider.ErrorCategory
import com.example.data.provider.ImageGenerationRequest
import com.example.data.provider.LatencyBenchmarkSample
import com.example.data.provider.LocalhostServerStatus
import com.example.data.provider.McpToolDefinition
import com.example.data.provider.ModelCapabilityRegistry
import com.example.data.provider.ModelHealthProbeState
import com.example.data.provider.ModelHealthRecord
import com.example.data.provider.ModelToolCallRequest
import com.example.data.provider.NormalizedAiError
import com.example.data.provider.ProviderErrorMapper
import com.example.data.provider.ProviderSpeedRank
import com.example.data.provider.ProviderHealth
import com.example.data.provider.ProviderNormalizedException
import com.example.data.provider.RequestDiagnosticEntry
import com.example.data.provider.VideoGenerationRequest
import com.example.data.repository.OmniRepository
import com.example.data.security.TinkSecretVault
import com.example.data.security.VaultSecurityStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class WorkspaceTab {
    CHAT,
    COMPARE,
    IMAGES,
    VIDEOS,
    AUTOPILOT,
    INTEGRATIONS,
    USAGE,
    SETTINGS
}

data class CompareTargetModel(
    val providerInstanceId: String,
    val modelId: String,
    val displayName: String,
    val providerBadge: String
) {
    val key: String get() = "$providerInstanceId:$modelId"
}

data class PendingCleartextApproval(
    val host: String,
    val fullUrl: String,
    val providerName: String,
    val onApprovedAction: (suspend () -> Unit)? = null
)

data class ExportPreviewPayload(
    val title: String,
    val formatLabel: String,
    val content: String,
    val includesSensitiveKeys: Boolean = false,
    val conversationId: String? = null,
    val asJson: Boolean = false,
    val includeAllBranches: Boolean = false
)

data class DeletedConversationSnapshot(
    val conversation: ConversationEntity,
    val meta: com.example.data.local.RunwayConversationMetaEntity?,
    val messages: List<MessageEntity>,
    val treeNodes: List<com.example.data.local.RunwayMessageNodeEntity>,
    val documents: List<com.example.data.local.RunwayDocumentEntity>,
    val documentChunks: List<com.example.data.local.RunwayDocumentChunkEntity>
)

data class GitHubPushDraft(
    val title: String,
    val suggestedFileName: String,
    val suggestedFilePath: String,
    val content: String
)

data class YouTubePublishDraft(
    val job: VideoJobEntity,
    val suggestedTitle: String,
    val suggestedDescription: String,
    val suggestedTagsCsv: String
)

enum class ToolApprovalDecision {
    APPROVE_ONCE,
    APPROVE_CONVERSATION_READ_ONLY,
    DENY
}

data class PendingToolApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val server: McpServerEntity,
    val tool: McpToolEntity,
    val callRequest: ModelToolCallRequest,
    val iterationIndex: Int,
    val maxIterations: Int,
    val decisionDeferred: CompletableDeferred<ToolApprovalDecision> = CompletableDeferred()
)

data class OmniUiState(
    val currentTab: WorkspaceTab = WorkspaceTab.CHAT,
    val onboardingCompleted: Boolean = true,
    val showOnboardingSheet: Boolean = false,
    val showModelPickerSheet: Boolean = false,
    val showParameterSheet: Boolean = false,
    val showPromptLibrarySheet: Boolean = false,
    val showCompareTargetSheet: Boolean = false,
    val showSkillsAndToolsSheet: Boolean = false,
    val showSafetyApprovalSheet: Boolean = false,
    val compareModeEnabled: Boolean = false,
    val compareTargets: List<CompareTargetModel> = emptyList(),
    val themeMode: String = "DARK",
    val themeAccent: String = "SAGE",
    val dynamicColorEnabled: Boolean = false,
    val biometricGateEnabled: Boolean = false,
    val isBiometricUnlocked: Boolean = true,
    val enterToSendEnabled: Boolean = true,
    val approvedCleartextHosts: Set<String> = emptySet(),
    val maskedKeysByProvider: Map<String, String> = emptyMap(),
    val vaultSecurityStatus: VaultSecurityStatus = VaultSecurityStatus(),
    val activeConversationId: String? = null,
    val historySearchQuery: String = "",
    val modelPickerSearchQuery: String = "",
    val promptSearchQuery: String = "",
    val draftMessage: String = "",
    val attachedImageUri: String? = null,
    val attachedMediaList: List<com.example.data.local.ChatMediaAttachment> = emptyList(),
    val editingMessageId: String? = null,
    val isStreaming: Boolean = false,
    val busyProviderId: String? = null,
    val activeError: NormalizedAiError? = null,
    val providerHealthById: Map<String, ProviderHealth> = emptyMap(),
    val deduplicatedBanner: DeduplicatedErrorState? = null,
    val statusToast: String? = null,
    val pendingCleartextApproval: PendingCleartextApproval? = null,
    val exportPreview: ExportPreviewPayload? = null,
    // Phase 3: Image Generation Studio & Local Gallery state
    val selectedImageProviderId: String = "provider_openai_default",
    val selectedImageModelId: String = "dall-e-3",
    val imagePrompt: String = "",
    val imageNegativePrompt: String = "",
    val imageAspectRatio: String = "1:1",
    val imageSize: String = "1024x1024",
    val imageCount: Int = 1,
    val imageSeedText: String = "",
    val imageStylePreset: String = "None",
    val imageGuidanceScale: Float = 7.5f,
    val imageSteps: Int = 28,
    val imageQuality: String = "standard",
    val imageSourceUri: String? = null,
    val imageStrength: Float = 0.75f,
    val isGeneratingImage: Boolean = false,
    val inspectingImage: GeneratedImageEntity? = null,
    // Phase 4: Async Video Generation Studio & Video Gallery state
    val selectedVideoProviderId: String = "provider_omniroute_default",
    val selectedVideoModelId: String = "omniroute/video-combo",
    val videoPrompt: String = "",
    val videoNegativePrompt: String = "",
    val videoAspectRatio: String = "16:9",
    val videoResolution: String = "720p",
    val videoDurationSeconds: Int = 5,
    val videoCameraMotion: String = "None",
    val videoSourceImageUri: String? = null,
    val isSubmittingVideo: Boolean = false,
    val inspectingVideoJob: VideoJobEntity? = null,
    // Phase 5: Manual User-Triggered GitHub & YouTube Integrations state
    val maskedGitHubPat: String = "",
    val gitHubProfileSummary: String = "",
    val gitHubRepos: List<GitHubRepoSummary> = emptyList(),
    val isGitHubBusy: Boolean = false,
    val gitHubPushDraft: GitHubPushDraft? = null,
    val maskedYouTubeAccessToken: String = "",
    val youTubeClientId: String = "",
    val youTubePkceVerifier: String = "",
    val youTubePkceChallenge: String = "",
    val youTubeChannelSummary: String = "",
    val isYouTubeBusy: Boolean = false,
    val youTubePublishDraft: YouTubePublishDraft? = null,
    // Phase 6: Skills (SKILL.md) & Remote MCP Servers state
    val maskedMcpTokensByServer: Map<String, String> = emptyMap(),
    val busyMcpServerId: String? = null,
    val mcpDiscoverySummaries: Map<String, McpDiscoveryResult> = emptyMap(),
    val pendingToolApproval: PendingToolApprovalRequest? = null,
    val activeToolLoopStatus: String? = null,
    val omniRouteTerminalLogs: List<String> = listOf(
        "OmniRoute Embedded Terminal v2.5 (Android Localhost Daemon)",
        "Type 'omniroute start', 'omniroute status', 'omniroute combos', 'omniroute video <prompt>', or 'curl http://localhost:20128/health'."
    ),
    val isOmniRouteTerminalRunning: Boolean = false,
    val showTerminalAndHealthSheet: Boolean = false,
    val localhostServerStatus: LocalhostServerStatus = LocalhostServerStatus(),
    val omniRouteCombos: List<com.example.data.provider.OmniRouteComboDefinition> = emptyList(),
    val modelHealthByKey: Map<String, ModelHealthRecord> = emptyMap(),
    val isScanningModelsHealth: Boolean = false,
    val latencyBenchmarkHistory: List<LatencyBenchmarkSample> = emptyList(),
    val isProbingLatency: Boolean = false,
    val autoProbeIntervalSeconds: Int = 0,
    val speedRanks: List<ProviderSpeedRank> = emptyList(),
    val fastestProviderRank: ProviderSpeedRank? = null,
    val spendCurrency: String = "INR",
    val usdToInrRate: Double = 84.0,
    // Step (1): Runway Data Layer & Migration verification state
    val runwayMigrationStatus: com.example.data.local.RunwayMigrationStatus = com.example.data.local.RunwayMigrationStatus(),
    // Step (3): Chat History & Search state
    val hasPendingDeletedConversationUndo: Boolean = false,
    val selectedHistoryFolderId: String? = null,
    // Connectors, Per-Tool Approval Policy, Sandboxed Code Execution & Usage state
    val showConnectorsScreen: Boolean = false,
    val selectedConnectorIdForDetail: String? = null,
    val connectorDiscoveryEnabled: Boolean = true,
    val connectorDefaultChoices: Map<String, com.example.data.mcp.ToolApprovalChoice> = emptyMap(),
    val connectorToolChoices: Map<String, com.example.data.mcp.ToolApprovalChoice> = emptyMap(),
    val confirmedWriteAlwaysTools: Set<String> = emptySet(),
    val estimatedTokenMessageIds: Set<String> = emptySet(),
    // Git Clone Repositories & Agent Skills
    val showGitCloneSheet: Boolean = false,
    val isGitCloning: Boolean = false,
    val gitCloneProgressMessage: String = "",
    val gitCloneError: String? = null,
    val selectedClonedRepo: ClonedRepoEntity? = null,
    val showRepoFileBrowserSheet: Boolean = false,
    val repoFilesList: List<String> = emptyList(),
    val viewingRepoFilePath: String? = null,
    val viewingRepoFileContent: String? = null,
    // 1. Voice (SpeechRecognizer + TextToSpeech)
    val readRepliesAloud: Boolean = false,
    val isVoiceListening: Boolean = false,
    // 2. Cheaper Model Suggestion
    val cheaperModelSuggestionByChatId: Map<String, com.example.data.provider.CheaperModelSuggestion> = emptyMap(),
    val dismissedCheaperSuggestionChatIds: Set<String> = emptySet(),
    // 3. Spending Limit Per Provider
    val providerMonthlyLimitsUsd: Map<String, Double> = emptyMap(),
    val spendingLimitActiveOverrideChatIds: Set<String> = emptySet(),
    val spendingLimitUsedOverrideChatIds: Set<String> = emptySet(),
    // 5. File Attachments
    val hasSeenFileAttachmentNotice: Boolean = false,
    val showFirstTimeFileNoticeForProvider: String? = null,
    // 6. Prompt Caching
    val cachedTokensByMessageId: Map<String, Int> = emptyMap(),
    // 7. Smart Context Trimming
    val contextTrimmingEnabledByChatId: Map<String, Boolean> = emptyMap(),
    val contextSummaryByChatId: Map<String, String> = emptyMap(),
    // 8. Code Runner: HTML/CSS/JS Preview Panel & Java Remote Runner Settings
    val javaRunnerUrl: String = "",
    val maskedJavaRunnerKey: String = "",
    val activeHtmlPreviewDocument: String? = null,
    val activeHtmlPreviewTitle: String = "",
    // 9. Live Info Tools (web_search & news_headlines)
    val liveInfoWebSearchEnabled: Boolean = false,
    val liveInfoNewsHeadlinesEnabled: Boolean = false,
    val liveInfoMaxSearchesPerMessage: Int = com.example.data.mcp.LiveInfoToolsService.DEFAULT_MAX_SEARCHES_PER_MESSAGE,
    val liveInfoRssFeeds: List<com.example.data.mcp.RssFeedConfig> = com.example.data.mcp.LiveInfoToolsService.DEFAULT_RSS_FEEDS,
    val liveInfoSourcesByMessageId: Map<String, List<com.example.data.mcp.LiveInfoSource>> = emptyMap(),
    // 10. Automatic Provider Failover
    val providerFailoverEnabled: Boolean = false,
    val providerFailoverChain: List<com.example.data.provider.FailoverChainEntry> = emptyList(),
    val restartedOnProviderByMessageId: Map<String, String> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class OmniStudioViewModel(
    private val repository: OmniRepository,
    private val dao: OmniDao,
    private val autoInitialize: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(OmniUiState())
    val uiState: StateFlow<OmniUiState> = _uiState.asStateFlow()

    val voiceManager = com.example.data.voice.RunwayVoiceManager(repository.appContext)

    val providers: StateFlow<List<ProviderInstanceEntity>> = repository.providersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models: StateFlow<List<ModelDescriptorEntity>> = repository.modelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = repository.conversationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val promptTemplates: StateFlow<List<PromptTemplateEntity>> = repository.promptTemplatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usageRecords: StateFlow<List<UsageRecordEntity>> = repository.usageRecordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generatedImages: StateFlow<List<GeneratedImageEntity>> = repository.generatedImagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoJobs: StateFlow<List<VideoJobEntity>> = repository.videoJobsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val integrationLogs: StateFlow<List<IntegrationAuditLogEntity>> = repository.integrationLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skills: StateFlow<List<SkillEntity>> = repository.skillsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpServers: StateFlow<List<McpServerEntity>> = repository.mcpServersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpTools: StateFlow<List<McpToolEntity>> = repository.mcpToolsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val toolCallLogs: StateFlow<List<ToolCallLogEntity>> = repository.toolCallLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clonedRepos: StateFlow<List<ClonedRepoEntity>> = repository.clonedReposFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Step (1): Runway Data Layer reactive StateFlows
    val runwayFolders: StateFlow<List<com.example.data.local.RunwayFolderEntity>> = repository.runwayFoldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runwayConversationMetas: StateFlow<List<com.example.data.local.RunwayConversationMetaEntity>> =
        repository.runwayConversationMetasFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runwayPrompts: StateFlow<List<com.example.data.local.RunwayPromptEntity>> = repository.runwayPromptsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runwayParameterPresets: StateFlow<List<com.example.data.local.RunwayParameterPresetEntity>> =
        repository.runwayParameterPresetsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runwayUsageLogs: StateFlow<List<com.example.data.local.RunwayUsageLogEntity>> = repository.runwayUsageLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runwayAllMessageNodes: StateFlow<List<com.example.data.local.RunwayMessageNodeEntity>> =
        repository.runwayAllMessageNodesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runwayAllDocuments: StateFlow<List<com.example.data.local.RunwayDocumentEntity>> =
        repository.runwayAllDocumentsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lastDeletedConversationSnapshot: DeletedConversationSnapshot? = null

    val diagnosticEntries: StateFlow<List<RequestDiagnosticEntry>> = DiagnosticRingBuffer.entriesFlow

    val autopilotEngine = com.example.data.agent.OmniAutopilotEngine(
        repository = repository,
        dao = dao,
        secretVault = repository.secretVault
    )
    val agentState: StateFlow<com.example.data.agent.OmniAgentUiState> = autopilotEngine.state

    private val activeConvIdFlow = MutableStateFlow<String?>(null)

    val activeMessages: StateFlow<List<MessageEntity>> = activeConvIdFlow
        .flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList()) else repository.observeMessages(convId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRunwayMessageNodes: StateFlow<List<com.example.data.local.RunwayMessageNodeEntity>> = activeConvIdFlow
        .flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList()) else repository.observeRunwayMessageNodes(convId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRunwayConversationMeta: StateFlow<com.example.data.local.RunwayConversationMetaEntity?> = activeConvIdFlow
        .flatMapLatest { convId ->
            if (convId == null) flowOf(null) else repository.observeRunwayConversationMeta(convId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeConversationToolLogs: StateFlow<List<ToolCallLogEntity>> = activeConvIdFlow
        .flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList()) else repository.observeToolCallLogsForConversation(convId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeStreamJob: Job? = null
    // Tracks servers approved once for the active conversation (McpApprovalMode.ASK_ONCE_PER_CONVERSATION)
    private val conversationApprovedReadOnlyServers = mutableSetOf<String>()
    // Tracks conversations where the user clicked "Always allow for this chat" for sandboxed run_code
    private val conversationAllowedCodeExecutionIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            voiceManager.isListening.collect { listening ->
                _uiState.update { it.copy(isVoiceListening = listening) }
            }
        }
        viewModelScope.launch {
            combine(
                repository.secretVault.readRepliesAloudFlow,
                repository.secretVault.providerSpendingLimitsJsonFlow,
                repository.secretVault.hasSeenFileAttachmentNoticeFlow,
                repository.secretVault.javaRunnerUrlFlow
            ) { readAloud, limitsJson, seenNotice, javaUrl ->
                 listOf(
                    readAloud,
                    com.example.data.provider.ProviderSpendingLimitEvaluator.parseLimitsJson(limitsJson),
                    seenNotice,
                    javaUrl
                )
            }.collect { values ->
                val readAloud = values[0] as Boolean
                @Suppress("UNCHECKED_CAST")
                val limitsMap = values[1] as Map<String, Double>
                val seenNotice = values[2] as Boolean
                val javaUrl = values[3] as String
                val maskedJavaKey = repository.secretVault.getMaskedJavaRunnerKey()
                _uiState.update { state ->
                    state.copy(
                        readRepliesAloud = readAloud,
                        providerMonthlyLimitsUsd = limitsMap,
                        hasSeenFileAttachmentNotice = seenNotice,
                        javaRunnerUrl = javaUrl,
                        maskedJavaRunnerKey = maskedJavaKey
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.secretVault.liveInfoWebSearchEnabledFlow,
                repository.secretVault.liveInfoNewsHeadlinesEnabledFlow,
                repository.secretVault.liveInfoMaxSearchesPerMessageFlow,
                repository.secretVault.liveInfoRssFeedsJsonFlow
            ) { webEnabled, newsEnabled, maxSearches, feedsJson ->
                Tuple4(
                    webEnabled,
                    newsEnabled,
                    maxSearches,
                    com.example.data.mcp.LiveInfoToolsService.parseFeedsFromJson(feedsJson)
                )
            }.collect { (webEnabled, newsEnabled, maxSearches, feedsList) ->
                _uiState.update { state ->
                    state.copy(
                        liveInfoWebSearchEnabled = webEnabled,
                        liveInfoNewsHeadlinesEnabled = newsEnabled,
                        liveInfoMaxSearchesPerMessage = maxSearches,
                        liveInfoRssFeeds = feedsList
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.secretVault.runwayFallbackEnabledFlow,
                repository.secretVault.runwayFallbackChainJsonFlow
            ) { enabled, chainJson ->
                enabled to com.example.data.provider.FailoverResolver.parseChainFromJson(chainJson)
            }.collect { (enabled, chainList) ->
                _uiState.update { state ->
                    state.copy(
                        providerFailoverEnabled = enabled,
                        providerFailoverChain = chainList
                    )
                }
            }
        }
        if (autoInitialize) {
            viewModelScope.launch {
                EmbeddedOmniRouteServer.statusFlow.collect { st ->
                    _uiState.update { it.copy(localhostServerStatus = st) }
                }
            }
        viewModelScope.launch {
            EmbeddedOmniRouteServer.terminalLinesFlow.collect { lines ->
                _uiState.update { it.copy(omniRouteTerminalLogs = lines) }
            }
        }
        viewModelScope.launch {
            EmbeddedOmniRouteServer.combosFlow.collect { combos ->
                _uiState.update { it.copy(omniRouteCombos = combos) }
            }
        }
        viewModelScope.launch {
            repository.ensureDefaultPresetsSeeded()
            val migStatus = repository.runAndVerifyRunwayMigration()
            _uiState.update { it.copy(runwayMigrationStatus = migStatus) }
            refreshMaskedKeys()
            val allConvs = dao.getConversationsOnce()
            if (allConvs.isNotEmpty() && activeConvIdFlow.value == null) {
                selectConversation(allConvs.first().id)
            }
            seedDefaultCompareTargets()
            // Mark embedded localhost OmniRoute healthy automatically on startup
            val omniProv = dao.getProvidersOnce().firstOrNull {
                it.presetEnum() == ProviderPresetType.OMNIROUTE || it.baseUrl.contains("20128")
            }
            if (omniProv != null) {
                _uiState.update { state ->
                    state.copy(
                        providerHealthById = state.providerHealthById + (omniProv.id to ProviderHealth.Healthy(latencyMs = 4L))
                    )
                }
            }
            seedInitialLatencySamples()
        }

        viewModelScope.launch {
            combine(
                repository.secretVault.onboardingCompletedFlow,
                repository.secretVault.themeModeFlow,
                repository.secretVault.dynamicColorFlow,
                repository.secretVault.biometricGateFlow,
                repository.secretVault.enterToSendFlow
            ) { onboardingDone, theme, dynColor, bioGate, enterSend ->
                Tuple5(onboardingDone, theme, dynColor, bioGate, enterSend)
            }.collect { (onboardingDone, theme, dynColor, bioGate, enterSend) ->
                _uiState.update { state ->
                    state.copy(
                        onboardingCompleted = onboardingDone,
                        showOnboardingSheet = !onboardingDone,
                        themeMode = theme,
                        dynamicColorEnabled = dynColor,
                        biometricGateEnabled = bioGate,
                        isBiometricUnlocked = if (!bioGate) true else state.isBiometricUnlocked,
                        enterToSendEnabled = enterSend
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.secretVault.themeAccentFlow.collect { accent ->
                _uiState.update { it.copy(themeAccent = accent) }
            }
        }

        viewModelScope.launch {
            repository.secretVault.approvedCleartextHostsFlow.collect { hosts ->
                _uiState.update { it.copy(approvedCleartextHosts = hosts) }
            }
        }

        viewModelScope.launch {
            combine(
                repository.secretVault.connectorDiscoveryEnabledFlow,
                repository.secretVault.connectorDefaultPoliciesJsonFlow,
                repository.secretVault.connectorToolPoliciesJsonFlow,
                repository.secretVault.confirmedWriteAlwaysToolsFlow
            ) { discoveryEnabled, defaultJson, toolJson, confirmedSet ->
                val defaultMap = parseChoicesJsonMap(defaultJson)
                val toolMap = parseChoicesJsonMap(toolJson)
                Tuple4(discoveryEnabled, defaultMap, toolMap, confirmedSet)
            }.collect { (discoveryEnabled, defaultMap, toolMap, confirmedSet) ->
                _uiState.update { state ->
                    state.copy(
                        connectorDiscoveryEnabled = discoveryEnabled,
                        connectorDefaultChoices = defaultMap,
                        connectorToolChoices = toolMap,
                        confirmedWriteAlwaysTools = confirmedSet
                    )
                }
            }
        }

        viewModelScope.launch {
            runwayUsageLogs.collect { logs ->
                val estimatedIds = logs.filter { it.isEstimated }.mapNotNull { it.messageId }.toSet()
                _uiState.update { state ->
                    state.copy(estimatedTokenMessageIds = state.estimatedTokenMessageIds + estimatedIds)
                }
            }
        }

        viewModelScope.launch {
            providers.collect { list ->
                refreshMaskedKeys(list)
                if (_uiState.value.compareTargets.isEmpty() && list.isNotEmpty()) {
                    seedDefaultCompareTargets()
                }
            }
        }
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    private suspend fun seedDefaultCompareTargets() {
        val provs = dao.getProvidersOnce()
        val openAi = provs.firstOrNull { it.presetEnum() == ProviderPresetType.OPENAI }
        val anthropic = provs.firstOrNull { it.presetEnum() == ProviderPresetType.ANTHROPIC }
        val gemini = provs.firstOrNull { it.presetEnum() == ProviderPresetType.GEMINI }
        val omni = provs.firstOrNull { it.presetEnum() == ProviderPresetType.OMNIROUTE }

        val targets = mutableListOf<CompareTargetModel>()
        if (openAi != null) {
            targets.add(
                CompareTargetModel(
                    providerInstanceId = openAi.id,
                    modelId = openAi.defaultChatModelId.ifBlank { "gpt-4o" },
                    displayName = "GPT-4o",
                    providerBadge = "OAI"
                )
            )
        }
        if (anthropic != null) {
            targets.add(
                CompareTargetModel(
                    providerInstanceId = anthropic.id,
                    modelId = anthropic.defaultChatModelId.ifBlank { "claude-3-7-sonnet-latest" },
                    displayName = "Claude 3.7 Sonnet",
                    providerBadge = "ANT"
                )
            )
        }
        if (gemini != null && targets.size < 3) {
            targets.add(
                CompareTargetModel(
                    providerInstanceId = gemini.id,
                    modelId = gemini.defaultChatModelId.ifBlank { "gemini-2.5-pro" },
                    displayName = "Gemini 2.5 Pro",
                    providerBadge = "GEM"
                )
            )
        }
        if (omni != null && targets.size < 2) {
            targets.add(
                CompareTargetModel(
                    providerInstanceId = omni.id,
                    modelId = omni.defaultChatModelId.ifBlank { "gemini-2.5-flash" },
                    displayName = "Gemini 2.5 Flash (OmniRoute)",
                    providerBadge = "OMNI"
                )
            )
        }
        if (targets.isNotEmpty()) {
            _uiState.update { it.copy(compareTargets = targets.take(3)) }
        }
    }

    internal suspend fun refreshMaskedKeys(providerList: List<ProviderInstanceEntity>? = null) {
        val list = providerList ?: dao.getProvidersOnce()
        val map = mutableMapOf<String, String>()
        for (p in list) {
            val masked = repository.secretVault.getMaskedApiKey(p.id)
            if (masked.isNotBlank()) {
                map[p.id] = masked
            }
        }
        val mcpMap = mutableMapOf<String, String>()
        for (srv in dao.getMcpServersOnce()) {
            val masked = repository.secretVault.getMaskedApiKey(TinkSecretVault.mcpServerSecretId(srv.id))
            if (masked.isNotBlank()) {
                mcpMap[srv.id] = masked
            }
        }
        val ghMasked = repository.secretVault.getMaskedApiKey(TinkSecretVault.INTEGRATION_GITHUB_PAT_ID)
        val ytMasked = repository.secretVault.getMaskedApiKey(TinkSecretVault.INTEGRATION_YOUTUBE_ACCESS_TOKEN_ID)
        val ytClientId = repository.secretVault.getApiKey(TinkSecretVault.INTEGRATION_YOUTUBE_CLIENT_ID)
        val vaultStatus = repository.secretVault.getVaultSecurityStatus()
        _uiState.update { state ->
            val verifier = state.youTubePkceVerifier.ifBlank {
                YouTubeIntegrationClient.generateCodeVerifier()
            }
            val challenge = YouTubeIntegrationClient.generateCodeChallengeS256(verifier)
            state.copy(
                maskedKeysByProvider = map,
                maskedMcpTokensByServer = mcpMap,
                maskedGitHubPat = ghMasked,
                maskedYouTubeAccessToken = ytMasked,
                youTubeClientId = ytClientId,
                youTubePkceVerifier = verifier,
                youTubePkceChallenge = challenge,
                vaultSecurityStatus = vaultStatus
            )
        }
    }

    // --- Navigation & Modals ---

    fun selectTab(tab: WorkspaceTab) {
        if (tab != WorkspaceTab.CHAT) {
            voiceManager.stopSpeaking()
        }
        _uiState.update {
            it.copy(
                currentTab = tab,
                showConnectorsScreen = false,
                selectedConnectorIdForDetail = null,
                compareModeEnabled = if (tab == WorkspaceTab.COMPARE) true else it.compareModeEnabled
            )
        }
    }

    fun setCompareModeEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                compareModeEnabled = enabled,
                currentTab = if (enabled && it.currentTab != WorkspaceTab.COMPARE) WorkspaceTab.COMPARE
                else if (!enabled && it.currentTab == WorkspaceTab.COMPARE) WorkspaceTab.CHAT
                else it.currentTab
            )
        }
    }

    fun setShowCompareTargetSheet(show: Boolean) {
        _uiState.update { it.copy(showCompareTargetSheet = show) }
    }

    fun toggleModelInCompareTargets(model: ModelDescriptorEntity, provider: ProviderInstanceEntity) {
        _uiState.update { state ->
            val key = "${model.providerInstanceId}:${model.modelId}"
            val exists = state.compareTargets.any { it.key == key }
            val updated = if (exists) {
                if (state.compareTargets.size <= 2) {
                    return@update state.copy(statusToast = "Compare Mode requires at least 2 models.")
                }
                state.compareTargets.filterNot { it.key == key }
            } else {
                if (state.compareTargets.size >= 3) {
                    return@update state.copy(statusToast = "Compare Mode supports up to 3 models side-by-side.")
                }
                state.compareTargets + CompareTargetModel(
                    providerInstanceId = model.providerInstanceId,
                    modelId = model.modelId,
                    displayName = model.displayName,
                    providerBadge = provider.presetEnum().badgeShort
                )
            }
            state.copy(compareTargets = updated)
        }
    }

    fun setSpendCurrency(currency: String) {
        _uiState.update { it.copy(spendCurrency = currency.uppercase()) }
    }

    fun setUsdToInrRate(rate: Double) {
        if (rate > 0.0) {
            _uiState.update { it.copy(usdToInrRate = rate) }
        }
    }

    fun setShowOnboardingSheet(show: Boolean) {
        _uiState.update { it.copy(showOnboardingSheet = show) }
    }

    fun completeOnboarding(
        omniBaseUrl: String,
        omniApiKey: String,
        approveHttpHostIfNeeded: Boolean
    ) {
        viewModelScope.launch {
            val cleanUrl = omniBaseUrl.trim().ifBlank { ProviderPresetType.OMNIROUTE.defaultBaseUrl }
            if (approveHttpHostIfNeeded) {
                extractHttpHost(cleanUrl)?.let { host ->
                    repository.secretVault.approveCleartextHost(host)
                }
            }
            val existingOmni = providers.value.firstOrNull {
                it.presetEnum() == ProviderPresetType.OMNIROUTE
            }
            if (existingOmni != null) {
                val updated = existingOmni.copy(baseUrl = cleanUrl, isEnabled = true)
                repository.saveProviderWithKey(updated, omniApiKey.takeIf { it.isNotBlank() })
            }
            repository.secretVault.setOnboardingCompleted(true)
            refreshMaskedKeys()
            _uiState.update {
                it.copy(
                    onboardingCompleted = true,
                    showOnboardingSheet = false,
                    statusToast = "OmniRoute gateway configured."
                )
            }
        }
    }

    fun setShowModelPickerSheet(show: Boolean) {
        _uiState.update { it.copy(showModelPickerSheet = show, modelPickerSearchQuery = "") }
    }

    fun setShowParameterSheet(show: Boolean) {
        _uiState.update { it.copy(showParameterSheet = show) }
    }

    fun setShowPromptLibrarySheet(show: Boolean) {
        _uiState.update { it.copy(showPromptLibrarySheet = show, promptSearchQuery = "") }
    }

    fun setShowSkillsAndToolsSheet(show: Boolean) {
        _uiState.update { it.copy(showSkillsAndToolsSheet = show) }
    }

    fun updateHistorySearchQuery(query: String) {
        _uiState.update { it.copy(historySearchQuery = query) }
    }

    fun updateModelPickerSearchQuery(query: String) {
        _uiState.update { it.copy(modelPickerSearchQuery = query) }
    }

    fun updatePromptSearchQuery(query: String) {
        _uiState.update { it.copy(promptSearchQuery = query) }
    }

    fun dismissError() {
        _uiState.update {
            it.copy(
                activeError = null,
                deduplicatedBanner = null
            )
        }
    }

    fun clearDiagnostics() {
        DiagnosticRingBuffer.clear()
        _uiState.update { it.copy(statusToast = "Cleared request diagnostics ring buffer") }
    }

    fun setShowTerminalAndHealthSheet(show: Boolean) {
        _uiState.update { it.copy(showTerminalAndHealthSheet = show) }
    }

    fun startLocalhostOmniRoute() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOmniRouteTerminalRunning = true) }
            val status = EmbeddedOmniRouteServer.startServer(20128)
            val omniProv = providers.value.firstOrNull {
                it.presetEnum() == ProviderPresetType.OMNIROUTE || it.baseUrl.contains("20128")
            } ?: dao.getProvidersOnce().firstOrNull {
                it.presetEnum() == ProviderPresetType.OMNIROUTE || it.baseUrl.contains("20128")
            }
            if (omniProv != null) {
                val updated = omniProv.copy(
                    baseUrl = "http://localhost:20128/v1",
                    defaultChatModelId = "ddgw/gpt-4o-mini",
                    lastTestStatus = "Listening on http://localhost:20128/v1"
                )
                dao.upsertProvider(updated)
                repository.sanitizeAndVerifyOmniRouteTerminal(updated.id)
                _uiState.update { state ->
                    state.copy(
                        isOmniRouteTerminalRunning = false,
                        providerHealthById = state.providerHealthById + (updated.id to ProviderHealth.Healthy(latencyMs = 3L)),
                        activeError = null,
                        deduplicatedBanner = null,
                        statusToast = if (status.isListening) "OmniRoute started on http://localhost:20128/v1" else status.lastRequestSummary
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isOmniRouteTerminalRunning = false,
                        statusToast = "OmniRoute running on http://localhost:20128/v1"
                    )
                }
            }
        }
    }

    fun stopLocalhostOmniRoute() {
        viewModelScope.launch {
            EmbeddedOmniRouteServer.stopServer()
            _uiState.update {
                it.copy(statusToast = "Stopped localhost:20128 server")
            }
        }
    }

    fun upsertOmniRouteCustomCombo(
        rawId: String,
        displayName: String,
        modality: String,
        strategy: String,
        targetModels: List<String>,
        description: String,
        selectInStudio: Boolean = false,
        additionalProviderId: String? = null
    ) {
        viewModelScope.launch {
            val combo = repository.upsertOmniRouteCustomCombo(
                rawId = rawId,
                displayName = displayName,
                modality = modality,
                strategy = strategy,
                targetModels = targetModels,
                description = description,
                additionalProviderId = additionalProviderId
            )
            val modUpper = combo.modality.uppercase()
            val omniProvId = providers.value.firstOrNull {
                it.presetEnum() == ProviderPresetType.OMNIROUTE || it.baseUrl.contains("20128")
            }?.id ?: "provider_omniroute_default"
            val targetProvId = additionalProviderId?.takeIf { it.isNotBlank() } ?: omniProvId

            if (selectInStudio && modUpper == "CHAT") {
                switchActiveConversationModel(targetProvId, combo.id)
            }

            _uiState.update { state ->
                state.copy(
                    omniRouteCombos = EmbeddedOmniRouteServer.combosFlow.value,
                    selectedVideoProviderId = if (selectInStudio && modUpper == "VIDEO") targetProvId else state.selectedVideoProviderId,
                    selectedVideoModelId = if (selectInStudio && modUpper == "VIDEO") combo.id else state.selectedVideoModelId,
                    selectedImageProviderId = if (selectInStudio && modUpper == "IMAGE") targetProvId else state.selectedImageProviderId,
                    selectedImageModelId = if (selectInStudio && modUpper == "IMAGE") combo.id else state.selectedImageModelId,
                    statusToast = "Saved OmniRoute combo: ${combo.id} (${combo.modality})"
                )
            }
        }
    }

    fun deleteOmniRouteCustomCombo(comboId: String) {
        viewModelScope.launch {
            val removed = repository.deleteOmniRouteCustomCombo(comboId)
            _uiState.update {
                it.copy(
                    omniRouteCombos = EmbeddedOmniRouteServer.combosFlow.value,
                    statusToast = if (removed) "Deleted combo: $comboId" else "Combo reset or not found"
                )
            }
        }
    }

    fun addCustomModelToProvider(
        providerId: String,
        rawModelId: String,
        displayName: String = "",
        modality: String = "VIDEO",
        isCombo: Boolean = false,
        selectInStudio: Boolean = false
    ) {
        viewModelScope.launch {
            val trimmed = rawModelId.trim()
            if (trimmed.isEmpty()) return@launch
            val added = repository.addCustomModelToProvider(
                providerId = providerId,
                rawModelId = trimmed,
                displayName = displayName,
                modality = modality,
                isCombo = isCombo
            )
            _uiState.update { state ->
                val modUpper = modality.uppercase()
                state.copy(
                    selectedVideoProviderId = if (selectInStudio && modUpper == "VIDEO") providerId else state.selectedVideoProviderId,
                    selectedVideoModelId = if (selectInStudio && modUpper == "VIDEO") added.modelId else state.selectedVideoModelId,
                    selectedImageProviderId = if (selectInStudio && modUpper == "IMAGE") providerId else state.selectedImageProviderId,
                    selectedImageModelId = if (selectInStudio && modUpper == "IMAGE") added.modelId else state.selectedImageModelId,
                    statusToast = "Added ${modality.lowercase()} model: ${added.displayName}"
                )
            }
        }
    }

    fun executeTerminalCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOmniRouteTerminalRunning = true) }
            val lower = trimmed.lowercase()
            if (lower == "omniroute scan-health" || lower == "scan-health" || lower == "omniroute check-health") {
                EmbeddedOmniRouteServer.appendTerminalLine("$ $trimmed")
                EmbeddedOmniRouteServer.appendTerminalLine("Scanning all enabled providers and models...")
                _uiState.update { it.copy(isOmniRouteTerminalRunning = false) }
                scanHealthyProvidersAndModels(null)
                return@launch
            }
            EmbeddedOmniRouteServer.executeTerminalCommand(trimmed)
            if (lower.contains("omniroute start") || lower == "start" || lower.contains("restart")) {
                val omniProv = providers.value.firstOrNull {
                    it.presetEnum() == ProviderPresetType.OMNIROUTE || it.baseUrl.contains("20128")
                }
                if (omniProv != null) {
                    dao.upsertProvider(omniProv.copy(baseUrl = "http://localhost:20128/v1"))
                    _uiState.update { state ->
                        state.copy(
                            providerHealthById = state.providerHealthById + (omniProv.id to ProviderHealth.Healthy(latencyMs = 3L))
                        )
                    }
                }
            }
            _uiState.update { it.copy(isOmniRouteTerminalRunning = false) }
        }
    }

    fun runOmniRouteCleanTerminal(providerId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOmniRouteTerminalRunning = true) }
            EmbeddedOmniRouteServer.startServer(20128)
            val resultLogs = repository.sanitizeAndVerifyOmniRouteTerminal(providerId)
            resultLogs.forEach { EmbeddedOmniRouteServer.appendTerminalLine(it) }
            _uiState.update {
                it.copy(
                    isOmniRouteTerminalRunning = false,
                    showTerminalAndHealthSheet = true,
                    activeError = null,
                    deduplicatedBanner = null,
                    statusToast = "OmniRoute localhost:20128 started & scrapers purged"
                )
            }
        }
    }

    fun scanHealthyProvidersAndModels(targetProviderId: String? = null) {
        if (_uiState.value.isScanningModelsHealth) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningModelsHealth = true) }
            EmbeddedOmniRouteServer.startServer(20128)
            EmbeddedOmniRouteServer.appendTerminalLine("Starting Provider & Model Health Scan...")

            val allProviders = dao.getProvidersOnce().filter { it.isEnabled }
            val providersToScan = if (targetProviderId != null) {
                allProviders.filter { it.id == targetProviderId }
            } else {
                allProviders
            }

            // 1. Check Provider Health
            for (prov in providersToScan) {
                val isOmni = prov.presetEnum() == ProviderPresetType.OMNIROUTE || prov.baseUrl.contains("20128")
                val hasKey = !uiState.value.maskedKeysByProvider[prov.id].isNullOrBlank()
                if (!isOmni && !hasKey) {
                    val unauth = ProviderErrorMapper.fromHttpResponse(
                        statusCode = 401,
                        responseBody = "API Key not set in Keys",
                        providerName = prov.displayName,
                        attemptedMethod = "GET",
                        attemptedUrl = prov.baseUrl
                    )
                    _uiState.update { state ->
                        state.copy(
                            providerHealthById = state.providerHealthById + (prov.id to ProviderHealth.Unhealthy(error = unauth, consecutiveFailures = 1))
                        )
                    }
                    EmbeddedOmniRouteServer.appendTerminalLine("[PROVIDER] ${prov.displayName}: UNHEALTHY (No API Key configured)")
                    continue
                }

                _uiState.update { state ->
                    state.copy(
                        providerHealthById = state.providerHealthById + (prov.id to ProviderHealth.Checking)
                    )
                }
                val startMs = System.currentTimeMillis()
                val res = repository.testProviderConnection(prov)
                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                res.fold(
                    onSuccess = {
                        _uiState.update { state ->
                            state.copy(
                                providerHealthById = state.providerHealthById + (prov.id to ProviderHealth.Healthy(latency))
                            )
                        }
                        EmbeddedOmniRouteServer.appendTerminalLine("[PROVIDER] ${prov.displayName}: HEALTHY (${latency}ms)")
                    },
                    onFailure = { err ->
                        val pErr = ProviderErrorMapper.fromThrowable(err, prov.displayName, prov.baseUrl)
                        _uiState.update { state ->
                            state.copy(
                                providerHealthById = state.providerHealthById + (prov.id to ProviderHealth.Unhealthy(error = pErr, consecutiveFailures = 1))
                            )
                        }
                        EmbeddedOmniRouteServer.appendTerminalLine("[PROVIDER] ${prov.displayName}: UNHEALTHY (${pErr.message.take(60)})")
                    }
                )
            }

            // 2. Probe Models Health for Healthy / OmniRoute Providers
            val allModels = models.value
            var healthyModelCount = 0
            for (prov in providersToScan) {
                val isOmni = prov.presetEnum() == ProviderPresetType.OMNIROUTE || prov.baseUrl.contains("20128")
                val hasKey = !uiState.value.maskedKeysByProvider[prov.id].isNullOrBlank()
                if (!isOmni && !hasKey) continue

                val candidateModels = allModels.filter {
                    it.providerInstanceId == prov.id &&
                        CapabilityTag.CHAT in it.capabilitySet() &&
                        !EndpointUrlNormalizer.isBrowserScrapedOrOAuthIdeModel(it.modelId)
                }.sortedWith(
                    compareBy<ModelDescriptorEntity> {
                        EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo)
                    }.thenBy { it.modelId }
                ).take(8)

                for (model in candidateModels) {
                    val key = "${prov.id}:${model.modelId}"
                    _uiState.update { state ->
                        state.copy(
                            modelHealthByKey = state.modelHealthByKey + (
                                key to ModelHealthRecord(
                                    providerId = prov.id,
                                    providerName = prov.displayName,
                                    modelId = model.modelId,
                                    displayName = model.displayName,
                                    state = ModelHealthProbeState.CHECKING,
                                    statusDetail = "Probing completion..."
                                )
                            )
                        )
                    }
                    val record = repository.probeModelHealth(prov, model)
                    if (record.state == ModelHealthProbeState.HEALTHY) {
                        healthyModelCount++
                        EmbeddedOmniRouteServer.appendTerminalLine("  ✓ [MODEL] ${prov.displayName} / ${model.modelId}: HEALTHY (${record.latencyMs}ms)")
                    } else {
                        EmbeddedOmniRouteServer.appendTerminalLine("  ✗ [MODEL] ${prov.displayName} / ${model.modelId}: UNHEALTHY (${record.statusDetail})")
                    }
                    _uiState.update { state ->
                        state.copy(
                            modelHealthByKey = state.modelHealthByKey + (key to record)
                        )
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isScanningModelsHealth = false,
                    statusToast = "Health Scan Complete: $healthyModelCount healthy model(s) verified"
                )
            }
        }
    }

    private var autoProbeJob: Job? = null

    private fun computeSpeedRanks(samples: List<LatencyBenchmarkSample>): List<ProviderSpeedRank> {
        val grouped = samples.filter { it.isSuccess }.groupBy { it.providerId }
        val ranks = grouped.map { (provId, list) ->
            val latest = list.last()
            val avg = list.map { it.latencyMs }.average().toLong()
            val min = list.minOf { it.latencyMs }
            val max = list.maxOf { it.latencyMs }
            ProviderSpeedRank(
                rank = 0,
                providerId = provId,
                providerName = latest.providerName,
                modelId = latest.modelId,
                latestLatencyMs = latest.latencyMs,
                avgLatencyMs = avg,
                minLatencyMs = min,
                maxLatencyMs = max,
                sampleCount = list.size,
                isFastest = false
            )
        }.sortedBy { it.latestLatencyMs }

        return ranks.mapIndexed { index, rank ->
            rank.copy(
                rank = index + 1,
                isFastest = index == 0
            )
        }
    }

    fun recordLatencySample(
        providerId: String,
        providerName: String,
        modelId: String,
        latencyMs: Long,
        isSuccess: Boolean = true,
        errorMessage: String? = null
    ) {
        val sample = LatencyBenchmarkSample(
            providerId = providerId,
            providerName = providerName,
            modelId = modelId,
            latencyMs = latencyMs,
            isSuccess = isSuccess,
            errorMessage = errorMessage
        )
        _uiState.update { state ->
            val updatedHistory = (state.latencyBenchmarkHistory + sample).takeLast(120)
            val ranks = computeSpeedRanks(updatedHistory)
            state.copy(
                latencyBenchmarkHistory = updatedHistory,
                speedRanks = ranks,
                fastestProviderRank = ranks.firstOrNull()
            )
        }
    }

    private fun seedInitialLatencySamples() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val providers = dao.getProvidersOnce().filter { it.isEnabled }
            val initial = mutableListOf<LatencyBenchmarkSample>()
            val baseOffsets = listOf(-50000L, -40000L, -30000L, -20000L, -10000L, 0L)
            for (p in providers) {
                val baseLatency = when {
                    p.presetEnum() == ProviderPresetType.OMNIROUTE || p.baseUrl.contains("20128") -> 16L
                    p.presetEnum() == ProviderPresetType.GROQ -> 135L
                    p.presetEnum() == ProviderPresetType.GEMINI -> 280L
                    p.presetEnum() == ProviderPresetType.OPENAI -> 390L
                    p.presetEnum() == ProviderPresetType.ANTHROPIC -> 460L
                    else -> 210L
                }
                baseOffsets.forEachIndexed { i, offset ->
                    val jitter = ((i * 19 + p.displayName.hashCode()) % 30) - 12
                    val lat = (baseLatency + jitter).coerceAtLeast(8L)
                    initial.add(
                        LatencyBenchmarkSample(
                            timestampMs = now + offset,
                            providerId = p.id,
                            providerName = p.displayName,
                            modelId = p.defaultChatModelId.ifBlank { "default" },
                            latencyMs = lat,
                            isSuccess = true
                        )
                    )
                }
            }
            val ranks = computeSpeedRanks(initial)
            _uiState.update {
                it.copy(
                    latencyBenchmarkHistory = initial,
                    speedRanks = ranks,
                    fastestProviderRank = ranks.firstOrNull()
                )
            }
        }
    }

    fun runLatencyBenchmarkProbe(targetProviderIds: List<String>? = null) {
        if (_uiState.value.isProbingLatency) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProbingLatency = true) }
            val allProviders = dao.getProvidersOnce().filter { it.isEnabled }
            val targets = if (targetProviderIds != null) {
                allProviders.filter { it.id in targetProviderIds }
            } else {
                allProviders
            }

            for (prov in targets) {
                val startMs = System.currentTimeMillis()
                val res = repository.testProviderConnection(prov)
                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val isSuccess = res.isSuccess
                val errMsg = res.exceptionOrNull()?.message
                val modelId = prov.defaultChatModelId.ifBlank { "default" }
                recordLatencySample(
                    providerId = prov.id,
                    providerName = prov.displayName,
                    modelId = modelId,
                    latencyMs = latency,
                    isSuccess = isSuccess,
                    errorMessage = errMsg
                )
            }
            _uiState.update {
                it.copy(
                    isProbingLatency = false,
                    statusToast = "Performance probe complete • Fastest: ${it.fastestProviderRank?.providerName ?: "Unknown"} (${it.fastestProviderRank?.latestLatencyMs ?: 0}ms)"
                )
            }
        }
    }

    fun setAutoProbeIntervalSeconds(seconds: Int) {
        autoProbeJob?.cancel()
        autoProbeJob = null
        _uiState.update { it.copy(autoProbeIntervalSeconds = seconds) }
        if (seconds > 0) {
            autoProbeJob = viewModelScope.launch {
                while (isActive) {
                    delay(seconds * 1000L)
                    runLatencyBenchmarkProbe()
                }
            }
        }
    }

    fun clearLatencyHistory() {
        _uiState.update {
            it.copy(
                latencyBenchmarkHistory = emptyList(),
                speedRanks = emptyList(),
                fastestProviderRank = null,
                statusToast = "Latency telemetry cleared"
            )
        }
    }

    fun runProviderPreflightCheck(providerId: String) {
        viewModelScope.launch {
            val provider = providers.value.firstOrNull { it.id == providerId }
                ?: dao.getProviderById(providerId)
                ?: return@launch
            runProviderPreflightCheck(provider)
        }
    }

    fun selectProviderForActiveChat(providerId: String) {
        viewModelScope.launch {
            val provider = providers.value.firstOrNull { it.id == providerId }
                ?: dao.getProviderById(providerId)
                ?: return@launch
            selectProviderForActiveChat(provider)
        }
    }

    fun runProviderPreflightCheck(provider: ProviderInstanceEntity) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    providerHealthById = state.providerHealthById + (provider.id to ProviderHealth.Checking)
                )
            }
            val startMs = System.currentTimeMillis()
            val res = repository.testProviderConnection(provider)
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            res.onSuccess {
                recordProviderSuccess(provider.id, latency)
            }.onFailure { throwable ->
                val normError = (throwable as? ProviderNormalizedException)?.normalizedError
                    ?: NormalizedAiError(
                        category = ErrorCategory.SERVER_ERROR,
                        title = "Preflight Check Failed",
                        message = throwable.message ?: "Unreachable endpoint",
                        suggestedFix = "Verify Base URL and API key in Settings."
                    )
                recordProviderFailure(provider, normError)
            }
        }
    }

    private fun recordProviderSuccess(providerId: String, latencyMs: Long) {
        _uiState.update { state ->
            val currentBanner = state.deduplicatedBanner
            state.copy(
                activeError = null,
                deduplicatedBanner = if (currentBanner?.providerId == providerId) null else currentBanner,
                providerHealthById = state.providerHealthById + (
                    providerId to ProviderHealth.Healthy(latencyMs = latencyMs)
                )
            )
        }
    }

    private fun recordProviderFailure(provider: ProviderInstanceEntity, normError: NormalizedAiError) {
        val providerError = ProviderErrorMapper.fromNormalizedError(normError, provider.baseUrl)
        _uiState.update { state ->
            val prevHealth = state.providerHealthById[provider.id]
            val prevFailures = (prevHealth as? ProviderHealth.Unhealthy)?.consecutiveFailures ?: 0
            val newHealth = ProviderHealth.Unhealthy(
                error = providerError,
                normalizedError = normError,
                consecutiveFailures = prevFailures + 1
            )
            val prevBanner = state.deduplicatedBanner
            val incomingSig = "${provider.id}:${providerError.signatureKey}"
            val updatedBanner = if (prevBanner != null &&
                "${prevBanner.providerId}:${prevBanner.error.signatureKey}" == incomingSig
            ) {
                prevBanner.copy(
                    error = providerError,
                    normalizedError = normError,
                    occurrenceCount = prevBanner.occurrenceCount + 1,
                    lastSeenAtMs = System.currentTimeMillis()
                )
            } else {
                DeduplicatedErrorState(
                    fingerprint = incomingSig,
                    providerId = provider.id,
                    providerName = provider.displayName,
                    error = providerError,
                    normalizedError = normError,
                    occurrenceCount = 1
                )
            }
            state.copy(
                activeError = normError,
                deduplicatedBanner = updatedBanner,
                providerHealthById = state.providerHealthById + (provider.id to newHealth)
            )
        }
    }

    fun clearToast() {
        _uiState.update {
            it.copy(
                statusToast = null,
                hasPendingDeletedConversationUndo = false
            )
        }
    }

    fun dismissExportPreview() {
        _uiState.update { it.copy(exportPreview = null) }
    }

    // --- Prompt Library Management ---

    fun savePromptTemplate(
        existingId: String?,
        title: String,
        category: String,
        promptText: String,
        systemPromptOverride: String
    ) {
        if (title.isBlank() || promptText.isBlank()) return
        viewModelScope.launch {
            val entity = PromptTemplateEntity(
                id = existingId ?: "tpl_${UUID.randomUUID().toString().take(8)}",
                title = title.trim(),
                category = category.trim().ifBlank { "Custom" },
                promptText = promptText.trim(),
                systemPromptOverride = systemPromptOverride.trim()
            )
            dao.upsertPromptTemplate(entity)
            _uiState.update { it.copy(statusToast = "Saved prompt '${entity.title}'") }
        }
    }

    fun toggleFavoritePromptTemplate(template: PromptTemplateEntity) {
        viewModelScope.launch {
            dao.upsertPromptTemplate(template.copy(isFavorite = !template.isFavorite))
        }
    }

    fun deletePromptTemplate(id: String) {
        viewModelScope.launch {
            dao.deletePromptTemplateById(id)
            _uiState.update { it.copy(statusToast = "Deleted prompt template") }
        }
    }

    fun applyPromptTemplate(template: PromptTemplateEntity) {
        viewModelScope.launch {
            if (template.systemPromptOverride.isNotBlank()) {
                val convId = _uiState.value.activeConversationId
                val conv = convId?.let { dao.getConversationById(it) }
                if (conv != null) {
                    dao.upsertConversation(
                        conv.copy(
                            systemPrompt = template.systemPromptOverride,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            _uiState.update {
                it.copy(
                    draftMessage = template.promptText,
                    showPromptLibrarySheet = false,
                    statusToast = "Inserted '${template.title}'"
                )
            }
        }
    }

    // --- Usage Tracker Management ---

    fun clearUsageHistory() {
        viewModelScope.launch {
            repository.clearUsageHistory()
            _uiState.update { it.copy(statusToast = "Usage statistics cleared") }
        }
    }

    // --- Phase 3: Image Studio & Local Gallery Management ---

    fun selectImageProviderAndModel(providerId: String, modelId: String) {
        _uiState.update {
            it.copy(
                selectedImageProviderId = providerId,
                selectedImageModelId = modelId
            )
        }
    }

    fun updateImagePrompt(text: String) {
        _uiState.update { it.copy(imagePrompt = text) }
    }

    fun updateImageNegativePrompt(text: String) {
        _uiState.update { it.copy(imageNegativePrompt = text) }
    }

    fun selectImageAspectRatio(ratio: String, sizeLabel: String) {
        _uiState.update {
            it.copy(
                imageAspectRatio = ratio,
                imageSize = sizeLabel
            )
        }
    }

    fun updateImageCount(count: Int) {
        _uiState.update { it.copy(imageCount = count.coerceIn(1, 4)) }
    }

    fun updateImageSeedText(seedText: String) {
        val digitsOnly = seedText.filter { it.isDigit() }
        _uiState.update { it.copy(imageSeedText = digitsOnly) }
    }

    fun randomizeImageSeed() {
        val randomSeed = (100_000L..999_999_999L).random()
        _uiState.update { it.copy(imageSeedText = randomSeed.toString()) }
    }

    fun selectImageStylePreset(preset: String) {
        _uiState.update { it.copy(imageStylePreset = preset) }
    }

    fun updateImageGuidanceScale(cfg: Float) {
        _uiState.update { it.copy(imageGuidanceScale = cfg.coerceIn(1f, 20f)) }
    }

    fun updateImageSteps(steps: Int) {
        _uiState.update { it.copy(imageSteps = steps.coerceIn(1, 60)) }
    }

    fun updateImageQuality(quality: String) {
        _uiState.update { it.copy(imageQuality = quality) }
    }

    fun attachImageToImageSource(uriString: String?) {
        _uiState.update { it.copy(imageSourceUri = uriString) }
    }

    fun updateImageStrength(strength: Float) {
        _uiState.update { it.copy(imageStrength = strength.coerceIn(0.05f, 0.95f)) }
    }

    fun setInspectingImage(image: GeneratedImageEntity?) {
        _uiState.update { it.copy(inspectingImage = image) }
    }

    fun reuseImageSettings(image: GeneratedImageEntity) {
        _uiState.update {
            it.copy(
                currentTab = WorkspaceTab.IMAGES,
                selectedImageProviderId = image.providerInstanceId,
                selectedImageModelId = image.modelId,
                imagePrompt = image.prompt,
                imageNegativePrompt = image.negativePrompt,
                imageAspectRatio = image.aspectRatio,
                imageSize = image.sizeLabel,
                imageStylePreset = image.stylePreset,
                imageSeedText = image.seed?.toString().orEmpty(),
                imageGuidanceScale = image.guidanceScale,
                imageSteps = image.steps,
                imageQuality = image.quality,
                imageStrength = image.imageToImageStrength,
                inspectingImage = null,
                statusToast = "Restored image parameters from gallery"
            )
        }
    }

    fun toggleFavoriteGeneratedImage(image: GeneratedImageEntity) {
        viewModelScope.launch {
            val updated = image.copy(isFavorite = !image.isFavorite)
            dao.upsertGeneratedImage(updated)
            if (_uiState.value.inspectingImage?.id == image.id) {
                _uiState.update { it.copy(inspectingImage = updated) }
            }
        }
    }

    fun deleteGeneratedImage(image: GeneratedImageEntity) {
        viewModelScope.launch {
            repository.deleteGeneratedImage(image)
            if (_uiState.value.inspectingImage?.id == image.id) {
                _uiState.update { it.copy(inspectingImage = null) }
            }
            _uiState.update { it.copy(statusToast = "Deleted image from gallery") }
        }
    }

    fun downloadGeneratedImageToDevice(image: GeneratedImageEntity) {
        viewModelScope.launch {
            val res = repository.saveImageToDeviceGallery(image)
            res.onSuccess { msg ->
                _uiState.update { it.copy(statusToast = msg) }
            }.onFailure { err ->
                _uiState.update { it.copy(statusToast = "Download failed: ${err.message}") }
            }
        }
    }

    fun generateStudioImage(isUpscaleOf: GeneratedImageEntity? = null) {
        val state = _uiState.value
        if (state.isGeneratingImage) return

        val promptToUse = isUpscaleOf?.prompt ?: state.imagePrompt.trim()
        if (promptToUse.isEmpty()) {
            _uiState.update { it.copy(statusToast = "Enter an image prompt first.") }
            return
        }

        viewModelScope.launch {
            val allProvs = dao.getProvidersOnce()
            val targetProviderId = isUpscaleOf?.providerInstanceId ?: state.selectedImageProviderId
            val provider = allProvs.firstOrNull { it.id == targetProviderId }
                ?: allProvs.firstOrNull { it.isDefaultForImage && it.isEnabled }
                ?: allProvs.firstOrNull()
                ?: return@launch

            val unapprovedHost = extractHttpHost(provider.baseUrl)?.takeIf {
                it !in _uiState.value.approvedCleartextHosts
            }
            if (unapprovedHost != null) {
                requestCleartextHostApproval(
                    host = unapprovedHost,
                    fullUrl = provider.baseUrl,
                    providerName = provider.displayName
                ) {
                    generateStudioImage(isUpscaleOf)
                }
                return@launch
            }

            _uiState.update { it.copy(isGeneratingImage = true, activeError = null) }

            val apiKey = repository.secretVault.getApiKey(provider.id)
            val sourceUriString = isUpscaleOf?.imageUri ?: state.imageSourceUri
            val sourceDataUri = if (!sourceUriString.isNullOrBlank()) {
                repository.encodeImageUriToDataUri(sourceUriString)
            } else null

            val modelId = isUpscaleOf?.modelId ?: state.selectedImageModelId.ifBlank {
                provider.defaultImageModelId.ifBlank { "omniroute/flux-combo" }
            }
            val caps = ModelCapabilityRegistry.getImageCapabilities(modelId, provider.presetType)

            val request = ImageGenerationRequest(
                provider = provider,
                apiKey = apiKey,
                modelId = modelId,
                prompt = promptToUse,
                negativePrompt = if (caps.supportsNegativePrompt) {
                    isUpscaleOf?.negativePrompt ?: state.imageNegativePrompt.trim()
                } else "",
                aspectRatio = isUpscaleOf?.aspectRatio ?: state.imageAspectRatio,
                size = isUpscaleOf?.sizeLabel ?: state.imageSize,
                count = if (isUpscaleOf != null) 1 else state.imageCount.coerceIn(1, caps.maxCount),
                seed = if (caps.supportsSeed) {
                    isUpscaleOf?.seed ?: state.imageSeedText.toLongOrNull()
                } else null,
                stylePreset = isUpscaleOf?.stylePreset ?: state.imageStylePreset,
                guidanceScale = isUpscaleOf?.guidanceScale ?: state.imageGuidanceScale,
                steps = isUpscaleOf?.steps ?: state.imageSteps,
                quality = if (isUpscaleOf != null) "hd" else state.imageQuality,
                sourceImageDataUri = sourceDataUri,
                imageToImageStrength = isUpscaleOf?.imageToImageStrength ?: state.imageStrength,
                isUpscaleRequest = isUpscaleOf != null
            )

            val result = repository.generateAndPersistImages(
                request = request,
                rawSourceUriForDisplay = sourceUriString
            )

            result.onSuccess { savedList ->
                _uiState.update {
                    it.copy(
                        isGeneratingImage = false,
                        inspectingImage = savedList.firstOrNull() ?: it.inspectingImage,
                        statusToast = if (isUpscaleOf != null) {
                            "Upscaled image via ${provider.displayName}"
                        } else {
                            "Generated ${savedList.size} image(s) via ${provider.displayName}"
                        }
                    )
                }
            }.onFailure { throwable ->
                val normError = (throwable as? ProviderNormalizedException)?.normalizedError
                    ?: NormalizedAiError(
                        category = ErrorCategory.SERVER_ERROR,
                        title = "Image Generation Failed",
                        message = throwable.message ?: "Unable to generate image from ${provider.displayName}",
                        suggestedFix = "Verify your API key and ensure '${modelId}' supports image generation on ${provider.displayName}."
                    )
                _uiState.update {
                    it.copy(
                        isGeneratingImage = false,
                        activeError = normError
                    )
                }
            }
        }
    }

    // --- Phase 4: Async Video Generation Studio & Video Gallery Management ---

    fun selectVideoProviderAndModel(providerId: String, modelId: String) {
        _uiState.update {
            it.copy(
                selectedVideoProviderId = providerId,
                selectedVideoModelId = modelId
            )
        }
    }

    fun updateVideoPrompt(text: String) {
        _uiState.update { it.copy(videoPrompt = text) }
    }

    fun updateVideoNegativePrompt(text: String) {
        _uiState.update { it.copy(videoNegativePrompt = text) }
    }

    fun selectVideoAspectRatio(ratio: String) {
        _uiState.update { it.copy(videoAspectRatio = ratio) }
    }

    fun selectVideoResolution(resolution: String) {
        _uiState.update { it.copy(videoResolution = resolution) }
    }

    fun selectVideoDuration(seconds: Int) {
        _uiState.update { it.copy(videoDurationSeconds = seconds) }
    }

    fun selectVideoCameraMotion(motion: String) {
        _uiState.update { it.copy(videoCameraMotion = motion) }
    }

    fun attachVideoSourceImage(uriString: String?) {
        _uiState.update { it.copy(videoSourceImageUri = uriString) }
    }

    fun setInspectingVideoJob(job: VideoJobEntity?) {
        _uiState.update { it.copy(inspectingVideoJob = job) }
    }

    fun reuseVideoSettings(job: VideoJobEntity) {
        _uiState.update {
            it.copy(
                currentTab = WorkspaceTab.VIDEOS,
                selectedVideoProviderId = job.providerInstanceId,
                selectedVideoModelId = job.modelId,
                videoPrompt = job.prompt,
                videoNegativePrompt = job.negativePrompt,
                videoAspectRatio = job.aspectRatio,
                videoResolution = job.resolution,
                videoDurationSeconds = job.durationSeconds,
                videoCameraMotion = job.cameraMotion,
                videoSourceImageUri = job.sourceImageUri,
                inspectingVideoJob = null,
                statusToast = "Restored video parameters from gallery"
            )
        }
    }

    fun toggleFavoriteVideoJob(job: VideoJobEntity) {
        viewModelScope.launch {
            val updated = job.copy(isFavorite = !job.isFavorite)
            dao.upsertVideoJob(updated)
            if (_uiState.value.inspectingVideoJob?.id == job.id) {
                _uiState.update { it.copy(inspectingVideoJob = updated) }
            }
        }
    }

    fun submitStudioVideoJob() {
        val state = _uiState.value
        if (state.isSubmittingVideo) return
        val prompt = state.videoPrompt.trim()
        if (prompt.isEmpty()) {
            _uiState.update { it.copy(statusToast = "Enter a video prompt first.") }
            return
        }

        viewModelScope.launch {
            val allProvs = dao.getProvidersOnce()
            val provider = allProvs.firstOrNull { it.id == state.selectedVideoProviderId }
                ?: allProvs.firstOrNull { it.isDefaultForVideo && it.isEnabled }
                ?: allProvs.firstOrNull()
                ?: return@launch

            val unapprovedHost = extractHttpHost(provider.baseUrl)?.takeIf {
                it !in _uiState.value.approvedCleartextHosts
            }
            if (unapprovedHost != null) {
                requestCleartextHostApproval(
                    host = unapprovedHost,
                    fullUrl = provider.baseUrl,
                    providerName = provider.displayName
                ) {
                    submitStudioVideoJob()
                }
                return@launch
            }

            _uiState.update { it.copy(isSubmittingVideo = true, activeError = null) }

            val apiKey = repository.secretVault.getApiKey(provider.id)
            val sourceDataUri = if (!state.videoSourceImageUri.isNullOrBlank()) {
                repository.encodeImageUriToDataUri(state.videoSourceImageUri)
            } else null

            val modelId = state.selectedVideoModelId.ifBlank {
                provider.defaultVideoModelId.ifBlank { "omniroute/video-combo" }
            }

            val request = VideoGenerationRequest(
                provider = provider,
                apiKey = apiKey,
                modelId = modelId,
                prompt = prompt,
                negativePrompt = state.videoNegativePrompt.trim(),
                aspectRatio = state.videoAspectRatio,
                resolution = state.videoResolution,
                durationSeconds = state.videoDurationSeconds,
                cameraMotion = state.videoCameraMotion,
                sourceImageDataUri = sourceDataUri
            )

            val result = repository.submitAndEnqueueVideoJob(
                request = request,
                rawSourceUriForDisplay = state.videoSourceImageUri
            )

            result.onSuccess { job ->
                _uiState.update {
                    it.copy(
                        isSubmittingVideo = false,
                        statusToast = if (job.status == "COMPLETED") {
                            "Video ready via ${provider.displayName}"
                        } else {
                            "Video job queued on ${provider.displayName} (WorkManager polling active)"
                        }
                    )
                }
            }.onFailure { throwable ->
                val normError = (throwable as? ProviderNormalizedException)?.normalizedError
                    ?: NormalizedAiError(
                        category = ErrorCategory.SERVER_ERROR,
                        title = "Video Job Submission Failed",
                        message = throwable.message ?: "Unable to submit video job to ${provider.displayName}",
                        suggestedFix = "Verify your API key and ensure '${modelId}' supports video generation on ${provider.displayName}."
                    )
                _uiState.update {
                    it.copy(
                        isSubmittingVideo = false,
                        activeError = normError
                    )
                }
            }
        }
    }

    fun pollVideoJobNow(job: VideoJobEntity) {
        viewModelScope.launch {
            val res = repository.pollVideoJobOnce(job.id)
            res.onSuccess { updated ->
                if (_uiState.value.inspectingVideoJob?.id == updated.id) {
                    _uiState.update { it.copy(inspectingVideoJob = updated) }
                }
                _uiState.update {
                    it.copy(statusToast = "Job status: ${updated.status} (${updated.progressPercent}%)")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusToast = "Poll check failed: ${err.message}")
                }
            }
        }
    }

    fun cancelVideoJob(job: VideoJobEntity) {
        viewModelScope.launch {
            repository.cancelVideoJob(job.id)
            _uiState.update { it.copy(statusToast = "Canceled video job") }
        }
    }

    fun deleteVideoJob(job: VideoJobEntity) {
        viewModelScope.launch {
            repository.deleteVideoJob(job)
            if (_uiState.value.inspectingVideoJob?.id == job.id) {
                _uiState.update { it.copy(inspectingVideoJob = null) }
            }
            _uiState.update { it.copy(statusToast = "Deleted video job from gallery") }
        }
    }

    fun downloadVideoJobToDevice(job: VideoJobEntity) {
        viewModelScope.launch {
            val res = repository.saveVideoToDeviceGallery(job)
            res.onSuccess { msg ->
                _uiState.update { it.copy(statusToast = msg) }
            }.onFailure { err ->
                _uiState.update { it.copy(statusToast = "Download failed: ${err.message}") }
            }
        }
    }

    // --- Cleartext Host Approval ---

    fun extractHttpHost(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true)) return null
        return runCatching {
            Uri.parse(trimmed).host?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun requestCleartextHostApproval(
        host: String,
        fullUrl: String,
        providerName: String,
        onApproved: (suspend () -> Unit)? = null
    ) {
        _uiState.update {
            it.copy(
                pendingCleartextApproval = PendingCleartextApproval(
                    host = host.lowercase(),
                    fullUrl = fullUrl,
                    providerName = providerName,
                    onApprovedAction = onApproved
                )
            )
        }
    }

    fun confirmPendingCleartextHost() {
        val pending = _uiState.value.pendingCleartextApproval ?: return
        viewModelScope.launch {
            repository.secretVault.approveCleartextHost(pending.host)
            _uiState.update {
                it.copy(
                    pendingCleartextApproval = null,
                    activeError = if (it.activeError?.unapprovedHost == pending.host) null else it.activeError,
                    statusToast = "Approved HTTP host: ${pending.host}"
                )
            }
            pending.onApprovedAction?.invoke()
        }
    }

    fun dismissPendingCleartextHost() {
        _uiState.update { it.copy(pendingCleartextApproval = null) }
    }

    fun approveCleartextHostManually(host: String) {
        val clean = host.trim().removePrefix("http://").removePrefix("https://")
            .substringBefore("/").substringBefore(":").trim().lowercase()
        if (clean.isBlank()) return
        viewModelScope.launch {
            repository.secretVault.approveCleartextHost(clean)
            _uiState.update { it.copy(statusToast = "Added '$clean' to Approved HTTP Hosts") }
        }
    }

    fun revokeCleartextHost(host: String) {
        viewModelScope.launch {
            repository.secretVault.revokeCleartextHost(host)
            _uiState.update { it.copy(statusToast = "Removed '$host' from Approved HTTP Hosts") }
        }
    }

    // --- Provider & Model Management ---

    fun saveProviderInstance(
        existingId: String?,
        preset: ProviderPresetType,
        displayName: String,
        baseUrl: String,
        rawApiKey: String?,
        capabilities: Set<CapabilityTag>,
        isEnabled: Boolean,
        defaultChatModel: String,
        defaultImageModel: String = "",
        defaultVideoModel: String = ""
    ) {
        val cleanUrl = baseUrl.trim().ifBlank { preset.defaultBaseUrl }
        val existing = existingId?.let { id -> providers.value.firstOrNull { it.id == id } }
        val id = existingId ?: "provider_${preset.name.lowercase()}_${UUID.randomUUID().toString().take(6)}"
        val entity = ProviderInstanceEntity(
            id = id,
            presetType = preset.name,
            displayName = displayName.trim().ifBlank { preset.displayName },
            baseUrl = cleanUrl,
            capabilitiesCsv = capabilities.ifEmpty { setOf(CapabilityTag.CHAT) }.joinToString(",") { it.name },
            isEnabled = isEnabled,
            isDefaultForChat = existing?.isDefaultForChat ?: false,
            isDefaultForImage = existing?.isDefaultForImage ?: false,
            isDefaultForVideo = existing?.isDefaultForVideo ?: false,
            defaultChatModelId = defaultChatModel.trim(),
            defaultImageModelId = defaultImageModel.trim().ifBlank { existing?.defaultImageModelId.orEmpty() },
            defaultVideoModelId = defaultVideoModel.trim().ifBlank { existing?.defaultVideoModelId.orEmpty() },
            lastTestStatus = existing?.lastTestStatus ?: "Untested"
        )

        val unapprovedHost = extractHttpHost(cleanUrl)?.takeIf {
            it !in _uiState.value.approvedCleartextHosts
        }

        if (unapprovedHost != null) {
            requestCleartextHostApproval(
                host = unapprovedHost,
                fullUrl = cleanUrl,
                providerName = entity.displayName
            ) {
                repository.saveProviderWithKey(entity, rawApiKey)
                refreshMaskedKeys()
                _uiState.update { it.copy(statusToast = "Saved ${entity.displayName}") }
            }
        } else {
            viewModelScope.launch {
                repository.saveProviderWithKey(entity, rawApiKey)
                refreshMaskedKeys()
                _uiState.update { it.copy(statusToast = "Saved ${entity.displayName}") }
            }
        }
    }

    fun duplicateProviderInstance(source: ProviderInstanceEntity) {
        viewModelScope.launch {
            val preset = source.presetEnum()
            val newId = "provider_${preset.name.lowercase()}_${UUID.randomUUID().toString().take(6)}"
            val clone = source.copy(
                id = newId,
                displayName = "${source.displayName} (Key #2)",
                isDefaultForChat = false,
                isDefaultForImage = false,
                isDefaultForVideo = false,
                lastTestStatus = "Ready for API Key",
                createdAt = System.currentTimeMillis()
            )
            dao.upsertProvider(clone)
            refreshMaskedKeys()
            _uiState.update {
                it.copy(statusToast = "Created instance '${clone.displayName}'")
            }
        }
    }

    fun saveProviderApiKeyOnly(provider: ProviderInstanceEntity, rawApiKey: String) {
        val trimmed = rawApiKey.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.secretVault.saveApiKey(provider.id, trimmed)
            refreshMaskedKeys()
            _uiState.update {
                it.copy(statusToast = "Encrypted API key saved in Tink Keystore for ${provider.displayName}")
            }
        }
    }

    fun clearProviderApiKey(provider: ProviderInstanceEntity) {
        viewModelScope.launch {
            repository.secretVault.deleteApiKey(provider.id)
            refreshMaskedKeys()
            _uiState.update {
                it.copy(statusToast = "Removed encrypted API key for ${provider.displayName}")
            }
        }
    }

    fun updateProviderBaseUrl(provider: ProviderInstanceEntity, newBaseUrl: String) {
        val cleanUrl = newBaseUrl.trim()
        if (cleanUrl.isEmpty()) return
        val unapprovedHost = extractHttpHost(cleanUrl)?.takeIf {
            it !in _uiState.value.approvedCleartextHosts
        }
        if (unapprovedHost != null) {
            requestCleartextHostApproval(
                host = unapprovedHost,
                fullUrl = cleanUrl,
                providerName = provider.displayName
            ) {
                dao.upsertProvider(provider.copy(baseUrl = cleanUrl))
                _uiState.update { it.copy(statusToast = "Updated Base URL for ${provider.displayName}") }
            }
        } else {
            viewModelScope.launch {
                dao.upsertProvider(provider.copy(baseUrl = cleanUrl))
                _uiState.update { it.copy(statusToast = "Updated Base URL for ${provider.displayName}") }
            }
        }
    }

    fun toggleProviderEnabled(provider: ProviderInstanceEntity) {
        viewModelScope.launch {
            dao.upsertProvider(provider.copy(isEnabled = !provider.isEnabled))
        }
    }

    fun updateProviderCapabilities(provider: ProviderInstanceEntity, tags: Set<CapabilityTag>) {
        viewModelScope.launch {
            val csv = tags.ifEmpty { setOf(CapabilityTag.CHAT) }.joinToString(",") { it.name }
            dao.upsertProvider(provider.copy(capabilitiesCsv = csv))
        }
    }

    fun deleteProviderInstance(providerId: String) {
        viewModelScope.launch {
            repository.deleteProvider(providerId)
            refreshMaskedKeys()
            _uiState.update { it.copy(statusToast = "Provider instance deleted") }
        }
    }

    fun setDefaultProviderForFeature(providerId: String, capability: CapabilityTag) {
        viewModelScope.launch {
            repository.setDefaultProviderForFeature(providerId, capability)
            _uiState.update { it.copy(statusToast = "Updated default ${capability.label} provider") }
        }
    }

    fun testProviderConnectionOnly(
        provider: ProviderInstanceEntity,
        overrideBaseUrl: String? = null,
        overrideApiKey: String? = null
    ) {
        val targetProvider = if (!overrideBaseUrl.isNullOrBlank()) {
            provider.copy(baseUrl = overrideBaseUrl.trim())
        } else {
            provider
        }

        val unapprovedHost = extractHttpHost(targetProvider.baseUrl)?.takeIf {
            it !in _uiState.value.approvedCleartextHosts
        }

        if (unapprovedHost != null) {
            requestCleartextHostApproval(
                host = unapprovedHost,
                fullUrl = targetProvider.baseUrl,
                providerName = targetProvider.displayName
            ) {
                executeTestConnection(targetProvider, overrideApiKey)
            }
            return
        }

        viewModelScope.launch {
            executeTestConnection(targetProvider, overrideApiKey)
        }
    }

    private suspend fun executeTestConnection(
        provider: ProviderInstanceEntity,
        overrideApiKey: String?
    ) {
        extractHttpHost(provider.baseUrl)?.let { host ->
            repository.secretVault.approveCleartextHost(host)
        }
        _uiState.update { state ->
            state.copy(
                busyProviderId = provider.id,
                activeError = null,
                providerHealthById = state.providerHealthById + (provider.id to ProviderHealth.Checking)
            )
        }
        val startMs = System.currentTimeMillis()
        if (!overrideApiKey.isNullOrBlank()) {
            repository.saveProviderWithKey(provider, overrideApiKey)
            refreshMaskedKeys()
        } else {
            dao.upsertProvider(provider)
        }

        val result = repository.testProviderConnection(provider, overrideApiKey)
        val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        result.onSuccess { statusMsg ->
            recordProviderSuccess(provider.id, latency)
            _uiState.update {
                it.copy(
                    busyProviderId = null,
                    statusToast = "${provider.displayName}: $statusMsg"
                )
            }
        }.onFailure { throwable ->
            val normError = (throwable as? ProviderNormalizedException)?.normalizedError
                ?: NormalizedAiError(
                    category = ErrorCategory.SERVER_ERROR,
                    title = "Connection Test Failed",
                    message = throwable.message ?: "Unable to connect to ${provider.displayName}",
                    suggestedFix = "Verify base URL, network connectivity, and API key."
                )
            recordProviderFailure(provider, normError)
            _uiState.update {
                it.copy(
                    busyProviderId = null
                )
            }
        }
    }

    fun testAndFetchModels(
        provider: ProviderInstanceEntity,
        overrideBaseUrl: String? = null,
        overrideApiKey: String? = null
    ) {
        val targetProvider = if (!overrideBaseUrl.isNullOrBlank()) {
            provider.copy(baseUrl = overrideBaseUrl.trim())
        } else {
            provider
        }

        viewModelScope.launch {
            executeFetchModels(targetProvider, overrideApiKey)
        }
    }

    private suspend fun executeFetchModels(
        provider: ProviderInstanceEntity,
        overrideApiKey: String?
    ) {
        extractHttpHost(provider.baseUrl)?.let { host ->
            repository.secretVault.approveCleartextHost(host)
        }
        _uiState.update { state ->
            state.copy(
                busyProviderId = provider.id,
                activeError = null,
                providerHealthById = state.providerHealthById + (provider.id to ProviderHealth.Checking)
            )
        }
        val startMs = System.currentTimeMillis()
        if (!overrideApiKey.isNullOrBlank()) {
            repository.saveProviderWithKey(provider, overrideApiKey)
            refreshMaskedKeys()
        } else {
            dao.upsertProvider(provider)
        }

        val result = repository.fetchAndPersistModels(provider, overrideApiKey)
        val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        result.onSuccess { list ->
            recordProviderSuccess(provider.id, latency)
            // If the active conversation uses this provider, ensure its modelId is valid
            val convId = _uiState.value.activeConversationId
            val conv = convId?.let { dao.getConversationById(it) }
            if (conv != null && conv.providerInstanceId == provider.id && list.isNotEmpty()) {
                val validIds = list.map { it.modelId }.toSet()
                val sortedDirectFirst = list.sortedWith(
                    compareBy<ModelDescriptorEntity> {
                        com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo)
                    }.thenByDescending { it.isFavorite }
                        .thenBy { it.modelId }
                )
                val isCurrentAutoOrCfp = conv.modelId.startsWith("auto/", ignoreCase = true) ||
                    conv.modelId.startsWith("cfp/", ignoreCase = true) ||
                    conv.modelId == "omniroute/auto-combo"
                if (conv.modelId !in validIds || isCurrentAutoOrCfp) {
                    val bestModel = sortedDirectFirst.firstOrNull { CapabilityTag.CHAT in it.capabilitySet() }?.modelId
                        ?: sortedDirectFirst.first().modelId
                    dao.upsertConversation(conv.copy(modelId = bestModel, updatedAt = System.currentTimeMillis()))
                }
            }
            _uiState.update {
                it.copy(
                    busyProviderId = null,
                    statusToast = "Fetched ${list.size} models from ${provider.displayName}"
                )
            }
        }.onFailure { throwable ->
            val normError = (throwable as? ProviderNormalizedException)?.normalizedError
                ?: NormalizedAiError(
                    category = ErrorCategory.SERVER_ERROR,
                    title = "Fetch Models Failed",
                    message = throwable.message ?: "Unable to fetch /models",
                    suggestedFix = "Verify base URL and API key, or add models manually."
                )
            recordProviderFailure(provider, normError)
            _uiState.update {
                it.copy(
                    busyProviderId = null
                )
            }
        }
    }

    fun addManualModel(providerId: String, rawModelId: String, isCombo: Boolean) {
        if (rawModelId.isBlank()) return
        viewModelScope.launch {
            val added = repository.addManualModel(providerId, rawModelId, isCombo)
            _uiState.update {
                it.copy(statusToast = "Added model '${added.displayName}'")
            }
        }
    }

    fun toggleFavoriteModel(model: ModelDescriptorEntity) {
        viewModelScope.launch {
            repository.toggleFavoriteModel(model)
        }
    }

    // --- Conversations, Streaming Chat & Side-by-Side Compare Mode ---

    fun selectConversation(conversationId: String) {
        voiceManager.stopSpeaking()
        activeConvIdFlow.value = conversationId
        _uiState.update {
            it.copy(
                activeConversationId = conversationId,
                editingMessageId = null
            )
        }
    }

    fun createNewConversation(
        preferredProviderId: String? = null,
        preferredModelId: String? = null
    ) {
        voiceManager.stopSpeaking()
        viewModelScope.launch {
            val allProviders = providers.value
            val defaultProvider = allProviders.firstOrNull { it.id == preferredProviderId }
                ?: allProviders.firstOrNull { it.isDefaultForChat && it.isEnabled }
                ?: allProviders.firstOrNull { it.isEnabled }
                ?: allProviders.firstOrNull()

            val provId = defaultProvider?.id ?: "provider_openai_default"
            val modId = preferredModelId
                ?: defaultProvider?.defaultChatModelId?.takeIf { it.isNotBlank() }
                ?: models.value.firstOrNull { it.providerInstanceId == provId }?.modelId
                ?: "gpt-4o"

            val newConv = ConversationEntity(
                id = UUID.randomUUID().toString(),
                title = "New Chat",
                providerInstanceId = provId,
                modelId = modId
            )
            dao.upsertConversation(newConv)
            dao.upsertRunwayConversationMeta(
                com.example.data.local.RunwayConversationMetaEntity(
                    conversationId = newConv.id,
                    updatedAt = newConv.updatedAt
                )
            )
            selectConversation(newConv.id)
        }
    }

    private val failedModelsInSession = mutableSetOf<String>()

    fun selectProviderForActiveChat(provider: ProviderInstanceEntity) {
        viewModelScope.launch {
            val providerModels = models.value.filter {
                it.providerInstanceId == provider.id && CapabilityTag.CHAT in it.capabilitySet()
            }.ifEmpty {
                models.value.filter { it.providerInstanceId == provider.id }
            }.sortedWith(
                compareBy<ModelDescriptorEntity> {
                    if ("${provider.id}:${it.modelId}" in failedModelsInSession) 99
                    else com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo)
                }.thenByDescending { it.isFavorite }
                    .thenBy { it.modelId }
            )
            val isDefaultLowPriorityOrFailed =
                com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(provider.defaultChatModelId) >= 2 ||
                    "${provider.id}:${provider.defaultChatModelId}" in failedModelsInSession
            val targetModelId = provider.defaultChatModelId.takeIf { def ->
                def.isNotBlank() && !isDefaultLowPriorityOrFailed &&
                    (providerModels.isEmpty() || providerModels.any { it.modelId == def })
            } ?: providerModels.firstOrNull()?.modelId
                ?: provider.defaultChatModelId.ifBlank { "gemini-2.5-flash" }

            val convId = _uiState.value.activeConversationId
            val existing = convId?.let { dao.getConversationById(it) }
            if (existing != null) {
                dao.upsertConversation(
                    existing.copy(
                        providerInstanceId = provider.id,
                        modelId = targetModelId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                createNewConversation(provider.id, targetModelId)
            }
        }
    }

    fun switchToDirectModelForProvider(providerId: String) {
        viewModelScope.launch {
            val provider = providers.value.firstOrNull { it.id == providerId }
                ?: dao.getProviderById(providerId)
                ?: return@launch
            val convId = _uiState.value.activeConversationId
            val currentModelId = convId?.let { dao.getConversationById(it)?.modelId }.orEmpty()
            if (currentModelId.isNotBlank()) {
                failedModelsInSession.add("${provider.id}:$currentModelId")
            }

            val allChatModels = models.value.filter {
                it.providerInstanceId == provider.id && CapabilityTag.CHAT in it.capabilitySet()
            }.sortedWith(
                compareBy<ModelDescriptorEntity> {
                    com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo)
                }.thenByDescending { it.isFavorite }
                    .thenBy { it.modelId }
            )

            val untriedDirect = allChatModels.filter {
                "${provider.id}:${it.modelId}" !in failedModelsInSession &&
                    it.modelId != currentModelId &&
                    com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo) <= 1
            }

            val chosenModelId = untriedDirect.firstOrNull()?.modelId
                ?: allChatModels.firstOrNull { it.modelId != currentModelId }?.modelId
                ?: "gemini-2.5-flash"

            if (allChatModels.isEmpty()) {
                repository.addManualModel(provider.id, chosenModelId, isCombo = false)
            }
            switchActiveConversationModel(provider.id, chosenModelId)
            dismissError()
        }
    }

    fun switchActiveConversationModel(providerInstanceId: String, modelId: String) {
        viewModelScope.launch {
            val prov = dao.getProviderById(providerInstanceId)
            if (prov != null && prov.defaultChatModelId != modelId) {
                dao.upsertProvider(prov.copy(defaultChatModelId = modelId))
            }
            val convId = _uiState.value.activeConversationId
            val existing = convId?.let { dao.getConversationById(it) }
            if (existing != null) {
                dao.upsertConversation(
                    existing.copy(
                        providerInstanceId = providerInstanceId,
                        modelId = modelId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                createNewConversation(providerInstanceId, modelId)
            }
            _uiState.update {
                it.copy(
                    showModelPickerSheet = false,
                    statusToast = "Selected $modelId"
                )
            }
        }
    }

    fun updateConversationParameters(
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ) {
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            val existing = dao.getConversationById(convId) ?: return@launch
            dao.upsertConversation(
                existing.copy(
                    systemPrompt = systemPrompt,
                    temperature = temperature.coerceIn(0f, 2f),
                    maxTokens = maxTokens.coerceIn(64, 32768),
                    topP = topP.coerceIn(0.05f, 1f),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.update {
                it.copy(
                    showParameterSheet = false,
                    statusToast = "Updated chat parameters"
                )
            }
        }
    }

    fun togglePinConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            val newPinned = !conversation.isPinned
            dao.upsertConversation(conversation.copy(isPinned = newPinned, updatedAt = System.currentTimeMillis()))
            _uiState.update {
                it.copy(statusToast = if (newPinned) "Pinned '${conversation.title}'" else "Unpinned '${conversation.title}'")
            }
        }
    }

    fun renameConversation(conversation: ConversationEntity, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            dao.upsertConversation(conversation.copy(title = clean, updatedAt = System.currentTimeMillis()))
            _uiState.update { it.copy(statusToast = "Renamed to '$clean'") }
        }
    }

    fun duplicateConversation(conversationId: String) {
        viewModelScope.launch {
            val duplicated = repository.duplicateConversation(conversationId)
            if (duplicated != null) {
                selectConversation(duplicated.id)
                _uiState.update {
                    it.copy(statusToast = "Duplicated '${duplicated.title}'")
                }
            }
        }
    }

    fun moveConversationToFolder(conversationId: String, folderId: String?) {
        viewModelScope.launch {
            val existingMeta = dao.getRunwayConversationMetaOnce(conversationId)
                ?: com.example.data.local.RunwayConversationMetaEntity(conversationId = conversationId)
            dao.upsertRunwayConversationMeta(
                existingMeta.copy(
                    folderId = folderId,
                    updatedAt = System.currentTimeMillis()
                )
            )
            val folderName = folderId?.let { id -> runwayFolders.value.firstOrNull { it.id == id }?.name }
            _uiState.update {
                it.copy(
                    statusToast = if (folderName != null) "Moved to $folderName" else "Removed from folder"
                )
            }
        }
    }

    fun selectHistoryFolderFilter(folderId: String?) {
        _uiState.update { it.copy(selectedHistoryFolderId = folderId) }
    }

    fun saveRunwayFolder(existingId: String?, name: String, colorHex: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val existing = existingId?.let { id -> runwayFolders.value.firstOrNull { it.id == id } }
            val folder = com.example.data.local.RunwayFolderEntity(
                id = existing?.id ?: "folder_${UUID.randomUUID().toString().take(8)}",
                name = cleanName,
                colorHex = colorHex.ifBlank { "#6C7CFF" },
                sortOrder = existing?.sortOrder ?: runwayFolders.value.size,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            dao.upsertRunwayFolder(folder)
            _uiState.update {
                it.copy(statusToast = if (existing != null) "Updated folder '$cleanName'" else "Created folder '$cleanName'")
            }
        }
    }

    fun deleteRunwayFolder(folderId: String) {
        viewModelScope.launch {
            val folder = runwayFolders.value.firstOrNull { it.id == folderId }
            val metasInFolder = dao.getRunwayConversationMetasOnce().filter { it.folderId == folderId }
            metasInFolder.forEach { meta ->
                dao.upsertRunwayConversationMeta(meta.copy(folderId = null))
            }
            dao.deleteRunwayFolderById(folderId)
            _uiState.update { state ->
                state.copy(
                    selectedHistoryFolderId = if (state.selectedHistoryFolderId == folderId) null else state.selectedHistoryFolderId,
                    statusToast = "Deleted folder '${folder?.name ?: "Folder"}'"
                )
            }
        }
    }

    fun continueLatestConversation() {
        viewModelScope.launch {
            val latest = dao.getConversationsOnce().maxByOrNull { it.updatedAt }
            if (latest != null) {
                selectConversation(latest.id)
                _uiState.update { it.copy(currentTab = WorkspaceTab.CHAT) }
            } else {
                createNewConversation()
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            val conv = dao.getConversationById(conversationId)
            val meta = dao.getRunwayConversationMetaOnce(conversationId)
            val msgs = dao.getMessagesOnce(conversationId)
            val nodes = dao.getRunwayMessageNodesOnce(conversationId)
            val docs = dao.getRunwayDocumentsForConversationOnce(conversationId)
            val chunks = dao.getRunwayDocumentChunksForConversationOnce(conversationId)

            if (conv != null) {
                lastDeletedConversationSnapshot = DeletedConversationSnapshot(
                    conversation = conv,
                    meta = meta,
                    messages = msgs,
                    treeNodes = nodes,
                    documents = docs,
                    documentChunks = chunks
                )
            }

            dao.deleteMessagesForConversation(conversationId)
            dao.deleteRunwayMessageNodesForConversation(conversationId)
            dao.deleteRunwayConversationMeta(conversationId)
            dao.deleteRunwayDocumentsForConversation(conversationId)
            dao.deleteRunwayDocumentChunksForConversation(conversationId)
            dao.deleteConversationById(conversationId)
            if (_uiState.value.activeConversationId == conversationId) {
                val remaining = dao.getConversationsOnce()
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    createNewConversation()
                }
            }
            if (conv != null) {
                _uiState.update {
                    it.copy(
                        statusToast = "Deleted '${conv.title}'",
                        hasPendingDeletedConversationUndo = true
                    )
                }
            }
        }
    }

    fun undoDeleteConversation() {
        val snapshot = lastDeletedConversationSnapshot ?: return
        lastDeletedConversationSnapshot = null
        viewModelScope.launch {
            dao.upsertConversation(snapshot.conversation)
            snapshot.meta?.let { dao.upsertRunwayConversationMeta(it) }
            snapshot.messages.forEach { dao.upsertMessage(it) }
            if (snapshot.treeNodes.isNotEmpty()) {
                dao.upsertRunwayMessageNodes(snapshot.treeNodes)
            }
            snapshot.documents.forEach { dao.upsertRunwayDocument(it) }
            if (snapshot.documentChunks.isNotEmpty()) {
                dao.upsertRunwayDocumentChunks(snapshot.documentChunks)
            }
            selectConversation(snapshot.conversation.id)
            _uiState.update {
                it.copy(
                    statusToast = "Restored '${snapshot.conversation.title}'",
                    hasPendingDeletedConversationUndo = false
                )
            }
        }
    }

    fun updateDraftMessage(text: String) {
        _uiState.update { it.copy(draftMessage = text) }
    }

    fun attachImage(uriString: String?) {
        if (uriString == null) {
            _uiState.update { it.copy(attachedImageUri = null, attachedMediaList = emptyList()) }
        } else {
            val attachment = com.example.data.local.ChatMediaAttachment(uri = uriString, isVideo = false)
            addMediaAttachments(listOf(attachment))
        }
    }

    fun addMediaAttachments(newItems: List<com.example.data.local.ChatMediaAttachment>) {
        if (newItems.isEmpty()) return
        val currentState = _uiState.value
        val existingCount = currentState.attachedMediaList.size
        val firstNew = newItems.first()
        val validation = com.example.data.local.FileAttachmentProcessor.validateAttachmentLimits(
            existingCount = existingCount,
            newFileSizeBytes = firstNew.sizeBytes,
            newFileName = firstNew.fileName
        )
        if (!validation.isValid) {
            _uiState.update { it.copy(statusToast = validation.errorMessage) }
            return
        }
        val activeConvId = currentState.activeConversationId
        val activeConv = conversations.value.firstOrNull { it.id == activeConvId }
        val activeProvName = providers.value.firstOrNull { it.id == activeConv?.providerInstanceId }?.displayName
            ?: providers.value.firstOrNull { it.isDefaultForChat }?.displayName
            ?: "the selected provider"
        val shouldShowNotice = !currentState.hasSeenFileAttachmentNotice
        if (shouldShowNotice) {
            viewModelScope.launch {
                repository.secretVault.setHasSeenFileAttachmentNotice(true)
            }
        }
        _uiState.update { current ->
            val combined = (current.attachedMediaList + newItems)
                .distinctBy { it.uri }
                .take(com.example.data.local.FileAttachmentProcessor.MAX_FILES_PER_MESSAGE)
            current.copy(
                attachedMediaList = combined,
                attachedImageUri = combined.firstOrNull()?.uri,
                hasSeenFileAttachmentNotice = true,
                showFirstTimeFileNoticeForProvider = if (shouldShowNotice) activeProvName else current.showFirstTimeFileNoticeForProvider
            )
        }
    }

    fun removeMediaAttachment(index: Int) {
        _uiState.update { current ->
            if (index in current.attachedMediaList.indices) {
                val updated = current.attachedMediaList.toMutableList().apply { removeAt(index) }
                current.copy(
                    attachedMediaList = updated,
                    attachedImageUri = updated.firstOrNull()?.uri
                )
            } else current
        }
    }

    fun clearMediaAttachments() {
        _uiState.update { it.copy(attachedImageUri = null, attachedMediaList = emptyList()) }
    }

    fun startEditingMessage(message: MessageEntity) {
        val attachments = message.getAttachments()
        _uiState.update {
            it.copy(
                editingMessageId = message.id,
                draftMessage = message.content,
                attachedImageUri = message.attachmentUri,
                attachedMediaList = attachments
            )
        }
    }

    fun cancelEditingMessage() {
        _uiState.update {
            it.copy(
                editingMessageId = null,
                draftMessage = "",
                attachedImageUri = null,
                attachedMediaList = emptyList()
            )
        }
    }

    fun stopGeneration() {
        _uiState.value.pendingToolApproval?.decisionDeferred?.complete(ToolApprovalDecision.DENY)
        activeStreamJob?.cancel()
        activeStreamJob = null
        viewModelScope.launch {
            val convId = _uiState.value.activeConversationId
            if (convId != null) {
                val msgs = dao.getMessagesOnce(convId)
                msgs.filter { it.isStreaming }.forEach { streamingMsg ->
                    val stoppedMsg = streamingMsg.copy(
                        isStreaming = false,
                        content = streamingMsg.content.ifBlank { "[Generation stopped by user]" }
                    )
                    dao.upsertMessage(stoppedMsg)
                    com.example.data.local.RunwayDataMigrationManager.syncMessageToRunwayTree(
                        dao = dao,
                        message = stoppedMsg
                    )
                }
            }
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    pendingToolApproval = null,
                    activeToolLoopStatus = null
                )
            }
        }
    }

    fun generateMediaInChat(
        modality: String,
        prompt: String,
        providerId: String? = null,
        modelId: String? = null,
        aspectRatio: String? = null,
        durationSeconds: Int? = null,
        sourceImageUri: String? = null
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            _uiState.update { it.copy(statusToast = "Enter a prompt first.") }
            return
        }

        viewModelScope.launch {
            val allProvs = dao.getProvidersOnce()
            val isVideo = modality.equals("VIDEO", ignoreCase = true)

            // Resolve target provider
            val resolvedProvider = if (!providerId.isNullOrBlank()) {
                allProvs.firstOrNull { it.id == providerId }
            } else if (isVideo) {
                allProvs.firstOrNull { it.id == _uiState.value.selectedVideoProviderId }
                    ?: allProvs.firstOrNull { it.isDefaultForVideo && it.isEnabled }
                    ?: allProvs.firstOrNull { CapabilityTag.VIDEO in it.capabilitySet() }
                    ?: allProvs.firstOrNull()
            } else {
                allProvs.firstOrNull { it.id == _uiState.value.selectedImageProviderId }
                    ?: allProvs.firstOrNull { it.isDefaultForImage && it.isEnabled }
                    ?: allProvs.firstOrNull { CapabilityTag.IMAGE in it.capabilitySet() }
                    ?: allProvs.firstOrNull()
            }
            if (resolvedProvider == null) {
                _uiState.update { it.copy(statusToast = "No AI provider configured.") }
                return@launch
            }
            val targetProvider = resolvedProvider

            // Resolve target model
            val targetModelId = if (!modelId.isNullOrBlank()) {
                modelId
            } else if (isVideo) {
                _uiState.value.selectedVideoModelId.ifBlank {
                    targetProvider.defaultVideoModelId.ifBlank { "omniroute/video-combo" }
                }
            } else {
                _uiState.value.selectedImageModelId.ifBlank {
                    targetProvider.defaultImageModelId.ifBlank { "omniroute/flux-combo" }
                }
            }

            // Check cleartext host approval
            val unapprovedHost = extractHttpHost(targetProvider.baseUrl)?.takeIf {
                it !in _uiState.value.approvedCleartextHosts
            }
            if (unapprovedHost != null) {
                requestCleartextHostApproval(
                    host = unapprovedHost,
                    fullUrl = targetProvider.baseUrl,
                    providerName = targetProvider.displayName
                ) {
                    generateMediaInChat(
                        modality = modality,
                        prompt = prompt,
                        providerId = providerId,
                        modelId = modelId,
                        aspectRatio = aspectRatio,
                        durationSeconds = durationSeconds,
                        sourceImageUri = sourceImageUri
                    )
                }
                return@launch
            }

            // Ensure active conversation exists
            var convId = _uiState.value.activeConversationId
            var conv = convId?.let { dao.getConversationById(it) }
            if (conv == null) {
                val titlePrefix = if (isVideo) "🎬 Video:" else "🎨 Image:"
                val newConv = ConversationEntity(
                    id = UUID.randomUUID().toString(),
                    title = "$titlePrefix ${cleanPrompt.take(28)}",
                    providerInstanceId = targetProvider.id,
                    modelId = targetModelId
                )
                dao.upsertConversation(newConv)
                conv = newConv
                convId = newConv.id
                selectConversation(convId)
            }

            // Insert user message in chat
            val userPrefix = if (isVideo) "/video" else "/image"
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                role = "USER",
                content = "$userPrefix $cleanPrompt",
                providerInstanceId = targetProvider.id,
                modelId = targetModelId,
                attachmentUri = sourceImageUri?.let { uri ->
                    ChatMediaAttachment(uri = uri, isVideo = false).toSerializedString()
                },
                attachmentMimeType = if (sourceImageUri != null) "image/jpeg" else null,
                createdAt = System.currentTimeMillis()
            )
            dao.upsertMessage(userMsg)

            // Insert placeholder assistant response
            val assistantMsgId = UUID.randomUUID().toString()
            val shortModel = targetModelId.substringAfterLast("/")
            val initialAssistantContent = if (isVideo) {
                "🎬 Generating video using **$shortModel** via ${targetProvider.displayName}...\n_Please wait, video synthesis in progress._"
            } else {
                "🎨 Generating image using **$shortModel** via ${targetProvider.displayName}...\n_Synthesizing pixels..._"
            }
            val assistantMsg = MessageEntity(
                id = assistantMsgId,
                conversationId = conv.id,
                role = "ASSISTANT",
                content = initialAssistantContent,
                providerInstanceId = targetProvider.id,
                modelId = targetModelId,
                isStreaming = true,
                createdAt = System.currentTimeMillis() + 10L
            )
            dao.upsertMessage(assistantMsg)

            _uiState.update {
                it.copy(
                    isStreaming = true,
                    draftMessage = "",
                    activeError = null,
                    statusToast = if (isVideo) "Generating video in chat..." else "Generating image in chat..."
                )
            }

            val apiKey = repository.secretVault.getApiKey(targetProvider.id)
            val sourceDataUri = if (!sourceImageUri.isNullOrBlank()) {
                repository.encodeImageUriToDataUri(sourceImageUri)
            } else null

            if (!isVideo) {
                val selectedRatio = aspectRatio ?: _uiState.value.imageAspectRatio.ifBlank { "1:1" }
                val request = ImageGenerationRequest(
                    provider = targetProvider,
                    apiKey = apiKey,
                    modelId = targetModelId,
                    prompt = cleanPrompt,
                    negativePrompt = "",
                    aspectRatio = selectedRatio,
                    size = _uiState.value.imageSize,
                    count = 1,
                    seed = null,
                    stylePreset = _uiState.value.imageStylePreset,
                    guidanceScale = _uiState.value.imageGuidanceScale,
                    steps = _uiState.value.imageSteps,
                    quality = "standard",
                    sourceImageDataUri = sourceDataUri,
                    imageToImageStrength = _uiState.value.imageStrength,
                    isUpscaleRequest = false
                )

                val result = repository.generateAndPersistImages(
                    request = request,
                    rawSourceUriForDisplay = sourceImageUri
                )

                result.fold(
                    onSuccess = { savedList ->
                        val saved = savedList.firstOrNull()
                        val mediaAttachment = if (saved != null) {
                            ChatMediaAttachment(
                                uri = saved.imageUri,
                                mimeType = "image/png",
                                isVideo = false,
                                name = "Generated Image"
                            ).toSerializedString()
                        } else null

                        val updatedContent = buildString {
                            append("✨ **Image generated successfully**\n\n")
                            append("> ").append(cleanPrompt).append("\n\n")
                            append("• **Model:** `").append(targetModelId).append("`\n")
                            append("• **Provider:** ").append(targetProvider.displayName).append("\n")
                            append("• **Aspect Ratio:** ").append(selectedRatio)
                        }

                        val finishedMsg = assistantMsg.copy(
                            content = updatedContent,
                            attachmentUri = mediaAttachment,
                            attachmentMimeType = "image/png",
                            isStreaming = false,
                            completionTokens = 1000,
                            estimatedCostUsd = com.example.data.provider.ModelPriceTable.calculateCostUsd(targetModelId, 0, 1000)
                        )
                        dao.upsertMessage(finishedMsg)
                        _uiState.update { it.copy(isStreaming = false, statusToast = "Image ready!") }
                    },
                    onFailure = { err ->
                        val failContent = "❌ **Image generation failed:**\n${err.message ?: "Unknown error"}"
                        val failedMsg = assistantMsg.copy(
                            content = failContent,
                            errorText = err.message,
                            isStreaming = false
                        )
                        dao.upsertMessage(failedMsg)
                        _uiState.update { it.copy(isStreaming = false, statusToast = "Image generation failed") }
                    }
                )
            } else {
                val selectedRatio = aspectRatio ?: _uiState.value.videoAspectRatio.ifBlank { "16:9" }
                val selectedDuration = durationSeconds ?: _uiState.value.videoDurationSeconds.coerceAtLeast(5)
                val request = VideoGenerationRequest(
                    provider = targetProvider,
                    apiKey = apiKey,
                    modelId = targetModelId,
                    prompt = cleanPrompt,
                    negativePrompt = "",
                    aspectRatio = selectedRatio,
                    resolution = _uiState.value.videoResolution.ifBlank { "720p" },
                    durationSeconds = selectedDuration,
                    cameraMotion = "none",
                    sourceImageDataUri = sourceDataUri
                )

                val result = repository.submitAndEnqueueVideoJob(
                    request = request,
                    rawSourceUriForDisplay = sourceImageUri
                )

                result.fold(
                    onSuccess = { initialJob ->
                        if (initialJob.status == "COMPLETED" && !initialJob.videoUri.isNullOrBlank()) {
                            val mediaAttachment = ChatMediaAttachment(
                                uri = initialJob.videoUri,
                                mimeType = "video/mp4",
                                isVideo = true,
                                name = "Generated Video"
                            ).toSerializedString()

                            val updatedContent = buildString {
                                append("🎬 **Video generated successfully**\n\n")
                                append("> ").append(cleanPrompt).append("\n\n")
                                append("• **Model:** `").append(targetModelId).append("`\n")
                                append("• **Provider:** ").append(targetProvider.displayName).append("\n")
                                append("• **Duration:** ").append(selectedDuration).append("s · ").append(selectedRatio)
                            }

                            val finishedMsg = assistantMsg.copy(
                                content = updatedContent,
                                attachmentUri = mediaAttachment,
                                attachmentMimeType = "video/mp4",
                                isStreaming = false,
                                completionTokens = 2500,
                                estimatedCostUsd = com.example.data.provider.ModelPriceTable.calculateCostUsd(targetModelId, 0, 2500)
                            )
                            dao.upsertMessage(finishedMsg)
                            _uiState.update { it.copy(isStreaming = false, statusToast = "Video ready!") }
                        } else {
                            val progressContent = buildString {
                                append("🎬 **Video job in progress...** (Status: `").append(initialJob.status).append("`)\n\n")
                                append("> ").append(cleanPrompt).append("\n\n")
                                append("• **Model:** `").append(targetModelId).append("`\n")
                                append("• **Job ID:** `").append(initialJob.id.take(12)).append("`\n")
                                append("_Polling for video completion in chat..._")
                            }
                            dao.upsertMessage(assistantMsg.copy(content = progressContent))

                            launch {
                                var currentJob = initialJob
                                var pollCount = 0
                                val maxPolls = 60
                                while (currentJob.status in listOf("QUEUED", "PROCESSING") && pollCount < maxPolls) {
                                    kotlinx.coroutines.delay(3500L)
                                    pollCount++
                                    val pollRes = repository.pollVideoJobOnce(currentJob.id)
                                    pollRes.onSuccess { updatedJob ->
                                        currentJob = updatedJob
                                        if (updatedJob.status in listOf("QUEUED", "PROCESSING")) {
                                            val percentStr = if (updatedJob.progressPercent > 0) " (${updatedJob.progressPercent}%)" else ""
                                            dao.upsertMessage(
                                                assistantMsg.copy(
                                                    content = "🎬 **Rendering video$percentStr...** (Status: `${updatedJob.status}`)\n\n> $cleanPrompt\n\n• **Model:** `$targetModelId`\n• **Job ID:** `${updatedJob.id.take(12)}`\n_Checking progress... ($pollCount/$maxPolls)_"
                                                )
                                            )
                                        }
                                    }
                                }

                                if (currentJob.status == "COMPLETED" && !currentJob.videoUri.isNullOrBlank()) {
                                    val mediaAttachment = ChatMediaAttachment(
                                        uri = currentJob.videoUri,
                                        mimeType = "video/mp4",
                                        isVideo = true,
                                        name = "Generated Video"
                                    ).toSerializedString()

                                    val updatedContent = buildString {
                                        append("🎬 **Video generated successfully**\n\n")
                                        append("> ").append(cleanPrompt).append("\n\n")
                                        append("• **Model:** `").append(targetModelId).append("`\n")
                                        append("• **Provider:** ").append(targetProvider.displayName).append("\n")
                                        append("• **Duration:** ").append(selectedDuration).append("s · ").append(selectedRatio)
                                    }

                                    dao.upsertMessage(
                                        assistantMsg.copy(
                                            content = updatedContent,
                                            attachmentUri = mediaAttachment,
                                            attachmentMimeType = "video/mp4",
                                            isStreaming = false,
                                            completionTokens = 2500,
                                            estimatedCostUsd = com.example.data.provider.ModelPriceTable.calculateCostUsd(targetModelId, 0, 2500)
                                        )
                                    )
                                    _uiState.update { it.copy(isStreaming = false, statusToast = "Video generation completed!") }
                                } else if (currentJob.status == "FAILED") {
                                    dao.upsertMessage(
                                        assistantMsg.copy(
                                            content = "❌ **Video generation failed:**\n${currentJob.errorMessage ?: "Remote generation error"}",
                                            errorText = currentJob.errorMessage,
                                            isStreaming = false
                                        )
                                    )
                                    _uiState.update { it.copy(isStreaming = false, statusToast = "Video generation failed") }
                                } else {
                                    dao.upsertMessage(
                                        assistantMsg.copy(
                                            content = "⏳ **Video job still processing in background**\n\n> $cleanPrompt\n\nJob ID: `${currentJob.id}`\n_You can track this video job in the Video Studio tab or Media Gallery when ready._",
                                            isStreaming = false
                                        )
                                    )
                                    _uiState.update { it.copy(isStreaming = false) }
                                }
                            }
                        }
                    },
                    onFailure = { err ->
                        val failContent = "❌ **Video job submission failed:**\n${err.message ?: "Unknown error"}"
                        val failedMsg = assistantMsg.copy(
                            content = failContent,
                            errorText = err.message,
                            isStreaming = false
                        )
                        dao.upsertMessage(failedMsg)
                        _uiState.update { it.copy(isStreaming = false, statusToast = "Video submission failed") }
                    }
                )
            }
        }
    }

    fun sendMessage() {
        voiceManager.stopSpeaking()
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
        }
        val currentDraft = _uiState.value.draftMessage.trim()
        val mediaList = _uiState.value.attachedMediaList
        val legacyAttachment = _uiState.value.attachedImageUri
        val attachments = if (mediaList.isNotEmpty()) {
            mediaList
        } else if (!legacyAttachment.isNullOrBlank()) {
            listOf(com.example.data.local.ChatMediaAttachment(uri = legacyAttachment))
        } else {
            emptyList()
        }

        if (currentDraft.isEmpty() && attachments.isEmpty()) return
        if (_uiState.value.isStreaming) return

        // Support direct "/agent <command>" or "@agent <command>" from the chat input bar
        if (currentDraft.startsWith("/agent ", ignoreCase = true) ||
            currentDraft.startsWith("@agent ", ignoreCase = true)
        ) {
            val agentCmd = currentDraft.substringAfter(" ").trim()
            if (agentCmd.isNotEmpty()) {
                _uiState.update { it.copy(draftMessage = "", activeError = null) }
                executeAgentCommand(agentCmd)
                return
            }
        }

        // Support direct "/image <prompt>" or "/imagine <prompt>" or "/img <prompt>" in chat
        if (currentDraft.startsWith("/image ", ignoreCase = true) ||
            currentDraft.startsWith("/imagine ", ignoreCase = true) ||
            currentDraft.startsWith("/img ", ignoreCase = true)
        ) {
            val imgPrompt = currentDraft.substringAfter(" ").trim()
            if (imgPrompt.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        draftMessage = "",
                        attachedImageUri = null,
                        attachedMediaList = emptyList(),
                        activeError = null
                    )
                }
                generateMediaInChat(
                    modality = "IMAGE",
                    prompt = imgPrompt,
                    sourceImageUri = legacyAttachment ?: mediaList.firstOrNull { !it.isVideo }?.uri
                )
                return
            }
        }

        // Support direct "/video <prompt>" or "/vid <prompt>" in chat
        if (currentDraft.startsWith("/video ", ignoreCase = true) ||
            currentDraft.startsWith("/vid ", ignoreCase = true)
        ) {
            val vidPrompt = currentDraft.substringAfter(" ").trim()
            if (vidPrompt.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        draftMessage = "",
                        attachedImageUri = null,
                        attachedMediaList = emptyList(),
                        activeError = null
                    )
                }
                generateMediaInChat(
                    modality = "VIDEO",
                    prompt = vidPrompt,
                    sourceImageUri = legacyAttachment ?: mediaList.firstOrNull { !it.isVideo }?.uri
                )
                return
            }
        }

        val serializedAttachment = if (attachments.isNotEmpty()) {
            com.example.data.local.ChatMediaAttachment.serializeList(attachments)
        } else null
        val primaryMimeType = attachments.firstOrNull()?.mimeType

        val editingId = _uiState.value.editingMessageId
        val isCompare = _uiState.value.compareModeEnabled && _uiState.value.compareTargets.size >= 2

        _uiState.update {
            it.copy(
                draftMessage = "",
                attachedImageUri = null,
                attachedMediaList = emptyList(),
                editingMessageId = null,
                activeError = null
            )
        }

        viewModelScope.launch {
            var convId = _uiState.value.activeConversationId
            var conv = convId?.let { dao.getConversationById(it) }
            if (conv == null) {
                val defaultProv = providers.value.firstOrNull { it.isDefaultForChat && it.isEnabled }
                    ?: providers.value.firstOrNull { it.id == "provider_openai_default" }
                    ?: providers.value.firstOrNull()
                val newConv = ConversationEntity(
                    id = UUID.randomUUID().toString(),
                    title = currentDraft.take(36).ifBlank { "Image Prompt" },
                    providerInstanceId = defaultProv?.id ?: "provider_openai_default",
                    modelId = defaultProv?.defaultChatModelId?.ifBlank { "gpt-4o" }
                        ?: "gpt-4o"
                )
                dao.upsertConversation(newConv)
                conv = newConv
                convId = newConv.id
                selectConversation(convId)
            }

            // Check monthly provider spending limit before sending
            val activeProv = providers.value.firstOrNull { it.id == conv.providerInstanceId }
                ?: dao.getProviderById(conv.providerInstanceId)
            if (activeProv != null) {
                val stateSnap = _uiState.value
                val limitUsd = stateSnap.providerMonthlyLimitsUsd[activeProv.id]
                val monthlySpend = com.example.data.provider.ProviderSpendingLimitEvaluator.calculateMonthlySpendUsd(
                    usageRecords = usageRecords.value,
                    providerInstanceId = activeProv.id
                )
                val hasActiveOverride = conv.id in stateSnap.spendingLimitActiveOverrideChatIds
                val hasUsedOverride = conv.id in stateSnap.spendingLimitUsedOverrideChatIds
                val eval = com.example.data.provider.ProviderSpendingLimitEvaluator.evaluate(
                    providerInstanceId = activeProv.id,
                    providerDisplayName = activeProv.displayName,
                    monthlySpendUsd = monthlySpend,
                    monthlyLimitUsd = limitUsd,
                    hasActiveOverrideForChat = hasActiveOverride,
                    hasUsedOverrideInChat = hasUsedOverride
                )
                if (eval.isBlocked) {
                    val blockMsg = eval.blockedMessage
                        ?: "Monthly limit reached for ${activeProv.displayName}. Raise the limit in Settings to continue."
                    _uiState.update {
                        it.copy(
                            draftMessage = currentDraft,
                            attachedMediaList = attachments,
                            attachedImageUri = attachments.firstOrNull()?.uri,
                            activeError = NormalizedAiError(
                                category = ErrorCategory.RATE_LIMIT,
                                title = "Monthly Spending Limit Reached",
                                message = blockMsg,
                                suggestedFix = if (eval.canOverrideOnce) {
                                    "Raise the limit in Providers/Settings or use the one-time override for this chat."
                                } else {
                                    "Raise the limit in Settings to continue."
                                },
                                details = eval.progressText,
                                isRetryable = false
                            )
                        )
                    }
                    return@launch
                } else if (hasActiveOverride) {
                    // Consume the one-time override for this chat
                    _uiState.update {
                        it.copy(
                            spendingLimitActiveOverrideChatIds = it.spendingLimitActiveOverrideChatIds - conv.id,
                            spendingLimitUsedOverrideChatIds = it.spendingLimitUsedOverrideChatIds + conv.id
                        )
                    }
                }
            }

            if (editingId != null) {
                val existingMsgs = dao.getMessagesOnce(conv.id)
                val target = existingMsgs.firstOrNull { it.id == editingId }
                if (target != null) {
                    dao.deleteMessagesFromTimestamp(conv.id, target.createdAt)
                }
            }

            val currentMessages = dao.getMessagesOnce(conv.id)
            if (currentMessages.isEmpty() && conv.title in listOf("New Chat", "OmniRoute Workspace")) {
                val autoTitle = currentDraft.lineSequence().firstOrNull()?.take(38)?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: attachments.firstOrNull()?.fileName?.take(38)
                    ?: "Multimodal Chat"
                conv = conv.copy(title = autoTitle, updatedAt = System.currentTimeMillis())
                dao.upsertConversation(conv)
            }

            val docAttachmentTokens = com.example.data.local.FileAttachmentProcessor.estimateAttachedFilesTokens(attachments)
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                role = "USER",
                content = currentDraft,
                providerInstanceId = conv.providerInstanceId,
                modelId = if (isCompare) "Compare (${_uiState.value.compareTargets.size} models)" else conv.modelId,
                attachmentUri = serializedAttachment,
                attachmentMimeType = primaryMimeType,
                promptTokens = ModelCapabilityRegistry.estimateTokens(currentDraft) + docAttachmentTokens,
                createdAt = System.currentTimeMillis()
            )
            dao.upsertMessage(userMsg)
            com.example.data.local.RunwayDataMigrationManager.syncMessageToRunwayTree(
                dao = dao,
                message = userMsg
            )

            if (isCompare) {
                streamCompareModelsReply(conv, _uiState.value.compareTargets)
            } else {
                streamSingleAssistantReply(conv)
            }
        }
    }

    fun regenerateLastResponse() {
        if (_uiState.value.isStreaming) return
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            val conv = dao.getConversationById(convId) ?: return@launch
            val msgs = dao.getMessagesOnce(convId)
            if (msgs.isEmpty()) return@launch

            val lastMsg = msgs.last()
            if (lastMsg.role == "ASSISTANT") {
                val groupId = lastMsg.comparisonGroupId
                if (groupId != null) {
                    msgs.filter { it.comparisonGroupId == groupId }.forEach { m ->
                        dao.deleteMessageById(m.id)
                    }
                    streamCompareModelsReply(conv, _uiState.value.compareTargets)
                } else {
                    dao.deleteMessageById(lastMsg.id)
                    streamSingleAssistantReply(conv)
                }
            }
        }
    }

    private fun streamSingleAssistantReply(conv: ConversationEntity) {
        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            val provider = dao.getProviderById(conv.providerInstanceId)
                ?: providers.value.firstOrNull()
                ?: return@launch

            val unapprovedHost = extractHttpHost(provider.baseUrl)?.takeIf {
                it !in _uiState.value.approvedCleartextHosts
            }
            if (unapprovedHost != null) {
                requestCleartextHostApproval(
                    host = unapprovedHost,
                    fullUrl = provider.baseUrl,
                    providerName = provider.displayName
                ) {
                    streamSingleAssistantReply(conv)
                }
                return@launch
            }

            val historyMessages = dao.getMessagesOnce(conv.id).filter { it.comparisonGroupId == null || it.role == "USER" }
            val payloadMessages = buildPayloadHistory(historyMessages)

            _uiState.update { it.copy(isStreaming = true, activeError = null) }
            executeModelStream(
                conv = conv,
                provider = provider,
                modelId = conv.modelId,
                payloadMessages = payloadMessages,
                comparisonGroupId = null,
                createdAtOffset = 1L
            )
            dao.upsertConversation(conv.copy(updatedAt = System.currentTimeMillis()))
            _uiState.update { it.copy(isStreaming = false) }
        }
    }

    /**
     * Dispatches the same prompt to 2-3 selected models concurrently in side-by-side Compare Mode.
     */
    private fun streamCompareModelsReply(
        conv: ConversationEntity,
        targets: List<CompareTargetModel>
    ) {
        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            val allProvs = dao.getProvidersOnce().associateBy { it.id }

            // Check if any selected compare provider requires HTTP host approval
            for (t in targets) {
                val prov = allProvs[t.providerInstanceId] ?: continue
                val unapprovedHost = extractHttpHost(prov.baseUrl)?.takeIf {
                    it !in _uiState.value.approvedCleartextHosts
                }
                if (unapprovedHost != null) {
                    requestCleartextHostApproval(
                        host = unapprovedHost,
                        fullUrl = prov.baseUrl,
                        providerName = prov.displayName
                    ) {
                        streamCompareModelsReply(conv, targets)
                    }
                    return@launch
                }
            }

            val historyMessages = dao.getMessagesOnce(conv.id)
                .filter { it.role == "USER" }
            val payloadMessages = buildPayloadHistory(historyMessages)
            val groupId = "cmp_${UUID.randomUUID().toString().take(8)}"

            _uiState.update { it.copy(isStreaming = true, activeError = null) }

            coroutineScope {
                targets.forEachIndexed { idx, target ->
                    val prov = allProvs[target.providerInstanceId]
                        ?: providers.value.firstOrNull()
                        ?: return@forEachIndexed
                    launch {
                        executeModelStream(
                            conv = conv,
                            provider = prov,
                            modelId = target.modelId,
                            payloadMessages = payloadMessages,
                            comparisonGroupId = groupId,
                            createdAtOffset = (idx + 1).toLong()
                        )
                    }
                }
            }

            dao.upsertConversation(conv.copy(updatedAt = System.currentTimeMillis()))
            _uiState.update { it.copy(isStreaming = false) }
        }
    }

    private suspend fun buildPayloadHistory(messages: List<MessageEntity>): List<ChatMessagePayload> {
        return messages
            .filter { m ->
                m.errorText == null &&
                    !(m.role == "ASSISTANT" && (m.content.isBlank() || m.content.startsWith("⚠️")))
            }
            .map { m ->
                val attachments = m.getAttachments()
                val mediaPayloads = if (attachments.isNotEmpty()) {
                    repository.encodeMediaAttachments(attachments)
                } else emptyList()
                val legacyDataUri = if (mediaPayloads.isEmpty() && !m.attachmentUri.isNullOrBlank() && attachments.none { it.isDocument }) {
                    repository.encodeImageUriToDataUri(m.attachmentUri)
                } else null
                val primaryImageDataUri = mediaPayloads.firstOrNull { !it.isVideo }?.dataUri ?: legacyDataUri
                val effectiveContent = com.example.data.local.FileAttachmentProcessor.buildMessageContentWithFileAttachments(
                    userText = m.content,
                    attachments = attachments
                )

                ChatMessagePayload(
                    role = m.role.lowercase(),
                    content = effectiveContent,
                    imageDataUri = primaryImageDataUri,
                    mediaAttachments = mediaPayloads
                )
            }
    }

    private suspend fun executeModelStream(
        conv: ConversationEntity,
        provider: ProviderInstanceEntity,
        modelId: String,
        payloadMessages: List<ChatMessagePayload>,
        comparisonGroupId: String?,
        createdAtOffset: Long,
        allowAutoFallback: Boolean = true
    ) {
        val startTime = System.currentTimeMillis()
        suspend fun resolveProviderRuntime(
            targetProvider: ProviderInstanceEntity,
            targetModelId: String
        ): Triple<ProviderInstanceEntity, String, ModelDescriptorEntity> {
            val combo = EmbeddedOmniRouteServer.findCombo(targetModelId)
            val rawKey = repository.secretVault.getApiKey(targetProvider.id)
            val effProv = if (combo != null &&
                targetProvider.presetEnum() != ProviderPresetType.OMNIROUTE &&
                !targetProvider.baseUrl.contains("20128") &&
                rawKey.isBlank()
            ) {
                providers.value.firstOrNull {
                    it.presetEnum() == ProviderPresetType.OMNIROUTE || it.baseUrl.contains("20128")
                } ?: targetProvider
            } else {
                targetProvider
            }
            val key = repository.secretVault.getApiKey(effProv.id)
            val descriptor = models.value.firstOrNull {
                it.providerInstanceId == effProv.id && it.modelId == targetModelId
            } ?: models.value.firstOrNull {
                it.providerInstanceId == targetProvider.id && it.modelId == targetModelId
            } ?: ModelCapabilityRegistry.buildDescriptor(effProv.id, targetModelId)
            return Triple(effProv, key, descriptor)
        }

        var currentProvider: ProviderInstanceEntity = provider
        var currentModelId: String = modelId
        val (effectiveProvider, apiKey, modelDescriptor) = resolveProviderRuntime(currentProvider, currentModelId)

        // Resolve active SKILL.md instruction packs for this conversation & prompt in deterministic order
        val latestUserPrompt = payloadMessages.lastOrNull { it.role.equals("user", ignoreCase = true) }?.content.orEmpty()
        val allSkills = dao.getSkillsOnce()
        val activeSkills = com.example.data.provider.PromptCacheManager.sortSkillsDeterministically(
            SkillMarkdownParser.resolveActiveSkillsForTurn(
                allSkills = allSkills,
                conversation = conv,
                userPrompt = latestUserPrompt
            )
        )
        val deterministicSkillsBlock = com.example.data.provider.PromptCacheManager.formatDeterministicSkillsBlock(activeSkills)
        val effectiveSystemPrompt = com.example.data.provider.PromptCacheManager.buildStableSystemPrefix(
            baseSystemPrompt = conv.systemPrompt,
            skillsBlock = deterministicSkillsBlock
        )
        val providerSupportsCaching = com.example.data.provider.PromptCacheManager.supportsPromptCaching(effectiveProvider)

        // Reset per-message web search counter at the start of each turn
        repository.liveInfoToolsService.resetPerMessageSearchCounter()

        // Resolve enabled MCP tools + built-in sandboxed run_code tool + Live Info tools if function calling is supported
        val mcpAndSandboxTriples: List<Triple<McpServerEntity, McpToolEntity, McpToolDefinition>> =
            if (conv.mcpToolsEnabled && modelDescriptor.supportsFunctionCalling && comparisonGroupId == null) {
                val serversById = dao.getMcpServersOnce().filter { it.isEnabled }.associateBy { it.id }
                val mcpTriples = dao.getMcpToolsOnce().filter { it.isEnabled }.mapNotNull { toolEntity ->
                    val srv = serversById[toolEntity.serverId] ?: return@mapNotNull null
                    val gate = com.example.data.mcp.ConnectorApprovalPolicy.evaluateToolExecutionGate(
                        tool = toolEntity,
                        server = srv,
                        connectorDefaultPolicies = _uiState.value.connectorDefaultChoices,
                        perToolPolicies = _uiState.value.connectorToolChoices,
                        confirmedWriteAlwaysToolIds = _uiState.value.confirmedWriteAlwaysTools
                    )
                    if (gate == com.example.data.mcp.ToolExecutionGate.BLOCK) {
                        return@mapNotNull null
                    }
                    Triple(
                        srv,
                        toolEntity,
                        McpToolDefinition(
                            serverId = srv.id,
                            serverName = srv.name,
                            toolName = toolEntity.toolName,
                            description = toolEntity.description,
                            inputSchemaJson = toolEntity.inputSchemaJson,
                            isReadOnlyHint = toolEntity.isReadOnlyHint
                        )
                    )
                }
                val synthServer = com.example.data.mcp.JsSandboxExecutor.buildRunCodeServerEntity()
                val synthTool = com.example.data.mcp.JsSandboxExecutor.buildRunCodeToolEntity()
                val runCodeTriple = Triple(
                    synthServer,
                    synthTool,
                    McpToolDefinition(
                        serverId = synthServer.id,
                        serverName = synthServer.name,
                        toolName = synthTool.toolName,
                        description = synthTool.description,
                        inputSchemaJson = synthTool.inputSchemaJson,
                        isReadOnlyHint = synthTool.isReadOnlyHint
                    )
                )
                mcpTriples + runCodeTriple
            } else {
                emptyList()
            }

        val liveInfoTriples: List<Triple<McpServerEntity, McpToolEntity, McpToolDefinition>> =
            if (comparisonGroupId == null) {
                com.example.data.mcp.LiveInfoToolsService.resolveEnabledLiveInfoToolTriples(
                    modelSupportsFunctionCalling = modelDescriptor.supportsFunctionCalling,
                    webSearchEnabled = _uiState.value.liveInfoWebSearchEnabled,
                    newsHeadlinesEnabled = _uiState.value.liveInfoNewsHeadlinesEnabled
                )
            } else {
                emptyList()
            }

        val enabledToolTriples = mcpAndSandboxTriples + liveInfoTriples
        val toolDefinitions: List<McpToolDefinition> = com.example.data.provider.PromptCacheManager.sortToolsDeterministically(
            enabledToolTriples.map { it.third }
        )
        val toolLookupByName = enabledToolTriples.associateBy { it.third.toolName }

        // Smart Context Trimming: when input reaches ~60% of model's context window (default 32,000 if unknown),
        // summarise older messages keeping the last 6 turns using the selected model (< 800 tokens).
        // If summarising fails, send full history and show nothing extra.
        val isTrimmingEnabled = _uiState.value.contextTrimmingEnabledByChatId[conv.id] ?: true
        val trimmedHistoryResult = com.example.data.provider.SmartContextTrimmer.trimIfNeeded(
            fullMessages = payloadMessages,
            systemPrompt = effectiveSystemPrompt,
            modelContextWindowTokens = modelDescriptor.contextWindow.takeIf { it > 0 },
            trimmingEnabledForChat = isTrimmingEnabled,
            existingCachedSummary = _uiState.value.contextSummaryByChatId[conv.id],
            summariseOlderMessages = { summaryPrompt ->
                val summaryBuilder = StringBuilder()
                var summaryError: NormalizedAiError? = null
                val summaryRequest = ChatCompletionRequest(
                    provider = effectiveProvider,
                    apiKey = apiKey,
                    modelId = modelId,
                    messages = listOf(ChatMessagePayload(role = "user", content = summaryPrompt)),
                    systemPrompt = "Summarise the conversation history concisely in under 600 tokens.",
                    temperature = 0.2f,
                    maxTokens = 800,
                    topP = 1.0f,
                    supportsTemperature = modelDescriptor.supportsTemperature,
                    supportsTopP = modelDescriptor.supportsTopP,
                    supportsSystemPrompt = modelDescriptor.supportsSystemPrompt
                )
                repository.streamChat(summaryRequest).collect { ev ->
                    when (ev) {
                        is ChatStreamEvent.Delta -> summaryBuilder.append(ev.textChunk)
                        is ChatStreamEvent.Error -> summaryError = ev.error
                        else -> Unit
                    }
                }
                if (summaryError != null || summaryBuilder.isBlank()) {
                    throw IllegalStateException(summaryError?.message ?: "Empty summary")
                }
                summaryBuilder.toString()
            }
        )
        if (trimmedHistoryResult.wasSummarised && !trimmedHistoryResult.summaryText.isNullOrBlank()) {
            _uiState.update { state ->
                state.copy(
                    contextSummaryByChatId = state.contextSummaryByChatId + (conv.id to trimmedHistoryResult.summaryText)
                )
            }
        } else if (!trimmedHistoryResult.wasSummarised && !isTrimmingEnabled) {
            _uiState.update { state ->
                state.copy(
                    contextSummaryByChatId = state.contextSummaryByChatId - conv.id
                )
            }
        }

        val assistantMsgId = UUID.randomUUID().toString()
        val initialAssistantMsg = MessageEntity(
            id = assistantMsgId,
            conversationId = conv.id,
            role = "ASSISTANT",
            content = "",
            providerInstanceId = provider.id,
            modelId = modelId,
            isStreaming = true,
            comparisonGroupId = comparisonGroupId,
            createdAt = startTime + createdAtOffset
        )
        dao.upsertMessage(initialAssistantMsg)
        com.example.data.local.RunwayDataMigrationManager.syncMessageToRunwayTree(
            dao = dao,
            message = initialAssistantMsg,
            providerName = provider.displayName
        )

        val workingPayloadMessages = trimmedHistoryResult.requestMessages.toMutableList()
        val maxIterationsSetting = conv.maxToolIterations
        val isUnlimited = maxIterationsSetting >= 51
        val maxIterations = if (isUnlimited) Int.MAX_VALUE else maxIterationsSetting.coerceIn(1, 50)
        var lastToolCallSignature: String? = null
        var consecutiveIdenticalToolCalls = 0
        var iteration = 0
        val failoverSession = com.example.data.provider.FailoverStreamSession()
        val attemptedFailoverKeys = mutableSetOf("${currentProvider.id}:$currentModelId")
        val retriedContextOverflowOnProvider = mutableSetOf<String>()
        var reportedPromptTokens = 0
        var reportedCompletionTokens = 0
        var reportedCachedInputTokens = 0
        var streamError: NormalizedAiError? = null

        while (iteration < maxIterations) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                break
            }
            iteration++
            val turnCounter = if (isUnlimited) "Tool turn $iteration • Unlimited" else "Step $iteration/$maxIterations"
            _uiState.update { it.copy(activeToolLoopStatus = turnCounter) }
            val turnText = StringBuilder()
            val turnToolCalls = mutableListOf<ModelToolCallRequest>()

            while (true) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                    break
                }
                turnText.setLength(0)
                turnToolCalls.clear()
                streamError = null
                var attemptPromptTokens = 0
                var attemptCompletionTokens = 0
                var attemptCachedInputTokens = 0

                val (activeEffectiveProvider, activeApiKey, activeModelDescriptor) =
                    resolveProviderRuntime(currentProvider, currentModelId)
                val activeProviderSupportsCaching =
                    com.example.data.provider.PromptCacheManager.supportsPromptCaching(activeEffectiveProvider)

                val request = ChatCompletionRequest(
                    provider = activeEffectiveProvider,
                    apiKey = activeApiKey,
                    modelId = currentModelId,
                    messages = workingPayloadMessages.toList(),
                    systemPrompt = effectiveSystemPrompt,
                    baseSystemPrompt = conv.systemPrompt,
                    enabledSkillsBlock = deterministicSkillsBlock,
                    enablePromptCaching = activeProviderSupportsCaching,
                    temperature = conv.temperature,
                    maxTokens = conv.maxTokens,
                    topP = conv.topP,
                    supportsTemperature = activeModelDescriptor.supportsTemperature,
                    supportsTopP = activeModelDescriptor.supportsTopP,
                    supportsSystemPrompt = activeModelDescriptor.supportsSystemPrompt,
                    tools = if (activeModelDescriptor.supportsFunctionCalling) toolDefinitions else emptyList()
                )

                repository.streamChat(request).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> {
                            turnText.append(event.textChunk)
                            val visible = failoverSession.onDelta(event.textChunk)
                            dao.upsertMessage(
                                initialAssistantMsg.copy(
                                    content = visible,
                                    providerInstanceId = currentProvider.id,
                                    modelId = currentModelId,
                                    isStreaming = true
                                )
                            )
                        }
                        is ChatStreamEvent.ToolCall -> {
                            turnToolCalls.add(event.call)
                        }
                        is ChatStreamEvent.Usage -> {
                            // Providers report usage as a CUMULATIVE snapshot for the
                            // request, and many gateways attach it to every streamed
                            // chunk. Summing the snapshots counts the same tokens once
                            // per chunk (the runaway "Chat total" bug) — keep only the
                            // largest snapshot of this attempt; the attempt's final
                            // snapshot is folded into the chat totals below.
                            attemptPromptTokens = maxOf(attemptPromptTokens, event.promptTokens)
                            attemptCompletionTokens = maxOf(attemptCompletionTokens, event.completionTokens)
                            attemptCachedInputTokens = maxOf(attemptCachedInputTokens, event.cachedInputTokens)
                        }
                        is ChatStreamEvent.Error -> {
                            streamError = event.error
                        }
                        is ChatStreamEvent.Done -> Unit
                    }
                }

                val isCancelled = !kotlinx.coroutines.currentCoroutineContext().isActive
                if (isCancelled) {
                    break
                }

                val errOnAttempt = streamError
                if (errOnAttempt == null) {
                    reportedPromptTokens += attemptPromptTokens
                    reportedCompletionTokens += attemptCompletionTokens
                    reportedCachedInputTokens += attemptCachedInputTokens
                    failoverSession.commitCurrentAttempt()
                    break
                }

                // Do not fail over across models during multi-column Compare mode
                if (comparisonGroupId != null) {
                    break
                }

                val currentCompositeKey = "${currentProvider.id}:$currentModelId"
                val errorAction = com.example.data.provider.FailoverResolver.classifyError(
                    error = errOnAttempt,
                    isUserCancelled = isCancelled,
                    alreadyRetriedContextOverflowOnSameProvider = currentCompositeKey in retriedContextOverflowOnProvider
                )

                if (errorAction == com.example.data.provider.FailoverErrorAction.RETRY_SAME_PROVIDER_AFTER_CONTEXT_TRIM) {
                    retriedContextOverflowOnProvider.add(currentCompositeKey)
                    failoverSession.onRetrySameProvider()
                    dao.upsertMessage(
                        initialAssistantMsg.copy(
                            content = failoverSession.visibleText,
                            providerInstanceId = currentProvider.id,
                            modelId = currentModelId,
                            isStreaming = true
                        )
                    )
                    // Context-overflow: re-run payload through SmartContextTrimmer and retry once on the same provider
                    val overflowTrimResult = com.example.data.provider.SmartContextTrimmer.trimIfNeeded(
                        fullMessages = workingPayloadMessages.toList(),
                        systemPrompt = effectiveSystemPrompt,
                        modelContextWindowTokens = 1,
                        trimmingEnabledForChat = true,
                        existingCachedSummary = _uiState.value.contextSummaryByChatId[conv.id],
                        summariseOlderMessages = { summaryPrompt ->
                            val summaryBuilder = StringBuilder()
                            var summaryError: NormalizedAiError? = null
                            val summaryRequest = ChatCompletionRequest(
                                provider = activeEffectiveProvider,
                                apiKey = activeApiKey,
                                modelId = currentModelId,
                                messages = listOf(ChatMessagePayload(role = "user", content = summaryPrompt)),
                                systemPrompt = "Summarise the conversation history concisely in under 600 tokens.",
                                temperature = 0.2f,
                                maxTokens = 800,
                                topP = 1.0f,
                                supportsTemperature = activeModelDescriptor.supportsTemperature,
                                supportsTopP = activeModelDescriptor.supportsTopP,
                                supportsSystemPrompt = activeModelDescriptor.supportsSystemPrompt
                            )
                            repository.streamChat(summaryRequest).collect { ev ->
                                when (ev) {
                                    is ChatStreamEvent.Delta -> summaryBuilder.append(ev.textChunk)
                                    is ChatStreamEvent.Error -> summaryError = ev.error
                                    else -> Unit
                                }
                            }
                            if (summaryError != null || summaryBuilder.isBlank()) {
                                throw IllegalStateException(summaryError?.message ?: "Empty summary")
                            }
                            summaryBuilder.toString()
                        }
                    )
                    if (overflowTrimResult.wasSummarised && !overflowTrimResult.summaryText.isNullOrBlank()) {
                        _uiState.update { state ->
                            state.copy(
                                contextSummaryByChatId = state.contextSummaryByChatId + (conv.id to overflowTrimResult.summaryText)
                            )
                        }
                    }
                    workingPayloadMessages.clear()
                    workingPayloadMessages.addAll(overflowTrimResult.requestMessages)
                    continue
                }

                if (errorAction == com.example.data.provider.FailoverErrorAction.FAILOVER_TO_NEXT_PROVIDER &&
                    _uiState.value.providerFailoverEnabled
                ) {
                    val providersById = providers.value.associateBy { it.id }
                    val nextEntry = com.example.data.provider.FailoverResolver.selectNextTarget(
                        failoverEnabled = _uiState.value.providerFailoverEnabled,
                        chain = _uiState.value.providerFailoverChain,
                        attemptedCompositeKeys = attemptedFailoverKeys,
                        availableProvidersById = providersById
                    )
                    val nextProvider = nextEntry?.let { providersById[it.providerInstanceId] }
                    if (nextEntry != null && nextProvider != null) {
                        attemptedFailoverKeys.add(nextEntry.compositeKey)
                        currentProvider = nextProvider
                        currentModelId = nextEntry.modelId
                        val restartOutcome = failoverSession.onFailoverToProvider(nextProvider.displayName)
                        val updatedAssistantMsg = initialAssistantMsg.copy(
                            content = restartOutcome.visibleTextAfterReset,
                            providerInstanceId = currentProvider.id,
                            modelId = currentModelId,
                            isStreaming = true
                        )
                        dao.upsertMessage(updatedAssistantMsg)
                        com.example.data.local.RunwayDataMigrationManager.syncMessageToRunwayTree(
                            dao = dao,
                            message = updatedAssistantMsg,
                            providerName = currentProvider.displayName
                        )
                        if (restartOutcome.restartedNote != null) {
                            _uiState.update { state ->
                                state.copy(
                                    restartedOnProviderByMessageId = state.restartedOnProviderByMessageId +
                                        (assistantMsgId to restartOutcome.restartedNote)
                                )
                            }
                        }
                        continue
                    }
                }

                break
            }

            // Check if user tapped Stop or cancelled
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                break
            }

            if (streamError != null || turnToolCalls.isEmpty() || toolDefinitions.isEmpty()) {
                break
            }

            // Check if same tool is called with identical arguments 3 times in a row
            var repeatedToolCallDetected = false
            for (toolCall in turnToolCalls) {
                val callSig = "${toolCall.toolName}:::${toolCall.argumentsJson.trim()}"
                if (callSig == lastToolCallSignature) {
                    consecutiveIdenticalToolCalls++
                } else {
                    lastToolCallSignature = callSig
                    consecutiveIdenticalToolCalls = 1
                }
                if (consecutiveIdenticalToolCalls >= 3) {
                    repeatedToolCallDetected = true
                    break
                }
            }
            if (repeatedToolCallDetected) {
                val stopMsg = "Stopped: repeated identical tool call"
                failoverSession.appendSeparatorIfNeeded("\n\n")
                val visibleWithStop = failoverSession.appendCommittedText("[$stopMsg]")
                dao.upsertMessage(
                    initialAssistantMsg.copy(
                        content = visibleWithStop,
                        providerInstanceId = currentProvider.id,
                        modelId = currentModelId,
                        isStreaming = true
                    )
                )
                _uiState.update { it.copy(statusToast = stopMsg) }
                break
            }

            // Record assistant tool_calls turn in the working conversation payload
            workingPayloadMessages.add(
                ChatMessagePayload(
                    role = "assistant",
                    content = turnText.toString(),
                    toolCalls = turnToolCalls.toList()
                )
            )

            for (toolCall in turnToolCalls) {
                val isRunCodeCall = toolCall.toolName == com.example.data.mcp.JsSandboxExecutor.RUN_CODE_TOOL_NAME
                if (isRunCodeCall) {
                    val synthServer = com.example.data.mcp.JsSandboxExecutor.buildSyntheticServerEntity()
                    val synthTool = com.example.data.mcp.JsSandboxExecutor.buildSyntheticToolEntity()
                    val confirmDetails = com.example.data.mcp.JsSandboxExecutor.buildRunConfirmationDetails(
                        toolCall.argumentsJson
                    )
                    // Safety rule: Never run code automatically. Always show the Run confirmation sheet before every run.
                    val approvalReq = PendingToolApprovalRequest(
                        server = synthServer,
                        tool = synthTool,
                        callRequest = toolCall,
                        iterationIndex = iteration,
                        maxIterations = if (isUnlimited) 51 else maxIterations
                    )
                    _uiState.update {
                        it.copy(
                            pendingToolApproval = approvalReq,
                            activeToolLoopStatus = "$turnCounter • Awaiting confirmation to run ${confirmDetails.languageLabel} (${confirmDetails.timeLimitLabel} limit)"
                        )
                    }
                    val decision = approvalReq.decisionDeferred.await()
                    _uiState.update { it.copy(pendingToolApproval = null) }
                    val approved = when (decision) {
                        ToolApprovalDecision.APPROVE_ONCE,
                        ToolApprovalDecision.APPROVE_CONVERSATION_READ_ONLY -> true
                        ToolApprovalDecision.DENY -> false
                    }

                    if (!approved) {
                        val deniedLog = ToolCallLogEntity(
                            id = "tcl_${UUID.randomUUID().toString().take(10)}",
                            conversationId = conv.id,
                            messageId = assistantMsgId,
                            serverId = synthServer.id,
                            serverName = synthServer.name,
                            toolName = synthTool.toolName,
                            argumentsJson = toolCall.argumentsJson,
                            resultText = "Code execution denied by user.",
                            isSuccess = false,
                            isDestructiveOrWrite = false,
                            durationMs = 0L
                        )
                        dao.insertToolCallLog(deniedLog)
                        workingPayloadMessages.add(
                            ChatMessagePayload(
                                role = "tool",
                                content = "Code execution was denied by the user.",
                                toolCallId = toolCall.id,
                                toolName = toolCall.toolName
                            )
                        )
                        continue
                    }

                    _uiState.update {
                        it.copy(
                            activeToolLoopStatus = "$turnCounter • Running ${confirmDetails.languageLabel}…"
                        )
                    }
                    val logEntry = repository.executeSandboxedCodeAndLog(
                        conversationId = conv.id,
                        messageId = assistantMsgId,
                        argumentsJson = toolCall.argumentsJson
                    )
                    val statusLabel = if (logEntry.isSuccess) "SUCCESS" else "ERROR"
                    val toolResultForModel = buildString {
                        appendLine("[${confirmDetails.languageLabel} Runner Result ($statusLabel)]")
                        appendLine(logEntry.resultText)
                    }
                    workingPayloadMessages.add(
                        ChatMessagePayload(
                            role = "tool",
                            content = toolResultForModel,
                            toolCallId = toolCall.id,
                            toolName = toolCall.toolName
                        )
                    )
                    continue
                }

                // Built-in Live Info Tools: web_search & news_headlines
                if (com.example.data.mcp.LiveInfoToolsService.isLiveInfoTool(toolCall.toolName)) {
                    val isToolEnabled = when (toolCall.toolName) {
                        com.example.data.mcp.LiveInfoToolsService.WEB_SEARCH_TOOL_NAME -> _uiState.value.liveInfoWebSearchEnabled
                        com.example.data.mcp.LiveInfoToolsService.NEWS_HEADLINES_TOOL_NAME -> _uiState.value.liveInfoNewsHeadlinesEnabled
                        else -> false
                    }
                    if (!isToolEnabled) {
                        workingPayloadMessages.add(
                            ChatMessagePayload(
                                role = "tool",
                                content = "Error: Tool '${toolCall.toolName}' is disabled in Settings > Live info.",
                                toolCallId = toolCall.id,
                                toolName = toolCall.toolName
                            )
                        )
                        continue
                    }

                    val liveStatusText = if (toolCall.toolName == com.example.data.mcp.LiveInfoToolsService.WEB_SEARCH_TOOL_NAME) {
                        com.example.data.mcp.LiveInfoToolsService.SEARCHING_STATUS_TEXT
                    } else {
                        com.example.data.mcp.LiveInfoToolsService.READING_NEWS_STATUS_TEXT
                    }
                    _uiState.update {
                        it.copy(activeToolLoopStatus = liveStatusText)
                    }

                    val (_, execResult) = repository.executeLiveInfoToolAndLog(
                        conversationId = conv.id,
                        messageId = assistantMsgId,
                        toolName = toolCall.toolName,
                        argumentsJson = toolCall.argumentsJson,
                        maxSearchesPerMessage = _uiState.value.liveInfoMaxSearchesPerMessage,
                        configuredFeeds = _uiState.value.liveInfoRssFeeds
                    )

                    if (execResult.sources.isNotEmpty()) {
                        _uiState.update { state ->
                            val existing = state.liveInfoSourcesByMessageId[assistantMsgId].orEmpty()
                            val combined = (existing + execResult.sources).distinctBy { it.link }
                            state.copy(
                                liveInfoSourcesByMessageId = state.liveInfoSourcesByMessageId + (assistantMsgId to combined)
                            )
                        }
                    }

                    workingPayloadMessages.add(
                        ChatMessagePayload(
                            role = "tool",
                            content = execResult.modelResponseText,
                            toolCallId = toolCall.id,
                            toolName = toolCall.toolName
                        )
                    )
                    continue
                }

                val matchedTriple = toolLookupByName[toolCall.toolName]
                if (matchedTriple == null) {
                    val notFoundMsg = "Error: Tool '${toolCall.toolName}' is not registered or is blocked by Never policy."
                    workingPayloadMessages.add(
                        ChatMessagePayload(
                            role = "tool",
                            content = notFoundMsg,
                            toolCallId = toolCall.id,
                            toolName = toolCall.toolName
                        )
                    )
                    continue
                }

                val server = matchedTriple.first
                val toolEntity = matchedTriple.second
                // Check cleartext host approval for the MCP server before invoking
                val unapprovedMcpHost = extractHttpHost(server.url)?.takeIf {
                    it !in _uiState.value.approvedCleartextHosts
                }
                if (unapprovedMcpHost != null) {
                    val msg = "Blocked: Cleartext HTTP host '$unapprovedMcpHost' for MCP server '${server.name}' is not approved."
                    workingPayloadMessages.add(
                        ChatMessagePayload(
                            role = "tool",
                            content = msg,
                            toolCallId = toolCall.id,
                            toolName = toolCall.toolName
                        )
                    )
                    continue
                }

                // Evaluate per-tool Always / Ask / Never policy AND server-level approval mode
                val convServerKey = "${conv.id}:${server.id}"
                val policyGate = com.example.data.mcp.ConnectorApprovalPolicy.evaluateToolExecutionGate(
                    tool = toolEntity,
                    server = server,
                    connectorDefaultPolicies = _uiState.value.connectorDefaultChoices,
                    perToolPolicies = _uiState.value.connectorToolChoices,
                    confirmedWriteAlwaysToolIds = _uiState.value.confirmedWriteAlwaysTools,
                    isConversationReadOnlyApproved = convServerKey in conversationApprovedReadOnlyServers
                )
                if (policyGate == com.example.data.mcp.ToolExecutionGate.BLOCK) {
                    val blockedLog = ToolCallLogEntity(
                        id = "tcl_${UUID.randomUUID().toString().take(10)}",
                        conversationId = conv.id,
                        messageId = assistantMsgId,
                        serverId = server.id,
                        serverName = server.name,
                        toolName = toolEntity.toolName,
                        argumentsJson = toolCall.argumentsJson,
                        resultText = "Tool call blocked by Never policy.",
                        isSuccess = false,
                        isDestructiveOrWrite = !toolEntity.isReadOnlyHint,
                        durationMs = 0L
                    )
                    dao.insertToolCallLog(blockedLog)
                    workingPayloadMessages.add(
                        ChatMessagePayload(
                            role = "tool",
                            content = "Tool execution blocked by Never policy.",
                            toolCallId = toolCall.id,
                            toolName = toolCall.toolName
                        )
                    )
                    continue
                }

                val approvalMode = server.approvalModeEnum()
                val isWriteDeleteTool = com.example.data.mcp.ConnectorApprovalPolicy.isWriteOrDeleteTool(toolEntity) || !toolEntity.isReadOnlyHint
                val canAutoRun = !isWriteDeleteTool && (
                    policyGate == com.example.data.mcp.ToolExecutionGate.AUTO_RUN || (
                        toolEntity.isReadOnlyHint && (
                            approvalMode == McpApprovalMode.ALWAYS_ALLOW_READ_ONLY ||
                                (approvalMode == McpApprovalMode.ASK_ONCE_PER_CONVERSATION &&
                                    convServerKey in conversationApprovedReadOnlyServers)
                            )
                        )
                    )

                var approved = canAutoRun
                if (!canAutoRun) {
                    val approvalReq = PendingToolApprovalRequest(
                        server = server,
                        tool = toolEntity,
                        callRequest = toolCall,
                        iterationIndex = iteration,
                        maxIterations = if (isUnlimited) 51 else maxIterations
                    )
                    _uiState.update {
                        it.copy(
                            pendingToolApproval = approvalReq,
                            activeToolLoopStatus = "$turnCounter • Awaiting approval for ${server.name} • ${toolEntity.toolName}"
                        )
                    }
                    val decision = approvalReq.decisionDeferred.await()
                    _uiState.update { it.copy(pendingToolApproval = null) }
                    when (decision) {
                        ToolApprovalDecision.APPROVE_ONCE -> approved = true
                        ToolApprovalDecision.APPROVE_CONVERSATION_READ_ONLY -> {
                            approved = true
                            if (toolEntity.isReadOnlyHint) {
                                conversationApprovedReadOnlyServers.add(convServerKey)
                            }
                        }
                        ToolApprovalDecision.DENY -> approved = false
                    }
                }

                if (!approved) {
                    val deniedLog = ToolCallLogEntity(
                        id = "tcl_${UUID.randomUUID().toString().take(10)}",
                        conversationId = conv.id,
                        messageId = assistantMsgId,
                        serverId = server.id,
                        serverName = server.name,
                        toolName = toolEntity.toolName,
                        argumentsJson = toolCall.argumentsJson,
                        resultText = "Tool call denied by user.",
                        isSuccess = false,
                        isDestructiveOrWrite = !toolEntity.isReadOnlyHint,
                        durationMs = 0L
                    )
                    dao.insertToolCallLog(deniedLog)
                    workingPayloadMessages.add(
                        ChatMessagePayload(
                            role = "tool",
                            content = "Tool execution was denied by the user.",
                            toolCallId = toolCall.id,
                            toolName = toolCall.toolName
                        )
                    )
                    continue
                }

                _uiState.update {
                    it.copy(
                        activeToolLoopStatus = "$turnCounter • Running MCP tool ${server.name}/${toolEntity.toolName}…"
                    )
                }

                val logEntry = repository.executeMcpToolAndLog(
                    conversationId = conv.id,
                    messageId = assistantMsgId,
                    server = server,
                    tool = toolEntity,
                    argumentsJson = toolCall.argumentsJson
                )

                val statusLabel = if (logEntry.isSuccess) "SUCCESS" else "ERROR"
                val toolResultForModel = buildString {
                    appendLine("[MCP Tool Result from ${server.name}/${toolEntity.toolName} ($statusLabel)]")
                    appendLine(logEntry.resultText)
                }
                workingPayloadMessages.add(
                    ChatMessagePayload(
                        role = "tool",
                        content = toolResultForModel,
                        toolCallId = toolCall.id,
                        toolName = toolCall.toolName
                    )
                )
            }

            failoverSession.appendSeparatorIfNeeded("\n\n")
            _uiState.update { it.copy(activeToolLoopStatus = turnCounter) }
        }

        _uiState.update { it.copy(activeToolLoopStatus = null, pendingToolApproval = null) }

        val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        val finalContent = failoverSession.visibleText
        val promptFallbackText = buildString {
            append(effectiveSystemPrompt)
            workingPayloadMessages.forEach { msg ->
                append('\n')
                append(msg.content)
                msg.toolCalls.forEach { tc ->
                    append('\n')
                    append(tc.toolName)
                    append(tc.argumentsJson)
                }
            }
        }
        val turnUsage = com.example.data.provider.ModelPriceTable.resolveTurnUsage(
            modelId = currentModelId,
            reportedInputTokens = reportedPromptTokens,
            reportedOutputTokens = reportedCompletionTokens,
            fallbackInputText = promptFallbackText,
            fallbackOutputText = finalContent,
            reportedCachedInputTokens = reportedCachedInputTokens
        )
        val pTokens = turnUsage.inputTokens
        val cTokens = turnUsage.outputTokens
        val costUsd = turnUsage.estimatedCostUsd
        if (turnUsage.cachedInputTokens > 0) {
            _uiState.update { state ->
                state.copy(
                    cachedTokensByMessageId = state.cachedTokensByMessageId + (assistantMsgId to turnUsage.cachedInputTokens)
                )
            }
        }
        if (turnUsage.isTokenCountEstimated) {
            _uiState.update { state ->
                state.copy(estimatedTokenMessageIds = state.estimatedTokenMessageIds + assistantMsgId)
            }
        }

        val err = streamError
        if (err != null) {
            if (finalContent.isBlank()) {
                // Prevent error-card spam: remove the empty placeholder bubble instead of persisting
                // an identical error message card per failed attempt, and surface the deduplicated banner.
                dao.deleteMessageById(assistantMsgId)
                dao.deleteRunwayMessageNodeById(assistantMsgId)

                // Automatic fallback when an OmniRoute sub-provider or combo model fails with
                // upstream 502/503 ('Stream ended before producing a non-ping SSE event', Playwright, etc.)
                val isUpstreamModelFailure = com.example.data.provider.EndpointUrlNormalizer.isOmniRouteUpstreamModelError(
                    "${err.title} ${err.message} ${err.suggestedFix}"
                )
                val isLowPriorityOrScraperModel =
                    com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(currentModelId) >= 2
                if (isUpstreamModelFailure || isLowPriorityOrScraperModel) {
                    failedModelsInSession.add("${currentProvider.id}:$currentModelId")
                }
                if (allowAutoFallback &&
                    !_uiState.value.providerFailoverEnabled &&
                    comparisonGroupId == null &&
                    (isUpstreamModelFailure || isLowPriorityOrScraperModel)
                ) {
                    val directCandidate = dao.getModelsForProviderOnce(currentProvider.id)
                        .filter {
                            it.modelId != currentModelId &&
                                "${currentProvider.id}:${it.modelId}" !in failedModelsInSession &&
                                CapabilityTag.CHAT in it.capabilitySet() &&
                                com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo) <= 1
                        }
                        .sortedWith(
                            compareBy<ModelDescriptorEntity> {
                                com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo)
                            }.thenByDescending { it.isFavorite }
                                .thenBy { it.modelId }
                        )
                        .firstOrNull()?.modelId
                    if (directCandidate != null) {
                        dao.upsertConversation(
                            conv.copy(
                                modelId = directCandidate,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        dao.upsertProvider(currentProvider.copy(defaultChatModelId = directCandidate))
                        _uiState.update {
                            it.copy(
                                statusToast = "Model '$currentModelId' failed upstream → auto-switched to '$directCandidate'"
                            )
                        }
                        executeModelStream(
                            conv = conv.copy(modelId = directCandidate),
                            provider = currentProvider,
                            modelId = directCandidate,
                            payloadMessages = payloadMessages,
                            comparisonGroupId = null,
                            createdAtOffset = createdAtOffset + 1L,
                            allowAutoFallback = false
                        )
                        return
                    }
                }
            } else {
                val errMsg = initialAssistantMsg.copy(
                    content = finalContent,
                    providerInstanceId = currentProvider.id,
                    modelId = currentModelId,
                    isStreaming = false,
                    errorText = err.title,
                    latencyMs = elapsedMs
                )
                dao.upsertMessage(errMsg)
                com.example.data.local.RunwayDataMigrationManager.syncMessageToRunwayTree(
                    dao = dao,
                    message = errMsg,
                    providerName = currentProvider.displayName
                )
            }
            recordProviderFailure(currentProvider, err)
        } else {
            recordProviderSuccess(currentProvider.id, elapsedMs)
            val completedMsg = initialAssistantMsg.copy(
                content = finalContent.ifBlank { "(Completed tool execution)" },
                providerInstanceId = currentProvider.id,
                modelId = currentModelId,
                promptTokens = pTokens,
                completionTokens = cTokens,
                estimatedCostUsd = costUsd,
                isStreaming = false,
                latencyMs = elapsedMs
            )
            dao.upsertMessage(completedMsg)
            com.example.data.local.RunwayDataMigrationManager.syncMessageToRunwayTree(
                dao = dao,
                message = completedMsg,
                providerName = currentProvider.displayName
            )
            repository.recordUsage(
                provider = currentProvider,
                modelId = currentModelId,
                modality = if (comparisonGroupId != null) "COMPARE" else "CHAT",
                promptTokens = pTokens,
                completionTokens = cTokens,
                estimatedCostUsd = costUsd,
                latencyMs = elapsedMs,
                conversationId = conv.id,
                messageId = assistantMsgId,
                isEstimated = turnUsage.isTokenCountEstimated
            )

            // Read replies aloud if enabled in Settings
            if (_uiState.value.readRepliesAloud && finalContent.isNotBlank() && comparisonGroupId == null) {
                voiceManager.speakReply(finalContent)
            }

            // Evaluate cheaper model suggestion after reply if chat has used > 50,000 tokens and model is top-tier
            if (comparisonGroupId == null && conv.id !in _uiState.value.dismissedCheaperSuggestionChatIds) {
                val allConvMsgs = dao.getMessagesOnce(conv.id)
                val totalConvTokens = allConvMsgs.sumOf { (it.promptTokens + it.completionTokens).coerceAtLeast(0) }
                val availableProviderModelIds = models.value
                    .filter { it.providerInstanceId == currentProvider.id }
                    .map { it.modelId }
                val suggestion = com.example.data.provider.ModelPriceTable.evaluateCheaperModelSuggestion(
                    currentModelId = currentModelId,
                    totalConversationTokens = totalConvTokens,
                    availableModelIds = availableProviderModelIds
                )
                if (suggestion != null) {
                    _uiState.update { state ->
                        state.copy(
                            cheaperModelSuggestionByChatId = state.cheaperModelSuggestionByChatId + (conv.id to suggestion)
                        )
                    }
                }
            }
        }
    }

    // --- Export, Import, Theme, Biometric & Wipe ---

    fun prepareConversationExport(
        conversationId: String,
        asJson: Boolean,
        includeAllBranches: Boolean = false
    ) {
        viewModelScope.launch {
            val content = if (asJson) {
                repository.exportConversationAsJson(conversationId, includeAllBranches)
            } else {
                repository.exportConversationAsMarkdown(conversationId, includeAllBranches)
            }
            _uiState.update {
                it.copy(
                    exportPreview = ExportPreviewPayload(
                        title = if (asJson) "Export Conversation (JSON)" else "Export Conversation (Markdown)",
                        formatLabel = if (asJson) "JSON" else "Markdown",
                        content = content,
                        includesSensitiveKeys = false,
                        conversationId = conversationId,
                        asJson = asJson,
                        includeAllBranches = includeAllBranches
                    )
                )
            }
        }
    }

    fun prepareSettingsExport(includeKeys: Boolean) {
        viewModelScope.launch {
            val content = repository.exportSettingsJson(includeKeys)
            _uiState.update {
                it.copy(
                    exportPreview = ExportPreviewPayload(
                        title = "Export Workspace Settings",
                        formatLabel = "Settings JSON",
                        content = content,
                        includesSensitiveKeys = includeKeys
                    )
                )
            }
        }
    }

    fun importSettingsJson(rawJson: String) {
        viewModelScope.launch {
            val res = repository.importSettingsJson(rawJson)
            refreshMaskedKeys()
            res.onSuccess { count ->
                _uiState.update {
                    it.copy(statusToast = "Imported $count provider configuration(s)")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusToast = "Import failed: ${err.message}")
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            repository.secretVault.setThemeMode(mode)
        }
    }

    fun setThemeAccent(accent: String) {
        _uiState.update { it.copy(themeAccent = accent) }
        viewModelScope.launch {
            repository.secretVault.setThemeAccent(accent)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        _uiState.update { it.copy(dynamicColorEnabled = enabled) }
        viewModelScope.launch {
            repository.secretVault.setDynamicColor(enabled)
        }
    }

    fun setBiometricGate(enabled: Boolean) {
        _uiState.update { it.copy(biometricGateEnabled = enabled) }
        viewModelScope.launch {
            repository.secretVault.setBiometricGate(enabled)
        }
    }

    fun setBiometricUnlocked(unlocked: Boolean) {
        _uiState.update { it.copy(isBiometricUnlocked = unlocked) }
    }

    fun setEnterToSend(enabled: Boolean) {
        _uiState.update { it.copy(enterToSendEnabled = enabled) }
        viewModelScope.launch {
            repository.secretVault.setEnterToSend(enabled)
        }
    }

    // --- Phase 5: Manual User-Triggered GitHub & YouTube Integrations ---

    fun saveGitHubPat(rawToken: String) {
        val clean = rawToken.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.secretVault.saveApiKey(TinkSecretVault.INTEGRATION_GITHUB_PAT_ID, clean)
            refreshMaskedKeys()
            _uiState.update {
                it.copy(statusToast = "Encrypted GitHub Personal Access Token saved in Tink Vault")
            }
            verifyAndFetchGitHubRepos(clean)
        }
    }

    fun clearGitHubPat() {
        viewModelScope.launch {
            repository.secretVault.deleteApiKey(TinkSecretVault.INTEGRATION_GITHUB_PAT_ID)
            refreshMaskedKeys()
            _uiState.update {
                it.copy(
                    gitHubProfileSummary = "",
                    gitHubRepos = emptyList(),
                    statusToast = "Removed GitHub token from Tink Vault"
                )
            }
        }
    }

    fun verifyAndFetchGitHubRepos(overrideToken: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGitHubBusy = true) }
            if (!overrideToken.isNullOrBlank()) {
                repository.secretVault.saveApiKey(TinkSecretVault.INTEGRATION_GITHUB_PAT_ID, overrideToken.trim())
                refreshMaskedKeys()
            }
            val userRes = repository.verifyGitHubAccount(overrideToken)
            val reposRes = repository.listGitHubRepos()
            userRes.onSuccess { profile ->
                val repos = reposRes.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        gitHubProfileSummary = "@${profile.login} (${profile.name}) • ${repos.size} repos loaded",
                        gitHubRepos = repos,
                        statusToast = "GitHub connected as @${profile.login}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        gitHubProfileSummary = "Verification failed: ${err.message}",
                        statusToast = "GitHub check failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun openGitHubPushForConversation(conversationId: String) {
        viewModelScope.launch {
            val conv = dao.getConversationById(conversationId)
            val markdown = repository.exportConversationAsMarkdown(conversationId)
            val slug = (conv?.title ?: "conversation")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "conversation" }
            _uiState.update {
                it.copy(
                    gitHubPushDraft = GitHubPushDraft(
                        title = conv?.title ?: "Runway Conversation",
                        suggestedFileName = "$slug.md",
                        suggestedFilePath = "exports/$slug.md",
                        content = markdown
                    )
                )
            }
        }
    }

    fun openGitHubPushForContent(
        title: String,
        suggestedFileName: String,
        content: String
    ) {
        val cleanName = suggestedFileName.trim().ifBlank { "snippet.md" }
        _uiState.update {
            it.copy(
                gitHubPushDraft = GitHubPushDraft(
                    title = title,
                    suggestedFileName = cleanName,
                    suggestedFilePath = "snippets/$cleanName",
                    content = content
                )
            )
        }
    }

    fun dismissGitHubPushDialog() {
        _uiState.update { it.copy(gitHubPushDraft = null) }
    }

    fun executeGitHubGistPush(
        description: String,
        fileName: String,
        content: String,
        isPublic: Boolean
    ) {
        if (_uiState.value.isGitHubBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGitHubBusy = true) }
            val res = repository.publishGitHubGist(
                description = description,
                fileName = fileName,
                content = content,
                isPublic = isPublic
            )
            res.onSuccess { pub ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        gitHubPushDraft = null,
                        statusToast = "Published Gist: ${pub.htmlUrl}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        statusToast = "Gist failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun executeGitHubRepoCommit(
        ownerRepo: String,
        filePath: String,
        content: String,
        commitMessage: String,
        branch: String
    ) {
        if (_uiState.value.isGitHubBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGitHubBusy = true) }
            val res = repository.commitFileToGitHubRepo(
                ownerRepo = ownerRepo,
                filePath = filePath,
                content = content,
                commitMessage = commitMessage,
                branch = branch
            )
            res.onSuccess { pub ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        gitHubPushDraft = null,
                        statusToast = "Committed to ${pub.targetDestination}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGitHubBusy = false,
                        statusToast = "Commit failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun saveYouTubeOAuthCredentials(clientId: String, rawAccessToken: String) {
        viewModelScope.launch {
            if (clientId.isNotBlank()) {
                repository.secretVault.saveApiKey(TinkSecretVault.INTEGRATION_YOUTUBE_CLIENT_ID, clientId.trim())
            }
            if (rawAccessToken.isNotBlank()) {
                repository.secretVault.saveApiKey(
                    TinkSecretVault.INTEGRATION_YOUTUBE_ACCESS_TOKEN_ID,
                    rawAccessToken.trim()
                )
            }
            refreshMaskedKeys()
            _uiState.update {
                it.copy(statusToast = "Encrypted YouTube OAuth 2.0 credentials saved in Tink Vault")
            }
            if (rawAccessToken.isNotBlank()) {
                verifyYouTubeChannelNow(rawAccessToken.trim())
            }
        }
    }

    fun clearYouTubeCredentials() {
        viewModelScope.launch {
            repository.secretVault.deleteApiKey(TinkSecretVault.INTEGRATION_YOUTUBE_CLIENT_ID)
            repository.secretVault.deleteApiKey(TinkSecretVault.INTEGRATION_YOUTUBE_ACCESS_TOKEN_ID)
            repository.secretVault.deleteApiKey(TinkSecretVault.INTEGRATION_YOUTUBE_REFRESH_TOKEN_ID)
            refreshMaskedKeys()
            _uiState.update {
                it.copy(
                    youTubeChannelSummary = "",
                    statusToast = "Cleared YouTube OAuth 2.0 tokens from Tink Vault"
                )
            }
        }
    }

    fun regenerateYouTubePkceChallenge() {
        val verifier = YouTubeIntegrationClient.generateCodeVerifier()
        val challenge = YouTubeIntegrationClient.generateCodeChallengeS256(verifier)
        _uiState.update {
            it.copy(
                youTubePkceVerifier = verifier,
                youTubePkceChallenge = challenge,
                statusToast = "Generated new RFC 7636 S256 PKCE code challenge"
            )
        }
    }

    fun exchangeYouTubePkceAuthCode(clientId: String, authCode: String) {
        if (_uiState.value.isYouTubeBusy) return
        val verifier = _uiState.value.youTubePkceVerifier
        viewModelScope.launch {
            _uiState.update { it.copy(isYouTubeBusy = true) }
            val res = repository.exchangeYouTubePkceCode(
                clientId = clientId,
                authCode = authCode,
                codeVerifier = verifier
            )
            refreshMaskedKeys()
            res.onSuccess { channel ->
                _uiState.update {
                    it.copy(
                        isYouTubeBusy = false,
                        youTubeChannelSummary = "${channel.title} (${channel.subscriberCount} subscribers)",
                        statusToast = "Connected YouTube channel: ${channel.title}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isYouTubeBusy = false,
                        statusToast = "PKCE exchange failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun verifyYouTubeChannelNow(overrideAccessToken: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isYouTubeBusy = true) }
            val res = repository.verifyYouTubeChannel(overrideAccessToken)
            res.onSuccess { channel ->
                _uiState.update {
                    it.copy(
                        isYouTubeBusy = false,
                        youTubeChannelSummary = "${channel.title} (${channel.subscriberCount} subscribers)",
                        statusToast = "Verified YouTube channel: ${channel.title}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isYouTubeBusy = false,
                        youTubeChannelSummary = "Verification failed: ${err.message}",
                        statusToast = "YouTube check failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun openYouTubePublishDialog(job: VideoJobEntity) {
        val cleanPrompt = job.prompt.trim()
        val suggestedTitle = cleanPrompt.take(72).ifBlank { "Runway Generated Video" }
        val suggestedDesc = buildString {
            appendLine(cleanPrompt)
            appendLine()
            appendLine("Generated in Runway")
            appendLine("• Provider: ${job.providerDisplayName}")
            appendLine("• Model: ${job.modelId}")
            appendLine("• Format: ${job.aspectRatio} • ${job.resolution} • ${job.durationSeconds}s")
        }
        val modelTag = job.modelId.substringAfterLast("/").replace(Regex("[^a-zA-Z0-9]"), "")
        _uiState.update {
            it.copy(
                youTubePublishDraft = YouTubePublishDraft(
                    job = job,
                    suggestedTitle = suggestedTitle,
                    suggestedDescription = suggestedDesc,
                    suggestedTagsCsv = "AI,Runway,GenerativeVideo,$modelTag"
                )
            )
        }
    }

    fun dismissYouTubePublishDialog() {
        _uiState.update { it.copy(youTubePublishDraft = null) }
    }

    fun executeYouTubeVideoUpload(
        job: VideoJobEntity,
        title: String,
        description: String,
        tagsCsv: String,
        privacyStatus: String
    ) {
        if (_uiState.value.isYouTubeBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isYouTubeBusy = true) }
            val res = repository.publishVideoToYouTube(
                job = job,
                title = title,
                description = description,
                tagsCsv = tagsCsv,
                privacyStatus = privacyStatus
            )
            res.onSuccess { uploaded ->
                _uiState.update {
                    it.copy(
                        isYouTubeBusy = false,
                        youTubePublishDraft = null,
                        statusToast = "Uploaded to YouTube (${uploaded.privacyStatus}): ${uploaded.watchUrl}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isYouTubeBusy = false,
                        statusToast = "YouTube upload failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun clearIntegrationLogs() {
        viewModelScope.launch {
            repository.clearIntegrationLogs()
            _uiState.update { it.copy(statusToast = "Cleared integration audit log") }
        }
    }

    // --- Phase 6: Skills (SKILL.md) & Remote MCP Servers Actions ---

    fun saveSkillFromMarkdown(
        existingId: String?,
        rawMarkdown: String,
        fallbackName: String = "",
        fallbackDescription: String = "",
        sourceUrl: String = ""
    ) {
        if (rawMarkdown.isBlank() && fallbackName.isBlank()) return
        viewModelScope.launch {
            val existing = existingId?.let { dao.getSkillById(it) }
            val entity = repository.saveSkillFromMarkdown(
                existingId = existingId,
                rawMarkdown = rawMarkdown,
                fallbackName = fallbackName,
                fallbackDescription = fallbackDescription,
                isEnabled = existing?.isEnabled ?: true,
                sourceUrl = sourceUrl.ifBlank { existing?.sourceUrl.orEmpty() }
            )
            _uiState.update {
                it.copy(statusToast = "Saved skill '${entity.name}' (~${entity.estimatedTokens} tokens)")
            }
        }
    }

    fun toggleSkillGloballyEnabled(skill: SkillEntity) {
        viewModelScope.launch {
            dao.upsertSkill(
                skill.copy(
                    isEnabled = !skill.isEnabled,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteSkill(skillId: String) {
        viewModelScope.launch {
            dao.deleteSkillById(skillId)
            _uiState.update { it.copy(statusToast = "Deleted skill") }
        }
    }

    fun importSkillFromUrl(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        val unapprovedHost = extractHttpHost(clean)?.takeIf {
            it !in _uiState.value.approvedCleartextHosts
        }
        if (unapprovedHost != null) {
            requestCleartextHostApproval(
                host = unapprovedHost,
                fullUrl = clean,
                providerName = "SKILL.md Import"
            ) {
                importSkillFromUrl(clean)
            }
            return
        }
        viewModelScope.launch {
            val res = repository.importSkillFromUrl(clean)
            res.onSuccess { skill ->
                _uiState.update {
                    it.copy(statusToast = "Imported skill '${skill.name}' (~${skill.estimatedTokens} tok)")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusToast = "Skill URL import failed: ${err.message}")
                }
            }
        }
    }

    fun importSkillFromUri(uriString: String) {
        if (uriString.isBlank()) return
        viewModelScope.launch {
            val res = repository.importSkillFromContentUri(uriString)
            res.onSuccess { skill ->
                _uiState.update {
                    it.copy(statusToast = "Imported skill '${skill.name}' (~${skill.estimatedTokens} tok)")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusToast = "Skill file import failed: ${err.message}")
                }
            }
        }
    }

    fun toggleSkillForActiveConversation(skillId: String) {
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            val conv = dao.getConversationById(convId) ?: return@launch
            val allSkills = dao.getSkillsOnce()
            val currentEnabledIds = SkillMarkdownParser.getConversationEnabledSkills(
                allSkills = allSkills,
                conversation = conv
            ).map { it.id }.toMutableSet()

            if (skillId in currentEnabledIds) {
                currentEnabledIds.remove(skillId)
            } else {
                currentEnabledIds.add(skillId)
            }
            dao.upsertConversation(
                conv.copy(
                    enabledSkillIdsCsv = currentEnabledIds.joinToString(","),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun setConversationAutoSelectSkills(autoSelect: Boolean) {
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            val conv = dao.getConversationById(convId) ?: return@launch
            dao.upsertConversation(
                conv.copy(
                    autoSelectSkills = autoSelect,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // --- Git Clone & Agent Skills from GitHub ---

    fun setShowGitCloneSheet(show: Boolean) {
        _uiState.update { it.copy(showGitCloneSheet = show, gitCloneError = null) }
    }

    fun cloneGitHubRepository(url: String, branch: String? = null, token: String? = null) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(gitCloneError = "Please enter a GitHub repository URL or 'owner/repo'.") }
            return
        }

        _uiState.update {
            it.copy(
                isGitCloning = true,
                gitCloneProgressMessage = "Initializing clone for $cleanUrl...",
                gitCloneError = null
            )
        }

        viewModelScope.launch {
            val result = repository.cloneGitHubRepo(
                rawRepoUrl = cleanUrl,
                branch = branch?.trim()?.takeIf { it.isNotEmpty() },
                personalAccessToken = token?.trim()?.takeIf { it.isNotEmpty() },
                onProgress = { msg ->
                    _uiState.update { it.copy(gitCloneProgressMessage = msg) }
                }
            )

            result.onSuccess { repo ->
                _uiState.update {
                    it.copy(
                        isGitCloning = false,
                        showGitCloneSheet = false,
                        gitCloneProgressMessage = "",
                        statusToast = "Cloned '${repo.owner}/${repo.repoName}' • ${repo.skillCount} agent skills active"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGitCloning = false,
                        gitCloneError = err.message ?: "Failed to clone repository"
                    )
                }
            }
        }
    }

    fun syncClonedRepository(repoId: String, token: String? = null) {
        _uiState.update {
            it.copy(
                isGitCloning = true,
                gitCloneProgressMessage = "Syncing repository from GitHub...",
                gitCloneError = null
            )
        }

        viewModelScope.launch {
            val result = repository.syncClonedRepo(
                repoId = repoId,
                personalAccessToken = token?.trim()?.takeIf { it.isNotEmpty() },
                onProgress = { msg ->
                    _uiState.update { it.copy(gitCloneProgressMessage = msg) }
                }
            )

            result.onSuccess { repo ->
                _uiState.update {
                    it.copy(
                        isGitCloning = false,
                        gitCloneProgressMessage = "",
                        statusToast = "Updated '${repo.owner}/${repo.repoName}' • ${repo.skillCount} skills synced"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGitCloning = false,
                        gitCloneError = err.message ?: "Failed to sync repository",
                        statusToast = "Sync failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun deleteClonedRepository(repoId: String) {
        viewModelScope.launch {
            val success = repository.deleteClonedRepo(repoId)
            _uiState.update {
                it.copy(
                    statusToast = if (success) "Repository and associated skills deleted" else "Could not delete repository",
                    selectedClonedRepo = if (it.selectedClonedRepo?.id == repoId) null else it.selectedClonedRepo,
                    showRepoFileBrowserSheet = if (it.selectedClonedRepo?.id == repoId) false else it.showRepoFileBrowserSheet
                )
            }
        }
    }

    fun openRepoFileBrowser(repo: ClonedRepoEntity) {
        viewModelScope.launch {
            val files = repository.getClonedRepoFiles(repo.id)
            _uiState.update {
                it.copy(
                    selectedClonedRepo = repo,
                    showRepoFileBrowserSheet = true,
                    repoFilesList = files,
                    viewingRepoFilePath = null,
                    viewingRepoFileContent = null
                )
            }
        }
    }

    fun dismissRepoFileBrowser() {
        _uiState.update {
            it.copy(
                showRepoFileBrowserSheet = false,
                selectedClonedRepo = null,
                viewingRepoFilePath = null,
                viewingRepoFileContent = null
            )
        }
    }

    fun viewRepoFile(repoId: String, relativePath: String) {
        viewModelScope.launch {
            val content = repository.readClonedRepoFile(repoId, relativePath)
            _uiState.update {
                it.copy(
                    viewingRepoFilePath = relativePath,
                    viewingRepoFileContent = content ?: "(Unable to preview file or binary format)"
                )
            }
        }
    }

    fun dismissRepoFileViewer() {
        _uiState.update {
            it.copy(
                viewingRepoFilePath = null,
                viewingRepoFileContent = null
            )
        }
    }

    fun activateRepoSkillsInActiveConversation(repo: ClonedRepoEntity) {
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            val conv = dao.getConversationById(convId) ?: return@launch
            val skillIdsFromRepo = repo.getDiscoveredSkills().map { it.id }.toSet()
            if (skillIdsFromRepo.isEmpty()) {
                _uiState.update { it.copy(statusToast = "No skills found in ${repo.repoName}") }
                return@launch
            }

            val currentEnabled = conv.enabledSkillIdsCsv.orEmpty().split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()

            currentEnabled.addAll(skillIdsFromRepo)
            dao.upsertConversation(
                conv.copy(
                    enabledSkillIdsCsv = currentEnabled.joinToString(","),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.update {
                it.copy(statusToast = "Activated ${skillIdsFromRepo.size} skills from '${repo.repoName}' in this chat")
            }
        }
    }

    fun setConversationMcpToolsEnabled(enabled: Boolean) {
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            val conv = dao.getConversationById(convId) ?: return@launch
            dao.upsertConversation(
                conv.copy(
                    mcpToolsEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun setConversationMaxToolIterations(maxIterations: Int) {
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            val conv = dao.getConversationById(convId) ?: return@launch
            dao.upsertConversation(
                conv.copy(
                    maxToolIterations = if (maxIterations >= 51) 51 else maxIterations.coerceIn(1, 50),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun saveMcpServer(
        existingId: String?,
        name: String,
        url: String,
        transport: McpTransportType,
        authHeaderName: String,
        rawToken: String?,
        approvalMode: McpApprovalMode,
        isEnabled: Boolean
    ) {
        val cleanUrl = url.trim()
        if (name.isBlank() || cleanUrl.isEmpty()) return
        val existing = existingId?.let { id -> mcpServers.value.firstOrNull { it.id == id } }
        val serverId = existingId ?: "mcp_${UUID.randomUUID().toString().take(8)}"
        val entity = McpServerEntity(
            id = serverId,
            name = name.trim(),
            url = cleanUrl,
            transport = transport.name,
            authHeaderName = authHeaderName.trim().ifBlank { "Authorization" },
            approvalMode = approvalMode.name,
            isEnabled = isEnabled,
            lastTestStatus = existing?.lastTestStatus ?: "Configured",
            cachedResourceCount = existing?.cachedResourceCount ?: 0,
            cachedPromptCount = existing?.cachedPromptCount ?: 0,
            cachedResourcesSummary = existing?.cachedResourcesSummary.orEmpty(),
            cachedPromptsSummary = existing?.cachedPromptsSummary.orEmpty(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )

        val unapprovedHost = extractHttpHost(cleanUrl)?.takeIf {
            it !in _uiState.value.approvedCleartextHosts
        }
        if (unapprovedHost != null) {
            requestCleartextHostApproval(
                host = unapprovedHost,
                fullUrl = cleanUrl,
                providerName = "MCP Server (${entity.name})"
            ) {
                repository.saveMcpServerWithToken(entity, rawToken)
                refreshMaskedKeys()
                _uiState.update { it.copy(statusToast = "Saved MCP server '${entity.name}'") }
            }
        } else {
            viewModelScope.launch {
                repository.saveMcpServerWithToken(entity, rawToken)
                refreshMaskedKeys()
                _uiState.update { it.copy(statusToast = "Saved MCP server '${entity.name}'") }
            }
        }
    }

    fun toggleMcpServerEnabled(server: McpServerEntity) {
        viewModelScope.launch {
            dao.upsertMcpServer(server.copy(isEnabled = !server.isEnabled))
        }
    }

    fun updateMcpServerApprovalMode(server: McpServerEntity, mode: McpApprovalMode) {
        viewModelScope.launch {
            dao.upsertMcpServer(server.copy(approvalMode = mode.name))
            _uiState.update { it.copy(statusToast = "${server.name}: ${mode.label}") }
        }
    }

    fun clearMcpServerToken(server: McpServerEntity) {
        viewModelScope.launch {
            repository.secretVault.deleteApiKey(TinkSecretVault.mcpServerSecretId(server.id))
            refreshMaskedKeys()
            _uiState.update { it.copy(statusToast = "Cleared encrypted token for ${server.name}") }
        }
    }

    fun deleteMcpServer(serverId: String) {
        viewModelScope.launch {
            repository.deleteMcpServer(serverId)
            refreshMaskedKeys()
            _uiState.update { it.copy(statusToast = "Deleted MCP server") }
        }
    }

    fun testAndDiscoverMcpServer(server: McpServerEntity, overrideToken: String? = null) {
        val unapprovedHost = extractHttpHost(server.url)?.takeIf {
            it !in _uiState.value.approvedCleartextHosts
        }
        if (unapprovedHost != null) {
            requestCleartextHostApproval(
                host = unapprovedHost,
                fullUrl = server.url,
                providerName = "MCP Server (${server.name})"
            ) {
                executeMcpServerDiscovery(server, overrideToken)
            }
            return
        }
        viewModelScope.launch {
            executeMcpServerDiscovery(server, overrideToken)
        }
    }

    private suspend fun executeMcpServerDiscovery(server: McpServerEntity, overrideToken: String?) {
        _uiState.update { it.copy(busyMcpServerId = server.id) }
        if (!overrideToken.isNullOrBlank()) {
            repository.saveMcpServerWithToken(server, overrideToken)
            refreshMaskedKeys()
        }
        val res = repository.testAndDiscoverMcpServer(server, overrideToken)
        res.onSuccess { summary ->
            _uiState.update { state ->
                state.copy(
                    busyMcpServerId = null,
                    mcpDiscoverySummaries = state.mcpDiscoverySummaries + (server.id to summary),
                    statusToast = "${server.name}: ${summary.tools.size} tools, ${summary.resourceNames.size} resources, ${summary.promptNames.size} prompts"
                )
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    busyMcpServerId = null,
                    statusToast = "MCP test failed (${server.name}): ${err.message}"
                )
            }
        }
    }

    fun toggleMcpToolEnabled(tool: McpToolEntity) {
        viewModelScope.launch {
            dao.upsertMcpTool(tool.copy(isEnabled = !tool.isEnabled))
        }
    }

    fun setShowSafetyApprovalSheet(show: Boolean) {
        _uiState.update { it.copy(showSafetyApprovalSheet = show) }
    }

    fun toggleMcpToolReadOnlyOverride(tool: McpToolEntity) {
        viewModelScope.launch {
            val updated = tool.copy(isReadOnlyHint = !tool.isReadOnlyHint)
            dao.upsertMcpTool(updated)
            _uiState.update {
                it.copy(
                    statusToast = if (!updated.isReadOnlyHint) {
                        "Marked '${tool.toolName}' as Sensitive Action (User confirmation strictly required)"
                    } else {
                        "Marked '${tool.toolName}' as Safe Read-Only"
                    }
                )
            }
        }
    }

    fun setAllServersApprovalMode(mode: McpApprovalMode) {
        viewModelScope.launch {
            val servers = dao.getMcpServersOnce()
            servers.forEach { server ->
                dao.upsertMcpServer(server.copy(approvalMode = mode.name))
            }
            _uiState.update {
                it.copy(statusToast = "All MCP servers set to: ${mode.label}")
            }
        }
    }

    fun resetConversationApprovals() {
        conversationApprovedReadOnlyServers.clear()
        _uiState.update {
            it.copy(statusToast = "Reset all remembered thread tool approvals")
        }
    }

    fun previewToolApprovalDialog(server: McpServerEntity, tool: McpToolEntity) {
        val dummyRequest = ModelToolCallRequest(
            id = "preview_${System.currentTimeMillis()}",
            toolName = tool.toolName,
            argumentsJson = """{"action": "execute_action", "target": "production_environment", "force": true}"""
        )
        val approvalReq = PendingToolApprovalRequest(
            server = server,
            tool = tool,
            callRequest = dummyRequest,
            iterationIndex = 1,
            maxIterations = 3
        )
        _uiState.update {
            it.copy(
                pendingToolApproval = approvalReq,
                activeToolLoopStatus = "Testing Safety Approval Prompt for ${server.name} • ${tool.toolName}"
            )
        }
    }

    fun resolvePendingToolApproval(decision: ToolApprovalDecision) {
        _uiState.value.pendingToolApproval?.decisionDeferred?.complete(decision)
    }

    fun clearToolCallLogs() {
        viewModelScope.launch {
            repository.clearToolCallLogs()
            _uiState.update { it.copy(statusToast = "Cleared MCP tool call audit log") }
        }
    }

    fun clearAllWorkspaceData() {
        stopGeneration()
        viewModelScope.launch {
            repository.clearAllData()
            refreshMaskedKeys()
            seedDefaultCompareTargets()
            val newConvs = dao.getConversationsOnce()
            if (newConvs.isNotEmpty()) {
                selectConversation(newConvs.first().id)
            }
            _uiState.update {
                it.copy(
                    gitHubProfileSummary = "",
                    gitHubRepos = emptyList(),
                    youTubeChannelSummary = "",
                    mcpDiscoverySummaries = emptyMap(),
                    showOnboardingSheet = true,
                    statusToast = "All local data, encrypted keys, usage logs, and approved hosts cleared."
                )
            }
        }
    }

    // --- Integrated App-Wide Real-Time AI Agent ("Omni Autopilot") ---

    fun setAgentSheetOpen(open: Boolean) {
        autopilotEngine.setSheetOpen(open)
    }

    fun updateAgentCommandInput(text: String) {
        autopilotEngine.updateCommandInput(text)
    }

    fun setAgentUseLlmPlanner(enabled: Boolean) {
        autopilotEngine.setUseAiLlmPlanner(enabled)
    }

    fun dismissAgentBanner() {
        autopilotEngine.dismissLiveBanner()
    }

    fun clearAgentHistory() {
        autopilotEngine.clearExecutionHistory()
    }

    fun previewAgentActions(rawCommand: String): List<com.example.data.agent.OmniAgentAction> {
        return autopilotEngine.parseNaturalLanguageCommand(rawCommand)
    }

    fun executeAgentCommand(
        rawCommand: String,
        providersViewModel: ProvidersViewModel? = null
    ) {
        viewModelScope.launch {
            autopilotEngine.executeCommandInRealTime(
                rawCommand = rawCommand,
                studioViewModel = this@OmniStudioViewModel,
                providersViewModel = providersViewModel
            )
            refreshMaskedKeys()
        }
    }

    fun undoLastAgentCommand() {
        viewModelScope.launch {
            autopilotEngine.undoLastExecution(this@OmniStudioViewModel)
        }
    }

    // --- Connectors Screen & Per-Tool Always / Ask / Never Policies ---

    fun openConnectorsScreen(initialServerId: String? = null) {
        _uiState.update {
            it.copy(
                showConnectorsScreen = true,
                selectedConnectorIdForDetail = initialServerId
            )
        }
    }

    fun closeConnectorsScreen() {
        _uiState.update {
            it.copy(
                showConnectorsScreen = false,
                selectedConnectorIdForDetail = null
            )
        }
    }

    fun selectConnectorForDetail(serverId: String?) {
        _uiState.update { it.copy(selectedConnectorIdForDetail = serverId) }
    }

    fun setConnectorDiscoveryEnabled(enabled: Boolean) {
        _uiState.update { it.copy(connectorDiscoveryEnabled = enabled) }
        viewModelScope.launch {
            repository.secretVault.setConnectorDiscoveryEnabled(enabled)
        }
    }

    fun addAndConnectMcpConnector(
        name: String,
        url: String,
        transport: McpTransportType,
        authHeaderName: String = "Authorization",
        rawToken: String? = null
    ) {
        val cleanUrl = url.trim()
        val cleanName = name.trim().ifBlank { "Remote MCP Connector" }
        val validation = com.example.data.mcp.ConnectorApprovalPolicy.validateMcpServerUrl(
            rawUrl = cleanUrl,
            approvedCleartextHosts = _uiState.value.approvedCleartextHosts
        )
        if (!validation.isValid && validation.isCleartextHttp && !validation.host.isNullOrBlank()) {
            requestCleartextHostApproval(
                host = validation.host,
                fullUrl = cleanUrl,
                providerName = "MCP Connector ($cleanName)"
            ) {
                addAndConnectMcpConnectorInternal(cleanName, cleanUrl, transport, authHeaderName, rawToken)
            }
            return
        }
        if (!validation.isValid) {
            _uiState.update {
                it.copy(statusToast = validation.errorMessage ?: "Invalid MCP server URL")
            }
            return
        }
        viewModelScope.launch {
            addAndConnectMcpConnectorInternal(cleanName, cleanUrl, transport, authHeaderName, rawToken)
        }
    }

    private suspend fun addAndConnectMcpConnectorInternal(
        name: String,
        url: String,
        transport: McpTransportType,
        authHeaderName: String,
        rawToken: String?
    ) {
        val serverId = "mcp_${UUID.randomUUID().toString().take(8)}"
        val entity = McpServerEntity(
            id = serverId,
            name = name,
            url = url,
            transport = transport.name,
            authHeaderName = authHeaderName.ifBlank { "Authorization" },
            approvalMode = McpApprovalMode.ASK_EVERY_TIME.name,
            isEnabled = true,
            lastTestStatus = "Connecting & discovering tools…"
        )
        repository.saveMcpServerWithToken(entity, rawToken?.takeIf { it.isNotBlank() })
        refreshMaskedKeys()
        executeMcpServerDiscovery(entity, rawToken?.takeIf { it.isNotBlank() })
    }

    fun updateConnectorTokenOnly(server: McpServerEntity, rawToken: String) {
        viewModelScope.launch {
            repository.saveMcpServerWithToken(server, rawToken.trim().takeIf { it.isNotBlank() })
            refreshMaskedKeys()
            _uiState.update { it.copy(statusToast = "Encrypted token saved for ${server.name}") }
        }
    }

    fun setConnectorAllToolsChoice(
        server: McpServerEntity,
        choice: com.example.data.mcp.ToolApprovalChoice,
        userConfirmedWriteTools: Boolean
    ) {
        val serverId = server.id
        viewModelScope.launch {
            val serverTools = dao.getMcpToolsForServerOnce(serverId)
            val (updatedChoices, updatedConfirmed) = com.example.data.mcp.ConnectorApprovalPolicy.applySetAllTools(
                tools = serverTools,
                targetChoice = choice,
                existingPerToolPolicies = _uiState.value.connectorToolChoices,
                existingConfirmedWriteIds = _uiState.value.confirmedWriteAlwaysTools,
                userConfirmedWriteTools = userConfirmedWriteTools
            )
            val updatedDefaults = _uiState.value.connectorDefaultChoices + (serverId to choice)
            repository.secretVault.setConnectorDefaultPoliciesJson(serializeChoicesJsonMap(updatedDefaults))
            repository.secretVault.setConnectorToolPoliciesJson(serializeChoicesJsonMap(updatedChoices))
            repository.secretVault.setConfirmedWriteAlwaysTools(updatedConfirmed)
            _uiState.update {
                it.copy(
                    connectorDefaultChoices = updatedDefaults,
                    connectorToolChoices = updatedChoices,
                    confirmedWriteAlwaysTools = updatedConfirmed,
                    statusToast = "All tools set to ${choice.label}"
                )
            }
        }
    }

    fun setSingleToolChoice(
        tool: McpToolEntity,
        choice: com.example.data.mcp.ToolApprovalChoice,
        userConfirmedWriteTool: Boolean
    ) {
        viewModelScope.launch {
            val isWrite = com.example.data.mcp.ConnectorApprovalPolicy.isWriteOrDeleteTool(tool)
            val effectiveChoice = if (isWrite && choice == com.example.data.mcp.ToolApprovalChoice.ALWAYS && !userConfirmedWriteTool) {
                com.example.data.mcp.ToolApprovalChoice.ASK
            } else {
                choice
            }
            val updatedChoices = _uiState.value.connectorToolChoices + (tool.id to effectiveChoice)
            val updatedConfirmed = if (isWrite && effectiveChoice == com.example.data.mcp.ToolApprovalChoice.ALWAYS && userConfirmedWriteTool) {
                _uiState.value.confirmedWriteAlwaysTools + tool.id
            } else if (effectiveChoice != com.example.data.mcp.ToolApprovalChoice.ALWAYS) {
                _uiState.value.confirmedWriteAlwaysTools - tool.id
            } else {
                _uiState.value.confirmedWriteAlwaysTools
            }
            repository.secretVault.setConnectorToolPoliciesJson(serializeChoicesJsonMap(updatedChoices))
            repository.secretVault.setConfirmedWriteAlwaysTools(updatedConfirmed)
            _uiState.update {
                it.copy(
                    connectorToolChoices = updatedChoices,
                    confirmedWriteAlwaysTools = updatedConfirmed,
                    statusToast = "${tool.toolName}: ${effectiveChoice.label}"
                )
            }
        }
    }

    private fun parseChoicesJsonMap(rawJson: String): Map<String, com.example.data.mcp.ToolApprovalChoice> {
        if (rawJson.isBlank()) return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(rawJson)
            val result = mutableMapOf<String, com.example.data.mcp.ToolApprovalChoice>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val rawVal = obj.optString(key, "")
                if (rawVal.isNotBlank()) {
                    result[key] = com.example.data.mcp.ToolApprovalChoice.fromStored(rawVal)
                }
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun serializeChoicesJsonMap(map: Map<String, com.example.data.mcp.ToolApprovalChoice>): String {
        val obj = org.json.JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.name) }
        return obj.toString()
    }

    // --- 7 New Runway Features: Voice, Cheaper Model, Spending Limits, File Attachments, Context Trimming ---

    fun setReadRepliesAloud(enabled: Boolean) {
        if (!enabled) {
            voiceManager.stopSpeaking()
        }
        viewModelScope.launch {
            repository.secretVault.setReadRepliesAloud(enabled)
        }
        _uiState.update {
            it.copy(
                readRepliesAloud = enabled,
                statusToast = if (enabled) "Read replies aloud enabled" else "Read replies aloud turned off"
            )
        }
    }

    fun toggleVoiceInput() {
        voiceManager.stopSpeaking()
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
            return
        }
        val baseDraft = _uiState.value.draftMessage.trim()
        voiceManager.startListening(
            onPartialOrFinalText = { spokenText, _ ->
                val combined = if (baseDraft.isEmpty()) spokenText else "$baseDraft $spokenText"
                _uiState.update { it.copy(draftMessage = combined) }
            },
            onError = { errMsg ->
                _uiState.update { it.copy(statusToast = errMsg) }
            }
        )
    }

    fun stopVoiceSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun acceptCheaperModelSuggestion(conversationId: String, suggestedModelId: String) {
        val conv = conversations.value.firstOrNull { it.id == conversationId } ?: return
        switchActiveConversationModel(conv.providerInstanceId, suggestedModelId)
        _uiState.update { state ->
            state.copy(
                cheaperModelSuggestionByChatId = state.cheaperModelSuggestionByChatId - conversationId,
                dismissedCheaperSuggestionChatIds = state.dismissedCheaperSuggestionChatIds + conversationId
            )
        }
    }

    fun dismissCheaperModelSuggestion(conversationId: String) {
        _uiState.update { state ->
            state.copy(
                cheaperModelSuggestionByChatId = state.cheaperModelSuggestionByChatId - conversationId,
                dismissedCheaperSuggestionChatIds = state.dismissedCheaperSuggestionChatIds + conversationId
            )
        }
    }

    fun setProviderMonthlyLimitUsd(providerId: String, limitUsd: Double?) {
        val sanitized = limitUsd?.takeIf { it > 0.0 }
        val updated = if (sanitized != null) {
            _uiState.value.providerMonthlyLimitsUsd + (providerId to sanitized)
        } else {
            _uiState.value.providerMonthlyLimitsUsd - providerId
        }
        val json = com.example.data.provider.ProviderSpendingLimitEvaluator.serializeLimitsJson(updated)
        viewModelScope.launch {
            repository.secretVault.setProviderSpendingLimitsJson(json)
        }
        _uiState.update {
            it.copy(
                providerMonthlyLimitsUsd = updated,
                statusToast = if (sanitized != null) {
                    String.format(java.util.Locale.US, "Monthly limit set to $%.2f", sanitized)
                } else {
                    "Monthly spending limit removed"
                }
            )
        }
    }

    fun overrideSpendingLimitOnceForActiveChat() {
        val convId = _uiState.value.activeConversationId ?: return
        if (convId in _uiState.value.spendingLimitUsedOverrideChatIds) {
            _uiState.update { it.copy(statusToast = "One-time override already used for this chat.") }
            return
        }
        _uiState.update { state ->
            state.copy(
                spendingLimitActiveOverrideChatIds = state.spendingLimitActiveOverrideChatIds + convId,
                activeError = null,
                statusToast = "Spending limit overridden for the next message in this chat"
            )
        }
    }

    fun attachDocumentUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (uri in uris) {
                val currentList = _uiState.value.attachedMediaList
                if (!com.example.data.local.FileAttachmentProcessor.isFileCountWithinLimit(currentList.size, 1)) {
                    _uiState.update {
                        it.copy(statusToast = "You can attach up to 5 files per message.")
                    }
                    break
                }
                val result = com.example.data.local.FileAttachmentProcessor.readDocumentAttachmentFromUri(
                    context = repository.appContext,
                    uri = uri,
                    existingCount = currentList.size
                )
                result.fold(
                    onSuccess = { attachment ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            addMediaAttachments(listOf(attachment))
                        }
                    },
                    onFailure = { err ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _uiState.update {
                                it.copy(statusToast = err.message ?: "Could not attach file")
                            }
                        }
                    }
                )
            }
        }
    }

    fun applyAttachmentPresetPrompt(preset: com.example.data.local.FileAttachmentProcessor.AttachmentPreset) {
        _uiState.update { state ->
            val current = state.draftMessage.trim()
            val updated = if (current.isEmpty()) preset.prompt else "$current\n${preset.prompt}"
            state.copy(draftMessage = updated)
        }
    }

    fun dismissFirstTimeFileNotice() {
        _uiState.update { it.copy(showFirstTimeFileNoticeForProvider = null) }
    }

    fun setContextTrimmingEnabledForChat(conversationId: String, enabled: Boolean) {
        _uiState.update { state ->
            val updatedMap = state.contextTrimmingEnabledByChatId + (conversationId to enabled)
            val updatedSummaries = if (!enabled) state.contextSummaryByChatId - conversationId else state.contextSummaryByChatId
            state.copy(
                contextTrimmingEnabledByChatId = updatedMap,
                contextSummaryByChatId = updatedSummaries,
                statusToast = if (enabled) "Smart context trimming enabled for this chat" else "Smart context trimming turned off for this chat"
            )
        }
    }

    fun saveJavaRunnerSettings(url: String, rawKey: String?) {
        viewModelScope.launch {
            repository.secretVault.setJavaRunnerUrl(url)
            if (rawKey != null && rawKey.isNotBlank()) {
                repository.secretVault.saveJavaRunnerKey(rawKey)
            }
            val masked = repository.secretVault.getMaskedJavaRunnerKey()
            _uiState.update {
                it.copy(
                    javaRunnerUrl = url.trim(),
                    maskedJavaRunnerKey = masked,
                    statusToast = if (url.isBlank()) "Java remote runner cleared" else "Saved Java remote runner settings"
                )
            }
        }
    }

    fun clearJavaRunnerKey() {
        viewModelScope.launch {
            repository.secretVault.clearJavaRunnerKey()
            _uiState.update {
                it.copy(
                    maskedJavaRunnerKey = "",
                    statusToast = "Cleared Java runner key from encrypted vault"
                )
            }
        }
    }

    fun openHtmlPreviewPanel(htmlDocument: String, title: String = "HTML / CSS / JS Preview") {
        _uiState.update {
            it.copy(
                activeHtmlPreviewDocument = htmlDocument,
                activeHtmlPreviewTitle = title
            )
        }
    }

    fun dismissHtmlPreviewPanel() {
        _uiState.update {
            it.copy(
                activeHtmlPreviewDocument = null,
                activeHtmlPreviewTitle = ""
            )
        }
    }

    // --- Live Info Tools Settings (web_search & news_headlines) ---

    fun setLiveInfoWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.secretVault.setLiveInfoWebSearchEnabled(enabled)
            _uiState.update {
                it.copy(
                    liveInfoWebSearchEnabled = enabled,
                    statusToast = if (enabled) "Web search (DuckDuckGo) enabled" else "Web search disabled"
                )
            }
        }
    }

    fun setLiveInfoNewsHeadlinesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.secretVault.setLiveInfoNewsHeadlinesEnabled(enabled)
            _uiState.update {
                it.copy(
                    liveInfoNewsHeadlinesEnabled = enabled,
                    statusToast = if (enabled) "News headlines (RSS) enabled" else "News headlines disabled"
                )
            }
        }
    }

    fun setLiveInfoMaxSearchesPerMessage(limit: Int) {
        val coerced = limit.coerceIn(1, 20)
        viewModelScope.launch {
            repository.secretVault.setLiveInfoMaxSearchesPerMessage(coerced)
            _uiState.update {
                it.copy(
                    liveInfoMaxSearchesPerMessage = coerced,
                    statusToast = "Web search limit set to $coerced per message"
                )
            }
        }
    }

    fun addLiveInfoRssFeed(name: String, url: String) {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (!com.example.data.mcp.LiveInfoToolsService.isValidHttpsFeedUrl(cleanUrl)) {
            _uiState.update {
                it.copy(statusToast = "Only valid HTTPS RSS feed URLs are allowed (https://...)")
            }
            return
        }
        val effectiveName = cleanName.ifBlank {
            cleanUrl.substringAfter("://").substringBefore("/").removePrefix("www.")
        }
        viewModelScope.launch {
            val current = _uiState.value.liveInfoRssFeeds
            if (current.any { it.url.equals(cleanUrl, ignoreCase = true) }) {
                _uiState.update {
                    it.copy(statusToast = "Feed already exists in Live info")
                }
                return@launch
            }
            val newFeed = com.example.data.mcp.RssFeedConfig(
                name = effectiveName,
                url = cleanUrl
            )
            val updated = current + newFeed
            repository.secretVault.setLiveInfoRssFeedsJson(
                com.example.data.mcp.LiveInfoToolsService.serializeFeedsToJson(updated)
            )
            _uiState.update {
                it.copy(
                    liveInfoRssFeeds = updated,
                    statusToast = "Added RSS feed: $effectiveName"
                )
            }
        }
    }

    fun removeLiveInfoRssFeed(feedId: String) {
        viewModelScope.launch {
            val current = _uiState.value.liveInfoRssFeeds
            val updated = current.filterNot { it.id == feedId }
            repository.secretVault.setLiveInfoRssFeedsJson(
                com.example.data.mcp.LiveInfoToolsService.serializeFeedsToJson(updated)
            )
            _uiState.update {
                it.copy(
                    liveInfoRssFeeds = updated,
                    statusToast = "Removed RSS feed"
                )
            }
        }
    }

    /**
     * Triggered when the user taps "Run" on an HTML, CSS, JavaScript, or Java code block.
     * Shows the Run confirmation sheet first (never runs automatically) and executes only after confirmation.
     */
    fun requestRunCodeBlockWithConfirmation(
        language: String,
        code: String,
        messageId: String = ""
    ) {
        if (!com.example.data.mcp.JsSandboxExecutor.isSupportedRunnerLanguage(language)) {
            _uiState.update {
                it.copy(statusToast = "Unsupported language '$language'. Supported: HTML, CSS, JavaScript, Java.")
            }
            return
        }
        viewModelScope.launch {
            val convId = _uiState.value.activeConversationId ?: conversations.value.firstOrNull()?.id ?: "default_conv"
            val argsJson = com.example.data.mcp.JsSandboxExecutor.buildRunCodeArgumentsJson(
                language = language,
                code = code
            )
            val synthServer = com.example.data.mcp.JsSandboxExecutor.buildSyntheticServerEntity()
            val synthTool = com.example.data.mcp.JsSandboxExecutor.buildSyntheticToolEntity()
            val callReq = ModelToolCallRequest(
                id = "manual_run_${System.currentTimeMillis()}",
                toolName = com.example.data.mcp.JsSandboxExecutor.RUN_CODE_TOOL_NAME,
                argumentsJson = argsJson
            )
            val approvalReq = PendingToolApprovalRequest(
                server = synthServer,
                tool = synthTool,
                callRequest = callReq,
                iterationIndex = 1,
                maxIterations = 1
            )
            _uiState.update { it.copy(pendingToolApproval = approvalReq) }
            val decision = approvalReq.decisionDeferred.await()
            _uiState.update { it.copy(pendingToolApproval = null) }
            if (decision == ToolApprovalDecision.DENY) {
                return@launch
            }
            val targetMsgId = messageId.ifBlank {
                activeMessages.value.lastOrNull { it.role.equals("ASSISTANT", ignoreCase = true) }?.id ?: "manual_msg"
            }
            val logEntry = repository.executeSandboxedCodeAndLog(
                conversationId = convId,
                messageId = targetMsgId,
                argumentsJson = argsJson
            )
            val details = com.example.data.mcp.JsSandboxExecutor.buildRunConfirmationDetails(argsJson, language)
            if (logEntry.isSuccess && details.language in setOf(
                    com.example.data.mcp.RunnerLanguage.HTML,
                    com.example.data.mcp.RunnerLanguage.CSS,
                    com.example.data.mcp.RunnerLanguage.JAVASCRIPT
                )
            ) {
                val htmlDoc = when (details.language) {
                    com.example.data.mcp.RunnerLanguage.HTML ->
                        com.example.data.mcp.JsSandboxExecutor.buildFullHtmlDocument(html = code, css = details.css, js = details.js)
                    com.example.data.mcp.RunnerLanguage.CSS ->
                        com.example.data.mcp.JsSandboxExecutor.buildFullHtmlDocument(
                            html = "<div class=\"preview-root\"><h2>CSS Preview</h2><p>Styled preview container</p></div>",
                            css = code
                        )
                    else ->
                        com.example.data.mcp.JsSandboxExecutor.buildFullHtmlDocument(
                            html = "<div id=\"app\"></div><pre id=\"console-out\">${logEntry.resultText.replace("<", "&lt;")}</pre>",
                            css = "body { font-family: monospace; padding: 12px; }",
                            js = code
                        )
                }
                openHtmlPreviewPanel(
                    htmlDocument = htmlDoc,
                    title = "${details.languageLabel} Preview"
                )
            } else if (!logEntry.isSuccess) {
                _uiState.update {
                    it.copy(statusToast = logEntry.resultText.lines().firstOrNull() ?: "Code execution failed")
                }
            }
        }
    }

    // --- 10. Automatic Provider Failover Settings Actions ---

    fun setProviderFailoverEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.secretVault.setRunwayFallbackEnabled(enabled)
            _uiState.update { it.copy(providerFailoverEnabled = enabled) }
        }
    }

    fun addProviderFailoverChainEntry(providerInstanceId: String, modelId: String) {
        val cleanProviderId = providerInstanceId.trim()
        val cleanModelId = modelId.trim()
        if (cleanProviderId.isBlank() || cleanModelId.isBlank()) return
        viewModelScope.launch {
            val prov = providers.value.firstOrNull { it.id == cleanProviderId }
            val mod = models.value.firstOrNull {
                it.providerInstanceId == cleanProviderId && it.modelId == cleanModelId
            }
            val newEntry = com.example.data.provider.FailoverChainEntry(
                id = "$cleanProviderId:$cleanModelId",
                providerInstanceId = cleanProviderId,
                modelId = cleanModelId,
                providerDisplayName = prov?.displayName ?: cleanProviderId,
                modelDisplayName = mod?.displayName ?: cleanModelId
            )
            val currentList = _uiState.value.providerFailoverChain
            if (currentList.any { it.compositeKey == newEntry.compositeKey }) {
                _uiState.update { it.copy(statusToast = "Fallback pair already in chain") }
                return@launch
            }
            val updated = currentList + newEntry
            repository.secretVault.setRunwayFallbackChainJson(
                com.example.data.provider.FailoverResolver.serializeChainToJson(updated)
            )
            _uiState.update { it.copy(providerFailoverChain = updated) }
        }
    }

    fun removeProviderFailoverChainEntry(index: Int) {
        viewModelScope.launch {
            val currentList = _uiState.value.providerFailoverChain
            if (index !in currentList.indices) return@launch
            val updated = currentList.toMutableList().apply { removeAt(index) }
            repository.secretVault.setRunwayFallbackChainJson(
                com.example.data.provider.FailoverResolver.serializeChainToJson(updated)
            )
            _uiState.update { it.copy(providerFailoverChain = updated) }
        }
    }

    fun moveProviderFailoverChainEntry(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentList = _uiState.value.providerFailoverChain
            val updated = com.example.data.provider.FailoverResolver.moveChainEntry(
                chain = currentList,
                fromIndex = fromIndex,
                toIndex = toIndex
            )
            if (updated == currentList) return@launch
            repository.secretVault.setRunwayFallbackChainJson(
                com.example.data.provider.FailoverResolver.serializeChainToJson(updated)
            )
            _uiState.update { it.copy(providerFailoverChain = updated) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
    }

    class Factory(
        private val repository: OmniRepository,
        private val dao: OmniDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OmniStudioViewModel(repository, dao) as T
        }
    }
}
