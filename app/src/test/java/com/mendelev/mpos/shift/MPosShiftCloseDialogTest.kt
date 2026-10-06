package com.mendelev.mpos.shift

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
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
@Config(sdk = [28], manifest = Config.NONE)
class MPosShiftCloseDialogTest {
    private fun show(token: String = "f1") = JSONObject().put("action", "closeFormShow").put("token", token).put("shiftId", "s1").put("currency", "BYN").put("expectedCash", 9999)
    private fun reply(request: JSONObject, cash: Double = 80.0, id: String = "s1") = JSONObject().put("ok", true).put("requestId", request.getString("requestId"))
        .put("shiftId", id).put("expectedCash", cash)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun countedCashPreservesZeroFractionsAndRejectsBlankOrNegative() {
        assertEquals(0.0, MPosCashMovementDialog.parseCounted("0")!!, 0.0)
        assertEquals(12.001, MPosCashMovementDialog.parseCounted("12,001")!!, 0.0)
        for (raw in listOf("", " ", "-1", "Infinity", "bad")) assertNull(MPosCashMovementDialog.parseCounted(raw))
    }
    @Test fun nativeSnapshotPrefillsExpectedAndAllowsDifferentCountedValueOnlyOnce() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val actions = mutableListOf<JSONObject>()
        val controller = MPosShiftCloseDialog(activity, { requests.add(it) }, { actions.add(it) })
        controller.handle(show()); ShadowLooper.idleMainLooper()
        assertFalse(JSONObject(requests.single().getString("payload")).has("expectedCash"))
        controller.result(reply(requests.single())); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val views = descendants(dialog.window!!.decorView)
        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "80,00 BYN" })
        val input = views.filterIsInstance<EditText>().single()
        assertEquals(80.0, input.text.toString().toDouble(), 0.0)
        input.setText("75.001"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(75.001, actions.single().getDouble("amount"), 0.0); assertEquals("close", actions.single().getString("type"))
        assertFalse(input.isEnabled)
        controller.handle(JSONObject().put("action", "closeFormResult").put("token", "f1").put("ok", true)); assertFalse(dialog.isShowing)
        controller.dismiss(); activity.finish()
    }
    @Test fun badOrChangedSnapshotOffersExplicitRetryFallbackAndLateReplyCannotReopen() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val actions = mutableListOf<JSONObject>()
        val controller = MPosShiftCloseDialog(activity, { requests.add(it) }, { actions.add(it) })
        controller.handle(show()); ShadowLooper.idleMainLooper()
        controller.result(reply(requests.last(), id = "other"))
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(descendants(dialog.window!!.decorView).filterIsInstance<EditText>().isEmpty())
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); assertEquals(2, requests.size)
        controller.result(reply(requests.first())); assertTrue(dialog.isShowing)
        controller.result(reply(requests.last(), cash = -1.0)); assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertEquals("fallback", actions.single().getString("action"))
        controller.result(reply(requests.last())); assertFalse(dialog.isShowing)
        controller.dismiss(); activity.finish()
    }
    @Test fun zeroExpectedIsValidAndUncertainCloseRequiresReload() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val actions = mutableListOf<JSONObject>()
        val controller = MPosShiftCloseDialog(activity, { requests.add(it) }, { actions.add(it) })
        controller.handle(show()); ShadowLooper.idleMainLooper(); controller.result(reply(requests.last(), 0.0)); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertEquals(0.0, actions.single().getDouble("amount"), 0.0)
        controller.handle(JSONObject().put("action", "closeFormResult").put("token", "f1").put("ok", false).put("blocked", true))
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled); assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        controller.dismiss(); activity.finish()
    }
}
