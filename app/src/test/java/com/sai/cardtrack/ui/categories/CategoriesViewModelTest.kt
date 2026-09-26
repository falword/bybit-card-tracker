package com.sai.cardtrack.ui.categories

import androidx.lifecycle.SavedStateHandle
import com.sai.cardtrack.data.InMemoryTransactionRepository
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Asia/Bangkok")
    private val ru = Locale("ru")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ms(year: Int, month: Int, day: Int): Long {
        return ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    }

    private suspend fun repoWith(vararg drafts: TransactionDraft): InMemoryTransactionRepository {
        val repo = InMemoryTransactionRepository()
        drafts.forEach { repo.upsertFromSync(it, 1L) }
        return repo
    }

    private fun expense(
        id: String,
        amount: String,
        year: Int,
        month: Int,
        day: Int
    ): TransactionDraft {
        return TransactionDraft(
            id, id, TransactionKind.Expense, amount, "USDT", id,
            ms(year, month, day), TransactionStatus.Success, "3"
        )
    }

    private fun vm(repo: InMemoryTransactionRepository): CategoriesViewModel {
        return CategoriesViewModel(repo, clock = { ms(2026, 9, 6) }, zone = zone)
    }

    private fun monthTitle(year: Int, month: Int): String {
        return YearMonth.of(year, month)
            .format(DateTimeFormatter.ofPattern("LLLL yyyy", ru))
            .replaceFirstChar { it.titlecase(ru) }
    }

    private fun chargeDate(year: Int, month: Int, day: Int): String {
        return Instant.ofEpochMilli(ms(year, month, day)).atZone(zone).format(
            DateTimeFormatter.ofPattern("d MMM", ru)
        )
    }

    private fun shortMonth(year: Int, month: Int): String {
        return YearMonth.of(year, month)
            .format(DateTimeFormatter.ofPattern("LLL", ru))
            .trimEnd('.')
    }

    @Test
    fun `opens on the current month in breakdown mode`() = runTest {
        val repo = repoWith(expense("A", "30.00", 2026, 9, 2), expense("B", "70.00", 2026, 8, 10))
        repo.setCategory("A", "food")
        repo.setCategory("B", "taxi")
        val categories = vm(repo)
        advanceUntilIdle()
        val state = categories.state.value
        assertEquals(CategoriesMode.Breakdown, state.mode)
        assertEquals(monthTitle(2026, 9), state.monthLabel)
        assertEquals("сравнить", state.compareActionLabel)
        assertTrue(state.showMonthNav)
        assertTrue(state.showCompareAction)
        assertEquals("30.00", state.expensesLabel)
        assertEquals("USD", state.currency)
        assertEquals(1, state.rows.size)
        assertEquals("Еда", state.rows.single().label)
        assertEquals("100%", state.rows.single().percentLabel)
        assertFalse(state.empty)
    }

    @Test
    fun `compare toggle switches modes`() = runTest {
        val repo = repoWith(expense("A", "10.00", 2026, 9, 2))
        repo.setCategory("A", "food")
        val categories = vm(repo)
        advanceUntilIdle()
        categories.onCompareToggle()
        advanceUntilIdle()
        assertEquals(CategoriesMode.Compare, categories.state.value.mode)
        assertEquals("к разбивке", categories.state.value.compareActionLabel)
        assertEquals(4, categories.state.value.compareMonthLabels.size)
        assertEquals(shortMonth(2026, 9), categories.state.value.compareMonthLabels.first())
        assertEquals(shortMonth(2026, 6), categories.state.value.compareMonthLabels.last())
        categories.onCompareToggle()
        advanceUntilIdle()
        assertEquals(CategoriesMode.Breakdown, categories.state.value.mode)
        assertEquals("сравнить", categories.state.value.compareActionLabel)
    }

    @Test
    fun `row tap lists every expense in that category and back returns to the month`() = runTest {
        val repo = repoWith(
            expense("A", "200.00", 2026, 7, 4),
            expense("B", "50.00", 2026, 9, 2)
        )
        repo.setCategory("A", "taxi")
        repo.setCategory("B", "taxi")
        val categories = vm(repo)
        advanceUntilIdle()
        categories.onRowTap("transport")
        advanceUntilIdle()
        val opened = categories.state.value
        assertEquals(CategoriesMode.History, opened.mode)
        assertEquals("Транспорт", opened.title)
        assertFalse(opened.showMonthNav)
        assertFalse(opened.showCompareAction)
        assertEquals(listOf("B"), opened.expenseRows.map { it.merchant })
        assertEquals("50.00", opened.expenseRows[0].amount)
        assertEquals(chargeDate(2026, 9, 2), opened.expenseRows[0].dateLabel)
        categories.onBackFromHistory()
        advanceUntilIdle()
        val back = categories.state.value
        assertEquals(CategoriesMode.Breakdown, back.mode)
        assertEquals(monthTitle(2026, 9), back.monthLabel)
        assertEquals("Категории", back.title)
    }

    @Test
    fun `simplified grain folds children into the parent and lists them on open`() = runTest {
        val repo = repoWith(
            expense("Groceries", "70.00", 2026, 9, 3),
            expense("Dinner", "30.00", 2026, 9, 2)
        )
        repo.setCategory("Groceries", "groceries")
        repo.setCategory("Dinner", "food")
        val categories = vm(repo)
        advanceUntilIdle()
        assertTrue(categories.state.value.simplified)
        assertEquals("полный", categories.state.value.grainActionLabel)
        assertEquals("food", categories.state.value.rows.single().categoryId)
        assertEquals("Еда", categories.state.value.rows.single().label)
        assertEquals("100.00", categories.state.value.rows.single().amount)
        categories.onCompareToggle()
        advanceUntilIdle()
        assertEquals("Еда", categories.state.value.compareRows.single().label)
        categories.onCompareToggle()
        advanceUntilIdle()
        categories.onRowTap("food")
        advanceUntilIdle()
        assertEquals(listOf("Groceries", "Dinner"), categories.state.value.expenseRows.map { it.merchant })
        categories.onBackFromHistory()
        advanceUntilIdle()
        categories.onGrainToggle()
        advanceUntilIdle()
        assertFalse(categories.state.value.simplified)
        assertEquals(2, categories.state.value.rows.size)
    }

    @Test
    fun `compare row lists charges inside the compare window`() = runTest {
        val repo = repoWith(
            expense("Old", "9.00", 2026, 3, 2),
            expense("June", "10.00", 2026, 6, 2),
            expense("Sept", "20.00", 2026, 9, 2)
        )
        repo.setCategory("Old", "food")
        repo.setCategory("June", "food")
        repo.setCategory("Sept", "food")
        val categories = vm(repo)
        advanceUntilIdle()
        categories.onCompareToggle()
        advanceUntilIdle()
        categories.onRowTap("food")
        advanceUntilIdle()
        assertEquals(listOf("Sept", "June"), categories.state.value.expenseRows.map { it.merchant })
    }

    @Test
    fun `saved full grain is restored`() = runTest {
        val repo = repoWith(
            expense("Groceries", "70.00", 2026, 9, 3),
            expense("Dinner", "30.00", 2026, 9, 2)
        )
        repo.setCategory("Groceries", "groceries")
        repo.setCategory("Dinner", "food")
        val saved = SavedStateHandle(mapOf("grain" to "Assigned"))
        val categories = CategoriesViewModel(
            repo,
            clock = { ms(2026, 9, 6) },
            zone = zone,
            savedState = saved
        )
        advanceUntilIdle()
        assertFalse(categories.state.value.simplified)
        assertEquals("упрощённый", categories.state.value.grainActionLabel)
        assertEquals(2, categories.state.value.rows.size)
        categories.onGrainToggle()
        advanceUntilIdle()
        assertEquals("Parent", saved.get<String>("grain"))
    }

    @Test
    fun `compare marks spend deltas against the last filled month`() = runTest {
        val repo = repoWith(
            expense("A", "10.00", 2026, 6, 2),
            expense("B", "20.00", 2026, 9, 2)
        )
        repo.setCategory("A", "food")
        repo.setCategory("B", "food")
        val categories = vm(repo)
        advanceUntilIdle()
        categories.onCompareToggle()
        advanceUntilIdle()
        val row = categories.state.value.compareRows.single()
        assertEquals(listOf(1, 0, 0, 0), row.changes)
        assertEquals("20.00", row.cells.first())
    }

    @Test
    fun `chevrons in compare shift the right edge of the window`() = runTest {
        val repo = repoWith(
            expense("A", "10.00", 2026, 4, 2),
            expense("B", "20.00", 2026, 9, 2)
        )
        repo.setCategory("A", "food")
        repo.setCategory("B", "food")
        val categories = vm(repo)
        advanceUntilIdle()
        categories.onCompareToggle()
        advanceUntilIdle()
        assertEquals(shortMonth(2026, 9), categories.state.value.compareMonthLabels.first())
        categories.prevMonth()
        advanceUntilIdle()
        assertEquals(CategoriesMode.Compare, categories.state.value.mode)
        assertEquals(shortMonth(2026, 8), categories.state.value.compareMonthLabels.first())
        assertEquals(shortMonth(2026, 5), categories.state.value.compareMonthLabels.last())
    }

    @Test
    fun `month without successful purchases is empty`() = runTest {
        val repo = repoWith(expense("A", "10.00", 2026, 8, 2))
        repo.setCategory("A", "food")
        val categories = vm(repo)
        advanceUntilIdle()
        assertTrue(categories.state.value.empty)
        assertEquals("нет расходов", categories.state.value.emptyLabel)
        assertTrue(categories.state.value.rows.isEmpty())
    }

    @Test
    fun `saved month and compare mode are restored`() = runTest {
        val repo = repoWith(
            expense("A", "10.00", 2026, 8, 2)
        )
        repo.setCategory("A", "food")
        val saved = SavedStateHandle(
            mapOf(
                "month" to "2026-08",
                "mode" to "Compare"
            )
        )
        val categories = CategoriesViewModel(
            repo,
            clock = { ms(2026, 9, 6) },
            zone = zone,
            savedState = saved
        )
        advanceUntilIdle()
        assertEquals(CategoriesMode.Compare, categories.state.value.mode)
        assertEquals(monthTitle(2026, 8), categories.state.value.monthLabel)
        categories.onCompareToggle()
        advanceUntilIdle()
        assertEquals("Breakdown", saved.get<String>("mode"))
    }

    @Test
    fun `tapping a charge assigns a new category and drops it from the open list`() = runTest {
        val repo = repoWith(expense("Cafe", "30.00", 2026, 9, 2))
        repo.setCategory("Cafe", "dining")
        val categories = vm(repo)
        advanceUntilIdle()
        categories.onGrainToggle()
        advanceUntilIdle()
        categories.onRowTap("dining")
        advanceUntilIdle()
        categories.onExpenseTap("Cafe")
        advanceUntilIdle()
        assertEquals("Cafe", categories.state.value.sheet?.merchant)
        assertEquals("dining", categories.state.value.sheet?.selectedCategoryId)
        categories.onCategoryPicked("food")
        advanceUntilIdle()
        assertEquals("food", categories.state.value.sheet?.browseParentId)
        categories.onCategoryPicked("groceries")
        advanceUntilIdle()
        assertNull(categories.state.value.sheet)
        assertEquals("groceries", repo.get("Cafe")?.categoryId)
        assertTrue(categories.state.value.expenseRows.isEmpty())
    }

    @Test
    fun `picking the parent while browsing assigns that parent`() = runTest {
        val repo = repoWith(expense("Cafe", "30.00", 2026, 9, 2))
        val categories = vm(repo)
        advanceUntilIdle()
        categories.onExpenseTap("Cafe")
        advanceUntilIdle()
        categories.onCategoryPicked("food")
        advanceUntilIdle()
        categories.onCategoryPicked("food")
        advanceUntilIdle()
        assertNull(categories.state.value.sheet)
        assertEquals("food", repo.get("Cafe")?.categoryId)
    }

    @Test
    fun `refund row does not open the category sheet`() = runTest {
        val repo = repoWith(
            TransactionDraft(
                "Back", "Back", TransactionKind.Expense, "10.00", "USDT", "Cafe",
                ms(2026, 9, 3), TransactionStatus.Success, "4",
                TransactionSource.Refund
            )
        )
        repo.setCategory("Back", "dining")
        val categories = vm(repo)
        advanceUntilIdle()
        categories.onRowTap("food")
        advanceUntilIdle()
        assertEquals("Cafe", categories.state.value.expenseRows.single().merchant)
        categories.onExpenseTap("Back")
        advanceUntilIdle()
        assertNull(categories.state.value.sheet)
    }
}
