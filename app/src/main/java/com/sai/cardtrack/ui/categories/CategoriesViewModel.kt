package com.sai.cardtrack.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sai.cardtrack.data.TransactionRepository
import com.sai.cardtrack.domain.CardMoney
import com.sai.cardtrack.domain.CategoryGrain
import com.sai.cardtrack.domain.CategorySpend
import com.sai.cardtrack.domain.ExpenseCategories
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.ui.AppLocale
import com.sai.cardtrack.ui.components.CategorySheetState
import com.sai.cardtrack.ui.SavedUi
import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CategoriesMode { Breakdown, Compare, History }

data class CategoriesBreakdownRow(
    val categoryId: String?,
    val label: String,
    val amount: String,
    val percentLabel: String,
    val share: Float
)

data class CategoryExpenseUiRow(
    val txnId: String,
    val merchant: String,
    val amount: String,
    val dateLabel: String,
    val refund: Boolean
)

data class CategoriesCompareRow(
    val categoryId: String?,
    val label: String,
    val share: Float,
    val cells: List<String>,
    val changes: List<Int> = emptyList()
)

data class CategoriesUiState(
    val monthLabel: String = "",
    val mode: CategoriesMode = CategoriesMode.Breakdown,
    val expensesLabel: String = "",
    val currency: String = "",
    val empty: Boolean = false,
    val emptyLabel: String = UiCopy.Ru.noExpenses,
    val compareActionLabel: String = UiCopy.Ru.compare,
    val showMonthNav: Boolean = true,
    val showCompareAction: Boolean = true,
    val title: String = UiCopy.Ru.categories,
    val simplified: Boolean = true,
    val grainActionLabel: String = UiCopy.Ru.simplified,
    val sheet: CategorySheetState? = null,
    val rows: List<CategoriesBreakdownRow> = emptyList(),
    val expenseRows: List<CategoryExpenseUiRow> = emptyList(),
    val compareMonthLabels: List<String> = emptyList(),
    val compareRows: List<CategoriesCompareRow> = emptyList()
)

