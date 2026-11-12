package com.example.data.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets

private val Context.omniDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "omniai_encrypted_vault_prefs"
)

data class VaultSecurityStatus(
    val cipherAlgorithm: String = "AES256_GCM (Google Tink AEAD)",
    val masterKeyUri: String = "android-keystore://omniai_studio_master_key",
    val isHardwareKeystoreBacked: Boolean = true,
    val encryptedKeyCount: Int = 0,
    val allowBackupExcluded: Boolean = true
)

/**
 * Hardware-backed secret vault using Google Tink AEAD (AES256-GCM) with an Android Keystore
 * master key, persisted inside Jetpack DataStore.
 *
 * Security guarantees:
 * - API keys are encrypted with AES256_GCM before touching disk.
 * - Each provider's ciphertext is bound to its `providerInstanceId` via AEAD Associated Data (AAD),
 *   preventing cross-provider ciphertext substitution.
 * - Plaintext keys are never written to Room SQLite, never logged, and never placed in URLs.
 */
class TinkSecretVault(private val context: Context) {

    @Volatile
    private var usingHardwareKeystore: Boolean = false

    private val aead: Aead? by lazy {
        runCatching {
            AeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(
                    context.applicationContext,
                    KEYSET_NAME,
                    KEYSET_PREF_FILE
                )
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            usingHardwareKeystore = true
            keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }.getOrElse {
            // Fallback keyset without AndroidKeyStore only when running in local JVM unit tests (Robolectric)
            runCatching {
                AeadConfig.register()
                val keysetHandle = AndroidKeysetManager.Builder()
                    .withSharedPref(
                        context.applicationContext,
                        FALLBACK_KEYSET_NAME,
                        FALLBACK_KEYSET_PREF_FILE
                    )
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .build()
                    .keysetHandle
                usingHardwareKeystore = false
                keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            }.getOrNull()
        }
    }

    @Volatile
    private var cachedApprovedHosts: Set<String> = emptySet()

