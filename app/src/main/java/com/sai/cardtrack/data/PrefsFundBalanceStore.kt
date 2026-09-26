package com.sai.cardtrack.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.sai.cardtrack.domain.FundBalanceSnapshot

class PrefsFundBalanceStore(
    private val prefs: SharedPreferences
) : FundBalanceStore {
    override suspend fun get(): FundBalanceSnapshot? {
        val amount = prefs.getString(KEY_AMOUNT, null) ?: return null
        return FundBalanceSnapshot(
            amount = amount,
            currency = prefs.getString(KEY_CURRENCY, "USD") ?: "USD",
            updatedAt = prefs.getLong(KEY_UPDATED, 0L)
        )
    }

    override suspend fun save(snapshot: FundBalanceSnapshot) {
        prefs.edit {
            putString(KEY_AMOUNT, snapshot.amount)
            putString(KEY_CURRENCY, snapshot.currency)
            putLong(KEY_UPDATED, snapshot.updatedAt)
        }
    }

    override suspend fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        const val PREFS_NAME: String = "cardtrack_fund_balance"
        const val KEY_AMOUNT: String = "amount"
        const val KEY_CURRENCY: String = "currency"
        const val KEY_UPDATED: String = "updated_at"
    }
}
