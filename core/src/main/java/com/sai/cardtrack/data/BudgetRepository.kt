package com.sai.cardtrack.data

import com.sai.cardtrack.domain.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    suspend fun upsert(budget: Budget)
    suspend fun delete(year: Int, month: Int, categoryId: String?)
    fun observe(year: Int, month: Int): Flow<List<Budget>>
    suspend fun clear()
}
