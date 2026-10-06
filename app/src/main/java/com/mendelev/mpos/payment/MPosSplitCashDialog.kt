package com.mendelev.mpos.payment

import com.mendelev.mpos.R
import com.mendelev.mpos.data.MPosJsonNumbers
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.widget.doAfterTextChanged
import org.json.JSONObject
import java.util.Locale

/** Native cash entry; the approved split-progress transaction remains the persistence boundary. */
class MPosSplitCashDialog(private val context: Context, private val action: (JSONObject) -> Unit) {
    private var dialog: AlertDialog? = null
    private var token: String? = null
    fun handle(payload: JSONObject) {
        val next = payload.optString("token")
        if (payload.optString("action") == "cashHide") { if (next == token) dismiss(); return }
        if (payload.optString("action") != "cashShow" || next.isBlank()) return
        val amount = payload.optDouble("amount", Double.NaN)
        val initial = payload.optDouble("given", amount)
        if (!amount.isFinite() || amount <= 0 || !initial.isFinite() || initial < 0) return
        dismiss(); token = next
        val dark = payload.optString("theme") == "dark"
        val themed = ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
        val currency = payload.optString("currency")
        fun money(value: Double) = String.format(Locale.US, "%.2f", MPosJsonNumbers.roundMoney(value)).replace('.', ',') + " " + currency
        val fields = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun label(value: String) = TextView(themed).apply { text = value; textSize = 18f; fields.addView(this) }
        label("Платёж ${payload.optInt("index") + 1}")
        label(payload.optString("amountLabel", money(amount)))
        label("Внесено наличными")
        val input = EditText(themed).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            contentDescription = "Внесено наличными"; setSingleLine(); setText(payload.optString("givenInput", String.format(Locale.US, "%.2f", initial)))
        }
        fields.addView(input)
        val quick = LinearLayout(themed).apply { orientation = LinearLayout.VERTICAL }; fields.addView(quick)
        for (value in MPosCashTender.denominations(amount)) quick.addView(Button(themed).apply {
            text = money(value); setOnClickListener { input.setText(String.format(Locale.US, "%.2f", MPosJsonNumbers.roundMoney(value))); input.setSelection(input.text.length) }
        })
        val change = label(context.getString(R.string.mpos_cash_change, money(MPosCashTender.preview(amount, MPosCashTender.parse(input.text.toString()) ?: 0.0)))).apply { contentDescription = "Сдача" }
        input.doAfterTextChanged { change.text = context.getString(R.string.mpos_cash_change, money(MPosCashTender.preview(amount, MPosCashTender.parse(it.toString()) ?: 0.0))) }
        val scroll = ScrollView(themed).apply { addView(fields) }
        val current = AlertDialog.Builder(themed).setTitle("Оплата наличными").setView(scroll)
            .setNegativeButton("Отмена", null).setPositiveButton("Оплатить", null).create()
        dialog = current
        fun cancel() { if (token == next) { dismiss(); action(JSONObject().put("token", next).put("action", "cancel")) } }
        current.setOnCancelListener { cancel() }
        current.setOnShowListener {
            current.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancel() }
            current.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setTextColor((if (dark) "#31B98D" else "#0E8F6F").toColorInt())
                setOnClickListener {
                    if (token != next) return@setOnClickListener
                    val payment = MPosCashTender.parse(input.text.toString())?.let { MPosCashTender.confirm(amount, it) }
                    if (payment == null) { input.error = "Недостаточно внесённой суммы или некорректная сумма"; return@setOnClickListener }
                    dismiss()
                    action(JSONObject().put("token", next).put("action", "confirm").put("cashGiven", payment.cashGiven).put("change", payment.change))
                }
            }
            input.requestFocus(); input.selectAll()
        }
        current.show()
    }
    fun dismiss() { token = null; dialog?.setOnCancelListener(null); dialog?.dismiss(); dialog = null }
}
