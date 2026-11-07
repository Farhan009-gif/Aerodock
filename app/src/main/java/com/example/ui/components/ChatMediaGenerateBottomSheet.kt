package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CapabilityTag
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity

import com.example.ui.viewmodel.OmniUiState

/**
 * Bottom sheet allowing direct AI Image and Video generation inside the Chat screen
 * without switching over to the Create tab.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatMediaGenerateBottomSheet(
    onDismiss: () -> Unit,
    initialModality: String = "IMAGE", // "IMAGE" or "VIDEO"
    initialPrompt: String = "",
    initialImageUri: String? = null,
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    uiState: OmniUiState,
    onGenerate: (
        modality: String,
        prompt: String,
        providerId: String?,
        modelId: String?,
        aspectRatio: String?,
        durationSeconds: Int?,
        sourceImageUri: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentModality by rememberSaveable { mutableStateOf(if (initialModality.equals("VIDEO", ignoreCase = true)) "VIDEO" else "IMAGE") }
    var promptText by rememberSaveable { mutableStateOf(initialPrompt) }
    var sourceUri by rememberSaveable { mutableStateOf(initialImageUri) }

    val isVideo = currentModality == "VIDEO"

    // Filter eligible providers
    val eligibleProviders = remember(providers, currentModality) {
        val targetCap = if (isVideo) CapabilityTag.VIDEO else CapabilityTag.IMAGE
        val filtered = providers.filter {
            it.isEnabled && (targetCap in it.capabilitySet() ||
                (if (isVideo) it.defaultVideoModelId.isNotBlank() else it.defaultImageModelId.isNotBlank()))
        }
        filtered.ifEmpty { providers.filter { it.isEnabled }.ifEmpty { providers } }
    }

    // Default provider selection
    var selectedProviderId by rememberSaveable(currentModality) {
        val defaultId = if (isVideo) {
            uiState.selectedVideoProviderId.ifBlank {
                eligibleProviders.firstOrNull { it.isDefaultForVideo }?.id ?: eligibleProviders.firstOrNull()?.id.orEmpty()
            }
        } else {
            uiState.selectedImageProviderId.ifBlank {
                eligibleProviders.firstOrNull { it.isDefaultForImage }?.id ?: eligibleProviders.firstOrNull()?.id.orEmpty()
            }
        }
        mutableStateOf(defaultId)
    }

    val selectedProvider = remember(eligibleProviders, selectedProviderId) {
        eligibleProviders.firstOrNull { it.id == selectedProviderId } ?: eligibleProviders.firstOrNull()
    }

    // Models for this provider
    val providerModels = remember(models, selectedProviderId, currentModality) {
        val targetCap = if (isVideo) "VIDEO" else "IMAGE"
        models.filter {
            it.providerInstanceId == selectedProviderId &&
                (it.capabilitiesCsv.contains(targetCap, ignoreCase = true) || it.isCombo)
        }
    }

    var selectedModelId by rememberSaveable(selectedProviderId, currentModality) {
        val defaultModel = if (isVideo) {
            selectedProvider?.defaultVideoModelId?.ifBlank { "omniroute/video-combo" } ?: "omniroute/video-combo"
        } else {
            selectedProvider?.defaultImageModelId?.ifBlank { "omniroute/flux-combo" } ?: "omniroute/flux-combo"
        }
        mutableStateOf(defaultModel)
    }

    // Aspect ratio selection
    val imageAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:2")
    val videoAspectRatios = listOf("16:9", "9:16", "1:1")
    var selectedAspectRatio by rememberSaveable(currentModality) {
        mutableStateOf(if (isVideo) "16:9" else "1:1")
    }

    // Duration selection (for video only)
    var selectedDurationSeconds by rememberSaveable { mutableStateOf(5) }

    var showModelMenu by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            sourceUri = uri.toString()
        }
    }

    val imageInspirationPrompts = listOf(
        "Cinematic cyberpunk street in heavy neon rain, 8k render",
        "Ethereal studio portrait with dramatic rim lighting and bokeh",
        "Isometric cozy developer room with glowing monitors and plants",
        "Hyperrealistic close-up of a futuristic android with golden circuits",
        "Moody oil painting of misty Scandinavian pine forest at dawn"
    )

    val videoInspirationPrompts = listOf(
        "Cinematic drone flying through misty neon skyscraper canyons",
        "Slow-motion raindrops falling onto wet city asphalt with reflections",
        "Smooth orbit camera around an ancient stone statue covered in moss",
        "Hyper-lapse sunset over bustling Tokyo skyline with cloud trails"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("chat_media_generate_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideo) LucideIcons.Video else LucideIcons.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (isVideo) "Generate Video in Chat" else "Generate Image in Chat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Create AI visuals directly without leaving the conversation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("chat_media_sheet_close")
                ) {
                    Icon(
                        imageVector = LucideIcons.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Modality Switcher (Image vs Video)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (!isVideo) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { currentModality = "IMAGE" }
                        .testTag("chat_media_tab_image")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = LucideIcons.Image,
                            contentDescription = null,
                            tint = if (!isVideo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Image",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (!isVideo) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isVideo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isVideo) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { currentModality = "VIDEO" }
                        .testTag("chat_media_tab_video")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = LucideIcons.Video,
                            contentDescription = null,
                            tint = if (isVideo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Video",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isVideo) FontWeight.Bold else FontWeight.Normal,
                            color = if (isVideo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Prompt Input Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PROMPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = {
                        Text(
                            text = if (isVideo) {
                                "Describe motion, camera movement, scene and style..."
                            } else {
                                "Describe the subject, lighting, atmosphere and composition..."
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 160.dp)
                        .testTag("chat_media_prompt_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Quick Inspiration Chips
                Text(
                    text = "Quick suggestions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val promptList = if (isVideo) videoInspirationPrompts else imageInspirationPrompts
                    promptList.forEach { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable {
                                promptText = if (promptText.isBlank()) suggestion else "$promptText, $suggestion"
                            }
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .widthIn(max = 240.dp)
                            )
                        }
                    }
                }
            }

            // Provider & Model Selector Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PROVIDER & MODEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModelMenu = true }
                            .testTag("chat_media_model_dropdown")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedModelId.substringAfterLast("/"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${selectedProvider?.displayName ?: "Provider"} · ${if (isVideo) "Video" else "Image"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = LucideIcons.ChevronDown,
                                contentDescription = "Select Model",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false },
                        modifier = Modifier.widthIn(min = 280.dp, max = 340.dp)
                    ) {
                        eligibleProviders.forEach { prov ->
                            Text(
                                text = prov.displayName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )

                            val modelsForProv = models.filter { it.providerInstanceId == prov.id }
                            val effectiveModels = if (modelsForProv.isNotEmpty()) {
                                modelsForProv.map { it.modelId }
                            } else {
                                val fallback = if (isVideo) prov.defaultVideoModelId else prov.defaultImageModelId
                                listOf(fallback.ifBlank { if (isVideo) "omniroute/video-combo" else "omniroute/flux-combo" })
                            }

                            effectiveModels.distinct().forEach { modId ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = modId.substringAfterLast("/"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (modId == selectedModelId && prov.id == selectedProviderId) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = modId,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedProviderId = prov.id
                                        selectedModelId = modId
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Aspect Ratio Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ASPECT RATIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ratios = if (isVideo) videoAspectRatios else imageAspectRatios
                    ratios.forEach { ratio ->
                        val isSelected = selectedAspectRatio == ratio
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .clickable { selectedAspectRatio = ratio }
                                .testTag("chat_media_ratio_$ratio")
                        ) {
                            Text(
                                text = ratio,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Duration (Only for Video)
            if (isVideo) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DURATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10).forEach { sec ->
                            val isSelected = selectedDurationSeconds == sec
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clickable { selectedDurationSeconds = sec }
                                    .testTag("chat_media_duration_$sec")
                            ) {
                                Text(
                                    text = "${sec}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reference Source Image (img2img or image-to-video)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isVideo) "REFERENCE IMAGE (OPTIONAL FOR IMAGE-TO-VIDEO)" else "REFERENCE IMAGE (OPTIONAL FOR IMG2IMG)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                if (sourceUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = sourceUri,
                            contentDescription = "Source reference image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Reference photo attached",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { sourceUri = null }) {
                            Icon(
                                imageVector = LucideIcons.Close,
                                contentDescription = "Remove source image",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("chat_media_pick_source_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = LucideIcons.Plus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Attach reference photo from device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Generate Button
            Button(
                onClick = {
                    val prompt = promptText.trim()
                    if (prompt.isNotEmpty()) {
                        onDismiss()
                        onGenerate(
                            currentModality,
                            prompt,
                            selectedProviderId,
                            selectedModelId,
                            selectedAspectRatio,
                            if (isVideo) selectedDurationSeconds else null,
                            sourceUri
                        )
                    }
                },
                enabled = promptText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("chat_generate_media_confirm_button")
            ) {
                Icon(
                    imageVector = LucideIcons.Sparkles,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isVideo) "Generate Video in Chat" else "Generate Image in Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
