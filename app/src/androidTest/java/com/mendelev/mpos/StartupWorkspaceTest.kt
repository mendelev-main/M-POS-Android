package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class StartupWorkspaceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun freshLaunchStartsInWorkspaceAndRecreationKeepsIt() {
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-workspace").assertIsSelected()
        compose.onNodeWithTag("catalogSearch").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-workspace").assertIsSelected()
    }
}
