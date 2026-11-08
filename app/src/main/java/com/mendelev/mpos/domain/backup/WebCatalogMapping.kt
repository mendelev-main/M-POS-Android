package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal

/** A projection for isolated conversion; no product-save/receiving command is executed. */
data class WebCatalogProduct(val id: String,val sourceId: String,val name: String,val categoryId: String?,
    val type: String,val price: String,val cost: String?,val stock: String?,val unit: String?,val displayUnit: String?,
    val noStockTracking: Boolean,val allowNegativeStock: Boolean,val minimumStock: String?,val purchaseUnit: String?,
    val packSize: String?,val recipeYield: String,val components: List<WebIngredient>,val modifierGroups: List<ModifierGroupInput>,
    val photoKey: String?,val externalPhoto: Boolean,val metadata: WebProductMetadata,val unmappedFields: Set<String>,val blockers: Set<String>)
data class WebIngredient(val productId: String,val quantity: String,val unit: String?)
data class WebCategory(val id: String,val name: String)
data class WebCatalogFacts(val products: List<WebCatalogProduct>,val categories: List<WebCategory>)

object WebCatalogMapping {
    private val units=setOf("piece","kg","g","l","ml")
    private val known=setOf("id","name","category","type","price","cost","stock","stockUnit","stockDisplayUnit","recipeUnit","recipeYield",
        "components","modifierGroups","noStockTracking","allowNegativeStock","minStock","purchaseUnit","purchaseContentUnit","purchasePackSize","localImageId","image","imageUrl")
    private fun unit(raw: String): String?=raw.takeUnless {it.isEmpty()}?.also {if(it !in units) backupBad("unit")}
    private fun compatible(a: String?,b: String?) {
        if(a!=null && b!=null) try {quantityUnit(a).convert(BigDecimal.ONE,quantityUnit(b))} catch(_:CatalogFailure) {backupBad("unit")}
    }
    fun map(document: WebBackupDocument): WebCatalogFacts {
        val rows=WebValues.rows(document.root.fields["products"]).map(WebValues::obj)
        val sourceIds=rows.map {WebIdentity.source(it["id"])}
        if(sourceIds.distinct().size!=sourceIds.size) backupBad("identity")
        val source=sourceIds.zip(rows).toMap()
        val mappedIds=sourceIds.map {WebIdentity.mapped("product",it)}.toSet()
        val categories=linkedMapOf<String,WebCategory>()
        fun type(p: Map<String,BackupJson>): String=WebValues.text(p["type"]).also {if(it !in setOf("simple","composite")) backupBad("product")}
        fun base(p: Map<String,BackupJson>): String?=unit(if(type(p)=="simple") WebValues.text(p["stockUnit"]).ifEmpty {"piece"} else WebValues.text(p["recipeUnit"]).ifEmpty {"piece"})
        fun yield(p: Map<String,BackupJson>): String=if(type(p)=="composite") WebValues.number(p["recipeYield"],"1").also {if(it.toBigDecimal().signum()<=0) backupBad("recipe")} else "1"
        fun physical(qty: String,child: Map<String,BackupJson>): String {
            val n=qty.toBigDecimal()*yield(child).toBigDecimal()
            return WebValues.number(BackupJson.Str(n.toPlainString())).also {if(it.toBigDecimal().signum()<=0) backupBad("recipe")}
        }
        val products=rows.mapIndexed {index,p->
            val sid=sourceIds[index];val id=WebIdentity.mapped("product",sid);val blockers=linkedSetOf<String>()
            val kind=type(p);val u=base(p)
            if(u==null) blockers.add("legacyUnitUnknown")
            val display=if(kind=="simple") unit(WebValues.text(p["stockDisplayUnit"]).ifEmpty {u ?: ""}) else u
            compatible(u,display)
            val cat=WebValues.text(p["category"]).trim().takeUnless {it.isEmpty() || it=="Без категории"}
            val categoryId=cat?.let {val key=WebIdentity.mapped("category",it);categories[key]=WebCategory(key,it);key}
            val name=try {CatalogRules.name(WebValues.text(p["name"]))} catch(_:CatalogFailure) {backupBad("name")}
            val components=WebValues.rows(p["components"]).map {value->val c=WebValues.obj(value)
                if((c.keys-setOf("productId","qty","displayUnit")).isNotEmpty()) blockers.add("ingredientMetadataNotMapped")
                val childSource=WebIdentity.source(c["productId"]);val child=source[childSource] ?: backupBad("reference")
                val cu=base(child);compatible(cu,unit(WebValues.text(c["displayUnit"]).ifEmpty {cu ?: ""}))
                if(cu==null) blockers.add("ingredientUnitUnknown")
                WebIngredient(WebIdentity.mapped("product",childSource),physical(WebValues.number(c["qty"]),child),cu)
            }
            if(kind=="simple" && components.isNotEmpty() || kind=="composite" && components.isEmpty()) backupBad("recipe")
            val groups=WebValues.rows(p["modifierGroups"]).map {value->val g=WebValues.obj(value)
                if((g.keys-setOf("id","name","min","max","options")).isNotEmpty()) blockers.add("modifierMetadataNotMapped")
                val gid=WebIdentity.source(g["id"])
                ModifierGroupInput(WebIdentity.mapped("modifierGroup",id+":"+gid),WebValues.text(g["name"]),
                    WebValues.number(g["min"],"0",scale=0),WebValues.number(g["max"],"1",scale=0),WebValues.rows(g["options"]).map {v->val o=WebValues.obj(v)
                        if((o.keys-setOf("id","productId","posName","qty","priceDelta")).isNotEmpty()) blockers.add("modifierMetadataNotMapped")
                        val childSource=WebIdentity.source(o["productId"]);val child=source[childSource] ?: backupBad("reference")
                        if(base(child)==null) blockers.add("modifierUnitUnknown")
                        ModifierOptionInput(WebIdentity.mapped("modifierOption",CatalogRules.fingerprint(id,gid,WebIdentity.source(o["id"]))),
                            WebIdentity.mapped("product",childSource),WebValues.text(o["posName"]),physical(WebValues.number(o["qty"],"1"),child),WebValues.number(o["priceDelta"],"0",signed=true,scale=2))
                    })
            }
            val modifiers=try {ModifierRules.validate(id,groups,mappedIds)} catch(_:CatalogFailure) {backupBad("modifier")}
            val local=WebValues.text(p["localImageId"]).takeIf {it.isNotEmpty()}
            val image=WebValues.text(p["image"])
            val photo=local ?: "inline:$index".takeIf {image.startsWith("data:")}
            if(photo!=null && photo !in document.images) backupBad("missingPhoto")
            if(photo!=null) blockers.add("photoTranscodePending")
            val external=photo==null && (image.isNotEmpty() || WebValues.text(p["imageUrl"]).isNotEmpty())
            if(external) blockers.add("externalPhotoNeedsExplicitSkip")
            val purchase=WebValues.text(p["purchaseUnit"]).takeIf {it.isNotEmpty()}
            if(purchase!=null && purchase !in setOf("pack","box","bottle")) {unit(purchase);compatible(u,purchase)}
            val content=unit(WebValues.text(p["purchaseContentUnit"]).ifEmpty {u ?: ""});compatible(u,content)
            val pack=p["purchasePackSize"]?.takeUnless {it===BackupJson.Nil}?.let {WebValues.number(it).also {q->if(q.toBigDecimal().signum()<=0) backupBad("pack")}}
            val convertedPack=pack?.let {q->if(u==null || content==null) {blockers.add("packUnitUnknown");q} else WebValues.number(BackupJson.Str(quantityUnit(content).convert(q.toBigDecimal(),quantityUnit(u)).toPlainString()))}
            val stock=if(kind=="simple") p["stock"]?.takeUnless {it===BackupJson.Nil}?.let {WebValues.number(it,signed=true)} else null
            val cost=if(kind=="simple") p["cost"]?.takeUnless {it===BackupJson.Nil}?.let {CatalogRules.canonical(WebValues.decimal(it).setScale(2,java.math.RoundingMode.HALF_UP))} else null
            if(kind=="simple" && stock==null) blockers.add("stockUnknown")
            if(kind=="simple" && cost==null) blockers.add("costUnknown")
            val negative=WebValues.bool(p["allowNegativeStock"])
            if(stock!=null && stock.toBigDecimal().signum()<0 && !negative) blockers.add("negativeStockWithoutPermission")
            val minimum=p["minStock"]?.takeUnless {it===BackupJson.Nil}?.let {WebValues.number(it)}
            val tracking=WebValues.bool(p["noStockTracking"])
            if(kind=="composite" && (tracking || negative || minimum!=null || pack!=null || purchase!=null)) blockers.add("compositeStockConfiguration")
            if(kind=="composite" && (p["stock"]!=null || p["cost"]!=null)) blockers.add("compositeLegacyStockFields")
            val metadata=WebProductMetadataCodec.project(p)
            if(metadata.retainedOnlyFields.isNotEmpty()) blockers.add("metadataRetentionRequired")
            val unmapped=p.keys-known-WebProductMetadataCodec.fields
            if(unmapped.isNotEmpty()) blockers.add("productMetadataNotMapped")
            WebCatalogProduct(id,sid,name,categoryId,kind,WebValues.number(p["price"],scale=2),
                cost,stock,u,display,tracking,negative,minimum,purchase,
                convertedPack,yield(p),components,modifiers,photo,external,metadata,unmapped,blockers)
        }
        val byId=products.associateBy {it.id};val visiting=mutableSetOf<String>();val visited=mutableSetOf<String>()
        fun visit(id: String,depth: Int) {
            if(depth>64 || !visiting.add(id)) backupBad("recipeCycle")
            if(id !in visited) {byId.getValue(id).components.forEach {visit(it.productId,depth+1)};visited.add(id)}
            visiting.remove(id)
        }
        products.forEach {visit(it.id,0)}
        return WebCatalogFacts(products,categories.values.toList())
    }
}
