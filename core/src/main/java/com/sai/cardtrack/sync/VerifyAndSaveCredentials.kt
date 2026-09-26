package com.sai.cardtrack.sync

import com.sai.cardtrack.bybit.AssetRecordsRequest
import com.sai.cardtrack.bybit.BybitCardClient
import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.domain.QueryType
import com.sai.cardtrack.ui.AppLocale
import com.sai.cardtrack.ui.UiCopy
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

sealed class VerifyResult {
    data object Ok : VerifyResult()
    data class Failed(val banner: String) : VerifyResult()
}

class VerifyAndSaveCredentials(
    private val client: BybitCardClient,
    private val store: CredentialsStore
) {
    suspend fun execute(credentials: Credentials, locale: AppLocale = AppLocale.Ru): VerifyResult {
        val keyPage = try {
            client.queryApiKey(credentials)
        } catch (error: IOException) {
            return VerifyResult.Failed(ioExceptionBanner(error, locale))
        }
        if (keyPage.retCode != 0) {
            return VerifyResult.Failed(bybitAccessBanner(keyPage.retCode, keyPage.retMsg, locale))
        }
        val info = keyPage.info
            ?: return VerifyResult.Failed(bybitAccessBanner(keyPage.retCode, "", locale))
        val permissionBanner = KeyPermissionCheck.banner(info, locale)
        if (permissionBanner != null) {
            return VerifyResult.Failed(permissionBanner)
        }
        val page = try {
            client.queryAssetRecords(
                credentials,
                AssetRecordsRequest(
                    type = QueryType.Auth,
                    page = 1,
                    limit = 1,
                    createBeginTime = null,
                    createEndTime = null
                )
            )
        } catch (error: IOException) {
            return VerifyResult.Failed(ioExceptionBanner(error, locale))
        }
        if (page.retCode == 0) {
            store.save(credentials)
            return VerifyResult.Ok
        }
        return VerifyResult.Failed(bybitAccessBanner(page.retCode, page.retMsg, locale))
    }
}

fun ioExceptionBanner(error: IOException, locale: AppLocale = AppLocale.Ru): String {
    val copy = UiCopy(locale)
    if (error.message == "invalid bybit json") {
        return copy.invalidJson
    }
    val http = error.message
        ?.takeIf { it.startsWith("http ") }
        ?.removePrefix("http ")
        ?.toIntOrNull()
    if (http != null) {
        return when (http) {
            401 -> copy.http401
            403 -> copy.http403
            404 -> copy.http404
            429 -> bybitAccessBanner(10006, "Too many visits!", locale)
            else -> copy.httpStatus(http)
        }
    }
    val chain = generateSequence<Throwable>(error) { it.cause }
    if (chain.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException }) {
        return copy.sslFail
    }
    if (chain.any { it is UnknownHostException }) {
        return copy.unknownHost
    }
    if (chain.any { it is SocketTimeoutException }) {
        return copy.timeout
    }
    if (chain.any { it is ConnectException }) {
        return copy.connectFail
    }
    return copy.noNetwork
}

const val WALLET_BANNER: String =
    "У ключа нет права Wallet (переводы). Отметь Account Transfer, только чтение, без вывода."

fun SyncError.banner(locale: AppLocale = AppLocale.Ru): String {
    val copy = UiCopy(locale)
    return when (this) {
        SyncError.Network -> copy.noNetwork
        SyncError.RateLimit -> bybitAccessBanner(10006, "", locale)
        SyncError.Auth -> copy.genericKeyFail
        SyncError.Unknown -> copy.syncUnknown
        SyncError.Wallet -> copy.wallet
    }
}

fun bybitAccessBanner(retCode: Int, retMsg: String, locale: AppLocale = AppLocale.Ru): String {
    val copy = UiCopy(locale)
    val raw = retMsg.trim()
    if (retCode == 10005 || raw.contains("permission denied", ignoreCase = true)) {
        return copy.noCard
    }
    if (retCode == 10010 || raw.contains("unmatched ip", ignoreCase = true)) {
        return copy.unmatchedIp
    }
    if (retCode == 10001 || raw.contains("param_illegal", ignoreCase = true)) {
        return copy.paramIllegal
    }
    return when (retCode) {
        10002 -> copy.clockSkew
        10003 -> copy.invalidKey
        10004 -> copy.badSignature
        10006 -> copy.rateLimit
        10007 -> copy.authRejected
        10008 -> copy.accountRestricted
        10009 -> copy.regionBlocked
        10016 -> copy.serverError
        10018 -> copy.ipRateLimit
        33004 -> copy.keyExpired
        else -> raw.ifEmpty {
            if (retCode > 0) copy.rejectedCode(retCode) else copy.genericKeyFail
        }
    }
}

fun displaySyncBanner(
    reason: SyncError,
    retMsg: String?,
    retCode: Int? = null,
    locale: AppLocale = AppLocale.Ru
): String {
    val copy = UiCopy(locale)
    if (reason == SyncError.Wallet) {
        return copy.wallet
    }
    if (reason == SyncError.Network) {
        return retMsg?.trim()?.ifEmpty { null } ?: reason.banner(locale)
    }
    val code = retCode ?: if (reason == SyncError.RateLimit) 10006 else -1
    return bybitAccessBanner(code, retMsg.orEmpty(), locale)
}
