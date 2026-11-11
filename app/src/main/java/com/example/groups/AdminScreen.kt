package com.example.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BadgePill
import com.example.ui.components.MinimalCard
import com.example.ui.components.UsageKpiCard

/**
 * Admin dashboard, styled after Runway's Usage analytics screen: KPI cards
 * on top, then breakdown cards. GroupsRoot only routes here when the
 * signed-in uid is in config/admin's `uids` array, so this screen — and the
 * admin-only flows behind it — are invisible to everyone else.
 *
 * Data: stats/global counters, every group, the latest messages app-wide
 * (sender names included) and every signed-in user. Exact Firebase quota
 * (reads, writes, storage) is only visible in the Firebase console.
 */
@Composable
internal fun AdminScreen(
    viewModel: GroupsViewModel,
    onBack: () -> Unit
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val recentActivity by viewModel.recentActivity.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val groupNames = remember(allGroups) { allGroups.associate { it.id to it.name } }

    Column(modifier = Modifier.fillMaxSize()) {
        GroupsHeader(
            title = "Admin",
            subtitle = "Runway usage at a glance",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // KPI cards — same arrangement as the Usage screen.
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
                            label = "Users",
                            value = stats.totalUsers.toString(),
                            subtitle = "signed in to Runway",
                            modifier = Modifier.weight(0.38f)
                        )
                        UsageKpiCard(
                            label = "Groups",
                            value = stats.totalGroups.toString(),
                            subtitle = "created so far",
                            modifier = Modifier.weight(0.62f)
                        )
                    }
                    UsageKpiCard(
                        label = "Messages",
                        value = stats.totalMessages.toString(),
                        subtitle = "sent across all groups",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Recent activity — who messaged, where, and what they said.
            item {
                MinimalCard(modifier = Modifier.fillMaxWidth()) {
                    AdminCardHeader(
                        title = "Recent activity",
                        subtitle = "Who messaged, and where",
                        badge = "Live"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (recentActivity.isEmpty()) {
                        Text(
                            text = "No messages yet. When people chat in groups, " +
                                "the latest messages land here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            recentActivity.forEach { message ->
                                AdminActivityRow(
                                    message = message,
                                    groupName = groupNames[message.groupId] ?: "a group"
                                )
                            }
                        }
                    }
                }
            }

            // Activity by group — every group on the backend.
            item {
                MinimalCard(modifier = Modifier.fillMaxWidth()) {
                    AdminCardHeader(
                        title = "Activity by group",
                        subtitle = "Every group on Runway",
                        badge = "${allGroups.size} groups"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (allGroups.isEmpty()) {
                        Text(
                            text = "No groups yet. Create the first one from the Groups screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            allGroups.forEach { group ->
                                AdminGroupRow(group = group)
                            }
                        }
                    }
                }
            }

            // People — everyone who has signed in.
            item {
                MinimalCard(modifier = Modifier.fillMaxWidth()) {
                    AdminCardHeader(
                        title = "People",
                        subtitle = "Everyone signed in to Runway",
                        badge = "${allUsers.size} users"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (allUsers.isEmpty()) {
                        Text(
                            text = "No users yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            allUsers.forEach { person ->
                                Column {
                                    Text(
                                        text = person.displayName.ifBlank { person.email },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${person.email} · active ${groupTimeLabel(person.lastActiveAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                MinimalCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Where the exact numbers live",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "These counters are app-level totals. Exact Firebase quota — " +
                            "Firestore reads, writes and storage against the free Spark limits — " +
                            "is only shown in the Firebase console (runway-c7ed3) under " +
                            "Firestore Database → Usage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/** Section header inside a breakdown card — mirrors the Usage screen's. */
@Composable
private fun AdminCardHeader(
    title: String,
    subtitle: String,
    badge: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BadgePill(
            text = badge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AdminActivityRow(
    message: GroupMessage,
    groupName: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = message.senderName.ifBlank { "Someone" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = groupTimeLabel(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "in $groupName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AdminGroupRow(group: GroupRoom) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (group.messageCount > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = group.messageCount.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "messages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "${group.memberUids.size} members · last active ${groupTimeLabel(group.lastMessageAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
