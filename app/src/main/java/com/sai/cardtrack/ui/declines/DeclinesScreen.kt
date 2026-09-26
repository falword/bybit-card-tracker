package com.sai.cardtrack.ui.declines

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import com.sai.cardtrack.ui.components.AmountTone
import com.sai.cardtrack.ui.components.EmptyHint
import com.sai.cardtrack.ui.components.LedgerRow
import com.sai.cardtrack.ui.components.MonthSwitcher
import com.sai.cardtrack.ui.components.ScreenScaffold
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy

@Composable
fun DeclinesScreen(
    viewModel: DeclinesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    ScreenScaffold {
        MonthSwitcher(
            monthLabel = state.monthLabel,
            onPrev = { viewModel.prevMonth() },
            onNext = { viewModel.nextMonth() },
            onBack = onBack,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            copy.declinesHint,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMute
        )
        Spacer(Modifier.height(8.dp))
        if (state.empty) {
            EmptyHint(copy.noDeclines, action = copy.pullToSync)
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(state.rows, key = { it.txnId }) { row ->
                    LedgerRow(
                        title = row.merchant,
                        amount = row.amount,
                        tone = AmountTone.Expense,
                        subtitle = row.reason,
                        details = listOf(row.date)
                    )
                }
            }
        }
    }
}
