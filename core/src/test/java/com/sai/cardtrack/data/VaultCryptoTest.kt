package com.sai.cardtrack.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VaultCryptoTest {

    @Test
    fun `roundtrip utf8 secret`() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val blob = VaultCrypto.encrypt(key, "super-secret-value".toByteArray())
        assertEquals("super-secret-value", String(VaultCrypto.decrypt(key, blob)))
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("super-secret-value"))
        val text = VaultCrypto.encode(blob)
        assertFalse(text.contains("super-secret-value"))
        assertEquals("super-secret-value", String(VaultCrypto.decrypt(key, VaultCrypto.decode(text))))
    }

    @Test
    fun `credentials clear leaves get null`() = runTest {
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        store.setLastSyncAt(1L)
        store.setFundingSyncedThrough(2L)
        store.setCardSyncedThrough(3L)
        store.setFinancialSyncedThrough(4L)
        store.setCardHistoryGeneration(2)
        store.setEarnSyncedThrough(5L)
        store.setFundingHistoryGeneration(1)
        store.clear()
        assertNull(store.get())
        assertNull(store.lastSyncAt())
        assertNull(store.fundingSyncedThrough())
        assertNull(store.cardSyncedThrough())
        assertNull(store.financialSyncedThrough())
        assertEquals(0, store.cardHistoryGeneration())
        assertNull(store.earnSyncedThrough())
        assertEquals(0, store.fundingHistoryGeneration())
        assertNull(store.apiKeyHint())
    }

    @Test
    fun `api key hint is last four`() = runTest {
        val store = InMemoryCredentialsStore()
        store.save(Credentials("stored-api-key-1234", "stored-hmac-secret"))
        assertEquals("1234", store.apiKeyHint())
    }
}
