package com.sai.cardtrack.domain

import com.sai.cardtrack.ui.AppLocale

object RewardDisplay {
    private val tierNumber = Regex(
        """(?:rewards[_-]?limits[_-]?)?tier[_-]?(\d+)""",
        RegexOption.IGNORE_CASE
    )

    fun tierLabel(raw: String, locale: AppLocale): String {
        val trimmed = raw.trim().trimEnd('*').trim()
        if (trimmed.isEmpty()) return ""
        val match = tierNumber.find(trimmed)
        if (match != null) {
            val n = match.groupValues[1]
            return if (locale == AppLocale.En) "Tier $n" else "Уровень $n"
        }
        return trimmed
    }

    fun limitLine(used: String, limit: String, unit: String): String {
        if (used.isBlank() && limit.isBlank()) return ""
        val usedF = CardMoney.formatWhole(used.ifBlank { "0" })
        val limitF = CardMoney.formatWhole(limit.ifBlank { "0" })
        return "$usedF / $limitF ${CardMoney.displayCurrency(unit)}"
    }

    fun subtitle(
        tier: String,
        used: String,
        limit: String,
        unit: String,
        locale: AppLocale
    ): String? {
        val parts = listOf(tierLabel(tier, locale), limitLine(used, limit, unit))
            .filter { it.isNotBlank() }
        return parts.joinToString("\n").ifBlank { null }
    }
}
