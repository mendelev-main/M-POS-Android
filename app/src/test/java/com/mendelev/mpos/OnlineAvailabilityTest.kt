package com.mendelev.mpos
import com.mendelev.mpos.backend.OnlineAvailability
import com.mendelev.mpos.data.local.ProductDetailsCodec
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.feature.products.ProductDraft
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class OnlineAvailabilityTest {
    @Test fun unlimitedIsExplicitNullAndDoesNotUsePhysicalStock() {
        val item=OnlineAvailability.item("source-id",true) {error("Must not calculate physical stock")}
        assertEquals("source-id",item.getString("externalId"))
        assertTrue(item.has("quantity"));assertTrue(item.isNull("quantity"))
    }
    @Test fun disablingRestoresExactStockAndNoStockTrackingStillMeansUnlimited() {
        assertEquals(BigDecimal("0.0004"),OnlineAvailability.item("p",false) {BigDecimal("0.0004")}.get("quantity"))
        assertEquals(BigDecimal.ZERO,OnlineAvailability.item("p",false) {BigDecimal("-2")}.get("quantity"))
        assertTrue(OnlineAvailability.item("p",false) {null}.isNull("quantity"))
    }
    @Test fun flagsPersistInCardDraftAndBackupCodecWithStrictLegacyCompatibility() {
        listOf(null,false,true).forEach {flag->
            val d=ProductDetails(description="Сайт",onlineOrder=true,alwaysAvailableOnline=flag)
            val raw=ProductDetailsCodec.encode("p",d)
            assertEquals(if(flag==null)3 else 4,JSONObject(raw).getInt("version"))
            assertEquals(d,ProductDetailsCodec.decode(raw,"p"))
            assertEquals(d,ProductDraft(id="p",details=d).let {ProductDraft.parse(it.json()).details})
        }
        listOf(ProductDetails(),ProductDetails(sellingBasis="stock-unit"),ProductDetails(onlineMenu=true)).forEach {old->
            assertNull(ProductDetailsCodec.decode(ProductDetailsCodec.encode("p",old),"p").alwaysAvailableOnline)
        }
        val raw=JSONObject(ProductDetailsCodec.encode("p",ProductDetails(alwaysAvailableOnline=true)))
        listOf(JSONObject(raw.toString()).put("alwaysAvailableOnline","true"),JSONObject(raw.toString()).put("version",3)).forEach {bad->
            try {ProductDetailsCodec.decode(bad.toString(),"p");fail()} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun commandCannotReplayWithChangedWebPolicyAndLocalStockFlagsStayIndependent() {
        val p=ProductInput("p",null,"Кофе",null,"5","2","0",null,true,"command",details=ProductDetails())
        assertNotEquals(p.fingerprint(),p.copy(details=p.details!!.copy(alwaysAvailableOnline=true)).fingerprint())
        assertNotEquals(p.copy(details=p.details!!.copy(alwaysAvailableOnline=false)).fingerprint(),p.copy(details=p.details!!.copy(alwaysAvailableOnline=true)).fingerprint())
        val enabled=p.copy(details=p.details!!.copy(alwaysAvailableOnline=true)).validated()
        assertFalse(enabled.noStockTracking);assertFalse(enabled.allowNegativeStock);assertEquals("0",enabled.stock)
    }
}
