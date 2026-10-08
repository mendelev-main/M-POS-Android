package com.mendelev.mpos.workspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Activity-scoped navigation authority; no database, history stack or financial side effects. Storage FIFO owns access. */
class MPosWorkspaceNavigationOwner {
    data class State(val initialized:Boolean=false,val tab:String="pos",val revision:Long=0,val search:String="")
    private val mutable=MutableStateFlow(State())
    val state:StateFlow<State> = mutable.asStateFlow()
    fun handle(input:JSONObject):JSONObject {
        require(input.getInt("version")==1)
        val previous=mutable.value
        when(input.getString("operation")) {
            "initialize" -> if(!previous.initialized) mutable.value=State(true,input.getString("tab"),previous.revision+1,input.optString("search", ""))
            "selectTab" -> {
                check(previous.initialized){"navigation is not initialized"}
                val tab=input.getString("tab")
                if(tab!=previous.tab)mutable.value=previous.copy(tab=tab,revision=previous.revision+1)
            }
            "read" -> check(previous.initialized){"navigation is not initialized"}
            "selectSearch" -> {
                check(previous.initialized){"navigation is not initialized"}
                val search=input.getString("search")
                if(search!=previous.search)mutable.value=previous.copy(search=search,revision=previous.revision+1)
            }
            else -> throw IllegalArgumentException("unknown navigation operation")
        }
        val current=mutable.value
        return JSONObject().put("ok",true).put("authoritative",true).put("source","native-workspace-navigation")
            .put("snapshot",JSONObject().put("tab",current.tab).put("search",current.search).put("revision",current.revision))
    }
}
