package com.sai.cardtrack.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.ui.components.AmountText
import com.sai.cardtrack.ui.components.AmountTone
import com.sai.cardtrack.ui.components.AuroraBackdrop
import com.sai.cardtrack.ui.components.AuroraIntensity
import com.sai.cardtrack.ui.components.CategoryPickerSheet
import com.sai.cardtrack.ui.components.CoinPullIndicator
import com.sai.cardtrack.ui.components.EmptyHint
import com.sai.cardtrack.ui.components.FlowRing
import com.sai.cardtrack.ui.components.GlyphKind
import com.sai.cardtrack.ui.components.GraphicsMath
import com.sai.cardtrack.ui.components.LedgerAmount
import com.sai.cardtrack.ui.components.LedgerRow
import com.sai.cardtrack.ui.components.MonthSwitcher
import com.sai.cardtrack.ui.components.ShortcutGlyph
import com.sai.cardtrack.ui.components.StatusBanner
import com.sai.cardtrack.ui.theme.CardTrackColors
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenDeclines: () -> Unit,
    onOpenSubscriptions: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.onStart()
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onStart()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.refresh(forceNetwork = true) },
        state = pullState,
        indicator = { CoinPullIndicator(pullState, state.refreshing) },
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBg)
    ) {
        AuroraBackdrop(intensity = AuroraIntensity.screen)
        val listState = rememberLazyListState()
        LaunchedEffect(state.monthLabel) {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                listState.scrollToItem(0)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthSwitcher(
                    monthLabel = state.monthLabel.replaceFirstChar { it.titlecase() },
                    subtitle = state.lastSyncLabel,
                    onPrev = { viewModel.prevMonth() },
                    onNext = { viewModel.nextMonth() },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = copy.search, tint = colors.textMute)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = copy.settings, tint = colors.textMute)
                }
            }
            var lastBanner by remember { mutableStateOf("") }
            LaunchedEffect(state.banner) { state.banner?.let { lastBanner = it } }
            AnimatedVisibility(
                visible = state.banner != null,
                enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    StatusBanner(state.banner ?: lastBanner)
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            MonthSummaryCard(state)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState
            ) {
                item(key = "ledger-header") {
                    HomeLedgerHeader(
                        onOpenCategories = onOpenCategories,
                        onOpenDeclines = onOpenDeclines,
                        onOpenSubscriptions = onOpenSubscriptions
                    )
                }
                if (state.empty) {
                    item(key = "empty") { EmptyHint(copy.noTransactions, action = copy.pullToSync) }
                } else {
                    state.rows.groupBy { it.dayKey }.forEach { (dayKey, dayRows) ->
                        if (dayKey.isNotEmpty()) {
                            item(key = "day-$dayKey") {
                                DayHeader(dayRows.first().dayLabel)
                            }
                        }
                        itemsIndexed(dayRows, key = { _, it -> it.txnId }) { _, row ->
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
                                subtitleHighlight = row.uncategorized || row.pending,
                                details = listOfNotNull(row.feeLine, row.cashbackLine, row.fxLine),
                                onClick = { viewModel.onRowTap(row.txnId) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
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

/** Shortcuts and section title; scrolls off with the transaction list to free vertical space. */
@Composable
private fun HomeLedgerHeader(
    onOpenCategories: () -> Unit,
    onOpenDeclines: () -> Unit,
    onOpenSubscriptions: () -> Unit
) {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeNavLink(copy.categories, GlyphKind.Categories, onOpenCategories)
            HomeNavDivider()
            HomeNavLink(copy.declines, GlyphKind.Declines, onOpenDeclines)
            HomeNavDivider()
            HomeNavLink(copy.subscriptions, GlyphKind.Subscriptions, onOpenSubscriptions)
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colors.line.copy(alpha = 0.45f))
        Spacer(Modifier.height(10.dp))
        Text(
            copy.transactions,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
            color = colors.textMain
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun HomeNavDivider() {
    val colors = LocalCardTrackColors.current
    Box(
        Modifier
            .height(20.dp)
            .width(1.dp)
            .background(colors.line.copy(alpha = 0.55f))
    )
}

@Composable
private fun HomeNavLink(label: String, glyph: GlyphKind, onClick: () -> Unit) {
    val colors = LocalCardTrackColors.current
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ShortcutGlyph(glyph, Modifier.size(16.dp), animated = false)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.brandText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DayHeader(label: String, modifier: Modifier = Modifier) {
    val colors = LocalCardTrackColors.current
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = colors.textMute,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

internal enum class MonthSummaryLineId { Expenses, Income, Cashback, Fees, Earn }

internal data class MonthSummaryLine(
    val id: MonthSummaryLineId,
    val note: String? = null
)

internal fun monthSummaryLines(
    showEarn: Boolean,
    cashbackNote: String?
): List<MonthSummaryLine> {
    val lines = mutableListOf<MonthSummaryLine>()
    lines.add(MonthSummaryLine(MonthSummaryLineId.Expenses))
    lines.add(MonthSummaryLine(MonthSummaryLineId.Income))
    lines.add(MonthSummaryLine(MonthSummaryLineId.Cashback, cashbackNote))
    lines.add(MonthSummaryLine(MonthSummaryLineId.Fees))
    if (showEarn) lines.add(MonthSummaryLine(MonthSummaryLineId.Earn))
    return lines
}

@Composable
private fun MonthSummaryCard(state: HomeUiState, modifier: Modifier = Modifier) {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    val netLabel = LedgerAmount.monthNet(state.totals.net, state.totals.currency)
    val currency = state.totals.currency
    val cashbackNote = LedgerAmount.note(listOf(state.cashbackSubtitle))
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.cardBg)
            .border(1.dp, colors.line.copy(alpha = 0.65f), shape)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.accountBalance != null) copy.accountBalance else copy.monthTotal,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMute
                )
                Spacer(Modifier.height(8.dp))
                AmountText(
                    state.accountBalance ?: netLabel,
                    colors.brandText,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            FlowRing(
                GraphicsMath.inflowShare(state.totals.income, state.totals.expenses),
                Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.line)
        if (state.accountBalance != null) {
            MonthLine(
                label = copy.monthTotal,
                amount = netLabel,
                amountColor = monthNetColor(state.totals.net, colors.brandText, colors.income, colors.expense),
                amountStyle = MaterialTheme.typography.titleLarge,
                labelEmphasis = true,
                verticalPadding = 12.dp
            )
            HorizontalDivider(color = colors.line)
        }
        Spacer(Modifier.height(4.dp))
        for (line in monthSummaryLines(
            showEarn = state.showEarn,
            cashbackNote = cashbackNote
        )) {
            when (line.id) {
                MonthSummaryLineId.Expenses -> MonthLine(
                    label = copy.expenses,
                    amount = LedgerAmount.breakdown(state.totals.expenses, currency, inflow = false),
                    amountColor = monthStatColor(state.totals.expenses, inflow = false, colors)
                )
                MonthSummaryLineId.Income -> MonthLine(
                    label = copy.income,
                    amount = LedgerAmount.breakdown(state.totals.income, currency, inflow = true),
                    amountColor = monthStatColor(state.totals.income, inflow = true, colors)
                )
                MonthSummaryLineId.Cashback -> MonthLine(
                    label = copy.cashback,
                    amount = LedgerAmount.breakdown(state.totals.cashback, currency, inflow = true),
                    amountColor = monthStatColor(state.totals.cashback, inflow = true, colors),
                    subtitle = line.note
                )
                MonthSummaryLineId.Fees -> MonthLine(
                    label = copy.fees,
                    amount = LedgerAmount.breakdown(state.totals.fees, currency, inflow = false),
                    amountColor = monthStatColor(state.totals.fees, inflow = false, colors)
                )
                MonthSummaryLineId.Earn -> MonthLine(
                    label = copy.earn,
                    amount = LedgerAmount.breakdown(state.totals.earnYield, currency, inflow = true),
                    amountColor = monthStatColor(state.totals.earnYield, inflow = true, colors)
                )
            }
        }
    }
}

private fun monthNetColor(
    net: BigDecimal,
    brand: Color,
    income: Color,
    expense: Color
): Color {
    return when {
        net.signum() > 0 -> income
        net.signum() < 0 -> expense
        else -> brand
    }
}

private fun monthStatColor(
    amount: BigDecimal,
    inflow: Boolean,
    colors: CardTrackColors
): Color {
    if (amount.signum() == 0) return colors.textMain
    return if (inflow) colors.income else colors.expense
}

@Composable
private fun MonthLine(
    label: String,
    amount: String,
    amountColor: Color,
    modifier: Modifier = Modifier,
    amountStyle: TextStyle = MaterialTheme.typography.titleSmall,
    subtitle: String? = null,
    labelEmphasis: Boolean = false,
    verticalPadding: Dp = 8.dp
) {
    val colors = LocalCardTrackColors.current
    val spoken = listOfNotNull(label, amount, subtitle).joinToString(", ")
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (labelEmphasis) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = if (labelEmphasis) colors.textMain else colors.textMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AmountText(amount, amountColor, style = amountStyle)
    }
}
