package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TransactionSearchTest {
    private fun row(
        id: String,
        merchant: String = "Amazon",
        amount: String = "10.00",
        categoryId: String? = "groceries",
        source: TransactionSource = TransactionSource.Purchase,
        mcc: String = "5411",
        t: Long = 100L
    ) = Transaction(
        id, id, TransactionKind.Expense, amount, "USD", merchant, t,
        TransactionStatus.Success, "3", categoryId, 0L, source, mccCode = mcc
    )

    @Test
    fun `query matches merchant substring case-insensitive`() {
        val found = TransactionSearch.apply(
            listOf(row("1", "Uber Trip"), row("2", "Amazon")),
            TransactionFilter(query = "uber")
        )
        assertEquals(listOf("1"), found.map { it.txnId })
    }

    @Test
    fun `parent category includes children`() {
        val found = TransactionSearch.apply(
            listOf(row("1", categoryId = "groceries"), row("2", categoryId = "taxi")),
            TransactionFilter(categoryId = "food")
        )
        assertEquals(listOf("1"), found.map { it.txnId })
    }

    @Test
    fun `uncategorized only keeps purchases with no category`() {
        val found = TransactionSearch.apply(
            listOf(
                row("bare", categoryId = null),
                row("food", categoryId = "groceries"),
                row("topup", categoryId = null, source = TransactionSource.TopUp)
            ),
            TransactionFilter(uncategorizedOnly = true)
        )
        assertEquals(listOf("bare"), found.map { it.txnId })
    }

    @Test
    fun `query matches paid amount without trailing zeros`() {
        val found = TransactionSearch.apply(
            listOf(row("1", amount = "650.00"), row("2", amount = "50.00")),
            TransactionFilter(query = "650")
        )
        assertEquals(listOf("1"), found.map { it.txnId })
    }

    @Test
    fun `amount max exclusive-not — inclusive max`() {
        val found = TransactionSearch.apply(
            listOf(row("1", amount = "10.00"), row("2", amount = "20.00")),
            TransactionFilter(amountMax = BigDecimal("10.00"))
        )
        assertEquals(listOf("1"), found.map { it.txnId })
    }

    @Test
    fun `custom range keeps inclusive day bounds`() {
        val found = TransactionSearch.apply(
            listOf(row("before", t = 50L), row("in", t = 100L), row("after", t = 201L)),
            TransactionFilter(beginMs = 100L, endMs = 200L)
        )
        assertEquals(listOf("in"), found.map { it.txnId })
    }
}
