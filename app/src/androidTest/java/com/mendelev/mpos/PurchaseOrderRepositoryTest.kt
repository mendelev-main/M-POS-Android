package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PurchaseOrderRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun setup(db: CatalogDatabase): PurchaseCommand {
        val catalog=CatalogRepository(db);catalog.initialize()
        for(id in listOf("p","unknown")) catalog.saveProduct(ProductInput(id,null,"Молоко $id",null,"2","1","0",null,true,"product-$id",unit="l",noStockTracking=true,purchaseLabel="box",packSize="99"))
        val repo=PurchaseDraftRepository(db);val actor=repo.snapshot().ownerId
        SupplierRepository(db).commit(SupplierCommand("supplier",actor,SupplierAction.Save("s",null,"Поставка",setOf("p","unknown"))))
        val s=repo.snapshot();val d=PurchaseDraft("d","s",1,"Поставка",s.products.map {it.blankLine().copy(quantity="24",unit="bottle",packSize=if(it.id=="p") "500" else "",contentUnit="ml")})
        repo.commit(PurchaseCommand("save",actor,s.revision,PurchaseAction.Save(d)))
        return PurchaseCommand("place",actor,repo.snapshot().revision,PurchaseAction.Place("o",d))
    }
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun submitReopenReplayHistoricalSnapshotsAndDefaultsWithoutStockWrites(): Unit=runBlocking {
        val name="order-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val c=setup(db);val before=db.catalog().balance("p");val p=db.catalog().product("p")!!
            val repo=PurchaseDraftRepository(db);assertEquals("o",repo.commit(c));val doc=db.workspace().document(PurchaseOrderCodec.KEY)
            assertNull(repo.snapshot().draft);assertEquals("o",repo.commit(c));
            reject {repo.commit(c.copy(action=(c.action as PurchaseAction.Place).copy(orderId="different")))};assertEquals(doc,db.workspace().document(PurchaseOrderCodec.KEY))
            assertEquals(p.copy(revision=2,purchaseLabel="bottle",packSize="0.5"),db.catalog().product("p"))
            assertNull(db.catalog().product("unknown")!!.packSize);assertEquals(before,db.catalog().balance("p"));assertEquals(0,db.catalog().movementCount("p"))
            SupplierRepository(db).commit(SupplierCommand("rename",c.actorId,SupplierAction.Save("s",1,"Новое имя",emptySet())))
            db.close();db=open();val order=PurchaseDraftRepository(db).snapshot().orders.single()
            assertEquals("Поставка",order.source.supplierName);assertEquals(listOf("12",null),order.expectedQuantities)
            assertTrue(PurchaseOrderRules.text(order).contains("500 мл"));assertEquals("o",PurchaseDraftRepository(db).commit(c))
            reject {SupplierRepository(db).commit(SupplierCommand("delete",c.actorId,SupplierAction.Delete("s",2)))}
            reject {CatalogRepository(db).deleteProduct("p",2,"delete")}
            reject {PurchaseDraftRepository(db).commit(c.copy(commandId="duplicate",revision=2))}
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun everyWriteBoundaryAndSqlAbortRollBackOrderDraftDefaultsAndResult(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=setup(db);val draft=db.workspace().document(PurchaseDraftCodec.KEY);val p=db.catalog().product("p");val unknown=db.catalog().product("unknown")
            for(boundary in 1..6) {
                var writes=0
                try {PurchaseDraftRepository(db,afterWrite={if(++writes==boundary) throw java.io.IOException("fault")}).commit(c);fail()} catch(_:java.io.IOException) {}
                assertEquals(draft,db.workspace().document(PurchaseDraftCodec.KEY));assertNull(db.workspace().document(PurchaseOrderCodec.KEY));assertEquals(p,db.catalog().product("p"));assertEquals(unknown,db.catalog().product("unknown"));assertNull(db.catalog().result(c.commandId))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_order BEFORE INSERT ON audit_events WHEN NEW.action='purchase.order.place' BEGIN SELECT RAISE(ABORT,'test'); END")
            try {PurchaseDraftRepository(db).commit(c);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(draft,db.workspace().document(PurchaseDraftCodec.KEY));assertNull(db.workspace().document(PurchaseOrderCodec.KEY));assertEquals(p,db.catalog().product("p"))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_order")
            try {PurchaseDraftRepository(db,afterCommit={throw java.io.IOException("lost")}).commit(c);fail()} catch(_:java.io.IOException) {}
            assertEquals("o",PurchaseDraftRepository(db).outcome(c));assertEquals("o",PurchaseDraftRepository(db).commit(c));assertEquals(1,PurchaseDraftRepository(db).snapshot().orders.size)
        } finally {db.close()}
    }
    @Test fun ownerGateSavedDraftAndLiveReferencesAreRequired(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=setup(db);val repo=PurchaseDraftRepository(db);val place=c.action as PurchaseAction.Place
            reject {repo.commit(c.copy(actorId="cashier"))}
            InstallationGate.prepare("order-test") {}
            try {try {repo.commit(c);fail()} catch(_:IllegalStateException) {}} finally {InstallationGate.release("order-test")}
            reject {repo.commit(c.copy(action=place.copy(draft=place.draft.copy(lines=place.draft.lines.map {it.copy(quantity="25")}))))}
            SupplierRepository(db).commit(SupplierCommand("rename",c.actorId,SupplierAction.Save("s",1,"Новое имя",setOf("p","unknown"))))
            reject {repo.commit(c)};assertTrue(repo.snapshot().orders.isEmpty());assertNotNull(repo.snapshot().draft)
        } finally {db.close()}
    }
    @Test fun concurrentSubmitHasOneWinnerAndCorruptHistoryIsNotReset(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=setup(db);val repo=PurchaseDraftRepository(db)
            val result=coroutineScope {(1..2).map {i->async(Dispatchers.IO) {runCatching {repo.commit(c.copy(commandId="place$i",action=(c.action as PurchaseAction.Place).copy(orderId="o$i")))}}}.awaitAll()}
            assertEquals(1,result.count {it.isSuccess});assertEquals(1,repo.snapshot().orders.size);assertNull(repo.snapshot().draft)
            val current=repo.snapshot()
            val sameSource=PurchaseDraftRules.revalidate((c.action as PurchaseAction.Place).draft,current.suppliers,current.products)
            repo.commit(PurchaseCommand("resave-old-source",c.actorId,current.revision,PurchaseAction.Save(sameSource)))
            reject {repo.commit(PurchaseCommand("reuse-source",c.actorId,repo.snapshot().revision,PurchaseAction.Place("new-order",sameSource)))}
            assertEquals(1,repo.snapshot().orders.size);assertNotNull(repo.snapshot().draft)
            val bad=WorkspaceDocument(PurchaseOrderCodec.KEY,9,"{\"version\":99,\"items\":[]}");db.workspace().put(bad)
            reject {repo.snapshot()};assertEquals(bad,db.workspace().document(PurchaseOrderCodec.KEY))
        } finally {db.close()}
    }
}
