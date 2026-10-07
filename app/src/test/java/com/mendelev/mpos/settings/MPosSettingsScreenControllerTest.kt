package com.mendelev.mpos.settings

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
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
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class MPosSettingsScreenControllerTest {
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun model(token: String = "f", dark: Boolean = false) = JSONObject().put("action", "formShow").put("token", token).put("theme", if (dark) "dark" else "light").put("items", JSONArray()
        .put(JSONObject().put("kind", "heading").put("text", "Новый сотрудник"))
        .put(JSONObject().put("kind", "field").put("key", "0").put("type", "text").put("label", "ФИО").put("value", "Очень длинное имя сотрудника"))
        .put(JSONObject().put("kind", "field").put("key", "1").put("type", "checkbox").put("label", "Администратор").put("value", false))
        .put(JSONObject().put("kind", "field").put("key", "2").put("type", "password").put("label", "Пароль").put("visible", false).put("value", ""))
        .put(JSONObject().put("kind", "button").put("key", "0").put("label", "Сохранить").put("primary", true)))
    private fun setup(): Triple<MPosSettingsScreenController, MutableList<JSONObject>, Activity> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity); activity.setContentView(root)
        val calls = mutableListOf<JSONObject>()
        return Triple(MPosSettingsScreenController(activity, root) { calls += it }, calls, activity)
    }
    @Test fun realFormForwardsOpaqueFieldsOnceAndWaitsForMatchingResult() {
        val (controller, calls) = setup(); controller.handle(model()); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val inputs = nodes(dialog.window!!.decorView).filterIsInstance<EditText>()
        val save = nodes(dialog.window!!.decorView).filterIsInstance<Button>().first { it.text == "Сохранить" }
        inputs[0].setText("Иван"); inputs[1].setText("synthetic-invalid")
        save.performClick(); save.performClick()
        assertEquals(1, calls.count { it.optString("action") == "click" })
        assertEquals("synthetic-invalid", calls.last().getJSONObject("fields").getString("2"))
        assertFalse(save.isEnabled); assertTrue(dialog.isShowing)
        controller.handle(JSONObject().put("action", "formResult").put("token", "stale")); assertFalse(save.isEnabled)
        controller.handle(JSONObject().put("action", "formResult").put("token", "f").put("message", "Проверьте пароль"))
        assertTrue(save.isEnabled); assertTrue(dialog.isShowing)
        controller.dismiss(); assertEquals("", inputs[1].text.toString()); assertFalse(dialog.isShowing)
    }
    @Test fun roleChangeUpdatesVisibilityWithoutRecreatingFormOrLosingEnteredName() {
        val (controller, calls) = setup(); controller.handle(model()); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog(); val views = nodes(dialog.window!!.decorView)
        val fields = views.filterIsInstance<EditText>(); fields[0].setText("Имя до переключения роли")
        views.filterIsInstance<CheckBox>().single().isChecked = true
        assertEquals(true, calls.last().getJSONObject("fields").getBoolean("1"))
        controller.handle(JSONObject().put("action", "formUpdate").put("token", "f").put("fields", JSONArray().put(JSONObject().put("key", "2").put("label", "Подтвердить роль").put("type", "password").put("visible", true))))
        assertSame(dialog, ShadowAlertDialog.getLatestAlertDialog()); assertEquals("Имя до переключения роли", fields[0].text.toString())
        assertEquals(View.VISIBLE, (fields[1].parent as View).visibility)
        controller.dismiss()
    }
    @Test fun pendingOperationCannotCancelBlockedOperationCannotResubmitButCanClose() {
        val (controller, calls) = setup(); controller.handle(model()); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog(); val save = nodes(dialog.window!!.decorView).filterIsInstance<Button>().first { it.text == "Сохранить" }
        save.performClick(); assertTrue(controller.consumeBack()); assertTrue(dialog.isShowing)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        controller.handle(JSONObject().put("action", "formResult").put("token", "f").put("blocked", true))
        assertFalse(save.isEnabled); assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        controller.consumeBack(); assertEquals("cancel", calls.last().getString("action")); assertFalse(dialog.isShowing)
    }
    @Test fun replacementClearsOldCredentialsAndStaleHideCannotDismissCurrentForm() {
        val (controller) = setup(); controller.handle(model("old")); ShadowLooper.idleMainLooper()
        val old = ShadowAlertDialog.getLatestAlertDialog(); val fields = nodes(old.window!!.decorView).filterIsInstance<EditText>()
        fields[1].setText("synthetic-invalid"); controller.handle(model("new", true)); ShadowLooper.idleMainLooper()
        assertEquals("", fields[1].text.toString()); assertFalse(old.isShowing)
        val next = ShadowAlertDialog.getLatestAlertDialog(); controller.handle(JSONObject().put("action", "formHide").put("token", "old")); assertTrue(next.isShowing)
        val save = nodes(next.window!!.decorView).filterIsInstance<Button>().first { it.text == "Сохранить" }
        assertFalse(save.isAllCaps); assertTrue(save.minimumHeight >= 48)
        controller.dismiss()
    }
    @Test fun syntheticLightAndDarkPreviewsUseNativeFormAndFontScaleSafeActions() {
        val (controller, _, activity) = setup()
        for (dark in listOf(false, true)) {
            controller.handle(model("preview", dark)); ShadowLooper.idleMainLooper()
            val view = ShadowAlertDialog.getLatestAlertDialog().window!!.decorView
            view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, 720, view.measuredHeight)
            val bitmap = android.graphics.Bitmap.createBitmap(720, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/design-previews/settings-employee-${if (dark) "dark" else "light"}.png")
            file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            assertTrue(file.length() > 1000); controller.dismiss()
        }
        val row = MPosSettingsActionRow(activity, 8)
        repeat(3) { row.addView(Button(activity).apply { text = "Длинное действие"; textSize = 30f; minHeight = 48 }) }
        row.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        row.layout(0, 0, 300, row.measuredHeight)
        assertTrue(row.getChildAt(1).top > row.getChildAt(0).top)
        assertTrue((0 until row.childCount).all { row.getChildAt(it).right <= 300 })
    }

}
