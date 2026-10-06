package com.mendelev.mpos.data

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosCatalogEditEngineTest {
    private fun same(a:Any?,b:Any?):Boolean=when{
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet() && a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number && b is Number -> a.toDouble()==b.toDouble()
        else -> a==b
    }
    @Test fun categoryTransitionsMatchReviewedSourceFixturesWithoutMutation(){
        val file=listOf(File("../tests/fixtures/catalog-edit.json"),File("tests/fixtures/catalog-edit.json")).first{it.exists()};val cases=JSONArray(file.readText())
        for(i in 0 until cases.length()){
            val c=cases.getJSONObject(i);val input=c.getJSONObject("input");val before=input.toString();val r=MPosCatalogEditEngine.calculate(input);val expected=c.getJSONObject("expected")
            for(key in expected.keys())assertTrue("case $i $key",same(expected.get(key),r.opt(key)))
            assertEquals(before,input.toString())
        }
    }
    @Test fun typeChangeProtectsUnreturnedReceiptsUnitsAndTransitiveDependencies(){
        val input=JSONObject("""{"version":1,"operation":"product","editingId":"a","name":"Milk","type":"composite","components":[{"productId":"x"}],"products":[{"id":"a","type":"simple"},{"id":"b","type":"composite","components":[{"productId":"a"}]}],"orders":[]}""")
        assertFalse(MPosCatalogEditEngine.calculate(input).getBoolean("allowed"))
        input.getJSONArray("products").remove(1);assertTrue(MPosCatalogEditEngine.calculate(input).getBoolean("allowed"))
        input.getJSONArray("orders").put(JSONObject("""{"stockConsumption":{"items":[{"productId":"a"}]}}"""));val blocked=MPosCatalogEditEngine.calculate(input);assertTrue(blocked.getString("message").contains("возврата"))
        input.getJSONArray("orders").getJSONObject(0).put("returnedAt",1);assertTrue(MPosCatalogEditEngine.calculate(input).getBoolean("allowed"))
        input.getJSONArray("products").getJSONObject(0).put("stockUnit","ml");assertFalse(MPosCatalogEditEngine.calculate(input).getBoolean("allowed"))
    }
}
