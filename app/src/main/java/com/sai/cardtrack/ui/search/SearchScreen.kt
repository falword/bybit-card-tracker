package com.sai.cardtrack.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sai.cardtrack.domain.ExpenseCategories
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.ui.UiCopy
import com.sai.cardtrack.ui.components.AmountText
import com.sai.cardtrack.ui.components.AmountTone
import com.sai.cardtrack.ui.components.AppTopBar
import com.sai.cardtrack.ui.components.CategoryPickerSheet
import com.sai.cardtrack.ui.components.EmptyHint
import com.sai.cardtrack.ui.components.LedgerAmount
import com.sai.cardtrack.ui.components.LedgerRow
import com.sai.cardtrack.ui.components.ScreenScaffold
import com.sai.cardtrack.ui.components.appDatePickerColors
import com.sai.cardtrack.ui.components.appFieldColors
import com.sai.cardtrack.ui.components.appTextButtonColors
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.reloadLocal()
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.reloadLocal()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    var showDateRange by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val fieldColors = appFieldColors()
    val active = activeFilterChips(state, copy, viewModel)
    val filtersOn = active.isNotEmpty() || state.query.isNotBlank()
    ScreenScaffold(consumeIme = true) {
        AppTopBar(title = copy.search, onBack = onBack)
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(copy.searchHint, style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = fieldColors,
            textStyle = MaterialTheme.typography.bodyMedium
        )
        if (active.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(active, key = { it.key }) { chip ->
                    FilterChip(
                        selected = true,
                        onClick = chip.onClear,
                        label = { Text(chip.label, style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = copy.resetFilters)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.brand,
                            selectedLabelColor = colors.onBrand,
                            selectedTrailingIconColor = colors.onBrand
                        )
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { filtersOpen = !filtersOpen },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                copy.filters,
                style = MaterialTheme.typography.bodySmall,
                color = if (filtersOpen || active.isNotEmpty()) colors.brandText else colors.textMute,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (filtersOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = copy.filters,
                tint = if (filtersOpen || active.isNotEmpty()) colors.brandText else colors.textMute
            )
        }
        if (filtersOpen) {
            FilterChipRow(
                items = sourceChips(copy),
                selectedKey = state.source?.name.orEmpty(),
                onSelect = { key ->
                    viewModel.onSource(
                        key.takeIf { it.isNotEmpty() }?.let { TransactionSource.valueOf(it) }
                    )
                }
            )
            Spacer(Modifier.height(4.dp))
            FilterChipRow(
                items = dateChips(copy, state.dateRangeLabel),
                selectedKey = state.datePreset.name,
                onSelect = { key ->
                    val preset = SearchDatePreset.valueOf(key)
                    if (preset == SearchDatePreset.Custom) {
                        showDateRange = true
                    } else {
                        viewModel.onDatePreset(preset)
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            FilterChipRow(
                items = categoryChips(copy),
                selectedKey = if (state.uncategorizedOnly) UNCATEGORIZED_CHIP else state.categoryId.orEmpty(),
                onSelect = { key ->
                    if (key == UNCATEGORIZED_CHIP) {
                        viewModel.onUncategorizedOnly(true)
                    } else {
                        viewModel.onCategory(key.ifEmpty { null })
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.amountMinText,
                    onValueChange = viewModel::onAmountMin,
                    placeholder = { Text(copy.amountMin, style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = state.amountMaxText,
                    onValueChange = viewModel::onAmountMax,
                    placeholder = { Text(copy.amountMax, style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        if (showDateRange) {
            val pickerRange = viewModel.datePickerRange()
            SearchDateRangeDialog(
                startUtc = pickerRange.first,
                endUtc = pickerRange.second,
                onConfirm = { start, end ->
                    viewModel.onCustomRange(start, end)
                    showDateRange = false
                },
                onDismiss = { showDateRange = false }
            )
        }
        if (!state.empty) {
            SearchFoundTotalLine(
                label = copy.searchTotal,
                amount = state.totalAmount,
                currency = state.totalCurrency
            )
        }
        Spacer(Modifier.height(4.dp))
        if (state.empty) {
            EmptyHint(
                text = state.emptyLabel,
                action = if (filtersOn) copy.resetFilters else null,
                onAction = if (filtersOn) {
                    {
                        viewModel.onQueryChange("")
                        viewModel.clearFilters()
                    }
                } else {
                    null
                }
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(state.rows, key = { it.txnId }) { row ->
                    val meta = buildString {
                        append(row.categoryLabel)
                        if (row.pending) {
                            append(" · ")
                            append(copy.pending)
                        }
                    }
                    LedgerRow(
                        title = row.merchant,
                        amount = LedgerAmount.signed(
                            row.amount,
                            row.currency,
                            income = row.kind == TransactionKind.Income
                        ),
                        tone = if (row.kind == TransactionKind.Income) {
                            AmountTone.Income
                        } else {
                            AmountTone.Expense
                        },
                        subtitle = meta,
                        subtitleHighlight = row.pending || row.categoryLabel == copy.uncategorized,
                        details = listOf(row.date),
                        onClick = { viewModel.onRowTap(row.txnId) }
                    )
                }
            }
        }
    }
    val sheet = state.sheet
    if (sheet != null) {
        CategoryPickerSheet(
            sheet = sheet,
            onDismiss = viewModel::dismissSheet,
            onBack = viewModel::onCategoryBack,
            onPicked = viewModel::onCategoryPicked
        )
    }
}

private data class SearchChip(val key: String, val label: String)

private data class ActiveFilterChip(
    val key: String,
    val label: String,
    val onClear: () -> Unit
)

private fun activeFilterChips(
    state: SearchUiState,
    copy: UiCopy,
    viewModel: SearchViewModel
): List<ActiveFilterChip> {
    return buildList {
        state.source?.let { source ->
            val label = sourceChips(copy).firstOrNull { it.key == source.name }?.label ?: source.name
            add(ActiveFilterChip("source", label) { viewModel.onSource(null) })
        }
        if (state.uncategorizedOnly) {
            add(ActiveFilterChip("uncat", copy.uncategorized) { viewModel.onUncategorizedOnly(false) })
        }
        state.categoryId?.let { id ->
            add(ActiveFilterChip("cat", copy.categoryLabel(id)) { viewModel.onCategory(null) })
        }
        if (state.datePreset != SearchDatePreset.All) {
            val label = state.dateRangeLabel
                ?: dateChips(copy, null).firstOrNull { it.key == state.datePreset.name }?.label
                ?: copy.customPeriod
            add(ActiveFilterChip("date", label) { viewModel.onDatePreset(SearchDatePreset.All) })
        }
        if (state.amountMinText.isNotBlank() || state.amountMaxText.isNotBlank()) {
            val label = listOf(state.amountMinText, state.amountMaxText)
                .filter { it.isNotBlank() }
                .joinToString("–")
            add(
                ActiveFilterChip("amount", label) {
                    viewModel.onAmountMin("")
                    viewModel.onAmountMax("")
                }
            )
        }
    }
}

@Composable
private fun FilterChipRow(
    items: List<SearchChip>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    val colors = LocalCardTrackColors.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.key }) { chip ->
            FilterChip(
                selected = chip.key == selectedKey,
                onClick = { onSelect(chip.key) },
                label = {
                    Text(chip.label, style = MaterialTheme.typography.labelMedium)
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.cardBg,
                    labelColor = colors.textMain,
                    selectedContainerColor = colors.brand,
                    selectedLabelColor = colors.onBrand
                )
            )
        }
    }
}

private fun sourceChips(copy: UiCopy): List<SearchChip> {
    return listOf(
        SearchChip("", copy.filterAll),
        SearchChip(TransactionSource.Purchase.name, copy.filterPurchases),
        SearchChip(TransactionSource.TopUp.name, copy.topUp),
        SearchChip(TransactionSource.Refund.name, copy.refund),
        SearchChip(TransactionSource.Declined.name, copy.declines)
    )
}

private const val UNCATEGORIZED_CHIP: String = "uncategorized"

private fun categoryChips(copy: UiCopy): List<SearchChip> {
    return listOf(
        SearchChip("", copy.filterAll),
        SearchChip(UNCATEGORIZED_CHIP, copy.uncategorized)
    ) + ExpenseCategories.PARENTS.map { parent ->
        SearchChip(parent.id, copy.categoryLabel(parent.id))
    }
}

private fun dateChips(copy: UiCopy, customLabel: String?): List<SearchChip> {
    return listOf(
        SearchChip(SearchDatePreset.All.name, copy.filterAll),
        SearchChip(SearchDatePreset.Days7.name, copy.last7Days),
        SearchChip(SearchDatePreset.Days30.name, copy.last30Days),
        SearchChip(SearchDatePreset.Custom.name, customLabel ?: copy.customPeriod)
    )
}

@Composable
private fun SearchFoundTotalLine(
    label: String,
    amount: String,
    currency: String
) {
    val colors = LocalCardTrackColors.current
    val value = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val income = value.signum() < 0
    val body = value.abs().toPlainString()
    val signed = when {
        value.signum() > 0 -> "−$body"
        value.signum() < 0 -> "+$body"
        else -> body
    }
    val text = if (currency.isBlank()) signed else "$signed $currency"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMute
        )
        AmountText(
            text,
            color = when {
                income -> colors.income
                value.signum() > 0 -> colors.expense
                else -> colors.textMain
            },
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDateRangeDialog(
    startUtc: Long?,
    endUtc: Long?,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val copy = LocalUiCopy.current
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startUtc,
        initialSelectedEndDateMillis = endUtc
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = pickerState.selectedStartDateMillis
                    val end = pickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        onConfirm(start, end)
                    }
                },
                enabled = pickerState.selectedStartDateMillis != null &&
                    pickerState.selectedEndDateMillis != null,
                colors = appTextButtonColors()
            ) {
                Text(copy.save, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = appTextButtonColors()) {
                Text(copy.cancel, style = MaterialTheme.typography.labelLarge)
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = LocalCardTrackColors.current.cardBg
        )
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.height(460.dp),
            colors = appDatePickerColors(),
            title = {
                Text(
                    copy.customPeriod,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp)
                )
            }
        )
    }
}
