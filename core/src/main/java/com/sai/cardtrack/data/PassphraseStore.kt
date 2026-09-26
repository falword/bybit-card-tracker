package com.sai.cardtrack.data

import java.security.SecureRandom
import javax.crypto.SecretKey

class PassphraseStore(
    private val secretKey: () -> SecretKey,
    private val readWrapped: () -> String?,
    private val writeWrapped: (String) -> Unit,
    private val randomBytes: () -> ByteArray = { generate() }
) {
    fun getOrCreate(beforeFirstWrite: (ByteArray) -> Unit = {}): ByteArray {
        val existing = readWrapped()
        if (existing != null) {
            return VaultCrypto.decrypt(secretKey(), VaultCrypto.decode(existing))
        }
        val bytes = randomBytes()
        require(bytes.size == BYTE_COUNT) { "passphrase must be $BYTE_COUNT bytes" }
        beforeFirstWrite(bytes)
        writeWrapped(VaultCrypto.encode(VaultCrypto.encrypt(secretKey(), bytes)))
        return bytes
    }

    companion object {
        const val PREFS_KEY: String = "db_pass"
        const val BYTE_COUNT: Int = 32

        fun generate(): ByteArray {
            val bytes = ByteArray(BYTE_COUNT)
            SecureRandom().nextBytes(bytes)
            return bytes
        }
    }
}
