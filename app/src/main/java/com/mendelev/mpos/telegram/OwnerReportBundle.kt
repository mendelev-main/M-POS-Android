package com.mendelev.mpos.telegram

enum class OwnerBundleFormat { PHOTO, ALBUM, PDF }
object OwnerReportBundle {
    fun format(pages: Int): OwnerBundleFormat {
        require(pages>0)
        return when(pages) {1->OwnerBundleFormat.PHOTO;in 2..10->OwnerBundleFormat.ALBUM;else->OwnerBundleFormat.PDF}
    }
    fun message(caption: String,pages: Int,png: (Int)->ByteArray,pdf: ()->ByteArray): TelegramMessage=when(format(pages)) {
        OwnerBundleFormat.PHOTO->TelegramMessage(caption,TelegramAttachment("analytics.png","image/png",png(0)))
        OwnerBundleFormat.ALBUM->TelegramMessage(caption,album=(0 until pages).map {TelegramAttachment("analytics-${it+1}.png","image/png",png(it))})
        OwnerBundleFormat.PDF->TelegramMessage(caption,TelegramAttachment("analytics.pdf","application/pdf",pdf().also {require(it.size in 1..50_000_000) {"PDF превышает ограничение Telegram"}}))
    }
}
