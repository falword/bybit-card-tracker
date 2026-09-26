package com.sai.cardtrack.bybit

import com.sai.cardtrack.data.Credentials

interface BybitCardClient {
    suspend fun queryAssetRecords(
        credentials: Credentials,
        request: AssetRecordsRequest
    ): AssetRecordsPage

    suspend fun queryPointRecords(
        credentials: Credentials,
        request: PointRecordsRequest
    ): PointRecordsPage {
        return PointRecordsPage(0, "OK", emptyList(), request.pageNo, 0)
    }

    suspend fun queryInterTransfers(
        credentials: Credentials,
        request: TransferListRequest
    ): TransferPage {
        return TransferPage(0, "OK", emptyList(), null)
    }

    suspend fun queryFundingHistory(
        credentials: Credentials,
        request: FundingHistoryRequest
    ): FundingPage {
        return FundingPage(10005, "permission denied", emptyList(), null)
    }

    suspend fun queryEarnYield(
        credentials: Credentials,
        request: EarnYieldRequest
    ): EarnYieldPage {
        return EarnYieldPage(10005, "permission denied", emptyList(), null)
    }

    suspend fun queryApiKey(credentials: Credentials): ApiKeyInfoPage {
        return ApiKeyInfoPage(10005, "permission denied", null)
    }

    suspend fun queryPointsTier(credentials: Credentials): PointsTierPage {
        return PointsTierPage(10005, "permission denied")
    }

    suspend fun queryPointsBalance(credentials: Credentials): PointsBalancePage {
        return PointsBalancePage(10005, "permission denied")
    }

    suspend fun queryCoinsBalance(credentials: Credentials): CoinsBalancePage {
        return CoinsBalancePage(10005, "permission denied")
    }

    suspend fun queryAssetOverview(credentials: Credentials): AssetOverviewPage {
        return AssetOverviewPage(10005, "permission denied")
    }
}
