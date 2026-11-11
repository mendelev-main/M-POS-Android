// Test-only frozen Web source MPosReceiptLayout.kt blob02dc4a95fe3941c9633b9dd42507add8c46d9354.
package com.mendelev.mpos.fixture

import com.mendelev.mpos.fixture.WebDocumentNumbers.number
import com.mendelev.mpos.fixture.WebDocumentNumbers.objects
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Presentation model ported from NetworkPrinterManager.swift, source 67d039c. */
internal object WebReceiptReference {
    enum class Kind { TEXT, PAIR, SEPARATOR }
    data class Row(val left:String="",val right:String="",val kind:Kind=Kind.TEXT,val size:Float=23f,val weight:Int=400,val centered:Boolean=false,val gap:Float=4f)
    data class Document(val width:Int,val margin:Float,val top:Float,val bottom:Float,val rows:List<Row>)
    fun build(order:JSONObject,choosePhrase:(List<String>)->String={it.random()},date:(Long)->String={DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.getDefault()).format(Date(it))}):Document {
        val cfg=order.optJSONObject("__printerConfig") ?: JSONObject();val narrow=cfg.optInt("paperWidth",80)<=58
        val small=if(narrow)18f else 20f;val regular=if(narrow)21f else 23f;val medium=if(narrow)22f else 24f;val bold=if(narrow)29f else 32f;val title=if(narrow)30f else 33f
        val rows=mutableListOf<Row>()
        fun add(text:String,size:Float=regular,weight:Int=400,center:Boolean=false,gap:Float=4f){rows+=Row(text,size=size,weight=weight,centered=center,gap=gap)}
        fun pair(left:String,right:String,size:Float=regular,weight:Int=400,gap:Float=4f){rows+=Row(left,right,Kind.PAIR,size,weight,gap=gap)}
        fun sep(gap:Float=8f){rows+=Row(kind=Kind.SEPARATOR,size=small,gap=gap)}
        fun money(value:Double)=String.format(Locale.US,"%.2f BYN",value)
        fun qty(value:Double)=if(value%1.0==0.0)value.toLong().toString() else String.format(Locale.US,"%.2f",value)
        fun modifiers(item:JSONObject,withPrices:Boolean,kitchenSize:Float=regular) {
            for(modifier in objects(item.optJSONArray("selectedModifiers"))) {
                val name=modifier.optString("name").trim()
                if(name.isEmpty())continue
                val quantity=number(modifier,"qty",1.0)
                val delta=number(modifier,"priceDelta")
                val label="↳ "+(if(quantity>1)qty(quantity)+" × " else "")+name
                val price=if(withPrices&&delta!=0.0)" ("+(if(delta>0)"+" else "")+money(delta)+")" else ""
                add(label+price,if(withPrices)small else kitchenSize,gap=3f)
            }
        }
        val kind=order.optString("__printDocumentType","receipt");val kitchen=kind=="kitchen"
        if(kind=="shift-close") {
            order.optString("establishmentName").trim().takeIf{it.isNotEmpty()}?.let{add(it,title,700,true,5f)}
            add("ОТЧЁТ О ЗАКРЫТИИ СМЕНЫ",bold,700,true,6f);add("Сотрудник: "+order.optString("employeeName","Сотрудник"),small,400,true,3f)
            if(number(order,"openedAt")>0)add("Открыта: "+date(number(order,"openedAt").toLong()),small,400,true,2f)
            if(number(order,"closedAt")>0)add("Закрыта: "+date(number(order,"closedAt").toLong()),small,400,true,8f)
            sep();pair("Заказов",qty(number(order,"count")),gap=3f);pair("Выручка",money(number(order,"total")),medium,600,3f)
            pair("Наличные",money(number(order,"cash")),gap=3f);pair("Карта",money(number(order,"card")),gap=8f);sep()
            pair("Наличные на начало",money(number(order,"openingCash")),gap=3f);pair("Внесения",money(number(order,"deposits")),gap=3f);pair("Изъятия",money(number(order,"withdrawals")),gap=3f)
            pair("Ожидается в кассе",money(number(order,"expectedCash")),medium,600,3f);pair("Фактически в кассе",money(number(order,"countedCash")),medium,600,3f);pair("Расхождение",money(number(order,"difference")),bold,700,8f);sep();add("Смена закрыта",medium,600,true,8f)
        } else if(kitchen) {
            val kitchenTitle=if(narrow)40f else 44f
            val kitchenItem=if(narrow)38f else 42f
            val kitchenBody=if(narrow)28f else 30f
            val kitchenType=if(narrow)30f else 32f
            val kitchenDate=if(narrow)24f else 26f
            val label=order.optString("orderLabel").trim().ifBlank{order.optString("receiptDisplayNumber").trim().ifBlank{"Заказ"}}
            add(label,kitchenTitle,700,true,3f);add(date(number(order,"timestamp").toLong()),kitchenDate,400,true,8f);sep(7f)
            add(order.optString("orderType","Заказ"),kitchenType,600,true,8f);sep()
            for(item in objects(order.optJSONArray("items"))){add(qty(number(item,"qty",1.0))+" × "+item.optString("name"),kitchenItem,700,false,4f);modifiers(item,false,kitchenBody);item.optString("comment").takeIf{it.isNotEmpty()}?.let{add("↳ "+it,kitchenBody,400,false,9f)}}
        } else {
            cfg.optString("paymentReceiptTitle","ПРИЛАВОК").trim().takeIf{it.isNotEmpty()}?.let{add(it,title,700,true,14f)}
            add("Сотрудник: "+order.optString("employeeName","Сотрудник"),small,gap=2f)
            cfg.optString("registerLabel","POS 1").trim().takeIf{it.isNotEmpty()}?.let{add("Касса: "+it,small,gap=11f)}
            order.optJSONObject("customer")?.let{customer->customer.optString("name").takeIf{it.isNotEmpty()}?.let{add("Клиент: "+it,gap=2f)};customer.optString("phone").takeIf{it.isNotEmpty()}?.let{add(it,gap=10f)}}
            sep();add(order.optString("orderType","На месте"),gap=9f);sep(10f)
            for(item in objects(order.optJSONArray("items"))) {
                val q=number(item,"qty",1.0);val price=number(item,"price");val gross=q*price;val dv=number(item,"discountValue");val dt=item.optString("discountType")
                val discount=if(dt=="percent")gross*dv/100 else if(dt.isEmpty())0.0 else dv*q
                pair(item.optString("name"),money(max(0.0,gross-discount)),medium,600,0f);add(qty(q)+" × "+money(price),gap=1f)
                modifiers(item,true)
                if(discount>0)add(item.optString("discountName").trim().ifBlank{"Скидка"}+": −"+money(discount),small,gap=1f)
                if(cfg.optBoolean("printPaymentComments",true))item.optString("comment").takeIf{it.isNotEmpty()}?.let{add("Комментарий: "+it,small,gap=1f)}
                add("",small,gap=0f)
            }
            val productDiscount=number(order,"productDiscountTotal");val loyaltyDiscount=number(order,"loyaltyDiscount")
            if(productDiscount>0||loyaltyDiscount>0){sep(7f);val before=number(order,"subtotalBeforeDiscounts");if(before>0)pair("Сумма без скидок",money(before),gap=3f);if(productDiscount>0)pair("Скидки на товары","−"+money(productDiscount),gap=3f)
                val programs=objects(order.optJSONArray("loyaltyProgramsApplied"));if(programs.isNotEmpty()){for(program in programs)if(number(program,"discount")>0)pair("Лояльность · "+program.optString("name","Программа лояльности").trim(),"−"+money(number(program,"discount")),gap=3f)}else if(loyaltyDiscount>0)pair("Программа лояльности","−"+money(loyaltyDiscount),gap=3f)
            }
            if(number(order,"deliveryFee")>0)pair("Доставка",money(number(order,"deliveryFee")),gap=10f)
            sep(10f);pair("Итого",money(number(order,"total")),medium,600,12f)
            val payments=objects(order.optJSONArray("payments"))
            if(kind=="bill"){add("СЧЁТ · НЕ ОПЛАЧЕН",medium,600,true,10f)}
            else if(payments.size>1){add("Платежи",small);for((i,payment) in payments.withIndex()){val method=payment.optString("method");val label=if(method=="cash")"Наличные" else if(method=="card")"Карта" else "Платёж";pair("Платёж ${i+1} · $label",money(number(payment,"amount")),gap=3f);if(method=="cash"&&number(payment,"cashGiven")>0){pair("  Внесено",money(number(payment,"cashGiven")),small,gap=2f);pair("  Сдача",money(number(payment,"change")),small,gap=4f)}}}
            else if(payments.isNotEmpty()){val p=payments.first();val cash=p.optString("method",order.optString("method"))=="cash";pair(if(cash)"Наличные" else "Карта",money(number(p,"amount",number(order,"total"))),gap=3f);val given=number(p,"cashGiven",number(order,"cashGiven"));if(cash&&given>0){pair("Внесено",money(given),gap=3f);pair("Сдача",money(number(p,"change",number(order,"change"))),medium,600,10f)}}
            else {val cash=order.optString("method")=="cash";pair(if(cash)"Наличные" else "Карта",money(number(order,"total")),gap=3f);if(cash&&number(order,"cashGiven")>0){pair("Внесено",money(number(order,"cashGiven")),gap=3f);pair("Сдача",money(number(order,"change")),medium,600,10f)}}
            if(kind=="receipt"&&!order.has("returnedAt")&&!order.optJSONObject("customer")?.optString("id").isNullOrBlank()&&order.optJSONObject("loyaltySync")?.optString("status")=="synced") {
                for(program in objects(order.optJSONArray("loyaltyReceiptProgress"))) {
                    val goal=number(program,"goal");val filled=number(program,"filled")
                    if(!goal.isFinite()||goal%1.0!=0.0||goal<1||goal>1000||!filled.isFinite()||filled<0||filled>goal)continue
                    val name=program.optString("name").trim();if(name.isEmpty())continue
                    sep(7f);add("Акция · "+name,medium,600,false,5f)
                    (0 until goal.toInt()).toList().chunked(12).forEach { dots ->
                        add(dots.joinToString(" "){if(it<filled.toInt())"●" else "○"},medium,400,false,5f)
                    }
                }
            }
            sep(9f);pair(date(number(order,"timestamp").toLong()),order.optString("receiptDisplayNumber","#—"),small,gap=8f)
            val phrases=cfg.optJSONArray("receiptRandomPhrases")?.let{(0 until it.length()).map{index->it.optString(index).trim()}.filter{it.isNotEmpty()}} ?: emptyList()
            if(phrases.isNotEmpty()){add("",small,center=true,gap=5f);add(choosePhrase(phrases),center=true,gap=10f)}
        }
        return Document(if(narrow)384 else 576,if(narrow)14f else 24f,if(kitchen)4f else 0f,if(kitchen)10f else 4f,rows)
    }
}
