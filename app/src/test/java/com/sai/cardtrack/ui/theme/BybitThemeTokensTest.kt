package com.sai.cardtrack.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test


class BybitThemeTokensTest {
    @Test
    fun `primary brand is bybit orange`() {
        assertEquals(Color(0xFFF7A600), Brand)
    }

    @Test
    fun `page background is bybit black`() {
        assertEquals(Color(0xFF000000), PageBg)
    }

    @Test
    fun `card surface matches bybit dark card`() {
        assertEquals(Color(0xFF16171A), CardBg)
    }

    @Test
    fun `income and expense use bybit market green and red`() {
        assertEquals(Color(0xFF20B26C), Income)
        assertEquals(Color(0xFFEF454A), Expense)
    }

    @Test
    fun `light theme uses gray canvas and white cards`() {
        assertEquals(Color(0xFFF5F5F5), LightPageBg)
        assertEquals(Color(0xFFFFFFFF), LightCardBg)
        assertEquals(Color(0xFFEEEEEE), LightAreaBg)
        assertEquals(Color(0xFFE5E5E5), LightLine)
        assertEquals(Color(0xFF121214), LightTextMain)
        assertEquals(Brand, LightCardTrackColors.brand)
        assertEquals(Color(0xFF8A5A00), LightCardTrackColors.brandText)
        assertEquals(Expense, LightCardTrackColors.expense)
        assertEquals(Income, LightCardTrackColors.income)
        assertEquals(LightTextMute, LightCardTrackColors.textMute)
        assertEquals(Color(0xFF5C6168), LightTextMute)
        assertEquals(LightBrandSoft, LightCardTrackColors.brandSoft)
        assertEquals(Color(0xFFFFF1D6), LightBrandSoft)
        assertEquals(BrandSoft, DarkCardTrackColors.brandSoft)
    }

    @Test
    fun `dark brand text stays gold while light brand text is darker`() {
        assertEquals(Brand, DarkCardTrackColors.brandText)
        assertEquals(Brand, DarkCardTrackColors.brand)
        assertEquals(Color(0xFF8A5A00), LightCardTrackColors.brandText)
        assertEquals(Brand, LightCardTrackColors.brand)
    }

    @Test
    fun `light typography uses dark ink so labels are not white on white`() {
        val type = cardTrackTypography(LightCardTrackColors)
        assertEquals(LightTextMain, type.bodyLarge.color)
        assertEquals(LightTextMain, type.bodyMedium.color)
        assertEquals(LightTextMain, type.titleLarge.color)
        assertEquals(LightTextMain, type.headlineMedium.color)
        assertEquals(LightTextMute, type.labelMedium.color)
        assertEquals(Inter, type.headlineSmall.fontFamily)
        assertEquals(Inter, type.titleSmall.fontFamily)
        assertEquals(Inter, type.bodySmall.fontFamily)
    }

    @Test
    fun `dark typography keeps white ink`() {
        val type = cardTrackTypography(DarkCardTrackColors)
        assertEquals(TextMain, type.bodyLarge.color)
        assertEquals(TextMute, type.labelMedium.color)
        assertEquals(Inter, type.headlineSmall.fontFamily)
        assertEquals(Inter, type.titleSmall.fontFamily)
        assertEquals(Inter, type.bodySmall.fontFamily)
    }
}
