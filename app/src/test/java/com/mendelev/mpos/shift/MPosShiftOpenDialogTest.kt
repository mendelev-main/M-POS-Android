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
}
