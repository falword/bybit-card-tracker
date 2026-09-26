package com.sai.cardtrack.domain

object RecordClassifier {

    fun classify(queryType: QueryType, record: BybitAssetRecord): ClassifyResult {
        val id = resolveId(record) ?: return ClassifyResult.Skip
        if (isHiddenStatus(record)) return ClassifyResult.Skip
        val sideSource = sourceFor(record.side) ?: return ClassifyResult.Skip
        val declined = record.tradeStatus == "2" || record.status == "2"
        val source = if (declined) TransactionSource.Declined else sideSource
        val status = if (declined) {
            TransactionStatus.Declined
        } else {
            when {
                record.status == "1" ||
                    (record.status.isBlank() && record.tradeStatus == "1") -> TransactionStatus.Success
                record.status == "0" ||
                    record.status == "-1" ||
                    (record.status.isBlank() && record.tradeStatus == "0") -> TransactionStatus.Pending
                else -> return ClassifyResult.Skip
            }
        }
        val money = CardMoney.fromRecord(record)
        if (CardMoney.isExactZero(money.amount)) return ClassifyResult.Skip
        val suggested = if (source == TransactionSource.Purchase || source == TransactionSource.Declined) {
            MccCategory.suggest(record.mccCode)
        } else {
            null
        }
        return ClassifyResult.Keep(
            TransactionDraft(
                txnId = id,
                orderNo = record.orderNo?.takeIf { it.isNotBlank() },
                kind = TransactionKind.Expense,
                paidAmount = money.amount,
                paidCurrency = money.currency,
                merchantName = record.merchName,
                txnCreate = record.txnCreate,
                status = status,
                bybitSide = record.side,
                source = source,
                declinedReason = record.declinedReason,
                mccCode = record.mccCode,
                merchCategoryDesc = record.merchCategoryDesc,
                categoryId = suggested,
                categoryOrigin = if (suggested != null) CategoryOrigin.Mcc else CategoryOrigin.None,
                fees = FeeBreakdown(
                    totalFees = record.totalFees,
                    foreignTransactionFee = record.foreignTransactionFee,
                    withdrawalFee = record.withdrawalFee,
                    fxPad = record.fxPad,
                    totalTax = record.totalTax,
                    billAmount = record.billAmount,
                    transactionAmount = record.transactionAmount,
                    transactionCurrency = record.transactionCurrency
                )
            )
        )
    }

    fun classifyTransfer(transfer: BybitTransfer): ClassifyResult {
        if (!transfer.status.equals("SUCCESS", ignoreCase = true)) return ClassifyResult.Skip
        val dest = transfer.toAccountType.trim().uppercase().replace(" ", "")
        if (dest != "FUND" && dest != "FUNDING" && dest != "FUNDINGACCOUNT") {
            return ClassifyResult.Skip
        }
        val id = transfer.transferId.trim().takeIf { it.isNotEmpty() } ?: return ClassifyResult.Skip
        val coin = transfer.coin.trim().uppercase()
        val currency = if (coin == "USDT" || coin == "USDC" || coin == "USD") "USD" else transfer.coin
        return ClassifyResult.Keep(
            TransactionDraft(
                txnId = "tu_$id",
                orderNo = id,
                kind = TransactionKind.Income,
                paidAmount = CardMoney.format(transfer.amount),
                paidCurrency = currency,
                merchantName = ExpenseCategories.TOPUP_LABEL,
                txnCreate = transfer.timestamp,
                status = TransactionStatus.Success,
                bybitSide = "topup",
                source = TransactionSource.TopUp
            )
        )
    }

    fun classifyFunding(record: BybitFundingRecord): ClassifyResult {
        classifyQr(record)?.let { return it }
        classifyP2p(record)?.let { return it }
        if (!record.ioDirection.equals("I", ignoreCase = true)) return ClassifyResult.Skip
        val haystack = listOf(
            record.showBusiType,
            record.showBusiTypeEn,
            record.description,
            record.descriptionEn
        ).joinToString(" ").lowercase()
        if (FUNDING_SKIP_TOKENS.any { haystack.contains(it) }) return ClassifyResult.Skip
        val amount = CardMoney.format(record.txnAmt)
        if (amount.toBigDecimalOrNull()?.signum() != 1) return ClassifyResult.Skip
        val rawId = record.id.trim().ifEmpty { return ClassifyResult.Skip }
        return ClassifyResult.Keep(
            fundingDraft(
                txnId = "fh_$rawId",
                rawId = rawId,
                kind = TransactionKind.Income,
                amount = amount,
                currency = fundingCurrency(record.currency),
                merchantName = ExpenseCategories.TOPUP_LABEL,
                txnCreate = record.createTime,
                bybitSide = "topup",
                source = TransactionSource.TopUp
            )
        )
    }