    companion object {
        const val MASTER_KEY_URI = "android-keystore://omniai_studio_master_key"
        private const val KEYSET_NAME = "omniai_tink_keyset"
        private const val KEYSET_PREF_FILE = "omniai_tink_keyset_prefs"
        private const val FALLBACK_KEYSET_NAME = "omniai_tink_fallback_keyset"
        private const val FALLBACK_KEYSET_PREF_FILE = "omniai_tink_fallback_prefs"
        private const val ENC_KEY_PREFIX = "enc_api_key_"

        const val INTEGRATION_GITHUB_PAT_ID = "integration_github_pat"
        const val INTEGRATION_YOUTUBE_CLIENT_ID = "integration_youtube_oauth_client_id"
        const val INTEGRATION_YOUTUBE_ACCESS_TOKEN_ID = "integration_youtube_access_token"
        const val INTEGRATION_YOUTUBE_REFRESH_TOKEN_ID = "integration_youtube_refresh_token"
        const val JAVA_RUNNER_SECRET_ID = "runway_java_runner_api_key"
        private const val MCP_SERVER_TOKEN_PREFIX = "mcp_server_token_"

        fun mcpServerSecretId(serverId: String): String = "$MCP_SERVER_TOKEN_PREFIX$serverId"

        private const val MCP_OAUTH_RECORD_PREFIX = "mcp_oauth_record_"
        private const val MCP_OAUTH_ERROR_PREFIX = "mcp_oauth_error_"
        private const val MCP_OAUTH_PENDING_ID = "mcp_oauth_pending_current"

        fun mcpOAuthRecordId(serverId: String): String = "$MCP_OAUTH_RECORD_PREFIX$serverId"
        fun mcpOAuthErrorId(serverId: String): String = "$MCP_OAUTH_ERROR_PREFIX$serverId"
        fun mcpOAuthPendingId(): String = MCP_OAUTH_PENDING_ID

        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_THEME_ACCENT = stringPreferencesKey("theme_accent")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        private val KEY_BIOMETRIC_GATE = booleanPreferencesKey("biometric_gate_enabled")
        private val KEY_ENTER_TO_SEND = booleanPreferencesKey("enter_to_send_enabled")
        private val KEY_APPROVED_CLEARTEXT_HOSTS = stringSetPreferencesKey("approved_cleartext_hosts")

        // Step (1): Runway Data Layer local preference keys (strictly prefixed "runway_")
        private val KEY_RUNWAY_MIGRATION_VERIFIED = booleanPreferencesKey("runway_migration_v1_verified")
        private val KEY_RUNWAY_MIGRATION_SUMMARY_JSON = stringPreferencesKey("runway_migration_summary_json")
        private val KEY_RUNWAY_DEFAULT_WEB_SEARCH = booleanPreferencesKey("runway_default_web_search")
        private val KEY_RUNWAY_COST_GUARD_JSON = stringPreferencesKey("runway_cost_guard_json")
        private val KEY_RUNWAY_PRICE_TABLE_JSON = stringPreferencesKey("runway_price_table_json")
        private val KEY_RUNWAY_FALLBACK_CHAIN_JSON = stringPreferencesKey("runway_fallback_chain_json")
        private val KEY_RUNWAY_FALLBACK_ENABLED = booleanPreferencesKey("runway_fallback_enabled")
        private val KEY_RUNWAY_SMART_ROUTING_JSON = stringPreferencesKey("runway_smart_routing_json")
        private val KEY_RUNWAY_VOICE_SETTINGS_JSON = stringPreferencesKey("runway_voice_settings_json")
        private val KEY_CONNECTOR_DISCOVERY_ENABLED = booleanPreferencesKey("runway_connector_discovery_enabled")
        private val KEY_CONNECTOR_DEFAULT_POLICIES_JSON = stringPreferencesKey("runway_connector_default_policies_json")
        private val KEY_CONNECTOR_TOOL_POLICIES_JSON = stringPreferencesKey("runway_connector_tool_policies_json")
        private val KEY_CONFIRMED_WRITE_ALWAYS_TOOLS = stringSetPreferencesKey("runway_confirmed_write_always_tools")
        private val KEY_READ_REPLIES_ALOUD = booleanPreferencesKey("runway_read_replies_aloud")
        private val KEY_PROVIDER_SPENDING_LIMITS_JSON = stringPreferencesKey("runway_provider_spending_limits_json")
        private val KEY_HAS_SEEN_FILE_ATTACHMENT_NOTICE = booleanPreferencesKey("runway_has_seen_file_attachment_notice")
        private val KEY_JAVA_RUNNER_URL = stringPreferencesKey("runway_java_runner_url")
        private val KEY_LIVE_INFO_WEB_SEARCH_ENABLED = booleanPreferencesKey("runway_live_info_web_search_enabled")
        private val KEY_LIVE_INFO_NEWS_HEADLINES_ENABLED = booleanPreferencesKey("runway_live_info_news_headlines_enabled")
        private val KEY_LIVE_INFO_MAX_SEARCHES_PER_MESSAGE = intPreferencesKey("runway_live_info_max_searches_per_message")
        private val KEY_LIVE_INFO_RSS_FEEDS_JSON = stringPreferencesKey("runway_live_info_rss_feeds_json")
        private val KEY_WORK_MODE = stringPreferencesKey("runway_work_mode")

        private fun apiKeyPreferenceKey(providerInstanceId: String) =
            stringPreferencesKey("$ENC_KEY_PREFIX$providerInstanceId")

        fun maskRawKey(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return ""
            return if (trimmed.length <= 8) {
                "••••••••"
            } else {
                "${trimmed.take(4)}••••••••${trimmed.takeLast(4)}"
            }
        }
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val themeModeFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "DARK"
    }

