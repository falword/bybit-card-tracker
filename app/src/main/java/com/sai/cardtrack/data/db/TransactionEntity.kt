package com.sai.cardtrack.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sai.cardtrack.domain.CategoryOrigin
import com.sai.cardtrack.domain.FeeBreakdown
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val txnId: String,
    val orderNo: String?,
    val kind: String,
    val paidAmount: String,
    val paidCurrency: String,
    val merchantName: String,
    val txnCreate: Long,
    val status: String,
    val bybitSide: String,
    val categoryId: String?,
    val syncedAt: Long,
    val source: String = "purchase",
    val declinedReason: String = "",
    val mccCode: String = "",
    val merchCategoryDesc: String = "",
    val categoryOrigin: String = "none",
    val totalFees: String = "",
    val foreignTransactionFee: String = "",
    val withdrawalFee: String = "",
    val fxPad: String = "",
    val totalTax: String = "",
    val billAmount: String = "",
    val transactionAmount: String = "",
    val transactionCurrency: String = ""
) {
    fun toDomain(): Transaction {
        return Transaction(
            txnId = txnId,
            orderNo = orderNo,
            kind = if (kind == "income") TransactionKind.Income else TransactionKind.Expense,
            paidAmount = paidAmount,
            paidCurrency = paidCurrency,
            merchantName = merchantName,
            txnCreate = txnCreate,
            status = when (status) {
                "pending" -> TransactionStatus.Pending
                "declined" -> TransactionStatus.Declined
                else -> TransactionStatus.Success
            },
            bybitSide = bybitSide,
            categoryId = categoryId,
            syncedAt = syncedAt,
            source = when (source) {
                "refund" -> TransactionSource.Refund
                "topup" -> TransactionSource.TopUp
                "cashback" -> TransactionSource.Cashback
                "declined" -> TransactionSource.Declined
                "p2p" -> TransactionSource.P2P
                "p2p_refund" -> TransactionSource.P2PRefund
                "earn" -> TransactionSource.Earn
                else -> TransactionSource.Purchase
            },
            categoryOrigin = when (categoryOrigin) {
                "mcc" -> CategoryOrigin.Mcc
                "user" -> CategoryOrigin.User
                else -> CategoryOrigin.None
            },
            declinedReason = declinedReason,
            mccCode = mccCode,
            merchCategoryDesc = merchCategoryDesc,
            fees = FeeBreakdown(
                totalFees = totalFees,
                foreignTransactionFee = foreignTransactionFee,
                withdrawalFee = withdrawalFee,
                fxPad = fxPad,
                totalTax = totalTax,
                billAmount = billAmount,
                transactionAmount = transactionAmount,
                transactionCurrency = transactionCurrency
            )
        )
    }

    companion object {
        fun from(transaction: Transaction): TransactionEntity {
            return TransactionEntity(
                txnId = transaction.txnId,
                orderNo = transaction.orderNo,
                kind = if (transaction.kind == TransactionKind.Income) "income" else "expense",
                paidAmount = transaction.paidAmount,
                paidCurrency = transaction.paidCurrency,
                merchantName = transaction.merchantName,
                txnCreate = transaction.txnCreate,
                status = when (transaction.status) {
                    TransactionStatus.Pending -> "pending"
                    TransactionStatus.Success -> "success"
                    TransactionStatus.Declined -> "declined"
                },
                bybitSide = transaction.bybitSide,
                categoryId = transaction.categoryId,
                syncedAt = transaction.syncedAt,
                source = when (transaction.source) {
                    TransactionSource.Refund -> "refund"
                    TransactionSource.TopUp -> "topup"
                    TransactionSource.Cashback -> "cashback"
                    TransactionSource.Declined -> "declined"
                    TransactionSource.Purchase -> "purchase"
                    TransactionSource.P2P -> "p2p"
                    TransactionSource.P2PRefund -> "p2p_refund"
                    TransactionSource.Earn -> "earn"
                },
                declinedReason = transaction.declinedReason,
                mccCode = transaction.mccCode,
                merchCategoryDesc = transaction.merchCategoryDesc,
                categoryOrigin = when (transaction.categoryOrigin) {
                    CategoryOrigin.Mcc -> "mcc"
                    CategoryOrigin.User -> "user"
                    CategoryOrigin.None -> "none"
                },
                totalFees = transaction.fees.totalFees,
                foreignTransactionFee = transaction.fees.foreignTransactionFee,
                withdrawalFee = transaction.fees.withdrawalFee,
                fxPad = transaction.fees.fxPad,
                totalTax = transaction.fees.totalTax,
                billAmount = transaction.fees.billAmount,
                transactionAmount = transaction.fees.transactionAmount,
                transactionCurrency = transaction.fees.transactionCurrency
            )
        }
    }
}
