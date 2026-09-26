package com.sai.cardtrack.ui.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockAuthenticatorsTest {

    @Test
    fun `API 28 uses deprecated device credential allowed`() {
        assertTrue(lockUsesDeviceCredentialAllowed(28))
    }

    @Test
    fun `API 29 uses deprecated device credential allowed`() {
        assertTrue(lockUsesDeviceCredentialAllowed(29))
    }

    @Test
    fun `API 30 uses allowed authenticators not device credential allowed`() {
        assertFalse(lockUsesDeviceCredentialAllowed(30))
    }
}
