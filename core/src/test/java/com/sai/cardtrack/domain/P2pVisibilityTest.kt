package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class P2pVisibilityTest {

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
    fun `setting off drops p2p and always drops p2p refund`() {
        val buy = txn("b", TransactionKind.Income, "10.00", TransactionStatus.Success, 2026, 9, 2, source = TransactionSource.P2P)
        val cancel = txn("c", TransactionKind.Expense, "10.00", TransactionStatus.Success, 2026, 9, 2, source = TransactionSource.P2PRefund)
        val purchase = txn("p", TransactionKind.Expense, "1.00", TransactionStatus.Success, 2026, 9, 2)
        assertEquals(listOf(purchase), P2pVisibility.filter(listOf(buy, cancel, purchase), includeP2p = false))
        assertEquals(listOf(buy, purchase), P2pVisibility.filter(listOf(buy, cancel, purchase), includeP2p = true))
    }
}
