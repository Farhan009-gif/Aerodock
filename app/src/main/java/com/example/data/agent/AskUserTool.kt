package com.example.data.agent

import com.example.data.local.db.entity.McpServerEntity
import com.example.data.local.db.entity.McpToolEntity
import com.example.data.model.McpToolDefinition
import com.example.domain.model.McpApprovalMode
import com.example.domain.model.McpTransportType
import org.json.JSONObject

/**
 * The built-in `ask_user` tool: the model pauses mid-task and asks the user
 * one question, shown as a sheet with optional quick-answer options plus a
 * free-text field. The answer returns as the tool result. This exists so a
 * model facing a genuine fork (which file? which approach?) asks instead of
 * guessing — the human-in-the-loop counterpart to the approval sheet.
 *
 * Main chat only: subagents never receive it (a background run cannot wait
 * on a question sheet), enforced in buildSubagentPlan.
 */
object AskUserTool {

    const val ASK_USER_TOOL_NAME = "ask_user"
    const val ASK_USER_SERVER_ID = "builtin_assistant"
    const val ASK_USER_SERVER_NAME = "Assistant"

    private const val TOOL_DESCRIPTION =
        "Ask the user ONE question when you genuinely cannot proceed without " +
            "their choice — a fork where guessing wrong wastes real work (which " +
            "file, which approach, which option). The user sees a sheet with " +
            "your question, your optional quick options, and a free-text box; " +
            "their answer comes back as this tool's result. Do NOT use it for " +
            "things you can decide yourself, find out with another tool, or " +
            "that a reasonable default covers — asking has a cost. Keep the " +
            "question short and self-contained; offer 2-4 options when the " +
            "answers are enumerable."

    private const val INPUT_SCHEMA_JSON =
        """{"type":"object","properties":{"question":{"type":"string","description":"The single question to ask, short and self-contained."},"options":{"type":"array","items":{"type":"string"},"description":"Optional 2-4 short answer choices shown as tappable buttons; the user can always type a different answer instead."}},"required":["question"]}"""

    fun buildServerEntity(): McpServerEntity {
        return McpServerEntity(
            id = ASK_USER_SERVER_ID,
            name = ASK_USER_SERVER_NAME,
            url = "builtin://assistant",
            transport = McpTransportType.STREAMABLE_HTTP.name,
            authHeaderName = "",
            approvalMode = McpApprovalMode.ASK_EVERY_TIME.name,
            isEnabled = true,
            lastTestStatus = "Built-in ask_user question sheet"
        )
    }

    fun buildToolEntity(): McpToolEntity {
        return McpToolEntity(
            id = "$ASK_USER_SERVER_ID:$ASK_USER_TOOL_NAME",
            serverId = ASK_USER_SERVER_ID,
            serverName = ASK_USER_SERVER_NAME,
            toolName = ASK_USER_TOOL_NAME,
            description = TOOL_DESCRIPTION,
            inputSchemaJson = INPUT_SCHEMA_JSON,
            // Asking changes nothing on disk or network; the sheet IS the
            // interaction, so no separate approval is layered on top.
            isReadOnlyHint = true,
            isEnabled = true
        )
    }

    fun buildToolDefinition(): McpToolDefinition {
        return McpToolDefinition(
            serverId = ASK_USER_SERVER_ID,
            serverName = ASK_USER_SERVER_NAME,
            toolName = ASK_USER_TOOL_NAME,
            description = TOOL_DESCRIPTION,
            inputSchemaJson = INPUT_SCHEMA_JSON,
            isReadOnlyHint = true
        )
    }

    /** Parses the call arguments into (question, options); tolerant of junk. */
    fun parse(argumentsJson: String): Pair<String, List<String>> {
        val obj = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return "The model wants to ask you something, but its question could not be read." to emptyList()
        val question = obj.optString("question", "").trim()
            .ifBlank { "The model wants to ask you something, but its question could not be read." }
        val optionsArray = obj.optJSONArray("options")
        val options = buildList {
            if (optionsArray != null) {
                for (i in 0 until optionsArray.length()) {
                    val value = optionsArray.optString(i, "").trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.take(4)
        return question to options
    }
}
