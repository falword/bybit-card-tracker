package com.sai.cardtrack.data

import com.sai.cardtrack.domain.RewardSnapshot

interface RewardStore {
    suspend fun get(): RewardSnapshot?
    suspend fun save(snapshot: RewardSnapshot)
    suspend fun clear()
}
