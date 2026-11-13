package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.agent.OmniAgentAction
import com.example.data.agent.OmniAgentExecutionRecord
import com.example.data.agent.OmniAgentUiState
import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.SkillEntity

import com.example.ui.viewmodel.OmniUiState

/**
 * Dedicated Omni Autopilot Tab Screen.
 * Replaces the floating Omni Agent pill and houses 1-Command Control, Daemon Status,
 * Live Actions with Undo, Quick Actions, Collapsible History, Skills & MCP Tools toggles, and Safety settings.
 */
@Composable
fun OmniAutopilotScreen(
    agentState: OmniAgentUiState,
    studioUiState: OmniUiState,
    activeProviderName: String,
    activeModelName: String,
    skills: List<SkillEntity>,
    mcpTools: List<McpToolEntity>,
    mcpServers: List<McpServerEntity>,
    maxToolIterations: Int,
    previewParsedActions: (String) -> List<OmniAgentAction>,
    onCommandInputChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onToggleDaemon: (Boolean) -> Unit,
    onToggleSkill: (SkillEntity) -> Unit,
    onToggleMcpTool: (McpToolEntity) -> Unit,
    onSetMaxToolIterations: (Int) -> Unit,
    onToggleLlmPlanner: (Boolean) -> Unit,
    onSetSafetyRiskyPrompt: (Boolean) -> Unit,
    onUndoLastCommand: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHistoryExpanded by rememberSaveable { mutableStateOf(true) }
    var confirmBeforeRun by rememberSaveable { mutableStateOf(false) }
    var pendingConfirmCommand by rememberSaveable { mutableStateOf<String?>(null) }

    val isDaemonRunning = remember(studioUiState.localhostServerStatus) {
        studioUiState.localhostServerStatus.isListening
    }

    val alwaysAskRisky = remember(mcpServers) {
        mcpServers.isEmpty() || mcpServers.any { it.approvalMode != McpApprovalMode.ALWAYS_ALLOW_READ_ONLY.name }
    }

    val livePreviewActions = remember(agentState.commandInput) {
        previewParsedActions(agentState.commandInput)
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onCommandInputChange(spoken)
            }
        }
    }

    val triggerRun: (String) -> Unit = { cmd ->
        if (cmd.isNotBlank()) {
            if (confirmBeforeRun && pendingConfirmCommand != cmd) {
                pendingConfirmCommand = cmd
            } else {
                pendingConfirmCommand = null
                onExecuteCommand(cmd)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("omni_autopilot_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Autopilot",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                MinimalChip(
                    label = if (agentState.useAiLlmPlanner) "Hybrid Planner" else "Instant 0ms",
                    selected = agentState.useAiLlmPlanner,
                    onClick = { onToggleLlmPlanner(!agentState.useAiLlmPlanner) },
                    icon = LucideIcons.Bot,
                    testTag = "omni_agent_llm_planner_switch"
                )
            }
        }

        // 1. Big, clean command box at the top: "Tell Omni what to do..." with a mic button and a Run button
        item {
            MinimalCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = agentState.commandInput,
                        onValueChange = {
                            pendingConfirmCommand = null
                            onCommandInputChange(it)
                        },
                        placeholder = {
                            Text(
                                text = "Tell Aerodock what to do...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (agentState.commandInput.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        pendingConfirmCommand = null
                                        onCommandInputChange("")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = LucideIcons.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { triggerRun(agentState.commandInput) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        ),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("omni_agent_command_input")
                    )

                    // Real-time parsed action preview pills
                    if (agentState.commandInput.isNotBlank() && livePreviewActions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            livePreviewActions.forEachIndexed { idx, act ->
                                ActionPreviewBadge(index = idx + 1, action = act)
                            }
                        }
                    }

                    if (pendingConfirmCommand != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Confirm execution of ${livePreviewActions.size.coerceAtLeast(1)} action(s)?",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val cmd = pendingConfirmCommand
                                    pendingConfirmCommand = null
                                    if (!cmd.isNullOrBlank()) {
                                        onExecuteCommand(cmd)
                                    }
                                }
                            ) {
                                Text("Confirm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic button
                        Surface(
                            onClick = {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Aerodock what to do...")
                                    }
                                    speechLauncher.launch(intent)
                                } catch (_: Exception) {
                                    if (agentState.commandInput.isBlank()) {
                                        onCommandInputChange("Start OmniRoute daemon and scan model latency")
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("omni_agent_mic_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = LucideIcons.Mic,
                                    contentDescription = "Voice Command",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (agentState.lastUndoSnapshot != null) {
                                MinimalButton(
                                    text = "Undo",
                                    onClick = onUndoLastCommand,
                                    icon = LucideIcons.Undo,
                                    filled = false,
                                    testTag = "omni_agent_undo_button"
                                )
                            }

                            MinimalButton(
                                text = if (agentState.isExecuting) "Running..." else "Run",
                                onClick = { triggerRun(agentState.commandInput) },
                                enabled = agentState.commandInput.isNotBlank() && !agentState.isExecuting,
                                icon = LucideIcons.Play,
                                filled = true,
                                testTag = "omni_agent_execute_button"
                            )
                        }
                    }
                }
            }
        }

        // 2. Status Card: Daemon running or stopped (toggle), connected provider, and current task
        item {
            MinimalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("autopilot_status_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MinimalStatusDot(isConnected = isDaemonRunning)
                            Column {
                                Text(
                                    text = if (isDaemonRunning) "Daemon running" else "Daemon stopped",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "localhost:20128",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        MinimalToggle(
                            checked = isDaemonRunning,
                            onCheckedChange = onToggleDaemon,
                            testTag = "autopilot_daemon_toggle"
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connected provider",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$activeProviderName · $activeModelName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current task",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    agentState.isExecuting -> "Executing command..."
                                    !agentState.liveStatusBanner.isNullOrBlank() -> agentState.liveStatusBanner
                                    agentState.executionHistory.isNotEmpty() -> agentState.executionHistory.first().command
                                    else -> "Idle · Ready"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (agentState.isExecuting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2b. Autopilot Smart Workflow Automation System (Context Recommendations, Templates, Progressive Guide, Smart Scheduler, Custom Rules, and Feedback Telemetry)
        item {
            AutopilotSmartWorkflowSection(
                activeCommandText = agentState.commandInput,
                activeProviderName = activeProviderName,
                enabledSkillsCount = skills.count { it.isEnabled },
                enabledMcpToolsCount = mcpTools.count { it.isEnabled },
                onDispatchAutomationCommand = { cmd ->
                    onCommandInputChange(cmd)
                    triggerRun(cmd)
                }
            )
        }

        // 3. "Live actions" list: each action shows an icon, a one-line description, the time taken (e.g. 8172ms), a status check, and an Undo button.
        item {
            MinimalSectionHeader(
                title = "Live actions",
                trailing = if (agentState.lastUndoSnapshot != null) {
                    {
                        Text(
                            text = "Undo last",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(onClick = onUndoLastCommand)
                                .padding(4.dp)
                        )
                    }
                } else null
            )
            Spacer(modifier = Modifier.height(8.dp))

            val latestRecord = agentState.executionHistory.firstOrNull()
            MinimalCard(modifier = Modifier.fillMaxWidth()) {
                if (latestRecord == null || latestRecord.steps.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "No live actions run in this session yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val perStepMs = (latestRecord.totalDurationMs / latestRecord.steps.size.coerceAtLeast(1)).coerceAtLeast(1L)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        latestRecord.steps.forEachIndexed { idx, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = LucideIcons.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${step.title}: ${step.detail}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${perStepMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = LucideIcons.Check,
                                    contentDescription = "Completed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                if (idx == 0 && agentState.lastUndoSnapshot != null) {
                                    Text(
                                        text = "Undo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(onClick = onUndoLastCommand)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. "Quick actions": 4 to 6 minimal tiles (e.g. Start OmniRoute Daemon, Launch app, Run skill, Run MCP tool)
        item {
            MinimalSectionHeader(title = "Quick actions")
            Spacer(modifier = Modifier.height(8.dp))

            val quickTiles = remember {
                listOf(
                    Triple(
                        "Start OmniRoute Daemon",
                        "start localhost omniroute server and scan model latency",
                        LucideIcons.Terminal
                    ),
                    Triple(
                        "Launch app workspace",
                        "open chat tab and switch model to gpt-4o",
                        LucideIcons.Play
                    ),
                    Triple(
                        "Run skill",
                        "create skill 'Clean Code Reviewer' for Kotlin architecture and go to chat",
                        LucideIcons.Sparkles
                    ),
                    Triple(
                        "Run MCP tool",
                        "add mcp server 'Local DevTools' at http://127.0.0.1:8090/mcp and scan health",
                        LucideIcons.Plug
                    ),
                    Triple(
                        "Compare top models",
                        "enable compare mode and open chat tab",
                        LucideIcons.Compare
                    ),
                    Triple(
                        "Generate studio art",
                        "generate image of minimalist obsidian monolith in misty fjord 16:9",
                        LucideIcons.Image
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickTiles.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { (title, command, icon) ->
                            MinimalCard(
                                onClick = { onExecuteCommand(command) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_${title.lowercase().replace(" ", "_")}"),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. "History": a collapsible list of past runs with a Re-run button
        item {
            MinimalCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isHistoryExpanded = !isHistoryExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = LucideIcons.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "History (${agentState.executionHistory.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (agentState.executionHistory.isNotEmpty()) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable(onClick = onClearHistory)
                                        .padding(4.dp)
                                        .testTag("omni_agent_clear_history_button")
                                )
                            }
                            Icon(
                                imageVector = if (isHistoryExpanded) LucideIcons.ChevronUp else LucideIcons.ChevronDown,
                                contentDescription = "Toggle History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isHistoryExpanded,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150))
                    ) {
                        if (agentState.executionHistory.isEmpty()) {
                            Text(
                                text = "No past runs recorded yet.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                agentState.executionHistory.forEach { record ->
                                    AgentExecutionRecordCard(
                                        record = record,
                                        onRerun = { onExecuteCommand(record.command) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. "Skills and MCP tools": a compact list with on/off toggles, token count, and the max-tools setting
        item {
            val totalSkillTokens = remember(skills) {
                skills.filter { it.isEnabled }.sumOf { (it.rawMarkdown.length / 4).coerceAtLeast(12) }
            }
            MinimalSectionHeader(
                title = "Skills and MCP tools",
                trailing = {
                    Text(
                        text = "~$totalSkillTokens tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            MinimalCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Max-tools setting row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Max tools per turn",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Maximum sequential tool iterations",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(2, 4, 8).forEach { limit ->
                                MinimalChip(
                                    label = "$limit",
                                    selected = maxToolIterations == limit,
                                    onClick = { onSetMaxToolIterations(limit) }
                                )
                            }
                        }
                    }

                    if (skills.isNotEmpty() || mcpTools.isNotEmpty()) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    }

                    skills.forEach { skill ->
                        val estTokens = (skill.rawMarkdown.length / 4).coerceAtLeast(12)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = skill.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Skill · ${estTokens}t",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MinimalToggle(
                                checked = skill.isEnabled,
                                onCheckedChange = { onToggleSkill(skill) }
                            )
                        }
                    }

                    mcpTools.forEach { tool ->
                        val estTokens = (tool.description.length / 4).coerceAtLeast(16)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tool.toolName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "MCP Tool · ${estTokens}t",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MinimalToggle(
                                checked = tool.isEnabled,
                                onCheckedChange = { onToggleMcpTool(tool) }
                            )
                        }
                    }
                }
            }
        }

        // 7. Safety: a confirm-before-run toggle, and an "Always ask for risky actions" option
        item {
            MinimalSectionHeader(title = "Safety")
            Spacer(modifier = Modifier.height(8.dp))

            MinimalCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Confirm before run",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Preview parsed actions before applying changes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MinimalToggle(
                            checked = confirmBeforeRun,
                            onCheckedChange = { confirmBeforeRun = it },
                            testTag = "safety_confirm_before_run_toggle"
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Always ask for risky actions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Require approval for destructive or external tool calls",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MinimalToggle(
                            checked = alwaysAskRisky,
                            onCheckedChange = onSetSafetyRiskyPrompt,
                            testTag = "safety_always_ask_risky_toggle"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slim top toast for executed actions with Undo support.
 * Auto-dismisses after 4s in MainActivity.
 */
@Composable
fun OmniAgentFloatingHud(
    agentState: OmniAgentUiState,
    onOpenAgentSheet: () -> Unit,
    onUndoLastCommand: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !agentState.liveStatusBanner.isNullOrBlank(),
        enter = slideInVertically { -it } + fadeIn(tween(150)),
        exit = slideOutVertically { -it } + fadeOut(tween(150)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("omni_agent_live_banner")
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (agentState.isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = LucideIcons.Check,
                        contentDescription = "Executed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = agentState.liveStatusBanner.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (agentState.lastUndoSnapshot != null && !agentState.isExecuting) {
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onUndoLastCommand)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("omni_agent_banner_undo_button")
                    )
                }
            }
        }
    }
}

/**
 * Modal bottom sheet wrapper around OmniAutopilotScreen for backward compatibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniAgentCommandCenterSheet(
    agentState: OmniAgentUiState,
    studioUiState: OmniUiState,
    activeModelName: String,
    encryptedKeysCount: Int,
    enabledSkillsCount: Int,
    previewParsedActions: (String) -> List<OmniAgentAction>,
    onCommandInputChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onToggleLlmPlanner: (Boolean) -> Unit,
    onUndoLastCommand: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .imePadding()
            .navigationBarsPadding()
            .testTag("omni_agent_command_sheet")
    ) {
        OmniAutopilotScreen(
            agentState = agentState,
            studioUiState = studioUiState,
            activeProviderName = "OmniRoute",
            activeModelName = activeModelName,
            skills = emptyList(),
            mcpTools = emptyList(),
            mcpServers = emptyList(),
            maxToolIterations = 4,
            previewParsedActions = previewParsedActions,
            onCommandInputChange = onCommandInputChange,
            onExecuteCommand = onExecuteCommand,
            onToggleDaemon = {},
            onToggleSkill = {},
            onToggleMcpTool = {},
            onSetMaxToolIterations = {},
            onToggleLlmPlanner = onToggleLlmPlanner,
            onSetSafetyRiskyPrompt = {},
            onUndoLastCommand = onUndoLastCommand,
            onClearHistory = onClearHistory
        )
    }
}

@Composable
private fun ActionPreviewBadge(
    index: Int,
    action: OmniAgentAction
) {
    val label = when (action) {
        is OmniAgentAction.NavigateTab -> "Tab → ${action.tab.name}"
        is OmniAgentAction.SetCompareMode -> "Compare=${action.enabled}"
        is OmniAgentAction.OpenModalSheet -> "Open ${action.sheetName}"
        is OmniAgentAction.SetThemeMode -> "Theme=${action.mode.ifBlank { "Dynamic" }}"
        is OmniAgentAction.SetSpendCurrency -> "Currency=${action.currency}"
        is OmniAgentAction.SetEnterToSend -> "EnterToSend=${action.enabled}"
        is OmniAgentAction.SetBiometricLock -> "Biometric=${action.enabled}"
        is OmniAgentAction.SwitchActiveModel -> "Model → ${action.modelQuery}"
        is OmniAgentAction.TuneChatParameters -> "Tune Parameters"
        is OmniAgentAction.CreateConversation -> "New Chat '${action.title.take(14)}'"
        is OmniAgentAction.SendChatMessage -> "Send Prompt"
        is OmniAgentAction.SaveProviderApiKey -> "Save Key (${action.providerQuery})"
        is OmniAgentAction.ClearProviderApiKey -> "Clear Key (${action.providerQuery})"
        is OmniAgentAction.AddOrUpdateProvider -> "Add Provider (${action.presetName})"
        is OmniAgentAction.ToggleProviderEnabled -> "Toggle Provider (${action.providerQuery})"
        is OmniAgentAction.SetDefaultProvider -> "Default ${action.capability.name}"
        is OmniAgentAction.AddManualModel -> "Add Model ${action.modelId}"
        is OmniAgentAction.GenerateImageAction -> "Image (${action.aspectRatio})"
        is OmniAgentAction.GenerateVideoAction -> "Video (${action.resolution})"
        is OmniAgentAction.CreateSkillAction -> "Create Skill '${action.name.take(14)}'"
        is OmniAgentAction.CreatePromptTemplateAction -> "Save Template '${action.title.take(14)}'"
        is OmniAgentAction.AddMcpServerAction -> "Add MCP '${action.name.take(14)}'"
        is OmniAgentAction.ControlLocalhostServer -> if (action.start) "Start Daemon" else "Stop Daemon"
        is OmniAgentAction.RunHealthAndLatencyScan -> "Latency Scan"
        is OmniAgentAction.ClearDataAction -> "Clear ${action.target}"
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Text(
            text = "$index. $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AgentExecutionRecordCard(
    record: OmniAgentExecutionRecord,
    onRerun: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_execution_record_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.command,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.steps.size} action(s) · ${record.totalDurationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Re-run",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRerun)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
