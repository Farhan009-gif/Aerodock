package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.McpTransportType
import com.example.data.mcp.ConnectorApprovalPolicy
import com.example.data.mcp.McpConnectorPreset
import com.example.data.mcp.ToolApprovalChoice
import com.example.ui.components.BadgePill
import com.example.ui.components.LucideIcons
import com.example.ui.components.MinimalButton
import com.example.ui.components.MinimalCard
import com.example.ui.components.MinimalChip
import com.example.ui.components.MinimalSectionHeader
import com.example.ui.components.MinimalStatusDot
import com.example.ui.components.MinimalToggle
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily

/**
 * Pending confirmation state when a user tries to set a write or delete tool
 * (or "Set all tools" on a connector containing write/delete tools) to "Always".
 */
private sealed class PendingWriteToolConfirmation {
    data class SingleTool(val tool: McpToolEntity) : PendingWriteToolConfirmation()
    data class SetAllToolsInConnector(
        val server: McpServerEntity,
        val writeToolsCount: Int
    ) : PendingWriteToolConfirmation()
}

/**
 * Main Connectors screen & Connector detail view.
 *
 * Features:
 * - Top bar with back arrow, title "Connectors", and a + button to add a connector.
 * - "Connector discovery" card with a toggle and the text:
 *   "Runway will help you find available connectors in your directory."
 * - List of connected connectors showing each connector's icon, name, and a badge with the tool count.
 * - Tapping a connector opens its detail screen with:
 *   - "Set all tools" row showing the current default (Always / Ask / Never) and applying it to every tool.
 *   - List of every tool in the connector, each with its own Always / Ask / Never choice.
 *   - Connect or disconnect, and a "Test connection" action.
 * - Write and delete tools default to "Ask" and cannot be set to "Always" without a confirmation dialog.
 */
