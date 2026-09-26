package com.sai.cardtrack.data

import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionDraft
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    suspend fun upsertFromSync(draft: TransactionDraft, syncedAt: Long)
    suspend fun setCategory(txnId: String, categoryId: String)
    fun observeAll(): Flow<List<Transaction>>
    suspend fun get(txnId: String): Transaction?
    suspend fun isEmpty(): Boolean
    suspend fun clear()
}
