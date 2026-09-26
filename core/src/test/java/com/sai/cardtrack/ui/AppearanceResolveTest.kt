package com.sai.cardtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceResolveTest {

    @Test
    fun `system language ru maps to Russian`() {
        assertEquals(AppLocale.Ru, AppearanceResolve.locale(LanguageMode.System, "ru"))
        assertEquals(AppLocale.Ru, AppearanceResolve.locale(LanguageMode.System, "ru-RU"))
    }

    @Test
    fun `system language en maps to English`() {
        assertEquals(AppLocale.En, AppearanceResolve.locale(LanguageMode.System, "en"))
        assertEquals(AppLocale.En, AppearanceResolve.locale(LanguageMode.System, "en-US"))
        assertEquals(AppLocale.En, AppearanceResolve.locale(LanguageMode.System, "th"))
    }

    @Test
    fun `explicit language ignores the device`() {
        assertEquals(AppLocale.En, AppearanceResolve.locale(LanguageMode.En, "ru"))
        assertEquals(AppLocale.Ru, AppearanceResolve.locale(LanguageMode.Ru, "en-US"))
    }

    @Test
    fun `system theme follows the device`() {
        assertTrue(AppearanceResolve.dark(ThemeMode.System, true))
        assertFalse(AppearanceResolve.dark(ThemeMode.System, false))
    }

    @Test
    fun `explicit theme ignores the device`() {
        assertTrue(AppearanceResolve.dark(ThemeMode.Dark, false))
        assertFalse(AppearanceResolve.dark(ThemeMode.Light, true))
    }
}
