package com.sai.cardtrack.data

import com.sai.cardtrack.data.db.TransactionDao
import com.sai.cardtrack.data.db.TransactionEntity
import com.sai.cardtrack.domain.CategoryOrigin
import com.sai.cardtrack.domain.FeeBreakdown
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomTransactionRepositoryTest {

    private fun draft(
        amount: String = "10.00",
        status: TransactionStatus = TransactionStatus.Pending
    ): TransactionDraft {
        return TransactionDraft(
            txnId = "TXN1",
            orderNo = "ORD1",
            kind = TransactionKind.Expense,
            paidAmount = amount,
            paidCurrency = "USDT",
            merchantName = "Amazon",
            txnCreate = 100L,
            status = status,
            bybitSide = "3"
        )
    }

    @Test
    fun `room upsert sql path does not replace categoryId`() = runTest {
        val dao = FakeTransactionDao()
        val repo = RoomTransactionRepository(dao)
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.setCategory("TXN1", "food")
        repo.upsertFromSync(draft(amount = "10.50", status = TransactionStatus.Success), syncedAt = 3L)
        val row = repo.get("TXN1")
        assertEquals("food", row?.categoryId)
        assertEquals(TransactionStatus.Success, row?.status)
        assertEquals("10.50", row?.paidAmount)
        assertEquals(3L, row?.syncedAt)
    }

    @Test
    fun `user category survives mcc draft on second sync`() = runTest {
        val dao = FakeTransactionDao()
        val repo = RoomTransactionRepository(dao)
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
    fun `financial purchase with new txnId updates auth row of same orderNo`() = runTest {
        val dao = FakeTransactionDao()
        val repo = RoomTransactionRepository(dao)
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.upsertFromSync(
            draft(amount = "12.00", status = TransactionStatus.Success).copy(txnId = "FIN1"),
            syncedAt = 2L
        )
        assertEquals(TransactionStatus.Success, repo.get("TXN1")?.status)
        assertEquals("12.00", repo.get("TXN1")?.paidAmount)
        assertEquals(null, repo.get("FIN1"))
    }

    @Test
    fun `existing auth and financial rows with the same orderNo collapse`() = runTest {
        val dao = FakeTransactionDao()
        val repo = RoomTransactionRepository(dao)
        repo.upsertFromSync(draft(), syncedAt = 1L)
        dao.insertIgnore(
            com.sai.cardtrack.data.db.TransactionEntity.from(
                repo.get("TXN1")!!.copy(txnId = "FIN1", paidAmount = "12.00", syncedAt = 2L)
            )
        )
        repo.upsertFromSync(
            draft(amount = "12.00", status = TransactionStatus.Success).copy(txnId = "FIN1"),
            syncedAt = 3L
        )
        assertEquals("12.00", repo.get("TXN1")?.paidAmount)
        assertEquals(TransactionStatus.Success, repo.get("TXN1")?.status)
        assertEquals(null, repo.get("FIN1"))
    }

    @Test
    fun `empty category is filled from mcc draft`() = runTest {
        val dao = FakeTransactionDao()
        val repo = RoomTransactionRepository(dao)
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.upsertFromSync(
            draft().copy(categoryId = "groceries", categoryOrigin = CategoryOrigin.Mcc),
            syncedAt = 2L
        )
        assertEquals("groceries", repo.get("TXN1")?.categoryId)
        assertEquals(CategoryOrigin.Mcc, repo.get("TXN1")?.categoryOrigin)
    }

    @Test
    fun `dao updateFromSync keeps stored user category`() = runTest {
        val dao = FakeTransactionDao()
        val repo = RoomTransactionRepository(dao)
        repo.upsertFromSync(draft(), syncedAt = 1L)
        repo.setCategory("TXN1", "dining")
        val before = dao.get("TXN1")!!
        dao.updateFromSync(
            txnId = before.txnId,
            orderNo = before.orderNo,
            kind = before.kind,
            paidAmount = "11.00",
            paidCurrency = before.paidCurrency,
            merchantName = before.merchantName,
            txnCreate = before.txnCreate,
            status = before.status,
            bybitSide = before.bybitSide,
            syncedAt = 9L,
            source = before.source,
            declinedReason = before.declinedReason,
            mccCode = before.mccCode,
            merchCategoryDesc = before.merchCategoryDesc,
            categoryId = "groceries",
            categoryOrigin = "mcc",
            totalFees = before.totalFees,
            foreignTransactionFee = before.foreignTransactionFee,
            withdrawalFee = before.withdrawalFee,
            fxPad = before.fxPad,
            totalTax = before.totalTax,
            billAmount = before.billAmount,
            transactionAmount = before.transactionAmount,
            transactionCurrency = before.transactionCurrency
        )
        val row = repo.get("TXN1")
        assertEquals("dining", row?.categoryId)
        assertEquals(CategoryOrigin.User, row?.categoryOrigin)
        assertEquals("11.00", row?.paidAmount)
        assertEquals(9L, row?.syncedAt)
    }

    @Test
    fun `zero amount is hidden from the transaction list`() = runTest {
        val repo = RoomTransactionRepository(FakeTransactionDao())
        repo.upsertFromSync(draft(amount = "0.00").copy(txnId = "ZERO", orderNo = "Z"), syncedAt = 1L)
        repo.upsertFromSync(draft(amount = "4.20").copy(txnId = "REAL", orderNo = "R"), syncedAt = 1L)
        assertEquals(listOf("REAL"), repo.observeAll().first().map { it.txnId })
    }

    private class FakeTransactionDao : TransactionDao {
        private val items = LinkedHashMap<String, TransactionEntity>()
        private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

        override fun observeAll(): Flow<List<TransactionEntity>> = flow

        override suspend fun get(id: String): TransactionEntity? = items[id]

        override suspend fun getOtherPurchaseByOrderNo(
            orderNo: String,
            excludeTxnId: String
        ): TransactionEntity? {
            return items.values.firstOrNull {
                it.orderNo == orderNo && it.source == "purchase" && it.txnId != excludeTxnId
            }
        }

        override suspend fun delete(txnId: String) {
            items.remove(txnId)
            publish()
        }

        override suspend fun count(): Int = items.size

        override suspend fun updateFromSync(
            txnId: String,
            orderNo: String?,
            kind: String,
            paidAmount: String,
            paidCurrency: String,
            merchantName: String,
            txnCreate: Long,
            status: String,
            bybitSide: String,
            syncedAt: Long,
            source: String,
            declinedReason: String,
            mccCode: String,
            merchCategoryDesc: String,
            categoryId: String?,
            categoryOrigin: String,
            totalFees: String,
            foreignTransactionFee: String,
            withdrawalFee: String,
            fxPad: String,
            totalTax: String,
            billAmount: String,
            transactionAmount: String,
            transactionCurrency: String
        ): Int {
            val existing = items[txnId] ?: return 0
            items[txnId] = existing.copy(
                orderNo = orderNo,
                kind = kind,
                paidAmount = paidAmount,
                paidCurrency = paidCurrency,
                merchantName = merchantName,
                txnCreate = txnCreate,
                status = status,
                bybitSide = bybitSide,
                syncedAt = syncedAt,
                source = source,
                declinedReason = declinedReason,
                mccCode = mccCode,
                merchCategoryDesc = merchCategoryDesc,
                categoryId = if (existing.categoryOrigin == "user") existing.categoryId else categoryId,
                categoryOrigin = if (existing.categoryOrigin == "user") existing.categoryOrigin else categoryOrigin,
                totalFees = totalFees,
                foreignTransactionFee = foreignTransactionFee,
                withdrawalFee = withdrawalFee,
                fxPad = fxPad,
                totalTax = totalTax,
                billAmount = billAmount,
                transactionAmount = transactionAmount,
                transactionCurrency = transactionCurrency
            )
            publish()
            return 1
        }

        override suspend fun insertIgnore(entity: TransactionEntity) {
            if (items.containsKey(entity.txnId)) return
            items[entity.txnId] = entity
            publish()
        }

        override suspend fun setCategory(txnId: String, categoryId: String) {
            val existing = items[txnId] ?: return
            items[txnId] = existing.copy(categoryId = categoryId, categoryOrigin = "user")
            publish()
        }

        override suspend fun clear() {
            items.clear()
            publish()
        }

        private fun publish() {
            flow.value = items.values.sortedByDescending { it.txnCreate }
        }
    }
}