    private fun classifyQr(record: BybitFundingRecord): ClassifyResult? {
        if (!record.showBusiType.equals(QR_BUSI_TYPE, ignoreCase = true)) return null
        val isPurchase = record.ioDirection.equals("O", ignoreCase = true) &&
            record.description.equals(QR_FREEZE, ignoreCase = true)
        val isCancel = record.ioDirection.equals("I", ignoreCase = true) &&
            record.description.equals(QR_UNFREEZE, ignoreCase = true)
        if (!isPurchase && !isCancel) return null
        val amount = CardMoney.format(record.txnAmt)
        if (amount.toBigDecimalOrNull()?.signum() != 1) return ClassifyResult.Skip
        val rawId = record.id.trim().ifEmpty { return ClassifyResult.Skip }
        val prefix = if (isPurchase) "qp_" else "qr_"
        return ClassifyResult.Keep(
            fundingDraft(
                txnId = prefix + rawId,
                rawId = rawId,
                kind = TransactionKind.Expense,
                amount = amount,
                currency = fundingCurrency(record.currency),
                merchantName = ExpenseCategories.QR_LABEL,
                txnCreate = record.createTime,
                bybitSide = if (isPurchase) "qr" else "qr_refund",
                source = if (isPurchase) TransactionSource.Purchase else TransactionSource.Refund
            )
        )
    }

    private fun classifyP2p(record: BybitFundingRecord): ClassifyResult? {
        if (!isP2pFamily(record)) return null
        val amount = CardMoney.format(record.txnAmt)
        if (amount.toBigDecimalOrNull()?.signum() != 1) return ClassifyResult.Skip
        val rawId = record.id.trim().ifEmpty { return ClassifyResult.Skip }
        val cancel = isP2pCancel(record)
        val inbound = record.ioDirection.equals("I", ignoreCase = true)
        val outbound = record.ioDirection.equals("O", ignoreCase = true)
        if (!inbound && !outbound) return ClassifyResult.Skip
        val source = if (cancel) TransactionSource.P2PRefund else TransactionSource.P2P
        val kind = when {
            cancel && inbound -> TransactionKind.Expense
            cancel && outbound -> TransactionKind.Income
            inbound -> TransactionKind.Income
            else -> TransactionKind.Expense
        }
        return ClassifyResult.Keep(
            fundingDraft(
                txnId = "fh_$rawId",
                rawId = rawId,
                kind = kind,
                amount = amount,
                currency = fundingCurrency(record.currency),
                merchantName = ExpenseCategories.P2P_LABEL,
                txnCreate = record.createTime,
                bybitSide = if (cancel) "p2p_refund" else "p2p",
                source = source
            )
        )
    }

    // official-v5/asset/fund-history.mdx has no busiType enum; live keys, same style as QR.
    private fun isP2pFamily(record: BybitFundingRecord): Boolean {
        if (record.showBusiType.equals(QR_BUSI_TYPE, ignoreCase = true)) return false
        val hay = fundingHay(record)
        return hay.contains("p2p") || hay.contains("c2c")
    }

    private fun isP2pCancel(record: BybitFundingRecord): Boolean {
        val hay = fundingHay(record)
        return P2P_CANCEL_TOKENS.any { hay.contains(it) }
    }

    private fun fundingHay(record: BybitFundingRecord): String {
        return listOf(
            record.showBusiType,
            record.showBusiTypeEn,
            record.description,
            record.descriptionEn
        ).joinToString(" ").lowercase()
    }

    private fun fundingDraft(
        txnId: String,
        rawId: String,
        kind: TransactionKind,
        amount: String,
        currency: String,
        merchantName: String,
        txnCreate: Long,
        bybitSide: String,
        source: TransactionSource
    ): TransactionDraft {
        return TransactionDraft(
            txnId = txnId,
            orderNo = rawId,
            kind = kind,
            paidAmount = amount,
            paidCurrency = currency,
            merchantName = merchantName,
            txnCreate = txnCreate,
            status = TransactionStatus.Success,
            bybitSide = bybitSide,
            source = source
        )
    }

