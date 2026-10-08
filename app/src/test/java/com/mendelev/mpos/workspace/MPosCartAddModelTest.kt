package com.mendelev.mpos.workspace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosCartAddModelTest {
    @Test fun selectionPreservesDomOrderingStrictDatasetIdsAndMissingModifierFailure() {
        val products=JSONArray("""[{"id":"m","name":"Milk"}]""")
        val p=JSONObject("""{"modifierGroups":[{"id":"g","name":"Options","min":1,"max":2,"options":[{"id":"text","productId":"m","qty":"0.5","priceDelta":"1","posName":"  Название  "},{"id":7,"productId":"m","priceDelta":3}]}]}""")
        val groups=MPosCartAddModel.groups(p,products);val selected=MPosCartAddModel.selected(groups,JSONArray("[[1,0]]"))
        assertEquals(1,selected.length());assertEquals("Название",selected.getJSONObject(0).getString("name"));assertEquals(0.5,selected.getJSONObject(0).getDouble("qty"),0.0)
        assertEquals(0,MPosCartAddModel.selected(groups,JSONArray("[[1]]")).length()) // numeric option ID is not a dataset string
        groups.getJSONObject(0).getJSONArray("options").getJSONObject(0).put("available",false)
        try{MPosCartAddModel.selected(groups,JSONArray("[[0]]"));fail("missing modifier accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun modifierOrderDoesNotPreventMergeButCommentDiscountAndManualDo() {
        val a=JSONArray("""[{"groupId":"b","productId":"m","qty":"1","priceDelta":2},{"groupId":"a","productId":"n","qty":1,"priceDelta":0}]""")
        val b=JSONArray().put(a.get(1)).put(a.get(0));assertEquals(MPosCartAddModel.signature(a),MPosCartAddModel.signature(b))
        val items=JSONArray().put(JSONObject().put("productId","p").put("selectedModifiers",a))
        assertEquals(0,MPosCartAddModel.existing(items,"p",b,false));assertNull(MPosCartAddModel.existing(items,"p",b,true))
        items.getJSONObject(0).put("comment","note");assertNull(MPosCartAddModel.existing(items,"p",b,false))
        items.getJSONObject(0).put("comment","").put("discountId","discount");assertNull(MPosCartAddModel.existing(items,"p",b,false))
    }
}
