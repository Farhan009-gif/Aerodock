package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.McpApprovalMode
import com.example.data.local.OmniDatabase
import com.example.data.local.ProviderPresetType
import com.example.data.repository.OmniRepository
import com.example.data.security.BiometricAuthHelper
import com.example.data.security.TinkSecretVault
import com.example.groups.GroupsRoot
import com.example.ui.components.CleartextHostWarningDialog
import com.example.ui.components.CompareTargetsBottomSheet
import com.example.ui.components.ConversationSkillsAndToolsSheet
import com.example.ui.components.ExportPreviewDialog
import com.example.ui.components.GitHubPushDialog
import com.example.ui.components.HtmlCssJsPreviewPanelSheet
import com.example.ui.components.LucideIcons
import com.example.ui.components.McpSafetyApprovalSheet
import com.example.ui.components.MinimalButton
import com.example.ui.components.MinimalSegmentedControl
import com.example.ui.components.MinimalToast
import com.example.ui.components.ModelPickerBottomSheet
import com.example.ui.components.OmniAgentCommandCenterSheet
import com.example.ui.components.OmniAutopilotScreen
import com.example.ui.components.OmniRouteOnboardingSheet
import com.example.ui.components.ParameterTuningBottomSheet
import com.example.ui.components.PromptLibraryBottomSheet
import com.example.ui.components.TerminalAndHealthBottomSheet
import com.example.ui.components.ToolCallApprovalDialog
import com.example.ui.components.UsageAnalyticsScreen
import com.example.ui.components.YouTubePublishDialog
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.screens.ConnectorsScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.IntegrationsWorkspaceScreen
import com.example.ui.screens.MinimalSettingsAndThemeScreen
import com.example.ui.screens.PersistentMediaGalleryScreen
import com.example.ui.screens.ProvidersScreen
import com.example.ui.screens.RunwayHomeScreen
import com.example.ui.screens.SettingsWorkspaceScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.theme.OmniAIStudioTheme

import com.example.ui.viewmodel.OmniStudioViewModel
import com.example.ui.viewmodel.ProvidersUiState
import com.example.ui.viewmodel.ProvidersViewModel
import com.example.ui.viewmodel.WorkspaceTab
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    /** Bumped when an MCP OAuth redirect returns to the already-running app. */
    private var oauthCallbackTick by mutableStateOf(0)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        oauthCallbackTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = OmniDatabase.getInstance(applicationContext)
        val dao = database.omniDao()
        val secretVault = TinkSecretVault(applicationContext)
        val repository = OmniRepository(applicationContext, dao, secretVault)

        setContent {
            val viewModel: OmniStudioViewModel = viewModel(
                factory = OmniStudioViewModel.Factory(repository, dao)
            )
            // MCP OAuth redirects land here via OAuthCallbackActivity.
            LaunchedEffect(oauthCallbackTick) {
                com.example.data.mcp.McpOAuthCallbacks.consume()?.let { callbackUri ->
                    viewModel.handleMcpOAuthCallback(callbackUri)
                }
            }
            val providersViewModel: ProvidersViewModel = viewModel(
                factory = ProvidersViewModel.Factory(repository, secretVault, dao)
            )
            val providersUiState by providersViewModel.uiState.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val providers by viewModel.providers.collectAsStateWithLifecycle()
            val models by viewModel.models.collectAsStateWithLifecycle()
            val conversations by viewModel.conversations.collectAsStateWithLifecycle()
            val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
            val promptTemplates by viewModel.promptTemplates.collectAsStateWithLifecycle()
            val usageRecords by viewModel.usageRecords.collectAsStateWithLifecycle()
            val generatedImages by viewModel.generatedImages.collectAsStateWithLifecycle()
            val videoJobs by viewModel.videoJobs.collectAsStateWithLifecycle()
            val integrationLogs by viewModel.integrationLogs.collectAsStateWithLifecycle()
            val skills by viewModel.skills.collectAsStateWithLifecycle()
            val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
            val mcpTools by viewModel.mcpTools.collectAsStateWithLifecycle()
            val toolCallLogs by viewModel.toolCallLogs.collectAsStateWithLifecycle()
            val activeConversationToolLogs by viewModel.activeConversationToolLogs.collectAsStateWithLifecycle()
            val diagnosticEntries by viewModel.diagnosticEntries.collectAsStateWithLifecycle()
            val agentState by viewModel.agentState.collectAsStateWithLifecycle()
            val runwayFolders by viewModel.runwayFolders.collectAsStateWithLifecycle()
            val runwayConversationMetas by viewModel.runwayConversationMetas.collectAsStateWithLifecycle()
            val runwayAllMessageNodes by viewModel.runwayAllMessageNodes.collectAsStateWithLifecycle()
            val runwayAllDocuments by viewModel.runwayAllDocuments.collectAsStateWithLifecycle()
            val clonedRepos by viewModel.clonedRepos.collectAsStateWithLifecycle()

            OmniAIStudioTheme(
                themeMode = uiState.themeMode,
                themeAccent = uiState.themeAccent,
                dynamicColor = uiState.dynamicColorEnabled
            ) {
                var showInitialSplash by rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(450L)
                    showInitialSplash = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.biometricGateEnabled && !uiState.isBiometricUnlocked) {
                        BiometricLockScreen(
                            onUnlockRequest = {
                                BiometricAuthHelper.authenticate(
                                    activity = this@MainActivity,
                                    onSuccess = { viewModel.setBiometricUnlocked(true) },
                                    onError = { /* remains locked */ }
                                )
                            }
                        )
                    } else {
                        OmniStudioRoot(
                            uiState = uiState,
                            providers = providers,
                            models = models,
                            conversations = conversations,
                            activeMessages = activeMessages,
                            promptTemplates = promptTemplates,
                            usageRecords = usageRecords,
                            generatedImages = generatedImages,
                            videoJobs = videoJobs,
                            integrationLogs = integrationLogs,
                            skills = skills,
                            mcpServers = mcpServers,
                            mcpTools = mcpTools,
                            toolCallLogs = toolCallLogs,
                            activeConversationToolLogs = activeConversationToolLogs,
                            diagnosticEntries = diagnosticEntries,
                            runwayFolders = runwayFolders,
                            runwayConversationMetas = runwayConversationMetas,
                            runwayAllMessageNodes = runwayAllMessageNodes,
                            runwayAllDocuments = runwayAllDocuments,
                            clonedRepos = clonedRepos,
                            viewModel = viewModel,
                            providersViewModel = providersViewModel,
                            providersUiState = providersUiState,
                            agentState = agentState
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showInitialSplash,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
                    ) {
                        RunwaySplashScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun RunwaySplashScreen() {
    // Takeoff transition: the plane climbs up and to the right as the app loads.
    val takeoff = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        takeoff.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 620,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }
    val p = takeoff.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("runway_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            com.example.ui.components.RunwayLogoIcon(
                size = 84.dp,
                modifier = Modifier
                    .offset(x = (16 * p).dp, y = (-20 * p).dp)
                    .alpha(1f - 0.15f * p)
            )
            com.example.ui.components.RunwayWordmark(
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun BiometricLockScreen(
    onUnlockRequest: () -> Unit
) {
    LaunchedEffect(Unit) {
        onUnlockRequest()
    }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            com.example.ui.components.RunwayLogoIcon(size = 64.dp)
            Spacer(modifier = Modifier.height(12.dp))
            com.example.ui.components.RunwayWordmark()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Authenticate to unlock your encrypted keys and conversations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            MinimalButton(
                text = "Unlock",
                onClick = onUnlockRequest,
                filled = true,
                testTag = "biometric_unlock_button"
            )
        }
    }
}

enum class RunwayBottomTab(val index: Int) {
    CHAT(0),
    CREATE(1),
    AUTOPILOT(2),
    CONNECT(3),
    MORE(4);

    companion object {
        fun fromWorkspaceTab(tab: WorkspaceTab): RunwayBottomTab = when (tab) {
            WorkspaceTab.CHAT, WorkspaceTab.COMPARE -> CHAT
            WorkspaceTab.IMAGES, WorkspaceTab.VIDEOS -> CREATE
            WorkspaceTab.AUTOPILOT -> AUTOPILOT
            WorkspaceTab.INTEGRATIONS -> CONNECT
            WorkspaceTab.USAGE, WorkspaceTab.SETTINGS -> MORE
        }
    }
}

@Composable
private fun rememberSystemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun isOff(): Boolean {
        return try {
            val duration = android.provider.Settings.Global.getFloat(
                resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            val transition = android.provider.Settings.Global.getFloat(
                resolver,
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            duration == 0f || transition == 0f
        } catch (_: Exception) {
            false
        }
    }
    var disabled by remember { mutableStateOf(isOff()) }
    DisposableEffect(resolver) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                disabled = isOff()
            }
        }
        resolver.registerContentObserver(
            android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        resolver.registerContentObserver(
            android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE),
            false,
            observer
        )
        onDispose {
            resolver.unregisterContentObserver(observer)
        }
    }
    return disabled
}

