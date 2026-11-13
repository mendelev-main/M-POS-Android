package com.mendelev.mpos.backend

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

internal data class EventsDirectoryState(val history: Boolean=false,val rows: List<JSONObject> = emptyList(),val cursor: String?=null,val loading: Boolean=false,val error: String?=null)
/** Presentation state with one request at a time and generation guards on source/page changes. */
internal class EventsDirectory(private val scope: CoroutineScope,private val refreshCurrent: suspend ()->Unit,private val readPage: suspend (String?)->OrderHistoryPage) {
    private val mutable=MutableStateFlow(EventsDirectoryState());val state=mutable.asStateFlow()
    private var job: Job?=null
    private var generation=0L
    fun reset() {generation++;job?.cancel();mutable.value=EventsDirectoryState()}
    fun current() {generation++;job?.cancel();mutable.update {it.copy(history=false,loading=false,error=null)}}
    fun history() {if(state.value.loading) return;mutable.update {it.copy(history=true)};load(true)}
    fun refresh()=load(true)
    fun more() {if(state.value.history && state.value.cursor!=null) load(false)}
    private fun load(reset: Boolean) {
        if(state.value.loading) return
        val start=state.value;val token=++generation
        mutable.update {it.copy(loading=true,error=null)}
        job=scope.launch {
            try {
                if(!start.history) refreshCurrent()
                else {
                    val requested=if(reset) null else start.cursor
                    val page=readPage(requested)
                    if(token==generation) {
                        val combined=if(reset) page.orders else (start.rows+page.orders).distinctBy {it.getString("id")}
                        mutable.update {it.copy(rows=combined,cursor=page.nextCursor)}
                    }
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(token==generation) mutable.update {it.copy(error=e.message ?: "Не удалось прочитать заказы. Повторите обновление")}}
            finally {if(token==generation) mutable.update {it.copy(loading=false)}}
        }
    }
}
