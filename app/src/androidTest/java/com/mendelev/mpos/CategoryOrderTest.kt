package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CategoryOrderTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private suspend fun entries(repo: CatalogRepository)=repo.dao.categorySnapshot().map { CategoryOrderEntry(it.id,it.revision) }
    private suspend fun seed(repo: CatalogRepository) { repo.initialize(); for(id in listOf("a","b","c")) repo.saveCategory(id,null,id,"create-$id") }
    private suspend fun reject(action: suspend () -> Unit) { try { action();fail("Expected conflict") } catch(_:CatalogFailure) {} }
    @Test fun orderIsDurableReplayableAndNewCategoriesAppendAfterDeletion() = runBlocking {
        val name="order-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            var repo=CatalogRepository(db);seed(repo)
            repo.saveProduct(ProductInput("p",null,"Чай","a","2","1","0",null,false,"product"))
            val target=entries(repo).reversed();repo.reorderCategories(target,"order");repo.reorderCategories(target,"order")
            assertEquals(listOf("c","b","a"),repo.dao.categorySnapshot().map { it.id })
            assertEquals("a",repo.dao.product("p")!!.categoryId)
            db.close();db=open();repo=CatalogRepository(db)
            assertEquals(listOf("c","b","a"),repo.dao.categorySnapshot().map { it.id })
            repo.deleteCategory("b",repo.dao.category("b")!!.revision,"delete-b")
            repo.saveCategory("d",null,"d","create-d")
            assertEquals(listOf("c","a","d"),repo.dao.categorySnapshot().map { it.id })
            assertEquals(listOf(0,2,3),repo.dao.categorySnapshot().map { it.sortOrder })
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun staleSnapshotsDuplicateIdsAndWriteFailureDoNotPartiallyReorder() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);seed(repo)
            val snapshot=entries(repo);val original=repo.dao.categorySnapshot()
            val failing=CatalogRepository(db,afterCategoryOrderWrite={throw java.io.IOException("injected")})
            try { failing.reorderCategories(snapshot.reversed(),"fault");fail() } catch(_:java.io.IOException) {}
            assertEquals(original,repo.dao.categorySnapshot());assertNull(repo.dao.result("fault"))
            reject { repo.reorderCategories(listOf(snapshot[0],snapshot[0],snapshot[2]),"duplicate") }
            repo.saveCategory("a",1,"renamed","rename-a")
            reject { repo.reorderCategories(snapshot.reversed(),"stale") }
            val current=entries(repo)
            repo.saveCategory("d",null,"d","create-d")
            reject { repo.reorderCategories(current.reversed(),"missing-new") }
            assertEquals(listOf("a","b","c","d"),repo.dao.categorySnapshot().map { it.id })
        } finally { db.close() }
    }
    @Test fun concurrentReordersOnOneSnapshotHaveOneWinner() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);seed(repo)
            val snapshot=entries(repo)
            val candidates=listOf(snapshot.reversed(),listOf(snapshot[1],snapshot[2],snapshot[0]))
            val results=coroutineScope { candidates.mapIndexed { i,order -> async(Dispatchers.IO) {
                try { repo.reorderCategories(order,"order-$i");true } catch(_:CatalogFailure) {false}
            } }.awaitAll() }
            assertEquals(1,results.count { it });assertEquals(listOf(0,1,2),repo.dao.categorySnapshot().map { it.sortOrder }.sorted())
        } finally { db.close() }
    }
}
