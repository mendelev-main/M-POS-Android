package com.mendelev.mpos.feature.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.ActionStyle

/** Pinned .pe-card .field variant; labels stay above the 48px input. */
@Composable internal fun ProductEditorField(value: String,onChange: (String)->Unit,label: String,modifier: Modifier=Modifier,
    enabled: Boolean=true,numeric: Boolean=false,error: String?=null,singleLine: Boolean=true,placeholder: String="",readOnly: Boolean=false,compact: Boolean=false) {
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        if(!compact) {Text(label,fontSize=12.sp,lineHeight=16.5.sp,fontWeight=FontWeight.Bold,color=c.onSurfaceVariant);Spacer(Modifier.height(6.dp))}
        BasicTextField(value,onChange,modifier=modifier.semantics {contentDescription=label}.fillMaxWidth().heightIn(min=if(singleLine) (if(compact) 44.dp else 48.dp) else 96.dp)
            .background(if(readOnly) c.background else c.surface,RoundedCornerShape(10.dp)).border(1.dp,if(error!=null) c.error else c.outline,RoundedCornerShape(10.dp))
            .padding(horizontal=14.dp,vertical=12.dp),enabled=enabled,readOnly=readOnly,singleLine=singleLine,minLines=if(singleLine) 1 else 3,
            textStyle=MaterialTheme.typography.bodyLarge.copy(fontSize=16.sp,lineHeight=22.sp,color=if(enabled) c.onSurface else c.onSurfaceVariant),
            cursorBrush=SolidColor(c.primary),keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),
            decorationBox={inner->Box(contentAlignment=if(singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if(value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder,fontSize=16.sp,lineHeight=22.sp,color=c.onSurfaceVariant)
                inner()
            }})
        if(error!=null) Text(error,fontSize=12.sp,color=c.error)
    }
}
@Composable internal fun ProductEditorSelect(label: String,value: String,options: List<Pair<String,String>>,onChange: (String)->Unit,
    enabled: Boolean=true,modifier: Modifier=Modifier,compact: Boolean=false) {
    var expanded by remember {mutableStateOf(false)}
    Column(modifier.fillMaxWidth()) {
        if(!compact) {Text(label,fontSize=12.sp,lineHeight=16.5.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(6.dp))}
        Box {
            Surface(onClick={expanded=true},enabled=enabled,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),modifier=Modifier.semantics {contentDescription=label}.fillMaxWidth().heightIn(min=if(compact) 44.dp else 48.dp)) {
                Row(Modifier.padding(horizontal=if(compact) 8.dp else 14.dp,vertical=if(compact) 10.dp else 12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(options.find {it.first==value}?.second ?: value,Modifier.weight(1f),fontSize=16.sp)
                    Text("⌄",fontSize=16.sp)
                }
            }
            DropdownMenu(expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=300.dp)) {
                options.forEach {(id,name)->DropdownMenuItem(text={Text(name)},onClick={expanded=false;onChange(id)})}
            }
        }
    }
}
@Composable internal fun ProductEditorButton(label: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,
    style: ActionStyle=ActionStyle.Outline,busy: Boolean=false) {
    val c=MaterialTheme.colorScheme
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(onClick=onClick,enabled=enabled && !busy,modifier=modifier.heightIn(min=48.dp),shape=RoundedCornerShape(16.dp),
            color=if(style==ActionStyle.Primary) c.primary else c.surface,
            contentColor=if(style==ActionStyle.Primary) c.onPrimary else if(style==ActionStyle.Danger) c.error else c.onSurface,
            border=if(style==ActionStyle.Primary) null else BorderStroke(if(style==ActionStyle.Danger) 1.dp else 1.5.dp,if(style==ActionStyle.Danger) c.error else c.outline)) {
            Box(Modifier.padding(horizontal=12.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                Text(if(busy) "Сохранение…" else label,fontSize=14.5.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
}

@Composable internal fun ProductEditorFlag(label: String,checked: Boolean,onChange: (Boolean)->Unit,enabled: Boolean=true) {
    Row(Modifier.fillMaxWidth().heightIn(min=44.dp).toggleable(value=checked,enabled=enabled,role=Role.Checkbox,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Checkbox(checked,onCheckedChange=null,enabled=enabled,modifier=Modifier.size(22.dp))
        Text(label,fontSize=14.sp,lineHeight=19.6.sp)
    }
}

/** Shared row for online and stock policies: label left, switch right, one accessible toggle. */
@Composable internal fun ProductEditorSwitch(label: String,checked: Boolean,onChange: (Boolean)->Unit,enabled: Boolean=true,modifier: Modifier=Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onChange),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,Modifier.weight(1f),fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)
        Switch(checked,onCheckedChange=null,enabled=enabled)
    }
}
