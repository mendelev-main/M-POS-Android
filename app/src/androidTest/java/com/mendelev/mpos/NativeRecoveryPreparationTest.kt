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
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NativeRecoveryPreparationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture: java.io.Closeable {
        val sourceName="recovery-source-${UUID.randomUUID()}.db";val targetName="recovery-target-${UUID.randomUUID()}.db"
        val source=Room.databaseBuilder(context,CatalogDatabase::class.java,sourceName).build()
        val target=Room.databaseBuilder(context,CatalogDatabase::class.java,targetName).build()
        val root=File(context.noBackupFilesDir,"recovery-test-${UUID.randomUUID()}").apply {mkdirs()}
        val sourcePhotos=ProductPhotoStore(File(root,"source"));val targetPhotos=ProductPhotoStore(File(root,"target"))
        val vault=File(root,"vault");val input=File(root,"input.mposnative")
        fun repo(checkpoint: (String)->Unit={})=NativeRecoveryPreparation(context,target,targetPhotos,vault,checkpoint)
        override fun close() {source.close();target.close();context.deleteDatabase(sourceName);context.deleteDatabase(targetName);root.deleteRecursively()}
    }
    private suspend fun seed(db: CatalogDatabase,photos: ProductPhotoStore,paid: Long,label: String) {
        val b=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().also {b.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();b.recycle()
        val photo=photos.prepare {bytes.inputStream()}
        val c=CatalogRepository(db,photos);c.initialize();c.saveProduct(ProductInput("p",null,label,null,"100","1","10.0004",null,true,"product",photoId=photo))
        val w=WorkspaceRepository(db);w.initialize();w.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",w.snapshot().cart.sessionId)
        val shifts=ShiftRepository(db);shifts.initializeCashierFixture();val before=shifts.snapshot();shifts.open("open",0,null,before.ownerId)
        val s=shifts.snapshot();val cart=w.snapshot()
        val checkout=CheckoutRepository(db).save(0,CheckoutDraft("sale",cart.cart.sessionId,cart.cartRevision,s.active!!.id,s.revision,s.ownerId,parts=listOf(PaymentInput("cash",paid,5000),PaymentInput("card",10000-paid))))
        SplitPaymentRepository(db).confirm(checkout,0,0)
    }
    private suspend fun fixture(p: AdminAuthorization)=Fixture().also {
        seed(it.source,it.sourcePhotos,4000,"source");seed(it.target,it.targetPhotos,3000,"target")
        p.write {NativeBackupRepository(context,it.source,it.sourcePhotos).export(Uri.fromFile(it.input).toString())}
    }
    @Test fun activeTarget30AndSource40AreDurableSeparateSnapshotsAcrossReopen(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val before=f.target.workspace().documents();val shift=ShiftRepository(f.target).snapshot();val balance=f.target.catalog().balance("p");val original=f.input.readBytes()
            val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())}
            assertEquals(4000L,prepared.sourceActivity.paidMinor);assertEquals(3000L,prepared.protection.activity.paidMinor)
            assertEquals(1,prepared.sourceActivity.openShifts);assertEquals(1,prepared.protection.activity.openShifts)
            val fresh=p.write {f.repo().inventory()};assertEquals(listOf(prepared),fresh.prepared);assertEquals(listOf(prepared.protection),fresh.protections);assertEquals(0,fresh.damaged)
            assertArrayEquals(original,f.input.readBytes());assertEquals(before,f.target.workspace().documents());assertEquals(shift,ShiftRepository(f.target).snapshot());assertEquals(balance,f.target.catalog().balance("p"))
            val dir=File(f.root,"extract").apply {mkdir()}
            val archive=File(f.vault,"protections/${prepared.protection.id}/backup.mposnative")
            val files=archive.inputStream().use {NativeBackupArchive.read(it,dir)}
            val cloneName="recovery-clone-${UUID.randomUUID()}.db";val clone=Room.databaseBuilder(context,CatalogDatabase::class.java,cloneName).createFromFile(files.database).build()
            try {
                assertEquals(before,clone.workspace().documents());assertEquals(3000L,SplitPaymentRepository(clone).load()!!.paidAmount)
                val old=SplitPaymentRepository(clone).load()!!.checkout
                assertEquals(SplitPaymentRepository(f.target).confirm(old,0,0),SplitPaymentRepository(clone).confirm(old,0,0));assertEquals(before,clone.workspace().documents())
            } finally {clone.close();context.deleteDatabase(cloneName)}
            // An uncommitted directory is not a completed protection and never activates itself.
            File(f.vault,".stage-${UUID.randomUUID()}").apply {mkdir();archive.copyTo(File(this,"backup.mposnative"))}
            assertEquals(1,p.write {f.repo().inventory()}.protections.size);assertEquals(before,f.target.workspace().documents())
        }
    }
    @Test fun faultsOnEveryPublicationBoundaryPreserveOldCopiesAndWorkingDataset(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val before=f.target.workspace().documents();val shift=ShiftRepository(f.target).snapshot();var published=0;var preparations=0
            for(boundary in listOf("sourceVerified","protectionCaptured","protectionArchived","protectionVerified","protectionRecorded","protectionPublished","targetProtected","preparationRecorded","preparationPublished")) {
                val previous=p.write {f.repo().inventory()}.protections
                try {p.write {f.repo {if(it==boundary) throw java.io.IOException("injected")}.prepare(Uri.fromFile(f.input).toString())};fail()} catch(_:java.io.IOException) {}
                if(boundary in listOf("protectionPublished","targetProtected","preparationRecorded","preparationPublished")) published++
                if(boundary=="preparationPublished") preparations++
                val inventory=p.write {f.repo().inventory()}
                assertEquals(boundary,published,inventory.protections.size);assertEquals(boundary,preparations,inventory.prepared.size);assertEquals(0,inventory.damaged)
                previous.forEach {assertTrue(inventory.protections.contains(it))}
                assertEquals(before,f.target.workspace().documents());assertEquals(shift,ShiftRepository(f.target).snapshot())
                assertTrue(f.vault.listFiles().orEmpty().none {it.name.startsWith(".stage-")})
            }
        }
    }
    @Test fun malformedInputAndCorruptPublishedFilesAreNotAppliedOrSilentlyDeleted(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val original=f.input.readBytes();val before=f.target.workspace().documents()
            f.input.writeText("not a native archive")
            try {p.write {f.repo().prepare(Uri.fromFile(f.input).toString())};fail()} catch(_:Exception) {}
            assertTrue(p.write {f.repo().inventory()}.protections.isEmpty())
            f.input.writeBytes(original);val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())}
            val protection=File(f.vault,"protections/${prepared.protection.id}/backup.mposnative");protection.appendText("tamper")
            val damaged=p.write {f.repo().inventory()};assertEquals(2,damaged.damaged);assertTrue(damaged.protections.isEmpty());assertTrue(damaged.prepared.isEmpty());assertTrue(protection.exists())
            assertEquals(before,f.target.workspace().documents());assertEquals(3000L,SplitPaymentRepository(f.target).load()!!.paidAmount)
        }
    }
    @Test fun cancellationAndRevokedAuthorityCannotConfirmPreparation(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val before=f.target.workspace().documents()
            try {p.write {f.repo {if(it=="targetProtected") throw CancellationException("cancel")}.prepare(Uri.fromFile(f.input).toString())};fail()} catch(_:CancellationException) {}
            val copies=p.write {f.repo().inventory()};assertEquals(1,copies.protections.size);assertTrue(copies.prepared.isEmpty())
            Administration.session.logout()
            try {p.write {f.repo().inventory()};fail()} catch(e:CatalogFailure) {assertEquals("access",e.kind)}
            Administration.session.login("fixture-admin".toCharArray());val next=AdminForms(this).permit()
            assertEquals(copies,next.write {f.repo().inventory()});assertEquals(before,f.target.workspace().documents())
            try {f.repo().prepare(Uri.fromFile(f.input).toString());fail()} catch(e:CatalogFailure) {assertEquals("access",e.kind)}
        }
    }
    @Test fun targetFingerprintIgnoresPageLayoutAndTimeButDetectsBusinessChange(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val first=File(f.root,"first").apply {mkdir()};val second=File(f.root,"second").apply {mkdir()}
            val a=NativeSnapshotDatabase.capture(f.target,f.targetPhotos,first);val b=NativeSnapshotDatabase.capture(f.target,f.targetPhotos,second)
            val expected=NativeSnapshotDatabase.fingerprint(a);assertEquals(expected,NativeSnapshotDatabase.fingerprint(b))
            android.database.sqlite.SQLiteDatabase.openDatabase(b.database.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {it.execSQL("VACUUM")}
            assertEquals(expected,NativeSnapshotDatabase.fingerprint(NativeBackupFiles(b.database,b.photos,b.createdAt+1000)))
            android.database.sqlite.SQLiteDatabase.openDatabase(b.database.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {it.execSQL("UPDATE stock_balances SET quantity='10.0005',revision=revision+1 WHERE productId='p'")}
            assertNotEquals(expected,NativeSnapshotDatabase.fingerprint(b))
            android.database.sqlite.SQLiteDatabase.openDatabase(b.database.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {it.execSQL("UPDATE workspace_documents SET payload='{\"version\":999}' WHERE id='split-progress'")}
            assertNull(NativeSnapshotDatabase.activity(b).paidMinor);assertNull(NativeSnapshotDatabase.activity(b).partials)
        }
    }
}
