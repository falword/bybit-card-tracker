package com.sai.cardtrack.sync

import com.sai.cardtrack.domain.MonthMath
import com.sai.cardtrack.domain.QueryType

enum class SliceFamily {
    Auth,
    Refund,
    Financial,
    Points,
    Funding,
    Transfer,
    Earn
    ;

    companion object {
        fun of(type: QueryType): SliceFamily {
            return when (type) {
                QueryType.Auth -> Auth
                QueryType.Refund -> Refund
                QueryType.Financial -> Financial
            }
        }
    }
}

class SliceCoverage {
    private val intervals = linkedMapOf<SliceFamily, MutableList<LongRange>>()

    fun covers(family: SliceFamily, from: Long, to: Long): Boolean {
        if (to < from) return false
        return intervals[family].orEmpty().any { it.first <= from && it.last >= to }
    }

    fun mark(family: SliceFamily, from: Long, to: Long) {
        if (to < from) return
        val list = intervals.getOrPut(family) { mutableListOf() }
        list.add(from..to)
        list.sortBy { it.first }
        val merged = mutableListOf<LongRange>()
        for (range in list) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged.add(range)
            }
        }
        intervals[family] = merged
    }

    fun markIfEligible(family: SliceFamily, from: Long, to: Long) {
        if (to - from > MonthMath.SEVEN_DAYS_MS) return
        mark(family, from, to)
    }

    fun shouldSkip(
        family: SliceFamily,
        from: Long,
        to: Long,
        now: Long,
        forceNetwork: Boolean
    ): Boolean {
        if (forceNetwork) return false
        if (to - from > MonthMath.SEVEN_DAYS_MS) return false
        if (to > now - MonthMath.INCREMENTAL_OVERLAP_MS) return false
        return covers(family, from, to)
    }

    fun clear() {
        intervals.clear()
    }

    fun encode(): String {
        return SliceFamily.entries.joinToString("\n") { family ->
            val ranges = intervals[family].orEmpty()
            if (ranges.isEmpty()) {
                ""
            } else {
                family.name.uppercase() + ":" + ranges.joinToString(",") { "${it.first}-${it.last}" }
            }
        }.lineSequence().filter { it.isNotEmpty() }.joinToString("\n")
    }

    companion object {
        fun decode(text: String): SliceCoverage {
            val coverage = SliceCoverage()
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val sep = trimmed.indexOf(':')
                if (sep <= 0) continue
                val family = runCatching { SliceFamily.valueOf(trimmed.take(sep).lowercase().replaceFirstChar { it.uppercase() }) }
                    .getOrNull() ?: continue
                for (part in trimmed.substring(sep + 1).split(',')) {
                    val dash = part.indexOf('-')
                    if (dash <= 0) continue
                    val from = part.take(dash).toLongOrNull() ?: continue
                    val to = part.substring(dash + 1).toLongOrNull() ?: continue
                    coverage.mark(family, from, to)
                }
            }
            return coverage
        }
    }
}
