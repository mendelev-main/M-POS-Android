package com.mendelev.mpos.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.mendelev.mpos.data.media.ProductPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Same bounded, sampled private thumbnail in catalog, editor and workspace. */
@Composable fun ProductPhoto(id: String?, store: ProductPhotoStore?, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null,id,store) {
        value=if(id==null) null else withContext(Dispatchers.IO) { store?.read(id) }
    }
    if(id!=null) {
        val image=bitmap
        if(image!=null) Image(image.asImageBitmap(),"Фото товара",modifier.testTag("productPhoto"),contentScale=ContentScale.Crop)
        else Text("Фото недоступно",modifier,style=MaterialTheme.typography.bodySmall)
    }
}
