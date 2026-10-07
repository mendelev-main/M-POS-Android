package com.mendelev.mpos.print

import com.mendelev.mpos.data.MPosSupplyParity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MPosPrintPlanTest {
    @Test fun everyReviewedRoutingFixtureMatchesEveryCopyAndPayload(){
        val fixtures=JSONArray(File("../tests/fixtures/print-jobs.json").readText())
        for(i in 0 until fixtures.length()){val f=fixtures.getJSONObject(i);val input=JSONObject().put("version",1).put("trigger",f.getString("trigger")).put("order",f.getJSONObject("order")).put("printers",f.getJSONArray("normalizedPrinters")).put("now",123);assertTrue(f.getString("name"),MPosSupplyParity.same(f.getJSONArray("jobs"),MPosPrintPlan.calculate(input)))}
    }
    @Test fun corruptUnboundedCopiesFailRatherThanHangOrPartiallyReturn(){
        val input=JSONObject("""{"version":1,"trigger":"manual-receipt","order":{},"printers":[{"ip":"192.168.1.1","copies":129}]}""")
        try{MPosPrintPlan.calculate(input);fail("unbounded batch")}catch(_:IllegalArgumentException){}
        input.getJSONArray("printers").getJSONObject(0).put("copies","Infinity");try{MPosPrintPlan.calculate(input);fail("infinite copies")}catch(_:IllegalArgumentException){}
    }
}
