package com.sai.cardtrack.sync

import com.sai.cardtrack.bybit.AssetRecordsPage
import com.sai.cardtrack.bybit.AssetRecordsRequest
import com.sai.cardtrack.bybit.BybitCardClient
import com.sai.cardtrack.bybit.EarnYieldPage
import com.sai.cardtrack.bybit.EarnYieldRequest
import com.sai.cardtrack.bybit.FundingHistoryRequest
import com.sai.cardtrack.bybit.FundingPage
import com.sai.cardtrack.bybit.PointRecordsPage
import com.sai.cardtrack.bybit.PointRecordsRequest
import com.sai.cardtrack.bybit.TransferListRequest
import com.sai.cardtrack.bybit.TransferPage
import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.data.FundBalanceStore
import com.sai.cardtrack.data.RewardStore
import com.sai.cardtrack.data.SliceCoverageStore
import com.sai.cardtrack.data.TransactionRepository
import com.sai.cardtrack.domain.ClassifyResult
import com.sai.cardtrack.domain.FundBalanceMath
import com.sai.cardtrack.domain.FundBalanceSnapshot
import com.sai.cardtrack.domain.MonthMath
import com.sai.cardtrack.domain.QueryType
import com.sai.cardtrack.domain.RecordClassifier
import com.sai.cardtrack.domain.RewardSnapshot
import com.sai.cardtrack.domain.TransactionDraft
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

