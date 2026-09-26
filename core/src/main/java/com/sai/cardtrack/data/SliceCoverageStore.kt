package com.sai.cardtrack.data

import com.sai.cardtrack.sync.SliceFamily

interface SliceCoverageStore {
    fun shouldSkip(
        family: SliceFamily,
        from: Long,
        to: Long,
        now: Long,
        forceNetwork: Boolean
    ): Boolean

    fun markSuccess(family: SliceFamily, from: Long, to: Long)

    fun clear()
}
