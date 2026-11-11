package com.mendelev.mpos

import com.mendelev.mpos.data.backup.*

/** Recovery data fixture shared by ViewModel tests; no Compose/UI dependency. */
internal fun recoveryFixture(): PreparedNativeRestore {
    val report=NativeBackupReport(12345,"a".repeat(64),1,1,1,0)
    val protection=ProtectedNativeCopy("00000000-0000-0000-0000-000000000001",1793577600000,report,"b".repeat(64),NativeSnapshotActivity(1,1,3000))
    return PreparedNativeRestore("00000000-0000-0000-0000-000000000002",report.copy(products=2),NativeSnapshotActivity(1,1,4000),protection)
}
