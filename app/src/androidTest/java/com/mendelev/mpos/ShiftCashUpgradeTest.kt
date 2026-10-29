package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShiftCashUpgradeTest {
    @Test fun manualCashClosedSummaryAndReplaySurviveActualApkUpdate(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="shift-cash-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val shifts=ShiftRepository(db);val cash=ShiftCashRepository(db)
            if(!verify) {
                shifts.initialize();var s=shifts.snapshot();shifts.open("open",s.revision,null,s.ownerId);s=shifts.snapshot()
                cash.commit(ShiftCashCommand("deposit",s.active!!.id,s.revision,s.ownerId,"deposit",10000,"Размен"));s=shifts.snapshot()
                cash.commit(ShiftCashCommand("withdraw",s.active!!.id,s.revision,s.ownerId,"withdrawal",500,"Инкассация"));s=shifts.snapshot()
                shifts.close("close",s.active!!.id,s.revision,ShiftRules.token(s),9400,s.ownerId);s=shifts.snapshot()
                shifts.open("next",s.revision,s.last!!.id,s.ownerId);s=shifts.snapshot()
                cash.commit(ShiftCashCommand("next-deposit",s.active!!.id,s.revision,s.ownerId,"deposit",2000,""))
            }
            val s=shifts.snapshot();assertEquals(9400L,s.active!!.openingCash);assertEquals(11400L,s.currentCash)
            val previous=db.shifts().shift(s.active!!.sourceShiftId!!)!!;assertEquals(9500L,previous.expectedCash);assertEquals(9400L,previous.countedCash);assertEquals(-100L,previous.difference)
            val rows=ShiftCashCodec.all(db);assertEquals(3,rows.size);val before=db.workspace().documents();rows.forEach {assertEquals(it.id,cash.commit(it.command))}
            assertEquals(before,db.workspace().documents());assertEquals(s,shifts.snapshot());assertEquals("Размен",rows.first().command.note)
        } finally {db.close()}
    }
}
