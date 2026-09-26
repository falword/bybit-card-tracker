package com.sai.cardtrack.domain

import java.math.BigDecimal

object FundBalanceMath {
    private val usdLike = setOf("USDT", "USDC", "USD", "USDE", "FDUSD", "TUSD", "DAI", "USD1")

    fun cardAvailableUsd(fundingEquity: String, easyEarnEquity: String): String {
        val a = fundingEquity.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val b = easyEarnEquity.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return CardMoney.format((a + b).toPlainString())
    }

    fun displayUsd(usdtWallet: String, usdcWallet: String): String {
        val a = usdtWallet.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val b = usdcWallet.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return CardMoney.format((a + b).toPlainString())
    }

    fun displayFromWallets(wallets: List<Pair<String, String>>): String {
        var usd = BigDecimal.ZERO
        var largest = BigDecimal.ZERO
        for ((coin, raw) in wallets) {
            val n = raw.toBigDecimalOrNull() ?: continue
            if (coin.trim().uppercase() in usdLike) {
                usd += n
            }
            if (n > largest) {
                largest = n
            }
        }
        val pick = if (usd.compareTo(BigDecimal.ONE) >= 0) usd else largest
        return CardMoney.format(pick.toPlainString())
    }
}
