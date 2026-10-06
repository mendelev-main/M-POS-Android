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
class MPosRecipeEditEngineTest {
    private fun same(a:Any?,b:Any?):Boolean=when{
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet() && a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number && b is Number -> a.toDouble()==b.toDouble()
        else -> a==b
    }
    @Test fun editorRecipeAndModifierPoliciesMatchSourceIncludingFirstErrorOrder(){
        val file=listOf(File("../tests/fixtures/recipe-edit.json"),File("tests/fixtures/recipe-edit.json")).first{it.exists()};val cases=JSONArray(file.readText())
        for(i in 0 until cases.length()){
            val c=cases.getJSONObject(i);val input=c.getJSONObject("input");val before=input.toString();val r=MPosRecipeEditEngine.calculate(input);val expected=c.getJSONObject("expected")
            for(key in expected.keys())assertTrue("case $i $key expected=${expected.opt(key)} actual=${r.opt(key)}",same(expected.get(key),r.opt(key)))
            assertEquals(before,input.toString())
        }
    }
    @Test fun invalidYieldAndGeneratedIdentifiersFollowExistingEditorPolicy(){
        val input=JSONObject("""{"version":1,"operation":"modifiers","editingId":null,"type":"composite","recipeYield":0,"products":[{"id":"p","type":"simple"}],"groups":[{"name":"G","options":[{"productId":"p","qty":0}]}],"generatedIds":[{"id":"new-group","options":["new-option"]}]}""")
        assertFalse(MPosRecipeEditEngine.calculate(input).getBoolean("allowed"));input.put("recipeYield",1)
        val group=MPosRecipeEditEngine.calculate(input).getJSONArray("groups").getJSONObject(0)
        assertEquals("new-group",group.getString("id"));assertEquals("new-option",group.getJSONArray("options").getJSONObject(0).getString("id"));assertEquals(1.0,group.getJSONArray("options").getJSONObject(0).getDouble("qty"),0.0)
    }
}
