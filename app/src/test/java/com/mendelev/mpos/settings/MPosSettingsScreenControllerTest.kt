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

    @Test fun productPatchKeepsLiveDraftFocusCursorAndAddsRemovesActionsWithoutRecreatingDialog() {
        val (controller, calls) = setup()
        val product = model("product").put("expanded", true)
        product.getJSONArray("items").getJSONObject(1).put("live", true).put("maxLength", 80)
        controller.handle(product); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val name = nodes(dialog.window!!.decorView).filterIsInstance<EditText>().first()
        name.requestFocus(); name.setText("Молоко с изменением"); name.setSelection(6)
        val patch = JSONObject().put("action", "formPatch").put("token", "product").put("items", JSONArray()
            .put(JSONObject().put("kind", "field").put("key", "0").put("live", true).put("type", "text").put("label", "Название").put("value", "Old source value"))
            .put(JSONObject().put("kind", "button").put("key", "new").put("label", "Добавить ингредиент")))
        controller.handle(patch)
        assertSame(dialog, ShadowAlertDialog.getLatestAlertDialog())
        val refreshed = nodes(dialog.window!!.decorView).filterIsInstance<EditText>().single()
        assertSame(name, refreshed); assertEquals("Молоко с изменением", refreshed.text.toString()); assertEquals(6, refreshed.selectionStart)
        assertTrue(refreshed.hasFocus())
        assertFalse(nodes(dialog.window!!.decorView).filterIsInstance<Button>().any { it.text == "Сохранить" })
        ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Молоко с изменением", calls.last().getJSONObject("fields").getString("0"))
        assertEquals("change", calls.last().getString("action"))
        nodes(dialog.window!!.decorView).filterIsInstance<Button>().first { it.text == "Добавить ингредиент" }.performClick()
        assertEquals("new", calls.last().getString("key")); assertEquals("click", calls.last().getString("action"))
        controller.dismiss()
    }
    @Test fun productConvertedValueReplacesAcknowledgedFieldAndCancelCarriesUnsentDraft() {
        val (controller, calls) = setup()
        val product = model("units")
        product.getJSONArray("items").getJSONObject(1).put("live", true).put("value", "7")
        controller.handle(product); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val name = nodes(dialog.window!!.decorView).filterIsInstance<EditText>().first()
        name.requestFocus()
        controller.handle(JSONObject().put("action", "formPatch").put("token", "units").put("items", JSONArray()
            .put(JSONObject().put("kind", "field").put("key", "0").put("live", true).put("type", "number").put("label", "Остаток, мл").put("value", "7000"))))
        assertEquals("7000", name.text.toString())
        name.setText("8000")
        controller.consumeBack()
        assertEquals("cancel", calls.last().getString("action")); assertEquals("8000", calls.last().getJSONObject("fields").getString("0"))
        ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("cancel", calls.last().getString("action")); assertFalse(dialog.isShowing); assertEquals("", name.text.toString())
    }
    @Test fun productRemovedFieldsAreWipedAndStalePatchCannotChangeNewForm() {
        val (controller, calls) = setup()
        val product = model("old"); product.getJSONArray("items").getJSONObject(1).put("live", true)
        controller.handle(product); ShadowLooper.idleMainLooper()
        val removed = nodes(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView).filterIsInstance<EditText>().first()
        removed.setText("draft")
        controller.handle(JSONObject().put("action", "formPatch").put("token", "old").put("items", JSONArray()))
        assertEquals("", removed.text.toString())
        controller.handle(model("new")); ShadowLooper.idleMainLooper()
        controller.handle(JSONObject().put("action", "formPatch").put("token", "old").put("items", JSONArray()))
        assertTrue(nodes(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView).filterIsInstance<Button>().any { it.text == "Сохранить" })
        val count = calls.size; ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(count, calls.size); controller.dismiss()
    }
    @Test fun syntheticProductEditorPreviewsRenderMetricsAndWrappingNavigationInBothPalettes() {
        val (controller, _, activity) = setup()
        activity.resources.displayMetrics.heightPixels = 1200
        activity.resources.displayMetrics.widthPixels = 1200
        for (dark in listOf(false, true)) {
            val items = JSONArray().put(JSONObject().put("kind", "heading").put("text", "Карточка товара · Молоко"))
            listOf("Основное", "Состав и остатки", "Модификаторы", "Где используется", "Онлайн-меню").forEachIndexed { index, label -> items.put(JSONObject().put("kind", "button").put("key", "tab-$index").put("label", label).put("selected", index == 0)) }
            items.put(JSONObject().put("kind", "card").put("items", JSONArray()
                .put(JSONObject().put("kind", "heading").put("text", "Основная информация"))
                .put(JSONObject().put("kind", "field").put("key", "name").put("label", "Название товара").put("type", "text").put("value", "Молоко 3,2%"))
                .put(JSONObject().put("kind", "field").put("key", "price").put("label", "Цена продажи, BYN").put("type", "number").put("value", "5.00"))))
            items.put(JSONObject().put("kind", "metric").put("label", "Цена продажи").put("value", "5,00 BYN").put("primary", true))
            items.put(JSONObject().put("kind", "metric").put("label", "Себестоимость").put("value", "3,20 BYN"))
            items.put(JSONObject().put("kind", "button").put("key", "save").put("label", "Сохранить").put("primary", true))
            controller.handle(JSONObject().put("action", "formShow").put("token", "product-preview").put("theme", if (dark) "dark" else "light").put("expanded", true).put("items", items).put("cancelLabel", "← Назад"))
            ShadowLooper.idleMainLooper()
            val view = ShadowAlertDialog.getLatestAlertDialog().window!!.decorView
            view.measure(View.MeasureSpec.makeMeasureSpec(1040, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); view.layout(0, 0, 1040, view.measuredHeight)
            val bitmap = android.graphics.Bitmap.createBitmap(1040, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/design-previews/product-editor-${if (dark) "dark" else "light"}.png")
            file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            assertTrue(file.length() > 1000); controller.dismiss()
        }
    }

    @Test fun pendingDirtyExitSaveLocksNewEditorUntilAcknowledgedWithoutAnotherGesture() {
        val (controller, calls) = setup()
        controller.handle(model("external").put("pending", true)); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val save = nodes(dialog.window!!.decorView).filterIsInstance<Button>().first { it.text == "Сохранить" }
        assertFalse(save.isEnabled); assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        assertTrue(controller.consumeBack()); assertTrue(dialog.isShowing); assertTrue(calls.isEmpty())
        controller.handle(JSONObject().put("action", "formPatch").put("token", "external").put("pending", false).put("items", model().getJSONArray("items")))
        val refreshed = nodes(dialog.window!!.decorView).filterIsInstance<Button>().first { it.text == "Сохранить" }
        assertTrue(refreshed.isEnabled); assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        refreshed.performClick(); assertEquals(1, calls.size); controller.dismiss()
    }

    @Test fun fractionalInputCanPauseAfterSeparatorWithoutDiscardingDraftOrDispatchingIncompleteNumber() {
        val (controller, calls) = setup()
        val numeric = model("decimal")
        numeric.getJSONArray("items").getJSONObject(1).put("live", true).put("type", "number").put("value", "1")
        controller.handle(numeric); ShadowLooper.idleMainLooper()
        val input = nodes(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView).filterIsInstance<EditText>().first()
        val initial = calls.size
        input.setText("2."); ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(initial, calls.size); assertEquals("2.", input.text.toString())
        controller.handle(JSONObject().put("action", "formPatch").put("token", "decimal").put("items", numeric.getJSONArray("items")))
        assertEquals("2.", input.text.toString())
        input.setText("2.5"); ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("2.5", calls.last().getJSONObject("fields").getString("0")); controller.dismiss()
    }

    @Test fun paymentBackWaitsForReviewedRefusalAndHardwareBackDoesNotDismissPaidSplitForm() {
        val (controller, calls) = setup()
        controller.handle(model("payment").put("deferCancel", true)); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK)))
        assertTrue(dialog.isShowing); assertEquals("cancel", calls.last().getString("action"))
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        controller.handle(JSONObject().put("action", "formResult").put("token", "payment").put("message", "Сначала завершите раздельную оплату"))
        assertTrue(dialog.isShowing); assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        assertTrue(nodes(dialog.window!!.decorView).filterIsInstance<android.widget.TextView>().any { it.text == "Сначала завершите раздельную оплату" })
        controller.handle(JSONObject().put("action", "formHide").put("token", "payment")); assertFalse(dialog.isShowing)
    }
    @Test fun paymentPageReopensWithRecoveryBlockAndCannotSubmitButCanRequestReviewedBack() {
        val (controller, calls) = setup()
        controller.handle(model("recovery").put("deferCancel", true).put("blocked", true)); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertFalse(nodes(dialog.window!!.decorView).filterIsInstance<Button>().first { it.text == "Сохранить" }.isEnabled)
        assertTrue(nodes(dialog.window!!.decorView).filterIsInstance<android.widget.TextView>().any { it.text == "Перезапустите M POS для восстановления данных" })
        controller.consumeBack(); assertEquals("cancel", calls.last().getString("action")); assertTrue(dialog.isShowing)
        controller.dismiss()
    }
    @Test fun syntheticPaymentPreviewsUseFormattedReceiptAndClearTenderActionsInBothThemes() {
        val (controller, _, activity) = setup()
        activity.resources.displayMetrics.heightPixels = 1200; activity.resources.displayMetrics.widthPixels = 1200
        for (dark in listOf(false, true)) {
            val receipt = JSONArray().put(JSONObject().put("kind", "heading").put("text", "Чек"))
                .put(JSONObject().put("kind", "text").put("text", "На месте · Стол 3"))
                .put(JSONObject().put("kind", "metric").put("label", "Молоко × 1").put("value", "12,50 BYN"))
                .put(JSONObject().put("kind", "metric").put("label", "Итого").put("value", "12,50 BYN").put("primary", true))
            val items = JSONArray().put(JSONObject().put("kind", "heading").put("text", "Оплата"))
                .put(JSONObject().put("kind", "card").put("items", receipt))
                .put(JSONObject().put("kind", "metric").put("label", "К оплате").put("value", "12,50 BYN").put("primary", true))
                .put(JSONObject().put("kind", "button").put("key", "split").put("label", "Разделить"))
                .put(JSONObject().put("kind", "button").put("key", "amount").put("label", "Сумма · 20,00 BYN"))
                .put(JSONObject().put("kind", "metric").put("label", "Сдача").put("value", "7,50 BYN"))
                .put(JSONObject().put("kind", "button").put("key", "cash").put("label", "Оплатить").put("primary", true))
                .put(JSONObject().put("kind", "button").put("key", "card").put("label", "Оплата картой"))
            controller.handle(JSONObject().put("action", "formShow").put("token", "payment-preview").put("expanded", true).put("deferCancel", true).put("cancelLabel", "← Назад").put("theme", if (dark) "dark" else "light").put("items", items))
            ShadowLooper.idleMainLooper(); val view = ShadowAlertDialog.getLatestAlertDialog().window!!.decorView
            view.measure(View.MeasureSpec.makeMeasureSpec(1040, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); view.layout(0, 0, 1040, view.measuredHeight)
            val bitmap = android.graphics.Bitmap.createBitmap(1040, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888); view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/design-previews/payment-${if (dark) "dark" else "light"}.png"); file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            assertTrue(file.length() > 1000); controller.dismiss()
        }
    }

    @Test fun completedReceiptShowsDoneOnceAndHardwareBackStillUsesReviewedCompletionAction() {
        val (controller, calls) = setup()
        controller.handle(model("receipt").put("deferCancel", true).put("hideCancel", true).put("cancelLabel", "Готово")); ShadowLooper.idleMainLooper()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(View.GONE, dialog.getButton(AlertDialog.BUTTON_NEGATIVE).visibility)
        controller.consumeBack(); assertTrue(dialog.isShowing); assertEquals("cancel", calls.last().getString("action")); controller.dismiss()
    }

}
