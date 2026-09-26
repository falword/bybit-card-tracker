package com.sai.cardtrack.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.data.TransactionRepository
import com.sai.cardtrack.domain.CardMoney
import com.sai.cardtrack.domain.ExpenseCategories
import com.sai.cardtrack.domain.P2pVisibility
import com.sai.cardtrack.domain.SearchDates
import com.sai.cardtrack.domain.SearchFoundTotals
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionFilter
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSearch
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus
import com.sai.cardtrack.ui.AppLocale
import com.sai.cardtrack.ui.SavedUi
import com.sai.cardtrack.ui.UiCopy
import com.sai.cardtrack.ui.components.CategorySheetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SearchDatePreset { All, Days7, Days30, Custom }

data class SearchRow(
    val txnId: String,
    val merchant: String,
    val amount: String,
    val currency: String,
    val categoryLabel: String,
    val date: String,
    val kind: TransactionKind,
    val pending: Boolean
)

data class SearchUiState(
    val query: String = "",
    val source: TransactionSource? = null,
    val categoryId: String? = null,
    val uncategorizedOnly: Boolean = false,
    val amountMinText: String = "",
    val amountMaxText: String = "",
    val datePreset: SearchDatePreset = SearchDatePreset.All,
    val customStartUtc: Long? = null,
    val customEndUtc: Long? = null,
    val dateRangeLabel: String? = null,
    val totalAmount: String = "0.00",
    val totalCurrency: String = "",
    val totalCount: Int = 0,
    val rows: List<SearchRow> = emptyList(),
    val empty: Boolean = false,
    val emptyLabel: String = "",
    val sheet: CategorySheetState? = null
)

class SearchViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId,
    private val copy: UiCopy = UiCopy.Ru,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val debounceMs: Long = 300L,
    private val savedState: SavedStateHandle? = null,
    private val credentialsStore: CredentialsStore? = null
) : ViewModel() {
    private val all = MutableStateFlow<List<Transaction>>(emptyList())
    private val dateLocale: Locale =
        if (copy.locale == AppLocale.En) Locale.ENGLISH else Locale("ru")
    private val _state = MutableStateFlow(restore(savedState, dateLocale))
    val state: StateFlow<SearchUiState> = _state
    private var publishJob: Job? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", dateLocale)

    init {
        viewModelScope.launch {
            repository.observeAll()
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { rows ->
                    all.value = rows
                    publish()
                }
        }
    }

    fun reloadLocal() {
        viewModelScope.launch { publish() }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        savedState?.set(SavedUi.QUERY, query)
        schedulePublish()
    }

    fun onSource(source: TransactionSource?) {
        _state.value = _state.value.copy(source = source)
        savedState?.set(SavedUi.SOURCE, source?.name.orEmpty())
        viewModelScope.launch { publish() }
    }

    fun onCategory(categoryId: String?) {
        _state.value = _state.value.copy(categoryId = categoryId, uncategorizedOnly = false)
        savedState?.set(SavedUi.CATEGORY, categoryId.orEmpty())
        savedState?.set(SavedUi.UNCATEGORIZED_ONLY, false)
        viewModelScope.launch { publish() }
    }

    fun onUncategorizedOnly(enabled: Boolean) {
        _state.value = _state.value.copy(
            uncategorizedOnly = enabled,
            categoryId = if (enabled) null else _state.value.categoryId
        )
        savedState?.set(SavedUi.UNCATEGORIZED_ONLY, enabled)
        if (enabled) savedState?.set(SavedUi.CATEGORY, "")
        viewModelScope.launch { publish() }
    }

    fun onAmountMin(text: String) {
        _state.value = _state.value.copy(amountMinText = text)
        savedState?.set(SavedUi.AMOUNT_MIN, text)
        viewModelScope.launch { publish() }
    }

    fun onAmountMax(text: String) {
        _state.value = _state.value.copy(amountMaxText = text)
        savedState?.set(SavedUi.AMOUNT_MAX, text)
        viewModelScope.launch { publish() }
    }

    fun onDatePreset(preset: SearchDatePreset) {
        if (preset == SearchDatePreset.Custom) return
        _state.value = _state.value.copy(
            datePreset = preset,
            customStartUtc = null,
            customEndUtc = null,
            dateRangeLabel = null
        )
        savedState?.set(SavedUi.DATE_PRESET, preset.name)
        savedState?.set(SavedUi.DATE_START_UTC, null)
        savedState?.set(SavedUi.DATE_END_UTC, null)
        viewModelScope.launch { publish() }
    }

    fun clearFilters() {
        _state.value = _state.value.copy(
            source = null,
            categoryId = null,
            uncategorizedOnly = false,
            amountMinText = "",
            amountMaxText = "",
            datePreset = SearchDatePreset.All,
            customStartUtc = null,
            customEndUtc = null,
            dateRangeLabel = null
        )
        savedState?.set(SavedUi.SOURCE, "")
        savedState?.set(SavedUi.CATEGORY, "")
        savedState?.set(SavedUi.UNCATEGORIZED_ONLY, false)
        savedState?.set(SavedUi.AMOUNT_MIN, "")
        savedState?.set(SavedUi.AMOUNT_MAX, "")
        savedState?.set(SavedUi.DATE_PRESET, SearchDatePreset.All.name)
        savedState?.set(SavedUi.DATE_START_UTC, null)
        savedState?.set(SavedUi.DATE_END_UTC, null)
        viewModelScope.launch { publish() }
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

    fun datePickerRange(): Pair<Long, Long> {
        val start = _state.value.customStartUtc
        val end = _state.value.customEndUtc
        if (start != null && end != null) return start to end
        return SearchDates.currentMonthUtcRange(clock(), zone)
    }

    fun onCustomRange(startUtcMillis: Long, endUtcMillis: Long) {
        val start = minOf(startUtcMillis, endUtcMillis)
        val end = maxOf(startUtcMillis, endUtcMillis)
        _state.value = _state.value.copy(
            datePreset = SearchDatePreset.Custom,
            customStartUtc = start,
            customEndUtc = end,
            dateRangeLabel = SearchDates.formatRange(start, end, dateLocale)
        )
        savedState?.set(SavedUi.DATE_PRESET, SearchDatePreset.Custom.name)
        savedState?.set(SavedUi.DATE_START_UTC, start)
        savedState?.set(SavedUi.DATE_END_UTC, end)
        viewModelScope.launch { publish() }
    }

    private fun schedulePublish() {
        publishJob?.cancel()
        publishJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            publish()
        }
    }

    private suspend fun publish() {
        val ui = _state.value
        val includeP2p = credentialsStore?.p2pAccountingEnabled() ?: false
        val scoped = P2pVisibility.filter(all.value, includeP2p)
        val found = TransactionSearch.apply(scoped, ui.toFilter())
        val totals = SearchFoundTotals.of(found)
        _state.value = ui.copy(
            rows = found.map { it.toRow() },
            empty = found.isEmpty(),
            emptyLabel = if (filtersActive(ui)) copy.nothingFound else copy.noTransactions,
            totalAmount = totals.amount.toPlainString(),
            totalCurrency = totals.currency,
            totalCount = totals.count
        )
    }

    private fun filtersActive(ui: SearchUiState): Boolean {
        return ui.query.isNotBlank() ||
            ui.source != null ||
            ui.categoryId != null ||
            ui.uncategorizedOnly ||
            ui.amountMinText.isNotBlank() ||
            ui.amountMaxText.isNotBlank() ||
            ui.datePreset != SearchDatePreset.All
    }

    private fun SearchUiState.toFilter(): TransactionFilter {
        return TransactionFilter(
            query = query,
            categoryId = categoryId,
            uncategorizedOnly = uncategorizedOnly,
            source = source,
            amountMin = parseAmount(amountMinText),
            amountMax = parseAmount(amountMaxText),
            beginMs = beginMs(),
            endMs = endMs()
        )
    }

    private fun SearchUiState.beginMs(): Long? {
        return when (datePreset) {
            SearchDatePreset.All -> null
            SearchDatePreset.Days7 -> clock() - 7L * 24 * 60 * 60 * 1000
            SearchDatePreset.Days30 -> clock() - 30L * 24 * 60 * 60 * 1000
            SearchDatePreset.Custom -> customStartUtc?.let { SearchDates.beginMs(it, zone) }
        }
    }

    private fun SearchUiState.endMs(): Long? {
        if (datePreset != SearchDatePreset.Custom) return null
        return customEndUtc?.let { SearchDates.endMs(it, zone) }
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.toBigDecimalOrNull()
    }

    private fun Transaction.toRow(): SearchRow {
        return SearchRow(
            txnId = txnId,
            merchant = merchantName,
            amount = CardMoney.format(paidAmount),
            currency = CardMoney.displayCurrency(paidCurrency),
            categoryLabel = sourceLabel(),
            date = Instant.ofEpochMilli(txnCreate).atZone(zone).format(dateFormatter),
            kind = kind,
            pending = status == TransactionStatus.Pending
        )
    }

    private fun Transaction.sourceLabel(): String {
        return when (source) {
            TransactionSource.TopUp -> copy.topUp
            TransactionSource.Purchase -> copy.categoryLabel(categoryId)
            TransactionSource.Refund -> copy.refund
            TransactionSource.Cashback -> copy.cashbackLabel
            TransactionSource.Declined -> copy.declines
            TransactionSource.P2P, TransactionSource.P2PRefund -> copy.p2p
            TransactionSource.Earn -> copy.earn
        }
    }

    companion object {
        private fun restore(saved: SavedStateHandle?, locale: Locale): SearchUiState {
            if (saved == null) return SearchUiState()
            val start = saved.get<Long>(SavedUi.DATE_START_UTC)
            val end = saved.get<Long>(SavedUi.DATE_END_UTC)
            val preset = saved.get<String>(SavedUi.DATE_PRESET)?.let { raw ->
                runCatching { SearchDatePreset.valueOf(raw) }.getOrNull()
            } ?: SearchDatePreset.All
            val custom = preset == SearchDatePreset.Custom && start != null && end != null
            return SearchUiState(
                query = saved.get<String>(SavedUi.QUERY).orEmpty(),
                source = saved.get<String>(SavedUi.SOURCE)?.let { raw ->
                    raw.takeIf { it.isNotBlank() }?.let {
                        runCatching { TransactionSource.valueOf(it) }.getOrNull()
                    }
                },
                categoryId = saved.get<String>(SavedUi.CATEGORY)?.ifBlank { null },
                uncategorizedOnly = saved.get<Boolean>(SavedUi.UNCATEGORIZED_ONLY) == true,
                amountMinText = saved.get<String>(SavedUi.AMOUNT_MIN).orEmpty(),
                amountMaxText = saved.get<String>(SavedUi.AMOUNT_MAX).orEmpty(),
                datePreset = if (custom) SearchDatePreset.Custom else {
                    if (preset == SearchDatePreset.Custom) SearchDatePreset.All else preset
                },
                customStartUtc = start.takeIf { custom },
                customEndUtc = end.takeIf { custom },
                dateRangeLabel = start?.let { from ->
                    end?.let { to ->
                        if (custom) SearchDates.formatRange(from, to, locale) else null
                    }
                }
            )
        }
    }
}
