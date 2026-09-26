package com.sai.cardtrack.data

import com.sai.cardtrack.domain.FundBalanceSnapshot

class InMemoryFundBalanceStore : FundBalanceStore {
    private var snapshot: FundBalanceSnapshot? = null

    override suspend fun get(): FundBalanceSnapshot? = snapshot

    override suspend fun save(snapshot: FundBalanceSnapshot) {
        this.snapshot = snapshot
    }

    override suspend fun clear() {
        snapshot = null
    }
}
