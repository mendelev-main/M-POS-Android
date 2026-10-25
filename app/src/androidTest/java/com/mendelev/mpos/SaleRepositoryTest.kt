package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SaleRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    private suspend fun seed(db: CatalogDatabase): SavedCheckout {
        val catalog=CatalogRepository(db);catalog.initialize();catalog.saveCategory("c",null,"Категория","category")
        catalog.saveProduct(ProductInput("p",null,"Чай","c","2","1","10",null,true,"product"))
        val work=WorkspaceRepository(db);work.initialize()
        work.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
        val shifts=ShiftRepository(db);shifts.initialize();val initial=shifts.snapshot();shifts.open("open",initial.revision,null,initial.ownerId)
        val s=shifts.snapshot();val w=work.snapshot()
        return CheckoutRepository(db).save(0,CheckoutDraft("sale",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,s.ownerId,parts=listOf(PaymentInput("cash",200,500))))
    }
    @Test fun atomicSaleReplayReopenHistoricalSnapshotAndClosePreview(): Unit=runBlocking {
        val name="sale-${UUID.randomUUID()}.db";fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val draft=seed(db);val before=ShiftRepository(db).snapshot();val sale=SaleRepository(db)
            val id=sale.commit(draft);assertEquals(id,sale.commit(draft));assertEquals(id,sale.outcome("sale")!!.id)
            assertEquals("9",db.catalog().balance("p")!!.quantity);assertEquals(2,db.catalog().movementCount("p"))
            assertTrue(WorkspaceRepository(db).snapshot().cart.lines.isEmpty());assertNull(CheckoutRepository(db).load())
            val receipt=sale.receipt(id)!!;assertEquals(1L,receipt.first.number);assertEquals(200L,receipt.second.quote.total)
            assertEquals(300L,receipt.second.payment.parts.single().change);assertEquals(mapOf("p" to "1"),receipt.second.consumption)
            val after=ShiftRepository(db).snapshot();assertEquals(200L,after.currentCash)
            reject {ShiftRepository(db).close("stale-close",before.active!!.id,before.revision,ShiftRules.token(before),200,before.ownerId)}
            val product=db.catalog().product("p")!!
            db.catalog().updateProduct(product.copy(name="Новое имя",price="99",cost="8",revision=2))
            assertEquals(receipt,sale.receipt(id));reject {CatalogRepository(db).deleteProduct("p",2,"delete")}
            reject {sale.commit(draft.copy(draft=draft.draft.copy(parts=listOf(PaymentInput("card",200)))))}
            ShiftRepository(db).close("close",after.active!!.id,after.revision,ShiftRules.token(after),190,after.ownerId)
            assertEquals(200L,ShiftRepository(db).snapshot().last!!.expectedCash);assertEquals(-10L,ShiftRepository(db).snapshot().last!!.difference)
            db.close();db=open()
            assertEquals(receipt,SaleRepository(db).receipt(id));assertEquals(id,SaleRepository(db).commit(draft));assertEquals(1,SaleRepository(db).history().size)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun eachWriteFailureAndSqlRefusalLeaveNoPartialSaleThenConcurrentRetryWritesOnce(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val draft=seed(db);val work=WorkspaceRepository(db).snapshot();val shift=ShiftRepository(db).snapshot();val balance=db.catalog().balance("p")
            // Receipt, reference, balance, movement, register, current, checkout, result, audit.
            for(boundary in 1..9) {
                var writes=0
                try {SaleRepository(db,afterWrite={if(++writes==boundary) throw java.io.IOException("write")}).commit(draft);fail()} catch(_:java.io.IOException) {}
                assertEquals(work,WorkspaceRepository(db).snapshot());assertEquals(shift,ShiftRepository(db).snapshot())
                assertEquals(balance,db.catalog().balance("p"));assertEquals(draft,CheckoutRepository(db).load())
                assertNull(db.catalog().result("sale"));assertTrue(db.sales().history().isEmpty());assertEquals(1,db.catalog().movementCount("p"))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER refuse_sale BEFORE INSERT ON receipts BEGIN SELECT RAISE(ABORT, 'injected'); END")
            try {SaleRepository(db).commit(draft);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(work,WorkspaceRepository(db).snapshot());assertNull(db.catalog().result("sale"))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_sale")
            val ids=coroutineScope {(1..2).map {async(Dispatchers.IO) {SaleRepository(db).commit(draft)}}.awaitAll()}
            assertEquals(1,ids.distinct().size);assertEquals(1,db.sales().history().size);assertEquals("9",db.catalog().balance("p")!!.quantity)
        } finally {db.close()}
    }
    @Test fun stockChangesAndClosedShiftCannotBeSilentlyPaid(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val draft=seed(db);val balance=db.catalog().balance("p")!!
            db.catalog().updateBalance(balance.copy(quantity="0.0004",revision=2))
            reject {SaleRepository(db).commit(draft)};assertEquals(draft,CheckoutRepository(db).load())
            db.catalog().updateBalance(balance)
            val s=ShiftRepository(db).snapshot();ShiftRepository(db).close("close",s.active!!.id,s.revision,ShiftRules.token(s),0,s.ownerId)
            reject {SaleRepository(db).commit(draft)};assertTrue(db.sales().history().isEmpty())
        } finally {db.close()}
    }
    @Test fun exactRecipeFlagsDeliveryCashAndHistoricalReferences(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            for(id in listOf("tracked","negative","untracked")) catalog.saveProduct(ProductInput(id,null,id,null,"1","3",if(id=="tracked") "0.001" else "0",null,true,"create-$id",
                unit="kg",noStockTracking=id=="untracked",allowNegativeStock=id=="negative"))
            catalog.saveProduct(ProductInput("dish",null,"Блюдо",null,"20","0","0",null,false,"dish",type="composite",
                components=listOf("tracked","negative","untracked").map {IngredientInput(it,"0.0004","kg")}))
            val work=WorkspaceRepository(db);work.initialize();val shifts=ShiftRepository(db);shifts.initialize();val initial=shifts.snapshot();shifts.open("open",0,null,initial.ownerId)
            suspend fun add(key: String) {work.cart(CartAction.Add("dish",1,emptySet(),null,"line-$key"),key,work.snapshot().cart.sessionId)}
            add("parked");val parked=work.snapshot();work.park(parked.cartRevision,parked.cart.sessionId,"Резерв","park")
            add("current");var w=work.snapshot()
            work.cart(CartAction.SetDetails(OrderDetails(type="delivery"),w.cartRevision),"details",w.cart.sessionId)
            w=work.snapshot();val shift=shifts.snapshot()
            val d=CheckoutDraft("recipe-sale",w.cart.sessionId,w.cartRevision,shift.active!!.id,shift.revision,shift.ownerId,
                delivery=DeliveryCharge("courier","Курьер",500,1),parts=listOf(PaymentInput("card",2500)))
            val repo=CheckoutRepository(db);var saved=repo.save(0,d)
            reject {SaleRepository(db).commit(saved)} // Card cannot fund the courier in an empty drawer.
            saved=repo.save(saved.revision,d.copy(parts=listOf(PaymentInput("cash",1000,1500),PaymentInput("card",1500))))
            val baseline=db.catalog().balance("tracked")!!
            db.catalog().updateBalance(baseline.copy(quantity="0.0007",revision=baseline.revision+1))
            reject {SaleRepository(db).commit(saved)} // 0.0004 current + 0.0004 parked.
            db.catalog().updateBalance(baseline)
            val id=SaleRepository(db).commit(saved);val receipt=SaleRepository(db).receipt(id)!!.second
            assertEquals(mapOf("tracked" to "0.0004","negative" to "0.0004"),receipt.consumption)
            assertEquals("0.0006",db.catalog().balance("tracked")!!.quantity)
            assertEquals("-0.0004",db.catalog().balance("negative")!!.quantity)
            assertEquals("0",db.catalog().balance("untracked")!!.quantity)
            assertEquals(500L,shifts.snapshot().currentCash);assertEquals(500L,receipt.payment.deliveryCashOut)
            assertEquals(1,work.snapshot().parked.size)
            // Even the composite and untracked ingredients have historical FK references.
            assertEquals(1,db.sales().productUses("dish"));assertEquals(1,db.sales().productUses("untracked"))
            reject {catalog.deleteProduct("dish",1,"delete-dish")}
        } finally {db.close()}
    }
    @Test fun migrationSixToSevenPreservesShiftDraftAndExactStock(): Unit=runBlocking {
        val name="sale-migration-${UUID.randomUUID()}.db"
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.mendelev.mpos.data.local.CatalogDatabase/6.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
        val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try {
            val es=schema.getJSONArray("entities")
            for(i in 0 until es.length()) {val e=es.getJSONObject(i);val table=e.getString("tableName");old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));e.optJSONArray("indices")?.let {ix->for(k in 0 until ix.length()) old.execSQL(ix.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}",table))}}
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO app_config VALUES(1,'r','owner','BYN')")
            old.execSQL("INSERT INTO products(id,name,searchKey,price,cost,revision,currency,unit,type) VALUES('p','Чай','чай','2','1',1,'BYN','piece','simple')")
            old.execSQL("INSERT INTO stock_balances VALUES('p','0.0004',8)")
            old.execSQL("INSERT INTO shifts VALUES('s','r','owner','Владелец',1,'UTC',14000,NULL,NULL,NULL,NULL,NULL,1)")
            old.execSQL("INSERT INTO shift_register VALUES(1,9,'s')")
            old.execSQL("INSERT INTO workspace_documents VALUES('checkout',3,'{\"version\":1,\"discarded\":true}')");old.version=6
        } finally {old.close()}
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(CatalogDatabase.MIGRATION_6_7).build()
        try {
            assertEquals("0.0004",db.catalog().balance("p")!!.quantity);assertEquals(14000L,ShiftRepository(db).snapshot().currentCash)
            assertEquals(3L,CheckoutRepository(db).revision());assertNull(CheckoutRepository(db).load());assertTrue(db.sales().history().isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
}
