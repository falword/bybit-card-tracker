package com.sai.cardtrack.data

import com.sai.cardtrack.domain.CardMoney
import com.sai.cardtrack.domain.CategoryMerge
import com.sai.cardtrack.domain.CategoryOrigin
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryTransactionRepository : TransactionRepository {
    private val items = LinkedHashMap<String, Transaction>()
    private val flow = MutableStateFlow<List<Transaction>>(emptyList())

    override suspend fun upsertFromSync(draft: TransactionDraft, syncedAt: Long) {
        val byTxn = items[draft.txnId]
        val byOrder = otherPurchase(draft)
        val existing = keepPurchase(byTxn, byOrder)
        val txnId = existing?.txnId ?: draft.txnId
        val (categoryId, categoryOrigin) = CategoryMerge.resolve(existing, draft)
        items[txnId] = Transaction(
            txnId = txnId,
            orderNo = draft.orderNo,
            kind = draft.kind,
            paidAmount = draft.paidAmount,
            paidCurrency = draft.paidCurrency,
            merchantName = draft.merchantName,
            txnCreate = draft.txnCreate,
            status = draft.status,
            bybitSide = draft.bybitSide,
            categoryId = categoryId,
            syncedAt = syncedAt,
            source = draft.source,
            categoryOrigin = categoryOrigin,
            declinedReason = draft.declinedReason,
            mccCode = draft.mccCode,
            merchCategoryDesc = draft.merchCategoryDesc,
            fees = draft.fees
        )
        if (byTxn != null && byTxn.txnId != txnId) items.remove(byTxn.txnId)
        if (byOrder != null && byOrder.txnId != txnId) items.remove(byOrder.txnId)
        publish()
    }

    override suspend fun setCategory(txnId: String, categoryId: String) {
        val existing = items[txnId] ?: return
        items[txnId] = existing.copy(categoryId = categoryId, categoryOrigin = CategoryOrigin.User)
        publish()
    }

    override fun observeAll(): Flow<List<Transaction>> = flow.asStateFlow()

    override suspend fun get(txnId: String): Transaction? = items[txnId]

    private fun otherPurchase(draft: TransactionDraft): Transaction? {
        if (draft.source != TransactionSource.Purchase) return null
        val orderNo = draft.orderNo?.trim().orEmpty()
        if (orderNo.isEmpty()) return null
        return items.values.firstOrNull { row ->
            row.source == TransactionSource.Purchase &&
                row.orderNo?.trim() == orderNo &&
                row.txnId != draft.txnId
        }
    }

    private fun keepPurchase(byTxn: Transaction?, byOrder: Transaction?): Transaction? {
        if (byTxn != null && byOrder != null) {
            return preferPurchase(byTxn, byOrder)
        }
        return byTxn ?: byOrder
    }

    private fun preferPurchase(left: Transaction, right: Transaction): Transaction {
        if (left.txnCreate != right.txnCreate) {
            return if (left.txnCreate < right.txnCreate) left else right
        }
        if (left.syncedAt != right.syncedAt) {
            return if (left.syncedAt < right.syncedAt) left else right
        }
        return if (left.txnId <= right.txnId) left else right
    }

    override suspend fun isEmpty(): Boolean = items.isEmpty()

    override suspend fun clear() {
        items.clear()
        publish()
    }

    private fun publish() {
        flow.value = items.values
            .filterNot { CardMoney.isExactZero(it.paidAmount) }
            .sortedByDescending { it.txnCreate }
    }
}
