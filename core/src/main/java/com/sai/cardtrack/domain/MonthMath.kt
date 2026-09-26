package com.sai.cardtrack.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class MonthTotals(
    val expenses: BigDecimal,
    val income: BigDecimal,
    val net: BigDecimal,
    val currency: String,
    val cashback: BigDecimal = BigDecimal.ZERO.setScale(2),
    val fees: BigDecimal = BigDecimal("0.00"),
    val earnYield: BigDecimal = BigDecimal("0.00")
)

object MonthMath {

    fun inMonth(txnCreate: Long, year: Int, month: Int, zone: ZoneId): Boolean {
        val ym = YearMonth.from(Instant.ofEpochMilli(txnCreate).atZone(zone))
        return ym.year == year && ym.monthValue == month
    }

    fun visible(
        transactions: List<Transaction>,
        year: Int,
        month: Int,
        zone: ZoneId,
        includeP2p: Boolean = false
    ): List<Transaction> {
        return transactions
            .filter { inMonth(it.txnCreate, year, month, zone) }
            .filter { it.source == TransactionSource.Purchase || it.source == TransactionSource.TopUp ||
                (includeP2p && it.source == TransactionSource.P2P) }
            .sortedByDescending { it.txnCreate }
    }

    fun declined(
        transactions: List<Transaction>,
        year: Int,
        month: Int,
        zone: ZoneId
    ): List<Transaction> {
        return transactions
            .filter { inMonth(it.txnCreate, year, month, zone) }
            .filter { it.source == TransactionSource.Declined }
            .sortedByDescending { it.txnCreate }
    }

