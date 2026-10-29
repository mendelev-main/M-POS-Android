package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdminAccessTest {
    @Test fun staleCatalogConfirmationRefusedAfterLogoutWithoutAnyWrites()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            repo.saveCategory("one",null,"Первое","create")
            val before=db.catalog().category("one")!!
            val audit=db.openHelper.readableDatabase.query("SELECT count(*) FROM audit_events").use {it.moveToFirst();it.getInt(0)}
            Administration.session.logout()
            try {repo.saveCategory("one",before.revision,"Подмена","stale");fail()} catch(e: CatalogFailure) {assertEquals("access",e.kind)}
            assertEquals(before,db.catalog().category("one"));assertNull(db.catalog().result("stale"))
            assertEquals(audit,db.openHelper.readableDatabase.query("SELECT count(*) FROM audit_events").use {it.moveToFirst();it.getInt(0)})
            assertTrue(Administration.session.login("fixture-admin".toCharArray()))
            repo.saveCategory("one",before.revision,"Новое","new")
            assertEquals("Новое",db.catalog().category("one")!!.name)
        } finally {db.close()}
    }
    @Test fun warehouseAndLayoutDeniedWhileCashierCanContinueCashOperations()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val workspace=WorkspaceRepository(db);workspace.initialize()
            val suppliers=SupplierRepository(db);val owner=suppliers.snapshot().ownerId
            val inventory=InventoryRepository(db);val stock=inventory.snapshot()
            val shifts=ShiftRepository(db);shifts.initializeCashierFixture()
            Administration.session.logout()
            suspend fun denied(action: suspend ()->Unit) {
                try {action();fail("Administrative command accepted")}
                catch(e: CatalogFailure) {assertEquals("access",e.kind)}
            }
            val documents=db.workspace().documents()
            denied {suppliers.commit(com.mendelev.mpos.domain.warehouse.SupplierCommand("denied-supplier",owner,
                com.mendelev.mpos.domain.warehouse.SupplierAction.Save("supplier",null,"Поставщик",emptySet())))}
            denied {inventory.commit(com.mendelev.mpos.domain.warehouse.InventoryCommand("denied-inventory",owner,stock.revision,
                com.mendelev.mpos.domain.warehouse.InventoryAction.Start("draft","adhoc")))}
            denied {workspace.home(com.mendelev.mpos.domain.workspace.HomeAction.Reset,workspace.snapshot().homeRevision,"denied-layout")}
            assertEquals(documents,db.workspace().documents())
            val before=shifts.snapshot();shifts.open("cashier-open",before.revision,null,owner)
            val opened=shifts.snapshot()
            shifts.cashAccess().commit(com.mendelev.mpos.domain.shift.ShiftCashCommand("cashier-deposit",opened.active!!.id,opened.revision,owner,"deposit",100,""))
            assertEquals(100L,shifts.snapshot().currentCash)
            assertFalse(Administration.session.state.value.active)
        } finally {db.close()}
    }

}
