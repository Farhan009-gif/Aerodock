package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.GeneratedImageEntity
import com.example.data.local.VideoJobEntity
import com.example.ui.components.MinimalCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GalleryModalityFilter(val label: String) {
    ALL("All Media"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    FAVORITES("Favorites ★"),
    IMG2IMG_OR_REF("Img2Img / Ref"),
    UPSCALED("Upscaled")
}

enum class GallerySortOption(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    LOWEST_LATENCY("Fastest Render"),
    HIGHEST_COST("Highest Spec")
}

sealed class PersistentGalleryMediaItem {
    abstract val id: String
    abstract val isVideo: Boolean
    abstract val prompt: String
    abstract val negativePrompt: String
    abstract val providerDisplayName: String
    abstract val modelId: String
    abstract val aspectRatio: String
    abstract val resolutionOrSize: String
    abstract val createdAt: Long
    abstract val isFavorite: Boolean
    abstract val estimatedCostUsd: Double
    abstract val latencyMs: Long
    abstract val hasSourceReference: Boolean

    data class ImageItem(val entity: GeneratedImageEntity) : PersistentGalleryMediaItem() {
        override val id: String get() = "img_${entity.id}"
        override val isVideo: Boolean get() = false
        override val prompt: String get() = entity.prompt
        override val negativePrompt: String get() = entity.negativePrompt
        override val providerDisplayName: String get() = entity.providerDisplayName
        override val modelId: String get() = entity.modelId
        override val aspectRatio: String get() = entity.aspectRatio
        override val resolutionOrSize: String get() = entity.sizeLabel
        override val createdAt: Long get() = entity.createdAt
        override val isFavorite: Boolean get() = entity.isFavorite
        override val estimatedCostUsd: Double get() = entity.estimatedCostUsd
        override val latencyMs: Long get() = entity.latencyMs
        override val hasSourceReference: Boolean get() = !entity.sourceImageUri.isNullOrBlank()
    }

    data class VideoItem(val entity: VideoJobEntity) : PersistentGalleryMediaItem() {
        override val id: String get() = "vid_${entity.id}"
        override val isVideo: Boolean get() = true
        override val prompt: String get() = entity.prompt
        override val negativePrompt: String get() = entity.negativePrompt
        override val providerDisplayName: String get() = entity.providerDisplayName
        override val modelId: String get() = entity.modelId
        override val aspectRatio: String get() = entity.aspectRatio
        override val resolutionOrSize: String get() = "${entity.resolution} · ${entity.durationSeconds}s"
        override val createdAt: Long get() = entity.createdAt
        override val isFavorite: Boolean get() = entity.isFavorite
        override val estimatedCostUsd: Double get() = entity.estimatedCostUsd
        override val latencyMs: Long get() = (entity.updatedAt - entity.createdAt).coerceAtLeast(0L)
        override val hasSourceReference: Boolean get() = !entity.sourceImageUri.isNullOrBlank()
    }
}

/**
 * Persistent Media Gallery Screen that organizes all generated images and videos stored in Room,
 * displaying rich generation parameters, creation timestamps, search/filtering, date grouping,
 * and a detailed metadata inspector.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersistentMediaGalleryScreen(
    generatedImages: List<GeneratedImageEntity>,
    videoJobs: List<VideoJobEntity>,
    onReuseImageSettings: (GeneratedImageEntity) -> Unit,
    onReuseVideoSettings: (VideoJobEntity) -> Unit,
    onUseImageForImg2Img: (String) -> Unit,
    onUseImageForVideoStartFrame: (String) -> Unit,
    onToggleFavoriteImage: (GeneratedImageEntity) -> Unit,
    onToggleFavoriteVideo: (VideoJobEntity) -> Unit,
    onDownloadImage: (GeneratedImageEntity) -> Unit,
    onDownloadVideo: (VideoJobEntity) -> Unit,
    onDeleteImage: (GeneratedImageEntity) -> Unit,
    onDeleteVideo: (VideoJobEntity) -> Unit,
    onInspectVideoInPlayer: (VideoJobEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(GalleryModalityFilter.ALL) }
    var selectedAspectRatio by rememberSaveable { mutableStateOf("All") }
    var selectedSort by rememberSaveable { mutableStateOf(GallerySortOption.NEWEST_FIRST) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var selectedItemForMetadataSheet by remember { mutableStateOf<PersistentGalleryMediaItem?>(null) }

    val unifiedMedia = remember(generatedImages, videoJobs, searchQuery, selectedFilter, selectedAspectRatio, selectedSort) {
        val rawList = buildList {
            generatedImages.forEach { add(PersistentGalleryMediaItem.ImageItem(it)) }
            videoJobs.forEach { add(PersistentGalleryMediaItem.VideoItem(it)) }
        }

        val filtered = rawList.filter { item ->
            val matchesModality = when (selectedFilter) {
                GalleryModalityFilter.ALL -> true
                GalleryModalityFilter.IMAGES -> !item.isVideo
                GalleryModalityFilter.VIDEOS -> item.isVideo
                GalleryModalityFilter.FAVORITES -> item.isFavorite
                GalleryModalityFilter.IMG2IMG_OR_REF -> item.hasSourceReference
                GalleryModalityFilter.UPSCALED -> (item as? PersistentGalleryMediaItem.ImageItem)?.entity?.isUpscaled == true
            }
            val matchesRatio = selectedAspectRatio == "All" || item.aspectRatio == selectedAspectRatio
            val q = searchQuery.trim().lowercase()
            val matchesQuery = if (q.isEmpty()) {
                true
            } else {
                item.prompt.lowercase().contains(q) ||
                    item.negativePrompt.lowercase().contains(q) ||
                    item.modelId.lowercase().contains(q) ||
                    item.providerDisplayName.lowercase().contains(q) ||
                    item.resolutionOrSize.lowercase().contains(q)
            }
            matchesModality && matchesRatio && matchesQuery
        }

        when (selectedSort) {
            GallerySortOption.NEWEST_FIRST -> filtered.sortedByDescending { it.createdAt }
            GallerySortOption.OLDEST_FIRST -> filtered.sortedBy { it.createdAt }
            GallerySortOption.LOWEST_LATENCY -> filtered.sortedBy { it.latencyMs.takeIf { ms -> ms > 0 } ?: Long.MAX_VALUE }
            GallerySortOption.HIGHEST_COST -> filtered.sortedByDescending { it.estimatedCostUsd }
        }
    }

    val groupedByDate = remember(unifiedMedia) {
        val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
        unifiedMedia.groupBy { item -> dateFormat.format(Date(item.createdAt)) }
    }

    val totalFavorites = remember(generatedImages, videoJobs) {
        generatedImages.count { it.isFavorite } + videoJobs.count { it.isFavorite }
    }
    val totalSpendUsd = remember(generatedImages, videoJobs) {
        generatedImages.sumOf { it.estimatedCostUsd } + videoJobs.sumOf { it.estimatedCostUsd }
    }

    selectedItemForMetadataSheet?.let { activeItem ->
        MediaMetadataInspectorSheet(
            item = activeItem,
            onDismiss = { selectedItemForMetadataSheet = null },
            onCopyPrompt = {
                clipboardManager.setText(AnnotatedString(activeItem.prompt))
            },
            onCopyFullMetadataJson = { jsonText ->
                clipboardManager.setText(AnnotatedString(jsonText))
            },
            onReuseInStudio = {
                when (activeItem) {
                    is PersistentGalleryMediaItem.ImageItem -> onReuseImageSettings(activeItem.entity)
                    is PersistentGalleryMediaItem.VideoItem -> onReuseVideoSettings(activeItem.entity)
                }
                selectedItemForMetadataSheet = null
            },
            onUseAsImg2ImgRef = {
                if (activeItem is PersistentGalleryMediaItem.ImageItem) {
                    onUseImageForImg2Img(activeItem.entity.imageUri)
                    selectedItemForMetadataSheet = null
                }
            },
            onUseAsVideoStartFrame = {
                if (activeItem is PersistentGalleryMediaItem.ImageItem) {
                    onUseImageForVideoStartFrame(activeItem.entity.imageUri)
                    selectedItemForMetadataSheet = null
                }
            },
            onOpenVideoPlayer = {
                if (activeItem is PersistentGalleryMediaItem.VideoItem) {
                    onInspectVideoInPlayer(activeItem.entity)
                    selectedItemForMetadataSheet = null
                }
            },
            onToggleFavorite = {
                when (activeItem) {
                    is PersistentGalleryMediaItem.ImageItem -> onToggleFavoriteImage(activeItem.entity)
                    is PersistentGalleryMediaItem.VideoItem -> onToggleFavoriteVideo(activeItem.entity)
                }
                selectedItemForMetadataSheet = null
            },
            onDownload = {
                when (activeItem) {
                    is PersistentGalleryMediaItem.ImageItem -> onDownloadImage(activeItem.entity)
                    is PersistentGalleryMediaItem.VideoItem -> onDownloadVideo(activeItem.entity)
                }
            },
            onDelete = {
                when (activeItem) {
                    is PersistentGalleryMediaItem.ImageItem -> onDeleteImage(activeItem.entity)
                    is PersistentGalleryMediaItem.VideoItem -> onDeleteVideo(activeItem.entity)
                }
                selectedItemForMetadataSheet = null
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("persistent_media_gallery_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Summary Telemetry Bar & Layout Mode Switcher
        item {
            MinimalCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Persistent Media Library",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${generatedImages.size} Images · ${videoJobs.size} Videos · $totalFavorites Favorites · Est. $${String.format(Locale.US, "%.2f", totalSpendUsd)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { isGridView = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("gallery_view_mode_grid")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Grid View",
                                    tint = if (isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { isGridView = false },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("gallery_view_mode_list")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewList,
                                    contentDescription = "Detailed Metadata List View",
                                    tint = if (!isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search prompts, models, seeds, styles, or camera motion...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gallery_search_input")
                    )

                    // Modality Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GalleryModalityFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label, maxLines = 1) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("gallery_filter_${filter.name.lowercase()}")
                            )
                        }
                    }

                    // Aspect Ratio & Sort Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("All", "1:1", "16:9", "9:16", "4:3", "3:4").forEach { ratio ->
                            FilterChip(
                                selected = selectedAspectRatio == ratio,
                                onClick = { selectedAspectRatio = ratio },
                                label = { Text(if (ratio == "All") "All Ratios" else ratio, maxLines = 1) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }

                        GallerySortOption.entries.forEach { sortOpt ->
                            FilterChip(
                                selected = selectedSort == sortOpt,
                                onClick = { selectedSort = sortOpt },
                                label = { Text(sortOpt.label, maxLines = 1) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Empty State or Date-Grouped Persistent Media Items
        if (unifiedMedia.isEmpty()) {
            item {
                MinimalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No matching generated images or videos found",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Generated images and videos are automatically saved to persistent storage with full generation metadata.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            groupedByDate.forEach { (dateHeader, itemsForDate) ->
                item(key = "header_$dateHeader") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateHeader,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${itemsForDate.size} item(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isGridView) {
                    val pairs = itemsForDate.chunked(2)
                    items(pairs, key = { pair -> "grid_${pair.first().id}" }) { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowPair.forEach { mediaItem ->
                                PersistentMediaGridCard(
                                    item = mediaItem,
                                    onClick = { selectedItemForMetadataSheet = mediaItem },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowPair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(itemsForDate, key = { "list_${it.id}" }) { mediaItem ->
                        PersistentMediaDetailedMetadataCard(
                            item = mediaItem,
                            onClick = { selectedItemForMetadataSheet = mediaItem },
                            onReuseClick = {
                                when (mediaItem) {
                                    is PersistentGalleryMediaItem.ImageItem -> onReuseImageSettings(mediaItem.entity)
                                    is PersistentGalleryMediaItem.VideoItem -> onReuseVideoSettings(mediaItem.entity)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersistentMediaGridCard(
    item: PersistentGalleryMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val timeLabel = remember(item.createdAt) { timeFormat.format(Date(item.createdAt)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("persistent_gallery_grid_card_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                when (item) {
                    is PersistentGalleryMediaItem.ImageItem -> {
                        val model = remember(item.entity.imageUri) {
                            if (item.entity.imageUri.startsWith("/")) File(item.entity.imageUri) else item.entity.imageUri
                        }
                        AsyncImage(
                            model = model,
                            contentDescription = item.prompt,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is PersistentGalleryMediaItem.VideoItem -> {
                        val previewUri = item.entity.sourceImageUri ?: item.entity.videoUri
                        if (!previewUri.isNullOrBlank()) {
                            val model = remember(previewUri) {
                                if (previewUri.startsWith("/")) File(previewUri) else previewUri
                            }
                            AsyncImage(
                                model = model,
                                contentDescription = item.prompt,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = "Video Item",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }

                // Top-left Modality Badge
                Surface(
                    color = if (item.isVideo) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = when {
                            item.isVideo -> "VIDEO · ${item.aspectRatio}"
                            item.hasSourceReference -> "IMG2IMG · ${item.aspectRatio}"
                            else -> "IMAGE · ${item.aspectRatio}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }

                if (item.isFavorite) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.modelId.substringAfterLast("/")} • $timeLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.resolutionOrSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersistentMediaDetailedMetadataCard(
    item: PersistentGalleryMediaItem,
    onClick: () -> Unit,
    onReuseClick: () -> Unit
) {
    val fullTimestampFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm:ss", Locale.US) }
    val dateLabel = remember(item.createdAt) { fullTimestampFormat.format(Date(item.createdAt)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("persistent_gallery_list_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    when (item) {
                        is PersistentGalleryMediaItem.ImageItem -> {
                            val model = remember(item.entity.imageUri) {
                                if (item.entity.imageUri.startsWith("/")) File(item.entity.imageUri) else item.entity.imageUri
                            }
                            AsyncImage(
                                model = model,
                                contentDescription = item.prompt,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is PersistentGalleryMediaItem.VideoItem -> {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Video",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.providerDisplayName} · ${item.modelId.substringAfterLast("/")}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = item.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Generation Parameters Pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetadataParameterPill("Ratio: ${item.aspectRatio}")
                MetadataParameterPill("Spec: ${item.resolutionOrSize}")
                when (item) {
                    is PersistentGalleryMediaItem.ImageItem -> {
                        val img = item.entity
                        MetadataParameterPill("Steps: ${img.steps}")
                        MetadataParameterPill("CFG: ${img.guidanceScale}")
                        if (img.stylePreset.isNotBlank() && img.stylePreset != "None") {
                            MetadataParameterPill("Style: ${img.stylePreset}")
                        }
                        img.seed?.let { MetadataParameterPill("Seed: $it") }
                        if (!img.sourceImageUri.isNullOrBlank()) {
                            MetadataParameterPill("Img2Img: ${(img.imageToImageStrength * 100).toInt()}%")
                        }
                        if (img.isUpscaled) {
                            MetadataParameterPill("Upscaled")
                        }
                    }
                    is PersistentGalleryMediaItem.VideoItem -> {
                        val vid = item.entity
                        MetadataParameterPill("Motion: ${vid.cameraMotion}")
                        MetadataParameterPill("Status: ${vid.status}")
                        if (!vid.sourceImageUri.isNullOrBlank()) {
                            MetadataParameterPill("Img2Video Seeded")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onReuseClick,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reuse Parameters", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MetadataParameterPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MediaMetadataInspectorSheet(
    item: PersistentGalleryMediaItem,
    onDismiss: () -> Unit,
    onCopyPrompt: () -> Unit,
    onCopyFullMetadataJson: (String) -> Unit,
    onReuseInStudio: () -> Unit,
    onUseAsImg2ImgRef: () -> Unit,
    onUseAsVideoStartFrame: () -> Unit,
    onOpenVideoPlayer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timestampFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US) }
    val createdDateFormatted = remember(item.createdAt) { timestampFormat.format(Date(item.createdAt)) }

    val metadataPairs = remember(item) {
        buildList {
            add("Modality" to if (item.isVideo) "Video Generation" else "Image Generation")
            add("Created At" to createdDateFormatted)
            add("Provider" to item.providerDisplayName)
            add("Model ID" to item.modelId)
            add("Aspect Ratio" to item.aspectRatio)
            add("Dimensions / Duration" to item.resolutionOrSize)
            add("Estimated Cost" to String.format(Locale.US, "$%.4f USD", item.estimatedCostUsd))
            if (item.latencyMs > 0L) {
                add("Generation Latency" to "${item.latencyMs} ms")
            }
            when (item) {
                is PersistentGalleryMediaItem.ImageItem -> {
                    val img = item.entity
                    add("Style Preset" to img.stylePreset)
                    add("Inference Steps" to img.steps.toString())
                    add("Guidance Scale (CFG)" to img.guidanceScale.toString())
                    add("Seed" to (img.seed?.toString() ?: "Random"))
                    add("Quality Tier" to img.quality)
                    add("Upscaled" to if (img.isUpscaled) "Yes (Super-Resolution)" else "Standard")
                    if (!img.sourceImageUri.isNullOrBlank()) {
                        add("Img2Img Strength" to "${(img.imageToImageStrength * 100).toInt()}%")
                        add("Img2Img Source" to img.sourceImageUri.substringAfterLast("/"))
                    }
                }
                is PersistentGalleryMediaItem.VideoItem -> {
                    val vid = item.entity
                    add("Camera Motion" to vid.cameraMotion)
                    add("Job Status" to "${vid.status} (${vid.progressPercent}%)")
                    if (vid.remoteJobId.isNotBlank()) {
                        add("Remote Job ID" to vid.remoteJobId)
                    }
                    if (!vid.sourceImageUri.isNullOrBlank()) {
                        add("Starting Frame" to vid.sourceImageUri.substringAfterLast("/"))
                    }
                }
            }
        }
    }

    val exportedJsonString = remember(item, metadataPairs) {
        buildString {
            appendLine("{")
            appendLine("  \"id\": \"${item.id}\",")
            appendLine("  \"prompt\": \"${item.prompt.replace("\"", "\\\"")}\",")
            appendLine("  \"negativePrompt\": \"${item.negativePrompt.replace("\"", "\\\"")}\",")
            metadataPairs.forEachIndexed { idx, (k, v) ->
                val comma = if (idx == metadataPairs.lastIndex) "" else ","
                appendLine("  \"$k\": \"${v.replace("\"", "\\\"")}\"$comma")
            }
            append("}")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("media_metadata_inspector_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Generation Parameters & Metadata",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Visual Preview
            if (item is PersistentGalleryMediaItem.ImageItem) {
                val model = remember(item.entity.imageUri) {
                    if (item.entity.imageUri.startsWith("/")) File(item.entity.imageUri) else item.entity.imageUri
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = model,
                        contentDescription = item.prompt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (item is PersistentGalleryMediaItem.VideoItem) {
                Button(
                    onClick = onOpenVideoPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PlayCircleFilled, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Video in ExoPlayer Inspector", maxLines = 1)
                }
            }

            // Prompt & Negative Prompt Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Prompt",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.prompt,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (item.negativePrompt.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Negative Prompt",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = item.negativePrompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Structured Metadata Specification Table
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metadataPairs.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.45f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.55f)
                            )
                        }
                        if (index < metadataPairs.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // Action Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReuseInStudio,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reuse Parameters", maxLines = 1)
                }

                if (!item.isVideo) {
                    OutlinedButton(
                        onClick = onUseAsImg2ImgRef,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use for Img2Img", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onUseAsVideoStartFrame,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Animate in Video", maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = onCopyPrompt,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Prompt", maxLines = 1)
                }

                OutlinedButton(
                    onClick = { onCopyFullMetadataJson(exportedJsonString) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Copy JSON Metadata", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (item.isFavorite) "Unfavorite" else "Favorite", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
            }
        }
    }
}
