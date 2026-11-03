package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.LayoutItem

data class WebCategoryTile(val id: String,val name: String,val color: String,val symbol: String)
data class WebHomeTile(val type: String,val id: String,val col: Int,val row: Int,val nativeSlot: Int?)
data class WebLayoutFacts(val categories: List<WebCategoryTile>,val home: List<WebHomeTile>,val items: List<LayoutItem>,
    val normalizedPositions: Int,val deferredFields: Set<String>,val blockers: Set<String>)

/** Exact Web five-column positions, one-level category folders; never project away stale references. */
object WebLayoutMapping {
    fun map(document: WebBackupDocument,catalog: WebCatalogFacts=WebCatalogMapping.map(document)): WebLayoutFacts {
        val layout=document.root.fields["layout"]?.let(WebValues::obj) ?: emptyMap()
        val products=catalog.products.associateBy {it.sourceId}
        fun categoryName(value: BackupJson?): String=WebValues.text(value).trim().also {if(it.isEmpty()) backupBad("category")}
        fun categoryId(name: String)=if(name=="Без категории") "" else WebIdentity.mapped("category",name)
        val names=WebValues.rows(layout["categoryOrder"]).map(::categoryName).toMutableList()
        if(names.distinct().size!=names.size) backupBad("category")
        catalog.categories.forEach {if(it.name !in names) names.add(it.name)}
        if(catalog.products.any {it.categoryId==null} && "Без категории" !in names) names.add("Без категории")
        val colors=layout["categoryColors"]?.let(WebValues::obj) ?: emptyMap()
        val symbols=layout["categorySymbols"]?.let(WebValues::obj) ?: emptyMap()
        val categories=names.map {name->try {WebCategoryTile(categoryId(name),name,
            CategoryAppearance.color(WebValues.text(colors[name],CategoryAppearance.DEFAULT_COLOR)),CategoryAppearance.symbol(WebValues.text(symbols[name])))}
            catch(_:CatalogFailure) {backupBad("categoryAppearance")}}
        val byName=categories.associateBy {it.name};val blockers=linkedSetOf<String>()
        val deferred=(layout.keys-setOf("categoryOrder","categoryColors","categorySymbols","tiles")).toMutableSet()
        if(colors.keys.any {it !in byName} || symbols.keys.any {it !in byName}) blockers.add("orphanCategoryAppearance")
        val tiles=layout["tiles"]?.let(WebValues::rows) ?: catalog.products.map {p->BackupJson.Obj(mapOf("type" to BackupJson.Str("product"),"id" to BackupJson.Str(p.sourceId)))}
        if(tiles.size>20) backupBad("homeCapacity")
        val occupied=mutableSetOf<Pair<Int,Int>>();val seen=mutableSetOf<Pair<String,String>>();var normalized=0
        fun coordinate(v: BackupJson?): Int? {
            if(v==null || v===BackupJson.Nil) return null
            if(v !is BackupJson.Num) backupBad("layout")
            return try {v.decimal().intValueExact()} catch(_:ArithmeticException) {backupBad("layout")}
        }
        val home=tiles.map {value->val p=WebValues.obj(value);val type=WebValues.text(p["type"])
            val sourceId=WebIdentity.source(p["id"])
            val id=when(type) {"product"->products[sourceId]?.id ?: backupBad("layoutReference");"category"->byName[sourceId]?.id ?: backupBad("layoutReference");else->backupBad("layout")}
            if(!seen.add(type to id)) backupBad("layoutDuplicate")
            var col=coordinate(p["col"]);var row=coordinate(p["row"])
            if(col==null || row==null || col !in 0..4 || row<0 || (col to row) in occupied) {
                val free=generateSequence(0) {it+1}.first {(it%5 to it/5) !in occupied};col=free%5;row=free/5;normalized++
            }
            occupied.add(col to row)
            val slot=row.toLong()*5+col
            if(slot>=100) blockers.add("homePositionBeyondNativeCapacity")
            if((p.keys-setOf("type","id","col","row")).isNotEmpty()) blockers.add("homeMetadataNotMapped")
            WebHomeTile(type,id,col,row,slot.takeIf {it<100}?.toInt())
        }
        val nav=document.root.fields["posNavigation"]?.let(WebValues::obj)
        if(nav!=null && WebValues.time(nav["version"])!=1L) backupBad("navigationVersion")
        val navigation=nav?.get("categories")?.let(WebValues::rows) ?: emptyList()
        val items=mutableListOf<LayoutItem>();val seenCategories=mutableSetOf<String>();val placed=mutableSetOf<String>()
        navigation.forEach {value->val entry=WebValues.obj(value);val name=categoryName(entry["category"])
            val category=byName[name] ?: backupBad("categoryReference")
            if(!seenCategories.add(name)) backupBad("layoutDuplicate")
            val saved=WebValues.rows(entry["items"]).map(WebValues::obj)
            val folders=linkedMapOf<String,String>()
            saved.filter {WebValues.text(it["type"])=="folder"}.forEach {p->
                val sourceId=WebIdentity.source(p["id"])
                if(folders.put(sourceId,WebIdentity.mapped("folder",CatalogRules.fingerprint(category.id,sourceId)))!=null) backupBad("layoutDuplicate")
            }
            saved.forEach {p->val type=WebValues.text(p["type"]);val sid=WebIdentity.source(p["id"])
                val parent=WebValues.text(p["parentId"])
                when(type) {
                    "folder"->{if(parent.isNotEmpty()) backupBad("nestedFolder")
                        val folderName=try {CatalogRules.name(WebValues.text(p["name"]),80)} catch(_:CatalogFailure) {backupBad("folder")}
                        items.add(LayoutItem(type,folders.getValue(sid),category.id,name=folderName))}
                    "product"->{val product=products[sid] ?: backupBad("layoutReference")
                        if((product.categoryId ?: "")!=category.id || !placed.add(product.id)) backupBad("layoutReference")
                        val folder=if(parent.isEmpty()) "" else folders[parent] ?: backupBad("folderReference")
                        items.add(LayoutItem(type,product.id,category.id,folder))}
                    else->backupBad("layout")
                }
                if((p.keys-setOf("type","id","name","parentId")).isNotEmpty()) blockers.add("navigationMetadataNotMapped")
            }
        }
        // Source posCategoryItems appends unplaced products by sortOrder, with source order as tie breaker.
        val sourceRows=WebValues.rows(document.root.fields["products"]).map(WebValues::obj)
        val order=sourceRows.associate {WebIdentity.source(it["id"]) to WebValues.decimal(it["sortOrder"],"0",signed=true)}
        categories.forEach {category->catalog.products.filter {(it.categoryId ?: "")==category.id && it.id !in placed}
            .sortedBy {order.getValue(it.sourceId)}.forEach {items.add(LayoutItem("product",it.id,category.id))}}
        if(nav!=null && (nav.keys-setOf("version","categories")).isNotEmpty()) blockers.add("navigationMetadataNotMapped")
        return WebLayoutFacts(categories,home,items,normalized,deferred,blockers)
    }
}
