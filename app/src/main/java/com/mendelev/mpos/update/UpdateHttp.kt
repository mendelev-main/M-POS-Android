package com.mendelev.mpos.update

import java.io.File
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Only public release assets; no account token, cookies or relaxed TLS. */
class UpdateHttp(
    private val localFixture: Boolean = false,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES).build(),
) {
    fun allowedUrl(value: String): Boolean = runCatching {
        if (localFixture && UpdatePolicy.isLocalUrl(value)) return@runCatching true
        val uri = URI(value)
        uri.scheme == "https" && uri.port in listOf(-1, 443) && uri.rawUserInfo == null &&
            uri.rawFragment == null && uri.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)

    private suspend fun open(url: String): Response {
        var next = url
        repeat(6) {
            currentCoroutineContext().ensureActive()
            require(allowedUrl(next)) { "Недопустимое перенаправление загрузки" }
            val response = client.newCall(Request.Builder().url(next).header("Accept", "application/octet-stream").build()).execute()
            if (response.code in setOf(301, 302, 303, 307, 308)) {
                val location = response.header("Location")
                val redirected = location?.let { response.request.url.resolve(it)?.toString() }
                response.close()
                next = redirected ?: throw IOException("Некорректное перенаправление")
            } else {
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    if (code == 404) throw NoPublishedUpdate()
                    throw IOException("Сервер обновлений ответил HTTP $code")
                }
                return response
            }
        }
        throw IOException("Слишком много перенаправлений")
    }

    suspend fun manifest(url: String): String = open(url).use { response ->
        val source = response.body?.source() ?: throw IOException("Пустой ответ сервера")
        val buffer = okio.Buffer()
        while (buffer.size <= 64 * 1024) {
            currentCoroutineContext().ensureActive()
            if (source.read(buffer, minOf(8192L, 64 * 1024 + 1L - buffer.size)) == -1L) break
        }
        require(buffer.size <= 64 * 1024) { "Описание обновления слишком большое" }
        buffer.readUtf8()
    }

    suspend fun download(url: String, destination: File, expectedBytes: Long, progress: (Int) -> Unit) {
        require(expectedBytes in 1..MAX_APK_BYTES)
        open(url).use { response ->
            val body = response.body ?: throw IOException("Пустой APK")
            val declared = body.contentLength()
            require(declared == -1L || declared == expectedBytes) { "Размер загрузки не совпадает с описанием" }
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var previous = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = try { input.read(buffer) }
                        catch (error: IOException) { throw IOException("APK download interrupted after $written of $expectedBytes bytes", error) }
                        if (count == -1) break
                        written += count
                        require(written <= expectedBytes) { "Размер APK превышает ожидаемый" }
                        output.write(buffer, 0, count)
                        val percent = (written * 100 / expectedBytes).toInt()
                        if (percent != previous) { progress(percent); previous = percent }
                    }
                    require(written == expectedBytes) { "APK скачан не полностью" }
                    output.fd.sync()
                }
            }
        }
    }
}

class NoPublishedUpdate : IOException("Опубликованных обновлений пока нет")
