package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConversationEntity
import com.example.data.local.ModelDescriptorEntity
import com.example.ui.components.LucideIcons
import com.example.ui.components.RunwayLogoIcon
import com.example.ui.components.RunwayWordmark
import com.example.ui.theme.CreamOnDark
import com.example.ui.theme.DeepForest
import com.example.ui.theme.runwayIsDarkTheme
import java.util.Calendar

/**
 * Concept A "Home-first workspace" hub — the app's start screen.
 * Replaces the old 5-tab bottom bar: every section (Chat, Create, Autopilot,
 * Connect, More) is entered from here, and detail screens carry a back
 * affordance back to this hub.
 */
@Composable
fun RunwayHomeScreen(
    models: List<ModelDescriptorEntity>,
    conversations: List<ConversationEntity>,
    activeConversation: ConversationEntity?,
    enabledSkillsCount: Int,
    mcpToolsCount: Int,
    onOpenChat: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenAutopilot: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenMore: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenConversation: (ConversationEntity) -> Unit,
    onSelectModel: (providerId: String, modelId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = runwayIsDarkTheme()
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..23 -> "Good evening"
            else -> "Good evening"
        }
    }
    val recentConversations = remember(conversations) {
        conversations.sortedByDescending { it.updatedAt }.take(3)
    }
    val modelChips = remember(models, activeConversation) {
        val activeFirst = models.sortedWith(
            compareByDescending<ModelDescriptorEntity> {
                it.providerInstanceId == activeConversation?.providerInstanceId &&
                    it.modelId == activeConversation?.modelId
            }.thenByDescending { it.isFavorite }
        )
        activeFirst.distinctBy { it.providerInstanceId to it.modelId }.take(6)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("runway_home_screen"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Wordmark row + avatar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RunwayLogoIcon(size = 28.dp)
                Spacer(modifier = Modifier.width(8.dp))
                RunwayWordmark()
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else DeepForest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "P",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else CreamOnDark
                    )
                }
            }
        }

        // Greeting
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$greeting,\nParker",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your AI workspace — private by default.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Ask anything card -> opens Chat
        item {
            Surface(
                onClick = onOpenChat,
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_ask_anything_card")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ask anything…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeAskToolPill(icon = LucideIcons.Plus, label = "Attach")
                        HomeAskToolPill(icon = LucideIcons.Sparkles, label = "Skills")
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.Mic,
                                contentDescription = "Voice input",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.ArrowUp,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }

        // Model chips
        if (modelChips.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    HomeSectionLabel(text = "Models")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(modelChips, key = { it.id }) { model ->
                            val isActive = model.providerInstanceId == activeConversation?.providerInstanceId &&
                                model.modelId == activeConversation?.modelId
                            HomeModelChip(
                                label = model.displayName.substringAfterLast("/"),
                                selected = isActive,
                                isDarkTheme = isDarkTheme,
                                onClick = { onSelectModel(model.providerInstanceId, model.modelId) }
                            )
                        }
                    }
                }
            }
        }

        // Jump back in
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeSectionLabel(text = "Jump back in", modifier = Modifier.weight(1f))
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onOpenChat)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                if (recentConversations.isEmpty()) {
                    HomeRecentCard(
                        title = "No conversations yet",
                        subtitle = "Tap Ask anything to start your first chat",
                        onClick = onOpenChat
                    )
                } else {
                    recentConversations.forEach { conversation ->
                        HomeRecentCard(
                            title = conversation.title.ifBlank { "Untitled conversation" },
                            subtitle = "${conversation.modelId.substringAfterLast("/")} · ${homeTimeAgo(conversation.updatedAt)}",
                            onClick = { onOpenConversation(conversation) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Studios & tools grid (2x2) + More row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                HomeSectionLabel(text = "Studios & tools")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeTile(
                        icon = LucideIcons.Chat,
                        title = "Chat",
                        subtitle = "$enabledSkillsCount skills ready",
                        lightIconBg = Color(0xFFEDF2E8),
                        lightIconTint = Color(0xFF5F7D5A),
                        isDarkTheme = isDarkTheme,
                        onClick = onOpenChat,
                        modifier = Modifier.weight(1f)
                    )
                    HomeTile(
                        icon = LucideIcons.Image,
                        title = "Create studio",
                        subtitle = "Image & video",
                        lightIconBg = Color(0xFFF6EBDD),
                        lightIconTint = Color(0xFFB4762E),
                        isDarkTheme = isDarkTheme,
                        onClick = onOpenCreate,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeTile(
                        icon = LucideIcons.Bot,
                        title = "Autopilot",
                        subtitle = "Tasks & daemon",
                        lightIconBg = Color(0xFFE8ECF6),
                        lightIconTint = Color(0xFF4C5FA8),
                        isDarkTheme = isDarkTheme,
                        onClick = onOpenAutopilot,
                        modifier = Modifier.weight(1f)
                    )
                    HomeTile(
                        icon = LucideIcons.Plug,
                        title = "Connectors",
                        subtitle = "$mcpToolsCount tools · MCP",
                        lightIconBg = Color(0xFFF3E8F0),
                        lightIconTint = Color(0xFF8A4E7C),
                        isDarkTheme = isDarkTheme,
                        onClick = onOpenConnect,
                        modifier = Modifier.weight(1f)
                    )
                }
                Surface(
                    onClick = onOpenGroups,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_groups_row")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.MessageSquare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Groups",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Discuss & build together",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Surface(
                    onClick = onOpenMore,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_more_row")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.MoreHorizontal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "More",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Usage · Keys · Settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }
        }

        // Privacy footer
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = LucideIcons.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Private mode",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "· chats stay on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomeSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp)
    )
}

@Composable
private fun HomeAskToolPill(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HomeModelChip(
    label: String,
    selected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        selected && !isDarkTheme -> DeepForest
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        selected && !isDarkTheme -> DeepForest
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val textColor = when {
        selected && !isDarkTheme -> CreamOnDark
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dotColor = when {
        selected && !isDarkTheme -> Color(0xFFA8C69A)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeRecentCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LucideIcons.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    lightIconBg: Color,
    lightIconTint: Color,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else lightIconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDarkTheme) MaterialTheme.colorScheme.primary else lightIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun homeTimeAgo(updatedAtMs: Long): String {
    val diffMs = System.currentTimeMillis() - updatedAtMs
    val minutes = diffMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        hours < 24L -> "$hours hr ago"
        days == 1L -> "Yesterday"
        days < 7L -> "$days days ago"
        else -> "${days / 7L} wk ago"
    }
}
