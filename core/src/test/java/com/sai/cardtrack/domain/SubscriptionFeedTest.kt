package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SubscriptionFeedTest {

    private val zone: ZoneId = ZoneId.of("Asia/Bangkok")
    private val september: YearMonth = YearMonth.of(2026, 9)
    private val october: YearMonth = YearMonth.of(2026, 10)

    private fun purchase(
        id: String,
        merchant: String,
        amount: String,
        day: Int,
        categoryId: String? = null
    ): Transaction {
        val ms = LocalDate.of(2026, 9, 1)
            .plusDays((day - 1).toLong())
            .atTime(12, 0)
            .atZone(zone)
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
            status = TransactionStatus.Success,
            bybitSide = "3",
            categoryId = categoryId,
            syncedAt = 0L,
            source = TransactionSource.Purchase
        )
    }

    @Test
    fun `streaming charge is listed when it is not a monthly series`() {
        val rows = SubscriptionFeed.list(
            listOf(purchase("1", "Spotify", "12.50", day = 4, categoryId = "streaming")),
            september,
            zone
        )

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("charge:1", row.key)
        assertEquals("Spotify", row.merchant)
        assertEquals("12.50", row.amount)
        assertFalse(row.recurring)
    }

    @Test
    fun `subscriptions parent and media are listed`() {
        val rows = SubscriptionFeed.list(
            listOf(
                purchase("1", "Patreon", "5.00", day = 2, categoryId = "subscriptions"),
                purchase("2", "Kindle", "3.00", day = 8, categoryId = "media")
            ),
            september,
            zone
        )

        assertEquals(listOf("Kindle", "Patreon"), rows.map { it.merchant })
        assertTrue(rows.all { !it.recurring })
    }

    @Test
    fun `monthly merchant stays one row when it is also classified as software`() {
        val rows = SubscriptionFeed.list(
            listOf(
                purchase("1", "Adobe", "9.99", day = 1, categoryId = "software"),
                purchase("2", "Adobe", "9.99", day = 31, categoryId = "software")
            ),
            october,
            zone
        )

        assertEquals(1, rows.size)
        assertFalse(rows.single().recurring)
        assertEquals("Adobe", rows.single().merchant)
        assertEquals("9.99", rows.single().amount)
    }

    @Test
    fun `food purchase is not added`() {
        val rows = SubscriptionFeed.list(
            listOf(purchase("1", "Cafe", "4.00", day = 3, categoryId = "food")),
            september,
            zone
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `september keeps the classified charge when the next repeat is in october`() {
        val rows = SubscriptionFeed.list(
            listOf(
                purchase("1", "Adobe", "9.99", day = 1, categoryId = "software"),
                purchase("2", "Adobe", "9.99", day = 31, categoryId = "software"),
                purchase("3", "Spotify Music", "5.47", day = 17, categoryId = "media")
            ),
            september,
            zone
        )

        assertEquals(listOf("Spotify Music", "Adobe"), rows.map { it.merchant })
        assertTrue(rows.all { !it.recurring })
    }

    @Test
    fun `charge from another month is hidden`() {
        val rows = SubscriptionFeed.list(
            listOf(purchase("1", "Spotify", "12.50", day = 4, categoryId = "streaming")),
            august(),
            zone
        )

        assertTrue(rows.isEmpty())
    }

    private fun august(): YearMonth = YearMonth.of(2026, 8)

    @Test
    fun `month spend is share of all successful purchases in the month`() {
        val rows = listOf(
            purchase("sub", "Spotify", "12.50", day = 4, categoryId = "streaming"),
            purchase("food", "Cafe", "87.50", day = 5, categoryId = "food")
        )

        val spend = SubscriptionFeed.monthSpend(rows, september, zone)

        assertEquals(BigDecimal("12.50"), spend.total)
        assertEquals(BigDecimal("100.00"), spend.monthExpenses)
        assertEquals("USD", spend.currency)
        assertEquals(BigDecimal("0.1250"), spend.shareOfMonth())
    }
}
