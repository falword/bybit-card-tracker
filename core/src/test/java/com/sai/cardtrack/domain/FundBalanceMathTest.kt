package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FundBalanceMathTest {

    @Test
    fun `usdt plus usdc is formatted usd`() {
        assertEquals("13.00", FundBalanceMath.displayUsd("12.3", "0.70"))
    }

    @Test
    fun `blank wallets sum to zero`() {
        assertEquals("0.00", FundBalanceMath.displayUsd("", ""))
    }

    @Test
    fun `dust usdt does not hide large funding coin`() {
        assertEquals(
            "80.00",
            FundBalanceMath.displayFromWallets(
                listOf("USDT" to "0.50", "EUR" to "80")
            )
        )
    }

    @Test
    fun `card available is funding plus easy earn`() {
        assertEquals("30.00", FundBalanceMath.cardAvailableUsd("10.00", "20.00"))
    }
}
