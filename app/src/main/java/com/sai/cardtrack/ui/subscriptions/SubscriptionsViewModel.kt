package com.sai.cardtrack.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sai.cardtrack.data.TransactionRepository
import com.sai.cardtrack.domain.CardMoney
import com.sai.cardtrack.domain.SubscriptionFeed
import com.sai.cardtrack.domain.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import com.sai.cardtrack.ui.AppLocale
import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SubscriptionRow(
    val merchantKey: String,
    val merchant: String,
    val amount: String,
    val nextOn: String
)

data class SubscriptionsUiState(
    val monthLabel: String = "",
    val totalLabel: String = "",
    val currency: String = "",
    val shareLabel: String = "",
    val rows: List<SubscriptionRow> = emptyList(),
    val empty: Boolean = false
)

class SubscriptionsViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId,
    private val copy: UiCopy = UiCopy.Ru,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val all = MutableStateFlow<List<Transaction>>(emptyList())
    private val _state = MutableStateFlow(SubscriptionsUiState())
    val state: StateFlow<SubscriptionsUiState> = _state
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

    private fun publish() {
        val month = YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zone))
        val entries = SubscriptionFeed.list(all.value, month, zone)
        val spend = SubscriptionFeed.monthSpend(all.value, month, zone)
        val sharePercent = spend.shareOfMonth()
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString() + "%"
        _state.value = SubscriptionsUiState(
            monthLabel = copy.monthLabel(month),
            totalLabel = CardMoney.format(spend.total.toPlainString()),
            currency = spend.currency,
            shareLabel = copy.subscriptionsShareOfExpenses(sharePercent),
            rows = entries.map { entry ->
                val date = Instant.ofEpochMilli(entry.atMs).atZone(zone).format(dateFormatter)
                SubscriptionRow(
                    merchantKey = entry.key,
                    merchant = entry.merchant,
                    amount = entry.amount,
                    nextOn = date
                )
            },
            empty = entries.isEmpty()
        )
    }
}
