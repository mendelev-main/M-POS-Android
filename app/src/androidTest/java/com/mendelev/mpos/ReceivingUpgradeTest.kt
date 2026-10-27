package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReceivingUpgradeTest {
    @Test fun invoiceFactsBaselineAndOriginalSaveReplaySurviveApkReplacement(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="receiving-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val repo=ReceivingRepository(db)
            if(!verify) {
                val seed=seedReceiving(db);val s=repo.snapshot();val d=ReceivingRules.open("r",seed.order,s.products)
                repo.commit(command(s.ownerId,d))
            }
            val s=repo.snapshot();val record=s.records.single();val d=record.draft!!;val doc=db.workspace().document(ReceivingCodec.KEY)
            assertEquals(1L,record.revision);assertEquals("TTN-1",d.invoiceNumber);assertEquals("2026-10-27",d.invoiceDate)
            assertEquals("10",d.lines.single().quantity);assertEquals("1000",d.lines.single().packSize);assertEquals("ml",d.lines.single().contentUnit);assertEquals("40",d.lines.single().total)
            assertEquals("10",d.lines.single().product.stock);assertEquals("2",d.lines.single().product.cost)
            val preview=ReceivingRules.preview(d,s.orders.single(),s.products);assertEquals("20",preview.lines.single().newStock);assertEquals("3",preview.lines.single().newCost)
            assertEquals("r",repo.commit(command(s.ownerId,d)));assertEquals(doc,db.workspace().document(ReceivingCodec.KEY))
            val p=db.catalog().product(d.lines.single().product.id)!!;val b=db.catalog().balance(p.id)!!
            assertEquals(2L,p.revision);assertEquals("2",p.cost);assertEquals("5",p.price);assertEquals("1",p.packSize);assertEquals("10",b.quantity);assertEquals(1L,b.revision)
            assertEquals("pending",s.orders.single().status);assertEquals(1L,s.orders.single().revision);assertEquals(1,db.catalog().movementCount(p.id))
            assertEquals(1,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE action='receiving.draft.save'").use {it.count})
        } finally {db.close()}
    }
    private fun command(owner: String,d: ReceivingDraft)=ReceivingCommand("save",owner,d.order.id,0,d.copy(invoiceNumber="TTN-1",invoiceDate="2026-10-27",lines=d.lines.map {it.copy(total="40")}))
}
