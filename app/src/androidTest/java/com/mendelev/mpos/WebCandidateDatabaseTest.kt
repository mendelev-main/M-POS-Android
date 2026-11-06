package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.NativeRestoreValidator
import com.mendelev.mpos.domain.backup.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

internal fun webOpenBackupFixture(): WebBackupDocument=WebBackupReader.read("""{
      "version":1,"products":[{"id":"p","name":"Fixture","type":"simple","price":100,"cost":20,"stock":10,"stockUnit":"piece"}],
      "employees":[{"id":"e","name":"Fixture cashier","role":"employee"}],
      "shifts":[{"id":"s","employeeId":"e","employeeName":"Fixture cashier","status":"open","openedAt":1,"openingCash":0}],"orders":[],
      "currentOrderSession":{"createdAt":2,"orderType":"На месте","items":[{"productId":"p","name":"Fixture","price":100,"qty":1}],
        "paymentDraft":{"version":1,"updatedAt":3,"totalCents":10000,"parts":[{"method":"cash","amount":40,"paid":true,"cashGiven":50,"change":10},{"method":"card","amount":60,"paid":false}]}}
    }""".byteInputStream())

class WebCandidateDatabaseTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun source()=webOpenBackupFixture()
    @Test fun candidateReopensNativeRemainderCommitsOnceAndBothStatesRestore()=runBlocking {
        val root=File(context.cacheDir,"web-candidate-${UUID.randomUUID()}").apply {mkdirs()}
        val path=File(root,"candidate.db").absolutePath
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,path).build()
        var db=open();val permit=AdminForms(this).permit()
        try {
            val source=source();permit.write {WebCandidateDatabase(context,db).install(source,emptyMap())}
            db.close();db=open()
            val split=SplitPaymentRepository(db);val paid=split.load()!!;val product=WebIdentity.mapped("product","p")
            assertEquals(3,paid.format);assertEquals(4000L,paid.paidAmount);assertEquals(4000L,paid.pendingCash)
            assertNull(db.catalog().result(paid.importedOrigin!!));assertTrue(db.catalog().movements().isEmpty());assertTrue(db.sales().history().isEmpty())
            assertNull(WorkspaceRepository(db).snapshot().cart.lines.single().basePrice)
            suspend fun validate(name: String) {
                val stage=File(root,name).apply {mkdir()}
                val files=NativeSnapshotDatabase.capture(db,ProductPhotoStore(File(root,"media")),stage)
                NativeRestoreValidator.validate(context,files,stage)
            }
            validate("before")
            val docs=db.workspace().documents()
            repeat(3) {i->var n=0
                try {SplitPaymentRepository(db) {if(++n==i+1)error("fault")}.confirmPart(paid.checkout,2,paid.checkout.draft.parts[1],paid.revision);fail()} catch(_:IllegalStateException) {}
                assertEquals(docs,db.workspace().documents())
            }
            val confirmation=split.confirmPart(paid.checkout,2,paid.checkout.draft.parts[1],paid.revision)
            assertEquals(confirmation,split.confirmPart(paid.checkout,2,paid.checkout.draft.parts[1],paid.revision))
            for(boundary in 1..10) {var writes=0
                try {SaleRepository(db,afterWrite={if(++writes==boundary)error("fault")}).commit(paid.checkout);fail()} catch(_:IllegalStateException) {}
                assertTrue(db.sales().history().isEmpty());assertEquals("10",db.catalog().balance(product)!!.quantity)
            }
            val receipt=SaleRepository(db).commit(paid.checkout)
            assertEquals(receipt,SaleRepository(db).commit(paid.checkout));assertEquals(confirmation,split.confirmPart(paid.checkout,2,paid.checkout.draft.parts[1],paid.revision))
            assertEquals("9",db.catalog().balance(product)!!.quantity);assertEquals(4000L,ShiftRepository(db).snapshot().currentCash)
            assertEquals(1000L,SaleRepository(db).receipt(receipt)!!.second.payment.parts[0].change)
            assertEquals(1,db.sales().history().size);assertNull(split.load());validate("after")
            assertArrayEquals(source.original,WebSourceDocuments.bytes(db.workspace().documents().associate {it.id to it.payload}))
        } finally {db.close();root.deleteRecursively()}
    }
    @Test fun everyCandidateWriteFailureRollsBackAndDoesNotInitializeEmptyCashRegister()=runBlocking {
        val root=File(context.cacheDir,"web-candidate-${UUID.randomUUID()}").apply {mkdirs()}
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"candidate.db").absolutePath).build()
        try {
            val permit=AdminForms(this).permit();val source=source()
            var boundaries=0
            permit.write {WebCandidateDatabase(context,db) {boundaries++}.install(source,emptyMap())}
            assertTrue(boundaries>15)
            for(boundary in 1..boundaries) {
                val candidate=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"fault-$boundary.db").absolutePath).build()
                try {
                    var count=0
                    try {permit.write {WebCandidateDatabase(context,candidate) {if(++count==boundary)throw java.io.IOException("injected")}.install(source,emptyMap())};fail("write boundary $boundary did not fail")} catch(_:java.io.IOException) {}
                    assertEquals(boundary,count)
                    assertNull(candidate.catalog().config())
                    assertTrue(candidate.catalog().allProducts().isEmpty())
                    assertTrue(candidate.catalog().allBalances().isEmpty())
                    assertTrue(candidate.catalog().movements().isEmpty())
                    assertTrue(candidate.workspace().documents().isEmpty())
                    assertTrue(candidate.shifts().history().isEmpty())
                    assertNull(candidate.shifts().register())
                } finally {candidate.close()}
            }
            val before=db.workspace().documents()
            try {permit.write {WebCandidateDatabase(context,db).install(source,emptyMap())};fail()} catch(_:BackupFailure) {}
            assertEquals(before,db.workspace().documents())
        } finally {db.close();root.deleteRecursively()}
    }
}
