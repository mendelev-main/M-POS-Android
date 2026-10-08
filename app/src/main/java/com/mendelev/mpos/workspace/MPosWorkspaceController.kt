package com.mendelev.mpos.workspace

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.graphics.toColorInt
import com.mendelev.mpos.settings.MPosSettingsActionRow
import com.mendelev.mpos.shift.MPosShiftScreenController
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Native normal-operation workspace. Business values/actions come from the approved owners. */
class MPosWorkspaceController(private val context: Context, private val host: FrameLayout, private val action: (JSONObject) -> Unit) {
    private val overlay = LinearLayout(context).apply { visibility = View.GONE }
    private var theme = MPosNativeTheme(context, false)
    private var token = ""
    private var contextKey = ""
    private var busy = false
    private var blocked = false
    private var navigation: JSONObject? = null
    private var catalogScroll: ScrollView? = null
    private var cartScroll: ScrollView? = null
    private var status: TextView? = null
    private val controls = mutableListOf<View>()
    private val disabled = mutableSetOf<View>()
    private var renderedModel: JSONObject? = null
    private var layoutKey = ""
    private var sourceLayout = false
    private var sourceScale = 1f
    private val totalRows = linkedMapOf<String, LinearLayout>()
    private val totalNames = linkedMapOf<String, TextView>()
    private val textBindings = mutableListOf<() -> Unit>()
    private val buttonBindings = linkedMapOf<Button, JSONObject>()
    private val tileBindings = mutableListOf<Pair<View, JSONObject>>()
    private var lineList: LinearLayout? = null
    private var lineViewport: ScrollView? = null
    private var emptyCart: TextView? = null
    private var totalsContainer: LinearLayout? = null
    private val totalBindings = linkedMapOf<String, TextView>()
    private data class LineBinding(val row: LinearLayout, val data: JSONObject, val edit: Button, val remove: Button, val removeData: JSONObject, val amount: TextView, val details: TextView)
    private val lineBindings = linkedMapOf<String, LineBinding>()
    init { host.addView(overlay, FrameLayout.LayoutParams(1, 1)) }
    fun handle(payload: JSONObject) {
        when (payload.optString("action")) {
            "hide" -> if (payload.optString("token") == token) hide()
            "result" -> if (payload.optString("token") == token) { busy = false; blocked = payload.optBoolean("blocked"); status?.text = payload.optString("message"); if (sourceLayout) status?.visibility = if (blocked || payload.optString("message").isNotBlank()) View.VISIBLE else View.GONE; status?.setTextColor(if (blocked) theme.danger else theme.muted); enable() }
            "show" -> show(payload)
        }
    }
    private fun column() = LinearLayout(theme.uiContext).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float = 16f, bold: Boolean = false, secondary: Boolean = false) = TextView(theme.uiContext).apply {
        text = value; theme.text(this, size, if (bold) 700 else 400, secondary)
    }
    private fun margin() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = theme.dp(10) }
    private fun boundLabel(value: () -> String, size: Float = 16f, bold: Boolean = false, secondary: Boolean = false, hideEmpty: Boolean = false): TextView {
        val view = label(value(), size, bold, secondary)
        val update: () -> Unit = { val next = value(); if (view.text.toString() != next) view.text = next; if (hideEmpty) view.visibility = if (next.isBlank()) View.GONE else View.VISIBLE }
        textBindings += update; update(); return view
    }
    private fun buttonStyle(value: JSONObject) = value.optString("style", "default")
    private fun updateButton(view: Button, value: JSONObject) {
        if (view.text.toString() != value.optString("label") && !(sourceLayout && buttonStyle(value) == "customer")) view.text = value.optString("label")
        val style = buttonStyle(value) + "|" + value.optBoolean("primary") + "|" + value.optBoolean("danger")
        if (view.tag != style) { theme.button(view, value.optBoolean("primary"), value.optBoolean("danger"), style = buttonStyle(value)); if (sourceLayout) MPosWorkspaceAppearance.button(view, theme, buttonStyle(value)); view.tag = style }
        view.contentDescription = value.optString("label")
        if (sourceLayout) view.alpha = if(value.optBoolean("disabled")) .4f else 1f
        if (value.optBoolean("disabled")) disabled += view else disabled -= view
    }
    private fun button(value: JSONObject) = Button(theme.uiContext).apply {
        controls += this; buttonBindings[this] = value; updateButton(this, value)
        setOnClickListener { if (this in controls) emit(value.optString("key")) }
    }
    private fun structuralKey(payload: JSONObject, model: JSONObject): String {
        val tiles = model.optJSONArray("tiles") ?: JSONArray()
        val geometry = JSONArray()
        for (i in 0 until tiles.length()) {
            val tile = tiles.getJSONObject(i)
            geometry.put(JSONArray().put(tile.optString("id", tile.optString("key"))).put(tile.optString("type"))
                .put(tile.optInt("column")).put(tile.optInt("row")).put(tile.optInt("columnSpan", 1)).put(tile.optInt("rowSpan", 1)))
        }
        val nav = model.optJSONObject("navigation")
        return JSONArray().put(payload.optString("theme")).put(payload.optJSONObject("rect")).put(payload.optInt("viewportWidth"))
            .put(payload.optInt("viewportHeight")).put(context.resources.configuration.fontScale.toDouble())
             .put(model.optBoolean("sourceLayout")).put(model.optJSONObject("presentation")).put(model.optString("context")).put(model.optInt("columns", 4)).put(model.optBoolean("folder"))
            .put(nav != null).put(nav?.optJSONArray("buttons")?.toString()).put(geometry)
            .put(model.optJSONArray("toolbar")?.length() ?: 0).put(model.optJSONArray("cartHeaderButtons")?.length() ?: 0)
            .put(model.optJSONArray("cartButtons")?.let { buttons -> JSONArray().also { placements -> for(i in 0 until buttons.length()) placements.put(buttons.getJSONObject(i).optString("placement")) } }).toString()
    }
    /** Preserve captured object identities while replacing their data/actions with the current snapshot. */
    private fun merge(target: JSONObject, source: JSONObject, skipLines: Boolean = false) {
        for (key in target.keys().asSequence().toList()) if ((!skipLines || key != "lines") && !source.has(key)) target.remove(key)
        for (key in source.keys()) {
            if (skipLines && key == "lines") continue
            val old = target.opt(key); val next = source.opt(key)
            if (old is JSONObject && next is JSONObject) merge(old, next)
            else if (old is JSONArray && next is JSONArray && old.length() == next.length()) {
                for (i in 0 until next.length()) {
                    val entry = old.opt(i); val replacement = next.opt(i)
                    if (entry is JSONObject && replacement is JSONObject) merge(entry, replacement) else old.put(i, replacement)
                }
            } else target.put(key, next)
        }
    }
    private fun refreshTiles() {
        for ((view, tile) in tileBindings) {
            view.contentDescription = tile.optString("name")
            val control = if(sourceLayout && view is FrameLayout) view.getChildAt(view.childCount-1) else view
            if (tile.optBoolean("disabled")) disabled += control else disabled -= control
            view.alpha = if (tile.optBoolean("disabled")) .42f else 1f
            val fill = if (tile.optString("type") == "category") runCatching { tile.optString("color").toColorInt() }.getOrDefault(theme.soft) else theme.surface
            val color = if (theme.dark) theme.surface else fill
            if (view.tag != color) { view.background = theme.shape(color, 16, if(sourceLayout) tile.optString("type") == "category" && theme.dark else true, if(sourceLayout)runCatching { tile.optString("color").toColorInt() }.getOrDefault(theme.border) else theme.border); view.tag = color }
        }
    }
    private fun reconcileLines(lines: JSONArray, model: JSONObject) {
        val parent = lineList ?: return
        val ids = (0 until lines.length()).map { lines.getJSONObject(it).let { line -> line.optString("id").ifBlank { line.optString("key") } } }
        require(ids.toSet().size == ids.size) { "Duplicate cart row identity" }
        for (id in lineBindings.keys.toList()) if (id !in ids) {
            val old = lineBindings.remove(id)!!; parent.removeView(old.row)
            controls.remove(old.edit); controls.remove(old.remove); disabled.remove(old.edit); disabled.remove(old.remove)
            buttonBindings.remove(old.remove)
        }
        for (i in ids.indices) {
            val next = lines.getJSONObject(i)
            val binding = lineBindings[ids[i]] ?: createLine(JSONObject(next.toString())).also { lineBindings[ids[i]] = it }
            merge(binding.data, next)
            val line = binding.data
            if (binding.edit.text.toString() != line.optString("name")) binding.edit.text = line.optString("name")
            binding.removeData.put("key", line.optString("removeKey")); updateButton(binding.remove, binding.removeData)
            binding.remove.contentDescription = "Удалить " + line.optString("name")
            if (binding.amount.text.toString() != line.optString("amount")) binding.amount.text = line.optString("amount")
            if (binding.details.text.toString() != line.optString("details")) binding.details.text = line.optString("details")
            if (parent.indexOfChild(binding.row) != i) { (binding.row.parent as? ViewGroup)?.removeView(binding.row); parent.addView(binding.row, i, margin()) }
        }
        emptyCart?.let { it.text = if(sourceLayout) model.optString("cartEmpty").replace(". ", ".\n") else model.optString("cartEmpty"); it.visibility = if (lines.length() == 0) View.VISIBLE else View.GONE }
        if (overlay.orientation == LinearLayout.VERTICAL) lineViewport?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, theme.dp((lines.length() * 170).coerceIn(80, 240)))
    }
    private fun reconcileTotals(totals: JSONArray) {
        val parent = totalsContainer ?: return
        val occurrences = mutableMapOf<String, Int>()
        val ids = (0 until totals.length()).map { i ->
            val name = totals.getJSONObject(i).optString("label")
            val occurrence = occurrences.getOrDefault(name, 0); occurrences[name] = occurrence + 1
            "$name|$occurrence"
        }
        for (id in totalBindings.keys.toList()) if (id !in ids) { parent.removeView(totalRows.remove(id) ?: totalBindings[id]); totalBindings.remove(id); totalNames.remove(id) }
        for (i in ids.indices) {
            val total = totals.getJSONObject(i); val last = i == totals.length() - 1
            val view = totalBindings.getOrPut(ids[i]) {
                label("").also { value -> if(sourceLayout) {
                    val name=label("",14.5f,secondary=true);totalNames[ids[i]]=name
                    val row=LinearLayout(theme.uiContext).apply{gravity=android.view.Gravity.CENTER_VERTICAL;addView(name,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));addView(value)}
                    value.gravity=android.view.Gravity.END;totalRows[ids[i]]=row
                } }
            }
            val role = if (last) "final" else "detail"
            if (view.tag != role) { theme.text(view, if (last) (if(sourceLayout)26f else 22f) else 14f, if (last) 800 else 600, !last); view.tag = role }
            val next = if(sourceLayout)total.optString("value") else total.optString("label") + "  " + total.optString("value")
            totalNames[ids[i]]?.text=total.optString("label")
            if (view.text.toString() != next) view.text = next
            val row:View=totalRows[ids[i]] ?: view
            if (parent.indexOfChild(row) != i) { (row.parent as? ViewGroup)?.removeView(row); parent.addView(row, i, margin()) }
        }
    }

    private fun createLine(line: JSONObject): LineBinding {
        val row = column().apply { background = if(sourceLayout)theme.shape(theme.surface,0) else theme.shape(theme.bg,12,true);setPadding(theme.dp(if(sourceLayout)6 else 12),theme.dp(if(sourceLayout)9 else 12),theme.dp(if(sourceLayout)6 else 12),theme.dp(if(sourceLayout)9 else 12)) }
        val titleRow = LinearLayout(theme.uiContext)
        val edit = Button(theme.uiContext).apply {
            theme.button(this); if(sourceLayout){theme.text(this,14f,700);background=theme.shape(android.graphics.Color.TRANSPARENT,0);minHeight=0;minimumHeight=0;setPadding(0,0,0,0);includeFontPadding=false;stateListAnimator=null}; controls += this; gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setOnClickListener { if (this in controls) emit(line.optString("key")) }
        }
        titleRow.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val removeData = JSONObject().put("label", "×").put("key", line.optString("removeKey")).put("danger", true)
        val remove = button(removeData)
        titleRow.addView(remove, LinearLayout.LayoutParams(theme.dp(48), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = theme.dp(8) })
        val amount = label("",if(sourceLayout)14f else 17f,true);val details=label("",if(sourceLayout)12f else 13f,secondary=true)
        if(sourceLayout){remove.visibility=View.GONE;titleRow.addView(amount);row.addView(titleRow);row.addView(details);row.addView(View(theme.uiContext).apply{setBackgroundColor(theme.border)},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,theme.dp(1)).apply{topMargin=theme.dp(9)})}
        else {row.addView(titleRow,margin());row.addView(amount,margin());row.addView(details,margin())}
        if(sourceLayout) edit.accessibilityDelegate=object:View.AccessibilityDelegate(){
            override fun onInitializeAccessibilityNodeInfo(host:View,info:android.view.accessibility.AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(host,info);info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS,"Удалить "+line.optString("name")))}
            override fun performAccessibilityAction(host:View,id:Int,args:android.os.Bundle?):Boolean {if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS&&host in controls){emit(line.optString("removeKey"));return true};return super.performAccessibilityAction(host,id,args)}
        }
        installSwipe(edit) { line.optString("removeKey") }
        return LineBinding(row, line, edit, remove, removeData, amount, details)
    }
    private fun show(payload: JSONObject) {
        val next = payload.optString("token"); val model = payload.optJSONObject("model") ?: return
        val bounds = MPosShiftScreenController.bounds(payload, host.width, host.height)
        if (next.isBlank()) return
        if (bounds == null) { hide(); action(JSONObject().put("action", "fallback").put("token", next)); return }
        val signature = structuralKey(payload, model)
        if (payload.optBoolean("retainedUpdates", true) && layoutKey == signature && renderedModel != null && overlay.visibility == View.VISIBLE) {
            token = next; blocked = model.optBoolean("blocked")
            val retained = renderedModel!!; merge(retained, model, skipLines = true)
            navigation = retained.optJSONObject("navigation")
            textBindings.forEach { it() }; buttonBindings.forEach { (view, value) -> updateButton(view, value) }
            refreshTiles(); reconcileLines(model.optJSONArray("lines") ?: JSONArray(), retained)
            reconcileTotals(retained.optJSONArray("totals") ?: JSONArray())
            if(sourceLayout)status?.visibility=if(blocked)View.VISIBLE else View.GONE
            status?.text = if (blocked) "Перезапустите M POS для восстановления заказа" else ""
            enable(); return
        }
        val sameContext = contextKey == model.optString("context")
        val catalogY = if (sameContext) catalogScroll?.scrollY ?: 0 else 0
        val cartY = cartScroll?.scrollY ?: 0
        token = next; blocked = model.optBoolean("blocked"); contextKey = model.optString("context"); controls.clear(); disabled.clear(); textBindings.clear(); buttonBindings.clear(); tileBindings.clear(); lineBindings.clear(); totalBindings.clear();totalRows.clear();totalNames.clear(); lineViewport = null; totalsContainer = null; overlay.removeAllViews()
        renderedModel = model; layoutKey = signature
        navigation=model.optJSONObject("navigation")
        sourceLayout=model.optBoolean("sourceLayout");sourceScale=(host.width/payload.optDouble("viewportWidth",host.width.toDouble())).toFloat()
        theme = MPosNativeTheme(context, payload.optString("theme") == "dark")
        val presentation=model.optJSONObject("presentation")
        val outerPadding=if(sourceLayout)((presentation?.optDouble("padding",16.0)?:16.0)*sourceScale).toInt() else theme.dp(12)
        overlay.setBackgroundColor(theme.bg); overlay.setPadding(outerPadding,outerPadding,outerPadding,outerPadding)
        overlay.orientation = if (if(sourceLayout)model.optJSONObject("presentation")?.optBoolean("vertical")!=true else bounds.width >= theme.dp(720)) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        overlay.layoutParams = FrameLayout.LayoutParams(bounds.width, bounds.height).apply { leftMargin = bounds.left; topMargin = bounds.top }
        val catalog = column(); val cart = column().apply { background = theme.shape(theme.surface,22,!sourceLayout);clipToOutline=true;setPadding(theme.dp(if(sourceLayout)0 else 16),theme.dp(if(sourceLayout)0 else 16),theme.dp(if(sourceLayout)0 else 16),theme.dp(if(sourceLayout)0 else 16)) }
        val title = boundLabel({ navigation?.optString("title")?:model.optString("title") },if(sourceLayout)14.5f else 24f,true)
        val toolbar=model.optJSONArray("toolbar") ?: JSONArray()
        val nativeButtons=navigation?.optJSONArray("buttons") ?: JSONArray()
        fun nativeButton(value:JSONObject):Button=Button(theme.uiContext).apply {
            text=value.getString("label");theme.button(this);if(sourceLayout)MPosWorkspaceAppearance.button(this,theme,"toolbar");controls+=this
            setOnClickListener{if(this in controls)emitNavigation(value.getString("operation"))}
        }
        if(sourceLayout&&!model.optBoolean("folder")) {
            val notice=column()
            notice.addView(boundLabel({model.optString("notice")},13f,hideEmpty=true))
            for(i in 0 until toolbar.length())if(toolbar.getJSONObject(i).optString("placement")=="notice")notice.addView(button(toolbar.getJSONObject(i)))
            if(model.optString("notice").isNotBlank())catalog.addView(notice,margin())
            val header=LinearLayout(theme.uiContext).apply{gravity=android.view.Gravity.CENTER_VERTICAL}
            val actions=LinearLayout(theme.uiContext).apply{gravity=android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL}
            for(i in 0 until nativeButtons.length())if(nativeButtons.getJSONObject(i).optString("operation")=="closeCategory")header.addView(nativeButton(nativeButtons.getJSONObject(i)))
            MPosWorkspaceAppearance.title(title,theme);header.addView(title)
            header.addView(View(theme.uiContext),LinearLayout.LayoutParams(0,1,1f))
            var inserted=false
            fun insertLayout(){if(!inserted){for(i in 0 until nativeButtons.length())if(nativeButtons.getJSONObject(i).optString("operation")=="toggleEdit")actions.addView(nativeButton(nativeButtons.getJSONObject(i)),LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{marginStart=theme.dp(10)});inserted=true}}
            for(i in 0 until toolbar.length()){
                val value=toolbar.getJSONObject(i);if(value.optString("placement")=="notice")continue
                if(buttonStyle(value)!="demand"&&buttonStyle(value)!="demandOverload")insertLayout()
                actions.addView(button(value),LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{marginStart=theme.dp(10)})
            }
            insertLayout();header.addView(actions);catalog.addView(header,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=theme.dp(12)})
        } else {
            catalog.addView(title,margin());catalog.addView(boundLabel({model.optString("notice")},14f,secondary=true,hideEmpty=true),margin())
            val bar=LinearLayout(theme.uiContext)
            for(i in 0 until nativeButtons.length())bar.addView(nativeButton(nativeButtons.getJSONObject(i)))
            for(i in 0 until toolbar.length())bar.addView(button(toolbar.getJSONObject(i)))
            if(model.optBoolean("folder")&&navigation==null){val closeData=JSONObject().put("key",model.optString("closeKey")).put("label","Закрыть");textBindings+={closeData.put("key",model.optString("closeKey"));Unit};bar.addView(button(closeData))}
            catalog.addView(HorizontalScrollView(theme.uiContext).apply{addView(bar)},margin())
        }
        status = label(if (blocked) "Перезапустите M POS для восстановления заказа" else "", 14f, secondary = true); catalog.addView(status, margin());if(sourceLayout)status?.visibility=if(blocked)View.VISIBLE else View.GONE
        val grid = MPosWorkspaceGrid(theme.uiContext, model.optInt("columns", 4).coerceIn(1, 12), if(sourceLayout)((presentation?.optDouble("gridGap",12.0)?:12.0)*sourceScale).toInt() else theme.dp(10), if(sourceLayout)((model.optJSONObject("presentation")?.optDouble("rowHeight",155.0)?:155.0)*sourceScale).toInt().coerceAtLeast(theme.dp(56)) else theme.dp((170 * context.resources.configuration.fontScale.coerceAtLeast(1f)).toInt()))
        if(sourceLayout)grid.setPadding((4*sourceScale).toInt(),(4*sourceScale).toInt(),(4*sourceScale).toInt(),(4*sourceScale).toInt())
        val tiles = model.optJSONArray("tiles") ?: JSONArray()
        for (i in 0 until tiles.length()) {
            val tile = tiles.getJSONObject(i)
            val cell = column().apply {
                val fill = if (tile.optString("type") == "category") runCatching { tile.optString("color").toColorInt() }.getOrDefault(theme.soft) else theme.surface
                background = theme.shape(if (theme.dark) theme.surface else fill, 16, true)
                setPadding(theme.dp(if(sourceLayout)14 else 12), theme.dp(if(sourceLayout)14 else 12), theme.dp(if(sourceLayout)14 else 12), theme.dp(if(sourceLayout)14 else 12)); contentDescription = tile.optString("name")
                isClickable = true; isFocusable = true; controls += this
                if (tile.optBoolean("disabled")) { disabled += this; alpha = .42f }
                setOnClickListener {
                    if(this in controls) {
                        val route=tile.optJSONObject("route")
                        if(navigation!=null&&route!=null)emitNavigation(route.getString("operation"),route.getString("value"))
                        else emit(tile.optString("key"))
                    }
                }
            }
            tileBindings += cell to tile
            if(!sourceLayout)cell.addView(boundLabel({ tile.optString("symbol") }, 22f, hideEmpty = true))
            cell.addView(boundLabel({ tile.optString("name") }, if(sourceLayout)14.5f else 16f, true).apply { maxLines = 4; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            cell.addView(boundLabel({ tile.optString("price") }, if(sourceLayout)15.5f else 17f, true, hideEmpty = true))
            cell.addView(boundLabel({ tile.optString("stock") }, if(sourceLayout)11.5f else 12f, secondary = true, hideEmpty = true))
            if(sourceLayout){
                val frame=FrameLayout(theme.uiContext)
                frame.addView(boundLabel({tile.optString("symbol")},46f,hideEmpty=true).apply{gravity=android.view.Gravity.CENTER;alpha=.6f},FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
                // Keep the symbol beneath the text without covering it with the card fill.
                frame.background=cell.background;cell.background=null;cell.alpha=1f
                frame.addView(cell,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
                tileBindings.removeAt(tileBindings.lastIndex);tileBindings+=frame to tile
                grid.addTile(frame,tile)
            }else grid.addTile(cell, tile)
        }
        val catalogContent: View = if (tiles.length() > 0) grid else boundLabel({ model.optString("empty") }, secondary = true)
        catalogScroll = ScrollView(theme.uiContext).apply { addView(catalogContent) }
        catalog.addView(catalogScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val headerButtons=model.optJSONArray("cartHeaderButtons") ?: JSONArray()
        val cartTitle=boundLabel({model.optString("cartTitle")},if(sourceLayout)17f else 19f,true)
        if(sourceLayout){
            val head=LinearLayout(theme.uiContext).apply{gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(theme.dp(20),theme.dp(18),theme.dp(20),theme.dp(10));addView(cartTitle,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))}
            val meta=column().apply{setPadding(theme.dp(12),0,theme.dp(12),theme.dp(2))}
            for(i in 0 until headerButtons.length()){val value=headerButtons.getJSONObject(i);if(value.optString("placement")=="customer")head.addView(button(value),LinearLayout.LayoutParams(theme.dp(44),theme.dp(44))) else meta.addView(button(value),LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))}
            meta.addView(boundLabel({model.optString("orderComment")},12f,hideEmpty=true));cart.addView(head);cart.addView(meta)
        }else{
            cart.addView(cartTitle,margin());cart.addView(boundLabel({model.optString("metadata")},14f,secondary=true,hideEmpty=true),margin())
            val headerActions=MPosSettingsActionRow(theme.uiContext,theme.dp(8));for(i in 0 until headerButtons.length())headerActions.addView(button(headerButtons.getJSONObject(i)));if(headerButtons.length()>0)cart.addView(headerActions,margin())
        }
        val lineList = column(); this.lineList = lineList
        emptyCart = label(model.optString("cartEmpty"),if(sourceLayout)14f else 16f,secondary=true).apply{if(sourceLayout){gravity=android.view.Gravity.CENTER;setPadding(theme.dp(20),theme.dp(40),theme.dp(20),theme.dp(40))}}; lineList.addView(emptyCart)
        val lines = model.optJSONArray("lines") ?: JSONArray(); reconcileLines(lines, model)
        cartScroll = ScrollView(theme.uiContext).apply { if(sourceLayout)setPadding(theme.dp(14),0,theme.dp(14),0);addView(lineList) }
        lineViewport = cartScroll
        cart.addView(cartScroll, if (overlay.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, theme.dp((lines.length() * 170).coerceIn(80, 240))))
        if(sourceLayout)cart.addView(View(theme.uiContext).apply{setBackgroundColor(theme.border)},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,theme.dp(1)))
        val foot=if(sourceLayout)column().apply{setPadding(theme.dp(20),theme.dp(16),theme.dp(20),theme.dp(20))} else cart
        totalsContainer=column();foot.addView(totalsContainer);reconcileTotals(model.optJSONArray("totals") ?: JSONArray())
        val buttons=model.optJSONArray("cartButtons") ?: JSONArray()
        if(sourceLayout){
            val secondary=LinearLayout(theme.uiContext)
            for(i in 0 until buttons.length()){val value=buttons.getJSONObject(i);if(value.optString("placement")!="payment")secondary.addView(button(value),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{if(secondary.childCount>0)marginStart=theme.dp(8)})}
            foot.addView(secondary)
            for(i in 0 until buttons.length()){val value=buttons.getJSONObject(i);if(value.optString("placement")=="payment")foot.addView(button(value),LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=theme.dp(8)})}
            cart.addView(foot)
        }else{val footerActions=MPosSettingsActionRow(theme.uiContext,theme.dp(8));for(i in 0 until buttons.length())footerActions.addView(button(buttons.getJSONObject(i)));cart.addView(footerActions,margin())}
        if (overlay.orientation == LinearLayout.HORIZONTAL) {
            overlay.addView(catalog, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if(sourceLayout)1f else 1.8f).apply { marginEnd = theme.dp(if(sourceLayout)16 else 12) })
            overlay.addView(cart, if(sourceLayout)LinearLayout.LayoutParams(((model.optJSONObject("presentation")?.optDouble("cartWidth",380.0)?:380.0)*sourceScale).toInt().coerceIn(theme.dp(220),bounds.width*2/3),ViewGroup.LayoutParams.MATCH_PARENT) else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else {
            overlay.addView(catalog, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.3f).apply { bottomMargin = theme.dp(12) })
            val portraitCart = ScrollView(theme.uiContext).apply { addView(cart) }
            overlay.addView(portraitCart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); cartScroll = portraitCart
        }
        refreshTiles(); overlay.visibility = View.VISIBLE; enable()
        catalogScroll?.post { catalogScroll?.scrollTo(0, catalogY) }; cartScroll?.post { cartScroll?.scrollTo(0, cartY) }
    }
    @SuppressLint("ClickableViewAccessibility") // Ordinary taps use Button.performClick; swipe removal is also an accessible button.
    private fun installSwipe(view: View, removeKey: () -> String) {
        var startX = 0f; var startY = 0f; var horizontal = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; horizontal = false; false }
                MotionEvent.ACTION_MOVE -> { val dx = event.x - startX; val dy = event.y - startY; horizontal = dx < -theme.dp(8) && abs(dx) > abs(dy); if (horizontal) view.parent.requestDisallowInterceptTouchEvent(true); horizontal }
                MotionEvent.ACTION_UP -> { val remove = horizontal && event.x - startX < -theme.dp(82); if (remove && view in controls) emit(removeKey()); view.parent.requestDisallowInterceptTouchEvent(false); val consumed = horizontal; if (consumed) view.isPressed = false; horizontal = false; consumed }
                MotionEvent.ACTION_CANCEL -> { horizontal = false; view.parent.requestDisallowInterceptTouchEvent(false); false }
                else -> false
            }
        }
    }
    private fun emit(key: String) { if (busy || blocked || token.isBlank()) return; busy = true; enable(); action(JSONObject().put("action", "click").put("token", token).put("key", key)) }
    private fun emitNavigation(operation:String,value:String?=null) {
        val model=navigation?:return
        if(busy||blocked||token.isBlank())return
        busy=true;enable()
        action(JSONObject().put("action","navigate").put("token",token).put("command",JSONObject().put("version",1)
            .put("route",operation).put("value",value?:JSONObject.NULL).put("expected",JSONObject(model.getJSONObject("expected").toString()))
            .put("folderModal",model.opt("folderModal")?:JSONObject.NULL)))
    }
    private fun enable() { controls.forEach { it.isActivated = busy && !blocked && it !in disabled; it.isEnabled = !busy && !blocked && it !in disabled } }
    fun hide() { overlay.visibility = View.GONE; token = ""; contextKey = ""; busy = false; blocked = false; navigation=null; controls.clear(); disabled.clear(); overlay.removeAllViews(); catalogScroll = null; cartScroll = null; status = null; renderedModel = null; layoutKey = ""; textBindings.clear(); buttonBindings.clear(); tileBindings.clear(); lineBindings.clear(); lineList = null; lineViewport = null; emptyCart = null; totalsContainer = null; totalBindings.clear();totalRows.clear();totalNames.clear() }
}

