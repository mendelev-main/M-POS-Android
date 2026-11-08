package com.mendelev.mpos.domain.settings

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.OrderRules
import org.junit.Assert.*
import org.junit.Test

class SettingsRulesTest {
    private fun refuses(block: ()->Unit) {try {block();fail("Expected rejection")} catch(_: CatalogFailure) {}}
    @Test fun tariffAllowsFreeDeliveryButRejectsFractionalKopecksAndOverflow() {
        assertEquals("0",SettingsRules.validate(SettingEntry("d",SettingKind.DELIVERY," Самовывоз ",value="0,00")).value)
        assertEquals("5",SettingsRules.validate(SettingEntry("d",SettingKind.DELIVERY,"По городу",value="5.000")).value)
        listOf("-1","1.001","10000000","NaN").forEach {amount->refuses {SettingsRules.validate(SettingEntry("d",SettingKind.DELIVERY,"По городу",value=amount))}}
    }
    @Test fun discountsUseExistingLineRoundingAndFixedAmountPerUnit() {
        val row=SettingsRules.validate(SettingEntry("s",SettingKind.DISCOUNT,"Сотруднику","percent","10"))
        val q=SaleMoney.quote(listOf(SaleLineInput("line","0.05","3",SaleDiscount(row.type,row.value,row.name))))
        assertEquals(14L,q.total)
        val fixed=SettingsRules.validate(row.copy(type="fixed",value="1"))
        assertEquals(1800L,SaleMoney.quote(listOf(SaleLineInput("line","10","2",SaleDiscount(fixed.type,fixed.value,fixed.name)))).total)
        listOf("0","-1","100.01").forEach {v->refuses {SettingsRules.validate(row.copy(value=v))}}
    }
    @Test fun templatesRejectNormalizedDuplicatesAndSuggestUnusedLabel() {
        val first=SettingEntry("one",SettingKind.RECEIPT_LABEL,"Стол 1")
        val c=SettingsRules.save(SettingsCatalog(),first)
        refuses {SettingsRules.save(c,first.copy(id="two",name="  СТОЛ\u00a0  1 "))}
        assertEquals("Стол 1 (3)",OrderRules.uniqueLabel(first.name,listOf("стол 1","Стол 1 (2)")))
        assertEquals("Новый стол",SettingsRules.save(c,first.copy(name="Новый стол")).entries.single().name)
        refuses {SettingsRules.validate(first.copy(name="я".repeat(81)))}
    }
}
