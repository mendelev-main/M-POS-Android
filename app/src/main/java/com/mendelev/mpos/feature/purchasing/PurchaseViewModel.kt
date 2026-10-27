package com.mendelev.mpos.feature.purchasing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class PurchaseUi(val snapshot: PurchaseSnapshot?=null,val form: PurchaseDraft?=null,val pending: Boolean=false,val blocked: Boolean=true,
    val unresolved: PurchaseCommand?=null,val message: String?=null) {
    val dirty: Boolean get()=form!=snapshot?.draft
}
class PurchaseViewModel(private val access: PurchaseAccess): ViewModel() {
    private val mutable=MutableStateFlow(PurchaseUi());val state=mutable.asStateFlow()
    init {refresh()}
    fun refresh() {
        if(state.value.pending || state.value.unresolved!=null || state.value.dirty) return
        mutable.update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,form=s.draft,blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(blocked=true,message="Не удалось прочитать закупки. Данные не сброшены; повторите чтение")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private fun editable()=!state.value.pending && !state.value.blocked && state.value.unresolved==null
    fun supplier(id: String) {
        if(!editable()) return
        val ui=state.value;val old=ui.form
        if(old?.supplierId==id) return
        if(old?.lines?.isNotEmpty()==true) {mutable.update {it.copy(message="Сначала уберите строки или удалите черновик")};return}
        val supplier=ui.snapshot?.suppliers?.find {it.id==id} ?: return
        mutable.update {it.copy(form=PurchaseDraft(old?.id ?: UUID.randomUUID().toString(),id,supplier.revision,supplier.name,emptyList()),message=null)}
    }
    fun line(id: String,change: (PurchaseLine)->PurchaseLine) {
        if(!editable()) return
        val ui=state.value;val d=ui.form ?: return
        val p=ui.snapshot?.products?.find {it.id==id} ?: return
        val s=ui.snapshot.suppliers.find {it.id==d.supplierId} ?: return
        if(id !in s.productIds && d.lines.none {it.productId==id}) return
        val old=d.lines.find {it.productId==id} ?: p.blankLine()
        val next=change(old)
        mutable.update {it.copy(form=d.copy(lines=if(d.lines.any {it.productId==id}) d.lines.map {if(it.productId==id) next else it} else d.lines+next),message=null)}
    }
    fun remove(id: String) {if(editable()) mutable.update {it.copy(form=it.form?.let {d->d.copy(lines=d.lines.filterNot {l->l.productId==id})},message=null)}}
    fun discard() {if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(form=it.snapshot?.draft,message=null)}}
    fun revalidate() {
        if(!editable()) return
        val before=state.value;val d=before.form ?: return
        mutable.update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {
                val s=withContext(Dispatchers.IO) {access.snapshot()}
                if(s.revision!=before.snapshot!!.revision) throw CatalogFailure("conflict","Черновик изменился в другом окне. Перечитайте его")
                val next=PurchaseDraftRules.revalidate(d,s.suppliers,s.products)
                mutable.update {it.copy(snapshot=s,form=next,message="Ссылки обновлены. Количества и упаковки сохранены; сохраните черновик")}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {mutable.update {it.copy(message=if(e is CatalogFailure) e.message else "Не удалось обновить ссылки. Ввод сохранён")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun save() {
        if(!editable()) return
        val ui=state.value;val d=ui.form ?: return;val s=ui.snapshot ?: return
        try {PurchaseDraftRules.validateReferences(d,s.suppliers,s.products)}
        catch(e:CatalogFailure) {mutable.update {it.copy(message=e.message)};return}
        submit(PurchaseCommand(UUID.randomUUID().toString(),s.ownerId,s.revision,PurchaseAction.Save(d)))
    }
    fun clear() {
        if(!editable()) return
        val s=state.value.snapshot ?: return
        if(s.draft==null) {mutable.update {it.copy(form=null,message=null)};return}
        submit(PurchaseCommand(UUID.randomUUID().toString(),s.ownerId,s.revision,PurchaseAction.Clear))
    }
    fun retry() {if(!state.value.pending) state.value.unresolved?.let(::submit)}
    private fun submit(c: PurchaseCommand) {
        mutable.update {it.copy(pending=true,unresolved=c,message=null)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {access.outcome(c) ?: access.commit(c)};saved(c)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {access.outcome(c)}!=null) saved(c)
                    else mutable.update {it.copy(unresolved=null,message=if(e is CatalogFailure) e.message else "Черновик не сохранён. Ввод сохранён; повторите действие")}
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(blocked=true,unresolved=c,message="Результат неизвестен. Проверьте ту же команду перед новым действием")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private suspend fun saved(c: PurchaseCommand) {
        val d=(c.action as? PurchaseAction.Save)?.draft
        mutable.update {it.copy(form=d,snapshot=it.snapshot?.copy(revision=Math.addExact(c.revision,1),draft=d),unresolved=null,blocked=true,message="Черновик сохранён")}
        try {val s=withContext(Dispatchers.IO) {access.snapshot()};mutable.update {it.copy(snapshot=s,form=s.draft,blocked=false,message=if(d==null) "Черновик удалён. Остатки не менялись" else "Черновик сохранён. Остатки не менялись")}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(message="Изменение сохранено. Не удалось перечитать черновик; повторите чтение")}}
    }
}
