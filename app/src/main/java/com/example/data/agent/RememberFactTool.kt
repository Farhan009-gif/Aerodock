package com.example.data.agent

import com.example.data.local.McpApprovalMode
import com.example.data.local.McpServerEntity
import com.example.data.local.McpToolEntity
import com.example.data.local.McpTransportType
import com.example.data.provider.McpToolDefinition
import org.json.JSONObject

/**
 * The built-in `remember_fact` tool: the model saves ONE durable fact
 * about the user (a project, a preference, a standing decision) into
 * Aerodock's on-device memory (see data/memory/AerodockMemory), where it
 * rides in future chats' system prompts and is listed in Settings.
 *
 * Saving is a write, so the main loop shows the approval sheet first —
 * the same treatment workspace file writes get — and subagents never
 * receive this tool (enforced in buildSubagentPlan, like ask_user).
 * It shares ask_user's synthetic "Assistant" server.
 */
object RememberFactTool {

    const val REMEMBER_FACT_TOOL_NAME = "remember_fact"

    private const val TOOL_DESCRIPTION =
        "Save ONE durable fact about the user to long-term memory in this " +
            "app — something true beyond this chat: a project they are " +
            "building, a preference, a standing decision or rule. The fact " +
            "is shown to the user for approval before it is saved, and it " +
            "will be included in future chats. Keep it to one short " +
            "sentence, stated as a fact about the user (e.g. \"The user is " +
            "building a laser security alarm for a school exhibition\"). " +
            "Do NOT save temporary task state, things already in this " +
            "chat's history, or guesses — only save when the user says or " +
            "clearly confirms something worth remembering."

    private const val INPUT_SCHEMA_JSON =
        """{"type":"object","properties":{"fact":{"type":"string","description":"One short sentence stating the durable fact about the user."}},"required":["fact"]}"""

    fun buildServerEntity(): McpServerEntity {
        return McpServerEntity(
            id = AskUserTool.ASK_USER_SERVER_ID,
            name = AskUserTool.ASK_USER_SERVER_NAME,
            url = "builtin://assistant",
            transport = McpTransportType.STREAMABLE_HTTP.name,
            authHeaderName = "",
            approvalMode = McpApprovalMode.ASK_EVERY_TIME.name,
            isEnabled = true,
            lastTestStatus = "Built-in remember_fact memory tool"
        )
    }

    fun buildToolEntity(): McpToolEntity {
        return McpToolEntity(
            id = "${AskUserTool.ASK_USER_SERVER_ID}:$REMEMBER_FACT_TOOL_NAME",
            serverId = AskUserTool.ASK_USER_SERVER_ID,
            serverName = AskUserTool.ASK_USER_SERVER_NAME,
            toolName = REMEMBER_FACT_TOOL_NAME,
            description = TOOL_DESCRIPTION,
            inputSchemaJson = INPUT_SCHEMA_JSON,
            // Saving a memory is a write, so it is not read-only; the
            // main loop approval-gates it before anything is stored.
            isReadOnlyHint = false,
            isEnabled = true
        )
    }

    fun buildToolDefinition(): McpToolDefinition {
        return McpToolDefinition(
            serverId = AskUserTool.ASK_USER_SERVER_ID,
            serverName = AskUserTool.ASK_USER_SERVER_NAME,
            toolName = REMEMBER_FACT_TOOL_NAME,
            description = TOOL_DESCRIPTION,
            inputSchemaJson = INPUT_SCHEMA_JSON,
            isReadOnlyHint = false
        )
    }

    /** Parses the call arguments into the fact text; "" when unreadable. */
    fun parseFact(argumentsJson: String): String {
        val obj = runCatching { JSONObject(argumentsJson) }.getOrNull() ?: return ""
        return obj.optString("fact", "").trim()
    }
}
