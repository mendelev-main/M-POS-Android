package com.mendelev.mpos.update

import org.junit.Assert.*
import org.junit.Test

class UpdateWindowPolicyTest {
    @Test fun readyDownloadAndActiveOperationsArePreservedOnOpen() {
        listOf(UpdateStage.READY,UpdateStage.RESTORING,UpdateStage.CHECKING,UpdateStage.DOWNLOADING,UpdateStage.VERIFYING,UpdateStage.INSTALLER).forEach {assertFalse(shouldCheckOnOpen(it,false))}
    }
    @Test fun firstCompletionReceiptIsPreservedButNormalOpenChecksAgain() {
        assertFalse(shouldCheckOnOpen(UpdateStage.IDLE,true))
        listOf(UpdateStage.IDLE,UpdateStage.CURRENT,UpdateStage.AVAILABLE,UpdateStage.ERROR).forEach {assertTrue(shouldCheckOnOpen(it,false))}
    }
}
