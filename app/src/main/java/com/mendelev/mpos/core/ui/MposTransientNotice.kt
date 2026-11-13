package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Compact overlay: it never takes space from the page or blocks interaction outside its bounds. */
@Composable fun MposTransientNotice(message: String,modifier: Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    Surface(modifier.widthIn(max=400.dp).testTag("transientNotice").semantics {liveRegion=LiveRegionMode.Polite},
        shape=RoundedCornerShape(16.dp),color=c.primaryContainer,contentColor=c.onPrimaryContainer,
        border=BorderStroke(1.dp,c.primary.copy(alpha=.25f)),shadowElevation=3.dp) {
        Text(message,Modifier.padding(horizontal=18.dp,vertical=12.dp),style=MaterialTheme.typography.bodyMedium)
    }
}

/** Standard feedback anchor: above screen content, never a row inside a scrolling form. */
@Composable fun BoxScope.MposTopNotice(message: String?) {
    message?.let {MposTransientNotice(it,Modifier.align(Alignment.TopCenter).padding(top=8.dp,start=16.dp,end=16.dp))}
}
