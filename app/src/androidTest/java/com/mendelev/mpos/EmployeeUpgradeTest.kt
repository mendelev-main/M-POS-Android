package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.staff.*
import com.mendelev.mpos.domain.shift.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EmployeeUpgradeTest {
    @Test fun directoryHistoricalCashierAndCommandReplaysSurviveActualApkUpdate(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="employee-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val shifts=ShiftRepository(db);shifts.initialize();val repo=EmployeeRepository(db);val owner=repo.snapshot().ownerId
            val create=EmployeeCommand("create",owner,EmployeeAction.Save("anna",null,"Анна Иванова","","employee"))
            val rename=EmployeeCommand("rename",owner,EmployeeAction.Save("anna",1,"Анна Новая","+375 00 000-00-00","employee"))
            if(!verify) {
                repo.commit(create);shifts.open("open",0,null,"anna");var s=shifts.snapshot()
                ShiftCashRepository(db).commit(ShiftCashCommand("deposit",s.active!!.id,s.revision,"anna","deposit",100,""));s=shifts.snapshot()
                shifts.close("close",s.active!!.id,s.revision,ShiftRules.token(s),100,"anna");repo.commit(rename)
                repo.commit(EmployeeCommand("admin",owner,EmployeeAction.Save("admin",null,"Управляющий","","admin")))
                repo.commit(EmployeeCommand("other",owner,EmployeeAction.Save("other",null,"Бывший сотрудник","","employee")))
                repo.commit(EmployeeCommand("delete",owner,EmployeeAction.Delete("other",1)))
                s=shifts.snapshot();shifts.open("next",s.revision,s.last!!.id,"anna")
            }
            val s=shifts.snapshot();val directory=repo.snapshot();val docs=db.workspace().documents()
            assertEquals(2,directory.employees.size);assertEquals(2L,directory.employees.single {it.id=="anna"}.revision)
            assertEquals("+375 00 000-00-00",directory.employees.single {it.id=="anna"}.phone)
            assertEquals("admin",directory.employees.single {it.id=="admin"}.role)
            assertTrue(EmployeeCodec.decode(db.workspace().document(EmployeeCodec.KEY)!!.payload).single {it.id=="other"}.deleted)
            assertEquals("anna",s.active!!.actorId);assertEquals("Анна Новая",s.active!!.actorName);assertEquals(100L,s.currentCash)
            val old=db.shifts().shift(s.active!!.sourceShiftId!!)!!
            assertEquals("anna",old.actorId);assertEquals("Анна Иванова",old.actorName)
            assertEquals("anna",repo.commit(create));assertEquals("anna",repo.commit(rename))
            assertEquals(old.id,shifts.open("open",0,null,"anna"))
            assertEquals(docs,db.workspace().documents());assertEquals(s,shifts.snapshot())
        } finally {db.close()}
    }
}
