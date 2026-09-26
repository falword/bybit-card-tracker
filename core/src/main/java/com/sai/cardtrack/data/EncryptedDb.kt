package com.sai.cardtrack.data

class EncryptedDbUnreadableException : Exception()

object EncryptedDb {
    fun canCreatePassphrase(
        hasWrap: Boolean,
        fileExists: Boolean,
        looksPlaintext: Boolean
    ): Boolean {
        if (hasWrap) return true
        return !fileExists || looksPlaintext
    }
}
