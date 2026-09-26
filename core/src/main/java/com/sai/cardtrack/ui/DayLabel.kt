package com.sai.cardtrack.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DayLabel {
    fun format(txnMs: Long, nowMs: Long, zone: ZoneId, copy: UiCopy): String {
        val javaLocale = if (copy.locale == AppLocale.En) Locale.ENGLISH else Locale("ru")
        val day = Instant.ofEpochMilli(txnMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return when (day) {
            today -> copy.today
            today.minusDays(1) -> copy.yesterday
            else -> day.format(DateTimeFormatter.ofPattern("d MMMM", javaLocale))
        }
    }

    fun key(txnMs: Long, zone: ZoneId): String {
        return Instant.ofEpochMilli(txnMs).atZone(zone).toLocalDate().toString()
    }
}
