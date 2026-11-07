package com.example.ui.components

import com.example.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.example.data.provider.LatencyBenchmarkSample
import com.example.data.provider.ProviderSpeedRank
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ModelDescriptorEntity
import com.example.data.local.PromptTemplateEntity
import com.example.data.local.ProviderInstanceEntity
import com.example.data.local.ProviderPresetType
import com.example.data.local.UsageRecordEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError

import com.example.ui.theme.EmeraldConnected
import com.example.ui.viewmodel.CompareTargetModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareTargetsBottomSheet(
    providers: List<ProviderInstanceEntity>,
    models: List<ModelDescriptorEntity>,
    selectedTargets: List<CompareTargetModel>,
    onToggleTarget: (ModelDescriptorEntity, ProviderInstanceEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val enabledProviders = remember(providers) {
        providers.filter { it.isEnabled }.sortedWith(
            compareBy<ProviderInstanceEntity> { it.presetEnum() != ProviderPresetType.OMNIROUTE }
                .thenBy { it.createdAt }
        )
    }

    val selectedKeys = remember(selectedTargets) {
        selectedTargets.map { it.key }.toSet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("compare_targets_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Compare Mode Models (2–3)",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Selected: ${selectedTargets.size} of 3 • Prompts run side-by-side concurrently",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("compare_sheet_done_button")
                ) {
                    Text("Done")
                }
            }

            // Active Compare Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedTargets.forEachIndexed { idx, target ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BadgePill(text = "#${idx + 1} ${target.providerBadge}", color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = target.displayName,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter models across OmniRoute, Anthropic, Gemini, OpenAI…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                enabledProviders.forEach { provider ->
                    val q = searchQuery.trim().lowercase()
                    val provModels = models.filter { m ->
                        m.providerInstanceId == provider.id &&
                            (q.isEmpty() ||
                                m.displayName.lowercase().contains(q) ||
                                m.modelId.lowercase().contains(q) ||
                                provider.displayName.lowercase().contains(q))
                    }
                    if (provModels.isNotEmpty()) {
                        item(key = "cmp_header_${provider.id}") {
                            Row(
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BadgePill(
                                    text = provider.presetEnum().badgeShort,
                                    color = if (provider.presetEnum() == ProviderPresetType.OMNIROUTE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }

                        items(provModels, key = { "cmp_${it.id}" }) { model ->
                            val isSelected = "${model.providerInstanceId}:${model.modelId}" in selectedKeys
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleTarget(model, provider) }
                                    .testTag("compare_model_option_${model.modelId}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.displayName,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = model.modelId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = if (isSelected) "Selected" else "Not selected",
                                        tint = if (isSelected) EmeraldConnected else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryBottomSheet(
    templates: List<PromptTemplateEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onApplyTemplate: (PromptTemplateEntity) -> Unit,
    onToggleFavorite: (PromptTemplateEntity) -> Unit,
    onSaveTemplate: (
        existingId: String?,
        title: String,
        category: String,
        promptText: String,
        systemPromptOverride: String
    ) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    val categories = listOf("All", "Favorites", "Coding", "Architecture", "Vision", "Custom")

    val filtered = remember(templates, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        templates.filter { tpl ->
            val matchesCat = when (selectedCategory) {
                "All" -> true
                "Favorites" -> tpl.isFavorite
                else -> tpl.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesQuery = q.isEmpty() ||
                tpl.title.lowercase().contains(q) ||
                tpl.promptText.lowercase().contains(q)
            matchesCat && matchesQuery
        }
    }

    if (showCreateDialog) {
        var title by rememberSaveable { mutableStateOf("") }
        var category by rememberSaveable { mutableStateOf("Coding") }
        var promptText by rememberSaveable { mutableStateOf("") }
        var sysOverride by rememberSaveable { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Prompt Template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_dialog_title_input")
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (Coding, Architecture, Vision, Custom)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Prompt Content") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_dialog_content_input")
                    )
                    OutlinedTextField(
                        value = sysOverride,
                        onValueChange = { sysOverride = it },
                        label = { Text("Optional System Prompt Override") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveTemplate(null, title, category, promptText, sysOverride)
                        showCreateDialog = false
                    },
                    enabled = title.isNotBlank() && promptText.isNotBlank(),
                    modifier = Modifier.testTag("prompt_dialog_save_button")
                ) {
                    Text("Save Template")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("prompt_library_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Prompt Library",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Tap any prompt to insert into Chat or Compare Mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("new_prompt_template_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Prompt")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search saved prompts…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prompt_library_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { tpl ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onApplyTemplate(tpl) }
                            .testTag("prompt_template_card_${tpl.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    BadgePill(text = tpl.category.uppercase(), color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = tpl.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { onToggleFavorite(tpl) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (tpl.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Favorite prompt",
                                            tint = if (tpl.isFavorite) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteTemplate(tpl.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete prompt",
                                            tint = CoralError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = tpl.promptText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tpl.systemPromptOverride.isNotBlank()) {
                                Text(
                                    text = "System Role: ${tpl.systemPromptOverride}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldConnected,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

private data class ModelSpendBreakdownItem(
    val modelId: String,
    val requestsCount: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val costUsd: Double
)

private data class ProviderSpendBreakdownItem(
    val providerKey: String,
    val providerDisplayName: String,
    val badgeShort: String,
    val requestsCount: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val costUsd: Double,
    val shareOfTotalCost: Float,
    val models: List<ModelSpendBreakdownItem>
)

private fun formatUsageCurrency(
    costUsd: Double,
    spendCurrency: String,
    usdToInrRate: Double
): String {
    return if (spendCurrency.equals("INR", ignoreCase = true)) {
        val inr = costUsd * usdToInrRate
        val formatted = when {
            inr == 0.0 -> "0.00"
            inr >= 100.0 -> String.format(Locale.US, "%,.2f", inr)
            inr >= 1.0 -> String.format(Locale.US, "%,.3f", inr)
            else -> String.format(Locale.US, "%,.4f", inr)
        }
        "₹$formatted"
    } else {
        val formatted = when {
            costUsd == 0.0 -> "0.0000"
            costUsd >= 100.0 -> String.format(Locale.US, "%,.2f", costUsd)
            costUsd >= 1.0 -> String.format(Locale.US, "%,.3f", costUsd)
            else -> String.format(Locale.US, "%,.4f", costUsd)
        }
        "$$formatted"
    }
}

@Composable
fun UsageAnalyticsScreen(
    usageRecords: List<UsageRecordEntity>,
    providers: List<ProviderInstanceEntity>,
    latencySamples: List<LatencyBenchmarkSample> = emptyList(),
    fastestProvider: ProviderSpeedRank? = null,
    speedRanks: List<ProviderSpeedRank> = emptyList(),
    isProbingLatency: Boolean = false,
    autoProbeIntervalSeconds: Int = 0,
    spendCurrency: String = "INR",
    usdToInrRate: Double = 84.0,
    onRunProbe: () -> Unit = {},
    onSetAutoProbeInterval: (Int) -> Unit = {},
    onClearLatencyHistory: () -> Unit = {},
    onSelectFastestModel: (providerId: String, modelId: String) -> Unit = { _, _ -> },
    onClearUsage: () -> Unit,
    onOpenPromptLibrary: () -> Unit,
    onSetSpendCurrency: (String) -> Unit = {}
) {
    val totalRequests = usageRecords.size
    val totalPromptTokens = remember(usageRecords) {
        usageRecords.sumOf { it.promptTokens.toLong() }
    }
    val totalCompletionTokens = remember(usageRecords) {
        usageRecords.sumOf { it.completionTokens.toLong() }
    }
    val totalTokens = totalPromptTokens + totalCompletionTokens
    val totalCostUsd = remember(usageRecords) {
        usageRecords.sumOf { it.estimatedCostUsd }
    }

    val formattedRequests = remember(totalRequests) {
        String.format(Locale.US, "%,d", totalRequests)
    }
    val formattedTokens = remember(totalTokens) {
        String.format(Locale.US, "%,d", totalTokens)
    }
    val formattedPromptTokens = remember(totalPromptTokens) {
        String.format(Locale.US, "%,d", totalPromptTokens)
    }
    val formattedCompletionTokens = remember(totalCompletionTokens) {
        String.format(Locale.US, "%,d", totalCompletionTokens)
    }
    val formattedCost = remember(totalCostUsd, spendCurrency, usdToInrRate) {
        formatUsageCurrency(totalCostUsd, spendCurrency, usdToInrRate)
    }
    val alternateCurrency = if (spendCurrency == "INR") "USD" else "INR"
    val formattedAlternateCost = remember(totalCostUsd, alternateCurrency, usdToInrRate) {
        formatUsageCurrency(totalCostUsd, alternateCurrency, usdToInrRate)
    }

    val providerSpendBreakdown = remember(usageRecords, providers, totalCostUsd) {
        val providerById = providers.associateBy { it.id }
        val groupedByProvider = usageRecords.groupBy { record ->
            record.providerInstanceId.ifBlank { record.providerDisplayName.ifBlank { "unknown" } }
        }

        val activeItems = groupedByProvider.map { (provKey, records) ->
            val matchedProvider = providerById[provKey]
                ?: providers.firstOrNull { it.displayName.equals(records.first().providerDisplayName, ignoreCase = true) }
            val displayName = matchedProvider?.displayName?.takeIf { it.isNotBlank() }
                ?: records.firstOrNull { it.providerDisplayName.isNotBlank() }?.providerDisplayName
                ?: provKey
            val badge = matchedProvider?.presetEnum()?.badgeShort
                ?: displayName.take(4).uppercase()
            val pTokens = records.sumOf { it.promptTokens.toLong() }
            val cTokens = records.sumOf { it.completionTokens.toLong() }
            val tTokens = pTokens + cTokens
            val cost = records.sumOf { it.estimatedCostUsd }
            val share = if (totalCostUsd > 0.0) {
                (cost / totalCostUsd).toFloat().coerceIn(0f, 1f)
            } else if (totalTokens > 0L) {
                (tTokens.toDouble() / totalTokens.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            val modelBreakdown = records
                .groupBy { it.modelId.ifBlank { "default" } }
                .map { (mId, mRecords) ->
                    val mp = mRecords.sumOf { it.promptTokens.toLong() }
                    val mc = mRecords.sumOf { it.completionTokens.toLong() }
                    ModelSpendBreakdownItem(
                        modelId = mId,
                        requestsCount = mRecords.size,
                        promptTokens = mp,
                        completionTokens = mc,
                        totalTokens = mp + mc,
                        costUsd = mRecords.sumOf { it.estimatedCostUsd }
                    )
                }
                .sortedWith(
                    compareByDescending<ModelSpendBreakdownItem> { it.costUsd }
                        .thenByDescending { it.totalTokens }
                )

            ProviderSpendBreakdownItem(
                providerKey = provKey,
                providerDisplayName = displayName,
                badgeShort = badge,
                requestsCount = records.size,
                promptTokens = pTokens,
                completionTokens = cTokens,
                totalTokens = tTokens,
                costUsd = cost,
                shareOfTotalCost = share,
                models = modelBreakdown
            )
        }

        val representedIds = activeItems.map { it.providerKey }.toSet()
        val representedNames = activeItems.map { it.providerDisplayName.lowercase() }.toSet()
        val zeroSpendProviders = providers
            .filter { prov ->
                prov.id !in representedIds && prov.displayName.lowercase() !in representedNames
            }
            .map { prov ->
                ProviderSpendBreakdownItem(
                    providerKey = prov.id,
                    providerDisplayName = prov.displayName,
                    badgeShort = prov.presetEnum().badgeShort,
                    requestsCount = 0,
                    promptTokens = 0L,
                    completionTokens = 0L,
                    totalTokens = 0L,
                    costUsd = 0.0,
                    shareOfTotalCost = 0f,
                    models = emptyList()
                )
            }

        activeItems.sortedWith(
            compareByDescending<ProviderSpendBreakdownItem> { it.costUsd }
                .thenByDescending { it.totalTokens }
                .thenByDescending { it.requestsCount }
        ) + zeroSpendProviders
    }

    val chartPoints = remember(usageRecords, latencySamples) {
        when {
            usageRecords.size >= 2 -> usageRecords.take(20).reversed().map {
                (it.promptTokens + it.completionTokens).toFloat().coerceAtLeast(10f)
            }
            latencySamples.size >= 2 -> latencySamples.takeLast(20).map {
                it.latencyMs.toFloat().coerceAtLeast(10f)
            }
            else -> listOf(120f, 180f, 145f, 230f, 195f, 310f, 260f, 290f)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("usage_analytics_screen")
    ) {
        // Header row with Currency toggle & Probe / Reset actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Usage",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinimalChip(
                        label = if (spendCurrency == "INR") "₹ INR" else "$ USD",
                        selected = true,
                        onClick = {
                            onSetSpendCurrency(if (spendCurrency == "INR") "USD" else "INR")
                        },
                        testTag = "spend_currency_switcher_card"
                    )
                    MinimalChip(
                        label = if (isProbingLatency) "Probing..." else "Probe",
                        selected = isProbingLatency,
                        onClick = onRunProbe,
                        icon = LucideIcons.Activity,
                        testTag = "run_latency_probe_button"
                    )
                    if (usageRecords.isNotEmpty()) {
                        MinimalChip(
                            label = "Reset",
                            selected = false,
                            onClick = onClearUsage,
                            testTag = "clear_usage_button"
                        )
                    }
                }
            }
        }

        // Spacious KPI Cards: Full Numbers Always Visible (Never Truncated)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UsageKpiCard(
                        label = "Requests",
                        value = formattedRequests,
                        subtitle = "${providerSpendBreakdown.count { it.requestsCount > 0 }} active providers",
                        modifier = Modifier
                            .weight(0.38f)
                            .testTag("usage_kpi_requests_card")
                    )
                    UsageKpiCard(
                        label = "Tokens",
                        value = formattedTokens,
                        subtitle = "In: $formattedPromptTokens · Out: $formattedCompletionTokens",
                        modifier = Modifier
                            .weight(0.62f)
                            .testTag("usage_kpi_tokens_card")
                    )
                }

                UsageKpiCard(
                    label = "Estimated Cost",
                    value = formattedCost,
                    subtitle = "≈ $formattedAlternateCost $alternateCurrency (estimated total across all providers)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("usage_kpi_cost_card")
                )
            }
        }

        // Spend by Provider Breakdown Section
        item {
            MinimalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_spend_breakdown_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Spend by Provider",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Exact token usage and estimated spend for each AI provider",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BadgePill(
                            text = "${providerSpendBreakdown.count { it.requestsCount > 0 }}/${providerSpendBreakdown.size} used",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (providerSpendBreakdown.isEmpty()) {
                        Text(
                            text = "No provider spend recorded yet. Send a chat message or generate media to track per-provider spend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            providerSpendBreakdown.forEach { item ->
                                ProviderSpendRowCard(
                                    item = item,
                                    spendCurrency = spendCurrency,
                                    usdToInrRate = usdToInrRate
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1 Minimal Line Chart
        item {
            MinimalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fastest_provider_hero_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (usageRecords.size >= 2) "Token activity" else "Response latency (ms)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = fastestProvider?.let {
                                    "Fastest: ${it.providerName} · ${it.latestLatencyMs}ms"
                                } ?: "Real-time telemetry across active providers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (fastestProvider != null) {
                            MinimalChip(
                                label = "Use fastest",
                                selected = false,
                                onClick = {
                                    onSelectFastestModel(fastestProvider.providerId, fastestProvider.modelId)
                                },
                                testTag = "switch_to_fastest_model_button"
                            )
                        }
                    }

                    val gridColor = MaterialTheme.colorScheme.outline
                    val accentColor = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val maxVal = (chartPoints.maxOrNull() ?: 1f).coerceAtLeast(1f)
                        val minVal = (chartPoints.minOrNull() ?: 0f).coerceAtMost(maxVal - 1f)
                        val range = (maxVal - minVal).coerceAtLeast(1f)

                        // Subtle horizontal baseline grid
                        listOf(0.2f, 0.5f, 0.85f).forEach { frac ->
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, h * frac),
                                end = Offset(w, h * frac),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val stepX = if (chartPoints.size > 1) w / (chartPoints.size - 1) else w
                        val coords = chartPoints.mapIndexed { idx, value ->
                            val norm = (value - minVal) / range
                            val x = idx * stepX
                            val y = h - (norm * (h * 0.75f)) - (h * 0.1f)
                            Offset(x, y)
                        }

                        if (coords.size >= 2) {
                            val linePath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(coords.first().x, coords.first().y)
                                for (i in 1 until coords.size) {
                                    val prev = coords[i - 1]
                                    val curr = coords[i]
                                    val midX = (prev.x + curr.x) / 2f
                                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                                }
                            }

                            val fillPath = androidx.compose.ui.graphics.Path().apply {
                                addPath(linePath)
                                lineTo(coords.last().x, h)
                                lineTo(coords.first().x, h)
                                close()
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.22f),
                                        accentColor.copy(alpha = 0.0f)
                                    )
                                )
                            )

                            drawPath(
                                path = linePath,
                                color = accentColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 2.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )

                            coords.forEach { pt ->
                                drawCircle(
                                    color = accentColor,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderSpendRowCard(
    item: ProviderSpendBreakdownItem,
    spendCurrency: String,
    usdToInrRate: Double
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val primaryCostText = remember(item.costUsd, spendCurrency, usdToInrRate) {
        formatUsageCurrency(item.costUsd, spendCurrency, usdToInrRate)
    }
    val secondaryCurrency = if (spendCurrency == "INR") "USD" else "INR"
    val secondaryCostText = remember(item.costUsd, secondaryCurrency, usdToInrRate) {
        formatUsageCurrency(item.costUsd, secondaryCurrency, usdToInrRate)
    }
    val sharePercentText = remember(item.shareOfTotalCost) {
        String.format(Locale.US, "%.1f%%", item.shareOfTotalCost * 100f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (item.requestsCount > 0) accentColor.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_spend_item_${item.providerKey}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = item.badgeShort,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = item.providerDisplayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = "${String.format(Locale.US, "%,d", item.requestsCount)} reqs · ${String.format(Locale.US, "%,d", item.totalTokens)} tokens (In: ${String.format(Locale.US, "%,d", item.promptTokens)} · Out: ${String.format(Locale.US, "%,d", item.completionTokens)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = primaryCostText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (item.costUsd > 0.0) EmeraldConnected else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "≈ $secondaryCostText · $sharePercentText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Proportional spend bar
            if (item.requestsCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxSize()
                    ) {}
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth(item.shareOfTotalCost.coerceIn(0.03f, 1f))
                            .height(5.dp)
                    ) {}
                }
            }

            // Per-model breakdown inside this provider
            if (item.models.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.models.forEach { mod ->
                        val modCostText = formatUsageCurrency(mod.costUsd, spendCurrency, usdToInrRate)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mod.modelId,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = "${String.format(Locale.US, "%,d", mod.requestsCount)} req · ${String.format(Locale.US, "%,d", mod.totalTokens)} tok · $modCostText",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageKpiCard(
    label: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val valueTextStyle = when {
        value.length <= 8 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        value.length <= 13 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    }

    MinimalCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = valueTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = true
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true
                )
            }
        }
    }
}

