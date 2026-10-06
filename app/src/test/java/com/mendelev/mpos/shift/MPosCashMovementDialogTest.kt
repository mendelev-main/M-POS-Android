package com.mendelev.mpos.shift

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosCashMovementDialogTest {
    private fun payload(token: String = "f1", type: String = "deposit") = JSONObject().put("action", "cashFormShow").put("token", token).put("type", type).put("shiftId", "s1")
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun parserPreservesPositiveFractionAndCommaWithoutRounding() {
        assertEquals(0.001, MPosCashMovementDialog.parseAmount("0,001")!!, 0.0)
        assertEquals(12.5, MPosCashMovementDialog.parseAmount(" 12.5 ")!!, 0.0)
        for (raw in listOf("", " ", "0", "-1", "NaN", "Infinity", "bad", "1,2,3")) assertNull(MPosCashMovementDialog.parseAmount(raw))
    }
    @Test fun actualDialogChecksInputLocksDuplicateClicksAndIgnoresStaleAcknowledgements() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val actions = mutableListOf<JSONObject>(); val controller = MPosCashMovementDialog(activity) { actions.add(it) }
        controller.handle(payload()); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val fields = descendants(dialog.window!!.decorView).filterIsInstance<EditText>()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        fields[0].setText("0"); save.performClick(); assertNotNull(fields[0].error); assertTrue(actions.isEmpty())
        fields[0].setText("0.001"); fields[1].setText(" note "); save.performClick()
        assertEquals(1, actions.size); assertEquals(0.001, actions[0].getDouble("amount"), 0.0); assertEquals("note", actions[0].getString("note"))
        assertFalse(save.isEnabled); assertFalse(fields[0].isEnabled); assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        controller.handle(JSONObject().put("action", "cashFormResult").put("token", "stale").put("ok", true)); assertTrue(dialog.isShowing)
        controller.handle(JSONObject().put("action", "cashFormResult").put("token", "f1").put("ok", false)); assertTrue(save.isEnabled)
        save.performClick(); controller.handle(JSONObject().put("action", "cashFormResult").put("token", "f1").put("ok", true)); assertFalse(dialog.isShowing)
        controller.dismiss(); activity.finish()
    }
    @Test fun cancelAndUnknownCommitStatusHaveDistinctBehavior() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val actions = mutableListOf<JSONObject>(); val controller = MPosCashMovementDialog(activity) { actions.add(it) }
        controller.handle(payload(type = "withdrawal")); ShadowLooper.idleMainLooper(); var dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); assertEquals("cancel", actions.single().getString("action")); assertFalse(dialog.isShowing)
        controller.handle(payload("f2")); ShadowLooper.idleMainLooper(); dialog = ShadowAlertDialog.getLatestAlertDialog()
        descendants(dialog.window!!.decorView).filterIsInstance<EditText>().first().setText("1")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        controller.handle(JSONObject().put("action", "cashFormResult").put("token", "f2").put("ok", false).put("blocked", true))
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled); assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        controller.dismiss(); activity.finish()
    }
}
