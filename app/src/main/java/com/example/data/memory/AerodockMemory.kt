package com.example.data.memory

import android.content.Context
import org.json.JSONArray

/**
 * Aerodock's persistent on-device memory: durable facts about the user,
 * their projects and their preferences, saved across chats. This sits
 * ALONGSIDE the cross-chat work brief (v2.7 previousWorkBrief, which
 * distils the last chat's tool work) — the brief says what was done
 * recently; these facts say what is true about the user in general.
 *
 * Storage is SharedPreferences holding a JSON array of strings — the
 * same pattern as KairoRelayPrefs — so there is no Room migration.
 * Facts are added by the user in Settings (Memory card) or by the model
 * through the approval-gated `remember_fact` tool, and the whole list
 * rides in every new chat's system prompt (see promptBlock).
 */
object AerodockMemory {

    private const val PREFS_NAME = "aerodock_memory"
    private const val KEY_FACTS = "facts_json"
    private const val MAX_FACTS = 100
    private const val MAX_FACT_CHARS = 300
    private const val MAX_PROMPT_CHARS = 2000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadFacts(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_FACTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val fact = array.optString(i, "").trim()
                    if (fact.isNotEmpty()) add(fact)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveFacts(context: Context, facts: List<String>) {
        val array = JSONArray()
        facts.forEach { array.put(it) }
        prefs(context).edit().putString(KEY_FACTS, array.toString()).apply()
    }

    /**
     * Adds [fact]; returns false when it was blank, too long after
     * trimming is NOT a failure (it is truncated), a duplicate of an
     * existing fact, or the store is full.
     */
    fun addFact(context: Context, fact: String): Boolean {
        val clean = fact.replace(Regex("\\s+"), " ").trim().take(MAX_FACT_CHARS)
        if (clean.isEmpty()) return false
        val facts = loadFacts(context)
        if (facts.any { it.equals(clean, ignoreCase = true) }) return false
        if (facts.size >= MAX_FACTS) return false
        saveFacts(context, facts + clean)
        return true
    }

    fun removeFact(context: Context, fact: String) {
        saveFacts(context, loadFacts(context).filterNot { it == fact })
    }

    fun clearFacts(context: Context) {
        prefs(context).edit().remove(KEY_FACTS).apply()
    }

    /**
     * The system-prompt block carrying the saved facts, or null when the
     * store is empty. Capped so a long memory can never crowd out the
     * actual conversation.
     */
    fun promptBlock(context: Context): String? {
        val facts = loadFacts(context)
        if (facts.isEmpty()) return null
        val block = buildString {
            append("What you remember about the user (facts they saved in this app, stored on-device):")
            for (fact in facts) {
                append("\n- ").append(fact)
            }
        }
        return if (block.length <= MAX_PROMPT_CHARS) {
            block
        } else {
            block.substring(0, MAX_PROMPT_CHARS).substringBeforeLast("\n- ")
        }
    }
}
