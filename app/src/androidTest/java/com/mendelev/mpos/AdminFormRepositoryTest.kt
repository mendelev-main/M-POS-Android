package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AdminFormRepositoryTest {
    @Test fun queuedTransactionCannotUseNewLoginToCommitOldForm(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob());val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        try {
            val repo=SupplierRepository(db);val before=repo.snapshot();val docs=db.workspace().documents()
            val permit=AdminForms(scope).permit()
            coroutineScope {
                val holder=launch {InstallationGate.write {entered.complete(Unit);release.await()}}
                entered.await()
                // Start before logout and suspend behind the write gate. Authorization must be rechecked inside Room's boundary.
                val command=async(start=CoroutineStart.UNDISPATCHED) {runCatching {permit.write {repo.commit(SupplierCommand("queued",before.ownerId,SupplierAction.Save("s",null,"Поставщик",emptySet())))}}}
                assertFalse(command.isCompleted)
                Administration.session.logout();assertTrue(Administration.session.login("fixture-admin".toCharArray()))
                release.complete(Unit);holder.join()
                assertEquals("access",(command.await().exceptionOrNull() as CatalogFailure).kind)
            }
            assertEquals(docs,db.workspace().documents());assertNull(db.catalog().result("queued"))
            val fresh=AdminForms(scope).permit()
            fresh.write {repo.commit(SupplierCommand("fresh",before.ownerId,SupplierAction.Save("s",null,"Поставщик",emptySet())))}
            assertEquals(1,repo.snapshot().suppliers.size)
        } finally {release.complete(Unit);scope.cancel();db.close()}
    }
    @Test fun admittedTransactionCompletesBeforeLogoutAndItsOutcomeRemainsReadable(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob());val admitted=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val repo=SupplierRepository(db);val owner=repo.snapshot().ownerId;val permit=AdminForms(scope).permit()
            val c=SupplierCommand("admitted",owner,SupplierAction.Save("s",null,"Поставщик",emptySet()))
            coroutineScope {
                var first=true
                val writer=async(Dispatchers.IO) {permit.write {SupplierRepository(db,afterWrite={if(first) {first=false;admitted.countDown();check(release.await(10,java.util.concurrent.TimeUnit.SECONDS))}}).commit(c)}}
                assertTrue(withContext(Dispatchers.IO) {admitted.await(10,java.util.concurrent.TimeUnit.SECONDS)})
                val logout=async(start=CoroutineStart.UNDISPATCHED) {Administration.session.logout()}
                assertFalse(logout.isCompleted);release.countDown();assertEquals("s",writer.await());logout.await()
            }
            assertEquals("s",repo.outcome(c));assertEquals(1,repo.snapshot().suppliers.size)
        } finally {release.countDown();scope.cancel();db.close()}
    }
}
