package com.sai.cardtrack.data

import com.sai.cardtrack.domain.FundBalanceSnapshot

interface FundBalanceStore {
    suspend fun get(): FundBalanceSnapshot?
    suspend fun save(snapshot: FundBalanceSnapshot)
    suspend fun clear()
}
