package com.mendelev.mpos.feature.sale

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.ShiftRules

@Composable internal fun ReceiptPaymentEditWindow(value: Pair<ReceiptEntity,ReceiptSnapshot>,busy: Boolean,error: String?,onClose: ()->Unit,onConfirm: ()->Unit) {
    val cash=value.second.payment.parts.single().method=="cash"
    MposDialog("Изменить способ оплаты",onClose,!busy,maxWidth=560.dp) {
        Surface(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background) {
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                MposPosIcon(if(cash) "cash" else "card",Modifier.size(20.dp))
                Text(if(cash) " Наличные" else " Карта",fontSize=18.sp)
                Text(" → ",Modifier.padding(horizontal=12.dp),fontSize=22.sp)
                Text(if(cash) "Карта" else "Наличные",fontSize=18.sp,fontWeight=FontWeight.Bold)
            }
        }
        Text("Чек №"+value.first.number+" · "+ShiftRules.money(value.first.total),fontWeight=FontWeight.Bold)
        Text("Изменятся способ оплаты и итоги исходной смены. Сумма и товары останутся прежними.",fontSize=14.sp)
        Text("Исправляется запись в приложении. Операция по банковскому терминалу не выполняется.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposPosControl("Отмена",onClose,Modifier.weight(1f),!busy,PosControl.Secondary,48.dp,16.dp)
            MposPosControl(if(cash) "Изменить на карту" else "Изменить на наличные",onConfirm,Modifier.weight(1f).testTag("receiptPaymentConfirm"),!busy,PosControl.Cash,48.dp,16.dp,retainingAppearance=true)
        }
    }
}
