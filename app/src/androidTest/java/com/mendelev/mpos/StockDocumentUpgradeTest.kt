package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StockDocumentUpgradeTest {
    @Test fun movementDocumentLinksSurviveActualApkReplacementWithoutMutations(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="stock-document-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            if(!verify) stockDocumentFixture(db)
            val before=db.workspace().documents();val movements=db.catalog().stockHistory("p-stock-doc");val balance=db.catalog().balance("p-stock-doc")
            assertEquals("0.0004",balance!!.quantity);assertEquals(5,movements.size)
            val reader=StockDocumentReader(db);movements.filter {it.reason!=null}.forEach {m->assertTrue(reader.read(m.id).lines.any {it.contains("Молоко stock-doc")})}
            assertEquals(before,db.workspace().documents());assertEquals(movements,db.catalog().stockHistory("p-stock-doc"));assertEquals(balance,db.catalog().balance("p-stock-doc"))
        } finally {db.close()}
    }
}
