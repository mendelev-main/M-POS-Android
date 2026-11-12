package com.mendelev.mpos.feature.purchasing

import com.mendelev.mpos.core.access.AdminForms
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class PurchaseEdit(val baseline: PurchaseOrder,val initial: PurchaseDraft,val form: PurchaseDraft) {
    val dirty: Boolean get()=form!=initial
}
data class PurchaseUi(val snapshot: PurchaseSnapshot?=null,val form: PurchaseDraft?=null,val pending: Boolean=false,val blocked: Boolean=true,
    val unresolved: PurchaseCommand?=null,val message: String?=null,val lastPlacedId: String?=null,val editing: PurchaseEdit?=null,val orderUnresolved: PurchaseOrderCommand?=null,val orderReloadNeeded: Boolean=false,val orderCommitted: Boolean=false,val canEdit: Boolean=false,val saving: Boolean=false,val autoError: String?=null) {
    val dirty: Boolean get()=snapshot!=null && form!=snapshot.draft
}
class PurchaseViewModel(private val access: PurchaseAccess,private val autoSaveEnabled: Boolean=true): ViewModel() {
    private val mutable=MutableStateFlow(PurchaseUi());val state=mutable.asStateFlow()
    private val forms=AdminForms(viewModelScope) // Administrative cancellation only.
    private fun update(change: (PurchaseUi)->PurchaseUi) {mutable.update {change(it).copy(canEdit=true)}}
    private val writes=Mutex()
    private var autoJob: Job?=null
    private var autoCommand: PurchaseCommand?=null
    private suspend fun completeAuto(command: PurchaseCommand) {
        withContext(Dispatchers.IO) {access.outcome(command) ?: access.commit(command)}
        val draft=(command.action as PurchaseAction.Working).draft
        update {it.copy(snapshot=it.snapshot?.copy(revision=Math.addExact(command.revision,1),draft=draft),autoError=null)}
        autoCommand=null
    }
    private fun autosave() {
        if(!autoSaveEnabled || autoJob?.isActive==true) return
        autoJob=viewModelScope.launch {
            update {it.copy(saving=true)}
            try {
                while(true) {
                    val again=writes.withLock {
                        val ui=state.value
                        if(ui.pending || ui.unresolved!=null || ui.orderUnresolved!=null) return@withLock false
                        val snapshot=ui.snapshot ?: return@withLock false
                        if(autoCommand==null && ui.form==snapshot.draft) return@withLock false
                        val command=autoCommand ?: PurchaseCommand(UUID.randomUUID().toString(),snapshot.ownerId,snapshot.revision,PurchaseAction.Working(ui.form))
                        autoCommand=command
                        completeAuto(command)
                        true
                    }
                    if(!again) break
                }
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {update {it.copy(autoError="Не удалось автоматически сохранить заказ. Ввод сохранён в открытом приложении; повторите сохранение.")}}
            finally {update {it.copy(saving=false)}}
        }
    }
    fun retryAutosave()=autosave()
    init {refresh()}
    fun refresh() {
        if(state.value.pending || state.value.saving || state.value.unresolved!=null || state.value.orderUnresolved!=null || state.value.editing!=null || state.value.dirty) return
        update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {val s=withContext(Dispatchers.IO) {access.snapshot()};update {it.copy(snapshot=s,form=s.draft,blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {update {it.copy(blocked=true,message="Не удалось прочитать закупки. Данные не сброшены; повторите чтение")}}
            finally {update {it.copy(pending=false)}}
        }
    }
    private fun available()=!state.value.pending && !state.value.blocked && state.value.unresolved==null && state.value.orderUnresolved==null
    private fun editable()=available() && state.value.editing==null
    fun supplier(id: String) {
        if(!editable()) return
        val ui=state.value;val old=ui.form
        if(old?.supplierId==id) return
        if(old!=null && PurchaseDraftRules.hasRequestedLines(old)) {update {it.copy(message="Сначала уберите товары из текущего заказа")};return}
        if(id.isBlank()) {update {it.copy(form=null,message=null)};autosave();return}
        val supplier=ui.snapshot?.suppliers?.find {it.id==id} ?: return
        update {it.copy(form=PurchaseDraft(old?.id ?: ui.snapshot?.draft?.id ?: UUID.randomUUID().toString(),id,supplier.revision,supplier.name,emptyList(),old?.note.orEmpty()),message=null)}
        autosave()
    }
    fun activate(id: String) {line(id,PurchaseDraftRules::activate)}
    fun activateEdit(id: String) {editLine(id,PurchaseDraftRules::activate)}
    fun line(id: String,change: (PurchaseLine)->PurchaseLine) {
        if(!editable()) return
        val ui=state.value;val d=ui.form ?: return
        val p=ui.snapshot?.products?.find {it.id==id} ?: return
        val s=ui.snapshot.suppliers.find {it.id==d.supplierId} ?: return
        if(id !in s.productIds && d.lines.none {it.productId==id}) return
        val old=d.lines.find {it.productId==id} ?: p.blankLine()
        val changed=change(old)
        val next=if(changed!=old && changed.quantity==old.quantity) PurchaseDraftRules.activate(changed) else changed
        update {it.copy(form=d.copy(lines=if(d.lines.any {it.productId==id}) d.lines.map {if(it.productId==id) next else it} else d.lines+next),message=null)}
        autosave()
    }
    fun note(value: String) {if(editable() && value.length<=1000) {update {it.copy(form=it.form?.copy(note=value),message=null)};autosave()}}
    fun remove(id: String) {if(editable()) {update {it.copy(form=it.form?.let {d->d.copy(lines=d.lines.filterNot {l->l.productId==id})},message=null)};autosave()}}
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
        val raw=change(line)
        val changed=if(raw!=line && raw.quantity==line.quantity) PurchaseDraftRules.activate(raw) else raw
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
        val permit=try {if(c.action is PurchaseOrderAction.Cancel) {forms.reopen {};forms.command(c.commandId)} else null} catch(e:CatalogFailure) {update {it.copy(message=e.message)};return}
        update {it.copy(pending=true,orderUnresolved=c,message=null)}
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {access.mutationOutcome(c) ?: if(permit!=null) permit.write {access.mutate(c)} else access.mutate(c)};orderSaved(c)}
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
            update {it.copy(snapshot=it.snapshot?.copy(orders=s.orders,products=s.products,suppliers=s.suppliers,receivingRecords=s.receivingRecords,receipts=s.receipts),blocked=false,orderReloadNeeded=false,orderCommitted=false,message=if(it.orderCommitted) "Изменение заказа сохранено. Остатки не менялись" else null)}
        } catch(ex:CancellationException) {throw ex}
        catch(_:Exception) {update {it.copy(blocked=true,orderReloadNeeded=true,message=if(it.orderCommitted) "Изменение заказа сохранено. Не удалось перечитать историю; повторите чтение" else "Не удалось прочитать историю. Ввод сохранён; повторите чтение")}}
    }
    fun refreshOrders() {
        if(state.value.pending || state.value.orderUnresolved!=null || state.value.unresolved!=null || state.value.editing!=null) return
        update {it.copy(pending=true)}
        viewModelScope.launch {try {readOrders()} finally {update {it.copy(pending=false)}}}
    }
    fun dismissPlaced() {if(!state.value.pending) update {it.copy(lastPlacedId=null,message=null)}}
    private val placeAfterSave=mutableSetOf<String>()
    fun saveAndPlace() {
        if(!editable()) return
        update {it.copy(pending=true,message=null)}
        viewModelScope.launch {
            try {writes.withLock {
                autoCommand?.let {completeAuto(it)}
                val snapshot=withContext(Dispatchers.IO) {access.snapshot()}
                check(snapshot.revision==state.value.snapshot?.revision) {"Заказ изменился в другом окне. Ввод сохранён; перечитайте данные"}
                val raw=state.value.form ?: return@withLock
                val draft=PurchaseDraftRules.revalidate(raw,snapshot.suppliers,snapshot.products)
                PurchaseDraftRules.validateReferences(draft,snapshot.suppliers,snapshot.products)
                update {it.copy(snapshot=snapshot,form=draft,pending=false)}
                if(!state.value.dirty && snapshot.draft!=null) place()
                else {
                    val command=PurchaseCommand(UUID.randomUUID().toString(),snapshot.ownerId,snapshot.revision,PurchaseAction.Save(draft))
                    placeAfterSave.add(command.commandId);submit(command)
                }
            }} catch(e:CancellationException) {throw e}
            catch(e:Exception) {update {it.copy(pending=false,message=e.message ?: "Не удалось сформировать заказ. Ввод сохранён")}}
            finally {if(state.value.unresolved==null) {update {it.copy(pending=false)};if(state.value.dirty) autosave()}}
        }
    }
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
    private fun submit(raw: PurchaseCommand) {
        update {it.copy(pending=true,unresolved=raw,message=null)}
        viewModelScope.launch {
            var command=raw
            try {writes.withLock {
                autoCommand?.let {completeAuto(it)}
                val known=withContext(Dispatchers.IO) {access.outcome(command)}
                if(known==null) {
                    command=command.copy(revision=state.value.snapshot?.revision ?: command.revision)
                    update {it.copy(unresolved=command)}
                    withContext(Dispatchers.IO) {access.commit(command)}
                }
                saved(command)
            }} catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    if(withContext(Dispatchers.IO) {access.outcome(command)}!=null) saved(command)
                    else update {it.copy(unresolved=null,message=if(e is CatalogFailure) e.message else "Заказ не сохранён. Ввод сохранён; повторите действие")}
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {update {it.copy(blocked=true,unresolved=command,message="Результат неизвестен. Проверьте ту же команду перед новым действием")}}
            } finally {update {it.copy(pending=false)}}
            if(placeAfterSave.remove(raw.commandId) && !state.value.blocked && state.value.unresolved==null && !state.value.dirty && state.value.snapshot?.draft!=null) place()
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
