package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LayoutCashierAccessTest {
    @Test fun loggedOutCashierCanArrangeTilesWithReplayConflictAndRollbackProtection(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            catalog.saveCategory("c",null,"Напитки","category")
            val input=ProductInput("p",null,"Чай","c","2","1","10",null,true,"create")
            catalog.saveProduct(input)
            val repo=WorkspaceRepository(db);repo.initialize()
            Administration.session.logout()
            var s=repo.snapshot()
            repo.home(HomeAction.Add("product","p"),s.homeRevision,"add-tile")
            s=repo.snapshot();repo.home(HomeAction.Move("product","p",8),s.homeRevision,"move-tile")
            var next=repo.snapshot();assertEquals(8,next.home.tiles.single {it.id=="p"}.slot)
            repo.home(HomeAction.Move("product","p",8),s.homeRevision,"move-tile")
            assertEquals(next,repo.snapshot())
            repo.layout(LayoutAction.SaveFolder("f","c","Чай"),next.layoutRevision,"folder")
            next=repo.snapshot();repo.layout(LayoutAction.MoveProduct("p","f"),next.layoutRevision,"move-product")
            val before=repo.snapshot();assertEquals("f",before.layout.single {it.id=="p"}.folderId)
            try {repo.layout(LayoutAction.ResetProduct("p"),next.layoutRevision,"stale");fail()} catch(e:CatalogFailure) {assertEquals("conflict",e.kind)}
            try {WorkspaceRepository(db) {throw java.io.IOException("fault")}.layout(LayoutAction.ResetProduct("p"),before.layoutRevision,"fault");fail()} catch(_:java.io.IOException) {}
            assertEquals(before,repo.snapshot());assertNull(db.catalog().result("fault"))
            try {catalog.saveProduct(input.copy(expectedRevision=1,stockEdited=false,commandId="denied"));fail()} catch(e:CatalogFailure) {assertEquals("access",e.kind)}
            assertFalse(Administration.session.state.value.active)
        } finally {db.close()}
    }
}
