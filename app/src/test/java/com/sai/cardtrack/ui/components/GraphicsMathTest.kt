package com.sai.cardtrack.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class GraphicsMathTest {

    @Test
    fun `same merchant keeps its colour regardless of case and padding`() {
        assertEquals(GraphicsMath.merchantHue("Netflix"), GraphicsMath.merchantHue("  NETFLIX "))
    }

    @Test
    fun `merchant colour stays on the colour wheel`() {
        val hues = listOf("Apple", "Uber", "Яндекс Еда", "7-Eleven").map(GraphicsMath::merchantHue)
        assertTrue(hues.all { it >= 0f && it < 360f })
    }

    @Test
    fun `nameless merchant falls back to the brand hue`() {
        assertEquals(GraphicsMath.BRAND_HUE, GraphicsMath.merchantHue("   "))
    }

    @Test
    fun `ring splits by the inflow share of all money moved`() {
        assertEquals(0.25f, GraphicsMath.inflowShare(BigDecimal("25"), BigDecimal("75"))!!, 0.0001f)
    }

    @Test
    fun `quiet month has no ring`() {
        assertNull(GraphicsMath.inflowShare(BigDecimal.ZERO, BigDecimal("0.00")))
    }
}
