package com.sai.cardtrack.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId

data class Budget(
    val year: Int,
    val month: Int,
    val categoryId: String?,
    val limitAmount: String
)

data class BudgetProgress(
    val budget: Budget,
    val spent: BigDecimal,
    val ratio: BigDecimal
)

object BudgetMath {
    fun progress(budget: Budget, transactions: List<Transaction>, zone: ZoneId): BudgetProgress {
        val spent = transactions
            .filter { MonthMath.inMonth(it.txnCreate, budget.year, budget.month, zone) }
            .filter { it.source == TransactionSource.Purchase }
            .filter { it.status == TransactionStatus.Success }
            .filter { categoryMatches(it.categoryId, budget.categoryId) }
            .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
            .setScale(2, RoundingMode.HALF_UP)
        val limit = parseAmount(budget.limitAmount)
        val ratio = if (limit <= BigDecimal.ZERO) {
            BigDecimal.ZERO.setScale(2)
        } else {
            spent.divide(limit, 8, RoundingMode.HALF_UP)
        }
        return BudgetProgress(budget, spent, ratio)
    }

    private fun categoryMatches(txnCategoryId: String?, budgetCategoryId: String?): Boolean {
        if (budgetCategoryId == null) return true
        if (txnCategoryId == budgetCategoryId) return true
        return ExpenseCategories.childrenOf(budgetCategoryId).any { it.id == txnCategoryId }
    }

    private fun parseAmount(raw: String): BigDecimal {
        return raw.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO.setScale(2)
    }
}
