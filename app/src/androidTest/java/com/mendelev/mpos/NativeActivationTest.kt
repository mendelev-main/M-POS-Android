package com.mendelev.mpos

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.*
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NativeActivationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture: java.io.Closeable {
        val sourceName="activation-source-${UUID.randomUUID()}.db";val targetName="activation-target-${UUID.randomUUID()}.db"
        val source=Room.databaseBuilder(context,CatalogDatabase::class.java,sourceName).build()
        var target=Room.databaseBuilder(context,CatalogDatabase::class.java,targetName).build()
        val root=File(context.noBackupFilesDir,"activation-test-${UUID.randomUUID()}").apply {mkdirs()}
        val sourceRoot=File(root,"source");val targetRoot=File(root,"target")
        val sourcePhotos=ProductPhotoStore(sourceRoot);val targetPhotos=ProductPhotoStore(targetRoot)
        val store=NativeGenerationStore(File(root,"switch"),context.getDatabasePath(targetName),targetRoot)
        val runtime=RecoveryRuntime();val vault=File(root,"vault");val input=File(root,"input.mposnative")
        fun repo(checkpoint: (String)->Unit={})=NativeRecoveryPreparation(context,target,targetPhotos,vault,store,runtime,checkpoint)
        fun releaseOwners()=runBlocking {File(store.root,"generations").listFiles().orEmpty().forEach {InstallationGate.release("restore:${it.name}")}}
        override fun close() {releaseOwners();source.close();target.close();context.deleteDatabase(sourceName);context.deleteDatabase(targetName);root.deleteRecursively()}
    }
    private suspend fun seed(db: CatalogDatabase,photos: ProductPhotoStore,paid: Long,label: String,history: Boolean) {
        if(history) seedReceiving(db,"activation")
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply {eraseColor(if(label=="source") android.graphics.Color.GREEN else android.graphics.Color.RED)}
        val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val photo=photos.prepare {bytes.inputStream()};val c=CatalogRepository(db,photos);c.initialize()
        c.saveProduct(ProductInput("p",null,label,null,"100","1","10.0004",null,true,"$label-product",photoId=photo))
        val w=WorkspaceRepository(db);w.initialize();var cart=w.snapshot();w.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",cart.cart.sessionId)
        val s=ShiftRepository(db);s.initializeCashierFixture();var shift=s.snapshot();s.open("open",shift.revision,null,shift.ownerId)
        if(history) {
            cart=w.snapshot();shift=s.snapshot()
            val saved=CheckoutRepository(db).save(0,CheckoutDraft("source-complete",cart.cart.sessionId,cart.cartRevision,shift.active!!.id,shift.revision,shift.ownerId,parts=listOf(PaymentInput("card",10000))))
            SaleRepository(db).commit(saved);cart=w.snapshot();w.cart(CartAction.Add("p",1,emptySet(),null,"partial"),"second-add",cart.cart.sessionId)
        }
        cart=w.snapshot();shift=s.snapshot()
        val saved=CheckoutRepository(db).save(CheckoutRepository(db).revision(),CheckoutDraft("$label-partial",cart.cart.sessionId,cart.cartRevision,shift.active!!.id,shift.revision,shift.ownerId,parts=listOf(PaymentInput("cash",paid,5000),PaymentInput("card",10000-paid))))
        SplitPaymentRepository(db).confirm(saved,0,0)
    }
    private suspend fun fixture(p: AdminAuthorization)=Fixture().also {f->
        seed(f.source,f.sourcePhotos,4000,"source",true);seed(f.target,f.targetPhotos,3000,"target",false)
        p.write {NativeBackupRepository(context,f.source,f.sourcePhotos).export(Uri.fromFile(f.input).toString())}
    }
    private suspend fun login(scope: CoroutineScope): AdminAuthorization {Administration.session.login("fixture-admin".toCharArray());return AdminForms(scope).permit()}
    private suspend fun reject(kind: String,block: suspend ()->Unit) {try {block();fail()} catch(e:BackupFailure) {assertEquals(kind,e.kind)}}
    @Test fun generationSwitchFreezesEveryOldWriterAndFreshStartupKeepsPaidPartsExactlyOnce(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())};val sourceDocs=f.source.workspace().documents();val targetDocs=f.target.workspace().documents()
            f.input.appendText("changed external file after preparation")
            val result=p.write {f.repo().apply(prepared.id,true,true)}
            assertEquals(result.generation,f.store.selected().id);assertEquals(3000L,SplitPaymentRepository(f.target).load()!!.paidAmount);assertEquals(targetDocs,f.target.workspace().documents())
            try {f.target.workspace().put(targetDocs.first().copy(revision=999));fail()} catch(_:android.database.sqlite.SQLiteException) {}
            try {CatalogRepository(f.target).initialize();fail()} catch(_:android.database.sqlite.SQLiteException) {}
            var entered=false
            try {InstallationGate.write {entered=true}} catch(_:IllegalStateException) {}
            assertFalse(entered)
            assertFalse(NativeActivationStartup(context,f.store,f.runtime).open().ready);assertNotNull(f.store.journal())
            val startup=NativeActivationStartup(context,f.store,RecoveryRuntime()).open();assertTrue(startup.notice ?: "",startup.ready);assertFalse(Administration.session.state.value.active);assertNull(f.store.journal())
            f.releaseOwners() // A real new process starts with a fresh process-wide installation mutex.
            val paths=f.store.selected();val restored=Room.databaseBuilder(context,CatalogDatabase::class.java,paths.database.absolutePath).build()
            try {
                assertEquals(sourceDocs,restored.workspace().documents());val progress=SplitPaymentRepository(restored).load()!!;assertEquals(4000L,progress.paidAmount)
                val photo=f.source.catalog().product("p")!!.photoId!!;assertArrayEquals(f.sourcePhotos.file(photo).readBytes(),ProductPhotoStore(paths.mediaRoot).file(photo).readBytes())
                val before=restored.workspace().documents();SplitPaymentRepository(restored).confirm(progress.checkout,0,0);assertEquals(before,restored.workspace().documents())
                SplitPaymentRepository(restored).confirm(progress.checkout,1,progress.revision);val id=SaleRepository(restored).commit(progress.checkout);assertEquals(id,SaleRepository(restored).commit(progress.checkout))
                assertEquals(4000L,ShiftRepository(restored).snapshot().currentCash);assertEquals("8.0004",restored.catalog().balance("p")!!.quantity)
                val permit=login(this@runBlocking);val repo=NativeRecoveryPreparation(context,restored,ProductPhotoStore(paths.mediaRoot),f.vault,f.store,RecoveryRuntime())
                val inventory=permit.write {repo.inventory()};assertTrue(inventory.protections.any {it.id==prepared.protection.id && it.activity.paidMinor==3000L})
                val rollback=permit.write {repo.prepareProtection(prepared.protection.id)};assertEquals(3000L,rollback.sourceActivity.paidMinor)
                assertTrue(NativeActivationStartup(context,f.store,RecoveryRuntime()).open().ready) // Later writes do not have to match the initial generation hash.
            } finally {restored.close()}
        }
    }
    @Test fun eachCheckpointReopensExactlyOldOrNewAndNeverAnUnreferencedGeneration(): Unit=runBlocking {
        for(boundary in listOf("sourceValidated","generationPrepared","targetRechecked","journalWritten","sealInstalled","oldFrozen","pointerPublished")) {
            val p=login(this);fixture(p).use {f->
                val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())}
                try {p.write {f.repo {if(it==boundary) throw java.io.IOException("injected")}.apply(prepared.id,true,true)};fail()} catch(_:java.io.IOException) {}
                val startup=NativeActivationStartup(context,f.store,RecoveryRuntime()).open();assertTrue("$boundary: ${startup.notice}",startup.ready)
                f.releaseOwners();val selected=f.store.selected();assertEquals(boundary=="pointerPublished",selected.id!="legacy")
                val open=Room.databaseBuilder(context,CatalogDatabase::class.java,selected.database.absolutePath).build()
                try {assertEquals(if(boundary=="pointerPublished") 4000L else 3000L,SplitPaymentRepository(open).load()!!.paidAmount)} finally {open.close()}
                assertTrue(File(f.vault,"protections/${prepared.protection.id}/backup.mposnative").isFile)
            }
        }
    }
    @Test fun staleTargetOrMissingExtraConfirmationNeverFreezesOrSwitchesAnything(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())}
            reject("activeConfirmation") {p.write {f.repo().apply(prepared.id,true,false)}}
            CatalogRepository(f.target,f.targetPhotos).saveProduct(ProductInput("new",null,"after preview",null,"1","1","1",null,true,"new-product"))
            reject("staleTarget") {p.write {f.repo().apply(prepared.id,true,true)}}
            assertNull(f.store.journal());assertNull(f.runtime.blocking.value);assertEquals("legacy",f.store.selected().id);assertEquals(0,NativeWriteSeal.count(context.getDatabasePath(f.targetName)))
            assertNotNull(f.target.catalog().product("new"));assertEquals(3000L,SplitPaymentRepository(f.target).load()!!.paidAmount)
        }
    }
    @Test fun copiedRoomHashCannotHideModifiedColumnsOrInconsistentPaidDraft(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            for(kind in listOf("schema","payment","fraction")) {
                val dir=File(f.root,"fake-$kind").apply {mkdir()};val files=NativeSnapshotDatabase.capture(f.source,f.sourcePhotos,dir)
                android.database.sqlite.SQLiteDatabase.openDatabase(files.database.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {sql->
                    if(kind=="schema") sql.execSQL("ALTER TABLE products ADD COLUMN injected TEXT")
                    else {val raw=sql.rawQuery("SELECT payload FROM workspace_documents WHERE id='split-progress'",null).use {it.moveToFirst();it.getString(0)};val j=org.json.JSONObject(raw);j.getJSONObject("checkout").getJSONArray("parts").getJSONObject(0).put("amount",if(kind=="fraction") "4000.5" else 4001);if(kind=="fraction") {val checkout=sql.rawQuery("SELECT payload FROM workspace_documents WHERE id='checkout'",null).use {it.moveToFirst();org.json.JSONObject(it.getString(0))};checkout.getJSONArray("parts").getJSONObject(0).put("amount","4000.5");sql.execSQL("UPDATE workspace_documents SET payload=? WHERE id='checkout'",arrayOf(checkout.toString()))};sql.execSQL("UPDATE workspace_documents SET payload=? WHERE id='split-progress'",arrayOf(j.toString()))}
                }
                NativeBackupArchive.write(f.input,files);val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())}
                reject("restoreValidation") {p.write {f.repo().apply(prepared.id,true,true)}}
                assertNull(f.store.journal());assertEquals("legacy",f.store.selected().id);assertEquals(3000L,SplitPaymentRepository(f.target).load()!!.paidAmount)
            }
        }
    }
    @Test fun pendingSourceWhoseSaleAlreadyCompletedInTargetCannotBeChargedAgain(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val dir=File(f.root,"same-origin").apply {mkdir()};val source=NativeSnapshotDatabase.capture(f.source,f.sourcePhotos,dir)
            f.target.close();context.deleteDatabase(f.targetName)
            source.photos.forEach {(id,file)->file.copyTo(f.targetPhotos.file(id),overwrite=true)}
            f.target=Room.databaseBuilder(context,CatalogDatabase::class.java,f.targetName).createFromFile(source.database).build()
            val progress=SplitPaymentRepository(f.target).load()!!;SplitPaymentRepository(f.target).confirm(progress.checkout,1,progress.revision);SaleRepository(f.target).commit(progress.checkout)
            val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())}
            reject("paymentProvenance") {p.write {f.repo().apply(prepared.id,true,true)}}
            assertNull(f.store.journal());assertEquals("legacy",f.store.selected().id);assertEquals(2,f.target.sales().history().size)
        }
    }
    @Test fun corruptPointerOrCommittedGenerationBlocksInsteadOfSilentlyOpeningOldData(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())};p.write {f.repo().apply(prepared.id,true,true)}
            val selected=f.store.selected()
            android.database.sqlite.SQLiteDatabase.openDatabase(selected.database.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {it.execSQL("UPDATE stock_balances SET quantity='0' WHERE productId='p'")}
            assertFalse(NativeActivationStartup(context,f.store,RecoveryRuntime()).open().ready);assertNotNull(f.store.journal());assertEquals(selected.id,f.store.selected().id)
            File(f.store.root,"active.json").writeText("{bad pointer")
            assertFalse(NativeActivationStartup(context,f.store,RecoveryRuntime()).open().ready);assertEquals(3000L,SplitPaymentRepository(f.target).load()!!.paidAmount)
        }
    }
    @Test fun legacyUnpaidCartMigratesOnlyWhenWorkspaceOpensAndKeepsNoPaymentAuthority(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val name="legacy-unpaid-${UUID.randomUUID()}.db";val legacy=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
            try {
                val c=CatalogRepository(legacy);c.initialize();c.saveProduct(ProductInput("legacy",null,"Legacy unpaid",null,"2","1","10",null,true,"legacy-create"))
                val w=WorkspaceRepository(legacy);w.initialize();w.cart(CartAction.Add("legacy",1,emptySet(),null,"legacy-line"),"legacy-add",w.snapshot().cart.sessionId)
                val current=legacy.workspace().document("current")!!;val j=org.json.JSONObject(current.payload).put("version",1).apply {remove("sessionId");remove("createdAt");remove("details")}
                legacy.workspace().put(current.copy(payload=j.toString()))
                p.write {NativeBackupRepository(context,legacy,ProductPhotoStore(File(f.root,"legacy-media"))).export(Uri.fromFile(f.input).toString())}
                val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())};p.write {f.repo().apply(prepared.id,true,true)}
                assertTrue(NativeActivationStartup(context,f.store,RecoveryRuntime()).open().ready);f.releaseOwners()
                val restored=Room.databaseBuilder(context,CatalogDatabase::class.java,f.store.selected().database.absolutePath).build()
                try {
                    assertEquals(1,WorkspaceCodec.cartVersion(restored.workspace().document("current")!!.payload))
                    WorkspaceRepository(restored).initialize();val cart=WorkspaceRepository(restored).snapshot().cart
                    assertTrue(cart.sessionId.isNotBlank());assertEquals("legacy",cart.lines.single().productId);assertNull(SplitPaymentRepository(restored).load());assertNull(CheckoutRepository(restored).load())
                } finally {restored.close()}
            } finally {legacy.close();context.deleteDatabase(name)}
        }
    }

    @Test fun rolledBackFreezeRetainsAlreadyQueuedOldCommandsInsteadOfRestoringAnOlderProtection(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())}
            try {p.write {f.repo {if(it=="journalWritten") throw java.io.IOException("before seal commit")}.apply(prepared.id,true,true)};fail()} catch(_:java.io.IOException) {}
            assertEquals(0,NativeWriteSeal.count(context.getDatabasePath(f.targetName)))
            CatalogRepository(f.target,f.targetPhotos).saveProduct(ProductInput("late",null,"Late old command",null,"1","1","1",null,true,"late-old-create"))
            val startup=NativeActivationStartup(context,f.store,RecoveryRuntime()).open();assertTrue(startup.notice ?: "",startup.ready)
            assertEquals("legacy",f.store.selected().id);assertNotNull(f.target.catalog().product("late"));assertEquals(3000L,SplitPaymentRepository(f.target).load()!!.paidAmount)
        }
    }

    @Test fun startupValidatorReadsCommittedWalAndBlocksInvalidFactsInsteadOfCheckingOnlyTheMainFile(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())}
            try {p.write {f.repo {if(it=="journalWritten") throw java.io.IOException("before commit")}.apply(prepared.id,true,true)};fail()} catch(_:java.io.IOException) {}
            f.target.openHelper.writableDatabase.query("PRAGMA wal_autocheckpoint=0").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
            f.target.openHelper.writableDatabase.execSQL("UPDATE products SET price='invalid' WHERE id='p'")
            assertFalse(NativeActivationStartup(context,f.store,RecoveryRuntime()).open().ready)
            assertNotNull(f.store.journal());assertEquals("legacy",f.store.selected().id)
        }
    }

    @Test fun oldSavedFormsCannotCrossGenerationButRotationKeepsTheSameInput() {
        val old=android.os.Bundle().apply {putString(DatasetSavedState.KEY,"legacy");putString("checkout-session","old-form")}
        assertSame(old,DatasetSavedState.filter(old,"legacy"))
        assertNull(DatasetSavedState.filter(old,"00000000-0000-0000-0000-000000000001"))
        assertNull(DatasetSavedState.filter(old,null))
        val upgraded=android.os.Bundle().apply {putString("checkout-session","pre-generation-version")}
        assertSame(upgraded,DatasetSavedState.filter(upgraded,"legacy"))
        assertNull(DatasetSavedState.filter(upgraded,"00000000-0000-0000-0000-000000000001"))
    }

    @Test fun missingPointerAfterSuccessfulRecoveryDoesNotReopenTheOldFrozenRegister(): Unit=runBlocking {
        val p=AdminForms(this).permit();fixture(p).use {f->
            val prepared=p.write {f.repo().prepare(Uri.fromFile(f.input).toString())};p.write {f.repo().apply(prepared.id,true,true)}
            assertTrue(NativeActivationStartup(context,f.store,RecoveryRuntime()).open().ready);assertNull(f.store.journal())
            val current=f.store.selected();assertTrue(File(f.store.root,"active.json").delete())
            assertFalse(NativeActivationStartup(context,f.store,RecoveryRuntime()).open().ready)
            assertTrue(current.database.isFile);assertTrue(File(f.vault,"protections/${prepared.protection.id}/backup.mposnative").isFile)
            assertEquals(3000L,SplitPaymentRepository(f.target).load()!!.paidAmount)
        }
    }

}
