package com.sai.cardtrack.data

import com.sai.cardtrack.sync.SliceCoverage
import com.sai.cardtrack.sync.SliceFamily

class InMemorySliceCoverageStore(
    private val coverage: SliceCoverage = SliceCoverage()
) : SliceCoverageStore {
    override fun shouldSkip(
        family: SliceFamily,
        from: Long,
        to: Long,
        now: Long,
        forceNetwork: Boolean
    ): Boolean {
        return coverage.shouldSkip(family, from, to, now, forceNetwork)
    }

    override fun markSuccess(family: SliceFamily, from: Long, to: Long) {
        coverage.markIfEligible(family, from, to)
    }

    override fun clear() {
        coverage.clear()
    }
}
