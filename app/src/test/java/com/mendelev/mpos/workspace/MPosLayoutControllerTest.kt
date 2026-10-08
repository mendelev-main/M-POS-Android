package com.mendelev.mpos.workspace

import android.widget.Button
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosLayoutControllerTest {
    private fun descendants(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap{descendants(view.getChildAt(it))}else emptyList())
    @Test fun nativeDeleteSendsOneAtomicCommandAndBusyBlocksDuplicateAndBack() {
        val context=RuntimeEnvironment.getApplication();val host=FrameLayout(context);host.layout(0,0,1200,800)
        val actions=mutableListOf<JSONObject>();val controller=MPosLayoutController(context,host){actions+=it}
        val model=JSONObject("""{"root":true,"parent":"","title":"Рабочая зона","expected":{"tab":"pos","editMode":true,"revision":1,"search":""},"documentRevision":"hash","tiles":[{"type":"product","id":"p","label":"Латте","index":0,"col":0,"row":0}],"choices":[],"folders":[]}""")
        controller.handle(JSONObject().put("action","layoutShow").put("token","one").put("model",model).put("viewportWidth",1200).put("viewportHeight",800).put("rect",JSONObject().put("left",0).put("top",80).put("width",600).put("height",650)))
        val delete=descendants(host).filterIsInstance<Button>().first{it.text=="Удалить"};delete.performClick();delete.performClick()
        val commands=actions.filter{it.optString("action")=="commit"};assertEquals(1,commands.size)
        val input=commands.single().getJSONObject("input");assertEquals("layoutCommit",input.getString("operation"));assertEquals("removeTile",input.getJSONObject("command").getString("operation"));assertEquals("hash",input.getString("documentRevision"))
        controller.handle(JSONObject().put("action","layoutBack").put("token","one"));assertEquals(1,actions.count{it.optString("action")=="commit"})
        controller.handle(JSONObject().put("action","layoutApplied").put("requestId","old"));assertFalse(delete.isEnabled)
        controller.hide();assertEquals(0,host.childCount)
    }
}
