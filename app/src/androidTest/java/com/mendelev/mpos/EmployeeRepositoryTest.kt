package com.mendelev.mpos

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.staff.*
import com.mendelev.mpos.domain.shift.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EmployeeRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun memory()=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
    private suspend fun reject(block: suspend ()->Unit): CatalogFailure {try {block();fail("Expected refusal")} catch(e:CatalogFailure) {return e};error("unreachable")}
    private fun create(owner: String,id: String="anna")=EmployeeCommand("create-$id",owner,EmployeeAction.Save(id,null,"Анна Иванова","","employee"))
    @Test fun emptyInstallationRequiresEmployeeAndActiveCashierCannotBeChanged(): Unit=runBlocking {
        val db=memory()
        try {
            val shifts=ShiftRepository(db);shifts.initialize();val before=shifts.snapshot()
            val employees=EmployeeRepository(db);assertTrue(employees.snapshot().employees.isEmpty());assertNull(db.workspace().document(EmployeeCodec.KEY))
            reject {shifts.open("owner-open",0,null,before.ownerId)};assertNull(db.catalog().result("owner-open"));assertNull(shifts.snapshot().last)
            val c=create(before.ownerId);employees.commit(c)
            shifts.open("open",0,null,"anna");val opened=shifts.snapshot();val immutable=db.shifts().shift(opened.active!!.id)
            assertEquals("anna",opened.active!!.actorId);assertEquals("Анна Иванова",opened.active!!.actorName)
            for(action in listOf(EmployeeAction.Save("anna",1,"Анна","123","employee"),EmployeeAction.Save("anna",1,"Анна","","admin"),EmployeeAction.Delete("anna",1))) {
                val edit=EmployeeCommand("edit-${UUID.randomUUID()}",before.ownerId,action)
                assertEquals("activeEmployee",reject {employees.commit(edit)}.kind);assertNull(db.catalog().result(edit.commandId))
            }
            reject {shifts.close("bad-close",opened.active!!.id,opened.revision,ShiftRules.token(opened),0,before.ownerId)}
            shifts.close("close",opened.active!!.id,opened.revision,ShiftRules.token(opened),0,"anna")
            val edit=EmployeeCommand("rename",before.ownerId,EmployeeAction.Save("anna",1,"Анна Новая","123","employee"));employees.commit(edit)
            assertEquals(immutable!!.copy(closedAt=db.shifts().shift(immutable.id)!!.closedAt,countedCash=0,expectedCash=0,difference=0,activeSlot=null),db.shifts().shift(immutable.id))
            assertEquals("Анна Иванова",shifts.snapshot().last!!.actorName)
            val closed=shifts.snapshot();shifts.open("next",closed.revision,closed.last!!.id,"anna");assertEquals("Анна Новая",shifts.snapshot().active!!.actorName)
            assertEquals(opened.active!!.id,shifts.open("open",0,null,"anna")) // Historical replay does not resolve current name.
        } finally {db.close()}
    }
    @Test fun transactionRollbackSqlFailureLostResponseAndRestartKeepOneMutation(): Unit=runBlocking {
        val name="employees-${UUID.randomUUID()}.db";fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val repo=EmployeeRepository(db);val c=create(repo.snapshot().ownerId);val original=db.workspace().documents()
            for(boundary in 1..3) {
                var writes=0
                try {EmployeeRepository(db,afterWrite={if(++writes==boundary) throw java.io.IOException("write")}).commit(c);fail()} catch(_:java.io.IOException) {}
                assertEquals(original,db.workspace().documents());assertNull(repo.outcome(c))
                assertEquals(0,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE commandId='create-anna'").use {it.count})
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_employee BEFORE INSERT ON audit_events WHEN NEW.action='employee.save' BEGIN SELECT RAISE(ABORT,'test'); END")
            try {repo.commit(c);fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(original,db.workspace().documents());assertNull(repo.outcome(c))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_employee")
            try {EmployeeRepository(db,afterCommit={throw java.io.IOException("lost response")}).commit(c);fail()} catch(_:java.io.IOException) {}
            db.close();db=open();val reopened=EmployeeRepository(db)
            assertEquals("anna",reopened.outcome(c));val doc=db.workspace().document(EmployeeCodec.KEY)
            assertEquals("anna",reopened.commit(c));assertEquals(doc,db.workspace().document(EmployeeCodec.KEY))
            reopened.commit(EmployeeCommand("delete",c.actorId,EmployeeAction.Delete("anna",1)))
            assertEquals("anna",reopened.commit(c));assertTrue(reopened.snapshot().employees.isEmpty())
            reject {reopened.commit(c.copy(commandId="reuse"))}
            assertEquals(1,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE commandId='create-anna'").use {it.count})
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun authorizationInstallerCasConcurrencyAndCorruptionFailWithoutWrites(): Unit=runBlocking {
        val db=memory()
        try {
            val repo=EmployeeRepository(db);val c=create(repo.snapshot().ownerId)
            reject {repo.commit(c.copy(actorId="intruder"))}
            Administration.session.logout();assertEquals("access",reject {repo.commit(c)}.kind);assertNull(repo.outcome(c))
            assertTrue(Administration.session.login("fixture-admin".toCharArray()))
            InstallationGate.prepare("employee-test") {}
            try {try {repo.commit(c);fail()} catch(_:IllegalStateException) {}} finally {InstallationGate.release("employee-test")}
            repo.commit(c)
            val edits=coroutineScope {(1..2).map {n->async(Dispatchers.IO) {runCatching {repo.commit(EmployeeCommand("edit$n",c.actorId,EmployeeAction.Save("anna",1,"Анна $n","","employee")))}}}.awaitAll()}
            assertEquals(1,edits.count {it.isSuccess});assertEquals(2L,repo.snapshot().employees.single().revision)
            reject {repo.commit(c.copy(action=EmployeeAction.Save("anna",null,"Подмена","","employee")))}
            val bad=WorkspaceDocument(EmployeeCodec.KEY,7,"{\"version\":99,\"items\":[]}");db.workspace().put(bad)
            reject {repo.snapshot()};reject {repo.commit(create(c.actorId,"other"))}
            assertEquals(bad,db.workspace().document(EmployeeCodec.KEY));assertNull(db.catalog().result("create-other"))
        } finally {db.close()}
    }
    @Test fun openingAndDeletingSameEmployeeSerializeAndAdministratorCannotOpen(): Unit=runBlocking {
        val db=memory()
        try {
            val shifts=ShiftRepository(db);shifts.initialize();val repo=EmployeeRepository(db);val owner=repo.snapshot().ownerId
            repo.commit(EmployeeCommand("admin",owner,EmployeeAction.Save("admin",null,"Управляющий","","admin")))
            reject {shifts.open("admin-open",0,null,"admin")};assertNull(db.catalog().result("admin-open"))
            repo.commit(create(owner))
            val outcomes=coroutineScope {listOf(
                async(Dispatchers.IO) {runCatching {shifts.open("race-open",0,null,"anna")}},
                async(Dispatchers.IO) {runCatching {repo.commit(EmployeeCommand("race-delete",owner,EmployeeAction.Delete("anna",1)))}}
            ).awaitAll()}
            assertEquals(1,outcomes.count {it.isSuccess})
            val active=shifts.snapshot().active
            if(active!=null) {assertEquals("anna",active.actorId);assertTrue(repo.snapshot().employees.any {it.id=="anna"});assertNull(db.catalog().result("race-delete"))}
            else {assertTrue(repo.snapshot().employees.none {it.id=="anna"});assertNull(db.catalog().result("race-open"))}
        } finally {db.close()}
    }
    @Test fun legacyShiftContinuesWithOriginalActorButNewShiftHasNoOwnerFallback(): Unit=runBlocking {
        val db=memory()
        try {
            val shifts=ShiftRepository(db);shifts.initialize();val initial=shifts.snapshot()
            // Released schema8 fixture: no employee document, existing owner shift and original v1 result.
            db.withTransaction {
                db.shifts().insert(ShiftEntity("legacy",initial.registerId,initial.ownerId,"Локальный владелец",1,"UTC",0,null,null,null,null,null,1))
                db.shifts().register(ShiftRegisterEntity(revision=1,lastShiftId="legacy"))
                db.catalog().result(ResultEntity("legacy-open",CatalogRules.fingerprint("shift.open.v1",0L,null,initial.ownerId),"legacy"))
            }
            val before=shifts.snapshot();val employees=EmployeeRepository(db);assertTrue(employees.snapshot().employees.isEmpty())
            assertEquals("legacy",shifts.open("legacy-open",0,null,initial.ownerId))
            val cash=ShiftCashCommand("legacy-cash","legacy",1,initial.ownerId,"deposit",100,"");ShiftCashRepository(db).commit(cash)
            val active=shifts.snapshot();shifts.close("legacy-close","legacy",active.revision,ShiftRules.token(active),100,initial.ownerId)
            val closed=shifts.snapshot();reject {shifts.open("new-owner",closed.revision,"legacy",initial.ownerId)}
            employees.commit(create(initial.ownerId));Administration.session.logout()
            shifts.open("new-cashier",closed.revision,"legacy","anna")
            assertEquals("anna",shifts.snapshot().active!!.actorId);assertEquals(100L,shifts.snapshot().currentCash)
            assertEquals("Локальный владелец",db.shifts().shift("legacy")!!.actorName);assertEquals(before.active!!.actorId,db.shifts().shift("legacy")!!.actorId)
            assertEquals("legacy",shifts.open("legacy-open",0,null,initial.ownerId));assertNotNull(ShiftCashRepository(db).commit(cash))
        } finally {db.close()}
    }
}
