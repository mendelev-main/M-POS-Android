package com.mendelev.mpos.core.access

import org.junit.Assert.*
import org.junit.Test

class RemotePasswordInputTest {
    @Test fun remoteAppendReplacementAndDeletePreservePrivateCharacters() {
        var value="A"
        value=RemotePasswordInput.restore(value,"•b")
        value=RemotePasswordInput.restore(value,"••9")
        assertEquals("Ab9",value)
        assertEquals("Ax9",RemotePasswordInput.restore(value,"•x•"))
        assertEquals("Ab",RemotePasswordInput.restore(value,"••"))
        assertEquals("Ab9",RemotePasswordInput.restore(value,"•••"))
        assertEquals("",RemotePasswordInput.restore(value,""))
    }
    @Test fun malformedMaskIsNeverSubmittedAndNormalTypingRemainsExact() {
        assertEquals("Ab9",RemotePasswordInput.restore("Ab9","••••x"))
        assertEquals("Ab9",RemotePasswordInput.restore("Ab9","x•y"))
        assertEquals("aB9",RemotePasswordInput.restore("Ab9","aB9"))
    }
}
