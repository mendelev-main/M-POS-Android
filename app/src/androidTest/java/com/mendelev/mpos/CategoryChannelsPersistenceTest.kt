package com.mendelev.mpos
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Isolated in-memory database, never a live POS dataset. Run only in test environment. */
class CategoryChannelsPersistenceTest {
    @Test fun renamePreservesChannelsAndWriteFailureRollsBack()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            repo.saveCategory("c",null,"Кофе","create",channels=CategoryChannels(false,true))
            val current=repo.dao.category("c")!!
            val before=db.workspace().document(CategoryChannelsCodec.PREFIX+"c")!!
            try {
                CatalogRepository(db,afterCategoryWrite={error("injected")}).saveCategory("c",current.revision,"Напитки","rename",channels=CategoryChannels(true,false))
                fail("Expected rollback")
            } catch(_:IllegalStateException) {}
            assertEquals(current,repo.dao.category("c"));assertEquals(before,db.workspace().document(before.id));assertNull(repo.dao.result("rename"))
            repo.saveCategory("c",current.revision,"Напитки","rename")
            val saved=db.workspace().document(before.id)!!
            assertEquals(CategoryChannels(false,true),CategoryChannelsCodec.decode(saved.payload,"c"))
            repo.saveCategory("c",current.revision,"Напитки","rename")
            assertEquals(saved,db.workspace().document(before.id))
            try {repo.saveCategory("c",current.revision,"Напитки","stale",channels=CategoryChannels(true,false));fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
            assertEquals(saved,db.workspace().document(before.id))
        } finally {db.close()}
    }
}
