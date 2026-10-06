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
class MPosNavigationEngineTest {
    private fun same(a:Any?,b:Any?):Boolean=when{
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet() && a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number && b is Number -> a.toDouble()==b.toDouble()
        else -> a==b
    }
    @Test fun folderMoveReorderAndRootCellsMatchReviewedSource(){
        val file=listOf(File("../tests/fixtures/navigation.json"),File("tests/fixtures/navigation.json")).first{it.exists()};val cases=JSONArray(file.readText())
        for(i in 0 until cases.length()){
            val c=cases.getJSONObject(i);val input=c.getJSONObject("input");val before=input.toString();val result=MPosNavigationEngine.calculate(input);val expected=c.getJSONObject("expected")
            for(key in expected.keys())assertTrue("case $i $key expected=${expected.opt(key)} actual=${result.opt(key)}",same(expected.opt(key),result.opt(key)))
            assertEquals(before,input.toString())
        }
    }
    @Test fun normalizeIsStrictVersionAndFoldersHaveOneLevel(){
        assertEquals(0,MPosNavigationEngine.normalize(JSONObject("""{"version":"1","categories":[]}""")).getJSONArray("categories").length())
        val raw=JSONObject("""{"version":1,"categories":[{"category":"C","items":[{"type":"folder","id":"f","name":" F ","parentId":"other"},{"type":"product","id":"p","parentId":"missing"},{"type":"product","id":"p"}]}]}""")
        val items=MPosNavigationEngine.normalize(raw).getJSONArray("categories").getJSONObject(0).getJSONArray("items")
        assertEquals(2,items.length());assertEquals("F",items.getJSONObject(0).getString("name"));assertEquals("",items.getJSONObject(0).getString("parentId"));assertEquals("",items.getJSONObject(1).getString("parentId"))
    }
    @Test fun folderNameLimitKeepsReviewedUtf16SliceRatherThanGraphemeCount(){
        val name="a".repeat(79)+"\uD83D\uDE00"
        val raw=JSONObject().put("version",1).put("categories",JSONArray().put(JSONObject().put("category","C").put("items",JSONArray().put(JSONObject().put("type","folder").put("id","f").put("name",name)))))
        val result=MPosNavigationEngine.normalize(raw).getJSONArray("categories").getJSONObject(0).getJSONArray("items").getJSONObject(0).getString("name")
        assertEquals(80,result.length);assertEquals('\uD83D',result.last())
    }

}
