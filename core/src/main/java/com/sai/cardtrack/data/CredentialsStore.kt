package com.sai.cardtrack.data

interface CredentialsStore {
    suspend fun get(): Credentials?
    suspend fun save(credentials: Credentials)
    suspend fun lastSyncAt(): Long?
    suspend fun setLastSyncAt(epochMs: Long)
    suspend fun fundingSyncedThrough(): Long?
    suspend fun setFundingSyncedThrough(epochMs: Long)
    suspend fun cardSyncedThrough(): Long? = null
    suspend fun setCardSyncedThrough(epochMs: Long) {}
    suspend fun financialSyncedThrough(): Long? = null
    suspend fun setFinancialSyncedThrough(epochMs: Long) {}
    suspend fun cardHistoryGeneration(): Int = 0
    suspend fun setCardHistoryGeneration(value: Int) {}
    suspend fun fundingHistoryGeneration(): Int = 0
    suspend fun setFundingHistoryGeneration(value: Int) {}
    suspend fun p2pAccountingEnabled(): Boolean = false
    suspend fun setP2pAccountingEnabled(enabled: Boolean) {}
    suspend fun earnSyncedThrough(): Long? = null
    suspend fun setEarnSyncedThrough(epochMs: Long) {}
    suspend fun clear()

    /** Last four of the API key. Must not decrypt the vault. */
    suspend fun apiKeyHint(): String? = get()?.apiKey?.takeLast(4)?.takeIf { it.isNotEmpty() }

    /** Drop in-memory secrets after the app lock screen is shown. */
    fun evictCachedSecrets() {}
}
