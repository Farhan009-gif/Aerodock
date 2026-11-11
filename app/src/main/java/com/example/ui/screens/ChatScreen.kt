package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.components.ChatMediaGenerateBottomSheet
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.ChatMediaAttachment
import com.example.data.local.ConversationEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.MessageEntity
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.RunwayConversationMetaEntity
import com.example.data.local.RunwayDocumentEntity
import com.example.data.local.RunwayFolderEntity
import com.example.data.local.RunwayMessageNodeEntity
import com.example.data.local.SkillEntity
import com.example.data.local.ToolCallLogEntity
import com.example.data.local.UsageRecordEntity
import com.example.data.local.FileAttachmentProcessor
import com.example.data.local.getAttachments
import com.example.data.mcp.SkillMarkdownParser
import com.example.data.provider.OmniRouteComboDefinition
import com.example.ui.components.CollapsibleToolCallCard
import com.example.ui.components.LucideIcons
import com.example.ui.components.MinimalButton
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalChip
import com.example.ui.components.MinimalSheet
import com.example.ui.components.RunwayChatHistoryDrawerContent
import com.example.ui.theme.CoralError
import com.example.ui.theme.CreamOnDark
import com.example.ui.theme.LocalRunwayAccentBubble
import com.example.ui.theme.runwayIsDarkTheme

import com.example.ui.theme.MonospaceFontFamily

import com.example.ui.viewmodel.OmniUiState
import com.example.ui.viewmodel.WorkMode
import kotlinx.coroutines.launch

private sealed class ThreadDisplayItem {
    abstract val key: String
    data class SingleMessage(val message: MessageEntity) : ThreadDisplayItem() {
        override val key: String = "single_${message.id}"
    }
    data class ComparisonRow(val groupId: String, val responses: List<MessageEntity>) : ThreadDisplayItem() {
        override val key: String = "cmp_$groupId"
    }
}

