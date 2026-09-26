package com.sai.cardtrack.data

import com.sai.cardtrack.domain.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryBudgetRepository : BudgetRepository {
    private val items = LinkedHashMap<Triple<Int, Int, String>, Budget>()
    private val all = MutableStateFlow<List<Budget>>(emptyList())

    override suspend fun upsert(budget: Budget) {
        items[key(budget.year, budget.month, budget.categoryId)] = budget
        publish()
    }

    override suspend fun delete(year: Int, month: Int, categoryId: String?) {
        items.remove(key(year, month, categoryId))
        publish()
    }

    override fun observe(year: Int, month: Int): Flow<List<Budget>> {
        return all.map { rows -> rows.filter { it.year == year && it.month == month } }
    }

    override suspend fun clear() {
        items.clear()
        publish()
    }

    private fun key(year: Int, month: Int, categoryId: String?): Triple<Int, Int, String> {
        return Triple(year, month, categoryId ?: "")
    }

    private fun publish() {
        all.value = items.values.toList()
    }
}
