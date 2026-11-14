package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.FontSizePreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.rikzenDataStore: DataStore<Preferences> by preferencesDataStore(name = "rikzen_preferences")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentPreset: AccentPreset = AccentPreset.CHAMPAGNE_GOLD,
    val fontSizePreset: FontSizePreset = FontSizePreset.STANDARD,
    val chatWallpaperEnabled: Boolean = true,
    val notifyMessages: Boolean = true,
    val notifyFriendRequests: Boolean = true,
    val notifyGroups: Boolean = true,
    val notifyCalls: Boolean = true,
    val notifyStatuses: Boolean = true,
    val soundAndVibration: Boolean = true,
    val autoDownloadMedia: Boolean = true,
    val appLanguage: String = "English"
)

class PreferencesRepository(private val context: Context) {
    private val dataStore = context.applicationContext.rikzenDataStore

    val preferencesFlow: Flow<UserPreferences> = dataStore.data
        .map { prefs ->
            UserPreferences(
                themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
                accentPreset = prefs[KEY_ACCENT_PRESET]?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() } ?: AccentPreset.CHAMPAGNE_GOLD,
                fontSizePreset = prefs[KEY_FONT_SIZE]?.let { runCatching { FontSizePreset.valueOf(it) }.getOrNull() } ?: FontSizePreset.STANDARD,
                chatWallpaperEnabled = prefs[KEY_CHAT_WALLPAPER] ?: true,
                notifyMessages = prefs[KEY_NOTIFY_MESSAGES] ?: true,
                notifyFriendRequests = prefs[KEY_NOTIFY_REQUESTS] ?: true,
                notifyGroups = prefs[KEY_NOTIFY_GROUPS] ?: true,
                notifyCalls = prefs[KEY_NOTIFY_CALLS] ?: true,
                notifyStatuses = prefs[KEY_NOTIFY_STATUSES] ?: true,
                soundAndVibration = prefs[KEY_SOUND_VIBRATION] ?: true,
                autoDownloadMedia = prefs[KEY_AUTO_DOWNLOAD] ?: true,
                appLanguage = prefs[KEY_LANGUAGE] ?: "English"
            )
        }
        .distinctUntilChanged()

    fun observeDraft(conversationId: String): Flow<String> = dataStore.data
        .map { prefs -> prefs[stringPreferencesKey("draft_$conversationId")] ?: "" }
        .distinctUntilChanged()

    suspend fun saveDraft(conversationId: String, text: String) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey("draft_$conversationId")
            if (text.isBlank()) prefs.remove(key) else prefs[key] = text
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        dataStore.edit { it[KEY_ACCENT_PRESET] = preset.name }
    }

    suspend fun setFontSizePreset(preset: FontSizePreset) {
        dataStore.edit { it[KEY_FONT_SIZE] = preset.name }
    }

    suspend fun setChatWallpaperEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CHAT_WALLPAPER] = enabled }
    }

    suspend fun setNotificationPreference(key: Preferences.Key<Boolean>, enabled: Boolean) {
        dataStore.edit { it[key] = enabled }
    }

    suspend fun setAutoDownloadMedia(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_DOWNLOAD] = enabled }
    }

    suspend fun setAppLanguage(language: String) {
        dataStore.edit { it[KEY_LANGUAGE] = language }
    }

    companion object {
        @Volatile
        private var instance: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also { instance = it }
            }
        }

        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val KEY_FONT_SIZE = stringPreferencesKey("font_size")
        val KEY_CHAT_WALLPAPER = booleanPreferencesKey("chat_wallpaper")
        val KEY_NOTIFY_MESSAGES = booleanPreferencesKey("notify_messages")
        val KEY_NOTIFY_REQUESTS = booleanPreferencesKey("notify_requests")
        val KEY_NOTIFY_GROUPS = booleanPreferencesKey("notify_groups")
        val KEY_NOTIFY_CALLS = booleanPreferencesKey("notify_calls")
        val KEY_NOTIFY_STATUSES = booleanPreferencesKey("notify_statuses")
        val KEY_SOUND_VIBRATION = booleanPreferencesKey("sound_vibration")
        val KEY_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_media")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
    }
}
