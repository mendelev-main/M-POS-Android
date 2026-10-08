package com.mendelev.mpos.data

import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Navigation mutations, independent of Android view geometry and inventory. */
object MPosNavigationEngine {
    private fun trim(s:String)=MPosOrderContextEngine.trim(s)
    private fun same(a:Any?,b:Any?)=if(a is Number && b is Number)a.toDouble()==b.toDouble() else a==b
    private fun number(v:Any?):Double {
        fun text(x:Any?):String=when(x){null,JSONObject.NULL->"";is JSONArray->(0 until x.length()).joinToString(","){text(x.opt(it))};is JSONObject->"[object Object]";else->x.toString()}
        val value=if(v is JSONArray)text(v) else v
        if(value is String){val s=trim(value);if(s!=s.trim{it.isWhitespace()||it=='\uFEFF'})return Double.NaN;return MPosJsonNumbers.number(s)}
        return MPosJsonNumbers.number(value)
    }
    fun normalize(value:Any?):JSONObject {
        val out=JSONObject().put("version",1);val categories=JSONArray();out.put("categories",categories)
        val nav=value as? JSONObject ?: return out
        val version=nav.opt("version");if(version !is Number||version.toDouble()!=1.0)return out
        val input=nav.optJSONArray("categories")?:return out;val seen=mutableSetOf<String>()
        for(i in 0 until input.length()){
            val entry=input.optJSONObject(i)?:continue;val category=entry.opt("category") as? String?:continue;val raw=entry.optJSONArray("items")?:continue
            if(!seen.add(category))continue
            val items=JSONArray();val ids=mutableSetOf<String>()
            for(j in 0 until raw.length()){
                val item=raw.optJSONObject(j)?:continue;val type=item.opt("type") as? String?:continue;val id=item.opt("id") as? String?:continue
                if(type !in setOf("product","folder")||id.isEmpty()||"$type:$id" in ids)continue
                val name=item.opt("name") as? String
                if(type=="folder"&&(name==null||trim(name).isEmpty()))continue
                ids.add("$type:$id")
                items.put(JSONObject().put("type",type).put("id",id).put("parentId",if(type=="folder")"" else item.opt("parentId") as? String?:"").apply{if(type=="folder")put("name",trim(name!!).take(80))})
            }
            val folders=(0 until items.length()).map{items.getJSONObject(it)}.filter{it.opt("type")=="folder"}.map{it.getString("id")}.toSet()
            for(j in 0 until items.length()){val item=items.getJSONObject(j);if(item.getString("parentId").isNotEmpty()&&item.getString("parentId") !in folders)item.put("parentId","")}
            categories.put(JSONObject().put("category",category).put("items",items))
        }
        return out
    }
    private fun categoryItems(category:String,nav:JSONObject,products:JSONArray):MutableList<JSONObject>{
        val categories=nav.getJSONArray("categories")
        val saved=(0 until categories.length()).map{categories.getJSONObject(it)}.firstOrNull{it.opt("category")==category}?.getJSONArray("items")?:JSONArray()
        val matching=(0 until products.length()).map{products.getJSONObject(it)}.filter{trim(if(MPosJsonNumbers.truthy(it.opt("category")))it.getString("category") else "").ifEmpty{"Без категории"}==category}.sortedWith{a,b->
            val delta=number(MPosJsonNumbers.fallback(a.opt("sortOrder"),0))-number(MPosJsonNumbers.fallback(b.opt("sortOrder"),0));if(delta.isNaN()||delta==0.0)0 else if(delta<0)-1 else 1
        }
        val items=(0 until saved.length()).map{saved.getJSONObject(it)}.filter{it.opt("type")=="folder"||matching.any{p->same(p.opt("id"),it.opt("id"))}}.toMutableList()
        for(p in matching)if(items.none{it.opt("type")=="product"&&same(it.opt("id"),p.opt("id"))})items.add(JSONObject().put("type","product").put("id",p.opt("id")?:JSONObject.NULL).put("parentId",""))
        return items
    }
    fun hasFolder(category: String, navigation: Any?, products: JSONArray, id: Any?): Boolean =
        categoryItems(category, normalize(navigation), products).any { it.opt("type") == "folder" && same(it.opt("id"), id) }
    fun items(category:String,navigation:Any?,products:JSONArray):JSONArray = JSONArray(categoryItems(category,normalize(navigation),products))

