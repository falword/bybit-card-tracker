package com.sai.cardtrack.bybit

import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.domain.QueryType
import com.sai.cardtrack.util.AppLog
import com.sai.cardtrack.util.RecordingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BybitSignerTest {

    @Test
    fun `sign matches HMAC SHA256 hex of timestamp key window body`() {
        // given
        val timestamp = "1658385589135"
        val key = "testkey"
        val window = "5000"
        val body = "{\"limit\":1}"
        val secret = "super-secret-value"
        val expected = hmacHex(timestamp + key + window + body, secret)

        // when
        val actual = BybitSigner.sign(timestamp, key, window, body, secret)

        // then
        assertEquals(expected, actual)
        assertEquals(expected.lowercase(), actual)
    }

    @Test
    fun `secret is not present in log output of the signer or request factory`() {
        val log = RecordingLog()
        val factory = BybitRequestFactory(
            clock = { 1_658_385_589_135L },
            log = log
        )
        factory.build(
            credentials = Credentials(apiKey = "visible-key", apiSecret = "super-secret-value"),
            bodyJson = "{\"limit\":1}"
        )
        val dumped = log.messages.joinToString("\n")
        assertFalse(dumped.contains("super-secret-value"))
        assertFalse(dumped.contains("visible-key"))
    }

    @Test
    fun `factory sets Bybit auth headers and mainnet url`() {
        val factory = BybitRequestFactory(
            clock = { 1_658_385_589_135L },
            log = RecordingLog()
        )
        val signed = factory.build(
            Credentials("k", "s"),
            "{\"limit\":1}"
        )
        assertEquals("https://api.bybit.com/v5/card/transaction/query-asset-records", signed.url)
        assertEquals("k", signed.headers["X-BAPI-API-KEY"])
        assertEquals("1658385589135", signed.headers["X-BAPI-TIMESTAMP"])
        assertEquals("5000", signed.headers["X-BAPI-RECV-WINDOW"])
        assertTrue(signed.headers["X-BAPI-SIGN"].orEmpty().isNotEmpty())
        assertEquals("{\"limit\":1}", signed.body)
    }

    @Test
    fun `financial body includes type`() {
        val factory = BybitRequestFactory(
            clock = { 1L },
            log = RecordingLog()
        )
        val financial = factory.bodyJson(
            AssetRecordsRequest(QueryType.Financial, 1, 50, null, null)
        )
        assertTrue(financial.contains("\"type\":\"SIDE_QUERY_FINANCIAL\""))
        assertTrue(financial.contains("\"page\":1"))
    }

    @Test
    fun `transfer get signs query string not json body`() {
        val factory = BybitRequestFactory(
            clock = { 1_658_385_589_135L },
            log = RecordingLog()
        )
        val query = factory.transferQuery(
            TransferListRequest(10L, 20L, 50, null)
        )
        val signed = factory.buildTransferGet(Credentials("k", "s"), query)
        assertEquals("GET", signed.method)
        assertTrue(signed.url.contains("/v5/asset/transfer/query-inter-transfer-list?"))
        assertEquals("", signed.body)
        val expected = BybitSigner.sign(
            "1658385589135",
            "k",
            "5000",
            query,
            "s"
        )
        assertEquals(expected, signed.headers["X-BAPI-SIGN"])
        assertFalse(query.contains("{"))
    }

    private fun hmacHex(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
            "%02x".format(byte)
        }
    }
}
