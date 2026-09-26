package com.sai.cardtrack.bybit

import com.sai.cardtrack.domain.BybitAssetRecord
import com.sai.cardtrack.domain.BybitEarnYieldRecord
import com.sai.cardtrack.domain.BybitFundingRecord
import com.sai.cardtrack.domain.BybitPointRecord
import com.sai.cardtrack.domain.BybitTransfer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object BybitJson {
    fun parsePage(json: String): AssetRecordsPage {
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            val pageNo = result?.optInt("pageNo", 1) ?: 1
            val totalCount = result?.optInt("totalCount", 0) ?: 0
            val pageSize = result?.optInt("pageSize", 0) ?: 0
            val data: JSONArray = result?.optJSONArray("data") ?: JSONArray()
            val records = buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    add(
                        BybitAssetRecord(
                            txnId = item.optString("txnId").ifEmpty { null },
                            orderNo = item.optString("orderNo").ifEmpty { null },
                            side = item.optString("side"),
                            paidAmount = item.optString("paidAmount"),
                            paidCurrency = item.optString("paidCurrency"),
                            merchName = item.optString("merchName"),
                            txnCreate = item.optLong("txnCreate"),
                            status = item.optString("status"),
                            tradeStatus = item.optString("tradeStatus"),
                            basicAmount = item.optString("basicAmount"),
                            basicCurrency = item.optString("basicCurrency"),
                            billAmount = item.optString("billAmount"),
                            paidFiat = item.optString("paidFiat"),
                            transactionAmount = item.optString("transactionAmount"),
                            transactionCurrency = item.optString("transactionCurrency"),
                            totalFees = item.optString("totalFees"),
                            foreignTransactionFee = item.optString("foreignTransactionFee"),
                            withdrawalFee = item.optString("withdrawalFee"),
                            fxPad = item.optString("fxPad"),
                            totalTax = item.optString("totalTax"),
                            declinedReason = item.optString("declinedReason"),
                            mccCode = item.optString("mccCode"),
                            merchCategoryDesc = item.optString("merchCategoryDesc")
                        )
                    )
                }
            }
            return AssetRecordsPage(retCode, retMsg, records, pageNo, totalCount, pageSize)
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parsePoints(json: String): PointRecordsPage {
        // official-v5/bybit-card/point/records.mdx: transactionId, outOrderId, bizTxnId
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            val pageNo = result?.optInt("pageNo", 1) ?: 1
            val totalCount = result?.optInt("totalCount", 0) ?: 0
            val data: JSONArray = result?.optJSONArray("data") ?: JSONArray()
            val records = buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    add(
                        BybitPointRecord(
                            bizId = item.optString("bizId").ifEmpty { null },
                            transactionId = item.optString("transactionId").ifEmpty { null },
                            point = item.optInt("point", 0),
                            side = item.optString("side"),
                            type = item.optString("type"),
                            createTime = item.optLong("createTime"),
                            outOrderId = item.optString("outOrderId").ifEmpty { null },
                            bizTxnId = item.optString("bizTxnId").ifEmpty { null }
                        )
                    )
                }
            }
            return PointRecordsPage(retCode, retMsg, records, pageNo, totalCount)
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parseTransfers(json: String): TransferPage {
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            val next = result?.optString("nextPageCursor").orEmpty().ifEmpty { null }
            val data: JSONArray = result?.optJSONArray("list") ?: JSONArray()
            val records = buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    add(
                        BybitTransfer(
                            transferId = item.optString("transferId"),
                            coin = item.optString("coin"),
                            amount = item.optString("amount"),
                            fromAccountType = item.optString("fromAccountType"),
                            toAccountType = item.optString("toAccountType"),
                            timestamp = parseEpochMillis(item.optString("timestamp")),
                            status = item.optString("status")
                        )
                    )
                }
            }
            return TransferPage(retCode, retMsg, records, next)
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parseFunding(json: String): FundingPage {
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            val next = result?.optString("nextPageCursor").orEmpty().ifEmpty { null }
            val data: JSONArray = result?.optJSONArray("list") ?: JSONArray()
            val records = buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.optString("currcCursor").ifEmpty {
                        item.optString("cursor")
                    }
                    add(
                        BybitFundingRecord(
                            id = id,
                            currency = item.optString("currency"),
                            ioDirection = item.optString("ioDirection"),
                            txnAmt = item.optString("txnAmt"),
                            createTime = parseEpochMillis(item.optString("createTime")),
                            showBusiType = item.optString("showBusiType"),
                            showBusiTypeEn = item.optString("showBusiTypeEn"),
                            description = item.optString("description"),
                            descriptionEn = item.optString("descriptionEn")
                        )
                    )
                }
            }
            return FundingPage(retCode, retMsg, records, next)
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parseEarnYield(json: String): EarnYieldPage {
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            val next = result?.optString("nextPageCursor").orEmpty().ifEmpty { null }
            val listed = result?.optJSONArray("list")
            val yielded = result?.optJSONArray("yield")
            val data: JSONArray = when {
                listed != null && listed.length() > 0 -> listed
                yielded != null && yielded.length() > 0 -> yielded
                else -> listed ?: yielded ?: JSONArray()
            }
            val records = buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    add(
                        BybitEarnYieldRecord(
                            id = item.optString("id"),
                            coin = item.optString("coin"),
                            amount = item.optString("amount"),
                            status = item.optString("status"),
                            createdAt = parseEpochMillis(item.optString("createdAt"))
                        )
                    )
                }
            }
            return EarnYieldPage(retCode, retMsg, records, next)
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parseApiKey(json: String): ApiKeyInfoPage {
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            val info = if (result == null) {
                null
            } else {
                val permissions = result.optJSONObject("permissions")
                val deadlineDay = if (result.has("deadlineDay") && !result.isNull("deadlineDay")) {
                    result.optInt("deadlineDay")
                } else {
                    null
                }
                ApiKeyInfo(
                    readOnly = result.optInt("readOnly", -1),
                    isMaster = result.optBoolean("isMaster", false),
                    bitCard = stringList(permissions, "BitCard"),
                    wallet = stringList(permissions, "Wallet"),
                    ips = stringList(result.optJSONArray("ips")),
                    deadlineDay = deadlineDay
                )
            }
            return ApiKeyInfoPage(retCode, retMsg, info)
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parsePointsTier(json: String): PointsTierPage {
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            return PointsTierPage(
                retCode = retCode,
                retMsg = retMsg,
                usedLimit = result?.optString("usedLimit").orEmpty(),
                limit = result?.optString("limit").orEmpty(),
                unit = result?.optString("unit").orEmpty(),
                tier = result?.optString("tier").orEmpty(),
                autoCashback = result?.optBoolean("autoCashback", false) ?: false
            )
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parsePointsBalance(json: String): PointsBalancePage {
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            return PointsBalancePage(
                retCode = retCode,
                retMsg = retMsg,
                availablePoint = optPoint(result, "availablePoint"),
                pendingPoint = optPoint(result, "pendingPoint")
            )
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parseCoinsBalance(json: String): CoinsBalancePage {
        // official-v5/asset/balance/all-balance.mdx — result.balance[].walletBalance
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            val rows: JSONArray = result?.optJSONArray("balance") ?: JSONArray()
            var usdtWallet = ""
            var usdcWallet = ""
            for (i in 0 until rows.length()) {
                val item = rows.getJSONObject(i)
                when (item.optString("coin").uppercase()) {
                    "USDT" -> usdtWallet = optPoint(item, "walletBalance")
                    "USDC" -> usdcWallet = optPoint(item, "walletBalance")
                }
            }
            return CoinsBalancePage(retCode, retMsg, usdtWallet, usdcWallet)
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    fun parseAssetOverview(json: String): AssetOverviewPage {
        // official-v5/asset/balance/asset-overview.mdx — list[].accountType / totalEquity
        try {
            val root = JSONObject(json)
            val retCode = root.optInt("retCode", -1)
            val retMsg = root.optString("retMsg", "")
            val result = root.optJSONObject("result")
            val rows: JSONArray = result?.optJSONArray("list") ?: JSONArray()
            var fundingEquity = ""
            var easyEarnEquity = ""
            for (i in 0 until rows.length()) {
                val item = rows.getJSONObject(i)
                val account = item.optString("accountType")
                if (account.equals("FundingAccount", ignoreCase = true)) {
                    fundingEquity = optPoint(item, "totalEquity")
                }
                if (account.equals("Earn", ignoreCase = true)) {
                    easyEarnEquity = easyEarnEquity(item)
                }
            }
            return AssetOverviewPage(retCode, retMsg, fundingEquity, easyEarnEquity)
        } catch (e: JSONException) {
            throw IOException("invalid bybit json", e)
        }
    }

    private fun easyEarnEquity(account: JSONObject): String {
        val categories = account.optJSONArray("categories") ?: return ""
        for (i in 0 until categories.length()) {
            val row = categories.getJSONObject(i)
            if (row.optString("category").equals("Easy Earn", ignoreCase = true)) {
                return optPoint(row, "equity")
            }
        }
        return ""
    }

    private fun optPoint(obj: JSONObject?, key: String): String {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return ""
        val opt = obj.opt(key)
        return when (opt) {
            is Number -> opt.toString()
            is String -> opt
            else -> ""
        }
    }

    private fun stringList(permissions: JSONObject?, key: String): List<String> {
        return stringList(permissions?.optJSONArray(key))
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(array.getString(i))
            }
        }
    }

    private fun parseEpochMillis(raw: String): Long {
        val n = raw.toLongOrNull() ?: return 0L
        return if (n > 0L && n < 10_000_000_000L) n * 1000L else n
    }
}
