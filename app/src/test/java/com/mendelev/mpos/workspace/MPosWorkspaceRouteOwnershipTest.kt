package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.MPosSupplyParity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosWorkspaceRouteOwnershipTest {
    private fun seed(owner:MPosWorkspaceNavigationOwner,state:JSONObject=JSONObject()):JSONObject = owner.handle(
        JSONObject(state.toString()).put("version",1).put("operation","initialize").put("tab","pos"))
    private fun prepare(owner:MPosWorkspaceNavigationOwner,route:String="openCategory",value:String="Кофе"):JSONObject = owner.handle(
        JSONObject().put("version",1).put("operation","prepareRoute").put("route",route).put("value",value)
            .put("expected",owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot")))
    private fun accept(owner:MPosWorkspaceNavigationOwner,token:String)=owner.handle(JSONObject().put("version",1).put("operation","acceptRoute").put("proposalToken",token))

    @Test fun preparedTransitionsMatchSourceFixturesAndOnlyAcknowledgementChangesOwner() {
        val file=listOf(File("../tests/fixtures/workspace-route.json"),File("tests/fixtures/workspace-route.json")).first{it.isFile}
        val fixtures=JSONArray(file.readText())
        for(i in 0 until fixtures.length()) {
            val row=fixtures.getJSONObject(i);val input=JSONObject(row.getJSONObject("input").toString())
            val owner=MPosWorkspaceNavigationOwner();val initial=seed(owner,input.getJSONObject("state"));val before=owner.state.value
            input.put("route",input.getString("operation")).put("operation","prepareRoute").put("expected",initial.getJSONObject("snapshot"))
            if(!input.has("products"))input.put("products",JSONArray())
            val prepared=owner.handle(input);assertEquals(before,owner.state.value)
            if(!prepared.getBoolean("allowed")){assertFalse(prepared.has("proposalToken"));continue}
            val accepted=owner.handle(JSONObject(input.toString()).put("operation","acceptRoute").put("proposalToken",prepared.getString("proposalToken")))
            val actual=accepted.getJSONObject("snapshot");actual.remove("tab");actual.remove("revision")
            assertTrue(row.getString("name"),MPosSupplyParity.same(row.getJSONObject("expected").getJSONObject("state"),actual))
            assertEquals(row.getJSONObject("expected").getJSONArray("events").getString(0),accepted.getString("effect"))
        }
    }
    @Test fun cancelledAndDuplicateProposalsCannotChangeSelection() {
        val owner=MPosWorkspaceNavigationOwner();seed(owner);val prepared=prepare(owner);val token=prepared.getString("proposalToken")
        owner.handle(JSONObject().put("version",1).put("operation","cancelRoute").put("proposalToken",token))
        assertThrows(IllegalStateException::class.java){accept(owner,token)};assertNull(owner.state.value.posPath)
        val next=prepare(owner).getString("proposalToken");accept(owner,next)
        assertThrows(IllegalStateException::class.java){accept(owner,next)};assertEquals("Кофе",owner.state.value.posPath)
    }
    @Test fun laterSearchOrTabInvalidatesPreparedBackAndPreservesNewerState() {
        for(operation in listOf("selectSearch","selectTab")) {
            val owner=MPosWorkspaceNavigationOwner();seed(owner,JSONObject().put("posPath","Кофе"))
            val token=prepare(owner,"closeCategory").getString("proposalToken")
            owner.handle(JSONObject().put("version",1).put("operation",operation).put("search","чай").put("tab","receipts"))
            assertThrows(IllegalStateException::class.java){accept(owner,token)};assertEquals("Кофе",owner.state.value.posPath)
        }
    }
    @Test fun forgedProjectionIsRejectedAndForgedRouteStateCannotSupplyNativeSelection() {
        val owner=MPosWorkspaceNavigationOwner();val initial=seed(owner,JSONObject().put("posPath","Кофе").put("editMode",true))
        val expected=initial.getJSONObject("snapshot")
        val input=JSONObject().put("version",1).put("operation","prepareRoute").put("route","closeCategory").put("expected",expected)
            .put("state",JSONObject().put("posFolder","forged"))
        val proposal=owner.handle(input);accept(owner,proposal.getString("proposalToken"));assertNull(owner.state.value.posPath);assertFalse(owner.state.value.editMode)
        assertThrows(IllegalStateException::class.java){owner.handle(input)}
    }
    @Test fun discardedLateAcceptanceRestoresOnlyItsRouteWithoutOverwritingNewTabOrQuery() {
        val owner=MPosWorkspaceNavigationOwner();seed(owner,JSONObject().put("posPath","Кофе").put("search","old"))
        val token=prepare(owner,"openCategory","Чай").getString("proposalToken");accept(owner,token)
        owner.handle(JSONObject().put("version",1).put("operation","selectTab").put("tab","receipts"))
        owner.handle(JSONObject().put("version",1).put("operation","selectSearch").put("search","new"))
        val discard=JSONObject().put("version",1).put("operation","discardRoute").put("proposalToken",token)
        owner.handle(discard);assertEquals("Кофе",owner.state.value.posPath)
        assertEquals("receipts",owner.state.value.tab);assertEquals("new",owner.state.value.search)
        val next=prepare(owner,"openCategory","Сок").getString("proposalToken");accept(owner,next)
        owner.handle(discard);assertEquals("Сок",owner.state.value.posPath)
    }
}
