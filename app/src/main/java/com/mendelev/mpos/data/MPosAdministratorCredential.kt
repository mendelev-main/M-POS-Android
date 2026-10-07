package com.mendelev.mpos.data

import java.security.MessageDigest

/** Exact reviewed administrator credential policy; no trim or role-policy change. */
internal object MPosAdministratorCredential {
    private const val REVIEWED_SHA256 = "cb104bb7036272cadc80638e2e58d92d81af1a749b0a1138f502e9b4cea0a3d2"
    fun accepts(value: String): Boolean {
        val actual = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val expected = REVIEWED_SHA256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(expected, actual)
    }
}
