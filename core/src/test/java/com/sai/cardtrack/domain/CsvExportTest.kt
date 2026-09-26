package com.sai.cardtrack.domain

import com.sai.cardtrack.data.Credentials
import com.sai.cardtrack.ui.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CsvExportTest {
    private val at = Instant.parse("2026-03-04T15:16:17Z").toEpochMilli()

    private val purchase = row(
        txnId = "txn-buy",
        orderNo = "ord-1",
        amount = "10.00",
        currency = "USDT",
        merchant = "Store, Inc",
        categoryId = "groceries",
        source = TransactionSource.Purchase,
        status = TransactionStatus.Success,
        fees = FeeBreakdown(
            totalFees = "1.50",
            transactionAmount = "10.00",
            transactionCurrency = "USD"
        ),
        mcc = "5411"
    )

    private val declined = row(
        txnId = "txn-deny",
        orderNo = "ord-2",
        amount = "5.00",
        merchant = "Denied Shop",
        source = TransactionSource.Declined,
        status = TransactionStatus.Declined,
        reason = "insufficient"
    )

    private val cashback = row(
        txnId = "txn-cb",
        orderNo = "ord-3",
        amount = "0.10",
        merchant = "Cashback",
        source = TransactionSource.Cashback,
        kind = TransactionKind.Income
    )

    private val earn = row(
        txnId = "txn-ey",
        orderNo = "ord-ey",
        amount = "1.50",
        merchant = "Earn",
        source = TransactionSource.Earn,
        kind = TransactionKind.Income
    )

    @Test
    fun `table quotes merchant comma and uses russian category`() {
        val csv = CsvExport.table(listOf(purchase, declined, cashback))
        val lines = csv.lines().filter { it.isNotEmpty() }
        assertEquals(
            "txnId,orderNo,date_iso,source,status,merchant,category,paidAmount,paidCurrency,totalFees,transactionAmount,transactionCurrency,mccCode,declinedReason",
            lines[0]
        )
        assertEquals(
            "txn-buy,ord-1,2026-03-04T15:16:17Z,Purchase,Success,\"Store, Inc\",Еда · Продукты,10.00,USDT,1.50,10.00,USD,5411,",
            lines[1]
        )
        assertEquals(
            "txn-deny,ord-2,2026-03-04T15:16:17Z,Declined,Declined,Denied Shop,,5.00,USD,,,,5411,insufficient",
            lines[2]
        )
        assertEquals(4, lines.size)
    }

    @Test
    fun `koinly keeps purchase with fee and omits declined and cashback`() {
        val csv = CsvExport.koinly(listOf(purchase, declined, cashback, earn))
        val lines = csv.lines().filter { it.isNotEmpty() }
        assertEquals(
            "Date,Sent Amount,Sent Currency,Received Amount,Received Currency,Fee Amount,Fee Currency,Net Worth Amount,Net Worth Currency,Label,Description,TxHash",
            lines[0]
        )
        assertEquals(
            "2026-03-04 15:16:17,8.50,USD,,,1.50,USD,,,cost,\"Store, Inc\",txn-buy",
            lines[1]
        )
        assertEquals(2, lines.size)
        assertFalse(csv.contains("txn-deny"))
        assertFalse(csv.contains("txn-cb"))
        assertFalse(csv.contains("txn-ey"))
        assertTrue(csv.contains(",cost,"))
    }

    @Test
    fun `table uses locale for category labels`() {
        val ru = CsvExport.table(listOf(purchase), AppLocale.Ru)
        val en = CsvExport.table(listOf(purchase), AppLocale.En)
        assertTrue(ru.contains("Еда · Продукты"))
        assertTrue(en.contains("Food · Groceries"))
    }

    @Test
    fun `table and koinly omit planted credential secrets`() {
        val planted = Credentials(apiKey = "apiKey", apiSecret = "apiSecret")
        val table = CsvExport.table(listOf(purchase), AppLocale.Ru)
        val koinly = CsvExport.koinly(listOf(purchase))
        val header = table.lines().first()
        assertEquals(
            "txnId,orderNo,date_iso,source,status,merchant,category,paidAmount,paidCurrency,totalFees,transactionAmount,transactionCurrency,mccCode,declinedReason",
            header
        )
        listOf(table, koinly).forEach { csv ->
            assertFalse(csv.contains(planted.apiKey))
            assertFalse(csv.contains(planted.apiSecret))
            assertFalse(csv.contains("X-BAPI"))
        }
    }

    @Test
    fun `koinly sent amount is paidAmount minus official sample fee`() {
        val official = purchase.copy(
            paidAmount = "101.50",
            fees = FeeBreakdown(
                totalFees = "1.50",
                transactionAmount = "100.00",
                transactionCurrency = "USD"
            )
        )
        val csv = CsvExport.koinly(listOf(official))
        val line = csv.lines().filter { it.isNotEmpty() }[1]
        assertEquals(
            "2026-03-04 15:16:17,100.00,USD,,,1.50,USD,,,cost,\"Store, Inc\",txn-buy",
            line
        )
    }

    @Test
    fun `table and koinly omit p2p and refund when setting off`() {
        val buy = row(
            txnId = "txn-p2p-buy",
            orderNo = "ord-p2p-b",
            amount = "10.00",
            currency = "USDT",
            merchant = "P2P",
            source = TransactionSource.P2P,
            kind = TransactionKind.Income
        )
        val cancel = row(
            txnId = "txn-p2p-cancel",
            orderNo = "ord-p2p-c",
            amount = "10.00",
            merchant = "P2P",
            source = TransactionSource.P2PRefund
        )
        val table = CsvExport.table(listOf(purchase, buy, cancel), includeP2p = false)
        val koinly = CsvExport.koinly(listOf(purchase, buy, cancel), includeP2p = false)
        assertTrue(table.contains("txn-buy"))
        assertFalse(table.contains("txn-p2p-buy"))
        assertFalse(table.contains("txn-p2p-cancel"))
        assertTrue(koinly.contains("txn-buy"))
        assertFalse(koinly.contains("txn-p2p-buy"))
        assertFalse(koinly.contains("txn-p2p-cancel"))
    }

    @Test
    fun `table and koinly include p2p only when setting on`() {
        val buy = row(
            txnId = "txn-p2p-buy",
            orderNo = "ord-p2p-b",
            amount = "10.00",
            currency = "USDT",
            merchant = "P2P",
            source = TransactionSource.P2P,
            kind = TransactionKind.Income
        )
        val cancel = row(
            txnId = "txn-p2p-cancel",
            orderNo = "ord-p2p-c",
            amount = "10.00",
            merchant = "P2P",
            source = TransactionSource.P2PRefund
        )
        val table = CsvExport.table(listOf(purchase, buy, cancel), includeP2p = true)
        val koinly = CsvExport.koinly(listOf(purchase, buy, cancel), includeP2p = true)
        assertTrue(table.contains("txn-p2p-buy"))
        assertFalse(table.contains("txn-p2p-cancel"))
        assertTrue(koinly.contains("txn-p2p-buy"))
        assertFalse(koinly.contains("txn-p2p-cancel"))
        assertTrue(koinly.contains(",P2P,txn-p2p-buy"))
    }

    @Test
    fun `koinly maps refund and top-up as received`() {
        val refund = row(
            txnId = "txn-ref",
            orderNo = "ord-4",
            amount = "3.00",
            currency = "USDT",
            merchant = "Store",
            source = TransactionSource.Refund,
            kind = TransactionKind.Income
        )
        val topUp = row(
            txnId = "txn-top",
            orderNo = "ord-5",
            amount = "20.00",
            currency = "USDC",
            merchant = "Top-up",
            source = TransactionSource.TopUp,
            kind = TransactionKind.Income
        )
        val csv = CsvExport.koinly(listOf(refund, topUp))
        val lines = csv.lines().filter { it.isNotEmpty() }
        assertEquals(
            "2026-03-04 15:16:17,,,3.00,USD,,,,,,Refund,txn-ref",
            lines[1]
        )
        assertEquals(
            "2026-03-04 15:16:17,,,20.00,USD,,,,,,Top-up,txn-top",
            lines[2]
        )
    }

    private fun row(
        txnId: String,
        orderNo: String?,
        amount: String,
        currency: String = "USD",
        merchant: String,
        categoryId: String? = null,
        source: TransactionSource,
        status: TransactionStatus = TransactionStatus.Success,
        kind: TransactionKind = TransactionKind.Expense,
        fees: FeeBreakdown = FeeBreakdown(),
        mcc: String = "5411",
        reason: String = ""
    ) = Transaction(
        txnId = txnId,
        orderNo = orderNo,
        kind = kind,
        paidAmount = amount,
        paidCurrency = currency,
        merchantName = merchant,
        txnCreate = at,
        status = status,
        bybitSide = "3",
        categoryId = categoryId,
        syncedAt = 0L,
        source = source,
        declinedReason = reason,
        mccCode = mcc,
        fees = fees
    )
}
