package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

class ParkedOrdersRepositoryTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private fun input(stock: String="10",untracked: Boolean=false)=ProductInput("p",null,"Чай",null,"2.5","1",stock,null,true,"create",noStockTracking=untracked)
    private suspend fun seed(db: CatalogDatabase,untracked: Boolean=false): WorkspaceRepository {
        val c=CatalogRepository(db);c.initialize();c.saveProduct(input(if(untracked) "0" else "10",untracked))
        return WorkspaceRepository(db).also {it.initialize()}
    }
    private suspend fun add(repo: WorkspaceRepository,qty: String="1") {
        val owner=repo.snapshot().cart.sessionId
        repo.cart(CartAction.Add("p",1,emptySet(),null,"line-${UUID.randomUUID()}"),UUID.randomUUID().toString(),owner)
        val state=repo.snapshot()
        if(qty!="1") repo.cart(CartAction.SetQuantity(state.cart.lines.single().id,qty,state.cartRevision),UUID.randomUUID().toString(),owner)
    }
    private suspend fun details(repo: WorkspaceRepository,label: String="Стол 1") {
        val s=repo.snapshot();repo.cart(CartAction.SetDetails(OrderDetails(label,"delivery","Без сахара","Тестовая 1","000"),s.cartRevision),"details-${UUID.randomUUID()}",s.cart.sessionId)
    }
    private fun reserve(s: WorkspaceSnapshot)=OrderReservations.total(listOf(s.cart)+s.parked.map {it.cart})
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun parkResumeReplayAndRestartKeepOneOwnerAndMetadata() = runBlocking {
        val name="parked-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            var repo=seed(db);add(repo,"2");details(repo);val before=repo.snapshot();val owner=before.cart.sessionId
            reject {repo.park(before.cartRevision,owner," ","empty-label")}
            assertEquals(before,repo.snapshot())
            repo.park(before.cartRevision,owner,"Стол 1","park");repo.park(before.cartRevision,owner,"Стол 1","park")
            var s=repo.snapshot();assertTrue(s.cart.lines.isEmpty());assertNotEquals(owner,s.cart.sessionId)
            assertEquals(owner,s.parked.single().id);assertEquals(reserve(before),reserve(s))
            db.close();db=open();repo=WorkspaceRepository(db);repo.initialize();s=repo.snapshot()
            val row=s.parked.single();assertEquals("Тестовая 1",row.cart.details.address)
            repo.resume(row.id,row.revision,s.cartRevision,s.cart.sessionId,"resume")
            repo.resume(row.id,row.revision,s.cartRevision,s.cart.sessionId,"resume")
            val resumed=repo.snapshot();assertTrue(resumed.parked.isEmpty());assertEquals(before.cart,resumed.cart)
            assertEquals(reserve(before),reserve(resumed));assertEquals("10",db.catalog().balance("p")!!.quantity)
            assertEquals(1,db.catalog().movementCount("p"))
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun swapIsExplicitAtomicAndDoesNotDoubleReserve() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=seed(db);add(repo,"3");details(repo,"Стол 1");var s=repo.snapshot()
            repo.park(s.cartRevision,s.cart.sessionId,"Стол 1","park-a")
            add(repo,"2");s=repo.snapshot();val before=s;val row=s.parked.single()
            reject {repo.resume(row.id,row.revision,s.cartRevision,s.cart.sessionId,"unsafe")}
            assertEquals(before,repo.snapshot())
            repo.resume(row.id,row.revision,s.cartRevision,s.cart.sessionId,"swap","Стол 2")
            s=repo.snapshot();assertEquals(row.id,s.cart.sessionId);assertEquals("Стол 2",s.parked.single().cart.details.label)
            assertEquals(BigDecimal("5"),reserve(s).getValue("p"));assertEquals(reserve(before),reserve(s))
            val owner=s.cart.sessionId;val id=s.cart.lines.single().id
            reject {repo.cart(CartAction.SetQuantity(id,"9",s.cartRevision),"oversell",owner)} // 9+2 >10.
            repo.cart(CartAction.SetQuantity(id,"8",s.cartRevision),"exact",owner) // 8+2=10, own old3 isn't counted twice.
            assertEquals(BigDecimal.TEN,reserve(repo.snapshot()).getValue("p"))
        } finally {db.close()}
    }
    @Test fun allTransferWriteFailuresRollBackBothOwnersResultAndRegistry() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=seed(db);add(repo);var s=repo.snapshot()
            suspend fun fault(writes: Int,command: String,block: suspend (WorkspaceRepository)->Unit) {
                val before=repo.snapshot()
                for(n in 1..writes) {
                    var count=0
                    val failing=WorkspaceRepository(db,afterWrite={if(++count==n) throw java.io.IOException("injected")})
                    try {block(failing);fail("Expected fault $n")} catch(_:java.io.IOException) {}
                    assertEquals(before,repo.snapshot());assertNull(db.catalog().result(command))
                }
            }
            fault(3,"fault-park") {it.park(s.cartRevision,s.cart.sessionId,"Стол 1","fault-park")}
            repo.park(s.cartRevision,s.cart.sessionId,"Стол 1","park");s=repo.snapshot()
            var row=s.parked.single()
            fault(3,"fault-resume") {it.resume(row.id,row.revision,s.cartRevision,s.cart.sessionId,"fault-resume")}
            fault(2,"fault-delete") {it.deleteParked(row.id,row.revision,"fault-delete")}
            add(repo);s=repo.snapshot();row=s.parked.single()
            fault(4,"fault-swap") {it.resume(row.id,row.revision,s.cartRevision,s.cart.sessionId,"fault-swap","Стол 2")}
            assertEquals(0,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE commandId LIKE 'fault-%'").use {it.count})
        } finally {db.close()}
    }
    @Test fun delayedIntentsConcurrentTransfersAndAbaRevisionCannotTouchAnotherOrder() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=seed(db);add(repo);val before=repo.snapshot();val id=before.cart.lines.single().id
            val results=coroutineScope {(1..2).map {n -> async(Dispatchers.IO) {
                try {repo.park(before.cartRevision,before.cart.sessionId,"Стол", "park-$n");true} catch(_:CatalogFailure) {false}
            }}.awaitAll()}
            assertEquals(1,results.count {it});var s=repo.snapshot();val old=s.parked.single()
            reject {repo.cart(CartAction.Step(id,1),"late",before.cart.sessionId)}
            reject {repo.cart(CartAction.Add("p",1,emptySet(),null,"late-add"),"late-add",before.cart.sessionId)}
            val resumes=coroutineScope {(1..2).map {n -> async(Dispatchers.IO) {
                try {repo.resume(old.id,old.revision,s.cartRevision,s.cart.sessionId,"resume-$n");true} catch(_:CatalogFailure) {false}
            }}.awaitAll()}
            assertEquals(1,resumes.count {it});s=repo.snapshot()
            repo.park(s.cartRevision,s.cart.sessionId,"Стол","repark")
            val new=repo.snapshot().parked.single();assertEquals(old.id,new.id);assertTrue(new.revision>old.revision)
            reject {repo.deleteParked(old.id,old.revision,"stale-delete")}
            repo.deleteParked(new.id,new.revision,"delete");repo.deleteParked(new.id,new.revision,"delete")
            assertTrue(repo.snapshot().parked.isEmpty())
        } finally {db.close()}
    }
    @Test fun emptyOrderMetadataReplacementRequiresConfirmationAndParkedProtectsCatalog() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=seed(db,true);add(repo);var s=repo.snapshot();repo.park(s.cartRevision,s.cart.sessionId,"С собой","park")
            reject {CatalogRepository(db).deleteProduct("p",1,"delete-active")}
            details(repo,"Пустой подготовленный");s=repo.snapshot();val row=s.parked.single()
            reject {repo.resume(row.id,row.revision,s.cartRevision,s.cart.sessionId,"replace-unconfirmed")}
            repo.resume(row.id,row.revision,s.cartRevision,s.cart.sessionId,"replace-confirmed",replaceEmptyDetails=true)
            assertEquals("С собой",repo.snapshot().cart.details.label)
        } finally {db.close()}
    }
    @Test fun legacyPayloadUpgradeKeepsLinesRevisionAndOldCommandReplay() = runBlocking {
        val name="payload-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            var repo=seed(db);add(repo,"3");val before=repo.snapshot()
            val legacy=JSONObject(WorkspaceCodec.cart(before.cart)).put("version",1).apply {remove("sessionId");remove("createdAt");remove("details")}.toString()
            db.workspace().put(WorkspaceDocument("current",before.cartRevision,legacy));db.workspace().delete("orders")
            val action=CartAction.Add("p",1,emptySet(),null,"old-line")
            val fp=CatalogRules.fingerprint("workspace.current","add","p","1",CatalogRules.fingerprint(),null,"old-line")
            db.catalog().result(ResultEntity("old-result",fp,"current"))
            db.close();db=open();repo=WorkspaceRepository(db);repo.initialize();val after=repo.snapshot()
            assertEquals(before.cart.lines,after.cart.lines);assertEquals(before.cart.graph,after.cart.graph)
            assertEquals(before.cartRevision,after.cartRevision);assertTrue(after.cart.sessionId.isNotBlank());assertEquals(2,WorkspaceCodec.cartVersion(db.workspace().document("current")!!.payload))
            repo.cart(action,"old-result",after.cart.sessionId);assertEquals(after,repo.snapshot())
            val owner=after.cart.sessionId;repo.initialize();assertEquals(owner,repo.snapshot().cart.sessionId)
            assertEquals("10",db.catalog().balance("p")!!.quantity)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
