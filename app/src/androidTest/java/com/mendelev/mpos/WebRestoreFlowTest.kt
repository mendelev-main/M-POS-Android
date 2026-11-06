package com.mendelev.mpos

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

class WebRestoreFlowTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun webFileProtectsNative30SwitchesToSource40AndCompletesOnly60()=runBlocking {
        val root=File(context.noBackupFilesDir,"web-flow-${UUID.randomUUID()}").apply {mkdirs()}
        val targetPath=File(root,"target.db").absolutePath
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,targetPath).build()
        val photos=ProductPhotoStore(File(root,"media"));val store=NativeGenerationStore(File(root,"switch"),File(targetPath),File(root,"media"))
        fun release()=runBlocking {File(store.root,"generations").listFiles().orEmpty().forEach {InstallationGate.release("restore:${it.name}")}}
        try {
            val c=CatalogRepository(db);c.initialize();c.saveProduct(ProductInput("target",null,"Target",null,"100","1","10",null,true,"target-product"))
            val w=WorkspaceRepository(db);w.initialize();w.cart(CartAction.Add("target",1,emptySet(),null,"target-line"),"target-add",w.snapshot().cart.sessionId)
            val shifts=ShiftRepository(db);shifts.initializeCashierFixture();val shiftBefore=shifts.snapshot();shifts.open("target-open",shiftBefore.revision,null,shiftBefore.ownerId)
            val work=w.snapshot();val shift=shifts.snapshot()
            val saved=CheckoutRepository(db).save(0,CheckoutDraft("target-sale",work.cart.sessionId,work.cartRevision,shift.active!!.id,shift.revision,shift.ownerId,parts=listOf(PaymentInput("cash",3000,5000),PaymentInput("card",7000))))
            SplitPaymentRepository(db).confirm(saved,0,0)
            val before=db.workspace().documents()
            val runtime=RecoveryRuntime();val recovery=NativeRecoveryPreparation(context,db,photos,File(root,"vault"),store,runtime)
            val adapter=WebRestorePreparation(context,recovery);val source=webOpenBackupFixture();val file=File(root,"source.json").apply {writeBytes(source.original!!)}
            val p=AdminForms(this).permit()
            val preview=p.write {adapter.inspect(Uri.fromFile(file).toString())}
            assertEquals(before,db.workspace().documents());assertEquals("legacy",store.selected().id)
            try {p.write {adapter.prepare(preview,false)};fail()} catch(e:BackupFailure) {assertEquals("unsupportedConfirmation",e.kind)}
            val prepared=p.write {adapter.prepare(preview,true)}
            assertEquals(3000L,prepared.protection.activity.paidMinor);assertEquals(4000L,prepared.sourceActivity.paidMinor)
            try {p.write {recovery.apply(prepared.id,true,false)};fail()} catch(e:BackupFailure) {assertEquals("activeConfirmation",e.kind)}
            assertEquals("legacy",store.selected().id)
            p.write {recovery.apply(prepared.id,true,true)}
            assertFalse(NativeActivationStartup(context,store,runtime).open().ready)
            assertTrue(NativeActivationStartup(context,store,RecoveryRuntime()).open().ready)
            assertFalse(Administration.session.state.value.active);release()
            val selected=store.selected();val restored=Room.databaseBuilder(context,CatalogDatabase::class.java,selected.database.absolutePath).build()
            try {
                val split=SplitPaymentRepository(restored);val progress=split.load()!!
                assertEquals(4000L,progress.paidAmount);assertEquals(6000L,progress.checkout.draft.parts[1].amount)
                val confirmed=split.confirmPart(progress.checkout,2,progress.checkout.draft.parts[1],progress.revision)
                val receipt=SaleRepository(restored).commit(progress.checkout)
                assertEquals(receipt,SaleRepository(restored).commit(progress.checkout));assertEquals(confirmed,split.confirmPart(progress.checkout,2,progress.checkout.draft.parts[1],progress.revision))
                assertEquals(4000L,ShiftRepository(restored).snapshot().currentCash)
                assertEquals("9",restored.catalog().balance(WebIdentity.mapped("product","p"))!!.quantity)
                assertNull(restored.catalog().product("target"));assertEquals(1,restored.sales().history().size)
                assertArrayEquals(source.original,WebSourceDocuments.bytes(restored.workspace().documents().associate {it.id to it.payload}))
            } finally {restored.close()}
            assertEquals(3000L,SplitPaymentRepository(db).load()!!.paidAmount)
            assertTrue(File(root,"vault/protections/${prepared.protection.id}/backup.mposnative").isFile)
        } finally {release();db.close();root.deleteRecursively()}
    }
}
