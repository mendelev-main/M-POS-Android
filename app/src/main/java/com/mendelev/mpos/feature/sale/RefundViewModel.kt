package com.mendelev.mpos.feature.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

interface RefundAccess {
    suspend fun history(): List<RefundEntity>
    suspend fun preview(receiptId: String,actor: String): RefundPreview
    suspend fun commit(command: String,preview: RefundPreview): String
    suspend fun outcome(command: String): RefundEntity?
}
private class RoomRefundAccess(db: CatalogDatabase): RefundAccess {
    private val repo=RefundRepository(db)
    override suspend fun history()=repo.history()
    override suspend fun preview(receiptId: String,actor: String)=repo.preview(receiptId,actor)
    override suspend fun commit(command: String,preview: RefundPreview)=repo.commit(command,preview)
    override suspend fun outcome(command: String)=repo.outcome(command)
}
data class RefundSubmission(val command: String,val preview: RefundPreview)
data class RefundUi(val pending: Boolean=false,val blocked: Boolean=true,val history: List<RefundEntity> = emptyList(),
    val review: RefundSubmission?=null,val unresolved: RefundSubmission?=null,val result: RefundEntity?=null,val message: String?=null)
class RefundViewModel(private val access: RefundAccess): ViewModel() {
    constructor(db: CatalogDatabase): this(RoomRefundAccess(db))
    private val mutable=MutableStateFlow(RefundUi());val state=mutable.asStateFlow()
    init {refresh()}
    fun refresh() {
        if(state.value.pending || state.value.unresolved!=null) return
        mutable.update {it.copy(pending=true,review=null,message=null)}
        viewModelScope.launch {
            try {val rows=withContext(Dispatchers.IO) {access.history()};mutable.update {it.copy(history=rows,blocked=false)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {mutable.update {it.copy(blocked=true,message="Не удалось прочитать возвраты. Повторите чтение перед новой операцией")}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun prepare(receiptId: String,actor: String) {
        if(state.value.pending || state.value.blocked || state.value.unresolved!=null || state.value.result!=null) return
        mutable.update {it.copy(pending=true,review=null,message=null)}
        viewModelScope.launch {
            try {
                val p=withContext(Dispatchers.IO) {access.preview(receiptId,actor)}
                mutable.update {it.copy(review=RefundSubmission(UUID.randomUUID().toString(),p))}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {mutable.update {it.copy(message=if(e is CatalogFailure) e.message else "Не удалось проверить возврат. Повторите чтение",blocked=e !is CatalogFailure)}}
            finally {mutable.update {it.copy(pending=false)}}
        }
    }
    fun dismiss() {if(!state.value.pending && state.value.unresolved==null) mutable.update {it.copy(review=null,result=null,message=null)}}
    fun confirm() {
        if(state.value.pending) return
        val submitted=state.value.unresolved ?: state.value.review ?: return
        mutable.update {it.copy(pending=true,unresolved=submitted,review=null,message=null)}
        viewModelScope.launch {
            try {
                // Query first on every retry; the same command is the only permitted retry.
                val result=withContext(Dispatchers.IO) {
                    access.outcome(submitted.command) ?: run {
                        access.commit(submitted.command,submitted.preview)
                        access.outcome(submitted.command) ?: error("Не найден результат возврата")
                    }
                }
                saved(result)
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                try {
                    val result=withContext(Dispatchers.IO) {access.outcome(submitted.command)}
                    if(result!=null) saved(result)
                    else {
                        val rows=withContext(Dispatchers.IO) {access.history()}
                        mutable.update {it.copy(history=rows,unresolved=null,blocked=false,message=if(e is CatalogFailure) e.message else "Возврат не сохранён. Проверьте чек и повторите операцию")}
                    }
                } catch(e2:CancellationException) {throw e2}
                catch(_:Exception) {mutable.update {it.copy(blocked=true,unresolved=submitted,message="Результат возврата неизвестен. Проверьте результат той же команды; новую операцию не создавайте")}}
            } finally {mutable.update {it.copy(pending=false)}}
        }
    }
    private suspend fun saved(result: RefundEntity) {
        // A history read failure cannot hide a known committed result or permit a duplicate.
        mutable.update {it.copy(result=result,unresolved=null,review=null,history=it.history.filterNot {r->r.receiptId==result.receiptId}+result,blocked=true)}
        try {val rows=withContext(Dispatchers.IO) {access.history()};mutable.update {it.copy(history=rows,blocked=false)}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {mutable.update {it.copy(message="Возврат сохранён. Не удалось обновить историю; повторите чтение")}}
    }
}
