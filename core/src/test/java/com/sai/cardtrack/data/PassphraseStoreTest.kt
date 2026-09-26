package com.sai.cardtrack.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.crypto.KeyGenerator

class PassphraseStoreTest {

    @Test
    fun `32-byte passphrase wrap roundtrips with raw SecretKey`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var stored: String? = null
        val store = PassphraseStore(
            secretKey = { key },
            readWrapped = { stored },
            writeWrapped = { stored = it }
        )

        val created = store.getOrCreate()
        assertEquals(32, created.size)
        assertNotNull(stored)
        val wrapped = stored!!
        assertFalse(wrapped.contains(String(created, Charsets.ISO_8859_1)))

        val again = store.getOrCreate()
        assertArrayEquals(created, again)
        assertArrayEquals(created, VaultCrypto.decrypt(key, VaultCrypto.decode(wrapped)))
    }
}
