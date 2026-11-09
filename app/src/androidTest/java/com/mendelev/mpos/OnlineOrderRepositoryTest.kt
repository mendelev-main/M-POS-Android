package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OnlineOrderRepositoryTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val target="a".repeat(64)
    private suspend fun seed(db: CatalogDatabase) {
        val repo=CatalogRepository(db);repo.initialize()
        repo.saveProduct(ProductInput("p",null,"Кофе",null,"12","3","10",null,true,"create"))
        WorkspaceRepository(db).initialize()
    }
    private fun order(id: String="order-1",total: String="20.00")=JSONObject().put("id",id).put("external_id","87").put("status","new").put("order_type","На месте")
        .put("total",total).put("delivery_fee","0").put("order_items",JSONArray().put(JSONObject().put("external_product_id","p").put("product_name","Кофе").put("quantity","2").put("price","10.00")))
    @Test fun repeatedAcceptCreatesOneParkedOrderAndDurableIntentAtWebsitePrice()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val repo=OnlineOrderRepository(db)
            val first=repo.accept(order(),target,"fixture","15m")
            assertEquals(first,repo.accept(order(),target,"fixture","15m"))
            val snapshot=WorkspaceRepository(db).snapshot()
            assertEquals(1,snapshot.parked.size);assertEquals(1,repo.records().size)
            assertEquals(first.sessionId,snapshot.parked.single().id)
            assertEquals("10",snapshot.parked.single().cart.lines.single().unitPrice)
            assertEquals("10",snapshot.parked.single().cart.lines.single().quotedPrice)
            assertEquals("10",db.catalog().balance("p")!!.quantity)
            assertFalse(first.accepted)
        } finally {db.close()}
    }
    @Test fun failureAfterParkWriteRollsBackBothOrderAndAcknowledgementIntent()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val repo=OnlineOrderRepository(db) {error("fixture write failure")}
            try {repo.accept(order(),target,"fixture","15m");fail()} catch(_:IllegalStateException) {}
            assertTrue(WorkspaceRepository(db).snapshot().parked.isEmpty())
            assertTrue(OnlineOrderRepository(db).records().isEmpty())
        } finally {db.close()}
    }
    @Test fun totalMismatchAndInsufficientStockDoNotCreateOrder()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val repo=OnlineOrderRepository(db)
            try {repo.accept(order(total="21"),target,"fixture","15m");fail()} catch(_:IllegalArgumentException) {}
            repeat(5) {repo.accept(order("order-$it"),target,"fixture","15m")}
            try {repo.accept(order("order-overflow"),target,"fixture","15m");fail()} catch(_:IllegalArgumentException) {}
            assertEquals(5,WorkspaceRepository(db).snapshot().parked.size)
        } finally {db.close()}
    }
    @Test fun quotedCartCodecRoundTripAndReadyBeforeAcceptAreSafe()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val repo=OnlineOrderRepository(db);val record=repo.accept(order(),target,"fixture","at:18:30")
            val cart=WorkspaceRepository(db).snapshot().parked.single().cart
            assertEquals(cart,WorkspaceCodec.cart(WorkspaceCodec.cart(cart)))
            try {repo.markReady(record.sessionId);fail()} catch(_:IllegalStateException) {}
            repo.update(record.sessionId) {it.copy(accepted=true)}
            repo.markReady(record.sessionId)
            assertTrue(repo.records().single().ready);assertFalse(repo.records().single().readyConfirmed)
        } finally {db.close()}
    }
    @Test fun cancellationBlocksPaymentUntilServerAcknowledgementAndReleasesOnlyReservation()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val repo=OnlineOrderRepository(db);val record=repo.accept(order(),target,"fixture","15m")
            repo.update(record.sessionId) {it.copy(accepted=true)}
            repo.requirePayable(record.sessionId)
            repo.cancel(record.sessionId)
            try {repo.requirePayable(record.sessionId);fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
            assertEquals(1,WorkspaceRepository(db).snapshot().parked.size)
            repo.finishCancel(record.sessionId)
            assertTrue(WorkspaceRepository(db).snapshot().parked.isEmpty())
            assertEquals("10",db.catalog().balance("p")!!.quantity)
            assertTrue(repo.record(record.sessionId)!!.cancelled)
            assertEquals(record.sessionId,repo.accept(order(),target,"fixture","15m").sessionId)
            assertTrue(WorkspaceRepository(db).snapshot().parked.isEmpty())
        } finally {db.close()}
    }
    @Test fun onlineCheckoutUsesWebsiteTotalAndCommitsOneReceiptWithPaidMarker()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val online=OnlineOrderRepository(db);val record=online.accept(order(),target,"fixture","15m")
            val work=WorkspaceRepository(db);val s=work.snapshot();val parked=s.parked.single()
            work.resume(parked.id,parked.revision,s.cartRevision,s.cart.sessionId,"resume")
            val shifts=ShiftRepository(db);shifts.initializeCashierFixture();val initial=shifts.snapshot()
            shifts.open("open",initial.revision,null,initial.ownerId)
            val shift=shifts.snapshot();val current=work.snapshot()
            val draft=CheckoutDraft("online-sale",current.cart.sessionId,current.cartRevision,shift.active!!.id,shift.revision,shift.ownerId,
                parts=listOf(com.mendelev.mpos.domain.sale.PaymentInput("cash",2000,2000)))
            try {CheckoutRepository(db).save(0,draft);fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
            online.update(record.sessionId) {it.copy(accepted=true)}
            val saved=CheckoutRepository(db).save(0,draft)
            val sales=SaleRepository(db);val id=sales.commit(saved)
            assertEquals(id,sales.commit(saved));assertEquals(2000L,sales.receipt(id)!!.first.total)
            assertEquals("8",db.catalog().balance("p")!!.quantity)
            assertTrue(online.record(record.sessionId)!!.paid)
            online.update(record.sessionId) {it.copy(settled=true)}
            assertEquals(1,online.records().size) // Still visible for marking ready after payment.
            online.markReady(record.sessionId);online.update(record.sessionId) {it.copy(readyConfirmed=true)}
            assertTrue(online.records().isEmpty())
        } finally {db.close()}
    }
}
