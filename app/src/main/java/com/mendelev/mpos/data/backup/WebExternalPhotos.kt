package com.mendelev.mpos.data.backup

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

/** Never follows backup-supplied redirects or resolves local/private network addresses. */
internal object WebExternalPhotos {
    fun read(value: String): ByteArray {
        val url=value.toHttpUrlOrNull()
        require(url!=null && url.isHttps && url.username.isEmpty() && url.password.isEmpty()) {"Фотография по ссылке требует HTTPS"}
        require(!url.host.contains(':') && !Regex("[0-9.]+").matches(url.host)) {"Фотографии по IP должны быть вложены в Web-копию"}
        val dns=object: Dns {override fun lookup(host: String): List<java.net.InetAddress> {
            val addresses=Dns.SYSTEM.lookup(host)
            require(addresses.isNotEmpty() && addresses.none {a->
                a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress ||
                    a.address.size==16 && (a.address[0].toInt() and 254)==252
            }) {"Фотография с локального адреса должна быть вложена в Web-копию"}
            return addresses
        }}
        val client=OkHttpClient.Builder().dns(dns).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(15,TimeUnit.SECONDS).build()
        return client.newCall(Request.Builder().url(url).build()).execute().use {r->
            check(r.isSuccessful) {"Не удалось загрузить фотографию из Web-копии"}
            val source=r.body?.source() ?: error("Пустая фотография")
            source.request(2_000_001);require(source.buffer.size in 1..2_000_000) {"Фотография превышает допустимый размер"}
            source.readByteArray()
        }
    }
}
