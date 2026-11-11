package com.example.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalSectionHeader

/**
 * Admin usage page. GroupsRoot only routes here when the signed-in uid is in
 * config/admin's `uids` array, so this screen is invisible to everyone else.
 * Counters come from stats/global; exact Firebase quota (reads, writes,
 * storage) is only visible in the Firebase console.
 */
@Composable
internal fun AdminScreen(
    viewModel: GroupsViewModel,
    onBack: () -> Unit
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()

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
            item {
                MinimalSectionHeader(title = "Totals")
            }
            item {
                MinimalCard(modifier = Modifier.fillMaxWidth()) {
                    AdminStatRow(label = "Users", value = stats.totalUsers)
                    Spacer(modifier = Modifier.height(10.dp))
                    AdminStatRow(label = "Groups", value = stats.totalGroups)
                    Spacer(modifier = Modifier.height(10.dp))
                    AdminStatRow(label = "Messages", value = stats.totalMessages)
                }
            }

            item {
                MinimalSectionHeader(title = "Your groups")
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        text = "You are not in any groups yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(groups, key = { it.id }) { group ->
                    MinimalCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${group.memberUids.size} members · " +
                                "${group.messageCount} messages · " +
                                groupTimeLabel(group.lastMessageAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

@Composable
private fun AdminStatRow(
    label: String,
    value: Long
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
