package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun MposSupplyField(label: String,value: String,onChange: (String)->Unit,modifier: Modifier=Modifier,
    enabled: Boolean=true,numeric: Boolean=true,suggested: Boolean=false,onTouch: ()->Unit={},tag: String="",placeholder: String="0",included: Boolean=false) {
    val c=MaterialTheme.colorScheme
    val yellow=if(isMposDarkTheme()) Color(0xFF514421) else Color(0xFFFFF1BB)
    val green=if(isMposDarkTheme()) Color(0xFF254237) else Color(0xFFD9EEE3)
    var editor by remember {mutableStateOf(TextFieldValue(value))}
    var focused by remember {mutableStateOf(false)}
    val touch by rememberUpdatedState(onTouch)
    LaunchedEffect(value) {if(editor.text!=value) editor=TextFieldValue(value,if(focused && numeric) TextRange(0,value.length) else TextRange(value.length))}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,fontSize=12.sp,lineHeight=16.sp,maxLines=1,color=c.onSurfaceVariant)
        Surface(shape=RoundedCornerShape(10.dp),color=if(suggested) yellow else if(included) green else c.background,border=BorderStroke(1.dp,if(suggested) {if(isMposDarkTheme()) Color(0xFFB99B49) else Color(0xFFB88A25)} else if(included) c.primary.copy(alpha=.5f) else c.outline)) {
            BasicTextField(editor,{next->val changed=next.text!=editor.text;editor=next;if(changed) onChange(next.text)},enabled=enabled,singleLine=true,textStyle=TextStyle(color=c.onSurface,fontSize=14.sp,lineHeight=20.sp,fontFamily=MaterialTheme.typography.bodyMedium.fontFamily),
                keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),
                modifier=Modifier.fillMaxWidth().heightIn(min=44.dp).pointerInput(enabled) {
                    awaitEachGesture {awaitFirstDown(requireUnconsumed=false);if(enabled) touch()}
                }.onFocusChanged {
                    if(it.isFocused && !focused) {focused=true;if(numeric) editor=editor.copy(selection=TextRange(0,editor.text.length));onTouch()}
                    else if(!it.isFocused) focused=false
                }.testTag(tag),
                decorationBox={inner->Box(Modifier.padding(horizontal=10.dp,vertical=10.dp),contentAlignment=Alignment.CenterStart) {
                    if(editor.text.isEmpty()) Text(placeholder,color=c.onSurfaceVariant,fontSize=14.sp)
                    inner()
                }})
        }
    }
}
