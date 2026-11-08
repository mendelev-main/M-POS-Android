package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.ProductDetails
import com.mendelev.mpos.domain.backup.*
import org.json.JSONObject

object ProductDetailsCodec {
    const val PREFIX="catalog-product:"
    fun owns(id: String)=id.startsWith(PREFIX)
    fun fields(value: ProductDetails)=JSONObject().put("sku",value.sku).put("note",value.note).put("symbol",value.symbol)
        .put("sellingBasis",value.sellingBasis).put("recipeYieldManual",value.recipeYieldManual).put("purchaseContentUnit",value.purchaseContentUnit)
    fun value(j: JSONObject): ProductDetails {
        if(!j.isNull("recipeYieldManual")) require(j.get("recipeYieldManual") is Boolean)
        return ProductDetails(j.getString("sku"),j.getString("note"),j.getString("symbol"),
        if(j.isNull("sellingBasis")) null else j.getString("sellingBasis"),
        if(j.isNull("recipeYieldManual")) null else j.getBoolean("recipeYieldManual"),
        if(j.isNull("purchaseContentUnit")) null else j.getString("purchaseContentUnit"))
    }
    fun encode(id: String,value: ProductDetails)=fields(value).put("version",if(value.sellingBasis!=null || value.recipeYieldManual!=null || value.purchaseContentUnit!=null) 2 else 1).put("productId",id).toString()
    fun decode(raw: String,id: String): ProductDetails {
        val j=JSONObject(raw);val version=j.getInt("version");require(version in 1..2 && j.getString("productId")==id)
        val row=value(j);require(row.validated()==row)
        require(version==2 || row.sellingBasis==null && row.recipeYieldManual==null && row.purchaseContentUnit==null)
        return row
    }
    fun imported(raw: String): ProductDetails {
        val values=JSONObject(raw).getJSONObject("values")
        fun text(key: String)=if(values.has(key) && !values.isNull(key)) values.getString(key) else ""
        return ProductDetails(text("sku"),text("internalNote"),text("tileSymbol"),recipeYieldManual=if(values.isNull("recipeYieldManual")) null else values.getBoolean("recipeYieldManual"))
    }
}
internal suspend fun CatalogDatabase.productDetails(id: String): ProductDetails {
    workspace().document(ProductDetailsCodec.PREFIX+id)?.let {return ProductDetailsCodec.decode(it.payload,id)}
    workspace().document(WebProductMetadataDocuments.PREFIX+id)?.let {return ProductDetailsCodec.imported(WebProductMetadataDocuments.read(it.payload,id).payload)}
    if(workspace().document(WebSourceDocuments.KEY)!=null) {
        WebSourceDocuments.decode(workspace().documents().associate {it.id to it.payload})?.let {source->
            WebCatalogMapping.map(source).products.find {it.id==id}?.let {return ProductDetailsCodec.imported(it.metadata.payload)}
        }
    }
    return ProductDetails()
}
