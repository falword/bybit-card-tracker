package com.sai.cardtrack.sync

import com.sai.cardtrack.bybit.AssetOverviewPage
import com.sai.cardtrack.bybit.AssetRecordsPage
import com.sai.cardtrack.bybit.AssetRecordsRequest
import com.sai.cardtrack.bybit.BybitCardClient
import com.sai.cardtrack.bybit.CoinsBalancePage
import com.sai.cardtrack.bybit.EarnYieldPage
import com.sai.cardtrack.bybit.EarnYieldRequest
import com.sai.cardtrack.bybit.PointRecordsPage
import com.sai.cardtrack.bybit.PointRecordsRequest
import com.sai.cardtrack.bybit.FundingHistoryRequest
import com.sai.cardtrack.bybit.FundingPage
import com.sai.cardtrack.bybit.PointsBalancePage
import com.sai.cardtrack.bybit.PointsTierPage
import com.sai.cardtrack.bybit.TransferListRequest
import com.sai.cardtrack.bybit.TransferPage
import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.data.InMemoryCredentialsStore
import com.sai.cardtrack.data.InMemoryFundBalanceStore
import com.sai.cardtrack.data.InMemoryRewardStore
import com.sai.cardtrack.data.InMemorySliceCoverageStore
import com.sai.cardtrack.data.InMemoryTransactionRepository
import com.sai.cardtrack.domain.BybitAssetRecord
import com.sai.cardtrack.domain.BybitEarnYieldRecord
import com.sai.cardtrack.domain.BybitFundingRecord
import com.sai.cardtrack.domain.BybitPointRecord
import com.sai.cardtrack.domain.BybitTransfer
import com.sai.cardtrack.domain.CategoryOrigin
import com.sai.cardtrack.domain.FundBalanceSnapshot
import com.sai.cardtrack.domain.MonthMath
import com.sai.cardtrack.domain.QueryType
import com.sai.cardtrack.domain.RewardSnapshot
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

class CardSyncTest {

    private val zone: ZoneId = ZoneId.of("Asia/Bangkok")
    private val now: Long = 1_757_116_800_000L // 2025-09-06 approx; clock is injected
    private val creds = Credentials("k", "s")

