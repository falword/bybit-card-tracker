package com.sai.cardtrack.ui.subscriptions

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sai.cardtrack.ui.components.AmountText
import com.sai.cardtrack.ui.components.AmountTone
import com.sai.cardtrack.ui.components.AppTopBar
import com.sai.cardtrack.ui.components.EmptyHint
import com.sai.cardtrack.ui.components.LedgerRow
import com.sai.cardtrack.ui.components.ScreenScaffold
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy

@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    ScreenScaffold {
        AppTopBar(title = copy.subscriptions, onBack = onBack)
        Text(
            copy.subscriptionsHint,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMute
        )
        Spacer(Modifier.height(8.dp))
        if (!state.empty) {
            Text(
                state.monthLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMute
            )
            Spacer(Modifier.height(8.dp))
            Text(copy.subscriptionsThisMonth, style = MaterialTheme.typography.labelMedium, color = colors.textMute)
            Spacer(Modifier.height(4.dp))
            AmountText(
                state.totalLabel + if (state.currency.isNotEmpty()) " " + state.currency else "",
                colors.expense,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                state.shareLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMute
            )
            Spacer(Modifier.height(16.dp))
        }
        if (state.empty) {
            EmptyHint(copy.noSubscriptions, action = copy.pullToSync)
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(state.rows, key = { it.merchantKey }) { row ->
                    LedgerRow(
                        title = row.merchant,
                        amount = row.amount,
                        tone = AmountTone.Expense,
                        subtitle = row.nextOn
                    )
                }
            }
        }
    }
}
