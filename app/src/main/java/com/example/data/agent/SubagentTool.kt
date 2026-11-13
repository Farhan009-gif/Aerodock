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
 * final answer. Also holds the sibling `spawn_subagent_chain` tool: an ordered pipeline
 * of subagent steps, run by one background job, each step fed the previous step's result.
 * This object holds only the tools' identity, synthetic entities and text
 * helpers; the runner itself lives in OmniStudioViewModel.executeModelStream, next to the
 * other built-in tool executors (run_code, Live Info), because it reuses that loop's
 * provider resolution, tool list and repository executors.
 */
object SubagentTool {

    const val SPAWN_SUBAGENT_TOOL_NAME = "spawn_subagent"
    const val SPAWN_SUBAGENT_CHAIN_TOOL_NAME = "spawn_subagent_chain"
    const val SUBAGENT_SERVER_ID = "builtin_subagent"
    const val SUBAGENT_SERVER_NAME = "Subagent"

    /** Hard caps for one subagent run: model turns and wall-clock time. */
    const val MAX_TURNS = 60
    const val TIMEOUT_MS = 1_200_000L

    /** Tool results longer than this are truncated before they reach the parent chat. */
    const val MAX_RESULT_CHARS = 12_000

    /** At most this many background subagents may run at once, app-wide. */
    const val MAX_BACKGROUND_JOBS = 12

    /** At most this many steps in one `spawn_subagent_chain` pipeline. */
    const val MAX_CHAIN_STEPS = 16

    private const val TOOL_DESCRIPTION =
        "Delegate a self-contained task to a subagent: a fresh AI conversation with its own " +
            "context that works independently and returns only its final answer. Use it to " +
            "research, summarise, draft or check something — including several tasks in " +
            "parallel — while you keep working. The brief must contain everything the " +
            "subagent needs: it cannot see this conversation. The subagent can use this " +
            "chat's tools except tools that need user approval, and it cannot spawn " +
            "subagents of its own. A run is capped at $MAX_TURNS turns and ${TIMEOUT_MS / 60_000} minutes. " +
            "Set background=true when the task is long or the user may want to keep " +
            "chatting; the result is posted back into this conversation when done."

    private const val INPUT_SCHEMA_JSON =
        """{"type":"object","properties":{"task":{"type":"string","description":"The complete, self-contained brief for the subagent. Include all context it needs; it cannot see this conversation."},"role":{"type":"string","description":"Optional role or expertise for the subagent, e.g. 'a careful code reviewer'."},"model":{"type":"string","description":"Optional model id on the same provider as this chat. Defaults to this chat's current model."},"background":{"type":"boolean","description":"Optional, default false. If true, the subagent runs in the background: this call returns immediately and the result is posted into this conversation when it finishes, so the user can keep chatting meanwhile."}},"required":["task"]}"""

    private const val CHAIN_TOOL_DESCRIPTION =
        "Run a pipeline of subagents in the background: up to $MAX_CHAIN_STEPS steps executed in order, " +
            "where each step is a fresh subagent under the same rules as spawn_subagent " +
            "and each step's result is passed into the next step's brief. Use it when a " +
            "later step needs an earlier step's output — e.g. step 1 gathers facts, " +
            "step 2 checks them. Write every step's task to stand on its own, except " +
            "that it may refer to 'the previous step's result', which is appended to " +
            "its brief automatically. This call returns immediately; each step's result " +
            "is posted into this conversation as it finishes, and the chain stops if a " +
            "step fails. A chain counts as one background job."

    private const val CHAIN_INPUT_SCHEMA_JSON =
        """{"type":"object","properties":{"steps":{"type":"array","description":"Up to 16 steps, executed in order; each step's result is fed into the next step.","minItems":1,"maxItems":16,"items":{"type":"object","properties":{"task":{"type":"string","description":"The complete, self-contained brief for this step's subagent. It may refer to 'the previous step's result'."},"role":{"type":"string","description":"Optional role or expertise for this step's subagent."}},"required":["task"]}}},"required":["steps"]}"""

    fun buildSubagentServerEntity(): McpServerEntity {
        return McpServerEntity(
            id = SUBAGENT_SERVER_ID,
            name = SUBAGENT_SERVER_NAME,
            url = "builtin://subagent",
            transport = McpTransportType.STREAMABLE_HTTP.name,
            authHeaderName = "",
            approvalMode = McpApprovalMode.ASK_EVERY_TIME.name,
            isEnabled = true,
            lastTestStatus = "Built-in subagent delegation (fresh context, $MAX_TURNS-turn / ${TIMEOUT_MS / 60_000}-minute cap)"
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

    fun buildChainToolEntity(): McpToolEntity {
        return McpToolEntity(
            id = "$SUBAGENT_SERVER_ID:$SPAWN_SUBAGENT_CHAIN_TOOL_NAME",
            serverId = SUBAGENT_SERVER_ID,
            serverName = SUBAGENT_SERVER_NAME,
            toolName = SPAWN_SUBAGENT_CHAIN_TOOL_NAME,
            description = CHAIN_TOOL_DESCRIPTION,
            inputSchemaJson = CHAIN_INPUT_SCHEMA_JSON,
            // Same reasoning as spawn_subagent: the steps only receive tools that
            // already run without user approval, so no approval prompt is ever needed.
            isReadOnlyHint = true,
            isEnabled = true
        )
    }

    fun buildChainToolDefinition(): McpToolDefinition {
        return McpToolDefinition(
            serverId = SUBAGENT_SERVER_ID,
            serverName = SUBAGENT_SERVER_NAME,
            toolName = SPAWN_SUBAGENT_CHAIN_TOOL_NAME,
            description = CHAIN_TOOL_DESCRIPTION,
            inputSchemaJson = CHAIN_INPUT_SCHEMA_JSON,
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

    data class SubagentChainStep(
        val task: String,
        val role: String?
    )

    /**
     * Parses the model's spawn_subagent_chain arguments into steps. Malformed input
     * yields an empty list; the caller validates the count (1–[MAX_CHAIN_STEPS]) and
     * non-blank tasks, mirroring how a blank spawn_subagent task is rejected.
     */
    fun parseChainSteps(argumentsJson: String): List<SubagentChainStep> {
        val obj = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrNull()
            ?: return emptyList()
        val steps = obj.optJSONArray("steps") ?: return emptyList()
        return (0 until steps.length()).map { index ->
            val step = steps.optJSONObject(index)
            SubagentChainStep(
                task = step?.optString("task")?.trim().orEmpty(),
                role = step?.optString("role")?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
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
