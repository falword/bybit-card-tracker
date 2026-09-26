package com.sai.cardtrack.ui.theme

import androidx.compose.ui.graphics.Color

/** Bybit BDS tokens taken from the live dark app / www.bybit.com. */
val Brand = Color(0xFFF7A600)
val BrandSoft = Color(0x29F7A600)
val PageBg = Color(0xFF000000)
val CardBg = Color(0xFF16171A)
val AreaBg = Color(0xFF101014)
val Line = Color(0xFF404347)
val TextMain = Color(0xFFFFFFFF)
val TextMute = Color(0xFF71757A)
val Expense = Color(0xFFEF454A)
val Income = Color(0xFF20B26C)
val OnBrand = Color(0xFF121214)

val LightPageBg = Color(0xFFF5F5F5)
val LightCardBg = Color(0xFFFFFFFF)
val LightAreaBg = Color(0xFFEEEEEE)
val LightLine = Color(0xFFE5E5E5)
val LightTextMain = Color(0xFF121214)
val LightTextMute = Color(0xFF5C6168)
val LightBrandText = Color(0xFF8A5A00)
val LightBrandSoft = Color(0xFFFFF1D6)

data class CardTrackColors(
    val brand: Color,
    val brandText: Color,
    val brandSoft: Color,
    val pageBg: Color,
    val cardBg: Color,
    val areaBg: Color,
    val line: Color,
    val textMain: Color,
    val textMute: Color,
    val expense: Color,
    val income: Color,
    val onBrand: Color
)

val DarkCardTrackColors = CardTrackColors(
    brand = Brand,
    brandText = Brand,
    brandSoft = BrandSoft,
    pageBg = PageBg,
    cardBg = CardBg,
    areaBg = AreaBg,
    line = Line,
    textMain = TextMain,
    textMute = TextMute,
    expense = Expense,
    income = Income,
    onBrand = OnBrand
)

val LightCardTrackColors = CardTrackColors(
    brand = Brand,
    brandText = LightBrandText,
    brandSoft = LightBrandSoft,
    pageBg = LightPageBg,
    cardBg = LightCardBg,
    areaBg = LightAreaBg,
    line = LightLine,
    textMain = LightTextMain,
    textMute = LightTextMute,
    expense = Expense,
    income = Income,
    onBrand = OnBrand
)
