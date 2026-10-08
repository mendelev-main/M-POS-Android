package com.mendelev.mpos.workspace

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.View
import android.widget.*
import com.mendelev.mpos.data.MPosAvailabilityEngine
import com.mendelev.mpos.data.MPosJsonNumbers
import com.mendelev.mpos.data.MPosSupplyParity
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max

/** Native quantity/comment/discount draft; cancellation never writes the session. */
class MPosCartItemController(private val context:Context,private val action:(JSONObject)->Unit) {
    private var dialog:AlertDialog?=null
    private var token=""
    private var busy=false
    private var status:TextView?=null
    private val controls=mutableListOf<View>()
    fun handle(payload:JSONObject) {
        when(payload.optString("action")) {
            "cartItemHide"->if(payload.optString("token")==token)hide()
            "cartItemShow"->show(payload)
            "cartItemResult"->if(payload.optString("token")==token){busy=false;status?.text=payload.optString("message");enable()}
        }
    }
    private fun enable(){controls.forEach{it.isEnabled=!busy};dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=!busy;dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled=!busy;dialog?.setCancelable(!busy)}
    private fun show(payload:JSONObject) {
        hide();token=payload.getString("token");val model=payload.getJSONObject("model");val theme=MPosNativeTheme(context,payload.optString("theme")=="dark")
        val body=LinearLayout(theme.uiContext).apply{orientation=LinearLayout.VERTICAL;setPadding(theme.dp(24),theme.dp(16),theme.dp(24),theme.dp(8))}
        fun label(text:String,secondary:Boolean=false)=TextView(theme.uiContext).apply{this.text=text;theme.text(this,16f,secondary=secondary);setPadding(0,theme.dp(12),0,theme.dp(8))}
        fun button(text:String,click:()->Unit)=Button(theme.uiContext).apply{this.text=text;theme.button(this);controls+=this;setOnClickListener{if(!busy)click()}}
        body.addView(label("Количество"))
        var quantity:Any=model.get("quantity")
        val out=label(MPosAvailabilityEngine.text(quantity))
        val qty=LinearLayout(theme.uiContext)
        fun change(delta:Int){val current=MPosJsonNumbers.number(quantity).let{if(it.isNaN()||it==0.0)1.0 else it};quantity=max(1.0,max(1.0,current)+delta);out.text=MPosAvailabilityEngine.text(quantity)}
        qty.addView(button("−"){change(-1)},LinearLayout.LayoutParams(0,-2,1f));qty.addView(out,LinearLayout.LayoutParams(0,-2,1f));out.gravity=android.view.Gravity.CENTER
        qty.addView(button("+"){change(1)},LinearLayout.LayoutParams(0,-2,1f));body.addView(qty)
        body.addView(label("Комментарий к товару"))
        val comment=EditText(theme.uiContext).apply{setText(model.getString("comment"));hint="Например: без сахара, хорошо прожарить...";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=3;theme.text(this);backgroundTintList=ColorStateList.valueOf(theme.border);controls+=this}
        body.addView(comment)
        body.addView(label("Скидка"))
        var selected:Any=model.get("discountId");var chosen=false;val choices=mutableListOf<Triple<Button,Any,String>>()
        fun refresh(){for((view,id,value)in choices)theme.button(view,selected=if(chosen)selected==value else MPosSupplyParity.same(selected,id))}
        fun choice(label:String,id:Any,value:String){val view=button(label){selected=value;chosen=true;refresh()};choices+=Triple(view,id,value);body.addView(view,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=theme.dp(8)})}
        choice("Без скидки","","")
        val discounts=model.getJSONArray("discounts")
        for(i in 0 until discounts.length()) {
            val d=discounts.getJSONObject(i);val value=MPosJsonNumbers.number(d.opt("value"))
            val amount=if(d.opt("type")=="percent")MPosAvailabilityEngine.text(value)+"%" else String.format(Locale.US,"%.2f",MPosJsonNumbers.roundMoney(value)).replace('.',',')+" "+model.getString("currency")
            // Dataset IDs become strings after choosing, as in the reviewed source form.
            choice(d.optString("name")+" · "+amount,d.get("id"),MPosAvailabilityEngine.text(d.get("id")))
        };refresh()
        body.addView(label("Скидка применяется только к этому товару в текущем заказе.",true))
        status=label("").apply{setTextColor(theme.danger)};body.addView(status)
        val captured=token
        val form=AlertDialog.Builder(theme.uiContext).setTitle(model.optString("name")).setView(ScrollView(theme.uiContext).apply{addView(body)})
            .setNegativeButton("Отмена"){_,_->}.setPositiveButton("Сохранить",null).create()
        dialog=form;form.setOnDismissListener{if(token==captured){dialog=null;action(JSONObject().put("action","cartItemCancel").put("token",captured))}}
        form.show();theme.dialog(form)
        form.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!busy){busy=true;enable();val n=MPosJsonNumbers.number(quantity).let{if(it.isNaN()||it==0.0)1.0 else max(1.0,it)}
                if(!n.isFinite()){busy=false;status?.text="Укажите допустимое количество";enable()}
                else action(JSONObject().put("action","cartItemSave").put("token",captured).put("quantity",n).put("comment",comment.text.toString()).put("discountId",MPosAvailabilityEngine.text(selected)))}
        }
    }
    fun hide(){token="";dialog?.setOnDismissListener(null);dialog?.dismiss();dialog=null;busy=false;controls.clear();status=null}
}
