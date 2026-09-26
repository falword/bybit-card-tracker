package com.sai.cardtrack.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month")
    fun observe(year: Int, month: Int): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE year = :year AND month = :month AND categoryKey = :categoryKey")
    suspend fun delete(year: Int, month: Int, categoryKey: String)

    @Query("DELETE FROM budgets")
    suspend fun clear()
}
