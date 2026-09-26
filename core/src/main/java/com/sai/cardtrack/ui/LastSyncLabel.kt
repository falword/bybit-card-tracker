package com.sai.cardtrack.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LastSyncLabel {
    private val timeOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun format(epochMs: Long?, nowMs: Long, zone: ZoneId, locale: AppLocale = AppLocale.Ru): String? {
        if (epochMs == null) return null
        val javaLocale = if (locale == AppLocale.En) Locale.ENGLISH else Locale("ru")
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", javaLocale)
        val synced = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val formatter = if (synced.toLocalDate() == today) timeOnly else dayMonth
        return synced.format(formatter)
    }
}
