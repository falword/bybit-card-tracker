package com.sai.cardtrack.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object SearchDates {
    fun currentMonthUtcRange(nowMillis: Long, zone: ZoneId): Pair<Long, Long> {
        val ym = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone))
        val start = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = ym.atEndOfMonth().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return start to end
    }

    fun beginMs(utcDayMillis: Long, zone: ZoneId): Long {
        return utcDate(utcDayMillis).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun endMs(utcDayMillis: Long, zone: ZoneId): Long {
        return utcDate(utcDayMillis).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }

    fun formatRange(startUtcMillis: Long, endUtcMillis: Long, locale: Locale): String {
        val fmt = DateTimeFormatter.ofPattern("d MMM", locale)
        val start = utcDate(startUtcMillis).format(fmt)
        val end = utcDate(endUtcMillis).format(fmt)
        return if (start == end) start else "$start – $end"
    }

    private fun utcDate(utcDayMillis: Long): LocalDate {
        return Instant.ofEpochMilli(utcDayMillis).atZone(ZoneOffset.UTC).toLocalDate()
    }
}
