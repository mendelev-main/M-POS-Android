package com.mendelev.mpos

import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.backend.OrderInbox
import com.mendelev.mpos.telegram.TelegramSecretCipher
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class OrderInboxTest {
    private fun file()=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"inbox-${UUID.randomUUID()}.json")
    private fun row(id: String)=JSONObject().put("id",id).put("status","new").put("created_at","2026-11-09T10:00:00Z")
    private val cipher=object: TelegramSecretCipher {override fun encrypt(bytes: ByteArray)=bytes;override fun decrypt(bytes: ByteArray)=bytes}
    @Test fun completeRefreshAndLateSnapshotRemainConsistentAcrossFileReopen() {
        val file=file()
        try {
            val inbox=OrderInbox(file,cipher);inbox.merge(listOf(row("taken"),row("waiting")),emptySet())
            inbox.reconcileCurrent(listOf(row("waiting")),emptySet())
            val reopened=OrderInbox(file,cipher)
            assertEquals(listOf("waiting"),reopened.read().map {it.getString("id")})
            assertEquals(listOf("waiting"),reopened.merge(listOf(row("taken")),emptySet()).map {it.getString("id")})
            val before=file.readText()
            try {reopened.reconcileCurrent(listOf(row("broken").put("status","accepted")),emptySet());fail()} catch(_:IllegalArgumentException) {}
            assertEquals(before,file.readText())
        } finally {file.delete()}
    }
    @Test fun parallelInstancesDoNotLoseOrdersOrCorruptAtomicFile()=runBlocking {
        val file=file()
        try {
            val a=OrderInbox(file,cipher);val b=OrderInbox(file,cipher)
            coroutineScope {repeat(20) {i->launch(Dispatchers.IO) {(if(i%2==0) a else b).merge(listOf(row("order-$i")),emptySet())}}}
            assertEquals(20,a.read().size)
            assertEquals(19,b.merge(emptyList(),setOf("order-1")).size)
        } finally {file.delete()}
    }
    @Test fun duplicateSnapshotsDoNotRewriteAndCorruptInboxCannotBeOverwritten() {
        val file=file();val writes=AtomicInteger()
        val counting=object: TelegramSecretCipher {override fun encrypt(bytes: ByteArray): ByteArray {writes.incrementAndGet();return bytes};override fun decrypt(bytes: ByteArray)=bytes}
        try {
            OrderInbox(file,counting).merge(listOf(row("1")),emptySet())
            OrderInbox(file,counting).merge(listOf(row("1")),emptySet())
            assertEquals(1,writes.get())
            file.writeText("corrupted")
            try {OrderInbox(file,counting).merge(listOf(row("2")),emptySet());fail()} catch(_:Exception) {}
            assertEquals("corrupted",file.readText())
        } finally {file.delete()}
    }

    @Test fun removedOrderCannotReturnAfterAtomicFileReopenOrLateEvent() {
        val file=file()
        try {
            val inbox=OrderInbox(file,cipher);inbox.merge(listOf(row("one"),row("two")),emptySet());inbox.remove("one")
            val reopened=OrderInbox(file,cipher)
            assertTrue(reopened.isRemoved("one"));assertFalse(reopened.isRemoved("two"))
            assertEquals(listOf("two"),reopened.merge(listOf(row("one")),emptySet()).map {it.getString("id")})
        } finally {file.delete()}
    }
}
