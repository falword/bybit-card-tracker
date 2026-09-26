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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.sai.cardtrack.ui.export.ExportViewModel
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
    private val now: Long = at(9, 26, 15, 30)

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
            InMemoryCredentialsStore().also { it.setLastSyncAt(at(9, 26, 15, 4)) }
        }
        val rewards = runBlocking {
            InMemoryRewardStore().also {
                it.save(RewardSnapshot("32", "50", "USD", "tier_2", true, "0", "0"))
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
        rule.onNodeWithText("0.80 USD fee").assertExists()
        rule.onNodeWithText("Tier 2 · 32 / 50 USD").assertExists()
        rule.onNodeWithText("+32.00 USD").assertExists()
        save("home")
    }

    @Test
    fun categoriesSimple() {
        showCategories()
        rule.onNodeWithText("Housing").assertExists()
        rule.onNodeWithText("simple").assertExists()
        save("categories")
    }

    @Test
    fun categoriesFull() {
        showCategories()
        rule.onNodeWithText("full").performClick()
        settle()
        rule.onNodeWithText("Housing · Rent").assertExists()
        save("categories-full")
    }

    @Test
    fun compareSimple() {
        showCategories()
        rule.onNodeWithText("compare").performClick()
        settle()
        rule.onNodeWithText("breakdown").assertExists()
        rule.onNodeWithText("Sep").assertExists()
        save("compare")
    }

    @Test
    fun compareFull() {
        showCategories()
        rule.onNodeWithText("full").performClick()
        rule.onNodeWithText("compare").performClick()
        settle()
        rule.onNodeWithText("Housing · Rent").assertExists()
        rule.onNodeWithText("Sep").assertExists()
        save("compare-full")
    }

    @Test
    fun settings() {
        val store = runBlocking {
            InMemoryCredentialsStore().also {
                it.save(Credentials("sample-key-7F3A", "sample-secret"))
                it.setLastSyncAt(at(9, 26, 15, 4))
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
                export = ExportViewModel(sampleLedger(), UiCopy.En, store),
                onWipe = {}
            )
        }
        rule.onNodeWithText("Export CSV").performScrollTo()
        rule.onNodeWithText("Export Koinly").performScrollTo()
        rule.onNodeWithText("Export").assertExists()
        save("settings")
    }

    private fun showCategories() {
        show {
            CategoriesScreen(
                viewModel = CategoriesViewModel(categoryLedger(), { now }, zone, UiCopy.En),
                onBack = {}
            )
        }
    }

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            Box(Modifier.size(390.dp, 844.dp).testTag("shot")) {
                CardTrackTheme(dark = true, copy = UiCopy.En, content = content)
            }
        }
        settle()
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(1_600)
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
        purchase(
            repo,
            "demo-market",
            26,
            18,
            "Harbor Market",
            "36.20",
            "groceries",
            FeeBreakdown(totalFees = "0.80")
        )
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
                paidAmount = "640.00",
                paidCurrency = "USDT",
                merchantName = "Card top-up",
                txnCreate = at(9, 20, 14),
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
                paidAmount = "16000",
                paidCurrency = "POINT",
                merchantName = "Cashback",
                txnCreate = at(9, 15, 12),
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
                txnCreate = at(9, 8, 6),
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
        fees: FeeBreakdown = FeeBreakdown(),
        month: Int = 9
    ) {
        repo.upsertFromSync(
            TransactionDraft(
                txnId = id,
                orderNo = id,
                kind = TransactionKind.Expense,
                paidAmount = amount,
                paidCurrency = "USDT",
                merchantName = merchant,
                txnCreate = at(month, day, hour),
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

    private fun categoryLedger(): InMemoryTransactionRepository = runBlocking {
        val repo = InMemoryTransactionRepository()
        sampleCategories.forEach { row ->
            purchase(
                repo,
                row.id,
                row.day,
                12,
                row.merchant,
                row.amount,
                row.categoryId,
                month = row.month
            )
        }
        repo
    }

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    }

    private data class SampleSpend(
        val month: Int,
        val day: Int,
        val id: String,
        val merchant: String,
        val amount: String,
        val categoryId: String
    )

    private val sampleCategories: List<SampleSpend> = listOf(
        SampleSpend(9, 2, "sep-rent", "Cedar Court", "682.00", "rent"),
        SampleSpend(9, 3, "sep-electronics", "North Circuit", "240.00", "electronics"),
        SampleSpend(9, 4, "sep-hotel", "Hotel Lumen", "210.00", "hotels"),
        SampleSpend(9, 5, "sep-groceries", "Harbor Market", "186.40", "groceries"),
        SampleSpend(9, 6, "sep-tickets", "Skyline Rail", "95.00", "tickets"),
        SampleSpend(9, 7, "sep-clothes", "Linen and Co", "86.00", "clothes"),
        SampleSpend(9, 8, "sep-fitness", "Iron Yard", "64.00", "fitness"),
        SampleSpend(9, 9, "sep-dining", "Mesa Table", "64.20", "dining"),
        SampleSpend(9, 10, "sep-software", "Fieldnote", "49.00", "software"),
        SampleSpend(9, 11, "sep-taxi", "City Cab", "42.30", "taxi"),
        SampleSpend(9, 12, "sep-fuel", "Pump 4", "36.00", "fuel"),
        SampleSpend(9, 13, "sep-gifts", "Ribbon Shop", "35.00", "gifts"),
        SampleSpend(9, 14, "sep-coffee", "Northwind Coffee", "28.50", "coffee"),
        SampleSpend(9, 15, "sep-games", "Arcade North", "24.00", "games"),
        SampleSpend(9, 16, "sep-pharmacy", "Green Cross", "22.40", "pharmacy"),
        SampleSpend(9, 17, "sep-mobile", "Halo Mobile", "19.00", "mobile"),
        SampleSpend(9, 18, "sep-hobbies", "Paper Lantern", "18.00", "hobbies"),
        SampleSpend(9, 19, "sep-transit", "Line 12 Metro", "18.40", "transit"),
        SampleSpend(9, 20, "sep-stream", "StreamBox", "15.99", "streaming"),
        SampleSpend(8, 2, "aug-rent", "Cedar Court", "980.00", "rent"),
        SampleSpend(8, 3, "aug-electronics", "North Circuit", "48.00", "electronics"),
        SampleSpend(8, 4, "aug-clothes", "Linen and Co", "19.00", "clothes"),
        SampleSpend(8, 5, "aug-hotel", "Hotel Lumen", "410.00", "hotels"),
        SampleSpend(8, 6, "aug-groceries", "Harbor Market", "310.00", "groceries"),
        SampleSpend(8, 7, "aug-dining", "Mesa Table", "48.00", "dining"),
        SampleSpend(8, 8, "aug-coffee", "Northwind Coffee", "22.00", "coffee"),
        SampleSpend(8, 9, "aug-taxi", "City Cab", "70.00", "taxi"),
        SampleSpend(8, 10, "aug-fuel", "Pump 4", "40.00", "fuel"),
        SampleSpend(8, 11, "aug-transit", "Line 12 Metro", "22.00", "transit"),
        SampleSpend(8, 12, "aug-fitness", "Iron Yard", "40.00", "fitness"),
        SampleSpend(8, 13, "aug-pharmacy", "Green Cross", "18.00", "pharmacy"),
        SampleSpend(8, 14, "aug-software", "Fieldnote", "12.00", "software"),
        SampleSpend(8, 15, "aug-stream", "StreamBox", "15.99", "streaming"),
        SampleSpend(8, 16, "aug-games", "Arcade North", "60.00", "games"),
        SampleSpend(8, 17, "aug-gifts", "Ribbon Shop", "12.00", "gifts"),
        SampleSpend(8, 18, "aug-mobile", "Halo Mobile", "19.00", "mobile"),
        SampleSpend(7, 4, "jul-rent", "Cedar Court", "700.00", "rent"),
        SampleSpend(7, 8, "jul-groceries", "Harbor Market", "200.00", "groceries"),
        SampleSpend(7, 12, "jul-tickets", "Skyline Rail", "150.00", "tickets"),
        SampleSpend(7, 18, "jul-taxi", "City Cab", "30.00", "taxi"),
        SampleSpend(6, 3, "jun-rent", "Cedar Court", "640.00", "rent"),
        SampleSpend(6, 9, "jun-groceries", "Harbor Market", "180.00", "groceries"),
        SampleSpend(6, 14, "jun-hotel", "Hotel Lumen", "90.00", "hotels"),
        SampleSpend(6, 21, "jun-stream", "StreamBox", "15.99", "streaming")
    )
}
