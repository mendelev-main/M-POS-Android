package com.mendelev.mpos.feature.receiving

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.purchasing.purchaseDate
import java.math.BigDecimal
@Composable internal fun ReceivingConfirmationWindow(preview: ReceivingPreview,locked: Boolean,dirty: Boolean,
    onBack: ()->Unit,onConfirm: ()->Unit) {
    MposDialog("Подтвердить приёмку?",onBack,closable=!locked,maxWidth=440.dp) {
        // The same explicit confirmation also authorizes the highlighted excess quantity.
        if(preview.hasOverage) Text("Количество превышает заказанное. Подтвердить приёмку с превышением?",
            fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposSupplyAction("Отмена",onBack,Modifier.weight(1f),!locked,PosControl.Outline,height=48.dp,radius=16.dp)
            MposSupplyAction("Подтвердить",onConfirm,Modifier.weight(1f),!locked && !dirty,PosControl.Cash,height=48.dp,radius=16.dp)
        }
    }
}

@Composable internal fun ReceivingRecordWindow(record: PostedReceiving,onClose: ()->Unit) {
    MposDialog(if(record.draft.order==null) "Закупка" else "Приёмка",onClose,maxWidth=680.dp) {
        Text("${record.draft.supplierName} · ${purchaseDate(record.at)}",style=MaterialTheme.typography.titleSmall)
        Text("Номер ${record.draft.invoiceNumber} · ${record.draft.invoiceDate}",modifier=Modifier.testTag("receivingPostedTitle"),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            com.mendelev.mpos.feature.purchasing.SupplyBadge("Принята","success")
            if(receivingDiscrepancy(record)) com.mendelev.mpos.feature.purchasing.SupplyBadge("Недовоз","danger")
        }
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {
            record.draft.lines.forEach {line->
                val total=ReceivingRules.lineTotal(line) ?: 0L
                val price=runCatching {val qty=line.quantity.toBigDecimal();if(qty.signum()==0) "0" else BigDecimal.valueOf(total,2).divide(qty,12,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}.getOrDefault("—")
                Column(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(line.product.name,style=MaterialTheme.typography.titleSmall)
                    Text("Количество: ${line.quantity} ${purchaseUnitLabel(line.unit)} · Цена: $price · Сумма: ${receivingMoney(total)}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
        Text("Итого: ${receivingMoney(record.result.totalMinor)}",style=MaterialTheme.typography.titleLarge)
        MposButton("Закрыть",onClose,Modifier.fillMaxWidth())
    }
}
