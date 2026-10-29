package com.mendelev.mpos

import com.mendelev.mpos.data.local.ShiftRepository
import com.mendelev.mpos.domain.staff.*

/** Business fixtures explicitly create their cashier under the privileged test session. Target APK has no bootstrap cashier. */
suspend fun ShiftRepository.initializeCashierFixture() {
    initialize()
    val access=employeeAccess();val snapshot=access.snapshot()
    if(snapshot.employees.none {it.id==snapshot.ownerId}) access.commit(EmployeeCommand("fixture-employee:${snapshot.ownerId}",snapshot.ownerId,
        EmployeeAction.Save(snapshot.ownerId,null,"Тестовый кассир","","employee")))
}
