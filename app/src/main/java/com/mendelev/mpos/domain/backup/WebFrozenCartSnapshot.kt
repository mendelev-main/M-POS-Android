package com.mendelev.mpos.domain.backup

/** Structural contract for the immutable source cart, NOT executable CurrentCart2/adoption authority. */
object WebFrozenCartSnapshot {
    fun validate(payload: String) {
        if(payload.toByteArray().size>WebImportedPaymentEvidenceCodec.MAX_BYTES)backupBad("frozenCartLimit")
        fun obj(v: BackupJson?,keys: String): Map<String,BackupJson> {
            val p=(v as? BackupJson.Obj)?.fields ?: backupBad("frozenCartShape")
            if(p.keys!=keys.split(' ').toSet())backupBad("frozenCartFields")
            return p
        }
        fun text(v: BackupJson?,nullable: Boolean=true) {if(!(nullable && v===BackupJson.Nil) && v !is BackupJson.Str)backupBad("frozenCartShape")}
        fun integer(v: BackupJson?) {if(v!==BackupJson.Nil && ((v as? BackupJson.Num)?.raw?.toLongOrNull()?.let {it>=0}!=true))backupBad("frozenCartMoney")}
        fun decimal(v: BackupJson?,signed: Boolean=false) {if(v!==BackupJson.Nil) {text(v,false);WebValues.decimal(v,signed=signed)}}
        fun bool(v: BackupJson?,nullable: Boolean=false) {if(!(nullable && v===BackupJson.Nil) && v !is BackupJson.Bool)backupBad("frozenCartShape")}
        fun rows(v: BackupJson?,max: Int): List<BackupJson> = (v as? BackupJson.Arr)?.values?.also {if(it.size>max)backupBad("frozenCartLimit")} ?: backupBad("frozenCartShape")
        val root=obj(BackupJsonParser(payload).parse(),"version origin createdAt updatedAt orderType label comment total compared delivery lines graph")
        if((root["version"] as? BackupJson.Num)?.raw!="1")backupBad("frozenCartVersion")
        text(root["origin"],false);listOf("total","compared","delivery","createdAt","updatedAt").forEach {integer(root[it])}
        listOf("orderType","label","comment").forEach {text(root[it])}
        for(v in rows(root["lines"],500)) {
            val l=obj(v,"sourceId product name category comment qty price basePrice manual unit basis physicalUnit physicalPerSold cost discount modifiers requests")
            listOf("sourceId","product","name","category","comment","unit","physicalUnit").forEach {text(l[it])}
            listOf("qty","price","basePrice","physicalPerSold").forEach {decimal(l[it])}
            bool(l["manual"],true);integer(l["cost"])
            val basis=l["basis"];if(basis!==BackupJson.Nil && (basis as? BackupJson.Str)?.value !in setOf("stock-unit","recipe-batch"))backupBad("frozenCartBasis")
            if(l["discount"]!==BackupJson.Nil) {
                val d=obj(l["discount"],"id name type value");text(d["id"],false);text(d["name"]);text(d["type"],false);decimal(d["value"])
            }
            for(mv in rows(l["modifiers"],2000)) {
                val m=obj(mv,"product group option groupName name qty delta")
                listOf("product","group","option","groupName","name").forEach {text(m[it])};decimal(m["qty"]);decimal(m["delta"],true)
            }
            if(l["requests"]!==BackupJson.Nil)for(rv in rows(l["requests"],2001)) {
                val r=obj(rv,"product qty unit");text(r["product"],false);decimal(r["qty"]);text(r["unit"],false)
                if((r["unit"] as BackupJson.Str).value !in setOf("PIECE","KG","G","L","ML"))backupBad("frozenCartUnit")
            }
        }
        if(root["graph"]!==BackupJson.Nil)for(gv in rows(root["graph"],5000)) {
            val type=((gv as? BackupJson.Obj)?.fields?.get("type") as? BackupJson.Str)?.value ?: backupBad("frozenCartShape")
            val g=obj(gv,when(type) {"simple"->"id unit type cost trackingOff negative";"composite"->"id unit type yield components";else->backupBad("frozenCartShape")})
            text(g["id"],false);text(g["unit"],false)
            if((g["unit"] as BackupJson.Str).value !in setOf("PIECE","KG","G","L","ML"))backupBad("frozenCartUnit")
            if(type=="simple") {decimal(g["cost"]);bool(g["trackingOff"]);bool(g["negative"])} else {
                decimal(g["yield"])
                for(cv in rows(g["components"],5000)) {val c=obj(cv,"id qty unit");text(c["id"],false);decimal(c["qty"]);text(c["unit"],false)
                    if((c["unit"] as BackupJson.Str).value !in setOf("PIECE","KG","G","L","ML"))backupBad("frozenCartUnit")}
            }
        }
    }
}
