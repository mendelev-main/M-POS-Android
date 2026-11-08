package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.settings.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SettingsRepositoryTest {
    @Test fun transactionRetryConflictFailureAndRevokedAccess(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            CatalogRepository(db).initialize()
            val repo=SettingsRepository(db)
            val row=SettingEntry("t",SettingKind.RECEIPT_LABEL,"Стол 1")
            repo.save(row,0,"save")
            repo.save(row,0,"save")
            assertEquals(1L,repo.snapshot().revision)
            assertEquals(repo.snapshot(),SettingsCodec.decode(SettingsCodec.encode(repo.snapshot()),1))
            try {repo.save(row.copy(name="Стол 2"),0,"stale");fail()} catch(e: CatalogFailure) {assertEquals("conflict",e.kind)}
            try {SettingsRepository(db) {error("disk fault")}.delete(row.id,1,"fault");fail()} catch(_: IllegalStateException) {}
            assertEquals(listOf(row),repo.snapshot().entries)
            assertNull(db.catalog().result("fault"))
            Administration.session.logout()
            try {repo.delete(row.id,1,"denied");fail()} catch(e: CatalogFailure) {assertEquals("access",e.kind)}
            assertNull(db.catalog().result("denied"))
            assertTrue(Administration.session.login("fixture-admin".toCharArray()))
            repo.delete(row.id,1,"delete")
            assertTrue(repo.snapshot().entries.isEmpty())
            assertEquals(2L,SettingsRepository(db).snapshot().revision)
        } finally {db.close()}
    }
}
