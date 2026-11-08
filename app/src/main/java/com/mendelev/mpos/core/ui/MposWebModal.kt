package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Pinned showModal / field / modal-actions variants. No per-feature close icon. */
@Composable fun MposWebModal(title: String,onClose: ()->Unit,enabled: Boolean=true,wide: Boolean=false,content: @Composable ColumnScope.()->Unit) {
    Dialog({if(enabled) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        MposDialogStatusBar()
        Surface(Modifier.padding(20.dp).widthIn(max=if(wide) 560.dp else 460.dp).fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.92f).dp).semantics {testTagsAsResourceId=true},shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp)) {
                Text(title,Modifier.padding(bottom=16.dp),fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.ExtraBold)
                content()
            }
        }
    }
}
@Composable fun MposWebLabel(label: String) {Text(label,Modifier.padding(bottom=6.dp),fontSize=12.5.sp,lineHeight=17.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable fun MposWebField(value: String,onChange: (String)->Unit,label: String,tag: String,enabled: Boolean=true,numeric: Boolean=false,placeholder: String="") {
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom=14.dp)) {
        MposWebLabel(label)
        BasicTextField(value,onChange,enabled=enabled,singleLine=true,textStyle=TextStyle(fontSize=15.sp,lineHeight=20.sp,color=c.onSurface,fontFamily=MaterialTheme.typography.bodyMedium.fontFamily),keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),modifier=Modifier.fillMaxWidth().testTag(tag),decorationBox={inner->
            Surface(shape=RoundedCornerShape(10.dp),border=BorderStroke(1.5.dp,c.outline),color=c.surface) {
                Box(Modifier.fillMaxWidth().heightIn(min=46.dp).padding(horizontal=14.dp,vertical=12.dp),contentAlignment=Alignment.CenterStart) {
                    if(value.isEmpty()) Text(placeholder,fontSize=15.sp,lineHeight=20.sp,color=c.onSurfaceVariant)
                    inner()
                }
            }
        })
    }
}
@Composable fun MposWebSelect(label: String,value: String,options: List<Pair<String,String>>,onChange: (String)->Unit,enabled: Boolean=true,tag: String="") {
    var expanded by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(bottom=14.dp)) {
        MposWebLabel(label)
        Box {
            Surface(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=46.dp).testTag(tag),shape=RoundedCornerShape(10.dp),border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline)) {
                Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(options.find {it.first==value}?.second ?: "Выберите сотрудника",Modifier.weight(1f),fontSize=15.sp,lineHeight=20.sp)
                    Text("⌄")
                }
            }
            DropdownMenu(expanded && enabled,{expanded=false}) {options.forEach {(id,text)->DropdownMenuItem(text={Text(text)},onClick={expanded=false;onChange(id)})}}
        }
    }
}
@Composable fun MposWebActions(onClose: ()->Unit,label: String,onSave: ()->Unit,enabled: Boolean=true,saveEnabled: Boolean=true,tag: String="") {
    Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        MposPosControl("Отмена",onClose,Modifier.weight(1f),enabled,PosControl.Secondary,height=48.dp,radius=16.dp,fontSize=14.5f)
        MposPosControl(label,onSave,Modifier.weight(1f).testTag(tag),enabled && saveEnabled,PosControl.Cash,height=48.dp,radius=16.dp,fontSize=14.5f)
    }
}

@Composable fun MposWebWarningNote(text: String) {
    val dark=isMposDarkTheme()
    Surface(shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(dark) Color(0xFF5A4A30) else Color(0xFFF5DCB8)),color=if(dark) Color(0xFF3A3022) else Color(0xFFFFF4E9)) {
        Text(text,Modifier.padding(horizontal=15.dp,vertical=14.dp),fontSize=12.5.sp,lineHeight=18.75.sp,color=if(dark) Color(0xFFE9C989) else Color(0xFF8A5A17))
    }
}
