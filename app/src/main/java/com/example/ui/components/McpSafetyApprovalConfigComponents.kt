package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError

import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily

/**
 * High-visibility banner summarizing per-server safety policies and providing
 * direct access to the Tool Call Safety Configuration UI.
 */
@Composable
fun McpSafetyPolicyOverviewBanner(
    mcpServers: List<McpServerEntity>,
    mcpTools: List<McpToolEntity>,
    onOpenSafetyConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strictCount = mcpServers.count { it.approvalModeEnum() == McpApprovalMode.ASK_EVERY_TIME }
    val sensitiveToolCount = mcpTools.count { !it.isReadOnlyHint }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mcp_safety_policy_banner")
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Tool Call Safety & Confirmation Policies",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Zero-Trust protocol: Sensitive write/delete AI actions always require user approval",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Safety Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (strictCount == mcpServers.size && mcpServers.isNotEmpty()) {
                                "All Servers Strict"
                            } else {
                                "$strictCount/${mcpServers.size} Strict"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Sensitive Tools Gated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$sensitiveToolCount Gated Writes",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (sensitiveToolCount > 0) CoralError else EmeraldConnected,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Button(
                onClick = onOpenSafetyConfig,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.scrim
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_safety_config_sheet_button")
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Configure Per-Server Safety Approval Modes",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Complete Modal Bottom Sheet for configuring per-server safety approval modes,
 * sensitive tool overrides, and testing interactive confirmation prompts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSafetyApprovalSheet(
    mcpServers: List<McpServerEntity>,
    mcpTools: List<McpToolEntity>,
    onUpdateServerApprovalMode: (McpServerEntity, McpApprovalMode) -> Unit,
    onToggleToolReadOnlyOverride: (McpToolEntity) -> Unit,
    onSetAllServersMode: (McpApprovalMode) -> Unit,
    onResetConversationApprovals: () -> Unit,
    onPreviewApprovalDialog: (McpServerEntity, McpToolEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("mcp_safety_approval_sheet")
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            item {
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
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Tool Call Safety Approval Modes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Set user confirmation policies per MCP server before AI actions run.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_safety_approval_sheet")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            // Zero-Trust Guarantee & Prompt Injection Shield Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    border = BorderStroke(1.dp, EmeraldConnected.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EmeraldConnected,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Zero-Trust Enforcement Guarantees",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldConnected,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "• Sensitive Actions NEVER Auto-Run: Any tool call that creates, modifies, writes files, commits code, or deletes data strictly triggers user confirmation regardless of mode.\n• Prompt Injection Barrier: External MCP server responses are untrusted and cannot execute unauthorized secondary tool actions without review.\n• Parameter Inspection: Every confirmation prompt reveals the exact tool name and full JSON payload before execution.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Global Quick-Set Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Global Policy Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSetAllServersMode(McpApprovalMode.ASK_EVERY_TIME) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("set_all_strict_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All: Ask Every Time", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onSetAllServersMode(McpApprovalMode.ALWAYS_ALLOW_READ_ONLY) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("set_all_balanced_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All: Auto Read-Only", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onResetConversationApprovals,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_session_approvals_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset All Remembered Thread Approvals", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Per-Server Configuration Section Header
            item {
                Text(
                    text = "Configured MCP Servers (${mcpServers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (mcpServers.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No remote MCP servers configured yet. Add an MCP server in the Connect tab to configure its tool call approval policies.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(mcpServers, key = { it.id }) { server ->
                    val serverTools = mcpTools.filter { it.serverId == server.id }
                    PerServerSafetyConfigCard(
                        server = server,
                        tools = serverTools,
                        onUpdateApprovalMode = { mode -> onUpdateServerApprovalMode(server, mode) },
                        onToggleToolReadOnlyOverride = onToggleToolReadOnlyOverride,
                        onPreviewApprovalDialog = { tool -> onPreviewApprovalDialog(server, tool) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Individual card for an MCP server presenting its approval mode radio cards,
 * sensitivity badge, tool overrides, and test preview.
 */
@Composable
private fun PerServerSafetyConfigCard(
    server: McpServerEntity,
    tools: List<McpToolEntity>,
    onUpdateApprovalMode: (McpApprovalMode) -> Unit,
    onToggleToolReadOnlyOverride: (McpToolEntity) -> Unit,
    onPreviewApprovalDialog: (McpToolEntity) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val currentMode = server.approvalModeEnum()
    val sensitiveCount = tools.count { !it.isReadOnlyHint }
    val readOnlyCount = tools.count { it.isReadOnlyHint }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (server.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("per_server_safety_card_${server.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Server Header
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        BadgePill(
                            text = server.transportEnum().label,
                            color = MaterialTheme.colorScheme.primary
                        )
                        BadgePill(
                            text = when (currentMode) {
                                McpApprovalMode.ASK_EVERY_TIME -> "STRICT CONFIRMATION"
                                McpApprovalMode.ASK_ONCE_PER_CONVERSATION -> "SESSION SCOPED"
                                McpApprovalMode.ALWAYS_ALLOW_READ_ONLY -> "AUTO READ-ONLY"
                            },
                            color = when (currentMode) {
                                McpApprovalMode.ASK_EVERY_TIME -> MaterialTheme.colorScheme.primary
                                McpApprovalMode.ASK_ONCE_PER_CONVERSATION -> MaterialTheme.colorScheme.primary
                                McpApprovalMode.ALWAYS_ALLOW_READ_ONLY -> EmeraldConnected
                            }
                        )
                    }
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("toggle_expand_server_${server.id}")
                ) {
                    Text(if (isExpanded) "Collapse" else "Expand")
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                Text(
                    text = "Select Safety Approval Mode for ${server.name}:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // 3 Approval Mode Selection Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SafetyModeOptionCard(
                        mode = McpApprovalMode.ASK_EVERY_TIME,
                        isSelected = currentMode == McpApprovalMode.ASK_EVERY_TIME,
                        title = "Ask Every Time (Maximum Security)",
                        badge = "RECOMMENDED FOR UNTRUSTED SERVERS",
                        badgeColor = MaterialTheme.colorScheme.primary,
                        description = "Strict manual review: requires user confirmation before ANY tool call runs (reads, writes, and deletes). Zero automatic execution.",
                        onClick = { onUpdateApprovalMode(McpApprovalMode.ASK_EVERY_TIME) },
                        testTag = "mode_ask_every_time_${server.id}"
                    )

                    SafetyModeOptionCard(
                        mode = McpApprovalMode.ASK_ONCE_PER_CONVERSATION,
                        isSelected = currentMode == McpApprovalMode.ASK_ONCE_PER_CONVERSATION,
                        title = "Ask Once Per Chat Thread",
                        badge = "SESSION-SCOPED READS",
                        badgeColor = MaterialTheme.colorScheme.primary,
                        description = "Confirms the first read-only call in each chat thread, then remembers it for that session. All sensitive write/delete actions ALWAYS prompt every time.",
                        onClick = { onUpdateApprovalMode(McpApprovalMode.ASK_ONCE_PER_CONVERSATION) },
                        testTag = "mode_ask_once_${server.id}"
                    )

                    SafetyModeOptionCard(
                        mode = McpApprovalMode.ALWAYS_ALLOW_READ_ONLY,
                        isSelected = currentMode == McpApprovalMode.ALWAYS_ALLOW_READ_ONLY,
                        title = "Auto Read-Only (Balanced Automation)",
                        badge = "SENSITIVE WRITES STRICTLY GATED",
                        badgeColor = EmeraldConnected,
                        description = "Safe read-only queries run automatically without interrupting flow. Any write, command, or delete operation strictly requires user confirmation.",
                        onClick = { onUpdateApprovalMode(McpApprovalMode.ALWAYS_ALLOW_READ_ONLY) },
                        testTag = "mode_auto_read_${server.id}"
                    )
                }

                // Discovered Tools Breakdown & Manual Sensitivity Override
                if (tools.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tool Sensitivity Classification (${tools.size} tools)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "$readOnlyCount Safe Reads • $sensitiveCount Sensitive Gated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tools.forEach { tool ->
                            val isSensitive = !tool.isReadOnlyHint
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSensitive) CoralError.copy(alpha = 0.5f) else EmeraldConnected.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
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
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Box(modifier = Modifier.wrapContentWidth()) {
                                                BadgePill(
                                                    text = if (isSensitive) "SENSITIVE ACTION (GATED)" else "SAFE READ-ONLY",
                                                    color = if (isSensitive) CoralError else EmeraldConnected
                                                )
                                            }
                                        }
                                        if (tool.description.isNotBlank()) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = tool.description,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Preview Confirmation Dialog Button
                                        IconButton(
                                            onClick = { onPreviewApprovalDialog(tool) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("preview_tool_${tool.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Test Approval Dialog",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Toggle sensitivity override
                                        OutlinedButton(
                                            onClick = { onToggleToolReadOnlyOverride(tool) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .testTag("toggle_sensitive_${tool.id}")
                                        ) {
                                            Text(
                                                text = if (isSensitive) "Make Read" else "Make Sensitive",
                                                style = MaterialTheme.typography.labelSmall
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

@Composable
private fun SafetyModeOptionCard(
    mode: McpApprovalMode,
    isSelected: Boolean,
    title: String,
    badge: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    description: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(20.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.scrim,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    BadgePill(
                        text = badge,
                        color = badgeColor
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
