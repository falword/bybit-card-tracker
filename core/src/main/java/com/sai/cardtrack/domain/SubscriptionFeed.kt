package com.sai.cardtrack.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneId

data class SubscriptionMonthSpend(
    val total: BigDecimal,
    val monthExpenses: BigDecimal,
    val currency: String
) {
    fun shareOfMonth(): BigDecimal {
        if (monthExpenses.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4)
        }
        return total.divide(monthExpenses, 4, RoundingMode.HALF_UP)
    }
}

data class SubscriptionEntry(
    val key: String,
    val merchant: String,
    val amount: String,
    val atMs: Long,
    val recurring: Boolean
)

object SubscriptionFeed {
    fun monthSpend(
        transactions: List<Transaction>,
        month: YearMonth,
        zone: ZoneId
    ): SubscriptionMonthSpend {
        val total = transactions
            .filter { txn ->
                txn.source == TransactionSource.Purchase &&
                    txn.status == TransactionStatus.Success &&
                    ExpenseCategories.isSubscriptionsFamily(txn.categoryId) &&
                    inMonth(txn.txnCreate, month, zone)
            }
            .fold(BigDecimal.ZERO.setScale(2)) { acc, txn ->
                acc + parseAmount(txn.paidAmount)
            }
        val monthTotals = MonthMath.totals(transactions, month.year, month.monthValue, zone)
        return SubscriptionMonthSpend(
            total = total,
            monthExpenses = monthTotals.expenses,
            currency = monthTotals.currency
        )
    }

    fun list(transactions: List<Transaction>, month: YearMonth, zone: ZoneId): List<SubscriptionEntry> {
        val charges = transactions
            .filter { txn ->
                txn.source == TransactionSource.Purchase &&
                    txn.status == TransactionStatus.Success &&
                    ExpenseCategories.isSubscriptionsFamily(txn.categoryId)
            }
            .sortedByDescending { it.txnCreate }
            .map { txn ->
                SubscriptionEntry(
                    key = "charge:" + txn.txnId,
                    merchant = txn.merchantName,
                    amount = CardMoney.format(txn.paidAmount),
                    atMs = txn.txnCreate,
                    recurring = false
                )
            }
        val chargedThisMonth = charges
            .filter { inMonth(it.atMs, month, zone) }
            .map { RecurringDetector.merchantKey(it.merchant) }
            .toSet()
        val recurring = RecurringDetector.detect(transactions)
            .filter { it.merchantKey !in chargedThisMonth }
            .map { row ->
                SubscriptionEntry(
                    key = row.merchantKey,
                    merchant = row.merchantName,
                    amount = row.typicalAmount,
                    atMs = row.nextExpectedMs,
                    recurring = true
                )
            }
        return (recurring + charges).filter { inMonth(it.atMs, month, zone) }
    }

    private fun inMonth(atMs: Long, month: YearMonth, zone: ZoneId): Boolean {
        return MonthMath.inMonth(atMs, month.year, month.monthValue, zone)
    }

    private fun parseAmount(raw: String): BigDecimal {
        return raw.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    }
}
