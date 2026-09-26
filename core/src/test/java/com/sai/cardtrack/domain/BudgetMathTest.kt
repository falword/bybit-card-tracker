package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BudgetMathTest {
    private val bangkok: ZoneId = ZoneId.of("Asia/Bangkok")

    private fun purchase(
        id: String,
        amount: String,
        categoryId: String?,
        year: Int,
        month: Int,
        day: Int
    ): Transaction {
        val ms = ZonedDateTime.of(year, month, day, 12, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        return Transaction(
            txnId = id,
            orderNo = id,
            kind = TransactionKind.Expense,
            paidAmount = amount,
            paidCurrency = "USD",
            merchantName = id,
            txnCreate = ms,
            status = TransactionStatus.Success,
            bybitSide = "3",
            categoryId = categoryId,
            syncedAt = 0L,
            source = TransactionSource.Purchase
        )
    }

    private fun refund(
        id: String,
        amount: String,
        year: Int,
        month: Int,
        day: Int
    ): Transaction {
        val ms = ZonedDateTime.of(year, month, day, 12, 0, 0, 0, bangkok)
            .toInstant()
            .toEpochMilli()
        return Transaction(
            txnId = id,
            orderNo = id,
            kind = TransactionKind.Income,
            paidAmount = amount,
            paidCurrency = "USD",
            merchantName = id,
            txnCreate = ms,
            status = TransactionStatus.Success,
            bybitSide = "2",
            categoryId = null,
            syncedAt = 0L,
            source = TransactionSource.Refund
        )
    }

    @Test
    fun `parent budget includes child grocery spend`() {
        val budget = Budget(2026, 9, "food", "20.00")
        val rows = listOf(
            purchase("1", "12.00", "groceries", 2026, 9, 2),
            purchase("2", "5.00", "taxi", 2026, 9, 3)
        )
        val p = BudgetMath.progress(budget, rows, bangkok)
        assertEquals(java.math.BigDecimal("12.00"), p.spent)
        assertEquals(java.math.BigDecimal("0.60"), p.ratio.setScale(2))
    }

    @Test
    fun `month envelope counts all purchases not refunds`() {
        val budget = Budget(2026, 9, null, "10.00")
        val rows = listOf(
            purchase("1", "10.00", "other", 2026, 9, 2),
            refund("2", "3.00", 2026, 9, 3)
        )
        val p = BudgetMath.progress(budget, rows, bangkok)
        assertEquals(java.math.BigDecimal("10.00"), p.spent)
        assertEquals(java.math.BigDecimal("1.00"), p.ratio.setScale(2))
    }
}
