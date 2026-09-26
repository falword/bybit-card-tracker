package com.sai.cardtrack.ui.declines

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
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DeclinesViewModelTest {

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

    private fun ms(year: Int, month: Int, day: Int): Long {
        return ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    }

    @Test
    fun `declined row is listed with reason`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "D", "D", TransactionKind.Expense, "12.00", "USDT", "Blocked",
                ms(2026, 9, 3), TransactionStatus.Declined, "3",
                source = TransactionSource.Declined,
                declinedReason = "insufficient"
            ),
            1L
        )
        val declines = DeclinesViewModel(repo, clock = { ms(2026, 9, 6) }, zone = zone)
        advanceUntilIdle()
        assertFalse(declines.state.value.empty)
        assertEquals(1, declines.state.value.rows.size)
        assertEquals("Blocked", declines.state.value.rows.single().merchant)
        assertEquals("insufficient", declines.state.value.rows.single().reason)
    }

    @Test
    fun `saved month is restored`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "D", "D", TransactionKind.Expense, "12.00", "USDT", "Blocked",
                ms(2026, 8, 3), TransactionStatus.Declined, "3",
                source = TransactionSource.Declined,
                declinedReason = "insufficient"
            ),
            1L
        )
        val saved = SavedStateHandle(mapOf("month" to "2026-08"))
        val declines = DeclinesViewModel(
            repo,
            clock = { ms(2026, 9, 6) },
            zone = zone,
            savedState = saved
        )
        advanceUntilIdle()
        assertEquals("Blocked", declines.state.value.rows.single().merchant)
        declines.prevMonth()
        advanceUntilIdle()
        assertEquals("2026-07", saved.get<String>("month"))
    }
}
