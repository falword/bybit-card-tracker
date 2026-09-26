package com.sai.cardtrack.ui.subscriptions

import com.sai.cardtrack.data.InMemoryTransactionRepository
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {

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

    private fun ms(day: Int): Long {
        return LocalDate.of(2026, 9, 1)
            .plusDays((day - 1).toLong())
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private suspend fun InMemoryTransactionRepository.purchase(
        id: String,
        merchant: String,
        amount: String,
        day: Int
    ) {
        upsertFromSync(
            TransactionDraft(
                id, id, TransactionKind.Expense, amount, "USD", merchant,
                ms(day), TransactionStatus.Success, "3"
            ),
            1L
        )
    }

    @Test
    fun `two uber charges 30 days apart are listed as a subscription`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.purchase("1", "Uber", "9.99", day = 1)
        repo.purchase("2", "Uber", "9.99", day = 31)
        val subscriptions = SubscriptionsViewModel(repo, zone = zone, clock = { ms(50) })
        advanceUntilIdle()
        assertFalse(subscriptions.state.value.empty)
        assertEquals(1, subscriptions.state.value.rows.size)
        val row = subscriptions.state.value.rows.single()
        assertEquals("Uber", row.merchant)
        assertEquals("9.99", row.amount)
        val nextMs = ms(31) + (ms(31) - ms(1))
        val date = Instant.ofEpochMilli(nextMs).atZone(zone).format(
            DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
        )
        assertEquals(date, row.nextOn)
    }

    @Test
    fun `classified streaming charge is listed beside a recurring series`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.purchase("3", "Spotify", "12.50", day = 4)
        repo.setCategory("3", "streaming")
        val subscriptions = SubscriptionsViewModel(repo, zone = zone, clock = { ms(10) })
        advanceUntilIdle()
        assertEquals(listOf("Spotify"), subscriptions.state.value.rows.map { it.merchant })
        val spotify = subscriptions.state.value.rows.single()
        assertEquals("12.50", spotify.amount)
        val date = Instant.ofEpochMilli(ms(4)).atZone(zone).format(
            DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
        )
        assertEquals(date, spotify.nextOn)
        assertEquals("12.50", subscriptions.state.value.totalLabel)
        assertEquals("USD", subscriptions.state.value.currency)
        assertEquals("100% от расходов", subscriptions.state.value.shareLabel)
    }

    @Test
    fun `ten day gap is empty`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.purchase("1", "Uber", "9.99", day = 1)
        repo.purchase("2", "Uber", "9.99", day = 11)
        val subscriptions = SubscriptionsViewModel(repo, zone = zone, clock = { ms(10) })
        advanceUntilIdle()
        assertTrue(subscriptions.state.value.empty)
        assertEquals(emptyList<SubscriptionRow>(), subscriptions.state.value.rows)
    }
}
