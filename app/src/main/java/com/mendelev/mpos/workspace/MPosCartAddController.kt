package com.mendelev.mpos.workspace

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.View
import android.widget.*
import com.mendelev.mpos.data.MPosAvailabilityEngine
import com.mendelev.mpos.data.MPosConfiguredPriceEngine
import com.mendelev.mpos.data.MPosJsonNumbers
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Native modifier/manual-price drafts. No HTML fields, save handlers or automatic retries. */
class MPosCartAddController(private val context:Context,private val action:(JSONObject)->Unit) {
    private var dialog:AlertDialog?=null
    private var token=""
    private var model:JSONObject?=null
    private var busy=false
    private var manualPhase=false
    private var theme=MPosNativeTheme(context,false)
    private var status:TextView?=null
    private val controls=mutableListOf<View>()
    private var selections=listOf<MutableSet<Int>>()
    fun handle(payload:JSONObject) {
        when(payload.optString("action")) {
            "cartAddHide"->if(payload.optString("token")==token)hide()
            "cartAddShow"->{hide();token=payload.getString("token");model=JSONObject(payload.getJSONObject("model").toString());theme=MPosNativeTheme(context,payload.optString("theme")=="dark")
                val groups=model!!.getJSONArray("groups");selections=(0 until groups.length()).map{linkedSetOf<Int>()};manualPhase=groups.length()==0&&model!!.getBoolean("manual");draw()}
            "cartAddResult"->if(payload.optString("token")==token){busy=false;status?.text=payload.optString("message");enable()}
        }
    }
    private fun selected()=JSONArray(selections.map{JSONArray(it.sorted())})
    private fun money(value:Double)=String.format(Locale.US,"%.2f",MPosJsonNumbers.roundMoney(value)).replace('.',',')+" "+model!!.getString("currency")
    private fun enable(){controls.forEach{it.isEnabled=!busy};dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=!busy;dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled=!busy;dialog?.setCancelable(!busy)}
    private fun draw() {
        dialog?.setOnDismissListener(null);dialog?.dismiss();controls.clear()
        val data=model?:return;val groups=data.getJSONArray("groups")
        val body=LinearLayout(theme.uiContext).apply{orientation=LinearLayout.VERTICAL;setPadding(theme.dp(24),theme.dp(16),theme.dp(24),theme.dp(8))}
        fun label(text:String,secondary:Boolean=false)=TextView(theme.uiContext).apply{this.text=text;theme.text(this,16f,secondary=secondary);setPadding(0,theme.dp(8),0,theme.dp(8))}
        var priceInput:EditText?=null
        status=label("").apply{setTextColor(theme.danger)}
        if(manualPhase) {
            body.addView(label("У товара не указана цена продажи. Введите цену для этой позиции заказа.",true));body.addView(label("Цена продажи, BYN"))
            priceInput=EditText(theme.uiContext).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;hint="0,00";theme.text(this);backgroundTintList=ColorStateList.valueOf(theme.border);controls+=this}
            body.addView(priceInput)
            val mods=MPosCartAddModel.selected(groups,selected());val extra=(0 until mods.length()).sumOf{mods.getJSONObject(it).getDouble("priceDelta")}
            if(extra!=0.0)body.addView(label("Модификаторы: "+(if(extra>0)"+" else "")+money(extra),true))
        }else {
            body.addView(label("Выберите модификаторы товара.",true))
            val total=label("");val optionsViews=mutableListOf<Triple<Button,Int,Int>>()
            fun refresh() {
                for((button,group,index)in optionsViews)theme.button(button,selected=index in selections[group])
                val mods=try{MPosCartAddModel.selected(groups,selected())}catch(_:IllegalArgumentException){MPosCartAddModel.selected(groups,selected(),false)}
                val quote=MPosConfiguredPriceEngine.calculate(JSONObject().put("version",1).put("catalogPrice",data.get("catalogPrice")).put("modifiers",mods))
                total.text="Итого: "+money(quote.getDouble("price"))
            }
            for(i in 0 until groups.length()) {
                val group=groups.getJSONObject(i);body.addView(label(group.getString("name")))
                val columns=if(context.resources.configuration.fontScale>1.4f)1 else 2
                val grid=GridLayout(theme.uiContext).apply{columnCount=columns};val options=group.getJSONArray("options")
                for(j in 0 until options.length()) {
                    val option=options.getJSONObject(j);val delta=option.getDouble("priceDelta")
                    val button=Button(theme.uiContext).apply{
                        text=option.getString("name")+(if(delta==0.0)"" else "\n"+(if(delta>0)"+" else "")+money(delta));theme.button(this);controls+=this
                        setOnClickListener {
                            if(!busy&&this in controls){val selected=selections[i];val max=group.getDouble("max")
                                if(max==1.0){val was=j in selected;selected.clear();if(!was)selected.add(j)}
                                else if(j in selected)selected.remove(j) else if(selected.size>=max){status?.text="Можно выбрать не больше "+MPosAvailabilityEngine.text(max);return@setOnClickListener}else selected.add(j)
                                status?.text="";refresh()}
                        }
                    };optionsViews+=Triple(button,i,j)
                    grid.addView(button,GridLayout.LayoutParams(GridLayout.spec(j/columns),GridLayout.spec(j%columns,1f)).apply{width=0;height=-2;setMargins(0,0,theme.dp(8),theme.dp(8))})
                };body.addView(grid)
            };body.addView(total);refresh()
        }
        body.addView(status);val captured=token
        val form=AlertDialog.Builder(theme.uiContext).setTitle(data.getString("name")).setView(ScrollView(theme.uiContext).apply{addView(body)})
            .setNegativeButton("Отмена"){_,_->}.setPositiveButton("Добавить в заказ",null).create()
        dialog=form;form.setOnDismissListener{if(token==captured){dialog=null;action(JSONObject().put("action","cartAddCancel").put("token",captured))}}
        form.show();theme.dialog(form)
        priceInput?.requestFocus()
        form.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(token!=captured||dialog!==form)return@setOnClickListener
            if(!busy)try {
                val mods=MPosCartAddModel.selected(groups,selected())
                if(data.getBoolean("manual")&&!manualPhase){manualPhase=true;draw();return@setOnClickListener}
                val payload=JSONObject().put("action","cartAddSave").put("token",captured).put("selections",selected())
                if(manualPhase){val raw=priceInput!!.text.toString();MPosConfiguredPriceEngine.calculate(JSONObject().put("version",1).put("manualInput",raw).put("modifiers",mods));payload.put("manualInput",raw)}
                busy=true;enable();action(payload)
            }catch(error:IllegalArgumentException){status?.text=if(manualPhase)"Укажите цену больше 0" else error.message}
        }
    }
    fun hide(){token="";dialog?.setOnDismissListener(null);dialog?.dismiss();dialog=null;model=null;busy=false;controls.clear();status=null;selections=emptyList()}
}
