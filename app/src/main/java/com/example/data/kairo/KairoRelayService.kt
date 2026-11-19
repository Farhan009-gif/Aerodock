package com.example.data.kairo

import android.content.Context
import com.example.data.local.OmniDatabase
import com.example.data.provider.ChatCompletionRequest
import com.example.data.provider.ChatMessagePayload
import com.example.data.provider.ChatStreamEvent
import com.example.data.provider.ModelToolCallRequest
import com.example.data.provider.TextToolCallParser
import com.example.data.repository.OmniRepository
import com.example.data.security.TinkSecretVault
import com.example.data.workspace.WorkspaceTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The phone half of "Kairo inside Aerodock" (v1, read-only).
 *
 * While the user has Kairo access switched on, this service polls the
 * Cloudflare relay Worker (see the kairo-relay package) every few seconds
 * for jobs Kairo left in the mailbox, runs them, and posts the results
 * back. Two job kinds exist:
 *
 *  - "tool": one of the five read-only workspace tools, executed through
 *    the same repository path the in-app agent uses, so calls are
 *    validated and land in the tool-call log like any other.
 *  - "chat": a message for the in-app model. The service runs a short
 *    tool loop (at most [MAX_CHAT_TOOL_ROUNDS] rounds) on the first
 *    enabled provider with an API key, offering the model only the same
 *    five read-only tools, and returns its final reply. The thread is
 *    kept in filesDir/kairo/thread.json so the conversation continues
 *    across jobs.
 *
 * Nothing here can write: the allowlist is enforced before execution, not
 * just in what the relay offers. Polling runs only while the app process
 * is alive and the toggle is on; switching the toggle off (or rotating
 * the token) locks Kairo out immediately.
 */
object KairoRelayService {

    private const val POLL_DELAY_MS = 4_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pollJob: Job? = null

    /** Human-readable state for the Settings card; read at composition time. */
    @Volatile
    var statusText: String = "Off"
        private set

    val isRunning: Boolean get() = pollJob?.isActive == true

    fun maybeStart(context: Context) {
        if (KairoRelayPrefs.isEnabled(context)) start(context)
    }

    fun start(context: Context) {
        if (isRunning) return
        val appContext = context.applicationContext
        statusText = "Starting…"
        pollJob = scope.launch {
            val database = OmniDatabase.getInstance(appContext)
            val dao = database.omniDao()
            val vault = TinkSecretVault(appContext)
            val repository = OmniRepository(appContext, dao, vault)
            val session = Session(appContext, dao, vault, repository) { statusText = it }
            while (isActive) {
                try {
                    session.pollOnce()
                } catch (e: Exception) {
                    statusText = "Relay unreachable: ${e.message ?: e.javaClass.simpleName}"
                }
                delay(POLL_DELAY_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        statusText = "Off"
    }

    /** One polling session: holds the app-side collaborators and the token. */
    private class Session(
        private val context: Context,
        private val dao: com.example.data.local.OmniDao,
        private val vault: TinkSecretVault,
        private val repository: OmniRepository,
        private val onStatus: (String) -> Unit
    ) {
        private val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        suspend fun pollOnce() {
            val base = KairoRelayPrefs.relayUrl(context).trimEnd('/')
            if (base.isEmpty()) {
                onStatus("Set the relay URL in Settings → Kairo access.")
                return
            }
            val token = vault.getApiKey(KairoRelayPrefs.TOKEN_RECORD_ID)
            if (token.isBlank()) {
                onStatus("Generate or paste a pairing token in Settings → Kairo access.")
                return
            }
            val jobs = fetchJobs(base, token)
            onStatus(
                if (jobs.isEmpty()) {
                    "On — mailbox empty, listening."
                } else {
                    "On — running ${jobs.size} job(s) from Kairo."
                }
            )
            for (job in jobs) {
                val (ok, text) = execute(job)
                postResult(base, token, job.id, ok, text)
            }
            if (jobs.isNotEmpty()) onStatus("On — answered ${jobs.size} job(s), listening.")
        }

        private fun fetchJobs(base: String, token: String): List<RelayJob> {
            val request = Request.Builder()
                .url("$base/poll")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onStatus("Relay said HTTP ${response.code} — check the URL and token.")
                    return emptyList()
                }
                val body = response.body?.string().orEmpty()
                val array = runCatching { JSONObject(body).optJSONArray("jobs") }.getOrNull()
                    ?: return emptyList()
                val jobs = mutableListOf<RelayJob>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isEmpty()) continue
                    jobs.add(
                        RelayJob(
                            id = id,
                            kind = item.optString("kind"),
                            tool = item.optString("tool").ifEmpty { null },
                            args = item.optJSONObject("args") ?: JSONObject(),
                            message = item.optString("message").ifEmpty { null }
                        )
                    )
                }
                return jobs
            }
        }

