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

class WebLegacyHistoryStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(note: String="x".repeat(130000))=WebBackupReader.read(JSONObject().put("version",1).put("products",JSONArray()).put("employees",JSONArray()).put("shifts",JSONArray())
        .put("orders",JSONArray().put(JSONObject().put("id","legacy-r").put("timestamp",1).put("total",10).put("method","card").put("items",JSONArray().put(JSONObject().put("productId","deleted").put("name","Then").put("qty","0.0004").put("cost","3.333333333333333").put("comment",note))))).toString().byteInputStream())
    private fun root()=File(context.cacheDir,"legacy-store-${UUID.randomUUID()}").apply {mkdirs()}
    @Test fun atomicFaultsReopenReplayAndOldAuthorityDoNotChangeNativeBusinessData()=runBlocking {
        val p=AdminForms(this).permit();val root=root();val path=File(root,"candidate.db").absolutePath
        var db=Room.databaseBuilder(context,CatalogDatabase::class.java,path).build()
        try {
            CatalogRepository(db).initialize();val doc=source();val expected=WebLegacyHistoryDocuments.encode(WebLegacyHistoryArchiveCodec.encode(doc)).size
            for(failAt in 1..expected) {
                var writes=0
                try {p.write {WebLegacyHistoryStore(context,db) {if(++writes==failAt) error("injected")}.install(doc)};fail()} catch(_:IllegalStateException) {}
                assertTrue(db.workspace().documents().none {WebLegacyHistoryDocuments.owns(it.id)})
            }
            p.write {WebLegacyHistoryStore(context,db).install(doc)};val before=db.workspace().documents()
            db.close();db=Room.databaseBuilder(context,CatalogDatabase::class.java,path).build()
            assertEquals(1,WebLegacyHistoryStore(context,db).read()!!.receipts.size)
            p.write {WebLegacyHistoryStore(context,db).install(doc)};assertEquals(before,db.workspace().documents())
            try {p.write {WebLegacyHistoryStore(context,db).install(source("different"))};fail()} catch(_:BackupFailure) {}
            assertEquals(before,db.workspace().documents());assertTrue(db.catalog().movements().isEmpty());assertTrue(db.sales().history().isEmpty())
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            try {p.write {WebLegacyHistoryStore(context,db).install(doc)};fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
            assertEquals(before,db.workspace().documents())
        } finally {db.close();root.deleteRecursively()}
    }
    @Test fun nativeArchiveRoundtripAndIndependentDomainValidationPreserveLegacyDecimalFacts()=runBlocking {
        val p=AdminForms(this).permit();val root=root();val path=File(root,"candidate.db").absolutePath
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,path).build()
        try {
            CatalogRepository(db).initialize();val photos=ProductPhotoStore(File(root,"media"))
            // A pre-feature schema9 snapshot with no legacy namespace remains valid.
            val old=File(root,"old").apply {mkdir()};val oldFiles=NativeSnapshotDatabase.capture(db,photos,old)
            NativeRestoreValidator.validate(context,oldFiles,old)
            p.write {WebLegacyHistoryStore(context,db).install(source())}
            val dir=File(root,"snapshot").apply {mkdir()};val files=NativeSnapshotDatabase.capture(db,photos,dir)
            val archive=File(root,"copy.mposnative");NativeBackupArchive.write(archive,files)
            val extracted=File(root,"extracted").apply {mkdir()};val restored=archive.inputStream().use {NativeBackupArchive.read(it,extracted)}
            NativeRestoreValidator.validate(context,restored,extracted)
            val clone=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"clone.db").absolutePath).createFromFile(restored.database).build()
            try {
                val value=WebLegacyHistoryStore(context,clone).read()!!
                assertEquals("3.333333333333333",value.receipts.single().lines.single().cost)
                assertEquals("0.0004",value.receipts.single().lines.single().quantity)
                assertEquals(db.workspace().documents(),clone.workspace().documents())
            } finally {clone.close()}
            android.database.sqlite.SQLiteDatabase.openDatabase(restored.database.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {raw->
                raw.execSQL("UPDATE workspace_documents SET payload=? WHERE id=?",arrayOf("{\"version\":1,\"content\":\"corrupt\"}",WebLegacyHistoryDocuments.PREFIX+"0000"))
            }
            try {NativeSnapshotDatabase.validate(restored);fail()} catch(_:BackupFailure) {}
        } finally {db.close();root.deleteRecursively()}
    }
    @Test fun activeDatabaseRefusesAppendAndRestoreSealsAlsoProtectLegacyNamespace()=runBlocking {
        val p=AdminForms(this).permit();val live=CatalogDatabase.get(context)
        try {p.write {WebLegacyHistoryStore(context,live).install(source("small"))};fail()} catch(e:BackupFailure) {assertEquals("legacyHistoryTarget",e.kind)}
        val root=root();val db=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"candidate.db").absolutePath).build()
        try {
            CatalogRepository(db).initialize();p.write {WebLegacyHistoryStore(context,db).install(source("small"))}
            val before=db.workspace().documents();db.withTransaction {NativeWriteSeal.install(db.openHelper.writableDatabase)}
            try {db.workspace().delete(WebLegacyHistoryDocuments.KEY);fail()} catch(_:android.database.SQLException) {}
            assertEquals(before,db.workspace().documents());assertEquals(1,WebLegacyHistoryStore(context,db).read()!!.receipts.size)
        } finally {db.close();root.deleteRecursively()}
    }
}
