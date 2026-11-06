package com.mendelev.mpos.feature.purchasing

import com.mendelev.mpos.core.access.AdminForms
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class PurchaseEdit(val baseline: PurchaseOrder,val initial: PurchaseDraft,val form: PurchaseDraft) {
    val dirty: Boolean get()=form!=initial
}
data class PurchaseUi(val snapshot: PurchaseSnapshot?=null,val form: PurchaseDraft?=null,val pending: Boolean=false,val blocked: Boolean=true,
    val unresolved: PurchaseCommand?=null,val message: String?=null,val lastPlacedId: String?=null,val editing: PurchaseEdit?=null,val orderUnresolved: PurchaseOrderCommand?=null,val orderReloadNeeded: Boolean=false,val orderCommitted: Boolean=false,val canEdit: Boolean=false) {
    val dirty: Boolean get()=form!=null && form!=snapshot?.draft
}
class PurchaseViewModel(private val access: PurchaseAccess): ViewModel() {
    private val mutable=MutableStateFlow(PurchaseUi());val state=mutable.asStateFlow()
    private val forms=AdminForms(viewModelScope)
    private fun revoke() {mutable.update {it.copy(form=null,editing=null,canEdit=false)}}
    private fun update(change: (PurchaseUi)->PurchaseUi) {mutable.update {old->change(old).let {if(forms.current()) it.copy(canEdit=true) else it.copy(form=null,editing=null,canEdit=false)}}}
    init {forms.watch(::revoke);refresh()}
    fun refresh() {
        if(state.value.pending || state.value.unresolved!=null || state.value.orderUnresolved!=null || state.value.editing!=null || state.value.dirty) return
        forms.reopen(::revoke)
        update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot()};update {it.copy(snapshot=s,form=s.draft,blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {update {it.copy(blocked=true,message="Не удалось прочитать закупки. Данные не сброшены; повторите чтение")}}
            finally {update {it.copy(pending=false)}}
        }
    }
    private fun available()=forms.current() && !state.value.pending && !state.value.blocked && state.value.unresolved==null && state.value.orderUnresolved==null
    private fun editable()=available() && state.value.editing==null
    fun supplier(id: String) {
        if(!editable()) return
        val ui=state.value;val old=ui.form
        if(old?.supplierId==id) return
        if(old?.lines?.isNotEmpty()==true) {update {it.copy(message="Сначала уберите строки или удалите черновик")};return}
        val supplier=ui.snapshot?.suppliers?.find {it.id==id} ?: return
        update {it.copy(form=PurchaseDraft(old?.id ?: UUID.randomUUID().toString(),id,supplier.revision,supplier.name,emptyList()),message=null)}
    }
    fun line(id: String,change: (PurchaseLine)->PurchaseLine) {
        if(!editable()) return
        val ui=state.value;val d=ui.form ?: return
        val p=ui.snapshot?.products?.find {it.id==id} ?: return
        val s=ui.snapshot.suppliers.find {it.id==d.supplierId} ?: return
        if(id !in s.productIds && d.lines.none {it.productId==id}) return
        val old=d.lines.find {it.productId==id} ?: p.blankLine()
        val next=change(old)
        update {it.copy(form=d.copy(lines=if(d.lines.any {it.productId==id}) d.lines.map {if(it.productId==id) next else it} else d.lines+next),message=null)}
    }
    fun remove(id: String) {if(editable()) update {it.copy(form=it.form?.let {d->d.copy(lines=d.lines.filterNot {l->l.productId==id})},message=null)}}
    fun discard() {if(!state.value.pending && state.value.unresolved==null && state.value.orderUnresolved==null && state.value.editing==null) update {it.copy(form=it.snapshot?.draft,message=null)}}
    fun revalidate() {
        if(!editable()) return
        val before=state.value;val d=before.form ?: return
        update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {
                val s=withContext(Dispatchers.IO) {access.snapshot()}
                if(s.revision!=before.snapshot!!.revision) throw CatalogFailure("conflict","Черновик изменился в другом окне. Перечитайте его")
                val next=PurchaseDraftRules.revalidate(d,s.suppliers,s.products)
                update {it.copy(snapshot=s,form=next,message="Ссылки обновлены. Количества и упаковки сохранены; сохраните черновик")}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {update {it.copy(message=if(e is CatalogFailure) e.message else "Не удалось обновить ссылки. Ввод сохранён")}}
            finally {update {it.copy(pending=false)}}
        }
    }
    fun save() {
        if(!editable()) return
        val ui=state.value;val d=ui.form ?: return;val s=ui.snapshot ?: return
        try {PurchaseDraftRules.validateReferences(d,s.suppliers,s.products)}
        catch(e:CatalogFailure) {update {it.copy(message=e.message)};return}
        submit(PurchaseCommand(UUID.randomUUID().toString(),s.ownerId,s.revision,PurchaseAction.Save(d)))
    }
    fun openEdit(order: PurchaseOrder) {
        if(!editable()) return
        val s=state.value.snapshot ?: return
        try {
            val d=PurchaseOrderMutationRules.open(order,s.suppliers,s.products)
            update {it.copy(editing=PurchaseEdit(order,d,d),message=null,lastPlacedId=null)}
        } catch(e:CatalogFailure) {update {it.copy(message=e.message)}}
    }
    fun editLine(id: String,change: (PurchaseLine)->PurchaseLine) {
        if(!available()) return
        val ui=state.value;val e=ui.editing ?: return;val s=ui.snapshot ?: return
        val p=s.products.find {it.id==id} ?: return
        if(id !in s.suppliers.find {it.id==e.form.supplierId}?.productIds.orEmpty() && e.form.lines.none {it.productId==id}) return
        val line=e.form.lines.find {it.productId==id} ?: p.blankLine()
        val changed=change(line)
        update {it.copy(editing=e.copy(form=e.form.copy(lines=if(e.form.lines.any {it.productId==id}) e.form.lines.map {if(it.productId==id) changed else it} else e.form.lines+changed)),message=null)}
    }
    fun removeEditLine(id: String) {if(available()) update {it.copy(editing=it.editing?.let {e->e.copy(form=e.form.copy(lines=e.form.lines.filterNot {l->l.productId==id}))},message=null)}}
    fun discardEdit() {if(!state.value.pending && state.value.orderUnresolved==null) update {it.copy(editing=null,message=null)}}
    fun revalidateEdit() {
        if(!available()) return
        val e=state.value.editing ?: return
        update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {
                val s=withContext(Dispatchers.IO) {access.snapshot()}
                if(s.orders.find {it.id==e.baseline.id}?.revision!=e.baseline.revision) throw CatalogFailure("conflict","Заказ изменился. Закройте редактор и перечитайте историю; ввод сохранён")
                val d=PurchaseDraftRules.revalidate(e.form,s.suppliers,s.products)
                update {it.copy(snapshot=it.snapshot?.copy(products=s.products,suppliers=s.suppliers,orders=s.orders),editing=e.copy(form=d),message="Ссылки обновлены. Количества сохранены")}
            } catch(ex:CancellationException) {throw ex}
            catch(ex:Exception) {update {it.copy(message=if(ex is CatalogFailure) ex.message else "Не удалось обновить ссылки. Ввод сохранён")}}
            finally {update {it.copy(pending=false)}}
        }
    }
    fun saveEdit() {
        if(!available()) return
        val ui=state.value;val e=ui.editing ?: return;val s=ui.snapshot ?: return
        val c=PurchaseOrderCommand(UUID.randomUUID().toString(),s.ownerId,e.baseline.id,e.baseline.revision,PurchaseOrderAction.Edit(e.form))
        try {c.validate();PurchaseDraftRules.validateReferences(e.form,s.suppliers,s.products)}
        catch(ex:CatalogFailure) {update {it.copy(message=ex.message)};return}
        mutate(c)
    }
    fun cancelOrder(o: PurchaseOrder) {
        if(!editable()) return
        val s=state.value.snapshot ?: return
        mutate(PurchaseOrderCommand(UUID.randomUUID().toString(),s.ownerId,o.id,o.revision,PurchaseOrderAction.Cancel))
    }
    fun retryOrder() {if(!state.value.pending) state.value.orderUnresolved?.let(::mutate)}
    private fun mutate(c: PurchaseOrderCommand) {
        val permit=try {forms.command(c.commandId)} catch(e:CatalogFailure) {update {it.copy(message=e.message)};return}
        update {it.copy(pending=true,orderUnresolved=c,message=null)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {access.mutationOutcome(c) ?: permit.write {access.mutate(c)}};orderSaved(c)}
            catch(ex:CancellationException) {throw ex}
            catch(ex:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {access.mutationOutcome(c)}!=null) orderSaved(c)
                    else update {it.copy(orderUnresolved=null,message=if(ex is CatalogFailure) ex.message else "Изменение заказа не сохранено. Ввод сохранён")}
                } catch(ex2:CancellationException) {throw ex2}
                catch(_:Exception) {update {it.copy(blocked=true,orderUnresolved=c,message="Результат неизвестен. Проверьте ту же команду перед новым действием")}}
            } finally {update {it.copy(pending=false)}}
        }
    }
    private suspend fun orderSaved(c: PurchaseOrderCommand) {
        update {it.copy(orderUnresolved=null,editing=null,blocked=true,orderReloadNeeded=true,orderCommitted=true,lastPlacedId=c.orderId,message="Изменение заказа сохранено")}
        readOrders()
    }
    private suspend fun readOrders() {
        try {
            val s=withContext(Dispatchers.IO) {access.snapshot()}
            update {it.copy(snapshot=it.snapshot?.copy(orders=s.orders,products=s.products,suppliers=s.suppliers),blocked=false,orderReloadNeeded=false,orderCommitted=false,message=if(it.orderCommitted) "Изменение заказа сохранено. Остатки не менялись" else "История закупок обновлена")}
        } catch(ex:CancellationException) {throw ex}
        catch(_:Exception) {update {it.copy(blocked=true,orderReloadNeeded=true,message=if(it.orderCommitted) "Изменение заказа сохранено. Не удалось перечитать историю; повторите чтение" else "Не удалось прочитать историю. Ввод сохранён; повторите чтение")}}
    }
    fun refreshOrders() {
        if(state.value.pending || state.value.orderUnresolved!=null || state.value.unresolved!=null || state.value.editing!=null) return
        update {it.copy(pending=true)}
        viewModelScope.launch {try {readOrders()} finally {update {it.copy(pending=false)}}}
    }
    fun dismissPlaced() {if(!state.value.pending) update {it.copy(lastPlacedId=null)}}
    fun place() {
        if(!editable()) return
        val ui=state.value;val s=ui.snapshot ?: return;val d=ui.form ?: return
        if(ui.dirty || s.draft==null) {update {it.copy(message="Сначала сохраните текущий черновик")};return}
        val c=PurchaseCommand(UUID.randomUUID().toString(),s.ownerId,s.revision,PurchaseAction.Place(UUID.randomUUID().toString(),d))
        try {c.validate();PurchaseDraftRules.validateReferences(d,s.suppliers,s.products)}
        catch(e:CatalogFailure) {update {it.copy(message=e.message)};return}
        submit(c)
    }
    fun clear() {
        if(!editable()) return
        val s=state.value.snapshot ?: return
        if(s.draft==null) {update {it.copy(form=null,message=null)};return}
        submit(PurchaseCommand(UUID.randomUUID().toString(),s.ownerId,s.revision,PurchaseAction.Clear))
    }
    fun retry() {if(!state.value.pending) state.value.unresolved?.let(::submit)}
    private fun submit(c: PurchaseCommand) {
        val permit=try {forms.command(c.commandId)} catch(e:CatalogFailure) {update {it.copy(message=e.message)};return}
        update {it.copy(pending=true,unresolved=c,message=null)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {access.outcome(c) ?: permit.write {access.commit(c)}};saved(c)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {access.outcome(c)}!=null) saved(c)
                    else update {it.copy(unresolved=null,message=if(e is CatalogFailure) e.message else "Черновик не сохранён. Ввод сохранён; повторите действие")}
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {update {it.copy(blocked=true,unresolved=c,message="Результат неизвестен. Проверьте ту же команду перед новым действием")}}
            } finally {update {it.copy(pending=false)}}
        }
    }
    private suspend fun saved(c: PurchaseCommand) {
        val d=(c.action as? PurchaseAction.Save)?.draft
        val placed=(c.action as? PurchaseAction.Place)?.orderId
        update {it.copy(form=d,snapshot=it.snapshot?.copy(revision=Math.addExact(c.revision,1),draft=d),unresolved=null,blocked=true,lastPlacedId=placed ?: it.lastPlacedId,message=if(placed!=null) "Заказ сформирован" else "Черновик сохранён")}
        try {val s=withContext(Dispatchers.IO) {access.snapshot()};update {it.copy(snapshot=s,form=s.draft,blocked=false,message=if(placed!=null) "Заказ сформирован. Ожидается приёмка; остатки не менялись" else if(d==null) "Черновик удалён. Остатки не менялись" else "Черновик сохранён. Остатки не менялись")}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {update {it.copy(message="Изменение сохранено. Не удалось перечитать черновик; повторите чтение")}}
    }
}
