package com.sai.cardtrack.ui.components

import java.math.BigDecimal

object LedgerAmount {
    fun signed(amount: String, currency: String, income: Boolean): String {
        val prefix = if (income) "+" else "−"
        return if (currency.isBlank()) prefix + amount else "$prefix$amount $currency"
    }

    fun monthNet(net: BigDecimal, currency: String): String {
        val sign = if (net < BigDecimal.ZERO) "−" else ""
        val body = sign + net.abs().toPlainString()
        return if (currency.isBlank()) body else "$body $currency"
    }

    fun breakdown(amount: BigDecimal, currency: String, inflow: Boolean): String {
        val body = amount.abs().toPlainString()
        val signed = when {
            amount.signum() == 0 -> body
            inflow -> "+$body"
            else -> "−$body"
        }
        return if (currency.isBlank()) signed else "$signed $currency"
    }

    fun note(parts: List<String?>): String? {
        return parts
            .flatMap { part -> part?.split('\n').orEmpty() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
            .ifBlank { null }
    }
}
