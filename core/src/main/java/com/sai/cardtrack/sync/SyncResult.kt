package com.sai.cardtrack.sync

enum class SyncError {
    Network,
    Auth,
    RateLimit,
    Unknown,
    Wallet
}

sealed class SyncResult {
    data object Success : SyncResult()
    data class Failed(
        val reason: SyncError,
        val retMsg: String? = null,
        val retCode: Int? = null
    ) : SyncResult()
    data class Partial(
        val reason: SyncError,
        val retMsg: String? = null,
        val retCode: Int? = null
    ) : SyncResult()
}
