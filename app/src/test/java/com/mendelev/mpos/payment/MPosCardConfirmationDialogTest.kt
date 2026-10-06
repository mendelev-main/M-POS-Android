package com.mendelev.mpos.payment
import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowAlertDialog
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosCardConfirmationDialogTest {
    private fun payload(token: String = "c1", theme: String = "light") = JSONObject().put("action", "show").put("token", token).put("amount", 12.5).put("amountLabel", "12,50 BYN").put("theme", theme)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun nativeConfirmationShowsAmountAndEmitsOnlyOnceInBothThemes() {
        for (theme in listOf("light", "dark")) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val actions = mutableListOf<JSONObject>(); val controller = MPosCardConfirmationDialog(activity) { actions.add(it) }
            controller.handle(payload(theme = theme)); ShadowLooper.idleMainLooper(); val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(descendants(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString() == "12,50 BYN" })
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE); button.performClick(); button.performClick()
            assertEquals(1, actions.size); assertEquals("confirm", actions[0].getString("action")); assertEquals("c1", actions[0].getString("token")); assertFalse(dialog.isShowing)
            controller.dismiss(); activity.finish()
        }
    }
    @Test fun staleHideAndOldButtonCannotConfirmReplacementWhileCancelIsDistinct() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val actions = mutableListOf<JSONObject>(); val controller = MPosCardConfirmationDialog(activity) { actions.add(it) }
        controller.handle(payload()); ShadowLooper.idleMainLooper(); val old = ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE)
        controller.handle(payload("c2")); ShadowLooper.idleMainLooper(); val dialog = ShadowAlertDialog.getLatestAlertDialog()
        old.performClick(); controller.handle(JSONObject().put("action", "hide").put("token", "c1")); assertTrue(actions.isEmpty()); assertTrue(dialog.isShowing)
        dialog.cancel(); ShadowLooper.idleMainLooper(); assertEquals(1, actions.size); assertEquals("cancel", actions[0].getString("action")); assertEquals("c2", actions[0].getString("token"))
        controller.handle(payload("c3")); controller.dismiss(); assertEquals(1, actions.size); activity.finish()
    }
    @Test fun invalidAmountsNeverReplaceAnActiveDialogAndZeroIsAllowed() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val actions = mutableListOf<JSONObject>(); val controller = MPosCardConfirmationDialog(activity) { actions.add(it) }
        controller.handle(payload()); ShadowLooper.idleMainLooper(); val original = ShadowAlertDialog.getLatestAlertDialog()
        controller.handle(payload("bad").put("amount", -1)); assertTrue(original.isShowing)
        controller.handle(payload("zero").put("amount", 0).put("amountLabel", "0,00 BYN")); ShadowLooper.idleMainLooper(); assertFalse(original.isShowing)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); assertEquals("cancel", actions.single().getString("action")); activity.finish()
    }
}
