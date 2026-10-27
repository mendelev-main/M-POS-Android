package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Isolated app-private supplier fixture across the real APK replacement. */
class SupplierUpgradeTest {
    @Test fun registryLinksTombstoneAndReplaySurviveReplacement(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="supplier-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=SupplierRepository(db)
            if(!verify) {
                val catalog=CatalogRepository(db);catalog.initialize()
                catalog.saveProduct(ProductInput("p",null,"Чай",null,"2","1","0",null,true,"product"))
                val actor=repo.snapshot().ownerId
                repo.commit(SupplierCommand("save",actor,SupplierAction.Save("s",null,"Поставщик",setOf("p"))))
                repo.commit(SupplierCommand("rename",actor,SupplierAction.Save("s",1,"Новое имя",setOf("p"))))
                repo.commit(SupplierCommand("other",actor,SupplierAction.Save("deleted",null,"Удалённый",emptySet())))
                repo.commit(SupplierCommand("delete",actor,SupplierAction.Delete("deleted",1)))
            }
            val s=repo.snapshot();assertEquals(1,s.suppliers.size)
            assertEquals(Supplier("s","Новое имя",2,setOf("p")),s.suppliers.single())
            val doc=db.workspace().document(SupplierCodec.KEY)!!
            assertTrue(SupplierCodec.decode(doc.payload).single {it.id=="deleted"}.deleted)
            assertEquals("s",repo.commit(SupplierCommand("save",s.ownerId,SupplierAction.Save("s",null,"Поставщик",setOf("p")))))
            assertEquals("deleted",repo.commit(SupplierCommand("delete",s.ownerId,SupplierAction.Delete("deleted",1))))
            assertEquals(doc,db.workspace().document(SupplierCodec.KEY))
            assertEquals("0",db.catalog().balance("p")!!.quantity);assertEquals("1",db.catalog().product("p")!!.cost)
            assertEquals(0,db.catalog().movementCount("p"))
            assertEquals(4,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE action LIKE 'supplier.%'").use {it.count})
        } finally {db.close()}
    }
}