        private fun postResult(base: String, token: String, id: String, ok: Boolean, text: String) {
            val payload = JSONObject()
                .put("id", id)
                .put("ok", ok)
                .put("text", text)
            val request = Request.Builder()
                .url("$base/result")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            runCatching {
                http.newCall(request).execute().use { response -> response.body?.string() }
            }
        }

        private suspend fun execute(job: RelayJob): Pair<Boolean, String> {
            return when (job.kind) {
                "tool" -> executeTool(job)
                "chat" -> runChat(job.message.orEmpty())
                else -> false to "Error: unknown job kind '${job.kind}'."
            }
        }

        private suspend fun executeTool(job: RelayJob): Pair<Boolean, String> {
            val tool = job.tool.orEmpty()
            if (tool !in READ_TOOLS) {
                return false to "Error: tool '$tool' is not allowed over the Kairo relay (read-only)."
            }
            val log = repository.executeWorkspaceToolAndLog(
                conversationId = CONVERSATION_ID,
                messageId = job.id,
                toolName = tool,
                argumentsJson = job.args.toString()
            )
            return log.isSuccess to log.resultText
        }

        /**
         * One Kairo chat turn: stream the model, run any read-only tool
         * calls it makes (native, or recovered from its text by
         * [TextToolCallParser] — the dialect fix), feed the results back,
         * and repeat until it answers in plain text or the round cap hits.
         */
        private suspend fun runChat(message: String): Pair<Boolean, String> {
            if (message.isBlank()) return false to "Error: the chat message was empty."
            val providers = dao.getProvidersOnce()
            val provider = providers.firstOrNull {
                it.isEnabled && vault.getApiKey(it.id).isNotBlank()
            } ?: return false to
                "Error: no enabled provider with an API key is configured in Aerodock, " +
                "so the in-app model cannot answer."
            val apiKey = vault.getApiKey(provider.id)
            val modelId = provider.defaultChatModelId.ifBlank {
                dao.getModelsForProviderOnce(provider.id).firstOrNull()?.modelId.orEmpty()
            }
            if (modelId.isBlank()) {
                return false to "Error: provider '${provider.displayName}' has no model selected."
            }
            val adapter = repository.resolveAdapter(provider)
            val toolDefinitions = WorkspaceTools.buildToolDefinitions()
                .filter { it.toolName in READ_TOOLS }

            val history = loadHistory()
            val payloads = mutableListOf<ChatMessagePayload>()
            history.mapTo(payloads) { (role, content) -> ChatMessagePayload(role, content) }
            payloads.add(ChatMessagePayload(role = "user", content = message))

            val toolsUsed = mutableListOf<String>()
            var finalText = ""
            var rounds = 0
            while (rounds <= MAX_CHAT_TOOL_ROUNDS) {
                rounds++
                val request = ChatCompletionRequest(
                    provider = provider,
                    apiKey = apiKey,
                    modelId = modelId,
                    messages = payloads.toList(),
                    systemPrompt = SYSTEM_PROMPT,
                    temperature = 0.6f,
                    maxTokens = 2048,
                    topP = 1.0f,
                    tools = toolDefinitions
                )
                val text = StringBuilder()
                val nativeCalls = mutableListOf<ModelToolCallRequest>()
                var errorMessage: String? = null
                adapter.streamChat(request).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> text.append(event.textChunk)
                        is ChatStreamEvent.ToolCall -> nativeCalls.add(event.call)
                        is ChatStreamEvent.Error -> errorMessage = event.error.message
                        else -> Unit
                    }
                }
                if (errorMessage != null) {
                    return false to "Error from the in-app model: $errorMessage"
                }
                var roundText = text.toString()
                var roundCalls = nativeCalls
                if (roundCalls.isEmpty() && roundText.isNotBlank()) {
                    // Text-dialect models print their calls; recover them the
                    // same way the main chat loop does.
                    val extraction = TextToolCallParser.extract(
                        roundText,
                        toolDefinitions.map { it.toolName }.toSet()
                    )
                    if (extraction.calls.isNotEmpty()) {
                        roundCalls = extraction.calls.toMutableList()
                        roundText = extraction.cleanedContent
                    }
                }
                if (roundCalls.isEmpty()) {
                    finalText = roundText.trim()
                    break
                }
                payloads.add(
                    ChatMessagePayload(
                        role = "assistant",
                        content = roundText,
                        toolCalls = roundCalls
                    )
                )
                for (call in roundCalls) {
                    val (resultText, isSuccess) = if (call.toolName in READ_TOOLS) {
                        val log = repository.executeWorkspaceToolAndLog(
                            conversationId = CONVERSATION_ID,
                            messageId = "chat_${System.currentTimeMillis()}",
                            toolName = call.toolName,
                            argumentsJson = call.argumentsJson
                        )
                        log.resultText to log.isSuccess
                    } else {
                        "Error: tool '${call.toolName}' is not allowed over the Kairo relay (read-only)." to false
                    }
                    toolsUsed.add(call.toolName + if (isSuccess) "" else " (failed)")
                    payloads.add(
                        ChatMessagePayload(
                            role = "tool",
                            content = resultText,
                            toolCallId = call.id,
                            toolName = call.toolName
                        )
                    )
                }
            }
            if (finalText.isBlank()) {
                finalText = "(The in-app model used its tools but sent no final summary.)"
            }
            val historyEntry = if (toolsUsed.isEmpty()) {
                finalText
            } else {
                "[Used tools: ${toolsUsed.joinToString(", ")}]\n$finalText"
            }
            saveHistory(history + ("user" to message) + ("assistant" to historyEntry))
            return true to finalText
        }

        private fun historyFile(): File {
            val dir = File(context.filesDir, "kairo")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "thread.json")
        }

        private fun loadHistory(): List<Pair<String, String>> {
            val file = historyFile()
            if (!file.exists()) return emptyList()
            val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
            val turns = mutableListOf<Pair<String, String>>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val role = item.optString("role")
                val content = item.optString("content")
                if ((role == "user" || role == "assistant") && content.isNotEmpty()) {
                    turns.add(role to content)
                }
            }
            return turns.takeLast(MAX_HISTORY_TURNS)
        }

        private fun saveHistory(turns: List<Pair<String, String>>) {
            val array = JSONArray()
            turns.takeLast(MAX_HISTORY_TURNS).forEach { (role, content) ->
                array.put(JSONObject().put("role", role).put("content", content))
            }
            runCatching { historyFile().writeText(array.toString()) }
        }

        private companion object {
            val READ_TOOLS = setOf(
                WorkspaceTools.LIST_FILES_TOOL_NAME,
                WorkspaceTools.READ_FILE_TOOL_NAME,
                WorkspaceTools.SEARCH_FILES_TOOL_NAME,
                WorkspaceTools.GIT_STATUS_TOOL_NAME,
                WorkspaceTools.BUILD_STATUS_TOOL_NAME
            )

            const val CONVERSATION_ID = "kairo-relay"
            const val MAX_CHAT_TOOL_ROUNDS = 6
            const val MAX_HISTORY_TURNS = 24

            const val SYSTEM_PROMPT =
                "You are the AI model inside Aerodock, an Android app on Farhan's phone. " +
                    "You are talking with Kairo, Farhan's personal AI agent, who is directing " +
                    "this conversation from outside the phone through a relay. You have " +
                    "read-only workspace tools (list_files, read_file, search_files, " +
                    "git_status, build_status) for the on-device project workspace. Use them " +
                    "whenever Kairo asks about files or projects instead of guessing. You " +
                    "cannot write files in this channel. Always finish with a clear summary " +
                    "of what you found or did, because Kairo cannot see your tool calls " +
                    "unless you describe the outcome."
        }

    }

    private data class RelayJob(
        val id: String,
        val kind: String,
        val tool: String?,
        val args: JSONObject,
        val message: String?
    )

}
