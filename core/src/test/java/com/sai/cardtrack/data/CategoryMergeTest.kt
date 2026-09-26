package com.sai.cardtrack.data

import com.sai.cardtrack.domain.CategoryMerge
import com.sai.cardtrack.domain.CategoryOrigin
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMergeTest {

    @Test
    fun `user origin wins over mcc draft`() {
        val existing = row(categoryId = "dining", origin = CategoryOrigin.User)
        val draft = draft(categoryId = "groceries", origin = CategoryOrigin.Mcc)
        val resolved = CategoryMerge.resolve(existing, draft)
        assertEquals("dining" to CategoryOrigin.User, resolved)
    }

    @Test
    fun `null existing uses draft`() {
        val draft = draft(categoryId = "groceries", origin = CategoryOrigin.Mcc)
        val resolved = CategoryMerge.resolve(null, draft)
        assertEquals("groceries" to CategoryOrigin.Mcc, resolved)
    }

    @Test
    fun `empty existing is filled from mcc draft`() {
        val existing = row(categoryId = null, origin = CategoryOrigin.None)
        val draft = draft(categoryId = "groceries", origin = CategoryOrigin.Mcc)
        val resolved = CategoryMerge.resolve(existing, draft)
        assertEquals("groceries" to CategoryOrigin.Mcc, resolved)
    }

    private fun row(categoryId: String?, origin: CategoryOrigin): Transaction {
        return Transaction(
            txnId = "T",
            orderNo = "O",
            kind = TransactionKind.Expense,
            paidAmount = "1.00",
            paidCurrency = "USD",
            merchantName = "Shop",
            txnCreate = 1L,
            status = TransactionStatus.Success,
            bybitSide = "3",
            categoryId = categoryId,
            syncedAt = 1L,
            categoryOrigin = origin
        )
    }

    private fun draft(categoryId: String?, origin: CategoryOrigin): TransactionDraft {
        return TransactionDraft(
            txnId = "T",
            orderNo = "O",
            kind = TransactionKind.Expense,
            paidAmount = "1.00",
            paidCurrency = "USD",
            merchantName = "Shop",
            txnCreate = 1L,
            status = TransactionStatus.Success,
            bybitSide = "3",
            categoryId = categoryId,
            categoryOrigin = origin
        )
    }
}
