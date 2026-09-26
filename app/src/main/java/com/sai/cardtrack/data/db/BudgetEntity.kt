package com.sai.cardtrack.data.db

import androidx.room.Entity
import com.sai.cardtrack.domain.Budget

@Entity(tableName = "budgets", primaryKeys = ["year", "month", "categoryKey"])
data class BudgetEntity(
    val year: Int,
    val month: Int,
    val categoryKey: String,
    val limitAmount: String
) {
    fun toDomain(): Budget {
        return Budget(
            year = year,
            month = month,
            categoryId = categoryKey.ifEmpty { null },
            limitAmount = limitAmount
        )
    }

    companion object {
        fun from(budget: Budget): BudgetEntity {
            return BudgetEntity(
                year = budget.year,
                month = budget.month,
                categoryKey = budget.categoryId.orEmpty(),
                limitAmount = budget.limitAmount
            )
        }
    }
}