class CardSync(
    private val client: BybitCardClient,
    private val repo: TransactionRepository,
    private val clock: () -> Long,
    private val zone: ZoneId,
    // official-v5/bybit-card/asset-records.mdx: limit [1, 500]
    private val pageSize: Int = 500,
    private val maxPages: Int = 20,
    private val rateLimitRetries: Int = 3,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val checkpoint: CredentialsStore? = null,
    private val rewardStore: RewardStore? = null,
    private val fundBalanceStore: FundBalanceStore? = null,
    private val coverage: SliceCoverageStore? = null
) {
    private val writeLock = Mutex()
    @Volatile
    private var forceNetwork: Boolean = false

    suspend fun sync(
        credentials: Credentials,
        forceNetwork: Boolean = false,
        onCurrentMonthReady: (suspend () -> Unit)? = null
    ): SyncResult {
        this.forceNetwork = forceNetwork
        if (repo.isEmpty()) {
            coverage?.clear()
        }
        val historyBegin = MonthMath.syncBeginMillis(clock(), zone)
        val end = clock()
        val savedAny = AtomicBoolean(false)
        val onSaved = { savedAny.set(true) }
        return coroutineScope {
            val staleCardCoverage =
                (checkpoint?.cardHistoryGeneration() ?: 0) < CARD_HISTORY_GENERATION
            val authThrough =
                if (repo.isEmpty() || staleCardCoverage) null else checkpoint?.cardSyncedThrough()
            val financialThrough =
                if (repo.isEmpty() || staleCardCoverage) null else checkpoint?.financialSyncedThrough()
            val authBegin = MonthMath.priorityBegin(historyBegin, end, authThrough, zone)
            val financialBegin = MonthMath.priorityBegin(historyBegin, end, financialThrough, zone)
            val pointsBegin = MonthMath.priorityBegin(historyBegin, end, lastSyncIfHistoryPresent(), zone)
            val staleFundingCoverage =
                (checkpoint?.fundingHistoryGeneration() ?: 0) < FUNDING_HISTORY_GENERATION
            val fundingThrough =
                if (repo.isEmpty() || staleFundingCoverage) null else checkpoint?.fundingSyncedThrough()
            val fundingBegin = MonthMath.priorityBegin(historyBegin, end, fundingThrough, zone)
            val transferBegin = MonthMath.priorityBegin(historyBegin, end, lastSyncIfHistoryPresent(), zone)
            val months = MonthMath.priorityMonthWindows(
                minOf(authBegin, financialBegin, pointsBegin, fundingBegin),
                end,
                zone
            )
            if (months.isEmpty()) {
                return@coroutineScope SyncResult.Success
            }

            val current = months.first()
            // official-v5/bybit-card/point/records.mdx: no documented max window.
            // Current month first so the header can update while AUTH/funding run.
            val currentPull = pullMonth(
                credentials,
                current,
                authBegin,
                financialBegin,
                pointsBegin,
                fundingBegin,
                transferBegin,
                onSaved
            )
            val cardCurrent = currentPull.card
            val walletCurrent = currentPull.wallet
            val pointsCurrent = currentPull.points
            if (cardCurrent.auth is SyncResult.Failed) {
                onCurrentMonthReady?.invoke()
                return@coroutineScope combineSync(cardCurrent.auth, pointsCurrent, savedAny.get())
            }
            onCurrentMonthReady?.invoke()
            val currentFamilies = combineFamilies(cardCurrent, walletCurrent)
            if (currentFamilies is SyncResult.Success || currentFamilies is SyncResult.Partial) {
                launch {
                    refreshRewards(credentials)
                    refreshFundBalance(credentials)
                }
            }

            var cardOlder = CardTypePull.ok()
            var walletOlder = WalletPull.skipped()
            var pointsOlder: SyncResult = SyncResult.Success
            for (month in months.drop(1)) {
                val monthPull = pullMonth(
                    credentials,
                    month,
                    authBegin,
                    financialBegin,
                    pointsBegin,
                    fundingBegin,
                    transferBegin,
                    onSaved
                )
                cardOlder = combineCardTypes(cardOlder, monthPull.card)
                walletOlder = combineWallet(walletOlder, monthPull.wallet)
                pointsOlder = firstNonSuccess(pointsOlder, monthPull.points)
                if (monthPull.card.auth is SyncResult.Failed) {
                    break
                }
            }

            val card = combineFamilies(
                combineCardTypes(cardCurrent, cardOlder),
                combineWallet(walletCurrent, walletOlder)
            )
            val points = firstNonSuccess(pointsCurrent, pointsOlder)
            val result = combineSync(card, points, savedAny.get())
            writeCheckpoints(
                end = end,
                authOk = cardCurrent.auth is SyncResult.Success && cardOlder.auth is SyncResult.Success,
                financialOk = financialReady(cardCurrent.financial) &&
                    financialReady(cardOlder.financial) &&
                    financialReady(cardCurrent.refunds) &&
                    financialReady(cardOlder.refunds),
                fundingOk = walletCurrent.fundingSuccess && walletOlder.fundingSuccess,
                families = card,
                markFullCardHistory = authBegin <= historyBegin,
                markFullFundingHistory = fundingBegin <= historyBegin
            )
            if (result is SyncResult.Success || result is SyncResult.Partial) {
                pullEarnBestEffort(credentials, historyBegin, end, onSaved)
            }
            result
        }
    }

    private suspend fun pullMonth(
        credentials: Credentials,
        month: Pair<Long, Long>,
        authBegin: Long,
        financialBegin: Long,
        pointsBegin: Long,
        fundingBegin: Long,
        transferBegin: Long,
        onSaved: () -> Unit
    ): MonthPull {
        return coroutineScope {
            val authWindow = clipWindow(month, authBegin)
            val financialWindow = clipWindow(month, financialBegin)
            val authDef = async { pullTypedWindow(credentials, QueryType.Auth, authWindow, onSaved) }
            val financialDef = async {
                pullTypedWindow(credentials, QueryType.Financial, financialWindow, onSaved)
            }
            val refundsDef = async {
                if (hasSpan(financialWindow)) {
                    pullRefunds(credentials, financialWindow!!.first, financialWindow.second, onSaved)
                } else {
                    SyncResult.Success
                }
            }
            val pointsDef = async { pullPointsForMonth(credentials, month, pointsBegin, onSaved) }
            val walletDef = async {
                pullWallet(
                    credentials,
                    maxOf(fundingBegin, month.first),
                    month.second,
                    transferBegin,
                    onSaved
                )
            }
            MonthPull(
                card = CardTypePull(authDef.await(), financialDef.await(), refundsDef.await()),
                wallet = walletDef.await(),
                points = pointsDef.await()
            )
        }
    }

    private suspend fun refreshRewards(credentials: Credentials) {
        val store = rewardStore ?: return
        try {
            val tier = client.queryPointsTier(credentials)
            pause(400)
            val balance = client.queryPointsBalance(credentials)
            if (tier.retCode != 0 || balance.retCode != 0) return
            store.save(
                RewardSnapshot(
                    usedLimit = tier.usedLimit,
                    limit = tier.limit,
                    unit = tier.unit,
                    tier = tier.tier,
                    autoCashback = tier.autoCashback,
                    availablePoint = balance.availablePoint,
                    pendingPoint = balance.pendingPoint
                )
            )
        } catch (_: Exception) {
            return
        }
    }

    private suspend fun refreshFundBalance(credentials: Credentials) {
        val store = fundBalanceStore ?: return
        try {
            pause(400)
            val page = client.queryAssetOverview(credentials)
            if (page.retCode != 0) return
            if (page.fundingEquity.isBlank() && page.easyEarnEquity.isBlank()) return
            store.save(
                FundBalanceSnapshot(
                    amount = FundBalanceMath.cardAvailableUsd(page.fundingEquity, page.easyEarnEquity),
                    currency = "USD",
                    updatedAt = clock()
                )
            )
        } catch (_: Exception) {
            return
        }
    }

    private suspend fun pullTypedWindow(
        credentials: Credentials,
        type: QueryType,
        window: Pair<Long, Long>?,
        onSaved: () -> Unit
    ): SyncResult {
        if (!hasSpan(window)) return SyncResult.Success
        return pullType(credentials, type, window!!.first, window.second, onSaved)
    }

    private fun hasSpan(window: Pair<Long, Long>?): Boolean {
        return window != null && window.second > window.first
    }

    private suspend fun saveDraft(draft: TransactionDraft, onSaved: () -> Unit) {
        writeLock.withLock {
            repo.upsertFromSync(draft, clock())
            onSaved()
        }
    }

    private fun clipWindow(month: Pair<Long, Long>, familyBegin: Long): Pair<Long, Long>? {
        val from = maxOf(month.first, familyBegin)
        if (month.second <= from) return null
        return from to month.second
    }

    private suspend fun pullPointsForMonth(
        credentials: Credentials,
        month: Pair<Long, Long>,
        pointsBegin: Long,
        onSaved: () -> Unit
    ): SyncResult {
        val window = clipWindow(month, pointsBegin) ?: return SyncResult.Success
        return pullPointsWideOrChunked(credentials, window.first, window.second, onSaved)
    }

    private suspend fun pullEarnBestEffort(
        credentials: Credentials,
        historyBegin: Long,
        end: Long,
        onSaved: () -> Unit
    ) {
        val floor = MonthMath.earnHistoryBeginMillis(end)
        val earnBegin = maxOf(historyBegin, floor)
        val through = if (repo.isEmpty()) null else checkpoint?.earnSyncedThrough()
        val from = MonthMath.incrementalBegin(earnBegin, end, through)
        val walked = pullChunked(
            from,
            end,
            pauseBetweenMs = 400,
            family = SliceFamily.Earn
        ) { windowFrom, windowTo ->
            pullEarnYield(credentials, windowFrom, windowTo, onSaved)
        }
        if (walked is SyncResult.Success) {
            checkpoint?.setEarnSyncedThrough(end)
        }
    }

    // official-v5/finance/earn/easy-onchain/yield-history.mdx:
    // GET /v5/earn/yield; startTime/endTime Unix ms; both → ≤ 7 days; limit [1, 100]
    private suspend fun pullEarnYield(
        credentials: Credentials,
        begin: Long,
        end: Long,
        onSaved: () -> Unit,
        retried: Boolean = false
    ): SyncResult {
        var cursor: String? = null
        var page = 1
        while (true) {
            val request = EarnYieldRequest(begin, end, 100, cursor)
            val response: EarnYieldPage = try {
                var body = client.queryEarnYield(credentials, request)
                var attempt = 0
                while (body.retCode == 10006 && attempt < rateLimitRetries) {
                    attempt += 1
                    pause(1000L * attempt)
                    body = client.queryEarnYield(credentials, request)
                }
                body
            } catch (_: IOException) {
                return SyncResult.Failed(SyncError.Network, "earn")
            }
            if (isWalletDenied(response.retCode, response.retMsg)) {
                return SyncResult.Failed(SyncError.Wallet, response.retMsg, response.retCode)
            }
            if (response.retCode == 10001 && !retried && begin < MonthMath.earnHistoryBeginMillis(clock())) {
                return pullEarnYield(
                    credentials,
                    MonthMath.earnHistoryBeginMillis(clock()),
                    end,
                    onSaved,
                    retried = true
                )
            }
            val mapped = mapRetCode(response.retCode)
            if (mapped != null) {
                return SyncResult.Failed(mapped, response.retMsg, response.retCode)
            }
            for (record in response.records) {
                when (val classified = RecordClassifier.classifyEarnYield(record)) {
                    ClassifyResult.Skip -> Unit
                    is ClassifyResult.Keep -> {
                        saveDraft(classified.draft, onSaved)
                    }
                }
            }
            val next = response.nextCursor?.takeIf { it.isNotBlank() }
            finishPage(moreRemains = next != null, page = page)?.let { return it }
            cursor = next
            page += 1
        }
    }

    private suspend fun pullWallet(
        credentials: Credentials,
        begin: Long,
        end: Long,
        transferBegin: Long,
        onSaved: () -> Unit
    ): WalletPull {
        if (end <= begin) {
            return WalletPull.skipped()
        }
        // official-v5/asset/fund-history.mdx: createTimeFrom/To in seconds, ≤ 7 days, limit [1, 100]
        // official-v5/rate-limit/rate-limit.mdx: /v5/asset/fundinghistory 30 req/s — no inter-window pause
        val funding = pullChunked(begin, end, pauseBetweenMs = 0, family = SliceFamily.Funding) { from, to ->
            pullFunding(credentials, from, to, onSaved)
        }
        if (funding is SyncResult.Success || funding is SyncResult.Partial) {
            return WalletPull(funding, fundingSuccess = funding is SyncResult.Success)
        }
        val sliceBegin = maxOf(transferBegin, begin)
        if (sliceBegin >= end) {
            return if (funding is SyncResult.Failed && isWalletFailure(funding)) {
                WalletPull.skipped()
            } else {
                WalletPull(funding, fundingSuccess = false)
            }
        }
        // official-v5/asset/transfer/inter-transfer-list.mdx: ≤ 7 days, limit [1, 50]
        // official-v5/rate-limit/rate-limit.mdx: 60 req/min
        val transfers = pullChunked(
            sliceBegin,
            end,
            pauseBetweenMs = 1_000L,
            family = SliceFamily.Transfer
        ) { from, to ->
            pullTransfers(credentials, from, to, onSaved)
        }
        if (transfers is SyncResult.Failed && isWalletFailure(transfers)) {
            return WalletPull(
                SyncResult.Failed(SyncError.Wallet, WALLET_BANNER, transfers.retCode),
                fundingSuccess = false
            )
        }
        return WalletPull(transfers, fundingSuccess = false)
    }

    private suspend fun writeCheckpoints(
        end: Long,
        authOk: Boolean,
        financialOk: Boolean,
        fundingOk: Boolean,
        families: SyncResult,
        markFullCardHistory: Boolean,
        markFullFundingHistory: Boolean
    ) {
        val walletDenied = families is SyncResult.Failed && isWalletFailure(families)
        if (authOk && (families !is SyncResult.Failed || walletDenied)) {
            checkpoint?.setCardSyncedThrough(end)
            if (markFullCardHistory) {
                checkpoint?.setCardHistoryGeneration(CARD_HISTORY_GENERATION)
            }
        }
        if (financialOk && (families !is SyncResult.Failed || walletDenied)) {
            checkpoint?.setFinancialSyncedThrough(end)
        }
        if (fundingOk || walletDenied) {
            checkpoint?.setFundingSyncedThrough(end)
            if (markFullFundingHistory) {
                checkpoint?.setFundingHistoryGeneration(FUNDING_HISTORY_GENERATION)
            }
        }
    }

    private fun combineCardTypes(first: CardTypePull, second: CardTypePull): CardTypePull {
        return CardTypePull(
            auth = firstNonSuccess(first.auth, second.auth),
            financial = firstNonSuccess(first.financial, second.financial),
            refunds = firstNonSuccess(first.refunds, second.refunds)
        )
    }

    private fun combineWallet(first: WalletPull, second: WalletPull): WalletPull {
        return WalletPull(
            result = firstNonSuccess(first.result, second.result),
            fundingSuccess = first.fundingSuccess && second.fundingSuccess
        )
    }

    private fun combineFamilies(card: CardTypePull, wallet: WalletPull): SyncResult {
        val refundForCombine = if (card.refunds is SyncResult.Failed) SyncResult.Success else card.refunds
        val financialForCombine =
            if (card.financial is SyncResult.Failed) SyncResult.Success else card.financial
        if (wallet.result is SyncResult.Failed && isWalletFailure(wallet.result)) {
            return SyncResult.Failed(SyncError.Wallet, WALLET_BANNER, wallet.result.retCode)
        }
        return firstNonSuccess(card.auth, financialForCombine, refundForCombine, wallet.result)
    }

    private fun firstNonSuccess(vararg results: SyncResult): SyncResult {
        return results.firstOrNull { it !is SyncResult.Success } ?: SyncResult.Success
    }

    private data class CardTypePull(
        val auth: SyncResult,
        val financial: SyncResult,
        val refunds: SyncResult
    ) {
        companion object {
            fun ok() = CardTypePull(SyncResult.Success, SyncResult.Success, SyncResult.Success)
        }
    }

    private data class MonthPull(
        val card: CardTypePull,
        val wallet: WalletPull,
        val points: SyncResult
    )

    private data class WalletPull(
        val result: SyncResult,
        val fundingSuccess: Boolean
    ) {
        companion object {
            fun skipped() = WalletPull(SyncResult.Success, fundingSuccess = true)
        }
    }

    private suspend fun lastSyncIfHistoryPresent(): Long? {
        return if (repo.isEmpty()) null else checkpoint?.lastSyncAt()
    }

    private fun combineSync(card: SyncResult, points: SyncResult, savedAny: Boolean): SyncResult {
        val late = listOf(card, points).firstOrNull { it !is SyncResult.Success }
        if (late is SyncResult.Failed && savedAny) {
            return SyncResult.Partial(late.reason, late.retMsg, late.retCode)
        }
        if (late is SyncResult.Failed) return late
        if (late is SyncResult.Partial) return late
        return SyncResult.Success
    }

    private suspend fun pullPointsWideOrChunked(
        credentials: Credentials,
        begin: Long,
        end: Long,
        onSaved: () -> Unit
    ): SyncResult {
        // official-v5/bybit-card/point/records.mdx: startTime/endTime optional, no max span.
        // official-v5/error.mdx: 181010 = range cannot exceed 7 days.
        // A month-or-year wide success can under-return; slice before the first call.
        if (end - begin > MonthMath.SEVEN_DAYS_MS) {
            return pullChunked(begin, end, pauseBetweenMs = 0, family = SliceFamily.Points) { from, to ->
                pullPoints(credentials, from, to, onSaved)
            }
        }
        val wide = pullPoints(credentials, begin, end, onSaved)
        return if (isRangeTooWide(wide)) {
            pullChunked(begin, end, pauseBetweenMs = 0, family = SliceFamily.Points) { from, to ->
                pullPoints(credentials, from, to, onSaved)
            }
        } else {
            wide
        }
    }

    private suspend fun pullRefunds(
        credentials: Credentials,
        begin: Long,
        end: Long,
        onSaved: () -> Unit
    ): SyncResult {
        val refund = pullType(credentials, QueryType.Refund, begin, end, onSaved)
        if (!isParamIllegalFailure(refund)) return refund
        val financial = pullType(credentials, QueryType.Financial, begin, end, onSaved)
        if (!isParamIllegalFailure(financial)) return financial
        return SyncResult.Success
    }

    private suspend fun pullChunked(
        begin: Long,
        end: Long,
        pauseBetweenMs: Long = 0,
        family: SliceFamily,
        pullWindow: suspend (Long, Long) -> SyncResult
    ): SyncResult {
        val windows = MonthMath.boundedWindows(begin, end)
        if (pauseBetweenMs > 0L) {
            var worst: SyncResult = SyncResult.Success
            for ((from, to) in windows) {
                val result = pullWindowOrSkip(family, from, to, pullWindow)
                worst = worseResult(worst, result)
                if (result is SyncResult.Failed &&
                    result.reason != SyncError.RateLimit &&
                    result.reason != SyncError.Network
                ) {
                    return result
                }
                pause(pauseBetweenMs)
            }
            return worst
        }
        return coroutineScope {
            val results = windows.map { (from, to) ->
                async { pullWindowOrSkip(family, from, to, pullWindow) }
            }.awaitAll()
            var worst: SyncResult = SyncResult.Success
            for (result in results) {
                if (result is SyncResult.Failed &&
                    result.reason != SyncError.RateLimit &&
                    result.reason != SyncError.Network
                ) {
                    return@coroutineScope result
                }
                worst = worseResult(worst, result)
            }
            worst
        }
    }

    private suspend fun pullWindowOrSkip(
        family: SliceFamily,
        from: Long,
        to: Long,
        pullWindow: suspend (Long, Long) -> SyncResult
    ): SyncResult {
        if (coverage?.shouldSkip(family, from, to, clock(), forceNetwork) == true) {
            return SyncResult.Success
        }
        val pulled = pullWindow(from, to)
        if (pulled is SyncResult.Success) {
            coverage?.markSuccess(family, from, to)
        }
        return pulled
    }

    private suspend fun pullPoints(
        credentials: Credentials,
        begin: Long,
        end: Long,
        onSaved: () -> Unit
    ): SyncResult {
        var pageNo = 1
        var pointsFetched = 0
        var saved = false
        while (true) {
            val request = PointRecordsRequest(pageNo, pageSize.coerceAtMost(50), begin, end)
            val response: PointRecordsPage = try {
                var page = client.queryPointRecords(credentials, request)
                var attempt = 0
                while (page.retCode == 10006 && attempt < rateLimitRetries) {
                    attempt += 1
                    pause(1000L * attempt)
                    page = client.queryPointRecords(credentials, request)
                }
                page
            } catch (error: IOException) {
                val why = ioExceptionBanner(error)
                return if (saved || pageNo > 1) {
                    SyncResult.Partial(SyncError.Network, why)
                } else {
                    SyncResult.Failed(SyncError.Network, why)
                }
            }
            val mapped = mapRetCode(response.retCode)
            if (mapped != null) {
                return if (saved || pageNo > 1) {
                    SyncResult.Partial(mapped, response.retMsg, response.retCode)
                } else {
                    SyncResult.Failed(mapped, response.retMsg, response.retCode)
                }
            }
            for (record in response.records) {
                when (val classified = RecordClassifier.classifyPoints(record)) {
                    ClassifyResult.Skip -> Unit
                    is ClassifyResult.Keep -> {
                        saveDraft(classified.draft, onSaved)
                        saved = true
                    }
                }
            }
            pointsFetched += response.records.size
            finishPage(
                moreRemains = hasMorePages(
                    fetched = pointsFetched,
                    pageSize = request.pageSize,
                    recordCount = response.records.size,
                    totalCount = response.totalCount
                ),
                page = pageNo
            )?.let { return it }
            pageNo += 1
        }
    }

    private suspend fun pullFunding(
        credentials: Credentials,
        begin: Long,
        end: Long,
        onSaved: () -> Unit
    ): SyncResult {
        var cursor: String? = null
        var page = 1
        var saved = false
        val fromSec = begin / 1000L
        val toSec = end / 1000L
        while (true) {
            val request = FundingHistoryRequest(fromSec, toSec, 100, cursor)
            val response: FundingPage = try {
                var body = client.queryFundingHistory(credentials, request)
                var attempt = 0
                while (body.retCode == 10006 && attempt < rateLimitRetries) {
                    attempt += 1
                    pause(1000L * attempt)
                    body = client.queryFundingHistory(credentials, request)
                }
                body
            } catch (error: IOException) {
                val why = ioExceptionBanner(error)
                return if (saved || page > 1) {
                    SyncResult.Partial(SyncError.Network, why)
                } else {
                    SyncResult.Failed(SyncError.Network, why)
                }
            }
            if (isWalletDenied(response.retCode, response.retMsg)) {
                return SyncResult.Failed(SyncError.Wallet, WALLET_BANNER, response.retCode)
            }
            val mapped = mapRetCode(response.retCode)
            if (mapped != null) {
                return if (saved || page > 1) {
                    SyncResult.Partial(mapped, response.retMsg, response.retCode)
                } else {
                    SyncResult.Failed(mapped, response.retMsg, response.retCode)
                }
            }
            for (record in response.records) {
                when (val classified = RecordClassifier.classifyFunding(record)) {
                    ClassifyResult.Skip -> Unit
                    is ClassifyResult.Keep -> {
                        saveDraft(classified.draft, onSaved)
                        saved = true
                    }
                }
            }
            val next = response.nextCursor?.takeIf { it.isNotBlank() }
            finishPage(moreRemains = next != null, page = page)?.let { return it }
            cursor = next
            page += 1
        }
    }

    private suspend fun pullTransfers(
        credentials: Credentials,
        begin: Long,
        end: Long,
        onSaved: () -> Unit
    ): SyncResult {
        var cursor: String? = null
        var page = 1
        var saved = false
        while (true) {
            val request = TransferListRequest(begin, end, 50, cursor)
            val response: TransferPage = try {
                var body = client.queryInterTransfers(credentials, request)
                var attempt = 0
                while (body.retCode == 10006 && attempt < rateLimitRetries) {
                    attempt += 1
                    pause(1000L * attempt)
                    body = client.queryInterTransfers(credentials, request)
                }
                body
            } catch (error: IOException) {
                val why = ioExceptionBanner(error)
                return if (saved || page > 1) {
                    SyncResult.Partial(SyncError.Network, why)
                } else {
                    SyncResult.Failed(SyncError.Network, why)
                }
            }
            if (isWalletDenied(response.retCode, response.retMsg)) {
                return SyncResult.Failed(SyncError.Wallet, WALLET_BANNER, response.retCode)
            }
            val mapped = mapRetCode(response.retCode)
            if (mapped != null) {
                return if (saved || page > 1) {
                    SyncResult.Partial(mapped, response.retMsg, response.retCode)
                } else {
                    SyncResult.Failed(mapped, response.retMsg, response.retCode)
                }
            }
            for (record in response.records) {
                when (val classified = RecordClassifier.classifyTransfer(record)) {
                    ClassifyResult.Skip -> Unit
                    is ClassifyResult.Keep -> {
                        saveDraft(classified.draft, onSaved)
                        saved = true
                    }
                }
            }
            val next = response.nextCursor?.takeIf { it.isNotBlank() }
            finishPage(moreRemains = next != null, page = page)?.let { return it }
            cursor = next
            page += 1
        }
    }

    private suspend fun pullType(
        credentials: Credentials,
        type: QueryType,
        begin: Long,
        end: Long,
        onSaved: () -> Unit
    ): SyncResult {
        // official-v5/bybit-card/asset-records.mdx: POST query-asset-records; type required without
        // txnId/orderNo; createBeginTime/createEndTime Unix ms; no documented max window.
        // official-v5/error.mdx: 10001 param error → split; 181010 range > 7 days.
        // Keep times on every request; do not omit them (omit ≠ 12 months).
        // Do not send a 31-day month as one call — a wide success can silently under-return.
        if (end - begin > MonthMath.SEVEN_DAYS_MS) {
            return pullChunked(begin, end, pauseBetweenMs = 0, family = SliceFamily.of(type)) { from, to ->
                pullTypeWindow(credentials, type, from, to, onSaved)
            }
        }
        val wide = pullTypeWindow(credentials, type, begin, end, onSaved)
        return if (shouldSplitCardWindow(wide)) {
            pullChunked(begin, end, pauseBetweenMs = 0, family = SliceFamily.of(type)) { from, to ->
                pullTypeWindow(credentials, type, from, to, onSaved)
            }
        } else {
            wide
        }
    }

    private suspend fun pullTypeWindow(
        credentials: Credentials,
        type: QueryType,
        begin: Long,
        end: Long,
        onSaved: () -> Unit
    ): SyncResult {
        var page = 1
        var fetched = 0
        var savedInThisType = false
        while (true) {
            val request = AssetRecordsRequest(
                type = type,
                page = page,
                limit = pageSize,
                createBeginTime = begin,
                createEndTime = end
            )
            val response: AssetRecordsPage = try {
                fetchPage(credentials, request)
            } catch (error: IOException) {
                val why = ioExceptionBanner(error)
                return if (savedInThisType || page > 1) {
                    SyncResult.Partial(SyncError.Network, why)
                } else {
                    SyncResult.Failed(SyncError.Network, why)
                }
            }
            val mapped = mapRetCode(response)
            if (mapped != null) {
                return if (savedInThisType || page > 1) {
                    SyncResult.Partial(mapped, response.retMsg, response.retCode)
                } else {
                    SyncResult.Failed(mapped, response.retMsg, response.retCode)
                }
            }
            for (record in response.records) {
                when (val classified = RecordClassifier.classify(type, record)) {
                    ClassifyResult.Skip -> Unit
                    is ClassifyResult.Keep -> {
                        saveDraft(classified.draft, onSaved)
                        savedInThisType = true
                    }
                }
            }
            fetched += response.records.size
            val servedPageSize = if (response.pageSize > 0) response.pageSize else pageSize
            finishPage(
                moreRemains = hasMorePages(
                    fetched = fetched,
                    pageSize = servedPageSize,
                    recordCount = response.records.size,
                    totalCount = response.totalCount
                ),
                page = page
            )?.let { return it }
            page += 1
        }
    }

    private fun hasMorePages(
        fetched: Int,
        pageSize: Int,
        recordCount: Int,
        totalCount: Int
    ): Boolean {
        if (recordCount <= 0) return false
        if (totalCount > 0 && fetched >= totalCount) return false
        if (recordCount >= pageSize) return true
        if (totalCount > 0 && fetched < totalCount) return true
        return false
    }

    private fun worseResult(current: SyncResult, incoming: SyncResult): SyncResult {
        if (incoming is SyncResult.Success) return current
        val softened = if (
            incoming is SyncResult.Failed &&
            (incoming.reason == SyncError.RateLimit || incoming.reason == SyncError.Network)
        ) {
            SyncResult.Partial(incoming.reason, incoming.retMsg, incoming.retCode)
        } else {
            incoming
        }
        if (current is SyncResult.Success) return softened
        if (softened is SyncResult.Failed) return softened
        return current
    }

    private fun finishPage(moreRemains: Boolean, page: Int): SyncResult? {
        if (!moreRemains) return SyncResult.Success
        if (page >= maxPages) {
            return SyncResult.Partial(SyncError.Unknown, "incomplete history")
        }
        return null
    }

    private suspend fun fetchPage(
        credentials: Credentials,
        request: AssetRecordsRequest
    ): AssetRecordsPage {
        var response = client.queryAssetRecords(credentials, request)
        var attempt = 0
        while (response.retCode == 10006 && attempt < rateLimitRetries) {
            attempt += 1
            pause(1000L * attempt)
            response = client.queryAssetRecords(credentials, request)
        }
        return response
    }

    private fun shouldSplitCardWindow(result: SyncResult): Boolean {
        val code: Int?
        val msg: String?
        when (result) {
            is SyncResult.Failed -> {
                code = result.retCode
                msg = result.retMsg
            }
            is SyncResult.Partial -> {
                code = result.retCode
                msg = result.retMsg
            }
            else -> return false
        }
        return code == 10001 ||
            code == 181010 ||
            msg.orEmpty().contains("param_illegal", ignoreCase = true) ||
            msg.orEmpty().contains("7 days", ignoreCase = true)
    }

    private fun isParamIllegalFailure(result: SyncResult): Boolean {
        val failed = result as? SyncResult.Failed ?: return false
        return failed.retCode == 10001 ||
            failed.retMsg.orEmpty().contains("param_illegal", ignoreCase = true)
    }

    private fun financialReady(result: SyncResult): Boolean {
        return result is SyncResult.Success || isParamIllegalFailure(result)
    }

    private fun isWalletFailure(result: SyncResult.Failed): Boolean {
        return result.reason == SyncError.Wallet ||
            isWalletDenied(result.retCode ?: -1, result.retMsg.orEmpty())
    }

    private fun isWalletDenied(retCode: Int, retMsg: String): Boolean {
        return retCode == 10005 || retMsg.contains("permission denied", ignoreCase = true)
    }

    private fun isRangeTooWide(result: SyncResult): Boolean {
        val code: Int?
        val msg: String?
        when (result) {
            is SyncResult.Failed -> {
                code = result.retCode
                msg = result.retMsg
            }
            is SyncResult.Partial -> {
                code = result.retCode
                msg = result.retMsg
            }
            else -> return false
        }
        return code == 181010 || msg.orEmpty().contains("7 days", ignoreCase = true)
    }

    private fun mapRetCode(page: AssetRecordsPage): SyncError? = mapRetCode(page.retCode)

    private fun mapRetCode(retCode: Int): SyncError? {
        if (retCode == 0) return null
        if (retCode == 10006) return SyncError.RateLimit
        if (retCode == 10003 || retCode == 10004 || retCode == 10005 || retCode == 33004) {
            return SyncError.Auth
        }
        return SyncError.Unknown
    }

    companion object {
        const val CARD_HISTORY_GENERATION: Int = 2
        const val FUNDING_HISTORY_GENERATION: Int = 1
    }
}
