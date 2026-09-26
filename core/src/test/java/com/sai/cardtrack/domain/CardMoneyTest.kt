package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMoneyTest {

    @Test
    fun `prefers basic usd total like bybit card details`() {
        val record = BybitAssetRecord(
            txnId = "G",
            orderNo = "G",
            side = "3",
            paidAmount = "28.000000000000000000",
            paidCurrency = "THB",
            merchName = "Grab",
            txnCreate = 1L,
            status = "1",
            tradeStatus = "1",
            basicAmount = "0.86",
            basicCurrency = "USD",
            billAmount = "0.86",
            paidFiat = "0"
        )
        val money = CardMoney.fromRecord(record)
        assertEquals("0.86", money.amount)
        assertEquals("USD", money.currency)
    }

    @Test
    fun `strips long fractional zeros to two decimals`() {
        assertEquals("13774.58", CardMoney.format("13774.580000000000000000"))
        assertEquals("303.00", CardMoney.format("303.000000000000000000"))
    }

    @Test
    fun `format whole rounds half up to an integer`() {
        assertEquals("28", CardMoney.formatWhole("27.86"))
        assertEquals("50", CardMoney.formatWhole("50.00"))
        assertEquals("0", CardMoney.formatWhole("0.20"))
        assertEquals("1", CardMoney.formatWhole(java.math.BigDecimal("0.50")))
    }

    @Test
    fun `display currency maps usd-like and empty units to usd`() {
        assertEquals("USD", CardMoney.displayCurrency("USDT"))
        assertEquals("USD", CardMoney.displayCurrency("usdc"))
        assertEquals("USD", CardMoney.displayCurrency(""))
        assertEquals("USD", CardMoney.displayCurrency("1"))
        assertEquals("THB", CardMoney.displayCurrency("THB"))
    }

    @Test
    fun `exact zero is a card check amount`() {
        assertTrue(CardMoney.isExactZero("0"))
        assertTrue(CardMoney.isExactZero("0.00"))
        assertTrue(CardMoney.isExactZero("0.000"))
        assertFalse(CardMoney.isExactZero("0.01"))
        assertFalse(CardMoney.isExactZero(""))
        assertFalse(CardMoney.isExactZero("0.0042"))
    }

    @Test
    fun `usdt paid amount is shown as usd`() {
        val record = BybitAssetRecord(
            txnId = "A",
            orderNo = "A",
            side = "3",
            paidAmount = "101.50",
            paidCurrency = "USDT",
            merchName = "Amazon",
            txnCreate = 1L,
            status = "1",
            tradeStatus = "1"
        )
        val money = CardMoney.fromRecord(record)
        assertEquals("101.50", money.amount)
        assertEquals("USD", money.currency)
    }
}
