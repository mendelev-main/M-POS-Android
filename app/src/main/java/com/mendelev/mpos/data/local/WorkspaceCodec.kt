package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import org.json.JSONArray
import org.json.JSONObject

/** Storage-only codecs. An unknown payload never becomes an empty customer order. */
object WorkspaceCodec {
    private fun root(raw: String, versions: Set<Int> = setOf(1))=JSONObject(raw).also {
        if(it.getInt("version") !in versions) throw CatalogFailure("workspace","Неизвестный формат рабочей зоны")
    }
    private fun <T> JSONArray.list(block: (JSONObject)->T): List<T> = (0 until length()).map { block(getJSONObject(it)) }
    private fun objects(values: List<JSONObject>)=JSONArray().also { a -> values.forEach(a::put) }
    fun home(value: HomeLayout): String=JSONObject().put("version",1).put("customized",value.customized).put("tiles",objects(value.tiles.map {
        JSONObject().put("type",it.type).put("id",it.id).put("slot",it.slot)
    })).toString()
    fun home(raw: String): HomeLayout=root(raw).let {j->HomeEngine.validate(HomeLayout(j.getBoolean("customized"),j.getJSONArray("tiles").list {
        HomeTile(it.getString("type"),it.getString("id"),it.getInt("slot"))
    }))}
    fun layout(items: List<LayoutItem>): String {
        val styled=items.any {it.appearance!=FolderAppearance()}
        return JSONObject().put("version",if(styled)2 else 1).put("items",objects(items.map {
            require(it.type=="folder" || it.appearance==FolderAppearance())
            JSONObject().put("type",it.type).put("id",it.id).put("categoryId",it.categoryId).put("folderId",it.folderId).put("name",it.name).apply {
                if(it.appearance!=FolderAppearance()) {
                    val a=it.appearance.validated()
                    put("appearance",JSONObject().put("color",a.color).put("symbol",a.symbol).put("columns",a.columns).put("tileHeight",a.tileHeight))
                }
            }
        })).toString()
    }
    fun layout(raw: String): List<LayoutItem> {
        val j=root(raw,setOf(1,2))
        return j.getJSONArray("items").list {
            val appearance=if(it.has("appearance")) {
                require(j.getInt("version")==2 && it.getString("type")=="folder")
                val a=it.getJSONObject("appearance")
                require(a.get("columns") is Int && a.get("tileHeight") is Int)
                FolderAppearance(a.getString("color"),a.getString("symbol"),a.getInt("columns"),a.getInt("tileHeight")).also {v->require(v.validated()==v)}
            } else FolderAppearance()
            LayoutItem(it.getString("type"),it.getString("id"),it.getString("categoryId"),it.getString("folderId"),it.getString("name"),appearance)
        }
    }
    fun cartVersion(raw: String)=root(raw,setOf(1,2,3)).getInt("version")
    fun cart(cart: CurrentCart): String=JSONObject().put("version",3).put("sessionId",cart.sessionId).put("createdAt",cart.createdAt)
        .also {j->cart.loyalty?.let {j.put("loyalty",LoyaltyCodec.encode(it))}}
        .put("details",JSONObject().put("label",cart.details.label).put("type",cart.details.type).put("comment",cart.details.comment)
            .put("address",cart.details.address).put("phone",cart.details.phone).also {d->
                if(cart.details.customerName.isNotEmpty()) d.put("customerName",cart.details.customerName)
                if(cart.details.deliveryEdited) d.put("deliveryEdited",true)
                cart.details.deliveryTariff?.let {t->d.put("deliveryTariff",JSONObject().put("id",t.id).put("name",t.name).put("fee",t.fee).put("revision",t.revision))}
            })
        .put("lines",objects(cart.lines.map { line ->
        JSONObject().put("id",line.id).put("productId",line.productId).put("name",line.name).put("basePrice",line.basePrice ?: JSONObject.NULL)
            .put("manual",line.manual ?: JSONObject.NULL).put("unitPrice",line.unitPrice).put("quantity",line.quantity).put("unit",line.unit)
            .put("sellingBasis",line.sellingBasis).also {j->
                line.quotedPrice?.let {j.put("quotedPrice",it)}
                if(line.comment.isNotEmpty()) j.put("comment",line.comment)
                if(line.discountEdited) j.put("discountEdited",true)
                if(line.discountId.isNotEmpty()) j.put("discountId",line.discountId)
                line.discount?.let {d->j.put("discount",JSONObject().put("type",d.type).put("value",d.value).put("name",d.name))}
            }.put("options",objects(line.options.map { JSONObject().put("id",it.id).put("name",it.name).put("quantity",it.quantity).put("priceDelta",it.priceDelta) }))
            .put("requests",objects(line.requests.map { JSONObject().put("id",it.productId).put("quantity",CatalogRules.canonical(it.quantity)).put("unit",it.unit.name) }))
            .put("references",objects(line.references.toSortedMap().map { (id,rev) -> JSONObject().put("id",id).put("revision",rev) }))
    })).put("graph",objects(cart.graph.map { p ->
        val j=JSONObject().put("id",p.id).put("unit",p.unit.name)
        when(p) {
            is RecipeProduct.Simple -> j.put("type","simple").put("cost",CatalogRules.canonical(p.cost)).put("noStock",p.noStockTracking).put("negative",p.allowNegativeStock)
            is RecipeProduct.Composite -> j.put("type","composite").put("yield",CatalogRules.canonical(p.yield)).put("components",objects(p.components.map {
                JSONObject().put("id",it.productId).put("quantity",CatalogRules.canonical(it.quantity)).put("unit",it.unit.name)
            }))
        }
        j
    })).toString()
    fun cart(raw: String): CurrentCart {
        val j=root(raw,setOf(1,2,3))
        val graph=j.getJSONArray("graph").list { p ->
            val id=p.getString("id");val unit=QuantityUnit.valueOf(p.getString("unit"))
            when(p.getString("type")) {
                "simple" -> RecipeProduct.Simple(id,unit,p.getString("cost").toBigDecimal(),p.getBoolean("noStock"),p.getBoolean("negative"))
                "composite" -> RecipeProduct.Composite(id,unit,p.getString("yield").toBigDecimal(),p.getJSONArray("components").list {
                    RecipeComponent(it.getString("id"),it.getString("quantity").toBigDecimal(),QuantityUnit.valueOf(it.getString("unit")))
                })
                else -> throw CatalogFailure("workspace","Некорректный тип товара в заказе")
            }
        }
        return CurrentCart(j.getJSONArray("lines").list { l ->
            CartLine(l.getString("id"),l.getString("productId"),l.getString("name"),if(j.getInt("version")==3 && l.isNull("basePrice")) null else l.getString("basePrice"),if(j.getInt("version")==3 && l.isNull("manual")) null else l.getBoolean("manual"),
                l.getString("unitPrice"),l.getString("quantity"),l.getString("unit"),l.getJSONArray("options").list {
                    ChosenOption(it.getString("id"),it.getString("name"),it.getString("quantity"),it.getString("priceDelta"))
                },l.getJSONArray("requests").list {
                    RecipeRequest(it.getString("id"),it.getString("quantity").toBigDecimal(),QuantityUnit.valueOf(it.getString("unit")))
                },l.getJSONArray("references").list { it.getString("id") to it.getLong("revision") }.toMap(),if(j.getInt("version")<3) "stock-unit" else l.getString("sellingBasis").also {
                    require(it in setOf("stock-unit","recipe-batch"))
                },if(l.has("comment")) l.getString("comment") else "",if(l.has("discountId")) l.getString("discountId") else "",
                if(l.has("discount")) l.getJSONObject("discount").let {d->com.mendelev.mpos.domain.sale.SaleDiscount(d.getString("type"),d.getString("value"),d.getString("name"))} else null,
                if(l.has("discountEdited")) l.getBoolean("discountEdited") else false,
                if(l.has("quotedPrice")) CatalogRules.decimal(l.getString("quotedPrice"),2,"Цена онлайн-заказа") else null).also {line->
                    require(line.comment.length<=2000 && line.comment.codePointCount(0,line.comment.length)<=1000 && line.comment.none {it.isISOControl() && it!='\n' && it!='\t'})
                    require(line.discountId.length<=100 && line.discountId.none {it.isISOControl()})
                    if(line.discount!=null) com.mendelev.mpos.domain.sale.SaleMoney.quote(listOf(com.mendelev.mpos.domain.sale.SaleLineInput(line.id,line.unitPrice,line.quantity,line.discount)))
                }
        },graph,sessionId=if(j.getInt("version")==1) "" else j.getString("sessionId").also {
            if(it.isBlank() || it.length>100) throw CatalogFailure("workspace","Не найден владелец заказа")
        },createdAt=if(j.getInt("version")==1) 0 else j.getLong("createdAt"),details=if(j.getInt("version")==1) OrderDetails() else j.getJSONObject("details").let {
            OrderRules.details(OrderDetails(it.getString("label"),it.getString("type"),it.getString("comment"),it.getString("address"),it.getString("phone"),
                if(it.has("customerName")) it.getString("customerName") else "",
                if(it.has("deliveryTariff")) it.getJSONObject("deliveryTariff").let {t->com.mendelev.mpos.domain.sale.DeliveryCharge(t.getString("id"),t.getString("name"),t.get("fee").toString().toLongOrNull() ?: throw CatalogFailure("workspace","Некорректная сумма тарифа"),t.get("revision").toString().toLongOrNull() ?: throw CatalogFailure("workspace","Некорректная версия тарифа"))} else null,
                if(it.has("deliveryEdited")) it.getBoolean("deliveryEdited") else false))
        },loyalty=j.optJSONObject("loyalty")?.let(LoyaltyCodec::decode))
    }
    fun parked(order: ParkedOrder): String=JSONObject().put("version",1).put("parkedAt",order.parkedAt).put("cart",JSONObject(cart(order.cart))).toString()
    fun parked(id: String, revision: Long, payload: String): ParkedOrder {
        val j=root(payload);val cart=cart(j.getJSONObject("cart").toString())
        if(id!="parked:${cart.sessionId}" || cart.lines.isEmpty()) throw CatalogFailure("workspace","Некорректный владелец отложенного заказа")
        return ParkedOrder(cart.sessionId,revision,cart,j.getLong("parkedAt"))
    }
    fun registry(raw: String) { root(raw) }
}