class CategoriesViewModel(
    private val repository: TransactionRepository,
    private val clock: () -> Long,
    private val zone: ZoneId,
    private val copy: UiCopy = UiCopy.Ru,
    private val savedState: SavedStateHandle? = null
) : ViewModel() {
    private val all = MutableStateFlow<List<Transaction>>(emptyList())
    private var yearMonth = SavedUi.month(savedState, clock, zone)
    private var mode: CategoriesMode = restoreMode(savedState)
    private var grain: CategoryGrain = restoreGrain(savedState)
    private var historyCategoryId: String? = savedState?.get<String>(SavedUi.HISTORY_CATEGORY)?.ifBlank { null }
    private var listFromCompare: Boolean = savedState?.get<Boolean>(SavedUi.HISTORY_FROM_COMPARE) == true
    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state
    private val dateFormatter = DateTimeFormatter.ofPattern(
        "d MMM",
        if (copy.locale == AppLocale.En) Locale.ENGLISH else Locale("ru")
    )

    init {
        if (mode == CategoriesMode.History && historyCategoryId == null) {
            mode = CategoriesMode.Breakdown
        }
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

    fun prevMonth() {
        if (mode == CategoriesMode.History) return
        yearMonth = yearMonth.minusMonths(1)
        persist()
        publish()
    }

    fun nextMonth() {
        if (mode == CategoriesMode.History) return
        yearMonth = yearMonth.plusMonths(1)
        persist()
        publish()
    }

    fun onGrainToggle() {
        if (mode == CategoriesMode.History) return
        grain = if (grain == CategoryGrain.Parent) CategoryGrain.Assigned else CategoryGrain.Parent
        persist()
        publish()
    }

    fun onCompareToggle() {
        if (mode == CategoriesMode.History) return
        mode = if (mode == CategoriesMode.Compare) {
            CategoriesMode.Breakdown
        } else {
            CategoriesMode.Compare
        }
        persist()
        publish()
    }

    fun onRowTap(categoryId: String?) {
        listFromCompare = mode == CategoriesMode.Compare
        historyCategoryId = categoryId
        mode = CategoriesMode.History
        persist()
        publish()
    }

    fun onBackFromHistory() {
        listFromCompare = false
        historyCategoryId = null
        mode = CategoriesMode.Breakdown
        persist()
        publish()
    }

    fun onExpenseTap(txnId: String) {
        viewModelScope.launch {
            val txn = repository.get(txnId) ?: return@launch
            if (txn.source != TransactionSource.Purchase) return@launch
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

    private fun persist() {
        SavedUi.persistMonth(savedState, yearMonth)
        savedState?.set(SavedUi.MODE, mode.name)
        savedState?.set(SavedUi.HISTORY_CATEGORY, historyCategoryId.orEmpty())
        savedState?.set(SavedUi.GRAIN, grain.name)
        savedState?.set(SavedUi.HISTORY_FROM_COMPARE, listFromCompare)
    }

    private fun publish() {
        val ym = yearMonth
        val transactions = all.value
        val breakdown = CategorySpend.monthBreakdown(transactions, ym, zone, copy.locale, grain)
        val compare = CategorySpend.compare(transactions, ym, zone, copy.locale, grain)
        val expenseFrom = if (listFromCompare) CategorySpend.compareStart(ym) else ym
        val expenses = CategorySpend.expenses(transactions, historyCategoryId, zone, expenseFrom, ym, grain)
        val inHistory = mode == CategoriesMode.History
        val empty = when (mode) {
            CategoriesMode.Breakdown -> breakdown.rows.isEmpty()
            CategoriesMode.Compare -> compare.rows.isEmpty()
            CategoriesMode.History -> expenses.isEmpty()
        }
        _state.value = CategoriesUiState(
            monthLabel = copy.monthLabel(ym),
            mode = mode,
            expensesLabel = breakdown.total.toPlainString(),
            currency = when (mode) {
                CategoriesMode.Breakdown -> breakdown.currency
                CategoriesMode.Compare -> compare.currency
                CategoriesMode.History -> breakdown.currency
            },
            empty = empty,
            emptyLabel = copy.noExpenses,
            compareActionLabel = if (mode == CategoriesMode.Compare) copy.toBreakdown else copy.compare,
            showMonthNav = !inHistory,
            showCompareAction = !inHistory,
            title = if (inHistory) categoryTitle(historyCategoryId) else copy.categories,
            simplified = grain == CategoryGrain.Parent,
            grainActionLabel = if (grain == CategoryGrain.Parent) copy.detailed else copy.simplified,
            sheet = _state.value.sheet,
            rows = breakdown.rows.map { row ->
                CategoriesBreakdownRow(
                    categoryId = row.categoryId,
                    label = row.label,
                    amount = CardMoney.format(row.amount.toPlainString()),
                    percentLabel = percentLabel(row.share),
                    share = row.share.toFloat()
                )
            },
            expenseRows = expenses.map { row ->
                CategoryExpenseUiRow(
                    txnId = row.txnId,
                    merchant = row.merchantName,
                    amount = CardMoney.format(row.amount.toPlainString()),
                    dateLabel = Instant.ofEpochMilli(row.txnCreate).atZone(zone).format(dateFormatter),
                    refund = row.refund
                )
            },
            compareMonthLabels = compare.months.map { copy.shortMonth(it) },
            compareRows = compare.rows.map { row ->
                CategoriesCompareRow(
                    categoryId = row.categoryId,
                    label = row.label,
                    share = row.share.toFloat(),
                    cells = row.amounts.map { amount ->
                        if (amount == null) "—" else CardMoney.format(amount.toPlainString())
                    },
                    changes = row.amounts.mapIndexed { index, amount ->
                        val older = row.amounts.drop(index + 1).firstOrNull { it != null }
                        when {
                            amount == null || older == null -> 0
                            amount > older -> 1
                            amount < older -> -1
                            else -> 0
                        }
                    }
                )
            }
        )
    }

    private fun categoryTitle(categoryId: String?): String {
        return ExpenseCategories.labelFor(categoryId, copy.locale) ?: copy.uncategorized
    }

    private fun percentLabel(share: BigDecimal): String {
        return share.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%"
    }

    companion object {
        private fun restoreMode(saved: SavedStateHandle?): CategoriesMode {
            return saved?.get<String>(SavedUi.MODE)?.let { raw ->
                runCatching { CategoriesMode.valueOf(raw) }.getOrNull()
            } ?: CategoriesMode.Breakdown
        }

        private fun restoreGrain(saved: SavedStateHandle?): CategoryGrain {
            return saved?.get<String>(SavedUi.GRAIN)?.let { raw ->
                runCatching { CategoryGrain.valueOf(raw) }.getOrNull()
            } ?: CategoryGrain.Parent
        }
    }
}
