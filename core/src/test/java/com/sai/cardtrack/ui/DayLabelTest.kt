package com.sai.cardtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DayLabelTest {
    private val zone: ZoneId = ZoneId.of("Asia/Bangkok")

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
    }

    @Test
    fun `same calendar day is today`() {
        val now = ms(2026, 9, 7, 18)
        assertEquals("Сегодня", DayLabel.format(ms(2026, 9, 7, 9), now, zone, UiCopy.Ru))
        assertEquals("Today", DayLabel.format(ms(2026, 9, 7, 9), now, zone, UiCopy.En))
    }

    @Test
    fun `previous calendar day is yesterday`() {
        val now = ms(2026, 9, 7)
        assertEquals("Вчера", DayLabel.format(ms(2026, 9, 6), now, zone, UiCopy.Ru))
        assertEquals("Yesterday", DayLabel.format(ms(2026, 9, 6), now, zone, UiCopy.En))
    }

    @Test
    fun `older day uses localized date`() {
        val now = ms(2026, 9, 7)
        assertEquals("3 сентября", DayLabel.format(ms(2026, 9, 3), now, zone, UiCopy.Ru))
        assertEquals("3 September", DayLabel.format(ms(2026, 9, 3), now, zone, UiCopy.En))
    }
}
