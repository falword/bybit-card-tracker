package com.sai.cardtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.ui.theme.LocalCardTrackColors

enum class AmountTone { Income, Expense, Neutral }

@Composable
fun LedgerRow(
    title: String,
    amount: String,
    tone: AmountTone,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleHighlight: Boolean = false,
    details: List<String> = emptyList(),
    showMark: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalCardTrackColors.current
    val amountColor = when (tone) {
        AmountTone.Income -> colors.income
        AmountTone.Expense -> colors.expense
        AmountTone.Neutral -> colors.textMain
    }
    val spoken = listOfNotNull(title, amount, subtitle).joinToString(", ")
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .then(
                if (onClick != null) {
                    Modifier.bouncyClick(pressedScale = 0.97f, onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showMark) {
                MerchantMark(title)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                    color = colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (subtitleHighlight) colors.brandText else colors.textMute,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                details.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            AmountText(amount, amountColor, style = MaterialTheme.typography.titleSmall)
        }
        if (showDivider) {
            HorizontalDivider(color = colors.line)
        }
    }
}

@Composable
fun MerchantMark(name: String) {
    val colors = LocalCardTrackColors.current
    val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
    val dark = colors.isDark()
    val hue = remember(name) { GraphicsMath.merchantHue(name) }
    val tint = Color.hsv(hue, 0.55f, if (dark) 0.95f else 0.62f)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (dark) 0.14f else 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.titleSmall,
            color = tint.copy(alpha = if (dark) 0.92f else 0.78f)
        )
    }
}
