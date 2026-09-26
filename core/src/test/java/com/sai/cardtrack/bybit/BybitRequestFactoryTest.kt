package com.sai.cardtrack.bybit

import com.sai.cardtrack.domain.QueryType
import com.sai.cardtrack.util.RecordingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BybitRequestFactoryTest {

    @Test
    fun `asset records url puts official page and limit on query string`() {
        // official-v5/bybit-card/asset-records.mdx HTTP/Python/Node: ?limit=&page=
        // official-v5/guide.mdx: POST HMAC is timestamp+key+recv+jsonBody, not the query
        val factory = BybitRequestFactory(clock = { 9L }, log = RecordingLog())
        val request = AssetRecordsRequest(QueryType.Auth, page = 2, limit = 500, 10L, 20L)
        val body = factory.bodyJson(request)
        val signed = factory.build(
            com.sai.cardtrack.data.Credentials("k", "s"),
            body,
            page = 2,
            limit = 500
        )
        assertTrue(signed.url.endsWith("/v5/card/transaction/query-asset-records?limit=500&page=2"))
        assertTrue(body.contains("\"page\":2"))
        assertTrue(body.contains("\"limit\":500"))
        assertTrue(body.contains("\"type\":\"SIDE_QUERY_AUTH\""))
        val expected = BybitSigner.sign("9", "k", "5000", body, "s")
        assertEquals(expected, signed.headers["X-BAPI-SIGN"])
    }

    @Test
    fun `points body asks for earns and omits CASHBACK type`() {
        // official-v5/bybit-card/point/records.mdx: type is optional with no enum;
        // live API uses numeric types, and type=CASHBACK returns zero rows.
        val factory = BybitRequestFactory(clock = { 1L }, log = RecordingLog())
        val body = factory.pointsBodyJson(PointRecordsRequest(1, 50, 10L, 20L))
        assertTrue(body.contains("\"side\":\"1\""))
        assertTrue(body.contains("\"pageNo\":1"))
        assertTrue(body.contains("\"pageSize\":50"))
        assertTrue(body.contains("\"startTime\":10"))
        assertTrue(body.contains("\"endTime\":20"))
        assertFalse(body.contains("CASHBACK"))
        assertFalse(body.contains("\"type\""))
    }

    @Test
    fun `query-api is GET with empty query`() {
        // official-v5/user/apikey-info.mdx — GET /v5/user/query-api, no params
        val log = RecordingLog()
        val factory = BybitRequestFactory(clock = { 9L }, log = log)
        val signed = factory.buildQueryApiGet(com.sai.cardtrack.data.Credentials("k", "SUPERSECRET"))
        assertEquals("GET", signed.method)
        assertTrue(signed.url.endsWith("/v5/user/query-api"))
        assertFalse(signed.url.contains("?"))
        assertEquals("", signed.body)
        assertFalse(log.messages.any { it.contains("SUPERSECRET") })
        assertTrue(log.messages.any { it.contains("query-api") })
    }

    @Test
    fun `tier is POST empty json to reward points tier`() {
        // official-v5/bybit-card/point/tier.mdx — no request params
        val log = RecordingLog()
        val signed = BybitRequestFactory(clock = { 9L }, log = log)
            .buildPointsTier(com.sai.cardtrack.data.Credentials("k", "SUPERSECRET"))
        assertEquals("POST", signed.method)
        assertTrue(signed.url.endsWith("/v5/card/reward/points/tier"))
        assertEquals("{}", signed.body)
        assertFalse(log.messages.any { it.contains("SUPERSECRET") })
    }

    @Test
    fun `transfer and funding cursors are url encoded`() {
        // official-v5/asset/transfer/inter-transfer-list.mdx: cursor is nextPageCursor token
        // official-v5/asset/fund-history.mdx: cursor is used for pagination
        val factory = BybitRequestFactory(clock = { 1L }, log = RecordingLog())
        val raw = "abc+/=x"
        val t = factory.transferQuery(TransferListRequest(1L, 2L, 50, raw))
        val f = factory.fundingQuery(FundingHistoryRequest(1L, 2L, 100, raw))
        val encoded = java.net.URLEncoder.encode(raw, "UTF-8")
        assertTrue(t.contains("cursor=$encoded"))
        assertTrue(f.contains("cursor=$encoded"))
        assertFalse(t.contains("cursor=abc+/="))
        val creds = com.sai.cardtrack.data.Credentials("k", "s")
        val signedT = factory.buildTransferGet(creds, t)
        val signedF = factory.buildFundingGet(creds, f)
        assertTrue(signedT.url.endsWith("?$t"))
        assertTrue(signedF.url.endsWith("?$f"))
    }

    @Test
    fun `balance is POST empty json to reward points balance`() {
        // official-v5/bybit-card/point/balance.mdx — no request params
        val log = RecordingLog()
        val signed = BybitRequestFactory(clock = { 9L }, log = log)
            .buildPointsBalance(com.sai.cardtrack.data.Credentials("k", "SUPERSECRET"))
        assertEquals("POST", signed.method)
        assertTrue(signed.url.endsWith("/v5/card/reward/points/balance"))
        assertEquals("{}", signed.body)
        assertFalse(log.messages.any { it.contains("SUPERSECRET") })
    }

    @Test
    fun `coins balance is GET fund usdt usdc with query hmac`() {
        // official-v5/asset/balance/all-balance.mdx; official-v5/guide.mdx GET HMAC
        val factory = BybitRequestFactory(clock = { 9L }, log = RecordingLog())
        val signed = factory.buildCoinsBalanceGet(com.sai.cardtrack.data.Credentials("k", "SUPERSECRET"))
        assertTrue(signed.url.endsWith("/v5/asset/transfer/query-account-coins-balance?accountType=FUND&coin=USDT,USDC"))
        assertEquals("GET", signed.method)
        val q = "accountType=FUND&coin=USDT,USDC"
        assertEquals(BybitSigner.sign("9", "k", "5000", q, "SUPERSECRET"), signed.headers["X-BAPI-SIGN"])
        assertFalse(signed.url.contains("startTime"))
    }

    @Test
    fun `earn yield query is FlexibleSaving 7-day ms window limit 100`() {
        // official-v5/finance/earn/easy-onchain/yield-history.mdx:
        // GET /v5/earn/yield; category required; startTime/endTime ms;
        // both set ⇒ endTime-startTime ≤ 7 days; limit [1,100] default 50;
        // cursor = nextPageCursor. This app uses limit=100.
        val factory = BybitRequestFactory(clock = { 9L }, log = RecordingLog())
        val raw = "abc+/=x"
        val q = factory.earnYieldQuery(EarnYieldRequest(1L, 2L, 100, raw))
        val encoded = java.net.URLEncoder.encode(raw, "UTF-8")
        assertTrue(q.contains("category=FlexibleSaving"))
        assertTrue(q.contains("startTime=1"))
        assertTrue(q.contains("endTime=2"))
        assertTrue(q.contains("limit=100"))
        assertTrue(q.contains("cursor=$encoded"))
        assertFalse(q.contains("productId"))
        val log = RecordingLog()
        val signed = BybitRequestFactory(clock = { 9L }, log = log)
            .buildEarnYieldGet(com.sai.cardtrack.data.Credentials("k", "SUPERSECRET"), q)
        assertEquals("GET", signed.method)
        assertTrue(signed.url.endsWith("/v5/earn/yield?$q"))
        assertEquals("", signed.body)
        assertFalse(log.messages.any { it.contains("SUPERSECRET") })
    }

    @Test
    fun `asset overview is GET with empty query hmac`() {
        // official-v5/asset/balance/asset-overview.mdx; official-v5/guide.mdx GET HMAC
        val factory = BybitRequestFactory(clock = { 9L }, log = RecordingLog())
        val signed = factory.buildAssetOverviewGet(com.sai.cardtrack.data.Credentials("k", "SUPERSECRET"))
        assertTrue(signed.url.endsWith("/v5/asset/asset-overview"))
        assertFalse(signed.url.contains("?"))
        assertEquals("GET", signed.method)
        assertEquals(BybitSigner.sign("9", "k", "5000", "", "SUPERSECRET"), signed.headers["X-BAPI-SIGN"])
        assertFalse(signed.url.contains("startTime"))
    }
}
