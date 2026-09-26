package com.sai.cardtrack.bybit

import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.domain.QueryType
import com.sai.cardtrack.util.RecordingLog
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OkHttpBybitCardClientTest {

    @Test
    fun `posts signed body and parses page`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{"retCode":0,"retMsg":"OK","result":{"pageNo":1,"totalCount":0,"data":[]}}""")
        )
        server.start()
        val log = RecordingLog()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = log,
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, log)
        val page = client.queryAssetRecords(
            Credentials("k", "super-secret-value"),
            AssetRecordsRequest(QueryType.Auth, 1, 1, null, null)
        )
        assertEquals(0, page.retCode)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.contains("/v5/card/transaction/query-asset-records?limit=1&page=1"))
        assertEquals("k", recorded.getHeader("X-BAPI-API-KEY"))
        assertTrue(recorded.body.readUtf8().contains("SIDE_QUERY_AUTH"))
        assertFalse(log.messages.joinToString().contains("super-secret-value"))
        server.shutdown()
    }

    @Test
    fun `card asset records wait 400ms between calls`() = runTest {
        // official-v5/rate-limit/rate-limit.mdx has no row for query-asset-records
        val pauses = mutableListOf<Long>()
        var nowMs = 10_000L
        val server = MockWebServer()
        val ok = """{"retCode":0,"retMsg":"OK","result":{"pageNo":1,"totalCount":0,"data":[]}}"""
        server.enqueue(MockResponse().setBody(ok))
        server.enqueue(MockResponse().setBody(ok))
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(
            OkHttpClient(),
            factory,
            RecordingLog(),
            gate = BybitCallGate(
                now = { nowMs },
                pause = { ms ->
                    pauses.add(ms)
                    nowMs += ms
                }
            )
        )
        val request = AssetRecordsRequest(QueryType.Auth, 1, 1, null, null)
        client.queryAssetRecords(Credentials("k", "s"), request)
        client.queryAssetRecords(Credentials("k", "s"), request)
        assertEquals(listOf(400L), pauses)
        server.shutdown()
    }

    @Test
    fun `http 429 becomes rate limit retCode`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setBody("nope"))
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, RecordingLog())
        val page = client.queryAssetRecords(
            Credentials("k", "s"),
            AssetRecordsRequest(QueryType.Auth, 1, 1, null, null)
        )
        assertEquals(10006, page.retCode)
        server.shutdown()
    }

    @Test
    fun `retries fallback host after connection failure`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{"retCode":0,"retMsg":"OK","result":{"pageNo":1,"totalCount":0,"data":[]}}""")
        )
        server.start()
        val fallback = server.url("/").toString().trimEnd('/')
        val client = OkHttpBybitCardClient(
            OkHttpClient(),
            BybitRequestFactory(
                clock = { 1L },
                log = RecordingLog(),
                baseUrl = "http://127.0.0.1:1"
            ),
            RecordingLog(),
            alternateBaseUrls = listOf(fallback)
        )
        val page = client.queryAssetRecords(
            Credentials("k", "s"),
            AssetRecordsRequest(QueryType.Auth, 1, 1, null, null)
        )
        assertEquals(0, page.retCode)
        assertEquals("POST", server.takeRequest().method)
        server.shutdown()
    }

    @Test
    fun `does not retry fallback host after invalid json`() = runTest {
        val html = MockWebServer()
        html.enqueue(MockResponse().setBody("<html>gateway</html>"))
        html.start()
        val ok = MockWebServer()
        ok.enqueue(
            MockResponse().setBody("""{"retCode":0,"retMsg":"OK","result":{"pageNo":1,"totalCount":0,"data":[]}}""")
        )
        ok.start()
        val client = OkHttpBybitCardClient(
            OkHttpClient(),
            BybitRequestFactory(
                clock = { 1L },
                log = RecordingLog(),
                baseUrl = html.url("/").toString().trimEnd('/')
            ),
            RecordingLog(),
            alternateBaseUrls = listOf(ok.url("/").toString().trimEnd('/'))
        )
        try {
            client.queryAssetRecords(
                Credentials("k", "s"),
                AssetRecordsRequest(QueryType.Auth, 1, 1, null, null)
            )
            throw AssertionError("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("invalid bybit json"))
        }
        assertEquals(0, ok.requestCount)
        html.shutdown()
        ok.shutdown()
    }

    @Test
    fun `default http uses http 1 1 and thirty second timeouts`() {
        val http = OkHttpBybitCardClient.defaultHttp()
        assertEquals(listOf(Protocol.HTTP_1_1), http.protocols)
        assertEquals(30_000, http.connectTimeoutMillis)
        assertEquals(30_000, http.readTimeoutMillis)
    }

    @Test
    fun `default http does not follow redirects`() {
        val http = OkHttpBybitCardClient.defaultHttp()
        assertFalse(http.followRedirects)
        assertFalse(http.followSslRedirects)
    }

    @Test
    fun `default http pins amazon cas for bybit api hosts`() {
        val http = OkHttpBybitCardClient.defaultHttp()
        assertEquals(BybitTls.certificatePinner().pins, http.certificatePinner.pins)
        assertEquals(8, http.certificatePinner.findMatchingPins("api.bybit.com").size)
        assertEquals(8, http.certificatePinner.findMatchingPins("api.bytick.com").size)
        assertTrue(
            http.certificatePinner.pins.joinToString { it.toString() }
                .contains(BybitTls.AMAZON_ROOT_CA_1)
        )
    }

    @Test
    fun `http 302 is not followed and is io exception`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/v5/card/transaction/query-asset-records-redirected")
        )
        server.enqueue(
            MockResponse().setBody("""{"retCode":0,"retMsg":"OK","result":{"pageNo":1,"totalCount":0,"data":[]}}""")
        )
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpBybitCardClient.defaultHttp(), factory, RecordingLog())
        try {
            client.queryAssetRecords(
                Credentials("k", "s"),
                AssetRecordsRequest(QueryType.Auth, 1, 1, null, null)
            )
            throw AssertionError("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("http 302"))
        }
        assertEquals(1, server.requestCount)
        server.shutdown()
    }

    @Test
    fun `non json http body is io exception`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("<html>gateway</html>"))
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, RecordingLog())
        try {
            client.queryAssetRecords(
                Credentials("k", "s"),
                AssetRecordsRequest(QueryType.Auth, 1, 1, null, null)
            )
            throw AssertionError("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("invalid bybit json"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `transfer query is get with signed query string`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{"retCode":0,"retMsg":"OK","result":{"list":[],"nextPageCursor":""}}""")
        )
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, RecordingLog())
        val page = client.queryInterTransfers(
            Credentials("k", "s"),
            TransferListRequest(10L, 20L, 50, null)
        )
        assertEquals(0, page.retCode)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/v5/asset/transfer/query-inter-transfer-list"))
        assertTrue(recorded.path!!.contains("startTime=10"))
        assertEquals("", recorded.body.readUtf8())
        server.shutdown()
    }

    @Test
    fun `funding history query is get with seconds`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{"retCode":0,"retMsg":"OK","result":{"list":[],"nextPageCursor":""}}""")
        )
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, RecordingLog())
        val page = client.queryFundingHistory(
            Credentials("k", "s"),
            FundingHistoryRequest(1_667_283_263L, 1_667_888_063L, 100, null)
        )
        assertEquals(0, page.retCode)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/v5/asset/fundinghistory"))
        assertTrue(recorded.path!!.contains("createTimeFrom=1667283263"))
        assertTrue(recorded.path!!.contains("createTimeTo=1667888063"))
        server.shutdown()
    }

    @Test
    fun `coins balance query is get fund usdt usdc with empty body`() = runTest {
        // official-v5/asset/balance/all-balance.mdx — GET, no time window
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{"retCode":0,"retMsg":"success","result":{"accountType":"FUND","balance":[]}}""")
        )
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, RecordingLog())
        val page = client.queryCoinsBalance(Credentials("k", "s"))
        assertEquals(0, page.retCode)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/v5/asset/transfer/query-account-coins-balance"))
        assertTrue(recorded.path!!.contains("accountType=FUND"))
        assertTrue(recorded.path!!.contains("coin=USDT,USDC"))
        assertEquals("", recorded.body.readUtf8())
        server.shutdown()
    }

    @Test
    fun `asset overview query is get with empty body`() = runTest {
        // official-v5/asset/balance/asset-overview.mdx — GET, no time window
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"retCode":0,"retMsg":"Success","result":{"totalEquity":"80","list":[
                  {"accountType":"FundingAccount","totalEquity":"80","valuationCurrency":"USD","coinDetail":[]}
                ]}}"""
            )
        )
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, RecordingLog())
        val page = client.queryAssetOverview(Credentials("k", "s"))
        assertEquals(0, page.retCode)
        assertEquals("80", page.fundingEquity)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v5/asset/asset-overview", recorded.path)
        assertEquals("", recorded.body.readUtf8())
        server.shutdown()
    }

    @Test
    fun `query api key is get with empty path`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"retCode":0,"retMsg":"","result":{"readOnly":1,"isMaster":true,"permissions":{"BitCard":["BitCard"]},"ips":[]}}"""
            )
        )
        server.start()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, RecordingLog())
        val page = client.queryApiKey(Credentials("k", "s"))
        assertEquals(0, page.retCode)
        assertEquals(1, page.info!!.readOnly)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v5/user/query-api", recorded.path)
        assertEquals("", recorded.body.readUtf8())
        server.shutdown()
    }

    @Test
    fun `points tier is post empty json`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"retCode":0,"retMsg":"OK","result":{"usedLimit":"10.00","limit":"500.00","unit":"1","tier":"GOLD","autoCashback":true}}"""
            )
        )
        server.start()
        val log = RecordingLog()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = log,
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, log)
        val page = client.queryPointsTier(Credentials("k", "super-secret-value"))
        assertEquals(0, page.retCode)
        assertEquals("GOLD", page.tier)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.contains("/v5/card/reward/points/tier"))
        assertEquals("{}", recorded.body.readUtf8())
        assertFalse(log.messages.joinToString().contains("super-secret-value"))
        server.shutdown()
    }

    @Test
    fun `points balance is post empty json`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"retCode":0,"retMsg":"OK","result":{"availablePoint":5000,"pendingPoint":200}}"""
            )
        )
        server.start()
        val log = RecordingLog()
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = log,
            baseUrl = server.url("/").toString().trimEnd('/')
        )
        val client = OkHttpBybitCardClient(OkHttpClient(), factory, log)
        val page = client.queryPointsBalance(Credentials("k", "super-secret-value"))
        assertEquals(0, page.retCode)
        assertEquals("5000", page.availablePoint)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.contains("/v5/card/reward/points/balance"))
        assertEquals("{}", recorded.body.readUtf8())
        assertFalse(log.messages.joinToString().contains("super-secret-value"))
        server.shutdown()
    }
}
