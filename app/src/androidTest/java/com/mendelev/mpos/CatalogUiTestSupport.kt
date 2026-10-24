package com.mendelev.mpos

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mendelev.mpos.feature.products.CatalogViewModel

/** A failed preceding UI test must not supply its unfinished editor to another test. */
object CatalogUiTestSupport {
    fun prepare(compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>,MainActivity>) {
        lateinit var model: CatalogViewModel
        compose.runOnIdle { model=ViewModelProvider(compose.activity)[CatalogViewModel::class.java] }
        compose.waitUntil(20_000) { !model.state.value.loading && !model.state.value.busy }
        if(model.state.value.draft!=null) {
            compose.runOnIdle { model.discard() }
            compose.waitUntil(20_000) { model.state.value.draft==null && !model.state.value.busy }
        }
    }
}
