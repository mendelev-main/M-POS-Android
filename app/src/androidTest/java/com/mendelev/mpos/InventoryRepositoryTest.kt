package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class InventoryRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val clock=Clock.fixed(Instant.parse("2026-10-07T22:30:00Z"),ZoneId.of("Europe/Minsk"))
    private suspend fun configure(db: CatalogDatabase): InventoryCommand {
        val d=standaloneInvoice(db,"inventory");val s=InventoryRepository(db,clock).snapshot()
        return InventoryCommand("configure",s.ownerId,0,InventoryAction.Configure(InventoryConfig(true,"monthly",setOf(d.lines.single().product.id)))).also {InventoryRepository(db,clock).commit(it)}
    }
    @Test fun configAndActiveScopeSurviveReopenWithoutStockChanges(): Unit=runBlocking {
        val name="inventory-${UUID.randomUUID()}.db";fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val c=configure(db);val before=db.catalog().allProducts().map {Triple(it,db.catalog().balance(it.id),db.catalog().movementCount(it.id))}
            val start=InventoryCommand("start",c.actorId,1,InventoryAction.Start("draft","adhoc"));InventoryRepository(db,clock).commit(start)
            db.close();db=open();val repo=InventoryRepository(db,clock);val s=repo.snapshot()
            assertEquals("2026-10-08",s.today);assertEquals("2026-10-08",s.document.draft!!.scheduledDate);assertEquals("draft",s.document.draft.id)
            assertEquals(before,db.catalog().allProducts().map {Triple(it,db.catalog().balance(it.id),db.catalog().movementCount(it.id))})
            assertEquals("draft",repo.commit(start));assertEquals(2L,repo.snapshot().revision)
            val scope=s.document.draft
            repo.commit(c.copy(commandId="new-config",revision=2,action=InventoryAction.Configure(s.document.config.copy(frequency="weekly"))))
            assertEquals(scope,repo.snapshot().document.draft)
            assertTrue(runCatching {repo.commit(start.copy(commandId="second",revision=3,action=InventoryAction.Start("d2","adhoc")))}.isFailure)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun everyWriteFailureSqlAbortAndLostReplyAreAtomic(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=configure(db);val command=InventoryCommand("start",c.actorId,1,InventoryAction.Start("draft","scheduled"));val before=db.workspace().documents()
            for(n in 1..3) {
                var writes=0;assertTrue(runCatching {InventoryRepository(db,clock,afterWrite={if(++writes==n) error("write")}).commit(command)}.isFailure)
                assertEquals(before,db.workspace().documents());assertNull(db.catalog().result(command.commandId))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_inventory BEFORE INSERT ON audit_events WHEN NEW.action='inventory.start' BEGIN SELECT RAISE(ABORT,'test'); END")
            assertTrue(runCatching {InventoryRepository(db,clock).commit(command)}.isFailure);assertEquals(before,db.workspace().documents())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_inventory")
            assertTrue(runCatching {InventoryRepository(db,clock,afterCommit={error("reply")}).commit(command)}.isFailure)
            val repo=InventoryRepository(db,clock);assertEquals("draft",repo.outcome(command));assertEquals("draft",repo.commit(command));assertEquals(2L,repo.snapshot().revision)
            assertEquals("2026-10-01",repo.snapshot().document.draft!!.scheduledDate)
        } finally {db.close()}
    }
    @Test fun configWritesAlsoRollbackAndCorruptFormatIsPreserved(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val d=standaloneInvoice(db);val repo=InventoryRepository(db,clock);val s=repo.snapshot();val c=InventoryCommand("c",s.ownerId,0,InventoryAction.Configure(InventoryConfig(true,"weekly",setOf(d.lines.single().product.id))))
            for(n in 1..3) {var writes=0;assertTrue(runCatching {InventoryRepository(db,clock,afterWrite={if(++writes==n) error("write")}).commit(c)}.isFailure);assertNull(db.workspace().document(InventoryCodec.KEY))}
            val doc=WorkspaceDocument(InventoryCodec.KEY,8,"{\"version\":99}");db.workspace().put(doc);assertTrue(runCatching {repo.snapshot()}.isFailure);assertEquals(doc,db.workspace().document(InventoryCodec.KEY))
        } finally {db.close()}
    }
    @Test fun concurrentStartsCasOwnerGateAndChangedPayloadAreGuarded(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=configure(db);val repo=InventoryRepository(db,clock);val start=InventoryCommand("start",c.actorId,1,InventoryAction.Start("draft","adhoc"))
            assertTrue(runCatching {repo.commit(start.copy(actorId="cashier"))}.isFailure)
            InstallationGate.prepare("inventory") {};try {assertTrue(runCatching {repo.commit(start)}.isFailure)} finally {InstallationGate.release("inventory")}
            val results=coroutineScope {(1..2).map {i->async(Dispatchers.IO) {runCatching {repo.commit(start.copy(commandId="s$i",action=InventoryAction.Start("d$i","adhoc")))}}}.awaitAll()};assertEquals(1,results.count {it.isSuccess})
            assertTrue(runCatching {repo.commit(c.copy(action=InventoryAction.Configure(InventoryConfig(true,"quarterly",setOf("p-inventory")))))}.isFailure)
        } finally {db.close()}
    }
    @Test fun untrackedAndMissingProductsRefusedAndReferencesGuardDeletion(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=configure(db);val repo=InventoryRepository(db,clock);val p="p-inventory"
            assertTrue(runCatching {repo.requireProductUnused(p)}.isFailure)
            assertTrue(runCatching {repo.commit(c.copy(commandId="bad",revision=1,action=InventoryAction.Configure(InventoryConfig(true,"monthly",setOf("missing")))))}.isFailure)
            db.catalog().updateProduct(db.catalog().product(p)!!.copy(noStockTracking=true))
            assertTrue(runCatching {repo.commit(InventoryCommand("start",c.actorId,1,InventoryAction.Start("d","adhoc")))}.isFailure)
            assertTrue(repo.snapshot().products.none {it.id==p});assertEquals(1L,repo.snapshot().revision)
        } finally {db.close()}
    }
}
