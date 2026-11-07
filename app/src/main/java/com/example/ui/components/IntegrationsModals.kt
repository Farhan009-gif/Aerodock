package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.integrations.GitHubRepoSummary
import com.example.data.integrations.YouTubeIntegrationClient
import com.example.data.local.IntegrationAuditLogEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.runwayIsDarkTheme
import com.example.ui.theme.CoralError

import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.GitHubPushDraft
import com.example.ui.viewmodel.OmniUiState
import com.example.ui.viewmodel.YouTubePublishDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitHubPushDialog(
    draft: GitHubPushDraft,
    maskedGitHubPat: String,
    repos: List<GitHubRepoSummary>,
    isBusy: Boolean,
    onSavePatInline: (String) -> Unit,
    onFetchRepos: () -> Unit,
    onConfirmCreateGist: (description: String, fileName: String, content: String, isPublic: Boolean) -> Unit,
    onConfirmRepoCommit: (ownerRepo: String, filePath: String, content: String, commitMessage: String, branch: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("GIST") } // "GIST" | "REPO_COMMIT"
    var gistDescription by rememberSaveable(draft.title) {
        mutableStateOf("${draft.title} — Exported from Runway")
    }
    var gistFileName by rememberSaveable(draft.suggestedFileName) {
        mutableStateOf(draft.suggestedFileName)
    }
    var gistIsPublic by rememberSaveable { mutableStateOf(false) }

    var ownerRepo by rememberSaveable {
        mutableStateOf(repos.firstOrNull()?.fullName.orEmpty())
    }
    var branch by rememberSaveable {
        mutableStateOf(repos.firstOrNull()?.defaultBranch ?: "main")
    }
    var repoFilePath by rememberSaveable(draft.suggestedFilePath) {
        mutableStateOf(draft.suggestedFilePath)
    }
    var commitMessage by rememberSaveable(draft.title) {
        mutableStateOf("Add ${draft.suggestedFileName} (${draft.title}) via Runway")
    }
    var editableContent by rememberSaveable(draft.content) {
        mutableStateOf(draft.content)
    }
    var inlineToken by rememberSaveable { mutableStateOf("") }
    var showInlineToken by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 640.dp)
                .padding(vertical = 16.dp)
                .testTag("github_push_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Push to GitHub (Manual Confirmation)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Explicit user-triggered publish • Never auto-posted",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldConnected
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Inline GitHub PAT configuration if missing
                if (maskedGitHubPat.isBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmberWarning)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "GitHub Personal Access Token Required",
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Enter a GitHub PAT with 'gist' or 'repo' scope. It will be encrypted with AES256-GCM inside the Tink + Android Keystore vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inlineToken,
                                    onValueChange = { inlineToken = it },
                                    placeholder = { Text("ghp_... or github_pat_...") },
                                    singleLine = true,
                                    visualTransformation = if (showInlineToken) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showInlineToken = !showInlineToken }) {
                                            Icon(
                                                imageVector = if (showInlineToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle token visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        onSavePatInline(inlineToken)
                                        inlineToken = ""
                                    },
                                    enabled = inlineToken.isNotBlank()
                                ) {
                                    Text("Save Key")
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BadgePill(
                            text = "Tink Vault PAT: $maskedGitHubPat",
                            color = EmeraldConnected
                        )
                        if (mode == "REPO_COMMIT") {
                            TextButton(onClick = onFetchRepos, enabled = !isBusy) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Load Repos")
                            }
                        }
                    }
                }

                // Mode Selector: Gist vs Repository Commit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == "GIST",
                        onClick = { mode = "GIST" },
                        label = { Text("Create GitHub Gist") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("github_mode_gist_chip")
                    )
                    FilterChip(
                        selected = mode == "REPO_COMMIT",
                        onClick = { mode = "REPO_COMMIT" },
                        label = { Text("Commit to Repository") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("github_mode_repo_chip")
                    )
                }

                if (mode == "GIST") {
                    OutlinedTextField(
                        value = gistFileName,
                        onValueChange = { gistFileName = it },
                        label = { Text("Gist Filename") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_gist_filename_input")
                    )
                    OutlinedTextField(
                        value = gistDescription,
                        onValueChange = { gistDescription = it },
                        label = { Text("Gist Description") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_gist_description_input")
                    )
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
                                imageVector = if (gistIsPublic) Icons.Default.Public else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (gistIsPublic) AmberWarning else EmeraldConnected,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (gistIsPublic) "Public Gist (Discoverable)" else "Secret Gist (Unlisted URL)",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "Secret Gists are hidden from search engines.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = gistIsPublic,
                            onCheckedChange = { gistIsPublic = it }
                        )
                    }
                } else {
                    if (repos.isNotEmpty()) {
                        Text(
                            text = "Quick-Select Repository:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repos.forEach { repo ->
                                FilterChip(
                                    selected = ownerRepo == repo.fullName,
                                    onClick = {
                                        ownerRepo = repo.fullName
                                        branch = repo.defaultBranch
                                    },
                                    label = { Text(repo.fullName) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ownerRepo,
                            onValueChange = { ownerRepo = it },
                            label = { Text("Repository (owner/repo)") },
                            placeholder = { Text("octocat/ai-workspace") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            modifier = Modifier
                                .weight(0.68f)
                                .testTag("github_repo_owner_input")
                        )
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            label = { Text("Branch") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            modifier = Modifier
                                .weight(0.32f)
                                .testTag("github_repo_branch_input")
                        )
                    }

                    OutlinedTextField(
                        value = repoFilePath,
                        onValueChange = { repoFilePath = it },
                        label = { Text("Target File Path in Repo") },
                        placeholder = { Text("exports/conversation.md") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_filepath_input")
                    )

                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit Message") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_commit_msg_input")
                    )
                }

                OutlinedTextField(
                    value = editableContent,
                    onValueChange = { editableContent = it },
                    label = { Text("Content Preview (Editable before push)") },
                    minLines = 5,
                    maxLines = 9,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isBusy) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (mode == "GIST") {
                                onConfirmCreateGist(
                                    gistDescription,
                                    gistFileName,
                                    editableContent,
                                    gistIsPublic
                                )
                            } else {
                                onConfirmRepoCommit(
                                    ownerRepo,
                                    repoFilePath,
                                    editableContent,
                                    commitMessage,
                                    branch
                                )
                            }
                        },
                        enabled = !isBusy && editableContent.isNotBlank() && maskedGitHubPat.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = if (runwayIsDarkTheme()) Color(0xFF04121D) else MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("github_confirm_push_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = if (runwayIsDarkTheme()) Color(0xFF04121D) else MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publishing…")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (mode == "GIST") "Publish Gist Now" else "Commit to Repo Now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubePublishDialog(
    draft: YouTubePublishDraft,
    maskedYouTubeToken: String,
    youTubeChannelSummary: String,
    isBusy: Boolean,
    onSaveAccessTokenInline: (String) -> Unit,
    onConfirmUpload: (title: String, description: String, tagsCsv: String, privacyStatus: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(draft.suggestedTitle) { mutableStateOf(draft.suggestedTitle) }
    var description by rememberSaveable(draft.suggestedDescription) { mutableStateOf(draft.suggestedDescription) }
    var tagsCsv by rememberSaveable(draft.suggestedTagsCsv) { mutableStateOf(draft.suggestedTagsCsv) }
    var privacyStatus by rememberSaveable { mutableStateOf("private") } // "private" | "unlisted" | "public"
    var inlineAccessToken by rememberSaveable { mutableStateOf("") }
    var showInlineToken by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 640.dp)
                .padding(vertical = 16.dp)
                .testTag("youtube_publish_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Publish Video to YouTube (Manual Upload)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "YouTube Data API v3 Resumable Upload • Explicit user confirmation",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldConnected
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (maskedYouTubeToken.isBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmberWarning)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "YouTube OAuth 2.0 Access Token Required",
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Paste a Google OAuth 2.0 Bearer token with 'youtube.upload' scope (or complete the OAuth 2.0 PKCE flow in Settings -> Integrations). Stored encrypted in Tink Vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inlineAccessToken,
                                    onValueChange = { inlineAccessToken = it },
                                    placeholder = { Text("ya29.a0...") },
                                    singleLine = true,
                                    visualTransformation = if (showInlineToken) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showInlineToken = !showInlineToken }) {
                                            Icon(
                                                imageVector = if (showInlineToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle token visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        onSaveAccessTokenInline(inlineAccessToken)
                                        inlineAccessToken = ""
                                    },
                                    enabled = inlineAccessToken.isNotBlank()
                                ) {
                                    Text("Save Token")
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BadgePill(
                            text = "OAuth Token: $maskedYouTubeToken",
                            color = EmeraldConnected
                        )
                        if (youTubeChannelSummary.isNotBlank()) {
                            Text(
                                text = youTubeChannelSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("YouTube Video Title (Max 100 chars)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Video Description") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_description_input")
                )

                OutlinedTextField(
                    value = tagsCsv,
                    onValueChange = { tagsCsv = it },
                    label = { Text("Tags (Comma-separated)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_tags_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Privacy Status (Defaults to Private for safety):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "private" to "Private (Only You)",
                            "unlisted" to "Unlisted (Link Only)",
                            "public" to "Public"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = privacyStatus == key,
                                onClick = { privacyStatus = key },
                                label = { Text(label) },
                                modifier = Modifier.testTag("youtube_privacy_chip_$key")
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            title = draft.suggestedTitle
                            description = draft.suggestedDescription
                            tagsCsv = draft.suggestedTagsCsv
                        }
                    ) {
                        Text("Reset Metadata from Prompt")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss, enabled = !isBusy) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onConfirmUpload(title, description, tagsCsv, privacyStatus)
                            },
                            enabled = !isBusy && title.isNotBlank() && maskedYouTubeToken.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = if (runwayIsDarkTheme()) Color(0xFF04121D) else MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("youtube_confirm_upload_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = if (runwayIsDarkTheme()) Color(0xFF04121D) else MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uploading MP4…")
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload to YouTube Now")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntegrationsSettingsSection(
    uiState: OmniUiState,
    integrationLogs: List<IntegrationAuditLogEntity>,
    onSaveGitHubPat: (String) -> Unit,
    onClearGitHubPat: () -> Unit,
    onVerifyGitHub: (String?) -> Unit,
    onOpenQuickGitHubGist: () -> Unit,
    onSaveYouTubeCredentials: (clientId: String, accessToken: String) -> Unit,
    onClearYouTubeCredentials: () -> Unit,
    onRegeneratePkce: () -> Unit,
    onExchangePkceCode: (clientId: String, authCode: String) -> Unit,
    onVerifyYouTubeChannel: (String?) -> Unit,
    onClearIntegrationLogs: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var ghTokenInput by rememberSaveable { mutableStateOf("") }
    var showGhToken by rememberSaveable { mutableStateOf(false) }

    var ytClientIdInput by rememberSaveable(uiState.youTubeClientId) {
        mutableStateOf(uiState.youTubeClientId)
    }
    var ytAccessTokenInput by rememberSaveable { mutableStateOf("") }
    var showYtToken by rememberSaveable { mutableStateOf(false) }
    var ytAuthCodeInput by rememberSaveable { mutableStateOf("") }
    var showPkceWizard by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("integrations_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "External Integrations (GitHub & YouTube)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Strictly manual user-triggered actions • Tokens encrypted in Tink Keystore Vault",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldConnected
                        )
                    }
                }
                BadgePill(text = "MANUAL ONLY", color = MaterialTheme.colorScheme.primary)
            }

            // --- Part A: GitHub Integration (Gists & Repository File Commits) ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = "GitHub (Gists & Repository Commits)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                val cleanGhSummary = uiState.gitHubProfileSummary
                                    .replace(" (null)", "")
                                    .replace("(null)", "")
                                    .replace(" null", "")
                                Text(
                                    text = if (cleanGhSummary.isNotBlank()) {
                                        cleanGhSummary
                                    } else if (uiState.maskedGitHubPat.isNotBlank()) {
                                        "Encrypted PAT stored (${uiState.maskedGitHubPat})"
                                    } else {
                                        "Not connected — Enter a Fine-Grained or Classic PAT ('gist' / 'repo')"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.maskedGitHubPat.isNotBlank()) EmeraldConnected
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (uiState.maskedGitHubPat.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "GitHub Token Stored",
                                tint = EmeraldConnected,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = ghTokenInput,
                        onValueChange = { ghTokenInput = it },
                        label = {
                            Text(
                                if (uiState.maskedGitHubPat.isNotBlank()) {
                                    "GitHub PAT (saved, enter new to replace)"
                                } else {
                                    "GitHub Personal Access Token (ghp_... / github_pat_...)"
                                },
                                maxLines = 1
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showGhToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGhToken = !showGhToken }) {
                                Icon(
                                    imageVector = if (showGhToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle GitHub PAT visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_pat_input")
                    )

                    if (ghTokenInput.isNotBlank()) {
                        Button(
                            onClick = {
                                onSaveGitHubPat(ghTokenInput)
                                ghTokenInput = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_github_pat_button")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    } else if (uiState.maskedGitHubPat.isNotBlank()) {
                        OutlinedButton(
                            onClick = onClearGitHubPat,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Disconnect")
                        }
                    }

                    OutlinedButton(
                        onClick = { onVerifyGitHub(ghTokenInput.takeIf { it.isNotBlank() }) },
                        enabled = !uiState.isGitHubBusy && (uiState.maskedGitHubPat.isNotBlank() || ghTokenInput.isNotBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("verify_github_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (uiState.isGitHubBusy) "Verifying…" else "Verify Account & Load Repos")
                    }

                    Button(
                        onClick = onOpenQuickGitHubGist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_quick_github_gist_button")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Gist / Repo Commit")
                    }
                }
            }

            // --- Part B: YouTube Data API v3 & OAuth 2.0 PKCE Integration ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = "YouTube Data API v3 (OAuth 2.0 PKCE + Resumable Upload)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                val cleanYtSummary = uiState.youTubeChannelSummary
                                    .replace(" (null)", "")
                                    .replace("(null)", "")
                                    .replace(" null", "")
                                Text(
                                    text = if (cleanYtSummary.isNotBlank()) {
                                        cleanYtSummary
                                    } else if (uiState.maskedYouTubeAccessToken.isNotBlank()) {
                                        "Encrypted OAuth Token stored (${uiState.maskedYouTubeAccessToken})"
                                    } else {
                                        "Not connected — Authorize via OAuth 2.0 PKCE or paste an Access Token"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.maskedYouTubeAccessToken.isNotBlank()) EmeraldConnected
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(onClick = { showPkceWizard = !showPkceWizard }) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showPkceWizard) "Hide PKCE Flow" else "OAuth 2.0 PKCE Flow")
                        }
                    }

                    OutlinedTextField(
                        value = ytAccessTokenInput,
                        onValueChange = { ytAccessTokenInput = it },
                        label = {
                            Text(
                                if (uiState.maskedYouTubeAccessToken.isNotBlank()) {
                                    "YouTube OAuth Token (saved, enter new to replace)"
                                } else {
                                    "YouTube OAuth 2.0 Access Token (ya29...)"
                                },
                                maxLines = 1
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showYtToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showYtToken = !showYtToken }) {
                                Icon(
                                    imageVector = if (showYtToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle YouTube token visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("youtube_access_token_input")
                    )

                    if (ytAccessTokenInput.isNotBlank()) {
                        Button(
                            onClick = {
                                onSaveYouTubeCredentials(ytClientIdInput, ytAccessTokenInput)
                                ytAccessTokenInput = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_youtube_token_button")
                        ) {
                            Text("Save")
                        }
                    } else if (uiState.maskedYouTubeAccessToken.isNotBlank()) {
                        OutlinedButton(
                            onClick = onClearYouTubeCredentials,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Disconnect")
                        }
                    }

                    AnimatedVisibility(visible = showPkceWizard) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "RFC 7636 OAuth 2.0 PKCE Authorization (No Client Secret in APK)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = ytClientIdInput,
                                    onValueChange = { ytClientIdInput = it },
                                    label = { Text("Google Cloud OAuth 2.0 Android/Installed Client ID") },
                                    placeholder = { Text("1234567890-abc.apps.googleusercontent.com") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "S256 Challenge: ${uiState.youTubePkceChallenge.take(22)}…",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = onRegeneratePkce) {
                                        Text("Regenerate PKCE")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val authUrl = YouTubeIntegrationClient.buildAuthorizationUrl(
                                                clientId = ytClientIdInput.ifBlank { "YOUR_GOOGLE_CLIENT_ID" },
                                                codeChallenge = uiState.youTubePkceChallenge
                                            )
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("1. Authorize in Browser")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = ytAuthCodeInput,
                                        onValueChange = { ytAuthCodeInput = it },
                                        label = { Text("2. Paste Authorization Code (4/0A...)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            onExchangePkceCode(ytClientIdInput, ytAuthCodeInput)
                                            ytAuthCodeInput = ""
                                        },
                                        enabled = ytClientIdInput.isNotBlank() && ytAuthCodeInput.isNotBlank() && !uiState.isYouTubeBusy
                                    ) {
                                        Text("Exchange PKCE")
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onVerifyYouTubeChannel(ytAccessTokenInput.takeIf { it.isNotBlank() }) },
                        enabled = !uiState.isYouTubeBusy && (uiState.maskedYouTubeAccessToken.isNotBlank() || ytAccessTokenInput.isNotBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("verify_youtube_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (uiState.isYouTubeBusy) "Checking…" else "Verify YouTube Channel")
                    }

                    Text(
                        text = "Publish from Video Studio → Complete Video → Publish to YouTube",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // --- Part C: Local Integration Audit Log ---
            if (integrationLogs.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manual Integration Audit Log (${integrationLogs.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = onClearIntegrationLogs) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Log")
                    }
                }

                val sdf = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    integrationLogs.take(8).forEach { log ->
                        val isSuccess = log.status == "SUCCESS"
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSuccess) EmeraldConnected.copy(alpha = 0.5f) else CoralError.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        BadgePill(
                                            text = "${log.service} • ${log.actionType}",
                                            color = if (isSuccess) EmeraldConnected else CoralError
                                        )
                                        Text(
                                            text = log.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${log.targetDestination} • ${sdf.format(Date(log.createdAt))} • ${log.details}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (log.externalUrl.isNotBlank()) {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(log.externalUrl))
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy published URL",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(log.externalUrl)).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Launch,
                                                contentDescription = "Open in browser",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
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
