package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.settings.*
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.telegram.*
import com.mendelev.mpos.data.local.PrinterConfiguration
import org.json.JSONObject
import org.json.JSONArray

internal object WebSettingsMapping {
    const val DEVICE="restored-device-settings"
    const val COMPANY="company-settings"
    fun envelope(j: JSONObject)=JSONObject().put("version",1).put("dataJson",j.toString()).toString()
    fun decode(raw: String)=JSONObject(raw).let {require(it.getInt("version")==1);JSONObject(it.getString("dataJson"))}
    fun value(v: BackupJson?): Any=when(v) {
        null,BackupJson.Nil->JSONObject.NULL
        is BackupJson.Str->v.value;is BackupJson.Num->v.raw.toBigDecimal();is BackupJson.Bool->v.value
        is BackupJson.Arr->JSONArray(v.values.map(::value));is BackupJson.Obj->JSONObject().apply {v.fields.forEach {(k,x)->put(k,value(x))}}
    }
    fun obj(v: BackupJson?)=value(v) as? JSONObject ?: JSONObject()
    fun catalog(source: WebBackupDocument): SettingsCatalog {
        var result=SettingsCatalog(1)
        fun add(row: SettingEntry) {result=SettingsRules.save(result,row)}
        WebValues.rows(source.root.fields["discounts"]).forEach {v->val p=WebValues.obj(v)
            add(SettingEntry(WebIdentity.mapped("discount",WebIdentity.source(p["id"])),SettingKind.DISCOUNT,WebValues.text(p["name"]),WebValues.text(p["type"]),WebValues.number(p["value"])))
        }
        WebValues.rows(source.root.fields["deliveryRates"]).forEach {v->val p=WebValues.obj(v)
            add(SettingEntry(WebIdentity.source(p["id"]),SettingKind.DELIVERY,WebValues.text(p["name"]),value=WebValues.number(p["amount"])))
        }
        WebValues.rows(source.root.fields["receiptLabelTemplates"]).forEachIndexed {i,v->
            val name=if(v is BackupJson.Str) v.value else WebValues.text(WebValues.obj(v)["name"])
            add(SettingEntry(WebIdentity.mapped("receiptLabel",i.toString()),SettingKind.RECEIPT_LABEL,name))
        }
        return result
    }
    fun device(source: WebBackupDocument): String {
        val r=source.root.fields
        return JSONObject().put("version",1).put("source",source.preview.sha256)
            .put("theme",WebValues.text(r["theme"],"light")).put("company",obj(r["company"]))
            .put("telegram",obj(r["telegram"])).put("network",obj(r["network"]))
            .put("printerSettings",obj(r["printerSettings"])).put("printer",obj(r["printer"]))
            .put("demandOverload",WebValues.bool(r["demandOverload"])).let(::envelope)
    }
    fun telegram(j: JSONObject): TelegramSettings {
        if(j.has("telegramNative")) return TelegramSettingsCodec.decode(j.getJSONObject("telegramNative").toString())
        val t=j.getJSONObject("telegram");val n=j.getJSONObject("network")
        return TelegramSettings(revision=1,enabled=t.optBoolean("enabled"),botToken=t.optString("botToken"),chatId=t.optString("chatId"),
            threadId=t.optString("threadId"),deviceChatId=t.optString("deviceChatId"),ownerChatId=t.optString("ownerChatId"),
            onlineOrders=t.optBoolean("notifyOnlineOrders"),shiftOpened=t.optBoolean("notifyShiftOpened",true),
            shiftClosed=t.optBoolean("notifyShiftClosed",true),monthlyWarehouse=t.optBoolean("notifyMonthlyWarehouse"),
            backendUrl=n.optString("backendUrl"),deviceKey=n.optString("deviceKey"),
            ownerShiftStats=t.optBoolean("ownerShiftStats"),ownerMonthlyStats=t.optBoolean("ownerMonthlyStats")).validated()
    }
    fun backend(j: JSONObject,dataset: String): BackendSettings {
        if(j.has("backendNative")) return BackendSettingsCodec.decode(j.getJSONObject("backendNative").toString()).copy(datasetId=dataset)
        val n=j.getJSONObject("network")
        return BackendSettings(revision=1,url=n.optString("backendUrl"),deviceKey=n.optString("deviceKey"),
            deviceName=n.optString("deviceName").ifBlank {"M POS Android"},datasetId=dataset,
            receiveOrders=n.optString("backendUrl").isNotBlank() && n.optString("deviceKey").isNotBlank()).validatedOrDraft()
    }
    fun printers(j: JSONObject): List<PrinterConfiguration> {
        val a=j.optJSONArray("printersNative") ?: j.getJSONObject("printerSettings").optJSONArray("printers") ?: JSONArray().apply {
            val legacy=j.getJSONObject("printer");if(legacy.optBoolean("enabled") || legacy.optString("ip").isNotBlank()) put(legacy)
        }
        return List(a.length()) {i->val p=a.getJSONObject(i)
            fun lines(key: String)=p.optJSONArray(key)?.let {x->List(x.length()) {x.getString(it)}.joinToString("\n")} ?: p.optString(key)
            PrinterConfiguration(id=if(j.has("printersNative")) p.getString("id") else WebIdentity.mapped("printer",p.optString("id").ifBlank {i.toString()}),revision=1,
                name=p.optString("name").ifBlank {"Чековый принтер"},ip=p.optString("ip"),paperWidth=p.opt("paperWidth")?.toString() ?: "80",
                printReceipts=p.optBoolean("printReceipts",true),printOrders=p.optBoolean("printOrders"),
                paymentComments=p.optBoolean("paymentComments",p.optBoolean("printPaymentComments",true)),receiptTitle=p.optString("receiptTitle",p.optString("paymentReceiptTitle")).ifBlank {"M POS"},
                registerLabel=p.optString("registerLabel").ifBlank {"POS 1"},phrases=lines(if(p.has("phrases")) "phrases" else "receiptRandomPhrases"),categories=lines(if(p.has("categories")) "categories" else "orderCategories"),
                drawer=p.optBoolean("drawer",p.optBoolean("cashDrawerEnabled",p.optBoolean("openDrawer"))),autoReceipt=p.optBoolean("autoReceipt",p.optBoolean("autoPrintReceipt",p.optBoolean("autoPrint",true))),
                autoKitchen=p.optBoolean("autoKitchen",p.optBoolean("autoPrintKitchen",true)),copies=p.opt("copies")?.toString() ?: "1",
                printMode=p.optString("printMode","graphic"),printWidth=p.optString("printWidth","auto"),dpi=p.opt("dpi")?.toString() ?: "203",
                initCommand=p.optString("initCommand","1B,40"),cutCommand=p.optString("cutCommand","1D,56,42,00"),
                drawerCommand=p.optString("drawerCommand","1B,70,00,19,FA"),enabled=p.optBoolean("enabled",true)).validated()
        }.also {rows->require(rows.map {it.id}.distinct().size==rows.size)}
    }
    fun validate(raw: String) {
        val j=decode(raw);require(j.getInt("version")==1 && j.getString("theme") in setOf("light","dark"))
        telegram(j);backend(j,"validation");printers(j)
        val company=j.getJSONObject("company")
        listOf("establishmentName","legalName","address","deliveryAddress").forEach {require(company.optString(it).length<=10000)}
    }
}