private fun Modifier.tabSwipeGestures(
    enabled: Boolean = true,
    screenWidthPx: Float,
    canSwipeLeft: Boolean,
    canSwipeRight: Boolean,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit
): Modifier = if (!enabled) this else this.pointerInput(canSwipeLeft, canSwipeRight, screenWidthPx) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalDragX = 0f
        var totalDragY = 0f
        var isHorizontalDrag: Boolean? = null
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!change.pressed) {
                if (isHorizontalDrag == true) {
                    val velocity = velocityTracker.calculateVelocity().x
                    val quarterWidth = screenWidthPx * 0.25f
                    val minFlingDistance = touchSlop * 2f

                    // Swiping towards left (totalDragX < 0) advances to the tab further right (next)
                    if (canSwipeLeft && (totalDragX <= -quarterWidth || (totalDragX < -minFlingDistance && velocity <= -1000f))) {
                        onSwipeNext()
                    } else if (canSwipeRight && (totalDragX >= quarterWidth || (totalDragX > minFlingDistance && velocity >= 1000f))) {
                        // Swiping towards right (totalDragX > 0) goes back to the tab on the left (previous)
                        onSwipePrevious()
                    }
                }
                break
            }

            if (change.isConsumed && isHorizontalDrag != true) {
                // If a child (e.g. text selection handle or code sandbox horizontal scroll) already consumed the drag, yield
                break
            }

            val dragDelta = change.position - change.previousPosition
            totalDragX += dragDelta.x
            totalDragY += dragDelta.y
            velocityTracker.addPosition(change.uptimeMillis, change.position)

            if (isHorizontalDrag == null) {
                val absX = kotlin.math.abs(totalDragX)
                val absY = kotlin.math.abs(totalDragY)
                if (absY > touchSlop && absY > absX) {
                    // Vertical scrolling in chat or lists -> yield immediately without consuming
                    isHorizontalDrag = false
                    break
                } else if (absX > touchSlop && absX > absY * 1.5f) {
                    if ((totalDragX < 0 && canSwipeLeft) || (totalDragX > 0 && canSwipeRight)) {
                        isHorizontalDrag = true
                        change.consume()
                    } else {
                        isHorizontalDrag = false
                        break
                    }
                }
            } else if (isHorizontalDrag == true) {
                change.consume()
            }
        }
    }
}

