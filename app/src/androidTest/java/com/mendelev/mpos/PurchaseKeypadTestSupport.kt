package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Exercise the same modal keypad as the cashier rather than editing a display-only label. */
internal object PurchaseKeypadTestSupport {
    fun enter(compose: ComposeTestRule,tag: String,value: String) {
        compose.onNode(hasAnyAncestor(hasTestTag(tag)) and hasClickAction()).performScrollTo().performClick()
        value.replace('.',',').forEach {compose.onNodeWithTag("purchaseKey:$it").performClick()}
        compose.onNodeWithTag("purchaseKeypadValue").assertTextEquals(value.replace('.',','))
        compose.onNodeWithTag("purchaseKeypadDone").performClick()
    }
}
