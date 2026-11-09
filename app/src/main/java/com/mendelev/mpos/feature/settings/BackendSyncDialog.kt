package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import kotlinx.coroutines.*

@Composable internal fun BackendSyncDialog(onClose: ()->Unit,onResult: (String)->Unit) {
    val context=LocalContext.current;val runtime=remember {BackendRuntime.get(context)}
    val generation=remember {Administration.session.state.value.generation}
    val scope=rememberCoroutineScope()
    var preview by remember {mutableStateOf<Pair<BackendSettings,MenuPublication>?>(null)}
    var busy by remember {mutableStateOf(true)}
    var result by remember {mutableStateOf<String?>(null)}
    val status by runtime.status.collectAsState()
    LaunchedEffect(Unit) {try {preview=runtime.preview(generation)} catch(e:Exception) {result=if(e is IllegalStateException || e is IllegalArgumentException) e.message else "Не удалось подготовить каталог"} finally {busy=false}}
    MposDialog("Публикация каталога",onClose,closable=!busy,maxWidth=640.dp) {
        if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth());Text(if(preview==null) "Подготавливаем каталог…" else status)}
        result?.let {Text(it)}
        if(result==null) preview?.let {(s,p)->
            Text(s.url,style=MaterialTheme.typography.titleMedium)
            Text("Категорий: ${p.categories} · Товаров: ${p.products} · Фото: ${p.photos.size}",Modifier.padding(top=12.dp))
            if(p.products==0) Text("Каталог пуст: публикация скроет все товары сайта.",color=MaterialTheme.colorScheme.error)
            if(p.excluded>0) Text("Исключено товаров с обязательными модификаторами: ${p.excluded}")
            Text("Тарифов доставки: ${org.json.JSONArray(p.tariffs).length()}"+(p.tariffVersion?.let {" · текущая версия сайта: $it"} ?: " · версия сайта пока не подтверждена"))
            if(p.tariffVersion!=null) Text("Список тарифов на сайте будет заменён вариантами из этого приложения.",style=MaterialTheme.typography.bodySmall)
            Text("Каталог сайта будет заменён этим списком. Отсутствующие товары backend скроет. Продолжайте только для сервера этой организации.",Modifier.padding(top=16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Закрыть",onClose,Modifier.weight(1f),enabled=!busy,style=ActionStyle.Outline)
            if(result==null && preview!=null) MposButton("Опубликовать",{
                val (s,p)=preview!!;busy=true
                scope.launch {try {result=runtime.publish(s,p,generation);onResult(result!!)} finally {busy=false}}
            },Modifier.weight(1f),enabled=!busy)
        }
    }
}
