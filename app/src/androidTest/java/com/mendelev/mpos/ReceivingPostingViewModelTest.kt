package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.receiving.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ReceivingPostingViewModelTest {
    private class Fake: ReceivingPostingAccess {
        var calls=0;var readFail=false;var outcomeFail=false;var lose=false;var reject=false
        val results=mutableMapOf<String,String>();var rows=emptyList<PostedReceiving>()
        override suspend fun history(): List<PostedReceiving> {if(readFail) error("read");return rows}
        override suspend fun outcome(c: ReceivingPostCommand): String? {if(outcomeFail) error("outcome");return results[c.commandId]}
        override suspend fun commit(c: ReceivingPostCommand): String {
            calls++;if(reject) throw com.mendelev.mpos.domain.catalog.CatalogFailure("conflict","Остаток изменился")
            val r=PostedReceiving("posted",c.commandId,c.actorId,1,c.revision,c.draft,c.overageConfirmed);rows=listOf(r);results[c.commandId]=r.id
            if(lose) {outcomeFail=true;error("reply")};return r.id
        }
    }
    private fun draft(): ReceivingDraft {
        val p=ReceivingProduct("p","Молоко","l",2,1,"10","2",false)
        val o=PurchaseOrderRules.create("o",PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","10","bottle","1000","ml"))),1)
        return ReceivingRules.open("r",o,listOf(p)).let {it.copy(lines=it.lines.map {l->l.copy(total="40")})}
    }
    private fun model(store: ViewModelStore,f: Fake)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=ReceivingPostingViewModel(f) as T
    })[ReceivingPostingViewModel::class.java]
    private suspend fun ready(vm: ReceivingPostingViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
    @Test fun unknownReplyLocksNewPostingAndRetryKeepsOriginalCommand(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake();f.lose=true
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)}
            withContext(Dispatchers.Main) {vm.post("owner",1,draft(),false);vm.post("owner",1,draft(),false)}
            val unknown=ready(vm);assertNotNull(unknown.unresolved);assertEquals(1,f.calls)
            withContext(Dispatchers.Main) {vm.acknowledge();vm.refresh();vm.post("owner",1,draft(),true)}
            assertEquals(unknown.unresolved,vm.state.value.unresolved);assertEquals(1,f.calls)
            f.outcomeFail=false;withContext(Dispatchers.Main) {vm.retry()};val result=ready(vm)
            assertNull(result.unresolved);assertEquals("posted",result.committed);assertEquals(1,f.calls)
            withContext(Dispatchers.Main) {vm.post("owner",1,draft(),false)};assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun knownCommitReadFailureRetainsSuccessAndDefiniteConflictCanBeRetried(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake();f.reject=true
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)}
            withContext(Dispatchers.Main) {vm.post("owner",1,draft(),false)};val rejected=ready(vm)
            assertNull(rejected.committed);assertNull(rejected.unresolved);assertEquals("Остаток изменился",rejected.message)
            f.reject=false;f.readFail=true
            withContext(Dispatchers.Main) {vm.post("owner",1,draft(),false)};val saved=ready(vm)
            assertEquals("posted",saved.committed);assertNull(saved.unresolved);assertTrue(saved.message!!.startsWith("Приход проведён"))
            f.readFail=false;withContext(Dispatchers.Main) {vm.refresh()};assertEquals(1,ready(vm).history.size);assertEquals("posted",vm.state.value.committed)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
}
