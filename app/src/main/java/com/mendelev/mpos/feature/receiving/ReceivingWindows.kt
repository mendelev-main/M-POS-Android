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

@Composable internal fun ReceivingConfirmationWindow(preview: ReceivingPreview,linked: Boolean,locked: Boolean,dirty: Boolean,
    overage: Boolean,onOverage: ()->Unit,onBack: ()->Unit,onConfirm: ()->Unit) {
    MposDialog("Подтвердить приёмку?",onBack,closable=!locked,wide=true) {
        Text("${preview.lines.size} строк · ${receivingMoney(preview.totalMinor)}. Фактическое количество зачислится на склад, себестоимость пересчитается.",modifier=Modifier.testTag("receivingPreviewTotal"))
        if(linked) Text("Заказ будет закрыт; недопоставленные позиции автоматически не переносятся.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            preview.lines.forEach {line->Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(line.name,style=MaterialTheme.typography.titleSmall)
                    Text("Остаток ${line.oldStock} → ${line.newStock}; себестоимость ${line.oldCost} → ${line.newCost} BYN/ед.",fontSize=13.sp,modifier=Modifier.testTag("receivingPreviewLine:${line.productId}"))
                }
            }}
            if(preview.hasOverage) MposButton(if(overage) "Превышение подтверждено" else "Подтверждаю превышение заказа",onOverage,Modifier.testTag("receivingOverage"),enabled=!locked,style=ActionStyle.Secondary)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposButton("Вернуться к приёмке",onBack,Modifier.weight(1f),enabled=!locked,style=ActionStyle.Secondary)
            MposButton("Подтвердить приход",onConfirm,Modifier.weight(1f).testTag("receivingPostConfirm"),enabled=!locked && !dirty && (!preview.hasOverage || overage))
        }
    }
}

@Composable internal fun ReceivingRecordWindow(record: PostedReceiving,onClose: ()->Unit) {
    MposDialog(if(record.draft.order==null) "Закупка" else "Приёмка",onClose,maxWidth=680.dp) {
        Text("${record.draft.supplierName} · ${purchaseDate(record.at)}",style=MaterialTheme.typography.titleSmall)
        Text("Номер ${record.draft.invoiceNumber} · ${record.draft.invoiceDate}",modifier=Modifier.testTag("receivingPostedTitle"),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
        if(record.draft.order!=null) ReceivingBadge(if(receivingDiscrepancy(record)) "Расхождение" else "Принято",receivingDiscrepancy(record))
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
