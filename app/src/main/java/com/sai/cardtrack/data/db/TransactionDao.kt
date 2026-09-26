package com.sai.cardtrack.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY txnCreate DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE txnId = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE orderNo = :orderNo AND source = 'purchase' AND txnId != :excludeTxnId LIMIT 1"
    )
    suspend fun getOtherPurchaseByOrderNo(orderNo: String, excludeTxnId: String): TransactionEntity?

    @Query("DELETE FROM transactions WHERE txnId = :txnId")
    suspend fun delete(txnId: String)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query(
        """
        UPDATE transactions SET
            orderNo = :orderNo,
            kind = :kind,
            paidAmount = :paidAmount,
            paidCurrency = :paidCurrency,
            merchantName = :merchantName,
            txnCreate = :txnCreate,
            status = :status,
            bybitSide = :bybitSide,
            syncedAt = :syncedAt,
            source = :source,
            declinedReason = :declinedReason,
            mccCode = :mccCode,
            merchCategoryDesc = :merchCategoryDesc,
            categoryId = CASE WHEN categoryOrigin = 'user' THEN categoryId ELSE :categoryId END,
            categoryOrigin = CASE WHEN categoryOrigin = 'user' THEN categoryOrigin ELSE :categoryOrigin END,
            totalFees = :totalFees,
            foreignTransactionFee = :foreignTransactionFee,
            withdrawalFee = :withdrawalFee,
            fxPad = :fxPad,
            totalTax = :totalTax,
            billAmount = :billAmount,
            transactionAmount = :transactionAmount,
            transactionCurrency = :transactionCurrency
        WHERE txnId = :txnId
        """
    )
    suspend fun updateFromSync(
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
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TransactionEntity)

    @Query("UPDATE transactions SET categoryId = :categoryId, categoryOrigin = 'user' WHERE txnId = :txnId")
    suspend fun setCategory(txnId: String, categoryId: String)

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Transaction
    suspend fun upsertFromSync(entity: TransactionEntity) {
        val changed = updateFromSync(
            txnId = entity.txnId,
            orderNo = entity.orderNo,
            kind = entity.kind,
            paidAmount = entity.paidAmount,
            paidCurrency = entity.paidCurrency,
            merchantName = entity.merchantName,
            txnCreate = entity.txnCreate,
            status = entity.status,
            bybitSide = entity.bybitSide,
            syncedAt = entity.syncedAt,
            source = entity.source,
            declinedReason = entity.declinedReason,
            mccCode = entity.mccCode,
            merchCategoryDesc = entity.merchCategoryDesc,
            categoryId = entity.categoryId,
            categoryOrigin = entity.categoryOrigin,
            totalFees = entity.totalFees,
            foreignTransactionFee = entity.foreignTransactionFee,
            withdrawalFee = entity.withdrawalFee,
            fxPad = entity.fxPad,
            totalTax = entity.totalTax,
            billAmount = entity.billAmount,
            transactionAmount = entity.transactionAmount,
            transactionCurrency = entity.transactionCurrency
        )
        if (changed == 0) {
            insertIgnore(entity)
        }
    }
}
