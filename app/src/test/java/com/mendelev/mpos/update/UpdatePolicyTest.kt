package com.mendelev.mpos.update

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val cert = "ab".repeat(32)
    private val policy = UpdatePolicy("com.mendelev.mpos", 10, 35, cert)
    private fun manifest(): JSONObject = JSONObject().put("schemaVersion", 1).put("channel", "stable")
        .put("applicationId", "com.mendelev.mpos").put("versionCode", 11).put("versionName", "0.1.11")
        .put("minSdk", 28).put("apkUrl", "https://github.com/mendelev-main/M-POS-Android/releases/download/v0.1.11/mpos-native.apk")
        .put("apkBytes", 1234).put("sha256", "cd".repeat(32)).put("certificateSha256", cert).put("notes", "Изменения")
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Invalid update accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun validUpdateAndActualArchiveMustAgree() {
        val update = policy.parse(manifest().toString())
        assertTrue(policy.isNewer(update))
        val archive = ApkIdentity("com.mendelev.mpos", 11, "0.1.11", 28, setOf(cert))
        policy.validateArchive(update, archive)
        listOf(archive.copy(packageName = "com.mendelev.mpos.web"), archive.copy(versionCode = 12),
            archive.copy(versionName = "other"), archive.copy(minSdk = 36), archive.copy(signers = setOf("ef".repeat(32))),
            archive.copy(signers = setOf(cert, "ef".repeat(32)))).forEach { invalid -> rejects { policy.validateArchive(update, invalid) } }
    }

    @Test fun sameAndOlderVersionsAreNeverInstalled() {
        for (code in listOf(9, 10)) {
            val update = policy.parse(manifest().put("versionCode", code).toString())
            assertFalse(policy.isNewer(update))
            rejects { policy.validateArchive(update, ApkIdentity("com.mendelev.mpos", code.toLong(), "0.1.11", 28, setOf(cert))) }
        }
    }

    @Test fun manifestRejectsWrongIdentitySchemaCompatibilitySizeAndSigner() {
        for ((key, value) in listOf("schemaVersion" to 2, "channel" to "beta", "applicationId" to "com.other.app",
            "minSdk" to 36, "apkBytes" to 0, "apkBytes" to MAX_APK_BYTES + 1, "certificateSha256" to "ef".repeat(32),
            "sha256" to "bad", "versionCode" to 0, "versionCode" to 2_100_000_001L)) {
            rejects { policy.parse(manifest().put(key, value).toString()) }
        }
    }

    @Test fun productionUrlsCannotSelectAnotherRepositoryOrCleartext() {
        val valid = manifest().getString("apkUrl")
        for (url in listOf(valid.replace("https:", "http:"), valid.replace("mendelev-main", "other"),
            valid.replace("github.com", "github.com.attacker.invalid"), valid.replace("https://", "https://user:pass@"),
            "$valid?redirect=1", "$valid#fragment", "http://127.0.0.1:8765/mpos-native.apk")) {
            rejects { policy.parse(manifest().put("apkUrl", url).toString()) }
        }
        assertTrue(UpdatePolicy.isLocalUrl("http://127.0.0.1:8765/update.json"))
        assertFalse(UpdatePolicy.isLocalUrl("http://192.168.1.2:8765/update.json"))
        assertTrue(UpdatePolicy.isLocalUrl("http://10.0.2.2:8765/update.json"))
    }
}
