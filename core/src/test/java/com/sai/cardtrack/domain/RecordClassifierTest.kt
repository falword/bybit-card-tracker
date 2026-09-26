package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordClassifierTest {

    private fun record(
        side: String,
        status: String = "1",
        tradeStatus: String = "1",
        txnId: String? = "TXN1",
        orderNo: String? = "ORD1",
        paidAmount: String = "10.00",
        paidCurrency: String = "USDT",
        merchName: String = "Amazon",
        txnCreate: Long = 1_725_000_000_000
    ): BybitAssetRecord {
        return BybitAssetRecord(
            txnId = txnId,
            orderNo = orderNo,
            side = side,
            paidAmount = paidAmount,
            paidCurrency = paidCurrency,
            merchName = merchName,
            txnCreate = txnCreate,
            status = status,
            tradeStatus = tradeStatus
        )
    }

    @Test
    fun `auth transaction is expense pending excluded later by totals`() {
        // given
        val input = record(side = "3", status = "0", tradeStatus = "0")

        // when
        val result = RecordClassifier.classify(QueryType.Auth, input)

        // then
        val keep = result as ClassifyResult.Keep
        assertEquals(TransactionKind.Expense, keep.draft.kind)
        assertEquals(TransactionSource.Purchase, keep.draft.source)
        assertEquals(TransactionStatus.Pending, keep.draft.status)
        assertEquals("TXN1", keep.draft.txnId)
    }

    @Test
    fun `auth success transaction is expense success`() {
        val result = RecordClassifier.classify(QueryType.Auth, record(side = "1", status = "1"))
        val keep = result as ClassifyResult.Keep
        assertEquals(TransactionKind.Expense, keep.draft.kind)
        assertEquals(TransactionStatus.Success, keep.draft.status)
    }

    @Test
    fun `atm and direct sides are expenses`() {
        val atm = RecordClassifier.classify(QueryType.Auth, record(side = "13")) as ClassifyResult.Keep
        val direct = RecordClassifier.classify(QueryType.Auth, record(side = "7")) as ClassifyResult.Keep
        assertEquals(TransactionKind.Expense, atm.draft.kind)
        assertEquals(TransactionKind.Expense, direct.draft.kind)
    }

    @Test
    fun `declined auth is kept as declined`() {
        val record = record(side = "3", status = "2", tradeStatus = "2").copy(
            declinedReason = "MCC not allowed",
            mccCode = "5411"
        )
        val keep = RecordClassifier.classify(QueryType.Auth, record) as ClassifyResult.Keep
        assertEquals(TransactionSource.Declined, keep.draft.source)
        assertEquals(TransactionStatus.Declined, keep.draft.status)
        assertEquals("MCC not allowed", keep.draft.declinedReason)
        assertEquals("groceries", keep.draft.categoryId)
        assertEquals(CategoryOrigin.Mcc, keep.draft.categoryOrigin)
    }

    @Test
    fun `purchase copies totalFees and grocery mcc`() {
        val keep = RecordClassifier.classify(
            QueryType.Auth,
            record(side = "3").copy(totalFees = "1.50", mccCode = "5411")
        ) as ClassifyResult.Keep
        assertEquals("1.50", keep.draft.fees.totalFees)
        assertEquals(java.math.BigDecimal("1.50"), keep.draft.fees.feeSum())
        assertEquals("groceries", keep.draft.categoryId)
        assertEquals(CategoryOrigin.Mcc, keep.draft.categoryOrigin)
    }

    @Test
    fun `tradeStatus reversal is not listed`() {
        assertTrue(
            RecordClassifier.classify(
                QueryType.Auth,
                record(side = "3", status = "1", tradeStatus = "3")
            ) is ClassifyResult.Skip
        )
    }

    @Test
    fun `authorization reversal side is not listed`() {
        assertTrue(
            RecordClassifier.classify(QueryType.Auth, record(side = "2")) is ClassifyResult.Skip
        )
    }

    @Test
    fun `refund side in auth query is hidden refund not income`() {
        val keep = RecordClassifier.classify(QueryType.Auth, record(side = "5")) as ClassifyResult.Keep
        assertEquals(TransactionSource.Refund, keep.draft.source)
        assertEquals(TransactionKind.Expense, keep.draft.kind)
    }

    @Test
    fun `financial query keeps refunds and clearing expenses`() {
        val refund = RecordClassifier.classify(QueryType.Financial, record(side = "5")) as ClassifyResult.Keep
        assertEquals(TransactionSource.Refund, refund.draft.source)
        val expense = RecordClassifier.classify(QueryType.Financial, record(side = "3")) as ClassifyResult.Keep
        assertEquals(TransactionSource.Purchase, expense.draft.source)
    }

    @Test
    fun `financial clearing expense is a purchase`() {
        val keep = RecordClassifier.classify(QueryType.Financial, record(side = "3")) as ClassifyResult.Keep
        assertEquals(TransactionSource.Purchase, keep.draft.source)
        assertEquals(TransactionStatus.Success, keep.draft.status)
        assertEquals("TXN1", keep.draft.txnId)
    }

    @Test
    fun `blank status with completed tradeStatus is success`() {
        val keep = RecordClassifier.classify(
            QueryType.Auth,
            record(side = "3", status = "", tradeStatus = "1")
        ) as ClassifyResult.Keep
        assertEquals(TransactionStatus.Success, keep.draft.status)
        assertEquals(TransactionSource.Purchase, keep.draft.source)
    }

    @Test
    fun `refund sides are stored as refund source`() {
        val r4 = RecordClassifier.classify(QueryType.Refund, record(side = "4")) as ClassifyResult.Keep
        val r5 = RecordClassifier.classify(QueryType.Refund, record(side = "5")) as ClassifyResult.Keep
        val r6 = RecordClassifier.classify(QueryType.Refund, record(side = "6")) as ClassifyResult.Keep
        assertEquals(TransactionSource.Refund, r4.draft.source)
        assertEquals(TransactionSource.Refund, r5.draft.source)
        assertEquals(TransactionSource.Refund, r6.draft.source)
    }

    @Test
    fun `funding transfer is top up income`() {
        val keep = RecordClassifier.classifyTransfer(
            BybitTransfer(
                transferId = "abc",
                coin = "USDT",
                amount = "20.00",
                fromAccountType = "UNIFIED",
                toAccountType = "FUND",
                timestamp = 1_725_000_000_000,
                status = "SUCCESS"
            )
        ) as ClassifyResult.Keep
        assertEquals("tu_abc", keep.draft.txnId)
        assertEquals(TransactionSource.TopUp, keep.draft.source)
        assertEquals(TransactionKind.Income, keep.draft.kind)
        assertEquals("Пополнение", keep.draft.merchantName)
        assertEquals("20.00", keep.draft.paidAmount)
        assertEquals("USD", keep.draft.paidCurrency)
    }

    @Test
    fun `outgoing or failed transfer is skipped`() {
        val outgoing = BybitTransfer("x", "USDT", "1", "FUND", "UNIFIED", 1L, "SUCCESS")
        val failed = BybitTransfer("y", "USDT", "1", "UNIFIED", "FUND", 1L, "FAILED")
        assertTrue(RecordClassifier.classifyTransfer(outgoing) is ClassifyResult.Skip)
        assertTrue(RecordClassifier.classifyTransfer(failed) is ClassifyResult.Skip)
    }

    @Test
    fun `funding inbound deposit is top up income`() {
        val keep = RecordClassifier.classifyFunding(
            BybitFundingRecord(
                id = "cur1",
                currency = "USDT",
                ioDirection = "I",
                txnAmt = "50.1",
                createTime = 1_725_000_000_000,
                showBusiType = "fundingAccountRecordDeposit",
                showBusiTypeEn = "Deposit",
                description = "onChainDeposit",
                descriptionEn = "On-chain Deposit"
            )
        ) as ClassifyResult.Keep
        assertEquals("fh_cur1", keep.draft.txnId)
        assertEquals(TransactionSource.TopUp, keep.draft.source)
        assertEquals(TransactionKind.Income, keep.draft.kind)
        assertEquals("Пополнение", keep.draft.merchantName)
        assertEquals("50.10", keep.draft.paidAmount)
        assertEquals("USD", keep.draft.paidCurrency)
    }

    @Test
    fun `qr freeze outbound is purchase`() {
        val keep = RecordClassifier.classifyFunding(
            qrFreeze(id = "pay1", amount = "8.54094")
        ) as ClassifyResult.Keep
        assertEquals("qp_pay1", keep.draft.txnId)
        assertEquals(TransactionSource.Purchase, keep.draft.source)
        assertEquals(TransactionKind.Expense, keep.draft.kind)
        assertEquals(TransactionStatus.Success, keep.draft.status)
        assertEquals("Bybit Pay", keep.draft.merchantName)
        assertEquals("8.54", keep.draft.paidAmount)
        assertEquals("USD", keep.draft.paidCurrency)
    }

    @Test
    fun `qr unfreeze inbound is hidden refund`() {
        val keep = RecordClassifier.classifyFunding(
            qrUnfreeze(id = "cnl1", amount = "2.90734")
        ) as ClassifyResult.Keep
        assertEquals("qr_cnl1", keep.draft.txnId)
        assertEquals(TransactionSource.Refund, keep.draft.source)
        assertEquals("2.91", keep.draft.paidAmount)
        assertEquals("USD", keep.draft.paidCurrency)
        assertEquals("Bybit Pay", keep.draft.merchantName)
    }

    @Test
    fun `earn bybit pay redemption is not a purchase`() {
        val earnPay = BybitFundingRecord(
            id = "e2",
            currency = "USDT",
            ioDirection = "I",
            txnAmt = "8.3196",
            createTime = 1_725_000_000_000,
            showBusiType = "fundingAccountRecordEarn",
            showBusiTypeEn = "Earn",
            description = "fundingAccountRecordFiatBybitpayRedemptionMove",
            descriptionEn = "Flexible Savings Auto Redemption (Bybit Pay)"
        )
        assertTrue(RecordClassifier.classifyFunding(earnPay) is ClassifyResult.Skip)
    }

    @Test
    fun `qr c2c collection is not a purchase`() {
        val keep = RecordClassifier.classifyFunding(
            BybitFundingRecord(
                id = "c2c1",
                currency = "USDT",
                ioDirection = "I",
                txnAmt = "5.0000",
                createTime = 1_725_000_000_000,
                showBusiType = "fundingAccountRecordBybitpay",
                showBusiTypeEn = "Bybit Pay",
                description = "fundingAccountRecordFiatBybitpayC2CSettlementDeposit",
                descriptionEn = "Bybit Pay transfer (collection) "
            )
        ) as ClassifyResult.Keep
        assertEquals(TransactionSource.TopUp, keep.draft.source)
        assertEquals("fh_c2c1", keep.draft.txnId)
    }

    @Test
    fun `p2p inbound cancel is refund not top up`() {
        val keep = RecordClassifier.classifyFunding(
            p2p("c1", "I", "40", "fundingAccountRecordP2PCancel", "P2P Cancel")
        ) as ClassifyResult.Keep
        assertEquals("fh_c1", keep.draft.txnId)
        assertEquals(TransactionSource.P2PRefund, keep.draft.source)
        assertEquals(TransactionKind.Expense, keep.draft.kind)
        assertEquals("P2P", keep.draft.merchantName)
        assertEquals("p2p_refund", keep.draft.bybitSide)
    }

    @Test
    fun `p2p outbound sell is expense`() {
        val keep = RecordClassifier.classifyFunding(
            p2p("s1", "O", "25.5", "fundingAccountRecordP2PSell", "P2P Sell")
        ) as ClassifyResult.Keep
        assertEquals(TransactionSource.P2P, keep.draft.source)
        assertEquals(TransactionKind.Expense, keep.draft.kind)
        assertEquals("25.50", keep.draft.paidAmount)
        assertEquals("USD", keep.draft.paidCurrency)
    }

    @Test
    fun `p2p inbound buy is income`() {
        val keep = RecordClassifier.classifyFunding(
            p2p("b1", "I", "10", "fundingAccountRecordP2PBuy", "P2P Buy")
        ) as ClassifyResult.Keep
        assertEquals(TransactionSource.P2P, keep.draft.source)
        assertEquals(TransactionKind.Income, keep.draft.kind)
    }

    @Test
    fun `p2p outbound buy cancel is income refund`() {
        val keep = RecordClassifier.classifyFunding(
            p2p("bc1", "O", "10", "fundingAccountRecordP2PCancel", "P2P cancel")
        ) as ClassifyResult.Keep
        assertEquals(TransactionSource.P2PRefund, keep.draft.source)
        assertEquals(TransactionKind.Income, keep.draft.kind)
    }

    @Test
    fun `p2p unfreeze only in busi type en is refund not top up`() {
        val keep = RecordClassifier.classifyFunding(
            BybitFundingRecord(
                id = "u1",
                currency = "USDT",
                ioDirection = "I",
                txnAmt = "40",
                createTime = 1_725_000_000_000,
                showBusiType = "fundingAccountRecordAITD",
                showBusiTypeEn = "P2P Unfreeze",
                description = "",
                descriptionEn = ""
            )
        ) as ClassifyResult.Keep
        assertEquals(TransactionSource.P2PRefund, keep.draft.source)
        assertEquals(TransactionKind.Expense, keep.draft.kind)
    }

    @Test
    fun `c2c inbound unfreeze is refund not top up`() {
        val keep = RecordClassifier.classifyFunding(
            p2p(
                "c2u",
                "I",
                "40",
                "fundingAccountRecordC2CUNFreeze",
                "C2C Unfreeze",
                busi = "fundingAccountRecordC2C",
                busiEn = "C2C Trading"
            )
        ) as ClassifyResult.Keep
        assertEquals(TransactionSource.P2PRefund, keep.draft.source)
    }

    @Test
    fun `p2p refund in description is cancel not buy`() {
        val keep = RecordClassifier.classifyFunding(
            p2p("r1", "I", "15", "fundingAccountRecordP2PRefund", "P2P Refund")
        ) as ClassifyResult.Keep
        assertEquals(TransactionSource.P2PRefund, keep.draft.source)
    }

    @Test
    fun `bybitpay c2c collection stays top up`() {
        val keep = RecordClassifier.classifyFunding(
            BybitFundingRecord(
                id = "c2c1",
                currency = "USDT",
                ioDirection = "I",
                txnAmt = "5.0000",
                createTime = 1_725_000_000_000,
                showBusiType = "fundingAccountRecordBybitpay",
                showBusiTypeEn = "Bybit Pay",
                description = "fundingAccountRecordFiatBybitpayC2CSettlementDeposit",
                descriptionEn = "Bybit Pay transfer (collection) "
            )
        ) as ClassifyResult.Keep
        assertEquals(TransactionSource.TopUp, keep.draft.source)
    }

    @Test
    fun `funding earn and card inbound are not income`() {
        val earn = BybitFundingRecord(
            id = "e1",
            currency = "USDT",
            ioDirection = "I",
            txnAmt = "0.01",
            createTime = 1L,
            showBusiType = "fundingAccountRecordEarn",
            showBusiTypeEn = "Earn",
            description = "fundingAccountRecordFlexSavingInterestDistribution",
            descriptionEn = "Easy Earn | Flexible Interest Distribution"
        )
        val cardIn = earn.copy(
            id = "c1",
            showBusiType = "fundingAccountRecordCard",
            showBusiTypeEn = "Bybit Card",
            description = "cardRefund",
            descriptionEn = "Bybit Card Refund"
        )
        val outgoing = earn.copy(id = "o1", ioDirection = "O", showBusiTypeEn = "Deposit")
        assertTrue(RecordClassifier.classifyFunding(earn) is ClassifyResult.Skip)
        assertTrue(RecordClassifier.classifyFunding(cardIn) is ClassifyResult.Skip)
        assertTrue(RecordClassifier.classifyFunding(outgoing) is ClassifyResult.Skip)
    }

    @Test
    fun `flexible usdt yield is stored as earn`() {
        val keep = RecordClassifier.classifyEarnYield(
            BybitEarnYieldRecord("42", "USDT", "1.50", "Success", 1_700_000_000_000L)
        ) as ClassifyResult.Keep
        assertEquals("ey_42", keep.draft.txnId)
        assertEquals(TransactionSource.Earn, keep.draft.source)
        assertEquals(TransactionKind.Income, keep.draft.kind)
        assertEquals("USD", keep.draft.paidCurrency)
        assertEquals("Earn", keep.draft.merchantName)
    }

    @Test
    fun `flexible usdt yield keeps sub-cent daily amount`() {
        val keep = RecordClassifier.classifyEarnYield(
            BybitEarnYieldRecord("9", "USDT", "0.0042", "Success", 1_700_000_000_000L)
        ) as ClassifyResult.Keep
        assertEquals("0.0042", keep.draft.paidAmount)
        assertEquals(TransactionSource.Earn, keep.draft.source)
    }

    @Test
    fun `earn yield skips btc fail zero and blank id`() {
        val ok = BybitEarnYieldRecord("1", "USDT", "1.00", "Success", 1L)
        assertTrue(RecordClassifier.classifyEarnYield(ok.copy(coin = "BTC")) is ClassifyResult.Skip)
        assertTrue(RecordClassifier.classifyEarnYield(ok.copy(status = "Fail")) is ClassifyResult.Skip)
        assertTrue(RecordClassifier.classifyEarnYield(ok.copy(amount = "0")) is ClassifyResult.Skip)
        assertTrue(RecordClassifier.classifyEarnYield(ok.copy(id = " ")) is ClassifyResult.Skip)
    }

    @Test
    fun `cashback earn is stored as points`() {
        val keep = RecordClassifier.classifyPoints(
            BybitPointRecord(
                bizId = "B1",
                transactionId = "T1",
                point = 100,
                side = "1",
                type = "CASHBACK",
                createTime = 1_725_000_000_000
            )
        ) as ClassifyResult.Keep
        assertEquals("cb_B1", keep.draft.txnId)
        assertEquals(TransactionSource.Cashback, keep.draft.source)
        assertEquals("100", keep.draft.paidAmount)
        assertEquals("PTS", keep.draft.paidCurrency)
        assertEquals("T1", keep.draft.orderNo)
    }

    @Test
    fun `cashback stores official join ids on orderNo`() {
        // official-v5/bybit-card/point/records.mdx: transactionId, outOrderId, bizTxnId
        val keep = RecordClassifier.classifyPoints(
            BybitPointRecord(
                bizId = "B1",
                transactionId = "TXN1",
                point = 100,
                side = "1",
                type = "CASHBACK",
                createTime = 1L,
                outOrderId = "ORD1",
                bizTxnId = "BIZ1"
            )
        ) as ClassifyResult.Keep
        assertEquals("TXN1\nORD1\nBIZ1", keep.draft.orderNo)
    }

    @Test
    fun `numeric type 1 earn is cashback`() {
        val keep = RecordClassifier.classifyPoints(
            BybitPointRecord(
                bizId = "B1",
                transactionId = "T1",
                point = 17,
                side = "1",
                type = "1",
                createTime = 1_788_656_374_000
            )
        ) as ClassifyResult.Keep
        assertEquals("cb_B1", keep.draft.txnId)
        assertEquals(TransactionSource.Cashback, keep.draft.source)
        assertEquals("17", keep.draft.paidAmount)
    }

    @Test
    fun `cashback redeem is skipped`() {
        assertTrue(
            RecordClassifier.classifyPoints(
                BybitPointRecord("B2", "T2", 50, "2", "CASHBACK", 1L)
            ) is ClassifyResult.Skip
        )
        assertTrue(
            RecordClassifier.classifyPoints(
                BybitPointRecord("B3", "T3", 292, "2", "5", 1L)
            ) is ClassifyResult.Skip
        )
    }

    @Test
    fun `refund reversal sides are not listed`() {
        assertTrue(RecordClassifier.classify(QueryType.Refund, record(side = "8")) is ClassifyResult.Skip)
        assertTrue(RecordClassifier.classify(QueryType.Refund, record(side = "9")) is ClassifyResult.Skip)
        assertTrue(RecordClassifier.classify(QueryType.Refund, record(side = "11")) is ClassifyResult.Skip)
    }

    @Test
    fun `missing txnId falls back to orderNo`() {
        val keep = RecordClassifier.classify(
            QueryType.Auth,
            record(side = "3", txnId = "", orderNo = "ORD9")
        ) as ClassifyResult.Keep
        assertEquals("ORD9", keep.draft.txnId)
    }

    @Test
    fun `missing txnId and orderNo is skipped`() {
        assertTrue(
            RecordClassifier.classify(
                QueryType.Auth,
                record(side = "3", txnId = "", orderNo = "")
            ) is ClassifyResult.Skip
        )
    }

    @Test
    fun `status minus one is pending`() {
        val keep = RecordClassifier.classify(
            QueryType.Auth,
            record(side = "3", status = "-1", tradeStatus = "0")
        ) as ClassifyResult.Keep
        assertEquals(TransactionStatus.Pending, keep.draft.status)
    }

    @Test
    fun `zero amount card check is not kept`() {
        val zeroPaid = RecordClassifier.classify(
            QueryType.Auth,
            record(side = "1", paidAmount = "0", paidCurrency = "USDT", merchName = "Google")
        )
        val zeroBasic = RecordClassifier.classify(
            QueryType.Financial,
            record(side = "3", paidAmount = "0.00", paidCurrency = "USDT", merchName = "Apple").copy(
                basicAmount = "0.00",
                basicCurrency = "USD",
                transactionAmount = "0",
                transactionCurrency = "USD"
            )
        )
        val billed = RecordClassifier.classify(
            QueryType.Auth,
            record(side = "1", paidAmount = "0", paidCurrency = "THB", merchName = "Shop").copy(
                basicAmount = "1.00",
                basicCurrency = "USD"
            )
        ) as ClassifyResult.Keep
        assertTrue(zeroPaid is ClassifyResult.Skip)
        assertTrue(zeroBasic is ClassifyResult.Skip)
        assertEquals("1.00", billed.draft.paidAmount)
    }

    @Test
    fun `stores usd billing amount not merchant thb`() {
        val keep = RecordClassifier.classify(
            QueryType.Auth,
            record(side = "3").copy(
                paidAmount = "28.000000000000000000",
                paidCurrency = "THB",
                basicAmount = "0.86",
                basicCurrency = "USD"
            )
        ) as ClassifyResult.Keep
        assertEquals("0.86", keep.draft.paidAmount)
        assertEquals("USD", keep.draft.paidCurrency)
    }

    private fun qrFreeze(id: String, amount: String): BybitFundingRecord {
        return BybitFundingRecord(
            id = id,
            currency = "USDT",
            ioDirection = "O",
            txnAmt = amount,
            createTime = 1_725_000_000_000,
            showBusiType = "fundingAccountRecordBybitpay",
            showBusiTypeEn = "Bybit Pay",
            description = "fundingAccountRecordBybitpayFiatFreeze",
            descriptionEn = "Purchase"
        )
    }

    private fun qrUnfreeze(id: String, amount: String): BybitFundingRecord {
        return qrFreeze(id, amount).copy(
            ioDirection = "I",
            description = "fundingAccountRecordBybitpayFiatUNFreeze",
            descriptionEn = "Canceled Purchase"
        )
    }

    private fun p2p(
        id: String,
        direction: String,
        amount: String,
        description: String,
        descriptionEn: String,
        busi: String = "fundingAccountRecordP2P",
        busiEn: String = "P2P Trading"
    ): BybitFundingRecord {
        return BybitFundingRecord(
            id = id,
            currency = "USDT",
            ioDirection = direction,
            txnAmt = amount,
            createTime = 1_725_000_000_000,
            showBusiType = busi,
            showBusiTypeEn = busiEn,
            description = description,
            descriptionEn = descriptionEn
        )
    }

}
