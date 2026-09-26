package com.sai.cardtrack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.ui.UiCopy

val LocalCardTrackColors = staticCompositionLocalOf { DarkCardTrackColors }
val LocalUiCopy = staticCompositionLocalOf { UiCopy.Ru }

private val BybitDarkScheme = darkColorScheme(
    primary = Brand,
    onPrimary = OnBrand,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Brand,
    background = PageBg,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = AreaBg,
    onSurfaceVariant = TextMute,
    outline = Line,
    outlineVariant = Line,
    error = Expense,
    onError = TextMain,
    secondary = Income,
    onSecondary = OnBrand
)

private val BybitLightScheme = lightColorScheme(
    primary = Brand,
    onPrimary = OnBrand,
    primaryContainer = BrandSoft,
    onPrimaryContainer = LightBrandText,
    background = LightPageBg,
    onBackground = LightTextMain,
    surface = LightCardBg,
    onSurface = LightTextMain,
    surfaceVariant = LightAreaBg,
    onSurfaceVariant = TextMute,
    outline = LightLine,
    outlineVariant = LightLine,
    error = Expense,
    onError = LightTextMain,
    secondary = Income,
    onSecondary = OnBrand
)

private val BybitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CardTrackTheme(
    dark: Boolean = true,
    copy: UiCopy = UiCopy.Ru,
    content: @Composable () -> Unit
) {
    val colors = if (dark) DarkCardTrackColors else LightCardTrackColors
    CompositionLocalProvider(
        LocalCardTrackColors provides colors,
        LocalUiCopy provides copy
    ) {
        MaterialTheme(
            colorScheme = if (dark) BybitDarkScheme else BybitLightScheme,
            typography = cardTrackTypography(colors),
            shapes = BybitShapes,
            content = content
        )
    }
}
