package com.mendelev.mpos

import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.telegram.TelegramKeystoreCipher
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BackendSettingsStoreTest {
    @Test fun encryptedReopenStaleSessionAndCorruptionDoNotOverwriteSettings() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"backend-fixture-${UUID.randomUUID()}.enc")
        var authorized=true
        fun store()=BackendSettingsStore(file,TelegramKeystoreCipher("mpos.backend.fixture.v1")) {authorized}
        try {
            val first=BackendSettings(url="https://shop.example.org",deviceKey="fixture-key-not-a-secret")
            val saved=store().save(first)
            assertEquals(saved,store().read())
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(first.deviceKey))
            val original=file.readBytes()
            try {store().save(first.copy(deviceName="stale"));fail()} catch(_:IllegalStateException) {}
            assertArrayEquals(original,file.readBytes())
            authorized=false
            try {store().save(saved.copy(deviceName="unauthorized"));fail()} catch(_:IllegalStateException) {}
            assertArrayEquals(original,file.readBytes())
            authorized=true;file.writeBytes(byteArrayOf(1,2,3))
            try {store().save(saved);fail()} catch(_:Exception) {}
            assertArrayEquals(byteArrayOf(1,2,3),file.readBytes())
        } finally {file.delete();File(file.path+".bak").delete();File(file.path+".new").delete()}
    }
}
