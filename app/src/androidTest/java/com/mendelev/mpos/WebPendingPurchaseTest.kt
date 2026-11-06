package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.NativeRestoreValidator
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class WebPendingPurchaseTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(keepPayment: Boolean=false): WebBackupDocument {
        val raw=JSONObject(String(webOpenBackupFixture().original!!,Charsets.UTF_8));if(!keepPayment)raw.remove("currentOrderSession")
        if(!keepPayment)raw.getJSONArray("products").getJSONObject(0).put("stock",0).put("cost",0)
        raw.put("suppliers",JSONArray().put(JSONObject().put("id","supplier").put("name","Supplier").put("productIds",JSONArray().put("p"))))
        raw.put("purchaseOrders",JSONArray().put(JSONObject().put("id","order").put("status","pending").put("timestamp",1).put("requestVersion",5).put("supplierId","supplier").put("supplierName","Supplier")
            .put("items",JSONArray().put(JSONObject().put("productId","p").put("productName","Fixture").put("requestedQty",10).put("requestedUnit","piece").put("stockUnit","piece").put("contentUnit","piece").put("expectedQty",10).put("qty",10)))))
        return WebBackupReader.read(raw.toString().byteInputStream())
    }
    @Test fun restoredPendingRequestReceivesThreeOnceAndSurvivesNativeRoundtrip()=runBlocking {
        val root=File(context.cacheDir,"web-purchase-${UUID.randomUUID()}").apply {mkdirs()}
        val path=File(root,"candidate.db").absolutePath
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,path).build()
        var db=open();val permit=AdminForms(this).permit()
        try {
            val source=source()
            permit.write {WebCandidateDatabase(context,db).install(source,emptyMap())}
            assertTrue(db.catalog().movements().isEmpty());assertNull(db.catalog().result("order"))
            db.close();db=open()
            suspend fun validate(name: String) {
                val stage=File(root,name).apply {mkdir()}
                val snapshot=NativeSnapshotDatabase.capture(db,ProductPhotoStore(File(root,"media")),stage)
                val archive=File(root,"$name.mposnative");NativeBackupArchive.write(archive,snapshot)
                val extracted=File(root,"$name-read").apply {mkdir()}
                val files=archive.inputStream().use {NativeBackupArchive.read(it,extracted)}
                NativeRestoreValidator.validate(context,files,extracted)
            }
            validate("pending")
            val order=PurchaseOrderStore(db).all().single();assertEquals(5L,order.importedOrigin!!.requestVersion);assertTrue(order.history.isEmpty())
            val owner=db.catalog().config()!!.ownerId
            val snapshot=ReceivingRepository(db).snapshot()
            val draft=ReceivingRules.open("invoice",order,snapshot.products).let {it.copy(lines=it.lines.map {l->l.copy(quantity="3",total="10")})}
            permit.write {ReceivingRepository(db).commit(ReceivingCommand("save",owner,order.id,0,draft))}
            val command=ReceivingPostCommand("post",owner,1,draft)
            val before=db.workspace().documents()
            for(boundary in 1..8) {var writes=0
                try {permit.write {ReceivingPostingRepository(db,afterWrite={if(++writes==boundary)throw java.io.IOException("fault")}).commit(command)};fail()} catch(_:java.io.IOException) {}
                assertEquals(before,db.workspace().documents());assertTrue(db.catalog().movements().isEmpty())
            }
            val posted=permit.write {ReceivingPostingRepository(db).commit(command)}
            assertEquals(posted,permit.write {ReceivingPostingRepository(db).commit(command)})
            assertEquals("received",PurchaseOrderStore(db).all().single().status)
            val product=WebIdentity.mapped("product","p")
            assertEquals("3",db.catalog().balance(product)!!.quantity);assertEquals("3.33",db.catalog().product(product)!!.cost)
            assertEquals(1,db.catalog().movements().size);validate("received")
        } finally {db.close();root.deleteRecursively()}
    }
    @Test fun receivingCannotInvalidateAnImportedPartiallyPaidCart()=runBlocking {
        val root=File(context.cacheDir,"web-purchase-lock-${UUID.randomUUID()}").apply {mkdirs()}
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"candidate.db").absolutePath).build()
        try {
            val permit=AdminForms(this).permit()
            permit.write {WebCandidateDatabase(context,db).install(source(true),emptyMap())}
            val order=PurchaseOrderStore(db).all().single();val owner=db.catalog().config()!!.ownerId
            val snapshot=ReceivingRepository(db).snapshot()
            val draft=ReceivingRules.open("invoice",order,snapshot.products).let {it.copy(lines=it.lines.map {l->l.copy(quantity="3",total="10")})}
            permit.write {ReceivingRepository(db).commit(ReceivingCommand("save",owner,order.id,0,draft))}
            val before=db.workspace().documents();val balance=db.catalog().allBalances();val products=db.catalog().allProducts()
            try {permit.write {ReceivingPostingRepository(db).commit(ReceivingPostCommand("post",owner,1,draft))};fail()} catch(e:com.mendelev.mpos.domain.catalog.CatalogFailure) {assertEquals("split",e.kind)}
            assertEquals(before,db.workspace().documents());assertEquals(balance,db.catalog().allBalances());assertEquals(products,db.catalog().allProducts())
            assertEquals(4000L,SplitPaymentRepository(db).load()!!.paidAmount);assertTrue(db.catalog().movements().isEmpty());assertNull(db.catalog().result("post"))
        } finally {db.close();root.deleteRecursively()}
    }

}
