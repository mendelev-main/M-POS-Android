package com.mendelev.mpos.workspace

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosWorkspaceNavigationOwnerTest {
    private fun command(operation:String,tab:String="pos")=JSONObject().put("version",1).put("operation",operation).put("tab",tab)
    @Test fun initialSeedIsAcceptedOnceAndCannotReplaceNativeSelection() {
        val owner=MPosWorkspaceNavigationOwner();owner.handle(command("initialize","receipts"))
        owner.handle(command("selectTab","analytics"));owner.handle(command("initialize","settings"))
        assertEquals("analytics",owner.state.value.tab);assertEquals(2L,owner.state.value.revision)
        assertEquals("analytics",owner.handle(command("read")).getJSONObject("snapshot").getString("tab"))
    }
    @Test fun repeatedSelectionKeepsRevisionAndUnknownStringRetainsReviewedNoAllowlistPolicy() {
        val owner=MPosWorkspaceNavigationOwner();owner.handle(command("initialize"));owner.handle(command("selectTab"))
        assertEquals(1L,owner.state.value.revision)
        owner.handle(command("selectTab","future-section"));assertEquals("future-section",owner.state.value.tab)
    }
    @Test fun snapshotsAreDetachedAndNewRuntimeDoesNotPersistTabsOrInventBackHistory() {
        val owner=MPosWorkspaceNavigationOwner();val snapshot=owner.handle(command("initialize","settings"))
        snapshot.getJSONObject("snapshot").put("tab","changed");assertEquals("settings",owner.state.value.tab)
        assertFalse(MPosWorkspaceNavigationOwner().state.value.initialized)
    }
    @Test(expected=IllegalStateException::class) fun selectionBeforeInitializationIsRejected(){MPosWorkspaceNavigationOwner().handle(command("selectTab","settings"))}
    @Test fun searchPreservesExactQueryAndTabWithoutPersistenceOrTabReset() {
        val owner=MPosWorkspaceNavigationOwner()
        owner.handle(command("initialize").put("search", "  Кофе Ё  "))
        owner.handle(command("initialize").put("search", "discarded"))
        assertEquals("  Кофе Ё  ",owner.state.value.search)
        val input=command("selectSearch").put("search", "чай")
        owner.handle(input);owner.handle(input)
        assertEquals(2L,owner.state.value.revision)
        owner.handle(command("selectTab","receipts"))
        assertEquals("чай",owner.handle(command("read")).getJSONObject("snapshot").getString("search"))
        assertEquals("receipts",owner.state.value.tab)
        assertEquals("",MPosWorkspaceNavigationOwner().state.value.search)
    }
    @Test(expected=IllegalStateException::class) fun searchBeforeInitializationIsRejected(){
        MPosWorkspaceNavigationOwner().handle(command("selectSearch").put("search", "чай"))
    }
    @Test fun runtimeRestartDropsNavigationAndCannotReusePreviousSelectionOrProposal() {
        val owner=MPosWorkspaceNavigationOwner();owner.handle(command("initialize","receipts").put("posPath","old").put("search","old"))
        owner.beginRuntime(1);assertFalse(owner.state.value.initialized);assertNull(owner.state.value.posPath);assertEquals("",owner.state.value.search)
        owner.handle(command("initialize","pos").put("search","imported"));assertEquals("pos",owner.state.value.tab);assertEquals("imported",owner.state.value.search)
        assertThrows(IllegalStateException::class.java){owner.beginRuntime(1)}
        assertEquals("imported",owner.state.value.search)
        owner.beginRuntime(2);assertFalse(owner.state.value.initialized)
    }

}
