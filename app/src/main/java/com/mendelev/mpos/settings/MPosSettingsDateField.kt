package com.mendelev.mpos.settings

import android.app.DatePickerDialog
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatButton
import com.mendelev.mpos.R
import com.mendelev.mpos.ui.MPosNativeTheme
import java.time.LocalDate
import java.time.ZoneId

/** Date presentation only; the reviewed handler still validates period/order semantics. */
internal class MPosSettingsDateField(private val theme: MPosNativeTheme, private val changed: () -> Unit) : AppCompatButton(ContextThemeWrapper(theme.uiContext, if (theme.dark) androidx.appcompat.R.style.Theme_AppCompat else androidx.appcompat.R.style.Theme_AppCompat_Light)) {
    constructor(context: Context) : this(MPosNativeTheme(context, false), {})
    var dateValue: String = ""
        private set
    init { theme.button(this); setOnClickListener { choose() } }
    fun bind(value: String) { dateValue = value; text = value.ifBlank { context.getString(R.string.mpos_choose_date) } }
    private fun choose() {
        if (!isEnabled) return
        val initial = runCatching { LocalDate.parse(dateValue) }.getOrElse { LocalDate.now() }
        DatePickerDialog(theme.uiContext, { _, year, month, day ->
            if (isEnabled) { bind(LocalDate.of(year, month + 1, day).toString()); changed() }
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            setButton(DatePickerDialog.BUTTON_NEUTRAL, context.getString(R.string.mpos_clear_date)) { _, _ ->
                if (isEnabled) { bind(""); changed() }
            }
            val zone = ZoneId.systemDefault()
            datePicker.minDate = LocalDate.of(1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            datePicker.maxDate = LocalDate.of(9999, 12, 31).atStartOfDay(zone).toInstant().toEpochMilli()
        }.show()
    }
}
