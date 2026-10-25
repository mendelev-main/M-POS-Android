package com.mendelev.mpos.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import androidx.core.content.FileProvider
import com.mendelev.mpos.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class UpdateRepository(private val context: Context, fixtureUrl: String? = null) {
    private val fixture = BuildConfig.DEBUG && fixtureUrl != null && UpdatePolicy.isLocalUrl(fixtureUrl)
    private val manifestUrl = if (fixture) requireNotNull(fixtureUrl) else UPDATE_MANIFEST_URL
    private val installed = context.packageManager.getPackageInfo(context.packageName, 0)
    val installedCode = installed.longVersionCode
    val installedName: String = installed.versionName ?: installedCode.toString()
    private val signer = if (BuildConfig.DEBUG) installedSigners(context).single() else BuildConfig.UPDATE_CERT_SHA256
    private val policy = UpdatePolicy(context.packageName, installedCode, Build.VERSION.SDK_INT, signer, fixture)
    private val http = UpdateHttp(fixture)
    private val folder = File(context.filesDir, "updates").apply { mkdirs() }
    private val apk = File(folder, "candidate.apk")
    private val partial = File(folder, "candidate.part")
    private val metadata = AtomicFile(File(folder, "candidate.json"))
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    val releaseChannelAvailable = !BuildConfig.DEBUG || fixture
    val lastChecked: Long get() = prefs.getLong("lastChecked", 0)

    suspend fun check(): UpdateDescriptor? = withContext(Dispatchers.IO) {
        check(releaseChannelAvailable) { "Обновления доступны в подписанной версии M-POS" }
        val descriptor = policy.parse(http.manifest(manifestUrl))
        check(prefs.edit().putLong("lastChecked", System.currentTimeMillis()).commit()) { "Не удалось сохранить результат проверки" }
        descriptor.takeIf(policy::isNewer)
    }

    suspend fun download(update: UpdateDescriptor, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        clearDownloaded()
        try {
            require(folder.usableSpace >= update.apkBytes + 16 * 1024 * 1024) { "Недостаточно свободного места для APK" }
            http.download(update.apkUrl, partial, update.apkBytes, progress)
            currentCoroutineContext().ensureActive()
            verify(update, partial)
            currentCoroutineContext().ensureActive()
            check(partial.renameTo(apk)) { "Не удалось сохранить APK" }
            val stream = metadata.startWrite()
            try { stream.write(update.rawJson.toByteArray()); metadata.finishWrite(stream) }
            catch (error: Exception) { metadata.failWrite(stream); throw error }
            apk
        } catch (error: Exception) { clearDownloaded(); throw error }
    }

    suspend fun restore(): UpdateDescriptor? = withContext(Dispatchers.IO) {
        partial.delete()
        if (!apk.exists()) { metadata.delete(); return@withContext null }
        try {
            val text = metadata.openRead().use { stream ->
                val buffer = ByteArray(64 * 1024 + 1)
                var total = 0
                while (total < buffer.size) {
                    val count = stream.read(buffer, total, buffer.size - total)
                    if (count == -1) break
                    total += count
                }
                require(total <= 64 * 1024)
                String(buffer, 0, total, Charsets.UTF_8)
            }
            val update = policy.parse(text)
            verify(update, apk)
            update
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { clearDownloaded(); null }
    }

    /** Recheck bytes and signer immediately before granting the installer access. */
    suspend fun installationIntent(update: UpdateDescriptor): Intent = withContext(Dispatchers.IO) {
        com.mendelev.mpos.core.operations.InstallationGate.prepare {
        verify(update, apk)
        check(prefs.edit().putLong("installTarget", update.versionCode).commit()) { "Не удалось сохранить состояние установки" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    fun completedUpdate(): Boolean {
        val target = prefs.getLong("installTarget", 0)
        return if (target > 0 && installedCode >= target) {
            prefs.edit().remove("installTarget").apply()
            clearDownloaded()
            true
        } else false
    }

    private fun verify(update: UpdateDescriptor, file: File) {
        require(file.isFile && file.length() == update.apkBytes) { "Размер APK не совпадает с описанием" }
        require(UpdatePolicy.sha256(file) == update.sha256) { "Контрольная сумма APK не совпадает" }
        val flags=PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val info=context.packageManager.getPackageArchiveInfo(file.absolutePath,flags)
            ?: error("Не удалось прочитать APK")
        val signatures = info.signingInfo?.apkContentsSigners?.map { certHash(it.toByteArray()) }?.toSet().orEmpty()
        policy.validateArchive(update, ApkIdentity(info.packageName, info.longVersionCode,
            info.versionName ?: "", requireNotNull(info.applicationInfo).minSdkVersion, signatures))
    }

    private fun clearDownloaded() { partial.delete(); apk.delete(); metadata.delete() }

    companion object {
        private fun certHash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        @Suppress("DEPRECATION")
        fun installedSigners(context: Context): Set<String> = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            ?.apkContentsSigners?.map { certHash(it.toByteArray()) }?.toSet().orEmpty()
    }
}
