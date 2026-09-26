package com.sai.cardtrack.data

import com.sai.cardtrack.domain.Budget
import com.sai.cardtrack.domain.FundBalanceSnapshot
import com.sai.cardtrack.domain.RewardSnapshot
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionStatus
import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalWipeTest {

    @Test
    fun `wipe leaves null creds and empty observeAll`() = runTest {
        val dir = kotlin.io.path.createTempDirectory("cardtrack-wipe").toFile()
        try {
            val db = File(dir, "cardtrack.db").apply { writeText("db") }
            val wal = File(dir, "cardtrack.db-wal").apply { writeText("wal") }
            val shm = File(dir, "cardtrack.db-shm").apply { writeText("shm") }
            val aliases = mutableSetOf("cardtrack_creds_aes", "cardtrack_db_aes", "keep_me")
            val prefs = mutableMapOf(
                "cardtrack_vault" to mutableMapOf("api_key" to "vault"),
                "cardtrack_creds" to mutableMapOf("api_key" to "esp"),
                "cardtrack_reward" to mutableMapOf("tier" to "GOLD"),
                "cardtrack_fund_balance" to mutableMapOf("amount" to "13.00"),
                "cardtrack_appearance" to mutableMapOf("theme_mode" to "Dark")
            )
            val credentials = InMemoryCredentialsStore()
            credentials.save(Credentials("k", "s"))
            credentials.setLastSyncAt(9L)
            val rewards = InMemoryRewardStore()
            rewards.save(
                RewardSnapshot("1", "2", "USDT", "GOLD", false, "0", "0")
            )
            val fund = InMemoryFundBalanceStore()
            fund.save(FundBalanceSnapshot("13.00", "USD", 1L))
            val budgets = InMemoryBudgetRepository()
            budgets.upsert(Budget(2026, 9, null, "10.00"))
            val repo = InMemoryTransactionRepository()
            repo.upsertFromSync(
                TransactionDraft(
                    txnId = "TXN1",
                    orderNo = "ORD1",
                    kind = TransactionKind.Expense,
                    paidAmount = "10.00",
                    paidCurrency = "USDT",
                    merchantName = "Amazon",
                    txnCreate = 100L,
                    status = TransactionStatus.Pending,
                    bybitSide = "3"
                ),
                syncedAt = 1L
            )

            LocalWipe(
                credentialsStore = credentials,
                rewardStore = rewards,
                fundBalanceStore = fund,
                budgetRepository = budgets,
                transactionRepository = repo,
                deleteKeystoreAlias = { aliases.remove(it) },
                deleteDatabase = { name -> File(dir, name).delete() },
                databaseFile = { name -> File(dir, name) },
                clearSharedPreferences = { name -> prefs.getValue(name).clear() }
            ).run()

            assertNull(credentials.get())
            assertNull(credentials.lastSyncAt())
            assertNull(rewards.get())
            assertNull(fund.get())
            assertEquals(0, budgets.observe(2026, 9).first().size)
            assertEquals(0, repo.observeAll().first().size)
            assertFalse(db.exists())
            assertFalse(wal.exists())
            assertFalse(shm.exists())
            assertFalse(aliases.contains("cardtrack_creds_aes"))
            assertFalse(aliases.contains("cardtrack_db_aes"))
            assertTrue(aliases.contains("keep_me"))
            assertTrue(prefs.getValue("cardtrack_vault").isEmpty())
            assertTrue(prefs.getValue("cardtrack_creds").isEmpty())
            assertTrue(prefs.getValue("cardtrack_reward").isEmpty())
            assertTrue(prefs.getValue("cardtrack_fund_balance").isEmpty())
            assertTrue(prefs.getValue("cardtrack_appearance").isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `wipe deletes leftover encrypted db sidecar`() = runTest {
        val dir = kotlin.io.path.createTempDirectory("cardtrack-wipe-enc").toFile()
        try {
            val enc = File(dir, "cardtrack.db.enc").apply { writeText("enc") }
            LocalWipe(
                credentialsStore = InMemoryCredentialsStore(),
                rewardStore = InMemoryRewardStore(),
                fundBalanceStore = InMemoryFundBalanceStore(),
                budgetRepository = InMemoryBudgetRepository(),
                transactionRepository = InMemoryTransactionRepository(),
                deleteKeystoreAlias = {},
                deleteDatabase = { name -> File(dir, name).delete() },
                databaseFile = { name -> File(dir, name) },
                clearSharedPreferences = {}
            ).run()
            assertFalse(enc.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `wipe closes the database before deleting files`() = runTest {
        val dir = kotlin.io.path.createTempDirectory("cardtrack-wipe-close").toFile()
        try {
            File(dir, "cardtrack.db").writeText("db")
            val events = mutableListOf<String>()
            LocalWipe(
                credentialsStore = InMemoryCredentialsStore(),
                rewardStore = InMemoryRewardStore(),
                fundBalanceStore = InMemoryFundBalanceStore(),
                budgetRepository = InMemoryBudgetRepository(),
                transactionRepository = InMemoryTransactionRepository(),
                deleteKeystoreAlias = {},
                deleteDatabase = { name ->
                    events += "delete"
                    File(dir, name).delete()
                },
                databaseFile = { name -> File(dir, name) },
                clearSharedPreferences = {},
                closeDatabase = { events += "close" }
            ).run()
            assertEquals(listOf("close", "delete"), events)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `wipe ignores missing keystore aliases and sidecars`() = runTest {
        val dir = kotlin.io.path.createTempDirectory("cardtrack-wipe-missing").toFile()
        try {
            LocalWipe(
                credentialsStore = InMemoryCredentialsStore(),
                rewardStore = InMemoryRewardStore(),
                fundBalanceStore = InMemoryFundBalanceStore(),
                budgetRepository = InMemoryBudgetRepository(),
                transactionRepository = InMemoryTransactionRepository(),
                deleteKeystoreAlias = { error("missing $it") },
                deleteDatabase = { false },
                databaseFile = { name -> File(dir, name) },
                clearSharedPreferences = {}
            ).run()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `wipe copy is exact`() {
        assertEquals("Удалить ключ и данные", UiCopy.Ru.wipe)
        assertEquals("Delete key and data", UiCopy.En.wipe)
        assertEquals(
            "Удалить ключ и все операции с этого устройства?",
            UiCopy.Ru.wipeConfirm
        )
        assertEquals(
            "Delete the key and all transactions from this device?",
            UiCopy.En.wipeConfirm
        )
        assertEquals(
            "Ключ и история удалены с устройства. Отзови ключ в Bybit → API.",
            UiCopy.Ru.wipeDone
        )
        assertEquals(
            "The key and history were deleted on this device. Revoke the key in Bybit → API.",
            UiCopy.En.wipeDone
        )
    }
}
