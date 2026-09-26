package com.sai.cardtrack.data

import com.sai.cardtrack.ui.UiCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedDbPassphrasePolicyTest {

    @Test
    fun `wrap missing and encrypted leftover must not mint`() {
        assertFalse(
            EncryptedDb.canCreatePassphrase(
                hasWrap = false,
                fileExists = true,
                looksPlaintext = false
            )
        )
    }

    @Test
    fun `wrap missing and plaintext or no file may mint`() {
        assertTrue(
            EncryptedDb.canCreatePassphrase(
                hasWrap = false,
                fileExists = true,
                looksPlaintext = true
            )
        )
        assertTrue(
            EncryptedDb.canCreatePassphrase(
                hasWrap = false,
                fileExists = false,
                looksPlaintext = false
            )
        )
    }

    @Test
    fun `wrap present may mint or unwrap`() {
        assertTrue(
            EncryptedDb.canCreatePassphrase(
                hasWrap = true,
                fileExists = true,
                looksPlaintext = false
            )
        )
        assertTrue(
            EncryptedDb.canCreatePassphrase(
                hasWrap = true,
                fileExists = false,
                looksPlaintext = false
            )
        )
    }

    @Test
    fun `open-failure copy is exact`() {
        assertEquals(
            "Не удалось открыть базу. Удали данные приложения.",
            UiCopy.Ru.dbOpenFailed
        )
        assertEquals(
            "Could not open the database. Clear app data.",
            UiCopy.En.dbOpenFailed
        )
    }
}