    private fun fundingCurrency(coin: String): String {
        val upper = coin.trim().uppercase()
        return if (upper == "USDT" || upper == "USDC" || upper == "USD") "USD" else coin
    }

    fun classifyEarnYield(record: BybitEarnYieldRecord): ClassifyResult {
        if (!record.status.equals("Success", ignoreCase = true)) return ClassifyResult.Skip
        val coin = record.coin.trim().uppercase()
        if (coin != "USDT" && coin != "USDC") return ClassifyResult.Skip
        val parsed = record.amount.toBigDecimalOrNull() ?: return ClassifyResult.Skip
        if (parsed.signum() != 1) return ClassifyResult.Skip
        val amount = parsed.stripTrailingZeros().toPlainString()
        val rawId = record.id.trim().ifEmpty { return ClassifyResult.Skip }
        return ClassifyResult.Keep(
            TransactionDraft(
                txnId = "ey_$rawId",
                orderNo = rawId,
                kind = TransactionKind.Income,
                paidAmount = amount,
                paidCurrency = "USD",
                merchantName = ExpenseCategories.EARN_LABEL,
                txnCreate = record.createdAt,
                status = TransactionStatus.Success,
                bybitSide = "earn",
                source = TransactionSource.Earn
            )
        )
    }

    fun classifyPoints(record: BybitPointRecord): ClassifyResult {
        // official-v5/bybit-card/point/records.mdx: side 1 = earn, 2 = deduct.
        // Live type is numeric (1 earn, 5 deduct); the docs sample "CASHBACK" is not used.
        if (record.side != "1") return ClassifyResult.Skip
        val rawId = record.bizId?.trim().orEmpty().ifEmpty { record.transactionId?.trim().orEmpty() }
        if (rawId.isEmpty()) return ClassifyResult.Skip
        return ClassifyResult.Keep(
            TransactionDraft(
                txnId = "cb_$rawId",
                orderNo = pointJoinKeys(record),
                kind = TransactionKind.Expense,
                paidAmount = record.point.toString(),
                paidCurrency = "PTS",
                merchantName = ExpenseCategories.CASHBACK_LABEL,
                txnCreate = record.createTime,
                status = TransactionStatus.Success,
                bybitSide = "cashback",
                source = TransactionSource.Cashback
            )
        )
    }

    private fun pointJoinKeys(record: BybitPointRecord): String? {
        // official-v5/bybit-card/point/records.mdx: join via transactionId / outOrderId / bizTxnId
        val keys = listOf(record.transactionId, record.outOrderId, record.bizTxnId)
            .mapNotNull { it?.trim()?.takeIf { key -> key.isNotEmpty() } }
            .distinct()
        return keys.joinToString("\n").takeIf { it.isNotEmpty() }
    }

    private fun resolveId(record: BybitAssetRecord): String? {
        val txn = record.txnId?.trim().orEmpty()
        if (txn.isNotEmpty()) return txn
        val order = record.orderNo?.trim().orEmpty()
        return order.takeIf { it.isNotEmpty() }
    }

    private fun isHiddenStatus(record: BybitAssetRecord): Boolean {
        return record.tradeStatus == "3"
    }

    private fun sourceFor(side: String): TransactionSource? {
        return when (side) {
            "1", "3", "7", "13" -> TransactionSource.Purchase
            "4", "5", "6" -> TransactionSource.Refund
            else -> null
        }
    }

    // Live July 2026 funding keys. official-v5/asset/fund-history.mdx has no enum.
    private const val QR_BUSI_TYPE: String = "fundingAccountRecordBybitpay"
    private const val QR_FREEZE: String = "fundingAccountRecordBybitpayFiatFreeze"
    private const val QR_UNFREEZE: String = "fundingAccountRecordBybitpayFiatUNFreeze"

    private val P2P_CANCEL_TOKENS: List<String> = listOf(
        "cancel",
        "unfreeze",
        "un_freeze",
        "unlock",
        "revoke",
        "releasefail",
        "release_fail",
        "refund",
        "return",
        "thaw",
        "отмен",
        "возврат",
        "разблок"
    )

    private val FUNDING_SKIP_TOKENS: List<String> = listOf(
        "earn",
        "interest",
        "saving",
        "reward",
        "cashback",
        "stake",
        "airdrop",
        "bonus",
        "rebate",
        "card",
        "refund",
        "fee",
        "loan",
        "borrow"
    )
}
