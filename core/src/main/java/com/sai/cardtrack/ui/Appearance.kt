package com.sai.cardtrack.ui

enum class AppLocale { Ru, En }

enum class LanguageMode { System, Ru, En }

enum class ThemeMode { System, Dark, Light }

object AppearanceResolve {
    fun locale(mode: LanguageMode, systemLanguage: String): AppLocale {
        return when (mode) {
            LanguageMode.Ru -> AppLocale.Ru
            LanguageMode.En -> AppLocale.En
            LanguageMode.System -> {
                if (systemLanguage.lowercase().startsWith("ru")) AppLocale.Ru else AppLocale.En
            }
        }
    }

    fun dark(mode: ThemeMode, systemDark: Boolean): Boolean {
        return when (mode) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.System -> systemDark
        }
    }
}
