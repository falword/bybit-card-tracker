package com.sai.cardtrack.domain

import com.sai.cardtrack.ui.AppLocale
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object CsvExport {
    private const val TABLE_HEADER =
        "txnId,orderNo,date_iso,source,status,merchant,category,paidAmount,paidCurrency,totalFees,transactionAmount,transactionCurrency,mccCode,declinedReason"
    private const val KOINLY_HEADER =
        "Date,Sent Amount,Sent Currency,Received Amount,Received Currency,Fee Amount,Fee Currency,Net Worth Amount,Net Worth Currency,Label,Description,TxHash"

    private val isoUtc: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private val koinlyUtc: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    private val usdLike = setOf("USD", "USDT", "USDC")

    fun table(rows: List<Transaction>, locale: AppLocale = AppLocale.Ru, includeP2p: Boolean = false): String {
        val scoped = P2pVisibility.filter(rows, includeP2p)
        return buildString {
            appendLine(TABLE_HEADER)
            scoped.forEach { appendLine(tableRow(it, locale)) }
        }
    }

    fun koinly(rows: List<Transaction>, includeP2p: Boolean = false): String {
        val scoped = P2pVisibility.filter(rows, includeP2p)
        return buildString {
            appendLine(KOINLY_HEADER)
            scoped.forEach { row ->
                val line = koinlyRow(row) ?: return@forEach
                appendLine(line)
            }
        }
    }

    private fun tableRow(row: Transaction, locale: AppLocale): String {
        val category = ExpenseCategories.labelFor(row.categoryId, locale).orEmpty()
        return csv(
            row.txnId,
            row.orderNo.orEmpty(),
            isoUtc.format(Instant.ofEpochMilli(row.txnCreate)),
            row.source.name,
            row.status.name,
            row.merchantName,
            category,
            row.paidAmount,
            row.paidCurrency,
            row.fees.totalFees,
            row.fees.transactionAmount,
            row.fees.transactionCurrency,
            row.mccCode,
            row.declinedReason
        )
    }

    private fun koinlyRow(row: Transaction): String? {
        if (row.status != TransactionStatus.Success) return null
        if (
            row.source == TransactionSource.Cashback ||
            row.source == TransactionSource.Declined ||
            row.source == TransactionSource.P2PRefund ||
            row.source == TransactionSource.Earn
        ) {
            return null
        }
        val date = koinlyUtc.format(Instant.ofEpochMilli(row.txnCreate))
        val currency = fiatCurrency(row.paidCurrency)
        return when (row.source) {
            TransactionSource.Purchase -> purchaseRow(date, row, currency)
            TransactionSource.Refund -> receivedRow(date, row, currency, "Refund")
            TransactionSource.TopUp -> receivedRow(date, row, currency, "Top-up")
            TransactionSource.P2P -> if (row.kind == TransactionKind.Expense) {
                purchaseRow(date, row, currency)
            } else {
                receivedRow(date, row, currency, "P2P")
            }
            TransactionSource.Cashback, TransactionSource.Declined, TransactionSource.P2PRefund, TransactionSource.Earn -> null
        }
    }

    private fun purchaseRow(date: String, row: Transaction, currency: String): String {
        val fee = feeAmount(row)
        val feeCurrency = if (fee.isNotEmpty()) currency else ""
        return csv(
            date,
            sentAmount(row, fee),
            currency,
            "",
            "",
            fee,
            feeCurrency,
            "",
            "",
            "cost",
            row.merchantName,
            row.txnId
        )
    }

    private fun receivedRow(date: String, row: Transaction, currency: String, description: String): String {
        return csv(
            date,
            "",
            "",
            row.paidAmount,
            currency,
            "",
            "",
            "",
            "",
            "",
            description,
            row.txnId
        )
    }

    private fun feeAmount(row: Transaction): String {
        val parsed = row.fees.totalFees.toBigDecimalOrNull() ?: return ""
        return if (parsed.compareTo(BigDecimal.ZERO) > 0) CardMoney.format(row.fees.totalFees) else ""
    }

    private fun sentAmount(row: Transaction, fee: String): String {
        if (fee.isEmpty()) return row.paidAmount
        val paid = row.paidAmount.toBigDecimalOrNull() ?: return row.paidAmount
        val feeN = fee.toBigDecimalOrNull() ?: return row.paidAmount
        if (feeN.compareTo(BigDecimal.ZERO) <= 0 || feeN >= paid) return row.paidAmount
        return CardMoney.format((paid - feeN).toPlainString())
    }

    private fun fiatCurrency(paid: String): String {
        return if (paid.trim().uppercase() in usdLike) "USD" else paid
    }

    private fun csv(vararg fields: String): String {
        return fields.joinToString(",") { field(it) }
    }

    private fun field(value: String): String {
        val quote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!quote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
