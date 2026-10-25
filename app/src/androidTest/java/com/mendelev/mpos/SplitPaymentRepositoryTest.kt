package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.core.operations.InstallationGate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SplitPaymentRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    private suspend fun seed(db: CatalogDatabase,price: String="100",parts: List<PaymentInput> = listOf(PaymentInput("cash",4000,5000),PaymentInput("card",6000))): SavedCheckout {
        val c=CatalogRepository(db);c.initialize();c.saveCategory("c",null,"Категория","category")
        c.saveProduct(ProductInput("p",null,"Заказ","c",price,"1","10",null,true,"product"))
        val w=WorkspaceRepository(db);w.initialize();w.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",w.snapshot().cart.sessionId)
        val s=ShiftRepository(db);s.initialize();val before=s.snapshot();s.open("open",0,null,before.ownerId)
        val active=s.snapshot();val cart=w.snapshot()
        return CheckoutRepository(db).save(0,CheckoutDraft("sale",cart.cart.sessionId,cart.cartRevision,active.active!!.id,active.revision,active.ownerId,
            parts=parts))
    }
    @Test fun partialReopensFreezesWritersAndCompletesExactlyOnce(): Unit=runBlocking {
        val name="split-${UUID.randomUUID()}.db";fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val saved=seed(db);var split=SplitPaymentRepository(db)
            val part=split.confirm(saved,0,0);assertEquals(part,split.confirm(saved,0,0))
            assertEquals(setOf(0),split.load()!!.paid);assertEquals(4000L,split.load()!!.paidAmount);assertEquals(4000L,split.load()!!.pendingCash)
            assertEquals(0L,ShiftRepository(db).snapshot().currentCash);assertEquals("10",db.catalog().balance("p")!!.quantity);assertTrue(db.sales().history().isEmpty())
            val work=WorkspaceRepository(db);val cart=work.snapshot();val state=ShiftRepository(db).snapshot()
            reject {work.cart(CartAction.Clear(cart.cartRevision),"clear",cart.cart.sessionId)}
            reject {work.cart(CartAction.Step("line",1),"increase",cart.cart.sessionId)}
            reject {work.cart(CartAction.SetDetails(OrderDetails(label="Изменён"),cart.cartRevision),"details",cart.cart.sessionId)}
            reject {work.park(cart.cartRevision,cart.cart.sessionId,"Отложить","park")}
            reject {CheckoutRepository(db).discard(saved.revision)}
            reject {CheckoutRepository(db).save(saved.revision,saved.draft)}
            reject {ShiftRepository(db).close("close",state.active!!.id,state.revision,ShiftRules.token(state),0,state.ownerId)}
            reject {SaleRepository(db).commit(saved)}
            reject {InstallationGate.prepare("split-install") {split.requireInstallAllowed()}}
            reject {CatalogRepository(db).saveProduct(ProductInput("p",1,"Изменён","c","99","1","10",null,false,"edit"))}
            reject {CatalogRepository(db).saveCategory("c",1,"Другая","category-edit")}
            assertEquals(cart,work.snapshot());assertEquals(saved,CheckoutRepository(db).load())
            db.close();db=open();split=SplitPaymentRepository(db)
            assertEquals(saved,split.load()!!.checkout);assertEquals(setOf(0),split.load()!!.paid)
            reject {split.confirm(saved,1,0)}
            val second=split.confirm(saved,1,1);assertTrue(split.load()!!.complete)
            val receipt=SaleRepository(db).commit(saved);assertNull(split.load());assertEquals(3L,split.revision())
            assertEquals(receipt,SaleRepository(db).commit(saved));assertEquals(second,split.confirm(saved,1,1));assertEquals(part,split.confirm(saved,0,0))
            assertEquals("9",db.catalog().balance("p")!!.quantity);assertEquals(4000L,ShiftRepository(db).snapshot().currentCash)
            val r=SaleRepository(db).receipt(receipt)!!.second
            assertEquals(1000L,r.payment.parts[0].change);assertEquals(6000L,r.payment.card);assertEquals(1,db.sales().history().size)
            split.requireInstallAllowed()
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun progressWriteFaultsAndConcurrentRetryCannotPublishFalseConfirmation(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val saved=seed(db);val documents=db.workspace().documents()
            for(boundary in 1..3) {
                var writes=0
                try {SplitPaymentRepository(db) {if(++writes==boundary) throw java.io.IOException("disk")}.confirm(saved,0,0);fail()} catch(_:java.io.IOException) {}
                assertEquals(documents,db.workspace().documents());assertNull(SplitPaymentRepository(db).load())
                assertEquals(0,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE action='sale.part.confirmed'").use {it.count})
            }
            db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER refuse_split BEFORE INSERT ON workspace_documents WHEN NEW.id='split-progress' BEGIN SELECT RAISE(ABORT, 'injected'); END")
            try {SplitPaymentRepository(db).confirm(saved,0,0);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(documents,db.workspace().documents());assertNull(SplitPaymentRepository(db).load())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_split")
            val keys=coroutineScope {(1..2).map {async(Dispatchers.IO) {SplitPaymentRepository(db).confirm(saved,0,0)}}.awaitAll()}
            assertEquals(1,keys.distinct().size);assertEquals(1L,SplitPaymentRepository(db).revision());assertEquals(setOf(0),SplitPaymentRepository(db).load()!!.paid)
            reject {SplitPaymentRepository(db).confirm(saved.copy(draft=saved.draft.copy(parts=listOf(PaymentInput("cash",5000,5000),PaymentInput("card",5000)))),0,1)}
            SplitPaymentRepository(db).confirm(saved,1,1)
            val progress=SplitPaymentRepository(db).load();val before=db.workspace().documents()
            // Extra final-sale boundary consumes progress in the same transaction as the receipt.
            for(boundary in 1..10) {
                var writes=0
                try {SaleRepository(db,afterWrite={if(++writes==boundary) throw java.io.IOException("disk")}).commit(saved);fail()} catch(_:java.io.IOException) {}
                assertEquals(progress,SplitPaymentRepository(db).load());assertEquals(before,db.workspace().documents());assertTrue(db.sales().history().isEmpty())
                assertEquals("10",db.catalog().balance("p")!!.quantity)
            }
            SaleRepository(db).commit(saved);assertNull(SplitPaymentRepository(db).load())
        } finally {db.close()}
    }
    @Test fun zeroValuePartsRemainFinishableAndFirstPartRacesCartClearSafely(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val saved=seed(db,"0.01",listOf(PaymentInput("cash",1,1),PaymentInput("card",0),PaymentInput("card",0)))
            val split=SplitPaymentRepository(db)
            split.confirm(saved,0,0);split.confirm(saved,1,1);split.confirm(saved,2,2)
            assertEquals(1L,split.load()!!.paidAmount);assertTrue(split.load()!!.complete)
            SaleRepository(db).commit(saved);assertEquals(1L,ShiftRepository(db).snapshot().currentCash)
        } finally {db.close()}
        val race=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val saved=seed(race);val cart=WorkspaceRepository(race).snapshot()
            val outcomes=coroutineScope {listOf(
                async(Dispatchers.IO) {runCatching {SplitPaymentRepository(race).confirm(saved,0,0)}},
                async(Dispatchers.IO) {runCatching {WorkspaceRepository(race).cart(CartAction.Clear(cart.cartRevision),"clear",cart.cart.sessionId)}}
            ).awaitAll()}
            assertEquals(1,outcomes.count {it.isSuccess})
            if(outcomes[0].isSuccess) {
                assertEquals(setOf(0),SplitPaymentRepository(race).load()!!.paid)
                assertEquals(cart.cart,WorkspaceRepository(race).snapshot().cart)
            } else {
                assertNull(SplitPaymentRepository(race).load());assertTrue(WorkspaceRepository(race).snapshot().cart.lines.isEmpty())
            }
            assertEquals("10",race.catalog().balance("p")!!.quantity);assertTrue(race.sales().history().isEmpty())
        } finally {race.close()}
    }

}
