package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Reviewed receiving arithmetic. Preview values never supply committed stock/cost. */
object MPosReceivingEngine {
    fun find(rows:JSONArray,id:Any?):JSONObject?=(0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{MPosSupplyParity.same(it.opt("id"),id)}
    private fun numericOrZero(value:Any?):Double=MPosPurchaseEngine.number(value).let{if(it.isNaN()||it==0.0)0.0 else it}
    fun round(value:Double,scale:Double):Double {
        if(!value.isFinite())return value
        val scaled=value*scale;val base=floor(scaled)
        return (if(scaled-base>=0.5)base+1 else base)/scale
    }
    fun items(draft:JSONObject,products:JSONArray):JSONArray {
        val lines=draft.getJSONArray("lines");check(lines.length()>0){"Добавьте товары"};val result=JSONArray()
        for(i in 0 until lines.length()){
            val line=lines.getJSONObject(i);val p=find(products,line.opt("productId"));check(p!=null&&p.opt("type")=="simple"){"Товар удалён или изменил тип"}
            check(line.opt("qtyInput")!=""&&line.opt("totalInput")!=""){"Заполните количество и сумму каждой строки"}
            val invoiceQty=if(line.has("qtyInput"))MPosPurchaseEngine.number(line.opt("qtyInput")) else Double.NaN;val total=if(line.has("totalInput"))MPosPurchaseEngine.number(line.opt("totalInput")) else Double.NaN
            check(invoiceQty.isFinite()&&invoiceQty>=0&&total.isFinite()&&total>=0&&(invoiceQty!=0.0||total==0.0)){"Проверьте количество и сумму: ${p.opt("name")}"}
            val unit=line.get("unit");val pack=unit in setOf("bottle","pack","box");val base=MPosPurchaseEngine.stockUnit(p);val content=MPosJsonNumbers.fallback(line.opt("contentUnit"),base)
            val size=if(pack)MPosPurchaseEngine.number(line.opt("packSize")) else 0.0
            check(!pack||(size.isFinite()&&size>0)){"Укажите количество внутри: ${p.opt("name")}"}
            val qty=MPosPurchaseEngine.convert(if(pack)invoiceQty*size else invoiceQty,if(pack)content else unit,base)
            val row=JSONObject();for(key in listOf("id","name"))if(p.has(key))row.put(if(key=="id")"productId" else "productName",p.get(key))
            row.put("qty",qty).put("unitCost",if(qty>0)total/qty else 0).put("totalCost",total).put("stockUnit",base).put("invoiceQty",invoiceQty).put("invoiceUnit",unit).put("packSize",if(pack)size else JSONObject.NULL).put("contentUnit",content).put("invoiceUnitPrice",if(invoiceQty>0)total/invoiceQty else 0)
            result.put(row)
        };return result
    }
    fun products(items:JSONArray,products:JSONArray):JSONArray {
        val updates=linkedMapOf<Any,JSONObject>();val result=JSONArray(products.toString())
        for(i in 0 until items.length()){
            val item=items.getJSONObject(i);val p=find(products,item.opt("productId"));check(p!=null&&p.opt("type")=="simple"){"Товар приёмки недоступен"}
            val qty=item.getDouble("qty");val total=item.getDouble("totalCost");check(qty.isFinite()&&qty>=0&&total.isFinite()&&total>=0){"Некорректная строка приёмки"}
            val id=p.get("id");val old=updates[id]?:JSONObject().put("stock",numericOrZero(p.opt("stock"))).put("cost",numericOrZero(p.opt("cost")))
            if(qty>0){val stock=old.getDouble("stock");val oldQty=max(0.0,stock);val untracked=MPosJsonNumbers.truthy(p.opt("noStockTracking"));val cost=if(untracked)total/qty else (oldQty*old.getDouble("cost")+total)/(oldQty+qty)
                check(cost.isFinite()&&(stock+qty).isFinite()){"Количество или стоимость слишком велики"}
                updates[id]=JSONObject().put("stock",if(untracked)stock else stock+qty).put("cost",cost)
            }
        }
        for((id,u) in updates){val p=requireNotNull(find(result,id));p.put("stock",round(u.getDouble("stock")+Math.ulp(1.0),1000.0)).put("cost",u.getDouble("cost"))};return result
    }
    fun discrepancy(order:JSONObject?,items:JSONArray):Boolean {
        if(order==null)return false;val lines=order.getJSONArray("items")
        return (0 until lines.length()).any{i->val line=lines.getJSONObject(i);var actual=0.0
            for(j in 0 until items.length()){val row=items.getJSONObject(j);if(MPosSupplyParity.same(row.opt("productId"),line.opt("productId")))actual+=row.getDouble("qty")}
            if(line.has("expectedQty")&&line.isNull("expectedQty"))actual==0.0 else {val expected=if(line.has("expectedQty")&&!line.isNull("expectedQty"))MPosPurchaseEngine.number(line.opt("expectedQty")) else if(line.has("qty"))MPosPurchaseEngine.number(line.opt("qty")) else Double.NaN;abs(actual-expected)>Math.ulp(1.0)*16*max(1.0,max(actual,expected))}
        }
    }
}
