package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class ShiftDashboardReaderTest {
    @Test fun cashierCanReadHistoryWithoutWritesAndClosedCashIsFrozen(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val repo=ShiftRepository(db);repo.initializeCashierFixture()
            val initial=repo.snapshot();repo.open("open",initial.revision,null,initial.ownerId)
            var s=repo.snapshot();val id=s.active!!.id
            ShiftCashRepository(db).commit(ShiftCashCommand("deposit",id,s.revision,s.active!!.actorId,"deposit",1000,"Размен"))
            s=repo.snapshot();repo.close("close",id,s.revision,ShiftRules.token(s),900,s.active!!.actorId)
            val docs=db.workspace().documents();val register=db.shifts().register()
            Administration.session.logout()
            val data=repo.dashboard();val row=data.history.single()
            assertEquals(BigInteger("1000"),row.expected);assertEquals(-100L,row.shift.difference)
            assertEquals(1000L,row.movements.single().delta)
            assertEquals(register,db.shifts().register());assertEquals(docs,db.workspace().documents())
            assertEquals(data,ShiftRepository(db).dashboard())
        } finally {db.close()}
    }
}
