package com.sai.cardtrack.ui.export

import com.sai.cardtrack.data.InMemoryCredentialsStore
import com.sai.cardtrack.data.InMemoryTransactionRepository
import com.sai.cardtrack.domain.CsvExport
import com.sai.cardtrack.domain.FeeBreakdown
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus
import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setting off omits p2p source line from csv`() = runTest {
        val repo = InMemoryTransactionRepository()
        val at = Instant.parse("2026-03-04T15:16:17Z").toEpochMilli()
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "fh_s",
                orderNo = "s",
                kind = TransactionKind.Expense,
                paidAmount = "40.00",
                paidCurrency = "USD",
                merchantName = "P2P",
                txnCreate = at,
                status = TransactionStatus.Success,
                bybitSide = "p2p",
                source = TransactionSource.P2P
            ),
            syncedAt = 1L
        )
        val store = InMemoryCredentialsStore()
        val export = ExportViewModel(repo, credentialsStore = store)
        val csv = export.exportCsv()
        assertFalse(csv.lines().any { it.split(",").getOrNull(3) == "P2P" })
        store.setP2pAccountingEnabled(true)
        val on = export.exportCsv()
        assertTrue(on.lines().any { it.split(",").getOrNull(3) == "P2P" })
        assertTrue(on.contains("fh_s"))
    }

    @Test
    fun `exportCsv and exportKoinly read observeAll once`() = runTest {
        val repo = InMemoryTransactionRepository()
        val at = Instant.parse("2026-03-04T15:16:17Z").toEpochMilli()
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "txn-buy",
                orderNo = "ord-1",
                kind = TransactionKind.Expense,
                paidAmount = "10.00",
                paidCurrency = "USDT",
                merchantName = "Store, Inc",
                txnCreate = at,
                status = TransactionStatus.Success,
                bybitSide = "3",
                source = TransactionSource.Purchase,
                categoryId = "groceries",
                mccCode = "5411",
                fees = FeeBreakdown(totalFees = "1.50")
            ),
            syncedAt = 1L
        )
        val export = ExportViewModel(repo)
        val rows = repo.observeAll().first()
        assertEquals(CsvExport.table(rows), export.exportCsv())
        assertEquals(CsvExport.koinly(rows), export.exportKoinly())
    }

    @Test
    fun `exportCsv uses copy locale for category`() = runTest {
        val repo = InMemoryTransactionRepository()
        val at = Instant.parse("2026-03-04T15:16:17Z").toEpochMilli()
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "txn-buy",
                orderNo = "ord-1",
                kind = TransactionKind.Expense,
                paidAmount = "10.00",
                paidCurrency = "USDT",
                merchantName = "Store",
                txnCreate = at,
                status = TransactionStatus.Success,
                bybitSide = "3",
                source = TransactionSource.Purchase,
                categoryId = "groceries"
            ),
            syncedAt = 1L
        )
        val export = ExportViewModel(repo, UiCopy.En)
        val csv = export.exportCsv()
        assertTrue(csv.contains("Food · Groceries"))
    }
}
