package com.sai.cardtrack.data

class InMemoryCredentialsStore : CredentialsStore {
    private var credentials: Credentials? = null
    private var syncAt: Long? = null
    private var fundingThrough: Long? = null
    private var cardThrough: Long? = null
    private var financialThrough: Long? = null
    private var cardHistoryGen: Int = 0
    private var fundingHistoryGen: Int = 0
    private var p2pOn = false
    private var earnThrough: Long? = null

    override suspend fun get(): Credentials? = credentials

    override suspend fun save(credentials: Credentials) {
        this.credentials = credentials
    }

    override suspend fun lastSyncAt(): Long? = syncAt

    override suspend fun setLastSyncAt(epochMs: Long) {
        syncAt = epochMs
    }

    override suspend fun fundingSyncedThrough(): Long? = fundingThrough

    override suspend fun setFundingSyncedThrough(epochMs: Long) {
        fundingThrough = epochMs
    }

    override suspend fun cardSyncedThrough(): Long? = cardThrough

    override suspend fun setCardSyncedThrough(epochMs: Long) {
        cardThrough = epochMs
    }

    override suspend fun financialSyncedThrough(): Long? = financialThrough

    override suspend fun setFinancialSyncedThrough(epochMs: Long) {
        financialThrough = epochMs
    }

    override suspend fun cardHistoryGeneration(): Int = cardHistoryGen

    override suspend fun setCardHistoryGeneration(value: Int) {
        cardHistoryGen = value
    }

    override suspend fun fundingHistoryGeneration(): Int = fundingHistoryGen

    override suspend fun setFundingHistoryGeneration(value: Int) {
        fundingHistoryGen = value
    }

    override suspend fun p2pAccountingEnabled(): Boolean = p2pOn

    override suspend fun setP2pAccountingEnabled(enabled: Boolean) {
        p2pOn = enabled
    }

    override suspend fun earnSyncedThrough(): Long? = earnThrough

    override suspend fun setEarnSyncedThrough(epochMs: Long) {
        earnThrough = epochMs
    }

    override suspend fun clear() {
        credentials = null
        syncAt = null
        fundingThrough = null
        cardThrough = null
        financialThrough = null
        cardHistoryGen = 0
        fundingHistoryGen = 0
        p2pOn = false
        earnThrough = null
    }
}
