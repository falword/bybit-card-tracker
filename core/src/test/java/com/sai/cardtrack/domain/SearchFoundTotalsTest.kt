package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SearchFoundTotalsTest {
    private fun row(
        id: String,
        amount: String,
        kind: TransactionKind = TransactionKind.Expense,
        currency: String = "USDT",
        source: TransactionSource = TransactionSource.Purchase
    ) = Transaction(
        id, id, kind, amount, currency, "Shop", 100L,
        TransactionStatus.Success, "3", null, 0L, source
    )

    @Test
    fun `expenses add and income subtracts among found rows`() {
        val total = SearchFoundTotals.of(
            listOf(
                row("p1", "10.00"),
                row("p2", "20.50"),
                row("r", "5.00", source = TransactionSource.Refund)
            )
        )
        assertEquals(BigDecimal("25.50"), total.amount)
        assertEquals("USD", total.currency)
        assertEquals(3, total.count)
    }

    @Test
    fun `cashback points are not in the money total or currency`() {
        val total = SearchFoundTotals.of(
            listOf(
                row("p1", "10.00"),
                row("cb1", "100", currency = "PTS", source = TransactionSource.Cashback),
                row("cb2", "50", currency = "PTS", source = TransactionSource.Cashback)
            )
        )
        assertEquals(BigDecimal("10.00"), total.amount)
        assertEquals("USD", total.currency)
        assertEquals(1, total.count)
    }

    @Test
    fun `pending and declined rows are not in the found total`() {
        val total = SearchFoundTotals.of(
            listOf(
                row("p1", "10.00"),
                row("hold", "8.00").copy(status = TransactionStatus.Pending),
                row("deny", "3.00", source = TransactionSource.Declined)
                    .copy(status = TransactionStatus.Declined)
            )
        )
        assertEquals(BigDecimal("10.00"), total.amount)
        assertEquals(1, total.count)
    }

    @Test
    fun `empty found list is zero`() {
        val total = SearchFoundTotals.of(emptyList())
        assertEquals(BigDecimal("0.00"), total.amount)
        assertEquals("", total.currency)
        assertEquals(0, total.count)
    }

    @Test
    fun `p2p expense plus income is signed and refund is ignored`() {
        val p2pOnly = SearchFoundTotals.of(
            listOf(
                row("p2p-e", "10", kind = TransactionKind.Expense, source = TransactionSource.P2P),
                row("p2p-i", "4", kind = TransactionKind.Income, source = TransactionSource.P2P)
            )
        )
        val withRefund = SearchFoundTotals.of(
            listOf(
                row("p2p-e", "10", kind = TransactionKind.Expense, source = TransactionSource.P2P),
                row("p2p-i", "4", kind = TransactionKind.Income, source = TransactionSource.P2P),
                row("p2p-r", "10", kind = TransactionKind.Expense, source = TransactionSource.P2PRefund)
            )
        )
        assertEquals(BigDecimal("6.00"), p2pOnly.amount)
        assertEquals(BigDecimal("6.00"), withRefund.amount)
    }
}
