package com.example.ui.components

import com.example.ui.theme.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Terminal
import com.example.data.provider.LocalhostServerStatus
import com.example.data.provider.ModelHealthProbeState
import com.example.data.provider.ModelHealthRecord
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConversationEntity
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.ProviderPresetType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.viewmodel.ExportPreviewPayload
import com.example.ui.viewmodel.PendingCleartextApproval
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Resolution-independent, hardware-accelerated Jetpack Compose Canvas banner.
 * Renders crisp anti-aliased Bezier fiber-optic routing paths and hexagonal core geometry
 * directly at native device pixel density (zero bitmap stretching or pixelation).
 */
@Composable
fun OmniStudioVectorBanner(
    modifier: Modifier = Modifier,
    badgeText: String = "RUNWAY • READY"
) {
    Surface(
        color = DarkElevatedSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkDivider),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RunwayLogoIcon(size = 28.dp)
            RunwayWordmark()
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniRouteOnboardingSheet(
    omniProvider: ProviderInstanceEntity?,
    approvedHosts: Set<String>,
    isBusy: Boolean,
    extractHttpHost: (String) -> String?,
    onTestAndFetch: (baseUrl: String, apiKey: String) -> Unit,
    onComplete: (baseUrl: String, apiKey: String, approveHost: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var baseUrl by rememberSaveable {
        mutableStateOf(omniProvider?.baseUrl ?: ProviderPresetType.OMNIROUTE.defaultBaseUrl)
    }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var showNetworkHelp by rememberSaveable { mutableStateOf(true) }

    val detectedHttpHost = extractHttpHost(baseUrl)
    val isAlreadyApproved = detectedHttpHost != null && detectedHttpHost in approvedHosts
    var approveCleartextChecked by rememberSaveable(detectedHttpHost) {
        mutableStateOf(true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("onboarding_bottom_sheet")
    ) {
        DialogWindowFocusGuard()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Crisp Native Compose Canvas Hero Banner
            OmniStudioVectorBanner(
                modifier = Modifier.height(124.dp),
                badgeText = "OMNIROUTE • UNIFIED AI GATEWAY"
            )

            Text(
                text = "Connect Your AI Gateway",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "OmniRoute is pre-configured as your default OpenAI-compatible router. Connect over your Wi-Fi LAN IP or Tailscale VPN from a physical phone, or switch to any cloud provider at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Quick Endpoint Presets (LAN IP & Tailscale primary, Emulator secondary hint)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Quick Endpoint Templates",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = baseUrl.startsWith("http://192.168."),
                        onClick = { baseUrl = "http://192.168.1.100:20128/v1" },
                        label = { Text("LAN IP (Real Phone)") },
                        modifier = Modifier.testTag("preset_lan_ip_chip")
                    )
                    FilterChip(
                        selected = baseUrl.contains("tailnet") || baseUrl.startsWith("http://100."),
                        onClick = { baseUrl = "http://omniroute.tailnet:20128/v1" },
                        label = { Text("Tailscale VPN") },
                        modifier = Modifier.testTag("preset_tailscale_chip")
                    )
                    FilterChip(
                        selected = baseUrl.contains("10.0.2.2"),
                        onClick = { baseUrl = "http://10.0.2.2:20128/v1" },
                        label = { Text("Emulator Hint (10.0.2.2)") },
                        modifier = Modifier.testTag("preset_emulator_chip")
                    )
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("OmniRoute Base URL") },
                placeholder = { Text("http://192.168.1.100:20128/v1") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_base_url_input")
            )

            // Cleartext HTTP host approval notice
            if (detectedHttpHost != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, AmberWarning),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Unencrypted HTTP warning",
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Cleartext HTTP Host: $detectedHttpHost",
                                style = MaterialTheme.typography.titleSmall,
                                color = AmberWarning
                            )
                        }
                        Text(
                            text = "Runway blocks unencrypted HTTP by default unless the host is in your Approved Host List.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { approveCleartextChecked = !approveCleartextChecked }
                        ) {
                            Checkbox(
                                checked = isAlreadyApproved || approveCleartextChecked,
                                onCheckedChange = { approveCleartextChecked = it },
                                modifier = Modifier.testTag("onboarding_approve_host_checkbox")
                            )
                            Text(
                                text = if (isAlreadyApproved) {
                                    "'$detectedHttpHost' is approved in your Host List"
                                } else {
                                    "Approve '$detectedHttpHost' for local LAN / Tailscale HTTP"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("OmniRoute API Key (Optional for local gateway)") },
                placeholder = { Text("sk-omni-...") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "Hide API key" else "Show API key"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_api_key_input")
            )

            // Expandable Network Help Guide
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNetworkHelp = !showNetworkHelp },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "LAN IP vs Tailscale vs 10.0.2.2 vs localhost",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Icon(
                            imageVector = if (showNetworkHelp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle guide"
                        )
                    }
                    AnimatedVisibility(visible = showNetworkHelp) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "• Real Phone on Wi-Fi (Default): Use your workstation's LAN IP (e.g. http://192.168.1.100:20128/v1).",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Real Phone Anywhere (Tailscale): Use your Tailscale IP (http://100.x.y.z:20128/v1) or MagicDNS hostname.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Android Emulator Hint: Use http://10.0.2.2:20128/v1 (maps to host machine 127.0.0.1).",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Why not localhost?: On Android, localhost points to the phone itself (only use if running inside Termux).",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Encrypted storage badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = EmeraldConnected,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Keys are encrypted on-device with Tink AES256-GCM (Android Keystore) and excluded from backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onTestAndFetch(baseUrl, apiKey)
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("onboarding_fetch_models_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Fetch Models")
                }

                Button(
                    onClick = {
                        onComplete(baseUrl, apiKey, approveCleartextChecked)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("onboarding_continue_button")
                ) {
                    Text("Enter Studio")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerBottomSheet(
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    activeProviderId: String?,
    activeModelId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectModel: (providerId: String, modelId: String) -> Unit,
    onToggleFavorite: (ModelDescriptorEntity) -> Unit,
    onAddManualModel: (providerId: String, modelId: String, isCombo: Boolean) -> Unit,
    onFetchModelsForProvider: (ProviderInstanceEntity) -> Unit = {},
    isFetchingProviderId: String? = null,
    omniRouteCombos: List<com.example.data.provider.OmniRouteComboDefinition> = emptyList(),
    onUpsertCustomCombo: (
        rawId: String,
        displayName: String,
        modality: String,
        strategy: String,
        targetModels: List<String>,
        description: String,
        additionalProviderId: String?
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteCustomCombo: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var manualModelText by rememberSaveable { mutableStateOf("") }

    // Order providers: OpenAI, Anthropic, Google Gemini first, then the rest
    val orderedProviders = remember(providers) {
        val active = providers.filter { it.isEnabled }.ifEmpty { providers }
        active.sortedWith(
            compareBy<ProviderInstanceEntity> { prov ->
                when (prov.presetEnum()) {
                    ProviderPresetType.OPENAI -> 0
                    ProviderPresetType.ANTHROPIC -> 1
                    ProviderPresetType.GEMINI -> 2
                    ProviderPresetType.OMNIROUTE -> 3
                    else -> 4
                }
            }.thenBy { it.displayName }
        )
    }

    var selectedProviderId by rememberSaveable(activeProviderId, orderedProviders.size) {
        mutableStateOf(
            activeProviderId?.takeIf { id -> orderedProviders.any { it.id == id } }
                ?: orderedProviders.firstOrNull()?.id.orEmpty()
        )
    }

    val selectedProvider = remember(orderedProviders, selectedProviderId) {
        orderedProviders.firstOrNull { it.id == selectedProviderId }
            ?: orderedProviders.firstOrNull()
    }

    val modelsForSelectedProvider = remember(selectedProvider, models, searchQuery) {
        val prov = selectedProvider ?: return@remember emptyList()
        val q = searchQuery.trim().lowercase()
        models.filter { m ->
            m.providerInstanceId == prov.id &&
                !com.example.data.provider.EndpointUrlNormalizer.isBrowserScrapedOrOAuthIdeModel(m.modelId) &&
                (q.isEmpty() ||
                    m.displayName.lowercase().contains(q) ||
                    m.modelId.lowercase().contains(q))
        }.sortedWith(
            compareByDescending<ModelDescriptorEntity> { it.isFavorite }
                .thenBy { com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo) }
                .thenBy { it.displayName }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.testTag("model_picker_bottom_sheet")
    ) {
        DialogWindowFocusGuard()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                selectedProvider?.let { prov ->
                    MinimalChip(
                        label = if (isFetchingProviderId == prov.id) "Syncing…" else "Sync",
                        icon = LucideIcons.RefreshCw,
                        onClick = { onFetchModelsForProvider(prov) },
                        modifier = Modifier.testTag("picker_sync_models_btn_${prov.id}")
                    )
                }
            }

            // Segmented provider row (OpenAI, Anthropic, Google Gemini, and the rest)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                orderedProviders.forEach { prov ->
                    val isSelected = prov.id == selectedProvider?.id
                    Surface(
                        onClick = { selectedProviderId = prov.id },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) com.example.ui.theme.SoftIndigo.copy(alpha = 0.16f) else Color.Transparent,
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("picker_provider_tab_${prov.id}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = prov.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                ),
                                color = if (isSelected) com.example.ui.theme.SoftIndigo else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Clean search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Search models…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = LucideIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = LucideIcons.X,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_picker_search_input")
            )

            // Clean list of models below
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (modelsForSelectedProvider.isEmpty()) {
                    item {
                        MinimalCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No models listed for ${selectedProvider?.displayName ?: "this provider"}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap Sync above or enter a custom model ID below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(modelsForSelectedProvider, key = { it.id }) { model ->
                        val isSelected = model.providerInstanceId == activeProviderId &&
                            model.modelId == activeModelId
                        Surface(
                            onClick = {
                                onSelectModel(model.providerInstanceId, model.modelId)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                com.example.ui.theme.SoftIndigo.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    com.example.ui.theme.SoftIndigo.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("model_item_${model.modelId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                        ),
                                        color = if (isSelected) com.example.ui.theme.SoftIndigo else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = model.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = LucideIcons.Check,
                                        contentDescription = "Selected",
                                        tint = com.example.ui.theme.SoftIndigo,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Subtle custom model entry row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualModelText,
                    onValueChange = { manualModelText = it },
                    placeholder = {
                        Text(
                            "Add custom model ID…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_model_input")
                )
                MinimalButton(
                    text = "Add",
                    icon = LucideIcons.Plus,
                    enabled = manualModelText.isNotBlank(),
                    onClick = {
                        val targetProv = selectedProvider?.id
                            ?: orderedProviders.firstOrNull()?.id.orEmpty()
                        if (targetProv.isNotBlank() && manualModelText.isNotBlank()) {
                            val clean = manualModelText.trim()
                            onAddManualModel(targetProv, clean, false)
                            onSelectModel(targetProv, clean)
                            manualModelText = ""
                        }
                    },
                    modifier = Modifier.testTag("manual_model_add_button")
                )
            }
        }
    }
}

@Composable
fun BadgePill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.wrapContentWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterTuningBottomSheet(
    conversation: ConversationEntity?,
    modelDescriptor: ModelDescriptorEntity?,
    onSaveParameters: (systemPrompt: String, temperature: Float, maxTokens: Int, topP: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var systemPrompt by rememberSaveable(conversation?.id) {
        mutableStateOf(conversation?.systemPrompt ?: "You are a helpful, precise AI assistant in Runway.")
    }
    var temperature by rememberSaveable(conversation?.id) {
        mutableFloatStateOf(conversation?.temperature ?: 0.7f)
    }
    var maxTokensText by rememberSaveable(conversation?.id) {
        mutableStateOf((conversation?.maxTokens ?: 2048).toString())
    }
    var topP by rememberSaveable(conversation?.id) {
        mutableFloatStateOf(conversation?.topP ?: 1.0f)
    }

    val supportsSystem = modelDescriptor?.supportsSystemPrompt ?: true
    val supportsTemp = modelDescriptor?.supportsTemperature ?: true
    val supportsTopP = modelDescriptor?.supportsTopP ?: true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("parameter_tuning_sheet")
    ) {
        DialogWindowFocusGuard()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Model Parameters & System Prompt",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Active model: ${modelDescriptor?.displayName ?: conversation?.modelId ?: "Default"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (supportsSystem) {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("param_system_prompt_input")
                )
            } else {
                Text(
                    text = "System prompt is disabled for this reasoning model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberWarning
                )
            }

            if (supportsTemp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Temperature", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = String.format(Locale.US, "%.2f", temperature),
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                    }
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        modifier = Modifier.testTag("param_temperature_slider")
                    )
                }
            }

            if (supportsTopP) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Top-P (Nucleus Sampling)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = String.format(Locale.US, "%.2f", topP),
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                    }
                    Slider(
                        value = topP,
                        onValueChange = { topP = it },
                        valueRange = 0.05f..1f,
                        modifier = Modifier.testTag("param_top_p_slider")
                    )
                }
            }

            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { maxTokensText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Max Output Tokens") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("param_max_tokens_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsedTokens = maxTokensText.toIntOrNull() ?: 2048
                        onSaveParameters(systemPrompt, temperature, parsedTokens, topP)
                    },
                    modifier = Modifier.testTag("param_save_button")
                ) {
                    Text("Apply Parameters")
                }
            }
        }
    }
}

