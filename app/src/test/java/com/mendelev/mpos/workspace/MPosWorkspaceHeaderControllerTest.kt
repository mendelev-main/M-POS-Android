package com.mendelev.mpos.workspace

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosWorkspaceHeaderControllerTest {
    private fun nodes(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))}else emptyList()
    private fun model(token:String="first",tab:String="pos",revision:Long=1,dark:Boolean=false):JSONObject {
        val owner=MPosWorkspaceNavigationOwner()
        val state=owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab",tab)).getJSONObject("snapshot").put("revision",revision)
        fun rect(left:Int,width:Int)=JSONObject().put("left",left).put("top",12).put("width",width).put("height",40)
        return JSONObject().put("action","headerShow").put("token",token).put("theme",if(dark)"dark" else "light")
            .put("viewportWidth",1200).put("viewportHeight",800).put("groups",JSONObject().put("brand",rect(16,96)).put("tabs",rect(122,800)).put("settings",rect(1050,44)))
            .put("navigation",MPosWorkspaceHeaderModel.calculate(state))
    }
    @Test fun typedTabsKeepManropeStyleBusyStateAndCorrelateCompletionAfterGeometryRefresh() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get();val host=FrameLayout(activity)
        activity.setContentView(host);host.layout(0,0,1200,800)
        val calls=mutableListOf<JSONObject>();val controller=MPosWorkspaceHeaderController(activity,host){calls+=it}
        controller.handle(model());val receipts=nodes(host).filterIsInstance<Button>().single{it.contentDescription=="Чеки"}
        receipts.performClick();receipts.performClick();assertEquals(1,calls.size)
        assertEquals("receipts",calls.single().getJSONObject("command").getString("tab"));assertFalse(calls.single().has("key"))
        assertFalse(receipts.isAllCaps);assertFalse(receipts.isEnabled)
        controller.handle(model("next"));val next=nodes(host).filterIsInstance<Button>().single{it.contentDescription=="Чеки"}
        assertFalse(next.isEnabled)
        controller.handle(JSONObject().put("action","headerResult").put("requestToken","unrelated"));assertFalse(next.isEnabled)
        controller.handle(JSONObject().put("action","headerResult").put("requestToken","first"));assertTrue(next.isEnabled)
        receipts.performClick();assertEquals(1,calls.size)
        next.performClick();assertEquals(2,calls.size);controller.hide()
        controller.handle(model("new"));controller.handle(JSONObject().put("action","headerResult").put("requestToken","next"))
        val newer=nodes(host).filterIsInstance<Button>().single{it.contentDescription=="Аналитика"};newer.performClick()
        controller.handle(JSONObject().put("action","headerResult").put("requestToken","next"));assertFalse(newer.isEnabled)
        controller.handle(JSONObject().put("action","headerResult").put("requestToken","new"));assertTrue(newer.isEnabled)
    }
    @Test fun selectedTabUsesSourceNavyPaletteInBothThemesAndInvalidGeometryFallsBack() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get();val host=FrameLayout(activity);host.layout(0,0,1200,800)
        val calls=mutableListOf<JSONObject>();val controller=MPosWorkspaceHeaderController(activity,host){calls+=it}
        for(dark in listOf(false,true)) {
            controller.handle(model(tab="analytics",dark=dark))
            val selected=nodes(host).filterIsInstance<Button>().single{it.contentDescription=="Аналитика"}
            assertEquals(Color.WHITE,selected.currentTextColor);assertTrue(selected.minimumHeight>=48)
            assertTrue(nodes(host).filterIsInstance<Button>().single{it.contentDescription=="Настройки"}.minimumWidth>=48)
        }
        val invalid=model();invalid.getJSONObject("groups").getJSONObject("tabs").put("width",0)
        controller.handle(invalid);assertEquals(0,host.childCount);assertEquals("fallback",calls.single().getString("action"))
    }
}
