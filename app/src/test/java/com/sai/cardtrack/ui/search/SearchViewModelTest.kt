package com.sai.cardtrack.ui.search

import androidx.lifecycle.SavedStateHandle
import com.sai.cardtrack.data.InMemoryCredentialsStore
import com.sai.cardtrack.data.InMemoryTransactionRepository
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus
import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Asia/Bangkok")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `p2p row is hidden until setting on`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "fh_s", "s", TransactionKind.Expense, "40.00", "USD", "P2P",
                100L, TransactionStatus.Success, "p2p",
                source = TransactionSource.P2P
            ),
            1L
        )
        val store = InMemoryCredentialsStore()
        val search = SearchViewModel(repo, zone, debounceMs = 0, credentialsStore = store)
        advanceUntilIdle()
        assertTrue(search.state.value.rows.none { it.txnId == "fh_s" })
        store.setP2pAccountingEnabled(true)
        search.reloadLocal()
        advanceUntilIdle()
        assertEquals("P2P", search.state.value.rows.single { it.txnId == "fh_s" }.merchant)
    }

    @Test
    fun `query filters observed rows by merchant`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "1", "1", TransactionKind.Expense, "10.00", "USDT", "Uber Trip",
                100L, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "2", "2", TransactionKind.Expense, "20.00", "USDT", "Amazon",
                200L, TransactionStatus.Success, "3"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0)
        advanceUntilIdle()
        assertEquals(listOf("2", "1"), search.state.value.rows.map { it.txnId })
        search.onQueryChange("uber")
        advanceUntilIdle()
        assertEquals(listOf("1"), search.state.value.rows.map { it.txnId })
        assertEquals("Uber Trip", search.state.value.rows.single().merchant)
        assertEquals("uber", search.state.value.query)
    }

    @Test
    fun `rows expose source label kind and pending like home`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "r", "r", TransactionKind.Income, "5.00", "USDT", "Shop",
                400L, TransactionStatus.Success, "3",
                source = TransactionSource.Refund
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "c", "c", TransactionKind.Income, "1.00", "USDT", "Bybit",
                300L, TransactionStatus.Success, "3",
                source = TransactionSource.Cashback
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "d", "d", TransactionKind.Expense, "8.00", "USDT", "Denied",
                200L, TransactionStatus.Declined, "3",
                source = TransactionSource.Declined
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "p", "p", TransactionKind.Expense, "3.00", "USDT", "Hold",
                100L, TransactionStatus.Pending, "3"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, UiCopy.En, debounceMs = 0)
        advanceUntilIdle()
        val rows = search.state.value.rows.associateBy { it.txnId }
        assertEquals(UiCopy.En.refund, rows.getValue("r").categoryLabel)
        assertEquals(TransactionKind.Income, rows.getValue("r").kind)
        assertEquals(false, rows.getValue("r").pending)
        assertEquals(UiCopy.En.cashbackLabel, rows.getValue("c").categoryLabel)
        assertEquals(TransactionKind.Income, rows.getValue("c").kind)
        assertEquals(UiCopy.En.declines, rows.getValue("d").categoryLabel)
        assertEquals(TransactionKind.Expense, rows.getValue("d").kind)
        assertTrue(rows.getValue("p").pending)
        assertEquals(UiCopy.En.uncategorized, rows.getValue("p").categoryLabel)
    }

    @Test
    fun `blank query empty list uses no transactions and typed miss uses nothing found`() = runTest {
        val repo = InMemoryTransactionRepository()
        val search = SearchViewModel(repo, zone, UiCopy.En, debounceMs = 0)
        advanceUntilIdle()
        assertEquals(true, search.state.value.empty)
        assertEquals(UiCopy.En.noTransactions, search.state.value.emptyLabel)
        search.onQueryChange("uber")
        advanceUntilIdle()
        assertEquals(true, search.state.value.empty)
        assertEquals(UiCopy.En.nothingFound, search.state.value.emptyLabel)
    }

    @Test
    fun `query does not filter until debounce elapses`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "1", "1", TransactionKind.Expense, "10.00", "USDT", "Uber Trip",
                100L, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "2", "2", TransactionKind.Expense, "20.00", "USDT", "Amazon",
                200L, TransactionStatus.Success, "3"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, debounceMs = 300)
        advanceUntilIdle()
        search.onQueryChange("uber")
        dispatcher.scheduler.runCurrent()
        assertEquals("uber", search.state.value.query)
        assertEquals(listOf("2", "1"), search.state.value.rows.map { it.txnId })
        dispatcher.scheduler.advanceTimeBy(299)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("2", "1"), search.state.value.rows.map { it.txnId })
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("1"), search.state.value.rows.map { it.txnId })
    }

    @Test
    fun `source chip keeps purchases and hides topups`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "p", "p", TransactionKind.Expense, "10.00", "USDT", "Cafe",
                200L, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "t", "t", TransactionKind.Income, "20.00", "USDT", "Bybit",
                100L, TransactionStatus.Success, "3",
                source = TransactionSource.TopUp
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0)
        advanceUntilIdle()
        search.onSource(TransactionSource.Purchase)
        advanceUntilIdle()
        assertEquals(listOf("p"), search.state.value.rows.map { it.txnId })
        assertEquals(TransactionSource.Purchase, search.state.value.source)
    }

    @Test
    fun `parent category chip and amount max filter together`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "g", "g", TransactionKind.Expense, "8.00", "USDT", "Market",
                300L, TransactionStatus.Success, "3",
                categoryId = "groceries"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "d", "d", TransactionKind.Expense, "30.00", "USDT", "Uber Eats",
                200L, TransactionStatus.Success, "3",
                categoryId = "food_delivery"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "x", "x", TransactionKind.Expense, "5.00", "USDT", "Bolt",
                100L, TransactionStatus.Success, "3",
                categoryId = "taxi"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0)
        advanceUntilIdle()
        search.onCategory("food")
        search.onAmountMax("10")
        advanceUntilIdle()
        assertEquals(listOf("g"), search.state.value.rows.map { it.txnId })
        assertEquals("food", search.state.value.categoryId)
        assertEquals("10", search.state.value.amountMaxText)
    }

    @Test
    fun `seven day preset hides older rows and empty filter uses nothing found`() = runTest {
        val now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "new", "new", TransactionKind.Expense, "4.00", "USDT", "New",
                now - 2L * 24 * 60 * 60 * 1000, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "old", "old", TransactionKind.Expense, "4.00", "USDT", "Old",
                now - 20L * 24 * 60 * 60 * 1000, TransactionStatus.Success, "3"
            ),
            1L
        )
        val search = SearchViewModel(
            repo,
            zone,
            UiCopy.En,
            clock = { now },
            debounceMs = 0
        )
        advanceUntilIdle()
        search.onDatePreset(SearchDatePreset.Days7)
        advanceUntilIdle()
        assertEquals(listOf("new"), search.state.value.rows.map { it.txnId })
        search.onSource(TransactionSource.Refund)
        advanceUntilIdle()
        assertEquals(true, search.state.value.empty)
        assertEquals(UiCopy.En.nothingFound, search.state.value.emptyLabel)
    }

    @Test
    fun `found total stays USD when cashback points are in the list`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "p", "p", TransactionKind.Expense, "10.00", "USDT", "Cafe",
                200L, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "cb", "cb", TransactionKind.Expense, "80", "PTS", "Кэшбек",
                100L, TransactionStatus.Success, "cashback",
                source = TransactionSource.Cashback
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0)
        advanceUntilIdle()
        assertEquals("10.00", search.state.value.totalAmount)
        assertEquals("USD", search.state.value.totalCurrency)
        assertEquals(1, search.state.value.totalCount)
        assertEquals(listOf("p", "cb"), search.state.value.rows.map { it.txnId })
    }

    @Test
    fun `saved ninety day preset falls back to all`() = runTest {
        val saved = SavedStateHandle(mapOf("datePreset" to "Days90"))
        val search = SearchViewModel(
            InMemoryTransactionRepository(),
            zone,
            debounceMs = 0,
            savedState = saved
        )
        advanceUntilIdle()
        assertEquals(SearchDatePreset.All, search.state.value.datePreset)
    }

    @Test
    fun `rows expose display currency`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "1", "1", TransactionKind.Expense, "10.00", "USDT", "Cafe",
                100L, TransactionStatus.Success, "3"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0)
        advanceUntilIdle()
        assertEquals("USD", search.state.value.rows.single().currency)
        assertEquals("10.00", search.state.value.rows.single().amount)
    }

    @Test
    fun `custom calendar range keeps only days in the picked window and totals them`() = runTest {
        val startUtc = java.time.LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val endUtc = java.time.LocalDate.of(2026, 9, 6).atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val inside = java.time.LocalDate.of(2026, 9, 3).atTime(15, 0).atZone(zone)
            .toInstant().toEpochMilli()
        val before = java.time.LocalDate.of(2026, 8, 20).atTime(12, 0).atZone(zone)
            .toInstant().toEpochMilli()
        val after = java.time.LocalDate.of(2026, 9, 7).atTime(9, 0).atZone(zone)
            .toInstant().toEpochMilli()
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "in", "in", TransactionKind.Expense, "12.00", "USDT", "Cafe",
                inside, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "old", "old", TransactionKind.Expense, "40.00", "USDT", "Old",
                before, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "new", "new", TransactionKind.Expense, "8.00", "USDT", "New",
                after, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "refund", "refund", TransactionKind.Income, "2.00", "USDT", "Cafe",
                inside, TransactionStatus.Success, "3",
                source = TransactionSource.Refund
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, UiCopy.En, debounceMs = 0)
        advanceUntilIdle()
        search.onCustomRange(startUtc, endUtc)
        advanceUntilIdle()
        assertEquals(SearchDatePreset.Custom, search.state.value.datePreset)
        assertEquals(setOf("refund", "in"), search.state.value.rows.map { it.txnId }.toSet())
        assertEquals("10.00", search.state.value.totalAmount)
        assertEquals("USD", search.state.value.totalCurrency)
        assertEquals(2, search.state.value.totalCount)
        search.onDatePreset(SearchDatePreset.All)
        advanceUntilIdle()
        assertEquals(4, search.state.value.rows.size)
        assertEquals("58.00", search.state.value.totalAmount)
    }

    @Test
    fun `saved custom range restores bounds and results`() = runTest {
        val startUtc = java.time.LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val endUtc = java.time.LocalDate.of(2026, 9, 6).atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val inside = java.time.LocalDate.of(2026, 9, 3).atTime(15, 0).atZone(zone)
            .toInstant().toEpochMilli()
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "in", "in", TransactionKind.Expense, "12.00", "USDT", "Cafe",
                inside, TransactionStatus.Success, "3"
            ),
            1L
        )
        val saved = SavedStateHandle(
            mapOf(
                "datePreset" to "Custom",
                "dateStartUtc" to startUtc,
                "dateEndUtc" to endUtc
            )
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0, savedState = saved)
        advanceUntilIdle()
        assertEquals(SearchDatePreset.Custom, search.state.value.datePreset)
        assertEquals(startUtc, search.state.value.customStartUtc)
        assertEquals(endUtc, search.state.value.customEndUtc)
        assertEquals(listOf("in"), search.state.value.rows.map { it.txnId })
        assertEquals("12.00", search.state.value.totalAmount)
    }

    @Test
    fun `saved filters restore query source and results`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "1", "1", TransactionKind.Expense, "10.00", "USDT", "Uber Trip",
                100L, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "2", "2", TransactionKind.Expense, "20.00", "USDT", "Amazon",
                200L, TransactionStatus.Success, "3"
            ),
            1L
        )
        val saved = SavedStateHandle(
            mapOf(
                "query" to "uber",
                "source" to "Purchase"
            )
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0, savedState = saved)
        advanceUntilIdle()
        assertEquals("uber", search.state.value.query)
        assertEquals(TransactionSource.Purchase, search.state.value.source)
        assertEquals(listOf("1"), search.state.value.rows.map { it.txnId })
        search.onQueryChange("amazon")
        advanceUntilIdle()
        assertEquals("amazon", saved.get<String>("query"))
    }

    @Test
    fun `clear filters drops source category amounts and dates but keeps the query`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "1", "1", TransactionKind.Expense, "10.00", "USDT", "Uber Trip",
                100L, TransactionStatus.Success, "3"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, UiCopy.En, debounceMs = 0)
        advanceUntilIdle()
        search.onQueryChange("uber")
        search.onSource(TransactionSource.Purchase)
        search.onAmountMin("5")
        search.onDatePreset(SearchDatePreset.Days7)
        search.onUncategorizedOnly(true)
        advanceUntilIdle()
        search.clearFilters()
        advanceUntilIdle()
        assertEquals("uber", search.state.value.query)
        assertEquals(null, search.state.value.source)
        assertEquals(false, search.state.value.uncategorizedOnly)
        assertEquals("", search.state.value.amountMinText)
        assertEquals(SearchDatePreset.All, search.state.value.datePreset)
        assertEquals(listOf("1"), search.state.value.rows.map { it.txnId })
    }

    @Test
    fun `date picker range defaults to current month`() = runTest {
        val now = ZonedDateTime.of(2026, 9, 7, 4, 33, 0, 0, zone).toInstant().toEpochMilli()
        val monthStart = java.time.LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val monthEnd = java.time.LocalDate.of(2026, 9, 30).atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val search = SearchViewModel(
            InMemoryTransactionRepository(),
            zone,
            clock = { now },
            debounceMs = 0
        )
        advanceUntilIdle()
        assertEquals(monthStart to monthEnd, search.datePickerRange())
    }

    @Test
    fun `date picker range keeps existing custom bounds`() = runTest {
        val now = ZonedDateTime.of(2026, 9, 7, 4, 33, 0, 0, zone).toInstant().toEpochMilli()
        val startUtc = java.time.LocalDate.of(2026, 8, 10).atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val endUtc = java.time.LocalDate.of(2026, 8, 20).atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val search = SearchViewModel(
            InMemoryTransactionRepository(),
            zone,
            clock = { now },
            debounceMs = 0
        )
        advanceUntilIdle()
        search.onCustomRange(startUtc, endUtc)
        advanceUntilIdle()
        assertEquals(startUtc to endUtc, search.datePickerRange())
    }

    @Test
    fun `uncategorized filter keeps purchases without a category`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "bare", "bare", TransactionKind.Expense, "4.00", "USDT", "Cafe",
                300L, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "food", "food", TransactionKind.Expense, "8.00", "USDT", "Market",
                200L, TransactionStatus.Success, "3",
                categoryId = "groceries"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "top", "top", TransactionKind.Income, "20.00", "USDT", "Bybit",
                100L, TransactionStatus.Success, "3",
                source = TransactionSource.TopUp
            ),
            1L
        )
        val saved = SavedStateHandle()
        val search = SearchViewModel(repo, zone, debounceMs = 0, savedState = saved)
        advanceUntilIdle()
        search.onUncategorizedOnly(true)
        advanceUntilIdle()
        assertEquals(listOf("bare"), search.state.value.rows.map { it.txnId })
        assertEquals(true, search.state.value.uncategorizedOnly)
        assertEquals(true, saved.get<Boolean>("uncategorizedOnly"))
        search.onCategory("food")
        advanceUntilIdle()
        assertEquals(false, search.state.value.uncategorizedOnly)
        assertEquals(listOf("food"), search.state.value.rows.map { it.txnId })
    }

    @Test
    fun `tap on a purchase opens the category sheet`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "bare", "bare", TransactionKind.Expense, "4.00", "USDT", "Cafe",
                100L, TransactionStatus.Success, "3"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0)
        advanceUntilIdle()
        search.onRowTap("bare")
        advanceUntilIdle()
        assertEquals("bare", search.state.value.sheet?.txnId)
        assertEquals("Cafe", search.state.value.sheet?.merchant)
        assertEquals(null, search.state.value.sheet?.selectedCategoryId)
    }

    @Test
    fun `tap on a top-up does not open the category sheet`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "top", "top", TransactionKind.Income, "20.00", "USDT", "Bybit",
                100L, TransactionStatus.Success, "3",
                source = TransactionSource.TopUp
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, debounceMs = 0)
        advanceUntilIdle()
        search.onRowTap("top")
        advanceUntilIdle()
        assertNull(search.state.value.sheet)
    }

    @Test
    fun `picking a subcategory assigns it and drops the row from uncategorized`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "bare", "bare", TransactionKind.Expense, "4.00", "USDT", "Cafe",
                100L, TransactionStatus.Success, "3"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, UiCopy.Ru, debounceMs = 0)
        advanceUntilIdle()
        search.onUncategorizedOnly(true)
        advanceUntilIdle()
        search.onRowTap("bare")
        advanceUntilIdle()
        search.onCategoryPicked("food")
        advanceUntilIdle()
        assertEquals("food", search.state.value.sheet?.browseParentId)
        assertNull(repo.get("bare")?.categoryId)
        search.onCategoryPicked("groceries")
        advanceUntilIdle()
        assertNull(search.state.value.sheet)
        assertEquals("groceries", repo.get("bare")?.categoryId)
        assertEquals(emptyList<String>(), search.state.value.rows.map { it.txnId })
        search.onUncategorizedOnly(false)
        advanceUntilIdle()
        assertEquals("Еда · Продукты", search.state.value.rows.single().categoryLabel)
    }

    @Test
    fun `picking the parent while browsing assigns that parent`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "bare", "bare", TransactionKind.Expense, "4.00", "USDT", "Cafe",
                100L, TransactionStatus.Success, "3"
            ),
            1L
        )
        val search = SearchViewModel(repo, zone, UiCopy.Ru, debounceMs = 0)
        advanceUntilIdle()
        search.onRowTap("bare")
        advanceUntilIdle()
        search.onCategoryPicked("food")
        advanceUntilIdle()
        search.onCategoryPicked("food")
        advanceUntilIdle()
        assertNull(search.state.value.sheet)
        assertEquals("food", repo.get("bare")?.categoryId)
        assertEquals("Еда", search.state.value.rows.single().categoryLabel)
    }

    @Test
    fun `saved uncategorized filter restores purchases without a category`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "bare", "bare", TransactionKind.Expense, "4.00", "USDT", "Cafe",
                200L, TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "food", "food", TransactionKind.Expense, "8.00", "USDT", "Market",
                100L, TransactionStatus.Success, "3",
                categoryId = "groceries"
            ),
            1L
        )
        val saved = SavedStateHandle(mapOf("uncategorizedOnly" to true))
        val search = SearchViewModel(repo, zone, debounceMs = 0, savedState = saved)
        advanceUntilIdle()
        assertEquals(true, search.state.value.uncategorizedOnly)
        assertEquals(listOf("bare"), search.state.value.rows.map { it.txnId })
    }
}
