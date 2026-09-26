package com.sai.cardtrack.domain

import com.sai.cardtrack.ui.AppLocale
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class CategoryShareRow(
    val categoryId: String?,
    val label: String,
    val amount: BigDecimal,
    val share: BigDecimal
)

data class CategoryMonthBreakdown(
    val total: BigDecimal,
    val currency: String,
    val rows: List<CategoryShareRow>
)

data class CategoryHistoryMonth(
    val yearMonth: YearMonth,
    val amount: BigDecimal,
    val share: BigDecimal
)

data class CategoryHistory(
    val categoryId: String?,
    val label: String,
    val currency: String,
    val rows: List<CategoryHistoryMonth>
)

data class CategoryCompareRow(
    val categoryId: String?,
    val label: String,
    val windowTotal: BigDecimal,
    val share: BigDecimal,
    val amounts: List<BigDecimal?>
)

data class CategoryCompareTable(
    val months: List<YearMonth>,
    val currency: String,
    val rows: List<CategoryCompareRow>
)

enum class CategoryGrain { Assigned, Parent }

data class CategoryExpense(
    val txnId: String,
    val merchantName: String,
    val amount: BigDecimal,
    val refund: Boolean,
    val txnCreate: Long
)

object CategorySpend {
    private const val WINDOW_MONTHS: Long = 4

