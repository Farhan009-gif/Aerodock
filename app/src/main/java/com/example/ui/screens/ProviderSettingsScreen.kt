package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.CapabilityTag
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.ProviderPresetType
import com.example.data.provider.RequestDiagnosticEntry
import com.example.ui.components.BadgePill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError

import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.OmniUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProviderSettingsScreen(
    uiState: OmniUiState,
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    diagnosticEntries: List<RequestDiagnosticEntry> = emptyList(),
    onClearDiagnostics: () -> Unit = {},
    onRunOmniRouteCleanTerminal: (String?) -> Unit = {},
    onSelectDefaultChatModel: (ProviderInstanceEntity, String) -> Unit = { _, _ -> },
    onSaveProvider: (
        existingId: String?,
        preset: ProviderPresetType,
        displayName: String,
        baseUrl: String,
        rawApiKey: String?,
        capabilities: Set<CapabilityTag>,
        isEnabled: Boolean,
        defaultChatModel: String,
        defaultImageModel: String,
        defaultVideoModel: String
    ) -> Unit,
    onDuplicateProvider: (ProviderInstanceEntity) -> Unit,
    onSaveProviderApiKeyOnly: (ProviderInstanceEntity, String) -> Unit,
    onClearProviderApiKey: (ProviderInstanceEntity) -> Unit,
    onUpdateProviderBaseUrl: (ProviderInstanceEntity, String) -> Unit,
    onToggleProviderEnabled: (ProviderInstanceEntity) -> Unit,
    onUpdateProviderCapabilities: (ProviderInstanceEntity, Set<CapabilityTag>) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSetDefaultForFeature: (providerId: String, capability: CapabilityTag) -> Unit,
    onTestConnectionOnly: (ProviderInstanceEntity, String?, String?) -> Unit,
    onTestAndFetchModels: (ProviderInstanceEntity, String?, String?) -> Unit,
    onAddManualModel: (providerId: String, modelId: String, isCombo: Boolean) -> Unit,
    onToggleFavoriteModel: (ModelDescriptorEntity) -> Unit,
    onApproveCleartextHostManually: (String) -> Unit,
    onRevokeCleartextHost: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetBiometricGate: (Boolean) -> Unit,
    onSetEnterToSend: (Boolean) -> Unit,
    onSetSpendCurrency: (String) -> Unit = {},
    onOpenOnboardingSheet: () -> Unit,
    onExportSettings: (includeKeys: Boolean) -> Unit,
    onImportSettings: (String) -> Unit,
    onClearAllData: () -> Unit
) {
    var editingProvider by remember { mutableStateOf<ProviderInstanceEntity?>(null) }
    var showAddProviderDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var includeKeysInExport by rememberSaveable { mutableStateOf(false) }
    var newCleartextHostText by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }

    val omniRouteProvider = remember(providers) {
        providers.firstOrNull { it.presetEnum() == ProviderPresetType.OMNIROUTE }
            ?: providers.firstOrNull { it.id == "provider_omniroute_default" }
    }
    val otherProviders = remember(providers, omniRouteProvider) {
        providers.filter { it.id != omniRouteProvider?.id && it.presetEnum() != ProviderPresetType.OMNIROUTE }
    }
    val filteredOtherProviders = remember(otherProviders, selectedFilter) {
        when (selectedFilter) {
            "ENABLED" -> otherProviders.filter { it.isEnabled }
            "CHAT" -> otherProviders.filter { CapabilityTag.CHAT in it.capabilitySet() }
            "IMAGE" -> otherProviders.filter { CapabilityTag.IMAGE in it.capabilitySet() }
            "VIDEO" -> otherProviders.filter { CapabilityTag.VIDEO in it.capabilitySet() }
            else -> otherProviders
        }
    }

    if (showAddProviderDialog || editingProvider != null) {
        ProviderEditorDialog(
            initialProvider = editingProvider,
            maskedKey = editingProvider?.let { uiState.maskedKeysByProvider[it.id] },
            onDismiss = {
                showAddProviderDialog = false
                editingProvider = null
            },
            onSave = { existingId, preset, name, url, key, caps, enabled, chatModel, imgModel, vidModel ->
                onSaveProvider(
                    existingId,
                    preset,
                    name,
                    url,
                    key,
                    caps,
                    enabled,
                    chatModel,
                    imgModel,
                    vidModel
                )
                showAddProviderDialog = false
                editingProvider = null
            }
        )
    }

    if (showImportDialog) {
        var rawImportJson by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Workspace Settings JSON") },
            text = {
                OutlinedTextField(
                    value = rawImportJson,
                    onValueChange = { rawImportJson = it },
                    placeholder = { Text("Paste exported Aerodock JSON here…") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportSettings(rawImportJson)
                        showImportDialog = false
                    },
                    enabled = rawImportJson.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = CoralError)
            },
            title = { Text("Clear All Workspace Data?") },
            text = {
                Text(
                    "This permanently wipes all encrypted API keys from the Tink Keystore vault, deletes all chat history and custom providers from Room, and resets your Approved Cleartext Host List."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                    modifier = Modifier.testTag("confirm_clear_all_data_button")
                ) {
                    Text("Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_list")
    ) {
        // Section 1: Header
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Settings & Providers",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bring Your Own Key (BYOK) multi-provider architecture backed by Tink AEAD Keystore encryption and unified endpoint routing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 2: Tink AEAD + Android Keystore Encryption Layer Status Banner
        item {
            val vault = uiState.vaultSecurityStatus
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, EmeraldConnected.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tink_keystore_vault_banner")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EmeraldConnected,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Tink AEAD + Android Keystore Vault",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        BadgePill(
                            text = "${vault.encryptedKeyCount} Encrypted Keys",
                            color = EmeraldConnected
                        )
                    }
                    Text(
                        text = "Cipher: ${vault.cipherAlgorithm} • Master Key: ${vault.masterKeyUri}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "API keys are encrypted with per-provider Associated Data (AAD) inside Jetpack DataStore, masked in UI, never written to SQLite or URLs, and excluded from cloud/ADB backups (allowBackup=\"false\").",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 3: DEDICATED OMNIROUTE ENDPOINT CONFIGURATION & BUILT-IN CLEAN TERMINAL
        item {
            val clipboardManager = LocalClipboardManager.current
            var copiedTermuxCmd by remember { mutableStateOf(false) }
            val termuxCleanStartCommand = remember(omniRouteProvider?.baseUrl) {
                "pkill -f omniroute 2>/dev/null; env PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 OMNIROUTE_DISABLE_SCRAPERS=cfp,dva,aug,cu,ws,gh,gc,kr,if,cc,cx,qoder OMNIROUTE_DEFAULT_PROVIDER=ddgw npx -y omniroute --port 20128"
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("omniroute_clean_terminal_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OmniRoute Clean Terminal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Pre-configured engine that bypasses Chromium/Playwright (cfp/) and unlinked OAuth/IDE scrapers (aug/, cu/, ws/, gh/, gc/) so OmniRoute runs smoothly out of the box.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BadgePill(
                            text = if (uiState.isOmniRouteTerminalRunning) "RUNNING" else "CLEAN MODE",
                            color = if (uiState.isOmniRouteTerminalRunning) AmberWarning else EmeraldConnected
                        )
                    }

                    // Live Monospace Terminal Output Console
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.omniRouteTerminalLogs.takeLast(12).forEach { line ->
                                val lineColor = when {
                                    line.startsWith("$") -> MaterialTheme.colorScheme.primary
                                    line.startsWith("[SUCCESS]") || line.startsWith("[OK]") || line.startsWith("[ACTIVE]") || line.startsWith("[LIVE]") -> EmeraldConnected
                                    line.startsWith("[PURGE]") || line.startsWith("[ROUTE]") -> AmberWarning
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = lineColor
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onRunOmniRouteCleanTerminal(omniRouteProvider?.id) },
                            enabled = !uiState.isOmniRouteTerminalRunning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("run_omniroute_clean_terminal_button")
                        ) {
                            if (uiState.isOmniRouteTerminalRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Starting Daemon…")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Terminal & Health")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(termuxCleanStartCommand))
                                copiedTermuxCmd = true
                            },
                            modifier = Modifier.testTag("copy_termux_clean_start_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Termux Clean Command",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedTermuxCmd) "Copied!" else "Copy Termux Cmd")
                        }
                    }
                }
            }

            if (omniRouteProvider != null) {
                val omniModels = models.filter {
                    it.providerInstanceId == omniRouteProvider.id &&
                        !com.example.data.provider.EndpointUrlNormalizer.isBrowserScrapedOrOAuthIdeModel(it.modelId)
                }
                OmniRouteEndpointCard(
                    provider = omniRouteProvider,
                    maskedKey = uiState.maskedKeysByProvider[omniRouteProvider.id],
                    providerModels = omniModels,
                    isBusy = uiState.busyProviderId == omniRouteProvider.id,
                    approvedCleartextHosts = uiState.approvedCleartextHosts,
                    onUpdateBaseUrl = { newUrl -> onUpdateProviderBaseUrl(omniRouteProvider, newUrl) },
                    onSaveApiKeyOnly = { rawKey -> onSaveProviderApiKeyOnly(omniRouteProvider, rawKey) },
                    onClearApiKey = { onClearProviderApiKey(omniRouteProvider) },
                    onToggleEnabled = { onToggleProviderEnabled(omniRouteProvider) },
                    onUpdateCapabilities = { tags -> onUpdateProviderCapabilities(omniRouteProvider, tags) },
                    onSetDefaultFeature = { cap -> onSetDefaultForFeature(omniRouteProvider.id, cap) },
                    onTestConnection = { overrideUrl, overrideKey ->
                        onTestConnectionOnly(omniRouteProvider, overrideUrl, overrideKey)
                    },
                    onFetchModels = { overrideUrl, overrideKey ->
                        onTestAndFetchModels(omniRouteProvider, overrideUrl, overrideKey)
                    },
                    onAddManualModel = { modelName, isCombo ->
                        onAddManualModel(omniRouteProvider.id, modelName, isCombo)
                    },
                    onToggleFavoriteModel = onToggleFavoriteModel,
                    onSelectModelAsDefault = { modelId -> onSelectDefaultChatModel(omniRouteProvider, modelId) },
                    onApproveCleartextHost = onApproveCleartextHostManually,
                    onOpenOnboardingSheet = onOpenOnboardingSheet,
                    onEditInDialog = { editingProvider = omniRouteProvider }
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "OmniRoute Endpoint is Not Configured",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "OmniRoute is an optional OpenAI-compatible router for local and remote models across Chat, Image, and Video.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                onSaveProvider(
                                    null,
                                    ProviderPresetType.OMNIROUTE,
                                    "OmniRoute Gateway",
                                    ProviderPresetType.OMNIROUTE.defaultBaseUrl,
                                    null,
                                    setOf(CapabilityTag.CHAT, CapabilityTag.IMAGE, CapabilityTag.VIDEO),
                                    true,
                                    "gemini-2.5-flash",
                                    "omniroute/flux-combo",
                                    "omniroute/video-combo"
                                )
                            }
                        ) {
                            Text("Initialize OmniRoute Endpoint")
                        }
                    }
                }
            }
        }

        // Section 4: AI Providers (BYOK) Section Header & Add Provider Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Providers (${otherProviders.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add and manage external BYOK keys (OpenAI, Anthropic, Gemini, OpenRouter, Groq, Fal.ai, etc.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { showAddProviderDialog = true },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_provider_instance_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Provider")
                }
            }
        }

        // Section 5: Filter Chips for Other Providers
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "ALL" to "All (${otherProviders.size})",
                    "ENABLED" to "Enabled",
                    "CHAT" to "Chat",
                    "IMAGE" to "Image",
                    "VIDEO" to "Video"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) },
                        modifier = Modifier.testTag("provider_filter_chip_$key")
                    )
                }
            }
        }

        // Section 6: Provider Cards for Other Providers (Empty State or List)
        if (filteredOtherProviders.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (otherProviders.isEmpty()) "No External Providers Configured"
                            else "No Providers Match Filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (otherProviders.isEmpty())
                                "OmniRoute handles all your requests by default. You can also connect OpenAI, Anthropic, Gemini, or OpenRouter keys directly."
                            else "Try changing the filter chips above or add a new provider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { showAddProviderDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add First Provider")
                        }
                    }
                }
            }
        } else {
            items(filteredOtherProviders, key = { it.id }) { provider ->
                val providerModels = models.filter { it.providerInstanceId == provider.id }
                ProviderInstanceCard(
                    provider = provider,
                    maskedKey = uiState.maskedKeysByProvider[provider.id],
                    providerModels = providerModels,
                    isBusy = uiState.busyProviderId == provider.id,
                    onEdit = { editingProvider = provider },
                    onDuplicate = { onDuplicateProvider(provider) },
                    onSaveApiKeyOnly = { rawKey -> onSaveProviderApiKeyOnly(provider, rawKey) },
                    onClearApiKey = { onClearProviderApiKey(provider) },
                    onUpdateBaseUrl = { newUrl -> onUpdateProviderBaseUrl(provider, newUrl) },
                    onToggleEnabled = { onToggleProviderEnabled(provider) },
                    onUpdateCapabilities = { tags -> onUpdateProviderCapabilities(provider, tags) },
                    onSetDefaultFeature = { cap -> onSetDefaultForFeature(provider.id, cap) },
                    onTestConnection = { overrideUrl, overrideKey ->
                        onTestConnectionOnly(provider, overrideUrl, overrideKey)
                    },
                    onFetchModels = { overrideUrl, overrideKey ->
                        onTestAndFetchModels(provider, overrideUrl, overrideKey)
                    },
                    onAddManualModel = { modelName, isCombo ->
                        onAddManualModel(provider.id, modelName, isCombo)
                    },
                    onToggleFavoriteModel = onToggleFavoriteModel,
                    onSelectModelAsDefault = { modelId -> onSelectDefaultChatModel(provider, modelId) },
                    onDelete = { onDeleteProvider(provider.id) }
                )
            }
        }

        // Section 7: Default Provider & Model per Modality Summary
        item {
            val chatDefault = providers.firstOrNull { it.isDefaultForChat } ?: providers.firstOrNull()
            val imageDefault = providers.firstOrNull { it.isDefaultForImage } ?: chatDefault
            val videoDefault = providers.firstOrNull { it.isDefaultForVideo } ?: chatDefault

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Default Provider & Model per Feature",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModalityDefaultPill(
                            modality = "Chat Default",
                            providerName = chatDefault?.displayName ?: "OmniRoute",
                            modelId = chatDefault?.defaultChatModelId?.ifBlank { "omniroute/auto-combo" } ?: "omniroute/auto-combo"
                        )
                        ModalityDefaultPill(
                            modality = "Image Default",
                            providerName = imageDefault?.displayName ?: "OmniRoute",
                            modelId = imageDefault?.defaultImageModelId?.ifBlank { "omniroute/flux-combo" } ?: "omniroute/flux-combo"
                        )
                        ModalityDefaultPill(
                            modality = "Video Default",
                            providerName = videoDefault?.displayName ?: "OmniRoute",
                            modelId = videoDefault?.defaultVideoModelId?.ifBlank { "omniroute/video-combo" } ?: "omniroute/video-combo"
                        )
                    }
                }
            }
        }

        // Section 6: User-Approved Cleartext HTTP Host List
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AmberWarning
                        )
                        Text(
                            text = "User-Approved Cleartext HTTP Hosts",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Android's static network_security_config cannot whitelist CIDR IP ranges like 192.168.x.x. Aerodock blocks HTTP by default in OkHttp unless the exact LAN IP, Tailscale host, or emulator loopback (10.0.2.2) is approved below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCleartextHostText,
                            onValueChange = { newCleartextHostText = it },
                            placeholder = { Text("e.g. 192.168.1.100 or omniroute.tailnet") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("approve_host_input")
                        )
                        Button(
                            onClick = {
                                onApproveCleartextHostManually(newCleartextHostText)
                                newCleartextHostText = ""
                            },
                            enabled = newCleartextHostText.isNotBlank(),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("approve_host_add_button")
                        ) {
                            Text("Approve")
                        }
                    }

                    if (uiState.approvedCleartextHosts.isEmpty()) {
                        Text(
                            text = "No HTTP hosts approved yet. HTTPS is enforced for all requests.",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldConnected
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.approvedCleartextHosts.forEach { host ->
                                FilterChip(
                                    selected = true,
                                    onClick = { onRevokeCleartextHost(host) },
                                    label = { Text("$host (Tap to revoke)") },
                                    modifier = Modifier.testTag("approved_host_chip_$host")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 7: Preferences
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enter Key Sends Message", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Press Enter to send chat messages immediately",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.enterToSendEnabled,
                            onCheckedChange = onSetEnterToSend
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Usage Spend Currency", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Display estimated spending in Indian Rupees (₹ INR) or US Dollars ($ USD) • 1 USD ≈ ₹${String.format(Locale.US, "%.2f", uiState.usdToInrRate)} INR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = uiState.spendCurrency == "INR",
                                onClick = { onSetSpendCurrency("INR") },
                                label = { Text("₹ INR") },
                                modifier = Modifier.testTag("settings_currency_inr")
                            )
                            FilterChip(
                                selected = uiState.spendCurrency == "USD",
                                onClick = { onSetSpendCurrency("USD") },
                                label = { Text("$ USD") },
                                modifier = Modifier.testTag("settings_currency_usd")
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenOnboardingSheet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open OmniRoute Onboarding & LAN/Tailscale Guide")
                    }
                }
            }
        }

        // Section 8: Vault Security, Biometric Gate, Export/Import & Clear All Data
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldConnected)
                        Text(
                            text = "Vault Security, Backup & Data Wipe",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Gate on Launch", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Require fingerprint/device credential before unlocking workspace",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.biometricGateEnabled,
                            onCheckedChange = onSetBiometricGate,
                            modifier = Modifier.testTag("biometric_gate_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeKeysInExport = !includeKeysInExport }
                    ) {
                        Checkbox(
                            checked = includeKeysInExport,
                            onCheckedChange = { includeKeysInExport = it }
                        )
                        Text(
                            text = "Opt-in: Include decrypted API keys when exporting settings JSON (Excluded by default)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExportSettings(includeKeysInExport) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_settings_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Settings")
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_settings_button")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Settings")
                        }
                    }

                    Button(
                        onClick = { showClearConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("clear_all_data_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Data (Keys, Chats & Approved Hosts)")
                    }
                }
            }
        }

        // Section 9: Network Diagnostics Ring-Buffer Inspector (100-entry redacted HTTP log)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("network_diagnostics_ring_buffer_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Network Diagnostics Ring-Buffer (${diagnosticEntries.size}/100)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Method, Redacted URL, HTTP Status, Latency & Error Taxonomy (Secrets Redacted)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (diagnosticEntries.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearDiagnostics,
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("clear_diagnostics_button")
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    if (diagnosticEntries.isEmpty()) {
                        Text(
                            text = "No HTTP requests recorded in this session yet. Send a message or test a provider connection to inspect live traffic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            diagnosticEntries.take(20).forEach { entry ->
                                val statusColor = when {
                                    entry.statusCode in 200..299 -> EmeraldConnected
                                    entry.statusCode == 401 || entry.statusCode == 405 || (entry.statusCode ?: 0) >= 400 -> CoralError
                                    entry.errorClass != null -> CoralError
                                    else -> AmberWarning
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                BadgePill(
                                                    text = entry.method,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                BadgePill(
                                                    text = entry.statusCode?.let { "HTTP $it" } ?: (entry.errorClass ?: "ERR"),
                                                    color = statusColor
                                                )
                                                Text(
                                                    text = "${entry.latencyMs}ms",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = timeFormatter.format(Date(entry.timestampMs)),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = entry.redactedUrl,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!entry.errorSummary.isNullOrBlank()) {
                                            Text(
                                                text = "${entry.errorClass ?: "Error"}: ${entry.errorSummary}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = CoralError
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

@Composable
private fun ModalityDefaultPill(
    modality: String,
    providerName: String,
    modelId: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = modality,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = providerName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = modelId,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OmniRouteEndpointCard(
    provider: ProviderInstanceEntity,
    maskedKey: String?,
    providerModels: List<ModelDescriptorEntity>,
    isBusy: Boolean,
    approvedCleartextHosts: Set<String>,
    onUpdateBaseUrl: (String) -> Unit,
    onSaveApiKeyOnly: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUpdateCapabilities: (Set<CapabilityTag>) -> Unit,
    onSetDefaultFeature: (CapabilityTag) -> Unit,
    onTestConnection: (overrideUrl: String?, overrideKey: String?) -> Unit,
    onFetchModels: (overrideUrl: String?, overrideKey: String?) -> Unit,
    onAddManualModel: (modelId: String, isCombo: Boolean) -> Unit,
    onToggleFavoriteModel: (ModelDescriptorEntity) -> Unit,
    onSelectModelAsDefault: (String) -> Unit = {},
    onApproveCleartextHost: (String) -> Unit,
    onOpenOnboardingSheet: () -> Unit,
    onEditInDialog: () -> Unit
) {
    val caps = provider.capabilitySet()

    var editableBaseUrl by rememberSaveable(provider.id, provider.baseUrl) {
        mutableStateOf(provider.baseUrl)
    }
    var quickKeyInput by rememberSaveable(provider.id) { mutableStateOf("") }
    var showQuickKey by rememberSaveable(provider.id) { mutableStateOf(false) }
    var quickManualModel by rememberSaveable(provider.id) { mutableStateOf("") }
    var manualIsCombo by rememberSaveable(provider.id) { mutableStateOf(true) }
    var showModelsList by rememberSaveable(provider.id) { mutableStateOf(true) }
    var showLanHelp by rememberSaveable(provider.id) { mutableStateOf(false) }

    val parsedHost = remember(editableBaseUrl) {
        if (editableBaseUrl.startsWith("http://", ignoreCase = true)) {
            editableBaseUrl.removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")
        } else ""
    }
    val needsApproval = parsedHost.isNotBlank() &&
        parsedHost != "localhost" &&
        parsedHost != "127.0.0.1" &&
        parsedHost !in approvedCleartextHosts

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}")
            .testTag("omniroute_endpoint_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Branding, Tag, Health Status & Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    BadgePill(text = "OMNI", color = MaterialTheme.colorScheme.primary)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "OmniRoute Endpoint Router",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!maskedKey.isNullOrBlank()) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Encrypted Key Stored",
                                    tint = EmeraldConnected,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "Unified Gateway • ${providerModels.size} models • ${provider.lastTestStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditInDialog) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit in dialog")
                    }
                    Switch(
                        checked = provider.isEnabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.testTag("toggle_provider_${provider.id}")
                    )
                }
            }

            HorizontalDivider()

            // 1. Modifiable Base URL
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Endpoint Base URL (OpenAI-Compatible):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { showLanHelp = !showLanHelp }) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showLanHelp) "Hide Network Guide" else "Network / IP Guide")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editableBaseUrl,
                        onValueChange = { editableBaseUrl = it },
                        singleLine = true,
                        label = { Text("OmniRoute Base URL") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("omni_settings_base_url_input")
                    )
                    if (editableBaseUrl.trim() != provider.baseUrl) {
                        Button(
                            onClick = { onUpdateBaseUrl(editableBaseUrl.trim()) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("omni_save_url_btn")
                        ) {
                            Text("Save URL")
                        }
                    }
                }

                // Quick-preset chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = editableBaseUrl.contains("192.168."),
                        onClick = {
                            editableBaseUrl = "http://192.168.1.100:20128/v1"
                            onUpdateBaseUrl(editableBaseUrl)
                        },
                        label = { Text("LAN IP (192.168.1.100)") }
                    )
                    FilterChip(
                        selected = editableBaseUrl.contains("10.0.2.2"),
                        onClick = {
                            editableBaseUrl = "http://10.0.2.2:20128/v1"
                            onUpdateBaseUrl(editableBaseUrl)
                        },
                        label = { Text("Emulator (10.0.2.2)") }
                    )
                    FilterChip(
                        selected = editableBaseUrl.contains("localhost"),
                        onClick = {
                            editableBaseUrl = "http://localhost:20128/v1"
                            onUpdateBaseUrl(editableBaseUrl)
                        },
                        label = { Text("localhost:20128") }
                    )
                    FilterChip(
                        selected = editableBaseUrl.contains("tailnet"),
                        onClick = {
                            editableBaseUrl = "http://omniroute.tailnet:20128/v1"
                            onUpdateBaseUrl(editableBaseUrl)
                        },
                        label = { Text("Tailscale") }
                    )
                    FilterChip(
                        selected = editableBaseUrl.contains("api.omniroute.ai"),
                        onClick = {
                            editableBaseUrl = "https://api.omniroute.ai/v1"
                            onUpdateBaseUrl(editableBaseUrl)
                        },
                        label = { Text("Cloud (omniroute.ai)") }
                    )
                }

                // Cleartext host unapproved warning
                if (needsApproval) {
                    Surface(
                        color = AmberWarning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmberWarning)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning)
                                Text(
                                    text = "Cleartext HTTP host '$parsedHost' requires security approval.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = { onApproveCleartextHost(parsedHost) },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text("Approve Host")
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showLanHelp) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "• Physical Android Device: Use your workstation LAN IP (e.g. http://192.168.1.100:20128/v1) or Tailscale magic DNS.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Android Emulator: 10.0.2.2 routes directly to host 127.0.0.1 on your dev PC.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Termux / Local Port Forward: localhost:20128 targets the on-device loopback interface.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 2. Encrypted API Key Input + Save Key / Clear Key
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "API Key (Tink Vault Keystore):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quickKeyInput,
                        onValueChange = { quickKeyInput = it },
                        label = {
                            Text(
                                if (!maskedKey.isNullOrBlank()) "Key: $maskedKey (Encrypted in Keystore)"
                                else "API Key (Optional for local gateway)"
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showQuickKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showQuickKey = !showQuickKey }) {
                                Icon(
                                    imageVector = if (showQuickKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("omni_key_input")
                            .testTag("provider_key_input_${provider.id}")
                    )

                    if (quickKeyInput.isNotBlank()) {
                        Button(
                            onClick = {
                                onSaveApiKeyOnly(quickKeyInput)
                                quickKeyInput = ""
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("omni_save_key_btn")
                                .testTag("save_key_btn_${provider.id}")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Key")
                        }
                    } else if (!maskedKey.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onClearApiKey,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("omni_clear_key_btn")
                                .testTag("clear_key_btn_${provider.id}")
                        ) {
                            Text("Clear Key")
                        }
                    }
                }
            }

            // 3. Actions: Fetch Models (Primary) & Test Connection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onFetchModels(
                            editableBaseUrl,
                            quickKeyInput.takeIf { it.isNotBlank() }
                        )
                        quickKeyInput = ""
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("omni_fetch_models_btn")
                        .testTag("test_fetch_btn_${provider.id}")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching Models…")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch Models")
                    }
                }

                OutlinedButton(
                    onClick = {
                        onTestConnection(
                            editableBaseUrl,
                            quickKeyInput.takeIf { it.isNotBlank() }
                        )
                        quickKeyInput = ""
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("omni_test_connection_btn")
                        .testTag("test_connection_btn_${provider.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Connection")
                }
            }

            // 4. Discovered Models & Combos display
            if (providerModels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showModelsList = !showModelsList },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showModelsList) "Discovered Models & Combos (${providerModels.size}) [Hide]"
                        else "Discovered Models & Combos (${providerModels.size}) [Show]",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (provider.defaultChatModelId.isNotBlank()) {
                        Text(
                            text = "Default: ${provider.defaultChatModelId}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = showModelsList) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        providerModels.forEach { m ->
                            val isDefaultChat = provider.defaultChatModelId == m.modelId
                            FilterChip(
                                selected = isDefaultChat || m.isFavorite,
                                onClick = {
                                    onSelectModelAsDefault(m.modelId)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (m.isFavorite || isDefaultChat) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "Favorite model",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onToggleFavoriteModel(m) }
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (m.isCombo) "${m.modelId} [Combo]" else m.modelId,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        fontWeight = if (isDefaultChat) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 5. Manual Model / Combo Entry row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickManualModel,
                    onValueChange = { quickManualModel = it },
                    placeholder = { Text("Add model ID or OmniRoute combo manually…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("omni_manual_model_input")
                        .testTag("manual_model_input_${provider.id}")
                )
                FilterChip(
                    selected = manualIsCombo,
                    onClick = { manualIsCombo = !manualIsCombo },
                    label = { Text("Combo") }
                )
                OutlinedButton(
                    onClick = {
                        if (quickManualModel.isNotBlank()) {
                            onAddManualModel(quickManualModel.trim(), manualIsCombo)
                            quickManualModel = ""
                        }
                    },
                    enabled = quickManualModel.isNotBlank(),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_manual_model_btn_${provider.id}")
                ) {
                    Text("Add")
                }
            }

            // 6. Capability Tags & Default Modality Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Capabilities:", style = MaterialTheme.typography.labelMedium)
                CapabilityTag.entries.forEach { tag ->
                    val selected = tag in caps
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = if (selected) caps - tag else caps + tag
                            onUpdateCapabilities(updated)
                        },
                        label = { Text(tag.label) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Default For:", style = MaterialTheme.typography.labelMedium)
                FilterChip(
                    selected = provider.isDefaultForChat,
                    onClick = { onSetDefaultFeature(CapabilityTag.CHAT) },
                    label = { Text("Chat Default") }
                )
                FilterChip(
                    selected = provider.isDefaultForImage,
                    onClick = { onSetDefaultFeature(CapabilityTag.IMAGE) },
                    label = { Text("Image Default") }
                )
                FilterChip(
                    selected = provider.isDefaultForVideo,
                    onClick = { onSetDefaultFeature(CapabilityTag.VIDEO) },
                    label = { Text("Video Default") }
                )
            }

            OutlinedButton(
                onClick = onOpenOnboardingSheet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Complete OmniRoute & LAN Setup Guide")
            }
        }
    }
}

