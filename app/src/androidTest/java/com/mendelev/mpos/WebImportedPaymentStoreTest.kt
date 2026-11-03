package com.mendelev.mpos

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.*
import com.mendelev.mpos.domain.backup.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class WebImportedPaymentStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(note: String="large"): WebBackupDocument {
        val j=JSONObject().put("version",1).put("products",JSONArray().put(JSONObject().put("id","p").put("type","simple").put("name","Fixture").put("price",100).put("cost",20).put("stock",10).put("stockUnit","piece")))
            .put("employees",JSONArray().put(JSONObject().put("id","cashier").put("name","Fixture cashier").put("role","employee")))
            .put("shifts",JSONArray().put(JSONObject().put("id","s").put("employeeId","cashier").put("employeeName","Fixture cashier").put("status","open").put("openingCash",0).put("openedAt",1)))
            .put("orders",JSONArray()).put("parked",JSONArray()).put("currentOrderSession",JSONObject().put("items",JSONArray().put(JSONObject().put("productId","p").put("name","Fixture").put("price",100).put("qty",1)))
                .put("paymentDraft",JSONObject().put("version",1).put("totalCents",10000).put("parts",JSONArray().put(JSONObject().put("method","cash").put("amount",40).put("paid",true).put("cashGiven",50).put("change",10)).put(JSONObject().put("method","card").put("amount",60).put("paid",false)))))
        val session=j.getJSONObject("currentOrderSession").put("createdAt",2).put("orderType","На месте").put("orderComment",note)
        session.getJSONObject("paymentDraft").put("updatedAt",3)
        j.getJSONArray("products").getJSONObject(0).put("stockUnit","piece")
        repeat(if(note=="large")80 else 0) {j.getJSONArray("parked").put(JSONObject(session.toString()).put("id","p$it"))}
        return WebBackupReader.read(j.toString().byteInputStream())
    }
    private fun root()=File(context.cacheDir,"imported-paid-${UUID.randomUUID()}").apply {mkdirs()}
    @Test fun atomicFaultsReopenReplayAndOldAuthorityDoNotChangeNativeBusinessData()=runBlocking {
        val p=AdminForms(this).permit();val root=root();val path=File(root,"candidate.db").absolutePath
        var db=Room.databaseBuilder(context,CatalogDatabase::class.java,path).build()
        try {
            CatalogRepository(db).initialize();val doc=source();val expected=WebImportedPaymentDocuments.encode(WebImportedPaymentEvidenceCodec.encode(doc)).size
            for(failAt in 1..expected) {
                var writes=0
                try {p.write {WebImportedPaymentStore(context,db) {if(++writes==failAt) error("injected")}.install(doc)};fail()} catch(_:IllegalStateException) {}
                assertTrue(db.workspace().documents().none {WebImportedPaymentDocuments.owns(it.id)})
            }
            p.write {WebImportedPaymentStore(context,db).install(doc)};val before=db.workspace().documents()
            db.close();db=Room.databaseBuilder(context,CatalogDatabase::class.java,path).build()
            assertEquals(81,WebImportedPaymentStore(context,db).read()!!.records.size)
            p.write {WebImportedPaymentStore(context,db).install(doc)};assertEquals(before,db.workspace().documents())
            try {p.write {WebImportedPaymentStore(context,db).install(source("different"))};fail()} catch(_:BackupFailure) {}
            assertEquals(before,db.workspace().documents());assertTrue(db.catalog().movements().isEmpty());assertTrue(db.sales().history().isEmpty());assertTrue(db.shifts().history().isEmpty())
            assertNull(db.catalog().result(WebImportedPaymentStore(context,db).read()!!.records.first().origin))
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            try {p.write {WebImportedPaymentStore(context,db).install(doc)};fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
            assertEquals(before,db.workspace().documents())
        } finally {db.close();root.deleteRecursively()}
    }
    @Test fun nativeArchiveRoundtripAndIndependentValidationPreserveImportedPaidEvidence()=runBlocking {
        val p=AdminForms(this).permit();val root=root();val path=File(root,"candidate.db").absolutePath
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,path).build()
        try {
            CatalogRepository(db).initialize();val photos=ProductPhotoStore(File(root,"media"))
            // A pre-feature schema9 snapshot with no imported-payment namespace remains valid.
            val old=File(root,"old").apply {mkdir()};val oldFiles=NativeSnapshotDatabase.capture(db,photos,old)
            NativeRestoreValidator.validate(context,oldFiles,old)
            p.write {WebImportedPaymentStore(context,db).install(source())}
            val dir=File(root,"snapshot").apply {mkdir()};val files=NativeSnapshotDatabase.capture(db,photos,dir)
            val archive=File(root,"copy.mposnative");NativeBackupArchive.write(archive,files)
            val extracted=File(root,"extracted").apply {mkdir()};val restored=archive.inputStream().use {NativeBackupArchive.read(it,extracted)}
            NativeRestoreValidator.validate(context,restored,extracted)
            val clone=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"clone.db").absolutePath).createFromFile(restored.database).build()
            try {
                val value=WebImportedPaymentStore(context,clone).read()!!
                assertEquals(4000L,value.records.first().paid)
                assertEquals(6000L,value.records.first().remaining)
                assertEquals(5000L,value.records.first().parts[0].given)
                assertEquals(1000L,value.records.first().parts[0].change)
                assertEquals(db.workspace().documents(),clone.workspace().documents())
            } finally {clone.close()}
            android.database.sqlite.SQLiteDatabase.openDatabase(restored.database.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {raw->
                raw.execSQL("UPDATE workspace_documents SET payload=? WHERE id=?",arrayOf("{\"version\":1,\"content\":\"corrupt\"}",WebImportedPaymentDocuments.KEY+":0000"))
            }
            try {NativeSnapshotDatabase.validate(restored);fail()} catch(_:BackupFailure) {}
        } finally {db.close();root.deleteRecursively()}
    }
    @Test fun activeDatabaseRefusesAppendAndRestoreSealsAlsoProtectLegacyNamespace()=runBlocking {
        val p=AdminForms(this).permit();val live=CatalogDatabase.get(context)
        try {p.write {WebImportedPaymentStore(context,live).install(source("small"))};fail()} catch(e:BackupFailure) {assertEquals("importedPaymentTarget",e.kind)}
        val root=root();val db=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"candidate.db").absolutePath).build()
        try {
            CatalogRepository(db).initialize();p.write {WebImportedPaymentStore(context,db).install(source("small"))}
            val before=db.workspace().documents();db.withTransaction {NativeWriteSeal.install(db.openHelper.writableDatabase)}
            try {db.workspace().delete(WebImportedPaymentDocuments.KEY);fail()} catch(_:android.database.SQLException) {}
            assertEquals(before,db.workspace().documents());assertEquals(1,WebImportedPaymentStore(context,db).read()!!.records.size)
        } finally {db.close();root.deleteRecursively()}
    }
}
