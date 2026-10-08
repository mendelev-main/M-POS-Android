package com.mendelev.mpos.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosRootStartupTest {
    private fun begin(machine: MPosRootStartup) = machine.execute(JSONObject().put("operation", "begin"))
    private fun next(machine: MPosRootStartup, ticket: JSONObject) = machine.execute(JSONObject()
        .put("operation", "advance").put("generation", ticket.getLong("generation")).put("completed", ticket.getString("step")))

    @Test fun startupRequiresRecoveryThenHydrationThenActivation() {
        val machine = MPosRootStartup()
        var ticket = begin(machine)
        assertEquals("recover", ticket.getString("step"))
        for (expected in listOf("hydrate", "activate", "ready")) {
            ticket = next(machine, ticket)
            assertEquals(expected, ticket.getString("step"))
        }
        assertThrows(IllegalStateException::class.java) { next(machine, ticket) }
    }
    @Test fun restartInvalidatesOldCompletionAndDuplicateAcknowledgements() {
        val machine = MPosRootStartup()
        val old = begin(machine)
        val current = begin(machine)
        assertThrows(IllegalStateException::class.java) { next(machine, old) }
        assertEquals("hydrate", next(machine, current).getString("step"))
        assertThrows(IllegalStateException::class.java) { next(machine, current) }
    }
    @Test fun cannotSkipRecoveryOrInventOperations() {
        val machine = MPosRootStartup()
        val ticket = begin(machine)
        ticket.put("step", "hydrate")
        assertThrows(IllegalStateException::class.java) { next(machine, ticket) }
        assertThrows(IllegalArgumentException::class.java) { machine.execute(JSONObject().put("operation", "ready")) }
        ticket.put("step", "recover")
        assertEquals("hydrate", next(machine, ticket).getString("step"))
    }
}
