package com.sai.cardtrack.bybit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BybitTlsTest {

    @Test
    fun `pins amazon roots and rsa intermediates for both api hosts not leaves`() {
        val pinner = BybitTls.certificatePinner()
        assertEquals(16, pinner.pins.size)
        assertEquals(8, pinner.findMatchingPins("api.bybit.com").size)
        assertEquals(8, pinner.findMatchingPins("api.bytick.com").size)
        val hashes = pinner.pins.joinToString { it.toString() }
        assertTrue(hashes.contains(BybitTls.AMAZON_ROOT_CA_1))
        assertTrue(hashes.contains(BybitTls.AMAZON_RSA_2048_M01))
        assertFalse(hashes.contains("swOm16kGGgCW5SqgfIfKhbgrV19O5YdI3S1+Jx+WaYc="))
        assertFalse(hashes.contains("pxwcoqcuiat3wc42qtSmo0IBAYkKDju0BXG3XARBwIY="))
    }
}
