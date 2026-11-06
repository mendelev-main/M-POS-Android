package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebStaffMappingTest {
    private fun source()=JSONObject(javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().use {it.readText()})
    private fun mapped(j: JSONObject)=WebStaffMapping.map(WebBackupReader.read(j.toString().byteInputStream()))
    private fun shift(j: JSONObject)=j.getJSONArray("shifts").getJSONObject(0)
    @Test fun currentEmployeeDoesNotReplaceHistoricalNameAndRolesDoNotGrantSession() {
        val j=source();val before=j.toString();val f=mapped(j)
        assertTrue(f.employees.single().selectable);assertNull(f.shifts.single().actorName)
        assertTrue(f.shifts.single().blockers.contains("historicalActorNameUnknown"))
        assertEquals(f.employees.single().id,f.shifts.single().actorId);assertEquals(before,j.toString())
        j.getJSONArray("employees").getJSONObject(0).put("name","Новое имя").put("role","admin")
        shift(j).put("employeeName","Имя при открытии")
        val updated=mapped(j);assertFalse(updated.employees.single().selectable)
        assertEquals("Имя при открытии",updated.shifts.single().actorName)
        assertTrue(updated.shifts.single().blockers.contains("activeCashierUnavailable"))
    }
    @Test fun closedShiftMissingExpectedIsNotRecalculatedFromCurrentProducts() {
        val j=source();shift(j).put("status","closed").put("closedAt",10).put("countedCash","10.25")
        val f=mapped(j).shifts.single();assertEquals(1025L,f.countedCash);assertNull(f.expectedCash);assertNull(f.difference)
        assertTrue(f.blockers.contains("expectedCashNeedsHistory"));assertEquals(10L,f.closedAt)
    }
    @Test fun cashMovementsKeepExactSignedDirectionSourceIdentityAndTimestamp() {
        val j=source();shift(j).put("cashMovements",JSONArray().put(JSONObject().put("id","movement").put("type","withdrawal").put("subtype","refund").put("amount","10.25").put("timestamp",2).put("note","Тест")))
        val f=mapped(j).shifts.single().movements.single()
        assertEquals(1025L,f.amount);assertEquals("withdrawal",f.type);assertEquals("refund",f.subtype)
        assertEquals(2L,f.timestamp);assertEquals("movement",f.sourceId);assertEquals(f,mapped(j).shifts.single().movements.single())
    }
    @Test fun invalidClosureMoneyDuplicateOpenAndUnknownRoleRefuse() {
        for(edit in listOf<(JSONObject)->Unit>(
            {shift(it).put("closedAt",10)},
            {shift(it).put("status","closed").put("closedAt",0)},
            {shift(it).put("openingCash","0.001")},
            {it.getJSONArray("shifts").put(JSONObject(shift(it).toString()).put("id","other"))},
            {it.getJSONArray("employees").getJSONObject(0).put("role","superuser")},
            {shift(it).put("status","closed").put("closedAt",2).put("countedCash",10).put("expectedCash",9).put("difference",0)})) {
            val j=source();edit(j);assertThrows(BackupFailure::class.java) {mapped(j)}
        }
    }
    @Test fun missingActorAndTimeRemainVisibleInsteadOfUsingLocalOwnerOrNow() {
        val j=source();shift(j).remove("employeeId");shift(j).remove("openedAt")
        val f=mapped(j).shifts.single();assertNull(f.actorId);assertNull(f.openedAt)
        assertTrue(f.blockers.containsAll(setOf("actorMissing","openedAtUnknown","activeCashierUnavailable")))
    }
}
