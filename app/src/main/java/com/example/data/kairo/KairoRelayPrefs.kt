package com.example.data.kairo

import android.content.Context

/**
 * On/off switch and relay URL for Kairo access (see KairoRelayService).
 * The pairing token itself never lives here — it is stored in the Tink
 * vault under [TOKEN_RECORD_ID], like every other secret in the app.
 */
object KairoRelayPrefs {

    const val TOKEN_RECORD_ID = "kairo_relay_token"

    private const val PREFS_NAME = "kairo_relay"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_URL = "relay_url"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun relayUrl(context: Context): String =
        prefs(context).getString(KEY_URL, "")?.trim().orEmpty()

    fun setRelayUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_URL, url.trim()).apply()
    }
}
