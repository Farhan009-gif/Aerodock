package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CapabilityTag
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.ProviderPresetType
import com.example.data.provider.OmniRouteComboDefinition
import com.example.data.provider.ProviderHealth
import com.example.ui.components.LucideIcons
import com.example.ui.components.MinimalButton
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalChip
import com.example.ui.components.MinimalStatusDot
import com.example.ui.components.MinimalToggle
import com.example.data.local.UsageRecordEntity
import com.example.data.provider.ProviderSpendingLimitEvaluator
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.SoftIndigo
import com.example.ui.viewmodel.ProvidersUiState
import com.example.ui.viewmodel.ProvidersViewModel
import androidx.compose.material3.LinearProgressIndicator
import java.util.Locale

/**
 * Minimal Keys screen: a clean list of providers, masked keys, an edit icon, a "Test" button,
 * and an OmniRoute Gateway card for configuring Video Generation models and Custom Combos.
 */
@Composable
fun ProvidersScreen(
    uiState: ProvidersUiState,
    viewModel: ProvidersViewModel,
    usageRecords: List<UsageRecordEntity> = emptyList(),
    providerMonthlyLimitsUsd: Map<String, Double> = emptyMap(),
    onSetProviderMonthlyLimitUsd: (providerId: String, limitUsd: Double?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showComboDialog by rememberSaveable { mutableStateOf(false) }
    var editingCombo by remember { mutableStateOf<OmniRouteComboDefinition?>(null) }
    var showAddCustomModelDialog by rememberSaveable { mutableStateOf(false) }
    var editingLimitProvider by remember { mutableStateOf<ProviderInstanceEntity?>(null) }

    val filteredProviders = remember(uiState.providers, uiState.selectedFilter, uiState.searchQuery) {
        uiState.providers.filter { prov ->
            val matchesSearch = uiState.searchQuery.isBlank() ||
                prov.displayName.contains(uiState.searchQuery, ignoreCase = true) ||
                prov.presetType.contains(uiState.searchQuery, ignoreCase = true) ||
                prov.baseUrl.contains(uiState.searchQuery, ignoreCase = true)

            val matchesFilter = when (uiState.selectedFilter) {
                "ENABLED" -> prov.isEnabled
                "CHAT" -> CapabilityTag.CHAT in prov.capabilitySet()
                "IMAGE" -> CapabilityTag.IMAGE in prov.capabilitySet()
                "VIDEO" -> CapabilityTag.VIDEO in prov.capabilitySet()
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    if (showComboDialog || editingCombo != null) {
        OmniRouteComboBuilderDialog(
            initialCombo = editingCombo,
            onDismiss = {
                showComboDialog = false
                editingCombo = null
            },
            onSave = { rawId, displayName, modality, strategy, targetModels, description ->
                viewModel.upsertOmniRouteCustomCombo(
                    rawId = rawId,
                    displayName = displayName,
                    modality = modality,
                    strategy = strategy,
                    targetModels = targetModels,
                    description = description
                )
                showComboDialog = false
                editingCombo = null
            }
        )
    }

    if (showAddCustomModelDialog) {
        AddCustomModelDialog(
            providers = uiState.providers,
            onDismiss = { showAddCustomModelDialog = false },
            onSave = { providerId, rawModelId, displayName, modality, isCombo ->
                viewModel.addCustomModelToProvider(
                    providerId = providerId,
                    rawModelId = rawModelId,
                    displayName = displayName,
                    modality = modality,
                    isCombo = isCombo
                )
                showAddCustomModelDialog = false
            }
        )
    }

    if (uiState.showAddProviderDialog || uiState.editingProvider != null) {
        AddEditProviderDialog(
            initialProvider = uiState.editingProvider,
            maskedKey = uiState.editingProvider?.let { uiState.maskedKeysByProvider[it.id] },
            onDismiss = {
                viewModel.closeAddProviderDialog()
                viewModel.closeEditProviderDialog()
            },
            onSave = { existingId, preset, name, url, key, caps, enabled, chatModel, imgModel, vidModel ->
                viewModel.saveProviderInstance(
                    existingId = existingId,
                    preset = preset,
                    displayName = name,
                    baseUrl = url,
                    rawApiKey = key,
                    capabilities = caps,
                    isEnabled = enabled,
                    defaultChatModel = chatModel,
                    defaultImageModel = imgModel,
                    defaultVideoModel = vidModel
                )
            }
        )
    }

    uiState.quickEditKeyProvider?.let { targetProv ->
        QuickEditApiKeyDialog(
            provider = targetProv,
            currentMaskedKey = uiState.maskedKeysByProvider[targetProv.id],
            onDismiss = viewModel::closeQuickEditKeyDialog,
            onSave = { newRawKey ->
                viewModel.saveApiKey(targetProv.id, newRawKey)
            },
            onClear = {
                viewModel.clearApiKey(targetProv.id)
                viewModel.closeQuickEditKeyDialog()
            }
        )
    }

    if (uiState.showVaultDetailsDialog) {
        TinkVaultDetailsDialog(
            status = uiState.vaultSecurityStatus,
            onDismiss = viewModel::closeVaultDetailsDialog
        )
    }

    editingLimitProvider?.let { targetProv ->
        val dialogFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val dialogKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        var clearDialogIme by remember { mutableStateOf<(() -> Unit)?>(null) }
        val dismissLimitDialog: () -> Unit = {
            clearDialogIme?.invoke()
            dialogFocusManager.clearFocus(force = true)
            dialogKeyboard?.hide()
            editingLimitProvider = null
        }
        val existingLimit = providerMonthlyLimitsUsd[targetProv.id]
        var limitText by rememberSaveable(targetProv.id) {
            mutableStateOf(
                if (existingLimit != null && existingLimit > 0.0) {
                    String.format(Locale.US, "%.2f", existingLimit)
                } else ""
            )
        }
        AlertDialog(
            onDismissRequest = dismissLimitDialog,
            title = {
                Text(
                    text = "Monthly Spending Limit · ${targetProv.displayName}",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                com.example.ui.components.DialogWindowFocusGuard(onRegisterCleaner = { clearDialogIme = it })
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Set an optional monthly USD limit for ${targetProv.displayName}. At 80% a warning appears in chat; at 100% requests are blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Monthly limit (USD)") },
                        placeholder = { Text("5.00") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_monthly_limit_input_${targetProv.id}")
                    )
                }
            },
            confirmButton = {
                MinimalButton(
                    text = "Save",
                    onClick = {
                        val parsed = limitText.trim().removePrefix("$").toDoubleOrNull()
                        clearDialogIme?.invoke()
                        dialogFocusManager.clearFocus(force = true)
                        dialogKeyboard?.hide()
                        onSetProviderMonthlyLimitUsd(targetProv.id, parsed)
                        editingLimitProvider = null
                    },
                    testTag = "save_provider_monthly_limit_button"
                )
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existingLimit != null) {
                        TextButton(
                            onClick = {
                                dialogFocusManager.clearFocus(force = true)
                                dialogKeyboard?.hide()
                                onSetProviderMonthlyLimitUsd(targetProv.id, null)
                                editingLimitProvider = null
                            }
                        ) {
                            Text("Remove limit", color = CoralError)
                        }
                    }
                    TextButton(onClick = dismissLimitDialog) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    val omniVideoModelsCount = remember(uiState.models) {
        uiState.models.count {
            it.providerInstanceId.contains("omniroute", ignoreCase = true) &&
                CapabilityTag.VIDEO in it.capabilitySet()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("providers_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Keys & Gateway",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${uiState.vaultSecurityStatus.encryptedKeyCount} encrypted · AES-256-GCM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = viewModel::openVaultDetailsDialog)
                            .testTag("tink_vault_security_card")
                    )
                }

                MinimalButton(
                    text = "Add provider",
                    onClick = viewModel::openAddProviderDialog,
                    icon = LucideIcons.Plus,
                    filled = false,
                    testTag = "add_provider_button"
                )
            }
        }

        item {
            OmniRouteGatewayCombosCard(
                isListening = uiState.localhostServerStatus.isListening,
                port = uiState.localhostServerStatus.port,
                combos = uiState.omniRouteCombos,
                videoModelsCount = omniVideoModelsCount,
                onNewCombo = {
                    editingCombo = null
                    showComboDialog = true
                },
                onEditCombo = { combo ->
                    editingCombo = combo
                    showComboDialog = true
                },
                onDeleteCombo = { comboId ->
                    viewModel.deleteOmniRouteCustomCombo(comboId)
                },
                onAddCustomModel = {
                    showAddCustomModelDialog = true
                }
            )
        }

        items(filteredProviders, key = { it.id }) { provider ->
            val maskedKey = uiState.maskedKeysByProvider[provider.id]
            val health = uiState.providerHealthById[provider.id] ?: ProviderHealth.Unknown
            val isBusyTesting = uiState.busyTestingProviderId == provider.id
            val monthlySpendUsd = remember(usageRecords, provider.id) {
                ProviderSpendingLimitEvaluator.calculateMonthlySpendUsd(
                    usageRecords = usageRecords,
                    providerInstanceId = provider.id
                )
            }
            val monthlyLimitUsd = providerMonthlyLimitsUsd[provider.id]

            ProviderCardItem(
                provider = provider,
                maskedKey = maskedKey,
                health = health,
                isBusyTesting = isBusyTesting,
                monthlySpendUsd = monthlySpendUsd,
                monthlyLimitUsd = monthlyLimitUsd,
                onToggleEnabled = { viewModel.toggleProviderEnabled(provider) },
                onEditFull = { viewModel.openEditProviderDialog(provider) },
                onQuickEditKey = { viewModel.openQuickEditKeyDialog(provider) },
                onTestConnection = { viewModel.testConnection(provider) },
                onEditMonthlyLimit = { editingLimitProvider = provider }
            )
        }
    }
}

@Composable
private fun ProviderCardItem(
    provider: ProviderInstanceEntity,
    maskedKey: String?,
    health: ProviderHealth,
    isBusyTesting: Boolean,
    monthlySpendUsd: Double = 0.0,
    monthlyLimitUsd: Double? = null,
    onToggleEnabled: () -> Unit,
    onEditFull: () -> Unit,
    onQuickEditKey: () -> Unit,
    onTestConnection: () -> Unit,
    onEditMonthlyLimit: () -> Unit = {}
) {
    val hasKey = !maskedKey.isNullOrBlank()
    val safeMaskedDisplay = remember(maskedKey) {
        if (maskedKey.isNullOrBlank()) {
            "••••••••••••"
        } else {
            val suffix = maskedKey.takeLast(4)
            "••••••••$suffix"
        }
    }

    MinimalCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Status dot + Provider Name + Masked Key
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEditFull)
                ) {
                    MinimalStatusDot(isConnected = provider.isEnabled && (hasKey || provider.presetEnum() == ProviderPresetType.OMNIROUTE))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (hasKey) safeMaskedDisplay else "No key set",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasKey) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            when (health) {
                                is ProviderHealth.Healthy -> {
                                    Text(
                                        text = "· ${health.latencyMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SoftIndigo
                                    )
                                }
                                is ProviderHealth.Unhealthy -> {
                                    Text(
                                        text = "· Failed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CoralError
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                // Right: Edit icon + "Test" button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onQuickEditKey,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("edit_key_button_${provider.id}")
                    ) {
                        Icon(
                            imageVector = LucideIcons.Edit,
                            contentDescription = "Edit Key",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Surface(
                        onClick = onTestConnection,
                        enabled = !isBusyTesting,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("test_ping_button_${provider.id}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isBusyTesting) "..." else "Test",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Monthly spending limit row & progress bar ("$3.20 of $5.00 this month")
            val progressText = remember(monthlySpendUsd, monthlyLimitUsd) {
                ProviderSpendingLimitEvaluator.formatMonthlyProgressText(monthlySpendUsd, monthlyLimitUsd)
            }
            val progressFraction = remember(monthlySpendUsd, monthlyLimitUsd) {
                if (monthlyLimitUsd != null && monthlyLimitUsd > 0.0) {
                    (monthlySpendUsd / monthlyLimitUsd).toFloat().coerceIn(0f, 1f)
                } else 0f
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditMonthlyLimit)
                    .testTag("provider_spending_limit_section_${provider.id}"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (monthlyLimitUsd != null && monthlyLimitUsd > 0.0) "Edit limit" else "Set limit",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = SoftIndigo,
                        modifier = Modifier.testTag("provider_set_limit_button_${provider.id}")
                    )
                }
                if (monthlyLimitUsd != null && monthlyLimitUsd > 0.0) {
                    val barColor = when {
                        progressFraction >= 1.0f -> CoralError
                        progressFraction >= 0.8f -> SoftIndigo
                        else -> EmeraldSuccess
                    }
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .testTag("provider_spending_progress_${provider.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickEditApiKeyDialog(
    provider: ProviderInstanceEntity,
    currentMaskedKey: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var rawKey by rememberSaveable { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var clearDialogIme by remember { mutableStateOf<(() -> Unit)?>(null) }
    val safeDismiss: () -> Unit = {
        clearDialogIme?.invoke()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = safeDismiss,
        title = {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            com.example.ui.components.DialogWindowFocusGuard(onRegisterCleaner = { clearDialogIme = it })
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!currentMaskedKey.isNullOrBlank()) {
                    Text(
                        text = "Current: ••••••••${currentMaskedKey.takeLast(4)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = rawKey,
                    onValueChange = { rawKey = it },
                    placeholder = { Text("Enter secret key...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_edit_api_key_input")
                )
                Text(
                    text = "Encrypted with AES-256-GCM before saving. Full keys are never displayed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            MinimalButton(
                text = "Save",
                onClick = {
                    clearDialogIme?.invoke()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onSave(rawKey)
                },
                enabled = rawKey.isNotBlank(),
                filled = true,
                testTag = "confirm_save_quick_key_button"
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!currentMaskedKey.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            clearDialogIme?.invoke()
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onClear()
                        },
                        modifier = Modifier.testTag("clear_key_button_${provider.id}")
                    ) {
                        Text("Clear", color = CoralError)
                    }
                }
                TextButton(onClick = safeDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun AddEditProviderDialog(
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
    var rawApiKey by rememberSaveable { mutableStateOf("") }
    var isEnabled by rememberSaveable { mutableStateOf(initialProvider?.isEnabled ?: true) }
    var capabilities by rememberSaveable {
        mutableStateOf(initialProvider?.capabilitySet() ?: setOf(CapabilityTag.CHAT, CapabilityTag.IMAGE))
    }
    var defaultChatModel by rememberSaveable {
        mutableStateOf(initialProvider?.defaultChatModelId ?: "gpt-4o")
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var clearDialogIme by remember { mutableStateOf<(() -> Unit)?>(null) }

    AlertDialog(
        onDismissRequest = {
            clearDialogIme?.invoke()
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            onDismiss()
        },
        title = {
            Text(
                text = if (initialProvider != null) initialProvider.displayName else "Add Provider",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            com.example.ui.components.DialogWindowFocusGuard(onRegisterCleaner = { clearDialogIme = it })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderPresetType.values().forEach { preset ->
                        MinimalChip(
                            label = preset.displayName,
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                if (initialProvider == null) {
                                    displayName = preset.displayName
                                    baseUrl = preset.defaultBaseUrl
                                    defaultChatModel = when (preset) {
                                        ProviderPresetType.OPENAI -> "gpt-4o"
                                        ProviderPresetType.ANTHROPIC -> "claude-3-5-sonnet-20241022"
                                        ProviderPresetType.GEMINI -> "gemini-1.5-pro"
                                        ProviderPresetType.OMNIROUTE -> "omniroute/auto-combo"
                                        else -> "default-model"
                                    }
                                }
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = defaultChatModel,
                    onValueChange = { defaultChatModel = it },
                    label = { Text("Default Model ID") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rawApiKey,
                    onValueChange = { rawApiKey = it },
                    label = { Text(if (maskedKey != null) "API Key (leave empty to keep)" else "API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                    MinimalToggle(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            MinimalButton(
                text = "Save",
                onClick = {
                    clearDialogIme?.invoke()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onSave(
                        initialProvider?.id,
                        selectedPreset,
                        displayName,
                        baseUrl,
                        rawApiKey.takeIf { it.isNotBlank() },
                        capabilities,
                        isEnabled,
                        defaultChatModel,
                        initialProvider?.defaultImageModelId ?: "",
                        initialProvider?.defaultVideoModelId ?: ""
                    )
                },
                enabled = displayName.isNotBlank() && baseUrl.isNotBlank(),
                filled = true
            )
        },
        dismissButton = {
            TextButton(onClick = {
                clearDialogIme?.invoke()
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TinkVaultDetailsDialog(
    status: com.example.data.security.VaultSecurityStatus,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Keystore Vault", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Google Tink AES-256-GCM AEAD",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Master Key: ${status.masterKeyUri}\nEncrypted Keys: ${status.encryptedKeyCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            MinimalButton(text = "Done", onClick = onDismiss, filled = true)
        }
    )
}

@Composable
private fun OmniRouteGatewayCombosCard(
    isListening: Boolean,
    port: Int,
    combos: List<OmniRouteComboDefinition>,
    videoModelsCount: Int,
    onNewCombo: () -> Unit,
    onEditCombo: (OmniRouteComboDefinition) -> Unit,
    onDeleteCombo: (String) -> Unit,
    onAddCustomModel: () -> Unit
) {
    var selectedModalityTab by rememberSaveable { mutableStateOf("ALL") }
    val filteredCombos = remember(combos, selectedModalityTab) {
        if (selectedModalityTab == "ALL") combos
        else combos.filter { it.modality.equals(selectedModalityTab, ignoreCase = true) }
    }

    MinimalCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("omniroute_gateway_combos_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    MinimalStatusDot(isConnected = isListening)
                    Column {
                        Text(
                            text = "OmniRoute Gateway · Video & Custom Combos",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "http://localhost:$port/v1 · ${combos.size} combos · $videoModelsCount video models",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ALL", "VIDEO", "CHAT", "IMAGE").forEach { tab ->
                    MinimalChip(
                        label = tab,
                        selected = selectedModalityTab == tab,
                        onClick = { selectedModalityTab = tab },
                        testTag = "omniroute_combo_filter_$tab"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                MinimalButton(
                    text = "+ New Combo",
                    onClick = onNewCombo,
                    filled = true,
                    testTag = "omniroute_new_combo_button"
                )
                MinimalButton(
                    text = "+ Custom Model",
                    onClick = onAddCustomModel,
                    filled = false,
                    testTag = "omniroute_add_custom_model_button"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredCombos.forEach { combo ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditCombo(combo) }
                            .testTag("omniroute_combo_item_${combo.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = combo.displayName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (combo.modality.uppercase()) {
                                            "VIDEO" -> SoftIndigo.copy(alpha = 0.14f)
                                            "IMAGE" -> EmeraldSuccess.copy(alpha = 0.14f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = "${combo.modality} · ${combo.strategy}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (combo.modality.uppercase()) {
                                                "VIDEO" -> SoftIndigo
                                                "IMAGE" -> EmeraldSuccess
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${combo.id} → ${combo.targetModels.joinToString(" → ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { onEditCombo(combo) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = LucideIcons.Edit,
                                        contentDescription = "Edit Combo",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (!combo.isBuiltIn) {
                                    IconButton(
                                        onClick = { onDeleteCombo(combo.id) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("delete_combo_${combo.id}")
                                    ) {
                                        Icon(
                                            imageVector = LucideIcons.Trash,
                                            contentDescription = "Delete Combo",
                                            tint = CoralError,
                                            modifier = Modifier.size(14.dp)
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

@Composable
internal fun OmniRouteComboBuilderDialog(
    initialCombo: OmniRouteComboDefinition?,
    defaultModality: String = "VIDEO",
    availableModels: List<com.example.data.local.ModelDescriptorEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        rawId: String,
        displayName: String,
        modality: String,
        strategy: String,
        targetModels: List<String>,
        description: String
    ) -> Unit
) {
    var rawId by rememberSaveable {
        mutableStateOf(initialCombo?.id ?: "omniroute/custom-video-combo")
    }
    var displayName by rememberSaveable {
        mutableStateOf(initialCombo?.displayName ?: "OmniRoute Custom Video Combo")
    }
    var modality by rememberSaveable {
        mutableStateOf(initialCombo?.modality ?: defaultModality)
    }
    var strategy by rememberSaveable {
        mutableStateOf(initialCombo?.strategy ?: "FALLBACK_CHAIN")
    }
    var targetModelsText by rememberSaveable {
        mutableStateOf(
            initialCombo?.targetModels?.joinToString(", ")
                ?: if (defaultModality == "VIDEO") {
                    "omniroute/wan-2.1-t2v, omniroute/kling-v1.6-pro, omniroute/luma-ray-2, omniroute/veo-2-cinema"
                } else {
                    "ddgw/gpt-4o-mini, ddgw/claude-3-haiku, ddgw/llama-3.3-70b"
                }
        )
    }
    var description by rememberSaveable {
        mutableStateOf(
            initialCombo?.description
                ?: "Custom OmniRoute multi-model routing combo"
        )
    }

    val suggestedTargets = remember(modality) {
        when (modality.uppercase()) {
            "VIDEO" -> listOf(
                "omniroute/wan-2.1-t2v",
                "omniroute/kling-v1.6-pro",
                "omniroute/luma-ray-2",
                "omniroute/veo-2-cinema",
                "omniroute/hunyuan-video",
                "omniroute/seedance-v1",
                "omniroute/gen4-turbo"
            )
            "IMAGE" -> listOf(
                "omniroute/flux-schnell",
                "omniroute/sdxl-lightning"
            )
            else -> listOf(
                "ddgw/gpt-4o-mini",
                "ddgw/claude-3-haiku",
                "ddgw/llama-3.3-70b",
                "ddgw/mistral-small-24b"
            )
        }
    }

    var clearDialogIme by remember { mutableStateOf<(() -> Unit)?>(null) }

    AlertDialog(
        onDismissRequest = {
            clearDialogIme?.invoke()
            onDismiss()
        },
        title = {
            Text(
                text = if (initialCombo != null) "Edit OmniRoute Combo" else "New OmniRoute Combo",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            com.example.ui.components.DialogWindowFocusGuard(onRegisterCleaner = { clearDialogIme = it })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Modality",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("VIDEO", "CHAT", "IMAGE").forEach { mod ->
                        MinimalChip(
                            label = mod,
                            selected = modality == mod,
                            onClick = {
                                modality = mod
                                if (initialCombo == null) {
                                    when (mod) {
                                        "VIDEO" -> {
                                            if (rawId.contains("chat") || rawId.contains("image")) {
                                                rawId = "omniroute/custom-video-combo"
                                            }
                                            targetModelsText = "omniroute/wan-2.1-t2v, omniroute/kling-v1.6-pro, omniroute/luma-ray-2"
                                        }
                                        "IMAGE" -> {
                                            rawId = "omniroute/custom-image-combo"
                                            targetModelsText = "omniroute/flux-schnell, omniroute/sdxl-lightning"
                                        }
                                        "CHAT" -> {
                                            rawId = "omniroute/custom-chat-combo"
                                            targetModelsText = "ddgw/gpt-4o-mini, ddgw/claude-3-haiku, ddgw/llama-3.3-70b"
                                        }
                                    }
                                }
                            },
                            testTag = "combo_modality_chip_$mod"
                        )
                    }
                }

                Text(
                    text = "Routing Strategy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("FALLBACK_CHAIN", "ROUND_ROBIN", "LATENCY_FIRST").forEach { strat ->
                        MinimalChip(
                            label = strat,
                            selected = strategy == strat,
                            onClick = { strategy = strat },
                            testTag = "combo_strategy_chip_$strat"
                        )
                    }
                }

                OutlinedTextField(
                    value = rawId,
                    onValueChange = { rawId = it },
                    label = { Text("Combo ID (e.g. omniroute/my-video-combo)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("combo_id_input")
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("combo_display_name_input")
                )

                OutlinedTextField(
                    value = targetModelsText,
                    onValueChange = { targetModelsText = it },
                    label = { Text("Target Models (comma-separated)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("combo_targets_input")
                )

                Text(
                    text = "Tap to append target model:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestedTargets.forEach { target ->
                        MinimalChip(
                            label = "+ ${target.substringAfter("/")}",
                            selected = targetModelsText.contains(target),
                            onClick = {
                                val existing = targetModelsText.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .toMutableList()
                                if (target in existing) {
                                    existing.remove(target)
                                } else {
                                    existing.add(target)
                                }
                                targetModelsText = existing.joinToString(", ")
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            MinimalButton(
                text = "Save Combo",
                onClick = {
                    clearDialogIme?.invoke()
                    val targets = targetModelsText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onSave(
                        rawId.trim(),
                        displayName.trim().ifBlank { rawId.trim() },
                        modality,
                        strategy,
                        targets,
                        description.trim()
                    )
                },
                enabled = rawId.isNotBlank(),
                filled = true,
                testTag = "save_combo_confirm_button"
            )
        },
        dismissButton = {
            TextButton(onClick = {
                clearDialogIme?.invoke()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun AddCustomModelDialog(
    providers: List<ProviderInstanceEntity>,
    defaultProviderId: String = "provider_omniroute_default",
    defaultModality: String = "VIDEO",
    onDismiss: () -> Unit,
    onSave: (
        providerId: String,
        rawModelId: String,
        displayName: String,
        modality: String,
        isCombo: Boolean
    ) -> Unit
) {
    var selectedProviderId by rememberSaveable {
        mutableStateOf(
            providers.firstOrNull { it.id == defaultProviderId }?.id
                ?: providers.firstOrNull()?.id
                ?: "provider_omniroute_default"
        )
    }
    var rawModelId by rememberSaveable { mutableStateOf("omniroute/wan-2.1-t2v") }
    var displayName by rememberSaveable { mutableStateOf("OmniRoute Wan 2.1 Text-to-Video") }
    var modality by rememberSaveable { mutableStateOf(defaultModality) }
    var isCombo by rememberSaveable { mutableStateOf(false) }

    var clearDialogIme by remember { mutableStateOf<(() -> Unit)?>(null) }

    AlertDialog(
        onDismissRequest = {
            clearDialogIme?.invoke()
            onDismiss()
        },
        title = {
            Text("Add Custom Model", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            com.example.ui.components.DialogWindowFocusGuard(onRegisterCleaner = { clearDialogIme = it })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Provider",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    providers.forEach { prov ->
                        MinimalChip(
                            label = prov.displayName,
                            selected = selectedProviderId == prov.id,
                            onClick = { selectedProviderId = prov.id }
                        )
                    }
                }

                Text(
                    text = "Modality",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("VIDEO", "CHAT", "IMAGE").forEach { mod ->
                        MinimalChip(
                            label = mod,
                            selected = modality == mod,
                            onClick = { modality = mod }
                        )
                    }
                }

                OutlinedTextField(
                    value = rawModelId,
                    onValueChange = { rawModelId = it },
                    label = { Text("Model ID (e.g. omniroute/kling-v1.6-pro)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_model_id_input")
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_model_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mark as Combo / Router Model", style = MaterialTheme.typography.bodyMedium)
                    MinimalToggle(
                        checked = isCombo,
                        onCheckedChange = { isCombo = it }
                    )
                }
            }
        },
        confirmButton = {
            MinimalButton(
                text = "Add Model",
                onClick = {
                    clearDialogIme?.invoke()
                    onSave(
                        selectedProviderId,
                        rawModelId.trim(),
                        displayName.trim().ifBlank { rawModelId.trim() },
                        modality,
                        isCombo
                    )
                },
                enabled = rawModelId.isNotBlank(),
                filled = true,
                testTag = "save_custom_model_confirm_button"
            )
        },
        dismissButton = {
            TextButton(onClick = {
                clearDialogIme?.invoke()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