@Composable
fun ConnectorsScreen(
    mcpServers: List<McpServerEntity>,
    mcpTools: List<McpToolEntity>,
    connectorDiscoveryEnabled: Boolean,
    selectedConnectorId: String?,
    connectorDefaultPolicies: Map<String, ToolApprovalChoice>,
    perToolPolicies: Map<String, ToolApprovalChoice>,
    confirmedWriteAlwaysToolIds: Set<String>,
    maskedTokensByServer: Map<String, String>,
    approvedCleartextHosts: Set<String>,
    busyServerId: String?,
    onBack: () -> Unit,
    onSelectConnector: (String?) -> Unit,
    onToggleConnectorDiscovery: (Boolean) -> Unit,
    onAddAndConnectMcpServer: (
        name: String,
        url: String,
        transport: McpTransportType,
        authHeaderName: String,
        rawToken: String?
    ) -> Unit,
    onToggleConnectorConnected: (McpServerEntity) -> Unit,
    onTestConnectionAndDiscover: (McpServerEntity, String?) -> Unit,
    onUpdateConnectorToken: (McpServerEntity, String) -> Unit,
    onClearConnectorToken: (McpServerEntity) -> Unit,
    onDeleteConnector: (String) -> Unit,
    onSetAllToolsChoice: (
        server: McpServerEntity,
        choice: ToolApprovalChoice,
        confirmedWriteTools: Boolean
    ) -> Unit,
    onSetSingleToolChoice: (
        tool: McpToolEntity,
        choice: ToolApprovalChoice,
        confirmedWriteTool: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddConnectorDialog by rememberSaveable { mutableStateOf(false) }
    var initialPresetForAdd by remember { mutableStateOf<McpConnectorPreset?>(null) }
    var pendingWriteConfirmation by remember { mutableStateOf<PendingWriteToolConfirmation?>(null) }

    val selectedServer = remember(mcpServers, selectedConnectorId) {
        selectedConnectorId?.let { id -> mcpServers.firstOrNull { it.id == id } }
    }

    BackHandler {
        if (selectedServer != null) {
            onSelectConnector(null)
        } else {
            onBack()
        }
    }

    // Write / Delete tool confirmation dialog before allowing "Always"
    pendingWriteConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingWriteConfirmation = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.AlertTriangle,
                        contentDescription = null,
                        tint = CoralError,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Allow write or delete without asking?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                val msg = when (pending) {
                    is PendingWriteToolConfirmation.SingleTool ->
                        "'${pending.tool.toolName}' can modify or delete remote data. Write and delete tools default to \"Ask\". Are you sure you want to set this tool to \"Always\" so it runs without asking?"
                    is PendingWriteToolConfirmation.SetAllToolsInConnector ->
                        "'${pending.server.name}' includes ${pending.writeToolsCount} write or delete tool(s) that can modify or delete data. Do you want to set all tools (including write/delete tools) to \"Always\"?"
                }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                MinimalButton(
                    text = "Confirm Always",
                    onClick = {
                        when (pending) {
                            is PendingWriteToolConfirmation.SingleTool -> {
                                onSetSingleToolChoice(
                                    pending.tool,
                                    ToolApprovalChoice.ALWAYS,
                                    true
                                )
                            }
                            is PendingWriteToolConfirmation.SetAllToolsInConnector -> {
                                onSetAllToolsChoice(
                                    pending.server,
                                    ToolApprovalChoice.ALWAYS,
                                    true
                                )
                            }
                        }
                        pendingWriteConfirmation = null
                    },
                    filled = true,
                    modifier = Modifier.testTag("confirm_write_always_dialog_button")
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingWriteConfirmation = null },
                    modifier = Modifier.testTag("cancel_write_always_dialog_button")
                ) {
                    Text("Keep Ask", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showAddConnectorDialog) {
        AddMcpConnectorDialog(
            initialPreset = initialPresetForAdd,
            approvedCleartextHosts = approvedCleartextHosts,
            onDismiss = {
                showAddConnectorDialog = false
                initialPresetForAdd = null
            },
            onConnect = { name, url, transport, authHeader, rawToken ->
                onAddAndConnectMcpServer(name, url, transport, authHeader, rawToken)
                showAddConnectorDialog = false
                initialPresetForAdd = null
            }
        )
    }

    if (selectedServer != null) {
        val serverTools = remember(mcpTools, selectedServer.id) {
            mcpTools.filter { it.serverId == selectedServer.id }
        }
        val currentDefaultChoice = connectorDefaultPolicies[selectedServer.id]
            ?: ConnectorApprovalPolicy.mapLegacyServerModeToChoice(selectedServer.approvalModeEnum())

        ConnectorDetailView(
            server = selectedServer,
            tools = serverTools,
            currentDefaultChoice = currentDefaultChoice,
            connectorDefaultPolicies = connectorDefaultPolicies,
            perToolPolicies = perToolPolicies,
            confirmedWriteAlwaysToolIds = confirmedWriteAlwaysToolIds,
            maskedToken = maskedTokensByServer[selectedServer.id].orEmpty(),
            isBusy = busyServerId == selectedServer.id,
            onBackToConnectorsList = { onSelectConnector(null) },
            onToggleConnected = { onToggleConnectorConnected(selectedServer) },
            onTestConnection = { overrideToken ->
                onTestConnectionAndDiscover(selectedServer, overrideToken)
            },
            onUpdateToken = { newToken ->
                onUpdateConnectorToken(selectedServer, newToken)
            },
            onClearToken = {
                onClearConnectorToken(selectedServer)
            },
            onDeleteConnector = {
                val idToRemove = selectedServer.id
                onSelectConnector(null)
                onDeleteConnector(idToRemove)
            },
            onSelectAllToolsChoice = { choice ->
                if (ConnectorApprovalPolicy.requiresConfirmationForSetAllTools(serverTools, choice)) {
                    val writeCount = serverTools.count { ConnectorApprovalPolicy.isWriteOrDeleteTool(it) }
                    pendingWriteConfirmation = PendingWriteToolConfirmation.SetAllToolsInConnector(
                        server = selectedServer,
                        writeToolsCount = writeCount
                    )
                } else {
                    onSetAllToolsChoice(selectedServer, choice, false)
                }
            },
            onSelectSingleToolChoice = { tool, choice ->
                if (ConnectorApprovalPolicy.requiresConfirmationToSetToolChoice(tool, choice)) {
                    pendingWriteConfirmation = PendingWriteToolConfirmation.SingleTool(tool)
                } else {
                    onSetSingleToolChoice(tool, choice, false)
                }
            },
            modifier = modifier
        )
    } else {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("connectors_screen"),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ConnectorsTopBar(
                    title = "Connectors",
                    onBack = onBack,
                    onAddClick = {
                        initialPresetForAdd = null
                        showAddConnectorDialog = true
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("connectors_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. "Connector discovery" card with toggle and required text
                item(key = "connector_discovery_card") {
                    MinimalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connector_discovery_card")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = LucideIcons.Compass,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Connector discovery",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Runway will help you find available connectors in your directory.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                MinimalToggle(
                                    checked = connectorDiscoveryEnabled,
                                    onCheckedChange = onToggleConnectorDiscovery,
                                    testTag = "connector_discovery_toggle"
                                )
                            }

                            AnimatedVisibility(visible = connectorDiscoveryEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        text = "Available in directory",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ConnectorApprovalPolicy.DIRECTORY_PRESETS.forEach { preset ->
                                        val alreadyAdded = mcpServers.any {
                                            it.url.trim().equals(preset.serverUrl.trim(), ignoreCase = true) ||
                                                it.name.trim().equals(preset.name, ignoreCase = true)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.background)
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = LucideIcons.GitBranch,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = preset.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Medium
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = preset.serverUrl,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = JetBrainsMonoFontFamily
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            MinimalChip(
                                                label = if (alreadyAdded) "Configured" else "Set up",
                                                selected = !alreadyAdded,
                                                onClick = {
                                                    val existing = mcpServers.firstOrNull {
                                                        it.url.trim().equals(preset.serverUrl.trim(), ignoreCase = true) ||
                                                            it.name.trim().equals(preset.name, ignoreCase = true)
                                                    }
                                                    if (existing != null) {
                                                        onSelectConnector(existing.id)
                                                    } else {
                                                        initialPresetForAdd = preset
                                                        showAddConnectorDialog = true
                                                    }
                                                },
                                                modifier = Modifier.testTag("directory_preset_${preset.id}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Connected Connectors Section Header
                item(key = "connected_connectors_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MinimalSectionHeader(title = "Connected connectors")
                        Text(
                            text = "${mcpServers.count { it.isEnabled }} active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (mcpServers.isEmpty()) {
                    item(key = "empty_connectors_state") {
                        MinimalCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = LucideIcons.Plug,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = "No connectors added yet",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap + to connect a remote MCP server or add the GitHub connector.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                MinimalButton(
                                    text = "Add Connector",
                                    icon = LucideIcons.Plus,
                                    onClick = {
                                        initialPresetForAdd = null
                                        showAddConnectorDialog = true
                                    },
                                    filled = true
                                )
                            }
                        }
                    }
                } else {
                    items(mcpServers, key = { it.id }) { server ->
                        val toolCount = mcpTools.count { it.serverId == server.id }
                        ConnectorListRow(
                            server = server,
                            toolCount = toolCount,
                            isBusy = busyServerId == server.id,
                            onClick = { onSelectConnector(server.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectorsTopBar(
    title: String,
    onBack: () -> Unit,
    onAddClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("connectors_back_button")
                    ) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("connectors_add_button")
                    ) {
                        Icon(
                            imageVector = LucideIcons.Plus,
                            contentDescription = "Add connector",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else if (trailingContent != null) {
                    trailingContent()
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ConnectorListRow(
    server: McpServerEntity,
    toolCount: Int,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    val isGitHub = server.name.contains("github", ignoreCase = true) ||
        server.url.contains("githubcopilot.com", ignoreCase = true)

    MinimalCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("connector_row_${server.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Connector Icon
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (server.isEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isGitHub) LucideIcons.GitBranch else LucideIcons.Server,
                            contentDescription = server.name,
                            tint = if (server.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        MinimalStatusDot(isConnected = server.isEnabled)
                    }
                    Text(
                        text = "${server.transportEnum().label} · ${server.url}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Badge with the number of tools it exposes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                BadgePill(
                    text = "$toolCount ${if (toolCount == 1) "tool" else "tools"}",
                    color = if (toolCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("connector_tool_badge_${server.id}")
                )
                Icon(
                    imageVector = LucideIcons.ChevronRight,
                    contentDescription = "Open connector details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectorDetailView(
    server: McpServerEntity,
    tools: List<McpToolEntity>,
    currentDefaultChoice: ToolApprovalChoice,
    connectorDefaultPolicies: Map<String, ToolApprovalChoice>,
    perToolPolicies: Map<String, ToolApprovalChoice>,
    confirmedWriteAlwaysToolIds: Set<String>,
    maskedToken: String,
    isBusy: Boolean,
    onBackToConnectorsList: () -> Unit,
    onToggleConnected: () -> Unit,
    onTestConnection: (overrideToken: String?) -> Unit,
    onUpdateToken: (String) -> Unit,
    onClearToken: () -> Unit,
    onDeleteConnector: () -> Unit,
    onSelectAllToolsChoice: (ToolApprovalChoice) -> Unit,
    onSelectSingleToolChoice: (McpToolEntity, ToolApprovalChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTokenDialog by rememberSaveable { mutableStateOf(false) }

    if (showTokenDialog) {
        var newTokenInput by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Update Auth Token", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Stored encrypted in the hardware-backed Tink vault. The full token is never shown after saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newTokenInput,
                        onValueChange = { newTokenInput = it },
                        placeholder = { Text("Bearer token / PAT") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connector_detail_token_input")
                    )
                }
            },
            confirmButton = {
                MinimalButton(
                    text = "Save Encrypted",
                    onClick = {
                        if (newTokenInput.isNotBlank()) {
                            onUpdateToken(newTokenInput.trim())
                        }
                        showTokenDialog = false
                    },
                    enabled = newTokenInput.isNotBlank(),
                    filled = true
                )
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("connector_detail_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ConnectorsTopBar(
                title = server.name,
                onBack = onBackToConnectorsList,
                trailingContent = {
                    IconButton(
                        onClick = onDeleteConnector,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("connector_detail_delete_button")
                    ) {
                        Icon(
                            imageVector = LucideIcons.Trash2,
                            contentDescription = "Delete connector",
                            tint = CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Connection & Test Connection Card
            item(key = "connector_connection_card") {
                MinimalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    MinimalStatusDot(isConnected = server.isEnabled)
                                    Text(
                                        text = if (server.isEnabled) "Connected" else "Disconnected",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    BadgePill(
                                        text = "${tools.size} ${if (tools.size == 1) "tool" else "tools"}",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = server.url,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Transport: ${server.transportEnum().label} · Status: ${server.lastTestStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Auth token status row (masked only, never full token)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Encrypted Auth Token (Tink Vault)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (maskedToken.isNotBlank()) {
                                        "Saved: $maskedToken"
                                    } else {
                                        "No token configured"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MinimalChip(
                                    label = if (maskedToken.isNotBlank()) "Replace token" else "Add token",
                                    onClick = { showTokenDialog = true }
                                )
                                if (maskedToken.isNotBlank()) {
                                    MinimalChip(
                                        label = "Clear",
                                        onClick = onClearToken
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        // Connect / Disconnect and "Test connection" actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MinimalButton(
                                text = if (server.isEnabled) "Disconnect" else "Connect",
                                onClick = onToggleConnected,
                                filled = !server.isEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("connector_connect_disconnect_button")
                            )
                            MinimalButton(
                                text = if (isBusy) "Testing…" else "Test connection",
                                icon = LucideIcons.RefreshCw,
                                onClick = { onTestConnection(null) },
                                enabled = !isBusy,
                                filled = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("connector_test_connection_button")
                            )
                        }
                    }
                }
            }

            // 2. "Set all tools" row showing the current default (Always / Ask / Never)
            item(key = "set_all_tools_card") {
                MinimalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connector_set_all_tools_row")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Set all tools",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Current default: ${currentDefaultChoice.label} (${currentDefaultChoice.description})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BadgePill(
                                text = currentDefaultChoice.label,
                                color = choiceColor(currentDefaultChoice)
                            )
                        }

                        ApprovalChoiceSegmentedRow(
                            selectedChoice = currentDefaultChoice,
                            onSelectChoice = onSelectAllToolsChoice,
                            testTagPrefix = "set_all_tools_choice"
                        )

                        Text(
                            text = "Note: Write and delete tools default to \"Ask\" and require confirmation before being set to \"Always\".",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Tools List Header
            item(key = "connector_tools_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinimalSectionHeader(title = "Tools (${tools.size})")
                    Text(
                        text = "Always / Ask / Never",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tools.isEmpty()) {
                item(key = "no_discovered_tools_card") {
                    MinimalCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No tools discovered yet",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap \"Test connection\" above to run MCP tool discovery on ${server.name}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(tools, key = { it.id }) { tool ->
                    val effectiveChoice = ConnectorApprovalPolicy.resolveToolChoice(
                        tool = tool,
                        server = server,
                        connectorDefaultPolicies = connectorDefaultPolicies,
                        perToolPolicies = perToolPolicies,
                        confirmedWriteAlwaysToolIds = confirmedWriteAlwaysToolIds
                    )
                    val isWriteOrDelete = ConnectorApprovalPolicy.isWriteOrDeleteTool(tool)

                    ConnectorToolPolicyCard(
                        tool = tool,
                        isWriteOrDelete = isWriteOrDelete,
                        selectedChoice = effectiveChoice,
                        onSelectChoice = { targetChoice ->
                            onSelectSingleToolChoice(tool, targetChoice)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectorToolPolicyCard(
    tool: McpToolEntity,
    isWriteOrDelete: Boolean,
    selectedChoice: ToolApprovalChoice,
    onSelectChoice: (ToolApprovalChoice) -> Unit
) {
    MinimalCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connector_tool_row_${tool.toolName}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tool.toolName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        BadgePill(
                            text = if (isWriteOrDelete) "Write / Delete" else "Read-only",
                            color = if (isWriteOrDelete) AmberWarning else EmeraldConnected
                        )
                    }
                    if (tool.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ApprovalChoiceSegmentedRow(
                selectedChoice = selectedChoice,
                onSelectChoice = onSelectChoice,
                testTagPrefix = "tool_choice_${tool.toolName}"
            )
        }
    }
}

@Composable
private fun ApprovalChoiceSegmentedRow(
    selectedChoice: ToolApprovalChoice,
    onSelectChoice: (ToolApprovalChoice) -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolApprovalChoice.entries.forEach { choice ->
            val isSelected = selectedChoice == choice
            val activeColor = choiceColor(choice)
            Surface(
                onClick = { onSelectChoice(choice) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) activeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) activeColor else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .testTag("${testTagPrefix}_${choice.name.lowercase()}")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                ) {
                    Text(
                        text = choice.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun choiceColor(choice: ToolApprovalChoice): Color {
    return when (choice) {
        ToolApprovalChoice.ALWAYS -> EmeraldConnected
        ToolApprovalChoice.ASK -> MaterialTheme.colorScheme.primary
        ToolApprovalChoice.NEVER -> CoralError
    }
}

/**
 * Dialog opened by the "+" button on the Connectors screen:
 * - Preset selector including GitHub (`https://api.githubcopilot.com/mcp/`, Bearer token,
 *   short note asking the user to use a read-only fine-grained personal access token)
 *   and Custom Remote MCP Server.
 * - URL input validated for HTTPS or HTTP only for approved hosts.
 * - Transport selector: Streamable HTTP or SSE.
 * - Auth token field stored encrypted in TinkSecretVault, never shown in plain text after saving.
 */
@Composable
private fun AddMcpConnectorDialog(
    initialPreset: McpConnectorPreset?,
    approvedCleartextHosts: Set<String>,
    onDismiss: () -> Unit,
    onConnect: (
        name: String,
        url: String,
        transport: McpTransportType,
        authHeaderName: String,
        rawToken: String?
    ) -> Unit
) {
    val gitHubPreset = ConnectorApprovalPolicy.GITHUB_PRESET
    var selectedPresetId by rememberSaveable(initialPreset?.id) {
        mutableStateOf(initialPreset?.id ?: "custom")
    }
    var name by rememberSaveable(initialPreset?.id) {
        mutableStateOf(initialPreset?.name.orEmpty())
    }
    var url by rememberSaveable(initialPreset?.id) {
        mutableStateOf(initialPreset?.serverUrl ?: "https://")
    }
    var transport by rememberSaveable(initialPreset?.id) {
        mutableStateOf(initialPreset?.transport ?: McpTransportType.STREAMABLE_HTTP)
    }
    var authToken by rememberSaveable(initialPreset?.id) {
        mutableStateOf("")
    }

    val urlValidation = remember(url, approvedCleartextHosts) {
        ConnectorApprovalPolicy.validateMcpServerUrl(url, approvedCleartextHosts)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Add Connector",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Preset selector (GitHub vs Custom MCP)
                Text(
                    text = "Preset",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinimalChip(
                        label = "GitHub MCP",
                        selected = selectedPresetId == gitHubPreset.id,
                        onClick = {
                            selectedPresetId = gitHubPreset.id
                            name = gitHubPreset.name
                            url = gitHubPreset.serverUrl
                            transport = gitHubPreset.transport
                        },
                        modifier = Modifier.testTag("add_connector_preset_github")
                    )
                    MinimalChip(
                        label = "Custom MCP",
                        selected = selectedPresetId == "custom",
                        onClick = {
                            selectedPresetId = "custom"
                            if (url == gitHubPreset.serverUrl) {
                                name = ""
                                url = "https://"
                            }
                        },
                        modifier = Modifier.testTag("add_connector_preset_custom")
                    )
                }

                // Short note for GitHub preset asking user to use a read-only fine-grained PAT
                if (selectedPresetId == gitHubPreset.id ||
                    url.trim().startsWith(ConnectorApprovalPolicy.GITHUB_MCP_SERVER_URL, ignoreCase = true)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_pat_readonly_note")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = LucideIcons.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "GitHub MCP: Uses Bearer token auth to https://api.githubcopilot.com/mcp/. Please use a read-only fine-grained personal access token.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Connector Name") },
                    placeholder = { Text("e.g. GitHub") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_connector_name_input")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL (HTTPS or approved HTTP)") },
                    placeholder = { Text("https://api.githubcopilot.com/mcp/") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_connector_url_input")
                )

                if (url.isNotBlank() && url != "https://" && !urlValidation.isValid) {
                    Text(
                        text = urlValidation.errorMessage ?: "Invalid URL",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralError
                    )
                }

                // Transport Selector: Streamable HTTP or SSE
                Text(
                    text = "Transport",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    McpTransportType.entries.forEach { t ->
                        val label = when (t) {
                            McpTransportType.STREAMABLE_HTTP -> "Streamable HTTP"
                            McpTransportType.SSE -> "SSE"
                            McpTransportType.WEBSOCKET -> "WebSocket"
                            McpTransportType.GRPC -> "gRPC"
                        }
                        MinimalChip(
                            label = label,
                            selected = transport == t,
                            onClick = { transport = t },
                            modifier = Modifier.testTag("add_connector_transport_${t.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = authToken,
                    onValueChange = { authToken = it },
                    label = { Text("Bearer Auth Token (Encrypted in Tink Vault)") },
                    placeholder = { Text("github_pat_... or Bearer token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_connector_token_input")
                )
                Text(
                    text = "Encrypted with AES256-GCM in the Android Keystore Tink vault. Never stored in plain text or logs, and never shown in full after saving.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            MinimalButton(
                text = "Connect & Discover",
                onClick = {
                    onConnect(
                        name.trim().ifBlank { "MCP Connector" },
                        url.trim(),
                        transport,
                        "Authorization",
                        authToken.trim().takeIf { it.isNotEmpty() }
                    )
                },
                enabled = name.isNotBlank() && urlValidation.isValid,
                filled = true,
                modifier = Modifier.testTag("add_connector_submit_button")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
