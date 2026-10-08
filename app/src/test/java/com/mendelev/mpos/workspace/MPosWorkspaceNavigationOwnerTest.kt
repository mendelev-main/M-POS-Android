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
}
