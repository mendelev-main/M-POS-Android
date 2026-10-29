package com.mendelev.mpos.domain.staff

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class EmployeeMutationsTest {
    private val anna=Employee("anna","Анна","","employee",1)
    private fun command(a: EmployeeAction)=EmployeeCommand("cmd","owner",a)
    private fun reject(block: ()->Unit) {try {block();fail()} catch(_:CatalogFailure) {}}
    @Test fun activeCashierRefusesEveryEditIncludingUnchangedFieldsAndDeletion() {
        for(action in listOf(EmployeeAction.Save("anna",1,"Анна","","employee"),EmployeeAction.Save("anna",1,"Новое имя","","employee"),
            EmployeeAction.Save("anna",1,"Анна","123","employee"),EmployeeAction.Save("anna",1,"Анна","","admin"),EmployeeAction.Delete("anna",1))) {
            reject {EmployeeMutations.apply(listOf(anna),command(action),"anna")}
        }
        val other=EmployeeMutations.apply(listOf(anna),command(EmployeeAction.Save("other",null,"Борис","","employee")),"anna")
        assertEquals(anna,other.first {it.id=="anna"});assertEquals(2,other.size)
    }
    @Test fun revisionRoleAndTombstonePreventOverwriteAndIdReuse() {
        val edited=EmployeeMutations.apply(listOf(anna),command(EmployeeAction.Save("anna",1,"Анна Новая","123","admin")),null)
        assertEquals(2L,edited.single().revision);assertFalse(edited.single().selectable)
        reject {EmployeeMutations.apply(edited,command(EmployeeAction.Delete("anna",2)),null)}
        reject {EmployeeMutations.apply(edited,command(EmployeeAction.Save("anna",1,"Старое","","employee")),null)}
        val employee=EmployeeMutations.apply(edited,command(EmployeeAction.Save("anna",2,"Анна Новая","123","employee")),null)
        val deleted=EmployeeMutations.apply(employee,command(EmployeeAction.Delete("anna",3)),null)
        assertTrue(deleted.single().deleted);assertEquals(4L,deleted.single().revision)
        reject {EmployeeMutations.apply(deleted,command(EmployeeAction.Save("anna",null,"Подмена","","employee")),null)}
        reject {EmployeeMutations.apply(deleted,command(EmployeeAction.Save("anna",4,"Подмена","","employee")),null)}
    }
}
