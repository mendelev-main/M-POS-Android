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
class MPosCartItemControllerTest {
    private fun descendants(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap{descendants(view.getChildAt(it))}else emptyList())
    private fun packet(theme:String)=JSONObject().put("action","cartItemShow").put("token","form").put("theme",theme)
        .put("model",JSONObject("""{"name":"Латте","quantity":"2","comment":"","discountId":"","currency":"BYN","discounts":[{"id":7,"name":"Сотрудник","type":"percent","value":10}]}"""))
    @Test fun nativeDraftProducesOneTypedSaveAndPreservesDatasetDiscountMappingInBothThemes() {
        for(theme in listOf("light","dark")) {
            val activity=Robolectric.buildActivity(Activity::class.java).setup().get();val actions=mutableListOf<JSONObject>()
            val controller=MPosCartItemController(activity){actions+=it};controller.handle(packet(theme))
            val dialog=ShadowAlertDialog.getLatestAlertDialog();val views=descendants(dialog.window!!.decorView)
            views.filterIsInstance<Button>().first{it.text=="+"}.performClick()
            views.filterIsInstance<EditText>().single().setText("без сахара")
            val discount=views.filterIsInstance<Button>().first{it.text.toString().startsWith("Сотрудник")};discount.performClick();assertTrue(discount.isSelected)
            val save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);save.performClick();save.performClick()
            val result=actions.single();assertEquals("cartItemSave",result.getString("action"));assertEquals(3,result.getInt("quantity"));assertEquals("7",result.getString("discountId"));assertEquals("без сахара",result.getString("comment"))
            assertFalse(save.isEnabled);assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
            controller.handle(JSONObject().put("action","cartItemResult").put("token","stale").put("message","late"));assertFalse(save.isEnabled)
            controller.handle(JSONObject().put("action","cartItemResult").put("token","form").put("message","Недостаточно остатка"));assertTrue(save.isEnabled)
            controller.hide();activity.finish()
        }
    }
    @Test fun cancelEmitsNoSaveAndReloadHideDropsTheDialog() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get();val actions=mutableListOf<JSONObject>()
        val controller=MPosCartItemController(activity){actions+=it};controller.handle(packet("light"))
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("cartItemCancel",actions.single().getString("action"))
        controller.handle(packet("dark"));val dialog=ShadowAlertDialog.getLatestAlertDialog();controller.hide();assertFalse(dialog.isShowing)
        assertEquals(1,actions.size);activity.finish()
    }
}
