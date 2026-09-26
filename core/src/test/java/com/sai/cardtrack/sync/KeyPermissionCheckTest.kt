package com.sai.cardtrack.sync

import com.sai.cardtrack.bybit.ApiKeyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyPermissionCheckTest {
    private fun info(
        readOnly: Int = 1,
        isMaster: Boolean = true,
        bitCard: List<String> = listOf("BitCard"),
        wallet: List<String> = listOf("AccountTransfer"),
        ips: List<String> = listOf("1.1.1.1"),
        deadlineDay: Int? = null
    ) = ApiKeyInfo(readOnly, isMaster, bitCard, wallet, ips, deadlineDay)

    @Test
    fun `read-only master with card is allowed`() {
        assertNull(KeyPermissionCheck.banner(info()))
    }

    @Test
    fun `write key is rejected`() {
        assertEquals(
            "Ключ не read-only. Создай новый ключ: только чтение, без торговли и вывода.",
            KeyPermissionCheck.banner(info(readOnly = 0))
        )
    }

    @Test
    fun `withdraw is rejected even if read-only flag is set`() {
        assertEquals(
            "У ключа есть право вывода. Сними Withdraw и оставь только чтение.",
            KeyPermissionCheck.banner(info(wallet = listOf("AccountTransfer", "Withdraw")))
        )
    }

    @Test
    fun `subaccount is rejected`() {
        assertEquals(
            "Ключ с субаккаунта. Создай read-only ключ на основном аккаунте.",
            KeyPermissionCheck.banner(info(isMaster = false))
        )
    }

    @Test
    fun `missing card permission uses existing card banner`() {
        assertEquals(
            bybitAccessBanner(10005, "permission denied"),
            KeyPermissionCheck.banner(info(bitCard = emptyList()))
        )
    }

    @Test
    fun `missing AccountTransfer is allowed at verify`() {
        assertNull(KeyPermissionCheck.banner(info(wallet = emptyList())))
    }

    @Test
    fun `expired key without ip bind is rejected`() {
        assertEquals(
            bybitAccessBanner(33004, ""),
            KeyPermissionCheck.banner(info(ips = emptyList(), deadlineDay = 0))
        )
    }

    @Test
    fun `dummy deadline on ip-bound key is ignored`() {
        assertNull(KeyPermissionCheck.banner(info(deadlineDay = -2)))
    }
}
