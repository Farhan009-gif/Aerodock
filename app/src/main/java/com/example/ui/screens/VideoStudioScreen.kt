package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.local.CapabilityTag
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.VideoJobEntity
import com.example.data.provider.ModelCapabilityRegistry
import com.example.data.provider.NormalizedAiError
import com.example.ui.components.LucideIcons
import com.example.ui.components.MinimalButton
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalChip
import com.example.ui.theme.CoralError

import com.example.ui.viewmodel.OmniUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoStudioScreen(
    uiState: OmniUiState,
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    videoJobs: List<VideoJobEntity>,
    onSelectProviderAndModel: (String, String) -> Unit,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onSelectAspectRatio: (String) -> Unit,
    onSelectResolution: (String) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onSelectCameraMotion: (String) -> Unit,
    onAttachSourceImage: (String?) -> Unit,
    onSubmitVideoJob: () -> Unit,
    onPollJobNow: (VideoJobEntity) -> Unit,
    onCancelJob: (VideoJobEntity) -> Unit,
    onInspectJob: (VideoJobEntity?) -> Unit,
    onReuseSettings: (VideoJobEntity) -> Unit,
    onToggleFavoriteJob: (VideoJobEntity) -> Unit,
    onDeleteJob: (VideoJobEntity) -> Unit,
    onDownloadJob: (VideoJobEntity) -> Unit,
    onAddManualModel: (String, String) -> Unit,
    onApproveCleartextHostFromError: (String) -> Unit,
    onDismissError: () -> Unit,
    onPublishToYouTube: (VideoJobEntity) -> Unit = {},
    onUpsertOmniRouteCombo: (
        rawId: String,
        displayName: String,
        strategy: String,
        targetModels: List<String>,
        description: String
    ) -> Unit = { _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showVideoModelPicker by rememberSaveable { mutableStateOf(false) }
    var showComboBuilderDialog by rememberSaveable { mutableStateOf(false) }
    var showAdvancedControls by rememberSaveable { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        onAttachSourceImage(uri?.toString())
    }

    val videoProviders = remember(providers) {
        val explicit = providers.filter {
            it.isEnabled && (CapabilityTag.VIDEO in it.capabilitySet() || it.defaultVideoModelId.isNotBlank())
        }
        explicit.ifEmpty { providers.filter { it.isEnabled }.ifEmpty { providers } }
    }

    val selectedProvider = remember(videoProviders, uiState.selectedVideoProviderId) {
        videoProviders.firstOrNull { it.id == uiState.selectedVideoProviderId }
            ?: videoProviders.firstOrNull { it.isDefaultForVideo }
            ?: videoProviders.firstOrNull()
    }

    val activeModelId = remember(selectedProvider, uiState.selectedVideoModelId) {
        uiState.selectedVideoModelId.ifBlank {
            selectedProvider?.defaultVideoModelId?.ifBlank { "omniroute/video-combo" } ?: "omniroute/video-combo"
        }
    }

    val capabilities = remember(activeModelId, selectedProvider) {
        ModelCapabilityRegistry.getVideoCapabilities(
            modelId = activeModelId,
            presetType = selectedProvider?.presetType ?: "OMNIROUTE"
        )
    }

    if (showComboBuilderDialog) {
        OmniRouteComboBuilderDialog(
            initialCombo = null,
            defaultModality = "VIDEO",
            onDismiss = { showComboBuilderDialog = false },
            onSave = { rawId, displayName, _, strategy, targetModels, description ->
                onUpsertOmniRouteCombo(rawId, displayName, strategy, targetModels, description)
                showComboBuilderDialog = false
            }
        )
    }

    if (showVideoModelPicker) {
        VideoModelPickerSheet(
            providers = videoProviders,
            models = models,
            omniRouteCombos = uiState.omniRouteCombos,
            selectedProviderId = selectedProvider?.id,
            selectedModelId = activeModelId,
            onSelect = { provId, modId ->
                onSelectProviderAndModel(provId, modId)
                showVideoModelPicker = false
            },
            onAddCustomModel = { provId, modId ->
                onAddManualModel(provId, modId)
                onSelectProviderAndModel(provId, modId)
                showVideoModelPicker = false
            },
            onNewVideoCombo = {
                showVideoModelPicker = false
                showComboBuilderDialog = true
            },
            onDismiss = { showVideoModelPicker = false }
        )
    }

    uiState.inspectingVideoJob?.let { inspected ->
        VideoPlayerInspectorDialog(
            job = inspected,
            onDismiss = { onInspectJob(null) },
            onDownload = { onDownloadJob(inspected) },
            onCopyPrompt = {
                clipboardManager.setText(AnnotatedString(inspected.prompt))
            },
            onReuseSettings = {
                onReuseSettings(inspected)
                onInspectJob(null)
            },
            onPollNow = { onPollJobNow(inspected) },
            onToggleFavorite = { onToggleFavoriteJob(inspected) },
            onDelete = { onDeleteJob(inspected) },
            onPublishToYouTube = { onPublishToYouTube(inspected) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("video_studio_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Model Selector Pill at the Top
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        onClick = { showVideoModelPicker = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("video_model_selector_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = activeModelId.substringAfterLast("/"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = LucideIcons.ChevronDown,
                                contentDescription = "Select Video Model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    MinimalChip(
                        label = "+ Combo",
                        selected = false,
                        onClick = { showComboBuilderDialog = true },
                        testTag = "video_studio_new_combo_button"
                    )
                }

                MinimalChip(
                    label = if (showAdvancedControls) "Hide options" else "Options",
                    selected = showAdvancedControls,
                    onClick = { showAdvancedControls = !showAdvancedControls },
                    icon = LucideIcons.Sliders
                )
            }
        }

        uiState.activeError?.let { err ->
            item {
                VideoErrorBanner(
                    error = err,
                    onApproveCleartextHost = onApproveCleartextHostFromError,
                    onDismiss = onDismissError
                )
            }
        }

        // 2. Prompt Field, Aspect-Ratio & Camera Motion Chips
        item {
            MinimalCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.videoPrompt,
                        onValueChange = onPromptChange,
                        placeholder = {
                            Text(
                                text = "Describe the video scene and motion...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        ),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_prompt_input")
                    )

                    // Aspect Ratio + Duration Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        capabilities.supportedAspectRatios.forEach { ratio ->
                            MinimalChip(
                                label = ratio,
                                selected = uiState.videoAspectRatio == ratio,
                                onClick = { onSelectAspectRatio(ratio) }
                            )
                        }
                        capabilities.supportedDurations.forEach { sec ->
                            MinimalChip(
                                label = "${sec}s",
                                selected = uiState.videoDurationSeconds == sec,
                                onClick = { onSelectDuration(sec) }
                            )
                        }
                    }

                    // Camera Motion Style Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModelCapabilityRegistry.CAMERA_MOTIONS.forEach { motion ->
                            MinimalChip(
                                label = motion,
                                selected = uiState.videoCameraMotion == motion,
                                onClick = { onSelectCameraMotion(motion) }
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAdvancedControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Resolution:", style = MaterialTheme.typography.labelSmall)
                                capabilities.supportedResolutions.forEach { res ->
                                    MinimalChip(
                                        label = res,
                                        selected = uiState.videoResolution == res,
                                        onClick = { onSelectResolution(res) }
                                    )
                                }
                            }

                            if (capabilities.supportsNegativePrompt) {
                                OutlinedTextField(
                                    value = uiState.videoNegativePrompt,
                                    onValueChange = onNegativePromptChange,
                                    placeholder = { Text("Negative prompt (optional)", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("video_negative_prompt_input")
                                )
                            }

                            if (capabilities.supportsImageToVideo) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Starting frame", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!uiState.videoSourceImageUri.isNullOrBlank()) {
                                            MinimalChip(
                                                label = "Clear",
                                                selected = false,
                                                onClick = { onAttachSourceImage(null) }
                                            )
                                        }
                                        MinimalChip(
                                            label = if (uiState.videoSourceImageUri == null) "Attach frame" else "Change",
                                            selected = uiState.videoSourceImageUri != null,
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            icon = LucideIcons.Image,
                                            testTag = "pick_img2video_button"
                                        )
                                    }
                                }
                            }

                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                MinimalButton(
                                    text = "Enable completion notifications",
                                    onClick = {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    filled = false
                                )
                            }
                        }
                    }

                    MinimalButton(
                        text = if (uiState.isSubmittingVideo) "Submitting..." else "Generate Video",
                        onClick = onSubmitVideoJob,
                        enabled = !uiState.isSubmittingVideo && uiState.videoPrompt.isNotBlank(),
                        icon = LucideIcons.Video,
                        filled = true,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "submit_video_job_button"
                    )
                }
            }
        }

        // 2b. 12-Pillar AI Video Production & Post-Processing Suite
        item {
            com.example.ui.components.VideoProductionSuiteSection(
                currentPrompt = uiState.videoPrompt,
                onApplyPromptAndSettings = { synthPrompt, negPrompt, aspect, res, durationSec, camera ->
                    onPromptChange(synthPrompt)
                    onNegativePromptChange(negPrompt)
                    onSelectAspectRatio(aspect)
                    onSelectResolution(res)
                    onSelectDuration(durationSec)
                    onSelectCameraMotion(camera)
                }
            )
        }

        // 3. Results Grid with 2 Columns and No Borders
        if (videoJobs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Generated videos will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val rows = videoJobs.chunked(2)
            items(rows) { rowPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPair.forEach { job ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                .clickable { onInspectJob(job) }
                                .padding(12.dp)
                                .testTag("video_job_card_${job.id}")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${job.aspectRatio} · ${job.durationSeconds}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = if (job.status == "COMPLETED") LucideIcons.Play else LucideIcons.Clock,
                                        contentDescription = job.status,
                                        tint = if (job.status == "COMPLETED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Text(
                                    text = job.prompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (job.status in listOf("QUEUED", "PROCESSING")) {
                                    LinearProgressIndicator(
                                        progress = { (job.progressPercent / 100f).coerceIn(0.05f, 1f) },
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = job.modelId.substringAfterLast("/"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (rowPair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoPlayerInspectorDialog(
    job: VideoJobEntity,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCopyPrompt: () -> Unit,
    onReuseSettings: () -> Unit,
    onPollNow: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onPublishToYouTube: () -> Unit = {}
) {
    val context = LocalContext.current
    val formattedDate = remember(job.createdAt) {
        SimpleDateFormat("MMM d · HH:mm", Locale.US).format(Date(job.createdAt))
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
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = job.modelId.substringAfterLast("/"),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${job.providerDisplayName} · $formattedDate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                if (job.status == "COMPLETED" && !job.videoUri.isNullOrBlank()) {
                    val exoPlayer = remember(job.videoUri) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(Uri.parse(job.videoUri)))
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (job.aspectRatio == "9:16") 9f / 16f else 16f / 9f)
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(16.dp))
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
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${job.status} (${job.progressPercent}%)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (job.status in listOf("QUEUED", "PROCESSING")) {
                                Spacer(modifier = Modifier.height(10.dp))
                                MinimalButton(
                                    text = "Poll status",
                                    onClick = onPollNow,
                                    filled = false
                                )
                            }
                        }
                    }
                }

                Text(
                    text = job.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (job.status == "COMPLETED" && !job.videoUri.isNullOrBlank()) {
                        MinimalButton(
                            text = "Save MP4",
                            onClick = onDownload,
                            icon = LucideIcons.Download,
                            filled = true,
                            testTag = "video_inspector_download_button"
                        )
                        MinimalButton(
                            text = "YouTube",
                            onClick = onPublishToYouTube,
                            icon = LucideIcons.Video,
                            filled = false,
                            testTag = "video_inspector_publish_youtube_button"
                        )
                    }
                    MinimalButton(
                        text = "Copy Prompt",
                        onClick = onCopyPrompt,
                        icon = LucideIcons.Copy,
                        filled = false
                    )
                    MinimalButton(
                        text = "Reuse",
                        onClick = onReuseSettings,
                        icon = LucideIcons.Refresh,
                        filled = false
                    )
                    MinimalButton(
                        text = "Delete",
                        onClick = onDelete,
                        icon = LucideIcons.Trash,
                        filled = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoModelPickerSheet(
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    omniRouteCombos: List<com.example.data.provider.OmniRouteComboDefinition>,
    selectedProviderId: String?,
    selectedModelId: String,
    onSelect: (String, String) -> Unit,
    onAddCustomModel: (String, String) -> Unit,
    onNewVideoCombo: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeProvId by rememberSaveable {
        mutableStateOf(selectedProviderId ?: providers.firstOrNull()?.id.orEmpty())
    }
    var customModelInput by rememberSaveable { mutableStateOf("") }

    val providerModels = remember(models, omniRouteCombos, activeProvId, providers) {
        val dbModels = models.filter {
            it.providerInstanceId == activeProvId &&
                (CapabilityTag.VIDEO in it.capabilitySet() ||
                    it.modelId.contains("video", true) ||
                    it.modelId.contains("veo", true) ||
                    it.modelId.contains("ray", true) ||
                    it.modelId.contains("gen3", true) ||
                    it.modelId.contains("gen4", true) ||
                    it.modelId.contains("wan", true) ||
                    it.modelId.contains("kling", true) ||
                    it.modelId.contains("hunyuan", true) ||
                    it.modelId.contains("seedance", true) ||
                    it.modelId.contains("cinema", true) ||
                    it.modelId.contains("sora", true))
        }.toMutableList()

        if (activeProvId.contains("omniroute", ignoreCase = true)) {
            val seen = dbModels.map { it.modelId }.toMutableSet()
            omniRouteCombos.filter { it.modality.equals("VIDEO", ignoreCase = true) }.forEach { combo ->
                if (seen.add(combo.id)) {
                    dbModels.add(
                        0,
                        ModelCapabilityRegistry.buildDescriptor(
                            providerInstanceId = activeProvId,
                            rawModelId = combo.id,
                            displayNameOverride = combo.displayName,
                            explicitCombo = true,
                            explicitCapabilities = setOf(CapabilityTag.VIDEO)
                        )
                    )
                }
            }
            com.example.data.provider.EndpointUrlNormalizer.defaultOmniRouteVideoModels.forEach { (id, label) ->
                if (seen.add(id)) {
                    dbModels.add(
                        ModelCapabilityRegistry.buildDescriptor(
                            providerInstanceId = activeProvId,
                            rawModelId = id,
                            displayNameOverride = label,
                            explicitCombo = id.contains("combo") || id.contains("director"),
                            explicitCapabilities = setOf(CapabilityTag.VIDEO)
                        )
                    )
                }
            }
        }

        if (dbModels.isNotEmpty()) {
            dbModels
        } else {
            val prov = providers.firstOrNull { it.id == activeProvId }
            val fallbackId = prov?.defaultVideoModelId?.takeIf { it.isNotBlank() } ?: "omniroute/video-combo"
            listOf(
                ModelCapabilityRegistry.buildDescriptor(
                    providerInstanceId = activeProvId,
                    rawModelId = fallbackId,
                    explicitCapabilities = setOf(CapabilityTag.VIDEO)
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Model & Combos",
                    style = MaterialTheme.typography.titleLarge
                )
                MinimalButton(
                    text = "+ Video Combo",
                    onClick = onNewVideoCombo,
                    filled = true,
                    testTag = "video_sheet_new_combo_button"
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                providers.forEach { prov ->
                    MinimalChip(
                        label = prov.displayName,
                        selected = prov.id == activeProvId,
                        onClick = { activeProvId = prov.id }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it },
                    placeholder = { Text("Custom video model ID (e.g. omniroute/wan-2.1-t2v)...", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                MinimalButton(
                    text = "Use",
                    onClick = {
                        val trimmed = customModelInput.trim()
                        if (trimmed.isNotEmpty() && activeProvId.isNotEmpty()) {
                            onAddCustomModel(activeProvId, trimmed)
                            customModelInput = ""
                        }
                    },
                    enabled = customModelInput.isNotBlank()
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(providerModels, key = { "${it.providerInstanceId}:${it.modelId}" }) { mod ->
                    val isSelected = mod.providerInstanceId == selectedProviderId && mod.modelId == selectedModelId
                    val comboDef = omniRouteCombos.firstOrNull { it.id.equals(mod.modelId, ignoreCase = true) }
                    Surface(
                        onClick = { onSelect(mod.providerInstanceId, mod.modelId) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = mod.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (mod.isCombo || comboDef != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = if (comboDef != null) "COMBO · ${comboDef.strategy}" else "COMBO",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (comboDef != null) {
                                        "${mod.modelId} → ${comboDef.targetModels.joinToString(" → ")}"
                                    } else {
                                        mod.modelId
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = LucideIcons.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VideoErrorBanner(
    error: NormalizedAiError,
    onApproveCleartextHost: (String) -> Unit,
    onDismiss: () -> Unit
) {
    MinimalCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = error.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = CoralError
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = LucideIcons.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = error.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            error.unapprovedHost?.let { host ->
                MinimalButton(
                    text = "Approve HTTP Host '$host'",
                    onClick = { onApproveCleartextHost(host) },
                    filled = false
                )
            }
        }
    }
}
