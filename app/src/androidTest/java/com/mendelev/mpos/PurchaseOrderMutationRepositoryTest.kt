package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PurchaseOrderMutationRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun setup(db: CatalogDatabase): Pair<String,PurchaseOrder> {
        val catalog=CatalogRepository(db);catalog.initialize()
        for(id in listOf("p","removed")) catalog.saveProduct(ProductInput(id,null,"Молоко $id",null,"2","1","0",null,true,"product-$id",unit="l",noStockTracking=true,purchaseLabel="bottle",packSize="0.5"))
        val repo=PurchaseDraftRepository(db);val s=repo.snapshot()
        SupplierRepository(db).commit(SupplierCommand("supplier",s.ownerId,SupplierAction.Save("s",null,"Поставка",setOf("p","removed"))))
        val fresh=repo.snapshot();val d=PurchaseDraft("d","s",1,"Поставка",fresh.products.map {it.blankLine().copy(quantity="24",packSize="500",contentUnit="ml")})
        repo.commit(PurchaseCommand("save",s.ownerId,0,PurchaseAction.Save(d)))
        repo.commit(PurchaseCommand("place",s.ownerId,1,PurchaseAction.Place("o",d)))
        return s.ownerId to repo.snapshot().orders.single()
    }
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail()} catch(_:CatalogFailure) {}}
    private suspend fun edit(db: CatalogDatabase,owner: String,o: PurchaseOrder): PurchaseOrderCommand {
        val s=PurchaseDraftRepository(db).snapshot()
        val d=PurchaseOrderMutationRules.open(o,s.suppliers,s.products).copy(lines=PurchaseOrderMutationRules.open(o,s.suppliers,s.products).lines.filter {it.productId=="p"}.map {it.copy(quantity="12")})
        return PurchaseOrderCommand("edit",owner,o.id,o.revision,PurchaseOrderAction.Edit(d))
    }
    @Test fun versionOneUpgradesOnEditReopenCancelAndReplayRetainsRemovedReferences(): Unit=runBlocking {
        val name="order-edit-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val (owner,old)=setup(db);val doc=db.workspace().document(PurchaseOrderCodec.KEY)!!
            val json=JSONObject(doc.payload);json.put("version",1);val item=json.getJSONArray("items").getJSONObject(0)
            listOf("revision","updatedAt","cancelledAt","cancelledBy","history").forEach {item.remove(it)}
            db.workspace().put(doc.copy(payload=json.toString()))
            assertEquals(old,PurchaseDraftRepository(db).snapshot().orders.single())
            val product=db.catalog().product("p");val stock=db.catalog().balance("p");val draftDoc=db.workspace().document(PurchaseDraftCodec.KEY)
            val c=edit(db,owner,old);val repo=PurchaseOrderRepository(db);repo.commit(c);val editedDoc=db.workspace().document(PurchaseOrderCodec.KEY)
            assertEquals("3",JSONObject(editedDoc!!.payload).get("version").toString());assertEquals("o",repo.commit(c));assertEquals(editedDoc,db.workspace().document(PurchaseOrderCodec.KEY))
            SupplierRepository(db).commit(SupplierCommand("unlink",owner,SupplierAction.Save("s",1,"Новое имя",setOf("p"))))
            reject {CatalogRepository(db).deleteProduct("removed",2,"delete-historical")}
            db.close();db=open();val current=PurchaseDraftRepository(db).snapshot().orders.single()
            assertEquals(2L,current.requestVersion);assertEquals(2, current.history.single().source.lines.size);assertEquals(listOf("6"),current.expectedQuantities)
            val cancel=PurchaseOrderCommand("cancel",owner,"o",2,PurchaseOrderAction.Cancel);PurchaseOrderRepository(db).commit(cancel)
            val cancelled=PurchaseDraftRepository(db).snapshot().orders.single();assertEquals("cancelled",cancelled.status);assertEquals(3L,cancelled.revision)
            assertEquals("o",PurchaseOrderRepository(db).commit(c));assertEquals("o",PurchaseOrderRepository(db).commit(cancel));assertEquals(cancelled,PurchaseDraftRepository(db).snapshot().orders.single())
            assertEquals(product,db.catalog().product("p"));assertEquals(stock,db.catalog().balance("p"));assertEquals(draftDoc,db.workspace().document(PurchaseDraftCodec.KEY));assertEquals(0,db.catalog().movementCount("p"))
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun everyMutationWriteAndSqlAbortRollBackAndLostReplyReplays(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val (owner,o)=setup(db);val edit=edit(db,owner,o);val original=db.workspace().document(PurchaseOrderCodec.KEY)
            for(action in listOf(edit,PurchaseOrderCommand("cancel",owner,"o",1,PurchaseOrderAction.Cancel))) {
                for(n in 1..3) {
                    var writes=0
                    try {PurchaseOrderRepository(db,afterWrite={if(++writes==n) throw java.io.IOException("fault")}).commit(action);fail()} catch(_:java.io.IOException) {}
                    assertEquals(original,db.workspace().document(PurchaseOrderCodec.KEY));assertNull(db.catalog().result(action.commandId))
                }
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_edit BEFORE INSERT ON audit_events WHEN NEW.action='purchase.order.edit' BEGIN SELECT RAISE(ABORT,'test'); END")
            try {PurchaseOrderRepository(db).commit(edit);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(original,db.workspace().document(PurchaseOrderCodec.KEY));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_edit")
            try {PurchaseOrderRepository(db,afterCommit={throw java.io.IOException("lost")}).commit(edit);fail()} catch(_:java.io.IOException) {}
            assertEquals("o",PurchaseOrderRepository(db).outcome(edit));assertEquals("o",PurchaseOrderRepository(db).commit(edit));assertEquals(2L,PurchaseDraftRepository(db).snapshot().orders.single().revision)
        } finally {db.close()}
    }
    @Test fun ownerGateLiveReferencesAndFingerprintMismatchAreRejected(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val (owner,o)=setup(db);val c=edit(db,owner,o);val repo=PurchaseOrderRepository(db)
            reject {repo.commit(c.copy(actorId="cashier"))}
            InstallationGate.prepare("edit-test") {}
            try {try {repo.commit(c);fail()} catch(_:IllegalStateException) {}} finally {InstallationGate.release("edit-test")}
            SupplierRepository(db).commit(SupplierCommand("rename",owner,SupplierAction.Save("s",1,"Новое имя",setOf("p","removed"))))
            reject {repo.commit(c)}
            val cancel=PurchaseOrderCommand("cancel",owner,"o",1,PurchaseOrderAction.Cancel);repo.commit(cancel)
            reject {repo.commit(cancel.copy(action=c.action))}
            assertEquals("cancelled",PurchaseDraftRepository(db).snapshot().orders.single().status)
        } finally {db.close()}
    }
    @Test fun concurrentEditCancelHasOneWinnerAndCorruptV2IsNotReset(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val (owner,o)=setup(db);val edit=edit(db,owner,o);val cancel=PurchaseOrderCommand("cancel",owner,"o",1,PurchaseOrderAction.Cancel)
            val results=coroutineScope {listOf(edit,cancel).map {c->async(Dispatchers.IO) {runCatching {PurchaseOrderRepository(db).commit(c)}}}.awaitAll()}
            assertEquals(1,results.count {it.isSuccess});assertEquals(2L,PurchaseDraftRepository(db).snapshot().orders.single().revision)
            val good=db.workspace().document(PurchaseOrderCodec.KEY)!!;val j=JSONObject(good.payload);j.getJSONArray("items").getJSONObject(0).put("revision",99)
            val bad=good.copy(payload=j.toString());db.workspace().put(bad)
            reject {PurchaseDraftRepository(db).snapshot()};reject {PurchaseOrderRepository(db).commit(cancel.copy(commandId="bad",revision=99))}
            assertEquals(bad,db.workspace().document(PurchaseOrderCodec.KEY))
        } finally {db.close()}
    }
}
