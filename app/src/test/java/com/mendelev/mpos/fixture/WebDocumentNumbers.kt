package com.mendelev.mpos.fixture
import org.json.JSONObject
import org.json.JSONArray
internal object WebDocumentNumbers {
 fun number(obj:JSONObject,key:String,fallback:Double=0.0):Double = when(val value=obj.opt(key)) {
 is Number->value.toDouble();is String->value.replace(',','.').toDoubleOrNull() ?: fallback;else->fallback
 }
 fun objects(values:JSONArray?):List<JSONObject> = if(values==null)emptyList() else (0 until values.length()).mapNotNull{values.optJSONObject(it)}
}
