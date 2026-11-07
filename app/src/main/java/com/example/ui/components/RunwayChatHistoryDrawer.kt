package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.local.ConversationEntity
import com.example.data.local.RunwayConversationMetaEntity
import com.example.data.local.RunwayDocumentEntity
import com.example.data.local.RunwayFolderEntity
import com.example.data.local.RunwayMessageNodeEntity
import com.example.ui.theme.CoralError
import com.example.ui.theme.Sage

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// STEP (3): CHAT HISTORY, FOLDERS, FULL-TEXT SEARCH & FILTERS, EXPORT & UNDO
// ============================================================================

enum class HistoryDateFilter(val label: String) {
    ALL("All dates"),
    TODAY("Today"),
    LAST_7_DAYS("7d"),
    LAST_30_DAYS("30d")
}

private val folderPaletteHex = listOf(
    "#6C7CFF",
    "#10B981",
    "#F59E0B",
    "#EC4899",
    "#3B82F6",
    "#A855F7"
)

fun parseHexColorSafe(hex: String, fallback: Color = Sage): Color {
    return runCatching {
        val cleaned = hex.trim().removePrefix("#")
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RunwayChatHistoryDrawerContent(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    searchQuery: String,
    selectedFolderId: String?,
    folders: List<RunwayFolderEntity>,
    conversationMetas: List<RunwayConversationMetaEntity>,
    allMessageNodes: List<RunwayMessageNodeEntity>,
    allDocuments: List<RunwayDocumentEntity>,
    hasPendingUndoDelete: Boolean,
    onSearchChange: (String) -> Unit,
    onSelectFolderFilter: (String?) -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onContinueLatestConversation: () -> Unit,
    onTogglePinConversation: (ConversationEntity) -> Unit,
    onRenameConversation: (ConversationEntity) -> Unit,
    onDuplicateConversation: (String) -> Unit,
    onMoveConversationToFolder: (conversationId: String, folderId: String?) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onUndoDeleteConversation: () -> Unit,
    onExportConversation: (conversationId: String, asJson: Boolean, includeAllBranches: Boolean) -> Unit,
    onSaveFolder: (existingId: String?, name: String, colorHex: String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onOpenConnectors: () -> Unit = {}
) {
    // Search filters state
    var dateFilter by rememberSaveable { mutableStateOf(HistoryDateFilter.ALL) }
    var selectedModelFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var filterHasImage by rememberSaveable { mutableStateOf(false) }
    var filterHasCode by rememberSaveable { mutableStateOf(false) }
    var filterHasDocument by rememberSaveable { mutableStateOf(false) }
    var showModelFilterSheet by remember { mutableStateOf(false) }

    // Action sheet & Folder dialog states
    var actionTargetConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var exportIncludeAllBranches by rememberSaveable { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<RunwayFolderEntity?>(null) }

    val metasByConvId = remember(conversationMetas) {
        conversationMetas.associateBy { it.conversationId }
    }
    val foldersById = remember(folders) {
        folders.associateBy { it.id }
    }
    val nodesByConvId = remember(allMessageNodes) {
        allMessageNodes.groupBy { it.conversationId }
    }
    val docCountByConvId = remember(allDocuments, conversationMetas) {
        val map = mutableMapOf<String, Int>()
        allDocuments.forEach { doc ->
            map[doc.conversationId] = (map[doc.conversationId] ?: 0) + 1
        }
        conversationMetas.forEach { meta ->
            val attachedCount = meta.attachedDocumentIds().size
            if (attachedCount > 0) {
                map[meta.conversationId] = maxOf(map[meta.conversationId] ?: 0, attachedCount)
            }
        }
        map
    }

    val distinctModels = remember(conversations) {
        conversations.map { it.modelId.substringAfterLast("/") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    // Filter and search across both titles AND message contents
    val nowMs = System.currentTimeMillis()
    val filteredConversationsWithSnippets = remember(
        conversations,
        searchQuery,
        selectedFolderId,
        dateFilter,
        selectedModelFilter,
        filterHasImage,
        filterHasCode,
        filterHasDocument,
        metasByConvId,
        nodesByConvId,
        docCountByConvId
    ) {
        val q = searchQuery.trim().lowercase()
        val startOfToday = getStartOfTodayMillis(nowMs)
        val cutoffMs = when (dateFilter) {
            HistoryDateFilter.ALL -> 0L
            HistoryDateFilter.TODAY -> startOfToday
            HistoryDateFilter.LAST_7_DAYS -> nowMs - 7L * 24L * 3600_000L
            HistoryDateFilter.LAST_30_DAYS -> nowMs - 30L * 24L * 3600_000L
        }

        conversations.mapNotNull { conv ->
            val meta = metasByConvId[conv.id]
            val convNodes = nodesByConvId[conv.id].orEmpty()

            // 1. Folder filter
            if (selectedFolderId != null && meta?.folderId != selectedFolderId) {
                return@mapNotNull null
            }

            // 2. Date range filter
            if (cutoffMs > 0L && conv.updatedAt < cutoffMs) {
                return@mapNotNull null
            }

            // 3. Model filter
            if (selectedModelFilter != null &&
                !conv.modelId.substringAfterLast("/").equals(selectedModelFilter, ignoreCase = true)
            ) {
                return@mapNotNull null
            }

            // 4. Has-image filter
            if (filterHasImage) {
                val hasImg = convNodes.any {
                    !it.attachmentUri.isNullOrBlank() || it.content.contains("![")
                }
                if (!hasImg) return@mapNotNull null
            }

            // 5. Has-code filter
            if (filterHasCode) {
                val hasCode = convNodes.any { it.content.contains("```") }
                if (!hasCode) return@mapNotNull null
            }

            // 6. Has-document filter
            if (filterHasDocument) {
                val hasDoc = (docCountByConvId[conv.id] ?: 0) > 0
                if (!hasDoc) return@mapNotNull null
            }

            // 7. Full-text query over title AND message contents
            if (q.isEmpty()) {
                conv to null
            } else {
                val titleMatches = conv.title.lowercase().contains(q)
                val matchingNode = convNodes.firstOrNull { it.content.lowercase().contains(q) }
                if (!titleMatches && matchingNode == null) {
                    null
                } else {
                    val snippet = matchingNode?.let { extractSnippetAroundQuery(it.content, q) }
                    conv to snippet
                }
            }
        }
    }

    // Group into Pinned, Today, Yesterday, Earlier
    val groupedSections = remember(filteredConversationsWithSnippets, nowMs) {
        val startOfToday = getStartOfTodayMillis(nowMs)
        val startOfYesterday = startOfToday - 24L * 3600_000L

        val pinned = mutableListOf<Pair<ConversationEntity, String?>>()
        val today = mutableListOf<Pair<ConversationEntity, String?>>()
        val yesterday = mutableListOf<Pair<ConversationEntity, String?>>()
        val earlier = mutableListOf<Pair<ConversationEntity, String?>>()

        filteredConversationsWithSnippets.forEach { pair ->
            val conv = pair.first
            when {
                conv.isPinned -> pinned.add(pair)
                conv.updatedAt >= startOfToday -> today.add(pair)
                conv.updatedAt >= startOfYesterday -> yesterday.add(pair)
                else -> earlier.add(pair)
            }
        }

        buildList {
            if (pinned.isNotEmpty()) add("Pinned" to pinned)
            if (today.isNotEmpty()) add("Today" to today)
            if (yesterday.isNotEmpty()) add("Yesterday" to yesterday)
            if (earlier.isNotEmpty()) add("Earlier" to earlier)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header: Runway Logo + Wordmark | Continue & New buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RunwayLogoIcon(size = 28.dp)
                RunwayWordmark()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (conversations.isNotEmpty()) {
                    MinimalChip(
                        label = "Continue",
                        onClick = onContinueLatestConversation,
                        modifier = Modifier.testTag("drawer_continue_chat_button")
                    )
                }
                MinimalButton(
                    text = "New",
                    icon = LucideIcons.Plus,
                    onClick = onNewConversation,
                    modifier = Modifier.testTag("drawer_new_chat_button")
                )
            }
        }

        // Main menu quick link to Connectors screen
        Surface(
            onClick = onOpenConnectors,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_open_connectors_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.Plug,
                        contentDescription = "Connectors",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Connectors",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = LucideIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 2. Full-text Search Field (titles + messages)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    "Search titles & messages…",
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
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.X,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_full_text_search_input")
        )

        // 3. Search Filter Chips Row (Date range, Model, Has-image, Has-code, Has-document)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinimalChip(
                label = dateFilter.label,
                selected = dateFilter != HistoryDateFilter.ALL,
                onClick = {
                    val entries = HistoryDateFilter.entries
                    dateFilter = entries[(dateFilter.ordinal + 1) % entries.size]
                },
                modifier = Modifier.testTag("history_filter_date_chip")
            )
            MinimalChip(
                label = selectedModelFilter ?: "All models",
                selected = selectedModelFilter != null,
                onClick = {
                    if (distinctModels.isEmpty()) {
                        selectedModelFilter = null
                    } else {
                        showModelFilterSheet = true
                    }
                },
                modifier = Modifier.testTag("history_filter_model_chip")
            )
            MinimalChip(
                label = "Has code",
                selected = filterHasCode,
                onClick = { filterHasCode = !filterHasCode },
                modifier = Modifier.testTag("history_filter_has_code_chip")
            )
            MinimalChip(
                label = "Has image",
                selected = filterHasImage,
                onClick = { filterHasImage = !filterHasImage },
                modifier = Modifier.testTag("history_filter_has_image_chip")
            )
            MinimalChip(
                label = "Has doc",
                selected = filterHasDocument,
                onClick = { filterHasDocument = !filterHasDocument },
                modifier = Modifier.testTag("history_filter_has_doc_chip")
            )
        }

        // 4. Folders Bar (All + Folders with Color Dots + "+ Folder")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinimalChip(
                label = "All (${conversations.size})",
                selected = selectedFolderId == null,
                onClick = { onSelectFolderFilter(null) },
                modifier = Modifier.testTag("history_folder_all_chip")
            )

            folders.forEach { folder ->
                val isSelected = selectedFolderId == folder.id
                val count = conversationMetas.count { it.folderId == folder.id }
                val dotColor = parseHexColorSafe(folder.colorHex)

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(
                            onClick = {
                                onSelectFolderFilter(if (isSelected) null else folder.id)
                            },
                            onLongClick = {
                                editingFolder = folder
                                showFolderDialog = true
                            }
                        )
                        .testTag("history_folder_chip_${folder.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = if (count > 0) "${folder.name} ($count)" else folder.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = LucideIcons.Edit2,
                                contentDescription = "Edit folder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(11.dp)
                                    .clickable {
                                        editingFolder = folder
                                        showFolderDialog = true
                                    }
                            )
                        }
                    }
                }
            }

            MinimalChip(
                label = "+ Folder",
                selected = false,
                onClick = {
                    editingFolder = null
                    showFolderDialog = true
                },
                modifier = Modifier.testTag("history_new_folder_chip")
            )
        }

        // 5. Inline Undo bar when a conversation was just deleted
        AnimatedVisibility(
            visible = hasPendingUndoDelete,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_undo_delete_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Conversation deleted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onUndoDeleteConversation)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("drawer_undo_delete_button")
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // 6. Grouped Conversation List (Pinned / Today / Yesterday / Earlier)
        if (groupedSections.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank() || selectedFolderId != null) {
                        "No conversations match your search or filters"
                    } else {
                        "No conversations yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_conversations_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groupedSections.forEach { (sectionTitle, sectionItems) ->
                    item(key = "section_$sectionTitle") {
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (sectionTitle == "Pinned") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }

                    items(sectionItems, key = { it.first.id }) { (conv, matchedSnippet) ->
                        val isSelected = conv.id == activeConversationId
                        val folder = metasByConvId[conv.id]?.folderId?.let { foldersById[it] }

                        HistoryConversationRow(
                            conversation = conv,
                            folder = folder,
                            isSelected = isSelected,
                            searchQuery = searchQuery,
                            matchedSnippet = matchedSnippet,
                            onSelect = { onSelectConversation(conv.id) },
                            onOpenActions = { actionTargetConversation = conv },
                            onSwipePin = { onTogglePinConversation(conv) },
                            onSwipeDelete = { onDeleteConversation(conv.id) },
                            onRenameQuick = { onRenameConversation(conv) },
                            onDeleteQuick = { onDeleteConversation(conv.id) }
                        )
                    }
                }
            }
        }
    }

    // Conversation Actions Bottom Sheet (Pin, Rename, Move to folder, Duplicate, Export MD/JSON with branch toggle, Delete)
    actionTargetConversation?.let { targetConv ->
        val currentFolderId = metasByConvId[targetConv.id]?.folderId
        MinimalSheet(
            onDismiss = { actionTargetConversation = null }
        ) {
            Text(
                text = targetConv.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${targetConv.modelId.substringAfterLast("/")} · ${formatHistoryDate(targetConv.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Folder assignment pills
            MinimalSectionHeader(title = "Folder")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MinimalChip(
                    label = "None",
                    selected = currentFolderId == null,
                    onClick = {
                        onMoveConversationToFolder(targetConv.id, null)
                        actionTargetConversation = null
                    }
                )
                folders.forEach { folder ->
                    val selected = currentFolderId == folder.id
                    Surface(
                        onClick = {
                            onMoveConversationToFolder(targetConv.id, folder.id)
                            actionTargetConversation = null
                        },
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColorSafe(folder.colorHex))
                            )
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Primary actions: Pin, Rename, Duplicate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MinimalButton(
                    text = if (targetConv.isPinned) "Unpin" else "Pin",
                    filled = false,
                    onClick = {
                        onTogglePinConversation(targetConv)
                        actionTargetConversation = null
                    },
                    modifier = Modifier.weight(1f)
                )
                MinimalButton(
                    text = "Rename",
                    icon = LucideIcons.Edit2,
                    filled = false,
                    onClick = {
                        val toRename = targetConv
                        actionTargetConversation = null
                        onRenameConversation(toRename)
                    },
                    modifier = Modifier.weight(1f)
                )
                MinimalButton(
                    text = "Duplicate",
                    icon = LucideIcons.Copy,
                    filled = false,
                    onClick = {
                        onDuplicateConversation(targetConv.id)
                        actionTargetConversation = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Export section (Active branch vs All branches)
            MinimalSectionHeader(title = "Export")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Include all branches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Export the full message tree instead of only the active branch",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MinimalToggle(
                    checked = exportIncludeAllBranches,
                    onCheckedChange = { exportIncludeAllBranches = it },
                    testTag = "export_include_all_branches_toggle"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MinimalButton(
                    text = "Export Markdown",
                    icon = LucideIcons.Download,
                    filled = false,
                    onClick = {
                        val id = targetConv.id
                        actionTargetConversation = null
                        onExportConversation(id, false, exportIncludeAllBranches)
                    },
                    modifier = Modifier.weight(1f)
                )
                MinimalButton(
                    text = "Export JSON",
                    icon = LucideIcons.Download,
                    filled = false,
                    onClick = {
                        val id = targetConv.id
                        actionTargetConversation = null
                        onExportConversation(id, true, exportIncludeAllBranches)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Delete with Undo
            MinimalButton(
                text = "Delete conversation",
                icon = LucideIcons.Trash2,
                filled = false,
                onClick = {
                    val id = targetConv.id
                    actionTargetConversation = null
                    onDeleteConversation(id)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Model Filter Bottom Sheet
    if (showModelFilterSheet) {
        MinimalSheet(onDismiss = { showModelFilterSheet = false }) {
            Text(
                text = "Filter by model",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            MinimalChip(
                label = "All models",
                selected = selectedModelFilter == null,
                onClick = {
                    selectedModelFilter = null
                    showModelFilterSheet = false
                }
            )
            distinctModels.forEach { modName ->
                MinimalChip(
                    label = modName,
                    selected = selectedModelFilter == modName,
                    onClick = {
                        selectedModelFilter = modName
                        showModelFilterSheet = false
                    }
                )
            }
        }
    }

    // Create / Edit Folder Dialog
    if (showFolderDialog) {
        FolderEditDialog(
            initialFolder = editingFolder,
            onDismiss = { showFolderDialog = false },
            onSave = { name, colorHex ->
                onSaveFolder(editingFolder?.id, name, colorHex)
                showFolderDialog = false
            },
            onDelete = editingFolder?.let { folder ->
                {
                    onDeleteFolder(folder.id)
                    showFolderDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryConversationRow(
    conversation: ConversationEntity,
    folder: RunwayFolderEntity?,
    isSelected: Boolean,
    searchQuery: String,
    matchedSnippet: String?,
    onSelect: () -> Unit,
    onOpenActions: () -> Unit,
    onSwipePin: () -> Unit,
    onSwipeDelete: () -> Unit,
    onRenameQuick: () -> Unit,
    onDeleteQuick: () -> Unit
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val accentColor = MaterialTheme.colorScheme.primary

    val highlightedTitle = remember(conversation.title, searchQuery, accentColor) {
        highlightMatchAnnotatedString(conversation.title, searchQuery, accentColor)
    }
    val highlightedSnippet = remember(matchedSnippet, searchQuery, accentColor) {
        matchedSnippet?.let { highlightMatchAnnotatedString(it, searchQuery, accentColor) }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
            .pointerInput(conversation.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffsetX > 110f -> onSwipePin()
                            dragOffsetX < -110f -> onSwipeDelete()
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-160f, 160f)
                    }
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onOpenActions
            )
            .testTag("history_conversation_row_${conversation.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (folder != null) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColorSafe(folder.colorHex))
                            )
                        }
                        Text(
                            text = highlightedTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = conversation.modelId.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatHistoryDate(conversation.updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = LucideIcons.Edit2,
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable(onClick = onRenameQuick)
                    )
                    Icon(
                        imageVector = LucideIcons.Trash2,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable(onClick = onDeleteQuick)
                    )
                    Icon(
                        imageVector = LucideIcons.MoreHorizontal,
                        contentDescription = "More conversation actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onOpenActions)
                            .testTag("history_conv_more_${conversation.id}")
                    )
                }
            }

            // Highlighted message content snippet when searching
            if (highlightedSnippet != null) {
                Text(
                    text = highlightedSnippet,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderEditDialog(
    initialFolder: RunwayFolderEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by rememberSaveable(initialFolder?.id) {
        mutableStateOf(initialFolder?.name.orEmpty())
    }
    var selectedHex by rememberSaveable(initialFolder?.id) {
        mutableStateOf(initialFolder?.colorHex ?: folderPaletteHex.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialFolder == null) "New folder" else "Edit folder")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Folder name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    folderPaletteHex.forEach { hex ->
                        val color = parseHexColorSafe(hex)
                        val isChosen = selectedHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isChosen) 2.dp else 0.dp,
                                    color = if (isChosen) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), selectedHex)
                    }
                }
            ) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = CoralError)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun getStartOfTodayMillis(nowMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun formatHistoryDate(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val startOfToday = getStartOfTodayMillis(now)
    val startOfYesterday = startOfToday - 24L * 3600_000L
    return when {
        timestampMs >= startOfToday -> SimpleDateFormat("HH:mm", Locale.US).format(Date(timestampMs))
        timestampMs >= startOfYesterday -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestampMs))
    }
}

private fun extractSnippetAroundQuery(content: String, lowerQuery: String): String {
    val flat = content.replace('\n', ' ').trim()
    val idx = flat.lowercase().indexOf(lowerQuery)
    if (idx == -1) return flat.take(90)
    val start = (idx - 28).coerceAtLeast(0)
    val end = (idx + lowerQuery.length + 46).coerceAtMost(flat.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < flat.length) "…" else ""
    return "$prefix${flat.substring(start, end)}$suffix"
}

private fun highlightMatchAnnotatedString(text: String, rawQuery: String, accentColor: Color): AnnotatedString {
    val q = rawQuery.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    val lowerText = text.lowercase()
    val lowerQ = q.lowercase()
    return buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val matchIdx = lowerText.indexOf(lowerQ, start)
            if (matchIdx == -1) {
                append(text.substring(start))
                break
            }
            if (matchIdx > start) {
                append(text.substring(start, matchIdx))
            }
            withStyle(
                SpanStyle(
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    background = accentColor.copy(alpha = 0.16f)
                )
            ) {
                append(text.substring(matchIdx, matchIdx + q.length))
            }
            start = matchIdx + q.length
        }
    }
}
