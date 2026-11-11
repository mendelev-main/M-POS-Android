package com.mendelev.mpos.feature.loyalty

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.print.LoyaltyReceiptRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

@Composable internal fun ReceiptLoyaltyInfo(receipt: ReceiptEntity,snapshot: ReceiptSnapshot) {
    if(snapshot.cart.loyalty==null) return
    val context=LocalContext.current
    var effect by remember(receipt.id) {mutableStateOf<LoyaltyEffect?>(null)}
    LaunchedEffect(receipt.id) {
        val dao=CatalogDatabase.get(context).workspace()
        val suffix=LoyaltyEffectCodec.suffix(receipt.createdAt,receipt.id)
        combine(dao.observeDocument(LoyaltyEffectCodec.PENDING+suffix),dao.observeDocument(LoyaltyEffectCodec.RESULT+suffix)) {pending,result->pending ?: result}
            .collect {document->effect=withContext(Dispatchers.Default) {document?.let {runCatching {LoyaltyEffectCodec.decode(it.payload)}.getOrNull()}}}
    }
    Column(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        LoyaltyReceiptRows.build(snapshot,effect).forEach {(name,value)->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(name,Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value,fontSize=12.sp)
            }
        }
    }
}
