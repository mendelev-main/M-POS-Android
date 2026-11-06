package com.mendelev.mpos

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NativeBackupTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun seed(db: CatalogDatabase,photos: ProductPhotoStore): String {
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val photo=photos.prepare {bytes.inputStream()}
        seedReceiving(db,"backup")
        val c=CatalogRepository(db,photos);c.initialize();c.saveCategory("c",null,"Категория","category")
        c.saveProduct(ProductInput("p",null,"Заказ","c","100","1","10.0004",null,true,"product",photoId=photo))
        val draftPhoto=photos.prepare {bytes.inputStream()}
        c.saveDraft(DraftEntity(payload=com.mendelev.mpos.feature.products.ProductDraft(name="private-fixture",photoId=draftPhoto,dirty=true).json()))
        val w=WorkspaceRepository(db);w.initialize();w.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",w.snapshot().cart.sessionId)
        val s=ShiftRepository(db);s.initializeCashierFixture();val before=s.snapshot();s.open("open",0,null,before.ownerId)
        val active=s.snapshot();val initialCart=w.snapshot()
        val completed=CheckoutRepository(db).save(0,CheckoutDraft("completed-sale",initialCart.cart.sessionId,initialCart.cartRevision,active.active!!.id,active.revision,active.ownerId,parts=listOf(PaymentInput("card",10000))))
        SaleRepository(db).commit(completed)
        val afterSale=w.snapshot();w.cart(CartAction.Add("p",1,emptySet(),null,"partial-line"),"partial-add",afterSale.cart.sessionId)
        val cart=w.snapshot();val currentShift=s.snapshot()
        val checkout=CheckoutRepository(db).save(CheckoutRepository(db).revision(),CheckoutDraft("sale",cart.cart.sessionId,cart.cartRevision,currentShift.active!!.id,currentShift.revision,currentShift.ownerId,parts=listOf(PaymentInput("cash",4000,5000),PaymentInput("card",6000))))
        SplitPaymentRepository(db).confirm(checkout,0,0)
        return photo
    }
    private fun rows(db: CatalogDatabase): Map<String,List<List<String?>>> {
        val raw=db.openHelper.readableDatabase;val names=mutableListOf<String>()
        raw.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use {c->while(c.moveToNext()) names+=c.getString(0)}
        return names.associateWith {name->raw.query("SELECT * FROM "+name).use {c->
            val rows=mutableListOf<List<String?>>()
            while(c.moveToNext()) rows+=(0 until c.columnCount).map {i->if(c.isNull(i)) null else c.getType(i).toString()+":"+c.getString(i)}
            rows.sortedBy {it.toString()}
        }}
    }
    @Test fun exactSnapshotOfWalActiveShiftPaidPartsCommandsAndPhotosCanReopen(): Unit=runBlocking {
        val name="native-backup-${UUID.randomUUID()}.db";val restoredName="native-reopen-${UUID.randomUUID()}.db"
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        val root=File(context.cacheDir,"backup-test-${UUID.randomUUID()}").apply {mkdir()};val photos=ProductPhotoStore(root)
        var restored: CatalogDatabase?=null
        try {
            val id=seed(db,photos);val rows=rows(db);val work=db.workspace().documents();val shift=ShiftRepository(db).snapshot();val balance=db.catalog().balance("p");val paid=SplitPaymentRepository(db).load()
            val output=File(root,"copy.mposnative");val access=NativeBackupRepository(context,db,photos);val permit=AdminForms(this).permit()
            val report=permit.write {access.export(Uri.fromFile(output).toString())}
            assertEquals(2L,report.products);assertEquals(2,report.photos);assertFalse(report.toString().contains("private-fixture"))
            assertEquals(report,permit.write {access.inspectNative(Uri.fromFile(output).toString())})
            val extract=File(root,"read").apply {mkdir()};val files=output.inputStream().use {NativeBackupArchive.read(it,extract)}
            assertArrayEquals(photos.file(id).readBytes(),files.photos.getValue(id).readBytes())
            restored=Room.databaseBuilder(context,CatalogDatabase::class.java,restoredName).createFromFile(files.database).build()
            val copy=restored!!
            assertEquals(rows,rows(copy));assertEquals(work,copy.workspace().documents());assertEquals(shift,ShiftRepository(copy).snapshot());assertEquals(balance,copy.catalog().balance("p"));assertEquals(paid,SplitPaymentRepository(copy).load())
            assertEquals(4000L,SplitPaymentRepository(copy).load()!!.paidAmount);assertEquals(1000L,SplitPaymentRepository(copy).load()!!.checkout.draft.parts.first().let {it.given!!-it.amount})
            assertEquals(db.catalog().draft(),copy.catalog().draft())
            val draftPhoto=com.mendelev.mpos.feature.products.ProductDraft.parse(copy.catalog().draft()!!.payload).photoId!!
            assertArrayEquals(photos.file(draftPhoto).readBytes(),files.photos.getValue(draftPhoto).readBytes())
            // Original command identity/result survives: no second accepted part or stock/cash mutation.
            val checkout=paid!!.checkout;val replay=SplitPaymentRepository(copy).confirm(checkout,0,0)
            assertEquals(replay,SplitPaymentRepository(db).confirm(checkout,0,0));assertEquals(work,copy.workspace().documents())
            assertEquals(work,db.workspace().documents());assertEquals(shift,ShiftRepository(db).snapshot());assertEquals(balance,db.catalog().balance("p"))
            assertTrue(File(context.cacheDir,"native-backup-staging").listFiles().orEmpty().isEmpty())
        } finally {restored?.close();db.close();context.deleteDatabase(name);context.deleteDatabase(restoredName);root.deleteRecursively()}
    }
    @Test fun faultsNeverChangeLiveDatasetAndMissingPhotoRefusesExport(): Unit=runBlocking {
        val name="native-fault-${UUID.randomUUID()}.db";val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        val root=File(context.cacheDir,"backup-test-${UUID.randomUUID()}").apply {mkdir()};val photos=ProductPhotoStore(root)
        try {
            val id=seed(db,photos);val before=db.workspace().documents();val balance=db.catalog().balance("p");val permit=AdminForms(this).permit()
            for(boundary in listOf("captured","archived","verified","written")) {
                val access=NativeBackupRepository(context,db,photos) {if(it==boundary) throw java.io.IOException("injected")}
                try {permit.write {access.export(Uri.fromFile(File(root,"fault-$boundary")).toString())};fail()} catch(_:java.io.IOException) {}
                assertEquals(before,db.workspace().documents());assertEquals(balance,db.catalog().balance("p"));assertTrue(File(context.cacheDir,"native-backup-staging").listFiles().orEmpty().isEmpty())
            }
            val corrupt=File(root,"provider-corrupt")
            try {permit.write {NativeBackupRepository(context,db,photos) {if(it=="written") corrupt.appendText("corrupt")}.export(Uri.fromFile(corrupt).toString())};fail()} catch(e:BackupFailure) {assertEquals("checksum",e.kind)}
            try {permit.write {NativeBackupRepository(context,db,photos) {if(it=="archived") throw CancellationException("cancel")}.export(Uri.fromFile(File(root,"cancelled")).toString())};fail()} catch(_:CancellationException) {}
            assertFalse(File(root,"cancelled").exists());assertEquals(before,db.workspace().documents())
            photos.file(id).delete();val output=File(root,"missing")
            try {permit.write {NativeBackupRepository(context,db,photos).export(Uri.fromFile(output).toString())};fail()} catch(_:CatalogFailure) {}
            assertFalse(output.exists());assertEquals(before,db.workspace().documents())
        } finally {db.close();context.deleteDatabase(name);root.deleteRecursively()}
    }
    @Test fun authCancellationUnknownAndTamperedArchiveDoNotTouchSource(): Unit=runBlocking {
        val name="native-auth-${UUID.randomUUID()}.db";val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        val root=File(context.cacheDir,"backup-test-${UUID.randomUUID()}").apply {mkdir()};val photos=ProductPhotoStore(root)
        try {
            seed(db,photos);val access=NativeBackupRepository(context,db,photos);val output=File(root,"copy");val uri=Uri.fromFile(output).toString();val permit=AdminForms(this).permit()
            try {access.export(uri);fail()} catch(e:CatalogFailure) {assertEquals("access",e.kind)}
            permit.write {access.export(uri)};val original=output.readBytes();val before=db.workspace().documents()
            val broken=original.copyOf().also {it[60]=(it[60].toInt() xor 127).toByte()};output.writeBytes(broken)
            try {permit.write {access.inspectNative(uri)};fail()} catch(_:Exception) {}
            assertArrayEquals(broken,output.readBytes());assertEquals(before,db.workspace().documents())
            Administration.session.logout();try {permit.write {access.export(uri)};fail()} catch(e:CatalogFailure) {assertEquals("access",e.kind)}
            assertEquals(before,db.workspace().documents())
        } finally {db.close();context.deleteDatabase(name);root.deleteRecursively()}
    }
}
