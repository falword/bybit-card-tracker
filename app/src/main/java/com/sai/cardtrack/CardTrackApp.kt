package com.sai.cardtrack

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sai.cardtrack.data.AppearanceStore
import com.sai.cardtrack.ui.LanguageMode

class CardTrackApp : Application() {
    lateinit var container: AppContainer
        private set
    lateinit var appearance: AppearanceStore
        private set

    override fun onCreate() {
        super.onCreate()
        appearance = AppearanceStore(getSharedPreferences(AppearanceStore.PREFS_NAME, MODE_PRIVATE))
        applyAppLocale(appearance.state.value.language)
        container = AppContainer(this)
    }
}

fun applyAppLocale(mode: LanguageMode) {
    val locales = when (mode) {
        LanguageMode.System -> LocaleListCompat.getEmptyLocaleList()
        LanguageMode.Ru -> LocaleListCompat.forLanguageTags("ru")
        LanguageMode.En -> LocaleListCompat.forLanguageTags("en")
    }
    AppCompatDelegate.setApplicationLocales(locales)
}
