package com.mendelev.mpos

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.feature.staff.*
import com.mendelev.mpos.feature.shift.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class EmployeeUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun createOptionalPhoneSelectCashierLockActiveAndRevokeStaleEditor() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        val store=ViewModelStore()
        lateinit var staff: EmployeeViewModel;lateinit var shift: ShiftViewModel
        try {
            compose.runOnIdle {
                val provider=ViewModelProvider(store,object: ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(c: Class<T>): T =if(c==EmployeeViewModel::class.java) EmployeeViewModel(EmployeeRepository(db)) as T else ShiftViewModel(ShiftRepository(db)) as T
                })
                staff=provider[EmployeeViewModel::class.java];shift=provider[ShiftViewModel::class.java]
            }
            val route=mutableStateOf("shift")
            compose.setContent {MposTheme {if(route.value=="shift") ShiftScreen(shift,{},{},{route.value="staff"}) else EmployeeScreen(staff) {route.value="shift"}}}
            compose.waitUntil(20_000) {!shift.state.value.loading && !shift.state.value.staffLoading}
            compose.onNodeWithTag("shiftOpen").assertIsNotEnabled()
            compose.onNodeWithTag("shiftSetupEmployees").performScrollTo().performClick()
            compose.waitUntil(20_000) {!staff.state.value.pending && !staff.state.value.blocked}
            compose.onNodeWithTag("employeeNew").performClick()
            compose.onNodeWithTag("employeeName").performTextInput("Иванова Анна")
            shot("employee-card")
            compose.onNodeWithTag("employeeSave").performScrollTo().performClick()
            compose.waitUntil(20_000) {!staff.state.value.pending && staff.state.value.form==null && staff.state.value.snapshot?.employees?.size==1}
            val anna=staff.state.value.snapshot!!.employees.single();assertEquals("",anna.phone)
            shot("employee-list")
            compose.runOnIdle {route.value="shift"}
            compose.waitUntil(20_000) {!shift.state.value.staffLoading && shift.state.value.cashiers.any {it.id==anna.id}}
            compose.onNodeWithTag("shiftOpen").performScrollTo().performClick()
            compose.onNodeWithTag("shiftEmployee:${anna.id}").assertIsSelected();shot("employee-shift-select")
            compose.onNodeWithTag("shiftConfirm").performClick()
            compose.waitUntil(20_000) {!shift.state.value.pending && shift.state.value.snapshot?.active?.actorId==anna.id}
            compose.onNodeWithTag("shiftCashier").assertTextEquals("Иванова Анна")
            compose.runOnIdle {route.value="staff"}
            compose.waitUntil(20_000) {!staff.state.value.pending && staff.state.value.snapshot?.activeActorId==anna.id}
            compose.onNodeWithTag("employee:${anna.id}").assertIsNotEnabled()
            compose.onNodeWithTag("employeeNew").performClick();compose.onNodeWithTag("employeeName").performTextInput("Несохранённое имя")
            runBlocking {Administration.session.logout()}
            compose.waitUntil(20_000) {staff.state.value.form==null}
            compose.onNodeWithTag("employeeSave").assertDoesNotExist();compose.onNodeWithTag("employeeNew").assertIsNotEnabled()
            assertEquals(1,runBlocking {EmployeeRepository(db).snapshot().employees.size})
            assertEquals(anna.id,runBlocking {ShiftRepository(db).snapshot().active!!.actorId})
        } finally {compose.runOnIdle {store.clear()};db.close()}
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file=File(instrumentation.targetContext.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
