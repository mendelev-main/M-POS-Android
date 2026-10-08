package com.mendelev.mpos.workspace

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
class MPosWorkspaceSearchModelTest {
    @Test fun sharedFixturesMatchReviewedLiveFilterWithoutMutatingCatalogOrTiles() {
        val file=listOf(File("../tests/fixtures/workspace-search.json"),File("tests/fixtures/workspace-search.json")).first{it.exists()}
        val fixture=JSONObject(file.readText());val cases=fixture.getJSONArray("cases")
        for(index in 0 until cases.length()) {
            val case=cases.getJSONObject(index);val before=case.toString()
            val result=MPosWorkspaceSearchModel.visibility(case.getString("search"),fixture.getJSONArray("tiles"),
                MPosWorkspaceSearchModel.names(fixture.getJSONArray("products")))
            assertEquals("case $index",case.getJSONArray("visible").toString(),result.toString())
            assertEquals(before,case.toString())
        }
    }
    private fun command(operation:String)=JSONObject().put("version",1).put("operation",operation)
    private fun snapshot(owner:MPosWorkspaceNavigationOwner)=owner.handle(command("read")).getJSONObject("snapshot")
    @Test fun discardedSearchRestoresProjectedQueryAndCannotUndoLaterExplicitQueryOrRoute() {
        val owner=MPosWorkspaceNavigationOwner();owner.handle(command("initialize").put("tab","pos").put("search","чай"))
        val expected=snapshot(owner)
        owner.handle(command("selectFilteredSearch").put("search","ко").put("expected",expected))
        val latest=owner.handle(command("selectFilteredSearch").put("search","кофе").put("expected",expected))
        owner.handle(command("selectTab").put("tab","receipts"))
        owner.handle(command("discardSearch").put("searchToken",latest.getString("searchToken")))
        assertEquals("чай",owner.state.value.search);assertEquals("receipts",owner.state.value.tab)
        val next=owner.handle(command("selectFilteredSearch").put("search","кофе").put("expected",snapshot(owner)))
        owner.handle(command("selectSearch").put("search","кофе"))
        owner.handle(command("discardSearch").put("searchToken",next.getString("searchToken")))
        assertEquals("кофе",owner.state.value.search)
        owner.beginRuntime(1)
        owner.handle(command("initialize").put("tab","pos").put("search","imported"))
        owner.handle(command("discardSearch").put("searchToken",next.getString("searchToken")))
        assertEquals("imported",owner.state.value.search)
    }
    @Test fun changedCategoryRejectsSearchBeforeOwnerMutation() {
        val owner=MPosWorkspaceNavigationOwner();owner.handle(command("initialize").put("tab","pos").put("search","чай"))
        val expected=snapshot(owner).put("posPath","other")
        try {owner.handle(command("selectFilteredSearch").put("search","кофе").put("expected",expected));fail("stale search accepted")}
        catch(_:IllegalStateException){}
        assertEquals("чай",owner.state.value.search)
    }
}
