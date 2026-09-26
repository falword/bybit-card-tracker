package com.sai.cardtrack.data.db

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.sai.cardtrack.data.EncryptedDbUnreadableException
import com.sai.cardtrack.data.LocalWipe
import com.sai.cardtrack.data.PassphraseStore
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Local Room file `cardtrack.db` — not a Bybit official-v5 `.mdx` path (no HTTP window).
 * SQLCipher `net.zetetic:sqlcipher-android:4.6.1` via [SupportOpenHelperFactory].
 */
object EncryptedDb {
    const val DATABASE_NAME: String = LocalWipe.DATABASE_NAME
    const val KEYSTORE_ALIAS: String = LocalWipe.DB_ALIAS
    private const val ENC_SUFFIX: String = LocalWipe.ENC_SUFFIX
    private const val PLAINTEXT_HEADER: String = "SQLite format 3\u0000"
    private const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
    private const val AUTH_VALIDITY_SECONDS: Int = 30

    fun open(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
        loadSqlCipher()
        return SupportOpenHelperFactory(passphrase)
    }

    fun passphrase(context: Context, vault: SharedPreferences): ByteArray {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val hasWrap = !vault.getString(PassphraseStore.PREFS_KEY, null).isNullOrEmpty()
        if (!com.sai.cardtrack.data.EncryptedDb.canCreatePassphrase(
                hasWrap = hasWrap,
                fileExists = dbFile.exists(),
                looksPlaintext = looksLikePlaintextSqlite(dbFile)
            )
        ) {
            throw EncryptedDbUnreadableException()
        }
        val store = PassphraseStore(
            secretKey = { openOrCreateAesKey() },
            readWrapped = { vault.getString(PassphraseStore.PREFS_KEY, null) },
            writeWrapped = { value ->
                vault.edit(commit = true) { putString(PassphraseStore.PREFS_KEY, value) }
            }
        )
        val bytes = store.getOrCreate()
        migratePlaintextIfNeeded(context, bytes)
        return bytes
    }

    fun openOrCreateAesKey(): SecretKey {
        loadExisting()?.let { return it }
        return generateAesKey(userAuth = true) // StrongBox→TEE fallback stays; userAuth=false is gone
    }

    internal fun looksLikePlaintextSqlite(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        val expected = PLAINTEXT_HEADER.toByteArray(Charsets.UTF_8)
        val header = ByteArray(expected.size)
        file.inputStream().use { stream ->
            if (stream.read(header) < expected.size) return false
        }
        return header.contentEquals(expected)
    }

    private fun migratePlaintextIfNeeded(context: Context, passphrase: ByteArray) {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists() || !looksLikePlaintextSqlite(dbFile)) return

        val plaintext = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .build()
        plaintext.openHelper.writableDatabase
        plaintext.close()

        exportPlaintext(dbFile, passphrase)
    }

    private fun exportPlaintext(dbFile: File, passphrase: ByteArray) {
        loadSqlCipher()
        val encFile = File(dbFile.path + ENC_SUFFIX)
        if (encFile.exists()) {
            encFile.delete()
        }
        val sqlcipher = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null, null)
        try {
            val version = sqlcipher.version
            sqlcipher.execSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                arrayOf(encFile.absolutePath, passphrase)
            )
            sqlcipher.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            sqlcipher.rawExecSQL("PRAGMA encrypted.user_version = $version")
            sqlcipher.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            sqlcipher.close()
        }
        File(dbFile.path + "-wal").takeIf { it.exists() }?.delete()
        File(dbFile.path + "-shm").takeIf { it.exists() }?.delete()
        if (!dbFile.delete()) {
            error("failed to delete plaintext ${dbFile.name}")
        }
        if (!encFile.renameTo(dbFile)) {
            error("failed to rename ${encFile.name} to ${dbFile.name}")
        }
    }

    private fun loadSqlCipher() {
        System.loadLibrary("sqlcipher")
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
