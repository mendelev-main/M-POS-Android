package com.mendelev.mpos

import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PrinterSettingsStoreTest {
    @Test fun reopenEditStaleRefusalAuthorizationAndDelete() {
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"printer-${UUID.randomUUID()}.json")
        var authorized=true
        fun store()=PrinterSettingsStore(file) {authorized}
        try {
            val initial=PrinterConfiguration(name="Касса",ip="192.168.1.100",printOrders=true,categories="Кофе\nДесерты",phrases="Спасибо\nДо встречи")
            val saved=store().save(initial).single()
            assertEquals(saved,store().read().single())
            val updated=store().save(saved.copy(paperWidth="58",name="Бар")).single()
            assertEquals("58",store().read().single().paperWidth)
            try {store().save(saved.copy(name="Устарело"));fail()} catch(_:IllegalStateException) {}
            assertEquals(updated,store().read().single())
            val withDrawer=store().save(updated.copy(drawer=true)).single()
            val second=store().save(PrinterConfiguration(name="Бар",ip="192.168.1.101",drawer=true)).single {it.id!=updated.id}
            assertEquals(second.id,store().read().single {it.drawer}.id)
            assertTrue(store().read().single {it.id==withDrawer.id}.revision>withDrawer.revision)
            store().delete(second)
            val remaining=store().read().single()
            authorized=false
            try {store().delete(remaining);fail()} catch(_:IllegalStateException) {}
            assertEquals(remaining,store().read().single())
            authorized=true;assertTrue(store().delete(remaining).isEmpty());assertTrue(store().read().isEmpty())
            file.writeText("broken")
            try {store().save(initial);fail()} catch(_:org.json.JSONException) {}
            assertEquals("broken",file.readText())
        } finally {file.delete();File(file.path+".bak").delete();File(file.path+".new").delete()}
    }
}
