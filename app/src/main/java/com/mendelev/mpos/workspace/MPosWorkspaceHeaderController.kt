package com.mendelev.mpos.workspace

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.mendelev.mpos.shift.MPosShiftScreenController
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONObject

/** Native fixed tabs occupy source geometry; no mounted HTML buttons are invoked. */
class MPosWorkspaceHeaderController(private val context:Context,private val host:FrameLayout,private val action:(JSONObject)->Unit) {
    private val groups=mutableListOf<View>()
    private val buttons=mutableListOf<Button>()
    private var token=""
    private var navigation:JSONObject?=null
    private var pending:String?=null
    fun handle(payload:JSONObject) {
        when(payload.optString("action")) {
            "headerHide" -> if(payload.optString("token")==token)hide()
            "headerResult" -> if(pending!=null&&payload.optString("requestToken")==pending){pending=null;enable()}
            "headerShow" -> show(payload)
        }
    }
    private fun show(payload:JSONObject) {
        val next=payload.getString("token");val model=payload.getJSONObject("navigation")
        val frames=payload.getJSONObject("groups")
        val keys=listOf("brand","tabs","settings")+if(model.has("shift"))listOf("shift") else emptyList()
        val bounds=keys.associateWith{key->MPosShiftScreenController.bounds(
            JSONObject().put("viewportWidth",payload.get("viewportWidth")).put("viewportHeight",payload.get("viewportHeight")).put("rect",frames.getJSONObject(key)),host.width,host.height)}
        if(next.isBlank()||bounds.values.any{it==null}){hide();action(JSONObject().put("action","fallback").put("token",next));return}
        groups.forEach(host::removeView);groups.clear();buttons.clear()
        token=next;navigation=JSONObject(model.toString())
        val theme=MPosNativeTheme(context,payload.optString("theme")=="dark")
        val items=model.getJSONArray("buttons")
        for(key in keys) {
            val frame=bounds.getValue(key)!!
            val row=LinearLayout(theme.uiContext).apply{gravity=android.view.Gravity.CENTER_VERTICAL}
            for(i in 0 until items.length()) {
                val item=items.getJSONObject(i);if(item.getString("group")!=key)continue
                val tab=item.getString("tab")
                row.addView(Button(theme.uiContext).apply {
                    text=when(key){"settings"->"⚙";"shift"->SpannableString("●  "+item.getString("label")).apply {
                        setSpan(ForegroundColorSpan(if(item.getBoolean("open"))0xFF3ED28E.toInt() else 0xFFFF7A70.toInt()),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    };else->item.getString("label")};contentDescription=item.getString("label")
                    theme.text(this,if(tab=="settings")22f else 14f,if(tab=="pos")700 else 600)
                    isAllCaps=false;minHeight=theme.dp(48);minimumHeight=theme.dp(48);minWidth=theme.dp(48);minimumWidth=theme.dp(48)
                    setPadding(theme.dp(if(key=="settings")0 else 14),0,theme.dp(if(key=="settings")0 else 14),0)
                    setTextColor(if(key=="shift"||item.getBoolean("selected"))Color.WHITE else 0x9EFFFFFF.toInt())
                    background=theme.shape(if(key=="shift")0x14FFFFFF else if(item.getBoolean("selected"))0x24FFFFFF else Color.TRANSPARENT,100)
                    stateListAnimator=null;buttons+=this
                    setOnClickListener{if(this in buttons&&pending==null&&token.isNotBlank()){
                        pending=token;enable()
                        val command=JSONObject().put("version",1).put("tab",tab)
                            .put("expected",JSONObject(navigation!!.getJSONObject(if(key=="shift")"shiftExpected" else "expected").toString()))
                        if(key=="shift")command.put("operation","selectShiftHeader").put("shiftRevision",navigation!!.getJSONObject("shift").getString("revision"))
                        action(JSONObject().put("token",token).put("kind",if(key=="shift")"shift" else "tab").put("command",command))
                    }}
                },LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.MATCH_PARENT).apply{if(key=="tabs")marginEnd=theme.dp(4)})
            }
            val group=HorizontalScrollView(theme.uiContext).apply {
                isHorizontalScrollBarEnabled=false;setBackgroundColor(theme.navy);addView(row)
            }
            val height=frame.height.coerceAtLeast(theme.dp(48));val width=frame.width.coerceAtLeast(theme.dp(48))
            host.addView(group,FrameLayout.LayoutParams(width,height).apply{leftMargin=(frame.left-(width-frame.width)/2).coerceAtLeast(0);topMargin=(frame.top-(height-frame.height)/2).coerceAtLeast(0)})
            groups+=group
        }
        enable()
    }
    private fun enable(){buttons.forEach{it.isEnabled=pending==null}}
    fun hide(){groups.forEach(host::removeView);groups.clear();buttons.clear();token="";navigation=null;pending=null}
}
