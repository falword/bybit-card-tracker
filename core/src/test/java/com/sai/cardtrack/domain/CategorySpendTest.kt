package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class CategorySpendTest {

    private val bangkok: ZoneId = ZoneId.of("Asia/Bangkok")
    private val sept: YearMonth = YearMonth.of(2026, 9)

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
        source: TransactionSource = TransactionSource.Purchase,
        orderNo: String = id
    ): Transaction {
        val ms = ZonedDateTime.of(year, month, day, 12, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        return Transaction(
            txnId = id,
            orderNo = orderNo,
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
    fun `food and groceries stay two rows`() {
        // given
        val rows = listOf(
            txn("a", TransactionKind.Expense, "30.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "food"),
            txn("b", TransactionKind.Expense, "70.00", TransactionStatus.Success, 2026, 9, 3, categoryId = "groceries")
        )

        // when
        val breakdown = CategorySpend.monthBreakdown(rows, sept, bangkok)

        // then
        assertEquals(2, breakdown.rows.size)
        assertEquals("groceries", breakdown.rows[0].categoryId)
        assertEquals("Еда · Продукты", breakdown.rows[0].label)
        assertEquals(BigDecimal("70.00"), breakdown.rows[0].amount)
        assertEquals(BigDecimal("0.7000"), breakdown.rows[0].share)
        assertEquals("food", breakdown.rows[1].categoryId)
        assertEquals("Еда", breakdown.rows[1].label)
        assertEquals(BigDecimal("30.00"), breakdown.rows[1].amount)
        assertEquals(BigDecimal("0.3000"), breakdown.rows[1].share)
        assertEquals(BigDecimal("100.00"), breakdown.total)
    }

    @Test
    fun `null category becomes без категории`() {
        val rows = listOf(
            txn("a", TransactionKind.Expense, "12.00", TransactionStatus.Success, 2026, 9, 2)
        )
        val breakdown = CategorySpend.monthBreakdown(rows, sept, bangkok)
        assertEquals(1, breakdown.rows.size)
        assertEquals(null, breakdown.rows.single().categoryId)
        assertEquals("без категории", breakdown.rows.single().label)
    }

    @Test
    fun `pending and income are excluded from breakdown`() {
        val rows = listOf(
            txn("p", TransactionKind.Expense, "40.00", TransactionStatus.Pending, 2026, 9, 2, categoryId = "food"),
            txn(
                "i",
                TransactionKind.Income,
                "15.00",
                TransactionStatus.Success,
                2026,
                9,
                3,
                source = TransactionSource.TopUp
            ),
            txn("e", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 4, categoryId = "food")
        )
        val breakdown = CategorySpend.monthBreakdown(rows, sept, bangkok)
        assertEquals(BigDecimal("10.00"), breakdown.total)
        assertEquals(1, breakdown.rows.size)
        assertEquals(BigDecimal("10.00"), breakdown.rows.single().amount)
    }

    @Test
    fun `zero expenses yield an empty breakdown`() {
        val rows = listOf(
            txn("p", TransactionKind.Expense, "40.00", TransactionStatus.Pending, 2026, 9, 2, categoryId = "food")
        )
        val breakdown = CategorySpend.monthBreakdown(rows, sept, bangkok)
        assertEquals(BigDecimal("0.00"), breakdown.total)
        assertTrue(breakdown.rows.isEmpty())
        assertEquals("", breakdown.currency)
    }

    @Test
    fun `history skips empty months and scales bar to the category max`() {
        val rows = listOf(
            txn("a", TransactionKind.Expense, "200.00", TransactionStatus.Success, 2026, 7, 4, categoryId = "taxi"),
            txn("b", TransactionKind.Expense, "50.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "taxi"),
            txn("c", TransactionKind.Expense, "9.00", TransactionStatus.Success, 2026, 8, 10, categoryId = "food")
        )
        val history = CategorySpend.history(rows, "taxi", bangkok)
        assertEquals("Транспорт · Такси", history.label)
        assertEquals(2, history.rows.size)
        assertEquals(YearMonth.of(2026, 9), history.rows[0].yearMonth)
        assertEquals(BigDecimal("50.00"), history.rows[0].amount)
        assertEquals(BigDecimal("0.2500"), history.rows[0].share)
        assertEquals(YearMonth.of(2026, 7), history.rows[1].yearMonth)
        assertEquals(BigDecimal("200.00"), history.rows[1].amount)
        assertEquals(BigDecimal("1.0000"), history.rows[1].share)
    }

    @Test
    fun `compare window ends on the selected month and sorts by window total`() {
        val rows = listOf(
            txn("a", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 6, 2, categoryId = "food"),
            txn("b", TransactionKind.Expense, "20.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "food"),
            txn("c", TransactionKind.Expense, "5.00", TransactionStatus.Success, 2026, 9, 3, categoryId = "taxi"),
            txn("old", TransactionKind.Expense, "99.00", TransactionStatus.Success, 2026, 3, 1, categoryId = "food")
        )
        val table = CategorySpend.compare(rows, sept, bangkok)
        assertEquals(
            (0..3).map { sept.minusMonths(it.toLong()) },
            table.months
        )
        assertEquals(YearMonth.of(2026, 9), table.months.first())
        assertEquals(YearMonth.of(2026, 6), table.months.last())
        assertEquals(listOf("food", "taxi"), table.rows.map { it.categoryId })
        assertEquals(BigDecimal("30.00"), table.rows[0].windowTotal)
        assertEquals(null, table.rows[0].amounts[1])
        assertEquals(BigDecimal("20.00"), table.rows[0].amounts[0])
        assertEquals(BigDecimal("10.00"), table.rows[0].amounts[3])
        assertEquals(BigDecimal("1.0000"), table.rows[0].share)
        assertEquals(BigDecimal("0.1667"), table.rows[1].share)
    }

    @Test
    fun `refunds and cashback are excluded from category spend`() {
        val rows = listOf(
            txn("e", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "food"),
            txn(
                "r",
                TransactionKind.Expense,
                "4.00",
                TransactionStatus.Success,
                2026,
                9,
                3,
                source = TransactionSource.Refund,
                orderNo = "e"
            ),
            txn(
                "c",
                TransactionKind.Expense,
                "800",
                TransactionStatus.Success,
                2026,
                9,
                4,
                currency = "PTS",
                source = TransactionSource.Cashback
            )
        )
        val breakdown = CategorySpend.monthBreakdown(rows, sept, bangkok)
        assertEquals(BigDecimal("6.00"), breakdown.total)
        assertEquals(1, breakdown.rows.size)
        assertEquals("food", breakdown.rows.single().categoryId)
        assertEquals(BigDecimal("6.00"), breakdown.rows.single().amount)
    }

    @Test
    fun `header currency uses the most common paid currency as USD`() {
        val rows = listOf(
            txn("a", TransactionKind.Expense, "1", TransactionStatus.Success, 2026, 9, 1, "USDT", "food"),
            txn("b", TransactionKind.Expense, "2", TransactionStatus.Success, 2026, 9, 2, "USDT", "food"),
            txn("c", TransactionKind.Expense, "3", TransactionStatus.Success, 2026, 9, 3, "USD", "taxi")
        )
        val breakdown = CategorySpend.monthBreakdown(rows, sept, bangkok)
        assertEquals("USD", breakdown.currency)
        assertEquals(BigDecimal("6.00"), breakdown.total)
    }

    @Test
    fun `parent grain folds child categories into the parent`() {
        val rows = listOf(
            txn("food-buy", TransactionKind.Expense, "30.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "food"),
            txn("groceries-buy", TransactionKind.Expense, "70.00", TransactionStatus.Success, 2026, 9, 3, categoryId = "groceries"),
            txn("taxi-buy", TransactionKind.Expense, "5.00", TransactionStatus.Success, 2026, 9, 4, categoryId = "taxi")
        )

        val breakdown = CategorySpend.monthBreakdown(rows, sept, bangkok, grain = CategoryGrain.Parent)

        assertEquals(listOf("food", "transport"), breakdown.rows.map { it.categoryId })
        assertEquals("Еда", breakdown.rows[0].label)
        assertEquals(BigDecimal("100.00"), breakdown.rows[0].amount)
        assertEquals(BigDecimal("0.9524"), breakdown.rows[0].share)
        assertEquals("Транспорт", breakdown.rows[1].label)
        assertEquals(BigDecimal("5.00"), breakdown.rows[1].amount)
        assertEquals(BigDecimal("105.00"), breakdown.total)
    }

    @Test
    fun `parent grain compare folds children into one row`() {
        val rows = listOf(
            txn("food-buy", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "food"),
            txn("groceries-buy", TransactionKind.Expense, "20.00", TransactionStatus.Success, 2026, 9, 3, categoryId = "groceries")
        )

        val table = CategorySpend.compare(rows, sept, bangkok, grain = CategoryGrain.Parent)

        assertEquals(listOf("food"), table.rows.map { it.categoryId })
        assertEquals("Еда", table.rows.single().label)
        assertEquals(BigDecimal("30.00"), table.rows.single().windowTotal)
    }

    @Test
    fun `opened parent lists child purchases and refunds newest first`() {
        val rows = listOf(
            txn("food-buy", TransactionKind.Expense, "30.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "food"),
            txn(
                "groceries-buy",
                TransactionKind.Expense,
                "70.00",
                TransactionStatus.Success,
                2026,
                9,
                3,
                categoryId = "groceries",
                orderNo = "groceries-order"
            ),
            txn(
                "groceries-refund",
                TransactionKind.Expense,
                "10.00",
                TransactionStatus.Success,
                2026,
                9,
                4,
                source = TransactionSource.Refund,
                orderNo = "groceries-order"
            ),
            txn("taxi-buy", TransactionKind.Expense, "5.00", TransactionStatus.Success, 2026, 9, 5, categoryId = "taxi"),
            txn("old-groceries", TransactionKind.Expense, "8.00", TransactionStatus.Success, 2026, 8, 2, categoryId = "groceries")
        )

        val opened = CategorySpend.expenses(rows, "food", bangkok, sept, sept, CategoryGrain.Parent)

        assertEquals(listOf("groceries-refund", "groceries-buy", "food-buy"), opened.map { it.txnId })
        assertTrue(opened[0].refund)
        assertEquals(BigDecimal("10.00"), opened[0].amount)
        assertFalse(opened[1].refund)
        assertEquals("groceries-buy", opened[1].merchantName)
    }

    @Test
    fun `opened assigned category lists only that category`() {
        val rows = listOf(
            txn("food-buy", TransactionKind.Expense, "30.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "food"),
            txn("groceries-buy", TransactionKind.Expense, "70.00", TransactionStatus.Success, 2026, 9, 3, categoryId = "groceries")
        )

        val opened = CategorySpend.expenses(rows, "groceries", bangkok, sept, sept, CategoryGrain.Assigned)

        assertEquals(listOf("groceries-buy"), opened.map { it.txnId })
    }

    @Test
    fun `opened category keeps purchases inside the selected months`() {
        val rows = listOf(
            txn("aug", TransactionKind.Expense, "4.00", TransactionStatus.Success, 2026, 8, 2, categoryId = "food"),
            txn("sep", TransactionKind.Expense, "6.00", TransactionStatus.Success, 2026, 9, 2, categoryId = "food"),
            txn("oct", TransactionKind.Expense, "8.00", TransactionStatus.Success, 2026, 10, 2, categoryId = "food")
        )

        val month = CategorySpend.expenses(rows, "food", bangkok, sept, sept)
        val window = CategorySpend.expenses(rows, "food", bangkok, YearMonth.of(2026, 6), sept)

        assertEquals(listOf("sep"), month.map { it.txnId })
        assertEquals(listOf("sep", "aug"), window.map { it.txnId })
    }
}
