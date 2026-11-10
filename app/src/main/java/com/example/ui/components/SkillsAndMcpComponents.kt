package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConversationEntity
import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.McpTransportType
import com.example.data.local.SkillEntity
import com.example.data.local.ToolCallLogEntity
import android.webkit.WebView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.mcp.JsSandboxExecutor
import com.example.data.mcp.McpDiscoveryResult
import com.example.data.mcp.RunnerLanguage
import com.example.data.mcp.SkillMarkdownParser
import com.example.data.provider.ModelCapabilityRegistry
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError

import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.OmniUiState
import com.example.ui.viewmodel.PendingToolApprovalRequest
import com.example.ui.viewmodel.ToolApprovalDecision
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SkillsAndMcpSection(
    uiState: OmniUiState,
    skills: List<SkillEntity>,
    mcpServers: List<McpServerEntity>,
    mcpTools: List<McpToolEntity>,
    toolCallLogs: List<ToolCallLogEntity>,
    onSaveSkillMarkdown: (existingId: String?, rawMarkdown: String, fallbackName: String, fallbackDesc: String, sourceUrl: String) -> Unit,
    onToggleSkillEnabled: (SkillEntity) -> Unit,
    onDeleteSkill: (String) -> Unit,
    onImportSkillFromUrl: (String) -> Unit,
    onImportSkillFromUri: (String) -> Unit,
    onSaveMcpServer: (
        existingId: String?,
        name: String,
        url: String,
        transport: McpTransportType,
        authHeaderName: String,
        rawToken: String?,
        approvalMode: McpApprovalMode,
        isEnabled: Boolean
    ) -> Unit,
    onToggleMcpServerEnabled: (McpServerEntity) -> Unit,
    onUpdateMcpServerApprovalMode: (McpServerEntity, McpApprovalMode) -> Unit,
    onClearMcpServerToken: (McpServerEntity) -> Unit,
    onDeleteMcpServer: (String) -> Unit,
    onTestAndDiscoverMcpServer: (McpServerEntity, String?) -> Unit,
    onToggleMcpToolEnabled: (McpToolEntity) -> Unit,
    onClearToolCallLogs: () -> Unit,
    onOpenSafetyApprovalSheet: () -> Unit = {}
) {
    var editingSkill by remember { mutableStateOf<SkillEntity?>(null) }
    var showSkillEditorDialog by rememberSaveable { mutableStateOf(false) }
    var showSkillUrlImportDialog by rememberSaveable { mutableStateOf(false) }

    var editingServer by remember { mutableStateOf<McpServerEntity?>(null) }
    var showServerEditorDialog by rememberSaveable { mutableStateOf(false) }

    val skillFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportSkillFromUri(uri.toString())
        }
    }

    if (showSkillEditorDialog) {
        SkillMarkdownEditorDialog(
            initialSkill = editingSkill,
            onSave = { existingId, rawMd, fallbackName, fallbackDesc ->
                onSaveSkillMarkdown(existingId, rawMd, fallbackName, fallbackDesc, "")
                showSkillEditorDialog = false
                editingSkill = null
            },
            onDismiss = {
                showSkillEditorDialog = false
                editingSkill = null
            }
        )
    }

    if (showSkillUrlImportDialog) {
        SkillUrlImportDialog(
            onImportUrl = { url ->
                onImportSkillFromUrl(url)
                showSkillUrlImportDialog = false
            },
            onDismiss = { showSkillUrlImportDialog = false }
        )
    }

    if (showServerEditorDialog) {
        McpServerEditorDialog(
            initialServer = editingServer,
            maskedToken = editingServer?.id?.let { uiState.maskedMcpTokensByServer[it] }.orEmpty(),
            onSave = { existingId, name, url, transport, authHeader, rawToken, approvalMode, isEnabled ->
                onSaveMcpServer(
                    existingId,
                    name,
                    url,
                    transport,
                    authHeader,
                    rawToken,
                    approvalMode,
                    isEnabled
                )
                showServerEditorDialog = false
                editingServer = null
            },
            onDismiss = {
                showServerEditorDialog = false
                editingServer = null
            }
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("skills_and_mcp_section")
    ) {
        // 1. Skills (SKILL.md) Instruction Packs Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("skills_manager_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
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
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Skills (SKILL.md Instruction Packs)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Markdown instruction packs with YAML frontmatter (name + description) injected into system prompts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val totalEnabledTokens = skills.filter { it.isEnabled }.sumOf { it.estimatedTokens }
                    BadgePill(
                        text = "${skills.count { it.isEnabled }} ACTIVE • ~$totalEnabledTokens TOK",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            editingSkill = null
                            showSkillEditorDialog = true
                        },
                        modifier = Modifier.testTag("create_skill_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create / Paste SKILL.md")
                    }
                    OutlinedButton(
                        onClick = { skillFileLauncher.launch("*/*") },
                        modifier = Modifier.testTag("import_skill_file_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import File")
                    }
                    OutlinedButton(
                        onClick = { showSkillUrlImportDialog = true },
                        modifier = Modifier.testTag("import_skill_url_button")
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import URL")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                if (skills.isEmpty()) {
                    Text(
                        text = "No skills saved yet. Create or import a SKILL.md instruction pack above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        skills.forEach { skill ->
                            SkillItemCard(
                                skill = skill,
                                onToggleEnabled = { onToggleSkillEnabled(skill) },
                                onEdit = {
                                    editingSkill = skill
                                    showSkillEditorDialog = true
                                },
                                onDelete = { onDeleteSkill(skill.id) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Remote MCP Servers Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mcp_servers_manager_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
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
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Remote MCP Servers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Streamable HTTP & SSE JSON-RPC 2.0 servers with Tink AES256-GCM token vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenSafetyApprovalSheet,
                            modifier = Modifier.testTag("open_safety_policies_btn")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Safety Policies")
                        }
                        Button(
                            onClick = {
                                editingServer = null
                                showServerEditorDialog = true
                            },
                            modifier = Modifier.testTag("add_mcp_server_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Server")
                        }
                    }
                }

                // Per-Server Safety Policy Overview Banner
                McpSafetyPolicyOverviewBanner(
                    mcpServers = mcpServers,
                    mcpTools = mcpTools,
                    onOpenSafetyConfig = onOpenSafetyApprovalSheet
                )

                // Required Android Remote-Only Note for stdio servers
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_remote_only_note")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Remote HTTP/SSE Only — stdio servers cannot run on Android",
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Local desktop or CLI MCP servers using stdio must be exposed over Streamable HTTP or SSE via LAN IP, Tailscale, OmniRoute MCP Bridge, or Termux before connecting. Cleartext HTTP URLs follow the same Approved HTTP Host rule as AI providers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (mcpServers.isEmpty()) {
                    Text(
                        text = "No remote MCP servers configured yet. Tap 'Add Server' to connect a Streamable HTTP or SSE MCP endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        mcpServers.forEach { server ->
                            val serverTools = mcpTools.filter { it.serverId == server.id }
                            val discovery = uiState.mcpDiscoverySummaries[server.id]
                            McpServerItemCard(
                                server = server,
                                serverTools = serverTools,
                                maskedToken = uiState.maskedMcpTokensByServer[server.id].orEmpty(),
                                isBusy = uiState.busyMcpServerId == server.id,
                                discoverySummary = discovery,
                                onToggleServerEnabled = { onToggleMcpServerEnabled(server) },
                                onUpdateApprovalMode = { mode -> onUpdateMcpServerApprovalMode(server, mode) },
                                onTestConnection = { overrideToken ->
                                    onTestAndDiscoverMcpServer(server, overrideToken)
                                },
                                onEditServer = {
                                    editingServer = server
                                    showServerEditorDialog = true
                                },
                                onClearToken = { onClearMcpServerToken(server) },
                                onDeleteServer = { onDeleteMcpServer(server.id) },
                                onToggleToolEnabled = onToggleMcpToolEnabled
                            )
                        }
                    }
                }
            }
        }

        // 3. Local Tool Call Audit Log Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mcp_tool_audit_log_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Local MCP Tool Call Audit Log (${toolCallLogs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Every tool call is logged locally with server, tool name, timestamp, duration, and status.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (toolCallLogs.isNotEmpty()) {
                        TextButton(
                            onClick = onClearToolCallLogs,
                            modifier = Modifier.testTag("clear_mcp_tool_logs_button")
                        ) {
                            Text("Clear Log", color = CoralError)
                        }
                    }
                }

                if (toolCallLogs.isEmpty()) {
                    Text(
                        text = "No MCP tool calls executed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        toolCallLogs.take(15).forEach { log ->
                            CollapsibleToolCallCard(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillItemCard(
    skill: SkillEntity,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(skill.id) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (skill.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("skill_card_${skill.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = skill.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        BadgePill(
                            text = "~${skill.estimatedTokens} tok",
                            color = if (skill.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = skill.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.testTag("toggle_skill_${skill.id}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (expanded) "Hide SKILL.md" else "Preview SKILL.md",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("edit_skill_${skill.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit skill", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_skill_${skill.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete skill",
                            tint = CoralError,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = skill.rawMarkdown,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun McpServerItemCard(
    server: McpServerEntity,
    serverTools: List<McpToolEntity>,
    maskedToken: String,
    isBusy: Boolean,
    discoverySummary: McpDiscoveryResult?,
    onToggleServerEnabled: () -> Unit,
    onUpdateApprovalMode: (McpApprovalMode) -> Unit,
    onTestConnection: (String?) -> Unit,
    onEditServer: () -> Unit,
    onClearToken: () -> Unit,
    onDeleteServer: () -> Unit,
    onToggleToolEnabled: (McpToolEntity) -> Unit
) {
    var showToolsList by rememberSaveable(server.id) { mutableStateOf(true) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (server.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mcp_server_card_${server.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        BadgePill(
                            text = server.transportEnum().label,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Status: ${server.lastTestStatus}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (server.lastTestStatus.startsWith("Connected")) EmeraldConnected else AmberWarning
                    )
                }
                Switch(
                    checked = server.isEnabled,
                    onCheckedChange = { onToggleServerEnabled() },
                    modifier = Modifier.testTag("toggle_mcp_server_${server.id}")
                )
            }

            // Encrypted Token Status & Approval Mode Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = if (maskedToken.isNotBlank()) EmeraldConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (maskedToken.isNotBlank()) {
                            "${server.authHeaderName}: $maskedToken (Tink Vault)"
                        } else {
                            "No Auth Token"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (maskedToken.isNotBlank()) {
                    TextButton(
                        onClick = onClearToken,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Clear Token", style = MaterialTheme.typography.labelSmall, color = CoralError)
                    }
                }
            }

            // Safety Approval Mode Selector per Server
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Safety Approval Policy (Write/Delete tools NEVER auto-run):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    McpApprovalMode.entries.forEach { mode ->
                        val selected = server.approvalModeEnum() == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateApprovalMode(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("mcp_approval_${server.id}_${mode.name}")
                        )
                    }
                }
            }

            // Actions Row: Test Connection, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onTestConnection(null) },
                    enabled = !isBusy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("test_mcp_server_btn_${server.id}")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Connection & Discover")
                }

                Row {
                    IconButton(
                        onClick = onEditServer,
                        modifier = Modifier.testTag("edit_mcp_server_${server.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit server", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDeleteServer,
                        modifier = Modifier.testTag("delete_mcp_server_${server.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete server",
                            tint = CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Discovered Resources & Prompts summary if present
            if (discoverySummary != null && (discoverySummary.resourceNames.isNotEmpty() || discoverySummary.promptNames.isNotEmpty())) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (discoverySummary.resourceNames.isNotEmpty()) {
                            Text(
                                text = "Resources (${discoverySummary.resourceNames.size}): ${discoverySummary.resourceNames.joinToString()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (discoverySummary.promptNames.isNotEmpty()) {
                            Text(
                                text = "Prompts (${discoverySummary.promptNames.size}): ${discoverySummary.promptNames.joinToString()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Cached Tools List with Per-Tool Enable Toggles
            if (serverTools.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showToolsList = !showToolsList },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered Tools (${serverTools.count { it.isEnabled }}/${serverTools.size} enabled)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showToolsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showToolsList) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        serverTools.forEach { tool ->
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mcp_tool_row_${tool.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tool.toolName,
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontFamily = JetBrainsMonoFontFamily
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Box(modifier = Modifier.wrapContentWidth()) {
                                                BadgePill(
                                                    text = if (tool.isReadOnlyHint) "READ-ONLY" else "WRITE/DELETE",
                                                    color = if (tool.isReadOnlyHint) EmeraldConnected else CoralError
                                                )
                                            }
                                        }
                                        if (tool.description.isNotBlank()) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = tool.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                    Switch(
                                        checked = tool.isEnabled,
                                        onCheckedChange = { onToggleToolEnabled(tool) },
                                        modifier = Modifier.testTag("toggle_mcp_tool_${tool.id}")
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

@Composable
fun CollapsibleToolCallCard(
    log: ToolCallLogEntity,
    modifier: Modifier = Modifier,
    onOpenHtmlPreview: ((htmlDocument: String, title: String) -> Unit)? = null
) {
    val isCodeExecution = log.toolName == JsSandboxExecutor.RUN_CODE_TOOL_NAME ||
        log.serverId == JsSandboxExecutor.RUN_CODE_SERVER_ID
    val isLiveInfo = com.example.data.mcp.LiveInfoToolsService.isLiveInfoTool(log.toolName) ||
        log.serverId == com.example.data.mcp.LiveInfoToolsService.LIVE_INFO_SERVER_ID
    val cleanResultText = remember(log.resultText) {
        com.example.data.mcp.LiveInfoToolsService.stripSourcesMarker(log.resultText)
    }
    var expanded by rememberSaveable(log.id) { mutableStateOf(isCodeExecution) }
    var showInlinePreview by rememberSaveable(log.id) { mutableStateOf(false) }
    val statusLabel = when {
        log.isSuccess -> "SUCCESS"
        log.resultText.contains("denied", ignoreCase = true) -> "DENIED"
        else -> "FAILED"
    }
    val statusColor = when (statusLabel) {
        "SUCCESS" -> EmeraldConnected
        "DENIED" -> AmberWarning
        else -> CoralError
    }
    val timeLabel = remember(log.createdAt) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.createdAt))
    }
    val confirmDetails = remember(log.argumentsJson, isCodeExecution) {
        if (isCodeExecution) {
            JsSandboxExecutor.buildRunConfirmationDetails(log.argumentsJson)
        } else {
            null
        }
    }
    val isJavaRunner = confirmDetails?.language == RunnerLanguage.JAVA
    val isHtmlCssJsRunner = isCodeExecution && !isJavaRunner
    val previewHtmlDocument = remember(log.argumentsJson, isHtmlCssJsRunner, confirmDetails, cleanResultText) {
        if (isHtmlCssJsRunner && confirmDetails != null) {
            when (confirmDetails.language) {
                RunnerLanguage.HTML -> JsSandboxExecutor.buildFullHtmlDocument(
                    html = confirmDetails.code,
                    css = confirmDetails.css,
                    js = confirmDetails.js
                )
                RunnerLanguage.CSS -> JsSandboxExecutor.buildFullHtmlDocument(
                    html = "<div class=\"preview-root\"><h2>CSS Preview</h2><p>Styled preview container</p></div>",
                    css = confirmDetails.code,
                    js = confirmDetails.js
                )
                else -> JsSandboxExecutor.buildFullHtmlDocument(
                    html = "<div id=\"app\"></div><pre id=\"console-out\">${cleanResultText.replace("<", "&lt;")}</pre>",
                    css = confirmDetails.css.ifBlank { "body { font-family: monospace; padding: 12px; }" },
                    js = confirmDetails.code
                )
            }
        } else {
            null
        }
    }
    val extractedCode = remember(log.argumentsJson, isCodeExecution, confirmDetails) {
        if (isCodeExecution) {
            confirmDetails?.code?.ifBlank { log.argumentsJson } ?: log.argumentsJson
        } else {
            log.argumentsJson
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("collapsible_tool_call_card_${log.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isCodeExecution) Icons.Default.Code else Icons.Default.Build,
                        contentDescription = null,
                        tint = if (isCodeExecution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    BadgePill(
                        text = when {
                            isJavaRunner -> "JAVA RUNNER"
                            isCodeExecution -> "WEBVIEW SANDBOX"
                            isLiveInfo -> "LIVE INFO"
                            else -> "MCP TOOL"
                        },
                        color = if (isCodeExecution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isCodeExecution) {
                            "run_code (${confirmDetails?.languageLabel ?: "JavaScript"})"
                        } else {
                            "${log.serverName} • ${log.toolName}"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BadgePill(text = statusLabel, color = statusColor)
                    Text(
                        text = "${log.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse tool call" else "Expand tool call",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Text(
                        text = when {
                            isJavaRunner -> "Executed at $timeLabel • Remote Java Runner (5s timeout • 10 KB output cap)"
                            isCodeExecution -> "Executed at $timeLabel • Isolated WebView Sandbox (5s timeout • 4 MB limit • 10 KB output cap)"
                            isLiveInfo -> "Executed at $timeLabel • Read-Only HTTPS Live Info (2 KB output cap • Untrusted Data Guard)"
                            else -> "Executed at $timeLabel • ${if (!log.isDestructiveOrWrite) "Read-Only Tool" else "Write/Mutating Tool"}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isCodeExecution) "${confirmDetails?.languageLabel ?: "JavaScript"} Code:" else "Arguments JSON:",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCodeExecution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = extractedCode,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        text = if (isCodeExecution) "Runner Output:" else "Result Output:",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldConnected
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cleanResultText,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            modifier = Modifier
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    }

                    if (isHtmlCssJsRunner && previewHtmlDocument != null && log.isSuccess) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showInlinePreview = !showInlinePreview },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("toggle_inline_html_preview_${log.id}")
                            ) {
                                Text(
                                    text = if (showInlinePreview) "Hide Preview Panel" else "Show Preview Panel",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (onOpenHtmlPreview != null) {
                                Button(
                                    onClick = {
                                        onOpenHtmlPreview(
                                            previewHtmlDocument,
                                            "${confirmDetails?.languageLabel ?: "HTML / CSS / JS"} Preview"
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_html_preview_panel_button_${log.id}")
                                ) {
                                    Text(
                                        text = "Open Full Preview",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                        }

                        if (showInlinePreview) {
                            SandboxedHtmlPreviewCard(
                                htmlDocument = previewHtmlDocument,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .testTag("runner_html_preview_panel_${log.id}")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCodeExecution && !isJavaRunner) Icons.Default.Security else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isCodeExecution && !isJavaRunner) EmeraldConnected else AmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = when {
                                isJavaRunner -> "Java runs on a remote server, so the code leaves the device."
                                isCodeExecution -> "Sandbox destroyed after run. blockNetworkLoads=true, no file/content access, no JS bridges."
                                else -> "Safety note: External MCP tool outputs can contain untrusted or prompt-injection text."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCodeExecution && !isJavaRunner) EmeraldConnected else AmberWarning
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SandboxedHtmlPreviewCard(
    htmlDocument: String,
    modifier: Modifier = Modifier,
    css: String = "",
    js: String = ""
) {
    WebViewPreviewer(
        html = htmlDocument,
        css = css,
        js = js,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSkillsAndToolsSheet(
    conversation: ConversationEntity?,
    skills: List<SkillEntity>,
    mcpServers: List<McpServerEntity>,
    mcpTools: List<McpToolEntity>,
    supportsFunctionCalling: Boolean,
    onToggleConversationSkill: (String) -> Unit,
    onSetAutoSelectSkills: (Boolean) -> Unit,
    onSetMcpToolsEnabled: (Boolean) -> Unit,
    onSetMaxToolIterations: (Int) -> Unit,
    onToggleMcpToolEnabled: (McpToolEntity) -> Unit,
    onOpenIntegrationsHub: () -> Unit,
    onOpenSafetyApprovalSheet: () -> Unit = {},
    onCloneRepoFromGit: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val enabledSkillsForConv = remember(skills, conversation) {
        SkillMarkdownParser.getConversationEnabledSkills(
            allSkills = skills,
            conversation = conversation
        ).map { it.id }.toSet()
    }
    val totalSkillTokens = remember(skills, enabledSkillsForConv) {
        skills.filter { it.id in enabledSkillsForConv }.sumOf { it.estimatedTokens }
    }

    // Fix 4 (Round 3): pure view state for collapsing the long sections —
    // never touches any skill/tool enabled state or the Auto-Select toggle.
    var skillsSectionExpanded by remember { mutableStateOf(true) }
    var mcpSectionExpanded by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("conversation_skills_tools_sheet")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Fix 4 (Round 3): pinned header — stays at the top of the sheet
            // while the sections below scroll, so Close and Collapse all are
            // always reachable without scrolling the whole list.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Thread Skills & MCP Tools",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure per-conversation SKILL.md instruction packs and remote MCP function calling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("conversation_skills_tools_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onCloneRepoFromGit,
                        modifier = Modifier.testTag("sheet_clone_repo_btn")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Clone")
                    }
                    TextButton(onClick = onOpenIntegrationsHub) {
                        Text("Manage All")
                    }
                    TextButton(
                        onClick = {
                            val expand = !(skillsSectionExpanded || mcpSectionExpanded)
                            skillsSectionExpanded = expand
                            mcpSectionExpanded = expand
                        },
                        modifier = Modifier.testTag("conversation_skills_tools_collapse_all_button")
                    ) {
                        Text(if (skillsSectionExpanded || mcpSectionExpanded) "Collapse all" else "Expand all")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

            // Section A: Per-Conversation Skills
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { skillsSectionExpanded = !skillsSectionExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Skills (${enabledSkillsForConv.size}/${skills.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        BadgePill(
                            text = "+~$totalSkillTokens prompt tokens",
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (skillsSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (skillsSectionExpanded) "Collapse skills" else "Expand skills",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (skillsSectionExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Select Matching Skills",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Only inject enabled skills whose name or description matches your prompt keywords to save tokens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = conversation?.autoSelectSkills ?: false,
                            onCheckedChange = onSetAutoSelectSkills,
                            modifier = Modifier.testTag("conv_auto_select_skills_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }

                    if (skillsSectionExpanded) {
                    skills.forEach { skill ->
                        val isChecked = skill.id in enabledSkillsForConv
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = skill.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    BadgePill(text = "~${skill.estimatedTokens} tok", color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    text = skill.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { onToggleConversationSkill(skill.id) },
                                modifier = Modifier.testTag("conv_skill_switch_${skill.id}")
                            )
                        }
                    }
                    }
                }
            }

            // Section B: MCP Tool-Calling Loop
            if (supportsFunctionCalling) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { mcpSectionExpanded = !mcpSectionExpanded }
                            ) {
                                Text(
                                    text = "MCP Function-Calling Loop",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Send enabled MCP tools to the model and automatically loop tool results.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = conversation?.mcpToolsEnabled ?: true,
                                onCheckedChange = onSetMcpToolsEnabled,
                                modifier = Modifier.testTag("conv_mcp_tools_enabled_switch")
                            )
                            Icon(
                                imageVector = if (mcpSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (mcpSectionExpanded) "Collapse MCP tools" else "Expand MCP tools",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (mcpSectionExpanded) {
                        val maxIter = (conversation?.maxToolIterations ?: 12).coerceIn(1, 51)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Max Tool Call Iterations Cap",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = if (maxIter >= 51) "Unlimited" else "$maxIter turns",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = maxIter.toFloat(),
                                onValueChange = { onSetMaxToolIterations(it.toInt()) },
                                valueRange = 1f..51f,
                                steps = 49,
                                modifier = Modifier.testTag("conv_max_tool_iterations_slider")
                            )
                        }

                        Button(
                            onClick = onOpenSafetyApprovalSheet,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.scrim
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("conv_open_safety_approval_sheet_btn")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure Per-Server Safety Approval Modes", fontWeight = FontWeight.SemiBold)
                        }

                        val enabledServerIds = mcpServers.filter { it.isEnabled }.map { it.id }.toSet()
                        val availableTools = mcpTools.filter { it.serverId in enabledServerIds }
                        if (availableTools.isEmpty()) {
                            Text(
                                text = "No tools discovered on enabled MCP servers yet. Open Connect -> Remote MCP Servers and tap 'Test Connection & Discover'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            availableTools.forEach { tool ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tool.toolName,
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontFamily = JetBrainsMonoFontFamily
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Box(modifier = Modifier.wrapContentWidth()) {
                                                BadgePill(
                                                    text = if (tool.isReadOnlyHint) "READ-ONLY" else "WRITE",
                                                    color = if (tool.isReadOnlyHint) EmeraldConnected else CoralError
                                                )
                                            }
                                        }
                                        if (tool.description.isNotBlank()) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = tool.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                    Switch(
                                        checked = tool.isEnabled,
                                        onCheckedChange = { onToggleMcpToolEnabled(tool) }
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "MCP Tools UI is hidden because the active model (${conversation?.modelId ?: ""}) does not support function calling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCallApprovalDialog(
    request: PendingToolApprovalRequest,
    onDecision: (ToolApprovalDecision) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isCodeExecution = request.tool.toolName == JsSandboxExecutor.RUN_CODE_TOOL_NAME ||
        request.server.id == JsSandboxExecutor.RUN_CODE_SERVER_ID
    val confirmDetails = remember(request.callRequest.argumentsJson, isCodeExecution) {
        if (isCodeExecution) {
            JsSandboxExecutor.buildRunConfirmationDetails(request.callRequest.argumentsJson)
        } else {
            null
        }
    }
    val extractedCode = remember(request.callRequest.argumentsJson, isCodeExecution, confirmDetails) {
        if (isCodeExecution) {
            confirmDetails?.code?.ifBlank { request.callRequest.argumentsJson } ?: request.callRequest.argumentsJson
        } else {
            request.callRequest.argumentsJson
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onDecision(ToolApprovalDecision.DENY) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("tool_call_approval_sheet")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            val maxSheetHeight = maxHeight * 0.75f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .wrapContentHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isCodeExecution) Icons.Default.Code else Icons.Default.Security,
                        contentDescription = null,
                        tint = when {
                            isCodeExecution -> MaterialTheme.colorScheme.primary
                            request.tool.isReadOnlyHint -> MaterialTheme.colorScheme.primary
                            else -> CoralError
                        }
                    )
                    Text(
                        text = if (isCodeExecution) "Run Code Confirmation" else "Approve MCP Tool Execution?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isCodeExecution && confirmDetails != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Language",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = confirmDetails.languageLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("run_confirmation_language")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Time limit",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${confirmDetails.timeLimitSeconds} seconds (${confirmDetails.timeLimitMs} ms)",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = EmeraldConnected,
                                    modifier = Modifier.testTag("run_confirmation_time_limit")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Limits",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (confirmDetails.language == RunnerLanguage.JAVA) {
                                        "10 KB output cap"
                                    } else {
                                        "4 MB memory • 10 KB output cap"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("run_confirmation_limits")
                                )
                            }
                        }
                    }

                    if (confirmDetails.isRemoteExecution && !confirmDetails.remoteServerNote.isNullOrBlank()) {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AmberWarning),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_confirmation_java_remote_note")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = confirmDetails.remoteServerNote,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.wrapContentWidth()) {
                            BadgePill(
                                text = request.server.name,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box(modifier = Modifier.wrapContentWidth()) {
                            BadgePill(
                                text = if (request.tool.isReadOnlyHint) "READ-ONLY TOOL" else "WRITE / DELETE TOOL",
                                color = if (request.tool.isReadOnlyHint) EmeraldConnected else CoralError
                            )
                        }
                        Box(modifier = Modifier.wrapContentWidth()) {
                            BadgePill(
                                text = if (request.maxIterations >= 51) "Tool turn ${request.iterationIndex} • Unlimited" else "Step ${request.iterationIndex}/${request.maxIterations}",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Tool: ${request.tool.toolName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        fontWeight = FontWeight.Bold
                    )
                    if (request.tool.description.isNotBlank()) {
                        Text(
                            text = request.tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = if (isCodeExecution) {
                        "Code to run (${confirmDetails?.languageLabel ?: "JavaScript"}):"
                    } else {
                        "Arguments JSON to send:"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCodeExecution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = extractedCode,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .padding(10.dp)
                            .testTag("run_confirmation_code")
                    )
                }

                Surface(
                    color = AmberWarning.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AmberWarning),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                isCodeExecution && confirmDetails?.language == RunnerLanguage.JAVA ->
                                    "Java runs on a remote server, so the code leaves the device. Execution is capped at 10 KB output and a 5-second timeout. Code never runs automatically."
                                isCodeExecution ->
                                    "HTML, CSS, and JavaScript run in an isolated WebView with blockNetworkLoads = true, allowFileAccess = false, allowContentAccess = false, no JavaScript bridges, a 5-second timeout, a 4 MB limit, and 10 KB output cap. Code never runs automatically."
                                else ->
                                    "Prompt-Injection Warning: Tool results returned by external MCP servers are fed back into the model context and may contain untrusted or adversarial prompt-injection instructions."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Button(
                    onClick = { onDecision(ToolApprovalDecision.APPROVE_ONCE) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isCodeExecution -> MaterialTheme.colorScheme.primary
                            request.tool.isReadOnlyHint -> MaterialTheme.colorScheme.primary
                            else -> CoralError
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("approve_tool_once_button")
                ) {
                    Text(
                        text = when {
                            isCodeExecution -> "Run Code"
                            request.tool.isReadOnlyHint -> "Approve & Run"
                            else -> "Confirm Write/Delete"
                        },
                        maxLines = 1,
                        softWrap = false,
                        color = if (isCodeExecution) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.scrim
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onDecision(ToolApprovalDecision.DENY) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("deny_tool_call_button")
                    ) {
                        Text(
                            text = if (isCodeExecution) "Cancel" else "Deny",
                            color = CoralError,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (!isCodeExecution) {
                        OutlinedButton(
                            onClick = { onDecision(ToolApprovalDecision.APPROVE_CONVERSATION_READ_ONLY) },
                            enabled = request.tool.isReadOnlyHint,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("approve_tool_conversation_button")
                        ) {
                            Text(
                                text = "Allow Read-Only for Thread",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlCssJsPreviewPanelSheet(
    htmlDocument: String,
    title: String,
    onDismiss: () -> Unit,
    css: String = "",
    js: String = ""
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentHtml by rememberSaveable(htmlDocument) { mutableStateOf(htmlDocument) }
    var currentCss by rememberSaveable(css) { mutableStateOf(css) }
    var currentJs by rememberSaveable(js) { mutableStateOf(js) }
    var currentTitle by rememberSaveable(title) { mutableStateOf(title) }
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCodeEditorAndTemplates by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("html_css_js_preview_panel_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTitle.ifBlank { "HTML / CSS / JS Preview Panel" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Isolated WebView • blockNetworkLoads=true • allowFileAccess=false • allowContentAccess=false • no JS bridges",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showCodeEditorAndTemplates = !showCodeEditorAndTemplates },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("preview_sheet_toggle_templates_button")
                    ) {
                        Text(
                            text = if (showCodeEditorAndTemplates) "Hide Editor" else "Templates & Code",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("close_html_preview_panel_button")
                    ) {
                        Text("Close")
                    }
                }
            }

            AnimatedVisibility(visible = showCodeEditorAndTemplates) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HtmlCssJsTemplateSelector(
                        selectedTemplateId = selectedTemplateId,
                        onSelectTemplate = { tpl ->
                            selectedTemplateId = tpl.id
                            currentTitle = "${tpl.title} Preview"
                            currentHtml = tpl.html
                            currentCss = tpl.css
                            currentJs = tpl.js
                        }
                    )
                    OutlinedTextField(
                        value = currentHtml,
                        onValueChange = { currentHtml = it },
                        label = { Text("HTML") },
                        minLines = 3,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_sheet_html_input")
                    )
                    OutlinedTextField(
                        value = currentCss,
                        onValueChange = { currentCss = it },
                        label = { Text("CSS") },
                        minLines = 2,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_sheet_css_input")
                    )
                    OutlinedTextField(
                        value = currentJs,
                        onValueChange = { currentJs = it },
                        label = { Text("JS") },
                        minLines = 2,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_sheet_js_input")
                    )
                }
            }

            WebViewPreviewer(
                html = currentHtml,
                css = currentCss,
                js = currentJs,
                title = currentTitle.ifBlank { "Sandboxed HTML / CSS / JS Preview Panel" },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("html_preview_sheet_webview_card")
            )
        }
    }
}

@Composable
private fun SkillMarkdownEditorDialog(
    initialSkill: SkillEntity?,
    onSave: (existingId: String?, rawMarkdown: String, fallbackName: String, fallbackDesc: String) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultMd = remember(initialSkill) {
        initialSkill?.rawMarkdown ?: """
            ---
            name: custom-architect-skill
            description: Domain-specific architecture rules, coding conventions, and security checklists
            ---
            # Custom Architect Skill
            - Follow strict separation of concerns and clean error handling.
            - Prefer immutable data structures and explicit types.
        """.trimIndent()
    }
    var rawMarkdown by rememberSaveable(initialSkill?.id) { mutableStateOf(defaultMd) }
    val parsedPreview = remember(rawMarkdown) {
        SkillMarkdownParser.parseSkillMarkdown(rawMarkdown)
    }
    val estTokens = remember(rawMarkdown) {
        ModelCapabilityRegistry.estimateTokens(rawMarkdown)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialSkill == null) "Create / Paste SKILL.md" else "Edit SKILL.md")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgePill(text = "Name: ${parsedPreview.name}", color = MaterialTheme.colorScheme.primary)
                    BadgePill(text = "~$estTokens tokens", color = EmeraldConnected)
                }
                Text(
                    text = "Description: ${parsedPreview.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = rawMarkdown,
                    onValueChange = { rawMarkdown = it },
                    label = { Text("SKILL.md Content (YAML frontmatter + Markdown body)") },
                    minLines = 10,
                    maxLines = 16,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skill_markdown_editor_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialSkill?.id,
                        rawMarkdown,
                        parsedPreview.name,
                        parsedPreview.description
                    )
                },
                enabled = rawMarkdown.isNotBlank(),
                modifier = Modifier.testTag("save_skill_markdown_button")
            ) {
                Text("Save SKILL.md")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SkillUrlImportDialog(
    onImportUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by rememberSaveable {
        mutableStateOf("https://raw.githubusercontent.com/")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import SKILL.md from URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a raw Markdown URL pointing to a SKILL.md file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("SKILL.md URL") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skill_import_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImportUrl(url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.testTag("confirm_skill_url_import_button")
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun McpServerEditorDialog(
    initialServer: McpServerEntity?,
    maskedToken: String,
    onSave: (
        existingId: String?,
        name: String,
        url: String,
        transport: McpTransportType,
        authHeaderName: String,
        rawToken: String?,
        approvalMode: McpApprovalMode,
        isEnabled: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.name ?: "Local HTTP MCP Bridge")
    }
    var url by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.url ?: "http://10.0.2.2:8787/mcp")
    }
    var transport by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.transportEnum() ?: McpTransportType.STREAMABLE_HTTP)
    }
    var authHeaderName by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.authHeaderName ?: "Authorization")
    }
    var rawToken by rememberSaveable(initialServer?.id) {
        mutableStateOf("")
    }
    var approvalMode by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.approvalModeEnum() ?: McpApprovalMode.ASK_EVERY_TIME)
    }
    var isEnabled by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.isEnabled ?: true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialServer == null) "Add Remote MCP Server" else "Edit Remote MCP Server")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_server_name_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Remote Endpoint URL (HTTPS or Approved HTTP)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_server_url_input")
                )

                Text(
                    text = "Transport Protocol:",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    McpTransportType.entries.forEach { t ->
                        FilterChip(
                            selected = transport == t,
                            onClick = { transport = t },
                            label = { Text(t.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = authHeaderName,
                    onValueChange = { authHeaderName = it },
                    label = { Text("Auth Header Name (e.g. Authorization or X-API-Key)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rawToken,
                    onValueChange = { rawToken = it },
                    label = {
                        Text(
                            if (maskedToken.isNotBlank()) "Bearer / Token (Stored: $maskedToken)"
                            else "Optional Bearer Token / Secret (Encrypted in Tink Vault)"
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_server_token_input")
                )

                Text(
                    text = "Default Approval Mode:",
                    style = MaterialTheme.typography.labelMedium
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    McpApprovalMode.entries.forEach { mode ->
                        FilterChip(
                            selected = approvalMode == mode,
                            onClick = { approvalMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialServer?.id,
                        name,
                        url,
                        transport,
                        authHeaderName,
                        rawToken.takeIf { it.isNotBlank() },
                        approvalMode,
                        isEnabled
                    )
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                modifier = Modifier.testTag("save_mcp_server_button")
            ) {
                Text("Save Server")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
