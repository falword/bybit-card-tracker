package com.sai.cardtrack.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryCredentialsStoreTest {

    @Test
    fun `funding history generation defaults zero and persists`() = runTest {
        val store = InMemoryCredentialsStore()
        assertEquals(0, store.fundingHistoryGeneration())
        store.setFundingHistoryGeneration(1)
        assertEquals(1, store.fundingHistoryGeneration())
        store.clear()
        assertEquals(0, store.fundingHistoryGeneration())
    }

    @Test
    fun `p2p accounting defaults off and persists`() = runTest {
        val store = InMemoryCredentialsStore()
        assertFalse(store.p2pAccountingEnabled())
        store.setP2pAccountingEnabled(true)
        assertTrue(store.p2pAccountingEnabled())
        store.clear()
        assertFalse(store.p2pAccountingEnabled())
    }
}
