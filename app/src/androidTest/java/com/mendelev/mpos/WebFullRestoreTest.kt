package com.mendelev.mpos

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.NativeRestoreValidator
import com.mendelev.mpos.domain.backup.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.*

/** All writes use private fixture DB/media, never the selected application dataset or device secrets. */
class WebFullRestoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun png(): ByteArray {
        val b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        return try {ByteArrayOutputStream().also {b.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()} finally {b.recycle()}
    }
    private fun root()=JSONObject("""{
      "version":1,"products":[{"id":"p","name":"Товар","type":"simple","stockUnit":"piece","stock":10,"cost":2,"price":10,"localImageId":"photo"}],
      "employees":[{"id":"e","name":"Кассир","role":"employee"}],
      "shifts":[{"id":"s","employeeId":"e","employeeName":"Кассир","status":"closed","openedAt":1,"closedAt":1000,"openingCash":0,"countedCash":0}],
      "orders":[{"id":"r","shiftId":"s","employeeId":"e","employeeName":"Кассир","timestamp":100,"total":10,"method":"card","payments":[{"method":"card","amount":10}],"items":[{"productId":"p","name":"Товар тогда","qty":1,"price":10,"cost":2}],"returnedAt":200,"returnedShiftId":"s","returnAmount":10}],
      "suppliers":[{"id":"supplier","name":"Поставка","productIds":["p"]}],
      "discounts":[{"id":"d","name":"Скидка","type":"fixed","value":1}],
      "deliveryRates":[{"id":"t","name":"Город","amount":5}],
      "receiptLabelTemplates":[{"id":"label","name":"На вечер"}],
      "company":{"establishmentName":"Тест","legalName":"ООО Тест","address":"Адрес","deliveryAddress":"Доставка"},
      "theme":"dark","network":{},"telegram":{}
    }""")
    @Test fun candidateIsAtomicAndRestoresAllRequestedDataWithoutReplayingSaleOrRefund()=runBlocking {
        val dir=File(context.cacheDir,"full-web-${UUID.randomUUID()}").apply {mkdirs()}
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,File(dir,"fixture.db").absolutePath).build()
        var staged: File?=null
        try {
            val j=root().put("productImages",JSONObject().put("photo",Base64.getEncoder().encodeToString(png())))
            val source=WebBackupReader.read(j.toString().byteInputStream());val permit=AdminForms(this).permit()
            val photos=permit.write {WebPhotoPreparation(context).prepare(source)};staged=photos.mediaRoot.parentFile
            var writes=0
            try {permit.write {WebCandidateDatabase(context,db) {if(++writes==8) error("fixture write fault")}.install(source,photos.files)};fail()} catch(_:IllegalStateException) {}
            assertNull(db.catalog().config());assertTrue(db.workspace().documents().isEmpty())
            permit.write {WebCandidateDatabase(context,db).install(source,photos.files)}
            WebCandidateValidation.initial(db,source)
            assertEquals(3,SettingsRepository(db).snapshot().entries.size)
            assertEquals("dark",WebSettingsMapping.decode(db.workspace().document(WebSettingsMapping.DEVICE)!!.payload).getString("theme"))
            assertEquals("ООО Тест",JSONObject(db.workspace().document(WebSettingsMapping.COMPANY)!!.payload).getString("legalName"))
            assertEquals("10",db.catalog().balance(WebIdentity.mapped("product","p"))!!.quantity)
            assertNotNull(db.catalog().allProducts().single().photoId)
            assertEquals(1,EmployeeRepository(db).snapshot().employees.size)
            assertEquals(1,SupplierCodec.decode(db.workspace().document(SupplierCodec.KEY)!!.payload).size)
            assertEquals(1,db.shifts().history().size)
            val page=ReceiptHistoryPaging(db).page()
            assertEquals(1,page.web.size);assertTrue(page.web.single().returned);assertFalse(page.more)
            val dashboard=ShiftRepository(db).dashboard()
            assertEquals(1,dashboard.history.single().count);assertEquals(1000.toBigInteger(),dashboard.history.single().sales)
            assertEquals(1000.toBigInteger(),dashboard.history.single().refunds)
            assertTrue(db.sales().history().isEmpty());assertTrue(db.refunds().history().isEmpty());assertTrue(db.catalog().movements().isEmpty())
            val captured=NativeSnapshotDatabase.capture(db,ProductPhotoStore(photos.mediaRoot),File(dir,"snapshot").apply {mkdir()})
            NativeRestoreValidator.validate(context,captured,File(dir,"validation").apply {mkdir()})
            val zip=File(dir,"roundtrip.mposnative");NativeBackupArchive.write(zip,captured)
            val reopened=NativeBackupArchive.read(zip.inputStream(),File(dir,"reopen").apply {mkdir()})
            NativeRestoreValidator.validate(context,reopened,File(dir,"validate-again").apply {mkdir()})
            assertEquals(captured.photos.keys,reopened.photos.keys)
        } finally {db.close();dir.deleteRecursively();staged?.deleteRecursively()}
    }
    @Test fun externalPhotoIsStagedWithInjectedBytesAndNeverSilentlyDiscarded()=runBlocking {
        val j=root();j.put("orders",JSONArray());j.put("shifts",JSONArray())
        j.getJSONArray("products").getJSONObject(0).remove("localImageId")
        j.getJSONArray("products").getJSONObject(0).put("imageUrl","https://fixture.example.org/photo.png")
        val source=WebBackupReader.read(j.toString().byteInputStream());val permit=AdminForms(this).permit()
        var calls=0;var staged: File?=null
        try {
            val result=permit.write {WebPhotoPreparation(context,externalReader={calls++;png()}).prepare(source)}
            staged=result.mediaRoot.parentFile
            assertEquals(1,calls);assertNotNull(result.files["external:0"])
            assertTrue(ProductPhotoStore(result.mediaRoot).file(result.files.getValue("external:0")).isFile)
            try {permit.write {WebPhotoPreparation(context,externalReader={error("fixture unavailable")}).prepare(source)};fail()} catch(_:IllegalStateException) {}
        } finally {staged?.deleteRecursively()}
    }
}