/** Source coordinates/spans are presentation metadata; no catalogue layout is written here. */
internal class MPosWorkspaceGrid @JvmOverloads constructor(context: Context, private val columns: Int = 4, private val gap: Int = 10, private val rowHeight: Int = 170) : ViewGroup(context) {
    private data class Cell(val column: Int, val row: Int, val width: Int, val height: Int)
    private val cells = mutableListOf<Cell>()
    fun addTile(view: View, tile: JSONObject) {
        val column = tile.optInt("column").coerceIn(0, columns - 1); val row = tile.optInt("row").coerceIn(0, 10000)
        cells += Cell(column, row, tile.optInt("columnSpan", 1).coerceIn(1, columns - column), tile.optInt("rowSpan", 1).coerceIn(1, 100))
        addView(view)
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec); val cellWidth = ((width - paddingLeft - paddingRight - gap * (columns - 1)) / columns).coerceAtLeast(1)
        for (i in cells.indices) { val c = cells[i]; getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellWidth * c.width + gap * (c.width - 1), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowHeight * c.height + gap * (c.height - 1), MeasureSpec.EXACTLY)) }
        val rows = cells.maxOfOrNull { it.row + it.height } ?: 0
        setMeasuredDimension(width, paddingTop + paddingBottom + rows * rowHeight + (rows - 1).coerceAtLeast(0) * gap)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val cellWidth = ((width - paddingLeft - paddingRight - gap * (columns - 1)) / columns).coerceAtLeast(1)
        for (i in cells.indices) { val c = cells[i]; val x = paddingLeft + c.column * (cellWidth + gap); val y = paddingTop + c.row * (rowHeight + gap); getChildAt(i).layout(x, y, x + getChildAt(i).measuredWidth, y + getChildAt(i).measuredHeight) }
    }
}
