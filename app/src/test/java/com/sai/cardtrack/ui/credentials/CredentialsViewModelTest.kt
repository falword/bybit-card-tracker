package com.sai.cardtrack.ui.credentials

import com.sai.cardtrack.bybit.ApiKeyInfo
import com.sai.cardtrack.bybit.ApiKeyInfoPage
import com.sai.cardtrack.bybit.AssetRecordsPage
import com.sai.cardtrack.bybit.AssetRecordsRequest
import com.sai.cardtrack.bybit.BybitCardClient
import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.data.InMemoryCredentialsStore
import com.sai.cardtrack.sync.VerifyAndSaveCredentials
import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val safeApiKeyPage = ApiKeyInfoPage(
        0,
        "",
        ApiKeyInfo(
            readOnly = 1,
            isMaster = true,
            bitCard = listOf("BitCard"),
            wallet = listOf("AccountTransfer"),
            ips = listOf("8.8.8.8"),
            deadlineDay = null
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `p2p accounting defaults off`() = runTest {
        val store = InMemoryCredentialsStore()
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store
        )
        advanceUntilIdle()
        assertFalse(vm.state.value.p2pAccounting)
        assertFalse(store.p2pAccountingEnabled())
    }

    @Test
    fun `onP2pAccounting writes the store`() = runTest {
        val store = InMemoryCredentialsStore()
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store
        )
        advanceUntilIdle()
        vm.onP2pAccounting(true)
        advanceUntilIdle()
        assertTrue(vm.state.value.p2pAccounting)
        assertTrue(store.p2pAccountingEnabled())
    }

    @Test
    fun `test request failure does not persist a new key`() = runTest {
        val store = InMemoryCredentialsStore()
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage {
                        throw IOException("offline")
                    }

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store
        )
        vm.onKeyChange("k")
        vm.onSecretChange("s")
        vm.submit()
        advanceUntilIdle()
        assertEquals(
            "Нет соединения с Bybit. Проверь интернет и что приложению разрешена сеть.",
            vm.state.value.error
        )
        assertFalse(vm.state.value.saved)
        assertNull(store.get())
    }

    @Test
    fun `submit trims key and secret`() = runTest {
        val store = InMemoryCredentialsStore()
        var seen: Credentials? = null
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage {
                        seen = credentials
                        return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
                    }

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store
        )
        vm.onKeyChange("  key  ")
        vm.onSecretChange("  secret  ")
        vm.submit()
        advanceUntilIdle()
        assertEquals("key", seen?.apiKey)
        assertEquals("secret", seen?.apiSecret)
        assertEquals(Credentials("key", "secret"), store.get())
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `does not load stored secret or key into ui state`() = runTest {
        val store = InMemoryCredentialsStore()
        store.save(Credentials("stored-api-key-1234", "stored-hmac-secret"))
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store
        )
        advanceUntilIdle()
        assertEquals("", vm.state.value.apiSecret)
        assertEquals("", vm.state.value.apiKey)
        assertEquals("1234", vm.state.value.keyHint)
    }

    @Test
    fun `clears secret from state after successful save`() = runTest {
        val store = InMemoryCredentialsStore()
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store
        )
        vm.onKeyChange("k")
        vm.onSecretChange("s")
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        assertEquals(Credentials("k", "s"), store.get())
        assertEquals("", vm.state.value.apiSecret)
    }

    @Test
    fun `caps pasted key and secret at 256 before verify`() = runTest {
        val store = InMemoryCredentialsStore()
        var seen: Credentials? = null
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage {
                        seen = credentials
                        return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
                    }

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store
        )
        vm.onKeyChange("k".repeat(300))
        vm.onSecretChange("s".repeat(300))
        assertEquals(256, vm.state.value.apiKey.length)
        assertEquals(256, vm.state.value.apiSecret.length)
        vm.submit()
        advanceUntilIdle()
        assertEquals(256, seen?.apiKey?.length)
        assertEquals(256, seen?.apiSecret?.length)
    }

    @Test
    fun `loads last sync label from store`() = runTest {
        val store = InMemoryCredentialsStore()
        val zone = ZoneId.of("Asia/Bangkok")
        val now = ZonedDateTime.of(2026, 9, 6, 15, 4, 0, 0, zone).toInstant().toEpochMilli()
        store.setLastSyncAt(now)
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store,
            clock = { now },
            zone = zone
        )
        advanceUntilIdle()
        assertEquals("15:04", vm.state.value.lastSyncLabel)
    }

    @Test
    fun `opening settings does not decrypt vault or prompt unlock`() = runTest {
        var prompted = 0
        var getCalls = 0
        val zone = ZoneId.of("Asia/Bangkok")
        val now = ZonedDateTime.of(2026, 9, 6, 15, 4, 0, 0, zone).toInstant().toEpochMilli()
        val store = object : CredentialsStore {
            override suspend fun get(): Credentials? {
                getCalls++
                throw RuntimeException("unauth")
            }
            override suspend fun save(credentials: Credentials) = Unit
            override suspend fun lastSyncAt(): Long? = now
            override suspend fun setLastSyncAt(epochMs: Long) = Unit
            override suspend fun fundingSyncedThrough(): Long? = null
            override suspend fun setFundingSyncedThrough(epochMs: Long) = Unit
            override suspend fun clear() = Unit
            override suspend fun apiKeyHint(): String? = "1234"
        }
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store,
            clock = { now },
            zone = zone,
            onUserNotAuthenticated = { prompted++ },
            userNotAuthenticated = { true }
        )
        advanceUntilIdle()
        assertEquals(0, prompted)
        assertEquals(0, getCalls)
        assertEquals("1234", vm.state.value.keyHint)
        assertEquals("15:04", vm.state.value.lastSyncLabel)
    }

    @Test
    fun `opening settings ignores hint auth errors without prompting`() = runTest {
        var prompted = 0
        val zone = ZoneId.of("Asia/Bangkok")
        val now = ZonedDateTime.of(2026, 9, 6, 15, 4, 0, 0, zone).toInstant().toEpochMilli()
        val store = object : CredentialsStore {
            override suspend fun get(): Credentials? = throw RuntimeException("unauth")
            override suspend fun save(credentials: Credentials) = Unit
            override suspend fun lastSyncAt(): Long? = now
            override suspend fun setLastSyncAt(epochMs: Long) = Unit
            override suspend fun fundingSyncedThrough(): Long? = null
            override suspend fun setFundingSyncedThrough(epochMs: Long) = Unit
            override suspend fun clear() = Unit
            override suspend fun apiKeyHint(): String? = throw RuntimeException("unauth")
        }
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store,
            clock = { now },
            zone = zone,
            onUserNotAuthenticated = { prompted++ },
            userNotAuthenticated = { true }
        )
        advanceUntilIdle()
        assertEquals(0, prompted)
        assertNull(vm.state.value.keyHint)
        assertEquals("15:04", vm.state.value.lastSyncLabel)
    }

    @Test
    fun `insecure device does not call verify`() = runTest {
        val store = InMemoryCredentialsStore()
        var called = false
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage {
                        called = true
                        return AssetRecordsPage(0, "OK", emptyList(), 1, 0)
                    }

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store,
            deviceSecure = { false }
        )
        vm.onKeyChange("k")
        vm.onSecretChange("s")
        vm.submit()
        advanceUntilIdle()
        assertFalse(called)
        assertNull(store.get())
        assertEquals(UiCopy.Ru.lockScreenRequired, vm.state.value.error)
    }

    @Test
    fun `submit prompts unlock when keystore save is not authenticated`() = runTest {
        var prompted = 0
        val store = object : CredentialsStore {
            override suspend fun get(): Credentials? = null
            override suspend fun save(credentials: Credentials) {
                throw RuntimeException("unauth")
            }
            override suspend fun lastSyncAt(): Long? = null
            override suspend fun setLastSyncAt(epochMs: Long) = Unit
            override suspend fun fundingSyncedThrough(): Long? = null
            override suspend fun setFundingSyncedThrough(epochMs: Long) = Unit
            override suspend fun clear() = Unit
        }
        val vm = CredentialsViewModel(
            VerifyAndSaveCredentials(
                object : BybitCardClient {
                    override suspend fun queryAssetRecords(
                        credentials: Credentials,
                        request: AssetRecordsRequest
                    ): AssetRecordsPage = AssetRecordsPage(0, "OK", emptyList(), 1, 0)

                    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage =
                        safeApiKeyPage
                },
                store
            ),
            store,
            onUserNotAuthenticated = { prompted++ },
            userNotAuthenticated = { true }
        )
        vm.onKeyChange("k")
        vm.onSecretChange("s")
        vm.submit()
        advanceUntilIdle()
        assertEquals(1, prompted)
        assertTrue(vm.state.value.error != UiCopy.Ru.verifyInternalError)
        assertNull(store.get())
        assertFalse(vm.state.value.busy)
        assertEquals("k", vm.state.value.apiKey)
        assertEquals("s", vm.state.value.apiSecret)
    }
}
