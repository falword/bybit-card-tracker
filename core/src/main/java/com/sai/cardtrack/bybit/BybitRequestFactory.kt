package com.sai.cardtrack.bybit

import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.domain.QueryType
import com.sai.cardtrack.util.AppLog
import java.net.URLEncoder

class BybitRequestFactory(
    private val clock: () -> Long,
    private val log: AppLog,
    private val recvWindow: String = "5000",
    private val baseUrl: String = "https://api.bybit.com"
) {
    fun build(
        credentials: Credentials,
        bodyJson: String,
        hostUrl: String = baseUrl,
        page: Int? = null,
        limit: Int? = null
    ): SignedRequest {
        val timestamp = clock().toString()
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = bodyJson,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit POST query-asset-records ts=$timestamp bytes=${bodyJson.length}")
        // official-v5/bybit-card/asset-records.mdx: HTTP/Python/Node put limit and page on the query.
        // official-v5/guide.mdx: POST HMAC is jsonBody only — query is not signed.
        val query = if (page != null && limit != null) {
            "?limit=$limit&page=$page"
        } else {
            ""
        }
        return SignedRequest(
            url = "$hostUrl/v5/card/transaction/query-asset-records$query",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign,
                "Content-Type" to "application/json"
            ),
            body = bodyJson
        )
    }

    fun bodyJson(request: AssetRecordsRequest): String {
        val type = when (request.type) {
            QueryType.Auth -> "SIDE_QUERY_AUTH"
            QueryType.Refund -> "SIDE_QUERY_REFUND"
            QueryType.Financial -> "SIDE_QUERY_FINANCIAL"
        }
        val parts = mutableListOf(
            "\"page\":${request.page}",
            "\"limit\":${request.limit}",
            "\"type\":\"$type\""
        )
        if (request.createBeginTime != null) {
            parts.add("\"createBeginTime\":${request.createBeginTime}")
        }
        if (request.createEndTime != null) {
            parts.add("\"createEndTime\":${request.createEndTime}")
        }
        return "{${parts.joinToString(",")}}"
    }

    fun pointsBodyJson(request: PointRecordsRequest): String {
        // official-v5/bybit-card/point/records.mdx: type is optional and has no enum.
        // Live records use type 1/5; sending type=CASHBACK returns totalCount=0.
        val parts = mutableListOf(
            "\"side\":\"1\"",
            "\"pageNo\":${request.pageNo}",
            "\"pageSize\":${request.pageSize}"
        )
        if (request.startTime != null) {
            parts.add("\"startTime\":${request.startTime}")
        }
        if (request.endTime != null) {
            parts.add("\"endTime\":${request.endTime}")
        }
        return "{${parts.joinToString(",")}}"
    }

    fun buildPoints(credentials: Credentials, bodyJson: String, hostUrl: String = baseUrl): SignedRequest {
        val timestamp = clock().toString()
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = bodyJson,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit POST reward/points/records ts=$timestamp bytes=${bodyJson.length}")
        return SignedRequest(
            url = "$hostUrl/v5/card/reward/points/records",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign,
                "Content-Type" to "application/json"
            ),
            body = bodyJson,
            method = "POST"
        )
    }

    fun transferQuery(request: TransferListRequest): String {
        val parts = mutableListOf<String>()
        if (request.startTime != null) {
            parts.add("startTime=${request.startTime}")
        }
        if (request.endTime != null) {
            parts.add("endTime=${request.endTime}")
        }
        parts.add("limit=${request.limit}")
        if (!request.cursor.isNullOrBlank()) {
            // official-v5/asset/transfer/inter-transfer-list.mdx: cursor is nextPageCursor token
            parts.add("cursor=" + URLEncoder.encode(request.cursor, "UTF-8"))
        }
        return parts.joinToString("&")
    }

    fun buildTransferGet(credentials: Credentials, query: String, hostUrl: String = baseUrl): SignedRequest {
        val timestamp = clock().toString()
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = query,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit GET inter-transfer-list ts=$timestamp q=${query.length}")
        val suffix = if (query.isEmpty()) "" else "?$query"
        return SignedRequest(
            url = "$hostUrl/v5/asset/transfer/query-inter-transfer-list$suffix",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign
            ),
            body = "",
            method = "GET"
        )
    }

    fun fundingQuery(request: FundingHistoryRequest): String {
        val parts = mutableListOf<String>()
        if (request.createTimeFromSec != null) {
            parts.add("createTimeFrom=${request.createTimeFromSec}")
        }
        if (request.createTimeToSec != null) {
            parts.add("createTimeTo=${request.createTimeToSec}")
        }
        parts.add("limit=${request.limit}")
        if (!request.cursor.isNullOrBlank()) {
            // official-v5/asset/fund-history.mdx: cursor paginates; times are seconds
            parts.add("cursor=" + URLEncoder.encode(request.cursor, "UTF-8"))
        }
        return parts.joinToString("&")
    }

    fun buildFundingGet(credentials: Credentials, query: String, hostUrl: String = baseUrl): SignedRequest {
        val timestamp = clock().toString()
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = query,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit GET fundinghistory ts=$timestamp q=${query.length}")
        val suffix = if (query.isEmpty()) "" else "?$query"
        return SignedRequest(
            url = "$hostUrl/v5/asset/fundinghistory$suffix",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign
            ),
            body = "",
            method = "GET"
        )
    }

    fun earnYieldQuery(request: EarnYieldRequest): String {
        // official-v5/finance/earn/easy-onchain/yield-history.mdx
        val parts = mutableListOf(
            "category=FlexibleSaving",
            "startTime=${request.startTime}",
            "endTime=${request.endTime}",
            "limit=${request.limit}"
        )
        if (!request.cursor.isNullOrBlank()) {
            parts.add("cursor=" + URLEncoder.encode(request.cursor, "UTF-8"))
        }
        return parts.joinToString("&")
    }

    fun buildEarnYieldGet(credentials: Credentials, query: String, hostUrl: String = baseUrl): SignedRequest {
        val timestamp = clock().toString()
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = query,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit GET earn/yield ts=$timestamp q=${query.length}")
        val suffix = if (query.isEmpty()) "" else "?$query"
        return SignedRequest(
            url = "$hostUrl/v5/earn/yield$suffix",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign
            ),
            body = "",
            method = "GET"
        )
    }

    fun buildQueryApiGet(credentials: Credentials, hostUrl: String = baseUrl): SignedRequest {
        // official-v5/user/apikey-info.mdx — GET /v5/user/query-api, no params;
        // HMAC of timestamp + apiKey + recvWindow + empty query (official-v5/guide.mdx)
        val timestamp = clock().toString()
        val query = ""
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = query,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit GET query-api ts=$timestamp")
        return SignedRequest(
            url = "$hostUrl/v5/user/query-api",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign
            ),
            body = "",
            method = "GET"
        )
    }

    fun buildPointsTier(credentials: Credentials, hostUrl: String = baseUrl): SignedRequest {
        // official-v5/bybit-card/point/tier.mdx — POST /v5/card/reward/points/tier;
        // request params: none; no time window; body exactly "{}"
        // POST HMAC of timestamp+apiKey+recvWindow+jsonBody (official-v5/guide.mdx)
        return buildEmptyJsonPost(credentials, "/v5/card/reward/points/tier", "reward/points/tier", hostUrl)
    }

    fun buildPointsBalance(credentials: Credentials, hostUrl: String = baseUrl): SignedRequest {
        // official-v5/bybit-card/point/balance.mdx — POST /v5/card/reward/points/balance;
        // request params: none; no time window; body exactly "{}"
        // POST HMAC of timestamp+apiKey+recvWindow+jsonBody (official-v5/guide.mdx)
        return buildEmptyJsonPost(credentials, "/v5/card/reward/points/balance", "reward/points/balance", hostUrl)
    }

    fun coinsBalanceQuery(): String {
        // official-v5/asset/balance/all-balance.mdx: accountType required; coin optional, uppercase
        return "accountType=FUND&coin=USDT,USDC"
    }

    fun buildCoinsBalanceGet(credentials: Credentials, hostUrl: String = baseUrl): SignedRequest {
        // official-v5/asset/balance/all-balance.mdx — GET /v5/asset/transfer/query-account-coins-balance
        // no time window; GET HMAC of timestamp+apiKey+recvWindow+queryString (official-v5/guide.mdx)
        val timestamp = clock().toString()
        val query = coinsBalanceQuery()
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = query,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit GET coins-balance ts=$timestamp q=${query.length}")
        return SignedRequest(
            url = "$hostUrl/v5/asset/transfer/query-account-coins-balance?$query",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign
            ),
            body = "",
            method = "GET"
        )
    }

    fun buildAssetOverviewGet(credentials: Credentials, hostUrl: String = baseUrl): SignedRequest {
        // official-v5/asset/balance/asset-overview.mdx — GET /v5/asset/asset-overview
        // no required params; default valuationCurrency=USD; no time window
        // GET HMAC of timestamp+apiKey+recvWindow+empty query (official-v5/guide.mdx)
        val timestamp = clock().toString()
        val query = ""
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = query,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit GET asset-overview ts=$timestamp")
        return SignedRequest(
            url = "$hostUrl/v5/asset/asset-overview",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign
            ),
            body = "",
            method = "GET"
        )
    }

    private fun buildEmptyJsonPost(
        credentials: Credentials,
        path: String,
        logName: String,
        hostUrl: String
    ): SignedRequest {
        val bodyJson = EMPTY_JSON
        val timestamp = clock().toString()
        val sign = BybitSigner.sign(
            timestamp = timestamp,
            apiKey = credentials.apiKey,
            recvWindow = recvWindow,
            jsonBody = bodyJson,
            apiSecret = credentials.apiSecret
        )
        log.d("bybit POST $logName ts=$timestamp bytes=${bodyJson.length}")
        return SignedRequest(
            url = "$hostUrl$path",
            headers = mapOf(
                "X-BAPI-API-KEY" to credentials.apiKey,
                "X-BAPI-TIMESTAMP" to timestamp,
                "X-BAPI-RECV-WINDOW" to recvWindow,
                "X-BAPI-SIGN" to sign,
                "Content-Type" to "application/json"
            ),
            body = bodyJson,
            method = "POST"
        )
    }

    companion object {
        private const val EMPTY_JSON = "{}"
    }
}
