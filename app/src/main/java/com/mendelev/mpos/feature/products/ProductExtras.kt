package com.mendelev.mpos.feature.products

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable fun ProductPhotoEditor(model: CatalogViewModel, d: ProductDraft, enabled: Boolean, onPick: () -> Unit) {
    Text("Фото товара",style=MaterialTheme.typography.titleMedium)
    ProductPhoto(d.photoId,model.photos,Modifier.size(160.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        MposButton(if(d.photoId==null) "Добавить фото" else "Заменить фото",onPick,enabled=enabled,style=ActionStyle.Secondary)
        if(d.photoId!=null) MposButton("Удалить фото",{model.change { it.copy(photoId=null) }},enabled=enabled,style=ActionStyle.Danger)
    }
    Text("Фото и его удаление применяются после сохранения карточки. До 16 МБ; JPEG, PNG, WebP или HEIF. Копия хранится на устройстве.",style=MaterialTheme.typography.bodySmall)
}

@Composable fun ProductModifierEditor(model: CatalogViewModel, state: CatalogUi, d: ProductDraft) {
    Text("Модификаторы",style=MaterialTheme.typography.titleMedium)
    val enabled= !state.busy
    fun group(index: Int, transform: (ModifierGroupInput) -> ModifierGroupInput) {
        model.change { draft -> draft.copy(modifierGroups=draft.modifierGroups.mapIndexed { i,g -> if(i==index) transform(g) else g }) }
    }
    d.modifierGroups.forEachIndexed { gi,g ->
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            MposField(g.name,{v -> group(gi) { it.copy(name=v) }},"Название группы ${gi+1}",Modifier.testTag("modifierGroup$gi"),enabled)
            MposField(g.minimum,{v -> group(gi) { it.copy(minimum=v) }},"Минимум вариантов ${gi+1}",enabled=enabled,numeric=true)
            MposField(g.maximum,{v -> group(gi) { it.copy(maximum=v) }},"Максимум вариантов ${gi+1}",enabled=enabled,numeric=true)
            g.options.forEachIndexed { oi,o ->
                fun option(transform: (ModifierOptionInput) -> ModifierOptionInput) { group(gi) { current ->
                    current.copy(options=current.options.mapIndexed { i,value -> if(i==oi) transform(value) else value })
                } }
                val product=state.products.find { it.product.id==o.productId }?.product
                Text(product?.name ?: "Товар недоступен",style=MaterialTheme.typography.titleSmall)
                MposField(o.posName,{v -> option { it.copy(posName=v) }},"Название в POS ${gi+1}.${oi+1}",enabled=enabled)
                MposField(o.quantity,{v -> option { it.copy(quantity=v) }},"Количество ${gi+1}.${oi+1}, ${unitLabel(product?.unit ?: "piece")}",Modifier.testTag("modifierQty$gi-$oi"),enabled,true)
                MposField(o.priceDelta,{v -> option { it.copy(priceDelta=v) }},"Доплата ${gi+1}.${oi+1}, BYN",Modifier.testTag("modifierPrice$gi-$oi"),enabled,true)
                MposButton("Удалить вариант ${gi+1}.${oi+1}",{ group(gi) { it.copy(options=it.options.filterIndexed { index,_ -> index!=oi }) } },enabled=enabled,style=ActionStyle.Secondary)
            }
            val choices=state.products.filter { it.product.id!=d.id && g.options.none { o -> o.productId==it.product.id } }
                .map { it.product.id to it.product.name }
            if(choices.isNotEmpty()) MposSelect("Добавить вариант в группу ${gi+1}","",listOf("" to "Выбрать вариант ${gi+1}")+choices,{ id ->
                if(id.isNotEmpty()) group(gi) { it.copy(options=it.options+ModifierOptionInput(UUID.randomUUID().toString(),id)) }
            },enabled)
            MposButton("Удалить группу ${gi+1}",{ model.change { it.copy(modifierGroups=it.modifierGroups.filterIndexed { index,_ -> index!=gi }) } },enabled=enabled,style=ActionStyle.Danger)
            HorizontalDivider()
        }
    }
    MposButton("Добавить группу",{ model.change { it.copy(modifierGroups=it.modifierGroups+ModifierGroupInput(UUID.randomUUID().toString(),"")) } },
        enabled=enabled && d.modifierGroups.size<20,style=ActionStyle.Secondary)
    Text("Выбор вариантов в заказе появится в рабочей зоне. Количество задаётся в базовой единице товара; доплата — за одну основную позицию.",style=MaterialTheme.typography.bodySmall)
}
