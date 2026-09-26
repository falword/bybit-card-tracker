package com.sai.cardtrack.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sai.cardtrack.ui.components.AmountText
import com.sai.cardtrack.ui.components.AmountTone
import com.sai.cardtrack.ui.components.CategoryPickerSheet
import com.sai.cardtrack.ui.components.AppTopBar
import com.sai.cardtrack.ui.components.EmptyHint
import com.sai.cardtrack.ui.components.LedgerRow
import com.sai.cardtrack.ui.components.MonthSwitcher
import com.sai.cardtrack.ui.components.ScreenScaffold
import com.sai.cardtrack.ui.components.bouncyClick
import com.sai.cardtrack.ui.components.sheen
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy

private val CompareRowHeight = 56.dp
private val CompareColWidth = 100.dp
private val CompareLabelWidth = 148.dp

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    BackHandler(enabled = state.mode == CategoriesMode.History) {
        viewModel.onBackFromHistory()
    }
    ScreenScaffold {
        CategoriesHeader(
            state = state,
            onBack = {
                if (state.mode == CategoriesMode.History) {
                    viewModel.onBackFromHistory()
                } else {
                    onBack()
                }
            },
            onPrev = { viewModel.prevMonth() },
            onNext = { viewModel.nextMonth() },
            onCompare = { viewModel.onCompareToggle() }
        )
        Spacer(Modifier.height(16.dp))
        if (state.mode != CategoriesMode.History) {
            Text(copy.expenses, style = MaterialTheme.typography.labelMedium, color = colors.textMute)
            Spacer(Modifier.height(4.dp))
            AmountText(
                state.expensesLabel + if (state.currency.isNotEmpty()) " " + state.currency else "",
                colors.expense,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(16.dp))
        }
        if (state.empty) {
            EmptyHint(state.emptyLabel, action = copy.pullToSync)
        } else {
            when (state.mode) {
                CategoriesMode.Breakdown -> BreakdownList(
                    state.rows,
                    onRowTap = { viewModel.onRowTap(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                CategoriesMode.History -> HistoryList(
                    state.expenseRows,
                    onExpenseTap = { viewModel.onExpenseTap(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                CategoriesMode.Compare -> CompareTable(
                    monthLabels = state.compareMonthLabels,
                    rows = state.compareRows,
                    onRowTap = { viewModel.onRowTap(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
        val sheet = state.sheet
        if (sheet != null) {
            CategoryPickerSheet(
                sheet = sheet,
                onDismiss = { viewModel.dismissSheet() },
                onBack = { viewModel.onCategoryBack() },
                onPicked = { viewModel.onCategoryPicked(it) }
            )
        }
    }
}

@Composable
private fun CategoriesHeader(
    state: CategoriesUiState,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCompare: () -> Unit
) {
    val colors = LocalCardTrackColors.current
    if (!state.showMonthNav) {
        AppTopBar(title = state.title, onBack = onBack)
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthSwitcher(
            monthLabel = state.monthLabel,
            onPrev = onPrev,
            onNext = onNext,
            onBack = onBack,
            modifier = Modifier.weight(1f)
        )
        if (state.showCompareAction) {
            Text(
                state.compareActionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.brandText,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onCompare)
                    .padding(horizontal = 4.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun BreakdownList(
    rows: List<CategoriesBreakdownRow>,
    onRowTap: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier) {
        itemsIndexed(rows, key = { _, it -> it.categoryId ?: "uncategorized" }) { index, row ->
            CategoryAmountRow(
                label = row.label,
                amount = row.amount,
                meta = row.percentLabel,
                share = row.share,
                onClick = { onRowTap(row.categoryId) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun HistoryList(
    rows: List<CategoryExpenseUiRow>,
    onExpenseTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier) {
        itemsIndexed(rows, key = { _, it -> it.txnId }) { _, row ->
            LedgerRow(
                title = row.merchant,
                amount = row.amount,
                tone = if (row.refund) AmountTone.Income else AmountTone.Expense,
                subtitle = row.dateLabel,
                showMark = false,
                onClick = if (row.refund) null else { { onExpenseTap(row.txnId) } },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun CategoryAmountRow(
    label: String,
    amount: String,
    meta: String?,
    share: Float,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalCardTrackColors.current
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.bouncyClick(pressedScale = 0.97f, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), color = colors.textMain)
                if (meta != null) {
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = colors.textMute)
                }
            }
            AmountText(amount, colors.expense, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        ShareBar(share)
    }
}

@Composable
private fun ShareBar(share: Float) {
    val colors = LocalCardTrackColors.current
    val target = share.coerceIn(0f, 1f)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(target) {
        grow.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 110f))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(colors.areaBg)
    ) {
        if (target > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(grow.value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(colors.brand.copy(alpha = 0.55f), colors.brand)))
                    .sheen(Color.White.copy(alpha = 0.35f), periodMillis = 6000)
            )
        }
    }
}

@Composable
private fun CompareTable(
    monthLabels: List<String>,
    rows: List<CategoriesCompareRow>,
    onRowTap: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    val horizontalScroll = rememberScrollState()
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(CompareLabelWidth)
                    .height(CompareRowHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(copy.category, style = MaterialTheme.typography.labelMedium, color = colors.textMute)
            }
            Row(Modifier.horizontalScroll(horizontalScroll)) {
                monthLabels.forEachIndexed { index, label ->
                    val current = index == monthLabels.lastIndex
                    Box(
                        Modifier
                            .width(CompareColWidth)
                            .height(CompareRowHeight),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (current) colors.brandText else colors.textMute
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.width(CompareLabelWidth)) {
                rows.forEach { row ->
                    Column(
                        Modifier
                            .height(CompareRowHeight)
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onRowTap(row.categoryId) }
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMain,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        ShareBar(row.share)
                    }
                }
            }
            Row(Modifier.horizontalScroll(horizontalScroll)) {
                monthLabels.forEachIndexed { index, _ ->
                    Column(Modifier.width(CompareColWidth)) {
                        rows.forEach { row ->
                            val cell = row.cells.getOrElse(index) { "—" }
                            val change = row.changes.getOrElse(index) { 0 }
                            val current = index == monthLabels.lastIndex
                            val color = when {
                                cell == "—" -> colors.textMute
                                change > 0 -> colors.expense
                                change < 0 -> colors.income
                                current -> colors.brandText
                                else -> colors.textMain
                            }
                            Box(
                                Modifier
                                    .height(CompareRowHeight)
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { onRowTap(row.categoryId) },
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                AmountText(
                                    cell,
                                    color,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
