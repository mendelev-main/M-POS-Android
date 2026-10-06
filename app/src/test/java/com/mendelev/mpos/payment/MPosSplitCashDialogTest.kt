package com.mendelev.mpos.payment
import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MPosSplitCashDialogTest {
    private fun payload(token: String = "cash1", theme: String = "light") = JSONObject().put("action", "cashShow").put("token", token).put("amount", 12.5).put("given", 12.5).put("givenInput", "12.50").put("index", 0).put("currency", "BYN").put("amountLabel", "12,50 BYN").put("theme", theme)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun nativeFormChecksShortageUpdatesChangeAndConfirmsExactlyOnceInBothThemes() {
        for (theme in listOf("light", "dark")) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val actions = mutableListOf<JSONObject>(); val controller = MPosSplitCashDialog(activity) { actions.add(it) }
            controller.handle(payload(theme = theme)); ShadowLooper.idleMainLooper()
            val dialog = ShadowAlertDialog.getLatestAlertDialog(); val views = descendants(dialog.window!!.decorView)
            val input = views.filterIsInstance<EditText>().single(); val change = views.filterIsInstance<TextView>().first { it.contentDescription == "Сдача" }
            input.setText("12.49"); val pay = dialog.getButton(AlertDialog.BUTTON_POSITIVE); pay.performClick()
            assertTrue(actions.isEmpty()); assertTrue(dialog.isShowing); assertNotNull(input.error)
            input.setText("20,00"); assertEquals("Сдача: 7,50 BYN", change.text.toString())
            pay.performClick(); pay.performClick(); assertFalse(dialog.isShowing); assertEquals(1, actions.size)
            assertEquals(20.0, actions[0].getDouble("cashGiven"), 0.0); assertEquals(7.5, actions[0].getDouble("change"), 0.0)
            controller.dismiss(); activity.finish()
        }
    }
    @Test fun quickAmountsOldButtonsAndCancelAreSafe() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val actions = mutableListOf<JSONObject>(); val controller = MPosSplitCashDialog(activity) { actions.add(it) }
        controller.handle(payload()); ShadowLooper.idleMainLooper(); val old = ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE)
        controller.handle(payload("cash2")); ShadowLooper.idleMainLooper(); val dialog = ShadowAlertDialog.getLatestAlertDialog()
        old.performClick(); controller.handle(JSONObject().put("action", "cashHide").put("token", "cash1")); assertTrue(actions.isEmpty()); assertTrue(dialog.isShowing)
        val views = descendants(dialog.window!!.decorView); views.filterIsInstance<Button>().first { it.text == "20,00 BYN" }.performClick()
        assertEquals("20.00", views.filterIsInstance<EditText>().single().text.toString())
        dialog.cancel(); ShadowLooper.idleMainLooper(); assertEquals("cancel", actions.single().getString("action")); assertEquals("cash2", actions.single().getString("token"))
        controller.handle(payload("cash3")); controller.dismiss(); assertEquals(1, actions.size); activity.finish()
    }
    @Test fun initialInputPreservesReviewedToFixedResult() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val controller = MPosSplitCashDialog(activity) {}
        controller.handle(payload().put("given", 2.675).put("givenInput", "2.67")); ShadowLooper.idleMainLooper()
        assertEquals("2.67", descendants(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView).filterIsInstance<EditText>().single().text.toString())
        controller.dismiss(); activity.finish()
    }
}
