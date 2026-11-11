package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PurchaseWorkingRepositoryTest {
    @Test fun incompleteAutosavedInputSurvivesReopenWithoutChangingStockAndCannotBecomeOrder()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="purchase-working-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val seed=seedReceiving(db,"working")
            val before=db.catalog().product(seed.productId)!! to db.catalog().balance(seed.productId)!!
            val repo=PurchaseDraftRepository(db);val state=repo.snapshot()
            val raw=seed.order.source.copy(id="working-draft",lines=seed.order.source.lines.map {it.copy(quantity="3",packSize="0,")})
            Administration.session.logout()
            val command=PurchaseCommand("autosave-working",state.ownerId,state.revision,PurchaseAction.Working(raw))
            repo.commit(command);repo.commit(command)
            assertEquals(state.revision+1,repo.snapshot().revision)
            db.close();db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
            val reopened=PurchaseDraftRepository(db).snapshot()
            assertEquals(raw,reopened.draft)
            assertEquals(before,db.catalog().product(seed.productId)!! to db.catalog().balance(seed.productId)!!)
            try {PurchaseDraftRepository(db).commit(PurchaseCommand("invalid-place",state.ownerId,reopened.revision,PurchaseAction.Place("bad-order",raw)));fail()} catch(_:CatalogFailure) {}
            assertEquals(1,PurchaseOrderStore(db).all().size)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
