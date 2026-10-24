package com.mendelev.mpos.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mendelev.mpos.R

@OptIn(ExperimentalTextApi::class)
private fun manropeFont(weight: FontWeight) = Font(R.font.manrope, weight=weight,
    variationSettings=FontVariation.Settings(FontVariation.weight(weight.weight)))
private val manrope = FontFamily(manropeFont(FontWeight.Normal), manropeFont(FontWeight.Medium),
    manropeFont(FontWeight.SemiBold), manropeFont(FontWeight.Bold), manropeFont(FontWeight.ExtraBold))
@Composable fun MposTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary=Color(0xFF31B98D), onPrimary=Color(0xFF07140F), primaryContainer=Color(0xFF173B31), onPrimaryContainer=Color(0xFF31B98D),
        background=Color(0xFF171A21), surface=Color(0xFF222631), onSurface=Color(0xFFF4F6FA), onBackground=Color(0xFFF4F6FA),
        onSurfaceVariant=Color(0xFFA9B0BF), outline=Color(0xFF353B49), error=Color(0xFFFF6B61),
        surfaceContainerHighest=Color(0xFF222631), errorContainer=Color(0xFF432522), onErrorContainer=Color(0xFFFF6B61))
    else lightColorScheme(primary=Color(0xFF0E8F6F), onPrimary=Color.White, primaryContainer=Color(0xFFE4F3EE), onPrimaryContainer=Color(0xFF0E8F6F),
        background=Color(0xFFF5F4F0), surface=Color.White, onSurface=Color(0xFF1B1F2A), onBackground=Color(0xFF1B1F2A),
        onSurfaceVariant=Color(0xFF767C8C), outline=Color(0xFFE7E4DD), error=Color(0xFFE0483E),
        surfaceContainerHighest=Color.White, errorContainer=Color(0xFFFBE7E5), onErrorContainer=Color(0xFF1B1F2A))
    MaterialTheme(colorScheme=colors, typography=Typography(
        displayLarge=TextStyle(fontFamily=manrope, fontSize=57.sp), displayMedium=TextStyle(fontFamily=manrope, fontSize=45.sp), displaySmall=TextStyle(fontFamily=manrope, fontSize=36.sp),
        headlineLarge=TextStyle(fontFamily=manrope, fontWeight=FontWeight.ExtraBold, fontSize=32.sp), headlineMedium=TextStyle(fontFamily=manrope, fontWeight=FontWeight.Bold, fontSize=28.sp), headlineSmall=TextStyle(fontFamily=manrope, fontWeight=FontWeight.Bold, fontSize=24.sp),
        titleSmall=TextStyle(fontFamily=manrope, fontWeight=FontWeight.Bold, fontSize=14.sp), labelSmall=TextStyle(fontFamily=manrope, fontWeight=FontWeight.Bold, fontSize=11.sp),
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
            Column(Modifier.widthIn(max=460.dp).padding(28.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
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

@Composable fun MposNavigationAction(label: String, onClick: () -> Unit) {
    TextButton(onClick=onClick, modifier=Modifier.heightIn(min=48.dp), shape=RoundedCornerShape(100.dp),
        colors=ButtonDefaults.textButtonColors(contentColor=Color.White)) { Text(label) }
}

/** Shared select and flag controls: the same button shape, text and hit area in all editors. */
@Composable fun MposSelect(label: String, value: String, options: List<Pair<String, String>>,
    onChange: (String) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column(modifier) {
        Text(label, style=MaterialTheme.typography.labelMedium)
        Box {
            MposButton(options.find { it.first == value }?.second ?: value, { keyboard?.hide();expanded=true }, enabled=enabled, style=ActionStyle.Secondary)
            DropdownMenu(expanded=expanded, onDismissRequest={expanded=false}, modifier=Modifier.heightIn(max=300.dp)) {
                options.forEach { (key, text) -> DropdownMenuItem(text={Text(text)}, onClick={expanded=false;onChange(key)}) }
            }
        }
    }
}

@Composable fun MposFlag(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp), verticalAlignment=Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style=MaterialTheme.typography.bodyMedium)
        Switch(checked=checked, onCheckedChange=onChange, enabled=enabled, modifier=Modifier.semantics { contentDescription=label })
    }
}
