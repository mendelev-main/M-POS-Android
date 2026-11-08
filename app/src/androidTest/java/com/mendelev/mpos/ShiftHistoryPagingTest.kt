package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShiftHistoryPagingTest {
    @Test fun pagesKeepAllAgesAndEqualTimestampsWithoutDuplicatesOrWrites(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val repo=ShiftRepository(db);repo.initialize();val s=repo.snapshot()
            val today=System.currentTimeMillis();val old=today-400L*24*60*60*1000
            repeat(23) {i->
                val at=if(i==0) old else today-1000
                db.shifts().insert(ShiftEntity("s${i.toString().padStart(2,'0')}",s.registerId,s.ownerId,"Кассир",at-1,"UTC",0,null,at,0,0,0,null))
            }
            db.shifts().register(ShiftRegisterEntity(revision=1,lastShiftId="s22"))
            val register=db.shifts().register()
            val first=repo.dashboard();assertEquals(10,first.history.size);assertNotNull(first.nextHistory)
            val second=repo.dashboard(first.nextHistory);assertEquals(10,second.history.size);assertNotNull(second.nextHistory)
            val third=repo.dashboard(second.nextHistory);assertEquals(3,third.history.size);assertNull(third.nextHistory)
            val all=first.history+second.history+third.history
            assertEquals(23,all.map {it.shift.id}.distinct().size)
            assertEquals("s22",all.first().shift.id);assertEquals("s00",all.last().shift.id)
            assertTrue(all.last().shift.closedAt!!<today-365L*24*60*60*1000)
            assertEquals(23,all.first().number);assertEquals(1,all.last().number)
            // A new latest shift cannot shift an older keyset page or duplicate its rows.
            db.shifts().insert(ShiftEntity("new",s.registerId,s.ownerId,"Кассир",today,"UTC",0,null,today+1,0,0,0,null))
            assertEquals(second.history,repo.dashboard(first.nextHistory).history)
            assertEquals(register,db.shifts().register())
            // Payload outside the requested page is not decoded; selecting it still detects invalid data.
            db.sales().insert(ReceiptEntity("bad","bad-command","bad-session","s00",1,old,0,0,0,0,"invalid fixture"))
            assertEquals(10,repo.dashboard().history.size)
            assertThrows(Exception::class.java) {runBlocking {repo.dashboard(second.nextHistory)}}
        } finally {db.close()}
    }
}
