package com.mendelev.mpos.update

import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.json.JSONObject

const val UPDATE_MANIFEST_URL = "https://github.com/mendelev-main/M-POS-Android/releases/latest/download/update.json"
const val MAX_APK_BYTES = 200L * 1024 * 1024

data class UpdateDescriptor(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val apkBytes: Long,
    val sha256: String,
    val certificateSha256: String,
    val notes: String,
    val rawJson: String,
)

data class ApkIdentity(val packageName: String, val versionCode: Long, val versionName: String, val minSdk: Int, val signers: Set<String>)

class UpdatePolicy(
    val packageName: String,
    val installedVersion: Long,
    val sdk: Int,
    val certificate: String,
    val localFixture: Boolean = false,
) {
    fun parse(text: String): UpdateDescriptor {
        val obj = JSONObject(text)
        require(obj.getInt("schemaVersion") == 1) { "Неизвестный формат обновления" }
        require(obj.getString("channel") == "stable") { "Неверный канал обновления" }
        require(obj.getString("applicationId") == packageName) { "Обновление предназначено для другого приложения" }
        val code = obj.getLong("versionCode")
        require(code in 1..2_100_000_000L) { "Некорректная версия" }
        val name = obj.getString("versionName")
        require(name.matches(Regex("[0-9][0-9A-Za-z.+_-]{0,79}"))) { "Некорректное имя версии" }
        val minimum = obj.getInt("minSdk")
        require(minimum in 28..sdk) { "Для обновления нужна более новая версия Android" }
        val url = obj.getString("apkUrl")
        require(validAssetUrl(url)) { "Недопустимый адрес обновления" }
        val size = obj.getLong("apkBytes")
        require(size in 1..MAX_APK_BYTES) { "Недопустимый размер обновления" }
        val sha = obj.getString("sha256")
        val cert = obj.getString("certificateSha256")
        require(sha.matches(Regex("[0-9a-f]{64}"))) { "Некорректная контрольная сумма" }
        require(cert == certificate) { "Сертификат обновления не совпадает с M-POS" }
        val notes = obj.optString("notes", "")
        require(notes.length <= 8000) { "Слишком большое описание обновления" }
        return UpdateDescriptor(code, name, minimum, url, size, sha, cert, notes, text)
    }

    fun isNewer(update: UpdateDescriptor) = update.versionCode > installedVersion

    fun validateArchive(update: UpdateDescriptor, archive: ApkIdentity) {
        require(isNewer(update)) { "Установлена такая же или более новая версия" }
        require(archive.packageName == packageName) { "APK предназначен для другого приложения" }
        require(archive.versionCode == update.versionCode && archive.versionName == update.versionName) { "Версия APK не совпадает с описанием" }
        require(archive.minSdk == update.minSdk && archive.minSdk <= sdk) { "APK несовместим с Android" }
        require(archive.signers == setOf(certificate)) { "APK подписан другим сертификатом" }
    }

    fun validAssetUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        if (localFixture && isLocalUrl(value)) return@runCatching true
        uri.scheme == "https" && uri.host == "github.com" && uri.port in listOf(-1, 443) &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            Regex("/mendelev-main/M-POS-Android/releases/download/v[0-9A-Za-z._-]+/mpos-native\\.apk").matches(uri.rawPath)
    }.getOrDefault(false)

    companion object {
        fun isLocalUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost") &&
                uri.port in 1024..65535 && uri.rawUserInfo == null && uri.rawFragment == null && uri.rawQuery == null
        }.getOrDefault(false)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count == -1) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
