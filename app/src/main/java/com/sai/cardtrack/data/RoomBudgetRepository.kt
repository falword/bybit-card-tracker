package com.sai.cardtrack.data

import com.sai.cardtrack.data.db.BudgetDao
import com.sai.cardtrack.data.db.BudgetEntity
import com.sai.cardtrack.domain.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBudgetRepository(
    private val dao: BudgetDao
) : BudgetRepository {
    override suspend fun upsert(budget: Budget) {
        dao.upsert(BudgetEntity.from(budget))
    }

    override suspend fun delete(year: Int, month: Int, categoryId: String?) {
        dao.delete(year, month, categoryId.orEmpty())
    }

    override fun observe(year: Int, month: Int): Flow<List<Budget>> {
        return dao.observe(year, month).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun clear() {
        dao.clear()
    }
}
