package com.sai.cardtrack.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.sai.cardtrack.domain.RewardSnapshot

class PrefsRewardStore(
    private val prefs: SharedPreferences
) : RewardStore {
    override suspend fun get(): RewardSnapshot? {
        val tier = prefs.getString(KEY_TIER, null) ?: return null
        return RewardSnapshot(
            usedLimit = prefs.getString(KEY_USED, "") ?: "",
            limit = prefs.getString(KEY_LIMIT, "") ?: "",
            unit = prefs.getString(KEY_UNIT, "") ?: "",
            tier = tier,
            autoCashback = prefs.getBoolean(KEY_AUTO, false),
            availablePoint = prefs.getString(KEY_AVAILABLE, "") ?: "",
            pendingPoint = prefs.getString(KEY_PENDING, "") ?: ""
        )
    }

    override suspend fun save(snapshot: RewardSnapshot) {
        prefs.edit {
            putString(KEY_USED, snapshot.usedLimit)
            putString(KEY_LIMIT, snapshot.limit)
            putString(KEY_UNIT, snapshot.unit)
            putString(KEY_TIER, snapshot.tier)
            putBoolean(KEY_AUTO, snapshot.autoCashback)
            putString(KEY_AVAILABLE, snapshot.availablePoint)
            putString(KEY_PENDING, snapshot.pendingPoint)
        }
    }

    override suspend fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        const val PREFS_NAME: String = "cardtrack_reward"
        const val KEY_USED: String = "used_limit"
        const val KEY_LIMIT: String = "limit"
        const val KEY_UNIT: String = "unit"
        const val KEY_TIER: String = "tier"
        const val KEY_AUTO: String = "auto_cashback"
        const val KEY_AVAILABLE: String = "available_point"
        const val KEY_PENDING: String = "pending_point"
    }
}
