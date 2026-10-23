package com.mendelev.mpos.update

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class UpdateHttpTest {
    @Test fun downloadsExactBytesAndRejectsShortOrOversizedBodies() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = UpdateHttp(localFixture = true)
            val file = File.createTempFile("update", ".part")
            try {
                server.enqueue(MockResponse().setBody("apk-data"))
                val progress = mutableListOf<Int>()
                client.download(server.url("/apk").toString(), file, 8) { progress.add(it) }
                assertEquals("apk-data", file.readText())
                assertEquals(100, progress.last())
                for (body in listOf("short", "longer-than-limit")) {
                    server.enqueue(MockResponse().setChunkedBody(body, 3))
                    try { client.download(server.url("/apk").toString(), file, 8) { }; fail("Bad size accepted") }
                    catch (_: Exception) { }
                }
            } finally { file.delete() }
        }
    }

    @Test fun manifestIsBoundedAndMissingReleaseIsDistinct() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = UpdateHttp(localFixture = true)
            server.enqueue(MockResponse().setBody("{\"schemaVersion\":1}"))
            assertEquals("{\"schemaVersion\":1}", client.manifest(server.url("/update.json").toString()))
            server.enqueue(MockResponse().setBody("x".repeat(65 * 1024)))
            try { client.manifest(server.url("/large").toString()); fail("Oversized manifest accepted") }
            catch (_: IllegalArgumentException) { }
            server.enqueue(MockResponse().setResponseCode(404))
            try { client.manifest(server.url("/missing").toString()); fail("Missing release accepted") }
            catch (_: NoPublishedUpdate) { }
        }
    }

    @Test fun cancellationStopsAStreamingDownload() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("x".repeat(64 * 1024)).throttleBody(1024, 100, TimeUnit.MILLISECONDS))
            val progress = CompletableDeferred<Unit>()
            val file = File.createTempFile("cancel-update", ".part")
            try {
                val job = launch(Dispatchers.IO) {
                    UpdateHttp(true).download(server.url("/slow").toString(), file, 64 * 1024L) { progress.complete(Unit) }
                }
                withTimeout(5000) { progress.await() }
                job.cancelAndJoin()
                assertTrue(job.isCancelled)
                assertTrue(file.length() < 64 * 1024)
            } finally { file.delete() }
        }
    }

    @Test fun redirectsNeverDowngradeOrLeakToUntrustedHosts() = runBlocking {
        val strict = UpdateHttp()
        assertFalse(strict.allowedUrl("http://github.com/file"))
        assertFalse(strict.allowedUrl("https://github.com.evil.invalid/file"))
        assertFalse(strict.allowedUrl("https://user:password@github.com/file"))
        assertTrue(strict.allowedUrl("https://release-assets.githubusercontent.com/file?signature=value"))
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://attacker.invalid/file"))
            try { UpdateHttp(true).manifest(server.url("/redirect").toString()); fail("Untrusted redirect accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
