package com.sai.cardtrack.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.sai.cardtrack.sync.SliceCoverage
import com.sai.cardtrack.sync.SliceFamily

class PrefsSliceCoverageStore(
    private val prefs: SharedPreferences
) : SliceCoverageStore {
    private val lock = Any()

    override fun shouldSkip(
        family: SliceFamily,
        from: Long,
        to: Long,
        now: Long,
        forceNetwork: Boolean
    ): Boolean {
        return synchronized(lock) {
            load().shouldSkip(family, from, to, now, forceNetwork)
        }
    }

    override fun markSuccess(family: SliceFamily, from: Long, to: Long) {
        synchronized(lock) {
            val coverage = load()
            coverage.markIfEligible(family, from, to)
            prefs.edit { putString(KEY, coverage.encode()) }
        }
    }

    override fun clear() {
        synchronized(lock) {
            prefs.edit { remove(KEY) }
        }
    }

    private fun load(): SliceCoverage {
        return SliceCoverage.decode(prefs.getString(KEY, "") ?: "")
    }

    companion object {
        const val KEY: String = "slice_coverage"
    }
}
