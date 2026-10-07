package com.mendelev.mpos.shift

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import org.json.JSONArray
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
class MPosShiftOpenDialogTest {
    private fun show() = JSONObject().put("action", "openFormShow").put("token", "f1").put("currency", "BYN")
    private fun model(request: JSONObject, staff: JSONArray = JSONArray("""[{"id":"e1","name":"Кассир","role":"cashier"},{"id":"e2","name":"Администратор","role":"admin"}]""")) = JSONObject()
        .put("requestId", request.getString("requestId")).put("ok", true).put("openingCash", 80).put("employees", staff)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun selectsStaffShowsAdminInputClearsItAndLocksPendingConfirmation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val actions = mutableListOf<JSONObject>()
        val controller = MPosShiftOpenDialog(activity, { requests.add(it) }, { actions.add(it) })
        controller.handle(show()); ShadowLooper.idleMainLooper(); controller.result(model(requests.single())); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog(); val views = descendants(dialog.window!!.decorView)
        val select = views.filterIsInstance<Spinner>().single(); val secret = views.filterIsInstance<EditText>().single()
        assertEquals(View.GONE, secret.visibility); assertFalse(secret.isSaveEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertTrue(actions.isEmpty())
        select.setSelection(2); ShadowLooper.idleMainLooper(); assertEquals(View.VISIBLE, secret.visibility)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertTrue(actions.isEmpty()); assertNotNull(secret.error)
        secret.setText("synthetic-invalid"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("e2", actions.single().getString("employeeId")); assertEquals("synthetic-invalid", actions.single().getString("password"))
        assertEquals("", secret.text.toString()); assertFalse(select.isEnabled)
        controller.handle(JSONObject().put("action", "openFormResult").put("token", "f1").put("ok", false)); assertTrue(select.isEnabled)
        select.setSelection(1); ShadowLooper.idleMainLooper(); assertEquals(View.GONE, secret.visibility)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertEquals("", actions.last().getString("password"))
        controller.handle(JSONObject().put("action", "openFormResult").put("token", "f1").put("ok", true)); assertFalse(dialog.isShowing)
        controller.dismiss(); activity.finish()
    }
    @Test fun emptyStaffDisablesOpeningAndReadFailureHasExplicitFallbackWithStaleProtection() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val actions = mutableListOf<JSONObject>()
        val controller = MPosShiftOpenDialog(activity, { requests.add(it) }, { actions.add(it) })
        controller.handle(show()); ShadowLooper.idleMainLooper(); controller.result(model(requests.last(), JSONArray())); ShadowLooper.idleMainLooper()
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        controller.dismiss(); controller.handle(show()); ShadowLooper.idleMainLooper()
        val loading = ShadowAlertDialog.getLatestAlertDialog()
        controller.result(JSONObject().put("requestId", requests.last().getString("requestId")).put("ok", false))
        loading.getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); assertEquals(3, requests.size)
        controller.result(model(requests[1])); assertTrue(loading.isShowing)
        controller.result(JSONObject().put("requestId", requests.last().getString("requestId")).put("ok", false))
        loading.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertEquals("fallback", actions.single().getString("action"))
        controller.result(model(requests.last())); assertFalse(loading.isShowing)
        controller.dismiss(); activity.finish()
    }

    @Test fun nativeHandshakeKeepsCredentialOutsideActionsAndOnlySendsAcknowledgedState() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val actions = mutableListOf<JSONObject>(); val commits = mutableListOf<Pair<JSONObject, String>>()
        val controller = MPosShiftOpenDialog(activity, { requests.add(it) }, { actions.add(it) }, { input, secret -> commits.add(input to secret) })
        controller.handle(show().put("nativeCommit", true)); ShadowLooper.idleMainLooper(); controller.result(model(requests.single())); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog(); val views = descendants(dialog.window!!.decorView)
        val select = views.filterIsInstance<Spinner>().single(); val secret = views.filterIsInstance<EditText>().single()
        select.setSelection(2); ShadowLooper.idleMainLooper(); secret.setText("synthetic-invalid"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("prepare", actions.single().getString("action")); assertFalse(actions.single().has("password")); assertEquals("", secret.text.toString()); assertFalse(select.isEnabled)
        val instruction = JSONObject().put("action", "openFormCommit").put("token", "f1").put("employeeId", "e2").put("id", "s1").put("openedAt", 200)
            .put("expectedShifts", JSONArray()).put("expectedEmployees", JSONArray())
        controller.handle(JSONObject(instruction.toString()).put("token", "old")); assertTrue(commits.isEmpty())
        controller.handle(instruction); controller.handle(instruction); assertEquals(1, commits.size)
        assertEquals("synthetic-invalid", commits.single().second); assertFalse(commits.single().first.has("password"))
        controller.result(JSONObject().put("requestId", "old").put("ok", false)); assertEquals(1, actions.size)
        controller.result(JSONObject().put("requestId", commits.single().first.getString("requestId")).put("ok", false).put("message", "Неверный пароль"))
        assertEquals("committed", actions.last().getString("action")); assertFalse(actions.last().has("password")); assertFalse(actions.last().getBoolean("ok"))
        controller.handle(instruction); assertEquals(1, commits.size) // Still awaiting UI acknowledgement, not a new submission.
        controller.handle(JSONObject().put("action", "openFormResult").put("token", "f1").put("ok", false)); assertTrue(select.isEnabled)
        select.setSelection(1); ShadowLooper.idleMainLooper(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); controller.handle(JSONObject(instruction.toString()).put("employeeId", "e1"))
        assertEquals("", commits.last().second)
        val shift = JSONObject().put("id", "s1").put("status", "open")
        controller.result(JSONObject().put("requestId", commits.last().first.getString("requestId")).put("ok", true).put("shift", shift).put("shifts", JSONArray().put(shift)))
        assertTrue(actions.last().getBoolean("ok")); assertEquals("s1", actions.last().getJSONObject("shift").getString("id")); assertFalse(actions.last().has("password"))
        controller.handle(JSONObject().put("action", "openFormResult").put("token", "f1").put("ok", true)); assertFalse(dialog.isShowing)
        controller.dismiss(); activity.finish()
    }
}
