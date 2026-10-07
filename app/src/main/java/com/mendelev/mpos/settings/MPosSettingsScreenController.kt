package com.mendelev.mpos.settings

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import com.mendelev.mpos.product.MPosProductPreviewLoader
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.view.isNotEmpty
import com.mendelev.mpos.shift.MPosShiftScreenController
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONArray
import org.json.JSONObject

/** Native presentation only. Tokens address mounted reviewed actions, never executable snippets. */
class MPosSettingsScreenController(
    private val context: Context,
    private val host: FrameLayout,
    private val action: (JSONObject) -> Unit,
) {
    private var theme = MPosNativeTheme(context, false)
    private val overlay = ScrollView(context).apply { visibility = View.GONE; isFillViewport = true }
    private var dialog: AlertDialog? = null
    private var token = ""
    private var busy = false
    private var externalBusy = false
    private var blocked = false
    private var form = false
    private var updating = false
    private var error: TextView? = null
    private var content: LinearLayout? = null
    private var formScroll: ScrollView? = null
    private var reuseFields: Map<String, View> = emptyMap()
    private val dirtyFields = mutableSetOf<String>()
    private val previewLoader = MPosProductPreviewLoader(context)
    private var imageSource: String? = null
    private var imageView: ImageView? = null
    private val controls = mutableListOf<View>()
    private val immutableControls = mutableSetOf<View>()
    private val fields = linkedMapOf<String, View>()
    private val fieldRows = linkedMapOf<String, LinearLayout>()
    private val fieldLabels = linkedMapOf<String, TextView>()
    init { host.addView(overlay, FrameLayout.LayoutParams(1, 1)) }

    fun handle(payload: JSONObject) {
        when (payload.optString("action")) {
            "show", "formShow" -> show(payload)
            "hide", "formHide" -> if (payload.optString("token") == token) dismiss()
            "formPatch" -> if (payload.optString("token") == token && form && !busy) patch(payload)
            "formUpdate" -> if (payload.optString("token") == token) update(payload.optJSONArray("fields"))
            "formResult" -> if (payload.optString("token") == token) {
                busy = false; blocked = payload.optBoolean("blocked")
                error?.text = payload.optString("message")
                error?.setTextColor(if (blocked || payload.optBoolean("error", true)) theme.danger else theme.muted); updateControls()
            }
        }
    }
    private fun column(padding: Int = 0) = LinearLayout(theme.uiContext).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(theme.dp(padding), theme.dp(padding), theme.dp(padding), theme.dp(padding))
    }
    private fun params() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = theme.dp(10)
    }
    private fun show(payload: JSONObject) {
        val next = payload.optString("token")
        if (next.isBlank()) return
        val nativeForm = payload.optString("action") == "formShow"
        val bounds = if (!nativeForm) MPosShiftScreenController.bounds(payload, host.width, host.height) ?: return else null
        dismiss(); token = next; form = nativeForm; externalBusy = payload.optBoolean("pending")
        theme = MPosNativeTheme(context, payload.optString("theme") == "dark")
        updating = true
        val content = column(24); this.content = content
        content.setBackgroundColor(if (form) theme.surface else theme.bg)
        render(payload.optJSONArray("items") ?: JSONArray(), content)
        updating = false
        if (form) {
            error = TextView(theme.uiContext).apply { theme.text(this, 14f); setTextColor(theme.danger) }
            content.addView(error, params())
            val scroll = MPosSettingsScrollView(theme.uiContext, (context.resources.displayMetrics.heightPixels * .85).toInt() - theme.dp(80)).apply { isFillViewport = false; addView(content) }
            formScroll = scroll
            // Custom action buttons above retain reviewed labels and primary/danger hierarchy.
            val current = AlertDialog.Builder(theme.uiContext).setView(scroll).setNegativeButton(payload.optString("cancelLabel", "Отмена"), null).create()
            dialog = current
            current.setOnCancelListener { cancel() }
            current.setOnShowListener {
                current.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancel() }
            }
            current.show()
            current.window?.setBackgroundDrawable(theme.shape(theme.surface, 22, true))
            current.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val metrics = context.resources.displayMetrics
            current.window?.setLayout(minOf(metrics.widthPixels - theme.dp(32), theme.dp(if (payload.optBoolean("expanded")) 1120 else 640)),
                ViewGroup.LayoutParams.WRAP_CONTENT)
            theme.button(current.getButton(AlertDialog.BUTTON_NEGATIVE))
            updateControls()
        } else {
            overlay.removeAllViews(); overlay.addView(content)
            overlay.layoutParams = FrameLayout.LayoutParams(requireNotNull(bounds).width, bounds.height).apply {
                leftMargin = bounds.left; topMargin = bounds.top
            }
            overlay.visibility = View.VISIBLE
        }
    }
    private fun render(items: JSONArray, parent: LinearLayout) {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            when (item.optString("kind")) {
                "heading", "text" -> {
                    val heading = item.optString("kind") == "heading"
                    if (item.optString("text").isBlank()) continue
                    parent.addView(TextView(theme.uiContext).apply {
                        text = item.optString("text"); theme.text(this, if (heading) 20f else 14f, if (heading) 700 else 400, !heading)
                    }, params())
                }
                "card" -> {
                    val card = column(16).apply { background = theme.shape(theme.surface, 22, true) }
                    render(item.optJSONArray("items") ?: JSONArray(), card); parent.addView(card, params())
                }
                "button" -> {
                    if (!item.optBoolean("visible", true)) continue
                    val key = item.optString("key")
                    val button = Button(theme.uiContext).apply {
                        text = item.optString("label"); contentDescription = text
                        theme.button(this, item.optBoolean("primary"), item.optBoolean("danger"), item.optBoolean("selected"))
                        isEnabled = !item.optBoolean("disabled")
                        setOnClickListener { if (!busy && !externalBusy && !blocked) submit(key) }
                    }
                    if (item.optBoolean("disabled")) immutableControls += button
                    controls += button
                    val row = (if (parent.isNotEmpty()) parent.getChildAt(parent.childCount - 1) else null) as? MPosSettingsActionRow
                        ?: MPosSettingsActionRow(theme.uiContext, theme.dp(8)).also { parent.addView(it, params()) }
                    row.addView(button)
                }
                "metric" -> {
                    val row = LinearLayout(theme.uiContext).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                    row.addView(TextView(theme.uiContext).apply { text = item.optString("label"); theme.text(this, 14f, 400, true) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(TextView(theme.uiContext).apply { text = item.optString("value"); theme.text(this, if (item.optBoolean("primary")) 24f else 16f, 700); gravity = android.view.Gravity.END }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    parent.addView(row, params())
                }
                "image" -> {
                    val source = item.optString("source")
                    val preview = if (source == imageSource && imageView != null) requireNotNull(imageView).also {
                        (it.parent as? ViewGroup)?.removeView(it)
                    } else ImageView(theme.uiContext).apply {
                        contentDescription = item.optString("label"); scaleType = ImageView.ScaleType.FIT_CENTER
                        imageSource = source; imageView = this
                        previewLoader.load(source) { bitmap ->
                            if (imageView === this && imageSource == source) {
                                setImageBitmap(bitmap)
                                if (bitmap == null) visibility = View.GONE
                            }
                        }
                    }
                    parent.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(160)))
                    if (source.isBlank()) parent.addView(TextView(theme.uiContext).apply { text = item.optString("label"); theme.text(this, 14f, 400, true) }, params())
                }
                "field" -> addField(item, parent)
            }
        }
    }
    private fun addField(item: JSONObject, parent: LinearLayout) {
        val key = item.optString("key")
        val row = column().apply { visibility = if (item.optBoolean("visible", true)) View.VISIBLE else View.GONE }
        val label = TextView(theme.uiContext).apply { text = item.optString("label"); theme.text(this, 14f, 600) }
        row.addView(label, params()); fieldLabels[key] = label
        val old = reuseFields[key]
        val field: View = if (old is EditText && item.optString("type") !in setOf("checkbox", "select")) {
            (old.parent as? ViewGroup)?.removeView(old)
            if (key !in dirtyFields && old.text.toString() != item.optString("value")) old.setText(item.optString("value"))
            val desired = inputType(item.optString("type"))
            if (old.inputType != desired) old.inputType = desired
            old.hint = item.optString("hint")
            old
        } else when (item.optString("type")) {
            "checkbox" -> CheckBox(theme.uiContext).apply {
                theme.text(this); buttonTintList = android.content.res.ColorStateList.valueOf(theme.accent)
                text = item.optString("label"); minHeight = theme.dp(48)
                isChecked = item.optBoolean("value")
                setOnCheckedChangeListener { _, _ -> changed() }
                label.visibility = View.GONE
            }
            "select" -> {
                val options = item.optJSONArray("options") ?: JSONArray()
                val values = (0 until options.length()).map { options.getJSONObject(it).optString("value") }
                val labels = (0 until options.length()).map { options.getJSONObject(it).optString("label") }
                Spinner(theme.uiContext).apply {
                    minimumHeight = theme.dp(52); background = theme.shape(theme.bg, 12, true)
                    adapter = object : ArrayAdapter<String>(theme.uiContext, android.R.layout.simple_spinner_item, labels) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                            super.getView(position, convertView, parent).also { if (it is TextView) theme.text(it) }
                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                            super.getDropDownView(position, convertView, parent).also { if (it is TextView) theme.text(it) }
                    }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    tag = values
                    setSelection(values.indexOf(item.optString("value")).coerceAtLeast(0))
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { changed() }
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
                }
            }
            else -> EditText(theme.uiContext).apply {
                theme.text(this); minHeight = theme.dp(52); background = theme.shape(theme.bg, 12, true)
                setPadding(theme.dp(16), theme.dp(12), theme.dp(16), theme.dp(12))
                inputType = inputType(item.optString("type")); hint = item.optString("hint")
                if (item.optInt("maxLength") > 0) filters = arrayOf(android.text.InputFilter.LengthFilter(item.getInt("maxLength")))
                setText(item.optString("value")); setHintTextColor(theme.muted)
                if (item.optString("type") == "multiline") minLines = 3
                if (item.optBoolean("live")) {
                    val input = this
                    var pending: Runnable? = null
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            if (updating || busy || externalBusy || blocked) return
                            dirtyFields += key
                            pending?.let { input.removeCallbacks(it) }
                            // Keep intermediate decimal input until the number is complete or the next gesture flushes it.
                            val draft = s?.toString().orEmpty()
                            if ((input.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER &&
                                (draft.isBlank() || draft == "-" || draft.endsWith(".") || draft.endsWith(","))) return
                            val epoch = token
                            pending = Runnable { if (token == epoch && fields[key] === input) changed() }
                            input.postDelayed(requireNotNull(pending), 180)
                        }
                    })
                }
                // Prevent keyboard learning/autofill of credentials; these never enter preferences/logs.
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                if (item.optString("type") == "password") imeOptions = imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
        }
        field.contentDescription = item.optString("label")
        if (item.optBoolean("readOnly")) immutableControls += field
        field.isEnabled = !item.optBoolean("readOnly")
        fields[key] = field; fieldRows[key] = row; controls += field
        row.addView(field, params()); parent.addView(row, params())
    }
    private fun inputType(type: String) = when (type) {
        "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        "number" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        "phone" -> InputType.TYPE_CLASS_PHONE
        "multiline" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
    private fun values(): JSONObject = JSONObject().also { values ->
        fields.forEach { (key, view) ->
            when (view) {
                is CheckBox -> values.put(key, view.isChecked)
                is EditText -> values.put(key, view.text.toString())
                is Spinner -> {
                    @Suppress("UNCHECKED_CAST") val options = view.tag as? List<String>
                    values.put(key, options?.getOrNull(view.selectedItemPosition) ?: "")
                }
            }
        }
    }
    private fun changed() {
        if (!updating && !busy && !externalBusy && !blocked && token.isNotBlank()) { dirtyFields.clear(); action(JSONObject().put("action", "change").put("token", token).put("fields", values())) }
    }
    private fun submit(key: String) {
        dirtyFields.clear(); busy = true; error?.text = "Выполнение…"; error?.setTextColor(theme.muted); updateControls()
        action(JSONObject().put("action", "click").put("token", token).put("key", key).put("fields", values()))
    }
    private fun patch(payload: JSONObject) {
        val root = content ?: return
        externalBusy = payload.optBoolean("pending")
        val focused = fields.entries.firstOrNull { it.value.hasFocus() }
        val selection = (focused?.value as? EditText)?.selectionStart ?: 0
        val scrollY = formScroll?.scrollY ?: 0
        updating = true
        try {
            reuseFields = fields.toMap()
            root.removeAllViews(); fields.clear(); fieldRows.clear(); fieldLabels.clear(); controls.clear(); immutableControls.clear()
            render(payload.optJSONArray("items") ?: JSONArray(), root)
            // Removed credentials/drafts cannot remain addressable by an old callback.
            reuseFields.filterKeys { it !in fields }.values.filterIsInstance<EditText>().forEach { it.setText("") }
            dirtyFields.retainAll(fields.keys)
            error?.let { (it.parent as? ViewGroup)?.removeView(it); root.addView(it, params()) }
            focused?.key?.let { fields[it] }?.let { view -> view.requestFocus(); if (view is EditText) view.setSelection(selection.coerceIn(0, view.length())) }
            formScroll?.post { formScroll?.scrollTo(0, scrollY) }
        } finally { reuseFields = emptyMap(); updating = false }
        updateControls()
    }
    private fun update(items: JSONArray?) {
        updating = true
        try {
            if (items == null) return
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i); val key = item.optString("key")
                fieldRows[key]?.visibility = if (item.optBoolean("visible", true)) View.VISIBLE else View.GONE
                fieldLabels[key]?.text = item.optString("label")
                val view = fields[key]
                if (view is CheckBox) view.text = item.optString("label")
                if (view is EditText && item.optString("type") in setOf("text", "password")) {
                    val desired = inputType(item.optString("type"))
                    if (view.inputType != desired) { val selection = view.selectionStart; view.inputType = desired; view.setSelection(selection.coerceIn(0, view.length())) }
                }
            }
        } finally { updating = false }
    }
    private fun updateControls() {
        controls.forEach { it.isEnabled = !busy && !externalBusy && !blocked && it !in immutableControls }
        dialog?.let { it.setCancelable(!busy && !externalBusy); it.setCanceledOnTouchOutside(false); it.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !busy && !externalBusy }
    }
    private fun cancel() {
        if (busy || externalBusy) return
        val old = token; val draft = values(); dismiss(); action(JSONObject().put("action", "cancel").put("token", old).put("fields", draft))
    }
    fun consumeBack(): Boolean { if (!form || dialog == null) return false; cancel(); return true }
    fun dismiss() {
        updating = true
        // Wipe form text before releasing references, including passwords and tokens.
        fields.values.filterIsInstance<EditText>().forEach { it.setText("") }
        dialog?.setOnCancelListener(null); dialog?.dismiss(); dialog = null
        overlay.visibility = View.GONE; overlay.removeAllViews()
        fields.clear(); controls.clear(); immutableControls.clear(); fieldRows.clear(); fieldLabels.clear(); error = null
        previewLoader.clear(); imageSource = null; imageView = null
        content = null; formScroll = null; reuseFields = emptyMap(); dirtyFields.clear()
        token = ""; busy = false; externalBusy = false; blocked = false; form = false; updating = false
    }
}

/** Compact wrapping actions remain usable at large font scales and narrow keyboard layouts. */
internal class MPosSettingsActionRow @JvmOverloads constructor(context: Context, private val gap: Int = 8) : ViewGroup(context) {
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        var x = 0; var height = 0; var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + child.measuredWidth > available) { height += rowHeight + gap; x = 0; rowHeight = 0 }
            x += child.measuredWidth + gap; rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        setMeasuredDimension(resolveSize(available, widthMeasureSpec), resolveSize(height + rowHeight, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var x = 0; var y = 0; var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x > 0 && x + child.measuredWidth > width) { y += rowHeight + gap; x = 0; rowHeight = 0 }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gap; rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
    }
}

internal class MPosSettingsScrollView @JvmOverloads constructor(context: Context, private val maximumHeight: Int = context.resources.displayMetrics.heightPixels) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val offered = MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maximumHeight else minOf(offered, maximumHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap.coerceAtLeast(1), MeasureSpec.AT_MOST))
    }
}
