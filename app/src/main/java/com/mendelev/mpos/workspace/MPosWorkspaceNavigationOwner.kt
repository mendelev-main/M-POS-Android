package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.MPosSupplyParity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

/** Activity-scoped navigation authority. Storage FIFO owns access; no persisted or financial state. */
class MPosWorkspaceNavigationOwner {
    data class State(val initialized:Boolean=false,val tab:String="pos",val revision:Long=0,val search:String="",
        val posPath:String?=null,val posFolder:String="",val editMode:Boolean=false)
    private data class Proposal(val before:State,val decision:String,val documents:String?)
    private data class Accepted(val token:String,val before:State,val after:State,val queryEpoch:Long)
    private var accepted:Accepted?=null
    private var queryEpoch=0L
    private val mutable=MutableStateFlow(State())
    private val proposals=linkedMapOf<String,Proposal>()
    val state:StateFlow<State> = mutable.asStateFlow()
    private var runtimeGeneration=0L
    fun beginRuntime(generation:Long) {
        check(generation>runtimeGeneration){"stale navigation runtime"}
        runtimeGeneration=generation;proposals.clear();accepted=null;queryEpoch=0
        mutable.value=State(revision=mutable.value.revision+1)
    }

    private fun snapshot(current:State)=JSONObject().put("tab",current.tab).put("search",current.search)
        .put("posPath",current.posPath?:JSONObject.NULL).put("posFolder",current.posFolder)
        .put("editMode",current.editMode).put("revision",current.revision)
    private fun documents(input:JSONObject)=JSONObject().put("products",input.getJSONArray("products"))
        .put("navigation",input.opt("navigation")?:JSONObject.NULL)
    private fun reply(decision:JSONObject=JSONObject()):JSONObject = decision.put("ok",true).put("authoritative",true)
        .put("source","native-workspace-navigation").put("snapshot",snapshot(mutable.value))
    private fun checkExpected(input:JSONObject) {
        val expected=input.getJSONObject("expected")
        val current=snapshot(mutable.value)
        check(listOf("tab","search","posPath","posFolder","editMode").all{MPosSupplyParity.same(expected.opt(it),current.opt(it))}) {
            "workspace projection changed"
        }
    }
    fun requiresDocuments(input:JSONObject):Boolean = when(input.getString("operation")) {
        "prepareRoute" -> input.optString("route")=="openFolder"
        "acceptRoute" -> proposals[input.getString("proposalToken")]?.documents!=null
        else -> false
    }
    fun handle(input:JSONObject):JSONObject {
        require(input.getInt("version")==1)
        val previous=mutable.value
        when(input.getString("operation")) {
            "initialize" -> if(!previous.initialized) mutable.value=State(true,input.getString("tab"),previous.revision+1,
                input.optString("search", ""),input.opt("posPath") as? String,input.optString("posFolder",""),input.optBoolean("editMode",false))
            "selectTab" -> {
                check(previous.initialized){"navigation is not initialized"}
                val tab=input.getString("tab")
                if(tab!=previous.tab)mutable.value=previous.copy(tab=tab,revision=previous.revision+1)
            }
            "read" -> check(previous.initialized){"navigation is not initialized"}
            "selectSearch" -> {
                check(previous.initialized){"navigation is not initialized"}
                val search=input.getString("search");queryEpoch++
                if(search!=previous.search)mutable.value=previous.copy(search=search,revision=previous.revision+1)
            }
            "prepareRoute" -> {
                check(previous.initialized){"navigation is not initialized"};checkExpected(input)
                val route=JSONObject(input.toString()).put("operation",input.getString("route"))
                    .put("state",snapshot(previous))
                val decision=MPosWorkspaceRouteEngine.calculate(route)
                if(!decision.getBoolean("allowed"))return reply(decision)
                proposals.clear() // Only the latest prepared transition may be acknowledged.
                val token=UUID.randomUUID().toString()
                proposals[token]=Proposal(previous,decision.toString(),if(input.getString("route")=="openFolder")documents(input).toString() else null)
                return reply(JSONObject(decision.toString()).put("proposalToken",token))
            }
            "acceptRoute" -> {
                val proposal=proposals.remove(input.getString("proposalToken"))?:error("route proposal unavailable")
                check(proposal.before==previous){"navigation changed before route acknowledgement"}
                if(proposal.documents!=null)check(MPosSupplyParity.same(JSONObject(proposal.documents),documents(input))){"workspace documents changed"}
                val decision=JSONObject(proposal.decision);val patch=decision.getJSONObject("patch")
                val next=previous.copy(
                    posPath=if(patch.has("posPath"))patch.opt("posPath") as? String else previous.posPath,
                    posFolder=patch.optString("posFolder",previous.posFolder),search=patch.optString("search",previous.search),
                    editMode=patch.optBoolean("editMode",previous.editMode))
                if(next!=previous){mutable.value=next.copy(revision=previous.revision+1);proposals.clear()}
                accepted=Accepted(input.getString("proposalToken"),previous,mutable.value,queryEpoch)
                return reply(decision)
            }
            "discardRoute" -> {
                val last=accepted
                if(last!=null&&last.token==input.getString("proposalToken")&&previous.posPath==last.after.posPath&&previous.posFolder==last.after.posFolder&&previous.editMode==last.after.editMode) {
                    val restored=previous.copy(posPath=last.before.posPath,posFolder=last.before.posFolder,editMode=last.before.editMode,
                        search=if(queryEpoch==last.queryEpoch)last.before.search else previous.search)
                    if(restored!=previous)mutable.value=restored.copy(revision=previous.revision+1)
                    accepted=null;proposals.clear()
                }
                return reply()
            }
            "cancelRoute" -> {proposals.remove(input.getString("proposalToken"));return reply()}
            else -> throw IllegalArgumentException("unknown navigation operation")
        }
        if(mutable.value!=previous)proposals.clear()
        return reply()
    }
}
