package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RefundRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun memory()=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    private suspend fun sale(db: CatalogDatabase,delivery: Boolean=false,card: Boolean=false,untracked: Boolean=false): ReceiptEntity {
        val catalog=CatalogRepository(db);catalog.initialize()
        catalog.saveProduct(ProductInput("p",null,"Товар",null,"20","1","10",null,true,"create",unit="kg",noStockTracking=untracked))
        val work=WorkspaceRepository(db);work.initialize()
        val shifts=ShiftRepository(db);shifts.initializeCashierFixture();val initial=shifts.snapshot()
        shifts.open("open",initial.revision,null,initial.ownerId)
        // Funding sale is unnecessary: cash delivery funds its own courier expense.
        work.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
        if(delivery) {val w=work.snapshot();work.cart(CartAction.SetDetails(OrderDetails(type="delivery"),w.cartRevision),"delivery",w.cart.sessionId)}
        val w=work.snapshot();val s=shifts.snapshot();val total=if(delivery) 2500L else 2000L
        val parts=if(card) listOf(PaymentInput("cash",1000,1500),PaymentInput("card",total-1000)) else listOf(PaymentInput("cash",total,total+500))
        val saved=CheckoutRepository(db).save(0,CheckoutDraft("sale",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,s.ownerId,
            delivery=if(delivery) DeliveryCharge("courier","Курьер",500,1) else null,parts=parts))
        return db.sales().receipt(SaleRepository(db).commit(saved))!!
    }
    @Test fun deliveryRefundIsAtomicHistoricalAndReplaySurvivesCloseAndReopen(): Unit=runBlocking {
        val name="refund-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val receipt=sale(db,true);val s=ShiftRepository(db).snapshot()
            assertEquals(2000L,s.currentCash)
            val p=db.catalog().product("p")!!
            db.catalog().updateProduct(p.copy(price="99",noStockTracking=true,revision=2))
            val repo=RefundRepository(db);val preview=repo.preview(receipt.id,s.ownerId)
            assertEquals(-2000L,preview.amounts.cashDelta)
            val id=repo.commit("refund",preview)
            assertEquals(id,repo.commit("refund",preview));assertEquals(id,repo.outcome("refund")!!.id)
            assertEquals(receipt,db.sales().receipt(receipt.id));assertEquals("10",db.catalog().balance("p")!!.quantity)
            assertEquals(3,db.catalog().movementCount("p"));assertEquals(0L,ShiftRepository(db).snapshot().currentCash)
            reject {repo.commit("other",preview)}
            reject {ShiftRepository(db).close("stale",s.active!!.id,s.revision,ShiftRules.token(s),0,s.ownerId)}
            val now=ShiftRepository(db).snapshot()
            ShiftRepository(db).close("close",now.active!!.id,now.revision,ShiftRules.token(now),0,now.ownerId)
            db.close();db=open()
            assertEquals(id,RefundRepository(db).commit("refund",preview));assertEquals(1,db.refunds().history().size)
            assertEquals(receipt,db.sales().receipt(receipt.id));assertEquals(0L,ShiftRepository(db).snapshot().currentCash)
            reject {RefundRepository(db).commit("refund",preview.copy(actorId="intruder"))}
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun crossShiftMixedRefundChangesOnlyTodaysDrawer(): Unit=runBlocking {
        val db=memory()
        try {
            val receipt=sale(db,true,true);val shifts=ShiftRepository(db);val old=shifts.snapshot()
            assertEquals(500L,old.currentCash)
            shifts.close("close",old.active!!.id,old.revision,ShiftRules.token(old),500,old.ownerId)
            val closed=db.shifts().shift(old.active!!.id)!!;val state=shifts.snapshot()
            shifts.open("today",state.revision,state.last!!.id,state.ownerId)
            val preview=RefundRepository(db).preview(receipt.id,state.ownerId)
            assertEquals(1000L,preview.amounts.cash);assertEquals(1500L,preview.amounts.card)
            assertEquals(500L,preview.amounts.deliveryCashCredit)
            RefundRepository(db).commit("refund",preview)
            assertEquals(0L,shifts.snapshot().currentCash);assertEquals(closed,db.shifts().shift(closed.id))
            assertEquals(receipt,db.sales().receipt(receipt.id))
        } finally {db.close()}
    }
    @Test fun everyWriteAndSqlAbortRollBackThenConcurrentCommandsHaveOneWinner(): Unit=runBlocking {
        val db=memory()
        try {
            val receipt=sale(db);val shifts=ShiftRepository(db);val s=shifts.snapshot()
            val repo=RefundRepository(db);val preview=repo.preview(receipt.id,s.ownerId);val balance=db.catalog().balance("p")
            // refund, balance, movement, register, result, audit.
            for(boundary in 1..6) {
                var writes=0
                try {RefundRepository(db,afterWrite={if(++writes==boundary) throw java.io.IOException("write")}).commit("refund",preview);fail()}
                catch(_:java.io.IOException) {}
                assertEquals(balance,db.catalog().balance("p"));assertEquals(s,shifts.snapshot())
                assertTrue(db.refunds().history().isEmpty());assertNull(db.catalog().result("refund"))
                assertEquals(2,db.catalog().movementCount("p"));assertEquals(receipt,db.sales().receipt(receipt.id))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER refuse_refund BEFORE INSERT ON audit_events WHEN NEW.action='sale.refund' BEGIN SELECT RAISE(ABORT, 'injected'); END")
            try {repo.commit("refund",preview);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(balance,db.catalog().balance("p"));assertEquals(s,shifts.snapshot());assertTrue(db.refunds().history().isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_refund")
            val results=coroutineScope {listOf("refund","different").map {async(Dispatchers.IO) {runCatching {repo.commit(it,preview)}}}.awaitAll()}
            assertEquals(1,results.count {it.isSuccess});assertEquals(1,db.refunds().history().size)
            val winner=db.refunds().history().single()
            val retries=coroutineScope {(1..2).map {async(Dispatchers.IO) {repo.commit(winner.commandId,preview)}}.awaitAll()}
            assertEquals(listOf(winner.id,winner.id),retries);assertEquals("10",db.catalog().balance("p")!!.quantity)
        } finally {db.close()}
    }
    @Test fun staleStockCorruptSnapshotClockOwnerAndInstallerRefuseWithoutWrites(): Unit=runBlocking {
        val db=memory()
        try {
            val receipt=sale(db);val repo=RefundRepository(db);val owner=ShiftRepository(db).snapshot().ownerId
            val preview=repo.preview(receipt.id,owner);val b=db.catalog().balance("p")!!
            db.catalog().updateBalance(b.copy(revision=b.revision+1))
            reject {repo.commit("stale",preview)};db.catalog().updateBalance(b)
            reject {repo.preview(receipt.id,"intruder")}
            reject {repo.commit("tampered",preview.copy(amounts=preview.amounts.copy(cash=0)))}
            reject {RefundRepository(db,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC)).commit("clock",preview)}
            InstallationGate.prepare("refund-test") {Unit}
            try {repo.commit("install",preview);fail()} catch(_:IllegalStateException) {} finally {InstallationGate.release("refund-test")}
            val payload=JSONObject(receipt.payload).put("cash",1).toString()
            db.openHelper.writableDatabase.execSQL("UPDATE receipts SET payload=? WHERE id=?",arrayOf(payload,receipt.id))
            reject {repo.preview(receipt.id,owner)}
            db.openHelper.writableDatabase.execSQL("UPDATE receipts SET payload=? WHERE id=?",arrayOf(receipt.payload,receipt.id))
            assertTrue(db.refunds().history().isEmpty());assertEquals(b,db.catalog().balance("p"))
        } finally {db.close()}
    }
    @Test fun refundCloseRaceHasOneWinnerAndUntrackedReceiptDoesNotCreateStock(): Unit=runBlocking {
        val db=memory()
        try {
            val receipt=sale(db,untracked=true);val shifts=ShiftRepository(db);val s=shifts.snapshot()
            val repo=RefundRepository(db);val preview=repo.preview(receipt.id,s.ownerId);assertTrue(preview.stock.isEmpty())
            val results=coroutineScope {listOf(
                async(Dispatchers.IO) {runCatching {repo.commit("refund",preview)}},
                async(Dispatchers.IO) {runCatching {shifts.close("close",s.active!!.id,s.revision,ShiftRules.token(s),2000,s.ownerId)}}
            ).awaitAll()}
            assertEquals(1,results.count {it.isSuccess});assertEquals("10",db.catalog().balance("p")!!.quantity)
            assertEquals(1,db.catalog().movementCount("p"))
            if(results[0].isSuccess) assertEquals(0L,shifts.snapshot().currentCash) else assertTrue(db.refunds().history().isEmpty())
        } finally {db.close()}
    }
    @Test fun exactHistoricalIngredientSurvivesRecipeAndTrackingChanges(): Unit=runBlocking {
        val db=memory()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            catalog.saveProduct(ProductInput("ingredient",null,"Ингредиент",null,"1","1","10",null,true,"ingredient",unit="kg"))
            catalog.saveProduct(ProductInput("dish",null,"Блюдо",null,"20","0","0",null,false,"dish",type="composite",
                components=listOf(IngredientInput("ingredient","0.0004","kg"))))
            val work=WorkspaceRepository(db);work.initialize();val shifts=ShiftRepository(db);shifts.initializeCashierFixture()
            val initial=shifts.snapshot();shifts.open("open",initial.revision,null,initial.ownerId)
            work.cart(CartAction.Add("dish",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
            val w=work.snapshot();val state=shifts.snapshot()
            val saved=CheckoutRepository(db).save(0,CheckoutDraft("sale",w.cart.sessionId,w.cartRevision,state.active!!.id,state.revision,state.ownerId,
                parts=listOf(PaymentInput("cash",2000,2000))))
            val receipt=SaleRepository(db).commit(saved)
            assertEquals("9.9996",db.catalog().balance("ingredient")!!.quantity)
            db.catalog().clearRecipe("dish")
            db.catalog().insertComponents(listOf(RecipeComponentEntity("dish",0,"ingredient","5","kg")))
            val ingredient=db.catalog().product("ingredient")!!
            db.catalog().updateProduct(ingredient.copy(noStockTracking=true,revision=2))
            val repo=RefundRepository(db);val preview=repo.preview(receipt,state.ownerId)
            assertEquals("0.0004",preview.stock.single().delta)
            repo.commit("refund",preview)
            assertEquals("10",db.catalog().balance("ingredient")!!.quantity)
            assertEquals("5",db.catalog().recipe("dish").single().quantity)
        } finally {db.close()}
    }
    @Test fun activeSplitBlocksRefundAndSaleRaceInvalidatesOtherPreview(): Unit=runBlocking {
        for(partial in listOf(true,false)) {
            val db=memory()
            try {
                val receipt=sale(db);val repo=RefundRepository(db);val shifts=ShiftRepository(db);val state=shifts.snapshot()
                val preview=repo.preview(receipt.id,state.ownerId)
                val work=WorkspaceRepository(db)
                work.cart(CartAction.Add("p",1,emptySet(),null,"next-line"),"next",work.snapshot().cart.sessionId)
                val w=work.snapshot();val checkout=CheckoutRepository(db)
                val saved=checkout.save(checkout.revision(),CheckoutDraft("next-sale",w.cart.sessionId,w.cartRevision,state.active!!.id,state.revision,state.ownerId,
                    parts=listOf(PaymentInput("cash",1000,1000),PaymentInput("card",1000))))
                if(partial) {
                    SplitPaymentRepository(db).confirm(saved,0,0)
                    reject {repo.preview(receipt.id,state.ownerId)}
                    reject {repo.commit("refund",preview)}
                    assertTrue(db.refunds().history().isEmpty());assertEquals("9",db.catalog().balance("p")!!.quantity)
                } else {
                    val results=coroutineScope {listOf(
                        async(Dispatchers.IO) {runCatching {repo.commit("refund",preview)}},
                        async(Dispatchers.IO) {runCatching {SaleRepository(db).commit(saved)}}
                    ).awaitAll()}
                    assertEquals(1,results.count {it.isSuccess})
                    assertEquals(state.revision+1,shifts.snapshot().revision)
                    if(results[0].isSuccess) {
                        assertEquals("10",db.catalog().balance("p")!!.quantity);assertEquals(0L,shifts.snapshot().currentCash)
                    } else {
                        assertTrue(db.refunds().history().isEmpty());assertEquals("8",db.catalog().balance("p")!!.quantity)
                        assertEquals(3000L,shifts.snapshot().currentCash)
                    }
                }
            } finally {db.close()}
        }
    }
    @Test fun allHistoricalSchemasMigrateWithoutChangingStockOrExistingReceipt(): Unit=runBlocking {
        val source=memory();val receipt=try {sale(source)} finally {source.close()}
        for(version in 1..7) {
            val name="refund-migration-$version-${UUID.randomUUID()}.db"
            val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.mendelev.mpos.data.local.CatalogDatabase/$version.json")
                .bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
            val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
            try {
                val entities=schema.getJSONArray("entities")
                for(i in 0 until entities.length()) {
                    val e=entities.getJSONObject(i);val table=e.getString("tableName")
                    old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                    e.optJSONArray("indices")?.let {ix->for(k in 0 until ix.length()) old.execSQL(ix.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}",table))}
                }
                val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("INSERT INTO app_config VALUES(1,'r','owner','BYN')")
                old.execSQL("INSERT INTO products(id,name,searchKey,price,cost,revision,currency,unit,type) VALUES('p','Товар','товар','20','1',1,'BYN','kg','simple')")
                old.execSQL("INSERT INTO stock_balances VALUES('p','0.0004',8)")
                if(version>=6) {
                    old.execSQL("INSERT INTO shifts VALUES(?,'r','owner','Владелец',1,'UTC',0,NULL,NULL,NULL,NULL,NULL,1)",arrayOf(receipt.shiftId))
                    old.execSQL("INSERT INTO shift_register VALUES(1,9,?)",arrayOf(receipt.shiftId))
                }
                if(version==7) {
                    old.execSQL("INSERT INTO receipts VALUES(?,?,?,?,?,?,?,?,?,?,?)",arrayOf<Any>(receipt.id,receipt.commandId,receipt.sessionId,receipt.shiftId,receipt.number,receipt.createdAt,receipt.total,receipt.cash,receipt.card,receipt.deliveryCashOut,receipt.payload))
                    old.execSQL("INSERT INTO receipt_products VALUES(?,'p')",arrayOf(receipt.id))
                }
                old.version=version
            } finally {old.close()}
            val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(
                CatalogDatabase.MIGRATION_1_2,CatalogDatabase.MIGRATION_2_3,CatalogDatabase.MIGRATION_3_4,
                CatalogDatabase.MIGRATION_4_5,CatalogDatabase.MIGRATION_5_6,CatalogDatabase.MIGRATION_6_7,CatalogDatabase.MIGRATION_7_8).build()
            try {
                assertEquals("0.0004",db.catalog().balance("p")!!.quantity);assertEquals(8L,db.catalog().balance("p")!!.revision)
                assertTrue(db.refunds().history().isEmpty())
                if(version==7) assertEquals(receipt,db.sales().receipt(receipt.id)) else assertTrue(db.sales().history().isEmpty())
                assertEquals(8,db.openHelper.readableDatabase.version)
            } finally {db.close();context.deleteDatabase(name)}
        }
    }
}
