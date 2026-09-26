package com.sai.cardtrack.sync

import com.sai.cardtrack.domain.MonthMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SliceCoverageTest {

    private val seven = MonthMath.SEVEN_DAYS_MS
    private val tail = MonthMath.INCREMENTAL_OVERLAP_MS
    private val now = 1_000_000_000_000L

    @Test
    fun `covers contained range after mark`() {
        val coverage = SliceCoverage()
        coverage.mark(SliceFamily.Funding, 100L, 400L)
        assertTrue(coverage.covers(SliceFamily.Funding, 150L, 300L))
        assertFalse(coverage.covers(SliceFamily.Funding, 50L, 200L))
        assertFalse(coverage.covers(SliceFamily.Auth, 150L, 300L))
    }

    @Test
    fun `mark merges overlapping and touching intervals`() {
        val coverage = SliceCoverage()
        coverage.mark(SliceFamily.Auth, 100L, 200L)
        coverage.mark(SliceFamily.Auth, 200L, 300L)
        coverage.mark(SliceFamily.Auth, 180L, 220L)
        assertTrue(coverage.covers(SliceFamily.Auth, 100L, 300L))
        assertEquals("AUTH:100-300", coverage.encode())
    }

    @Test
    fun `shouldSkip only when contained closed and not forced`() {
        val coverage = SliceCoverage()
        val from = now - 10L * 24 * 60 * 60 * 1000
        val to = from + seven
        coverage.markIfEligible(SliceFamily.Funding, from, to)
        assertTrue(coverage.shouldSkip(SliceFamily.Funding, from, to, now, forceNetwork = false))
        assertFalse(coverage.shouldSkip(SliceFamily.Funding, from, to, now, forceNetwork = true))
        assertFalse(coverage.shouldSkip(SliceFamily.Funding, from, now, now, forceNetwork = false))
    }

    @Test
    fun `live tail is never skipped`() {
        val coverage = SliceCoverage()
        val from = now - seven
        coverage.markIfEligible(SliceFamily.Funding, from, now)
        assertFalse(coverage.shouldSkip(SliceFamily.Funding, from, now, now, forceNetwork = false))
        val closedTo = now - tail
        val closedFrom = closedTo - seven
        coverage.markIfEligible(SliceFamily.Funding, closedFrom, closedTo)
        assertTrue(coverage.shouldSkip(SliceFamily.Funding, closedFrom, closedTo, now, forceNetwork = false))
    }

    @Test
    fun `wide window is not marked and not skipped`() {
        val coverage = SliceCoverage()
        val from = now - 30L * 24 * 60 * 60 * 1000
        val to = now - tail
        coverage.markIfEligible(SliceFamily.Auth, from, to)
        assertFalse(coverage.covers(SliceFamily.Auth, from, to))
        assertFalse(coverage.shouldSkip(SliceFamily.Auth, from, to, now, forceNetwork = false))
    }

    @Test
    fun `encode and decode roundtrip`() {
        val coverage = SliceCoverage()
        coverage.mark(SliceFamily.Points, 10L, 20L)
        coverage.mark(SliceFamily.Funding, 5L, 8L)
        val restored = SliceCoverage.decode(coverage.encode())
        assertTrue(restored.covers(SliceFamily.Points, 10L, 20L))
        assertTrue(restored.covers(SliceFamily.Funding, 5L, 8L))
        assertEquals(coverage.encode(), restored.encode())
    }
}
