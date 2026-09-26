package com.sai.cardtrack.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class LedgerAmountTest {
    @Test
    fun `expense with currency uses minus prefix`() {
        assertEquals("−12.50 USD", LedgerAmount.signed("12.50", "USD", income = false))
    }

    @Test
    fun `income without currency uses plus prefix only`() {
        assertEquals("+8.00", LedgerAmount.signed("8.00", "", income = true))
    }

    @Test
    fun `negative month net keeps minus and currency`() {
        assertEquals("−40.25 EUR", LedgerAmount.monthNet(BigDecimal("-40.25"), "EUR"))
    }

    @Test
    fun `positive month net has no plus and keeps currency`() {
        assertEquals("15 USD", LedgerAmount.monthNet(BigDecimal("15"), "USD"))
    }

    @Test
    fun `zero month net without currency is plain zero`() {
        assertEquals("0", LedgerAmount.monthNet(BigDecimal.ZERO, ""))
    }

    @Test
    fun `breakdown expense keeps minus and currency`() {
        assertEquals("−12.50 USD", LedgerAmount.breakdown(BigDecimal("12.50"), "USD", inflow = false))
    }

    @Test
    fun `breakdown income keeps plus and currency`() {
        assertEquals("+8.00 USD", LedgerAmount.breakdown(BigDecimal("8.00"), "USD", inflow = true))
    }

    @Test
    fun `breakdown zero is unsigned even for an outflow`() {
        assertEquals("0.00 USD", LedgerAmount.breakdown(BigDecimal("0.00"), "USD", inflow = false))
    }

    @Test
    fun `breakdown without currency omits the suffix`() {
        assertEquals("+1.50", LedgerAmount.breakdown(BigDecimal("1.50"), "", inflow = true))
    }

    @Test
    fun `note joins percent and multiline reward on one line`() {
        assertEquals(
            "2.0% · GOLD · 10 / 500 USD",
            LedgerAmount.note(listOf("2.0%", "GOLD\n10 / 500 USD"))
        )
    }

    @Test
    fun `note drops blank parts`() {
        assertEquals(null, LedgerAmount.note(listOf(null, "", "  \n")))
        assertEquals("GOLD", LedgerAmount.note(listOf(null, "GOLD", "")))
    }
}
