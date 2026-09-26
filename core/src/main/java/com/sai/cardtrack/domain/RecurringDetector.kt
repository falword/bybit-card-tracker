package com.sai.cardtrack.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

data class RecurringSeries(
    val merchantKey: String,
    val merchantName: String,
    val typicalAmount: String,
    val intervalDays: Int,
    val nextExpectedMs: Long
)

object RecurringDetector {
    fun detect(transactions: List<Transaction>): List<RecurringSeries> {
        val eligible = transactions.filter {
            it.source == TransactionSource.Purchase && it.status == TransactionStatus.Success
        }
        return eligible
            .groupBy { merchantKey(it.merchantName) }
            .mapNotNull { (key, group) -> seriesFor(key, group) }
            .sortedBy { it.nextExpectedMs }
    }

    private fun seriesFor(merchantKey: String, group: List<Transaction>): RecurringSeries? {
        if (group.size < 2) return null
        val ordered = group.sortedBy { it.txnCreate }
        val gaps = ordered.zipWithNext { a, b -> b.txnCreate - a.txnCreate }
        val medianGapMs = medianLong(gaps)
        val intervalDays = TimeUnit.MILLISECONDS.toDays(medianGapMs).toInt()
        if (intervalDays !in 25..35) return null
        val amounts = ordered.map { it.paidAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO }
        val medianAmount = medianDecimal(amounts)
        val allowed = medianAmount.abs().multiply(BigDecimal("0.10"))
        val amountsOk = amounts.all { amount ->
            amount.subtract(medianAmount).abs().compareTo(allowed) <= 0
        }
        if (!amountsOk) return null
        val last = ordered.last()
        return RecurringSeries(
            merchantKey = merchantKey,
            merchantName = last.merchantName,
            typicalAmount = CardMoney.format(medianAmount.toPlainString()),
            intervalDays = intervalDays,
            nextExpectedMs = last.txnCreate + medianGapMs
        )
    }

    fun merchantKey(name: String): String {
        return name.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    private fun medianLong(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2
        }
    }

    private fun medianDecimal(values: List<BigDecimal>): BigDecimal {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            sorted[mid - 1].add(sorted[mid]).divide(BigDecimal("2"), 8, RoundingMode.HALF_UP)
        }
    }
}
