package com.mendelev.mpos.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MposCountTone { Parked, Events }

/** Web .park-badge geometry; semantic colors stay identical inside feature-local themes. */
@Composable fun MposCountBadge(count: Int,tone: MposCountTone,modifier: Modifier=Modifier) {
    if(count<=0) return
    val dark=isMposDarkTheme()
    val fill=when(tone) {
        MposCountTone.Parked->if(dark) Color(0xFF31B98D) else Color(0xFF0E8F6F)
        MposCountTone.Events->if(dark) Color(0xFFFF6B61) else Color(0xFFE0483E)
    }
    Box(modifier.background(fill,RoundedCornerShape(100.dp)).padding(horizontal=8.dp,vertical=1.dp)) {
        Text(count.toString(),color=Color.White,fontSize=12.5.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold,maxLines=1,softWrap=false)
    }
}
