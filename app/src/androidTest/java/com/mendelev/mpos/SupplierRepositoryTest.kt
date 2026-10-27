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

class SupplierRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun prepare(db: CatalogDatabase): SupplierCommand {
        val catalog=CatalogRepository(db);catalog.initialize()
        catalog.saveProduct(ProductInput("p",null,"Чай",null,"2","1","0",null,true,"product"))
        val s=SupplierRepository(db).snapshot()
        return SupplierCommand("save",s.ownerId,SupplierAction.Save("s",null,"Поставщик",setOf("p")))
    }
    private suspend fun reject(block: suspend ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun saveReopenReplayAndDeletePreserveProductsAndAudit(): Unit=runBlocking {
        val name="supplier-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val c=prepare(db);val repo=SupplierRepository(db);val product=db.catalog().product("p");val balance=db.catalog().balance("p")
            assertEquals("s",repo.commit(c));val doc=db.workspace().document(SupplierCodec.KEY)
            assertEquals("s",repo.commit(c));assertEquals(doc,db.workspace().document(SupplierCodec.KEY))
            assertEquals(1,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE commandId='save'").use {it.count})
            db.close();db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
            val reopened=SupplierRepository(db)
            assertEquals(setOf("p"),reopened.snapshot().suppliers.single().productIds)
            reject {CatalogRepository(db).deleteProduct("p",1,"delete-linked")}
            val del=SupplierCommand("delete",c.actorId,SupplierAction.Delete("s",1))
            reopened.commit(del);reopened.commit(del)
            assertTrue(reopened.snapshot().suppliers.isEmpty())
            assertEquals(product,db.catalog().product("p"));assertEquals(balance,db.catalog().balance("p"))
            assertEquals("s",reopened.commit(c)) // Replay never recreates a deleted supplier.
            reject {reopened.commit(c.copy(commandId="recreate"))}
            CatalogRepository(db).deleteProduct("p",1,"delete-unlinked")
            assertNull(db.catalog().product("p"))
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun eachWriteAndSqlFailureRollBackWithoutFalseOutcome(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=prepare(db);val original=db.workspace().document(SupplierCodec.KEY)
            for(boundary in 1..3) {
                var writes=0
                try {SupplierRepository(db,afterWrite={if(++writes==boundary) throw java.io.IOException("write")}).commit(c);fail()}
                catch(_:java.io.IOException) {}
                assertEquals(original,db.workspace().document(SupplierCodec.KEY));assertNull(db.catalog().result(c.commandId))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_supplier BEFORE INSERT ON audit_events WHEN NEW.action='supplier.save' BEGIN SELECT RAISE(ABORT,'test'); END")
            try {SupplierRepository(db).commit(c);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(original,db.workspace().document(SupplierCodec.KEY));assertNull(db.catalog().result(c.commandId))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_supplier")
            try {SupplierRepository(db,afterCommit={throw java.io.IOException("lost response")}).commit(c);fail()} catch(_:java.io.IOException) {}
            assertEquals("s",SupplierRepository(db).outcome(c));assertEquals("s",SupplierRepository(db).commit(c))
            assertEquals(1,SupplierRepository(db).snapshot().suppliers.size)
        } finally {db.close()}
    }
    @Test fun ownerReferenceRevisionRaceAndInstallerGuardsAreEnforcedInCommands(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=prepare(db);val repo=SupplierRepository(db)
            reject {repo.commit(c.copy(actorId="cashier"))}
            reject {repo.commit(c.copy(action=SupplierAction.Save("s",null,"Поставщик",setOf("missing"))))}
            InstallationGate.prepare("supplier-test") {}
            try {try {repo.commit(c);fail()} catch(_:IllegalStateException) {}} finally {InstallationGate.release("supplier-test")}
            repo.commit(c)
            reject {repo.commit(c.copy(action=SupplierAction.Save("s",null,"Подмена",emptySet())))}
            val outcomes=coroutineScope {(1..2).map {n->async(Dispatchers.IO) {runCatching {repo.commit(SupplierCommand("edit$n",c.actorId,SupplierAction.Save("s",1,"Версия$n",setOf("p"))))}}}.awaitAll()}
            assertEquals(1,outcomes.count {it.isSuccess});assertEquals(2L,repo.snapshot().suppliers.single().revision)
        } finally {db.close()}
    }
    @Test fun malformedRegistryFailsClosedAndIsNeverReset(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val c=prepare(db);val bad=WorkspaceDocument(SupplierCodec.KEY,12,"{\"version\":99,\"items\":[]}")
            db.workspace().put(bad)
            reject {SupplierRepository(db).snapshot()};reject {SupplierRepository(db).commit(c)}
            assertEquals(bad,db.workspace().document(SupplierCodec.KEY));assertNull(db.catalog().result(c.commandId))
        } finally {db.close()}
    }
}
