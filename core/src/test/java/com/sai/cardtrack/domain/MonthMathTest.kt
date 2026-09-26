package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class MonthMathTest {

    private val bangkok: ZoneId = ZoneId.of("Asia/Bangkok")

    private fun txn(
        id: String,
        kind: TransactionKind,
        amount: String,
        status: TransactionStatus,
        year: Int,
        month: Int,
        day: Int,
        currency: String = "USDT",
        categoryId: String? = null,
        source: TransactionSource = TransactionSource.Purchase
    ): Transaction {
        val ms = ZonedDateTime.of(year, month, day, 12, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        return Transaction(
            txnId = id,
            orderNo = id,
            kind = kind,
            paidAmount = amount,
            paidCurrency = currency,
            merchantName = id,
            txnCreate = ms,
            status = status,
            bybitSide = "3",
            categoryId = categoryId,
            syncedAt = 0L,
            source = source
        )
    }

    @Test
    fun `pending expense is listed and excluded from month totals`() {
        // given
        val rows = listOf(
            txn("a", TransactionKind.Expense, "10.00", TransactionStatus.Pending, 2026, 9, 2),
            txn("b", TransactionKind.Expense, "5.00", TransactionStatus.Success, 2026, 9, 3)
        )

        // when
        val visible = MonthMath.visible(rows, 2026, 9, bangkok)
        val totals = MonthMath.totals(rows, 2026, 9, bangkok)

        // then
        assertEquals(2, visible.size)
        assertEquals(BigDecimal("5.00"), totals.expenses)
        assertEquals(BigDecimal("0.00"), totals.income)
        assertEquals(BigDecimal("0.00"), totals.cashback)
        assertEquals(BigDecimal("-5.00"), totals.net)
        assertEquals("USD", totals.currency)
    }

    @Test
    fun `qr purchase cancel and retry is one expense`() {
        val rows = listOf(
            txn("qp_1", TransactionKind.Expense, "2.91", TransactionStatus.Success, 2026, 7, 18),
            txn(
                "qr_1",
                TransactionKind.Expense,
                "2.91",
                TransactionStatus.Success,
                2026,
                7,
                18,
                source = TransactionSource.Refund
            ),
            txn("qp_2", TransactionKind.Expense, "2.91", TransactionStatus.Success, 2026, 7, 18)
        )
        val visible = MonthMath.visible(rows, 2026, 7, bangkok)
        val totals = MonthMath.totals(rows, 2026, 7, bangkok)
        assertEquals(2, visible.size)
        assertTrue(visible.none { it.source == TransactionSource.Refund })
        assertEquals(BigDecimal("2.91"), totals.expenses)
    }

    @Test
    fun `purchase minus refund is expense and refund is hidden`() {
        val rows = listOf(
            txn("p", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2),
            txn(
                "r",
                TransactionKind.Expense,
                "3.00",
                TransactionStatus.Success,
                2026,
                9,
                3,
                source = TransactionSource.Refund
            )
        )
        val visible = MonthMath.visible(rows, 2026, 9, bangkok)
        val totals = MonthMath.totals(rows, 2026, 9, bangkok)
        assertEquals(1, visible.size)
        assertEquals("p", visible.single().txnId)
        assertEquals(BigDecimal("7.00"), totals.expenses)
        assertEquals(BigDecimal("0.00"), totals.income)
        assertEquals(BigDecimal("-7.00"), totals.net)
    }

    @Test
    fun `refund without purchases is zero expense and empty list`() {
        val rows = listOf(
            txn(
                "r",
                TransactionKind.Expense,
                "3.00",
                TransactionStatus.Success,
                2026,
                9,
                3,
                source = TransactionSource.Refund
            )
        )
        assertTrue(MonthMath.visible(rows, 2026, 9, bangkok).isEmpty())
        assertEquals(BigDecimal("0.00"), MonthMath.totals(rows, 2026, 9, bangkok).expenses)
    }

    @Test
    fun `top up is income and listed`() {
        val rows = listOf(
            txn(
                "tu",
                TransactionKind.Income,
                "20.00",
                TransactionStatus.Success,
                2026,
                9,
                4,
                source = TransactionSource.TopUp
            )
        )
        val visible = MonthMath.visible(rows, 2026, 9, bangkok)
        val totals = MonthMath.totals(rows, 2026, 9, bangkok)
        assertEquals(1, visible.size)
        assertEquals(BigDecimal("20.00"), totals.income)
        assertEquals(BigDecimal("0.00"), totals.expenses)
        assertEquals(BigDecimal("20.00"), totals.net)
    }

    @Test
    fun `earn yield is header only and stays out of list and net`() {
        val rows = listOf(
            txn("p", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2),
            txn(
                "ey",
                TransactionKind.Income,
                "1.50",
                TransactionStatus.Success,
                2026,
                9,
                2,
                source = TransactionSource.Earn
            )
        )
        val visible = MonthMath.visible(rows, 2026, 9, bangkok)
        val totals = MonthMath.totals(rows, 2026, 9, bangkok)
        assertEquals(1, visible.size)
        assertEquals(BigDecimal("1.50"), totals.earnYield)
        assertEquals(BigDecimal("0.00"), totals.income)
        assertEquals(BigDecimal("-10.00"), totals.net)
    }

    @Test
    fun `sub-cent earn days sum before rounding the month`() {
        val rows = listOf(
            txn("ey1", TransactionKind.Income, "0.0042", TransactionStatus.Success, 2026, 9, 2, source = TransactionSource.Earn),
            txn("ey2", TransactionKind.Income, "0.0042", TransactionStatus.Success, 2026, 9, 3, source = TransactionSource.Earn)
        )
        val totals = MonthMath.totals(rows, 2026, 9, bangkok)
        assertEquals(BigDecimal("0.01"), totals.earnYield)
        assertEquals(BigDecimal("0.00"), totals.income)
    }

    @Test
    fun `earn column hides without checkpoint or when month is older than 90 days`() {
        val now = ZonedDateTime.of(2026, 9, 7, 12, 0, 0, 0, bangkok).toInstant().toEpochMilli()
        assertFalse(MonthMath.showEarnColumn(2026, 9, bangkok, now, null))
        assertTrue(MonthMath.showEarnColumn(2026, 9, bangkok, now, now))
        assertTrue(MonthMath.showEarnColumn(2026, 6, bangkok, now, now))
        assertFalse(MonthMath.showEarnColumn(2026, 5, bangkok, now, now))
    }

    @Test
    fun `cashback points become usd and stay out of list and net`() {
        val rows = listOf(
            txn("p", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2),
            txn(
                "cb",
                TransactionKind.Expense,
                "100",
                TransactionStatus.Success,
                2026,
                9,
                2,
                currency = "PTS",
                source = TransactionSource.Cashback
            )
        )
        val visible = MonthMath.visible(rows, 2026, 9, bangkok)
        val totals = MonthMath.totals(rows, 2026, 9, bangkok)
        assertEquals(1, visible.size)
        assertEquals(BigDecimal("0.20"), totals.cashback)
        assertEquals(BigDecimal("10.00"), totals.expenses)
        assertEquals(BigDecimal("-10.00"), totals.net)
    }

    @Test
    fun `month chevron filters by local calendar month`() {
        val sept = txn("s", TransactionKind.Expense, "1.00", TransactionStatus.Success, 2026, 9, 1)
        val aug = txn("a", TransactionKind.Expense, "9.00", TransactionStatus.Success, 2026, 8, 31)
        assertTrue(MonthMath.inMonth(sept.txnCreate, 2026, 9, bangkok))
        assertFalse(MonthMath.inMonth(aug.txnCreate, 2026, 9, bangkok))
        assertEquals(1, MonthMath.visible(listOf(sept, aug), 2026, 9, bangkok).size)
        assertEquals(BigDecimal("9.00"), MonthMath.totals(listOf(sept, aug), 2026, 8, bangkok).expenses)
    }

    @Test
    fun `mixed currencies pick the most common for the header`() {
        val rows = listOf(
            txn("a", TransactionKind.Expense, "1", TransactionStatus.Success, 2026, 9, 1, "USDT"),
            txn("b", TransactionKind.Expense, "2", TransactionStatus.Success, 2026, 9, 2, "USDT"),
            txn("c", TransactionKind.Expense, "3", TransactionStatus.Success, 2026, 9, 3, "USD")
        )
        val totals = MonthMath.totals(rows, 2026, 9, bangkok)
        assertEquals(BigDecimal("6.00"), totals.expenses)
        assertEquals("USD", totals.currency)
    }

    @Test
    fun `month begin is the first day of the current month`() {
        val now = ZonedDateTime.of(2025, 9, 6, 15, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        val expected = ZonedDateTime.of(2025, 9, 1, 0, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, MonthMath.monthBeginMillis(now, bangkok))
    }

    @Test
    fun `priority month windows are current then previous then older and never after now`() {
        val now = ZonedDateTime.of(2026, 9, 6, 15, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        val historyBegin = ZonedDateTime.of(2025, 9, 1, 0, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        val sept1 = ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        val aug1 = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        val windows = MonthMath.priorityMonthWindows(historyBegin, now, bangkok)
        assertEquals(13, windows.size)
        assertEquals(sept1 to now, windows[0])
        assertEquals(aug1 to sept1, windows[1])
        assertEquals(historyBegin, windows.last().first)
        windows.forEach { (from, to) ->
            assertTrue(from < to)
            assertTrue(to <= now)
            assertTrue(from >= historyBegin)
            val startYm = YearMonth.from(java.time.Instant.ofEpochMilli(from).atZone(bangkok))
            val endYm = YearMonth.from(java.time.Instant.ofEpochMilli(to - 1).atZone(bangkok))
            assertEquals(startYm, endYm)
            assertFalse(startYm.isAfter(YearMonth.of(2026, 9)))
        }
    }

    @Test
    fun `priority begin always includes the previous calendar month`() {
        val now = ZonedDateTime.of(2026, 9, 6, 15, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        val historyBegin = ZonedDateTime.of(2025, 9, 1, 0, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        val aug1 = MonthMath.previousMonthBeginMillis(now, bangkok)
        val since = now - 2L * 60 * 60 * 1000
        assertEquals(aug1, MonthMath.priorityBegin(historyBegin, now, since, bangkok))
        assertEquals(historyBegin, MonthMath.priorityBegin(historyBegin, now, null, bangkok))
        assertEquals(aug1, MonthMath.previousMonthBeginMillis(now, bangkok))
    }

    @Test
    fun `incremental begin is last sync minus one day and never before history start`() {
        val seven = MonthMath.SEVEN_DAYS_MS
        val historyBegin = 1_000L
        val now = historyBegin + seven * 8
        val since = now - 3_600_000L
        assertEquals(since - MonthMath.INCREMENTAL_OVERLAP_MS, MonthMath.incrementalBegin(historyBegin, now, since))
        assertEquals(historyBegin, MonthMath.incrementalBegin(historyBegin, now, null))
        assertEquals(historyBegin, MonthMath.incrementalBegin(historyBegin, now, historyBegin - 1_000L))
    }

    @Test
    fun `fees sum successful purchase totalFees only`() {
        val rows = listOf(
            txn("a", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2)
                .copy(fees = FeeBreakdown(totalFees = "1.50", foreignTransactionFee = "9.00")),
            txn("b", TransactionKind.Expense, "4.00", TransactionStatus.Success, 2026, 9, 3)
                .copy(source = TransactionSource.Refund, fees = FeeBreakdown(totalFees = "0.40")),
            txn("c", TransactionKind.Expense, "8.00", TransactionStatus.Declined, 2026, 9, 4)
                .copy(source = TransactionSource.Declined, fees = FeeBreakdown(totalFees = "2.00"))
        )
        val totals = MonthMath.totals(rows, 2026, 9, bangkok)
        assertEquals(java.math.BigDecimal("6.00"), totals.expenses)
        assertEquals(java.math.BigDecimal("1.50"), totals.fees)
        assertEquals(1, MonthMath.declined(rows, 2026, 9, bangkok).size)
        assertFalse(MonthMath.visible(rows, 2026, 9, bangkok).any { it.source == TransactionSource.Declined })
        assertEquals(java.math.BigDecimal("0.0"), MonthMath.effectiveCashbackPercent(totals))
    }

    @Test
    fun `bounded windows are at most 7 days and cover the range newest first`() {
        val seven = 7L * 24 * 60 * 60 * 1000
        val begin = 1_000L
        val end = begin + seven * 2 + 1_000L
        val windows = MonthMath.boundedWindows(begin, end, seven)
        assertTrue(windows.size >= 3)
        assertEquals(end, windows.first().second)
        assertEquals(begin, windows.last().first)
        windows.forEach { (start, stop) ->
            assertTrue(stop - start <= seven)
            assertTrue(start >= begin)
            assertTrue(stop <= end)
        }
    }

    @Test
    fun `matched cashback is points times 0_002 usd`() {
        val purchase = txn("AIRBNB", TransactionKind.Expense, "650.00", TransactionStatus.Success, 2026, 9, 2)
        val cashback = txn(
            "cb_B1",
            TransactionKind.Expense,
            "100",
            TransactionStatus.Success,
            2026,
            9,
            3,
            currency = "PTS",
            source = TransactionSource.Cashback
        ).copy(orderNo = "AIRBNB")
        assertEquals(BigDecimal("0.20"), MonthMath.cashbackUsdForPurchase(purchase, listOf(purchase, cashback)))
    }

    @Test
    fun `cashback matches purchase orderNo and outOrderId keys`() {
        val purchase = txn("TXN1", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2)
            .copy(orderNo = "ORD9")
        val byTxn = txn(
            "cb_a",
            TransactionKind.Expense,
            "50",
            TransactionStatus.Success,
            2026,
            8,
            1,
            currency = "PTS",
            source = TransactionSource.Cashback
        ).copy(orderNo = "TXN1")
        val byOrder = txn(
            "cb_b",
            TransactionKind.Expense,
            "50",
            TransactionStatus.Success,
            2026,
            8,
            2,
            currency = "PTS",
            source = TransactionSource.Cashback
        ).copy(orderNo = "ORD9\nOUT1")
        assertEquals(
            BigDecimal("0.20"),
            MonthMath.cashbackUsdForPurchase(purchase, listOf(purchase, byTxn, byOrder))
        )
    }

    @Test
    fun `unmatched cashback is not attached to a purchase`() {
        val purchase = txn("P1", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2)
        val cashback = txn(
            "cb_B1",
            TransactionKind.Expense,
            "100",
            TransactionStatus.Success,
            2026,
            9,
            2,
            currency = "PTS",
            source = TransactionSource.Cashback
        ).copy(orderNo = "OTHER")
        assertEquals(null, MonthMath.cashbackUsdForPurchase(purchase, listOf(purchase, cashback)))
        assertEquals(null, MonthMath.cashbackUsdForPurchase(purchase.copy(source = TransactionSource.TopUp), listOf(cashback.copy(orderNo = "P1"))))
    }

    @Test
    fun `p2p cancel is not income when setting off`() {
        val cancel = txn(
            "c", TransactionKind.Expense, "40.00", TransactionStatus.Success, 2026, 9, 2,
            source = TransactionSource.P2PRefund
        )
        val totals = MonthMath.totals(listOf(cancel), 2026, 9, bangkok, includeP2p = false)
        assertEquals(BigDecimal("0.00"), totals.income)
        assertTrue(MonthMath.visible(listOf(cancel), 2026, 9, bangkok, includeP2p = false).isEmpty())
    }

    @Test
    fun `p2p sell plus cancel nets expenses when setting on`() {
        val sell = txn(
            "s", TransactionKind.Expense, "40.00", TransactionStatus.Success, 2026, 9, 2,
            source = TransactionSource.P2P
        )
        val cancel = txn(
            "c", TransactionKind.Expense, "40.00", TransactionStatus.Success, 2026, 9, 3,
            source = TransactionSource.P2PRefund
        )
        val on = MonthMath.totals(listOf(sell, cancel), 2026, 9, bangkok, includeP2p = true)
        assertEquals(BigDecimal("0.00"), on.expenses)
        assertEquals(listOf(sell), MonthMath.visible(listOf(sell, cancel), 2026, 9, bangkok, includeP2p = true))
        val off = MonthMath.totals(listOf(sell, cancel), 2026, 9, bangkok, includeP2p = false)
        assertEquals(BigDecimal("0.00"), off.expenses)
        assertTrue(MonthMath.visible(listOf(sell, cancel), 2026, 9, bangkok, includeP2p = false).isEmpty())
    }

    @Test
    fun `p2p buy is income only when setting on`() {
        val buy = txn(
            "b", TransactionKind.Income, "10.00", TransactionStatus.Success, 2026, 9, 2,
            source = TransactionSource.P2P
        )
        assertEquals(
            BigDecimal("0.00"),
            MonthMath.totals(listOf(buy), 2026, 9, bangkok, includeP2p = false).income
        )
        assertEquals(
            BigDecimal("10.00"),
            MonthMath.totals(listOf(buy), 2026, 9, bangkok, includeP2p = true).income
        )
    }

    @Test
    fun `p2p buy plus income cancel nets income when setting on`() {
        // given
        val buy = txn(
            "b", TransactionKind.Income, "10.00", TransactionStatus.Success, 2026, 9, 2,
            source = TransactionSource.P2P
        )
        val cancel = txn(
            "c", TransactionKind.Income, "10.00", TransactionStatus.Success, 2026, 9, 3,
            source = TransactionSource.P2PRefund
        )

        // when
        val totals = MonthMath.totals(listOf(buy, cancel), 2026, 9, bangkok, includeP2p = true)
        val visible = MonthMath.visible(listOf(buy, cancel), 2026, 9, bangkok, includeP2p = true)

        // then
        assertEquals(BigDecimal("0.00"), totals.income)
        assertEquals(listOf(buy), visible)
    }

    @Test
    fun `currency vote ignores p2p and p2p refund when setting off`() {
        // given
        val purchase = txn(
            "card", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2,
            currency = "USDT"
        )
        val p2p = txn(
            "p2p", TransactionKind.Expense, "99.00", TransactionStatus.Success, 2026, 9, 3,
            currency = "THB",
            source = TransactionSource.P2P
        )
        val refund = txn(
            "p2pr", TransactionKind.Expense, "99.00", TransactionStatus.Success, 2026, 9, 4,
            currency = "THB",
            source = TransactionSource.P2PRefund
        )
        val rows = listOf(purchase, p2p, refund)

        // when
        val off = MonthMath.totals(rows, 2026, 9, bangkok, includeP2p = false)
        val on = MonthMath.totals(rows, 2026, 9, bangkok, includeP2p = true)

        // then
        assertEquals("USD", off.currency)
        assertEquals("THB", on.currency)
    }
}
