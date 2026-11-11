package com.mendelev.mpos
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.settings.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OperatorDictionaryAccessTest {
    @Test fun operatorCanAddAndEditButCannotDeleteOrEditDiscounts()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),CatalogDatabase::class.java).build()
        try {
            CatalogRepository(db).initialize()
            val settings=SettingsRepository(db)
            Administration.session.logout()
            val d=SettingEntry("delivery",SettingKind.DELIVERY,"Курьер",value="5")
            val label=SettingEntry("label",SettingKind.RECEIPT_LABEL,"На вечер")
            settings.save(d,0,"d-create");settings.save(d.copy(value="7"),1,"d-edit")
            settings.save(label,2,"l-create");settings.save(label.copy(name="На утро"),3,"l-edit")
            settings.save(label.copy(name="На утро"),3,"l-edit")
            assertEquals(4L,settings.snapshot().revision)
            assertEquals("7",settings.snapshot().entries.single {it.id=="delivery"}.value)
            suspend fun denied(block: suspend ()->Unit) {
                try {block();fail("Admin operation allowed")} catch(e:CatalogFailure) {assertEquals("access",e.kind)}
            }
            denied {settings.delete("delivery",4,"d-delete")}
            denied {settings.save(SettingEntry("discount",SettingKind.DISCOUNT,"Акция","percent","10"),4,"discount-create")}
            val suppliers=SupplierRepository(db);val owner=suppliers.snapshot().ownerId
            val create=SupplierCommand("s-create",owner,SupplierAction.Save("s",null,"Поставщик",emptySet()))
            suppliers.commit(create);suppliers.commit(create)
            suppliers.commit(SupplierCommand("s-edit",owner,SupplierAction.Save("s",1,"Новый поставщик",emptySet())))
            assertEquals("Новый поставщик",suppliers.snapshot().suppliers.single().name)
            denied {suppliers.commit(SupplierCommand("s-delete",owner,SupplierAction.Delete("s",2)))}
            val before=settings.snapshot()
            try {SettingsRepository(db) {error("disk")}.save(label.copy(name="Не сохранено"),4,"fault");fail()} catch(_:IllegalStateException) {}
            assertEquals(before,settings.snapshot());assertNull(db.catalog().result("fault"))
            assertFalse(Administration.session.state.value.active)
        } finally {db.close()}
    }
}
