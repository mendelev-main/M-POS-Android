package com.mendelev.mpos.feature.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.telegram.*
import kotlinx.coroutines.*

@Composable internal fun OwnerAnalyticsPreview(report: OwnerAnalyticsReport,onClose: ()->Unit) {
    var page by remember(report) {mutableStateOf(0)}
    var image by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    val count=OwnerAnalyticsImage.pages(report).size
    LaunchedEffect(report,page) {
        image=null;error=null
        try {image=withContext(Dispatchers.Default) {OwnerAnalyticsImage.render(report,page).let {bytes->BitmapFactory.decodeByteArray(bytes,0,bytes.size)}}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {error="Не удалось сформировать изображение аналитики"}
    }
    MposDialog("Статистика владельца · ${page+1}/$count",onClose,maxWidth=840.dp) {
        Text("Предпросмотр не отправляет сообщения в Telegram.",style=MaterialTheme.typography.bodySmall)
        error?.let {MposNotice(it,true)}
        if(image==null && error==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        image?.let {bitmap->Box(Modifier.weight(1f,fill=false).heightIn(max=650.dp).verticalScroll(rememberScrollState())) {
            Image(bitmap.asImageBitmap(),"Коммерческая аналитика",Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height).testTag("ownerAnalyticsImage"))
        }}
        Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Предыдущая",{page--},Modifier.weight(1f),enabled=page>0,style=ActionStyle.Outline)
            MposButton("Закрыть",onClose,Modifier.weight(1f),style=ActionStyle.Outline)
            MposButton("Следующая",{page++},Modifier.weight(1f),enabled=page+1<count,style=ActionStyle.Outline)
        }
    }
}
