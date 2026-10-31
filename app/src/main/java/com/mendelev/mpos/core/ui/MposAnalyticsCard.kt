package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Pinned Web analytics-kpi / analytics-card variants, shared by all report panels. */
@Composable fun MposAnalyticsCard(modifier: Modifier=Modifier,metric: Boolean=false,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier,shape=RoundedCornerShape(if(metric) 18.dp else 20.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(if(metric) 16.dp else 18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
