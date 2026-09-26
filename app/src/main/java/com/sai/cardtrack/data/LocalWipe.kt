package com.sai.cardtrack.data

import android.content.Context
import java.io.File
import java.security.KeyStore

class LocalWipe(
    private val credentialsStore: CredentialsStore,
    private val rewardStore: RewardStore,
    private val fundBalanceStore: FundBalanceStore,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val deleteKeystoreAlias: (String) -> Unit,
    private val deleteDatabase: (String) -> Boolean,
    private val databaseFile: (String) -> File,
    private val clearSharedPreferences: (String) -> Unit,
    private val closeDatabase: () -> Unit = {}
) {
    constructor(
        context: Context,
        credentialsStore: CredentialsStore,
        rewardStore: RewardStore,
        fundBalanceStore: FundBalanceStore,
        budgetRepository: BudgetRepository,
        transactionRepository: TransactionRepository,
        closeDatabase: () -> Unit = {}
    ) : this(
        credentialsStore = credentialsStore,
        rewardStore = rewardStore,
        fundBalanceStore = fundBalanceStore,
        budgetRepository = budgetRepository,
        transactionRepository = transactionRepository,
        deleteKeystoreAlias = { alias -> deleteAndroidKeystoreAlias(alias) },
        deleteDatabase = { name -> context.deleteDatabase(name) },
        databaseFile = { name -> context.getDatabasePath(name) },
        clearSharedPreferences = { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        },
        closeDatabase = closeDatabase
    )

    suspend fun run() {
        credentialsStore.clear()
        rewardStore.clear()
        fundBalanceStore.clear()
        budgetRepository.clear()
        transactionRepository.clear()
        closeDatabase()
        for (alias in KEYSTORE_ALIASES) {
            try {
                deleteKeystoreAlias(alias)
            } catch (_: Exception) {
            }
        }
        deleteDatabaseArtifacts(deleteDatabase, databaseFile)
        for (name in PREFS_NAMES) {
            clearSharedPreferences(name)
        }
    }

    companion object {
        const val DATABASE_NAME: String = "cardtrack.db"
        const val ENC_SUFFIX: String = ".enc"
        const val CREDS_ALIAS: String = "cardtrack_creds_aes"
        const val DB_ALIAS: String = "cardtrack_db_aes"

        fun deleteDatabaseArtifacts(
            deleteDatabase: (String) -> Boolean,
            databaseFile: (String) -> File
        ) {
            deleteDatabase(DATABASE_NAME)
            val db = databaseFile(DATABASE_NAME)
            File(db.path + "-wal").takeIf { it.exists() }?.delete()
            File(db.path + "-shm").takeIf { it.exists() }?.delete()
            File(db.path + ENC_SUFFIX).takeIf { it.exists() }?.delete()
            if (db.exists()) {
                db.delete()
            }
        }
        val KEYSTORE_ALIASES: List<String> = listOf(CREDS_ALIAS, DB_ALIAS)
        val PREFS_NAMES: List<String> = listOf(
            "cardtrack_vault",
            "cardtrack_creds",
            "cardtrack_reward",
            "cardtrack_fund_balance",
            "cardtrack_appearance"
        )

        private fun deleteAndroidKeystoreAlias(alias: String) {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }
}