@Composable
private fun OmniStudioRoot(
    uiState: com.example.ui.viewmodel.OmniUiState,
    providers: List<com.example.data.local.ProviderInstanceEntity>,
    models: List<com.example.data.local.ModelDescriptorEntity>,
    conversations: List<com.example.data.local.ConversationEntity>,
    activeMessages: List<com.example.data.local.MessageEntity>,
    promptTemplates: List<com.example.data.local.PromptTemplateEntity>,
    usageRecords: List<com.example.data.local.UsageRecordEntity>,
    generatedImages: List<com.example.data.local.GeneratedImageEntity>,
    videoJobs: List<com.example.data.local.VideoJobEntity>,
    integrationLogs: List<com.example.data.local.IntegrationAuditLogEntity>,
    skills: List<com.example.data.local.SkillEntity>,
    mcpServers: List<com.example.data.local.McpServerEntity>,
    mcpTools: List<com.example.data.local.McpToolEntity>,
    toolCallLogs: List<com.example.data.local.ToolCallLogEntity>,
    activeConversationToolLogs: List<com.example.data.local.ToolCallLogEntity>,
    diagnosticEntries: List<com.example.data.provider.RequestDiagnosticEntry> = emptyList(),
    runwayFolders: List<com.example.data.local.RunwayFolderEntity> = emptyList(),
    runwayConversationMetas: List<com.example.data.local.RunwayConversationMetaEntity> = emptyList(),
    runwayAllMessageNodes: List<com.example.data.local.RunwayMessageNodeEntity> = emptyList(),
    runwayAllDocuments: List<com.example.data.local.RunwayDocumentEntity> = emptyList(),
    clonedRepos: List<com.example.data.local.ClonedRepoEntity> = emptyList(),
    viewModel: OmniStudioViewModel,
    providersViewModel: ProvidersViewModel? = null,
    providersUiState: ProvidersUiState? = null,
    agentState: com.example.data.agent.OmniAgentUiState = com.example.data.agent.OmniAgentUiState()
) {
    // Sub-tab inside More: 0 = Usage, 1 = Keys, 2 = Settings & Theme
    var moreSubTab by rememberSaveable { mutableIntStateOf(1) }
    // Persistent Media Gallery mode inside Create tab (Images | Videos | Gallery)
    var showPersistentGallery by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // Concept A navigation: the app opens on the Home hub; sections are
    // entered from Home and return here via the back affordance / Back.
    var homeVisible by rememberSaveable { mutableStateOf(true) }
    // Groups feature (Phase 1): full-screen overlay destination entered from Home.
    var groupsVisible by rememberSaveable { mutableStateOf(false) }

    val rootFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val rootKeyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val rootView = androidx.compose.ui.platform.LocalView.current

    val anyOverlayOpen = agentState.isSheetOpen ||
        uiState.showOnboardingSheet ||
        uiState.showModelPickerSheet ||
        uiState.showCompareTargetSheet ||
        uiState.showTerminalAndHealthSheet ||
        uiState.showPromptLibrarySheet ||
        uiState.showParameterSheet ||
        uiState.showSkillsAndToolsSheet ||
        uiState.showGitCloneSheet ||
        uiState.showRepoFileBrowserSheet ||
        uiState.showSafetyApprovalSheet ||
        uiState.pendingToolApproval != null ||
        uiState.activeHtmlPreviewDocument != null ||
        uiState.pendingCleartextApproval != null ||
        uiState.exportPreview != null ||
        uiState.gitHubPushDraft != null ||
        uiState.youTubePublishDraft != null

    LaunchedEffect(uiState.currentTab, moreSubTab, anyOverlayOpen) {
        runCatching {
            rootFocusManager.clearFocus(force = true)
            rootKeyboardController?.hide()
            val imm = rootView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(rootView.windowToken, 0)
        }
    }

    // Sync More sub-tab when navigated via ViewModel or Agent
    LaunchedEffect(uiState.currentTab) {
        if (uiState.currentTab == WorkspaceTab.USAGE) {
            moreSubTab = 0
        } else if (uiState.currentTab == WorkspaceTab.SETTINGS && moreSubTab == 0) {
            moreSubTab = 1
        }
    }

    // Auto-dismiss status toast after 4s
    LaunchedEffect(uiState.statusToast) {
        if (!uiState.statusToast.isNullOrBlank()) {
            delay(4000)
            viewModel.clearToast()
        }
    }

    if (uiState.showConnectorsScreen) {
        BackHandler {
            viewModel.closeConnectorsScreen()
        }
    } else if (!homeVisible && !groupsVisible) {
        BackHandler {
            viewModel.setCompareModeEnabled(false)
            homeVisible = true
        }
    }

    val activeConversation = remember(conversations, uiState.activeConversationId) {
        conversations.firstOrNull { it.id == uiState.activeConversationId } ?: conversations.firstOrNull()
    }
    val activeProvider = remember(providers, activeConversation) {
        providers.firstOrNull { it.id == activeConversation?.providerInstanceId } ?: providers.firstOrNull()
    }
    val activeModelDescriptor = remember(models, activeConversation) {
        models.firstOrNull {
            it.providerInstanceId == activeConversation?.providerInstanceId &&
                it.modelId == activeConversation?.modelId
        }
    }
    val omniProvider = remember(providers) {
        providers.firstOrNull { it.presetEnum() == ProviderPresetType.OMNIROUTE }
    }

    // Modals & Bottom Sheets
    if (agentState.isSheetOpen) {
        OmniAgentCommandCenterSheet(
            agentState = agentState,
            studioUiState = uiState,
            activeModelName = activeModelDescriptor?.displayName ?: activeConversation?.modelId ?: "gpt-4o",
            encryptedKeysCount = uiState.maskedKeysByProvider.size,
            enabledSkillsCount = skills.count { it.isEnabled },
            previewParsedActions = viewModel::previewAgentActions,
            onCommandInputChange = viewModel::updateAgentCommandInput,
            onExecuteCommand = { cmd ->
                viewModel.executeAgentCommand(cmd, providersViewModel)
            },
            onToggleLlmPlanner = viewModel::setAgentUseLlmPlanner,
            onUndoLastCommand = viewModel::undoLastAgentCommand,
            onClearHistory = viewModel::clearAgentHistory,
            onDismiss = { viewModel.setAgentSheetOpen(false) }
        )
    }

    if (uiState.showOnboardingSheet) {
        OmniRouteOnboardingSheet(
            omniProvider = omniProvider,
            approvedHosts = uiState.approvedCleartextHosts,
            isBusy = uiState.busyProviderId == omniProvider?.id && omniProvider != null,
            extractHttpHost = viewModel::extractHttpHost,
            onTestAndFetch = { url, key ->
                if (omniProvider != null) {
                    viewModel.testAndFetchModels(omniProvider, url, key.takeIf { it.isNotBlank() })
                }
            },
            onComplete = { url, key, approveHost ->
                viewModel.completeOnboarding(url, key, approveHost)
            },
            onDismiss = {
                viewModel.setShowOnboardingSheet(false)
            }
        )
    }

    if (uiState.showModelPickerSheet) {
        ModelPickerBottomSheet(
            providers = providers,
            models = models,
            activeProviderId = activeConversation?.providerInstanceId,
            activeModelId = activeConversation?.modelId,
            searchQuery = uiState.modelPickerSearchQuery,
            onSearchQueryChange = viewModel::updateModelPickerSearchQuery,
            onSelectModel = viewModel::switchActiveConversationModel,
            onToggleFavorite = viewModel::toggleFavoriteModel,
            onAddManualModel = viewModel::addManualModel,
            onFetchModelsForProvider = { prov -> viewModel.testAndFetchModels(prov) },
            isFetchingProviderId = uiState.busyProviderId,
            omniRouteCombos = uiState.omniRouteCombos,
            onUpsertCustomCombo = { rawId, displayName, modality, strategy, targetModels, description, additionalProviderId ->
                viewModel.upsertOmniRouteCustomCombo(
                    rawId = rawId,
                    displayName = displayName,
                    modality = modality,
                    strategy = strategy,
                    targetModels = targetModels,
                    description = description,
                    selectInStudio = true,
                    additionalProviderId = additionalProviderId
                )
            },
            onDeleteCustomCombo = viewModel::deleteOmniRouteCustomCombo,
            onDismiss = { viewModel.setShowModelPickerSheet(false) }
        )
    }

    if (uiState.showCompareTargetSheet) {
        CompareTargetsBottomSheet(
            providers = providers,
            models = models,
            selectedTargets = uiState.compareTargets,
            onToggleTarget = viewModel::toggleModelInCompareTargets,
            onDismiss = { viewModel.setShowCompareTargetSheet(false) }
        )
    }

    if (uiState.showTerminalAndHealthSheet) {
        TerminalAndHealthBottomSheet(
            terminalLogs = uiState.omniRouteTerminalLogs,
            serverStatus = uiState.localhostServerStatus,
            modelHealthByKey = uiState.modelHealthByKey,
            isScanning = uiState.isScanningModelsHealth,
            providers = providers,
            models = models,
            onSendCommand = viewModel::executeTerminalCommand,
            onStartLocalhost = viewModel::startLocalhostOmniRoute,
            onStopLocalhost = viewModel::stopLocalhostOmniRoute,
            onScanHealthy = viewModel::scanHealthyProvidersAndModels,
            onSelectHealthyModel = { provId, modId ->
                viewModel.switchActiveConversationModel(provId, modId)
                viewModel.setShowTerminalAndHealthSheet(false)
            },
            onDismiss = { viewModel.setShowTerminalAndHealthSheet(false) }
        )
    }

    if (uiState.showPromptLibrarySheet) {
        PromptLibraryBottomSheet(
            templates = promptTemplates,
            searchQuery = uiState.promptSearchQuery,
            onSearchQueryChange = viewModel::updatePromptSearchQuery,
            onApplyTemplate = viewModel::applyPromptTemplate,
            onToggleFavorite = viewModel::toggleFavoritePromptTemplate,
            onSaveTemplate = viewModel::savePromptTemplate,
            onDeleteTemplate = viewModel::deletePromptTemplate,
            onDismiss = { viewModel.setShowPromptLibrarySheet(false) }
        )
    }

    if (uiState.showParameterSheet) {
        ParameterTuningBottomSheet(
            conversation = activeConversation,
            modelDescriptor = activeModelDescriptor,
            onSaveParameters = viewModel::updateConversationParameters,
            onDismiss = { viewModel.setShowParameterSheet(false) }
        )
    }

    if (uiState.showSkillsAndToolsSheet) {
        ConversationSkillsAndToolsSheet(
            conversation = activeConversation,
            skills = skills,
            mcpServers = mcpServers,
            mcpTools = mcpTools,
            supportsFunctionCalling = activeModelDescriptor?.supportsFunctionCalling ?: true,
            onToggleConversationSkill = viewModel::toggleSkillForActiveConversation,
            onSetAutoSelectSkills = viewModel::setConversationAutoSelectSkills,
            onSetMcpToolsEnabled = viewModel::setConversationMcpToolsEnabled,
            onSetMaxToolIterations = viewModel::setConversationMaxToolIterations,
            onToggleMcpToolEnabled = viewModel::toggleMcpToolEnabled,
            onOpenIntegrationsHub = {
                viewModel.setShowSkillsAndToolsSheet(false)
                viewModel.selectTab(WorkspaceTab.INTEGRATIONS)
            },
            onOpenSafetyApprovalSheet = {
                viewModel.setShowSkillsAndToolsSheet(false)
                viewModel.setShowSafetyApprovalSheet(true)
            },
            onCloneRepoFromGit = {
                viewModel.setShowSkillsAndToolsSheet(false)
                viewModel.setShowGitCloneSheet(true)
            },
            onDismiss = { viewModel.setShowSkillsAndToolsSheet(false) }
        )
    }

    if (uiState.showGitCloneSheet) {
        com.example.ui.components.GitCloneBottomSheet(
            isCloning = uiState.isGitCloning,
            progressMessage = uiState.gitCloneProgressMessage,
            errorMessage = uiState.gitCloneError,
            defaultToken = uiState.maskedGitHubPat,
            onClone = viewModel::cloneGitHubRepository,
            onDismiss = { viewModel.setShowGitCloneSheet(false) }
        )
    }

    if (uiState.showRepoFileBrowserSheet && uiState.selectedClonedRepo != null) {
        com.example.ui.components.RepoFileBrowserBottomSheet(
            repo = uiState.selectedClonedRepo,
            filesList = uiState.repoFilesList,
            viewingFilePath = uiState.viewingRepoFilePath,
            viewingFileContent = uiState.viewingRepoFileContent,
            onViewFile = { path ->
                viewModel.viewRepoFile(uiState.selectedClonedRepo.id, path)
            },
            onBackToList = viewModel::dismissRepoFileViewer,
            onDismiss = viewModel::dismissRepoFileBrowser
        )
    }

    if (uiState.showSafetyApprovalSheet) {
        McpSafetyApprovalSheet(
            mcpServers = mcpServers,
            mcpTools = mcpTools,
            onUpdateServerApprovalMode = viewModel::updateMcpServerApprovalMode,
            onToggleToolReadOnlyOverride = viewModel::toggleMcpToolReadOnlyOverride,
            onSetAllServersMode = viewModel::setAllServersApprovalMode,
            onResetConversationApprovals = viewModel::resetConversationApprovals,
            onPreviewApprovalDialog = viewModel::previewToolApprovalDialog,
            onDismiss = { viewModel.setShowSafetyApprovalSheet(false) }
        )
    }

    uiState.pendingToolApproval?.let { approvalReq ->
        ToolCallApprovalDialog(
            request = approvalReq,
            onDecision = viewModel::resolvePendingToolApproval
        )
    }

    uiState.activeHtmlPreviewDocument?.let { previewDoc ->
        HtmlCssJsPreviewPanelSheet(
            htmlDocument = previewDoc,
            title = uiState.activeHtmlPreviewTitle,
            onDismiss = viewModel::dismissHtmlPreviewPanel
        )
    }

    uiState.pendingCleartextApproval?.let { pending ->
        CleartextHostWarningDialog(
            pending = pending,
            onApprove = viewModel::confirmPendingCleartextHost,
            onCancel = viewModel::dismissPendingCleartextHost
        )
    }

    uiState.exportPreview?.let { preview ->
        ExportPreviewDialog(
            payload = preview,
            onDismiss = viewModel::dismissExportPreview,
            onCopiedToast = { /* handled via clipboard feedback */ },
            onChangeConversationExportOptions = viewModel::prepareConversationExport
        )
    }

    uiState.gitHubPushDraft?.let { ghDraft ->
        GitHubPushDialog(
            draft = ghDraft,
            maskedGitHubPat = uiState.maskedGitHubPat,
            repos = uiState.gitHubRepos,
            isBusy = uiState.isGitHubBusy,
            onSavePatInline = viewModel::saveGitHubPat,
            onFetchRepos = { viewModel.verifyAndFetchGitHubRepos(null) },
            onConfirmCreateGist = viewModel::executeGitHubGistPush,
            onConfirmRepoCommit = viewModel::executeGitHubRepoCommit,
            onDismiss = viewModel::dismissGitHubPushDialog
        )
    }

    uiState.youTubePublishDraft?.let { ytDraft ->
        YouTubePublishDialog(
            draft = ytDraft,
            maskedYouTubeToken = uiState.maskedYouTubeAccessToken,
            youTubeChannelSummary = uiState.youTubeChannelSummary,
            isBusy = uiState.isYouTubeBusy,
            onSaveAccessTokenInline = { token ->
                viewModel.saveYouTubeOAuthCredentials(uiState.youTubeClientId, token)
            },
            onConfirmUpload = { title, desc, tagsCsv, privacy ->
                viewModel.executeYouTubeVideoUpload(
                    job = ytDraft.job,
                    title = title,
                    description = desc,
                    tagsCsv = tagsCsv,
                    privacyStatus = privacy
                )
            },
            onDismiss = viewModel::dismissYouTubePublishDialog
        )
    }

    val isChatSelected = uiState.currentTab == WorkspaceTab.CHAT || uiState.currentTab == WorkspaceTab.COMPARE
    val isCreateSelected = uiState.currentTab == WorkspaceTab.IMAGES || uiState.currentTab == WorkspaceTab.VIDEOS
    val isAutopilotSelected = uiState.currentTab == WorkspaceTab.AUTOPILOT
    val isConnectSelected = uiState.currentTab == WorkspaceTab.INTEGRATIONS
    val isMoreSelected = uiState.currentTab == WorkspaceTab.USAGE || uiState.currentTab == WorkspaceTab.SETTINGS

    val currentPrimaryTab = remember(uiState.currentTab) {
        RunwayBottomTab.fromWorkspaceTab(uiState.currentTab)
    }
    val areAnimationsDisabled = rememberSystemAnimationsDisabled()
    val saveableStateHolder = rememberSaveableStateHolder()

    fun switchToBottomTab(tab: RunwayBottomTab) {
        homeVisible = false
        viewModel.setCompareModeEnabled(false)
        when (tab) {
            RunwayBottomTab.CHAT -> viewModel.selectTab(WorkspaceTab.CHAT)
            RunwayBottomTab.CREATE -> {
                if (uiState.currentTab != WorkspaceTab.VIDEOS) {
                    viewModel.selectTab(WorkspaceTab.IMAGES)
                }
            }
            RunwayBottomTab.AUTOPILOT -> viewModel.selectTab(WorkspaceTab.AUTOPILOT)
            RunwayBottomTab.CONNECT -> viewModel.selectTab(WorkspaceTab.INTEGRATIONS)
            RunwayBottomTab.MORE -> {
                if (moreSubTab == 0) {
                    viewModel.selectTab(WorkspaceTab.USAGE)
                } else {
                    viewModel.selectTab(WorkspaceTab.SETTINGS)
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val canSwipeLeft = currentPrimaryTab.index < RunwayBottomTab.entries.size - 1
        val canSwipeRight = currentPrimaryTab.index > 0

        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Round 17: on the Chat tab the section header is hidden - its
                // back arrow moves into the chat's own top bar, saving a full row.
                val chatTabActive = !homeVisible && !groupsVisible && !uiState.showConnectorsScreen &&
                    currentPrimaryTab == RunwayBottomTab.CHAT
                if (!homeVisible && !groupsVisible && !chatTabActive) {
                    RunwaySectionHeader(
                        title = if (uiState.showConnectorsScreen) {
                            "Connectors"
                        } else {
                            when (currentPrimaryTab) {
                                RunwayBottomTab.CHAT -> "Chat"
                                RunwayBottomTab.CREATE -> "Create studio"
                                RunwayBottomTab.AUTOPILOT -> "Autopilot"
                                RunwayBottomTab.CONNECT -> "Connect"
                                RunwayBottomTab.MORE -> "More"
                            }
                        },
                        onBack = {
                            if (uiState.showConnectorsScreen) {
                                viewModel.closeConnectorsScreen()
                            } else {
                                viewModel.setCompareModeEnabled(false)
                                homeVisible = true
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tabSwipeGestures(
                            enabled = !homeVisible && !groupsVisible && !uiState.showConnectorsScreen,
                            screenWidthPx = screenWidthPx,
                            canSwipeLeft = canSwipeLeft,
                            canSwipeRight = canSwipeRight,
                            onSwipeNext = {
                                val nextIndex = currentPrimaryTab.index + 1
                                if (nextIndex < RunwayBottomTab.entries.size) {
                                    switchToBottomTab(RunwayBottomTab.entries[nextIndex])
                                }
                            },
                            onSwipePrevious = {
                                val prevIndex = currentPrimaryTab.index - 1
                                if (prevIndex >= 0) {
                                    switchToBottomTab(RunwayBottomTab.entries[prevIndex])
                                }
                            }
                        )
                ) {
                    // Round 15: section switches (Home <-> Chat and friends) get the
                    // same slide+fade the tab switches already had, not a hard cut.
                    val sectionIndex = when {
                        homeVisible -> 0
                        groupsVisible -> 1
                        uiState.showConnectorsScreen -> 3
                        else -> 2
                    }
                    AnimatedContent(
                        targetState = sectionIndex,
                        transitionSpec = {
                            if (areAnimationsDisabled) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val direction = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    initialOffsetX = { fullWidth -> direction * fullWidth }
                                ) + fadeIn(
                                    animationSpec = tween(durationMillis = 150)
                                )) togetherWith (slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    targetOffsetX = { fullWidth -> -direction * fullWidth }
                                ) + fadeOut(
                                    animationSpec = tween(durationMillis = 150)
                                ))
                            }
                        },
                        label = "section_switch_animation",
                        modifier = Modifier.fillMaxSize()
                    ) { section ->
                    when (section) {
                        0 -> {
                        RunwayHomeScreen(
                            models = models,
                            conversations = conversations,
                            activeConversation = activeConversation,
                            enabledSkillsCount = skills.count { it.isEnabled },
                            mcpToolsCount = mcpTools.size,
                            onOpenChat = { switchToBottomTab(RunwayBottomTab.CHAT) },
                            onSendFromHome = { viewModel.sendFromHome(uiState.draftMessage); switchToBottomTab(RunwayBottomTab.CHAT) },
                            draftText = uiState.draftMessage,
                            onDraftChange = viewModel::updateDraftMessage,
                            attachedMedia = uiState.attachedMediaList,
                            onMediaPicked = viewModel::addMediaAttachments,
                            onRemoveAttachment = viewModel::removeMediaAttachment,
                            onOpenSkills = { viewModel.setShowSkillsAndToolsSheet(true) },
                            onToggleVoice = viewModel::toggleVoiceInput,
                            onOpenCreate = { switchToBottomTab(RunwayBottomTab.CREATE) },
                            onOpenAutopilot = { switchToBottomTab(RunwayBottomTab.AUTOPILOT) },
                            onOpenConnect = { switchToBottomTab(RunwayBottomTab.CONNECT) },
                            onOpenMore = { switchToBottomTab(RunwayBottomTab.MORE) },
                            onOpenGroups = {
                                groupsVisible = true
                                homeVisible = false
                            },
                            onOpenConversation = { conversation ->
                                viewModel.selectConversation(conversation.id)
                                switchToBottomTab(RunwayBottomTab.CHAT)
                            },
                            onSelectModel = viewModel::switchActiveConversationModel
                        )
                        }
                        1 -> {
                        GroupsRoot(
                            onExit = {
                                groupsVisible = false
                                homeVisible = true
                            }
                        )
                        }
                        3 -> {
                        ConnectorsScreen(
                            mcpServers = mcpServers,
                            mcpTools = mcpTools,
                            connectorDiscoveryEnabled = uiState.connectorDiscoveryEnabled,
                            selectedConnectorId = uiState.selectedConnectorIdForDetail,
                            connectorDefaultPolicies = uiState.connectorDefaultChoices,
                            perToolPolicies = uiState.connectorToolChoices,
                            confirmedWriteAlwaysToolIds = uiState.confirmedWriteAlwaysTools,
                            maskedTokensByServer = uiState.maskedMcpTokensByServer,
                            oauthStatusByServer = uiState.mcpOAuthStatusByServer,
                            approvedCleartextHosts = uiState.approvedCleartextHosts,
                            busyServerId = uiState.busyMcpServerId,
                            onBack = viewModel::closeConnectorsScreen,
                            onSelectConnector = viewModel::selectConnectorForDetail,
                            onToggleConnectorDiscovery = viewModel::setConnectorDiscoveryEnabled,
                            onAddAndConnectMcpServer = viewModel::addAndConnectMcpConnector,
                            onToggleConnectorConnected = viewModel::toggleMcpServerEnabled,
                            onTestConnectionAndDiscover = viewModel::testAndDiscoverMcpServer,
                            onUpdateConnectorToken = viewModel::updateConnectorTokenOnly,
                            onClearConnectorToken = viewModel::clearMcpServerToken,
                            onOAuthSignIn = viewModel::signInMcpOAuth,
                            onOAuthDisconnect = viewModel::disconnectMcpOAuth,
                            onDeleteConnector = viewModel::deleteMcpServer,
                            onSetAllToolsChoice = viewModel::setConnectorAllToolsChoice,
                            onSetSingleToolChoice = viewModel::setSingleToolChoice
                        )
                        }
                        else -> {
                        AnimatedContent(
                            targetState = currentPrimaryTab,
                            transitionSpec = {
                                if (areAnimationsDisabled) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    val direction = if (targetState.index > initialState.index) 1 else -1
                                    (slideInHorizontally(
                                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> direction * fullWidth }
                                    ) + fadeIn(
                                        animationSpec = tween(durationMillis = 150)
                                    )) togetherWith (slideOutHorizontally(
                                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> -direction * fullWidth }
                                    ) + fadeOut(
                                        animationSpec = tween(durationMillis = 150)
                                    ))
                                }
                            },
                            label = "tab_slide_animation",
                            modifier = Modifier.fillMaxSize()
                        ) { targetTab ->
                            saveableStateHolder.SaveableStateProvider(targetTab) {
                                when (targetTab) {
                                    RunwayBottomTab.CHAT -> {
                            ChatWorkspaceScreen(
                                uiState = uiState,
                                providers = providers,
                                models = models,
                                conversations = conversations,
                                messages = activeMessages,
                                onBackToHome = {
                                    viewModel.setCompareModeEnabled(false)
                                    homeVisible = true
                                },
                                skills = skills,
                                mcpTools = mcpTools,
                                conversationToolLogs = activeConversationToolLogs,
                                folders = runwayFolders,
                                conversationMetas = runwayConversationMetas,
                                allMessageNodes = runwayAllMessageNodes,
                                allDocuments = runwayAllDocuments,
                                usageRecords = usageRecords,
                                onSelectConversation = viewModel::selectConversation,
                                onNewConversation = { viewModel.createNewConversation() },
                                onContinueLatestConversation = viewModel::continueLatestConversation,
                                onTogglePinConversation = viewModel::togglePinConversation,
                                onRenameConversation = viewModel::renameConversation,
                                onDuplicateConversation = viewModel::duplicateConversation,
                                onDeleteConversation = viewModel::deleteConversation,
                                onUndoDeleteConversation = viewModel::undoDeleteConversation,
                                onExportConversation = { id, asJson ->
                                    viewModel.prepareConversationExport(id, asJson, false)
                                },
                                onExportConversationWithBranches = viewModel::prepareConversationExport,
                                onSelectFolderFilter = viewModel::selectHistoryFolderFilter,
                                onMoveConversationToFolder = viewModel::moveConversationToFolder,
                                onSaveFolder = viewModel::saveRunwayFolder,
                                onDeleteFolder = viewModel::deleteRunwayFolder,
                                onHistorySearchChange = viewModel::updateHistorySearchQuery,
                                onOpenModelPicker = { viewModel.setShowModelPickerSheet(true) },
                                onOpenParameterSheet = { viewModel.setShowParameterSheet(true) },
                                onOpenPromptLibrary = { viewModel.setShowPromptLibrarySheet(true) },
                                onOpenSkillsAndToolsSheet = { viewModel.setShowSkillsAndToolsSheet(true) },
                                onOpenGitClone = { viewModel.setShowGitCloneSheet(true) },
                                onOpenCompareTargetPicker = { viewModel.setShowCompareTargetSheet(true) },
                                onToggleCompareMode = viewModel::setCompareModeEnabled,
                                onOpenOnboardingGuide = { viewModel.setShowOnboardingSheet(true) },
                                onDraftChange = viewModel::updateDraftMessage,
                                onAttachImage = viewModel::attachImage,
                                onAddMediaAttachments = viewModel::addMediaAttachments,
                                onAttachDocumentUris = viewModel::attachDocumentUris,
                                onApplyAttachmentPreset = viewModel::applyAttachmentPresetPrompt,
                                onDismissFirstTimeFileNotice = viewModel::dismissFirstTimeFileNotice,
                                onRemoveMediaAttachment = viewModel::removeMediaAttachment,
                                onClearMediaAttachments = viewModel::clearMediaAttachments,
                                onSendMessage = viewModel::sendMessage,
                                onStopGeneration = viewModel::stopGeneration,
                                onRegenerateLast = viewModel::regenerateLastResponse,
                                onStartEditMessage = viewModel::startEditingMessage,
                                onCancelEditMessage = viewModel::cancelEditingMessage,
                                onApproveCleartextHostFromError = { host ->
                                    viewModel.approveCleartextHostManually(host)
                                    viewModel.dismissError()
                                },
                                onDismissError = viewModel::dismissError,
                                onCopiedToast = { /* clipboard copy */ },
                                onPushConversationToGitHub = viewModel::openGitHubPushForConversation,
                                onPushContentToGitHub = viewModel::openGitHubPushForContent,
                                onSelectProvider = viewModel::selectProviderForActiveChat,
                                onSelectModel = viewModel::switchActiveConversationModel,
                                onRunPreflightCheck = viewModel::runProviderPreflightCheck,
                                onSwitchToDirectModel = { provId ->
                                    viewModel.runOmniRouteCleanTerminal(provId)
                                    viewModel.switchToDirectModelForProvider(provId)
                                },
                                onOpenProviderSettings = {
                                    moreSubTab = 1
                                    viewModel.selectTab(WorkspaceTab.SETTINGS)
                                },
                                onOpenTerminalAndHealthSheet = { viewModel.setShowTerminalAndHealthSheet(true) },
                                onOpenConnectors = { viewModel.openConnectorsScreen(null) },
                                onToggleVoiceInput = viewModel::toggleVoiceInput,
                                onStopVoiceSpeaking = viewModel::stopVoiceSpeaking,
                                onAcceptCheaperModelSuggestion = viewModel::acceptCheaperModelSuggestion,
                                onDismissCheaperModelSuggestion = viewModel::dismissCheaperModelSuggestion,
                                onOverrideSpendingLimitOnce = viewModel::overrideSpendingLimitOnceForActiveChat,
                                onSetContextTrimmingEnabled = viewModel::setContextTrimmingEnabledForChat,
                                onCancelBackgroundSubagents = viewModel::cancelBackgroundSubagentsForConversation,
                                onQueueMessage = viewModel::queueMessage,
                                onRemoveQueuedMessage = viewModel::removeQueuedMessage,
                                onSetWorkMode = viewModel::setWorkMode,
                                onGenerateMediaInChat = viewModel::generateMediaInChat,
                                onUpsertCustomCombo = { rawId, displayName, strategy, targetModels, description ->
                                    viewModel.upsertOmniRouteCustomCombo(
                                        rawId = rawId,
                                        displayName = displayName,
                                        modality = "CHAT",
                                        strategy = strategy,
                                        targetModels = targetModels,
                                        description = description,
                                        selectInStudio = true,
                                        additionalProviderId = activeConversation?.providerInstanceId
                                    )
                                },
                                onRunCodeBlock = { lang, code, msgId ->
                                    viewModel.requestRunCodeBlockWithConfirmation(lang, code, msgId.orEmpty())
                                },
                                onOpenHtmlPreview = viewModel::openHtmlPreviewPanel
                            )
                        }

                        RunwayBottomTab.CREATE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                // Segmented Control (Images | Videos | Gallery) inside the Create screen
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    MinimalSegmentedControl(
                                        options = listOf("Images", "Videos", "Gallery"),
                                        selectedIndex = when {
                                            showPersistentGallery -> 2
                                            uiState.currentTab == WorkspaceTab.VIDEOS -> 1
                                            else -> 0
                                        },
                                        onSelect = { idx ->
                                            when (idx) {
                                                0 -> {
                                                    showPersistentGallery = false
                                                    viewModel.selectTab(WorkspaceTab.IMAGES)
                                                }
                                                1 -> {
                                                    showPersistentGallery = false
                                                    viewModel.selectTab(WorkspaceTab.VIDEOS)
                                                }
                                                2 -> {
                                                    showPersistentGallery = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when {
                                        showPersistentGallery -> {
                                            PersistentMediaGalleryScreen(
                                                generatedImages = generatedImages,
                                                videoJobs = videoJobs,
                                                onReuseImageSettings = { image ->
                                                    viewModel.reuseImageSettings(image)
                                                    showPersistentGallery = false
                                                    viewModel.selectTab(WorkspaceTab.IMAGES)
                                                },
                                                onReuseVideoSettings = { job ->
                                                    viewModel.reuseVideoSettings(job)
                                                    showPersistentGallery = false
                                                    viewModel.selectTab(WorkspaceTab.VIDEOS)
                                                },
                                                onUseImageForImg2Img = { uri ->
                                                    viewModel.attachImageToImageSource(uri)
                                                    showPersistentGallery = false
                                                    viewModel.selectTab(WorkspaceTab.IMAGES)
                                                },
                                                onUseImageForVideoStartFrame = { uri ->
                                                    viewModel.attachVideoSourceImage(uri)
                                                    showPersistentGallery = false
                                                    viewModel.selectTab(WorkspaceTab.VIDEOS)
                                                },
                                                onToggleFavoriteImage = viewModel::toggleFavoriteGeneratedImage,
                                                onToggleFavoriteVideo = viewModel::toggleFavoriteVideoJob,
                                                onDownloadImage = viewModel::downloadGeneratedImageToDevice,
                                                onDownloadVideo = viewModel::downloadVideoJobToDevice,
                                                onDeleteImage = viewModel::deleteGeneratedImage,
                                                onDeleteVideo = viewModel::deleteVideoJob,
                                                onInspectVideoInPlayer = viewModel::setInspectingVideoJob
                                            )
                                        }
                                        uiState.currentTab == WorkspaceTab.IMAGES -> {
                                            ImageStudioScreen(
                                                uiState = uiState,
                                                providers = providers,
                                                models = models,
                                                generatedImages = generatedImages,
                                                onSelectProviderAndModel = viewModel::selectImageProviderAndModel,
                                                onPromptChange = viewModel::updateImagePrompt,
                                                onNegativePromptChange = viewModel::updateImageNegativePrompt,
                                                onSelectAspectRatio = viewModel::selectImageAspectRatio,
                                                onUpdateCount = viewModel::updateImageCount,
                                                onUpdateSeedText = viewModel::updateImageSeedText,
                                                onRandomizeSeed = viewModel::randomizeImageSeed,
                                                onSelectStylePreset = viewModel::selectImageStylePreset,
                                                onUpdateGuidanceScale = viewModel::updateImageGuidanceScale,
                                                onUpdateSteps = viewModel::updateImageSteps,
                                                onUpdateQuality = viewModel::updateImageQuality,
                                                onAttachSourceImage = viewModel::attachImageToImageSource,
                                                onUpdateStrength = viewModel::updateImageStrength,
                                                onGenerateImage = viewModel::generateStudioImage,
                                                onInspectImage = viewModel::setInspectingImage,
                                                onReuseSettings = viewModel::reuseImageSettings,
                                                onToggleFavoriteImage = viewModel::toggleFavoriteGeneratedImage,
                                                onDeleteImage = viewModel::deleteGeneratedImage,
                                                onDownloadImage = viewModel::downloadGeneratedImageToDevice,
                                                onAddManualModel = { provId, modId ->
                                                    viewModel.addManualModel(provId, modId, false)
                                                },
                                                onApproveCleartextHostFromError = { host ->
                                                    viewModel.approveCleartextHostManually(host)
                                                    viewModel.dismissError()
                                                },
                                                onDismissError = viewModel::dismissError,
                                                onOpenProviderSettings = {
                                                    moreSubTab = 1
                                                    viewModel.selectTab(WorkspaceTab.SETTINGS)
                                                },
                                                onUpsertOmniRouteCombo = { rawId, displayName, strategy, targetModels, description ->
                                                    viewModel.upsertOmniRouteCustomCombo(
                                                        rawId = rawId,
                                                        displayName = displayName,
                                                        modality = "IMAGE",
                                                        strategy = strategy,
                                                        targetModels = targetModels,
                                                        description = description,
                                                        selectInStudio = true
                                                    )
                                                }
                                            )
                                        }
                                        else -> {
                                            VideoStudioScreen(
                                                uiState = uiState,
                                                providers = providers,
                                                models = models,
                                                videoJobs = videoJobs,
                                                onSelectProviderAndModel = viewModel::selectVideoProviderAndModel,
                                                onPromptChange = viewModel::updateVideoPrompt,
                                                onNegativePromptChange = viewModel::updateVideoNegativePrompt,
                                                onSelectAspectRatio = viewModel::selectVideoAspectRatio,
                                                onSelectResolution = viewModel::selectVideoResolution,
                                                onSelectDuration = viewModel::selectVideoDuration,
                                                onSelectCameraMotion = viewModel::selectVideoCameraMotion,
                                                onAttachSourceImage = viewModel::attachVideoSourceImage,
                                                onSubmitVideoJob = viewModel::submitStudioVideoJob,
                                                onPollJobNow = viewModel::pollVideoJobNow,
                                                onCancelJob = viewModel::cancelVideoJob,
                                                onInspectJob = viewModel::setInspectingVideoJob,
                                                onReuseSettings = viewModel::reuseVideoSettings,
                                                onToggleFavoriteJob = viewModel::toggleFavoriteVideoJob,
                                                onDeleteJob = viewModel::deleteVideoJob,
                                                onDownloadJob = viewModel::downloadVideoJobToDevice,
                                                onAddManualModel = { provId, modId ->
                                                    viewModel.addCustomModelToProvider(
                                                        providerId = provId,
                                                        rawModelId = modId,
                                                        displayName = modId,
                                                        modality = "VIDEO",
                                                        isCombo = modId.contains("combo", ignoreCase = true),
                                                        selectInStudio = true
                                                    )
                                                },
                                                onApproveCleartextHostFromError = { host ->
                                                    viewModel.approveCleartextHostManually(host)
                                                    viewModel.dismissError()
                                                },
                                                onDismissError = viewModel::dismissError,
                                                onPublishToYouTube = viewModel::openYouTubePublishDialog,
                                                onUpsertOmniRouteCombo = { rawId, displayName, strategy, targetModels, description ->
                                                    viewModel.upsertOmniRouteCustomCombo(
                                                        rawId = rawId,
                                                        displayName = displayName,
                                                        modality = "VIDEO",
                                                        strategy = strategy,
                                                        targetModels = targetModels,
                                                        description = description,
                                                        selectInStudio = true
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        RunwayBottomTab.AUTOPILOT -> {
                            OmniAutopilotScreen(
                                agentState = agentState,
                                studioUiState = uiState,
                                activeProviderName = activeProvider?.displayName ?: "OmniRoute",
                                activeModelName = (activeModelDescriptor?.displayName ?: activeConversation?.modelId ?: "gpt-4o").substringAfterLast("/"),
                                skills = skills,
                                mcpTools = mcpTools,
                                mcpServers = mcpServers,
                                maxToolIterations = activeConversation?.maxToolIterations ?: 4,
                                previewParsedActions = viewModel::previewAgentActions,
                                onCommandInputChange = viewModel::updateAgentCommandInput,
                                onExecuteCommand = { cmd ->
                                    viewModel.executeAgentCommand(cmd, providersViewModel)
                                },
                                onToggleDaemon = { run ->
                                    if (run) viewModel.startLocalhostOmniRoute() else viewModel.stopLocalhostOmniRoute()
                                },
                                onToggleSkill = viewModel::toggleSkillGloballyEnabled,
                                onToggleMcpTool = viewModel::toggleMcpToolEnabled,
                                onSetMaxToolIterations = viewModel::setConversationMaxToolIterations,
                                onToggleLlmPlanner = viewModel::setAgentUseLlmPlanner,
                                onSetSafetyRiskyPrompt = { alwaysAsk ->
                                    viewModel.setAllServersApprovalMode(
                                        if (alwaysAsk) McpApprovalMode.ASK_EVERY_TIME else McpApprovalMode.ALWAYS_ALLOW_READ_ONLY
                                    )
                                },
                                onUndoLastCommand = viewModel::undoLastAgentCommand,
                                onClearHistory = viewModel::clearAgentHistory
                            )
                        }

                        RunwayBottomTab.CONNECT -> {
                            IntegrationsWorkspaceScreen(
                                uiState = uiState,
                                skills = skills,
                                mcpServers = mcpServers,
                                mcpTools = mcpTools,
                                toolCallLogs = toolCallLogs,
                                integrationLogs = integrationLogs,
                                onSaveSkillMarkdown = viewModel::saveSkillFromMarkdown,
                                onToggleSkillEnabled = viewModel::toggleSkillGloballyEnabled,
                                onDeleteSkill = viewModel::deleteSkill,
                                onImportSkillFromUrl = viewModel::importSkillFromUrl,
                                onImportSkillFromUri = viewModel::importSkillFromUri,
                                onSaveMcpServer = viewModel::saveMcpServer,
                                onToggleMcpServerEnabled = viewModel::toggleMcpServerEnabled,
                                onUpdateMcpServerApprovalMode = viewModel::updateMcpServerApprovalMode,
                                onClearMcpServerToken = viewModel::clearMcpServerToken,
                                onDeleteMcpServer = viewModel::deleteMcpServer,
                                onTestAndDiscoverMcpServer = viewModel::testAndDiscoverMcpServer,
                                onToggleMcpToolEnabled = viewModel::toggleMcpToolEnabled,
                                onClearToolCallLogs = viewModel::clearToolCallLogs,
                                onSaveGitHubPat = viewModel::saveGitHubPat,
                                onClearGitHubPat = viewModel::clearGitHubPat,
                                onVerifyGitHub = viewModel::verifyAndFetchGitHubRepos,
                                onOpenQuickGitHubGist = {
                                    viewModel.openGitHubPushForContent(
                                        title = "Aerodock Workspace Note",
                                        suggestedFileName = "runway-note.md",
                                        content = "# Aerodock Note\n\nCreated manually from Connect."
                                    )
                                },
                                onSaveYouTubeCredentials = viewModel::saveYouTubeOAuthCredentials,
                                onClearYouTubeCredentials = viewModel::clearYouTubeCredentials,
                                onRegeneratePkce = viewModel::regenerateYouTubePkceChallenge,
                                onExchangePkceCode = viewModel::exchangeYouTubePkceAuthCode,
                                onVerifyYouTubeChannel = viewModel::verifyYouTubeChannelNow,
                                onClearIntegrationLogs = viewModel::clearIntegrationLogs,
                                onOpenSafetyApprovalSheet = { viewModel.setShowSafetyApprovalSheet(true) },
                                onOpenConnectors = { viewModel.openConnectorsScreen(null) },
                                clonedRepos = clonedRepos,
                                onOpenGitClone = { viewModel.setShowGitCloneSheet(true) },
                                onSyncClonedRepo = viewModel::syncClonedRepository,
                                onDeleteClonedRepo = viewModel::deleteClonedRepository,
                                onExploreRepoFiles = viewModel::openRepoFileBrowser,
                                onActivateRepoSkillsInChat = viewModel::activateRepoSkillsInActiveConversation
                            )
                        }

                        RunwayBottomTab.MORE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                // Segmented Control (Usage | Keys | Settings) inside the More tab
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    MinimalSegmentedControl(
                                        options = listOf("Usage", "Keys", "Settings"),
                                        selectedIndex = moreSubTab,
                                        onSelect = { idx ->
                                            moreSubTab = idx
                                            if (idx == 0) {
                                                viewModel.selectTab(WorkspaceTab.USAGE)
                                            } else {
                                                viewModel.selectTab(WorkspaceTab.SETTINGS)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when (moreSubTab) {
                                        0 -> {
                                            UsageAnalyticsScreen(
                                                usageRecords = usageRecords,
                                                providers = providers,
                                                latencySamples = uiState.latencyBenchmarkHistory,
                                                fastestProvider = uiState.fastestProviderRank,
                                                speedRanks = uiState.speedRanks,
                                                isProbingLatency = uiState.isProbingLatency,
                                                autoProbeIntervalSeconds = uiState.autoProbeIntervalSeconds,
                                                spendCurrency = uiState.spendCurrency,
                                                usdToInrRate = uiState.usdToInrRate,
                                                onRunProbe = viewModel::runLatencyBenchmarkProbe,
                                                onSetAutoProbeInterval = viewModel::setAutoProbeIntervalSeconds,
                                                onClearLatencyHistory = viewModel::clearLatencyHistory,
                                                onSelectFastestModel = { provId, modId ->
                                                    viewModel.switchActiveConversationModel(provId, modId)
                                                    viewModel.selectTab(WorkspaceTab.CHAT)
                                                },
                                                onClearUsage = viewModel::clearUsageHistory,
                                                onOpenPromptLibrary = { viewModel.setShowPromptLibrarySheet(true) },
                                                onSetSpendCurrency = viewModel::setSpendCurrency
                                            )
                                        }
                                        1 -> {
                                            if (providersViewModel != null && providersUiState != null) {
                                                ProvidersScreen(
                                                    uiState = providersUiState,
                                                    viewModel = providersViewModel,
                                                    usageRecords = usageRecords,
                                                    providerMonthlyLimitsUsd = uiState.providerMonthlyLimitsUsd,
                                                    onSetProviderMonthlyLimitUsd = viewModel::setProviderMonthlyLimitUsd,
                                                    failoverEnabled = uiState.providerFailoverEnabled,
                                                    failoverChain = uiState.providerFailoverChain,
                                                    onSetFailoverEnabled = viewModel::setProviderFailoverEnabled,
                                                    onAddFailoverEntry = viewModel::addProviderFailoverChainEntry,
                                                    onRemoveFailoverEntry = viewModel::removeProviderFailoverChainEntry,
                                                    onMoveFailoverEntry = viewModel::moveProviderFailoverChainEntry
                                                )
                                            } else {
                                                SettingsWorkspaceScreen(
                                                    uiState = uiState,
                                                    providers = providers,
                                                    models = models,
                                                    diagnosticEntries = diagnosticEntries,
                                                    onClearDiagnostics = viewModel::clearDiagnostics,
                                                    onRunOmniRouteCleanTerminal = viewModel::runOmniRouteCleanTerminal,
                                                    onSelectDefaultChatModel = { prov, modId ->
                                                        viewModel.switchActiveConversationModel(prov.id, modId)
                                                    },
                                                    onSaveProvider = viewModel::saveProviderInstance,
                                                    onDuplicateProvider = viewModel::duplicateProviderInstance,
                                                    onSaveProviderApiKeyOnly = viewModel::saveProviderApiKeyOnly,
                                                    onClearProviderApiKey = viewModel::clearProviderApiKey,
                                                    onUpdateProviderBaseUrl = viewModel::updateProviderBaseUrl,
                                                    onToggleProviderEnabled = viewModel::toggleProviderEnabled,
                                                    onUpdateProviderCapabilities = viewModel::updateProviderCapabilities,
                                                    onDeleteProvider = viewModel::deleteProviderInstance,
                                                    onSetDefaultForFeature = viewModel::setDefaultProviderForFeature,
                                                    onTestConnectionOnly = viewModel::testProviderConnectionOnly,
                                                    onTestAndFetchModels = viewModel::testAndFetchModels,
                                                    onAddManualModel = viewModel::addManualModel,
                                                    onToggleFavoriteModel = viewModel::toggleFavoriteModel,
                                                    onApproveCleartextHostManually = viewModel::approveCleartextHostManually,
                                                    onRevokeCleartextHost = viewModel::revokeCleartextHost,
                                                    onSetThemeMode = viewModel::setThemeMode,
                                                    onSetThemeAccent = viewModel::setThemeAccent,
                                                    onSetDynamicColor = viewModel::setDynamicColor,
                                                    onSetBiometricGate = viewModel::setBiometricGate,
                                                    onSetEnterToSend = viewModel::setEnterToSend,
                                                    onSetReadRepliesAloud = viewModel::setReadRepliesAloud,
                                                    onSetSpendCurrency = viewModel::setSpendCurrency,
                                                    onOpenOnboardingSheet = { viewModel.setShowOnboardingSheet(true) },
                                                    onExportSettings = viewModel::prepareSettingsExport,
                                                    onImportSettings = viewModel::importSettingsJson,
                                                    onClearAllData = viewModel::clearAllWorkspaceData,
                                                    onOpenConnectors = { viewModel.openConnectorsScreen(null) },
                                                    onSaveJavaRunnerSettings = viewModel::saveJavaRunnerSettings,
                                                    onClearJavaRunnerKey = viewModel::clearJavaRunnerKey,
                                                    onSetLiveInfoWebSearchEnabled = viewModel::setLiveInfoWebSearchEnabled,
                                                    onSetLiveInfoNewsHeadlinesEnabled = viewModel::setLiveInfoNewsHeadlinesEnabled,
                                                    onSetLiveInfoMaxSearchesPerMessage = viewModel::setLiveInfoMaxSearchesPerMessage,
                                                    onAddLiveInfoRssFeed = viewModel::addLiveInfoRssFeed,
                                                    onRemoveLiveInfoRssFeed = viewModel::removeLiveInfoRssFeed
                                                )
                                            }
                                        }
                                        else -> {
                                            MinimalSettingsAndThemeScreen(
                                                uiState = uiState,
                                                onSetThemeMode = viewModel::setThemeMode,
                                                onSetThemeAccent = viewModel::setThemeAccent,
                                                onSetBiometricGate = viewModel::setBiometricGate,
                                                onSetEnterToSend = viewModel::setEnterToSend,
                                                onSetReadRepliesAloud = viewModel::setReadRepliesAloud,
                                                onSetSpendCurrency = viewModel::setSpendCurrency,
                                                onOpenOnboardingSheet = { viewModel.setShowOnboardingSheet(true) },
                                                onExportSettings = viewModel::prepareSettingsExport,
                                                onClearAllData = viewModel::clearAllWorkspaceData,
                                                onOpenConnectors = { viewModel.openConnectorsScreen(null) },
                                                onSaveJavaRunnerSettings = viewModel::saveJavaRunnerSettings,
                                                onClearJavaRunnerKey = viewModel::clearJavaRunnerKey,
                                                onSetLiveInfoWebSearchEnabled = viewModel::setLiveInfoWebSearchEnabled,
                                                onSetLiveInfoNewsHeadlinesEnabled = viewModel::setLiveInfoNewsHeadlinesEnabled,
                                                onSetLiveInfoMaxSearchesPerMessage = viewModel::setLiveInfoMaxSearchesPerMessage,
                                                onAddLiveInfoRssFeed = viewModel::addLiveInfoRssFeed,
                                                onRemoveLiveInfoRssFeed = viewModel::removeLiveInfoRssFeed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                    }
                    }
            }
        }

                    // Slim, rounded, dark Toast at the top with a small check icon, one-line summary, and "Undo" text button.
                    // Auto-dismisses after 4s.
                    val toastMessage = agentState.liveStatusBanner?.takeIf { it.isNotBlank() }
                        ?: uiState.statusToast?.takeIf { it.isNotBlank() }

                    MinimalToast(
                        message = toastMessage,
                        onUndo = when {
                            agentState.lastUndoSnapshot != null && !agentState.isExecuting -> viewModel::undoLastAgentCommand
                            uiState.hasPendingDeletedConversationUndo -> viewModel::undoDeleteConversation
                            else -> null
                        },
                        onDismiss = {
                            viewModel.dismissAgentBanner()
                            viewModel.clearToast()
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .testTag("top_action_toast")
                    )
                }
            }
        }
    }
}

/**
 * Concept A detail-screen header: a white circular back button (returns to
 * the Home hub) plus the section title. Shown above every section entered
 * from Home.
 */
@Composable
private fun RunwaySectionHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = onBack,
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .size(34.dp)
                .testTag("section_back_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = LucideIcons.ArrowLeft,
                    contentDescription = "Back to Home",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
