package com.sai.cardtrack.bybit

import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class OkHttpBybitCardClient(
    private val http: OkHttpClient,
    private val factory: BybitRequestFactory,
    private val log: AppLog,
    private val alternateBaseUrls: List<String> = emptyList(),
    private val gate: BybitCallGate = BybitCallGate()
) : BybitCardClient {
    override suspend fun queryAssetRecords(
        credentials: Credentials,
        request: AssetRecordsRequest
    ): AssetRecordsPage {
        return withHosts(BybitThrottle.Card) { host ->
            val bodyJson = factory.bodyJson(request)
            val signed = if (host == null) {
                factory.build(credentials, bodyJson, page = request.page, limit = request.limit)
            } else {
                factory.build(
                    credentials,
                    bodyJson,
                    host,
                    page = request.page,
                    limit = request.limit
                )
            }
            executeSigned(
                signed,
                rateLimited = AssetRecordsPage(10006, "rate limit", emptyList(), request.page, 0),
                parse = { BybitJson.parsePage(it) }
            )
        }
    }

    override suspend fun queryPointRecords(
        credentials: Credentials,
        request: PointRecordsRequest
    ): PointRecordsPage {
        return withHosts(BybitThrottle.Card) { host ->
            val bodyJson = factory.pointsBodyJson(request)
            val signed = if (host == null) {
                factory.buildPoints(credentials, bodyJson)
            } else {
                factory.buildPoints(credentials, bodyJson, host)
            }
            executeSigned(
                signed,
                rateLimited = PointRecordsPage(10006, "rate limit", emptyList(), request.pageNo, 0),
                parse = { BybitJson.parsePoints(it) }
            )
        }
    }

    override suspend fun queryInterTransfers(
        credentials: Credentials,
        request: TransferListRequest
    ): TransferPage {
        return withHosts(BybitThrottle.Transfer) { host ->
            val query = factory.transferQuery(request)
            val signed = if (host == null) {
                factory.buildTransferGet(credentials, query)
            } else {
                factory.buildTransferGet(credentials, query, host)
            }
            executeSigned(
                signed,
                rateLimited = TransferPage(10006, "rate limit", emptyList(), null),
                parse = { BybitJson.parseTransfers(it) }
            )
        }
    }

    override suspend fun queryFundingHistory(
        credentials: Credentials,
        request: FundingHistoryRequest
    ): FundingPage {
        return withHosts { host ->
            val query = factory.fundingQuery(request)
            val signed = if (host == null) {
                factory.buildFundingGet(credentials, query)
            } else {
                factory.buildFundingGet(credentials, query, host)
            }
            executeSigned(
                signed,
                rateLimited = FundingPage(10006, "rate limit", emptyList(), null),
                parse = { BybitJson.parseFunding(it) }
            )
        }
    }

    override suspend fun queryEarnYield(
        credentials: Credentials,
        request: EarnYieldRequest
    ): EarnYieldPage {
        return withHosts { host ->
            val query = factory.earnYieldQuery(request)
            val signed = if (host == null) {
                factory.buildEarnYieldGet(credentials, query)
            } else {
                factory.buildEarnYieldGet(credentials, query, host)
            }
            executeSigned(
                signed,
                rateLimited = EarnYieldPage(10006, "rate limit", emptyList(), null),
                parse = { BybitJson.parseEarnYield(it) }
            )
        }
    }

    override suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage {
        return withHosts { host ->
            val signed = if (host == null) {
                factory.buildQueryApiGet(credentials)
            } else {
                factory.buildQueryApiGet(credentials, host)
            }
            executeSigned(
                signed,
                rateLimited = ApiKeyInfoPage(10006, "rate limit", null),
                parse = { BybitJson.parseApiKey(it) }
            )
        }
    }

    override suspend fun queryPointsTier(credentials: Credentials): PointsTierPage {
        return withHosts(BybitThrottle.Card) { host ->
            val signed = if (host == null) {
                factory.buildPointsTier(credentials)
            } else {
                factory.buildPointsTier(credentials, host)
            }
            executeSigned(
                signed,
                rateLimited = PointsTierPage(10006, "rate limit"),
                parse = { BybitJson.parsePointsTier(it) }
            )
        }
    }

    override suspend fun queryPointsBalance(credentials: Credentials): PointsBalancePage {
        return withHosts(BybitThrottle.Card) { host ->
            val signed = if (host == null) {
                factory.buildPointsBalance(credentials)
            } else {
                factory.buildPointsBalance(credentials, host)
            }
            executeSigned(
                signed,
                rateLimited = PointsBalancePage(10006, "rate limit"),
                parse = { BybitJson.parsePointsBalance(it) }
            )
        }
    }

    override suspend fun queryCoinsBalance(credentials: Credentials): CoinsBalancePage {
        return withHosts { host ->
            val signed = if (host == null) {
                factory.buildCoinsBalanceGet(credentials)
            } else {
                factory.buildCoinsBalanceGet(credentials, host)
            }
            executeSigned(
                signed,
                rateLimited = CoinsBalancePage(10006, "rate limit"),
                parse = { BybitJson.parseCoinsBalance(it) }
            )
        }
    }

    override suspend fun queryAssetOverview(credentials: Credentials): AssetOverviewPage {
        return withHosts { host ->
            val signed = if (host == null) {
                factory.buildAssetOverviewGet(credentials)
            } else {
                factory.buildAssetOverviewGet(credentials, host)
            }
            executeSigned(
                signed,
                rateLimited = AssetOverviewPage(10006, "rate limit"),
                parse = { BybitJson.parseAssetOverview(it) }
            )
        }
    }

    private suspend fun <T> withHosts(
        throttle: BybitThrottle = BybitThrottle.Fast,
        block: (String?) -> T
    ): T {
        return gate.withPermit(throttle) {
            withContext(Dispatchers.IO) {
                val hosts = listOf(null as String?) + alternateBaseUrls
                var lastError: IOException? = null
                for ((index, host) in hosts.withIndex()) {
                    try {
                        return@withContext block(host)
                    } catch (error: IOException) {
                        log.d("bybit io ${error.javaClass.simpleName} ${error.message.orEmpty()}")
                        lastError = error
                        val canRetry = index < hosts.lastIndex && connectionFailure(error)
                        if (!canRetry) {
                            throw error
                        }
                    }
                }
                throw lastError ?: IOException("bybit request failed")
            }
        }
    }

    private fun <T> executeSigned(
        signed: SignedRequest,
        rateLimited: T,
        parse: (String) -> T
    ): T {
        val req = Request.Builder()
            .url(signed.url)
            .apply {
                signed.headers.forEach { (k, v) -> header(k, v) }
                if (signed.method == "GET") {
                    get()
                } else {
                    post(signed.body.toRequestBody("application/json".toMediaType()))
                }
            }
            .build()
        return http.newCall(req).execute().use { response ->
            if (response.code == 429) {
                return@use rateLimited
            }
            if (response.code in 300..399) {
                throw IOException("http ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful && text.isEmpty()) {
                throw IOException("http ${response.code}")
            }
            log.d("bybit response http=${response.code} bytes=${text.length}")
            parse(text)
        }
    }

    private fun connectionFailure(error: IOException): Boolean {
        if (error.message == "invalid bybit json") return false
        if (error.message?.startsWith("http ") == true) return false
        return true
    }

    companion object {
        fun defaultHttp(): OkHttpClient {
            return OkHttpClient.Builder()
                .certificatePinner(BybitTls.certificatePinner())
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .protocols(listOf(Protocol.HTTP_1_1))
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return Dns.SYSTEM.lookup(hostname).sortedBy { addr ->
                            if (addr is Inet4Address) 0 else 1
                        }
                    }
                })
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "CardTrack/1.0 (Android)")
                            .build()
                    )
                }
                .build()
        }
    }
}
