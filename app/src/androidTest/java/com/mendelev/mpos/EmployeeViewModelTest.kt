package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.staff.*
import com.mendelev.mpos.feature.staff.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class EmployeeViewModelTest {
    private class Fake: EmployeeAccess {
        @Volatile var rows=emptyList<Employee>();@Volatile var failRead=false;@Volatile var failOutcome=false;@Volatile var lose=false;@Volatile var calls=0
        val results=mutableMapOf<String,String>();var barrier: CompletableDeferred<Unit>?=null
        override suspend fun snapshot(): EmployeeSnapshot {if(failRead) error("read");return EmployeeSnapshot("owner",rows,null)}
        override suspend fun outcome(command: EmployeeCommand): String? {if(failOutcome) error("outcome");return results[command.commandId]}
        override suspend fun commit(command: EmployeeCommand): String {
            calls++;barrier?.await()
            rows=EmployeeMutations.apply(rows,command,null);val id=(command.action as EmployeeAction.Save).id
            results[command.commandId]=id
            if(lose) {failOutcome=true;error("lost")}
            return id
        }
    }
    private suspend fun ready(vm: EmployeeViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
    private fun model(store: ViewModelStore,fake: Fake)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=EmployeeViewModel(fake) as T
    })[EmployeeViewModel::class.java]
    @Test fun lostReplyLocksEditorAndRecoversOriginalCommandWithoutDuplicate(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            f.lose=true
            withContext(Dispatchers.Main) {vm.edit();vm.change {it.copy(name="Сотрудник")};vm.save()}
            val first=ready(vm)
            assertNotNull(first.unresolved)
            assertTrue(first.blocked);val command=first.unresolved
            withContext(Dispatchers.Main) {vm.dismiss();vm.edit();vm.save();vm.change {it.copy(name="Подмена")}}
            assertEquals(command,vm.state.value.unresolved);assertEquals(1,f.calls)
            f.failOutcome=false
            withContext(Dispatchers.Main) {vm.retry()};val saved=ready(vm)
            assertNull(saved.unresolved);assertNull(saved.form);assertFalse(saved.blocked);assertEquals(1,f.calls);assertEquals(1,f.rows.size)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun invalidInputDoesNotBecomeUnknownAndDoubleClickIsSingleCommand(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.edit();vm.save()}
            assertNull(vm.state.value.unresolved);assertFalse(vm.state.value.blocked);assertEquals(0,f.calls)
            f.barrier=CompletableDeferred()
            withContext(Dispatchers.Main) {vm.change {it.copy(name="Сотрудник")};vm.save();vm.save()}
            assertTrue(vm.state.value.pending);f.barrier!!.complete(Unit)
            assertNull(ready(vm).form);assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun savedResultRemainsKnownWhenReloadFails(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.edit();vm.change {it.copy(name="Сотрудник")}}
            f.failRead=true
            withContext(Dispatchers.Main) {vm.save()};val saved=ready(vm)
            assertNull(saved.form);assertNull(saved.unresolved);assertTrue(saved.blocked);assertTrue(saved.message!!.startsWith("Изменение сохранено"))
            f.failRead=false
            withContext(Dispatchers.Main) {vm.refresh()};assertEquals(1,ready(vm).snapshot!!.employees.size)
            assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
}
