package com.mendelev.mpos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun foundationLaunchesAndSurvivesRecreation() {
        val title = compose.activity.getString(R.string.foundation_title)
        val next = compose.activity.getString(R.string.foundation_next)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(next).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(next).assertIsDisplayed()
    }
}
