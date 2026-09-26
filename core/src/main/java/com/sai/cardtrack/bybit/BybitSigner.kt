package com.sai.cardtrack.bybit

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BybitSigner {
    fun sign(
        timestamp: String,
        apiKey: String,
        recvWindow: String,
        jsonBody: String,
        apiSecret: String
    ): String {
        val payload = timestamp + apiKey + recvWindow + jsonBody
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
            "%02x".format(byte)
        }
    }
}
