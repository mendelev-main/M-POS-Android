package com.mendelev.mpos

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.*
import com.mendelev.mpos.domain.backup.NativeBackupArchive
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** Instrumentation-only, invoked explicitly by the emulator driver; never included in an application APK. */
object NativeRestoreProcessFixture {
    suspend fun run(mode: String): String {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".debug") && android.os.Build.HARDWARE in setOf("ranchu","goldfish")) {"Emulator debug fixture only"}
        check(!Administration.session.state.value.active) {"A fresh process must start administratively locked"}
        val session=AdminSession(PasswordVerifier {it.concatToString()=="restore-process-fixture"});Administration.session=session
        check(session.login("restore-process-fixture".toCharArray()));val p=AdminForms(CoroutineScope(currentCoroutineContext())).permit()
        val db=CatalogDatabase.get(context);val photoStore=ProductPhotoStore(context.filesDir)
        val report=File(context.filesDir,"qa/native-restore-process.json").apply {parentFile!!.mkdirs()}
        if(mode=="seed") {
            check(db.catalog().allProducts().isEmpty() && db.shifts().history().isEmpty()) {"Fixture requires a freshly installed empty debug app"}
            val source=Room.databaseBuilder(context,CatalogDatabase::class.java,"native-process-source.db").build()
            try {
                val sourcePhotos=ProductPhotoStore(File(context.noBackupFilesDir,"process-source-photos"))
                seed(source,sourcePhotos,4000,"source");seed(db,photoStore,3000,"target")
                val archive=File(context.noBackupFilesDir,"process-source.mposnative")
                p.write {NativeBackupRepository(context,source,sourcePhotos).export(Uri.fromFile(archive).toString())}
                val recovery=NativeRecoveryPreparation(context)
                val prepared=p.write {recovery.prepare(Uri.fromFile(archive).toString())}
                val sourcePhoto=source.catalog().product("process-product")!!.photoId!!
                val targetPhoto=db.catalog().product("process-product")!!.photoId!!
                val result=p.write {recovery.apply(prepared.id,true,true)}
                check(AppDataset.paths(context).id=="legacy" && AppDataset.store(context).selected().id==result.generation)
                check(photoStore.file(targetPhoto).parentFile!!.parentFile==context.filesDir)
                check(SplitPaymentRepository(db).load()!!.paidAmount==3000L)
                check(!NativeActivationStartup(context).open().ready) {"Same process must remain blocked"}
                try {db.workspace().put(db.workspace().documents().first().copy(revision=999));error("old writer admitted")} catch(_:android.database.sqlite.SQLiteException) {}
                report.writeText(JSONObject().put("origin",AppDataset.runtime.processId).put("generation",result.generation).put("protection",prepared.protection.id).put("sourcePhoto",sourcePhoto).put("photoHash",NativeBackupArchive.sha256(sourcePhotos.file(sourcePhoto))).toString())
            } finally {source.close()}
            return "NATIVE RESTORE PROCESS SEED PASSED"
        }
        check(mode=="verify");val expected=JSONObject(report.readText())
        check(AppDataset.runtime.processId!=expected.getString("origin"))
        check(AppDataset.paths(context).id==expected.getString("generation"))
        check(AppDataset.store(context).journal()==null) {"Startup must reconcile before working UI"}
        val photo=expected.getString("sourcePhoto")
        check(photoStore.file(photo).parentFile!!.parentFile==AppDataset.paths(context).mediaRoot)
        check(NativeBackupArchive.sha256(photoStore.file(photo))==expected.getString("photoHash"))
        val progress=SplitPaymentRepository(db).load()!!;check(progress.paidAmount==4000L && progress.checkout.draft.parts[0].given==5000L)
        val before=db.workspace().documents();SplitPaymentRepository(db).confirm(progress.checkout,0,0);check(before==db.workspace().documents())
        SplitPaymentRepository(db).confirm(progress.checkout,1,progress.revision)
        val id=SaleRepository(db).commit(progress.checkout);check(id==SaleRepository(db).commit(progress.checkout))
        val receipt=SaleRepository(db).receipt(id)!!;check(receipt.second.payment.parts.first().change==1000L && ShiftRepository(db).snapshot().currentCash==4000L)
        check(db.catalog().balance("process-product")!!.quantity=="9.0004")
        val protections=p.write {NativeRecoveryPreparation(context).inventory()}
        check(protections.protections.single {it.id==expected.getString("protection")}.activity.paidMinor==3000L)
        return "NATIVE RESTORE PROCESS VERIFY PASSED"
    }
    private suspend fun seed(db: CatalogDatabase,photos: ProductPhotoStore,paid: Long,label: String) {
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply {eraseColor(if(label=="source") android.graphics.Color.GREEN else android.graphics.Color.RED)}
        val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val photo=photos.prepare {bytes.inputStream()};val catalog=CatalogRepository(db,photos);catalog.initialize()
        catalog.saveProduct(ProductInput("process-product",null,"Native E2E $label",null,"100","1","10.0004",null,true,"$label-create",photoId=photo))
        val work=WorkspaceRepository(db);work.initialize();work.cart(CartAction.Add("process-product",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
        val shifts=ShiftRepository(db);shifts.initializeCashierFixture();val initial=shifts.snapshot();shifts.open("open",initial.revision,null,initial.ownerId)
        val s=shifts.snapshot();val w=work.snapshot()
        val saved=CheckoutRepository(db).save(0,CheckoutDraft("$label-partial",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,s.ownerId,parts=listOf(PaymentInput("cash",paid,5000),PaymentInput("card",10000-paid))))
        SplitPaymentRepository(db).confirm(saved,0,0)
    }
}
