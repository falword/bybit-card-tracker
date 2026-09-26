package com.sai.cardtrack.ui.home

import com.sai.cardtrack.ui.components.LedgerAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthSummaryLayoutTest {

    @Test
    fun `breakdown starts at expenses and keeps cashback note off earn`() {
        val lines = monthSummaryLines(showEarn = true, cashbackNote = "GOLD · 10 / 500 USD")
        assertEquals(
            listOf(
                MonthSummaryLineId.Expenses,
                MonthSummaryLineId.Income,
                MonthSummaryLineId.Cashback,
                MonthSummaryLineId.Fees,
                MonthSummaryLineId.Earn
            ),
            lines.map { it.id }
        )
        assertEquals("GOLD · 10 / 500 USD", lines.single { it.id == MonthSummaryLineId.Cashback }.note)
        assertEquals(null, lines.single { it.id == MonthSummaryLineId.Earn }.note)
        assertFalse(lines.single { it.id == MonthSummaryLineId.Cashback }.note.orEmpty().contains('%'))
    }

    @Test
    fun `reward subtitle stays one line and does not add a percent`() {
        val note = LedgerAmount.note(listOf("GOLD\n10 / 500 USD"))
        assertEquals("GOLD · 10 / 500 USD", note)
        assertFalse(note.orEmpty().contains('%'))
    }

    @Test
    fun `earn without a cashback note has no subtitle on any row`() {
        val lines = monthSummaryLines(showEarn = true, cashbackNote = null)
        assertTrue(lines.all { it.note == null })
        assertTrue(lines.any { it.id == MonthSummaryLineId.Earn })
    }

    @Test
    fun `month total is not a breakdown peer`() {
        val lines = monthSummaryLines(showEarn = false, cashbackNote = "GOLD")
        assertEquals(
            listOf(
                MonthSummaryLineId.Expenses,
                MonthSummaryLineId.Income,
                MonthSummaryLineId.Cashback,
                MonthSummaryLineId.Fees
            ),
            lines.map { it.id }
        )
        assertEquals("GOLD", lines.single { it.id == MonthSummaryLineId.Cashback }.note)
    }
}
