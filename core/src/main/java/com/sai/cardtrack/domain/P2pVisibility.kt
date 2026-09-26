package com.sai.cardtrack.domain

object P2pVisibility {
    fun keep(row: Transaction, includeP2p: Boolean): Boolean {
        return when (row.source) {
            TransactionSource.P2P -> includeP2p
            TransactionSource.P2PRefund -> false
            else -> true
        }
    }

    fun filter(rows: List<Transaction>, includeP2p: Boolean): List<Transaction> {
        return rows.filter { keep(it, includeP2p) }
    }
}
