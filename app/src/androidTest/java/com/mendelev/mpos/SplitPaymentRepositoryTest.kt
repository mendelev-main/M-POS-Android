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
        val s=ShiftRepository(db);s.initializeCashierFixture();val before=s.snapshot();s.open("open",0,null,before.ownerId)
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

    @Test fun legacyProgressMigratesWithStableAliasesReopensAndFinalizesOnce(): Unit=runBlocking {
        val name="split-edit-${UUID.randomUUID()}.db";fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val saved=seed(db,parts=listOf(PaymentInput("cash",3334,4000),PaymentInput("card",3333),PaymentInput("cash",3333,4000)))
            var split=SplitPaymentRepository(db)
            val legacy=split.confirm(saved,1,0)
            split.edit("resize",saved,1,SplitEdit.Count(4))
            assertEquals("resize",split.edit("resize",saved,1,SplitEdit.Count(4)))
            reject {split.edit("resize",saved,1,SplitEdit.Count(5))}
            var p=split.load()!!
            assertEquals(2,p.format);assertEquals(listOf(2L,1L,3L,4L),p.partIds)
            assertEquals(listOf(3333L,2223L,2222L,2222L),p.checkout.draft.parts.map {it.amount})
            assertEquals(setOf(0),p.paid);assertEquals(3333L,p.paidAmount);assertEquals(0L,p.pendingCash)
            assertEquals(saved.draft.parts[1],p.checkout.draft.parts[0])
            assertEquals(legacy,split.confirm(saved,1,0))
            assertEquals(legacy,split.confirmPart(p.checkout,2,p.checkout.draft.parts[0],0))
            reject {split.confirm(p.checkout,1,p.revision)} // Index API cannot create confirmations after migration.
            reject {split.edit("paid-change",p.checkout,p.revision,SplitEdit.Amount(2,0))}
            reject {split.edit("stale",saved,1,SplitEdit.Count(5))}
            db.close();db=open();split=SplitPaymentRepository(db)
            assertEquals(p,split.load());assertEquals(p.checkout,CheckoutRepository(db).load())
            for(id in listOf(1L,3L,4L)) {
                p=split.load()!!
                split.edit("method-$id",p.checkout,p.revision,SplitEdit.Method(id,"card"))
            }
            val finalSaved=split.load()!!.checkout
            for(id in listOf(1L,3L,4L)) {
                p=split.load()!!;val index=p.partIds.indexOf(id)
                split.confirmPart(p.checkout,id,p.checkout.draft.parts[index],p.revision)
            }
            val receipt=SaleRepository(db).commit(finalSaved)
            assertNull(split.load());assertEquals(receipt,SaleRepository(db).commit(finalSaved))
            assertEquals(legacy,split.confirm(saved,1,0))
            assertEquals(legacy,split.confirmPart(finalSaved,2,PaymentInput("card",3333),0))
            assertEquals("resize",split.edit("resize",saved,1,SplitEdit.Count(4)))
            assertEquals(1,db.sales().history().size);assertEquals("9",db.catalog().balance("p")!!.quantity)
            assertEquals(0L,ShiftRepository(db).snapshot().currentCash)
        } finally {db.close();context.deleteDatabase(name)}
    }

    @Test fun stableConfirmationsSurviveUnpaidAmountMethodTenderAndCountEdits(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val saved=seed(db);val split=SplitPaymentRepository(db)
            val documents=db.workspace().documents()
            for(boundary in 1..3) {
                var writes=0
                try {SplitPaymentRepository(db) {if(++writes==boundary) throw java.io.IOException("disk")}
                    .confirmPart(saved,1,saved.draft.parts[0],0);fail()} catch(_:java.io.IOException) {}
                assertNull(split.load());assertEquals(documents,db.workspace().documents())
            }
            val first=split.confirmPart(saved,1,saved.draft.parts[0],0)
            suspend fun edit(command: String,action: SplitEdit) {
                val p=split.load()!!;split.edit(command,p.checkout,p.revision,action)
                assertEquals(saved.draft.parts[0],split.load()!!.checkout.draft.parts[0])
                assertEquals(4000L,split.load()!!.pendingCash)
            }
            edit("three",SplitEdit.Count(3));edit("two",SplitEdit.Count(2));edit("three-again",SplitEdit.Count(3))
            assertEquals(listOf(1L,2L,4L),split.load()!!.partIds)
            val before=split.load()!!
            reject {split.edit("retired",before.checkout,before.revision,SplitEdit.Amount(3,1))}
            edit("amount",SplitEdit.Amount(2,2000))
            assertEquals(listOf(4000L,2000L,4000L),split.load()!!.checkout.draft.parts.map {it.amount})
            edit("card",SplitEdit.Method(2,"card"));edit("tender",SplitEdit.Given(4,5000))
            val p=split.load()!!;val finalSaved=p.checkout
            assertEquals(first,split.confirmPart(saved,1,saved.draft.parts[0],0))
            reject {split.confirmPart(saved,1,PaymentInput("cash",4000,6000),0)}
            reject {split.confirmPart(saved.copy(draft=saved.draft.copy(actorId="other")),1,saved.draft.parts[0],0)}
            reject {split.edit("paid-given",p.checkout,p.revision,SplitEdit.Given(1,6000))}
            split.confirmPart(p.checkout,2,p.checkout.draft.parts[1],p.revision)
            val cash=split.load()!!
            val last=split.confirmPart(cash.checkout,4,cash.checkout.draft.parts[2],cash.revision)
            val complete=split.load()!!
            reject {split.edit("after-complete",complete.checkout,complete.revision,SplitEdit.Count(4))}
            val finalDocuments=db.workspace().documents()
            for(boundary in 1..10) {
                var writes=0
                try {SaleRepository(db,afterWrite={if(++writes==boundary) throw java.io.IOException("disk")}).commit(finalSaved);fail()} catch(_:java.io.IOException) {}
                assertEquals(complete,split.load());assertEquals(finalDocuments,db.workspace().documents())
                assertTrue(db.sales().history().isEmpty());assertEquals("10",db.catalog().balance("p")!!.quantity)
            }
            val receipt=SaleRepository(db).commit(finalSaved)
            assertEquals(last,split.confirmPart(cash.checkout,4,cash.checkout.draft.parts[2],0))
            assertEquals(first,split.confirmPart(saved,1,saved.draft.parts[0],0))
            assertEquals(8000L,ShiftRepository(db).snapshot().currentCash)
            val payment=SaleRepository(db).receipt(receipt)!!.second.payment
            assertEquals(2000L,payment.card);assertEquals(1000L,payment.parts[0].change);assertEquals(1000L,payment.parts[2].change)
            assertEquals("9",db.catalog().balance("p")!!.quantity)
        } finally {db.close()}
    }

    @Test fun editMigrationAndStableConfirmFaultsRollbackEveryWriteIncludingAliases(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val saved=seed(db);val split=SplitPaymentRepository(db)
            split.confirm(saved,0,0)
            val before=split.load();val documents=db.workspace().documents()
            val alias=CatalogRules.fingerprint("split.part.command.v2",saved.draft.commandId,1L)
            for(boundary in 1..5) { // Legacy alias + checkout + progress + result + audit.
                var writes=0
                try {SplitPaymentRepository(db) {if(++writes==boundary) throw java.io.IOException("disk")}
                    .edit("edit",saved,1,SplitEdit.Count(3));fail()} catch(_:java.io.IOException) {}
                assertEquals(before,split.load());assertEquals(documents,db.workspace().documents())
                assertNull(db.catalog().result("edit"));assertNull(db.catalog().result(alias))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER refuse_checkout_edit BEFORE INSERT ON workspace_documents WHEN NEW.id='checkout' BEGIN SELECT RAISE(ABORT, 'injected'); END")
            try {split.edit("edit",saved,1,SplitEdit.Count(3));fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(before,split.load());assertNull(db.catalog().result(alias));assertNull(db.catalog().result("edit"))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_checkout_edit")
            // Migration performed by a new confirmation also aliases the previously paid legacy row.
            for(boundary in 1..4) {
                var writes=0
                try {SplitPaymentRepository(db) {if(++writes==boundary) throw java.io.IOException("disk")}
                    .confirmPart(saved,2,saved.draft.parts[1],1);fail()} catch(_:java.io.IOException) {}
                assertEquals(before,split.load());assertEquals(documents,db.workspace().documents());assertNull(db.catalog().result(alias))
            }
            split.edit("edit",saved,1,SplitEdit.Count(3))
            val p=split.load()!!
            // Redistributed cash needs an explicit tender; no missing input may be marked paid.
            reject {split.confirmPart(p.checkout,3,p.checkout.draft.parts[2],p.revision)}
            split.edit("give",p.checkout,p.revision,SplitEdit.Given(3,3000))
            val ready=split.load()!!;val stableDocuments=db.workspace().documents()
            for(boundary in 1..3) {
                var writes=0
                try {SplitPaymentRepository(db) {if(++writes==boundary) throw java.io.IOException("disk")}
                    .confirmPart(ready.checkout,3,ready.checkout.draft.parts[2],ready.revision);fail()} catch(_:java.io.IOException) {}
                assertEquals(ready,split.load());assertEquals(stableDocuments,db.workspace().documents())
            }
            split.confirmPart(ready.checkout,3,ready.checkout.draft.parts[2],ready.revision)
            assertEquals(7000L,split.load()!!.pendingCash);assertTrue(db.sales().history().isEmpty())
            assertEquals("10",db.catalog().balance("p")!!.quantity)
        } finally {db.close()}
    }

    @Test fun editAndConfirmationRaceHasOneWinnerAndSameCommandRetriesAreIdempotent(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val saved=seed(db);val split=SplitPaymentRepository(db)
            split.confirmPart(saved,1,saved.draft.parts[0],0)
            val outcomes=coroutineScope {listOf(
                async(Dispatchers.IO) {runCatching {split.edit("race",saved,1,SplitEdit.Count(3))}},
                async(Dispatchers.IO) {runCatching {split.confirmPart(saved,2,saved.draft.parts[1],1)}}
            ).awaitAll()}
            assertEquals(1,outcomes.count {it.isSuccess});assertEquals(2L,split.revision())
            if(outcomes[0].isSuccess) {
                assertEquals(3,split.load()!!.partIds.size);assertFalse(split.load()!!.complete)
                val retries=coroutineScope {(1..4).map {async(Dispatchers.IO) {split.edit("race",saved,1,SplitEdit.Count(3))}}.awaitAll()}
                assertEquals(listOf("race"),retries.distinct());assertEquals(2L,split.revision())
            } else {
                assertTrue(split.load()!!.complete);assertEquals(saved,split.load()!!.checkout)
            }
            reject {split.requireInstallAllowed()}
            assertEquals("10",db.catalog().balance("p")!!.quantity);assertTrue(db.sales().history().isEmpty())
        } finally {db.close()}
    }

    @Test fun stableZeroPartsCompleteAndUnknownOrCorruptProgressCannotBeDiscarded(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val saved=seed(db,"0.01",listOf(PaymentInput("cash",1,1),PaymentInput("card",0),PaymentInput("card",0)))
            val split=SplitPaymentRepository(db)
            split.confirmPart(saved,1,saved.draft.parts[0],0)
            val doc=db.workspace().document("split-progress")!!
            val unknown=org.json.JSONObject(doc.payload).put("version",99).toString()
            db.workspace().put(doc.copy(payload=unknown))
            reject {split.load()};reject {CheckoutRepository(db).discard(saved.revision)}
            assertEquals(unknown,db.workspace().document("split-progress")!!.payload)
            val duplicate=org.json.JSONObject(doc.payload).put("partIds",org.json.JSONArray(listOf(1,1,3))).toString()
            db.workspace().put(doc.copy(payload=duplicate))
            try {split.load();fail()} catch(_:IllegalArgumentException) {}
            assertEquals(duplicate,db.workspace().document("split-progress")!!.payload)
            db.workspace().put(doc)
            split.confirmPart(saved,2,saved.draft.parts[1],1)
            split.confirmPart(saved,3,saved.draft.parts[2],2)
            assertTrue(split.load()!!.complete)
            SaleRepository(db).commit(saved);assertNull(split.load());assertEquals(1L,ShiftRepository(db).snapshot().currentCash)
        } finally {db.close()}
    }

}
