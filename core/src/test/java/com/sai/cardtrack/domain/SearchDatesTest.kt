package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class SearchDatesTest {
    private val zone: ZoneId = ZoneId.of("Asia/Bangkok")

    @Test
    fun `utc picker day becomes local start and inclusive end`() {
        val utcDay = LocalDate.of(2026, 9, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val begin = SearchDates.beginMs(utcDay, zone)
        val end = SearchDates.endMs(utcDay, zone)
        assertEquals(
            LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            begin
        )
        assertEquals(
            LocalDate.of(2026, 9, 2).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
            end
        )
    }

    @Test
    fun `current month utc range is first and last local calendar day`() {
        val now = LocalDate.of(2026, 9, 7).atTime(4, 33).atZone(zone).toInstant().toEpochMilli()
        val range = SearchDates.currentMonthUtcRange(now, zone)
        assertEquals(
            LocalDate.of(2026, 9, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            range.first
        )
        assertEquals(
            LocalDate.of(2026, 9, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            range.second
        )
    }

    @Test
    fun `range label uses picked calendar days`() {
        val start = LocalDate.of(2026, 9, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 9, 6).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val ru = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
        val en = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        assertEquals(
            "${LocalDate.of(2026, 9, 1).format(ru)} – ${LocalDate.of(2026, 9, 6).format(ru)}",
            SearchDates.formatRange(start, end, Locale("ru"))
        )
        assertEquals(
            "${LocalDate.of(2026, 9, 1).format(en)} – ${LocalDate.of(2026, 9, 6).format(en)}",
            SearchDates.formatRange(start, end, Locale.ENGLISH)
        )
    }
}
