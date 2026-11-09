package com.mendelev.mpos

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache

/** Shared offline thumbnail loader. Files are immutable UUIDs owned by ProductPhotoStore. */
class MposApplication: Application(), SingletonImageLoader.Factory {
    override fun onCreate() {super.onCreate();com.mendelev.mpos.data.recovery.AppDataset.initialize(this);com.mendelev.mpos.telegram.TelegramRuntime.get(this)}
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {MemoryCache.Builder().maxSizeBytes(16L*1024*1024).build()}
        .diskCache(null)
        .build()
}
