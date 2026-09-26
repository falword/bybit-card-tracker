package com.sai.cardtrack.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.sai.cardtrack.applyAppLocale
import com.sai.cardtrack.ui.LanguageMode
import com.sai.cardtrack.ui.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppearancePrefs(
    val language: LanguageMode = LanguageMode.System,
    val theme: ThemeMode = ThemeMode.System
)

class AppearanceStore(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppearancePrefs> = _state

    fun setLanguage(mode: LanguageMode) {
        prefs.edit { putString(KEY_LANGUAGE, mode.name) }
        _state.value = read()
        applyAppLocale(mode)
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _state.value = read()
    }

    private fun read(): AppearancePrefs {
        return AppearancePrefs(
            language = enumOr(prefs.getString(KEY_LANGUAGE, null), LanguageMode.System),
            theme = enumOr(prefs.getString(KEY_THEME, null), ThemeMode.System)
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { java.lang.Enum.valueOf(T::class.java, raw) }.getOrDefault(fallback)
    }

    companion object {
        const val PREFS_NAME: String = "cardtrack_appearance"
        const val KEY_LANGUAGE: String = "language_mode"
        const val KEY_THEME: String = "theme_mode"
    }
}
