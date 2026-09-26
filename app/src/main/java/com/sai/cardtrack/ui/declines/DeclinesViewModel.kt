package com.sai.cardtrack.ui.declines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sai.cardtrack.data.TransactionRepository
import com.sai.cardtrack.domain.CardMoney
import com.sai.cardtrack.domain.MonthMath
import com.sai.cardtrack.domain.Transaction
import com.sai.cardtrack.ui.AppLocale
import com.sai.cardtrack.ui.SavedUi
import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeclineRow(
    val txnId: String,
    val merchant: String,
    val amount: String,
    val reason: String,
    val date: String
)

data class DeclinesUiState(
    val monthLabel: String = "",
    val rows: List<DeclineRow> = emptyList(),
    val empty: Boolean = false
)

class DeclinesViewModel(
    private val repository: TransactionRepository,
    private val clock: () -> Long,
    private val zone: ZoneId,
    private val copy: UiCopy = UiCopy.Ru,
    private val savedState: SavedStateHandle? = null
) : ViewModel() {
    private val all = MutableStateFlow<List<Transaction>>(emptyList())
    private var yearMonth = SavedUi.month(savedState, clock, zone)
    private val _state = MutableStateFlow(DeclinesUiState())
    val state: StateFlow<DeclinesUiState> = _state
    private val dateFormatter = DateTimeFormatter.ofPattern(
        "d MMM",
        if (copy.locale == AppLocale.En) Locale.ENGLISH else Locale("ru")
    )

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

    fun prevMonth() {
        yearMonth = yearMonth.minusMonths(1)
        SavedUi.persistMonth(savedState, yearMonth)
        publish()
    }

    fun nextMonth() {
        yearMonth = yearMonth.plusMonths(1)
        SavedUi.persistMonth(savedState, yearMonth)
        publish()
    }

    private fun publish() {
        val ym = yearMonth
        val declined = MonthMath.declined(all.value, ym.year, ym.monthValue, zone)
        _state.value = DeclinesUiState(
            monthLabel = copy.monthLabel(ym),
            rows = declined.map { it.toRow() },
            empty = declined.isEmpty()
        )
    }

    private fun Transaction.toRow(): DeclineRow {
        return DeclineRow(
            txnId = txnId,
            merchant = merchantName,
            amount = CardMoney.format(paidAmount),
            reason = declinedReason.ifBlank { copy.noDeclineReason },
            date = Instant.ofEpochMilli(txnCreate).atZone(zone).format(dateFormatter)
        )
    }
}
