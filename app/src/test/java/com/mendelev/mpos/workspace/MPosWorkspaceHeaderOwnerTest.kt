package com.mendelev.mpos.workspace

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosWorkspaceHeaderOwnerTest {
    private fun input(operation:String)=JSONObject().put("version",1).put("operation",operation)
    private fun owner()=MPosWorkspaceNavigationOwner().also{it.handle(input("initialize").put("tab","pos").put("search","чай").put("posPath","Кофе"))}
    private fun snapshot(owner:MPosWorkspaceNavigationOwner)=owner.handle(input("read")).getJSONObject("snapshot")
    private fun select(owner:MPosWorkspaceNavigationOwner,tab:String)=owner.handle(input("selectHeaderTab").put("tab",tab).put("expected",snapshot(owner)))
    private fun discard(owner:MPosWorkspaceNavigationOwner,reply:JSONObject)=owner.handle(input("discardHeaderTab").put("headerToken",reply.getString("headerToken")))
    @Test fun modelUsesNativeSelectionAndStableReviewedLabelsAndDetachedSnapshot() {
        val owner=owner();val before=snapshot(owner)
        val model=owner.handle(input("headerView").put("expected",before)).getJSONObject("navigation")
        val buttons=model.getJSONArray("buttons")
        assertEquals(listOf("pos","purchaseOrders","receiving","receipts","analytics","bookings","settings"),(0 until buttons.length()).map{buttons.getJSONObject(it).getString("tab")})
        assertEquals("M POS",buttons.getJSONObject(0).getString("label"));assertTrue(buttons.getJSONObject(0).getBoolean("selected"))
        assertEquals("Настройки",buttons.getJSONObject(6).getString("label"))
        model.getJSONObject("expected").put("search","changed");assertEquals("чай",before.getString("search"));assertEquals("чай",owner.state.value.search)
    }
    @Test fun directTabSelectionChecksRevisionAndDestinationWithoutChangingWorkspaceContext() {
        val owner=owner();val before=snapshot(owner)
        val reply=owner.handle(input("selectHeaderTab").put("expected",before).put("tab","receipts"))
        assertEquals("receipts",reply.getJSONObject("snapshot").getString("tab"));assertEquals("чай",owner.state.value.search)
        assertEquals("Кофе",owner.state.value.posPath);assertFalse(owner.state.value.editMode)
        assertThrows(IllegalStateException::class.java){owner.handle(input("selectHeaderTab").put("expected",before).put("tab","analytics"))}
        val after=owner.state.value
        assertThrows(IllegalArgumentException::class.java){select(owner,"unknown")};assertEquals(after,owner.state.value)
    }
    @Test fun discardedTabPreservesNewQueryAndCannotUndoLaterExplicitChoice() {
        val owner=owner();val first=select(owner,"receipts")
        owner.handle(input("selectSearch").put("search","новый"));discard(owner,first)
        assertEquals("pos",owner.state.value.tab);assertEquals("новый",owner.state.value.search)
        val second=select(owner,"analytics")
        owner.handle(input("selectTab").put("tab","analytics"));discard(owner,second)
        assertEquals("analytics",owner.state.value.tab)
        val third=select(owner,"receipts");val latest=select(owner,"settings")
        discard(owner,third);assertEquals("settings",owner.state.value.tab)
        discard(owner,latest);assertEquals("receipts",owner.state.value.tab)
    }
    @Test fun runtimeReplacementDropsOldTabSelectionAndRequiresFreshInitialization() {
        val owner=owner();val before=select(owner,"receipts");owner.beginRuntime(1)
        assertThrows(IllegalStateException::class.java){owner.handle(input("headerView").put("expected",JSONObject()))}
        owner.handle(input("initialize").put("tab","settings").put("search","импорт"));discard(owner,before)
        assertEquals("settings",owner.state.value.tab);assertEquals("импорт",owner.state.value.search)
    }
}
