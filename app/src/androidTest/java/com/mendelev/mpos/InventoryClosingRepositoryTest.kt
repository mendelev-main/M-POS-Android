package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class InventoryClosingRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun memory()=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
    private val october=Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"),ZoneId.of("Europe/Minsk"))
    private val november=Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"),ZoneId.of("Europe/Minsk"))
    private suspend fun prepare(db: CatalogDatabase,fixed: Boolean=true,type: String="scheduled"): InventoryRepository {
        standaloneInvoice(db,"closing");val r=InventoryRepository(db,clock=october,generation="runtime");val s=r.snapshot();val p=s.products.single {it.id=="p-closing"}
        r.commit(InventoryCommand("config",s.ownerId,0,InventoryAction.Configure(InventoryConfig(true,"monthly",setOf(p.id)))))
        r.commit(InventoryCommand("start",s.ownerId,1,InventoryAction.Start("draft",type)))
        r.commit(InventoryCommand("count",s.ownerId,2,InventoryAction.Count("draft",p,"1.5","runtime")))
        if(fixed) r.commit(InventoryCommand("fix",s.ownerId,3,InventoryAction.Fix("draft",p.id,"runtime")))
        return r
    }
    private suspend fun close(r: InventoryRepository,cancel: Boolean=false): InventoryCommand {val s=r.snapshot();return InventoryCommand("close",s.ownerId,s.revision,InventoryAction.Finish("draft",cancel))}
    @Test fun completionUsesActualDateAndImmutableSnapshotWithoutStockOrCashWrites(): Unit=runBlocking {
        val db=memory()
        try {
            prepare(db);val r=InventoryRepository(db,clock=november);val c=close(r)
            val balance=db.catalog().balance("p-closing");val product=db.catalog().product("p-closing");val shifts=ShiftRepository(db);shifts.initialize();val cash=shifts.snapshot()
            r.commit(c);val s=r.snapshot();assertNull(s.document.draft);assertEquals("2026-11-01",s.document.config.lastCompleted)
            assertFalse(InventoryRules.due(s.document.config,LocalDate.parse("2026-11-03")))
            val h=s.document.history.single();assertEquals(1700L,h.lossMinor);assertEquals("1.5",h.draft.counts.single().fixed!!.actual)
            assertEquals(balance,db.catalog().balance("p-closing"));assertEquals(product,db.catalog().product("p-closing"));assertEquals(cash,shifts.snapshot());assertEquals(2,db.catalog().movementCount("p-closing"))
            val docs=db.workspace().documents();assertEquals("draft",r.commit(c));assertEquals("draft",r.commit(InventoryCommand("start",s.ownerId,1,InventoryAction.Start("draft","scheduled"))));assertEquals(docs,db.workspace().documents())
            assertTrue(runCatching {r.commit(c.copy(commandId="another",revision=s.revision))}.isFailure)
            assertTrue(runCatching {r.commit(InventoryCommand("reuse",s.ownerId,s.revision,InventoryAction.Start("draft","adhoc")))}.isFailure)
            r.commit(InventoryCommand("next",s.ownerId,s.revision,InventoryAction.Start("next-draft","adhoc")));assertEquals(listOf(h),r.snapshot().document.history)
        } finally {db.close()}
    }
    @Test fun cancellationPreservesLaterStockCorrectionAndUnfixedRawNeverChangesStock(): Unit=runBlocking {
        for(fixed in listOf(false,true)) {
            val db=memory()
            try {
                prepare(db,fixed);val r=InventoryRepository(db,clock=november,generation="new-process")
                val p=db.catalog().product("p-closing")!!;val b=db.catalog().balance(p.id)!!
                CatalogRepository(db).saveProduct(com.mendelev.mpos.domain.catalog.ProductInput(p.id,p.revision,p.name,p.categoryId,p.price,"9","1",b.revision,true,"later-stock",unit=p.unit))
                val before=db.catalog().balance(p.id);val movements=db.catalog().movementCount(p.id)
                if(!fixed) assertTrue(runCatching {r.commit(close(r))}.isFailure)
                r.commit(close(r,true));val s=r.snapshot();assertNull(s.document.config.lastCompleted);assertTrue(InventoryRules.due(s.document.config,LocalDate.parse("2026-11-01")))
                val h=s.document.history.single();assertTrue(h.cancelled);assertEquals(if(fixed) 1700L else 0L,h.lossMinor);assertEquals("1.5",h.draft.counts.single().raw)
                assertEquals(before,db.catalog().balance(p.id));assertEquals(movements,db.catalog().movementCount(p.id));assertEquals("9",db.catalog().product(p.id)!!.cost)
                InventoryRepository(db).requireProductUnused("unrelated")
                assertTrue(runCatching {InventoryRepository(db).requireProductUnused(p.id)}.isFailure)
            } finally {db.close()}
        }
    }
    @Test fun eachCloseWriteSqlAbortAndLostReplyAreAtomic(): Unit=runBlocking {
        val db=memory()
        try {
            val r=prepare(db);val c=close(r);val docs=db.workspace().documents();val b=db.catalog().balance("p-closing")
            for(n in 1..3) {var writes=0;assertTrue(runCatching {InventoryRepository(db,afterWrite={if(++writes==n) error("fail")}).commit(c)}.isFailure);assertEquals(docs,db.workspace().documents());assertNull(db.catalog().result(c.commandId));assertEquals(b,db.catalog().balance("p-closing"))}
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_close BEFORE INSERT ON audit_events WHEN NEW.action='inventory.complete' BEGIN SELECT RAISE(ABORT,'test'); END")
            assertTrue(runCatching {r.commit(c)}.isFailure);assertEquals(docs,db.workspace().documents());db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_close")
            assertTrue(runCatching {InventoryRepository(db,afterCommit={error("reply")}).commit(c)}.isFailure)
            assertEquals("draft",r.outcome(c));r.commit(c);assertEquals(1,r.snapshot().document.history.size);assertEquals(b,db.catalog().balance("p-closing"))
        } finally {db.close()}
    }
    @Test fun ownerGateCasAndConcurrentCompleteCancelAllowOneHistoryRecord(): Unit=runBlocking {
        val db=memory()
        try {
            val r=prepare(db);val c=close(r)
            assertTrue(runCatching {r.commit(c.copy(actorId="cashier"))}.isFailure)
            assertTrue(runCatching {r.commit(c.copy(revision=0))}.isFailure)
            InstallationGate.prepare("close") {};try {assertTrue(runCatching {r.commit(c)}.isFailure)} finally {InstallationGate.release("close")}
            val result=coroutineScope {listOf(false,true).map {cancel->async(Dispatchers.IO) {runCatching {r.commit(c.copy(commandId="close-$cancel",action=InventoryAction.Finish("draft",cancel)))}}}.awaitAll()}
            assertEquals(1,result.count {it.isSuccess});assertEquals(1,r.snapshot().document.history.size);assertEquals(2,db.catalog().movementCount("p-closing"))
        } finally {db.close()}
    }
    @Test fun legacyV2ReadsWithoutHistoryAndMalformedV3IsNeverReset(): Unit=runBlocking {
        val db=memory()
        try {
            val r=prepare(db);val doc=db.workspace().document(InventoryCodec.KEY)!!;val v2=JSONObject(doc.payload).put("version",2);v2.remove("history");db.workspace().put(doc.copy(payload=v2.toString()))
            assertTrue(r.snapshot().document.history.isEmpty());assertNotNull(r.snapshot().document.draft!!.counts.single().fixed)
            r.commit(close(r));val closed=db.workspace().document(InventoryCodec.KEY)!!;val bad=JSONObject(closed.payload);bad.getJSONArray("history").getJSONObject(0).put("lossMinor",1)
            db.workspace().put(closed.copy(payload=bad.toString()));assertTrue(runCatching {r.snapshot()}.isFailure);assertEquals(bad.toString(),db.workspace().document(InventoryCodec.KEY)!!.payload)
        } finally {db.close()}
    }
    @Test fun adhocCompletionDoesNotSuppressScheduledReminder(): Unit=runBlocking {
        val db=memory()
        try {prepare(db,type="adhoc");val r=InventoryRepository(db,clock=november);r.commit(close(r));assertNull(r.snapshot().document.config.lastCompleted)} finally {db.close()}
    }
}
