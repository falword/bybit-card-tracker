package com.sai.cardtrack.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.data.FundBalanceStore
import com.sai.cardtrack.data.RewardStore
import com.sai.cardtrack.data.TransactionRepository
import com.sai.cardtrack.domain.CardMoney
import com.sai.cardtrack.domain.RewardDisplay
import com.sai.cardtrack.domain.ExpenseCategories
import com.sai.cardtrack.domain.MonthMath
import com.sai.cardtrack.domain.MonthTotals
import com.sai.cardtrack.domain.RewardSnapshot
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus
import com.sai.cardtrack.sync.CardSync
import com.sai.cardtrack.sync.SyncResult
import com.sai.cardtrack.sync.displaySyncBanner
import com.sai.cardtrack.sync.ioExceptionBanner
import com.sai.cardtrack.ui.DayLabel
import com.sai.cardtrack.ui.LastSyncLabel
import com.sai.cardtrack.ui.SavedUi
import com.sai.cardtrack.ui.UiCopy
import com.sai.cardtrack.ui.components.CategorySheetState
import com.sai.cardtrack.ui.lock.isUserNotAuthenticated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class HomeRow(
    val txnId: String,
    val merchant: String,
    val amount: String,
    val currency: String,
    val kind: TransactionKind,
    val categoryLabel: String,
    val pending: Boolean,
    val uncategorized: Boolean,
    val feeLine: String? = null,
    val fxLine: String? = null,
    val cashbackLine: String? = null,
    val dayKey: String = "",
    val dayLabel: String = ""
)

data class HomeUiState(
    val monthLabel: String = "",
    val totals: MonthTotals = MonthTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, ""),
    val rows: List<HomeRow> = emptyList(),
    val banner: String? = null,
    val refreshing: Boolean = false,
    val lastSyncLabel: String? = null,
    val sheet: CategorySheetState? = null,
    val empty: Boolean = false,
    val cashbackSubtitle: String? = null,
    val accountBalance: String? = null,
    val showEarn: Boolean = false
)