    private suspend fun seededRepo(): InMemoryTransactionRepository {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "KEEP",
                orderNo = "KEEP",
                kind = TransactionKind.Expense,
                paidAmount = "1.00",
                paidCurrency = "USDT",
                merchantName = "Old",
                txnCreate = now - 10L * 24 * 60 * 60 * 1000,
                status = TransactionStatus.Success,
                bybitSide = "3"
            ),
            syncedAt = 1L
        )
        return repo
    }

    private fun rec(
        id: String,
        side: String,
        status: String = "1",
        tradeStatus: String = "1",
        declinedReason: String = "",
        mccCode: String = ""
    ): BybitAssetRecord {
        return BybitAssetRecord(
            txnId = id,
            orderNo = id,
            side = side,
            paidAmount = "10.00",
            paidCurrency = "USDT",
            merchName = "Shop",
            txnCreate = now,
            status = status,
            tradeStatus = tradeStatus,
            declinedReason = declinedReason,
            mccCode = mccCode
        )
    }

    @Test
    fun `declined auth is persisted`() = runTest {
        val client = ScriptedClient(
            pages = listOf(
                page(
                    QueryType.Auth,
                    rec("D", "3", status = "2", tradeStatus = "2", declinedReason = "MCC not allowed")
                ),
                page(QueryType.Refund)
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        val rows = repo.observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(TransactionSource.Declined, rows.single().source)
        assertEquals("MCC not allowed", rows.single().declinedReason)
    }

    @Test
    fun `grocery MCC is stored`() = runTest {
        val client = ScriptedClient(
            pages = listOf(
                page(QueryType.Auth, rec("G", "3", mccCode = "5411")),
                page(QueryType.Refund)
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        val row = repo.get("G")
        assertEquals("5411", row?.mccCode)
        assertEquals("groceries", row?.categoryId)
        assertEquals(CategoryOrigin.Mcc, row?.categoryOrigin)
    }

    @Test
    fun `failed tier leaves sync successful and keeps prior snapshot`() = runTest {
        val rewards = InMemoryRewardStore()
        rewards.save(RewardSnapshot("1", "2", "USD", "SILVER", false, "1", "0"))
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            tier = PointsTierPage(10005, "denied"),
            balance = PointsBalancePage(0, "OK", "9", "0")
        )
        val result = CardSync(client, InMemoryTransactionRepository(), { now }, zone, rewardStore = rewards).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertEquals("SILVER", rewards.get()?.tier)
    }

    @Test
    fun `successful tier and balance save GOLD snapshot`() = runTest {
        val rewards = InMemoryRewardStore()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund))
        )
        val result = CardSync(client, InMemoryTransactionRepository(), { now }, zone, rewardStore = rewards).sync(creds)
        assertEquals(SyncResult.Success, result)
        val snap = rewards.get()
        assertEquals("GOLD", snap?.tier)
        assertEquals("5000", snap?.availablePoint)
    }

    @Test
    fun `authorization reversal is not listed`() = runTest {
        val client = ScriptedClient(
            pages = listOf(
                page(QueryType.Auth, rec("R", "2")),
                page(QueryType.Refund)
            )
        )
        val repo = InMemoryTransactionRepository()
        CardSync(client, repo, { now }, zone).sync(creds)
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun `refund is stored as refund source not income`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth)),
            refunds = listOf(page(QueryType.Refund, rec("RF", "5")))
        )
        val repo = InMemoryTransactionRepository()
        CardSync(client, repo, { now }, zone).sync(creds)
        val row = repo.get("RF")
        assertEquals(TransactionSource.Refund, row?.source)
        assertEquals(TransactionStatus.Success, row?.status)
        assertEquals(0, repo.observeAll().first().count { it.source == TransactionSource.TopUp })
    }

    @Test
    fun `walks pages until empty and also queries financial`() = runTest {
        val client = ScriptedClient(
            pages = listOf(
                page(QueryType.Auth, rec("A1", "3"), pageNo = 1, total = 0),
                page(QueryType.Auth, rec("A2", "3"), pageNo = 2, total = 0),
                page(QueryType.Auth),
                page(QueryType.Refund)
            )
        )
        val repo = InMemoryTransactionRepository()
        CardSync(client, repo, { now }, zone, pageSize = 1).sync(creds)
        assertEquals(2, repo.observeAll().first().size)
        assertTrue(client.requestedTypes.contains(QueryType.Financial))
        assertTrue(client.requestedTypes.contains(QueryType.Refund))
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val currentAuth = client.requests.filter {
            it.type == QueryType.Auth && it.createBeginTime == monthBegin
        }
        assertEquals(3, currentAuth.size)
    }

    @Test
    fun `network on first page is failed and keeps old rows`() = runTest {
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            com.sai.cardtrack.domain.TransactionDraft(
                txnId = "OLD",
                orderNo = "OLD",
                kind = TransactionKind.Expense,
                paidAmount = "1",
                paidCurrency = "USDT",
                merchantName = "Old",
                txnCreate = now,
                status = TransactionStatus.Success,
                bybitSide = "3"
            ),
            syncedAt = 1L
        )
        val client = ScriptedClient(failFirst = IOException("offline"))
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(
            SyncResult.Failed(
                SyncError.Network,
                "Нет соединения с Bybit. Проверь интернет и что приложению разрешена сеть."
            ),
            result
        )
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `failure after page 1 is partial and keeps saved pages`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A1", "3"), pageNo = 1, total = 200)),
            failOnCall = 2
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone, pageSize = 1).sync(creds)
        assertEquals(
            SyncResult.Partial(
                SyncError.Network,
                "Нет соединения с Bybit. Проверь интернет и что приложению разрешена сеть."
            ),
            result
        )
        assertEquals("A1", repo.get("A1")?.txnId)
    }

    @Test
    fun `refund param illegal falls back to financial query`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth)),
            refunds = listOf(AssetRecordsPage(10001, "param_illegal", emptyList(), 1, 0)),
            financial = listOf(page(QueryType.Financial, rec("RF", "5")))
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertEquals(TransactionSource.Refund, repo.get("RF")?.source)
        assertTrue(client.requestedTypes.contains(QueryType.Financial))
        val financialBegins = client.requests.filter { it.type == QueryType.Financial }.map { it.createBeginTime }
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        assertEquals(monthBegin, financialBegins.first())
        assertTrue(financialBegins.contains(historyBegin))
    }

    @Test
    fun `financial query keeps refunds and clearing expenses`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A1", "3"))),
            financial = listOf(page(QueryType.Financial, rec("A1", "3"), rec("RF", "5")))
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertEquals(TransactionKind.Expense, repo.get("A1")?.kind)
        assertEquals(TransactionSource.Purchase, repo.get("A1")?.source)
        assertEquals(TransactionSource.Refund, repo.get("RF")?.source)
    }

    @Test
    fun `refund and untyped param illegal still succeeds after auth`() = runTest {
        val illegal = AssetRecordsPage(10001, "param_illegal", emptyList(), 1, 0)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A1", "3"))),
            refunds = listOf(illegal)
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertEquals("A1", repo.get("A1")?.txnId)
        assertTrue(repo.get("A1")?.kind == TransactionKind.Expense)
    }

    @Test
    fun `param illegal on refund splits window and keeps times`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth)),
            refunds = listOf(
                AssetRecordsPage(10001, "param_illegal", emptyList(), 1, 0),
                page(QueryType.Refund, rec("RF", "5"))
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertEquals("RF", repo.get("RF")?.txnId)
        assertRefundSplitKeptTimes(client)
    }

    @Test
    fun `card 181010 on refund splits window and keeps times`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth)),
            refunds = listOf(
                AssetRecordsPage(
                    181010,
                    "The time range between startTime and endTime cannot exceed 7 days",
                    emptyList(),
                    1,
                    0
                ),
                page(QueryType.Refund, rec("RF", "5"))
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertEquals("RF", repo.get("RF")?.txnId)
        assertRefundSplitKeptTimes(client)
    }

    @Test
    fun `rate limit retCode maps to rate limit`() = runTest {
        val limited = AssetRecordsPage(10006, "too many", emptyList(), 1, 0)
        val client = ScriptedClient(pages = listOf(limited, limited, limited, limited))
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Failed(SyncError.RateLimit, "too many", 10006), result)
    }

    @Test
    fun `rate limit is retried then succeeds`() = runTest {
        val client = ScriptedClient(
            pages = listOf(
                AssetRecordsPage(10006, "Too many visits!", emptyList(), 1, 0),
                page(QueryType.Auth),
                page(QueryType.Refund)
            )
        )
        val result = CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertTrue(client.requestedTypes.contains(QueryType.Financial))
        assertTrue(client.requests.count { it.type == QueryType.Auth } >= 3)
        assertTrue(client.requests.count { it.type == QueryType.Refund } >= 2)
    }

    @Test
    fun `financial clearing purchase with new txnId updates auth row of same orderNo`() = runTest {
        val client = ScriptedClient(
            pages = listOf(
                page(
                    QueryType.Auth,
                    rec("AUTH1", "3").copy(orderNo = "ORD9", paidAmount = "640.00")
                ),
                page(QueryType.Refund)
            ),
            financial = listOf(
                page(
                    QueryType.Financial,
                    rec("FIN1", "3").copy(
                        orderNo = "ORD9",
                        merchName = "AIRBNB",
                        paidAmount = "650.00"
                    )
                )
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertEquals(1, repo.observeAll().first().size)
        val row = repo.get("AUTH1")
        assertEquals(TransactionSource.Purchase, row?.source)
        assertEquals("650.00", row?.paidAmount)
        assertEquals("AIRBNB", row?.merchantName)
        assertEquals(null, repo.get("FIN1"))
    }

    @Test
    fun `financial clearing purchase is saved when auth omitted it`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: type SIDE_QUERY_FINANCIAL is clearing
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            financial = listOf(
                page(
                    QueryType.Financial,
                    rec("AIRBNB", "3").copy(merchName = "AIRBNB", paidAmount = "650.00")
                )
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        val row = repo.get("AIRBNB")
        assertEquals(TransactionSource.Purchase, row?.source)
        assertEquals("650.00", row?.paidAmount)
        assertEquals("AIRBNB", row?.merchantName)
        assertTrue(client.requestedTypes.contains(QueryType.Financial))
    }

    @Test
    fun `short card page still walks when totalCount remains`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: page + limit [1, 500]; totalCount
        val client = ShortAuthPagesClient(
            pages = mapOf(
                1 to page(QueryType.Auth, rec("A1", "3"), rec("A2", "3"), pageNo = 1, total = 3),
                2 to page(QueryType.Auth, rec("A3", "3"), pageNo = 2, total = 3)
            )
        )
        val repo = InMemoryTransactionRepository()
        CardSync(client, repo, { now }, zone, pageSize = 500).sync(creds)
        assertEquals("A3", repo.get("A3")?.txnId)
        assertEquals(3, repo.observeAll().first().size)
    }

    @Test
    fun `card pagination counts actual rows when live page is smaller than limit`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: limit default 100, range [1, 500];
        // response pageSize is the page that was served, not the requested limit.
        val client = CappedAuthPagesClient(actualPageSize = 100, totalCount = 250)
        val repo = InMemoryTransactionRepository()
        CardSync(client, repo, { now }, zone, pageSize = 500).sync(creds)
        assertEquals(250, repo.observeAll().first().size)
        assertTrue(client.authPages.contains(3))
        assertFalse(client.authPages.contains(4))
    }

    @Test
    fun `later seven day card slices still run after a rate limited window`() = runTest {
        // official-v5/error.mdx: 10006 too many visits. One 7-day slice must not drop the month.
        val client = RateLimitedAugustSliceClient()
        CardSync(
            client,
            InMemoryTransactionRepository(),
            { now },
            zone,
            rateLimitRetries = 0
        ).sync(creds)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val augustAuth = client.authWindows.filter { (from, to) ->
            from >= prevBegin && to <= monthBegin
        }
        val expected = com.sai.cardtrack.domain.MonthMath.boundedWindows(prevBegin, monthBegin)
        assertEquals(expected, augustAuth)
        assertTrue(augustAuth.size > 2)
    }

    @Test
    fun `stale card checkpoint still walks twelve month history`() = runTest {
        // Coverage written before month-sized card windows under-fetched July and older.
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        store.setCardSyncedThrough(since)
        store.setFinancialSyncedThrough(since)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        assertTwelveMonthCardWalksCurrentThenOlder(client)
    }

    @Test
    fun `older card months still run after points rate limit`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            points = listOf(
                PointRecordsPage(0, "OK", emptyList(), 1, 0),
                PointRecordsPage(10006, "Too many visits!", emptyList(), 1, 0)
            ),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone, rateLimitRetries = 0)
            .sync(creds)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        assertTrue(
            client.requests.any { request ->
                request.type == QueryType.Auth && (request.createBeginTime ?: 0L) < prevBegin
            }
        )
    }

    @Test
    fun `seeded repo without financial checkpoint still uses card incremental`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: createBeginTime/createEndTime Unix ms
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        store.setCardSyncedThrough(since)
        store.setCardHistoryGeneration(CardSync.CARD_HISTORY_GENERATION)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val authRequests = client.requests.filter { it.type == QueryType.Auth }
        assertTrue(authRequests.isNotEmpty())
        authRequests.forEach { request ->
            assertTrue((request.createBeginTime ?: 0L) >= prevBegin)
            assertTrue((request.createEndTime ?: 0L) <= now)
        }
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val financialBegins = client.requests.filter { it.type == QueryType.Financial }.map { it.createBeginTime }
        assertEquals(monthBegin, financialBegins.first())
        assertTrue(financialBegins.contains(historyBegin))
    }

    @Test
    fun `stops when totalCount is already fetched`() = runTest {
        val client = ScriptedClient(
            pages = listOf(
                page(QueryType.Auth, rec("A1", "3"), pageNo = 1, total = 1),
                page(QueryType.Refund)
            )
        )
        val repo = InMemoryTransactionRepository()
        CardSync(client, repo, { now }, zone, pageSize = 1).sync(creds)
        assertTrue(client.requestedTypes.containsAll(
            listOf(QueryType.Auth, QueryType.Financial, QueryType.Refund)
        ))
        assertTrue(client.requestedTypes.drop(3).contains(QueryType.Auth))
        assertEquals("A1", repo.get("A1")?.txnId)
    }

    @Test
    fun `auth retMsg is kept on failed result`() = runTest {
        val client = ScriptedClient(
            pages = listOf(AssetRecordsPage(10004, "error sign!", emptyList(), 1, 0))
        )
        val result = CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        assertEquals(SyncResult.Failed(SyncError.Auth, "error sign!", 10004), result)
    }

    @Test
    fun `full history queries current month then previous month then older months`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: createBeginTime/createEndTime Unix ms
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        val auth = client.requests.filter { it.type == QueryType.Auth }
        assertTrue(auth.isNotEmpty())
        assertEquals(monthBegin, auth.first().createBeginTime)
        assertEquals(now, auth.first().createEndTime)
        val afterCurrent = auth.drop(1)
        val previous = afterCurrent.takeWhile { request ->
            (request.createBeginTime ?: 0L) >= prevBegin && (request.createEndTime ?: 0L) <= monthBegin
        }
        assertTrue(previous.isNotEmpty())
        val older = afterCurrent.drop(previous.size)
        assertTrue(older.isNotEmpty())
        assertTrue(older.any { it.createBeginTime == historyBegin })
        older.forEach { request ->
            assertTrue((request.createEndTime ?: 0L) <= monthBegin)
            assertTrue((request.createBeginTime ?: 0L) < prevBegin)
        }
    }

    @Test
    fun `card and points requests never use a time after now`() = runTest {
        // official-v5/bybit-card/asset-records.mdx + point/records.mdx: Unix ms; end is clock()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        client.requests.forEach { request ->
            assertTrue((request.createBeginTime ?: 0L) <= now)
            assertTrue((request.createEndTime ?: 0L) <= now)
            assertTrue((request.createBeginTime ?: 0L) < (request.createEndTime ?: 0L))
        }
        client.pointRequests.forEach { request ->
            assertTrue((request.startTime ?: 0L) <= now)
            assertTrue((request.endTime ?: 0L) <= now)
            assertTrue((request.startTime ?: 0L) < (request.endTime ?: 0L))
        }
        client.fundingRequests.forEach { request ->
            assertTrue((request.createTimeFromSec ?: 0L) <= now / 1000L)
            assertTrue((request.createTimeToSec ?: 0L) <= now / 1000L)
        }
    }

    @Test
    fun `checkpoint still refreshes the previous calendar month`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: a year-wide success can under-return
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        store.setCardSyncedThrough(since)
        store.setFinancialSyncedThrough(since)
        store.setCardHistoryGeneration(CardSync.CARD_HISTORY_GENERATION)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val auth = client.requests.filter { it.type == QueryType.Auth }
        assertTrue(auth.any { it.createBeginTime == monthBegin })
        assertTrue(
            auth.any { request ->
                (request.createBeginTime ?: 0L) >= prevBegin &&
                    (request.createEndTime ?: 0L) <= monthBegin
            }
        )
        auth.forEach { request ->
            assertTrue((request.createEndTime ?: 0L) <= now)
        }
    }

    @Test
    fun `previous month card windows stay within 7 days`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: no documented max window
        // official-v5/error.mdx: 181010 range > 7 days — do not send 31-day August as one call
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val prevAuth = client.requests.filter { request ->
            request.type == QueryType.Auth &&
                (request.createBeginTime ?: 0L) >= prevBegin &&
                (request.createEndTime ?: 0L) <= monthBegin
        }
        assertTrue(prevAuth.size > 1)
        prevAuth.forEach { request ->
            assertTrue(
                (request.createEndTime ?: 0L) - (request.createBeginTime ?: 0L) <=
                    com.sai.cardtrack.domain.MonthMath.SEVEN_DAYS_MS
            )
        }
    }

    @Test
    fun `current month callback fires before older auth window`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: createBeginTime/createEndTime Unix ms
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        var olderAuthBeforeReady = false
        var ready = false
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds) {
            ready = true
            olderAuthBeforeReady = client.requests.any { request ->
                request.type == QueryType.Auth && (request.createBeginTime ?: 0L) < monthBegin
            }
        }
        assertTrue(ready)
        assertFalse(olderAuthBeforeReady)
        assertTrue(
            client.requests.any { request ->
                request.type == QueryType.Auth && request.createBeginTime == historyBegin
            }
        )
    }

    @Test
    fun `card checkpoints wait until the older tail finishes`() = runTest {
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            InMemoryTransactionRepository(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds) {
            assertEquals(null, store.cardSyncedThrough())
            assertEquals(null, store.financialSyncedThrough())
        }
        assertEquals(now, store.cardSyncedThrough())
        assertEquals(now, store.financialSyncedThrough())
    }

    @Test
    fun `sync window starts 12 months ago first day`() = runTest {
        val client = ScriptedClient(pages = listOf(page(QueryType.Auth), page(QueryType.Refund)))
        CardSync(client, repo = InMemoryTransactionRepository(), clock = { now }, zone = zone).sync(creds)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        val authBegins = client.requests.filter { it.type == QueryType.Auth }.map { it.createBeginTime }
        assertEquals(monthBegin, authBegins.first())
        assertTrue(authBegins.contains(historyBegin))
    }

    @Test
    fun `funding transfer is stored as top up income`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            transfers = listOf(
                TransferPage(
                    0,
                    "OK",
                    listOf(
                        BybitTransfer("abc", "USDT", "20.00", "UNIFIED", "FUND", now, "SUCCESS")
                    ),
                    null
                )
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        val row = repo.get("tu_abc")
        assertEquals(TransactionSource.TopUp, row?.source)
        assertEquals(TransactionKind.Income, row?.kind)
        assertEquals("20.00", row?.paidAmount)
    }

    @Test
    fun `cashback points are stored and not listed as spend`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A1", "3")), page(QueryType.Refund)),
            points = listOf(
                PointRecordsPage(
                    0,
                    "OK",
                    listOf(BybitPointRecord("B1", "T1", 100, "1", "CASHBACK", now)),
                    1,
                    1
                )
            )
        )
        val repo = InMemoryTransactionRepository()
        CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(TransactionSource.Cashback, repo.get("cb_B1")?.source)
        assertEquals("100", repo.get("cb_B1")?.paidAmount)
        val visible = com.sai.cardtrack.domain.MonthMath.visible(
            repo.observeAll().first(),
            2025,
            9,
            zone
        )
        assertTrue(visible.none { it.source == TransactionSource.Cashback })
    }

    @Test
    fun `missing wallet permission keeps purchases and reports wallet`() = runTest {
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A1", "3")), page(QueryType.Refund)),
            transfers = listOf(TransferPage(10005, "permission denied", emptyList(), null))
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone, checkpoint = store).sync(creds)
        assertEquals(SyncResult.Partial(SyncError.Wallet, WALLET_BANNER, 10005), result)
        assertEquals("A1", repo.get("A1")?.txnId)
        assertEquals(now, store.cardSyncedThrough())
        assertEquals(now, store.financialSyncedThrough())
        assertEquals(now, store.fundingSyncedThrough())
    }

    @Test
    fun `funding and transfers never request more than 7 days`() = runTest {
        val client = ScriptedClient(pages = listOf(page(QueryType.Auth), page(QueryType.Refund)))
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        val maxSpan = 7L * 24 * 60 * 60 * 1000
        assertTrue(client.transferRequests.size > 1)
        client.transferRequests.forEach { request ->
            assertTrue((request.endTime ?: 0) - (request.startTime ?: 0) <= maxSpan)
        }
        client.fundingRequests.forEach { request ->
            val spanSec = (request.createTimeToSec ?: 0) - (request.createTimeFromSec ?: 0)
            assertTrue(spanSec * 1000L <= maxSpan)
            assertEquals(100, request.limit)
        }
        val begin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        assertEquals(now, client.transferRequests.first().endTime)
        assertEquals(begin, client.transferRequests.last().startTime)
    }

    @Test
    fun `points query this month before older history`() = runTest {
        // official-v5/bybit-card/point/records.mdx: no documented max window
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        assertTrue(client.pointRequests.size >= 2)
        assertEquals(monthBegin, client.pointRequests[0].startTime)
        assertEquals(now, client.pointRequests[0].endTime)
        assertTrue((client.pointRequests[1].startTime ?: 0L) >= prevBegin)
        assertTrue((client.pointRequests[1].endTime ?: 0L) <= monthBegin)
        assertTrue(client.pointRequests.any { it.startTime == historyBegin })
    }

    @Test
    fun `points start before auth returns`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            assetDelayMs = 1_000L
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        assertTrue(client.pointsWhileAuthInFlight)
    }

    @Test
    fun `funding starts before auth returns`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            assetDelayMs = 1_000L
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        assertTrue(client.fundingWhileAuthInFlight)
    }

    @Test
    fun `financial and refund start before auth returns`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            assetDelayMs = 1_000L
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        assertTrue(client.financialWhileAuthInFlight)
        assertTrue(client.refundWhileAuthInFlight)
    }

    @Test
    fun `rewards are not queried before current month callback`() = runTest {
        val rewards = InMemoryRewardStore()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            assetDelayMs = 1_000L
        )
        var ready = false
        var tierBeforeReady = false
        val wrapping = object : BybitCardClient by client {
            override suspend fun queryPointsTier(credentials: Credentials): PointsTierPage {
                if (!ready) {
                    tierBeforeReady = true
                }
                return client.queryPointsTier(credentials)
            }
        }
        CardSync(wrapping, InMemoryTransactionRepository(), { now }, zone, rewardStore = rewards)
            .sync(creds) {
                ready = true
            }
        assertFalse(tierBeforeReady)
        assertEquals("GOLD", rewards.get()?.tier)
    }

    @Test
    fun `previous month auth slices overlap`() = runTest {
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val inFlight = AtomicInteger(0)
        var maxInFlight = 0
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage {
                val from = request.createBeginTime ?: 0L
                val to = request.createEndTime ?: 0L
                if (request.type == QueryType.Auth && from >= prevBegin && to <= monthBegin) {
                    val n = inFlight.incrementAndGet()
                    if (n > maxInFlight) {
                        maxInFlight = n
                    }
                    delay(50)
                    inFlight.decrementAndGet()
                }
                return AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
            }

            override suspend fun queryFundingHistory(
                credentials: Credentials,
                request: FundingHistoryRequest
            ): FundingPage {
                return FundingPage(0, "OK", emptyList(), null)
            }
        }
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        assertTrue(maxInFlight > 1)
    }

    @Test
    fun `transfer fallback waits at least one second between windows`() = runTest {
        // official-v5/rate-limit/rate-limit.mdx: inter-transfer-list 60 req/min
        val pauses = mutableListOf<Long>()
        val client = ScriptedClient(pages = listOf(page(QueryType.Auth), page(QueryType.Refund)))
        CardSync(
            client,
            InMemoryTransactionRepository(),
            { now },
            zone,
            pause = { ms ->
                pauses.add(ms)
                delay(ms)
            }
        ).sync(creds)
        assertTrue(client.transferRequests.size > 1)
        assertTrue(pauses.any { it >= 1_000L })
    }

    @Test
    fun `older points stay within 7 days without waiting for 181010`() = runTest {
        // official-v5/bybit-card/point/records.mdx: no documented max
        // official-v5/error.mdx: 181010 — slice months longer than 7 days up front
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        val maxSpan = 7L * 24 * 60 * 60 * 1000
        val begin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        assertEquals(monthBegin, client.pointRequests.first().startTime)
        assertTrue(client.pointRequests.size > 2)
        assertTrue(client.pointRequests.any { it.startTime == begin })
        client.pointRequests.forEach { request ->
            assertTrue((request.endTime ?: 0) - (request.startTime ?: 0) <= maxSpan)
            assertTrue((request.endTime ?: 0) <= now)
        }
    }

    @Test
    fun `funding after checkpoint only walks recent windows and saves a new checkpoint`() = runTest {
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setFundingSyncedThrough(since)
        store.setFundingHistoryGeneration(CardSync.FUNDING_HISTORY_GENERATION)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        val result = CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        assertEquals(SyncResult.Success, result)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        assertTrue(client.fundingRequests.size > 1)
        client.fundingRequests.forEach { request ->
            assertEquals(100, request.limit)
            assertTrue((request.createTimeFromSec ?: 0L) >= prevBegin / 1000L)
            assertTrue((request.createTimeToSec ?: 0L) <= now / 1000L)
        }
        assertEquals(now / 1000L, client.fundingRequests.first().createTimeToSec)
        assertEquals(now, store.fundingSyncedThrough())
    }

    @Test
    fun `failed funding does not save a checkpoint`() = runTest {
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        val client = ScriptedClient(pages = listOf(page(QueryType.Auth), page(QueryType.Refund)))
        CardSync(
            client,
            InMemoryTransactionRepository(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        assertEquals(null, store.fundingSyncedThrough())
    }

    @Test
    fun `card records use official max page of 500`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: limit [1, 500]
        val client = ScriptedClient(pages = listOf(page(QueryType.Auth), page(QueryType.Refund)))
        CardSync(client, InMemoryTransactionRepository(), { now }, zone).sync(creds)
        assertTrue(client.requests.isNotEmpty())
        client.requests.forEach { request ->
            assertEquals(500, request.limit)
        }
    }

    @Test
    fun `max pages with more card history is partial`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: page + limit [1, 500]; totalCount
        val client = AlwaysFullAuthClient(pageSize = 2, totalCount = 10)
        val result = CardSync(
            client,
            InMemoryTransactionRepository(),
            { now },
            zone,
            pageSize = 2,
            maxPages = 2
        ).sync(creds)
        assertTrue(result is SyncResult.Partial)
        assertEquals(SyncError.Unknown, (result as SyncResult.Partial).reason)
        assertEquals("incomplete history", result.retMsg)
    }

    @Test
    fun `auth pagination partial still saves funding`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: page + limit [1, 500]; totalCount
        // official-v5/asset/fund-history.mdx: createTimeFrom/To seconds, ≤ 7 days
        val client = AlwaysFullAuthClient(
            pageSize = 2,
            totalCount = 10,
            funding = listOf(
                FundingPage(
                    0,
                    "OK",
                    listOf(
                        BybitFundingRecord(
                            id = "dep_partial",
                            currency = "USDT",
                            ioDirection = "I",
                            txnAmt = "40.00",
                            createTime = now,
                            showBusiType = "fundingAccountRecordDeposit",
                            showBusiTypeEn = "Deposit",
                            description = "onChain",
                            descriptionEn = "On-chain Deposit"
                        )
                    ),
                    null
                )
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(
            client,
            repo,
            { now },
            zone,
            pageSize = 2,
            maxPages = 2
        ).sync(creds)
        assertTrue(result is SyncResult.Partial)
        assertEquals("incomplete history", (result as SyncResult.Partial).retMsg)
        assertEquals(TransactionSource.TopUp, repo.get("fh_dep_partial")?.source)
    }

    @Test
    fun `empty repo ignores last sync and walks twelve month card history`() = runTest {
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            InMemoryTransactionRepository(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        assertTwelveMonthCardWalksCurrentThenOlder(client)
    }

    @Test
    fun `seeded repo with last sync still walks twelve month card history without card checkpoint`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: createBeginTime/createEndTime Unix ms
        // lastSyncAt is "sync finished now", not "AUTH already covered 12 months".
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        assertTwelveMonthCardWalksCurrentThenOlder(client)
        assertEquals(now, store.cardSyncedThrough())
    }

    @Test
    fun `auth after last sync still refreshes current and previous months`() = runTest {
        // official-v5/bybit-card/asset-records.mdx: createBeginTime/createEndTime Unix ms
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        store.setCardSyncedThrough(since)
        store.setFinancialSyncedThrough(since)
        store.setCardHistoryGeneration(CardSync.CARD_HISTORY_GENERATION)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        val authRequests = client.requests.filter { it.type == QueryType.Auth }
        assertTrue(authRequests.isNotEmpty())
        assertEquals(monthBegin, authRequests.first().createBeginTime)
        assertTrue(authRequests.any { (it.createBeginTime ?: 0L) >= prevBegin && (it.createEndTime ?: 0L) <= monthBegin })
        authRequests.forEach { request ->
            assertTrue((request.createBeginTime ?: 0L) >= prevBegin)
            assertTrue((request.createEndTime ?: 0L) <= now)
        }
        assertFalse(authRequests.any { it.createBeginTime == historyBegin })
    }

    @Test
    fun `funding after last sync still walks twelve month history when funding checkpoint is empty`() = runTest {
        // official-v5/asset/fund-history.mdx: createTimeFrom/To seconds, ≤ 7 days, limit [1, 100]
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        val cardBegin = com.sai.cardtrack.domain.MonthMath.incrementalBegin(historyBegin, now, since)
        val oldestFromSec = client.fundingRequests.minOf { it.createTimeFromSec ?: Long.MAX_VALUE }
        assertEquals(historyBegin / 1000L, oldestFromSec)
        assertTrue(client.fundingRequests.size > 1)
        assertTrue(oldestFromSec < cardBegin / 1000L)
        client.fundingRequests.forEach { request ->
            val spanSec = (request.createTimeToSec ?: 0) - (request.createTimeFromSec ?: 0)
            assertTrue(spanSec * 1000L <= com.sai.cardtrack.domain.MonthMath.SEVEN_DAYS_MS)
            assertEquals(100, request.limit)
        }
    }

    @Test
    fun `points after last sync still refresh current and previous months`() = runTest {
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        assertTrue(client.pointRequests.size > 1)
        assertEquals(now, client.pointRequests.first().endTime)
        assertEquals(monthBegin, client.pointRequests.first().startTime)
        client.pointRequests.forEach { request ->
            assertTrue((request.startTime ?: 0L) >= prevBegin)
            assertTrue((request.endTime ?: 0L) <= now)
            assertTrue(
                (request.endTime ?: 0L) - (request.startTime ?: 0L) <=
                    com.sai.cardtrack.domain.MonthMath.SEVEN_DAYS_MS
            )
        }
    }

    @Test
    fun `transfer fallback after last sync only walks recent windows`() = runTest {
        val since = now - 2L * 60 * 60 * 1000
        val store = com.sai.cardtrack.data.InMemoryCredentialsStore()
        store.setLastSyncAt(since)
        val client = ScriptedClient(pages = listOf(page(QueryType.Auth), page(QueryType.Refund)))
        CardSync(
            client,
            seededRepo(),
            { now },
            zone,
            checkpoint = store
        ).sync(creds)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        assertTrue(client.transferRequests.isNotEmpty())
        client.transferRequests.forEach { request ->
            assertEquals(50, request.limit)
            assertTrue((request.startTime ?: 0L) >= prevBegin)
            assertTrue((request.endTime ?: 0L) <= now)
        }
        assertEquals(now, client.transferRequests.first().endTime)
    }

    @Test
    fun `qr freeze is stored as purchase from funding`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(
                FundingPage(
                    0,
                    "OK",
                    listOf(
                        BybitFundingRecord(
                            id = "pay1",
                            currency = "USDT",
                            ioDirection = "O",
                            txnAmt = "8.54094",
                            createTime = now,
                            showBusiType = "fundingAccountRecordBybitpay",
                            showBusiTypeEn = "Bybit Pay",
                            description = "fundingAccountRecordBybitpayFiatFreeze",
                            descriptionEn = "Purchase"
                        )
                    ),
                    null
                )
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        val row = repo.get("qp_pay1")
        assertEquals(TransactionSource.Purchase, row?.source)
        assertEquals("Bybit Pay", row?.merchantName)
        assertEquals("8.54", row?.paidAmount)
        assertEquals("USD", row?.paidCurrency)
    }

    @Test
    fun `funding inbound is stored as top up when transfer list is empty`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(
                FundingPage(
                    0,
                    "OK",
                    listOf(
                        BybitFundingRecord(
                            id = "dep1",
                            currency = "USDT",
                            ioDirection = "I",
                            txnAmt = "40.00",
                            createTime = now,
                            showBusiType = "fundingAccountRecordDeposit",
                            showBusiTypeEn = "Deposit",
                            description = "onChain",
                            descriptionEn = "On-chain Deposit"
                        )
                    ),
                    null
                )
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone).sync(creds)
        assertEquals(SyncResult.Success, result)
        val row = repo.get("fh_dep1")
        assertEquals(TransactionSource.TopUp, row?.source)
        assertEquals("40.00", row?.paidAmount)
    }

    @Test
    fun `stale funding generation rewalks older window and reclassifies stored p2p cancel`() = runTest {
        val june = java.time.YearMonth.of(2025, 6).atDay(15).atStartOfDay(zone).toInstant().toEpochMilli()
        val repo = InMemoryTransactionRepository()
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "fh_june",
                orderNo = "june",
                kind = TransactionKind.Income,
                paidAmount = "40.00",
                paidCurrency = "USD",
                merchantName = "Пополнение",
                txnCreate = june,
                status = TransactionStatus.Success,
                bybitSide = "topup",
                source = TransactionSource.TopUp
            ),
            syncedAt = 1L
        )
        val store = InMemoryCredentialsStore()
        store.setCardHistoryGeneration(CardSync.CARD_HISTORY_GENERATION)
        store.setCardSyncedThrough(now)
        store.setFinancialSyncedThrough(now)
        store.setFundingSyncedThrough(now)
        val cancel = BybitFundingRecord(
            id = "june",
            currency = "USDT",
            ioDirection = "I",
            txnAmt = "40",
            createTime = june,
            showBusiType = "fundingAccountRecordAITD",
            showBusiTypeEn = "P2P Unfreeze",
            description = "",
            descriptionEn = ""
        )
        val client = WindowedFundingClient(cancel)
        CardSync(client, repo, { now }, zone, checkpoint = store).sync(creds)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        assertTrue(client.fundingFromSec.min() <= june / 1000L)
        assertTrue(client.fundingFromSec.min() <= historyBegin / 1000L)
        assertEquals(TransactionSource.P2PRefund, repo.get("fh_june")?.source)
        assertEquals(CardSync.FUNDING_HISTORY_GENERATION, store.fundingHistoryGeneration())
    }

    @Test
    fun `funding p2p cancel upserts refund not top up`() = runTest {
        val repo = InMemoryTransactionRepository()
        val store = InMemoryCredentialsStore()
        val client = ScriptedClient(
            funding = listOf(
                FundingPage(
                    0, "OK",
                    listOf(
                        BybitFundingRecord(
                            id = "p2c",
                            currency = "USDT",
                            ioDirection = "I",
                            txnAmt = "40",
                            createTime = 1_725_000_000_000,
                            showBusiType = "fundingAccountRecordP2P",
                            showBusiTypeEn = "P2P Trading",
                            description = "fundingAccountRecordP2PCancel",
                            descriptionEn = "P2P Cancel"
                        )
                    ),
                    null
                )
            )
        )
        CardSync(client, repo, { now }, zone, checkpoint = store).sync(Credentials("k", "s"))
        val row = repo.get("fh_p2c")
        assertEquals(TransactionSource.P2PRefund, row?.source)
    }

    @Test
    fun `failed coins balance does not fail sync and keeps snapshot`() = runTest {
        val fund = InMemoryFundBalanceStore()
        fund.save(FundBalanceSnapshot("9.00", "USD", 1L))
        val client = ScriptedClient(
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            coins = CoinsBalancePage(10005, "permission denied"),
            overview = AssetOverviewPage(10005, "permission denied")
        )
        val result = CardSync(
            client, InMemoryTransactionRepository(), { now }, zone,
            checkpoint = InMemoryCredentialsStore(),
            fundBalanceStore = fund
        ).sync(Credentials("k", "s"))
        assertTrue(result is SyncResult.Success || result is SyncResult.Partial)
        assertEquals("9.00", fund.get()?.amount)
    }

    @Test
    fun `successful coins balance saves usdt plus usdc`() = runTest {
        val fund = InMemoryFundBalanceStore()
        val client = ScriptedClient(
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            coins = CoinsBalancePage(0, "OK", "12.3", "0.70")
        )
        CardSync(
            client, InMemoryTransactionRepository(), { now }, zone,
            checkpoint = InMemoryCredentialsStore(),
            fundBalanceStore = fund
        ).sync(Credentials("k", "s"))
        assertEquals("13.00", fund.get()?.amount)
        assertEquals("USD", fund.get()?.currency)
    }

    @Test
    fun `successful asset overview saves funding totalEquity not leftover usdt`() = runTest {
        val fund = InMemoryFundBalanceStore()
        val client = ScriptedClient(
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            coins = CoinsBalancePage(0, "OK", "0.58", ""),
            overview = AssetOverviewPage(0, "OK", "80")
        )
        CardSync(
            client, InMemoryTransactionRepository(), { now }, zone,
            checkpoint = InMemoryCredentialsStore(),
            fundBalanceStore = fund
        ).sync(Credentials("k", "s"))
        assertEquals("80.00", fund.get()?.amount)
        assertEquals("USD", fund.get()?.currency)
    }

    @Test
    fun `asset overview card available adds easy earn to funding`() = runTest {
        val fund = InMemoryFundBalanceStore()
        val client = ScriptedClient(
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            overview = AssetOverviewPage(0, "OK", "10.00", "20.00")
        )
        CardSync(
            client, InMemoryTransactionRepository(), { now }, zone,
            checkpoint = InMemoryCredentialsStore(),
            fundBalanceStore = fund
        ).sync(Credentials("k", "s"))
        assertEquals("30.00", fund.get()?.amount)
    }

    @Test
    fun `snapshot calls wait 400ms between tier balance and coins`() = runTest {
        val events = mutableListOf<String>()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        val wrapping = object : BybitCardClient by client {
            override suspend fun queryPointsTier(credentials: Credentials): PointsTierPage {
                events.add("tier")
                return client.queryPointsTier(credentials)
            }

            override suspend fun queryPointsBalance(credentials: Credentials): PointsBalancePage {
                events.add("balance")
                return client.queryPointsBalance(credentials)
            }

            override suspend fun queryCoinsBalance(credentials: Credentials): CoinsBalancePage {
                events.add("coins")
                return client.queryCoinsBalance(credentials)
            }

            override suspend fun queryAssetOverview(credentials: Credentials): AssetOverviewPage {
                events.add("overview")
                return client.queryAssetOverview(credentials)
            }
        }
        CardSync(
            wrapping,
            InMemoryTransactionRepository(),
            { now },
            zone,
            pause = { ms ->
                if (ms == 400L) events.add("pause")
                delay(ms)
            },
            rewardStore = InMemoryRewardStore(),
            fundBalanceStore = InMemoryFundBalanceStore()
        ).sync(creds)
        assertEquals(listOf("tier", "pause", "balance", "pause", "overview"), events)
    }

    @Test
    fun `points failure after cashback still loads top ups`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A1", "3")), page(QueryType.Refund)),
            points = listOf(
                PointRecordsPage(
                    181010,
                    "The time range between startTime and endTime cannot exceed 7 days",
                    emptyList(),
                    1,
                    0
                ),
                PointRecordsPage(
                    0,
                    "OK",
                    listOf(BybitPointRecord("B1", "T1", 17, "1", "CASHBACK", now)),
                    1,
                    1
                ),
                PointRecordsPage(10006, "Too many visits!", emptyList(), 1, 0),
                PointRecordsPage(10006, "Too many visits!", emptyList(), 1, 0),
                PointRecordsPage(10006, "Too many visits!", emptyList(), 1, 0),
                PointRecordsPage(10006, "Too many visits!", emptyList(), 1, 0)
            ),
            transfers = listOf(
                TransferPage(
                    0,
                    "OK",
                    listOf(BybitTransfer("abc", "USDT", "20.00", "UNIFIED", "FUND", now, "SUCCESS")),
                    null
                )
            )
        )
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone, rateLimitRetries = 0).sync(creds)
        assertTrue(result is SyncResult.Partial)
        assertEquals(TransactionSource.TopUp, repo.get("tu_abc")?.source)
        assertEquals("A1", repo.get("A1")?.txnId)
    }

    @Test
    fun `auto sync skips closed funding slices already covered`() = runTest {
        // official-v5/asset/fund-history.mdx: createTimeFrom/To seconds, ≤ 7 days
        val coverage = InMemorySliceCoverageStore()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A", "3")), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        val repo = InMemoryTransactionRepository()
        val sync = CardSync(client, repo, { now }, zone, coverage = coverage)
        assertEquals(SyncResult.Success, sync.sync(creds))
        val firstCalls = client.fundingRequests.size
        assertTrue(firstCalls > 1)
        client.fundingRequests.clear()
        assertEquals(SyncResult.Success, sync.sync(creds))
        val overlap = com.sai.cardtrack.domain.MonthMath.INCREMENTAL_OVERLAP_MS
        assertTrue(client.fundingRequests.size < firstCalls)
        client.fundingRequests.forEach { request ->
            assertTrue((request.createTimeToSec ?: 0L) * 1000L > now - overlap)
        }
    }

    @Test
    fun `force network still queries covered closed funding slices`() = runTest {
        // official-v5/asset/fund-history.mdx: createTimeFrom/To seconds, ≤ 7 days
        val coverage = InMemorySliceCoverageStore()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A", "3")), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        val repo = InMemoryTransactionRepository()
        val sync = CardSync(client, repo, { now }, zone, coverage = coverage)
        assertEquals(SyncResult.Success, sync.sync(creds))
        val firstCalls = client.fundingRequests.size
        client.fundingRequests.clear()
        assertEquals(SyncResult.Success, sync.sync(creds, forceNetwork = true))
        assertEquals(firstCalls, client.fundingRequests.size)
    }

    @Test
    fun `earn yield windows are at most 7 days and not older than 90 days`() = runTest {
        // official-v5/finance/earn/easy-onchain/yield-history.mdx
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            yield = listOf(EarnYieldPage(0, "OK", emptyList(), null))
        )
        val store = InMemoryCredentialsStore()
        CardSync(client, InMemoryTransactionRepository(), { now }, zone, checkpoint = store)
            .sync(creds)
        val maxSpan = MonthMath.SEVEN_DAYS_MS
        val floor = MonthMath.earnHistoryBeginMillis(now)
        assertTrue(client.yieldRequests.isNotEmpty())
        client.yieldRequests.forEach { request ->
            assertTrue(request.endTime - request.startTime <= maxSpan)
            assertTrue(request.startTime >= floor)
            assertEquals(100, request.limit)
        }
        assertEquals(now, store.earnSyncedThrough())
    }

    @Test
    fun `earn 10005 does not fail sync or write coverage`() = runTest {
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        val store = InMemoryCredentialsStore()
        val repo = InMemoryTransactionRepository()
        val result = CardSync(client, repo, { now }, zone, checkpoint = store).sync(creds)
        assertEquals(SyncResult.Success, result)
        assertNull(store.earnSyncedThrough())
        assertTrue(repo.observeAll().first().none { it.txnId.startsWith("ey_") })
    }

    @Test
    fun `earn success upserts usdt row and later yield error keeps it`() = runTest {
        val record = BybitEarnYieldRecord("42", "USDT", "1.25", "Success", now)
        val ok = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            yield = listOf(EarnYieldPage(0, "OK", listOf(record), null))
        )
        val repo = InMemoryTransactionRepository()
        val store = InMemoryCredentialsStore()
        assertEquals(
            SyncResult.Success,
            CardSync(ok, repo, { now }, zone, checkpoint = store).sync(creds)
        )
        assertEquals(TransactionSource.Earn, repo.get("ey_42")?.source)
        val denied = ScriptedClient(
            pages = listOf(page(QueryType.Auth), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null)),
            yield = listOf(EarnYieldPage(10016, "server error", emptyList(), null))
        )
        assertEquals(
            SyncResult.Success,
            CardSync(denied, repo, { now }, zone, checkpoint = store).sync(creds)
        )
        assertEquals("1.25", repo.get("ey_42")?.paidAmount)
    }

    @Test
    fun `empty repo clears slice coverage before walking`() = runTest {
        // official-v5/asset/fund-history.mdx: createTimeFrom/To seconds, ≤ 7 days
        val coverage = InMemorySliceCoverageStore()
        val client = ScriptedClient(
            pages = listOf(page(QueryType.Auth, rec("A", "3")), page(QueryType.Refund)),
            funding = listOf(FundingPage(0, "OK", emptyList(), null))
        )
        val repo = InMemoryTransactionRepository()
        val sync = CardSync(client, repo, { now }, zone, coverage = coverage)
        assertEquals(SyncResult.Success, sync.sync(creds))
        val firstCalls = client.fundingRequests.size
        repo.clear()
        client.fundingRequests.clear()
        assertEquals(SyncResult.Success, sync.sync(creds))
        assertEquals(firstCalls, client.fundingRequests.size)
    }

    private fun assertRefundSplitKeptTimes(client: ScriptedClient) {
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val refundRequests = client.requests.filter { it.type == QueryType.Refund }
        val currentMonthRefunds = refundRequests.filter { (it.createBeginTime ?: 0L) >= monthBegin }
        assertTrue(currentMonthRefunds.size >= 2)
        refundRequests.forEach { request ->
            assertTrue(request.createBeginTime != null)
            assertTrue(request.createEndTime != null)
        }
        val maxSpan = com.sai.cardtrack.domain.MonthMath.SEVEN_DAYS_MS
        currentMonthRefunds.drop(1).forEach { request ->
            assertTrue((request.createEndTime ?: 0) - (request.createBeginTime ?: 0) <= maxSpan)
        }
    }

    private fun assertTwelveMonthCardWalksCurrentThenOlder(client: ScriptedClient) {
        val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
        val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
        val historyBegin = com.sai.cardtrack.domain.MonthMath.syncBeginMillis(now, zone)
        val auth = client.requests.filter { it.type == QueryType.Auth }
        assertTrue(auth.isNotEmpty())
        assertEquals(monthBegin, auth.first().createBeginTime)
        assertEquals(now, auth.first().createEndTime)
        assertTrue(
            auth.any { request ->
                (request.createBeginTime ?: 0L) >= prevBegin &&
                    (request.createEndTime ?: 0L) <= monthBegin
            }
        )
        assertTrue(auth.any { it.createBeginTime == historyBegin })
        auth.forEach { request ->
            assertTrue((request.createEndTime ?: 0L) <= now)
            assertTrue((request.createBeginTime ?: 0L) >= historyBegin)
        }
    }

    private fun page(
        type: QueryType,
        vararg records: BybitAssetRecord,
        pageNo: Int = 1,
        total: Int = records.size
    ): AssetRecordsPage {
        return AssetRecordsPage(0, "OK", records.toList(), pageNo, total)
    }

    private inner class CappedAuthPagesClient(
        private val actualPageSize: Int,
        private val totalCount: Int
    ) : BybitCardClient {
        val authPages = mutableListOf<Int>()

        override suspend fun queryAssetRecords(
            credentials: Credentials,
            request: AssetRecordsRequest
        ): AssetRecordsPage {
            if (request.type != QueryType.Auth) {
                return AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
            }
            authPages.add(request.page)
            val offset = (request.page - 1) * actualPageSize
            val remaining = (totalCount - offset).coerceAtLeast(0)
            val count = remaining.coerceAtMost(actualPageSize)
            val records = List(count) { index -> rec("A${offset + index}", "3") }
            return AssetRecordsPage(0, "OK", records, request.page, totalCount, actualPageSize)
        }
    }

    private inner class RateLimitedAugustSliceClient : BybitCardClient {
        val authWindows = mutableListOf<Pair<Long, Long>>()
        private var augustAuth = 0

        override suspend fun queryAssetRecords(
            credentials: Credentials,
            request: AssetRecordsRequest
        ): AssetRecordsPage {
            if (request.type != QueryType.Auth) {
                return AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
            }
            val from = request.createBeginTime ?: 0L
            val to = request.createEndTime ?: 0L
            authWindows.add(from to to)
            val prevBegin = com.sai.cardtrack.domain.MonthMath.previousMonthBeginMillis(now, zone)
            val monthBegin = com.sai.cardtrack.domain.MonthMath.monthBeginMillis(now, zone)
            if (from >= prevBegin && to <= monthBegin) {
                augustAuth += 1
                if (augustAuth == 2) {
                    return AssetRecordsPage(10006, "Too many visits!", emptyList(), request.page, 0)
                }
            }
            return AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
        }
    }

    private inner class ShortAuthPagesClient(
        private val pages: Map<Int, AssetRecordsPage>
    ) : BybitCardClient {
        override suspend fun queryAssetRecords(
            credentials: Credentials,
            request: AssetRecordsRequest
        ): AssetRecordsPage {
            if (request.type != QueryType.Auth) {
                return AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
            }
            return pages[request.page] ?: AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
        }

        override suspend fun queryPointRecords(
            credentials: Credentials,
            request: PointRecordsRequest
        ): PointRecordsPage {
            return PointRecordsPage(0, "OK", emptyList(), request.pageNo, 0)
        }

        override suspend fun queryInterTransfers(
            credentials: Credentials,
            request: TransferListRequest
        ): TransferPage {
            return TransferPage(0, "OK", emptyList(), null)
        }

        override suspend fun queryFundingHistory(
            credentials: Credentials,
            request: FundingHistoryRequest
        ): FundingPage {
            return FundingPage(0, "OK", emptyList(), null)
        }

        override suspend fun queryPointsTier(credentials: Credentials): PointsTierPage {
            return PointsTierPage(0, "OK", "10.00", "500.00", "1", "GOLD", true)
        }

        override suspend fun queryPointsBalance(credentials: Credentials): PointsBalancePage {
            return PointsBalancePage(0, "OK", "5000", "200")
        }
    }

    private inner class AlwaysFullAuthClient(
        private val pageSize: Int,
        private val totalCount: Int,
        private val funding: List<FundingPage> = emptyList()
    ) : BybitCardClient {
        override suspend fun queryAssetRecords(
            credentials: Credentials,
            request: AssetRecordsRequest
        ): AssetRecordsPage {
            if (request.type != QueryType.Auth) {
                return AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
            }
            val records = List(pageSize) { index -> rec("A${request.page}_$index", "3") }
            return AssetRecordsPage(0, "OK", records, request.page, totalCount)
        }

        override suspend fun queryPointRecords(
            credentials: Credentials,
            request: PointRecordsRequest
        ): PointRecordsPage {
            return PointRecordsPage(0, "OK", emptyList(), request.pageNo, 0)
        }

        override suspend fun queryInterTransfers(
            credentials: Credentials,
            request: TransferListRequest
        ): TransferPage {
            return TransferPage(0, "OK", emptyList(), null)
        }

        override suspend fun queryFundingHistory(
            credentials: Credentials,
            request: FundingHistoryRequest
        ): FundingPage {
            return funding.firstOrNull() ?: FundingPage(0, "OK", emptyList(), null)
        }

        override suspend fun queryPointsTier(credentials: Credentials): PointsTierPage {
            return PointsTierPage(0, "OK", "10.00", "500.00", "1", "GOLD", true)
        }

        override suspend fun queryPointsBalance(credentials: Credentials): PointsBalancePage {
            return PointsBalancePage(0, "OK", "5000", "200")
        }
    }

    private inner class WindowedFundingClient(
        private val record: BybitFundingRecord
    ) : BybitCardClient {
        val fundingFromSec = mutableListOf<Long>()

        override suspend fun queryAssetRecords(
            credentials: Credentials,
            request: AssetRecordsRequest
        ): AssetRecordsPage {
            return AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
        }

        override suspend fun queryFundingHistory(
            credentials: Credentials,
            request: FundingHistoryRequest
        ): FundingPage {
            val from = request.createTimeFromSec ?: 0L
            val to = request.createTimeToSec ?: Long.MAX_VALUE
            fundingFromSec.add(from)
            val createSec = record.createTime / 1000L
            val hit = createSec in from until to || createSec == from
            return FundingPage(
                0,
                "OK",
                if (hit) listOf(record) else emptyList(),
                null
            )
        }
    }

    private class ScriptedClient(
        private val pages: List<AssetRecordsPage> = emptyList(),
        private val financial: List<AssetRecordsPage> = emptyList(),
        private val refunds: List<AssetRecordsPage> = emptyList(),
        private val failFirst: IOException? = null,
        private val failOnCall: Int? = null,
        private val points: List<PointRecordsPage> = emptyList(),
        private val transfers: List<TransferPage> = emptyList(),
        private val funding: List<FundingPage> = emptyList(),
        private val yield: List<EarnYieldPage> = emptyList(),
        private val assetDelayMs: Long = 0L,
        private val tier: PointsTierPage? = PointsTierPage(0, "OK", "10.00", "500.00", "1", "GOLD", true),
        private val balance: PointsBalancePage? = PointsBalancePage(0, "OK", "5000", "200"),
        private val coins: CoinsBalancePage = CoinsBalancePage(0, "OK", "12.30", "0.70"),
        private val overview: AssetOverviewPage = AssetOverviewPage(0, "OK", "13.00")
    ) : BybitCardClient {
        val requestedTypes = mutableListOf<QueryType>()
        val requests = mutableListOf<AssetRecordsRequest>()
        val pointRequests = mutableListOf<PointRecordsRequest>()
        val transferRequests = mutableListOf<TransferListRequest>()
        val fundingRequests = mutableListOf<FundingHistoryRequest>()
        val yieldRequests = mutableListOf<EarnYieldRequest>()
        var pointsWhileAuthInFlight = false
        var fundingWhileAuthInFlight = false
        var financialWhileAuthInFlight = false
        var refundWhileAuthInFlight = false
        @Volatile private var authInFlight = false
        private val lock = Any()
        private var call = 0
        private var financialCall = 0
        private var refundCall = 0
        private var pointCall = 0
        private var transferCall = 0
        private var fundingCall = 0
        private var yieldCall = 0

        override suspend fun queryAssetRecords(
            credentials: Credentials,
            request: AssetRecordsRequest
        ): AssetRecordsPage {
            if (assetDelayMs > 0L && request.type == QueryType.Auth) {
                authInFlight = true
                delay(assetDelayMs)
            }
            try {
                val page: AssetRecordsPage
                synchronized(lock) {
                    when (request.type) {
                        QueryType.Financial -> {
                            if (authInFlight) {
                                financialWhileAuthInFlight = true
                            }
                            requestedTypes.add(request.type)
                            requests.add(request)
                            financialCall += 1
                            page = financial.getOrElse(financialCall - 1) {
                                AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
                            }
                        }
                        QueryType.Refund -> {
                            if (authInFlight) {
                                refundWhileAuthInFlight = true
                            }
                            requestedTypes.add(request.type)
                            requests.add(request)
                            refundCall += 1
                            page = refunds.getOrElse(refundCall - 1) {
                                AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
                            }
                        }
                        QueryType.Auth -> {
                            call += 1
                            if (failFirst != null && call == 1) throw failFirst
                            if (failOnCall != null && call == failOnCall) throw IOException("boom")
                            requestedTypes.add(request.type)
                            requests.add(request)
                            page = pages.getOrElse(call - 1) {
                                AssetRecordsPage(0, "OK", emptyList(), request.page, 0)
                            }
                        }
                    }
                }
                return page
            } finally {
                if (request.type == QueryType.Auth) {
                    authInFlight = false
                }
            }
        }

        override suspend fun queryPointRecords(
            credentials: Credentials,
            request: PointRecordsRequest
        ): PointRecordsPage {
            synchronized(lock) {
                if (authInFlight) {
                    pointsWhileAuthInFlight = true
                }
                pointCall += 1
                pointRequests.add(request)
                return points.getOrElse(pointCall - 1) {
                    PointRecordsPage(0, "OK", emptyList(), request.pageNo, 0)
                }
            }
        }

        override suspend fun queryInterTransfers(
            credentials: Credentials,
            request: TransferListRequest
        ): TransferPage {
            transferCall += 1
            transferRequests.add(request)
            return transfers.getOrElse(transferCall - 1) {
                TransferPage(0, "OK", emptyList(), null)
            }
        }

        override suspend fun queryFundingHistory(
            credentials: Credentials,
            request: FundingHistoryRequest
        ): FundingPage {
            if (authInFlight) {
                fundingWhileAuthInFlight = true
            }
            fundingCall += 1
            fundingRequests.add(request)
            return funding.getOrElse(fundingCall - 1) {
                if (funding.isEmpty()) {
                    FundingPage(10005, "permission denied", emptyList(), null)
                } else {
                    FundingPage(0, "OK", emptyList(), null)
                }
            }
        }

        override suspend fun queryEarnYield(
            credentials: Credentials,
            request: EarnYieldRequest
        ): EarnYieldPage {
            yieldCall += 1
            yieldRequests.add(request)
            return yield.getOrElse(yieldCall - 1) {
                if (yield.isEmpty()) {
                    EarnYieldPage(10005, "permission denied", emptyList(), null)
                } else {
                    EarnYieldPage(0, "OK", emptyList(), null)
                }
            }
        }

        override suspend fun queryPointsTier(credentials: Credentials): PointsTierPage {
            return tier ?: PointsTierPage(10005, "permission denied")
        }

        override suspend fun queryPointsBalance(credentials: Credentials): PointsBalancePage {
            return balance ?: PointsBalancePage(10005, "permission denied")
        }

        override suspend fun queryCoinsBalance(credentials: Credentials): CoinsBalancePage {
            return coins
        }

        override suspend fun queryAssetOverview(credentials: Credentials): AssetOverviewPage {
            return overview
        }
    }
}