@Composable
fun ChatWorkspaceScreen(
    uiState: OmniUiState,
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    conversations: List<ConversationEntity>,
    messages: List<MessageEntity>,
    skills: List<SkillEntity> = emptyList(),
    mcpTools: List<McpToolEntity> = emptyList(),
    conversationToolLogs: List<ToolCallLogEntity> = emptyList(),
    folders: List<RunwayFolderEntity> = emptyList(),
    conversationMetas: List<RunwayConversationMetaEntity> = emptyList(),
    allMessageNodes: List<RunwayMessageNodeEntity> = emptyList(),
    allDocuments: List<RunwayDocumentEntity> = emptyList(),
    usageRecords: List<UsageRecordEntity> = emptyList(),
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onContinueLatestConversation: () -> Unit = {},
    onTogglePinConversation: (ConversationEntity) -> Unit,
    onRenameConversation: (ConversationEntity, String) -> Unit,
    onDuplicateConversation: (String) -> Unit = {},
    onDeleteConversation: (String) -> Unit,
    onUndoDeleteConversation: () -> Unit = {},
    onExportConversation: (conversationId: String, asJson: Boolean) -> Unit,
    onExportConversationWithBranches: (conversationId: String, asJson: Boolean, includeAllBranches: Boolean) -> Unit = { id, asJson, _ ->
        onExportConversation(id, asJson)
    },
    onSelectFolderFilter: (String?) -> Unit = {},
    onMoveConversationToFolder: (conversationId: String, folderId: String?) -> Unit = { _, _ -> },
    onSaveFolder: (id: String?, name: String, colorHex: String) -> Unit = { _, _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onHistorySearchChange: (String) -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenParameterSheet: () -> Unit,
    onOpenPromptLibrary: () -> Unit,
    onOpenSkillsAndToolsSheet: () -> Unit = {},
    onOpenGitClone: () -> Unit = {},
    onOpenCompareTargetPicker: () -> Unit,
    onToggleCompareMode: (Boolean) -> Unit,
    onOpenOnboardingGuide: () -> Unit,
    onDraftChange: (String) -> Unit,
    onAttachImage: (String?) -> Unit,
    onAddMediaAttachments: (List<ChatMediaAttachment>) -> Unit = {},
    onAttachDocumentUris: (List<Uri>) -> Unit = {},
    onApplyAttachmentPreset: (FileAttachmentProcessor.AttachmentPreset) -> Unit = {},
    onDismissFirstTimeFileNotice: () -> Unit = {},
    onRemoveMediaAttachment: (Int) -> Unit = {},
    onClearMediaAttachments: () -> Unit = {},
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateLast: () -> Unit,
    onStartEditMessage: (MessageEntity) -> Unit,
    onCancelEditMessage: () -> Unit,
    onApproveCleartextHostFromError: (String) -> Unit,
    onDismissError: () -> Unit,
    onCopiedToast: (String) -> Unit,
    onPushConversationToGitHub: (String) -> Unit = {},
    onPushContentToGitHub: (title: String, fileName: String, content: String) -> Unit = { _, _, _ -> },
    onSelectProvider: (String) -> Unit = {},
    onSelectModel: (String, String) -> Unit = { _, _ -> },
    onRunPreflightCheck: (String) -> Unit = {},
    onSwitchToDirectModel: (String) -> Unit = {},
    onOpenProviderSettings: () -> Unit = {},
    onOpenTerminalAndHealthSheet: () -> Unit = {},
    onOpenConnectors: () -> Unit = {},
    onToggleVoiceInput: () -> Unit = {},
    onStopVoiceSpeaking: () -> Unit = {},
    onAcceptCheaperModelSuggestion: (conversationId: String, modelId: String) -> Unit = { _, _ -> },
    onDismissCheaperModelSuggestion: (conversationId: String) -> Unit = {},
    onOverrideSpendingLimitOnce: () -> Unit = {},
    onSetContextTrimmingEnabled: (conversationId: String, enabled: Boolean) -> Unit = { _, _ -> },
    onCancelBackgroundSubagents: (String) -> Unit = {},
    onQueueMessage: () -> Unit = {},
    onRemoveQueuedMessage: (String) -> Unit = {},
    onSetWorkMode: (WorkMode) -> Unit = {},
    onGenerateMediaInChat: (
        modality: String,
        prompt: String,
        providerId: String?,
        modelId: String?,
        aspectRatio: String?,
        durationSeconds: Int?,
        sourceImageUri: String?
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onUpsertCustomCombo: (
        rawId: String,
        displayName: String,
        strategy: String,
        targetModels: List<String>,
        description: String
    ) -> Unit = { _, _, _, _, _ -> },
    onRunCodeBlock: (language: String, code: String, messageId: String?) -> Unit = { _, _, _ -> },
    onOpenHtmlPreview: (htmlDocument: String, title: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        onDispose {
            onStopVoiceSpeaking()
        }
    }
    val activeConversation = remember(conversations, uiState.activeConversationId) {
        conversations.firstOrNull { it.id == uiState.activeConversationId } ?: conversations.firstOrNull()
    }
    val activeModelDescriptor = remember(models, activeConversation) {
        models.firstOrNull {
            it.providerInstanceId == activeConversation?.providerInstanceId &&
                it.modelId == activeConversation?.modelId
        }
    }
    val isActiveModelCombo = remember(activeModelDescriptor, activeConversation, uiState.omniRouteCombos) {
        val modelId = activeConversation?.modelId.orEmpty()
        activeModelDescriptor?.isCombo == true ||
            uiState.omniRouteCombos.any { it.id.equals(modelId, ignoreCase = true) }
    }

    val activeSkillsCount = remember(skills, activeConversation, uiState.draftMessage) {
        SkillMarkdownParser.resolveActiveSkillsForTurn(
            allSkills = skills,
            conversation = activeConversation,
            userPrompt = uiState.draftMessage
        ).size
    }
    val liveInfoToolsCount = (if (uiState.liveInfoWebSearchEnabled) 1 else 0) +
        (if (uiState.liveInfoNewsHeadlinesEnabled) 1 else 0)
    val enabledMcpToolsCount = remember(mcpTools, activeConversation, liveInfoToolsCount) {
        val mcpCount = if (activeConversation?.mcpToolsEnabled == false) 0
        else mcpTools.count { it.isEnabled }
        mcpCount + liveInfoToolsCount
    }

    var showToolsSheet by rememberSaveable { mutableStateOf(false) }
    var showWorkModeSheet by rememberSaveable { mutableStateOf(false) }
    var showAttachSheet by rememberSaveable { mutableStateOf(false) }
    var showHtmlCodeInputSheet by rememberSaveable { mutableStateOf(false) }
    var showChatMediaGenerateSheet by rememberSaveable { mutableStateOf(false) }
    var chatMediaGenerateModality by rememberSaveable { mutableStateOf("IMAGE") }
    var showChatComboBuilderDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    if (showHtmlCodeInputSheet) {
        com.example.ui.components.HtmlCssJsCodeInputBottomSheet(
            onDismiss = { showHtmlCodeInputSheet = false },
            onRunInSandboxWithConfirmation = { html, css, js ->
                val fullDoc = com.example.data.mcp.JsSandboxExecutor.buildFullHtmlDocument(
                    html = html,
                    css = css,
                    js = js
                )
                showHtmlCodeInputSheet = false
                onRunCodeBlock("html", fullDoc, null)
            },
            onInsertIntoChat = { markdownBlock ->
                val existing = uiState.draftMessage.trim()
                val nextDraft = if (existing.isEmpty()) markdownBlock else "$existing\n\n$markdownBlock"
                onDraftChange(nextDraft)
                showHtmlCodeInputSheet = false
            }
        )
    }

    if (showChatMediaGenerateSheet) {
        ChatMediaGenerateBottomSheet(
            onDismiss = { showChatMediaGenerateSheet = false },
            initialModality = chatMediaGenerateModality,
            initialPrompt = uiState.draftMessage,
            initialImageUri = uiState.attachedImageUri ?: uiState.attachedMediaList.firstOrNull { !it.isVideo }?.uri,
            providers = providers,
            models = models,
            uiState = uiState,
            onGenerate = { mod, pr, provId, modId, aspect, dur, srcUri ->
                onGenerateMediaInChat(mod, pr, provId, modId, aspect, dur, srcUri)
            }
        )
    }

    if (showChatComboBuilderDialog) {
        OmniRouteComboBuilderDialog(
            initialCombo = null,
            defaultModality = "CHAT",
            onDismiss = { showChatComboBuilderDialog = false },
            onSave = { rawId, displayName, _, strategy, targetModels, description ->
                onUpsertCustomCombo(rawId, displayName, strategy, targetModels, description)
                showChatComboBuilderDialog = false
            }
        )
    }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(FileAttachmentProcessor.MAX_FILES_PER_MESSAGE)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val items = uris.take(FileAttachmentProcessor.MAX_FILES_PER_MESSAGE).map { uri ->
                ChatMediaAttachment(
                    uri = uri.toString(),
                    mimeType = "image/jpeg",
                    isVideo = false,
                    name = uri.lastPathSegment ?: "photo.jpg"
                )
            }
            onAddMediaAttachments(items)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAttachDocumentUris(uris.take(FileAttachmentProcessor.MAX_FILES_PER_MESSAGE))
        }
    }

    val textFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAttachDocumentUris(uris.take(FileAttachmentProcessor.MAX_FILES_PER_MESSAGE))
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleVoiceInput()
        } else {
            onCopiedToast("Microphone permission is needed for voice input.")
        }
    }

    val handleMicTap: () -> Unit = {
        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            onToggleVoiceInput()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAddMediaAttachments(
                listOf(
                    ChatMediaAttachment(
                        uri = uri.toString(),
                        mimeType = "video/mp4",
                        isVideo = true,
                        name = uri.lastPathSegment ?: "video.mp4"
                    )
                )
            )
        }
    }

    // Rename Conversation Dialog
    renameTarget?.let { target ->
        var clearDialogIme by remember { mutableStateOf<(() -> Unit)?>(null) }
        var newTitle by rememberSaveable(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = {
                clearDialogIme?.invoke()
                renameTarget = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Rename conversation", style = MaterialTheme.typography.titleMedium) },
            text = {
                com.example.ui.components.DialogWindowFocusGuard(onRegisterCleaner = { clearDialogIme = it })
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                MinimalButton(
                    text = "Save",
                    onClick = {
                        clearDialogIme?.invoke()
                        onRenameConversation(target, newTitle)
                        renameTarget = null
                    }
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clearDialogIme?.invoke()
                        renameTarget = null
                    }
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Minimal "Tools" Bottom Sheet (Compare, Enhance, Quick Actions, Parameters, Console, Rename)
    if (showToolsSheet) {
        MinimalSheet(
            onDismiss = { showToolsSheet = false },
            title = "Tools",
            modifier = Modifier.testTag("chat_tools_bottom_sheet")
        ) {
            MinimalToolRow(
                icon = LucideIcons.GitCompare,
                title = if (uiState.compareModeEnabled) "Compare mode (On)" else "Compare",
                subtitle = "Run up to 3 models side-by-side",
                isActive = uiState.compareModeEnabled,
                onClick = {
                    showToolsSheet = false
                    onToggleCompareMode(!uiState.compareModeEnabled)
                },
                testTag = "tools_sheet_compare"
            )
            MinimalToolRow(
                icon = LucideIcons.Layers,
                title = "Custom combos",
                subtitle = "Create multi-model fallback, round-robin, or latency-first chat combos",
                isActive = isActiveModelCombo,
                onClick = {
                    showToolsSheet = false
                    showChatComboBuilderDialog = true
                },
                testTag = "tools_sheet_custom_combos"
            )
            MinimalToolRow(
                icon = LucideIcons.Plus,
                title = "+ Combo",
                subtitle = "Create a new multi-model chat combo",
                onClick = {
                    showToolsSheet = false
                    showChatComboBuilderDialog = true
                },
                testTag = "tools_sheet_new_combo"
            )
            MinimalToolRow(
                icon = LucideIcons.Sparkles,
                title = "Enhance prompt",
                subtitle = "Refine your current message draft for clarity",
                onClick = {
                    showToolsSheet = false
                    val draft = uiState.draftMessage.trim()
                    if (draft.isNotBlank()) {
                        onDraftChange("Please provide a thorough, well-structured response to:\n\n$draft")
                    } else {
                        onOpenPromptLibrary()
                    }
                },
                testTag = "tools_sheet_enhance"
            )
            MinimalToolRow(
                icon = LucideIcons.Zap,
                title = "Quick actions",
                subtitle = "Browse saved prompt templates",
                onClick = {
                    showToolsSheet = false
                    onOpenPromptLibrary()
                },
                testTag = "tools_sheet_quick_actions"
            )
            MinimalToolRow(
                icon = LucideIcons.Sliders,
                title = "Parameters",
                subtitle = "Temperature, top-p, max tokens & system prompt",
                onClick = {
                    showToolsSheet = false
                    onOpenParameterSheet()
                },
                testTag = "tools_sheet_parameters"
            )
            MinimalToolRow(
                icon = LucideIcons.Plug,
                title = "Connectors",
                subtitle = "Manage MCP connectors, tool policies & GitHub",
                onClick = {
                    showToolsSheet = false
                    onOpenConnectors()
                },
                testTag = "tools_sheet_connectors"
            )
            MinimalToolRow(
                icon = LucideIcons.GitBranch,
                title = "Git Clone & Repo Skills",
                subtitle = "Clone GitHub repo and use agent skills (SKILL.md)",
                onClick = {
                    showToolsSheet = false
                    onOpenGitClone()
                },
                testTag = "tools_sheet_git_clone"
            )
            MinimalToolRow(
                icon = LucideIcons.Sparkles,
                title = "Generate Image / Video",
                subtitle = "Synthesize images or videos right here in chat without leaving the tab",
                onClick = {
                    showToolsSheet = false
                    chatMediaGenerateModality = "IMAGE"
                    showChatMediaGenerateSheet = true
                },
                testTag = "tools_sheet_generate_media"
            )
            MinimalToolRow(
                icon = LucideIcons.Code,
                title = "HTML/CSS/JS Code Input & Templates",
                subtitle = "Starter boilerplates ('Centered Card', 'CSS Grid Layout', 'Interactive Button' & online)",
                onClick = {
                    showToolsSheet = false
                    showHtmlCodeInputSheet = true
                },
                testTag = "tools_sheet_html_code_input"
            )
            MinimalToolRow(
                icon = LucideIcons.Terminal,
                title = "Console",
                subtitle = "OmniRoute localhost daemon & model health check",
                onClick = {
                    showToolsSheet = false
                    onOpenTerminalAndHealthSheet()
                },
                testTag = "tools_sheet_console"
            )
            activeConversation?.let { currentConv ->
                MinimalToolRow(
                    icon = LucideIcons.Edit,
                    title = "Rename chat",
                    subtitle = currentConv.title,
                    onClick = {
                        showToolsSheet = false
                        renameTarget = currentConv
                    },
                    testTag = "tools_sheet_rename_chat"
                )
            }
        }
    }

    // Work Mode Bottom Sheet (who does tool work: model, subagents, or auto)
    if (showWorkModeSheet) {
        MinimalSheet(
            onDismiss = { showWorkModeSheet = false },
            title = "Work mode",
            modifier = Modifier.testTag("chat_work_mode_bottom_sheet")
        ) {
            MinimalToolRow(
                icon = LucideIcons.Sparkles,
                title = "Auto",
                subtitle = "Model decides — it may do the work itself, delegate, or both",
                isActive = uiState.workMode == WorkMode.AUTO,
                onClick = {
                    showWorkModeSheet = false
                    onSetWorkMode(WorkMode.AUTO)
                },
                testTag = "work_mode_auto"
            )
            MinimalToolRow(
                icon = LucideIcons.Bot,
                title = "Agent only",
                subtitle = "All tool work goes to subagents — the model only delegates and relays",
                isActive = uiState.workMode == WorkMode.AGENT_ONLY,
                onClick = {
                    showWorkModeSheet = false
                    onSetWorkMode(WorkMode.AGENT_ONLY)
                },
                testTag = "work_mode_agent_only"
            )
            MinimalToolRow(
                icon = LucideIcons.Zap,
                title = "Model only",
                subtitle = "No subagents — the model does all the work itself",
                isActive = uiState.workMode == WorkMode.MODEL_ONLY,
                onClick = {
                    showWorkModeSheet = false
                    onSetWorkMode(WorkMode.MODEL_ONLY)
                },
                testTag = "work_mode_model_only"
            )
        }
    }

    // "+" Attachment & Enhance Options Bottom Sheet
    if (showAttachSheet) {
        MinimalSheet(
            onDismiss = { showAttachSheet = false },
            title = "Add to message",
            modifier = Modifier.testTag("chat_attach_bottom_sheet")
        ) {
            MinimalToolRow(
                icon = LucideIcons.Image,
                title = "Photo",
                subtitle = "Select photos from your device (up to 5 files, 10 MB each)",
                onClick = {
                    showAttachSheet = false
                    multiPhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                testTag = "attach_sheet_photos"
            )
            MinimalToolRow(
                icon = LucideIcons.Layers,
                title = "Document",
                subtitle = "PDF, DOCX, CSV, JSON, or code file (max 10 MB, up to 5 per message)",
                onClick = {
                    showAttachSheet = false
                    documentPickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/*",
                            "application/json",
                            "application/xml",
                            "application/javascript",
                            "*/*"
                        )
                    )
                },
                testTag = "attach_sheet_document"
            )
            MinimalToolRow(
                icon = LucideIcons.Code,
                title = "Text file",
                subtitle = "TXT, MD, CSV, JSON, or source code files",
                onClick = {
                    showAttachSheet = false
                    textFilePickerLauncher.launch(
                        arrayOf(
                            "text/*",
                            "application/json",
                            "application/xml",
                            "application/javascript"
                        )
                    )
                },
                testTag = "attach_sheet_text_file"
            )
            MinimalToolRow(
                icon = LucideIcons.Image,
                title = "Generate AI image",
                subtitle = "Synthesize an image directly in chat using Flux, Imagen or SD",
                onClick = {
                    showAttachSheet = false
                    chatMediaGenerateModality = "IMAGE"
                    showChatMediaGenerateSheet = true
                },
                testTag = "attach_sheet_generate_image"
            )
            MinimalToolRow(
                icon = LucideIcons.Video,
                title = "Generate AI video",
                subtitle = "Synthesize video clips using Runway, Luma, Kling or Video Combo",
                onClick = {
                    showAttachSheet = false
                    chatMediaGenerateModality = "VIDEO"
                    showChatMediaGenerateSheet = true
                },
                testTag = "attach_sheet_generate_video"
            )
            MinimalToolRow(
                icon = LucideIcons.Video,
                title = "Attach video",
                subtitle = "Upload a video clip for multimodal analysis",
                onClick = {
                    showAttachSheet = false
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                testTag = "attach_sheet_video"
            )
            MinimalToolRow(
                icon = LucideIcons.Code,
                title = "HTML/CSS/JS Code Input & Templates",
                subtitle = "Pick 'Centered Card', 'CSS Grid Layout', 'Interactive Button' or import online",
                onClick = {
                    showAttachSheet = false
                    showHtmlCodeInputSheet = true
                },
                testTag = "attach_sheet_html_code_input"
            )
            MinimalToolRow(
                icon = LucideIcons.Sparkles,
                title = "Enhance prompt",
                subtitle = "Expand draft or insert a prompt template",
                onClick = {
                    showAttachSheet = false
                    val draft = uiState.draftMessage.trim()
                    if (draft.isNotBlank()) {
                        onDraftChange("Act as an expert engineer. Provide a concise, high-signal solution for:\n\n$draft")
                    } else {
                        onOpenPromptLibrary()
                    }
                },
                testTag = "attach_sheet_enhance"
            )
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideTablet = maxWidth >= 840.dp

        if (isWideTablet) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                ) {
                    RunwayChatHistoryDrawerContent(
                        conversations = conversations,
                        activeConversationId = activeConversation?.id,
                        folders = folders,
                        conversationMetas = conversationMetas,
                        allMessageNodes = allMessageNodes,
                        allDocuments = allDocuments,
                        selectedFolderId = uiState.selectedHistoryFolderId,
                        searchQuery = uiState.historySearchQuery,
                        hasPendingUndoDelete = uiState.hasPendingDeletedConversationUndo,
                        onSearchChange = onHistorySearchChange,
                        onSelectFolderFilter = onSelectFolderFilter,
                        onSelectConversation = onSelectConversation,
                        onNewConversation = onNewConversation,
                        onContinueLatestConversation = onContinueLatestConversation,
                        onTogglePinConversation = onTogglePinConversation,
                        onRenameConversation = { renameTarget = it },
                        onDuplicateConversation = onDuplicateConversation,
                        onMoveConversationToFolder = onMoveConversationToFolder,
                        onDeleteConversation = onDeleteConversation,
                        onUndoDeleteConversation = onUndoDeleteConversation,
                        onExportConversation = onExportConversationWithBranches,
                        onSaveFolder = onSaveFolder,
                        onDeleteFolder = onDeleteFolder,
                        onOpenConnectors = onOpenConnectors
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    MinimalChatCanvas(
                        uiState = uiState,
                        providers = providers,
                        models = models,
                        usageRecords = usageRecords,
                        activeConversation = activeConversation,
                        activeModelName = activeModelDescriptor?.modelId
                            ?: activeConversation?.modelId
                            ?: "gpt-4o",
                        isActiveModelCombo = isActiveModelCombo,
                        activeSkillsCount = activeSkillsCount,
                        enabledMcpToolsCount = enabledMcpToolsCount,
                        messages = messages,
                        conversationToolLogs = conversationToolLogs,
                        showMenuButton = false,
                        onOpenMenu = {},
                        onOpenModelPicker = onOpenModelPicker,
                        onOpenToolsSheet = { showToolsSheet = true },
                        onOpenSkillsAndToolsSheet = onOpenSkillsAndToolsSheet,
                        onOpenAttachSheet = { showAttachSheet = true },
                        onDraftChange = onDraftChange,
                        onRemoveMediaAttachment = onRemoveMediaAttachment,
                        onClearMediaAttachments = onClearMediaAttachments,
                        onSendMessage = onSendMessage,
                        onStopGeneration = onStopGeneration,
                        onRegenerateLast = onRegenerateLast,
                        onStartEditMessage = onStartEditMessage,
                        onCancelEditMessage = onCancelEditMessage,
                        onApproveCleartextHostFromError = onApproveCleartextHostFromError,
                        onDismissError = onDismissError,
                        onCopiedToast = onCopiedToast,
                        onPushContentToGitHub = onPushContentToGitHub,
                        onOpenCompareTargetPicker = onOpenCompareTargetPicker,
                        onOpenProviderSettings = onOpenProviderSettings,
                        onMicClick = handleMicTap,
                        onApplyAttachmentPreset = onApplyAttachmentPreset,
                        onDismissFirstTimeFileNotice = onDismissFirstTimeFileNotice,
                        onAcceptCheaperModelSuggestion = onAcceptCheaperModelSuggestion,
                        onDismissCheaperModelSuggestion = onDismissCheaperModelSuggestion,
                        onOverrideSpendingLimitOnce = onOverrideSpendingLimitOnce,
                        onSetContextTrimmingEnabled = onSetContextTrimmingEnabled,
                        onCancelBackgroundSubagents = onCancelBackgroundSubagents,
                        onQueueMessage = onQueueMessage,
                        onRemoveQueuedMessage = onRemoveQueuedMessage,
                        onOpenWorkModeSheet = { showWorkModeSheet = true },
                        onRunCodeBlock = onRunCodeBlock,
                        onOpenHtmlPreview = onOpenHtmlPreview
                    )
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.width(305.dp)
                    ) {
                        RunwayChatHistoryDrawerContent(
                            conversations = conversations,
                            activeConversationId = activeConversation?.id,
                            folders = folders,
                            conversationMetas = conversationMetas,
                            allMessageNodes = allMessageNodes,
                            allDocuments = allDocuments,
                            selectedFolderId = uiState.selectedHistoryFolderId,
                            searchQuery = uiState.historySearchQuery,
                            hasPendingUndoDelete = uiState.hasPendingDeletedConversationUndo,
                            onSearchChange = onHistorySearchChange,
                            onSelectFolderFilter = onSelectFolderFilter,
                            onSelectConversation = { id ->
                                onSelectConversation(id)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onNewConversation = {
                                onNewConversation()
                                coroutineScope.launch { drawerState.close() }
                            },
                            onContinueLatestConversation = {
                                onContinueLatestConversation()
                                coroutineScope.launch { drawerState.close() }
                            },
                            onTogglePinConversation = onTogglePinConversation,
                            onRenameConversation = { renameTarget = it },
                            onDuplicateConversation = onDuplicateConversation,
                            onMoveConversationToFolder = onMoveConversationToFolder,
                            onDeleteConversation = onDeleteConversation,
                            onUndoDeleteConversation = onUndoDeleteConversation,
                            onExportConversation = onExportConversationWithBranches,
                            onSaveFolder = onSaveFolder,
                            onDeleteFolder = onDeleteFolder,
                            onOpenConnectors = {
                                coroutineScope.launch { drawerState.close() }
                                onOpenConnectors()
                            }
                        )
                    }
                }
            ) {
                MinimalChatCanvas(
                    uiState = uiState,
                    providers = providers,
                    models = models,
                    usageRecords = usageRecords,
                    activeConversation = activeConversation,
                    activeModelName = activeModelDescriptor?.modelId
                        ?: activeConversation?.modelId
                        ?: "gpt-4o",
                    isActiveModelCombo = isActiveModelCombo,
                    activeSkillsCount = activeSkillsCount,
                    enabledMcpToolsCount = enabledMcpToolsCount,
                    messages = messages,
                    conversationToolLogs = conversationToolLogs,
                    showMenuButton = true,
                    onOpenMenu = { coroutineScope.launch { drawerState.open() } },
                    onOpenModelPicker = onOpenModelPicker,
                    onOpenNewComboDialog = { showChatComboBuilderDialog = true },
                    onOpenToolsSheet = { showToolsSheet = true },
                    onOpenSkillsAndToolsSheet = onOpenSkillsAndToolsSheet,
                    onOpenAttachSheet = { showAttachSheet = true },
                    onDraftChange = onDraftChange,
                    onRemoveMediaAttachment = onRemoveMediaAttachment,
                    onClearMediaAttachments = onClearMediaAttachments,
                    onSendMessage = onSendMessage,
                    onStopGeneration = onStopGeneration,
                    onRegenerateLast = onRegenerateLast,
                    onStartEditMessage = onStartEditMessage,
                    onCancelEditMessage = onCancelEditMessage,
                    onApproveCleartextHostFromError = onApproveCleartextHostFromError,
                    onDismissError = onDismissError,
                    onCopiedToast = onCopiedToast,
                    onPushContentToGitHub = onPushContentToGitHub,
                    onOpenCompareTargetPicker = onOpenCompareTargetPicker,
                    onOpenProviderSettings = onOpenProviderSettings,
                    onMicClick = handleMicTap,
                    onApplyAttachmentPreset = onApplyAttachmentPreset,
                    onDismissFirstTimeFileNotice = onDismissFirstTimeFileNotice,
                    onAcceptCheaperModelSuggestion = onAcceptCheaperModelSuggestion,
                    onDismissCheaperModelSuggestion = onDismissCheaperModelSuggestion,
                    onOverrideSpendingLimitOnce = onOverrideSpendingLimitOnce,
                    onSetContextTrimmingEnabled = onSetContextTrimmingEnabled,
                    onCancelBackgroundSubagents = onCancelBackgroundSubagents,
                    onQueueMessage = onQueueMessage,
                    onRemoveQueuedMessage = onRemoveQueuedMessage,
                    onOpenWorkModeSheet = { showWorkModeSheet = true },
                    onRunCodeBlock = onRunCodeBlock,
                    onOpenHtmlPreview = onOpenHtmlPreview
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    messages: List<MessageEntity>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    activeModelId: String = "gpt-4o",
    attachedImageUri: String? = null,
    activeToolLoopStatus: String? = null,
    onPickVisionImage: () -> Unit = {},
    onClearVisionImage: () -> Unit = {},
    onStopGeneration: () -> Unit = {},
    topBar: @Composable () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val reversedMessages = remember(messages) { messages.asReversed() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = topBar,
        bottomBar = {
            MinimalFloatingComposerBar(
                draft = draft,
                isStreaming = isStreaming,
                enterToSend = true,
                editingMessageId = null,
                attachments = if (!attachedImageUri.isNullOrBlank()) {
                    listOf(ChatMediaAttachment(uri = attachedImageUri))
                } else emptyList(),
                onDraftChange = onDraftChange,
                onOpenAttachOptions = onPickVisionImage,
                onRemoveAttachment = { onClearVisionImage() },
                onCancelEdit = {},
                onSend = onSendMessage,
                onStop = onStopGeneration
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("chat_screen_message_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(reversedMessages, key = { it.id }) { msg ->
                MinimalChatMessageItem(
                    message = msg,
                    toolLogs = emptyList(),
                    isLastAssistantMessage = false,
                    activeToolLoopStatus = if (msg.isStreaming) activeToolLoopStatus else null,
                    onEditUserMessage = {},
                    onRegenerate = {},
                    onCopiedToast = {},
                    onPushToGitHub = { _, _, _ -> }
                )
            }
        }
    }
}

@Composable
private fun MinimalChatCanvas(
    uiState: OmniUiState,
    providers: List<ProviderInstanceEntity> = emptyList(),
    models: List<ModelDescriptorEntity> = emptyList(),
    usageRecords: List<UsageRecordEntity> = emptyList(),
    activeConversation: ConversationEntity?,
    activeModelName: String,
    isActiveModelCombo: Boolean = false,
    activeSkillsCount: Int,
    enabledMcpToolsCount: Int,
    messages: List<MessageEntity>,
    conversationToolLogs: List<ToolCallLogEntity>,
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenNewComboDialog: () -> Unit = {},
    onOpenToolsSheet: () -> Unit,
    onOpenSkillsAndToolsSheet: () -> Unit,
    onOpenAttachSheet: () -> Unit,
    onDraftChange: (String) -> Unit,
    onRemoveMediaAttachment: (Int) -> Unit,
    onClearMediaAttachments: () -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateLast: () -> Unit,
    onStartEditMessage: (MessageEntity) -> Unit,
    onCancelEditMessage: () -> Unit,
    onApproveCleartextHostFromError: (String) -> Unit,
    onDismissError: () -> Unit,
    onCopiedToast: (String) -> Unit,
    onPushContentToGitHub: (title: String, fileName: String, content: String) -> Unit,
    onOpenCompareTargetPicker: () -> Unit,
    onOpenProviderSettings: () -> Unit = {},
    onMicClick: () -> Unit = {},
    onApplyAttachmentPreset: (FileAttachmentProcessor.AttachmentPreset) -> Unit = {},
    onDismissFirstTimeFileNotice: () -> Unit = {},
    onAcceptCheaperModelSuggestion: (conversationId: String, modelId: String) -> Unit = { _, _ -> },
    onDismissCheaperModelSuggestion: (conversationId: String) -> Unit = {},
    onOverrideSpendingLimitOnce: () -> Unit = {},
    onSetContextTrimmingEnabled: (conversationId: String, enabled: Boolean) -> Unit = { _, _ -> },
    onCancelBackgroundSubagents: (String) -> Unit = {},
    onQueueMessage: () -> Unit = {},
    onRemoveQueuedMessage: (String) -> Unit = {},
    onOpenWorkModeSheet: () -> Unit = {},
    onRunCodeBlock: (language: String, code: String, messageId: String?) -> Unit = { _, _, _ -> },
    onOpenHtmlPreview: (htmlDocument: String, title: String) -> Unit = { _, _ -> }
) {
    val listState = rememberLazyListState()
    val isDarkTheme = runwayIsDarkTheme()
    val accentBubble = LocalRunwayAccentBubble.current

    val threadItems = remember(messages) {
        val result = mutableListOf<ThreadDisplayItem>()
        var idx = 0
        while (idx < messages.size) {
            val current = messages[idx]
            val groupId = current.comparisonGroupId
            if (groupId != null && current.role == "assistant") {
                val group = mutableListOf<MessageEntity>()
                while (idx < messages.size && messages[idx].comparisonGroupId == groupId) {
                    group.add(messages[idx])
                    idx++
                }
                result.add(ThreadDisplayItem.ComparisonRow(groupId, group))
            } else {
                result.add(ThreadDisplayItem.SingleMessage(current))
                idx++
            }
        }
        result.asReversed()
    }

    val lastAssistantId = remember(messages) {
        messages.lastOrNull { it.role == "assistant" }?.id
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (threadItems.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val currentAttachments = remember(uiState.attachedMediaList, uiState.attachedImageUri) {
        if (uiState.attachedMediaList.isNotEmpty()) {
            uiState.attachedMediaList
        } else if (!uiState.attachedImageUri.isNullOrBlank()) {
            listOf(ChatMediaAttachment(uri = uiState.attachedImageUri))
        } else {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        // 1. Minimal Top Bar: Menu left, Model pill + Combo chip center, Overflow right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showMenuButton) {
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("open_drawer_button")
                ) {
                    Icon(
                        imageVector = LucideIcons.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(44.dp))
            }

            // Center tappable model pill ("+ Combo" moved into the Tools sheet)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onOpenModelPicker,
                    shape = RoundedCornerShape(50),
                    color = if (isDarkTheme) MaterialTheme.colorScheme.surfaceContainerHigh else accentBubble.fill,
                    border = BorderStroke(1.dp, if (isDarkTheme) MaterialTheme.colorScheme.outline else accentBubble.fill),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .widthIn(max = 210.dp)
                        .testTag("top_bar_model_selector_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) MaterialTheme.colorScheme.primary else accentBubble.dot)
                        )
                        Text(
                            text = activeModelName.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else CreamOnDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActiveModelCombo) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "COMBO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = LucideIcons.ChevronDown,
                            contentDescription = "Select model",
                            tint = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else CreamOnDark.copy(alpha = 0.75f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

            }

            // Right single overflow icon -> opens "Tools" bottom sheet
            IconButton(
                onClick = onOpenToolsSheet,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("top_bar_tools_overflow_button")
            ) {
                Icon(
                    imageVector = LucideIcons.MoreHorizontal,
                    contentDescription = "Tools",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 3. Quiet Status Line under the top bar: "2 skills · 0 tools ready" + work-mode chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$activeSkillsCount skills · $enabledMcpToolsCount tools ready",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onOpenSkillsAndToolsSheet)
                    .testTag("quiet_skills_tools_status_line")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = onOpenWorkModeSheet,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.testTag("work_mode_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = when (uiState.workMode) {
                            WorkMode.AGENT_ONLY -> "Agent only"
                            WorkMode.MODEL_ONLY -> "Model only"
                            WorkMode.AUTO -> "Auto"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = LucideIcons.ChevronDown,
                        contentDescription = "Work mode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Compare Mode subtle bar when Compare Mode is active
        AnimatedVisibility(
            visible = uiState.compareModeEnabled,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comparing ${uiState.compareTargets.size} models",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Configure",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenCompareTargetPicker)
                )
            }
        }

        // Provider Monthly Spending Limit Evaluation (80% warning & 100% block banner)
        val activeProvider = remember(providers, activeConversation) {
            providers.firstOrNull { it.id == activeConversation?.providerInstanceId }
        }
        val spendingEvaluation = remember(
            activeProvider,
            usageRecords,
            uiState.providerMonthlyLimitsUsd,
            uiState.spendingLimitActiveOverrideChatIds,
            uiState.spendingLimitUsedOverrideChatIds,
            activeConversation?.id
        ) {
            if (activeProvider == null) null
            else {
                val limitUsd = uiState.providerMonthlyLimitsUsd[activeProvider.id]
                val monthlySpend = com.example.data.provider.ProviderSpendingLimitEvaluator.calculateMonthlySpendUsd(
                    usageRecords = usageRecords,
                    providerInstanceId = activeProvider.id
                )
                val convId = activeConversation?.id.orEmpty()
                com.example.data.provider.ProviderSpendingLimitEvaluator.evaluate(
                    providerInstanceId = activeProvider.id,
                    providerDisplayName = activeProvider.displayName,
                    monthlySpendUsd = monthlySpend,
                    monthlyLimitUsd = limitUsd,
                    hasActiveOverrideForChat = convId in uiState.spendingLimitActiveOverrideChatIds,
                    hasUsedOverrideInChat = convId in uiState.spendingLimitUsedOverrideChatIds
                )
            }
        }

        if (spendingEvaluation != null && (spendingEvaluation.isWarning || spendingEvaluation.isBlocked)) {
            val isBlocked = spendingEvaluation.isBlocked
            val bannerColor = if (isBlocked) CoralError else MaterialTheme.colorScheme.primary
            MinimalCard(
                borderColor = bannerColor.copy(alpha = 0.5f),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag(if (isBlocked) "spending_limit_blocked_banner" else "spending_limit_warning_banner")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBlocked) {
                                spendingEvaluation.blockedMessage
                                    ?: "Monthly limit reached for ${spendingEvaluation.providerDisplayName}. Raise the limit in Settings to continue."
                            } else {
                                spendingEvaluation.warningMessage ?: spendingEvaluation.progressText
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = spendingEvaluation.progressText,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isBlocked && spendingEvaluation.canOverrideOnce) {
                                Text(
                                    text = "Override once",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable(onClick = onOverrideSpendingLimitOnce)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("spending_limit_override_once_button")
                                )
                            }
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable(onClick = onOpenProviderSettings)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("spending_limit_open_settings_button")
                            )
                        }
                    }
                }
            }
        }

        // Cheaper model suggestion card ("Try <model> to save about X%") after > 50,000 tokens on top-tier model
        val activeConvId = activeConversation?.id
        val cheaperSuggestion = activeConvId?.let { uiState.cheaperModelSuggestionByChatId[it] }
        if (activeConvId != null && cheaperSuggestion != null) {
            MinimalCard(
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("cheaper_model_suggestion_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cheaperSuggestion.bannerText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "This chat has used over 50,000 tokens.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MinimalChip(
                            label = "Switch",
                            selected = true,
                            onClick = {
                                onAcceptCheaperModelSuggestion(activeConvId, cheaperSuggestion.suggestedModelId)
                            },
                            testTag = "cheaper_model_switch_button"
                        )
                        Icon(
                            imageVector = LucideIcons.X,
                            contentDescription = "Dismiss suggestion",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDismissCheaperModelSuggestion(activeConvId) }
                                .testTag("cheaper_model_dismiss_button")
                        )
                    }
                }
            }
        }

        // Structured Error Card: Title, plain cause, one next step, and collapsible "Details" line
        uiState.activeError?.let { err ->
            var showErrorDetails by rememberSaveable(err.title, err.message) { mutableStateOf(false) }
            MinimalCard(
                borderColor = CoralError.copy(alpha = 0.45f),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("structured_error_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = CoralError,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("structured_error_title")
                        )
                        Icon(
                            imageVector = LucideIcons.X,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = onDismissError)
                                .testTag("structured_error_dismiss")
                        )
                    }
                    Text(
                        text = err.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("structured_error_cause")
                    )
                    if (err.suggestedFix.isNotBlank()) {
                        Text(
                            text = err.suggestedFix,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("structured_error_next_step")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val detailsText = err.details.ifBlank { "${err.category.name}: ${err.title}" }
                        Text(
                            text = if (showErrorDetails) "Hide Details" else "Details",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showErrorDetails = !showErrorDetails }
                                .padding(vertical = 2.dp)
                                .testTag("structured_error_details_toggle")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (err.unapprovedHost != null) {
                                Text(
                                    text = "Approve",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onApproveCleartextHostFromError(err.unapprovedHost) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (err.category == com.example.data.provider.AiErrorCategory.AUTH ||
                                err.suggestedFix.contains("Providers", ignoreCase = true) ||
                                err.suggestedFix.contains("Settings", ignoreCase = true)
                            ) {
                                Text(
                                    text = "Open Providers",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable(onClick = onOpenProviderSettings)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (showErrorDetails) {
                        val detailsText = err.details.ifBlank { "${err.category.name}: ${err.title}" }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("structured_error_details_content")
                        ) {
                            Text(
                                text = detailsText,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Main Chat Area: Empty state OR minimal message stream
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (threadItems.isEmpty()) {
                MinimalEmptyChatState(
                    onPickSuggestion = { prompt ->
                        onDraftChange(prompt)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_messages_lazy_column")
                ) {
                    items(threadItems, key = { it.key }) { item ->
                        when (item) {
                            is ThreadDisplayItem.SingleMessage -> {
                                val msg = item.message
                                val msgLogs = remember(conversationToolLogs, msg.id) {
                                    conversationToolLogs.filter { it.messageId == msg.id }
                                }
                                MinimalChatMessageItem(
                                    message = msg,
                                    providers = providers,
                                    models = models,
                                    combos = uiState.omniRouteCombos,
                                    toolLogs = msgLogs,
                                    liveInfoSources = uiState.liveInfoSourcesByMessageId[msg.id].orEmpty(),
                                    isLastAssistantMessage = msg.id == lastAssistantId,
                                    isTokenCountEstimated = msg.id in uiState.estimatedTokenMessageIds,
                                    cachedInputTokens = uiState.cachedTokensByMessageId[msg.id] ?: 0,
                                    activeToolLoopStatus = if (msg.isStreaming) uiState.activeToolLoopStatus else null,
                                    restartedOnNote = uiState.restartedOnProviderByMessageId[msg.id],
                                    onEditUserMessage = { onStartEditMessage(msg) },
                                    onRegenerate = onRegenerateLast,
                                    onCopiedToast = onCopiedToast,
                                    onPushToGitHub = onPushContentToGitHub,
                                    onRunCodeBlock = { lang, code ->
                                        onRunCodeBlock(lang, code, msg.id)
                                    },
                                    onOpenHtmlPreview = onOpenHtmlPreview
                                )
                            }

                            is ThreadDisplayItem.ComparisonRow -> {
                                MinimalComparisonRow(
                                    responses = item.responses,
                                    providers = providers,
                                    models = models,
                                    combos = uiState.omniRouteCombos,
                                    estimatedMessageIds = uiState.estimatedTokenMessageIds,
                                    cachedTokensByMessageId = uiState.cachedTokensByMessageId,
                                    onCopiedToast = onCopiedToast,
                                    onRunCodeBlock = onRunCodeBlock
                                )
                            }
                        }
                    }
                }
            }
        }

        // Conversation Usage Summary line at the bottom of each chat (includes tool calls and code runs)
        val completedAssistantMessages = remember(messages) {
            messages.filter { it.role.equals("ASSISTANT", ignoreCase = true) && !it.isStreaming }
        }
        if (completedAssistantMessages.isNotEmpty() || conversationToolLogs.isNotEmpty()) {
            val conversationSummaryText = remember(
                completedAssistantMessages,
                conversationToolLogs,
                uiState.estimatedTokenMessageIds,
                uiState.cachedTokensByMessageId
            ) {
                val totalIn = completedAssistantMessages.sumOf { msg ->
                    if (msg.promptTokens > 0) msg.promptTokens
                    else com.example.data.provider.ModelPriceTable.estimateTokensFromText(msg.content)
                }
                val totalOut = completedAssistantMessages.sumOf { msg ->
                    if (msg.completionTokens > 0) msg.completionTokens
                    else com.example.data.provider.ModelPriceTable.estimateTokensFromText(msg.content)
                }
                val totalCached = completedAssistantMessages.sumOf { msg ->
                    uiState.cachedTokensByMessageId[msg.id] ?: 0
                }
                val totalCost = completedAssistantMessages.sumOf { msg ->
                    if (msg.estimatedCostUsd > 0.0) msg.estimatedCostUsd
                    else com.example.data.provider.ModelPriceTable.calculateCostUsd(
                        modelId = msg.modelId,
                        inputTokens = if (msg.promptTokens > 0) msg.promptTokens else 1,
                        outputTokens = if (msg.completionTokens > 0) msg.completionTokens else 1,
                        cachedInputTokens = uiState.cachedTokensByMessageId[msg.id] ?: 0
                    )
                }
                val hasEstimated = completedAssistantMessages.any { msg ->
                    msg.id in uiState.estimatedTokenMessageIds || (msg.promptTokens == 0 && msg.completionTokens == 0)
                }
                val codeRunsCount = conversationToolLogs.count { log ->
                    log.toolName == com.example.data.mcp.JsSandboxExecutor.RUN_CODE_TOOL_NAME ||
                        log.serverId == com.example.data.mcp.JsSandboxExecutor.RUN_CODE_SERVER_ID
                }
                val mcpToolCallsCount = (conversationToolLogs.size - codeRunsCount).coerceAtLeast(0)
                com.example.data.provider.ModelPriceTable.formatConversationUsageSummary(
                    totalInputTokens = totalIn,
                    totalOutputTokens = totalOut,
                    totalEstimatedCostUsd = totalCost,
                    toolCallsCount = mcpToolCallsCount,
                    codeRunsCount = codeRunsCount,
                    hasEstimatedTokens = hasEstimated,
                    totalCachedInputTokens = totalCached
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("chat_conversation_usage_summary_bar")
            ) {
                Text(
                    text = conversationSummaryText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Active tool turn status indicator banner while loop runs
        if (uiState.isStreaming && !uiState.activeToolLoopStatus.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("chat_active_tool_turn_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = uiState.activeToolLoopStatus,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Smart Context Trimming line above the composer: "Older messages summarised to save context."
        // Tap to view the summary or turn trimming off for this chat (default on).
        var showContextSummaryDialog by rememberSaveable { mutableStateOf(false) }
        val activeSummaryText = activeConvId?.let { uiState.contextSummaryByChatId[it] }
        val isContextTrimmingEnabled = activeConvId?.let { uiState.contextTrimmingEnabledByChatId[it] ?: true } ?: true

        if (showContextSummaryDialog && activeConvId != null) {
            AlertDialog(
                onDismissRequest = { showContextSummaryDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Context Summary",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = activeSummaryText?.ifBlank { "No summary generated yet." } ?: "No summary generated yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Smart context trimming for this chat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            com.example.ui.components.MinimalToggle(
                                checked = isContextTrimmingEnabled,
                                onCheckedChange = { enabled ->
                                    onSetContextTrimmingEnabled(activeConvId, enabled)
                                    if (!enabled) {
                                        showContextSummaryDialog = false
                                    }
                                },
                                testTag = "context_trimming_chat_toggle"
                            )
                        }
                    }
                },
                confirmButton = {
                    MinimalButton(
                        text = "Close",
                        onClick = { showContextSummaryDialog = false }
                    )
                }
            )
        }

        if (!activeSummaryText.isNullOrBlank() && isContextTrimmingEnabled) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showContextSummaryDialog = true }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("context_trimming_summary_line")
            ) {
                Text(
                    text = com.example.data.provider.SmartContextTrimmer.COMPOSER_NOTICE_TEXT,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // First-time file attachment notice: "Attached files are sent to <provider>"
        uiState.showFirstTimeFileNoticeForProvider?.let { provName ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("first_time_file_attachment_notice")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FileAttachmentProcessor.formatFirstTimeNotice(provName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = LucideIcons.X,
                        contentDescription = "Dismiss notice",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(onClick = onDismissFirstTimeFileNotice)
                    )
                }
            }
        }

        // Background subagents of this conversation: slim row above the composer while
        // any are running, with one Cancel for all of them. Gone when none are running.
        val runningBackgroundJobs = uiState.backgroundSubagentJobs.filter {
            it.conversationId == activeConversation?.id &&
                it.status == com.example.ui.viewmodel.BackgroundSubagentStatus.RUNNING
        }
        if (activeConversation != null && runningBackgroundJobs.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("chat_background_subagents_row")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "◌ ${runningBackgroundJobs.size} subagent(s) working in background",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            onCancelBackgroundSubagents(activeConversation.id)
                        }
                    )
                }
            }
        }

        // Messages queued while a reply is generating: each sends itself through the
        // normal send path when a turn ends. This conversation's pills only.
        val queuedForConversation = uiState.queuedMessages.filter {
            it.conversationId == activeConversation?.id
        }
        if (activeConversation != null && queuedForConversation.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("chat_queued_messages_row")
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Queued",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    queuedForConversation.forEach { queued ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.testTag("queued_message_pill_${queued.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = queued.text,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 150.dp)
                                )
                                Icon(
                                    imageVector = LucideIcons.X,
                                    contentDescription = "Remove queued message",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRemoveQueuedMessage(queued.id) }
                                        .testTag("remove_queued_message_${queued.id}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Minimal Input Bar
        MinimalFloatingComposerBar(
            draft = uiState.draftMessage,
            isStreaming = uiState.isStreaming,
            isVoiceListening = uiState.isVoiceListening,
            // shortcut: main composer always gets paragraph Enter (the user asked for a
            // newline key, not send-on-Enter); re-wire uiState.enterToSendEnabled here
            // if the Settings toggle should drive this composer again.
            enterToSend = false,
            editingMessageId = uiState.editingMessageId,
            attachments = currentAttachments,
            onDraftChange = onDraftChange,
            onOpenAttachOptions = onOpenAttachSheet,
            onMicClick = onMicClick,
            onApplyAttachmentPreset = onApplyAttachmentPreset,
            onRemoveAttachment = { idx ->
                if (uiState.attachedMediaList.isNotEmpty()) {
                    onRemoveMediaAttachment(idx)
                } else {
                    onClearMediaAttachments()
                }
            },
            onCancelEdit = onCancelEditMessage,
            onSend = onSendMessage,
            onStop = onStopGeneration,
            onQueue = onQueueMessage,
            queuedCount = queuedForConversation.size
        )
    }
}

@Composable
private fun MinimalEmptyChatState(
    onPickSuggestion: (String) -> Unit
) {
    val suggestions = remember {
        listOf(
            "Review my architecture for edge cases",
            "Draft a clean Kotlin coroutine pipeline",
            "Summarize key tradeoffs in 3 bullets"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        com.example.ui.components.RunwayLogoIcon(size = 44.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Start a conversation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEachIndexed { idx, text ->
                MinimalChip(
                    label = text,
                    onClick = { onPickSuggestion(text) },
                    modifier = Modifier.testTag("empty_suggestion_chip_$idx")
                )
            }
        }
    }
}

/**
 * Messages:
 * - No bubbles for the AI (plain text on the #0B0D10 background)
 * - A soft rounded bubble only for user messages (#14171C surface with 1px subtle border)
 */
private data class AssistantResponseIdentity(
    val modelDisplayName: String,
    val rawModelId: String,
    val providerDisplayName: String,
    val isCombo: Boolean
)

private fun resolveAssistantIdentity(
    message: MessageEntity,
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    combos: List<OmniRouteComboDefinition>
): AssistantResponseIdentity {
    val rawModelId = message.modelId.ifBlank { "gpt-4o" }
    val matchedCombo = combos.firstOrNull { it.id.equals(rawModelId, ignoreCase = true) }
    val matchedModel = models.firstOrNull {
        it.providerInstanceId == message.providerInstanceId && it.modelId.equals(rawModelId, ignoreCase = true)
    } ?: models.firstOrNull { it.modelId.equals(rawModelId, ignoreCase = true) }

    val matchedProvider = providers.firstOrNull { it.id == message.providerInstanceId }
    val providerDisplayName = when {
        matchedProvider != null && matchedProvider.displayName.isNotBlank() -> matchedProvider.displayName
        rawModelId.startsWith("omniroute/", ignoreCase = true) ||
            message.providerInstanceId.contains("omniroute", ignoreCase = true) -> "OmniRoute Gateway"
        message.providerInstanceId.contains("openai", ignoreCase = true) ||
            rawModelId.startsWith("gpt-", ignoreCase = true) ||
            rawModelId.startsWith("o1", ignoreCase = true) ||
            rawModelId.startsWith("o3", ignoreCase = true) -> "OpenAI"
        message.providerInstanceId.contains("anthropic", ignoreCase = true) ||
            rawModelId.contains("claude", ignoreCase = true) -> "Anthropic"
        message.providerInstanceId.contains("gemini", ignoreCase = true) ||
            rawModelId.contains("gemini", ignoreCase = true) -> "Google Gemini"
        message.providerInstanceId.contains("groq", ignoreCase = true) -> "Groq"
        message.providerInstanceId.contains("openrouter", ignoreCase = true) -> "OpenRouter"
        message.providerInstanceId.contains("deepseek", ignoreCase = true) ||
            rawModelId.contains("deepseek", ignoreCase = true) -> "DeepSeek"
        message.providerInstanceId.isNotBlank() -> message.providerInstanceId
            .removePrefix("preset_")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        else -> "AI Provider"
    }

    val priceEntry = com.example.data.provider.ModelPriceTable.lookupPricing(rawModelId)
    val modelDisplayName = when {
        matchedCombo != null && matchedCombo.displayName.isNotBlank() -> matchedCombo.displayName
        matchedModel != null && matchedModel.displayName.isNotBlank() -> matchedModel.displayName
        priceEntry.modelPattern != "default" -> priceEntry.displayName
        else -> rawModelId.substringAfterLast("/")
    }

    return AssistantResponseIdentity(
        modelDisplayName = modelDisplayName,
        rawModelId = rawModelId,
        providerDisplayName = providerDisplayName,
        isCombo = matchedCombo != null || matchedModel?.isCombo == true || rawModelId.startsWith("omniroute/", ignoreCase = true)
    )
}

@Composable
private fun AssistantResponderHeader(
    message: MessageEntity,
    identity: AssistantResponseIdentity,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("assistant_response_ai_header_${message.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Tinted logo chip (Concept A reply-card header, both modes)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LucideIcons.Sparkles,
                contentDescription = "AI Responder",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = identity.modelDisplayName,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        val shortRaw = identity.rawModelId.substringAfterLast("/")
        if (!identity.modelDisplayName.equals(shortRaw, ignoreCase = true) &&
            !identity.modelDisplayName.equals(identity.rawModelId, ignoreCase = true)
        ) {
            Text(
                text = "($shortRaw)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
            Text(
                text = identity.providerDisplayName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (message.isStreaming) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(9.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Responding…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else if (message.latencyMs > 0L) {
            Text(
                text = "· ${message.latencyMs}ms",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun MinimalChatMessageItem(
    message: MessageEntity,
    providers: List<ProviderInstanceEntity> = emptyList(),
    models: List<ModelDescriptorEntity> = emptyList(),
    combos: List<OmniRouteComboDefinition> = emptyList(),
    toolLogs: List<ToolCallLogEntity>,
    liveInfoSources: List<com.example.data.mcp.LiveInfoSource> = emptyList(),
    isLastAssistantMessage: Boolean,
    isTokenCountEstimated: Boolean = false,
    cachedInputTokens: Int = 0,
    activeToolLoopStatus: String? = null,
    restartedOnNote: String? = null,
    onEditUserMessage: () -> Unit,
    onRegenerate: () -> Unit,
    onCopiedToast: (String) -> Unit,
    onPushToGitHub: (title: String, fileName: String, content: String) -> Unit,
    onRunCodeBlock: ((language: String, code: String) -> Unit)? = null,
    onOpenHtmlPreview: ((htmlDocument: String, title: String) -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val accentBubble = LocalRunwayAccentBubble.current
    val resolvedSources = remember(toolLogs, liveInfoSources) {
        val fromLogs = com.example.data.mcp.LiveInfoToolsService.extractSourcesFromToolLogs(toolLogs)
        (liveInfoSources + fromLogs).distinctBy { it.link }
    }
    val isUser = message.role.equals("user", ignoreCase = true)
    val attachments = remember(message.attachmentUri, message.attachmentMimeType) {
        message.getAttachments()
    }
    val assistantIdentity = remember(message.modelId, message.providerInstanceId, providers, models, combos) {
        resolveAssistantIdentity(
            message = message,
            providers = providers,
            models = models,
            combos = combos
        )
    }
    var fullScreenAttachmentUri by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoUri by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_message_${message.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User bubble: deep shade of the active theme accent, filled
            // in BOTH modes (Concept A) — cream text on top.
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accentBubble.fill,
                border = BorderStroke(1.dp, accentBubble.fill),
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clickable(onClick = onEditUserMessage)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (attachments.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            attachments.forEach { media ->
                                if (media.isDocument) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = LucideIcons.Code,
                                                contentDescription = "Document",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = media.fileName.ifBlank { "document.txt" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                if (!media.isVideo) {
                                                    fullScreenAttachmentUri = media.uri
                                                } else {
                                                    fullScreenVideoUri = media.uri
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (media.isVideo) {
                                            Icon(
                                                imageVector = LucideIcons.Video,
                                                contentDescription = "Video",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            AsyncImage(
                                                model = media.uri,
                                                contentDescription = "Attached photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CreamOnDark
                        )
                    }
                }
            }
        } else {
            // AI responses: full-width card in BOTH modes (Concept A) —
            // white card on cream in light, #151920 card with a subtle
            // light outline on near-black in dark.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top header showing which AI model and provider is responding to the request
                AssistantResponderHeader(
                    message = message,
                    identity = assistantIdentity
                )

                if (!restartedOnNote.isNullOrBlank()) {
                    Text(
                        text = restartedOnNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("failover_restarted_note_${message.id}")
                    )
                }

                if (toolLogs.isNotEmpty()) {
                    toolLogs.forEach { log ->
                        CollapsibleToolCallCard(
                            log = log,
                            onOpenHtmlPreview = onOpenHtmlPreview
                        )
                    }
                }

                if (attachments.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        attachments.forEach { media ->
                            if (!media.isVideo) {
                                com.example.ui.components.RunwayReplyImageItem(
                                    altText = media.name ?: "Generated image",
                                    imageUrl = media.uri,
                                    onClick = { fullScreenAttachmentUri = media.uri }
                                )
                            } else {
                                ChatReplyVideoCard(
                                    media = media,
                                    onClick = { fullScreenVideoUri = media.uri }
                                )
                            }
                        }
                    }
                }

                com.example.ui.components.RunwayRichMarkdownRenderer(
                    content = if (message.content.isBlank() && message.isStreaming) "…" else message.content,
                    isStreaming = message.isStreaming,
                    onCopiedToast = onCopiedToast,
                    onRunCodeBlock = onRunCodeBlock
                )

                if (message.isStreaming && !activeToolLoopStatus.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("message_active_tool_turn_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = activeToolLoopStatus,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Sources used in the reply (from web_search and news_headlines), with titles and links
                if (resolvedSources.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("message_sources_section_${message.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = LucideIcons.Search,
                                    contentDescription = "Sources",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Sources (${resolvedSources.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            resolvedSources.forEachIndexed { idx, src ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            runCatching { uriHandler.openUri(src.link) }.onFailure {
                                                clipboard.setText(AnnotatedString(src.link))
                                                onCopiedToast("Copied source link")
                                            }
                                        }
                                        .padding(vertical = 3.dp)
                                        .testTag("message_source_item_${message.id}_$idx"),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = buildString {
                                            append("${idx + 1}. ${src.title}")
                                            if (src.sourceName.isNotBlank()) {
                                                append(" · ${src.sourceName}")
                                            }
                                            if (src.publishDate.isNotBlank()) {
                                                append(" (${src.publishDate})")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = src.link,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Per-message usage footer under every assistant reply (input tokens, output tokens, estimated cost)
                if (!message.isStreaming && (message.content.isNotBlank() || toolLogs.isNotEmpty())) {
                    val footerText = remember(
                        message.id,
                        message.promptTokens,
                        message.completionTokens,
                        message.estimatedCostUsd,
                        message.modelId,
                        message.content,
                        isTokenCountEstimated,
                        cachedInputTokens
                    ) {
                        val inTok = if (message.promptTokens > 0) {
                            message.promptTokens
                        } else {
                            com.example.data.provider.ModelPriceTable.estimateTokensFromText(message.content)
                        }
                        val outTok = if (message.completionTokens > 0) {
                            message.completionTokens
                        } else {
                            com.example.data.provider.ModelPriceTable.estimateTokensFromText(message.content)
                        }
                        val estTokensFlag = isTokenCountEstimated || (message.promptTokens == 0 && message.completionTokens == 0)
                        val costVal = if (message.estimatedCostUsd > 0.0) {
                            message.estimatedCostUsd
                        } else {
                            com.example.data.provider.ModelPriceTable.calculateCostUsd(
                                modelId = message.modelId,
                                inputTokens = inTok,
                                outputTokens = outTok,
                                cachedInputTokens = cachedInputTokens
                            )
                        }
                        com.example.data.provider.ModelPriceTable.formatMessageUsageFooter(
                            inputTokens = inTok,
                            outputTokens = outTok,
                            estimatedCostUsd = costVal,
                            isTokenCountEstimated = estTokensFlag,
                            cachedInputTokens = cachedInputTokens
                        )
                    }
                    Text(
                        text = footerText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .testTag("assistant_message_usage_footer_${message.id}")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable {
                                    clipboard.setText(AnnotatedString(message.content))
                                    onCopiedToast("Copied Markdown")
                                }
                                .testTag("copy_reply_markdown_${message.id}")
                        ) {
                            Icon(
                                imageVector = LucideIcons.Copy,
                                contentDescription = "Copy reply",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        if (isLastAssistantMessage) {
                            Icon(
                                imageVector = LucideIcons.RefreshCw,
                                contentDescription = "Regenerate",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable(onClick = onRegenerate)
                            )
                        }
                    }
                }
            }
        }
    }

    fullScreenAttachmentUri?.let { uri ->
        com.example.ui.components.RunwayFullScreenImageDialog(
            altText = "Attachment",
            imageUrl = uri,
            onDismiss = { fullScreenAttachmentUri = null },
            onToast = onCopiedToast
        )
    }

    fullScreenVideoUri?.let { vidUri ->
        ChatFullScreenVideoDialog(
            videoUri = vidUri,
            onDismiss = { fullScreenVideoUri = null },
            onToast = onCopiedToast
        )
    }
}

@Composable
private fun MinimalComparisonRow(
    responses: List<MessageEntity>,
    providers: List<ProviderInstanceEntity> = emptyList(),
    models: List<ModelDescriptorEntity> = emptyList(),
    combos: List<OmniRouteComboDefinition> = emptyList(),
    estimatedMessageIds: Set<String> = emptySet(),
    cachedTokensByMessageId: Map<String, Int> = emptyMap(),
    onCopiedToast: (String) -> Unit,
    onRunCodeBlock: (language: String, code: String, messageId: String?) -> Unit = { _, _, _ -> }
) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        responses.forEach { resp ->
            val identity = remember(resp.modelId, resp.providerInstanceId, providers, models, combos) {
                resolveAssistantIdentity(resp, providers, models, combos)
            }
            MinimalCard(
                modifier = Modifier.width(280.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = LucideIcons.Sparkles,
                                contentDescription = "AI Responder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = identity.modelDisplayName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Text(
                            text = if (resp.isStreaming) "${identity.providerDisplayName} · Responding…" else "${identity.providerDisplayName} · ${identity.rawModelId.substringAfterLast("/")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (resp.isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = LucideIcons.Copy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                clipboard.setText(AnnotatedString(resp.content))
                                onCopiedToast("Copied Markdown")
                            }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                com.example.ui.components.RunwayRichMarkdownRenderer(
                    content = if (resp.content.isBlank() && resp.isStreaming) "…" else resp.content,
                    isStreaming = resp.isStreaming,
                    onCopiedToast = onCopiedToast,
                    onRunCodeBlock = { lang, code ->
                        onRunCodeBlock(lang, code, resp.id)
                    }
                )
                if (!resp.isStreaming && resp.content.isNotBlank()) {
                    val cachedTok = cachedTokensByMessageId[resp.id] ?: 0
                    val footer = com.example.data.provider.ModelPriceTable.formatMessageUsageFooter(
                        inputTokens = resp.promptTokens.takeIf { it > 0 }
                            ?: com.example.data.provider.ModelPriceTable.estimateTokensFromText(resp.content),
                        outputTokens = resp.completionTokens.takeIf { it > 0 }
                            ?: com.example.data.provider.ModelPriceTable.estimateTokensFromText(resp.content),
                        estimatedCostUsd = resp.estimatedCostUsd.takeIf { it > 0.0 }
                            ?: com.example.data.provider.ModelPriceTable.calculateCostUsd(
                                resp.modelId,
                                resp.promptTokens.coerceAtLeast(1),
                                resp.completionTokens.coerceAtLeast(1),
                                cachedTok
                            ),
                        isTokenCountEstimated = resp.id in estimatedMessageIds || (resp.promptTokens == 0 && resp.completionTokens == 0),
                        cachedInputTokens = cachedTok
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonospaceFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Input bar: a single rounded floating field with a "+" button on the left
 * (the attach image, video and enhance options open from it) and an accent send button on the right.
 */
@Composable
private fun MinimalFloatingComposerBar(
    draft: String,
    isStreaming: Boolean,
    isVoiceListening: Boolean = false,
    enterToSend: Boolean,
    editingMessageId: String?,
    attachments: List<ChatMediaAttachment>,
    onDraftChange: (String) -> Unit,
    onOpenAttachOptions: () -> Unit,
    onMicClick: () -> Unit = {},
    onApplyAttachmentPreset: (FileAttachmentProcessor.AttachmentPreset) -> Unit = {},
    onRemoveAttachment: (Int) -> Unit,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onQueue: (() -> Unit)? = null,
    queuedCount: Int = 0
) {
    val composerFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val composerKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (editingMessageId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Editing message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onCancelEdit)
                )
            }
        }

        // Attached media & document chips with an X + Preset actions strip
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                attachments.forEachIndexed { index, item ->
                    if (item.isDocument) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.testTag("attachment_chip_$index")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = LucideIcons.Code,
                                    contentDescription = "Document",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = item.fileName.ifBlank { "document.txt" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 150.dp)
                                )
                                Icon(
                                    imageVector = LucideIcons.X,
                                    contentDescription = "Remove attachment",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRemoveAttachment(index) }
                                        .testTag("remove_attachment_chip_$index")
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .testTag("attachment_chip_$index")
                        ) {
                            if (item.isVideo) {
                                Icon(
                                    imageVector = LucideIcons.Video,
                                    contentDescription = "Video",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.Center)
                                )
                            } else {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = "Attachment",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                                    .clickable { onRemoveAttachment(index) }
                                    .testTag("remove_attachment_chip_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = LucideIcons.X,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Presets: Summarise, Find key points, Analyse data, Explain code, Compare two files
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FileAttachmentProcessor.PRESET_ACTIONS.forEach { preset ->
                    MinimalChip(
                        label = preset.label,
                        selected = false,
                        onClick = { onApplyAttachmentPreset(preset) },
                        testTag = "attachment_preset_${preset.label.lowercase().replace(' ', '_')}"
                    )
                }
            }
        }

        // Single rounded floating field
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "+" button on the left
                IconButton(
                    onClick = {
                        composerFocusManager.clearFocus(force = true)
                        composerKeyboard?.hide()
                        onOpenAttachOptions()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("composer_plus_button")
                ) {
                    Icon(
                        imageVector = LucideIcons.Plus,
                        contentDescription = "Add attachment or enhance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Clean text input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .testTag("chat_composer_input"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = if (isVoiceListening) "Listening…" else "Message…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isVoiceListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (enterToSend) ImeAction.Send else ImeAction.None
                        ),
                        keyboardActions = if (enterToSend) {
                            KeyboardActions(
                                onSend = {
                                    if (draft.isNotBlank() || attachments.isNotEmpty()) {
                                        composerFocusManager.clearFocus(force = true)
                                        composerKeyboard?.hide()
                                        onSend()
                                    }
                                }
                            )
                        } else {
                            // No send action: Enter inserts a newline (paragraph key).
                            KeyboardActions.Default
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Mic button in the composer (SpeechRecognizer; asks RECORD_AUDIO permission on first tap)
                IconButton(
                    onClick = {
                        composerFocusManager.clearFocus(force = true)
                        composerKeyboard?.hide()
                        onMicClick()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("composer_mic_button")
                ) {
                    Icon(
                        imageVector = LucideIcons.Mic,
                        contentDescription = if (isVoiceListening) "Stop voice input" else "Voice input",
                        tint = if (isVoiceListening) CoralError else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Queue-send button: only while streaming (when the accent button is
                // Stop), only for plain text, and only below the per-chat queue cap.
                val canQueue = onQueue != null && isStreaming && draft.isNotBlank() &&
                    attachments.isEmpty() &&
                    queuedCount < com.example.ui.viewmodel.MAX_QUEUED_MESSAGES_PER_CONVERSATION
                if (canQueue) {
                    IconButton(
                        onClick = { onQueue?.invoke() },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("composer_queue_button")
                    ) {
                        Icon(
                            imageVector = LucideIcons.ArrowUp,
                            contentDescription = "Queue message to send when the reply finishes",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Accent send / stop button on the right
                val canSend = draft.isNotBlank() || attachments.isNotEmpty()
                Surface(
                    onClick = {
                        if (isStreaming) {
                            onStop()
                        } else if (canSend) {
                            composerFocusManager.clearFocus(force = true)
                            composerKeyboard?.hide()
                            onSend()
                        }
                    },
                    enabled = isStreaming || canSend,
                    shape = CircleShape,
                    color = if (isStreaming || canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("chat_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isStreaming) LucideIcons.Square else LucideIcons.ArrowUp,
                            contentDescription = if (isStreaming) "Stop" else "Send",
                            tint = if (isStreaming || canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalToolRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tinted icon chip (Concept A row language, both modes)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MinimalConversationDrawerContent(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (ConversationEntity) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onExportConversation: (String, Boolean) -> Unit
) {
    val filtered = remember(conversations, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) conversations
        else conversations.filter { it.title.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                com.example.ui.components.RunwayLogoIcon(size = 28.dp)
                com.example.ui.components.RunwayWordmark()
            }
            MinimalButton(
                text = "New",
                icon = LucideIcons.Plus,
                onClick = onNewConversation,
                modifier = Modifier.testTag("drawer_new_chat_button")
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    "Search…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = LucideIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.id }) { conv ->
                val isSelected = conv.id == activeConversationId
                Surface(
                    onClick = { onSelectConversation(conv.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conv.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = conv.modelId.substringAfterLast("/"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = LucideIcons.Edit2,
                                contentDescription = "Rename",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onRenameConversation(conv) }
                            )
                            Icon(
                                imageVector = LucideIcons.Trash2,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onDeleteConversation(conv.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatReplyVideoCard(
    media: ChatMediaAttachment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("reply_inline_video")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = LucideIcons.Play,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.Video,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "VIDEO CLIP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = media.name?.takeIf { it.isNotBlank() } ?: "Generated Video (Tap to play)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ChatFullScreenVideoDialog(
    videoUri: String,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.Video,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AI Video Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = LucideIcons.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.example.ui.components.MinimalButton(
                        text = "Copy link",
                        icon = LucideIcons.Copy,
                        onClick = {
                            clipboard.setText(AnnotatedString(videoUri))
                            onToast("Video link copied to clipboard")
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.example.ui.components.MinimalButton(
                        text = "Close",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

