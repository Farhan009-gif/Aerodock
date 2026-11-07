package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.CapabilityTag
import com.example.data.local.GeneratedImageEntity
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.provider.ModelCapabilityRegistry
import com.example.data.provider.NormalizedAiError
import com.example.ui.components.LucideIcons
import com.example.ui.components.MinimalButton
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalChip
import com.example.ui.theme.CoralError

import com.example.ui.viewmodel.OmniUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Studio screen for text-to-image, image-to-image, and OmniRoute image combo generation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageStudioScreen(
    uiState: OmniUiState,
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    generatedImages: List<GeneratedImageEntity>,
    onSelectProviderAndModel: (String, String) -> Unit,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onSelectAspectRatio: (String, String) -> Unit,
    onUpdateCount: (Int) -> Unit,
    onUpdateSeedText: (String) -> Unit,
    onRandomizeSeed: () -> Unit,
    onSelectStylePreset: (String) -> Unit,
    onUpdateGuidanceScale: (Float) -> Unit,
    onUpdateSteps: (Int) -> Unit,
    onUpdateQuality: (String) -> Unit,
    onAttachSourceImage: (String?) -> Unit,
    onUpdateStrength: (Float) -> Unit,
    onGenerateImage: (GeneratedImageEntity?) -> Unit,
    onInspectImage: (GeneratedImageEntity?) -> Unit,
    onReuseSettings: (GeneratedImageEntity) -> Unit,
    onToggleFavoriteImage: (GeneratedImageEntity) -> Unit,
    onDeleteImage: (GeneratedImageEntity) -> Unit,
    onDownloadImage: (GeneratedImageEntity) -> Unit,
    onAddManualModel: (String, String) -> Unit,
    onApproveCleartextHostFromError: (String) -> Unit,
    onDismissError: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onUpsertOmniRouteCombo: (
        rawId: String,
        displayName: String,
        strategy: String,
        targetModels: List<String>,
        description: String
    ) -> Unit = { _, _, _, _, _ -> }
) {
    val clipboardManager = LocalClipboardManager.current
    var showImageModelPicker by rememberSaveable { mutableStateOf(false) }
    var showComboBuilderDialog by rememberSaveable { mutableStateOf(false) }
    var showAdvancedControls by rememberSaveable { mutableStateOf(false) }

    val imageProviders = remember(providers) {
        val explicit = providers.filter {
            it.isEnabled && (CapabilityTag.IMAGE in it.capabilitySet() || it.defaultImageModelId.isNotBlank())
        }
        explicit.ifEmpty { providers.filter { it.isEnabled }.ifEmpty { providers } }
    }

    val selectedProvider = remember(imageProviders, uiState.selectedImageProviderId) {
        imageProviders.firstOrNull { it.id == uiState.selectedImageProviderId }
            ?: imageProviders.firstOrNull { it.isDefaultForImage }
            ?: imageProviders.firstOrNull()
    }

    val activeModelId = remember(selectedProvider, uiState.selectedImageModelId) {
        uiState.selectedImageModelId.ifBlank {
            selectedProvider?.defaultImageModelId?.ifBlank { "omniroute/flux-combo" } ?: "omniroute/flux-combo"
        }
    }

    val capabilities = remember(activeModelId, selectedProvider) {
        ModelCapabilityRegistry.getImageCapabilities(
            modelId = activeModelId,
            presetType = selectedProvider?.presetType ?: "OMNIROUTE"
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        onAttachSourceImage(uri?.toString())
    }

    if (showComboBuilderDialog) {
        OmniRouteComboBuilderDialog(
            initialCombo = null,
            defaultModality = "IMAGE",
            availableModels = models,
            onDismiss = { showComboBuilderDialog = false },
            onSave = { rawId, displayName, _, strategy, targetModels, description ->
                onUpsertOmniRouteCombo(rawId, displayName, strategy, targetModels, description)
                showComboBuilderDialog = false
            }
        )
    }

    if (showImageModelPicker) {
        ImageModelPickerSheet(
            providers = imageProviders,
            models = models,
            omniRouteCombos = uiState.omniRouteCombos,
            selectedProviderId = selectedProvider?.id,
            selectedModelId = activeModelId,
            onSelect = { provId, modId ->
                onSelectProviderAndModel(provId, modId)
                showImageModelPicker = false
            },
            onAddCustomModel = { provId, modId ->
                onAddManualModel(provId, modId)
                onSelectProviderAndModel(provId, modId)
                showImageModelPicker = false
            },
            onNewImageCombo = {
                showImageModelPicker = false
                showComboBuilderDialog = true
            },
            onDismiss = { showImageModelPicker = false }
        )
    }

    uiState.inspectingImage?.let { inspected ->
        val inspectedProvider = providers.firstOrNull { it.id == inspected.providerInstanceId }
        val inspectedCaps = ModelCapabilityRegistry.getImageCapabilities(
            modelId = inspected.modelId,
            presetType = inspectedProvider?.presetType ?: "OMNIROUTE"
        )
        ImageInspectorDialog(
            image = inspected,
            providerName = inspectedProvider?.displayName ?: inspected.providerInstanceId,
            supportsUpscale = inspectedCaps.supportsUpscale,
            isGenerating = uiState.isGeneratingImage,
            onDismiss = { onInspectImage(null) },
            onDownload = { onDownloadImage(inspected) },
            onCopyPrompt = {
                clipboardManager.setText(AnnotatedString(inspected.prompt))
            },
            onReuseSettings = {
                onReuseSettings(inspected)
                onInspectImage(null)
            },
            onUpscale = {
                onGenerateImage(inspected)
            },
            onUseAsImg2ImgReference = {
                onAttachSourceImage(inspected.imageUri)
                onInspectImage(null)
            },
            onToggleFavorite = { onToggleFavoriteImage(inspected) },
            onDelete = { onDeleteImage(inspected) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("image_studio_screen"),
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
                        onClick = { showImageModelPicker = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.testTag("image_model_selector_chip")
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
                                contentDescription = "Select Model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    MinimalChip(
                        label = "+ Combo",
                        selected = false,
                        onClick = { showComboBuilderDialog = true },
                        testTag = "image_studio_new_combo_button"
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

        // Error Banner if any
        uiState.activeError?.let { err ->
            item {
                ImageErrorBanner(
                    error = err,
                    onApproveCleartextHost = onApproveCleartextHostFromError,
                    onDismiss = onDismissError
                )
            }
        }

        // 2. Prompt Field & Generate Action
        item {
            MinimalCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.imagePrompt,
                        onValueChange = onPromptChange,
                        placeholder = {
                            Text(
                                text = "Describe the image...",
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
                            .testTag("image_prompt_input")
                    )

                    // 3. Aspect-Ratio and Style Row of Small Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ModelCapabilityRegistry.ASPECT_RATIOS.forEach { (ratio, sizeLabel) ->
                                MinimalChip(
                                    label = ratio,
                                    selected = uiState.imageAspectRatio == ratio,
                                    onClick = { onSelectAspectRatio(ratio, sizeLabel) },
                                    testTag = "aspect_ratio_chip_$ratio"
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ModelCapabilityRegistry.STYLE_PRESETS.forEach { style ->
                                MinimalChip(
                                    label = style,
                                    selected = uiState.imageStylePreset == style,
                                    onClick = { onSelectStylePreset(style) },
                                    testTag = "style_preset_chip_$style"
                                )
                            }
                        }
                    }

                    // Collapsible Advanced Controls (preserves all capabilities)
                    AnimatedVisibility(visible = showAdvancedControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                            if (capabilities.supportsNegativePrompt) {
                                OutlinedTextField(
                                    value = uiState.imageNegativePrompt,
                                    onValueChange = onNegativePromptChange,
                                    placeholder = { Text("Negative prompt (optional)", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("image_negative_prompt_input")
                                )
                            }

                            if (capabilities.supportsSeed) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = uiState.imageSeedText,
                                        onValueChange = onUpdateSeedText,
                                        placeholder = { Text("Seed (random)", style = MaterialTheme.typography.labelSmall) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("image_seed_input")
                                    )
                                    MinimalButton(
                                        text = "Random",
                                        onClick = onRandomizeSeed,
                                        filled = false,
                                        testTag = "image_randomize_seed_button"
                                    )
                                }
                            }

                            if (capabilities.supportsGuidanceScale) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Guidance (CFG)", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        String.format(Locale.US, "%.1f", uiState.imageGuidanceScale),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = uiState.imageGuidanceScale,
                                    onValueChange = onUpdateGuidanceScale,
                                    valueRange = 1f..20f
                                )
                            }

                            if (capabilities.supportsSteps) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Steps", style = MaterialTheme.typography.labelSmall)
                                    Text("${uiState.imageSteps}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = uiState.imageSteps.toFloat(),
                                    onValueChange = { onUpdateSteps(it.roundToInt()) },
                                    valueRange = 4f..50f
                                )
                            }

                            if (capabilities.supportsImageToImage) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Reference image (img2img)", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!uiState.imageSourceUri.isNullOrBlank()) {
                                            MinimalChip(
                                                label = "Clear",
                                                selected = false,
                                                onClick = { onAttachSourceImage(null) }
                                            )
                                        }
                                        MinimalChip(
                                            label = if (uiState.imageSourceUri == null) "Attach" else "Change",
                                            selected = uiState.imageSourceUri != null,
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            icon = LucideIcons.Image,
                                            testTag = "pick_img2img_button"
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Img2Img Strength", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        String.format(Locale.US, "%.2f", uiState.imageStrength),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = uiState.imageStrength.coerceIn(0.05f, 0.95f),
                                    onValueChange = onUpdateStrength,
                                    valueRange = 0.05f..0.95f
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        MinimalButton(
                            text = if (uiState.isGeneratingImage) "Generating..." else if (!uiState.imageSourceUri.isNullOrBlank()) "Generate (Img2Img)" else "Generate",
                            onClick = { onGenerateImage(null) },
                            enabled = uiState.imagePrompt.isNotBlank() && !uiState.isGeneratingImage,
                            icon = LucideIcons.Sparkles,
                            filled = true,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "generate_image_button"
                        )
                    }
                }
            }
        }

        // 3b. 12-Pillar AI Image Generation, Post-Processing & Img2Img Suite
        item {
            com.example.ui.components.ImageProductionSuiteSection(
                currentPrompt = uiState.imagePrompt,
                attachedSourceImageUri = uiState.imageSourceUri,
                currentImg2ImgStrength = uiState.imageStrength,
                recentGeneratedImages = generatedImages,
                onPickReferenceImageFromDevice = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onAttachSourceImageUri = onAttachSourceImage,
                onUpdateImg2ImgStrength = onUpdateStrength,
                onApplyPromptAndSettings = { synthPrompt, negPrompt, aspectRatio, sizeLabel, stylePreset, strength ->
                    onPromptChange(synthPrompt)
                    onNegativePromptChange(negPrompt)
                    onSelectAspectRatio(aspectRatio, sizeLabel)
                    onSelectStylePreset(stylePreset)
                    onUpdateStrength(strength)
                }
            )
        }

        // 4. Results Grid with 2 Columns and No Borders
        if (generatedImages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Generated images will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val rows = generatedImages.chunked(2)
            items(rows) { rowPair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_image_gallery_grid"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPair.forEach { img ->
                        val imageModel = remember(img.imageUri) {
                            if (img.imageUri.startsWith("/")) File(img.imageUri) else img.imageUri
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                .clickable { onInspectImage(img) }
                                .testTag("gallery_card_${img.id}")
                        ) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = img.prompt,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
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
private fun ImageInspectorDialog(
    image: GeneratedImageEntity,
    providerName: String,
    supportsUpscale: Boolean,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCopyPrompt: () -> Unit,
    onReuseSettings: () -> Unit,
    onUpscale: () -> Unit,
    onUseAsImg2ImgReference: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val imageModel = remember(image.imageUri) {
        if (image.imageUri.startsWith("/")) File(image.imageUri) else image.imageUri
    }
    val formattedDate = remember(image.createdAt) {
        SimpleDateFormat("MMM d · HH:mm", Locale.US).format(Date(image.createdAt))
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
                            text = image.modelId.substringAfterLast("/"),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "$providerName · $formattedDate",
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = image.prompt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = image.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinimalButton(
                        text = "Save",
                        onClick = onDownload,
                        icon = LucideIcons.Download,
                        filled = true,
                        testTag = "inspector_download_button"
                    )
                    MinimalButton(
                        text = "Use for Img2Img",
                        onClick = onUseAsImg2ImgReference,
                        icon = LucideIcons.Image,
                        filled = false,
                        testTag = "inspector_use_img2img_button"
                    )
                    MinimalButton(
                        text = "Copy Prompt",
                        onClick = onCopyPrompt,
                        icon = LucideIcons.Copy,
                        filled = false,
                        testTag = "inspector_copy_prompt_button"
                    )
                    MinimalButton(
                        text = "Reuse",
                        onClick = onReuseSettings,
                        icon = LucideIcons.Refresh,
                        filled = false,
                        testTag = "inspector_reuse_settings_button"
                    )
                    if (supportsUpscale && !image.isUpscaled) {
                        MinimalButton(
                            text = if (isGenerating) "Upscaling..." else "2x Upscale",
                            onClick = onUpscale,
                            enabled = !isGenerating,
                            filled = false,
                            testTag = "inspector_upscale_button"
                        )
                    }
                    MinimalButton(
                        text = "Delete",
                        onClick = onDelete,
                        icon = LucideIcons.Trash,
                        filled = false,
                        testTag = "inspector_delete_button"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageModelPickerSheet(
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    omniRouteCombos: List<com.example.data.provider.OmniRouteComboDefinition> = emptyList(),
    selectedProviderId: String?,
    selectedModelId: String,
    onSelect: (String, String) -> Unit,
    onAddCustomModel: (String, String) -> Unit,
    onNewImageCombo: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeProvId by rememberSaveable {
        mutableStateOf(selectedProviderId ?: providers.firstOrNull()?.id.orEmpty())
    }
    var customModelInput by rememberSaveable { mutableStateOf("") }

    val providerModels = remember(models, activeProvId, providers, omniRouteCombos) {
        val dbModels = models.filter {
            it.providerInstanceId == activeProvId &&
                (CapabilityTag.IMAGE in it.capabilitySet() || it.modelId.contains("flux", true) ||
                    it.modelId.contains("dall-e", true) || it.modelId.contains("sd", true) ||
                    it.modelId.contains("imagen", true) || it.modelId.contains("stable", true) ||
                    it.modelId.contains("image", true))
        }.toMutableList()

        if (activeProvId.contains("omniroute", ignoreCase = true)) {
            val seen = dbModels.map { it.modelId }.toMutableSet()
            omniRouteCombos.filter { it.modality.equals("IMAGE", ignoreCase = true) }.forEach { combo ->
                if (seen.add(combo.id)) {
                    dbModels.add(
                        0,
                        ModelCapabilityRegistry.buildDescriptor(
                            providerInstanceId = activeProvId,
                            rawModelId = combo.id,
                            displayNameOverride = combo.displayName,
                            explicitCombo = true,
                            explicitCapabilities = setOf(CapabilityTag.IMAGE)
                        )
                    )
                }
            }
        }

        if (dbModels.isNotEmpty()) {
            dbModels
        } else {
            val prov = providers.firstOrNull { it.id == activeProvId }
            val fallbackId = prov?.defaultImageModelId?.takeIf { it.isNotBlank() } ?: "omniroute/flux-combo"
            listOf(
                ModelCapabilityRegistry.buildDescriptor(
                    providerInstanceId = activeProvId,
                    rawModelId = fallbackId
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
                    text = "Image Model & Combos",
                    style = MaterialTheme.typography.titleLarge
                )
                MinimalButton(
                    text = "+ Image Combo",
                    onClick = onNewImageCombo,
                    filled = true,
                    testTag = "image_sheet_new_combo_button"
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
                    placeholder = { Text("Custom model ID...", style = MaterialTheme.typography.labelSmall) },
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
                                        style = MaterialTheme.typography.bodyMedium
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
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (comboDef != null && comboDef.targetModels.isNotEmpty()) {
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
private fun ImageErrorBanner(
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
