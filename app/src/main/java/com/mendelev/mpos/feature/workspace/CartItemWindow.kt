package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.sale.SaleDiscount
import com.mendelev.mpos.domain.settings.SettingEntry
import com.mendelev.mpos.domain.workspace.CartLine
import java.math.BigDecimal

internal fun cartItemStep(quantity: String,original: String,delta: Int): String {
    require(delta in listOf(-1,1))
    return CatalogRules.canonical((quantity.toBigDecimal()+delta.toBigDecimal()).max(original.toBigDecimal().min(BigDecimal.ONE)))
}
/** Pinned openCartItemModal: local draft, one atomic Save, no writes on +/- or Cancel. */
@Composable internal fun CartItemWindow(line: CartLine,currentDiscount: SaleDiscount?,discounts: List<SettingEntry>,busy: Boolean,error: String?,onClose: ()->Unit,
    onSave: (String,String,String,SaleDiscount?)->Unit) {
    var quantity by rememberSaveable(line.id) {mutableStateOf(line.quantity)}
    var comment by rememberSaveable(line.id) {mutableStateOf(line.comment)}
    val matches=discounts.find {it.id==line.discountId && currentDiscount==SaleDiscount(it.type,it.value,it.name)}
    var selected by rememberSaveable(line.id) {mutableStateOf(if(currentDiscount==null) "" else matches?.id ?: "__saved_discount__")}
    val selectedRow=discounts.find {it.id==selected}
    val selectedValue=if(selected=="__saved_discount__") currentDiscount else selectedRow?.let {SaleDiscount(it.type,it.value,it.name)}
    val validChoice=selected.isEmpty() || selectedValue!=null
    val c=MaterialTheme.colorScheme
    Dialog({if(!busy) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        MposDialogStatusBar()
        Surface(Modifier.padding(20.dp).widthIn(max=560.dp).fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.92f).dp).testTag("cartItemWindow"),shape=RoundedCornerShape(22.dp),color=c.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp)) {
                Text(line.name,fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(bottom=16.dp))
                CartItemLabel("Количество")
                Row(Modifier.fillMaxWidth().padding(top=6.dp,bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    MposPosControl("−",{quantity=cartItemStep(quantity,line.quantity,-1)},Modifier.width(46.dp).testTag("workspaceMinus:${line.id}"),!busy,height=46.dp,radius=14.dp,fontSize=24f,horizontalPadding=0.dp)
                    Box(Modifier.widthIn(min=52.dp),contentAlignment=Alignment.Center) {Text(quantity,Modifier.testTag("cartItemQuantity"),fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.ExtraBold)}
                    MposPosControl("+",{quantity=cartItemStep(quantity,line.quantity,1)},Modifier.width(46.dp).testTag("workspacePlus:${line.id}"),!busy,height=46.dp,radius=14.dp,fontSize=24f,horizontalPadding=0.dp)
                    Spacer(Modifier.weight(1f))
                }
                CartItemLabel("Комментарий к товару")
                BasicTextField(comment,{comment=it},enabled=!busy,textStyle=TextStyle(fontSize=15.sp,color=c.onSurface,fontFamily=FontFamily.Monospace,lineHeight=18.sp),modifier=Modifier.fillMaxWidth().testTag("cartItemComment"),decorationBox={inner->
                    Surface(shape=RoundedCornerShape(10.dp),border=BorderStroke(1.5.dp,c.outline),color=c.surface) {
                        Box(Modifier.fillMaxWidth().heightIn(min=80.dp).padding(horizontal=14.dp,vertical=12.dp)) {
                            if(comment.isEmpty()) Text("Например: без сахара, хорошо прожарить...",fontSize=15.sp,lineHeight=18.sp,fontFamily=FontFamily.Monospace,color=c.onSurfaceVariant)
                            inner()
                        }
                    }
                })
                Spacer(Modifier.height(18.dp));CartItemLabel("Скидка")
                val choices=listOf("" to ("Без скидки" to ""))+
                    listOfNotNull(currentDiscount?.takeIf {matches==null}?.let {"__saved_discount__" to (it.name to if(it.type=="percent") "${it.value}%" else "${it.value} BYN")})+
                    discounts.map {it.id to (it.name to if(it.type=="percent") "${it.value}%" else "${it.value} BYN")}
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    choices.chunked(2).forEach {row->Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        row.forEach {(id,text)->
                            val active=selected==id
                            Surface(onClick={selected=id},enabled=!busy,modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=48.dp).testTag("cartItemDiscount:$id").semantics {this.selected=active},shape=RoundedCornerShape(12.dp),border=BorderStroke(1.5.dp,if(active) c.primary else c.outline),color=if(active) c.primaryContainer else c.background,contentColor=if(active) c.primary else c.onSurface) {
                                Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(text.first,Modifier.weight(1f),fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)
                                    if(text.second.isNotEmpty()) Text(text.second,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.ExtraBold)
                                }
                            }
                        }
                        if(row.size==1) Spacer(Modifier.weight(1f))
                    }}
                }
                Spacer(Modifier.height(14.dp))
                Surface(shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(isMposDarkTheme()) Color(0xFF5A4A30) else Color(0xFFF5DCB8)),color=if(isMposDarkTheme()) Color(0xFF3A3022) else Color(0xFFFFF4E9)) {
                    Text("Скидка применяется только к этому товару в текущем заказе.",Modifier.padding(horizontal=15.dp,vertical=14.dp),fontSize=12.5.sp,lineHeight=18.75.sp,color=if(isMposDarkTheme()) Color(0xFFE9C989) else Color(0xFF8A5A17))
                }
                error?.let {Spacer(Modifier.height(10.dp));MposNotice(it,true)}
                if(!validChoice) MposNotice("Скидка больше не существует. Выберите другую или «Без скидки».",true)
                Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposPosControl("Отмена",onClose,Modifier.weight(1f),!busy,PosControl.Secondary,height=48.dp,radius=16.dp,fontSize=14.5f)
                    MposPosControl("Сохранить",{onSave(quantity,comment,if(selected=="__saved_discount__") line.discountId else selected,selectedValue)},Modifier.weight(1f).testTag("cartItemSave"),!busy && validChoice,PosControl.Cash,height=48.dp,radius=16.dp,fontSize=14.5f)
                }
            }
        }
    }
}
@Composable private fun CartItemLabel(text: String) {Text(text,Modifier.padding(bottom=6.dp),fontSize=12.5.sp,lineHeight=17.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)}
