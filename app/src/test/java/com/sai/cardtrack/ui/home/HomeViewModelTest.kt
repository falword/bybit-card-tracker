package com.sai.cardtrack.ui.home

import com.sai.cardtrack.bybit.AssetRecordsPage
import com.sai.cardtrack.bybit.AssetRecordsRequest
import com.sai.cardtrack.bybit.BybitCardClient
import com.sai.cardtrack.bybit.FundingHistoryRequest
import com.sai.cardtrack.bybit.FundingPage
import com.sai.cardtrack.bybit.TransferListRequest
import com.sai.cardtrack.bybit.TransferPage
import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.data.InMemoryCredentialsStore
import com.sai.cardtrack.data.InMemoryFundBalanceStore
import com.sai.cardtrack.data.InMemoryRewardStore
import com.sai.cardtrack.data.InMemorySliceCoverageStore
import com.sai.cardtrack.data.InMemoryTransactionRepository
import com.sai.cardtrack.data.TransactionRepository
import com.sai.cardtrack.domain.BybitAssetRecord
import com.sai.cardtrack.domain.ExpenseCategories
import com.sai.cardtrack.domain.FeeBreakdown
import com.sai.cardtrack.domain.FundBalanceSnapshot
import com.sai.cardtrack.domain.QueryType
import com.sai.cardtrack.domain.RewardSnapshot
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus
import com.sai.cardtrack.sync.CardSync
import com.sai.cardtrack.ui.UiCopy
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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

    private fun emptyClient(): BybitCardClient {
        return object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            }
        }
    }

    private suspend fun savedStore(): InMemoryCredentialsStore {
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        return store
    }

    private suspend fun expenseRepo(txnId: String): InMemoryTransactionRepository {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                txnId, txnId, TransactionKind.Expense, "10.00", "USDT", "Cafe",
                ms(2026, 9, 3), TransactionStatus.Success, "3"
            ),
            1L
        )
        return repo
    }

    private fun vm(
        repo: InMemoryTransactionRepository,
        store: InMemoryCredentialsStore,
        rewardStore: InMemoryRewardStore? = null,
        savedState: SavedStateHandle? = null
    ): HomeViewModel {
        return HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(emptyClient(), repo, { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone,
            rewardStore = rewardStore,
            savedState = savedState
        )
    }

    @Test
    fun `manual refresh shows spinner immediately`() = runTest {
        val home = vm(expenseRepo("1"), savedStore())
        advanceUntilIdle()
        assertFalse(home.state.value.refreshing)
        home.refresh()
        assertTrue(home.state.value.refreshing)
    }

    @Test
    fun `manual refresh during silent sync keeps spinner until that sync ends`() = runTest {
        val release = CompletableDeferred<Unit>()
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                release.await()
                return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            }

            override suspend fun queryFundingHistory(
                credentials: Credentials,
                request: FundingHistoryRequest
            ): FundingPage {
                return FundingPage(0, "OK", emptyList(), null)
            }
        }
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "1", "1", TransactionKind.Expense, "10.00", "USDT", "Cafe",
                ms(2026, 9, 3), TransactionStatus.Success, "3"
            ),
            1L
        )
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone
        )
        dispatcher.scheduler.runCurrent()
        assertTrue(home.state.value.refreshing)
        home.refresh()
        assertTrue(home.state.value.refreshing)
        release.complete(Unit)
        advanceUntilIdle()
        assertFalse(home.state.value.refreshing)
    }

    @Test
    fun `second refresh while first is active is ignored`() = runTest {
        val release = CompletableDeferred<Unit>()
        var assetCalls = 0
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                assetCalls += 1
                release.await()
                return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            }
        }
        val repo = InMemoryTransactionRepository()
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone
        )
        home.refresh()
        dispatcher.scheduler.runCurrent()
        val duringFirst = assetCalls
        assertTrue(duringFirst >= 1)
        home.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(duringFirst, assetCalls)
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(home.state.value.refreshing)
    }

    @Test
    fun `onStart during first empty sync does not start a second walk`() = runTest {
        val release = CompletableDeferred<Unit>()
        var assetCalls = 0
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                assetCalls += 1
                if (assetCalls == 1) {
                    release.await()
                }
                return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            }

            override suspend fun queryFundingHistory(
                credentials: Credentials,
                request: FundingHistoryRequest
            ): FundingPage {
                return FundingPage(0, "OK", emptyList(), null)
            }
        }
        val repo = InMemoryTransactionRepository()
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone
        )
        dispatcher.scheduler.runCurrent()
        val duringFirst = assetCalls
        assertTrue(duringFirst >= 1)
        home.onStart()
        dispatcher.scheduler.runCurrent()
        assertEquals(duringFirst, assetCalls)
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(assetCalls > 1)
        assertTrue(assetCalls < 250)
    }

    @Test
    fun `onStart after idle does not sync or show spinner`() = runTest {
        var assetCalls = 0
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                assetCalls += 1
                return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            }

            override suspend fun queryFundingHistory(
                credentials: Credentials,
                request: FundingHistoryRequest
            ): FundingPage {
                return FundingPage(0, "OK", emptyList(), null)
            }
        }
        val repo = expenseRepo("1")
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = savedStore(),
            cardSync = CardSync(client, repo, { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone
        )
        advanceUntilIdle()
        val afterOpen = assetCalls
        assertTrue(afterOpen > 0)
        assertFalse(home.state.value.refreshing)
        home.onStart()
        dispatcher.scheduler.runCurrent()
        assertFalse(home.state.value.refreshing)
        advanceUntilIdle()
        assertEquals(afterOpen, assetCalls)
        assertFalse(home.state.value.refreshing)
    }

    @Test
    fun `opening home with saved history spins until current month is ready`() = runTest {
        val release = CompletableDeferred<Unit>()
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                release.await()
                return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            }
        }
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "1", "1", TransactionKind.Expense, "10.00", "USDT", "Cafe",
                ms(2026, 9, 3), TransactionStatus.Success, "3"
            ),
            1L
        )
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone
        )
        dispatcher.scheduler.runCurrent()
        assertTrue(home.state.value.refreshing)
        assertEquals(listOf("1"), home.state.value.rows.map { it.txnId })
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(home.state.value.refreshing)
    }

    @Test
    fun `pull refresh spinner clears after current month while previous month still loads`() = runTest {
        val clockMs = ms(2026, 9, 6)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(clockMs, zone)
        val releaseOlder = CompletableDeferred<Unit>()
        var olderAuth = false
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                val begin = request.createBeginTime ?: 0L
                if (request.type == QueryType.Auth && begin < monthBegin) {
                    olderAuth = true
                    releaseOlder.await()
                }
                return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            }

            override suspend fun queryFundingHistory(
                credentials: Credentials,
                request: FundingHistoryRequest
            ): FundingPage {
                return FundingPage(0, "OK", emptyList(), null)
            }
        }
        val repo = InMemoryTransactionRepository()
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { clockMs }, zone),
            clock = { clockMs },
            zone = zone
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(olderAuth)
        assertFalse(home.state.value.refreshing)
        assertEquals(UiCopy.Ru.lastSyncJustNow, home.state.value.lastSyncLabel)
        releaseOlder.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(home.state.value.refreshing)
    }

    @Test
    fun `refresh prompts unlock when keystore get is not authenticated`() = runTest {
        var prompted = 0
        val store = object : CredentialsStore {
            override suspend fun get(): Credentials = throw RuntimeException("unauth")
            override suspend fun save(credentials: Credentials) = Unit
            override suspend fun lastSyncAt(): Long? = null
            override suspend fun setLastSyncAt(epochMs: Long) = Unit
            override suspend fun fundingSyncedThrough(): Long? = null
            override suspend fun setFundingSyncedThrough(epochMs: Long) = Unit
            override suspend fun clear() = Unit
        }
        val repo = InMemoryTransactionRepository()
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(emptyClient(), repo, { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone,
            onUserNotAuthenticated = { prompted++ },
            userNotAuthenticated = { true }
        )
        advanceUntilIdle()
        assertEquals(1, prompted)
        assertFalse(home.state.value.refreshing)
        assertNull(home.state.value.banner)
    }

    @Test
    fun `success purchase fees and gold snapshot fill cashback subtitle`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "P", "P", TransactionKind.Expense, "10.00", "USDT", "Cafe",
                ms(2026, 9, 3), TransactionStatus.Success, "3",
                fees = FeeBreakdown(totalFees = "1.50")
            ),
            1L
        )
        val rewards = InMemoryRewardStore()
        rewards.save(
            RewardSnapshot(
                usedLimit = "10.00",
                limit = "500.00",
                unit = "1",
                tier = "GOLD",
                autoCashback = true,
                availablePoint = "5000",
                pendingPoint = "200"
            )
        )
        val home = vm(repo, savedStore(), rewards)
        advanceUntilIdle()
        assertEquals(BigDecimal("1.50"), home.state.value.totals.fees)
        val subtitle = home.state.value.cashbackSubtitle
        assertEquals("GOLD\n10 / 500 USD", subtitle)
        home.prevMonth()
        advanceUntilIdle()
        assertNull(home.state.value.cashbackSubtitle)
        home.nextMonth()
        advanceUntilIdle()
        assertEquals("GOLD\n10 / 500 USD", home.state.value.cashbackSubtitle)
    }

    @Test
    fun `usdt paid usd charge shows muted fx line with raw txn currency`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "FX", "FX", TransactionKind.Expense, "100.00", "USDT", "Shop",
                ms(2026, 9, 3), TransactionStatus.Success, "3",
                fees = FeeBreakdown(
                    transactionAmount = "100.00",
                    transactionCurrency = "USD"
                )
            ),
            1L
        )
        val home = vm(repo, savedStore())
        advanceUntilIdle()
        val fxLine = home.state.value.rows.single().fxLine
        assertTrue(fxLine!!.contains("100.00 USD"))
    }

    @Test
    fun `purchase shows muted cashback line when points match txnId`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "AIRBNB", "AIRBNB", TransactionKind.Expense, "650.00", "USDT", "Airbnb",
                ms(2026, 9, 3), TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "cb_B1", "AIRBNB", TransactionKind.Expense, "100", "PTS", "Кэшбек",
                ms(2026, 9, 4), TransactionStatus.Success, "cashback",
                source = TransactionSource.Cashback
            ),
            1L
        )
        val home = vm(repo, savedStore())
        advanceUntilIdle()
        val row = home.state.value.rows.single()
        assertEquals("кэшбек 0.20 USD", row.cashbackLine)
    }

    @Test
    fun `purchase has no cashback line without a matching points row`() = runTest {
        val home = vm(expenseRepo("P"), savedStore())
        advanceUntilIdle()
        assertNull(home.state.value.rows.single().cashbackLine)
    }

    @Test
    fun `declined row is hidden from home list`() = runTest {
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
        val home = vm(repo, savedStore())
        advanceUntilIdle()
        assertTrue(home.state.value.rows.isEmpty())
    }

    @Test
    fun `pending expense is listed and excluded from month totals`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "P", "P", TransactionKind.Expense, "40.00", "USDT", "PendingShop",
                ms(2026, 9, 2), TransactionStatus.Pending, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "S", "S", TransactionKind.Expense, "10.00", "USDT", "OkShop",
                ms(2026, 9, 3), TransactionStatus.Success, "3"
            ),
            1L
        )
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = vm(repo, store)
        advanceUntilIdle()
        assertEquals("10.00", home.state.value.totals.expenses.toPlainString())
        assertEquals(2, home.state.value.rows.size)
        assertTrue(home.state.value.rows.any { it.txnId == "P" && it.pending })
    }

    @Test
    fun `month chevron filters by local calendar month`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "SEP", "SEP", TransactionKind.Expense, "1.00", "USDT", "Sep",
                ms(2026, 9, 1), TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "AUG", "AUG", TransactionKind.Expense, "9.00", "USDT", "Aug",
                ms(2026, 8, 31), TransactionStatus.Success, "3"
            ),
            1L
        )
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = vm(repo, store)
        advanceUntilIdle()
        assertEquals(1, home.state.value.rows.size)
        home.prevMonth()
        advanceUntilIdle()
        assertEquals("AUG", home.state.value.rows.single().txnId)
        assertEquals("9.00", home.state.value.totals.expenses.toPlainString())
    }

    @Test
    fun `rows carry today and calendar day labels`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "TODAY", "TODAY", TransactionKind.Expense, "2.00", "USDT", "Now",
                ms(2026, 9, 6), TransactionStatus.Success, "3"
            ),
            1L
        )
        repo.upsertFromSync(
            TransactionDraft(
                "OLD", "OLD", TransactionKind.Expense, "3.00", "USDT", "Then",
                ms(2026, 9, 3), TransactionStatus.Success, "3"
            ),
            1L
        )
        val home = vm(repo, savedStore())
        advanceUntilIdle()
        val today = home.state.value.rows.single { it.txnId == "TODAY" }
        val older = home.state.value.rows.single { it.txnId == "OLD" }
        assertEquals("Сегодня", today.dayLabel)
        assertEquals("2026-09-06", today.dayKey)
        assertEquals("3 сентября", older.dayLabel)
        assertEquals("2026-09-03", older.dayKey)
    }

    @Test
    fun `picking a parent with children opens subcategories`() = runTest {
        val repo = expenseRepo("E")
        val home = vm(repo, savedStore())
        advanceUntilIdle()
        home.onRowTap("E")
        advanceUntilIdle()
        home.onCategoryPicked("food")
        advanceUntilIdle()
        assertEquals("food", home.state.value.sheet?.browseParentId)
        assertEquals("E", home.state.value.sheet?.txnId)
        assertNull(repo.get("E")?.categoryId)
    }

    @Test
    fun `picking a subcategory assigns it and closes sheet`() = runTest {
        val repo = expenseRepo("E")
        val home = vm(repo, savedStore())
        advanceUntilIdle()
        home.onRowTap("E")
        advanceUntilIdle()
        home.onCategoryPicked("food")
        advanceUntilIdle()
        home.onCategoryPicked("groceries")
        advanceUntilIdle()
        assertNull(home.state.value.sheet)
        assertEquals("groceries", repo.get("E")?.categoryId)
        assertEquals("Еда · Продукты", home.state.value.rows.single().categoryLabel)
    }

    @Test
    fun `picking the parent while browsing assigns that parent`() = runTest {
        val repo = expenseRepo("E")
        val home = vm(repo, savedStore())
        advanceUntilIdle()
        home.onRowTap("E")
        advanceUntilIdle()
        home.onCategoryPicked("food")
        advanceUntilIdle()
        home.onCategoryPicked("food")
        advanceUntilIdle()
        assertNull(home.state.value.sheet)
        assertEquals("food", repo.get("E")?.categoryId)
        assertEquals("Еда", home.state.value.rows.single().categoryLabel)
    }

    @Test
    fun `back from subcategories returns to parents`() = runTest {
        val repo = expenseRepo("E")
        val home = vm(repo, savedStore())
        advanceUntilIdle()
        home.onRowTap("E")
        advanceUntilIdle()
        home.onCategoryPicked("food")
        advanceUntilIdle()
        home.onCategoryBack()
        advanceUntilIdle()
        assertNull(home.state.value.sheet?.browseParentId)
        assertEquals("E", home.state.value.sheet?.txnId)
    }

    @Test
    fun `assigning category does not open sheet for income`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "RF", "RF", TransactionKind.Income, "5.00", "USDT", "Пополнение",
                ms(2026, 9, 4), TransactionStatus.Success, "topup",
                TransactionSource.TopUp
            ),
            1L
        )
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = vm(repo, store)
        advanceUntilIdle()
        home.onRowTap("RF")
        advanceUntilIdle()
        assertNull(home.state.value.sheet)
        assertEquals(ExpenseCategories.TOPUP_LABEL, home.state.value.rows.single().categoryLabel)
    }

    @Test
    fun `stored last sync is shown when credentials are missing`() = runTest {
        val repo = InMemoryTransactionRepository()
        val store = InMemoryCredentialsStore()
        store.setLastSyncAt(ms(2026, 9, 6))
        val home = vm(repo, store)
        advanceUntilIdle()
        assertEquals("Обновлено в 12:00", home.state.value.lastSyncLabel)
    }

    @Test
    fun `successful refresh sets last sync to updated just now`() = runTest {
        val repo = InMemoryTransactionRepository()
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = vm(repo, store)
        advanceUntilIdle()
        assertEquals("Обновлено только что", home.state.value.lastSyncLabel)
        assertEquals(ms(2026, 9, 6), store.lastSyncAt())
    }

    @Test
    fun `partial refresh does not write last sync`() = runTest {
        var written: Long? = null
        val inner = InMemoryCredentialsStore()
        inner.save(Credentials("k", "s"))
        val store = object : CredentialsStore {
            override suspend fun get(): Credentials? = inner.get()
            override suspend fun save(credentials: Credentials) = inner.save(credentials)
            override suspend fun lastSyncAt(): Long? = inner.lastSyncAt()
            override suspend fun setLastSyncAt(epochMs: Long) {
                written = epochMs
                inner.setLastSyncAt(epochMs)
            }
            override suspend fun fundingSyncedThrough(): Long? = inner.fundingSyncedThrough()
            override suspend fun setFundingSyncedThrough(epochMs: Long) {
                inner.setFundingSyncedThrough(epochMs)
            }
            override suspend fun clear() = inner.clear()
        }
        val repo = InMemoryTransactionRepository()
        val clockMs = ms(2026, 9, 6)
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                return if (request.type == QueryType.Auth) {
                    AssetRecordsPage(
                        0,
                        "OK",
                        listOf(
                            BybitAssetRecord(
                                "A1", "A1", "3", "10.00", "USDT", "Shop",
                                clockMs, "1", "1"
                            )
                        ),
                        1,
                        1
                    )
                } else {
                    AssetRecordsPage(0, "OK", emptyList(), 1, 0)
                }
            }

            override suspend fun queryFundingHistory(
                credentials: Credentials,
                request: FundingHistoryRequest
            ): FundingPage {
                return FundingPage(10005, "permission denied", emptyList(), null)
            }

            override suspend fun queryInterTransfers(
                credentials: Credentials,
                request: TransferListRequest
            ): TransferPage {
                return TransferPage(10005, "permission denied", emptyList(), null)
            }
        }
        HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { clockMs }, zone),
            clock = { clockMs },
            zone = zone
        )
        advanceUntilIdle()
        assertEquals(null, written)
    }

    @Test
    fun `auth failure shows bybit retMsg on banner`() = runTest {
        val home = homeWithClient { _, _ ->
            AssetRecordsPage(10004, "error sign!", emptyList(), 1, 0)
        }
        advanceUntilIdle()
        assertEquals(
            "Подпись не сошлась. Проверь Secret и что копируешь его целиком, без пробелов.",
            home.state.value.banner
        )
        assertFalse(home.state.value.refreshing)
    }

    @Test
    fun `auth failure with blank retMsg uses fallback banner`() = runTest {
        val home = homeWithClient { _, _ ->
            AssetRecordsPage(10004, "  ", emptyList(), 1, 0)
        }
        advanceUntilIdle()
        assertEquals(
            "Подпись не сошлась. Проверь Secret и что копируешь его целиком, без пробелов.",
            home.state.value.banner
        )
    }

    @Test
    fun `refreshing is cleared when sync throws`() = runTest {
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val repo = ThrowingRepo()
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage {
                        return AssetRecordsPage(
                            0,
                            "OK",
                            listOf(
                                BybitAssetRecord(
                                    "X", "X", "3", "1.00", "USDT", "Shop",
                                    ms(2026, 9, 6), "1", "1"
                                )
                            ),
                            1,
                            1
                        )
                    }
                },
                repo,
                { ms(2026, 9, 6) },
                zone
            ),
            clock = { ms(2026, 9, 6) },
            zone = zone
        )
        advanceUntilIdle()
        assertFalse(home.state.value.refreshing)
    }

    private suspend fun homeWithClient(
        block: suspend (Credentials, AssetRecordsRequest) -> AssetRecordsPage
    ): HomeViewModel {
        val repo = InMemoryTransactionRepository()
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage = block(credentials, request)
        }
        return HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone
        )
    }

    @Test
    fun `p2p sell is hidden until setting on`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "fh_s",
                orderNo = "s",
                kind = TransactionKind.Expense,
                paidAmount = "40.00",
                paidCurrency = "USD",
                merchantName = "P2P",
                txnCreate = ms(2026, 9, 2),
                status = TransactionStatus.Success,
                bybitSide = "p2p",
                source = TransactionSource.P2P
            ),
            1L
        )
        val store = savedStore()
        val home = vm(repo, store)
        advanceUntilIdle()
        assertTrue(home.state.value.rows.none { it.txnId == "fh_s" })
        assertEquals(BigDecimal("0.00"), home.state.value.totals.expenses)
        store.setP2pAccountingEnabled(true)
        home.reloadLocal()
        advanceUntilIdle()
        assertEquals("P2P", home.state.value.rows.single { it.txnId == "fh_s" }.merchant)
        assertEquals(BigDecimal("40.00"), home.state.value.totals.expenses)
    }

    @Test
    fun `account balance from snapshot sits on state`() = runTest {
        val fund = InMemoryFundBalanceStore()
        fund.save(FundBalanceSnapshot("13.00", "USD", 1L))
        val home = HomeViewModel(
            repository = InMemoryTransactionRepository(),
            credentialsStore = savedStore(),
            cardSync = CardSync(emptyClient(), InMemoryTransactionRepository(), { ms(2026, 9, 6) }, zone),
            clock = { ms(2026, 9, 6) },
            zone = zone,
            fundBalanceStore = fund
        )
        advanceUntilIdle()
        assertEquals("13.00 USD", home.state.value.accountBalance)
    }

    @Test
    fun `saved month is restored and prev month is persisted`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "AUG", "AUG", TransactionKind.Expense, "9.00", "USDT", "Aug",
                ms(2026, 8, 31), TransactionStatus.Success, "3"
            ),
            1L
        )
        val saved = SavedStateHandle(mapOf("month" to "2026-08"))
        val home = vm(repo, savedStore(), savedState = saved)
        advanceUntilIdle()
        assertEquals("AUG", home.state.value.rows.single().txnId)
        home.prevMonth()
        advanceUntilIdle()
        assertEquals("2026-07", saved.get<String>("month"))
    }

    @Test
    fun `manual refresh ignores closed slice coverage`() = runTest {
        val coverage = InMemorySliceCoverageStore()
        var fundingCalls = 0
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            }

            override suspend fun queryFundingHistory(
                credentials: Credentials,
                request: FundingHistoryRequest
            ): FundingPage {
                fundingCalls += 1
                return FundingPage(0, "OK", emptyList(), null)
            }
        }
        val repo = expenseRepo("1")
        val store = savedStore()
        val home = HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { ms(2026, 9, 6) }, zone, coverage = coverage),
            clock = { ms(2026, 9, 6) },
            zone = zone
        )
        advanceUntilIdle()
        val afterInit = fundingCalls
        assertTrue(afterInit > 1)
        home.refresh(showSpinner = false, forceNetwork = false)
        advanceUntilIdle()
        val afterAuto = fundingCalls
        assertTrue(afterAuto - afterInit < afterInit)
        home.refresh(showSpinner = false, forceNetwork = true)
        advanceUntilIdle()
        assertEquals(afterInit, fundingCalls - afterAuto)
    }

    @Test
    fun `earn column shows when rows exist without checkpoint`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "ey_1", "1", TransactionKind.Income, "1.50", "USD", "Earn",
                ms(2026, 9, 4), TransactionStatus.Success, "earn",
                TransactionSource.Earn
            ),
            1L
        )
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        val home = vm(repo, store)
        advanceUntilIdle()
        assertTrue(home.state.value.showEarn)
        assertEquals(BigDecimal("1.50"), home.state.value.totals.earnYield)
    }

    @Test
    fun `earn column shows zero when covered and no payouts`() = runTest {
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        store.setEarnSyncedThrough(ms(2026, 9, 6))
        val home = vm(InMemoryTransactionRepository(), store)
        advanceUntilIdle()
        assertTrue(home.state.value.showEarn)
        assertEquals(BigDecimal("0.00"), home.state.value.totals.earnYield)
    }

    @Test
    fun `earn column sums usdt yield for selected month`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                "ey_1", "1", TransactionKind.Income, "1.50", "USD", "Earn",
                ms(2026, 9, 4), TransactionStatus.Success, "earn",
                TransactionSource.Earn
            ),
            1L
        )
        val store = InMemoryCredentialsStore()
        store.save(Credentials("k", "s"))
        store.setEarnSyncedThrough(ms(2026, 9, 6))
        val home = vm(repo, store)
        advanceUntilIdle()
        assertTrue(home.state.value.showEarn)
        assertEquals(BigDecimal("1.50"), home.state.value.totals.earnYield)
        assertTrue(home.state.value.rows.none { it.txnId.startsWith("ey_") })
    }

    private class ThrowingRepo : TransactionRepository {
        override suspend fun upsertFromSync(draft: TransactionDraft, syncedAt: Long) {
            throw IllegalStateException("db down")
        }

        override suspend fun setCategory(txnId: String, categoryId: String) = Unit

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override suspend fun get(txnId: String): Transaction? = null

        override suspend fun isEmpty(): Boolean = true

        override suspend fun clear() = Unit
    }
}
