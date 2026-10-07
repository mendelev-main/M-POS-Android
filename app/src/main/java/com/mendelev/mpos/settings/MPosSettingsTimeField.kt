package com.mendelev.mpos.settings

import android.app.TimePickerDialog
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatButton
import com.mendelev.mpos.R
import com.mendelev.mpos.ui.MPosNativeTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** HH:mm presentation only; booking intervals and fallback rules remain in 098/source. */
internal class MPosSettingsTimeField(private val theme: MPosNativeTheme, private val changed: () -> Unit) : AppCompatButton(ContextThemeWrapper(theme.uiContext, if (theme.dark) androidx.appcompat.R.style.Theme_AppCompat else androidx.appcompat.R.style.Theme_AppCompat_Light)) {
    constructor(context: Context) : this(MPosNativeTheme(context, false), {})
    private var picker: TimePickerDialog? = null
    var timeValue: String = ""
        private set
    fun bind(value: String) { timeValue = value; text = value.ifBlank { context.getString(R.string.mpos_choose_time) } }
    fun dismissPicker() { picker?.dismiss() }
    init {
        theme.button(this)
        setOnClickListener {
            if (isEnabled && picker == null) {
                val initial = runCatching { LocalTime.parse(timeValue) }.getOrElse { LocalTime.of(19, 0) }
                val dialog = TimePickerDialog(theme.uiContext, { _, hour, minute -> if (isEnabled) { bind(LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"))); changed() } }, initial.hour, initial.minute, true)
                dialog.setButton(TimePickerDialog.BUTTON_NEUTRAL, context.getString(R.string.mpos_clear_time)) { _, _ -> if (isEnabled) { bind(""); changed() } }
                picker = dialog; dialog.setOnDismissListener { if (picker === dialog) picker = null }; dialog.show()
            }
        }
    }
}
