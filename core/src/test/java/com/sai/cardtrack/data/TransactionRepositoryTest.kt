package com.sai.cardtrack.data

import com.sai.cardtrack.domain.CategoryOrigin
import com.sai.cardtrack.domain.FeeBreakdown
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionRepositoryTest {

    private fun draft(
        id: String = "TXN1",
        status: TransactionStatus = TransactionStatus.Pending,
        amount: String = "10.00",
        kind: TransactionKind = TransactionKind.Expense
    ): TransactionDraft {
        return TransactionDraft(
            txnId = id,
            orderNo = "ORD1",
            kind = kind,
            paidAmount = amount,
            paidCurrency = "USDT",
            merchantName = "Amazon",
            txnCreate = 100L,
            status = status,
            bybitSide = "3"
        )
    }

    @Test
    fun `duplicate txnId does not insert a second row`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.upsertFromSync(draft(amount = "11.00"), syncedAt = 2L)
        val all = repo.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("11.00", all[0].paidAmount)
        assertEquals(2L, all[0].syncedAt)
    }

    @Test
    fun `assigned category survives a second sync`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.setCategory("TXN1", "food")
        repo.upsertFromSync(draft(status = TransactionStatus.Success, amount = "10.50"), syncedAt = 3L)
        val row = repo.get("TXN1")
        assertEquals("food", row?.categoryId)
        assertEquals(TransactionStatus.Success, row?.status)
        assertEquals("10.50", row?.paidAmount)
    }

    @Test
    fun `success with the same txnId updates status`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(draft(status = TransactionStatus.Pending), syncedAt = 1L)
        repo.upsertFromSync(draft(status = TransactionStatus.Success), syncedAt = 2L)
        assertEquals(TransactionStatus.Success, repo.get("TXN1")?.status)
    }

    @Test
    fun `unknown category target is ignored`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.setCategory("MISSING", "food")
        assertNull(repo.get("MISSING"))
    }

    @Test
    fun `user category survives mcc draft on second sync`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.setCategory("TXN1", "dining")
        repo.upsertFromSync(
            draft().copy(
                categoryId = "groceries",
                categoryOrigin = CategoryOrigin.Mcc,
                fees = FeeBreakdown(totalFees = "1.50")
            ),
            syncedAt = 2L
        )
        val row = repo.get("TXN1")
        assertEquals("dining", row?.categoryId)
        assertEquals(CategoryOrigin.User, row?.categoryOrigin)
        assertEquals("1.50", row?.fees?.totalFees)
    }

    @Test
    fun `empty category is filled from mcc draft`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.upsertFromSync(
            draft().copy(categoryId = "groceries", categoryOrigin = CategoryOrigin.Mcc),
            syncedAt = 2L
        )
        assertEquals("groceries", repo.get("TXN1")?.categoryId)
        assertEquals(CategoryOrigin.Mcc, repo.get("TXN1")?.categoryOrigin)
    }

    @Test
    fun `zero amount is hidden from the transaction list`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(draft(id = "ZERO", amount = "0.00").copy(orderNo = "Z"), syncedAt = 1L)
        repo.upsertFromSync(draft(id = "REAL", amount = "4.20").copy(orderNo = "R"), syncedAt = 1L)
        assertEquals(listOf("REAL"), repo.observeAll().first().map { it.txnId })
    }

    @Test
    fun `clear leaves observeAll empty`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.clear()
        assertEquals(0, repo.observeAll().first().size)
        assertNull(repo.get("TXN1"))
    }
}