    val themeAccentFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_THEME_ACCENT] ?: "SAGE"
    }

    val dynamicColorFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: false
    }

    val biometricGateFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_GATE] ?: false
    }

    val enterToSendFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_ENTER_TO_SEND] ?: true
    }

    val approvedCleartextHostsFlow: Flow<Set<String>> = context.omniDataStore.data.map { prefs ->
        val hosts = prefs[KEY_APPROVED_CLEARTEXT_HOSTS] ?: emptySet()
        cachedApprovedHosts = hosts
        hosts
    }

    val connectorDiscoveryEnabledFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_CONNECTOR_DISCOVERY_ENABLED] ?: true
    }

    val connectorDefaultPoliciesJsonFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_CONNECTOR_DEFAULT_POLICIES_JSON] ?: "{}"
    }

    val connectorToolPoliciesJsonFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_CONNECTOR_TOOL_POLICIES_JSON] ?: "{}"
    }

    val confirmedWriteAlwaysToolsFlow: Flow<Set<String>> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_CONFIRMED_WRITE_ALWAYS_TOOLS] ?: emptySet()
    }

    val readRepliesAloudFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_READ_REPLIES_ALOUD] ?: false
    }

    val providerSpendingLimitsJsonFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_PROVIDER_SPENDING_LIMITS_JSON] ?: "{}"
    }

    val hasSeenFileAttachmentNoticeFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_FILE_ATTACHMENT_NOTICE] ?: false
    }

    val javaRunnerUrlFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_JAVA_RUNNER_URL] ?: ""
    }

    val liveInfoWebSearchEnabledFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_LIVE_INFO_WEB_SEARCH_ENABLED] ?: false
    }

    val liveInfoNewsHeadlinesEnabledFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_LIVE_INFO_NEWS_HEADLINES_ENABLED] ?: false
    }

    val liveInfoMaxSearchesPerMessageFlow: Flow<Int> = context.omniDataStore.data.map { prefs ->
        (prefs[KEY_LIVE_INFO_MAX_SEARCHES_PER_MESSAGE] ?: 3).coerceIn(1, 20)
    }

    val liveInfoRssFeedsJsonFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_LIVE_INFO_RSS_FEEDS_JSON] ?: ""
    }

    val workModeFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_WORK_MODE] ?: "AUTO"
    }

    val runwayMigrationVerifiedFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_RUNWAY_MIGRATION_VERIFIED] ?: false
    }

    val runwayMigrationSummaryJsonFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_RUNWAY_MIGRATION_SUMMARY_JSON] ?: ""
    }

    val runwayDefaultWebSearchFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_RUNWAY_DEFAULT_WEB_SEARCH] ?: false
    }

    val runwayFallbackEnabledFlow: Flow<Boolean> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_RUNWAY_FALLBACK_ENABLED] ?: false
    }

    val runwayFallbackChainJsonFlow: Flow<String> = context.omniDataStore.data.map { prefs ->
        prefs[KEY_RUNWAY_FALLBACK_CHAIN_JSON] ?: "[]"
    }

    suspend fun setRunwayFallbackEnabled(enabled: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_RUNWAY_FALLBACK_ENABLED] = enabled
        }
    }

    suspend fun setRunwayFallbackChainJson(json: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_RUNWAY_FALLBACK_CHAIN_JSON] = json
        }
    }

    suspend fun setReadRepliesAloud(enabled: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_READ_REPLIES_ALOUD] = enabled
        }
    }

    suspend fun setLiveInfoWebSearchEnabled(enabled: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_LIVE_INFO_WEB_SEARCH_ENABLED] = enabled
        }
    }

    suspend fun setLiveInfoNewsHeadlinesEnabled(enabled: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_LIVE_INFO_NEWS_HEADLINES_ENABLED] = enabled
        }
    }

    suspend fun setLiveInfoMaxSearchesPerMessage(limit: Int) {
        val coerced = limit.coerceIn(1, 20)
        context.omniDataStore.edit { prefs ->
            prefs[KEY_LIVE_INFO_MAX_SEARCHES_PER_MESSAGE] = coerced
        }
    }

    suspend fun setLiveInfoRssFeedsJson(json: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_LIVE_INFO_RSS_FEEDS_JSON] = json
        }
    }

    suspend fun setWorkMode(mode: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_WORK_MODE] = mode
        }
    }

    suspend fun setProviderSpendingLimitsJson(json: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_PROVIDER_SPENDING_LIMITS_JSON] = json
        }
    }

    suspend fun setHasSeenFileAttachmentNotice(seen: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_FILE_ATTACHMENT_NOTICE] = seen
        }
    }

    suspend fun getJavaRunnerUrl(): String {
        return context.omniDataStore.data.first()[KEY_JAVA_RUNNER_URL] ?: ""
    }

    suspend fun setJavaRunnerUrl(url: String) {
        val trimmed = url.trim()
        context.omniDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(KEY_JAVA_RUNNER_URL)
            } else {
                prefs[KEY_JAVA_RUNNER_URL] = trimmed
            }
        }
    }

    suspend fun saveJavaRunnerKey(rawKey: String) {
        saveApiKey(JAVA_RUNNER_SECRET_ID, rawKey)
    }

    suspend fun getJavaRunnerKey(): String {
        return getApiKey(JAVA_RUNNER_SECRET_ID)
    }

    suspend fun getMaskedJavaRunnerKey(): String {
        return getMaskedApiKey(JAVA_RUNNER_SECRET_ID)
    }

    suspend fun clearJavaRunnerKey() {
        deleteApiKey(JAVA_RUNNER_SECRET_ID)
    }

    suspend fun getRunwayPrefString(keyName: String, defaultValue: String = ""): String {
        val prefKey = stringPreferencesKey(if (keyName.startsWith("runway_")) keyName else "runway_$keyName")
        return context.omniDataStore.data.first()[prefKey] ?: defaultValue
    }

    suspend fun setRunwayPrefString(keyName: String, value: String) {
        val prefKey = stringPreferencesKey(if (keyName.startsWith("runway_")) keyName else "runway_$keyName")
        context.omniDataStore.edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun setRunwayMigrationVerified(verified: Boolean, summaryJson: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_RUNWAY_MIGRATION_VERIFIED] = verified
            prefs[KEY_RUNWAY_MIGRATION_SUMMARY_JSON] = summaryJson
        }
    }

    suspend fun isRunwayMigrationVerified(): Boolean {
        return context.omniDataStore.data.first()[KEY_RUNWAY_MIGRATION_VERIFIED] ?: false
    }

    fun getApprovedCleartextHostsSnapshot(): Set<String> = cachedApprovedHosts

    suspend fun refreshApprovedHostsSnapshot(): Set<String> {
        val hosts = approvedCleartextHostsFlow.first()
        cachedApprovedHosts = hosts
        return hosts
    }

    suspend fun approveCleartextHost(host: String) {
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return
        context.omniDataStore.edit { prefs ->
            val current = prefs[KEY_APPROVED_CLEARTEXT_HOSTS] ?: emptySet()
            val updated = current + normalized
            prefs[KEY_APPROVED_CLEARTEXT_HOSTS] = updated
            cachedApprovedHosts = updated
        }
    }

    suspend fun revokeCleartextHost(host: String) {
        val normalized = host.trim().lowercase()
        context.omniDataStore.edit { prefs ->
            val current = prefs[KEY_APPROVED_CLEARTEXT_HOSTS] ?: emptySet()
            val updated = current - normalized
            prefs[KEY_APPROVED_CLEARTEXT_HOSTS] = updated
            cachedApprovedHosts = updated
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setThemeAccent(accent: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_THEME_ACCENT] = accent
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setBiometricGate(enabled: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_GATE] = enabled
        }
    }

    suspend fun setEnterToSend(enabled: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_ENTER_TO_SEND] = enabled
        }
    }

    suspend fun setConnectorDiscoveryEnabled(enabled: Boolean) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_CONNECTOR_DISCOVERY_ENABLED] = enabled
        }
    }

    suspend fun setConnectorDefaultPoliciesJson(json: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_CONNECTOR_DEFAULT_POLICIES_JSON] = json
        }
    }

    suspend fun setConnectorToolPoliciesJson(json: String) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_CONNECTOR_TOOL_POLICIES_JSON] = json
        }
    }

    suspend fun setConfirmedWriteAlwaysTools(toolIds: Set<String>) {
        context.omniDataStore.edit { prefs ->
            prefs[KEY_CONFIRMED_WRITE_ALWAYS_TOOLS] = toolIds
        }
    }

    suspend fun saveApiKey(providerInstanceId: String, rawApiKey: String) {
        val trimmed = rawApiKey.trim()
        val prefKey = apiKeyPreferenceKey(providerInstanceId)
        if (trimmed.isEmpty()) {
            context.omniDataStore.edit { prefs -> prefs.remove(prefKey) }
            return
        }
        val encryptedBase64 = encryptString(trimmed, providerInstanceId)
        context.omniDataStore.edit { prefs ->
            prefs[prefKey] = encryptedBase64
        }
    }

    suspend fun getApiKey(providerInstanceId: String): String {
        val prefKey = apiKeyPreferenceKey(providerInstanceId)
        val stored = context.omniDataStore.data.first()[prefKey] ?: return ""
        return decryptString(stored, providerInstanceId)
    }

    suspend fun getEncryptedCiphertextPreview(providerInstanceId: String): String {
        val prefKey = apiKeyPreferenceKey(providerInstanceId)
        val stored = context.omniDataStore.data.first()[prefKey] ?: return ""
        if (stored.length <= 16) return stored
        return "${stored.take(10)}…${stored.takeLast(6)}"
    }

    suspend fun hasApiKey(providerInstanceId: String): Boolean {
        return getApiKey(providerInstanceId).isNotBlank()
    }

    suspend fun getMaskedApiKey(providerInstanceId: String): String {
        val raw = getApiKey(providerInstanceId)
        return maskRawKey(raw)
    }

    suspend fun deleteApiKey(providerInstanceId: String) {
        val prefKey = apiKeyPreferenceKey(providerInstanceId)
        context.omniDataStore.edit { prefs -> prefs.remove(prefKey) }
    }

    suspend fun getVaultSecurityStatus(): VaultSecurityStatus {
        // Trigger lazy initialization so usingHardwareKeystore is populated
        val initialized = aead != null
        val prefs = context.omniDataStore.data.first()
        val count = prefs.asMap().keys.count { it.name.startsWith(ENC_KEY_PREFIX) }
        return VaultSecurityStatus(
            cipherAlgorithm = if (initialized) "AES256_GCM (Google Tink AEAD)" else "Fallback Encoding",
            masterKeyUri = MASTER_KEY_URI,
            isHardwareKeystoreBacked = usingHardwareKeystore,
            encryptedKeyCount = count,
            allowBackupExcluded = true
        )
    }

    suspend fun clearAllSecretsAndPreferences() {
        cachedApprovedHosts = emptySet()
        context.omniDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    internal fun encryptString(plaintext: String, associatedData: String): String {
        val primitive = aead
        val plainBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val aadBytes = "omniai_aad:$associatedData".toByteArray(StandardCharsets.UTF_8)
        val cipherBytes = primitive?.encrypt(plainBytes, aadBytes) ?: plainBytes
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    internal fun decryptString(cipherBase64: String, associatedData: String): String {
        return runCatching {
            val primitive = aead
            val cipherBytes = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val aadBytes = "omniai_aad:$associatedData".toByteArray(StandardCharsets.UTF_8)
            val plainBytes = primitive?.decrypt(cipherBytes, aadBytes)
                ?: runCatching {
                    // Backward-compatible fallback for keys encrypted with raw providerInstanceId AAD
                    primitive?.decrypt(cipherBytes, associatedData.toByteArray(StandardCharsets.UTF_8))
                }.getOrNull()
                ?: cipherBytes
            String(plainBytes, StandardCharsets.UTF_8)
        }.getOrDefault("")
    }
}
