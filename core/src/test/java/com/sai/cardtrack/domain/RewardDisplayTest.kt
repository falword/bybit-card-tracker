package com.sai.cardtrack.domain

import com.sai.cardtrack.ui.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardDisplayTest {

    @Test
    fun `maps rewards_limits_tier2 to a short localized label`() {
        assertEquals("Уровень 2", RewardDisplay.tierLabel("rewards_limits_tier2", AppLocale.Ru))
        assertEquals("Tier 2", RewardDisplay.tierLabel("rewards_limits_tier2", AppLocale.En))
        assertEquals("GOLD", RewardDisplay.tierLabel("GOLD", AppLocale.En))
        assertEquals("Уровень 2", RewardDisplay.tierLabel("rewards_limits_tier2*", AppLocale.Ru))
        assertEquals("GOLD", RewardDisplay.tierLabel("GOLD*", AppLocale.En))
    }

    @Test
    fun `limit line formats used and cap without raw unit codes`() {
        assertEquals("28 / 50 USD", RewardDisplay.limitLine("27.86", "50.00", "1"))
    }

    @Test
    fun `subtitle drops empty parts`() {
        assertEquals(
            "Уровень 2\n28 / 50 USD",
            RewardDisplay.subtitle("rewards_limits_tier2", "27.86", "50.00", "USDT", AppLocale.Ru)
        )
        assertEquals("Уровень 2", RewardDisplay.subtitle("rewards_limits_tier2", "", "", "", AppLocale.Ru))
        assertEquals(null, RewardDisplay.subtitle("", "", "", "", AppLocale.Ru))
    }
}
