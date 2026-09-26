package com.sai.cardtrack.ui.components

import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import com.sai.cardtrack.ui.theme.LocalCardTrackColors

@Composable
fun appFieldColors(): TextFieldColors {
    val colors = LocalCardTrackColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.brand,
        unfocusedBorderColor = colors.line,
        focusedLabelColor = colors.brand,
        unfocusedLabelColor = colors.textMute,
        focusedTextColor = colors.textMain,
        unfocusedTextColor = colors.textMain,
        focusedPlaceholderColor = colors.textMute,
        unfocusedPlaceholderColor = colors.textMute,
        cursorColor = colors.brand,
        focusedContainerColor = colors.cardBg,
        unfocusedContainerColor = colors.cardBg
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appDatePickerColors(): DatePickerColors {
    val colors = LocalCardTrackColors.current
    return DatePickerDefaults.colors(
        containerColor = colors.cardBg,
        titleContentColor = colors.textMain,
        headlineContentColor = colors.textMain,
        weekdayContentColor = colors.textMute,
        subheadContentColor = colors.textMute,
        yearContentColor = colors.textMain,
        currentYearContentColor = colors.brandText,
        selectedYearContentColor = colors.onBrand,
        selectedYearContainerColor = colors.brand,
        dayContentColor = colors.textMain,
        disabledDayContentColor = colors.textMute,
        selectedDayContentColor = colors.onBrand,
        selectedDayContainerColor = colors.brand,
        disabledSelectedDayContentColor = colors.onBrand.copy(alpha = 0.45f),
        disabledSelectedDayContainerColor = colors.brand.copy(alpha = 0.35f),
        todayContentColor = colors.brandText,
        todayDateBorderColor = colors.brand,
        dayInSelectionRangeContentColor = colors.textMain,
        dayInSelectionRangeContainerColor = colors.brandSoft,
        dividerColor = colors.line,
        navigationContentColor = colors.textMain
    )
}

@Composable
fun appTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = LocalCardTrackColors.current.brandText,
    disabledContentColor = LocalCardTrackColors.current.textMute
)
