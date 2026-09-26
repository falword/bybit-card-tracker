package com.sai.cardtrack.domain

import java.math.BigDecimal

data class TransactionFilter(
    val query: String = "",
    val categoryId: String? = null,
    val uncategorizedOnly: Boolean = false,
    val source: TransactionSource? = null,
    val status: TransactionStatus? = null,
    val amountMin: BigDecimal? = null,
    val amountMax: BigDecimal? = null,
    val beginMs: Long? = null,
    val endMs: Long? = null
)

object TransactionSearch {
    fun apply(rows: List<Transaction>, filter: TransactionFilter): List<Transaction> {
        return rows.filter { matches(it, filter) }.sortedByDescending { it.txnCreate }
    }

    private fun matches(row: Transaction, filter: TransactionFilter): Boolean {
        if (!queryMatches(row, filter.query)) return false
        if (!uncategorizedMatches(row, filter.uncategorizedOnly)) return false
        if (!categoryMatches(row.categoryId, filter.categoryId)) return false
        if (filter.source != null && row.source != filter.source) return false
        if (filter.status != null && row.status != filter.status) return false
        if (!amountMatches(row.paidAmount, filter.amountMin, filter.amountMax)) return false
        if (filter.beginMs != null && row.txnCreate < filter.beginMs) return false
        if (filter.endMs != null && row.txnCreate > filter.endMs) return false
        return true
    }

    private fun queryMatches(row: Transaction, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return containsIgnoreCase(row.merchantName, needle) ||
            containsIgnoreCase(row.mccCode, needle) ||
            containsIgnoreCase(row.merchCategoryDesc, needle) ||
            containsIgnoreCase(row.txnId, needle) ||
            amountQueryMatches(row.paidAmount, needle)
    }

    private fun containsIgnoreCase(haystack: String, needle: String): Boolean {
        return haystack.contains(needle, ignoreCase = true)
    }

    private fun uncategorizedMatches(row: Transaction, uncategorizedOnly: Boolean): Boolean {
        if (!uncategorizedOnly) return true
        return row.source == TransactionSource.Purchase && row.categoryId == null
    }

    private fun categoryMatches(categoryId: String?, filterId: String?): Boolean {
        if (filterId == null) return true
        if (categoryId == filterId) return true
        return ExpenseCategories.childrenOf(filterId).any { it.id == categoryId }
    }

    private fun amountQueryMatches(paidAmount: String, needle: String): Boolean {
        if (containsIgnoreCase(paidAmount, needle)) return true
        val query = needle.toBigDecimalOrNull() ?: return false
        val amount = paidAmount.toBigDecimalOrNull() ?: return false
        return query.compareTo(amount) == 0
    }

    private fun amountMatches(paidAmount: String, min: BigDecimal?, max: BigDecimal?): Boolean {
        if (min == null && max == null) return true
        val amount = paidAmount.toBigDecimalOrNull() ?: return false
        if (min != null && amount < min) return false
        if (max != null && amount > max) return false
        return true
    }
}
