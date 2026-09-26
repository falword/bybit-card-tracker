package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurringDetectorTest {
    private val bangkok: ZoneId = ZoneId.of("Asia/Bangkok")

    private fun purchase(
        merchant: String,
        amount: String,
        day: Int,
        source: TransactionSource = TransactionSource.Purchase,
        status: TransactionStatus = TransactionStatus.Success,
        id: String = "$merchant-$day"
    ): Transaction {
        val ms = LocalDate.of(2026, 9, 1)
            .plusDays((day - 1).toLong())
            .atTime(12, 0)
            .atZone(bangkok)
            .toInstant()
            .toEpochMilli()
        return Transaction(
            txnId = id,
            orderNo = id,
            kind = TransactionKind.Expense,
            paidAmount = amount,
            paidCurrency = "USD",
            merchantName = merchant,
            txnCreate = ms,
            status = status,
            bybitSide = "3",
            categoryId = null,
            syncedAt = 0L,
            source = source
        )
    }

    @Test
    fun `two uber charges 30 days apart become one series`() {
        val a = purchase("Uber", "9.99", day = 1)
        val b = purchase("Uber", "9.99", day = 31)
        val series = RecurringDetector.detect(listOf(a, b))
        assertEquals(1, series.size)
        assertEquals("9.99", series[0].typicalAmount)
        assertEquals(30, series[0].intervalDays)
    }

    @Test
    fun `ten day gap is not recurring`() {
        val a = purchase("Uber", "9.99", day = 1)
        val b = purchase("Uber", "9.99", day = 11)
        assertEquals(emptyList<RecurringSeries>(), RecurringDetector.detect(listOf(a, b)))
    }

    @Test
    fun `declined or pending purchase is not a series`() {
        val a = purchase("Uber", "9.99", day = 1, status = TransactionStatus.Declined)
        val b = purchase("Uber", "9.99", day = 31, status = TransactionStatus.Pending)
        assertEquals(emptyList<RecurringSeries>(), RecurringDetector.detect(listOf(a, b)))
    }

    @Test
    fun `merchant key collapses case and whitespace`() {
        val a = purchase("  UBER  ", "9.99", day = 1)
        val b = purchase("uber", "9.99", day = 31)
        val series = RecurringDetector.detect(listOf(a, b))
        assertEquals(1, series.size)
        assertEquals("uber", series[0].merchantKey)
    }

    @Test
    fun `amount more than ten percent off median is not recurring`() {
        val a = purchase("Netflix", "10.00", day = 1)
        val b = purchase("Netflix", "13.00", day = 31)
        assertEquals(emptyList<RecurringSeries>(), RecurringDetector.detect(listOf(a, b)))
    }

    @Test
    fun `series are sorted by next expected time`() {
        val later = listOf(
            purchase("Spotify", "5.00", day = 5, id = "s1"),
            purchase("Spotify", "5.00", day = 35, id = "s2")
        )
        val sooner = listOf(
            purchase("Uber", "9.99", day = 1, id = "u1"),
            purchase("Uber", "9.99", day = 31, id = "u2")
        )
        val series = RecurringDetector.detect(sooner + later)
        assertEquals(listOf("uber", "spotify"), series.map { it.merchantKey })
    }
}
