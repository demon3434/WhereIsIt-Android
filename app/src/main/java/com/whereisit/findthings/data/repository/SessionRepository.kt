package com.whereisit.findthings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URI

private val Context.settingsDataStore by preferencesDataStore(name = "findthings_settings")

enum class ActiveEndpoint { INTERNAL, EXTERNAL }

enum class AppTheme { SAND, MINT, SKY, PEACH }

data class SessionSettings(
    val internalUrl: String,
    val externalUrl: String,
    val activeEndpoint: ActiveEndpoint,
    val token: String,
    val appTheme: AppTheme,
    val lastSuccessBaseUrl: String,
    val biometricUnlockEnabled: Boolean = false,
    val currentUsername: String = "",
    val currentFullName: String = "",
    val currentNickname: String = ""
) {
    fun activeBaseUrl(): String {
        val raw = when (activeEndpoint) {
            ActiveEndpoint.INTERNAL -> internalUrl
            ActiveEndpoint.EXTERNAL -> externalUrl
        }.trim()
        return normalizeBaseUrl(raw)
    }

    fun fallbackBaseUrl(): String {
        val raw = when (activeEndpoint) {
            ActiveEndpoint.INTERNAL -> externalUrl
            ActiveEndpoint.EXTERNAL -> internalUrl
        }.trim()
        return normalizeBaseUrl(raw)
    }

    fun currentDisplayName(): String {
        return currentFullName.ifBlank { currentNickname }.ifBlank { currentUsername }
    }
}

class SessionRepository(private val context: Context) {
    private object Keys {
        val internal = stringPreferencesKey("internal_url")
        val external = stringPreferencesKey("external_url")
        val active = stringPreferencesKey("active_endpoint")
        val token = stringPreferencesKey("token")
        val theme = stringPreferencesKey("app_theme")
        val lastSuccessBaseUrl = stringPreferencesKey("last_success_base_url")
        val biometricUnlockEnabled = booleanPreferencesKey("biometric_unlock_enabled")
        val currentUsername = stringPreferencesKey("current_username")
        val currentFullName = stringPreferencesKey("current_full_name")
        val currentNickname = stringPreferencesKey("current_nickname")
    }

    val settings: Flow<SessionSettings> = context.settingsDataStore.data.map { pref ->
        pref.toSession()
    }

    suspend fun current(): SessionSettings = settings.first()

    suspend fun saveServerSettings(internal: String, external: String, active: ActiveEndpoint) {
        context.settingsDataStore.edit {
            it[Keys.internal] = internal.trim()
            it[Keys.external] = external.trim()
            it[Keys.active] = active.name
        }
    }

    suspend fun saveTheme(theme: AppTheme) {
        context.settingsDataStore.edit {
            it[Keys.theme] = theme.name
        }
    }

    suspend fun saveBiometricUnlockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[Keys.biometricUnlockEnabled] = enabled
        }
    }

    suspend fun setToken(token: String) {
        context.settingsDataStore.edit { it[Keys.token] = token }
    }

    suspend fun saveCurrentUser(username: String, fullName: String = "", nickname: String = "") {
        context.settingsDataStore.edit {
            it[Keys.currentUsername] = username.trim()
            it[Keys.currentFullName] = fullName.trim()
            it[Keys.currentNickname] = nickname.trim()
        }
    }

    suspend fun saveLastSuccessBaseUrl(baseUrl: String) {
        context.settingsDataStore.edit { it[Keys.lastSuccessBaseUrl] = baseUrl.trim() }
    }

    suspend fun clearToken() {
        context.settingsDataStore.edit { it[Keys.token] = "" }
    }

    suspend fun clearSessionState() {
        context.settingsDataStore.edit {
            it[Keys.token] = ""
            it[Keys.lastSuccessBaseUrl] = ""
            it[Keys.currentUsername] = ""
            it[Keys.currentFullName] = ""
            it[Keys.currentNickname] = ""
        }
    }

    suspend fun switchEndpoint() {
        context.settingsDataStore.edit {
            val current = ActiveEndpoint.valueOf(it[Keys.active] ?: ActiveEndpoint.INTERNAL.name)
            it[Keys.active] = if (current == ActiveEndpoint.INTERNAL) ActiveEndpoint.EXTERNAL.name else ActiveEndpoint.INTERNAL.name
        }
    }

    private fun Preferences.toSession(): SessionSettings {
        return SessionSettings(
            internalUrl = this[Keys.internal] ?: "",
            externalUrl = this[Keys.external] ?: "",
            activeEndpoint = ActiveEndpoint.valueOf(this[Keys.active] ?: ActiveEndpoint.INTERNAL.name),
            token = this[Keys.token] ?: "",
            appTheme = AppTheme.valueOf(this[Keys.theme] ?: AppTheme.SAND.name),
            lastSuccessBaseUrl = this[Keys.lastSuccessBaseUrl] ?: "",
            biometricUnlockEnabled = this[Keys.biometricUnlockEnabled] ?: false,
            currentUsername = this[Keys.currentUsername] ?: "",
            currentFullName = this[Keys.currentFullName] ?: "",
            currentNickname = this[Keys.currentNickname] ?: ""
        )
    }
}

private fun normalizeBaseUrl(raw: String): String {
    if (raw.isBlank()) return ""
    val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
    return try {
        val uri = URI(withScheme)
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: return ""
        val port = if (uri.port == -1) 3000 else uri.port
        val path = uri.path?.trim()?.trimEnd('/') ?: ""
        val normalizedPath = if (path.isNotBlank()) "$path/" else "/"
        "$scheme://$host:$port$normalizedPath"
    } catch (_: Exception) {
        ""
    }
}