    fun totals(
        transactions: List<Transaction>,
        year: Int,
        month: Int,
        zone: ZoneId,
        includeP2p: Boolean = false
    ): MonthTotals {
        val monthRows = transactions.filter { inMonth(it.txnCreate, year, month, zone) }
        val success = monthRows.filter { it.status == TransactionStatus.Success }
        val purchases = success
            .filter { it.source == TransactionSource.Purchase }
            .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        val refunds = success
            .filter { it.source == TransactionSource.Refund }
            .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        val topUps = success
            .filter { it.source == TransactionSource.TopUp }
            .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        val p2pExpense = if (includeP2p) {
            success.filter { it.source == TransactionSource.P2P && it.kind == TransactionKind.Expense }
                .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        } else {
            BigDecimal.ZERO
        }
        val p2pIncome = if (includeP2p) {
            success.filter { it.source == TransactionSource.P2P && it.kind == TransactionKind.Income }
                .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        } else {
            BigDecimal.ZERO
        }
        val p2pExpenseRefund = if (includeP2p) {
            success.filter { it.source == TransactionSource.P2PRefund && it.kind == TransactionKind.Expense }
                .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        } else {
            BigDecimal.ZERO
        }
        val p2pIncomeRefund = if (includeP2p) {
            success.filter { it.source == TransactionSource.P2PRefund && it.kind == TransactionKind.Income }
                .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        } else {
            BigDecimal.ZERO
        }
        val expenses = (purchases + p2pExpense - refunds - p2pExpenseRefund)
            .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        val income = (topUps + p2pIncome - p2pIncomeRefund).setScale(2, RoundingMode.HALF_UP)
        val cashbackPoints = success
            .filter { it.source == TransactionSource.Cashback }
            .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        val cashback = cashbackUsd(cashbackPoints)
        val earnYield = success
            .filter { it.source == TransactionSource.Earn }
            .fold(BigDecimal.ZERO) { acc, row ->
                acc + (row.paidAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            }
            .setScale(2, RoundingMode.HALF_UP)
        val fees = success
            .filter { it.source == TransactionSource.Purchase }
            .fold(BigDecimal.ZERO) { acc, row -> acc + row.fees.feeSum() }
            .setScale(2, RoundingMode.HALF_UP)
        val currency = success
            .filter { it.source != TransactionSource.Cashback && it.source != TransactionSource.Earn }
            .filter {
                includeP2p ||
                    (it.source != TransactionSource.P2P && it.source != TransactionSource.P2PRefund)
            }
            .groupingBy { it.paidCurrency }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
        return MonthTotals(
            expenses = expenses,
            income = income,
            net = (income - expenses).setScale(2, RoundingMode.HALF_UP),
            currency = if (currency.equals("USDT", ignoreCase = true) || currency.equals("USDC", ignoreCase = true)) {
                "USD"
            } else {
                currency
            },
            cashback = cashback,
            fees = fees,
            earnYield = earnYield
        )
    }

    fun cashbackUsd(points: BigDecimal): BigDecimal {
        return points.multiply(CASHBACK_USD_PER_POINT).setScale(2, RoundingMode.HALF_UP)
    }

    fun cashbackUsdForPurchase(purchase: Transaction, all: List<Transaction>): BigDecimal? {
        if (purchase.source != TransactionSource.Purchase) return null
        val purchaseKeys = linkKeys(purchase)
        if (purchaseKeys.isEmpty()) return null
        val points = all
            .filter { it.source == TransactionSource.Cashback && it.status == TransactionStatus.Success }
            .filter { linkKeys(it).any { key -> key in purchaseKeys } }
            .fold(BigDecimal.ZERO) { acc, row -> acc + parseAmount(row.paidAmount) }
        if (points <= BigDecimal.ZERO) return null
        return cashbackUsd(points)
    }

    fun effectiveCashbackPercent(totals: MonthTotals): BigDecimal? {
        if (totals.expenses <= BigDecimal.ZERO) return null
        return totals.cashback
            .multiply(BigDecimal("100"))
            .divide(totals.expenses, 1, RoundingMode.HALF_UP)
    }

    fun syncBeginMillis(nowMillis: Long, zone: ZoneId): Long {
        val ym = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone)).minusMonths(12)
        return ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun monthBeginMillis(nowMillis: Long, zone: ZoneId): Long {
        val ym = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone))
        return ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun previousMonthBeginMillis(nowMillis: Long, zone: ZoneId): Long {
        val ym = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone)).minusMonths(1)
        return ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun priorityBegin(historyBegin: Long, nowMillis: Long, sinceMillis: Long?, zone: ZoneId): Long {
        val incremental = incrementalBegin(historyBegin, nowMillis, sinceMillis)
        val previousMonth = previousMonthBeginMillis(nowMillis, zone)
        return maxOf(historyBegin, minOf(incremental, previousMonth))
    }

    fun priorityMonthWindows(begin: Long, nowMillis: Long, zone: ZoneId): List<Pair<Long, Long>> {
        if (nowMillis <= begin) {
            return emptyList()
        }
        val startYm = YearMonth.from(Instant.ofEpochMilli(begin).atZone(zone))
        val endYm = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone))
        if (endYm.isBefore(startYm)) {
            return emptyList()
        }
        val windows = mutableListOf<Pair<Long, Long>>()
        var ym = endYm
        while (!ym.isBefore(startYm)) {
            val monthStart = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val nextStart = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val from = maxOf(begin, monthStart)
            val to = minOf(nowMillis, nextStart)
            if (to > from) {
                windows.add(from to to)
            }
            ym = ym.minusMonths(1)
        }
        return windows
    }

    fun incrementalBegin(historyBegin: Long, nowMillis: Long, sinceMillis: Long?): Long {
        if (sinceMillis == null) return historyBegin
        val overlapped = sinceMillis - INCREMENTAL_OVERLAP_MS
        return maxOf(historyBegin, minOf(overlapped, nowMillis))
    }

    fun earnHistoryBeginMillis(nowMillis: Long): Long = nowMillis - EARN_HISTORY_MS

    fun showEarnColumn(
        year: Int,
        month: Int,
        zone: ZoneId,
        nowMillis: Long,
        earnSyncedThrough: Long?
    ): Boolean {
        if (earnSyncedThrough == null) return false
        val monthEnd = YearMonth.of(year, month)
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli() - 1L
        return monthEnd >= nowMillis - EARN_HISTORY_MS
    }

    fun boundedWindows(
        begin: Long,
        end: Long,
        maxSpanMs: Long = SEVEN_DAYS_MS
    ): List<Pair<Long, Long>> {
        if (end <= begin || maxSpanMs <= 0L) {
            return listOf(begin to end)
        }
        val windows = mutableListOf<Pair<Long, Long>>()
        var stop = end
        while (stop > begin) {
            val start = maxOf(begin, stop - maxSpanMs)
            windows.add(start to stop)
            if (start == begin) break
            stop = start
        }
        return windows
    }

    private val CASHBACK_USD_PER_POINT: BigDecimal = BigDecimal("0.002")
    const val SEVEN_DAYS_MS: Long = 7L * 24 * 60 * 60 * 1000
    const val INCREMENTAL_OVERLAP_MS: Long = 24L * 60 * 60 * 1000
    const val EARN_HISTORY_MS: Long = 90L * 24 * 60 * 60 * 1000

    private fun linkKeys(row: Transaction): Set<String> {
        if (row.source == TransactionSource.Cashback) {
            return row.orderNo.orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        return listOfNotNull(
            row.txnId.trim().takeIf { it.isNotEmpty() },
            row.orderNo?.trim()?.takeIf { it.isNotEmpty() }
        ).toSet()
    }

    private fun parseAmount(raw: String): BigDecimal {
        return raw.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    }
}
