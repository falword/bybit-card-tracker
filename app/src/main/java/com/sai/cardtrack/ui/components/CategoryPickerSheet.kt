package com.sai.cardtrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.domain.ExpenseCategories
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import com.sai.cardtrack.ui.theme.LocalUiCopy

data class CategorySheetState(
    val txnId: String,
    val merchant: String,
    val amount: String,
    val selectedCategoryId: String?,
    val browseParentId: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    sheet: CategorySheetState,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onPicked: (String) -> Unit
) {
    val colors = LocalCardTrackColors.current
    val copy = LocalUiCopy.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.cardBg
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                sheet.merchant,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textMain
            )
            AmountText(
                sheet.amount,
                colors.expense,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                if (sheet.browseParentId == null) {
                    copy.pickType
                } else {
                    ExpenseCategories.labelFor(sheet.browseParentId, copy.locale) ?: copy.pickType
                },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textMute,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (sheet.browseParentId != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.brandSoft)
                        .clickable(role = Role.Button, onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = colors.brandText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        copy.backToCategories,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.brandText
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            val choices = ExpenseCategories.pickerChoices(
                sheet.browseParentId,
                copy.locale,
                copy.generalCategory
            )
            for (i in choices.indices step 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CategoryChip(
                        choices[i].label,
                        isCategoryChipSelected(choices[i].id, sheet),
                        Modifier.weight(1f)
                    ) {
                        onPicked(choices[i].id)
                    }
                    if (i + 1 < choices.size) {
                        CategoryChip(
                            choices[i + 1].label,
                            isCategoryChipSelected(choices[i + 1].id, sheet),
                            Modifier.weight(1f)
                        ) {
                            onPicked(choices[i + 1].id)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun isCategoryChipSelected(choiceId: String, sheet: CategorySheetState): Boolean {
    val selectedId = sheet.selectedCategoryId ?: return false
    if (choiceId == selectedId) return true
    if (sheet.browseParentId != null) return false
    return ExpenseCategories.find(selectedId)?.parentId == choiceId
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalCardTrackColors.current
    val background by animateColorAsState(if (selected) colors.brand else colors.areaBg, label = "chipBg")
    val content by animateColorAsState(if (selected) colors.onBrand else colors.textMain, label = "chipFg")
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        ),
        color = content,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    )
}
