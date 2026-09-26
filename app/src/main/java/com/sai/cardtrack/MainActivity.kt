package com.sai.cardtrack

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sai.cardtrack.data.EncryptedDbUnreadableException
import com.sai.cardtrack.data.LocalWipe
import com.sai.cardtrack.ui.AppearanceResolve
import com.sai.cardtrack.ui.UiCopy
import com.sai.cardtrack.ui.categories.CategoriesScreen
import com.sai.cardtrack.ui.categories.CategoriesViewModel
import com.sai.cardtrack.ui.credentials.CredentialsScreen
import com.sai.cardtrack.ui.credentials.CredentialsViewModel
import com.sai.cardtrack.ui.export.ExportViewModel
import com.sai.cardtrack.ui.declines.DeclinesScreen
import com.sai.cardtrack.ui.declines.DeclinesViewModel
import com.sai.cardtrack.ui.home.HomeScreen
import com.sai.cardtrack.ui.home.HomeViewModel
import com.sai.cardtrack.ui.lock.LockGate
import com.sai.cardtrack.ui.lock.LockViewModel
import com.sai.cardtrack.ui.lock.clearVaultSecrets
import com.sai.cardtrack.ui.lock.deviceUnlockAvailable
import com.sai.cardtrack.ui.lock.isKeyPermanentlyInvalidated
import com.sai.cardtrack.ui.lock.isUserNotAuthenticated
import com.sai.cardtrack.ui.lock.launchBiometricUnlock
import com.sai.cardtrack.ui.search.SearchScreen
import com.sai.cardtrack.ui.search.SearchViewModel
import com.sai.cardtrack.ui.subscriptions.SubscriptionsScreen
import com.sai.cardtrack.ui.subscriptions.SubscriptionsViewModel
import com.sai.cardtrack.ui.components.BannerTone
import com.sai.cardtrack.ui.components.PageLoading
import com.sai.cardtrack.ui.components.StatusBanner
import com.sai.cardtrack.ui.theme.CardTrackTheme
import com.sai.cardtrack.ui.theme.LocalCardTrackColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val keyResetSignal = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CardTrackApp
        val container = app.container
        val appearanceStore = app.appearance
        setContent {
            val appearance by appearanceStore.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val locale = AppearanceResolve.locale(appearance.language, Locale.getDefault().language)
            val dark = AppearanceResolve.dark(appearance.theme, systemDark)
            val copy = remember(locale) { UiCopy(locale) }
            SideEffect {
                val bar = if (dark) {
                    SystemBarStyle.dark(Color.BLACK)
                } else {
                    SystemBarStyle.light(Color.parseColor("#F5F5F5"), Color.BLACK)
                }
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                window.setBackgroundDrawable(ColorDrawable(if (dark) Color.BLACK else Color.parseColor("#F5F5F5")))
            }
            CardTrackTheme(dark = dark, copy = copy) {
                val colors = LocalCardTrackColors.current
                val wipeScope = rememberCoroutineScope()
                var startRoute by remember { mutableStateOf<String?>(null) }
                var showKeyReset by remember { mutableStateOf(false) }
                var showWipeDone by remember { mutableStateOf(false) }
                var dbReady by remember { mutableStateOf(false) }
                var dbError by remember { mutableStateOf(false) }
                var dbRetry by remember { mutableIntStateOf(0) }
                var dbSession by remember { mutableIntStateOf(0) }
                var credentialsGeneration by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    var invalidated = false
                    startRoute = withContext(Dispatchers.IO) {
                        try {
                            if (container.credentialsStore.get() == null) "credentials" else "home"
                        } catch (error: Exception) {
                            when {
                                isKeyPermanentlyInvalidated(error) -> {
                                    clearVaultSecrets(this@MainActivity)
                                    invalidated = true
                                    "credentials"
                                }
                                isUserNotAuthenticated(error) -> "home"
                                else -> throw error
                            }
                        }
                    }
                    if (invalidated) showKeyReset = true
                }
                val lockVm: LockViewModel = viewModel(
                    factory = factory {
                        LockViewModel(
                            hasCredentials = {
                                try {
                                    container.credentialsStore.get() != null
                                } catch (error: Exception) {
                                    when {
                                        isKeyPermanentlyInvalidated(error) -> {
                                            clearVaultSecrets(this@MainActivity)
                                            keyResetSignal.value = true
                                            false
                                        }
                                        isUserNotAuthenticated(error) -> true
                                        else -> throw error
                                    }
                                }
                            },
                            clock = { System.currentTimeMillis() },
                            biometricAvailable = { deviceUnlockAvailable(this@MainActivity) }
                        )
                    }
                )
                fun openCredentialsWithoutDb(keyReset: Boolean = false, wipeDone: Boolean = false) {
                    container.closeDatabase()
                    dbReady = false
                    dbError = false
                    showKeyReset = keyReset
                    showWipeDone = wipeDone
                    credentialsGeneration++
                    startRoute = "credentials"
                    lockVm.unlock()
                }
                LaunchedEffect(Unit) {
                    keyResetSignal.collect { reset ->
                        if (reset) {
                            openCredentialsWithoutDb(keyReset = true)
                        }
                    }
                }
                val locked by lockVm.lockedState.collectAsStateWithLifecycle()
                LaunchedEffect(locked) {
                    if (locked) {
                        container.closeDatabase()
                        dbReady = false
                    }
                }
                val resolvedStart = startRoute
                if (resolvedStart == null) {
                    PageLoading()
                } else {
                    LockGate(
                        viewModel = lockVm,
                        onOpen = {
                            launchBiometricUnlock(
                                activity = this@MainActivity,
                                credentialsStore = container.credentialsStore,
                                lockViewModel = lockVm,
                                onInvalidated = { openCredentialsWithoutDb(keyReset = true) },
                                onUnlocked = { dbRetry++ }
                            )
                        }
                    ) {
                        if (resolvedStart == "credentials") {
                            var retryCredentialsSubmit by remember { mutableStateOf<(() -> Unit)?>(null) }
                            val vm: CredentialsViewModel = viewModel(
                                key = viewModelKey("credentials", locale.name, credentialsGeneration),
                                factory = factory {
                                    CredentialsViewModel(
                                        container.verify,
                                        container.credentialsStore,
                                        copy = copy,
                                        deviceSecure = { deviceUnlockAvailable(this@MainActivity) },
                                        onUserNotAuthenticated = {
                                            launchBiometricUnlock(
                                                activity = this@MainActivity,
                                                credentialsStore = container.credentialsStore,
                                                lockViewModel = lockVm,
                                                onInvalidated = {
                                                    openCredentialsWithoutDb(keyReset = true)
                                                },
                                                onUnlocked = { retryCredentialsSubmit?.invoke() }
                                            )
                                        },
                                        userNotAuthenticated = ::isUserNotAuthenticated
                                    )
                                }
                            )
                            retryCredentialsSubmit = { vm.submit() }
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .background(colors.pageBg)
                            ) {
                                if (showKeyReset) {
                                    StatusBanner(
                                        copy.keyProtectionReset,
                                        tone = BannerTone.Danger,
                                        modifier = Modifier
                                            .statusBarsPadding()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                } else if (showWipeDone) {
                                    StatusBanner(
                                        copy.wipeDone,
                                        tone = BannerTone.Danger,
                                        modifier = Modifier
                                            .statusBarsPadding()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                                CredentialsScreen(
                                    viewModel = vm,
                                    firstLaunch = true,
                                    onSaved = {
                                        showKeyReset = false
                                        showWipeDone = false
                                        startRoute = "home"
                                        dbRetry++
                                    }
                                )
                            }
                        } else {
                            LaunchedEffect(dbRetry) {
                                if (dbReady || dbError) return@LaunchedEffect
                                try {
                                    container.openDatabase()
                                    dbSession++
                                    dbReady = true
                                } catch (error: Exception) {
                                    when {
                                        isUserNotAuthenticated(error) -> {
                                            launchBiometricUnlock(
                                                activity = this@MainActivity,
                                                credentialsStore = container.credentialsStore,
                                                lockViewModel = lockVm,
                                                onInvalidated = { openCredentialsWithoutDb(keyReset = true) },
                                                onUnlocked = { dbRetry++ }
                                            )
                                        }
                                        error is EncryptedDbUnreadableException -> dbError = true
                                        else -> throw error
                                    }
                                }
                            }
                            if (dbError) {
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .background(colors.pageBg)
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    StatusBanner(copy.dbOpenFailed, tone = BannerTone.Danger)
                                }
                            } else if (dbReady) {
                                val nav = rememberNavController()
                                NavHost(
                                    navController = nav,
                                    startDestination = "home",
                                    enterTransition = {
                                        slideInHorizontally(tween(360)) { it / 4 } + fadeIn(tween(360))
                                    },
                                    exitTransition = {
                                        scaleOut(tween(360), targetScale = 0.94f) + fadeOut(tween(260))
                                    },
                                    popEnterTransition = {
                                        scaleIn(tween(360), initialScale = 0.94f) + fadeIn(tween(360))
                                    },
                                    popExitTransition = {
                                        slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(260))
                                    }
                                ) {
                                    composable("home") {
                                        var retryHomeRefresh by remember { mutableStateOf<(() -> Unit)?>(null) }
                                        val vm: HomeViewModel = viewModel(
                                            key = viewModelKey("home", locale.name, dbSession),
                                            factory = savedFactory { saved ->
                                                HomeViewModel(
                                                    container.repository,
                                                    container.credentialsStore,
                                                    container.cardSync,
                                                    { System.currentTimeMillis() },
                                                    ZoneId.systemDefault(),
                                                    copy,
                                                    container.rewardStore,
                                                    onUserNotAuthenticated = {
                                                        launchBiometricUnlock(
                                                            activity = this@MainActivity,
                                                            credentialsStore = container.credentialsStore,
                                                            lockViewModel = lockVm,
                                                            onInvalidated = {
                                                                openCredentialsWithoutDb(keyReset = true)
                                                            },
                                                            onUnlocked = { retryHomeRefresh?.invoke() }
                                                        )
                                                    },
                                                    savedState = saved,
                                                    fundBalanceStore = container.fundBalanceStore
                                                )
                                            }
                                        )
                                        retryHomeRefresh = { vm.refresh(forceNetwork = true) }
                                        HomeScreen(
                                            viewModel = vm,
                                            onOpenSettings = { nav.navigate("settings") },
                                            onOpenSearch = { nav.navigate("search") },
                                            onOpenCategories = { nav.navigate("categories") },
                                            onOpenDeclines = { nav.navigate("declines") },
                                            onOpenSubscriptions = { nav.navigate("subscriptions") }
                                        )
                                    }
                                    composable("categories") {
                                        val vm: CategoriesViewModel = viewModel(
                                            key = viewModelKey("categories", locale.name, dbSession),
                                            factory = savedFactory { saved ->
                                                CategoriesViewModel(
                                                    container.repository,
                                                    { System.currentTimeMillis() },
                                                    ZoneId.systemDefault(),
                                                    copy,
                                                    saved
                                                )
                                            }
                                        )
                                        CategoriesScreen(
                                            viewModel = vm,
                                            onBack = { nav.popBackStack() }
                                        )
                                    }
                                    composable("search") {
                                        val vm: SearchViewModel = viewModel(
                                            key = viewModelKey("search", locale.name, dbSession),
                                            factory = savedFactory { saved ->
                                                SearchViewModel(
                                                    container.repository,
                                                    ZoneId.systemDefault(),
                                                    copy,
                                                    savedState = saved,
                                                    credentialsStore = container.credentialsStore
                                                )
                                            }
                                        )
                                        SearchScreen(
                                            viewModel = vm,
                                            onBack = { nav.popBackStack() }
                                        )
                                    }
                                    composable("declines") {
                                        val vm: DeclinesViewModel = viewModel(
                                            key = viewModelKey("declines", locale.name, dbSession),
                                            factory = savedFactory { saved ->
                                                DeclinesViewModel(
                                                    container.repository,
                                                    { System.currentTimeMillis() },
                                                    ZoneId.systemDefault(),
                                                    copy,
                                                    saved
                                                )
                                            }
                                        )
                                        DeclinesScreen(
                                            viewModel = vm,
                                            onBack = { nav.popBackStack() }
                                        )
                                    }
                                    composable("subscriptions") {
                                        val vm: SubscriptionsViewModel = viewModel(
                                            key = viewModelKey("subscriptions", locale.name, dbSession),
                                            factory = factory {
                                                SubscriptionsViewModel(
                                                    container.repository,
                                                    ZoneId.systemDefault(),
                                                    copy
                                                )
                                            }
                                        )
                                        SubscriptionsScreen(
                                            viewModel = vm,
                                            onBack = { nav.popBackStack() }
                                        )
                                    }
                                    composable("settings") {
                                        var retryCredentialsSubmit by remember { mutableStateOf<(() -> Unit)?>(null) }
                                        val vm: CredentialsViewModel = viewModel(
                                            key = viewModelKey("settings", locale.name, dbSession),
                                            factory = factory {
                                                CredentialsViewModel(
                                                    container.verify,
                                                    container.credentialsStore,
                                                    copy = copy,
                                                    deviceSecure = { deviceUnlockAvailable(this@MainActivity) },
                                                    onUserNotAuthenticated = {
                                                        launchBiometricUnlock(
                                                            activity = this@MainActivity,
                                                            credentialsStore = container.credentialsStore,
                                                            lockViewModel = lockVm,
                                                            onInvalidated = {
                                                                openCredentialsWithoutDb(keyReset = true)
                                                            },
                                                            onUnlocked = { retryCredentialsSubmit?.invoke() }
                                                        )
                                                    },
                                                    userNotAuthenticated = ::isUserNotAuthenticated
                                                )
                                            }
                                        )
                                        retryCredentialsSubmit = { vm.submit() }
                                        val export: ExportViewModel = viewModel(
                                            key = viewModelKey("export", locale.name, dbSession),
                                            factory = factory {
                                                ExportViewModel(
                                                    container.repository,
                                                    copy,
                                                    container.credentialsStore
                                                )
                                            }
                                        )
                                        CredentialsScreen(
                                            viewModel = vm,
                                            firstLaunch = false,
                                            onSaved = { nav.popBackStack() },
                                            onBack = { nav.popBackStack() },
                                            appearance = appearance,
                                            onLanguage = { mode ->
                                                appearanceStore.setLanguage(mode)
                                            },
                                            onTheme = { mode -> appearanceStore.setTheme(mode) },
                                            export = export,
                                            onWipe = {
                                                wipeScope.launch {
                                                    LocalWipe(
                                                        context = this@MainActivity,
                                                        credentialsStore = container.credentialsStore,
                                                        rewardStore = container.rewardStore,
                                                        fundBalanceStore = container.fundBalanceStore,
                                                        budgetRepository = container.budgetRepository,
                                                        transactionRepository = container.repository,
                                                        closeDatabase = { container.closeDatabase() }
                                                    ).run()
                                                    openCredentialsWithoutDb(wipeDone = true)
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                PageLoading()
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun viewModelKey(screen: String, locale: String, dbSession: Int): String {
    return "$screen-$locale-$dbSession"
}

internal inline fun <reified VM : ViewModel> factory(crossinline create: () -> VM): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer { create() }
    }
}

internal inline fun <reified VM : ViewModel> savedFactory(
    crossinline create: (SavedStateHandle) -> VM
): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer { create(createSavedStateHandle()) }
    }
}
