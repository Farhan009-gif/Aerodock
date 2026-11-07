package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ClonedRepoEntity
import com.example.data.local.SkillEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError

import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily

/**
 * Modal BottomSheet for cloning GitHub repositories and extracting agent skills.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCloneBottomSheet(
    isCloning: Boolean,
    progressMessage: String,
    errorMessage: String?,
    defaultToken: String = "",
    onClone: (url: String, branch: String?, token: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var repoUrlInput by rememberSaveable { mutableStateOf("") }
    var branchInput by rememberSaveable { mutableStateOf("") }
    var tokenInput by rememberSaveable { mutableStateOf(defaultToken) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    val quickPresets = listOf(
        "anthropics/anthropic-quickstarts" to "Claude Agent Skills",
        "modelcontextprotocol/servers" to "MCP Official Servers",
        "github/copilot-sdk" to "GitHub Agent SDK",
        "octocat/Hello-World" to "Starter Repo"
    )

    ModalBottomSheet(
        onDismissRequest = { if (!isCloning) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("git_clone_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Clone GitHub Repository",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Download repo, index files & extract SKILL.md agent skills",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    enabled = !isCloning,
                    modifier = Modifier.testTag("git_clone_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Quick Preset Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPresets.forEach { (repoSlug, label) ->
                        FilterChip(
                            selected = repoUrlInput == repoSlug,
                            onClick = {
                                repoUrlInput = repoSlug
                                branchInput = ""
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("quick_preset_${repoSlug.replace('/', '_')}")
                        )
                    }
                }
            }

            // Repository URL Input
            OutlinedTextField(
                value = repoUrlInput,
                onValueChange = { repoUrlInput = it },
                label = { Text("Repository URL or owner/repo") },
                placeholder = { Text("https://github.com/owner/repo or owner/repo") },
                singleLine = true,
                enabled = !isCloning,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("git_clone_url_input")
            )

            // Optional Advanced: Branch and Personal Access Token
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showAdvanced) "Hide Advanced Options" else "Branch & Private Token Options",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = branchInput,
                        onValueChange = { branchInput = it },
                        label = { Text("Branch / Tag (Optional)") },
                        placeholder = { Text("main (auto-detected if blank)") },
                        singleLine = true,
                        enabled = !isCloning,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("git_clone_branch_input")
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("GitHub Token / PAT (Optional)") },
                        placeholder = { Text("ghp_... (for private repos or higher limits)") },
                        singleLine = true,
                        enabled = !isCloning,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("git_clone_token_input")
                    )
                }
            }

            // Progress Banner
            if (isCloning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = progressMessage.ifBlank { "Cloning repository..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Error Message
            if (!errorMessage.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CoralError.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, CoralError.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralError,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isCloning,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onClone(
                            repoUrlInput.trim(),
                            branchInput.trim().takeIf { it.isNotEmpty() },
                            tokenInput.trim().takeIf { it.isNotEmpty() }
                        )
                    },
                    enabled = !isCloning && repoUrlInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("git_clone_submit_button")
                ) {
                    if (isCloning) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cloning...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clone & Use Skills")
                    }
                }
            }
        }
    }
}

/**
 * List of cloned repositories with their extracted skills, file counts, and action controls.
 */