    fun monthBreakdown(
        transactions: List<Transaction>,
        month: YearMonth,
        zone: ZoneId,
        locale: AppLocale = AppLocale.Ru,
        grain: CategoryGrain = CategoryGrain.Assigned
    ): CategoryMonthBreakdown {
        val success = successfulPurchases(transactions).filter {
            MonthMath.inMonth(it.txnCreate, month.year, month.monthValue, zone)
        }
        val refunds = successfulRefunds(transactions).filter {
            MonthMath.inMonth(it.txnCreate, month.year, month.monthValue, zone)
        }
        val grouped = netAmounts(success, refunds, successfulPurchases(transactions), grain)
        val purchaseTotal = success.fold(BigDecimal.ZERO.setScale(2)) { acc, txn ->
            acc + parseAmount(txn.paidAmount)
        }
        val refundTotal = refunds.fold(BigDecimal.ZERO.setScale(2)) { acc, txn ->
            acc + parseAmount(txn.paidAmount)
        }
        val total = (purchaseTotal - refundTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        val rows = grouped
            .filter { it.value > BigDecimal.ZERO }
            .map { (categoryId, amount) ->
                CategoryShareRow(
                    categoryId = categoryId,
                    label = labelOf(categoryId, locale),
                    amount = amount,
                    share = shareOf(amount, total)
                )
            }
            .sortedByDescending { it.amount }
        return CategoryMonthBreakdown(
            total = total,
            currency = headerCurrency(success),
            rows = rows
        )
    }

    fun history(
        transactions: List<Transaction>,
        categoryId: String?,
        zone: ZoneId,
        locale: AppLocale = AppLocale.Ru
    ): CategoryHistory {
        val success = netCategoryRows(transactions, categoryId)
        val byMonth = linkedMapOf<YearMonth, BigDecimal>()
        success.forEach { txn ->
            val ym = YearMonth.from(Instant.ofEpochMilli(txn.txnCreate).atZone(zone))
            val signed = signedAmount(txn)
            byMonth[ym] = (byMonth[ym] ?: BigDecimal.ZERO.setScale(2)) + signed
        }
        val max = byMonth.values.maxOrNull() ?: BigDecimal.ZERO
        val rows = byMonth
            .filter { it.value > BigDecimal.ZERO }
            .map { (ym, amount) ->
                CategoryHistoryMonth(
                    yearMonth = ym,
                    amount = amount,
                    share = shareOf(amount, max)
                )
            }
            .sortedByDescending { it.yearMonth }
        return CategoryHistory(
            categoryId = categoryId,
            label = labelOf(categoryId, locale),
            currency = headerCurrency(success),
            rows = rows
        )
    }

    fun compare(
        transactions: List<Transaction>,
        endMonth: YearMonth,
        zone: ZoneId,
        locale: AppLocale = AppLocale.Ru,
        grain: CategoryGrain = CategoryGrain.Assigned
    ): CategoryCompareTable {
        val oldest = endMonth.minusMonths(WINDOW_MONTHS - 1)
        val months = (0 until WINDOW_MONTHS.toInt()).map { offset ->
            endMonth.minusMonths(offset.toLong())
        }
        val purchases = successfulPurchases(transactions)
        val success = purchases + successfulRefunds(transactions)
        val inWindow = success.filter { txn ->
            val ym = YearMonth.from(Instant.ofEpochMilli(txn.txnCreate).atZone(zone))
            !ym.isBefore(oldest) && !ym.isAfter(endMonth)
        }
        val keys = inWindow.map { bucket(categoryOf(it, purchases), grain) }.distinct()
        val rows = keys.map { categoryId ->
            val amounts = months.map { month ->
                val sum = inWindow
                    .filter {
                        bucket(categoryOf(it, purchases), grain) == categoryId &&
                            MonthMath.inMonth(it.txnCreate, month.year, month.monthValue, zone)
                    }
                    .fold(BigDecimal.ZERO.setScale(2)) { acc, txn -> acc + signedAmount(txn) }
                if (sum.compareTo(BigDecimal.ZERO) == 0) null else sum
            }
            val windowTotal = amounts.fold(BigDecimal.ZERO.setScale(2)) { acc, amount ->
                acc + (amount ?: BigDecimal.ZERO.setScale(2))
            }
            CategoryCompareRow(
                categoryId = categoryId,
                label = labelOf(categoryId, locale),
                windowTotal = windowTotal,
                share = BigDecimal.ZERO,
                amounts = amounts
            )
        }
            .filter { it.windowTotal > BigDecimal.ZERO }
            .sortedByDescending { it.windowTotal }
        val maxTotal = rows.maxOfOrNull { it.windowTotal } ?: BigDecimal.ZERO
        return CategoryCompareTable(
            months = months,
            currency = headerCurrency(inWindow.filter { it.source == TransactionSource.Purchase }),
            rows = rows.map { it.copy(share = shareOf(it.windowTotal, maxTotal)) }
        )
    }

    fun compareStart(endMonth: YearMonth): YearMonth {
        return endMonth.minusMonths(WINDOW_MONTHS - 1)
    }

    fun expenses(
        transactions: List<Transaction>,
        categoryId: String?,
        zone: ZoneId,
        from: YearMonth,
        to: YearMonth,
        grain: CategoryGrain = CategoryGrain.Assigned
    ): List<CategoryExpense> {
        val purchases = successfulPurchases(transactions)
        return (purchases + successfulRefunds(transactions))
            .filter { txn ->
                bucket(categoryOf(txn, purchases), grain) == categoryId && inRange(txn.txnCreate, from, to, zone)
            }
            .sortedByDescending { it.txnCreate }
            .map { txn ->
                CategoryExpense(
                    txnId = txn.txnId,
                    merchantName = txn.merchantName,
                    amount = parseAmount(txn.paidAmount),
                    refund = txn.source == TransactionSource.Refund,
                    txnCreate = txn.txnCreate
                )
            }
    }

    private fun inRange(txnCreate: Long, from: YearMonth, to: YearMonth, zone: ZoneId): Boolean {
        val month = YearMonth.from(Instant.ofEpochMilli(txnCreate).atZone(zone))
        return !month.isBefore(from) && !month.isAfter(to)
    }

    private fun bucket(categoryId: String?, grain: CategoryGrain): String? {
        if (categoryId == null || grain == CategoryGrain.Assigned) return categoryId
        return ExpenseCategories.find(categoryId)?.parentId ?: categoryId
    }

    private fun successfulPurchases(transactions: List<Transaction>): List<Transaction> {
        return transactions.filter {
            it.source == TransactionSource.Purchase && it.status == TransactionStatus.Success
        }
    }

    private fun successfulRefunds(transactions: List<Transaction>): List<Transaction> {
        return transactions.filter {
            it.source == TransactionSource.Refund && it.status == TransactionStatus.Success
        }
    }

    private fun netCategoryRows(transactions: List<Transaction>, categoryId: String?): List<Transaction> {
        val purchases = successfulPurchases(transactions)
        val refunds = successfulRefunds(transactions).filter { categoryOf(it, purchases) == categoryId }
        return purchases.filter { it.categoryId == categoryId } + refunds
    }

    private fun categoryOf(txn: Transaction, purchases: List<Transaction>): String? {
        return if (txn.source == TransactionSource.Refund) refundCategory(txn, purchases) else txn.categoryId
    }

    private fun refundCategory(refund: Transaction, purchases: List<Transaction>): String? {
        val orderNo = refund.orderNo?.trim().orEmpty()
        if (orderNo.isNotEmpty()) {
            purchases.firstOrNull { it.orderNo?.trim() == orderNo }?.let { return it.categoryId }
        }
        val merchant = refund.merchantName.trim()
        if (merchant.isNotEmpty()) {
            val matches = purchases.filter { it.merchantName.trim().equals(merchant, ignoreCase = true) }
            if (matches.size == 1) return matches.single().categoryId
        }
        return refund.categoryId
    }

    private fun signedAmount(txn: Transaction): BigDecimal {
        val amount = parseAmount(txn.paidAmount)
        return if (txn.source == TransactionSource.Refund) amount.negate() else amount
    }

    private fun netAmounts(
        purchases: List<Transaction>,
        refunds: List<Transaction>,
        allPurchases: List<Transaction>,
        grain: CategoryGrain
    ): Map<String?, BigDecimal> {
        val grouped = linkedMapOf<String?, BigDecimal>()
        purchases.forEach { txn ->
            val key = bucket(txn.categoryId, grain)
            val current = grouped[key] ?: BigDecimal.ZERO.setScale(2)
            grouped[key] = current + parseAmount(txn.paidAmount)
        }
        refunds.forEach { txn ->
            val key = bucket(refundCategory(txn, allPurchases), grain)
            val current = grouped[key] ?: BigDecimal.ZERO.setScale(2)
            grouped[key] = current - parseAmount(txn.paidAmount)
        }
        return grouped
    }

    private fun labelOf(categoryId: String?, locale: AppLocale = AppLocale.Ru): String {
        return ExpenseCategories.labelFor(categoryId, locale)
            ?: if (locale == AppLocale.En) "uncategorized" else "без категории"
    }

    private fun shareOf(amount: BigDecimal, denominator: BigDecimal): BigDecimal {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4)
        }
        return amount.divide(denominator, 4, RoundingMode.HALF_UP)
    }

    private fun headerCurrency(rows: List<Transaction>): String {
        val currency = rows
            .groupingBy { it.paidCurrency }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
        return if (currency.equals("USDT", ignoreCase = true) || currency.equals("USDC", ignoreCase = true)) {
            "USD"
        } else {
            currency
        }
    }

    private fun parseAmount(raw: String): BigDecimal {
        return raw.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    }
}
