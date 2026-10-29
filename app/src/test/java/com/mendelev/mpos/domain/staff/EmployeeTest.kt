package com.mendelev.mpos.domain.staff

import com.mendelev.mpos.data.local.EmployeeCodec
import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EmployeeTest {
    private val worker=Employee("anna","Иванова Анна","","employee",1)
    private fun save()=EmployeeCommand("cmd","owner",EmployeeAction.Save("anna",null," Иванова Анна "," +375 00 000-00-00 ","employee"))
    private fun rejected(block: ()->Unit) {try {block();fail("Invalid employee accepted")} catch(_: IllegalArgumentException) {}}
    @Test fun optionalPhoneExactRolesAndNormalizedInput() {
        val c=save().validated().action as EmployeeAction.Save
        assertEquals("Иванова Анна",c.name);assertEquals("+375 00 000-00-00",c.phone)
        assertEquals("",EmployeeRules.phone("  "))
        assertTrue(worker.selectable);assertFalse(worker.copy(role="admin").selectable);assertFalse(worker.copy(deleted=true).selectable)
        rejected {EmployeeRules.name("  ")};rejected {EmployeeRules.name("A\nB")};rejected {EmployeeRules.role("owner")}
        rejected {EmployeeRules.id(" anna ")};rejected {EmployeeRules.phone("1".repeat(65))};rejected {EmployeeRules.id("bad\u0000")}
    }
    @Test fun fingerprintsBindIdentityRevisionDetailsAndRole() {
        val c=save();val a=c.action as EmployeeAction.Save;val fp=c.fingerprint()
        assertEquals(fp,c.copy(commandId="another").fingerprint())
        listOf(c.copy(actorId="other"),c.copy(action=a.copy(id="other")),c.copy(action=a.copy(revision=1)),
            c.copy(action=a.copy(name="Другое имя")),c.copy(action=a.copy(phone="")),c.copy(action=a.copy(role="admin")),
            c.copy(action=EmployeeAction.Delete("anna",1))).forEach {assertNotEquals(fp,it.fingerprint())}
    }
    @Test fun unicodeAndHyphenatedNamePresentationDoesNotChangeIdentity() {
        assertEquals("Иванова А.-М. И.",EmployeeRules.displayName("Иванова Анна-Мария Ивановна"))
        assertEquals("Анна",EmployeeRules.displayName(" Анна "));assertEquals("Сотрудник",EmployeeRules.displayName(""))
        EmployeeRules.validate(worker.copy(name="😀 "+"А".repeat(198)))
        rejected {EmployeeRules.validate(worker.copy(name="😀 "+"А".repeat(199)))}
    }
    @Test fun codecRoundTripPreservesReservedDeletedIdentitiesAndRevision() {
        val rows=listOf(worker,worker.copy(id="former",deleted=true,revision=7),worker.copy(id="admin",role="admin"))
        assertEquals(rows,EmployeeCodec.decode(EmployeeCodec.encode(rows)))
        assertEquals(emptyList<Employee>(),EmployeeCodec.decode(EmployeeCodec.encode(emptyList())))
        rejected {EmployeeCodec.encode(rows+worker)}
    }
    @Test fun corruptUnknownAndCoercedPayloadsAreRefusedWithoutAnEmptyFallback() {
        val raw=EmployeeCodec.encode(listOf(worker))
        val bad=listOf("broken",JSONObject(raw).put("version",2).toString(),JSONObject(raw).put("version","1").toString(),
            JSONObject(raw).also {it.getJSONArray("items").getJSONObject(0).put("revision","1")}.toString(),
            JSONObject(raw).also {it.getJSONArray("items").getJSONObject(0).put("deleted","false")}.toString(),
            JSONObject(raw).also {it.getJSONArray("items").getJSONObject(0).put("name",55)}.toString(),
            JSONObject(raw).also {it.getJSONArray("items").put(it.getJSONArray("items").getJSONObject(0))}.toString())
        bad.forEach {value->try {EmployeeCodec.decode(value);fail("Corrupt directory accepted")} catch(e: CatalogFailure) {assertEquals("employeeFormat",e.kind)}}
        assertEquals(listOf(worker),EmployeeCodec.decode(raw))
    }
}
