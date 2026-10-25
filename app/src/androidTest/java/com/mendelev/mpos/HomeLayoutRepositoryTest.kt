package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HomeLayoutRepositoryTest {
    @Test fun legacyInitializationFaultReplayAndReopenKeepSlotsAndCart() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="home-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val c=CatalogRepository(db);c.initialize();c.saveCategory("a",null,"Категория","cat")
            var repo=WorkspaceRepository(db);repo.initialize();val before=repo.snapshot()
            db.workspace().delete("home");repo.initialize()
            assertEquals(before.cart,repo.snapshot().cart);assertEquals(before.layout,repo.snapshot().layout)
            assertEquals(0L,repo.snapshot().homeRevision)
            repo.home(HomeAction.Move("category","a",7),0,"move");repo.home(HomeAction.Move("category","a",7),0,"move")
            val moved=repo.snapshot();assertEquals(7,moved.home.tiles.single().slot);assertEquals(1L,moved.homeRevision)
            try {WorkspaceRepository(db) {throw IllegalStateException("disk")}.home(HomeAction.Move("category","a",9),1,"fault");fail()} catch(_:IllegalStateException) {}
            assertEquals(moved,repo.snapshot());assertNull(db.catalog().result("fault"))
            try {c.deleteCategory("a",1,"delete-category");fail()} catch(_:CatalogFailure) {}
            db.close();db=open();repo=WorkspaceRepository(db);repo.initialize()
            assertEquals(moved.home,repo.snapshot().home);assertEquals(moved.cart,repo.snapshot().cart)
            val document=db.workspace().document("home")!!
            db.workspace().put(document.copy(payload="{\"version\":99}"))
            try {repo.snapshot();fail()} catch(_:CatalogFailure) {}
            assertEquals("{\"version\":99}",db.workspace().document("home")!!.payload)

        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun concurrentMoveHasOneWinnerAndHomeReferencesGuardDeletion() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),CatalogDatabase::class.java).build()
        try {
            val c=CatalogRepository(db);c.initialize();c.saveProduct(ProductInput("p",null,"Чай",null,"2","1","0",null,true,"create",noStockTracking=true))
            val repo=WorkspaceRepository(db);repo.initialize();repo.home(HomeAction.Add("product","p"),0,"add")
            val s=repo.snapshot()
            val winners=(1..2).map {n->async(Dispatchers.IO) {try {repo.home(HomeAction.Move("product","p",n+5),s.homeRevision,"move-$n");true} catch(_:CatalogFailure) {false}}}.awaitAll()
            assertEquals(1,winners.count {it})
            try {c.deleteProduct("p",1,"delete-blocked");fail()} catch(_:CatalogFailure) {}
            assertNotNull(db.catalog().product("p"))
            repo.home(HomeAction.Remove("product","p"),repo.snapshot().homeRevision,"remove")
            c.deleteProduct("p",1,"delete");assertNull(db.catalog().product("p"))
            assertEquals(s.cart,repo.snapshot().cart)
        } finally {db.close()}
    }
}
