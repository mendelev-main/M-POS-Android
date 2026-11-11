package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ManagementContactsRepositoryTest {
    @Test fun durableContactsRejectStaleEditsAndRollbackFailuresAndRevokedSessions(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val repo=ManagementContactsRepository(db)
            val generation=Administration.session.state.value.generation
            val initial=repo.read()
            val saved=initial.copy(owner=ManagementContact("owner","+375291234567","owner@example.com","123"),
                administrators=listOf(ManagementContact("a",email="admin@example.com")))
            repo.save(saved,generation)
            val durable=ManagementContactsRepository(db).read()
            assertEquals(saved.copy(revision=1),durable)
            assertTrue(runCatching {repo.save(saved,generation)}.isFailure)
            val changed=durable.copy(owner=durable.owner.copy(phone="+375331234567"))
            assertTrue(runCatching {ManagementContactsRepository(db) {error("write failure")}.save(changed,generation)}.isFailure)
            assertEquals(durable,repo.read())
            Administration.session.logout()
            assertTrue(runCatching {repo.save(changed,generation)}.isFailure)
            assertEquals(durable,repo.read())
            assertTrue(Administration.session.login("fixture-admin".toCharArray()))
            assertTrue(runCatching {repo.save(changed,generation)}.isFailure)
            assertEquals(durable,repo.read())
        } finally {db.close()}
    }
}