@Composable
fun ClonedReposSection(
    clonedRepos: List<ClonedRepoEntity>,
    allSkills: List<SkillEntity>,
    onCloneNewRepo: () -> Unit,
    onSyncRepo: (repoId: String) -> Unit,
    onDeleteRepo: (repoId: String) -> Unit,
    onExploreFiles: (repo: ClonedRepoEntity) -> Unit,
    onActivateSkillsInChat: (repo: ClonedRepoEntity) -> Unit,
    onToggleSkillEnabled: (skill: SkillEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloned_repos_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Cloned Repositories & Agent Skills",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Import GitHub repositories and use their SKILL.md instruction packs in AI chats.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onCloneNewRepo,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("clone_new_repo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clone", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (clonedRepos.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No GitHub repositories cloned yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Clone any public or private GitHub repository to automatically parse its agent skills (SKILL.md) and use them directly in conversations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        OutlinedButton(
                            onClick = onCloneNewRepo,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("empty_state_clone_repo_btn")
                        ) {
                            Text("Clone a GitHub Repo")
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    clonedRepos.forEach { repo ->
                        ClonedRepoCard(
                            repo = repo,
                            allSkills = allSkills,
                            onSync = { onSyncRepo(repo.id) },
                            onDelete = { onDeleteRepo(repo.id) },
                            onExploreFiles = { onExploreFiles(repo) },
                            onActivateSkillsInChat = { onActivateSkillsInChat(repo) },
                            onToggleSkillEnabled = onToggleSkillEnabled
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual card displaying a cloned repository and its discovered skills.
 */
@Composable
fun ClonedRepoCard(
    repo: ClonedRepoEntity,
    allSkills: List<SkillEntity>,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onExploreFiles: () -> Unit,
    onActivateSkillsInChat: () -> Unit,
    onToggleSkillEnabled: (SkillEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    // Find the SkillEntity objects corresponding to this repo
    val repoSkills = remember(repo, allSkills) {
        allSkills.filter { it.sourceUrl.contains("github.com/${repo.owner}/${repo.repoName}") }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Cloned Repo?") },
            text = {
                Text("Are you sure you want to remove '${repo.owner}/${repo.repoName}' and its extracted skills from your workspace?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cloned_repo_card_${repo.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Repo Info Header
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
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "${repo.owner} / ${repo.repoName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Branch: ${repo.branch}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${repo.fileCount} files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Skills Badge
                Surface(
                    color = EmeraldConnected.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${repo.skillCount} SKILLS",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldConnected,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Discovered Skills preview list
            if (repoSkills.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val displayedSkills = if (expanded) repoSkills else repoSkills.take(2)
                    displayedSkills.forEach { skill ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (skill.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = skill.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = skill.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Switch(
                                checked = skill.isEnabled,
                                onCheckedChange = { onToggleSkillEnabled(skill) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.size(36.dp).testTag("toggle_skill_${skill.id}")
                            )
                        }
                    }

                    if (repoSkills.size > 2) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = if (expanded) "Show less" else "View all ${repoSkills.size} skills",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Activate in Chat
                    OutlinedButton(
                        onClick = onActivateSkillsInChat,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("activate_repo_skills_${repo.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Use in Chat", style = MaterialTheme.typography.labelSmall)
                    }

                    // Explore Files
                    OutlinedButton(
                        onClick = onExploreFiles,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("explore_repo_files_${repo.id}")
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Files", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Re-sync button
                    IconButton(
                        onClick = onSync,
                        modifier = Modifier.size(32.dp).testTag("sync_repo_${repo.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync from GitHub",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp).testTag("delete_repo_${repo.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete repository",
                            tint = CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * File Explorer BottomSheet for inspecting repository files and code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoFileBrowserBottomSheet(
    repo: ClonedRepoEntity,
    filesList: List<String>,
    viewingFilePath: String?,
    viewingFileContent: String?,
    onViewFile: (relativePath: String) -> Unit,
    onBackToList: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    var copiedToast by remember { mutableStateOf(false) }

    val filteredFiles = remember(filesList, searchQuery) {
        if (searchQuery.isBlank()) filesList
        else filesList.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("repo_file_browser_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewingFilePath != null) {
                        IconButton(onClick = onBackToList) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Column {
                        Text(
                            text = viewingFilePath ?: "${repo.owner}/${repo.repoName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (viewingFilePath != null) "File content preview" else "${filesList.size} files in repository",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (viewingFileContent != null) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(viewingFileContent))
                                copiedToast = true
                            }
                        ) {
                            Icon(
                                imageVector = if (copiedToast) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy file content",
                                tint = if (copiedToast) EmeraldConnected else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            if (viewingFilePath != null && viewingFileContent != null) {
                // Display File Content Preview
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = viewingFileContent,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter files (e.g. SKILL.md, src/, .kt)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )

                // Files List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredFiles) { path ->
                        val isSkill = path.endsWith("SKILL.md", ignoreCase = true) || path.endsWith(".skill.md", ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onViewFile(path) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isSkill) Icons.Default.AutoAwesome else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (isSkill) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = if (isSkill) FontFamily.Default else JetBrainsMonoFontFamily,
                                fontWeight = if (isSkill) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSkill) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSkill) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "SKILL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
