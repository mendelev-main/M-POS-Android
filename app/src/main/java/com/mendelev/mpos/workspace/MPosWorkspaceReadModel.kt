package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.*
import com.mendelev.mpos.payment.MPosDeliveryEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.floor

/** Read-only presentation from domain JSON. No HTML extraction, source targets or mutations. */
object MPosWorkspaceReadModel {
    fun calculate(products:JSONArray,layout:JSONObject,navigation:Any?,snapshot:JSONObject,folder:JSONObject?,order:JSONObject,shiftOpen:Boolean,parkCount:Int,columns:Int,liveScope:JSONArray?=null):JSONObject {
        val records=(0 until products.length()).map{products.getJSONObject(it)}
        fun product(id:Any?)=if(id is JSONObject||id is JSONArray)null else records.firstOrNull{MPosSupplyParity.same(it.opt("id"),id)}
        val availability=MPosAvailabilityEngine.items(products)
        fun quantity(p:JSONObject):Any {
            if(p.opt("type")!="simple")return availability.getJSONObject(records.indexOf(p)).get("quantity")
            if(MPosJsonNumbers.truthy(p.opt("noStockTracking")))return JSONObject.NULL
            val value=MPosJsonNumbers.number(p.opt("stock"))
            // POS tiles retain negative stock; publication advisory clamps it separately.
            return if(value==Double.POSITIVE_INFINITY)JSONObject.NULL else if(value.isNaN())0.0 else value
        }
        fun text(value:Any?)=MPosAvailabilityEngine.text(if(value==null||value===JSONObject.NULL)"" else value)
        fun money(value:Any?):String {
            val n=MPosJsonNumbers.number(MPosJsonNumbers.fallback(value,0));require(n.isFinite())
            return String.format(Locale.US,"%.2f",MPosJsonNumbers.roundMoney(n)).replace('.',',')+" "+order.getString("currency")
        }
        fun stock(p:JSONObject):String {
            val quantity=quantity(p)
            if(quantity===JSONObject.NULL)return "Остаток: ∞"
            val n=MPosJsonNumbers.number(quantity)
            if(folder==null&&p.opt("type")!="simple")return "Доступно: "+MPosAvailabilityEngine.text(n)
            val unit=if(p.opt("type")=="simple")p.optString("stockUnit") else text(MPosJsonNumbers.fallback(p.opt("recipeUnit"),"piece"))
            val label=mapOf("piece" to "шт.","kg" to "кг","g" to "г","l" to "л","ml" to "мл")[unit]?:"ед. (не задана)"
            return "Остаток: "+(if(n.isFinite())MPosAvailabilityEngine.text(floor((n+Math.ulp(1.0))*1000+.5)/1000) else "0")+" "+label
        }
        val actions=JSONObject()
        fun command(operation:String,value:Any?=null):String {
            val key=actions.length().toString();actions.put(key,JSONObject().put("operation",operation).put("value",value?:JSONObject.NULL));return key
        }
        fun button(label:String,operation:String,style:String="toolbar",disabled:Boolean=false,placement:String="normal")=JSONObject()
            .put("label",label).put("key",command(operation)).put("style",style).put("disabled",disabled).put("placement",placement).put("primary",style=="primary")
        val q=MPosWorkspaceSearchModel.query(snapshot.getString("search"));val path=snapshot.opt("posPath") as? String
        val parent=folder?.getString("id")?:snapshot.getString("posFolder");val tiles=JSONArray()
        val categoryItems=if(path==null)JSONArray() else MPosNavigationEngine.items(path,navigation,products)
        fun tile(type:String,id:Any?,position:JSONObject?) {
            val p=if(type=="product")product(id) else null
            if(type=="product"&&p==null)return
            if(folder==null&&liveScope!=null) {
                if((0 until liveScope.length()).none{liveScope.getJSONObject(it).let{entry->entry.opt("type")==type&&MPosSupplyParity.same(entry.opt("id"),id)}})return
                if(q.isNotEmpty()&&id !is String)return
            }
            if(folder==null&&q.isNotEmpty()&&(p==null||!text(p.opt("name")).lowercase(Locale.forLanguageTag("ru")).contains(q)))return
            val i=tiles.length();val item=JSONObject().put("id",type+":"+text(id)+":"+i).put("type",type)
                .put("column",position?.optInt("col")?:i%columns).put("row",position?.optInt("row")?:i/columns).put("columnSpan",1).put("rowSpan",1).put("sourceId",id?:JSONObject.NULL)
            if(type=="product") {
                val record=p!!;val quantity=quantity(record)
                item.put("name",text(record.opt("name"))).put("price",money(record.opt("price"))).put("stock",stock(record)).put("symbol",text(record.opt("tileSymbol")))
                    .put("disabled",quantity!==JSONObject.NULL&&MPosJsonNumbers.number(quantity)<=0).put("key",command("addProduct",text(record.opt("id"))))
            }else {
                val name=if(type=="folder")(0 until categoryItems.length()).map{categoryItems.getJSONObject(it)}.first{it.opt("type")=="folder"&&it.opt("id")==id}.getString("name") else text(id)
                item.put("name",name).put("stock",if(type=="category")"Категория" else "").put("price","").put("disabled",false)
                    .put("symbol",if(type=="category")text(layout.optJSONObject("categorySymbols")?.opt(text(id))) else "")
                    .put("color",if(type=="category")layout.optJSONObject("categoryColors")?.optString(text(id),"#EEF1F5")?:"#EEF1F5" else "")
                    .put("route",JSONObject().put("operation",if(type=="folder")"openFolder" else "openCategory").put("value",id))
            };tiles.put(item)
        }
        if(path==null) {
            val cats=layout.optJSONArray("categoryOrder")?:JSONArray()
            val entries=MPosNavigationEngine.calculate(JSONObject().put("version",1).put("operation","normalizeTiles").put("tiles",layout.optJSONArray("tiles")?:JSONArray())).getJSONArray("tiles")
            for(i in 0 until entries.length()) {
                val entry=entries.optJSONObject(i)?:continue;val type=entry.optString("type");val id=entry.opt("id")
                if(type=="product"||type=="category"&&(0 until cats.length()).any{MPosSupplyParity.same(cats.opt(it),id)})tile(type,id,entry)
            }
        }else for(i in 0 until categoryItems.length()) {
            val entry=categoryItems.getJSONObject(i)
            if(if(folder==null&&q.isNotEmpty())entry.opt("type")=="product" else entry.opt("parentId")==parent)tile(entry.getString("type"),entry.opt("id"),null)
        }
        val nav=MPosWorkspaceToolbarModel.calculate(snapshot,folder,navigation)
        val items=order.getJSONArray("items");val discounts=order.getJSONArray("discounts")
        val quote=MPosCartTotalsEngine.calculate(JSONObject().put("version",1).put("items",items).put("discounts",discounts)
            .put("orderType",order.getString("orderType")).put("deliveryFee",order.opt("deliveryFee"))
            .put("programs",order.getJSONArray("loyaltyPrograms")).put("redemptions",order.getJSONObject("loyaltyRedemptions")))
        val pricing=quote.getJSONObject("pricing");val lines=JSONArray();var count:Any=0.0
        for(i in 0 until items.length()) {
            val item=items.getJSONObject(i);val id=MPosJsonNumbers.fallback(item.opt("cartLineId"),item.opt("productId"))
            val discount=(0 until discounts.length()).map{discounts.getJSONObject(it)}.firstOrNull{MPosSupplyParity.same(it.opt("id"),item.opt("discountId"))}
            var details=money(item.opt("price"))+" / шт · ×"+MPosAvailabilityEngine.text(item.opt("qty"))
            if(MPosJsonNumbers.truthy(item.opt("discountId")))details+=" · "+text(MPosJsonNumbers.fallback(discount?.opt("name"),"Скидка"))
            val mods=item.optJSONArray("selectedModifiers")?:JSONArray()
            if(mods.length()>0)details+="\n↳ "+(0 until mods.length()).joinToString(" · "){text(mods.getJSONObject(it).opt("name"))}
            if(MPosJsonNumbers.truthy(item.opt("comment")))details+="\nКомментарий: "+text(item.opt("comment"))
            lines.put(JSONObject().put("id",text(id)).put("key",command("editCartLine",text(id))).put("removeKey",command("removeCartLine",text(id)))
                .put("name",text(item.opt("name"))).put("amount",money(pricing.getJSONArray("lines").getJSONObject(i).get("total"))).put("details",details))
            count=MPosCartQuantityRepository.quantity(count,item.opt("qty"))
        }
        val customer=order.getJSONObject("customer");val hasItems=items.length()>0
        val metadata=(if(MPosJsonNumbers.truthy(order.opt("orderLabel")))text(order.opt("orderLabel"))+" · " else "")+order.getString("orderType")+
            (if(MPosJsonNumbers.truthy(customer.opt("name"))||MPosJsonNumbers.truthy(customer.opt("phone")))" · "+text(MPosJsonNumbers.fallback(customer.opt("name"),customer.opt("phone"))) else "")
        val totals=JSONArray()
        if(order.opt("orderType")=="Доставка") {
            val selected=MPosDeliveryEngine.allowed(JSONObject().put("orderType","Доставка").put("fee",order.opt("deliveryFee")).put("selected",order.opt("deliveryTariffSelected")).put("rates",order.getJSONArray("deliveryRates")))
            totals.put(JSONObject().put("label","Доставка").put("value",if(selected)money(order.opt("deliveryFee")) else "Выберите тариф"))
        }
        val loyalty=quote.getJSONObject("loyalty").getDouble("discount")
        if(loyalty>0)totals.put(JSONObject().put("label","Программа лояльности").put("value","−"+money(loyalty)))
        totals.put(JSONObject().put("label","Итого").put("value",money(pricing.get("total"))))
        val cartButtons=JSONArray().put(button("Отложить","parkOrder","secondary",!hasItems))
        if(hasItems&&order.opt("source")=="web"&&MPosJsonNumbers.truthy(order.opt("webOrderId"))&&order.opt("webOrderStatus")!="ready")cartButtons.put(button("Заказ готов","readyOrder","primary"))
        cartButtons.put(button("Оплатить","payment","primary",!hasItems||!shiftOpen,"payment"))
        val toolbar=JSONArray()
        if(folder==null) {
            val overload=order.optBoolean("demandOverload")
            toolbar.put(button(if(overload)"Повышенный спрос" else "Обычная загрузка","demand",if(overload)"demandOverload" else "demand"))
                .put(button("Отложенные"+(if(parkCount>0)" $parkCount" else ""),"parked"))
            if(!shiftOpen)toolbar.put(button("Открыть смену","openShift","default",placement="notice"))
        }
        return JSONObject().put("tiles",tiles).put("columns",columns).put("actions",actions).put("navigation",nav).put("title",nav.getString("title"))
            .put("catalogScope",liveScope?:JSONArray((0 until tiles.length()).map{tiles.getJSONObject(it).let{item->JSONObject().put("type",item.get("type")).put("id",item.opt("sourceId"))}}))
            .put("context",(path?:"")+"|"+parent).put("folder",folder!=null).put("toolbar",toolbar)
            .put("notice",if(folder==null&&!shiftOpen)"Чтобы принимать оплату, откройте кассовую смену." else "")
            .put("empty",if(folder!=null)"В папке пока нет товаров." else if(path!=null)"В этой категории пока нет товаров." else "Рабочая зона пуста. Нажмите «Раскладка», чтобы добавить плитки.")
            .put("cartTitle","Текущий заказ — "+MPosAvailabilityEngine.text(count)+" поз.").put("orderComment",if(MPosJsonNumbers.truthy(order.opt("orderComment")))"Комментарий: "+text(order.opt("orderComment")) else "")
            .put("metadata",metadata).put("lines",lines).put("totals",totals).put("cartButtons",cartButtons)
            .put("cartHeaderButtons",JSONArray().put(button(if(MPosJsonNumbers.truthy(customer.opt("id")))"Изменить клиента заказа" else "Выбрать клиента","customer","customer",placement="customer"))
                .put(button(metadata,"orderSettings","orderMeta",placement="metadata")))
            .put("cartEmpty","Заказ пуст.\nНажмите на товар слева, чтобы добавить его.")
    }
}
