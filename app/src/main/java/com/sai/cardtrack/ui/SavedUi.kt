package com.sai.cardtrack.ui

import androidx.lifecycle.SavedStateHandle
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

object SavedUi {
    const val MONTH: String = "month"
    const val MODE: String = "mode"
    const val HISTORY_CATEGORY: String = "historyCategoryId"
    const val GRAIN: String = "grain"
    const val HISTORY_FROM_COMPARE: String = "historyFromCompare"
    const val QUERY: String = "query"
    const val SOURCE: String = "source"
    const val CATEGORY: String = "categoryId"
    const val UNCATEGORIZED_ONLY: String = "uncategorizedOnly"
    const val AMOUNT_MIN: String = "amountMin"
    const val AMOUNT_MAX: String = "amountMax"
    const val DATE_PRESET: String = "datePreset"
    const val DATE_START_UTC: String = "dateStartUtc"
    const val DATE_END_UTC: String = "dateEndUtc"

    fun month(saved: SavedStateHandle?, clock: () -> Long, zone: ZoneId): YearMonth {
        val parsed = saved?.get<String>(MONTH)?.let { raw ->
            runCatching { YearMonth.parse(raw) }.getOrNull()
        }
        return parsed ?: YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zone))
    }

    fun persistMonth(saved: SavedStateHandle?, month: YearMonth) {
        saved?.set(MONTH, month.toString())
    }
}