    private fun reply()=JSONObject().put("ok",true).put("authoritative",true).put("source","native-navigation")
    private fun fail(message:String,form:Boolean=false)=reply().put("allowed",false).put("message",message).put("formError",form)
    private fun spliceIndex(n:Double,size:Int):Int {
        if(n.isNaN()||n==0.0)return 0
        if(n<0)return max(0,size+n.toInt())
        return min(size,n.toInt())
    }
    fun calculate(input:JSONObject):JSONObject {
        require(input.getInt("version")==1)
        val operation=input.getString("operation")
        if(operation in setOf("addTile","removeTile","moveTile","normalizeTiles"))return layout(input)
        val nav=normalize(input.opt("navigation"));val category=input.getString("category");val items=categoryItems(category,nav,input.getJSONArray("products"));val id=input.opt("id")
        when(operation){
            "saveFolder"->{
                val name=trim(input.getString("name"));if(name.isEmpty()||name.length>80)return fail("Укажите название до 80 символов",true)
                if(items.any{it.opt("type")=="folder"&&!same(it.opt("id"),id)&&it.getString("name").lowercase(Locale.forLanguageTag("ru"))==name.lowercase(Locale.forLanguageTag("ru"))})return fail("Папка с таким названием уже есть")
                if(MPosJsonNumbers.truthy(id)){val folder=items.firstOrNull{it.opt("type")=="folder"&&same(it.opt("id"),id)}?:return fail("Папка не найдена");folder.put("name",name)}
                else items.add(JSONObject().put("type","folder").put("id",input.getString("newId")).put("name",name).put("parentId",""))
            }
            "removeFolder"->{
                val index=items.indexOfFirst{it.opt("type")=="folder"&&same(it.opt("id"),id)};if(index<0)return fail("Папка не найдена")
                items.removeAt(index);for(item in items)if(same(item.opt("parentId"),id))item.put("parentId","")
            }
            "moveProduct"->{
                val folder=MPosJsonNumbers.fallback(input.opt("folderId"))
                if(MPosJsonNumbers.truthy(folder)&&items.none{it.opt("type")=="folder"&&same(it.opt("id"),folder)})return fail("Папка не найдена")
                val index=items.indexOfFirst{it.opt("type")=="product"&&same(it.opt("id"),id)};if(index<0)return fail("Товар не найден в категории")
                val item=items.removeAt(index);item.put("parentId",folder);items.add(item)
            }
            "reorder"->{
                val parent=input.getString("parentId");val visible=items.filter{it.opt("parentId")==parent}.toMutableList();val from=visible.indexOfFirst{it.opt("type")==input.opt("type")&&same(it.opt("id"),id)}
                if(from>=0){val item=visible.removeAt(from);val n=number(input.opt("targetIndex"));val clamped=if(n.isNaN())0.0 else max(0.0,min(visible.size.toDouble(),n));visible.add(spliceIndex(clamped,visible.size),item);var j=0;for(i in items.indices)if(items[i].opt("parentId")==parent)items[i]=visible[j++]}
            }
            else->throw IllegalArgumentException("unknown navigation operation")
        }
        val categories=nav.getJSONArray("categories");val entry=(0 until categories.length()).map{categories.getJSONObject(it)}.firstOrNull{it.opt("category")==category}
        val array=JSONArray(items);if(entry!=null)entry.put("items",array) else categories.put(JSONObject().put("category",category).put("items",array))
        return reply().put("allowed",true).put("navigation",nav)
    }
    private fun layout(input:JSONObject):JSONObject{
        val tiles=JSONArray(input.getJSONArray("tiles").toString());val operation=input.getString("operation")
        if(operation=="addTile"){
            if(!MPosJsonNumbers.truthy(input.opt("type"))||!MPosJsonNumbers.truthy(input.opt("id")))return reply().put("allowed",true).put("changed",false).put("tiles",tiles)
            if(tiles.length()>=20)return fail("В рабочей зоне можно разместить максимум 20 плиток",true)
            tiles.put(JSONObject().put("type",input.get("type")).put("id",input.get("id")))
        }
        if(operation=="removeTile"){tiles.remove(spliceIndex(number(input.opt("index")),tiles.length()));return reply().put("allowed",true).put("changed",true).put("tiles",tiles)}
        if(operation=="moveTile"){
            val cols=input.getInt("cols");require(cols>0);val col=input.getInt("col");val row=input.getInt("row");val index=input.getInt("index");require(col>=0&&row>=0)
            fun free(c:Int,r:Int)=(0 until tiles.length()).none{it!=index&&tiles.optJSONObject(it)?.let{t->t.opt("col") is Number&&t.opt("row") is Number&&t.getDouble("col")==c.toDouble()&&t.getDouble("row")==r.toDouble()}==true}
            var cell:Pair<Int,Int>?=null
            loop@for(d in 0 until 30)for(rr in max(0,row-d)..row+d)for(cc in max(0,col-d)..min(cols-1,col+d))if(abs(cc-col)+abs(rr-row)==d&&free(cc,rr)){cell=cc to rr;break@loop}
            val target=cell?: (min(cols-1,col) to row+1)
            tiles.optJSONObject(index)?.put("col",target.first)?.put("row",target.second)
            return reply().put("allowed",true).put("changed",true).put("tiles",tiles)
        }
        var changed=operation=="addTile";val result=if(tiles.length()>20)JSONArray((0 until 20).map{tiles.get(it)}).also{changed=true} else tiles
        val occupied=mutableSetOf<Pair<Double,Double>>()
        for(i in 0 until result.length()){
            val tile=result.optJSONObject(i)?:continue;val col=tile.opt("col");val row=tile.opt("row")
            if(col is Number&&row is Number){val c=col.toDouble();val rr=row.toDouble();if(c.isFinite()&&rr.isFinite()&&floor(c)==c&&floor(rr)==rr&&c>=0&&c<5&&rr>=0&&occupied.add(c to rr))continue}
            var n=0;while((n%5).toDouble() to (n/5).toDouble() in occupied)n++
            tile.put("col",n%5).put("row",n/5);occupied.add((n%5).toDouble() to (n/5).toDouble());changed=true
        }
        return reply().put("allowed",true).put("changed",changed).put("tiles",result)
    }
}
