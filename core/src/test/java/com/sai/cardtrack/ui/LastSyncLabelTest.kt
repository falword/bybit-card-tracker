package com.sai.cardtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LastSyncLabelTest {
    private val zone: ZoneId = ZoneId.of("Asia/Bangkok")

    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    }

    @Test
    fun `missing timestamp is null`() {
        assertNull(LastSyncLabel.format(null, ms(2026, 9, 6, 15, 4), zone))
    }

    @Test
    fun `same day is clock time`() {
        val now = ms(2026, 9, 6, 15, 4)
        assertEquals("12:00", LastSyncLabel.format(ms(2026, 9, 6, 12, 0), now, zone))
    }

    @Test
    fun `other day is russian day and month`() {
        val label = LastSyncLabel.format(ms(2026, 8, 1, 9, 0), ms(2026, 9, 6, 15, 4), zone)
        assertTrue(label!!.contains("1"))
        assertTrue(label.contains("авг"))
    }

    @Test
    fun `other day in english uses english month`() {
        val label = LastSyncLabel.format(
            ms(2026, 8, 1, 9, 0),
            ms(2026, 9, 6, 15, 4),
            zone,
            AppLocale.En
        )
        assertTrue(label!!.contains("1"))
        assertTrue(label.lowercase().contains("aug"))
    }
}
