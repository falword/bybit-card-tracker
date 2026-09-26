package com.sai.cardtrack.data

import com.sai.cardtrack.domain.RewardSnapshot

class InMemoryRewardStore : RewardStore {
    private var snapshot: RewardSnapshot? = null

    override suspend fun get(): RewardSnapshot? = snapshot

    override suspend fun save(snapshot: RewardSnapshot) {
        this.snapshot = snapshot
    }

    override suspend fun clear() {
        snapshot = null
    }
}
