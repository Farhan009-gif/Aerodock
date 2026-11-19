package com.example.data.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Phase 1 trajectory log: one JSON object per user turn, appended to
 * filesDir/trajectories/<conversationId>.jsonl (same per-conversation file
 * pattern as the conversation plans). This is the raw material for the
 * self-improvement outer loop — teacher grading and distillation in later
 * phases read these files. The writer's only job is to record faithfully
 * and never disturb the turn, so every failure is swallowed.
 */
object TrajectoryLog {

    data class Step(
        val tool: String,
        val args: String,
        val ok: Boolean,
        val ms: Long,
        val result: String
    )

    suspend fun append(
        filesDir: File,
        conversationId: String,
        messageId: String,
        task: String,
        steps: List<Step>,
        outcome: String,
        buildVerdict: String
    ) {
        try {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(filesDir, "trajectories")
                    if (!dir.exists()) dir.mkdirs()
                    val safeId = conversationId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                    val stepsJson = JSONArray()
                    for (step in steps) {
                        stepsJson.put(
                            JSONObject()
                                .put("tool", step.tool)
                                .put("args", step.args.take(1_000))
                                .put("ok", step.ok)
                                .put("ms", step.ms)
                                .put("result", step.result.take(300))
                        )
                    }
                    val record = JSONObject()
                        .put("conversationId", conversationId)
                        .put("messageId", messageId)
                        .put("ts", System.currentTimeMillis())
                        .put("task", task.take(2_000))
                        .put("steps", stepsJson)
                        .put("outcome", outcome)
                        .put("buildVerdict", buildVerdict)
                    File(dir, "$safeId.jsonl").appendText(record.toString() + "\n", Charsets.UTF_8)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never throw into the turn: a lost record beats a broken reply.
        }
    }
}
