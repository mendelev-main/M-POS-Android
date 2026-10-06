package com.mendelev.mpos.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import com.mendelev.mpos.data.MPosShiftReportRepository
import com.mendelev.mpos.shift.MPosShiftOpenDialog
import com.mendelev.mpos.shift.MPosShiftScreenController
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MPosNativeThemeTest {
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun preview(view: View, name: String, width: Int = 720, height: Int? = null) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height ?: 0, if (height == null) View.MeasureSpec.UNSPECIFIED else View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(width, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.TRANSPARENT); view.draw(canvas)
        val file = File("build/design-previews/$name.png"); requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        assertTrue(file.length() > 1000)
    }
    @Test fun palettesButtonsFontAndNativePreviewsMatchPos() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        for (dark in listOf(false, true)) {
            val theme = MPosNativeTheme(activity, dark)
            assertEquals(Color.parseColor(if (dark) "#222631" else "#FFFFFF"), theme.surface)
            assertEquals(Color.parseColor(if (dark) "#31B98D" else "#0E8F6F"), theme.accent)
            val button = Button(activity); theme.button(button, primary = true)
            assertEquals(theme.accentInk, button.currentTextColor); assertTrue(button.minimumHeight >= theme.dp(48))
            button.isEnabled = false; assertEquals(theme.muted, button.currentTextColor)
            val requests = mutableListOf<JSONObject>()
            val open = MPosShiftOpenDialog(activity, { requests.add(it) }, {})
            open.handle(JSONObject().put("action", "openFormShow").put("token", "preview").put("theme", if (dark) "dark" else "light").put("currency", "BYN"))
            ShadowLooper.idleMainLooper()
            open.result(JSONObject().put("ok", true).put("requestId", requests.last().getString("requestId")).put("openingCash", 180.5)
                .put("employees", JSONArray("""[{"id":"synthetic-1","name":"Анна Ковальская","role":"cashier"},{"id":"synthetic-2","name":"Администратор","role":"admin"}]""")))
            ShadowLooper.idleMainLooper()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val picker = descendants(dialog.window!!.decorView).filterIsInstance<Spinner>().single(); picker.setSelection(1); ShadowLooper.idleMainLooper()
            assertTrue(picker.minimumHeight >= theme.dp(48))
            assertEquals(theme.accentInk, dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).currentTextColor)
            preview(dialog.window!!.decorView, "employee-${if (dark) "dark" else "light"}")
            open.dismiss()
            val host = FrameLayout(activity); host.layout(0, 0, 1000, 1200)
            val screen = MPosShiftScreenController(activity, host, { requests.add(it) }, {})
            screen.handle(JSONObject().put("action", "show").put("theme", if (dark) "dark" else "light").put("currency", "BYN")
                .put("viewportWidth", 1000).put("viewportHeight", 1200).put("rect", JSONObject().put("left", 0).put("top", 0).put("width", 1000).put("height", 1200)))
            val shift = JSONObject().put("id", "synthetic-shift").put("employeeName", "Анна Ковальская").put("openedAt", 1760000400000L).put("openingCash", 180.5)
            val orders = JSONArray("""[{"id":"synthetic-receipt","shiftId":"synthetic-shift","total":425.75,"payments":[{"method":"cash","amount":225.75},{"method":"card","amount":200}]}]""")
            val model = MPosShiftReportRepository.build(shift, orders, "BYN", "M POS").put("number", 8)
            screen.result(JSONObject().put("ok", true).put("requestId", requests.last().getString("requestId")).put("active", model).put("history", JSONArray()))
            preview(host, "shift-${if (dark) "dark" else "light"}", 1000, 1200)
            assertTrue(descendants(host).filterIsInstance<TextView>().any { it.text.toString() == "406,25 BYN" })
            screen.hide()
        }
        activity.finish()
    }
}
