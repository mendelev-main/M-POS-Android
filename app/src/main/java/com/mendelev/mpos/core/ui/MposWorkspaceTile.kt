package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** pos.html pcard / product-grid: shared 18dp card, 144dp minimum, 12dp grid gaps. */
@Composable fun MposWorkspaceTile(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=modifier.fillMaxWidth().heightIn(min=144.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
