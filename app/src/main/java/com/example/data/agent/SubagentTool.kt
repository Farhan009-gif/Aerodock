package com.example.data.agent

import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.McpTransportType
import com.example.data.provider.McpToolDefinition
import org.json.JSONObject

/**
 * Built-in `spawn_subagent` tool: lets the chat agent delegate a self-contained task to a
 * fresh subagent (its own short conversation, no parent history) and read back only its
 * final answer. This object holds only the tool's identity, synthetic entities and text
 * helpers; the runner itself lives in OmniStudioViewModel.executeModelStream, next to the
 * other built-in tool executors (run_code, Live Info), because it reuses that loop's
 * provider resolution, tool list and repository executors.
 */
object SubagentTool {

    const val SPAWN_SUBAGENT_TOOL_NAME = "spawn_subagent"
    const val SUBAGENT_SERVER_ID = "builtin_subagent"
    const val SUBAGENT_SERVER_NAME = "Subagent"

    /** Hard caps for one subagent run: model turns and wall-clock time. */
    const val MAX_TURNS = 8
    const val TIMEOUT_MS = 300_000L

    /** Tool results longer than this are truncated before they reach the parent chat. */
    const val MAX_RESULT_CHARS = 6_000

    /** At most this many background subagents may run at once, app-wide. */
    const val MAX_BACKGROUND_JOBS = 3

    private const val TOOL_DESCRIPTION =
        "Delegate a self-contained task to a subagent: a fresh AI conversation with its own " +
            "context that works independently and returns only its final answer. Use it to " +
            "research, summarise, draft or check something — including several tasks in " +
            "parallel — while you keep working. The brief must contain everything the " +
            "subagent needs: it cannot see this conversation. The subagent can use this " +
            "chat's tools except tools that need user approval, and it cannot spawn " +
            "subagents of its own. A run is capped at 8 turns and 5 minutes. " +
            "Set background=true when the task is long or the user may want to keep " +
            "chatting; the result is posted back into this conversation when done."

    private const val INPUT_SCHEMA_JSON =
        """{"type":"object","properties":{"task":{"type":"string","description":"The complete, self-contained brief for the subagent. Include all context it needs; it cannot see this conversation."},"role":{"type":"string","description":"Optional role or expertise for the subagent, e.g. 'a careful code reviewer'."},"model":{"type":"string","description":"Optional model id on the same provider as this chat. Defaults to this chat's current model."},"background":{"type":"boolean","description":"Optional, default false. If true, the subagent runs in the background: this call returns immediately and the result is posted into this conversation when it finishes, so the user can keep chatting meanwhile."}},"required":["task"]}"""

    fun buildSubagentServerEntity(): McpServerEntity {
        return McpServerEntity(
            id = SUBAGENT_SERVER_ID,
            name = SUBAGENT_SERVER_NAME,
            url = "builtin://subagent",
            transport = McpTransportType.STREAMABLE_HTTP.name,
            authHeaderName = "",
            approvalMode = McpApprovalMode.ASK_EVERY_TIME.name,
            isEnabled = true,
            lastTestStatus = "Built-in subagent delegation (fresh context, 8-turn / 5-minute cap)"
        )
    }

    fun buildSubagentToolEntity(): McpToolEntity {
        return McpToolEntity(
            id = "$SUBAGENT_SERVER_ID:$SPAWN_SUBAGENT_TOOL_NAME",
            serverId = SUBAGENT_SERVER_ID,
            serverName = SUBAGENT_SERVER_NAME,
            toolName = SPAWN_SUBAGENT_TOOL_NAME,
            description = TOOL_DESCRIPTION,
            inputSchemaJson = INPUT_SCHEMA_JSON,
            // Spawning itself writes nothing; the subagent only receives tools that
            // already run without user approval, so no approval prompt is ever needed.
            isReadOnlyHint = true,
            isEnabled = true
        )
    }

    fun buildSubagentToolDefinition(): McpToolDefinition {
        return McpToolDefinition(
            serverId = SUBAGENT_SERVER_ID,
            serverName = SUBAGENT_SERVER_NAME,
            toolName = SPAWN_SUBAGENT_TOOL_NAME,
            description = TOOL_DESCRIPTION,
            inputSchemaJson = INPUT_SCHEMA_JSON,
            isReadOnlyHint = true
        )
    }

    data class SubagentRequest(
        val task: String,
        val role: String?,
        val model: String?,
        val background: Boolean = false
    )

    /** Parses the model's spawn_subagent arguments; blank optional fields become null. */
    fun parseRequest(argumentsJson: String): SubagentRequest {
        val obj = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrNull()
        return SubagentRequest(
            task = obj?.optString("task")?.trim().orEmpty(),
            role = obj?.optString("role")?.trim()?.takeIf { it.isNotEmpty() },
            model = obj?.optString("model")?.trim()?.takeIf { it.isNotEmpty() },
            background = obj?.optBoolean("background", false) ?: false
        )
    }

    /** Short one-line label for a task, used in the background-jobs row and the posted result. */
    fun taskLabel(task: String): String {
        val oneLine = task.trim().replace(Regex("\\s+"), " ")
        return if (oneLine.length <= 60) oneLine else oneLine.take(57) + "…"
    }

    fun buildSystemPrompt(role: String?): String = buildString {
        appendLine("You are a subagent inside the Runway app, working for the main assistant.")
        if (!role.isNullOrBlank()) {
            appendLine("Your role: ${role.trim()}")
        }
        appendLine("Complete the task in the user message and finish with one clear, complete final answer. Only that final answer is passed back to the main assistant — it never sees your intermediate steps or tool calls, so include everything that matters in the final answer.")
        append("Tools that need user approval are not available to you, and you cannot ask the user or the main assistant questions. Work with what you have, stay on the task, and state any limits plainly in your final answer.")
    }

    fun truncateResult(text: String): String {
        if (text.length <= MAX_RESULT_CHARS) return text
        return text.take(MAX_RESULT_CHARS) +
            "\n\n[Truncated: the subagent's answer was ${text.length} characters; only the first $MAX_RESULT_CHARS are shown.]"
    }
}
