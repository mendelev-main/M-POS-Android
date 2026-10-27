package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.core.operations.InstallationGate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShiftRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val clock=Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"),ZoneId.of("Europe/Minsk"))
    private suspend fun reject(block: suspend () -> Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun openCloseReplayConcurrencyAndReopenPreserveWorkspace(): Unit=runBlocking {
        val name="shift-${UUID.randomUUID()}.db"
        fun db()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var database=db()
        try {
            val work=WorkspaceRepository(database);work.initialize();val documents=database.workspace().documents()
            val repo=ShiftRepository(database,clock);repo.initialize();val s=repo.snapshot()
            val results=coroutineScope {(1..2).map {n->async(Dispatchers.IO) {runCatching {repo.open("open$n",s.revision,null,s.ownerId)}}}.awaitAll()}
            assertEquals(1,results.count {it.isSuccess});val winner=results.indexOfFirst {it.isSuccess}+1;val id=results.first {it.isSuccess}.getOrThrow()
            assertEquals(id,repo.open("open$winner",0,null,s.ownerId))
            val active=repo.snapshot();assertEquals(1, database.shifts().active().size)
            dbUnique(database, database.shifts().shift(id)!!.copy(id="duplicate"))
            database.withTransaction {assertEquals(id,repo.requireActive(id,active.revision).id)}
            val token=ShiftRules.token(active)
            val closes=coroutineScope {listOf("close","close-other").map {key->async(Dispatchers.IO) {runCatching {repo.close(key,id,active.revision,token,14000,s.ownerId)}}}.awaitAll()}
            assertEquals(1,closes.count {it.isSuccess})
            val closeCommand=if(closes[0].isSuccess) "close" else "close-other"
            val closed=repo.snapshot();assertNull(closed.active);assertEquals(14000L,closed.last!!.difference)
            assertEquals(id,repo.close(closeCommand,id,active.revision,token,14000,s.ownerId))
            assertEquals(id,repo.open("open$winner",0,null,s.ownerId));assertEquals(closed,repo.snapshot())
            reject {repo.close(closeCommand,id,active.revision,token,13999,s.ownerId)}
            reject {database.withTransaction {repo.requireActive(id,active.revision)}}
            val next=repo.open("next",closed.revision,id,s.ownerId)
            assertEquals(14000L,repo.snapshot().active!!.openingCash);assertEquals(id,repo.snapshot().active!!.sourceShiftId)
            assertEquals(documents,database.workspace().documents())
            database.close();database=db();assertEquals(next,ShiftRepository(database,clock).snapshot().active!!.id)
        } finally {database.close();context.deleteDatabase(name)}
    }
    private suspend fun dbUnique(db: CatalogDatabase,value: ShiftEntity) {
        try {db.shifts().insert(value);fail("Database must prevent second active slot")} catch(_:android.database.sqlite.SQLiteConstraintException) {}
    }
    @Test fun everyOpenAndCloseWriteFailureRollsBackShiftRegisterResultAndAudit(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=ShiftRepository(db,clock);repo.initialize();val before=repo.snapshot()
            suspend fun faults(close: Boolean) {
                val baseline=repo.snapshot()
                for(boundary in 1..4) {
                    var writes=0;val command="fault-$close-$boundary"
                    val failing=ShiftRepository(db,clock,afterWrite={if(++writes==boundary) throw java.io.IOException("write boundary")})
                    try {
                        if(close) failing.close(command,baseline.active!!.id,baseline.revision,ShiftRules.token(baseline),100,baseline.ownerId)
                        else failing.open(command,baseline.revision,baseline.last?.id,baseline.ownerId)
                        fail("Expected injected failure")
                    } catch(_:java.io.IOException) {}
                    assertEquals(baseline,repo.snapshot());assertNull(db.catalog().result(command))
                    assertEquals(0,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE commandId='$command'").use {it.count})
                }
            }
            faults(false);repo.open("open",before.revision,null,before.ownerId);faults(true)
            val active=repo.snapshot()
            // Actual SQLite write refusal, in addition to post-write rollback fault injection.
            db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER refuse_close BEFORE UPDATE ON shifts BEGIN SELECT RAISE(ABORT, 'injected'); END")
            try {repo.close("sql-fault",active.active!!.id,active.revision,ShiftRules.token(active),100,active.ownerId);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(active,repo.snapshot());assertNull(db.catalog().result("sql-fault"))
        } finally {db.close()}
    }
    @Test fun installationWaitsForCommittedWriteAndBlocksNewShiftCommands(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build();val entered=CountDownLatch(1);val resume=CountDownLatch(1)
        try {
            val repo=ShiftRepository(db,clock);repo.initialize();val s=repo.snapshot();var first=true
            val slow=ShiftRepository(db,clock,afterWrite={if(first) {first=false;entered.countDown();check(resume.await(10,TimeUnit.SECONDS))}})
            coroutineScope {
                val write=async(Dispatchers.IO) {slow.open("slow",0,null,s.ownerId)}
                assertTrue(withContext(Dispatchers.IO) {entered.await(10,TimeUnit.SECONDS)})
                val cancelled=async {InstallationGate.prepare("cancelled") {error("Cancelled preparation must never run")}}
                yield();cancelled.cancelAndJoin()
                val install=async {InstallationGate.prepare("owner") {assertEquals(1L,repo.snapshot().revision);"intent"}}
                yield();assertFalse(install.isCompleted);resume.countDown();write.await();assertEquals("intent",install.await())
            }
            val active=repo.snapshot()
            InstallationGate.release("another-window")
            try {repo.close("blocked",active.active!!.id,active.revision,ShiftRules.token(active),0,active.ownerId);fail()} catch(_:IllegalStateException) {}
            assertEquals(active,repo.snapshot());assertNull(db.catalog().result("blocked"))
            InstallationGate.release("owner");repo.close("allowed",active.active!!.id,active.revision,ShiftRules.token(active),0,active.ownerId)
        } finally {resume.countDown();InstallationGate.release("owner");db.close()}
    }
    @Test fun migration5To6PreservesLegacyCatalogAndWorkspaceDocuments(): Unit=runBlocking {
        val name="shift-migrate-${UUID.randomUUID()}.db"
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.mendelev.mpos.data.local.CatalogDatabase/5.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
        val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try {
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {val e=entities.getJSONObject(i);val table=e.getString("tableName");old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));e.optJSONArray("indices")?.let {indices->for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}}
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO app_config VALUES(1,'legacy-register','legacy-owner','BYN')")
            old.execSQL("INSERT INTO products(id,name,searchKey,price,cost,revision,currency,unit,type) VALUES('p','Чай','чай','2','1',1,'BYN','piece','simple')")
            old.execSQL("INSERT INTO stock_balances VALUES('p','0.0004',8)")
            old.execSQL("INSERT INTO workspace_documents VALUES('custom',11,'unchanged-payload')");old.version=5
        } finally {old.close()}
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(CatalogDatabase.MIGRATION_5_6,CatalogDatabase.MIGRATION_6_7,CatalogDatabase.MIGRATION_7_8).build()
        try {
            val repo=ShiftRepository(db,clock);repo.initialize();assertNull(repo.snapshot().last);assertEquals("legacy-register",repo.snapshot().registerId)
            assertEquals("0.0004",db.catalog().balance("p")!!.quantity);assertEquals(11L,db.workspace().document("custom")!!.revision);assertEquals("unchanged-payload",db.workspace().document("custom")!!.payload)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
