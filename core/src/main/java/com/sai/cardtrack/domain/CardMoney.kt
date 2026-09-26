package com.sai.cardtrack.domain

import java.math.BigDecimal
import java.math.RoundingMode

object CardMoney {
    private val usdLike = setOf("USD", "USDT", "USDC")

    fun fromRecord(record: BybitAssetRecord): CardMoneyAmount {
        val basic = pickUsd(record.basicAmount, record.basicCurrency)
        if (basic != null) return basic
        val txn = pickUsd(record.transactionAmount, record.transactionCurrency)
        if (txn != null) return txn
        val paid = pickUsd(record.paidAmount, record.paidCurrency)
        if (paid != null) return paid
        val paidCur = record.paidCurrency.trim().uppercase()
        if (paidCur !in usdLike && hasNumber(record.billAmount)) {
            val paidN = record.paidAmount.toBigDecimalOrNull()
            val billN = record.billAmount.toBigDecimalOrNull()
            if (paidN == null || billN == null || paidN.compareTo(billN) != 0) {
                return CardMoneyAmount(format(record.billAmount), "USD")
            }
        }
        if (hasNumber(record.paidFiat) && !isZero(record.paidFiat)) {
            return CardMoneyAmount(format(record.paidFiat), "USD")
        }
        val fallback = record.paidCurrency.trim().ifEmpty { "USD" }
        val label = if (fallback.uppercase() in usdLike) "USD" else fallback
        return CardMoneyAmount(format(record.paidAmount), label)
    }

    fun isExactZero(raw: String): Boolean {
        val amount = raw.trim().toBigDecimalOrNull() ?: return false
        return amount.compareTo(BigDecimal.ZERO) == 0
    }

    fun format(raw: String): String {
        val n = raw.toBigDecimalOrNull() ?: return raw
        return n.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    fun formatWhole(raw: String): String {
        val n = raw.toBigDecimalOrNull() ?: return raw
        return formatWhole(n)
    }

    fun formatWhole(amount: BigDecimal): String {
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString()
    }

    fun displayCurrency(unit: String): String {
        val raw = unit.trim()
        if (raw.isEmpty() || raw == "1" || raw.uppercase() in usdLike) {
            return "USD"
        }
        return raw
    }

    private fun pickUsd(amount: String, currency: String): CardMoneyAmount? {
        if (!hasNumber(amount)) return null
        if (currency.trim().uppercase() !in usdLike) return null
        return CardMoneyAmount(format(amount), "USD")
    }

    private fun hasNumber(raw: String): Boolean {
        return raw.trim().isNotEmpty() && raw.toBigDecimalOrNull() != null
    }

    private fun isZero(raw: String): Boolean {
        val n = raw.toBigDecimalOrNull() ?: return true
        return n.compareTo(BigDecimal.ZERO) == 0
    }
}
