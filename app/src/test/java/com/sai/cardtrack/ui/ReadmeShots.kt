package com.sai.cardtrack.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sai.cardtrack.bybit.AssetRecordsPage
import com.sai.cardtrack.bybit.AssetRecordsRequest
import com.sai.cardtrack.bybit.BybitCardClient
import com.sai.cardtrack.data.AppearancePrefs
import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.data.InMemoryCredentialsStore
import com.sai.cardtrack.data.InMemoryFundBalanceStore
import com.sai.cardtrack.data.InMemoryRewardStore
import com.sai.cardtrack.data.InMemoryTransactionRepository
import com.sai.cardtrack.domain.CategoryOrigin
import com.sai.cardtrack.domain.FeeBreakdown
import com.sai.cardtrack.domain.FundBalanceSnapshot
import com.sai.cardtrack.domain.RewardSnapshot
import com.sai.cardtrack.domain.TransactionDraft
import com.sai.cardtrack.domain.TransactionKind
import com.sai.cardtrack.domain.TransactionSource
import com.sai.cardtrack.domain.TransactionStatus
import com.sai.cardtrack.sync.CardSync
import com.sai.cardtrack.sync.VerifyAndSaveCredentials
import com.sai.cardtrack.ui.categories.CategoriesScreen
import com.sai.cardtrack.ui.categories.CategoriesViewModel
import com.sai.cardtrack.ui.credentials.CredentialsScreen
import com.sai.cardtrack.ui.credentials.CredentialsViewModel
import com.sai.cardtrack.ui.home.HomeScreen
import com.sai.cardtrack.ui.home.HomeViewModel
import com.sai.cardtrack.ui.theme.CardTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Writes docs/screenshots from the real Compose screens.
 * Sample merchants and amounts are fictional.
 *
 * ./gradlew :app:testDebugUnitTest -PreadmeShots=true --tests com.sai.cardtrack.ui.ReadmeShots
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class ReadmeShots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone: ZoneId = ZoneId.of("Asia/Dubai")
    private val now: Long = at(26, 15, 30)

    @Before
    fun mainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        rule.mainClock.autoAdvance = false
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun home() {
        val repo = sampleLedger()
        val store = runBlocking {
            InMemoryCredentialsStore().also { it.setLastSyncAt(at(26, 15, 4)) }
        }
        val rewards = runBlocking {
            InMemoryRewardStore().also {
                it.save(RewardSnapshot("420", "3000", "USD", "tier_1", true, "0", "0"))
            }
        }
        val funds = runBlocking {
            InMemoryFundBalanceStore().also {
                it.save(FundBalanceSnapshot("1240.80", "USD", now))
            }
        }
        show {
            HomeScreen(
                viewModel = homeViewModel(repo, store, rewards, funds),
                onOpenSettings = {},
                onOpenSearch = {},
                onOpenCategories = {},
                onOpenDeclines = {},
                onOpenSubscriptions = {}
            )
        }
        rule.onNodeWithText("Northwind Coffee").assertExists()
        save("home")
    }

    @Test
    fun categories() {
        show {
            CategoriesScreen(
                viewModel = CategoriesViewModel(sampleLedger(), { now }, zone, UiCopy.En),
                onBack = {}
            )
        }
        rule.onNodeWithText("Food").assertExists()
        save("categories")
    }

    @Test
    fun settings() {
        val store = runBlocking {
            InMemoryCredentialsStore().also {
                it.save(Credentials("sample-key-7F3A", "sample-secret"))
                it.setLastSyncAt(at(26, 15, 4))
            }
        }
        val viewModel = CredentialsViewModel(
            verify = VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)
                },
                store
            ),
            store = store,
            clock = { now },
            zone = zone,
            copy = UiCopy.En
        )
        show {
            CredentialsScreen(
                viewModel = viewModel,
                firstLaunch = false,
                onSaved = {},
                onBack = {},
                appearance = AppearancePrefs(LanguageMode.En, ThemeMode.Dark),
                onLanguage = {},
                onTheme = {},
                onWipe = {}
            )
        }
        rule.onNodeWithText("Delete key and data").assertExists()
        save("settings")
    }

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            Box(Modifier.size(390.dp, 844.dp).testTag("shot")) {
                CardTrackTheme(dark = true, copy = UiCopy.En, content = content)
            }
        }
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
    }

    private fun save(name: String) {
        val dir = File(System.getProperty("readmeShotsDir") ?: error("readmeShotsDir is not set"))
        dir.mkdirs()
        val content = rule.activity.findViewById<View>(android.R.id.content)
        check(content.width > 0 && content.height > 0)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
    }

    private fun homeViewModel(
        repo: InMemoryTransactionRepository,
        store: InMemoryCredentialsStore,
        rewards: InMemoryRewardStore,
        funds: InMemoryFundBalanceStore
    ): HomeViewModel {
        val client = object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)
        }
        return HomeViewModel(
            repository = repo,
            credentialsStore = store,
            cardSync = CardSync(client, repo, { now }, zone),
            clock = { now },
            zone = zone,
            copy = UiCopy.En,
            rewardStore = rewards,
            fundBalanceStore = funds
        )
    }

    private fun sampleLedger(): InMemoryTransactionRepository = runBlocking {
        val repo = InMemoryTransactionRepository()
        purchase(repo, "demo-coffee", 26, 9, "Northwind Coffee", "4.50", "coffee")
        purchase(repo, "demo-market", 26, 18, "Harbor Market", "36.20", "groceries")
        purchase(repo, "demo-metro", 25, 8, "Line 12 Metro", "2.75", "transit")
        purchase(repo, "demo-books", 22, 16, "Paper Lantern", "18.00", "hobbies")
        purchase(repo, "demo-stream", 18, 11, "StreamBox", "9.99", "streaming")
        purchase(
            repo,
            "demo-hotel",
            12,
            20,
            "Hotel Lumen",
            "86.00",
            "hotels",
            FeeBreakdown(totalFees = "1.20")
        )
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "demo-topup",
                orderNo = "demo-topup",
                kind = TransactionKind.Income,
                paidAmount = "200.00",
                paidCurrency = "USDT",
                merchantName = "Card top-up",
                txnCreate = at(20, 14),
                status = TransactionStatus.Success,
                bybitSide = "1",
                source = TransactionSource.TopUp
            ),
            now
        )
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "demo-cashback",
                orderNo = "demo-cashback",
                kind = TransactionKind.Income,
                paidAmount = "1200",
                paidCurrency = "POINT",
                merchantName = "Cashback",
                txnCreate = at(15, 12),
                status = TransactionStatus.Success,
                bybitSide = "1",
                source = TransactionSource.Cashback
            ),
            now
        )
        repo.upsertFromSync(
            TransactionDraft(
                txnId = "demo-earn",
                orderNo = "demo-earn",
                kind = TransactionKind.Income,
                paidAmount = "1.25",
                paidCurrency = "USDT",
                merchantName = "Easy Earn",
                txnCreate = at(8, 6),
                status = TransactionStatus.Success,
                bybitSide = "1",
                source = TransactionSource.Earn
            ),
            now
        )
        repo
    }

    private suspend fun purchase(
        repo: InMemoryTransactionRepository,
        id: String,
        day: Int,
        hour: Int,
        merchant: String,
        amount: String,
        categoryId: String,
        fees: FeeBreakdown = FeeBreakdown()
    ) {
        repo.upsertFromSync(
            TransactionDraft(
                txnId = id,
                orderNo = id,
                kind = TransactionKind.Expense,
                paidAmount = amount,
                paidCurrency = "USDT",
                merchantName = merchant,
                txnCreate = at(day, hour),
                status = TransactionStatus.Success,
                bybitSide = "1",
                source = TransactionSource.Purchase,
                categoryId = categoryId,
                categoryOrigin = CategoryOrigin.User,
                fees = fees
            ),
            now
        )
    }

    private fun at(day: Int, hour: Int, minute: Int = 0): Long {
        return ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    }
}
