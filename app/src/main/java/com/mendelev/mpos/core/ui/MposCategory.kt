package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable fun MposCategoryBadge(color: String, symbol: String) {
    Surface(color=Color(android.graphics.Color.parseColor(color)),contentColor=Color(0xFF202631),shape=MaterialTheme.shapes.medium) {
        Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).padding(8.dp),contentAlignment=Alignment.Center) {
            Text(symbol,style=MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun MposColorPicker(colors: List<String>, selected: String, onSelect: (String) -> Unit, enabled: Boolean = true) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { index,color ->
            Surface(color=Color(android.graphics.Color.parseColor(color)),shape=MaterialTheme.shapes.medium,
                border=BorderStroke(if(color==selected) 3.dp else 1.dp,
                    if(color==selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier=Modifier.size(48.dp).testTag("categoryColor:$color")
                    .selectable(selected=color==selected,enabled=enabled,role=Role.RadioButton,onClick={onSelect(color)})
                    .semantics { contentDescription="Цвет категории ${index+1}: $color" }) {
                Box(contentAlignment=Alignment.Center) {
                    if(color==selected) Text("✓",color=Color(0xFF202631),style=MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
