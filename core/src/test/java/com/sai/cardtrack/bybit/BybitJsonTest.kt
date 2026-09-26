package com.sai.cardtrack.bybit

import com.sai.cardtrack.domain.FundBalanceMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BybitJsonTest {

    @Test
    fun `parses asset records page`() {
        val json = """
            {"retCode":0,"retMsg":"OK","result":{"pageSize":100,"pageNo":1,"totalCount":1,"data":[{
              "txnId":"TXN20230101001","orderNo":"ORD1","side":"3","paidAmount":"28.00",
              "paidCurrency":"THB","basicAmount":"0.86","basicCurrency":"USD",
              "billAmount":"0.86","merchName":"Amazon","txnCreate":1672211918471,
              "status":"1","tradeStatus":"1"
            }]}}
        """.trimIndent()
        val page = BybitJson.parsePage(json)
        assertEquals(0, page.retCode)
        assertEquals(100, page.pageSize)
        assertEquals(1, page.records.size)
        assertEquals("TXN20230101001", page.records[0].txnId)
        assertEquals("28.00", page.records[0].paidAmount)
        assertEquals("THB", page.records[0].paidCurrency)
        assertEquals("0.86", page.records[0].basicAmount)
        assertEquals("USD", page.records[0].basicCurrency)
    }

    @Test
    fun `missing data is empty list`() {
        val page = BybitJson.parsePage("""{"retCode":10004,"retMsg":"error sign!","result":{}}""")
        assertEquals(10004, page.retCode)
        assertEquals("error sign!", page.retMsg)
        assertEquals(0, page.records.size)
        assertNull(page.records.firstOrNull())
    }

    @Test
    fun `non json body is io exception`() {
        try {
            BybitJson.parsePage("<html>nope</html>")
            throw AssertionError("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("invalid bybit json"))
        }
    }

    @Test
    fun `parses cashback point records`() {
        val json = """
            {"retCode":0,"retMsg":"OK","result":{"pageNo":1,"totalCount":1,"data":[{
              "bizId":"B1","transactionId":"T1","point":100,"side":"1","type":"CASHBACK",
              "createTime":1672211918471
            }]}}
        """.trimIndent()
        val page = BybitJson.parsePoints(json)
        assertEquals(100, page.records.single().point)
        assertEquals("B1", page.records.single().bizId)
        assertEquals("T1", page.records.single().transactionId)
    }

    @Test
    fun `parses point record join ids from official fields`() {
        // official-v5/bybit-card/point/records.mdx: transactionId, outOrderId, bizTxnId
        val json = """
            {"retCode":0,"retMsg":"OK","result":{"pageNo":1,"totalCount":1,"data":[{
              "bizId":"B1","transactionId":"TXN1","outOrderId":"ORD1","bizTxnId":"BIZ1",
              "point":100,"side":"1","type":"CASHBACK","createTime":1672211918471
            }]}}
        """.trimIndent()
        val row = BybitJson.parsePoints(json).records.single()
        assertEquals("TXN1", row.transactionId)
        assertEquals("ORD1", row.outOrderId)
        assertEquals("BIZ1", row.bizTxnId)
    }

    @Test
    fun `parses live numeric point type and string createTime`() {
        val json = """
            {"retCode":0,"retMsg":"success","result":{"pageNo":1,"totalCount":1,"data":[{
              "bizId":"B1","transactionId":"T1","point":17,"side":"1","type":1,
              "createTime":"1788656374000"
            }]}}
        """.trimIndent()
        val page = BybitJson.parsePoints(json)
        assertEquals("1", page.records.single().type)
        assertEquals("1", page.records.single().side)
        assertEquals(17, page.records.single().point)
        assertEquals(1_788_656_374_000L, page.records.single().createTime)
    }

    @Test
    fun `parses funding transfers and seconds timestamps`() {
        val json = """
            {"retCode":0,"retMsg":"success","result":{"list":[{
              "transferId":"abc","coin":"USDT","amount":"20","fromAccountType":"UNIFIED",
              "toAccountType":"FUND","timestamp":"1667283263","status":"SUCCESS"
            }],"nextPageCursor":""}}
        """.trimIndent()
        val page = BybitJson.parseTransfers(json)
        assertEquals("abc", page.records.single().transferId)
        assertEquals(1_667_283_263_000L, page.records.single().timestamp)
    }

    @Test
    fun `parses funding history inbound and seconds timestamps`() {
        val json = """
            {"retCode":0,"retMsg":"OK","result":{"nextPageCursor":"","list":[{
              "memberId":"1","currency":"USDT","ioDirection":"I","txnAmt":"20",
              "afterAmt":"100","createTime":"1667283263",
              "showBusiType":"fundingAccountRecordDeposit","showBusiTypeEn":"Deposit",
              "description":"onChain","descriptionEn":"On-chain Deposit",
              "currcCursor":"abc="
            }]}}
        """.trimIndent()
        val page = BybitJson.parseFunding(json)
        assertEquals("abc=", page.records.single().id)
        assertEquals(1_667_283_263_000L, page.records.single().createTime)
        assertEquals("I", page.records.single().ioDirection)
        assertEquals("20", page.records.single().txnAmt)
    }

    @Test
    fun `parses query-api key permissions`() {
        // official-v5/user/apikey-info.mdx sample (trimmed)
        val json = """
            {"retCode":0,"retMsg":"","result":{
              "readOnly":1,"isMaster":true,"secret":"",
              "permissions":{"BitCard":["BitCard"],"Wallet":["AccountTransfer"]},
              "ips":["1.2.3.4"],"deadlineDay":-2
            }}
        """.trimIndent()
        val page = BybitJson.parseApiKey(json)
        assertEquals(0, page.retCode)
        assertEquals(1, page.info!!.readOnly)
        assertEquals(true, page.info!!.isMaster)
        assertEquals(listOf("BitCard"), page.info!!.bitCard)
        assertEquals(listOf("AccountTransfer"), page.info!!.wallet)
        assertEquals(listOf("1.2.3.4"), page.info!!.ips)
        assertEquals(-2, page.info!!.deadlineDay)
    }

    @Test
    fun `query-api missing deadlineDay is null`() {
        val page = BybitJson.parseApiKey(
            """{"retCode":0,"retMsg":"OK","result":{"readOnly":1,"isMaster":true,"permissions":{}}}"""
        )
        assertEquals(null, page.info!!.deadlineDay)
        assertEquals(emptyList<String>(), page.info!!.bitCard)
        assertEquals(emptyList<String>(), page.info!!.wallet)
    }

    @Test
    fun `parses asset-record fees mcc and declinedReason`() {
        // official-v5/bybit-card/asset-records.mdx sample (trimmed)
        val json = """
            {"retCode":0,"retMsg":"OK","result":{"pageNo":1,"totalCount":1,"data":[{
              "txnId":"TXN20230101001","orderNo":"ORD20230101001","side":"3",
              "paidAmount":"101.50","paidCurrency":"USDT","merchName":"Amazon",
              "txnCreate":1672211918471,"status":"1","tradeStatus":"1",
              "totalFees":"1.50","foreignTransactionFee":"0","withdrawalFee":"0",
              "fxPad":"","totalTax":"1.50","billAmount":"101.50",
              "transactionAmount":"100.00","transactionCurrency":"USD",
              "declinedReason":"","mccCode":"5411","merchCategoryDesc":"Grocery Stores"
            }]}}
        """.trimIndent()
        val row = BybitJson.parsePage(json).records[0]
        assertEquals("1.50", row.totalFees)
        assertEquals("0", row.foreignTransactionFee)
        assertEquals("0", row.withdrawalFee)
        assertEquals("", row.fxPad)
        assertEquals("1.50", row.totalTax)
        assertEquals("5411", row.mccCode)
        assertEquals("Grocery Stores", row.merchCategoryDesc)
        assertEquals("", row.declinedReason)
    }

    @Test
    fun `parses points tier sample`() {
        // official-v5/bybit-card/point/tier.mdx
        val page = BybitJson.parsePointsTier(
            """{"retCode":0,"retMsg":"OK","result":{"usedLimit":"10.00","limit":"500.00","unit":"1","tier":"GOLD","autoCashback":true}}"""
        )
        assertEquals("10.00", page.usedLimit)
        assertEquals("500.00", page.limit)
        assertEquals("1", page.unit)
        assertEquals("GOLD", page.tier)
        assertEquals(true, page.autoCashback)
    }

    @Test
    fun `parses points balance numeric availablePoint`() {
        // official-v5/bybit-card/point/balance.mdx — sample uses numbers
        val page = BybitJson.parsePointsBalance(
            """{"retCode":0,"retMsg":"OK","result":{"availablePoint":5000,"pendingPoint":200}}"""
        )
        assertEquals("5000", page.availablePoint)
        assertEquals("200", page.pendingPoint)
    }

    @Test
    fun `parses fund coins walletBalance usdt and usdc`() {
        // official-v5/asset/balance/all-balance.mdx — walletBalance per coin
        val page = BybitJson.parseCoinsBalance(
            """{"retCode":0,"retMsg":"success","result":{"accountType":"FUND","balance":[
              {"coin":"USDT","walletBalance":"12.3","transferBalance":"1","bonus":""},
              {"coin":"USDC","walletBalance":"0.70","transferBalance":"0","bonus":""}
            ]}}"""
        )
        assertEquals(0, page.retCode)
        assertEquals("12.3", page.usdtWallet)
        assertEquals("0.70", page.usdcWallet)
    }

    @Test
    fun `parses earn yield official yield array`() {
        // official-v5/finance/earn/easy-onchain/yield-history.mdx
        val json = """
            {"retCode":0,"retMsg":"","result":{"yield":[{
              "productId":"1","coin":"USDT","id":"42","amount":"1.50",
              "yieldType":"Normal","distributionMode":"Manual",
              "effectiveStakingAmount":"100","orderId":"00000000-0000-4000-8000-000000000001",
              "status":"Success","createdAt":"1700000000000"
            }],"nextPageCursor":"n1"}}
        """.trimIndent()
        val page = BybitJson.parseEarnYield(json)
        assertEquals(0, page.retCode)
        assertEquals("n1", page.nextCursor)
        val row = page.records.single()
        assertEquals("42", row.id)
        assertEquals("USDT", row.coin)
        assertEquals("1.50", row.amount)
        assertEquals("Success", row.status)
        assertEquals(1_700_000_000_000L, row.createdAt)
    }

    @Test
    fun `parses earn yield list array when yield is absent`() {
        val json = """
            {"retCode":0,"retMsg":"OK","result":{"list":[{
              "id":"2","coin":"USDC","amount":"1.25","status":"Success",
              "createdAt":"1700000000000"
            }],"nextPageCursor":""}}
        """.trimIndent()
        val page = BybitJson.parseEarnYield(json)
        assertEquals("2", page.records.single().id)
        assertEquals("USDC", page.records.single().coin)
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `parses earn yield official yield array when list is empty`() {
        // official-v5/finance/earn/easy-onchain/yield-history.mdx: table says list, example uses yield
        val json = """
            {"retCode":0,"retMsg":"","result":{"list":[],"yield":[{
              "productId":"1","coin":"USDT","id":"42","amount":"1.50",
              "status":"Success","createdAt":"1700000000000"
            }],"nextPageCursor":""}}
        """.trimIndent()
        val page = BybitJson.parseEarnYield(json)
        assertEquals("42", page.records.single().id)
        assertEquals("1.50", page.records.single().amount)
    }

    @Test
    fun `parses funding account totalEquity not leftover usdt`() {
        // official-v5/asset/balance/asset-overview.mdx — FundingAccount.totalEquity is USD
        val page = BybitJson.parseAssetOverview(
            """{"retCode":0,"retMsg":"Success","result":{"totalEquity":"9999","list":[
              {"accountType":"Earn","totalEquity":"100","valuationCurrency":"USD","coinDetail":[]},
              {"accountType":"FundingAccount","totalEquity":"80","valuationCurrency":"USD",
               "coinDetail":[{"coin":"USDT","equity":"0.50"},{"coin":"BTC","equity":"0.05"}]}
            ]}}"""
        )
        assertEquals(0, page.retCode)
        assertEquals("80", page.fundingEquity)
    }

    @Test
    fun `parses easy earn equity for card available`() {
        // official-v5/asset/balance/asset-overview.mdx — Earn categories Easy Earn
        // Bybit Card spends Funding + Flexible Easy Earn
        val page = BybitJson.parseAssetOverview(
            """{"retCode":0,"retMsg":"Success","result":{"totalEquity":"9999","list":[
              {"accountType":"FundingAccount","totalEquity":"10.00","valuationCurrency":"USD",
               "coinDetail":[{"coin":"USDT","equity":"0.50"}]},
              {"accountType":"Earn","totalEquity":"30","valuationCurrency":"USD","categories":[
                {"category":"Easy Earn","equity":"20.00","coinDetail":[]},
                {"category":"OnChain","equity":"10.00","coinDetail":[]}
              ]}
            ]}}"""
        )
        assertEquals("10.00", page.fundingEquity)
        assertEquals("20.00", page.easyEarnEquity)
    }

    @Test
    fun `parses funding leftover plus easy earn usdt`() {
        val page = BybitJson.parseAssetOverview(
            """{"retCode":0,"retMsg":"Success","result":{"totalEquity":"40","list":[
              {"accountType":"FundingAccount","totalEquity":"10.00","valuationCurrency":"USD",
               "coinDetail":[{"coin":"USD","equity":"9.00"},{"coin":"USDT","equity":"1.00"}]},
              {"accountType":"Earn","totalEquity":"20.00","valuationCurrency":"USD","categories":[
                {"category":"Easy Earn","equity":"20.00",
                 "coinDetail":[{"coin":"USDT","equity":"20.00"}]}
              ]}
            ]}}"""
        )
        assertEquals("10.00", page.fundingEquity)
        assertEquals("20.00", page.easyEarnEquity)
        assertEquals("30.00", FundBalanceMath.cardAvailableUsd(page.fundingEquity, page.easyEarnEquity))
    }
}
