package com.example.groups

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.LucideIcons
import com.example.ui.components.MinimalButton
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalSectionHeader
import com.example.ui.components.MinimalSheet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Internal destinations of the Groups feature — it keeps its own tiny back stack. */
private sealed interface GroupsDestination {
    data object List : GroupsDestination
    data class Chat(val groupId: String) : GroupsDestination
    data object Admin : GroupsDestination
}

/**
 * Entry point of the Groups feature, hosted by MainActivity in place of the
 * tab content. Back pops the internal stack first, then exits to Home.
 */
@Composable
fun GroupsRoot(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: GroupsViewModel = viewModel()
    var destination by remember { mutableStateOf<GroupsDestination>(GroupsDestination.List) }

    BackHandler {
        when (destination) {
            is GroupsDestination.List -> onExit()
            else -> destination = GroupsDestination.List
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("groups_root")
    ) {
        when (val dest = destination) {
            is GroupsDestination.List -> GroupsScreen(
                viewModel = viewModel,
                onBack = onExit,
                onOpenGroup = { destination = GroupsDestination.Chat(it) },
                onOpenAdmin = { destination = GroupsDestination.Admin }
            )
            is GroupsDestination.Chat -> GroupChatScreen(
                viewModel = viewModel,
                groupId = dest.groupId,
                onBack = { destination = GroupsDestination.List }
            )
            is GroupsDestination.Admin -> AdminScreen(
                viewModel = viewModel,
                onBack = { destination = GroupsDestination.List }
            )
        }
    }
}

/** Shared slim header for the Groups screens, in the Home/Concept-A language. */
@Composable
internal fun GroupsHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LucideIcons.ArrowLeft,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(17.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun GroupsScreen(
    viewModel: GroupsViewModel,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenAdmin: () -> Unit
) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var showCreateSheet by remember { mutableStateOf(false) }
    var showJoinSheet by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        GroupsHeader(
            title = "Groups",
            subtitle = "Discuss & build together",
            onBack = onBack
        )

        val currentUser = user
        when {
            !viewModel.firebaseAvailable -> {
                Box(modifier = Modifier.padding(horizontal = 18.dp)) {
                    MinimalCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Groups unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "The group service could not be started on this device. " +
                                "Everything else in Runway works as usual.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            currentUser == null -> {
                Box(modifier = Modifier.padding(horizontal = 18.dp)) {
                    MinimalCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Build together",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Groups let you discuss and build with other people in real time. " +
                                "Sign in with Google to create a group or join one with an invite code.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        MinimalButton(
                            text = if (busy) "Signing in…" else "Sign in with Google",
                            onClick = { viewModel.signIn(context) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "groups_sign_in_button"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Your chats and API keys stay on this phone — groups only sync group messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        MinimalCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = currentUser.displayName?.takeIf { it.isNotBlank() }
                                    ?: currentUser.email
                                    ?: "Signed in",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!currentUser.email.isNullOrBlank()) {
                                Text(
                                    text = currentUser.email.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Your ID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentUser.uid,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = LucideIcons.Copy,
                                    contentDescription = "Copy your ID",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable {
                                            clipboard.setText(AnnotatedString(currentUser.uid))
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            MinimalButton(
                                text = "Sign out",
                                onClick = { viewModel.signOut() },
                                filled = false
                            )
                        }
                    }

                    if (status != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = status.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = LucideIcons.X,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { viewModel.clearStatus() }
                                )
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            MinimalButton(
                                text = "Create group",
                                icon = LucideIcons.Plus,
                                onClick = { showCreateSheet = true },
                                modifier = Modifier.weight(1f)
                            )
                            MinimalButton(
                                text = "Join with code",
                                onClick = { showJoinSheet = true },
                                filled = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (isAdmin) {
                        item {
                            MinimalCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onOpenAdmin
                            ) {
                                Row(
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
                                            imageVector = LucideIcons.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Admin",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Usage & stats",
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

                    item {
                        MinimalSectionHeader(title = "Your groups")
                    }

                    if (groups.isEmpty()) {
                        item {
                            Text(
                                text = "No groups yet — create one, or join with an invite code.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    } else {
                        items(groups, key = { it.id }) { group ->
                            MinimalCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onOpenGroup(group.id) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${group.memberUids.size} members · " +
                                                groupTimeLabel(group.lastMessageAt),
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
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        MinimalSheet(
            onDismiss = { showCreateSheet = false },
            title = "Create group",
            subtitle = "You will be the owner. Share the invite code from inside the group."
        ) {
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                placeholder = {
                    Text(
                        text = "Group name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            )
            MinimalButton(
                text = if (busy) "Creating…" else "Create group",
                onClick = {
                    viewModel.createGroup(newGroupName) { groupId ->
                        showCreateSheet = false
                        newGroupName = ""
                        onOpenGroup(groupId)
                    }
                },
                enabled = !busy && newGroupName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showJoinSheet) {
        MinimalSheet(
            onDismiss = { showJoinSheet = false },
            title = "Join with code",
            subtitle = "Ask the group owner for the 6-letter invite code."
        ) {
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it },
                placeholder = {
                    Text(
                        text = "Invite code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            )
            MinimalButton(
                text = if (busy) "Joining…" else "Join group",
                onClick = {
                    viewModel.joinGroup(joinCode) { groupId ->
                        showJoinSheet = false
                        joinCode = ""
                        onOpenGroup(groupId)
                    }
                },
                enabled = !busy && joinCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** "HH:mm" for today, "dd MMM, HH:mm" before that. Empty for unset timestamps. */
internal fun groupTimeLabel(millis: Long): String {
    if (millis <= 0L) return "No activity yet"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance()
    then.timeInMillis = millis
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "dd MMM, HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

/** Clock-only label for chat bubbles. */
internal fun groupClockLabel(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
