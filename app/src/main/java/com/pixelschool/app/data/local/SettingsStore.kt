package com.pixelschool.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemePalette(val title: String, val previewColor: Color) {
    DYNAMIC("Системная (Material You)", Color(0xFF6750A4)),
    COBALT("Сапфир (Cobalt)", Color(0xFF1E56A0)),
    EMERALD("Изумруд (Emerald)", Color(0xFF00796B)),
    VIOLET("Фиолетовый (Violet)", Color(0xFF7C3AED)),
    AMBER("Янтарь (Sunset Amber)", Color(0xFFD97706)),
    ROSE("Розе (Coral Rose)", Color(0xFFBE185D))
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixel_school_settings", Context.MODE_PRIVATE)

    private val _themePalette = MutableStateFlow(loadThemePalette())
    val themePalette: StateFlow<AppThemePalette> = _themePalette.asStateFlow()

    private fun loadThemePalette(): AppThemePalette {
        val savedName = prefs.getString("key_theme_palette", AppThemePalette.DYNAMIC.name)
        return try {
            AppThemePalette.valueOf(savedName ?: AppThemePalette.DYNAMIC.name)
        } catch (_: Exception) {
            AppThemePalette.DYNAMIC
        }
    }

    fun setThemePalette(palette: AppThemePalette) {
        prefs.edit().putString("key_theme_palette", palette.name).apply()
        _themePalette.value = palette
    }

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("key_notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("key_notifications_enabled", enabled).apply()
        _notificationsEnabled.value = enabled
    }

    private val _geminiApiKey = MutableStateFlow(prefs.getString("key_gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun setGeminiApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("key_gemini_api_key", trimmed).apply()
        _geminiApiKey.value = trimmed
    }

    private val _homeworkReminderEnabled =
        MutableStateFlow(prefs.getBoolean("key_homework_reminder_enabled", false))
    val homeworkReminderEnabled: StateFlow<Boolean> = _homeworkReminderEnabled.asStateFlow()

    fun setHomeworkReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("key_homework_reminder_enabled", enabled).apply()
        _homeworkReminderEnabled.value = enabled
    }

    private val _homeworkReminderTime =
        MutableStateFlow(prefs.getString("key_homework_reminder_time", "19:00") ?: "19:00")
    val homeworkReminderTime: StateFlow<String> = _homeworkReminderTime.asStateFlow()

    fun setHomeworkReminderTime(time: String) {
        prefs.edit().putString("key_homework_reminder_time", time).apply()
        _homeworkReminderTime.value = time
    }
}
