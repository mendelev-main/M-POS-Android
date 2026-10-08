package com.mendelev.mpos.workspace

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosCartAddControllerTest {
    private fun descendants(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap{descendants(view.getChildAt(it))}else emptyList())
    private fun packet(manual:Boolean=false,theme:String="light")=JSONObject().put("action","cartAddShow").put("token","add").put("theme",theme)
        .put("model",JSONObject("""{"name":"Кофе","currency":"BYN","catalogPrice":5,"groups":[{"id":"g","name":"Молоко","min":1,"max":1,"options":[{"id":"o","name":"Молоко","productId":"milk","qty":0.5,"priceDelta":1,"available":true}]}]}""").put("manual",manual))
    @Test fun modifierValidationAndOneTypedSaveUseBothNativeThemes() {
        for(theme in listOf("light","dark")) {
            val activity=Robolectric.buildActivity(Activity::class.java).setup().get();val actions=mutableListOf<JSONObject>();val controller=MPosCartAddController(activity){actions+=it}
            controller.handle(packet(theme=theme));val dialog=ShadowAlertDialog.getLatestAlertDialog();val save=dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.performClick();assertTrue(actions.isEmpty())
            descendants(dialog.window!!.decorView).filterIsInstance<Button>().first{it.text.toString().startsWith("Молоко")}.performClick()
            save.performClick();save.performClick();assertEquals(1,actions.size);assertEquals("[[0]]",actions.single().getJSONArray("selections").toString());assertFalse(save.isEnabled)
            assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled);controller.hide();activity.finish()
        }
    }
    @Test fun modifierToManualTransitionRejectsDetachedButtonAndPreservesRawPositiveInput() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get();val actions=mutableListOf<JSONObject>();val controller=MPosCartAddController(activity){actions+=it}
        controller.handle(packet(manual=true));val first=ShadowAlertDialog.getLatestAlertDialog()
        descendants(first.window!!.decorView).filterIsInstance<Button>().first{it.text.toString().startsWith("Молоко")}.performClick()
        val old=first.getButton(AlertDialog.BUTTON_POSITIVE);old.performClick();old.performClick();assertTrue(actions.isEmpty())
        val manual=ShadowAlertDialog.getLatestAlertDialog();assertNotSame(first,manual)
        val input=descendants(manual.window!!.decorView).filterIsInstance<EditText>().single();val save=manual.getButton(AlertDialog.BUTTON_POSITIVE)
        input.setText("0");save.performClick();assertTrue(actions.isEmpty())
        input.setText("0,004");save.performClick();assertEquals("0,004",actions.single().getString("manualInput"));assertEquals("[[0]]",actions.single().getJSONArray("selections").toString())
        controller.handle(JSONObject().put("action","cartAddResult").put("token","old").put("message","late"));assertFalse(save.isEnabled)
        controller.handle(JSONObject().put("action","cartAddResult").put("token","add").put("message","Недостаточно остатка"));assertTrue(save.isEnabled);assertEquals("0,004",input.text.toString())
        controller.hide();activity.finish()
    }
    @Test fun cancelAndRuntimeResetNeverEmitSave() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get();val actions=mutableListOf<JSONObject>();val controller=MPosCartAddController(activity){actions+=it}
        controller.handle(packet());ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertEquals("cartAddCancel",actions.single().getString("action"));controller.handle(packet());controller.hide();assertEquals(1,actions.size);activity.finish()
    }
}
