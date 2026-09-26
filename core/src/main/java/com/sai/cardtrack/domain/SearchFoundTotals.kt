package com.sai.cardtrack.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class SearchFoundTotal(
    val amount: BigDecimal,
    val currency: String,
    val count: Int
)

object SearchFoundTotals {
    fun of(rows: List<Transaction>): SearchFoundTotal {
        if (rows.isEmpty()) {
            return SearchFoundTotal(BigDecimal.ZERO.setScale(2), "", 0)
        }
        var signed = BigDecimal.ZERO
        val currencies = mutableMapOf<String, Int>()
        var counted = 0
        for (row in rows) {
            if (row.status != TransactionStatus.Success) continue
            if (
                row.source == TransactionSource.Cashback ||
                row.source == TransactionSource.Declined ||
                row.source == TransactionSource.P2PRefund ||
                row.source == TransactionSource.Earn
            ) {
                continue
            }
            val amount = row.paidAmount.toBigDecimalOrNull() ?: continue
            signed = when (row.source) {
                TransactionSource.Purchase -> signed + amount
                TransactionSource.Refund, TransactionSource.TopUp -> signed - amount
                TransactionSource.P2P -> if (row.kind == TransactionKind.Expense) {
                    signed + amount
                } else {
                    signed - amount
                }
                TransactionSource.Cashback, TransactionSource.Declined, TransactionSource.P2PRefund, TransactionSource.Earn -> continue
            }
            counted += 1
            val currency = CardMoney.displayCurrency(row.paidCurrency)
            if (currency.isNotEmpty()) {
                currencies[currency] = (currencies[currency] ?: 0) + 1
            }
        }
        return SearchFoundTotal(
            amount = signed.setScale(2, RoundingMode.HALF_UP),
            currency = currencies.maxByOrNull { it.value }?.key.orEmpty(),
            count = counted
        )
    }
}