@Composable
fun CleartextHostWarningDialog(
    pending: PendingCleartextApproval,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Cleartext HTTP Warning",
                tint = AmberWarning
            )
        },
        title = {
            Text("Approve Unencrypted HTTP Host?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${pending.providerName} is attempting to connect over unencrypted HTTP to:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = pending.fullUrl,
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = "Only approve '${pending.host}' if it is your trusted workstation on a private Wi-Fi LAN, Tailscale VPN, or local Android emulator (10.0.2.2).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                modifier = Modifier.testTag("confirm_approve_cleartext_button")
            ) {
                Text("Approve '${pending.host}'", color = MaterialTheme.colorScheme.scrim)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_approve_cleartext_button")
            ) {
                Text("Block Request")
            }
        }
    )
}

@Composable
fun ExportPreviewDialog(
    payload: ExportPreviewPayload,
    onDismiss: () -> Unit,
    onCopiedToast: (String) -> Unit,
    onChangeConversationExportOptions: ((conversationId: String, asJson: Boolean, includeAllBranches: Boolean) -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Export Security Warning",
                tint = AmberWarning
            )
        },
        title = {
            Text(payload.title)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (payload.conversationId != null && onChangeConversationExportOptions != null) {
                    val convId = payload.conversationId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MinimalChip(
                            label = "Markdown",
                            selected = !payload.asJson,
                            onClick = {
                                onChangeConversationExportOptions(convId, false, payload.includeAllBranches)
                            }
                        )
                        MinimalChip(
                            label = "JSON",
                            selected = payload.asJson,
                            onClick = {
                                onChangeConversationExportOptions(convId, true, payload.includeAllBranches)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MinimalChip(
                            label = "Active branch",
                            selected = !payload.includeAllBranches,
                            onClick = {
                                onChangeConversationExportOptions(convId, payload.asJson, false)
                            }
                        )
                        MinimalChip(
                            label = "All branches",
                            selected = payload.includeAllBranches,
                            onClick = {
                                onChangeConversationExportOptions(convId, payload.asJson, true)
                            }
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, AmberWarning)
                ) {
                    Text(
                        text = if (payload.includesSensitiveKeys) {
                            "⚠️ SENSITIVE EXPORT: You opted in to include raw API keys. Keep this file strictly confidential."
                        } else {
                            "Security Notice: API keys are excluded from this export. Review prompts before sharing publicly."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    Text(
                        text = payload.content,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(payload.content))
                    onCopiedToast("Copied ${payload.formatLabel} export to clipboard")
                    onDismiss()
                },
                modifier = Modifier.testTag("copy_export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy ${payload.formatLabel}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAndHealthBottomSheet(
    terminalLogs: List<String>,
    serverStatus: LocalhostServerStatus,
    modelHealthByKey: Map<String, ModelHealthRecord>,
    isScanning: Boolean,
    providers: List<com.example.data.local.ProviderInstanceEntity>,
    models: List<com.example.data.local.ModelDescriptorEntity>,
    onSendCommand: (String) -> Unit,
    onStartLocalhost: () -> Unit,
    onStopLocalhost: () -> Unit,
    onScanHealthy: () -> Unit,
    onSelectHealthyModel: (providerId: String, modelId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by rememberSaveable { mutableStateOf(0) }
    var inputCommand by rememberSaveable { mutableStateOf("") }
    val terminalListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            terminalListState.animateScrollToItem(terminalLogs.lastIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("omniroute_terminal_and_health_sheet")
    ) {
        DialogWindowFocusGuard()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Terminal,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "OmniRoute Terminal & Health",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Localhost daemon (localhost:20128) • Website & Upstream Model Verifier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Close modal"
                    )
                }
            }

            // Tabs: Terminal vs Health Scanner
            androidx.compose.material3.TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                androidx.compose.material3.Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Terminal (localhost:20128)", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    modifier = Modifier.testTag("tab_omniroute_terminal")
                )
                androidx.compose.material3.Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = EmeraldConnected
                            )
                            Text("Healthy Models", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    modifier = Modifier.testTag("tab_omniroute_health")
                )
            }

            if (activeTab == 0) {
                val context = androidx.compose.ui.platform.LocalContext.current
                // TAB 0: Interactive Terminal
                // Server Status Banner
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (serverStatus.isListening) EmeraldConnected.copy(alpha = 0.12f)
                        else CoralError.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (serverStatus.isListening) EmeraldConnected else CoralError
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (serverStatus.isListening) "● DAEMON RUNNING" else "○ DAEMON STOPPED",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = if (serverStatus.isListening) EmeraldConnected else CoralError
                                )
                                BadgePill(
                                    text = "localhost:${serverStatus.port}",
                                    color = if (serverStatus.isListening) EmeraldConnected else CoralError
                                )
                            }
                            Text(
                                text = "Base URL: http://localhost:${serverStatus.port}/v1 • Web: http://localhost:${serverStatus.port} • Served ${serverStatus.requestCount} reqs",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (!serverStatus.isListening) {
                                        onStartLocalhost()
                                    }
                                    runCatching {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("http://localhost:${serverStatus.port}")
                                        )
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("open_omniroute_web_button")
                            ) {
                                Text("Open Web", style = MaterialTheme.typography.labelSmall)
                            }
                            if (serverStatus.isListening) {
                                OutlinedButton(
                                    onClick = onStopLocalhost,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Stop", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Button(
                                    onClick = onStartLocalhost,
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldConnected),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Start", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Quick Command Action Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickCmds = listOf(
                        "omniroute start" to "Start",
                        "omniroute status" to "Status",
                        "omniroute combos" to "Combos",
                        "omniroute video Cyberpunk neon city" to "Test Video",
                        "curl http://localhost:20128/v1/combos" to "curl /combos",
                        "curl http://localhost:20128/v1/models" to "curl /models",
                        "curl http://localhost:20128/health" to "curl health",
                        "help" to "Help",
                        "clear" to "Clear"
                    )
                    for ((cmd, label) in quickCmds) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable {
                                onSendCommand(cmd)
                            }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = CyberCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Terminal Console Output Window
                Surface(
                    color = androidx.compose.ui.graphics.Color(0xFF090D12),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF1E2633)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("omniroute_terminal_console")
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Console top bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Color(0xFF131A24))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(CoralError))
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(AmberWarning))
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(EmeraldConnected))
                            }
                            Text(
                                text = "omniroute@localhost:20128",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = androidx.compose.ui.graphics.Color(0xFF8B949E)
                            )
                            Text(
                                text = "${terminalLogs.size} lines",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = androidx.compose.ui.graphics.Color(0xFF58A6FF)
                            )
                        }

                        // Logs scroll
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = terminalListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(terminalLogs) { line ->
                                val tint = when {
                                    line.startsWith("$") -> CyberCyan
                                    line.contains("OK") || line.contains("SUCCESS") || line.contains("HEALTHY") -> EmeraldConnected
                                    line.contains("ERR") || line.contains("FAIL") || line.contains("UNHEALTHY") -> CoralError
                                    line.contains("WARN") || line.contains("NOTE") -> AmberWarning
                                    else -> androidx.compose.ui.graphics.Color(0xFFC9D1D9)
                                }
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)
                                    ),
                                    color = tint
                                )
                            }
                        }
                    }
                }

                // Interactive Command Prompt Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = inputCommand,
                        onValueChange = { inputCommand = it },
                        placeholder = {
                            Text(
                                "omniroute status, curl health…",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                            )
                        },
                        leadingIcon = {
                            Text(
                                "$",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = CyberCyan,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (inputCommand.isNotBlank()) {
                                    onSendCommand(inputCommand.trim())
                                    inputCommand = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("omniroute_terminal_input")
                    )
                    Button(
                        onClick = {
                            if (inputCommand.isNotBlank()) {
                                onSendCommand(inputCommand.trim())
                                inputCommand = ""
                            }
                        },
                        enabled = inputCommand.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("omniroute_terminal_run_button")
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Run command",
                            tint = MaterialTheme.colorScheme.scrim
                        )
                    }
                }
            } else {
                // TAB 1: Provider & Model Health Scanner
                // Control Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upstream Provider & Model Prober",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "Excludes scraper/OAuth models (cfp/, aug/) • Verifies zero-config working routes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onScanHealthy,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldConnected),
                        modifier = Modifier.testTag("scan_healthy_models_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing…", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan All", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Summary Stats Card
                val healthyCount = modelHealthByKey.values.count { it.state == com.example.data.provider.ModelHealthProbeState.HEALTHY }
                val unhealthyCount = modelHealthByKey.values.count { it.state == com.example.data.provider.ModelHealthProbeState.UNHEALTHY }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EmeraldConnected.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, EmeraldConnected),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("HEALTHY MODELS", style = MaterialTheme.typography.labelSmall, color = EmeraldConnected)
                            Text("$healthyCount verified", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CoralError.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, CoralError),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("UNHEALTHY / CLOSED", style = MaterialTheme.typography.labelSmall, color = CoralError)
                            Text("$unhealthyCount failed", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }

                // Grouped Models List
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val enabledProviders = providers.filter { it.isEnabled }
                    for (prov in enabledProviders) {
                        val provModels = models.filter {
                            it.providerInstanceId == prov.id &&
                                !com.example.data.provider.EndpointUrlNormalizer.isBrowserScrapedOrOAuthIdeModel(it.modelId)
                        }.sortedWith(
                            compareBy<com.example.data.local.ModelDescriptorEntity> {
                                com.example.data.provider.EndpointUrlNormalizer.modelPriorityRank(it.modelId, it.isCombo)
                            }.thenBy { it.modelId }
                        )

                        if (provModels.isNotEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${prov.displayName} (${prov.baseUrl})",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = CyberCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            items(provModels) { model ->
                                val key = "${prov.id}:${model.modelId}"
                                val health = modelHealthByKey[key]
                                val state = health?.state ?: com.example.data.provider.ModelHealthProbeState.UNTESTED

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(
                                        1.dp,
                                        when (state) {
                                            com.example.data.provider.ModelHealthProbeState.HEALTHY -> EmeraldConnected
                                            com.example.data.provider.ModelHealthProbeState.UNHEALTHY -> CoralError
                                            com.example.data.provider.ModelHealthProbeState.CHECKING -> AmberWarning
                                            com.example.data.provider.ModelHealthProbeState.UNTESTED -> MaterialTheme.colorScheme.outline
                                        }
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("health_card_${model.modelId}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                when (state) {
                                                    com.example.data.provider.ModelHealthProbeState.HEALTHY -> {
                                                        BadgePill(
                                                            text = "HEALTHY • ${health?.latencyMs ?: 0}ms",
                                                            color = EmeraldConnected
                                                        )
                                                    }
                                                    com.example.data.provider.ModelHealthProbeState.CHECKING -> {
                                                        BadgePill(text = "CHECKING…", color = AmberWarning)
                                                    }
                                                    com.example.data.provider.ModelHealthProbeState.UNHEALTHY -> {
                                                        BadgePill(text = "FAILED", color = CoralError)
                                                    }
                                                    com.example.data.provider.ModelHealthProbeState.UNTESTED -> {
                                                        BadgePill(text = "READY TO PROBE", color = MaterialTheme.colorScheme.outline)
                                                    }
                                                }
                                                if (model.modelId.startsWith("ddgw/")) {
                                                    BadgePill(text = "ZERO-CONFIG", color = CyberCyan)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = model.displayName.ifBlank { model.modelId },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                            Text(
                                                text = model.modelId,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (state == com.example.data.provider.ModelHealthProbeState.UNHEALTHY) {
                                                Text(
                                                    text = health?.statusDetail ?: "Upstream connection closed",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CoralError,
                                                    maxLines = 2
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                onSelectHealthyModel(prov.id, model.modelId)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (state == com.example.data.provider.ModelHealthProbeState.HEALTHY) EmeraldConnected else MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = if (state == com.example.data.provider.ModelHealthProbeState.HEALTHY) MaterialTheme.colorScheme.scrim else MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp).testTag("select_model_${model.modelId}")
                                        ) {
                                            Text("Use Model", style = MaterialTheme.typography.labelSmall)
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

