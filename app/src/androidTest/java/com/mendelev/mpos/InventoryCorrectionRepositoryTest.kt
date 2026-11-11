package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InventoryCorrectionRepositoryTest {
    @Test fun correctionRetainsLaterStockAndReplaysWithoutDuplicateMovement(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            catalog.saveProduct(ProductInput("p",null,"Товар",null,"2","1","12",null,true,"create"))
            val repo=InventoryRepository(db,generation="fixture")
            var s=repo.snapshot()
            suspend fun write(id: String,action: InventoryAction) {s=repo.snapshot();repo.commit(InventoryCommand(id,s.ownerId,s.revision,action))}
            write("config",InventoryAction.Configure(InventoryConfig(true,"monthly",setOf("p"))))
            write("start",InventoryAction.Start("draft","adhoc"));s=repo.snapshot()
            write("count",InventoryAction.Count("draft",s.products.single(),"10",s.generation))
            write("fix",InventoryAction.Fix("draft","p",s.generation))
            val p=db.catalog().product("p")!!;val b=db.catalog().balance("p")!!
            catalog.saveProduct(ProductInput("p",p.revision,p.name,p.categoryId,p.price,p.cost,"8",b.revision,true,"later-stock",unit=p.unit))
            s=repo.snapshot();val command=InventoryCommand("correct",s.ownerId,s.revision,InventoryAction.Correct("draft","p","10","11"))
            val before=db.catalog().movementCount("p")
            for(fault in 1..5) {
                var writes=0
                val failing=InventoryRepository(db,afterWrite={if(++writes==fault) error("injected")},generation="fixture")
                assertTrue(runCatching {failing.commit(command)}.isFailure)
                assertEquals("8",db.catalog().balance("p")!!.quantity)
                assertEquals(before,db.catalog().movementCount("p"))
                assertTrue(repo.snapshot().document.draft!!.counts.single().corrections.isEmpty())
            }
            repo.commit(command);repo.commit(command)
            assertEquals("9",db.catalog().balance("p")!!.quantity)
            assertEquals(before+1,db.catalog().movementCount("p"))
            val c=repo.snapshot().document.draft!!.counts.single()
            assertEquals("10",c.fixed!!.actual);assertEquals("11",c.effectiveActual)
            assertEquals(1,c.corrections.size)
            assertTrue(runCatching {repo.commit(command.copy(commandId="stale",revision=repo.snapshot().revision))}.isFailure)
            write("finish",InventoryAction.Finish("draft",false))
            val history=repo.snapshot().document
            assertEquals(history,InventoryCodec.decode(InventoryCodec.encode(history)))
            assertEquals("11",history.history.single().draft.counts.single().effectiveActual)
        } finally {db.close()}
    }
}
