package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.backend.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.settings.*
import com.mendelev.mpos.print.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class WebFullSettingsTest {
    private fun root()=JSONObject("""{
      "version":1,"products":[],"employees":[],"shifts":[],"orders":[],
      "discounts":[{"id":"d","name":"Скидка","type":"percent","value":10}],
      "deliveryRates":[{"id":"t","name":"Город","amount":5.5}],
      "receiptLabelTemplates":[{"id":"l","name":"Сотруднику"}],
      "theme":"dark","company":{"establishmentName":"Тест","legalName":"Тест ООО","address":"Адрес","deliveryAddress":"Доставка"},
      "network":{"backendUrl":"https://fixture.example.org","deviceName":"Касса","deviceKey":"fixture-device-key"},
      "telegram":{"enabled":false,"notifyShiftOpened":false,"notifyShiftClosed":true},
      "printerSettings":{"printers":[{"id":"one","name":"Кухня","ip":"192.0.2.10","paperWidth":58,"printReceipts":false,"printOrders":true,"autoPrintKitchen":false,"copies":2,"orderCategories":["Еда"],"printMode":"text","enabled":false}],"posNotifications":{}}
    }""")
    private fun source(j: JSONObject)=WebBackupReader.read(j.toString().byteInputStream())
    @Test fun allCatalogsAndDeviceValuesBecomeUsableNativeSettings() {
        val source=source(root());val rows=WebSettingsMapping.catalog(source)
        assertEquals(3,rows.entries.size)
        assertEquals("5.5",rows.rows(SettingKind.DELIVERY).single().value)
        assertEquals("t",rows.rows(SettingKind.DELIVERY).single().id)
        assertEquals("Сотруднику",rows.rows(SettingKind.RECEIPT_LABEL).single().name)
        assertEquals(rows,SettingsCodec.decode(SettingsCodec.encode(rows),rows.revision))
        val raw=WebSettingsMapping.device(source);WebSettingsMapping.validate(raw)
        val j=WebSettingsMapping.decode(raw);assertEquals("dark",j.getString("theme"))
        assertEquals("Тест ООО",j.getJSONObject("company").getString("legalName"))
        val backend=WebSettingsMapping.backend(j,"fresh-dataset")
        assertEquals("fresh-dataset",backend.datasetId);assertEquals("fixture-device-key",backend.deviceKey);assertTrue(backend.receiveOrders)
        assertFalse(WebSettingsMapping.telegram(j).shiftOpened)
        val p=WebSettingsMapping.printers(j).single()
        assertEquals("58",p.paperWidth);assertEquals("Еда",p.categories);assertEquals("2",p.copies);assertFalse(p.enabled);assertFalse(p.autoKitchen)
        assertTrue(PrinterRouting.targets(PrintKind.KITCHEN,listOf(p)).isEmpty())
        assertFalse(source.preview.deferredSections.any {it in setOf("discounts","deliveryRates","receiptLabelTemplates","network","telegram","company","theme","printerSettings")})
    }
    @Test fun partialConnectionAndEmptyLegacyCopyRemainEditableWithoutStartingNetworking() {
        val source=source(root().put("network",JSONObject().put("backendUrl","https://fixture.example.org")).put("printerSettings",JSONObject()))
        val j=WebSettingsMapping.decode(WebSettingsMapping.device(source))
        val b=WebSettingsMapping.backend(j,"dataset")
        assertEquals("",b.deviceKey);assertFalse(b.receiveOrders)
        assertEquals(b,BackendSettingsCodec.decode(BackendSettingsCodec.encode(b)))
        assertTrue(WebSettingsMapping.printers(j).isEmpty())
    }
    @Test fun malformedSettingsFailPreparationInsteadOfSilentlyDisappearing() {
        val j=root();j.getJSONArray("discounts").getJSONObject(0).put("value",-10)
        assertThrows(Exception::class.java) {WebSettingsMapping.catalog(source(j))}
        val device=WebSettingsMapping.decode(WebSettingsMapping.device(source(root())))
        device.getJSONObject("network").put("backendUrl","http://fixture.example.org")
        assertThrows(Exception::class.java) {WebSettingsMapping.validate(WebSettingsMapping.envelope(device))}
    }
    @Test fun nativeOverridesSurviveRebindRatherThanReapplyingOldWebValues() {
        val j=WebSettingsMapping.decode(WebSettingsMapping.device(source(root())))
        val edited=BackendSettings(url="https://another.example.org",deviceKey="edited-fixture-key",deviceName="Новая касса",datasetId="old")
        j.put("backendNative",JSONObject(BackendSettingsCodec.encode(edited)))
        val rebound=WebSettingsMapping.backend(j,"new")
        assertEquals("https://another.example.org",rebound.url);assertEquals("new",rebound.datasetId);assertEquals("edited-fixture-key",rebound.deviceKey)
    }
}
