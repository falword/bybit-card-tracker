package com.sai.cardtrack.bybit

import com.sai.cardtrack.domain.QueryType

data class AssetRecordsRequest(
    val type: QueryType,
    val page: Int,
    val limit: Int,
    val createBeginTime: Long?,
    val createEndTime: Long?
)

data class AssetRecordsPage(
    val retCode: Int,
    val retMsg: String,
    val records: List<com.sai.cardtrack.domain.BybitAssetRecord>,
    val pageNo: Int,
    val totalCount: Int,
    val pageSize: Int = 0
)

data class SignedRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val method: String = "POST"
)

data class PointRecordsRequest(
    val pageNo: Int,
    val pageSize: Int,
    val startTime: Long?,
    val endTime: Long?
)

data class PointRecordsPage(
    val retCode: Int,
    val retMsg: String,
    val records: List<com.sai.cardtrack.domain.BybitPointRecord>,
    val pageNo: Int,
    val totalCount: Int
)

data class TransferListRequest(
    val startTime: Long?,
    val endTime: Long?,
    val limit: Int,
    val cursor: String?
)

data class TransferPage(
    val retCode: Int,
    val retMsg: String,
    val records: List<com.sai.cardtrack.domain.BybitTransfer>,
    val nextCursor: String?
)

data class FundingHistoryRequest(
    val createTimeFromSec: Long?,
    val createTimeToSec: Long?,
    val limit: Int,
    val cursor: String?
)

data class FundingPage(
    val retCode: Int,
    val retMsg: String,
    val records: List<com.sai.cardtrack.domain.BybitFundingRecord>,
    val nextCursor: String?
)

data class EarnYieldRequest(
    val startTime: Long,
    val endTime: Long,
    val limit: Int,
    val cursor: String?
)

data class EarnYieldPage(
    val retCode: Int,
    val retMsg: String,
    val records: List<com.sai.cardtrack.domain.BybitEarnYieldRecord>,
    val nextCursor: String?
)

data class ApiKeyInfo(
    val readOnly: Int,
    val isMaster: Boolean,
    val bitCard: List<String>,
    val wallet: List<String>,
    val ips: List<String>,
    val deadlineDay: Int?
)

data class ApiKeyInfoPage(
    val retCode: Int,
    val retMsg: String,
    val info: ApiKeyInfo?
)

data class PointsTierPage(
    val retCode: Int,
    val retMsg: String,
    val usedLimit: String = "",
    val limit: String = "",
    val unit: String = "",
    val tier: String = "",
    val autoCashback: Boolean = false
)

data class PointsBalancePage(
    val retCode: Int,
    val retMsg: String,
    val availablePoint: String = "",
    val pendingPoint: String = ""
)

data class CoinsBalancePage(
    val retCode: Int,
    val retMsg: String,
    val usdtWallet: String = "",
    val usdcWallet: String = ""
)

data class AssetOverviewPage(
    val retCode: Int,
    val retMsg: String,
    val fundingEquity: String = "",
    val easyEarnEquity: String = ""
)
