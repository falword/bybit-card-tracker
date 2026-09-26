package com.sai.cardtrack

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sai.cardtrack.bybit.BybitRequestFactory
import com.sai.cardtrack.bybit.OkHttpBybitCardClient
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.data.KeystoreCredentialsStore
import com.sai.cardtrack.data.LegacyEspKeys
import com.sai.cardtrack.data.PrefsFundBalanceStore
import com.sai.cardtrack.data.PrefsRewardStore
import com.sai.cardtrack.data.PrefsSliceCoverageStore
import com.sai.cardtrack.data.RoomBudgetRepository
import com.sai.cardtrack.data.RoomTransactionRepository
import com.sai.cardtrack.data.db.AppDatabase
import com.sai.cardtrack.data.db.EncryptedDb
import com.sai.cardtrack.sync.CardSync
import com.sai.cardtrack.sync.VerifyAndSaveCredentials
import com.sai.cardtrack.util.AndroidLog
import java.time.ZoneId

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val log = AndroidLog()
    private val http = OkHttpBybitCardClient.defaultHttp()
    private val factory = BybitRequestFactory(
        clock = { System.currentTimeMillis() },
        log = log
    )
    val client = OkHttpBybitCardClient(
        http,
        factory,
        log,
        alternateBaseUrls = listOf("https://api.bytick.com")
    )
    private val vault = appContext.getSharedPreferences(
        KeystoreCredentialsStore.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val rewardStore = PrefsRewardStore(
        appContext.getSharedPreferences(PrefsRewardStore.PREFS_NAME, Context.MODE_PRIVATE)
    )
    val fundBalanceStore = PrefsFundBalanceStore(
        appContext.getSharedPreferences(PrefsFundBalanceStore.PREFS_NAME, Context.MODE_PRIVATE)
    )
    val credentialsStore: CredentialsStore
    val verify: VerifyAndSaveCredentials
    lateinit var repository: RoomTransactionRepository
        private set
    lateinit var budgetRepository: RoomBudgetRepository
        private set
    lateinit var cardSync: CardSync
        private set
    private var database: AppDatabase? = null
    private var databaseOpened = false

    init {
        val master = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val esp = EncryptedSharedPreferences.create(
            appContext,
            LegacyEspKeys.PREFS_NAME,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        credentialsStore = KeystoreCredentialsStore(
            prefs = vault,
            secretKey = { KeystoreCredentialsStore.openOrCreateAesKey() },
            esp = esp
        )
        verify = VerifyAndSaveCredentials(client, credentialsStore)
    }

    fun openDatabase() {
        if (databaseOpened) return
        val db = Room.databaseBuilder(appContext, AppDatabase::class.java, EncryptedDb.DATABASE_NAME)
            .openHelperFactory(EncryptedDb.open(EncryptedDb.passphrase(appContext, vault)))
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        database = db
        repository = RoomTransactionRepository(db.transactionDao())
        budgetRepository = RoomBudgetRepository(db.budgetDao())
        cardSync = CardSync(
            client,
            repository,
            { System.currentTimeMillis() },
            ZoneId.systemDefault(),
            checkpoint = credentialsStore,
            rewardStore = rewardStore,
            fundBalanceStore = fundBalanceStore,
            coverage = PrefsSliceCoverageStore(vault)
        )
        databaseOpened = true
    }

    fun closeDatabase() {
        credentialsStore.evictCachedSecrets()
        val db = database
        database = null
        databaseOpened = false
        db?.close()
    }
}
