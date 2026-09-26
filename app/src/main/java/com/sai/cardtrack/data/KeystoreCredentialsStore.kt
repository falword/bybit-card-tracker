package com.sai.cardtrack.data

import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeystoreCredentialsStore(
    private val prefs: SharedPreferences,
    private val secretKey: () -> SecretKey,
    private val esp: SharedPreferences? = null
) : CredentialsStore {
    @Volatile
    private var cached: Credentials? = null

    override suspend fun get(): Credentials? {
        cached?.let { return it }
        val vault = readVaultSecrets()
        if (vault != null) {
            remember(vault)
            return vault
        }
        migrateFromEsp()
        val migrated = readVaultSecrets()
        if (migrated != null) remember(migrated)
        return migrated
    }

    override suspend fun apiKeyHint(): String? {
        cached?.apiKey?.takeLast(4)?.takeIf { it.isNotEmpty() }?.let { return it }
        return prefs.getString(KEY_API_HINT, null)?.takeIf { it.isNotEmpty() }
    }

    override fun evictCachedSecrets() {
        cached = null
    }

    override suspend fun save(credentials: Credentials) {
        val key = secretKey()
        prefs.edit {
            putString(KEY_API, VaultCrypto.encode(VaultCrypto.encrypt(key, credentials.apiKey.toByteArray())))
            putString(
                KEY_SECRET,
                VaultCrypto.encode(VaultCrypto.encrypt(key, credentials.apiSecret.toByteArray()))
            )
            persistHint(this, credentials.apiKey)
        }
        cached = credentials
    }

    override suspend fun lastSyncAt(): Long? {
        if (!prefs.contains(KEY_SYNC)) return null
        return prefs.getLong(KEY_SYNC, 0L)
    }

    override suspend fun setLastSyncAt(epochMs: Long) {
        prefs.edit { putLong(KEY_SYNC, epochMs) }
    }

    override suspend fun fundingSyncedThrough(): Long? {
        if (!prefs.contains(KEY_FUNDING)) return null
        return prefs.getLong(KEY_FUNDING, 0L)
    }

    override suspend fun setFundingSyncedThrough(epochMs: Long) {
        prefs.edit { putLong(KEY_FUNDING, epochMs) }
    }

    override suspend fun cardSyncedThrough(): Long? {
        if (!prefs.contains(KEY_CARD)) return null
        return prefs.getLong(KEY_CARD, 0L)
    }

    override suspend fun setCardSyncedThrough(epochMs: Long) {
        prefs.edit { putLong(KEY_CARD, epochMs) }
    }

    override suspend fun financialSyncedThrough(): Long? {
        if (!prefs.contains(KEY_FINANCIAL)) return null
        return prefs.getLong(KEY_FINANCIAL, 0L)
    }

    override suspend fun setFinancialSyncedThrough(epochMs: Long) {
        prefs.edit { putLong(KEY_FINANCIAL, epochMs) }
    }

    override suspend fun cardHistoryGeneration(): Int {
        return prefs.getInt(KEY_CARD_HISTORY_GEN, 0)
    }

    override suspend fun setCardHistoryGeneration(value: Int) {
        prefs.edit { putInt(KEY_CARD_HISTORY_GEN, value) }
    }

    override suspend fun fundingHistoryGeneration(): Int {
        return prefs.getInt(KEY_FUNDING_HISTORY_GEN, 0)
    }

    override suspend fun setFundingHistoryGeneration(value: Int) {
        prefs.edit { putInt(KEY_FUNDING_HISTORY_GEN, value) }
    }

    override suspend fun p2pAccountingEnabled(): Boolean {
        return prefs.getBoolean(KEY_P2P, false)
    }

    override suspend fun setP2pAccountingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_P2P, enabled) }
    }

    override suspend fun earnSyncedThrough(): Long? {
        if (!prefs.contains(KEY_EARN)) return null
        return prefs.getLong(KEY_EARN, 0L)
    }

    override suspend fun setEarnSyncedThrough(epochMs: Long) {
        prefs.edit { putLong(KEY_EARN, epochMs) }
    }

    override suspend fun clear() {
        cached = null
        prefs.edit { clear() }
    }

    private fun remember(credentials: Credentials) {
        cached = credentials
        if (prefs.getString(KEY_API_HINT, null).isNullOrEmpty()) {
            prefs.edit { persistHint(this, credentials.apiKey) }
        }
    }

    private fun persistHint(editor: SharedPreferences.Editor, apiKey: String) {
        val hint = apiKey.takeLast(4).takeIf { it.isNotEmpty() }
        if (hint == null) editor.remove(KEY_API_HINT) else editor.putString(KEY_API_HINT, hint)
    }

    private fun readVaultSecrets(): Credentials? {
        val keyText = prefs.getString(KEY_API, null) ?: return null
        val secretText = prefs.getString(KEY_SECRET, null) ?: return null
        if (keyText.isBlank() || secretText.isBlank()) return null
        val key = secretKey()
        val apiKey = String(VaultCrypto.decrypt(key, VaultCrypto.decode(keyText)))
        val apiSecret = String(VaultCrypto.decrypt(key, VaultCrypto.decode(secretText)))
        if (apiKey.isBlank() || apiSecret.isBlank()) return null
        return Credentials(apiKey, apiSecret)
    }

    private suspend fun migrateFromEsp() {
        val source = esp ?: return
        val apiKey = source.getString(KEY_API, null)
        val apiSecret = source.getString(KEY_SECRET, null)
        if (apiKey.isNullOrBlank() || apiSecret.isNullOrBlank()) return
        save(Credentials(apiKey, apiSecret))
        if (!prefs.contains(KEY_SYNC) && source.contains(KEY_SYNC)) {
            prefs.edit { putLong(KEY_SYNC, source.getLong(KEY_SYNC, 0L)) }
        }
        if (!prefs.contains(KEY_FUNDING) && source.contains(KEY_FUNDING)) {
            prefs.edit { putLong(KEY_FUNDING, source.getLong(KEY_FUNDING, 0L)) }
        }
        source.edit {
            remove(KEY_API)
            remove(KEY_SECRET)
        }
    }

    companion object {
        const val PREFS_NAME: String = "cardtrack_vault"
        const val KEYSTORE_ALIAS: String = "cardtrack_creds_aes"
        const val KEY_API: String = "api_key"
        const val KEY_SECRET: String = "api_secret"
        const val KEY_SYNC: String = "last_sync_at"
        const val KEY_FUNDING: String = "funding_synced_through"
        const val KEY_CARD: String = "card_synced_through"
        const val KEY_FINANCIAL: String = "financial_synced_through"
        const val KEY_CARD_HISTORY_GEN: String = "card_history_generation"
        const val KEY_FUNDING_HISTORY_GEN: String = "funding_history_generation"
        const val KEY_P2P: String = "p2p_accounting"
        const val KEY_EARN: String = "earn_synced_through"
        const val KEY_API_HINT: String = "api_key_hint"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AUTH_VALIDITY_SECONDS = 30

        fun openOrCreateAesKey(): SecretKey {
            loadExisting()?.let { return it }
            return generateAesKey(userAuth = true) // StrongBox→TEE fallback stays; userAuth=false is gone
        }

        private fun generateAesKey(userAuth: Boolean): SecretKey {
            return try {
                generateAesKey(userAuth = userAuth, strongBox = true)
            } catch (_: Exception) {
                deleteAlias()
                generateAesKey(userAuth = userAuth, strongBox = false)
            }
        }

        private fun generateAesKey(userAuth: Boolean, strongBox: Boolean): SecretKey {
            val purposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            val builder = KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, purposes)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            if (userAuth) {
                builder.setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(
                        AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        builder.setUnlockedDeviceRequired(true)
                    } catch (_: Exception) {
                    }
                }
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(builder.build())
            return generator.generateKey()
        }

        private fun loadExisting(): SecretKey? {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) return null
            return keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        }

        private fun deleteAlias() {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }
        }
    }
}
