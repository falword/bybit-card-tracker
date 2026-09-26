package com.sai.cardtrack.bybit

import okhttp3.CertificatePinner

/**
 * SPKI pins for Bybit API hosts.
 *
 * Pins Amazon Root CA 1–4 and Amazon RSA 2048 M01–M04 (not leaves), so leaf
 * rotation does not break the client. Hashes taken 2026-09-06 from
 * https://www.amazontrust.com/repository/ and the live
 * api.bybit.com / api.bytick.com chains.
 */
object BybitTls {
    const val HOST_BYBIT: String = "api.bybit.com"
    const val HOST_BYTICK: String = "api.bytick.com"

    const val AMAZON_ROOT_CA_1: String = "++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="
    const val AMAZON_ROOT_CA_2: String = "f0KW/FtqTjs108NpYj42SrGvOB2PpxIVM8nWxjPqJGE="
    const val AMAZON_ROOT_CA_3: String = "NqvDJlas/GRcYbcWE8S/IceH9cq77kg0jVhZeAPXq8k="
    const val AMAZON_ROOT_CA_4: String = "9+ze1cZgR9KO1kZrVDxA4HQ6voHRCSVNz4RdTCx4U8U="
    const val AMAZON_RSA_2048_M01: String = "DxH4tt40L+eduF6szpY6TONlxhZhBd+pJ9wbHlQ2fuw="
    const val AMAZON_RSA_2048_M02: String = "18tkPyr2nckv4fgo0dhAkaUtJ2hu2831xlO2SKhq8dg="
    const val AMAZON_RSA_2048_M03: String = "vxRon/El5KuI4vx5ey1DgmsYmRY0nDd5Cg4GfJ8S+bg="
    const val AMAZON_RSA_2048_M04: String = "G9LNNAql897egYsabashkzUCTEJkWBzgoEtk8X/678c="

    private val pins: List<String> = listOf(
        AMAZON_ROOT_CA_1,
        AMAZON_ROOT_CA_2,
        AMAZON_ROOT_CA_3,
        AMAZON_ROOT_CA_4,
        AMAZON_RSA_2048_M01,
        AMAZON_RSA_2048_M02,
        AMAZON_RSA_2048_M03,
        AMAZON_RSA_2048_M04
    )

    fun certificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        for (host in listOf(HOST_BYBIT, HOST_BYTICK)) {
            for (pin in pins) {
                builder.add(host, "sha256/$pin")
            }
        }
        return builder.build()
    }
}