@Composable
private fun ProviderInstanceCard(
    provider: ProviderInstanceEntity,
    maskedKey: String?,
    providerModels: List<ModelDescriptorEntity>,
    isBusy: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSaveApiKeyOnly: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onUpdateBaseUrl: (String) -> Unit,
    onToggleEnabled: () -> Unit,
    onUpdateCapabilities: (Set<CapabilityTag>) -> Unit,
    onSetDefaultFeature: (CapabilityTag) -> Unit,
    onTestConnection: (overrideUrl: String?, overrideKey: String?) -> Unit,
    onFetchModels: (overrideUrl: String?, overrideKey: String?) -> Unit,
    onAddManualModel: (modelId: String, isCombo: Boolean) -> Unit,
    onToggleFavoriteModel: (ModelDescriptorEntity) -> Unit,
    onSelectModelAsDefault: (String) -> Unit = {},
    onDelete: () -> Unit
) {
    val preset = provider.presetEnum()
    val isOmniRoute = preset == ProviderPresetType.OMNIROUTE
    val caps = provider.capabilitySet()

    var editableBaseUrl by rememberSaveable(provider.id, provider.baseUrl) {
        mutableStateOf(provider.baseUrl)
    }
    var quickKeyInput by rememberSaveable(provider.id) { mutableStateOf("") }
    var showQuickKey by rememberSaveable(provider.id) { mutableStateOf(false) }
    var quickManualModel by rememberSaveable(provider.id) { mutableStateOf("") }
    var manualIsCombo by rememberSaveable(provider.id) { mutableStateOf(isOmniRoute) }
    var showModelsList by rememberSaveable(provider.id) { mutableStateOf(isOmniRoute) }
    var showLanHelp by rememberSaveable(provider.id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Badge, Name, Status, Enabled Toggle
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
                    BadgePill(
                        text = preset.badgeShort,
                        color = if (isOmniRoute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!maskedKey.isNullOrBlank()) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Encrypted Key Stored",
                                    tint = EmeraldConnected,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "${preset.phaseTag} • ${providerModels.size} models • ${provider.lastTestStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.testTag("toggle_provider_${provider.id}")
                )
            }

            // OmniRoute Preset Inline Base URL Editor & Quick-Fill Chips
            if (isOmniRoute) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OmniRoute Gateway Base URL (OpenAI-Compatible):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { showLanHelp = !showLanHelp }) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showLanHelp) "Hide Network Guide" else "LAN vs 10.0.2.2 Help")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editableBaseUrl,
                            onValueChange = { editableBaseUrl = it },
                            singleLine = true,
                            label = { Text("OmniRoute Base URL") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("omni_settings_base_url_input")
                        )
                        if (editableBaseUrl.trim() != provider.baseUrl) {
                            Button(
                                onClick = { onUpdateBaseUrl(editableBaseUrl) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Save URL")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = editableBaseUrl.contains("192.168."),
                            onClick = {
                                editableBaseUrl = "http://192.168.1.100:20128/v1"
                                onUpdateBaseUrl(editableBaseUrl)
                            },
                            label = { Text("LAN IP (Phone)") }
                        )
                        FilterChip(
                            selected = editableBaseUrl.contains("tailnet"),
                            onClick = {
                                editableBaseUrl = "http://omniroute.tailnet:20128/v1"
                                onUpdateBaseUrl(editableBaseUrl)
                            },
                            label = { Text("Tailscale") }
                        )
                        FilterChip(
                            selected = editableBaseUrl.contains("10.0.2.2"),
                            onClick = {
                                editableBaseUrl = "http://10.0.2.2:20128/v1"
                                onUpdateBaseUrl(editableBaseUrl)
                            },
                            label = { Text("10.0.2.2 (Emulator Hint)") }
                        )
                        FilterChip(
                            selected = editableBaseUrl.contains("localhost"),
                            onClick = {
                                editableBaseUrl = "http://localhost:20128/v1"
                                onUpdateBaseUrl(editableBaseUrl)
                            },
                            label = { Text("localhost:20128") }
                        )
                    }

                    AnimatedVisibility(visible = showLanHelp) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "• Real Android Phone: Use your PC/server LAN IP (e.g. http://192.168.1.100:20128/v1) or Tailscale hostname.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• Android Emulator Only: 10.0.2.2 maps to the host machine's 127.0.0.1.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• On-Device Termux / Port-Forward: localhost:20128 points to the Android device itself.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = provider.baseUrl,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Encrypted API Key Input + Save Key / Clear Key buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickKeyInput,
                    onValueChange = { quickKeyInput = it },
                    label = {
                        Text(
                            if (!maskedKey.isNullOrBlank()) "Tink Vault Key ($maskedKey) — Enter new to replace"
                            else "Enter API Key (Encrypted via Tink + Android Keystore)"
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showQuickKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showQuickKey = !showQuickKey }) {
                            Icon(
                                imageVector = if (showQuickKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("provider_key_input_${provider.id}")
                )

                if (quickKeyInput.isNotBlank()) {
                    Button(
                        onClick = {
                            onSaveApiKeyOnly(quickKeyInput)
                            quickKeyInput = ""
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("save_key_btn_${provider.id}")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Key")
                    }
                } else if (!maskedKey.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onClearApiKey,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("clear_key_btn_${provider.id}")
                    ) {
                        Text("Clear Key")
                    }
                }
            }

            // Capability Tags (Auto-detected & Editable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Capabilities:", style = MaterialTheme.typography.labelMedium)
                CapabilityTag.entries.forEach { tag ->
                    val selected = tag in caps
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = if (selected) caps - tag else caps + tag
                            onUpdateCapabilities(updated)
                        },
                        label = { Text(tag.label) }
                    )
                }
            }

            // Default Provider per Feature toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Default For:", style = MaterialTheme.typography.labelMedium)
                FilterChip(
                    selected = provider.isDefaultForChat,
                    onClick = { onSetDefaultFeature(CapabilityTag.CHAT) },
                    label = { Text("Chat Default") }
                )
                FilterChip(
                    selected = provider.isDefaultForImage,
                    onClick = { onSetDefaultFeature(CapabilityTag.IMAGE) },
                    label = { Text("Image Default") }
                )
                FilterChip(
                    selected = provider.isDefaultForVideo,
                    onClick = { onSetDefaultFeature(CapabilityTag.VIDEO) },
                    label = { Text("Video Default") }
                )
            }

            // Manual Model Entry fallback row with Combo toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickManualModel,
                    onValueChange = { quickManualModel = it },
                    placeholder = { Text("Add model ID or OmniRoute combo manually…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_model_input_${provider.id}")
                )
                FilterChip(
                    selected = manualIsCombo,
                    onClick = { manualIsCombo = !manualIsCombo },
                    label = { Text("Combo") }
                )
                OutlinedButton(
                    onClick = {
                        if (quickManualModel.isNotBlank()) {
                            onAddManualModel(quickManualModel.trim(), manualIsCombo)
                            quickManualModel = ""
                        }
                    },
                    enabled = quickManualModel.isNotBlank(),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_manual_model_btn_${provider.id}")
                ) {
                    Text("Add")
                }
            }

            // Expandable Discovered Models & Combos section
            if (providerModels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showModelsList = !showModelsList },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showModelsList) "Hide Models & Combos (${providerModels.size})"
                        else "Show Models & Combos (${providerModels.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (provider.defaultChatModelId.isNotBlank()) {
                        Text(
                            text = "Default: ${provider.defaultChatModelId}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = showModelsList) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        providerModels.forEach { m ->
                            val isDefaultChat = provider.defaultChatModelId == m.modelId
                            FilterChip(
                                selected = isDefaultChat || m.isFavorite,
                                onClick = {
                                    onSelectModelAsDefault(m.modelId)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (m.isFavorite || isDefaultChat) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "Favorite model",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onToggleFavoriteModel(m) }
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (m.isCombo) "${m.modelId} [Combo]" else m.modelId,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        fontWeight = if (isDefaultChat) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Actions Row: Separate "Test Connection" and "Fetch Models" buttons + Clone / Edit / Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onTestConnection(
                                editableBaseUrl.takeIf { isOmniRoute },
                                quickKeyInput.takeIf { it.isNotBlank() }
                            )
                            quickKeyInput = ""
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("test_connection_btn_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection")
                    }

                    Button(
                        onClick = {
                            onFetchModels(
                                editableBaseUrl.takeIf { isOmniRoute },
                                quickKeyInput.takeIf { it.isNotBlank() }
                            )
                            quickKeyInput = ""
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("test_fetch_btn_${provider.id}")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Fetch Models")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.testTag("clone_provider_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Add second instance of ${provider.displayName}"
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit provider")
                    }
                    if (provider.id != "provider_omniroute_default") {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete provider",
                                tint = CoralError
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    initialProvider: ProviderInstanceEntity?,
    maskedKey: String?,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        preset: ProviderPresetType,
        displayName: String,
        baseUrl: String,
        rawApiKey: String?,
        capabilities: Set<CapabilityTag>,
        isEnabled: Boolean,
        defaultChatModel: String,
        defaultImageModel: String,
        defaultVideoModel: String
    ) -> Unit
) {
    var selectedPreset by rememberSaveable {
        mutableStateOf(initialProvider?.presetEnum() ?: ProviderPresetType.OPENAI)
    }
    var displayName by rememberSaveable {
        mutableStateOf(initialProvider?.displayName ?: selectedPreset.displayName)
    }
    var baseUrl by rememberSaveable {
        mutableStateOf(initialProvider?.baseUrl ?: selectedPreset.defaultBaseUrl)
    }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var defaultChatModel by rememberSaveable {
        mutableStateOf(initialProvider?.defaultChatModelId ?: "")
    }
    var defaultImageModel by rememberSaveable {
        mutableStateOf(initialProvider?.defaultImageModelId ?: "")
    }
    var defaultVideoModel by rememberSaveable {
        mutableStateOf(initialProvider?.defaultVideoModelId ?: "")
    }
    var selectedCaps by remember {
        mutableStateOf(initialProvider?.capabilitySet() ?: selectedPreset.defaultCapabilities)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProvider == null) "Add Provider Instance" else "Edit Provider Instance")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Select Preset Type:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderPresetType.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                if (initialProvider == null) {
                                    displayName = preset.displayName
                                    baseUrl = preset.defaultBaseUrl
                                    selectedCaps = preset.defaultCapabilities
                                }
                            },
                            label = { Text(preset.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Instance Name (e.g. OpenAI Work Key)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_provider_name_input")
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_provider_url_input")
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            if (!maskedKey.isNullOrBlank()) "API Key ($maskedKey) — Leave blank to keep"
                            else "API Key (Encrypted via Tink + Android Keystore)"
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_provider_key_input")
                )

                OutlinedTextField(
                    value = defaultChatModel,
                    onValueChange = { defaultChatModel = it },
                    label = { Text("Default Chat Model ID") },
                    placeholder = { Text("e.g. gpt-4o or omniroute/auto-combo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (CapabilityTag.IMAGE in selectedCaps) {
                    OutlinedTextField(
                        value = defaultImageModel,
                        onValueChange = { defaultImageModel = it },
                        label = { Text("Default Image Model ID") },
                        placeholder = { Text("e.g. dall-e-3 or flux-pro") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (CapabilityTag.VIDEO in selectedCaps) {
                    OutlinedTextField(
                        value = defaultVideoModel,
                        onValueChange = { defaultVideoModel = it },
                        label = { Text("Default Video Model ID") },
                        placeholder = { Text("e.g. runway-gen3 or luma-ray2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CapabilityTag.entries.forEach { tag ->
                        val active = tag in selectedCaps
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedCaps = if (active) selectedCaps - tag else selectedCaps + tag
                            },
                            label = { Text(tag.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialProvider?.id,
                        selectedPreset,
                        displayName,
                        baseUrl,
                        apiKey.takeIf { it.isNotBlank() },
                        selectedCaps,
                        initialProvider?.isEnabled ?: true,
                        defaultChatModel,
                        defaultImageModel,
                        defaultVideoModel
                    )
                },
                modifier = Modifier.testTag("dialog_save_provider_button")
            ) {
                Text("Save Instance")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
