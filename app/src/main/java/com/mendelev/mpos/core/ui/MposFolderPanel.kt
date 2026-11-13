package com.mendelev.mpos.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MposFolderTab(val label: String,val icon: String) { Supplies("Поставки","delivery"), New("Новый заказ","add"), History("История","history") }

/** One continuous silhouette: the selected tab and panel never have overlapping borders. */
@Composable fun MposFolderPanel(selected: MposFolderTab,onSelect: (MposFolderTab)->Unit,
    modifier: Modifier=Modifier,enabled: Boolean=true,content: @Composable ColumnScope.()->Unit) {
    val dark=isMposDarkTheme()
    val panel=MaterialTheme.colorScheme.surface
    val gap=20.dp
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val top=56.dp.toPx();val r=18.dp.toPx();val shoulder=10.dp.toPx()
            // Tabs share the full panel width. Edge tabs join the outer side directly,
            // without a concave shoulder extending beyond the panel.
            val cell=(size.width-2*gap.toPx())/3
            val left=selected.ordinal*(cell+gap.toPx())
            val right=left+cell
            val path=Path().apply {
                if(selected==MposFolderTab.Supplies) {
                    moveTo(0f,r);quadraticTo(0f,0f,r,0f)
                } else {
                    moveTo(0f,top+r);quadraticTo(0f,top,r,top)
                    lineTo(left-shoulder,top);quadraticTo(left,top,left,top-shoulder)
                    lineTo(left,r);quadraticTo(left,0f,left+r,0f)
                }
                lineTo((right-r).coerceAtLeast(left+r),0f);quadraticTo(right,0f,right,r)
                if(selected!=MposFolderTab.History) {
                    lineTo(right,top-shoulder);quadraticTo(right,top,right+shoulder,top)
                    lineTo(size.width-r,top);quadraticTo(size.width,top,size.width,top+r)
                }
                lineTo(size.width,size.height-r);quadraticTo(size.width,size.height,size.width-r,size.height)
                lineTo(r,size.height);quadraticTo(0f,size.height,0f,size.height-r)
                close()
            }
            if(cell>0) drawPath(path,panel)
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(gap)) {
                MposFolderTab.entries.forEach {tab->
                    val active=tab==selected
                    val fill=when(tab) {
                        MposFolderTab.Supplies->if(dark) Color(0xFF293C58) else Color(0xFFE1EBFB)
                        MposFolderTab.New->if(dark) Color(0xFF254237) else Color(0xFFD9EEE3)
                        MposFolderTab.History->if(dark) Color(0xFF4A3E25) else Color(0xFFF5E8BC)
                    }
                    val ink=when(tab) {
                        MposFolderTab.Supplies->if(dark) Color(0xFFAACBF7) else Color(0xFF365F96)
                        MposFolderTab.New->if(dark) Color(0xFF9CDDBB) else Color(0xFF226245)
                        MposFolderTab.History->if(dark) Color(0xFFEED08C) else Color(0xFF795B13)
                    }
                    Box(Modifier.weight(1f).height(56.dp)
                        .clip(RoundedCornerShape(topStart=16.dp,topEnd=16.dp))
                        .clickable(enabled=enabled,role=Role.Tab) {onSelect(tab)}.semantics {this.selected=active}
                        .padding(vertical=8.dp),contentAlignment=Alignment.Center) {
                        Row(Modifier.fillMaxWidth(.88f).heightIn(min=34.dp).background(if(active) fill else fill.copy(alpha=.55f),RoundedCornerShape(10.dp))
                            .padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(7.dp,Alignment.CenterHorizontally)) {
                            MposPosIcon(tab.icon,Modifier.size(16.dp),tint=ink)
                            Text(tab.label,color=ink,fontSize=13.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(18.dp),content=content)
        }
    }
}
