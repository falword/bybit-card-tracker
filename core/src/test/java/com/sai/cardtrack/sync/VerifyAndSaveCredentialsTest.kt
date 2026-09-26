package com.sai.cardtrack.sync

import com.sai.cardtrack.bybit.ApiKeyInfo
import com.sai.cardtrack.bybit.ApiKeyInfoPage
import com.sai.cardtrack.bybit.AssetRecordsPage
import com.sai.cardtrack.bybit.AssetRecordsRequest
import com.sai.cardtrack.bybit.BybitCardClient
import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.data.InMemoryCredentialsStore
import com.sai.cardtrack.domain.QueryType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class VerifyAndSaveCredentialsTest {

    private val creds = Credentials("key", "secret")

    private val safeInfo = ApiKeyInfo(
        readOnly = 1,
        isMaster = true,
        bitCard = listOf("BitCard"),
        wallet = listOf("AccountTransfer"),
        ips = listOf("8.8.8.8"),
        deadlineDay = null
    )

    private fun client(
        asset: suspend (Credentials, AssetRecordsRequest) -> AssetRecordsPage,
        apiKey: suspend (Credentials) -> ApiKeyInfoPage = {
            ApiKeyInfoPage(0, "", safeInfo)
        }
    ): BybitCardClient {
        return object : BybitCardClient {
            override suspend fun queryAssetRecords(
                credentials: Credentials,
                request: AssetRecordsRequest
            ): AssetRecordsPage = asset(credentials, request)

            override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage = apiKey(credentials)
        }
    }

    @Test
    fun `test request failure does not persist a new key`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(asset = { _, _ -> throw IOException("offline") })
        val useCase = VerifyAndSaveCredentials(client, store)
        val result = useCase.execute(creds)
        assertEquals(
            VerifyResult.Failed(
                "Нет соединения с Bybit. Проверь интернет и что приложению разрешена сеть."
            ),
            result
        )
        assertNull(store.get())
    }

    @Test
    fun `ssl failure is not shown as no network`() {
        val handshake = SSLHandshakeException("chain")
        assertEquals(
            "Защищённое соединение с Bybit не удалось. Проверь дату и время на телефоне.",
            ioExceptionBanner(handshake)
        )
        assertEquals(
            "Защищённое соединение с Bybit не удалось. Проверь дату и время на телефоне.",
            ioExceptionBanner(IOException("wrap", handshake))
        )
        assertEquals(
            "Bybit отказал в доступе (HTTP 403). Часто это регион, VPN или лимит IP.",
            ioExceptionBanner(IOException("http 403"))
        )
        assertEquals(
            "Нет соединения с Bybit. Проверь интернет и что приложению разрешена сеть.",
            ioExceptionBanner(IOException("offline"))
        )
    }

    @Test
    fun `invalid bybit json is not shown as no network`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(asset = { _, _ -> throw IOException("invalid bybit json") })
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertEquals(
            VerifyResult.Failed(
                "Bybit вернул непонятный ответ, не JSON. Часто это блок сети, VPN или антивирус."
            ),
            result
        )
        assertNull(store.get())
    }

    @Test
    fun `unmatched ip explains whitelist and does not save`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(asset = { _, _ ->
            AssetRecordsPage(10010, "Unmatched IP", emptyList(), 1, 0)
        })
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertEquals(
            VerifyResult.Failed(
                "IP не совпадает со списком в ключе. Убери IP restriction или добавь текущий адрес."
            ),
            result
        )
        assertNull(store.get())
    }

    @Test
    fun `param illegal is explained not shown raw`() {
        assertEquals(
            "Bybit отклонил параметры запроса. Часто так отвечает запрос возвратов с фильтром дат.",
            bybitAccessBanner(10001, "param_illegal")
        )
        assertEquals(
            "Bybit отклонил параметры запроса. Часто так отвечает запрос возвратов с фильтром дат.",
            displaySyncBanner(SyncError.Unknown, "param_illegal", 10001)
        )
    }

    @Test
    fun `unknown bybit code keeps retMsg instead of generic stub`() {
        assertEquals(
            "weird card error",
            displaySyncBanner(SyncError.Unknown, "weird card error", 19999)
        )
        assertEquals(
            "Bybit отклонил запрос (код 19999).",
            displaySyncBanner(SyncError.Unknown, "  ", 19999)
        )
        assertEquals(
            "Время на телефоне расходится с Bybit. Включи автоматические дату и время.",
            displaySyncBanner(SyncError.Unknown, "expired", 10002)
        )
    }

    @Test
    fun `permission denied explains missing card right and does not save`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(asset = { _, _ ->
            AssetRecordsPage(10005, "Permission denied", emptyList(), 1, 0)
        })
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertEquals(
            VerifyResult.Failed(
                "У ключа нет права Card (BitCard). Создай read-only ключ на основном аккаунте и отметь Bybit Card."
            ),
            result
        )
        assertNull(store.get())
    }

    @Test
    fun `bybit retMsg shown and key not saved`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(asset = { _, request ->
            assertEquals(QueryType.Auth, request.type)
            assertEquals(1, request.page)
            assertEquals(1, request.limit)
            assertNull(request.createBeginTime)
            assertNull(request.createEndTime)
            AssetRecordsPage(10004, "error sign!", emptyList(), 1, 0)
        })
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertEquals(
            VerifyResult.Failed(
                "Подпись не сошлась. Проверь Secret и что копируешь его целиком, без пробелов."
            ),
            result
        )
        assertNull(store.get())
    }

    @Test
    fun `success saves credentials`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(asset = { _, _ ->
            AssetRecordsPage(0, "OK", emptyList(), 1, 0)
        })
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertEquals(VerifyResult.Ok, result)
        assertEquals(creds, store.get())
    }

    @Test
    fun `rate limit uses fixed russian copy`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(asset = { _, _ ->
            AssetRecordsPage(10006, "rate", emptyList(), 1, 0)
        })
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertEquals(
            VerifyResult.Failed(
                "Bybit временно отклонил запрос из‑за лимита частоты. Подожди минуту и обнови."
            ),
            result
        )
        assertTrue(store.get() == null)
    }

    @Test
    fun `empty retMsg uses fallback banner and does not save`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(asset = { _, _ ->
            AssetRecordsPage(10004, "  ", emptyList(), 1, 0)
        })
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertEquals(
            VerifyResult.Failed(
                "Подпись не сошлась. Проверь Secret и что копируешь его целиком, без пробелов."
            ),
            result
        )
        assertNull(store.get())
    }

    @Test
    fun `sync error banner uses russian copy`() {
        assertEquals(
            "Нет соединения с Bybit. Проверь интернет и что приложению разрешена сеть.",
            SyncError.Network.banner()
        )
        assertEquals(
            "Bybit временно отклонил запрос из‑за лимита частоты. Подожди минуту и обнови.",
            SyncError.RateLimit.banner()
        )
        assertEquals(
            "Bybit не принял ключ. Проверь Key, Secret и право Card.",
            SyncError.Auth.banner()
        )
        assertEquals(
            "Bybit отклонил запрос. Обнови позже или проверь ключ.",
            SyncError.Unknown.banner()
        )
    }

    @Test
    fun `auth banner prefers non blank retMsg`() {
        assertEquals("error sign!", displaySyncBanner(SyncError.Auth, "error sign!"))
        assertEquals(
            "Bybit не принял ключ. Проверь Key, Secret и право Card.",
            displaySyncBanner(SyncError.Auth, "  ")
        )
        assertEquals(
            "Bybit не принял ключ. Проверь Key, Secret и право Card.",
            displaySyncBanner(SyncError.Auth, null)
        )
        assertEquals(
            "У ключа нет права Card (BitCard). Создай read-only ключ на основном аккаунте и отметь Bybit Card.",
            displaySyncBanner(SyncError.Auth, "permission denied")
        )
        assertEquals(
            "Bybit временно отклонил запрос из‑за лимита частоты. Подожди минуту и обнови.",
            displaySyncBanner(SyncError.RateLimit, "too many")
        )
    }

    @Test
    fun `withdraw key is not saved even when card query would succeed`() = runTest {
        val store = InMemoryCredentialsStore()
        val unsafe = safeInfo.copy(wallet = listOf("Withdraw"))
        val client = client(
            asset = { _, _ -> AssetRecordsPage(0, "OK", emptyList(), 1, 0) },
            apiKey = { ApiKeyInfoPage(0, "", unsafe) }
        )
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertEquals(VerifyResult.Failed(KeyPermissionCheck.HAS_WITHDRAW), result)
        assertNull(store.get())
    }

    @Test
    fun `query-api is called before asset records`() = runTest {
        val order = mutableListOf<String>()
        val client = client(
            asset = { _, _ ->
                order.add("asset")
                AssetRecordsPage(0, "OK", emptyList(), 1, 0)
            },
            apiKey = {
                order.add("api")
                ApiKeyInfoPage(0, "", safeInfo)
            }
        )
        VerifyAndSaveCredentials(client, InMemoryCredentialsStore()).execute(creds)
        assertEquals(listOf("api", "asset"), order)
    }

    @Test
    fun `query-api network failure does not save`() = runTest {
        val store = InMemoryCredentialsStore()
        val client = client(
            asset = { _, _ -> AssetRecordsPage(0, "OK", emptyList(), 1, 0) },
            apiKey = { throw IOException("offline") }
        )
        val result = VerifyAndSaveCredentials(client, store).execute(creds)
        assertTrue(result is VerifyResult.Failed)
        assertNull(store.get())
    }
}
