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
class MPosCustomerEngineTest {
    private fun same(a:Any?,b:Any?):Boolean=when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet() && a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number && b is Number -> a.toDouble()==b.toDouble()
        else -> a==b
    }
    @Test fun sourceAssociationProfileAndRemovalFixturesMatchWithoutMutatingInput(){
        val file=listOf(File("../tests/fixtures/customer-context.json"),File("tests/fixtures/customer-context.json")).first{it.exists()};val cases=JSONArray(file.readText())
        for(i in 0 until cases.length()){val c=cases.getJSONObject(i);val input=c.getJSONObject("input");val before=input.toString();assertTrue("case $i",same(c.getJSONObject("expected"),MPosCustomerEngine.calculate(input).getJSONObject("session")));assertEquals(before,input.toString())}
    }
    @Test(expected=IllegalArgumentException::class) fun unsupportedCommandsNeverProduceSession(){MPosCustomerEngine.calculate(JSONObject("""{"version":1,"operation":"create-local-directory","session":{}}"""))}
}
