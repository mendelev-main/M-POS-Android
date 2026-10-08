package com.mendelev.mpos.employee

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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosEmployeeAuthorizationDialogTest {
    private fun show(dark: Boolean = false) = JSONObject().put("action", "employeeAuthorize").put("requestId", "employee-native-1")
        .put("theme", if (dark) "dark" else "light").put("command", JSONObject().put("version", 1).put("operation", "save"))
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun credentialsRemainNativeClearOnDispatchAndActionsStayLockedUntilReply() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val credentials = mutableListOf<String>(); val replies = mutableListOf<JSONObject>()
        val controller = MPosEmployeeAuthorizationDialog(activity, { value, secret -> requests.add(value); credentials.add(secret) }, replies::add)
        controller.handle(show()); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog(); val field = views(dialog.window!!.decorView).filterIsInstance<EditText>().single()
        assertFalse(field.isSaveEnabled)
        field.setText("synthetic-input"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(listOf("synthetic-input"), credentials)
        assertEquals("", field.text.toString()); assertFalse(field.isEnabled)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertEquals(1, requests.size)
        assertFalse(requests.single().toString().contains("synthetic-input")); assertFalse(replies.single().toString().contains("synthetic-input"))
        controller.result(JSONObject().put("requestId", "stale").put("ok", true)); assertTrue(dialog.isShowing)
        controller.result(JSONObject().put("requestId", requests.single().getString("requestId")).put("ok", true))
        assertFalse(dialog.isShowing); assertTrue(replies.last().getBoolean("ok")); assertEquals("employee-native-1", replies.last().getString("requestId"))
        controller.close(); activity.finish()
    }

    @Test fun wrongCredentialAllowsRetryAndCancellationWithoutAnySaveAcknowledgement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val replies = mutableListOf<JSONObject>()
        val controller = MPosEmployeeAuthorizationDialog(activity, { value, _ -> requests.add(value) }, replies::add)
        controller.handle(show(true)); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog(); val field = views(dialog.window!!.decorView).filterIsInstance<EditText>().single()
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps)
        field.setText("synthetic-wrong"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        controller.result(JSONObject().put("requestId", requests.single().getString("requestId")).put("ok", false).put("credentialRejected", true))
        assertTrue(dialog.isShowing); assertTrue(field.isEnabled); assertEquals("", field.text.toString())
        assertEquals("retry", replies.last().getString("action"))
        field.setText("synthetic-abandoned"); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals("", field.text.toString()); assertFalse(dialog.isShowing)
        assertTrue(replies.last().getBoolean("cancelled")); assertEquals(1, requests.size)
        controller.close(); activity.finish()
    }

    @Test fun nativeDeadlineBlocksRetryAndDiscardsLateCredentialFailure() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val replies = mutableListOf<JSONObject>()
        val controller = MPosEmployeeAuthorizationDialog(activity, { value, _ -> requests.add(value) }, replies::add)
        controller.handle(show()); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(31, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(replies.last().getBoolean("uncertain"))
        controller.result(JSONObject().put("requestId", requests.single().getString("requestId")).put("credentialRejected", true))
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertEquals(1, requests.size)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); assertFalse(dialog.isShowing)
        controller.close(); activity.finish()
    }

    @Test fun destructionClearsFieldAndLateResponseCannotCloseNewConfirmation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requests = mutableListOf<JSONObject>(); val replies = mutableListOf<JSONObject>()
        val controller = MPosEmployeeAuthorizationDialog(activity, { value, _ -> requests.add(value) }, replies::add)
        controller.handle(show()); ShadowLooper.idleMainLooper()
        val first = ShadowAlertDialog.getLatestAlertDialog(); val field = views(first.window!!.decorView).filterIsInstance<EditText>().single()
        field.setText("synthetic-input"); first.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); val old = requests.single().getString("requestId")
        controller.close(); assertEquals("", field.text.toString()); assertFalse(first.isShowing)
        controller.handle(show().put("requestId", "employee-native-2")); ShadowLooper.idleMainLooper()
        val second = ShadowAlertDialog.getLatestAlertDialog()
        controller.result(JSONObject().put("requestId", old).put("ok", true)); assertTrue(second.isShowing)
        assertEquals(1, replies.size)
        controller.close(); activity.finish()
    }
}
