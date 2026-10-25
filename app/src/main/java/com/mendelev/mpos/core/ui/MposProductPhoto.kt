package com.mendelev.mpos.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import com.mendelev.mpos.data.media.ProductPhotoStore

/** One shared bounded memory cache; no network module or duplicate disk cache of private files. */
@Composable fun ProductPhoto(id: String?,store: ProductPhotoStore?,modifier: Modifier = Modifier) {
    val context=LocalContext.current
    val request=remember(id,store,context) {
        if(id==null || store==null) null else runCatching {
            ImageRequest.Builder(context).data(store.file(id)).size(256).diskCachePolicy(CachePolicy.DISABLED).build()
        }.getOrNull()
    }
    var failed by remember(id,store) {mutableStateOf(false)}
    var loaded by remember(id,store) {mutableStateOf(false)}
    if(id!=null) Box(modifier) {
        if(request!=null) AsyncImage(model=request,contentDescription="Фото товара",contentScale=ContentScale.Crop,
            modifier=Modifier.fillMaxSize().then(if(loaded) Modifier.testTag("productPhoto") else Modifier),
            onLoading={loaded=false;failed=false},onSuccess={loaded=true;failed=false},onError={loaded=false;failed=true})
        if(request==null || failed) Text("Фото недоступно",style=MaterialTheme.typography.bodySmall)
    }
}