class HomeViewModel(
    private val repository: TransactionRepository,
    private val credentialsStore: CredentialsStore,
    private val cardSync: CardSync,
    private val clock: () -> Long,
    private val zone: ZoneId,
    private val copy: UiCopy = UiCopy.Ru,
    private val rewardStore: RewardStore? = null,
    private val onUserNotAuthenticated: () -> Unit = {},
    private val userNotAuthenticated: (Throwable) -> Boolean = ::isUserNotAuthenticated,
    private val savedState: SavedStateHandle? = null,
    private val fundBalanceStore: FundBalanceStore? = null
) : ViewModel() {
    private val all = MutableStateFlow<List<Transaction>>(emptyList())
    private val visibleMonth = MutableStateFlow(SavedUi.month(savedState, clock, zone))
    private var lastSyncBanner: String? = null
    private val refreshMutex = Mutex()
    private val pendingRefreshes = AtomicInteger(0)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                lastSyncLabel = LastSyncLabel.format(
                    credentialsStore.lastSyncAt(),
                    clock(),
                    zone,
                    copy.locale
                )?.let { copy.lastSyncAt(it) }
            )
            repository.observeAll()
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { rows ->
                    all.value = rows
                    publish()
                }
        }
        viewModelScope.launch { refresh(showSpinner = true) }
    }

    fun onStart() {
        viewModelScope.launch { publish() }
    }

    fun reloadLocal() {
        viewModelScope.launch { publish() }
    }

    fun prevMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
        SavedUi.persistMonth(savedState, visibleMonth.value)
        viewModelScope.launch { publish() }
    }

    fun nextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
        SavedUi.persistMonth(savedState, visibleMonth.value)
        viewModelScope.launch { publish() }
    }

    fun refresh(showSpinner: Boolean = true, forceNetwork: Boolean = false) {
        if (showSpinner) {
            _state.value = _state.value.copy(refreshing = true)
        }
        pendingRefreshes.incrementAndGet()
        viewModelScope.launch {
            val acquired = refreshMutex.tryLock()
            if (!acquired) {
                finishPendingRefresh()
                return@launch
            }
            try {
                val creds = try {
                    credentialsStore.get()
                } catch (error: Exception) {
                    if (userNotAuthenticated(error)) {
                        onUserNotAuthenticated()
                        return@launch
                    }
                    throw error
                } ?: return@launch
                if (showSpinner) {
                    _state.value = _state.value.copy(refreshing = true)
                }
                try {
                    when (val result = cardSync.sync(creds, forceNetwork = forceNetwork) {
                        lastSyncBanner = null
                        publish()
                        _state.value = _state.value.copy(
                            banner = null,
                            lastSyncLabel = copy.lastSyncJustNow,
                            refreshing = false
                        )
                    }) {
                        SyncResult.Success -> {
                            credentialsStore.setLastSyncAt(clock())
                            lastSyncBanner = null
                            _state.value = _state.value.copy(banner = null, lastSyncLabel = copy.lastSyncJustNow)
                        }
                        is SyncResult.Failed -> {
                            lastSyncBanner = displaySyncBanner(result.reason, result.retMsg, result.retCode, copy.locale)
                            _state.value = _state.value.copy(banner = lastSyncBanner)
                        }
                        is SyncResult.Partial -> {
                            lastSyncBanner = displaySyncBanner(result.reason, result.retMsg, result.retCode, copy.locale)
                            _state.value = _state.value.copy(banner = lastSyncBanner)
                        }
                    }
                } catch (error: Exception) {
                    val why = if (error is IOException) {
                        ioExceptionBanner(error, copy.locale)
                    } else {
                        copy.syncInterrupted
                    }
                    lastSyncBanner = why
                    _state.value = _state.value.copy(banner = why)
                }
                publish()
            } finally {
                refreshMutex.unlock()
                finishPendingRefresh()
            }
        }
    }

    private fun finishPendingRefresh() {
        if (pendingRefreshes.decrementAndGet() == 0) {
            _state.value = _state.value.copy(refreshing = false)
        }
    }

    fun onRowTap(txnId: String) {
        viewModelScope.launch {
            val txn = repository.get(txnId) ?: return@launch
            if (txn.source != TransactionSource.Purchase) {
                _state.value = _state.value.copy(sheet = null)
                return@launch
            }
            _state.value = _state.value.copy(
                sheet = CategorySheetState(
                    txnId = txn.txnId,
                    merchant = txn.merchantName,
                    amount = CardMoney.format(txn.paidAmount) + " " + CardMoney.displayCurrency(txn.paidCurrency),
                    selectedCategoryId = txn.categoryId
                )
            )
        }
    }

    fun onCategoryPicked(categoryId: String) {
        val open = _state.value.sheet ?: return
        val drillingDown = open.browseParentId == null &&
            ExpenseCategories.childrenOf(categoryId).isNotEmpty()
        if (drillingDown) {
            _state.value = _state.value.copy(sheet = open.copy(browseParentId = categoryId))
            return
        }
        viewModelScope.launch {
            repository.setCategory(open.txnId, categoryId)
            _state.value = _state.value.copy(sheet = null)
        }
    }

    fun onCategoryBack() {
        val open = _state.value.sheet ?: return
        _state.value = _state.value.copy(sheet = open.copy(browseParentId = null))
    }

    fun dismissSheet() {
        _state.value = _state.value.copy(sheet = null)
    }

    private suspend fun publish() {
        val ym = visibleMonth.value
        val includeP2p = credentialsStore.p2pAccountingEnabled()
        val visible = MonthMath.visible(all.value, ym.year, ym.monthValue, zone, includeP2p)
        val totals = MonthMath.totals(all.value, ym.year, ym.monthValue, zone, includeP2p)
        val snapshot = rewardStore?.get()
        val fund = fundBalanceStore?.get()
        val accountBalance = fund?.let { CardMoney.format(it.amount) + " " + it.currency }
        val currentYm = YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zone))
        val showEarn = MonthMath.showEarnColumn(
            ym.year,
            ym.monthValue,
            zone,
            clock(),
            credentialsStore.earnSyncedThrough()
        ) || all.value.any {
            it.source == TransactionSource.Earn &&
                MonthMath.inMonth(it.txnCreate, ym.year, ym.monthValue, zone)
        }
        _state.value = _state.value.copy(
            monthLabel = copy.monthLabel(ym),
            totals = totals,
            rows = visible.map { it.toRow(all.value) },
            banner = lastSyncBanner,
            empty = visible.isEmpty(),
            cashbackSubtitle = if (ym == currentYm) cashbackSubtitle(snapshot) else null,
            accountBalance = accountBalance,
            showEarn = showEarn
        )
    }

    private fun cashbackSubtitle(snapshot: RewardSnapshot?): String? {
        if (snapshot == null) return null
        return RewardDisplay.subtitle(
            snapshot.tier,
            snapshot.usedLimit,
            snapshot.limit,
            snapshot.unit,
            copy.locale
        )
    }

    private fun Transaction.toRow(all: List<Transaction>): HomeRow {
        val label = when (source) {
            TransactionSource.TopUp -> copy.topUp
            TransactionSource.Purchase -> copy.categoryLabel(categoryId)
            TransactionSource.Refund -> copy.refund
            TransactionSource.Cashback -> copy.cashbackLabel
            TransactionSource.Declined -> copy.declines
            TransactionSource.P2P, TransactionSource.P2PRefund -> copy.p2p
            TransactionSource.Earn -> copy.earn
        }
        val currency = CardMoney.displayCurrency(paidCurrency)
        val feeLine = if (fees.feeSum() > BigDecimal.ZERO) {
            copy.commissionLine.format(CardMoney.format(fees.totalFees), currency)
        } else {
            null
        }
        val txnCur = fees.transactionCurrency.trim()
        val fxLine = if (
            fees.transactionAmount.isNotBlank() &&
            txnCur.isNotBlank() &&
            !paidCurrency.trim().equals(txnCur, ignoreCase = true)
        ) {
            CardMoney.format(fees.transactionAmount) + " " + txnCur
        } else {
            null
        }
        val cashbackUsd = MonthMath.cashbackUsdForPurchase(this, all)
        val cashbackLine = if (cashbackUsd != null) {
            copy.cashbackLine.format(CardMoney.format(cashbackUsd.toPlainString()), "USD")
        } else {
            null
        }
        return HomeRow(
            txnId = txnId,
            merchant = merchantName,
            amount = CardMoney.format(paidAmount),
            currency = currency,
            kind = kind,
            categoryLabel = label,
            pending = status == TransactionStatus.Pending,
            uncategorized = source == TransactionSource.Purchase && categoryId == null,
            feeLine = feeLine,
            fxLine = fxLine,
            cashbackLine = cashbackLine,
            dayKey = DayLabel.key(txnCreate, zone),
            dayLabel = DayLabel.format(txnCreate, clock(), zone, copy)
        )
    }
}
