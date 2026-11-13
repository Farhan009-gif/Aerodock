package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.McpApprovalMode
import com.example.data.local.McpTransportType
import com.example.data.mcp.InMemoryEncryptedVaultBridge
import com.example.data.mcp.McpClient
import com.example.data.mcp.McpConfigurationParser
import com.example.data.mcp.McpConnectionManager
import com.example.data.mcp.McpDiagnosticLogger
import com.example.data.mcp.McpLinkConfig
import com.example.data.mcp.McpPluginLoader
import com.example.data.mcp.McpProtocolType
import com.example.data.mcp.McpRepositorySynchronizer
import com.example.data.mcp.McpSystemResult
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun McpSystemStudioSheet(
    onDismiss: () -> Unit,
    onPersistServerToWorkspace: (
        name: String,
        url: String,
        transport: McpTransportType,
        authHeaderName: String,
        rawToken: String?,
        approvalMode: McpApprovalMode
    ) -> Unit,
    onSendFormattedPromptToChat: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val connectionManager = remember {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        val logger = McpDiagnosticLogger()
        val mcpClient = McpClient(okHttp)
        val loader = McpPluginLoader(mcpClient, okHttp, logger)
        val repoSync = McpRepositorySynchronizer(okHttp, logger)
        val vault = InMemoryEncryptedVaultBridge()
        McpConnectionManager(loader, repoSync, vault, logger).also { mgr ->
            // Seed with the two sample link configs so users immediately see multi-protocol servers
            mgr.importFromLinkFile(McpConfigurationParser.sampleYamlMcpTemplate(), "codegraph.mcp")
            mgr.importFromLinkFile(McpConfigurationParser.sampleJsonLinkTemplate(), "telemetry.link")
        }
    }

    val connections by connectionManager.connections.collectAsState()
    val plugins by connectionManager.pluginLoader.registeredPlugins.collectAsState()
    val syncedRepos by connectionManager.syncedRepositories.collectAsState()
    val templates by connectionManager.promptTemplates.collectAsState()
    val history by connectionManager.promptHistory.collectAsState()
    val logs by connectionManager.logger.logs.collectAsState()

    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var statusBanner by rememberSaveable { mutableStateOf("Ready • Multi-Server MCP Link, Plugin & Git Repository System") }

    // Section 0: .mcp / .link Editor State
    var linkFileName by rememberSaveable { mutableStateOf("workspace-server.mcp") }
    var linkRawContent by rememberSaveable { mutableStateOf(McpConfigurationParser.sampleYamlMcpTemplate()) }
    var inlineSecretInput by rememberSaveable { mutableStateOf("") }

    // Section 2: Git Repository Sync State
    var gitRepoUrl by rememberSaveable { mutableStateOf("https://github.com/modelcontextprotocol/servers") }
    var gitBranch by rememberSaveable { mutableStateOf("main") }
    var gitManifestPath by rememberSaveable { mutableStateOf("src/git/plugin.mcp") }
    var gitAuthToken by rememberSaveable { mutableStateOf("") }

    // Section 3: Prompt Router State
    var routerPromptText by rememberSaveable {
        mutableStateOf("Analyze the repository dependency graph and inspect exposed microservice schemas.")
    }
    var selectedServerIdForPrompt by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("mcp_system_studio_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "MCP Connectivity Hub",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "MCP Connectivity & Plugin Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = ".mcp / .link Config • Protocol Plugins • Git Sync • Prompt Router",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }

            // Status Banner
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = statusBanner,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Section Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    "1. .mcp / .link Config",
                    "2. Protocol Plugins (${plugins.size})",
                    "3. Git Repo Sync (${syncedRepos.size})",
                    "4. Prompt Router (${history.size})",
                    "5. System Logs (${logs.size})"
                )
                tabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedSection == index,
                        onClick = { selectedSection = index },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag("mcp_studio_tab_$index")
                    )
                }
            }

            HorizontalDivider()

            when (selectedSection) {
                // ----------------------------------------------------------------
                // TAB 0: .mcp / .link Configuration Parser & Multi-Server Registry
                // ----------------------------------------------------------------
                0 -> {
                    Text(
                        text = "Import .mcp (YAML) or .link (JSON) Server Definition",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                linkFileName = "codegraph.mcp"
                                linkRawContent = McpConfigurationParser.sampleYamlMcpTemplate()
                                statusBanner = "Loaded YAML .mcp template"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load YAML (.mcp)", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                linkFileName = "telemetry.link"
                                linkRawContent = McpConfigurationParser.sampleJsonLinkTemplate()
                                statusBanner = "Loaded JSON .link template"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load JSON (.link)", maxLines = 1)
                        }
                    }

                    OutlinedTextField(
                        value = linkFileName,
                        onValueChange = { linkFileName = it },
                        label = { Text("Link File Name (.mcp or .link)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = linkRawContent,
                        onValueChange = { linkRawContent = it },
                        label = { Text("YAML / JSON Configuration Payload") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mcp_link_payload_input")
                    )

                    OutlinedTextField(
                        value = inlineSecretInput,
                        onValueChange = { inlineSecretInput = it },
                        label = { Text("Optional API Key / Bearer Token (Encrypted in Vault)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            when (val res = connectionManager.importFromLinkFile(linkRawContent, linkFileName)) {
                                is McpSystemResult.Failure -> {
                                    statusBanner = "Validation Error: ${res.error.message}"
                                }
                                is McpSystemResult.Success -> {
                                    val cfg: McpLinkConfig = res.data
                                    if (inlineSecretInput.isNotBlank()) {
                                        connectionManager.registerOrUpdateConfig(cfg, inlineSecretInput)
                                    }
                                    onPersistServerToWorkspace(
                                        cfg.name,
                                        cfg.endpointUrl,
                                        cfg.protocol.mappedTransport,
                                        cfg.auth.headerName,
                                        inlineSecretInput.takeIf { it.isNotBlank() },
                                        cfg.approvalMode
                                    )
                                    statusBanner = "Imported & synced '${cfg.name}' (${cfg.protocol.name}) to Aerodock Connect"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("mcp_import_link_button")
                    ) {
                        Text("Validate, Import & Register MCP Server", maxLines = 1)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Active Multi-Server Connections (${connections.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    connections.forEach { conn ->
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${conn.config.name} (v${conn.config.version})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${conn.config.protocol.displayName} • ${conn.config.endpointUrl}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = conn.config.protocol.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Text(
                                    text = "Plugin: ${conn.activePluginId} • Status: ${conn.statusMessage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val probe = connectionManager.connectServer(conn.config.id)
                                                statusBanner = when (probe) {
                                                    is McpSystemResult.Success ->
                                                        "Connected to ${conn.config.name} (${probe.data.tools.size} tools)"
                                                    is McpSystemResult.Failure ->
                                                        "Probe completed for ${conn.config.name}: ${probe.error.message}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Probe Handshake", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            linkFileName = "${conn.config.name.lowercase().replace(" ", "-")}.mcp"
                                            linkRawContent = McpConfigurationParser.serializeToYamlMcp(conn.config)
                                            statusBanner = "Exported '${conn.config.name}' to YAML .mcp editor"
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Export .mcp", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // TAB 1: Modular Plugin Architecture & Auto-Discovery Registry
                // ----------------------------------------------------------------
                1 -> {
                    Text(
                        text = "Registered MCP Protocol Plugins",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Plugins self-register with McpPluginLoader and handle protocol-specific framing (HTTP JSON-RPC, SSE EventStreams, Full-Duplex WebSockets, and gRPC-Web HTTP/2).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    plugins.forEach { plugin ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${plugin.displayName} (v${plugin.version})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "ID: ${plugin.pluginId} • Source: ${plugin.sourceRepository}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (plugin.supportsStreaming) "Full-Duplex / Server Streaming supported" else "Request-Response JSON-RPC 2.0",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = plugin.protocol.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            connectionManager.pluginLoader.autoRegisterCorePlugins()
                            statusBanner = "Re-verified all ${plugins.size} core protocol plugins"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Auto-Discovery & Verify Protocol Plugins", maxLines = 1)
                    }
                }

                // ----------------------------------------------------------------
                // TAB 2: Git Repository Integration & Versioning
                // ----------------------------------------------------------------
                2 -> {
                    Text(
                        text = "Git Repository MCP Plugin Synchronizer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Clone or fetch `.mcp` plugin manifests directly from public or private Git repositories with semantic version tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = gitRepoUrl,
                        onValueChange = { gitRepoUrl = it },
                        label = { Text("Git Repository URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = gitBranch,
                            onValueChange = { gitBranch = it },
                            label = { Text("Branch / Tag") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = gitManifestPath,
                            onValueChange = { gitManifestPath = it },
                            label = { Text("Manifest Path") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                    }

                    OutlinedTextField(
                        value = gitAuthToken,
                        onValueChange = { gitAuthToken = it },
                        label = { Text("Private Repo PAT / Bearer Token (Optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                statusBanner = "Synchronizing Git repository $gitRepoUrl..."
                                when (
                                    val res = connectionManager.syncAndRegisterGitRepository(
                                        repoUrl = gitRepoUrl,
                                        branch = gitBranch,
                                        manifestPath = gitManifestPath,
                                        authToken = gitAuthToken.takeIf { it.isNotBlank() }
                                    )
                                ) {
                                    is McpSystemResult.Failure -> {
                                        statusBanner = "Git Sync Error: ${res.error.message}"
                                    }
                                    is McpSystemResult.Success -> {
                                        val synced = res.data
                                        synced.discoveredConfigs.forEach { cfg ->
                                            onPersistServerToWorkspace(
                                                cfg.name,
                                                cfg.endpointUrl,
                                                cfg.protocol.mappedTransport,
                                                cfg.auth.headerName,
                                                gitAuthToken.takeIf { it.isNotBlank() },
                                                cfg.approvalMode
                                            )
                                        }
                                        statusBanner =
                                            "Synced ${synced.discoveredConfigs.size} plugin(s) from ${synced.repoUrl} @ ${synced.lastCommitSha}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("mcp_git_sync_button")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch & Synchronize Repository Plugins", maxLines = 1)
                    }

                    syncedRepos.forEach { repo ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = repo.repoUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Branch: ${repo.branch} • Version: v${repo.installedVersion} • Commit: ${repo.lastCommitSha}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Status: ${repo.syncStatus} (${repo.discoveredConfigs.size} MCP server config discovered)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // TAB 3: Workspace Prompt Router, Templates & Execution History
                // ----------------------------------------------------------------
                3 -> {
                    Text(
                        text = "MCP Workspace Prompt Router",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Routes prompts to the best-matching MCP server based on capability tags, protocol plugin, or explicit target selection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Prompt Templates:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        templates.forEach { tpl ->
                            FilterChip(
                                selected = routerPromptText == tpl.templateText,
                                onClick = {
                                    routerPromptText = tpl.templateText
                                    statusBanner = "Applied template '${tpl.title}'"
                                },
                                label = { Text(tpl.title, maxLines = 1) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = routerPromptText,
                        onValueChange = { routerPromptText = it },
                        label = { Text("Prompt to Route Across MCP Servers") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mcp_router_prompt_input")
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                when (
                                    val res = connectionManager.routePrompt(
                                        promptText = routerPromptText,
                                        preferredConfigId = selectedServerIdForPrompt
                                    )
                                ) {
                                    is McpSystemResult.Failure -> {
                                        statusBanner = "Routing failed: ${res.error.message}"
                                    }
                                    is McpSystemResult.Success -> {
                                        val rec = res.data
                                        statusBanner =
                                            "Routed via ${rec.targetServerName} (${rec.protocol.name}) in ${rec.latencyMs}ms"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("mcp_route_prompt_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Route Prompt via MCP Plugin", maxLines = 1)
                    }

                    if (history.isNotEmpty()) {
                        Text(
                            text = "Prompt Routing History (${history.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        history.forEach { item ->
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.targetServerName} • ${item.protocol.name}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${item.latencyMs} ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = item.formattedOutput,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                    if (onSendFormattedPromptToChat != null) {
                                        OutlinedButton(
                                            onClick = {
                                                onSendFormattedPromptToChat(item.formattedOutput)
                                                onDismiss()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Insert Result into Active Chat", maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // TAB 4: Error Handling & Security Audit Logs
                // ----------------------------------------------------------------
                4 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MCP Diagnostic & Security Audit Log",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { connectionManager.logger.clear() }) {
                            Text("Clear")
                        }
                    }

                    logs.forEach { entry ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "[${entry.severity.name}] ${entry.component}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = entry.message,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (entry.details.isNotBlank()) {
                                    Text(
                                        text = entry.details,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
