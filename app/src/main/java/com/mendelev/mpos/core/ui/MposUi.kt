package com.mendelev.mpos.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mendelev.mpos.R

private val manrope = FontFamily(Font(R.font.manrope, FontWeight.Normal), Font(R.font.manrope, FontWeight.Medium), Font(R.font.manrope, FontWeight.SemiBold), Font(R.font.manrope, FontWeight.Bold), Font(R.font.manrope, FontWeight.ExtraBold))
@Composable fun MposTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary=Color(0xFF31B98D), onPrimary=Color(0xFF07140F), primaryContainer=Color(0xFF173B31),
        background=Color(0xFF171A21), surface=Color(0xFF222631), onSurface=Color(0xFFF4F6FA), onBackground=Color(0xFFF4F6FA),
        onSurfaceVariant=Color(0xFFA9B0BF), outline=Color(0xFF353B49), error=Color(0xFFFF6B61))
    else lightColorScheme(primary=Color(0xFF0E8F6F), onPrimary=Color.White, primaryContainer=Color(0xFFE4F3EE),
        background=Color(0xFFF5F4F0), surface=Color.White, onSurface=Color(0xFF1B1F2A), onBackground=Color(0xFF1B1F2A),
        onSurfaceVariant=Color(0xFF767C8C), outline=Color(0xFFE7E4DD), error=Color(0xFFE0483E))
    MaterialTheme(colorScheme=colors, typography=Typography(
        titleLarge=TextStyle(fontFamily=manrope, fontWeight=FontWeight.ExtraBold, fontSize=22.sp),
        titleMedium=TextStyle(fontFamily=manrope, fontWeight=FontWeight.Bold, fontSize=18.sp),
        bodyLarge=TextStyle(fontFamily=manrope, fontSize=16.sp), bodyMedium=TextStyle(fontFamily=manrope, fontSize=15.sp),
        bodySmall=TextStyle(fontFamily=manrope, fontSize=12.sp), labelLarge=TextStyle(fontFamily=manrope, fontWeight=FontWeight.Bold, fontSize=14.5.sp),
        labelMedium=TextStyle(fontFamily=manrope, fontWeight=FontWeight.Bold, fontSize=12.5.sp)
    ), shapes=Shapes(small=RoundedCornerShape(10.dp), medium=RoundedCornerShape(16.dp), large=RoundedCornerShape(22.dp)), content=content)
}

enum class ActionStyle { Primary, Secondary, Danger }
@Composable fun MposButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    style: ActionStyle = ActionStyle.Primary, busy: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val colors = when(style) {
        ActionStyle.Primary -> ButtonDefaults.buttonColors()
        ActionStyle.Secondary -> ButtonDefaults.buttonColors(containerColor=c.background, contentColor=c.onSurface)
        ActionStyle.Danger -> ButtonDefaults.buttonColors(containerColor=c.error, contentColor=if(isSystemInDarkTheme()) Color(0xFF1B1F2A) else Color.White)
    }
    Button(onClick=onClick, enabled=enabled && !busy, modifier=modifier.heightIn(min=48.dp),
        shape=RoundedCornerShape(16.dp), colors=colors, contentPadding=PaddingValues(horizontal=16.dp, vertical=12.dp)) {
        if(busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth=2.dp); Spacer(Modifier.width(8.dp)) }
        Text(label)
    }
}

@Composable fun MposCloseButton(onClose: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick=onClose, enabled=enabled, modifier=Modifier.size(48.dp).semantics { contentDescription = "Закрыть" }) {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp)), contentAlignment=Alignment.Center) {
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(22.dp)) {
                drawLine(color, Offset(4.dp.toPx(),4.dp.toPx()), Offset(size.width-4.dp.toPx(),size.height-4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width-4.dp.toPx(),4.dp.toPx()), Offset(4.dp.toPx(),size.height-4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable fun MposDialog(title: String, onClose: () -> Unit, closable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest={ if(closable) onClose() }) {
        Surface(shape=RoundedCornerShape(22.dp), color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.widthIn(max=460.dp).padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style=MaterialTheme.typography.titleMedium)
                    MposCloseButton(onClose, closable)
                }
                content()
            }
        }
    }
}

@Composable fun MposField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, numeric: Boolean = false, error: String? = null) {
    OutlinedTextField(value=value, onValueChange=onChange, label={ Text(label) }, enabled=enabled,
        modifier=modifier.fillMaxWidth().heightIn(min=48.dp), shape=RoundedCornerShape(10.dp), singleLine=true,
        isError=error!=null, supportingText=if(error!=null) {{ Text(error) }} else null,
        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text),
        colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outline))
}

@Composable fun MposNotice(text: String, isError: Boolean = false) {
    Surface(color=if(isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape=RoundedCornerShape(16.dp)) {
        Text(text, Modifier.padding(16.dp), color=if(isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
    }
}
