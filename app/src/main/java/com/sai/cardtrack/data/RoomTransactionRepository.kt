package com.sai.cardtrack.data

import com.sai.cardtrack.data.db.TransactionDao
import com.sai.cardtrack.data.db.TransactionEntity
import com.sai.cardtrack.domain.CardMoney
import com.sai.cardtrack.domain.CategoryMerge
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTransactionRepository(
    private val dao: TransactionDao
) : TransactionRepository {
    override suspend fun upsertFromSync(draft: TransactionDraft, syncedAt: Long) {
        val byTxn = dao.get(draft.txnId)?.toDomain()
        val byOrder = otherPurchase(draft)
        val existing = keepPurchase(byTxn, byOrder)
        val txnId = existing?.txnId ?: draft.txnId
        val (categoryId, categoryOrigin) = CategoryMerge.resolve(existing, draft)
        val next = Transaction(
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
        dao.upsertFromSync(TransactionEntity.from(next))
        if (byTxn != null && byTxn.txnId != txnId) dao.delete(byTxn.txnId)
        if (byOrder != null && byOrder.txnId != txnId) dao.delete(byOrder.txnId)
    }

    override suspend fun setCategory(txnId: String, categoryId: String) {
        dao.setCategory(txnId, categoryId)
    }

    override fun observeAll(): Flow<List<Transaction>> {
        return dao.observeAll().map { rows ->
            rows.map { it.toDomain() }.filterNot { CardMoney.isExactZero(it.paidAmount) }
        }
    }

    override suspend fun get(txnId: String): Transaction? {
        return dao.get(txnId)?.toDomain()
    }

    private suspend fun otherPurchase(draft: TransactionDraft): Transaction? {
        if (draft.source != TransactionSource.Purchase) return null
        val orderNo = draft.orderNo?.trim().orEmpty()
        if (orderNo.isEmpty()) return null
        return dao.getOtherPurchaseByOrderNo(orderNo, draft.txnId)?.toDomain()
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

    override suspend fun isEmpty(): Boolean {
        return dao.count() == 0
    }

    override suspend fun clear() {
        dao.clear()
    }
}
