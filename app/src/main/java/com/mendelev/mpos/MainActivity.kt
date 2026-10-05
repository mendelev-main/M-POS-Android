package com.mendelev.mpos

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mendelev.mpos.backup.BackupManager
import com.mendelev.mpos.bridge.NativeBridgeRouter
import com.mendelev.mpos.data.MPosDatabase
import com.mendelev.mpos.data.NativeStorageMirror
import com.mendelev.mpos.media.ProductImageStore
import com.mendelev.mpos.network.NativeNetworkTransport
import com.mendelev.mpos.media.ProductPhotoManager
import com.mendelev.mpos.print.EscPosPrinter
import com.mendelev.mpos.share.ReportShareManager
import com.mendelev.mpos.settings.NativeSettingsStore
import com.mendelev.mpos.telegram.TelegramClient
import com.mendelev.mpos.web.LocalContentWebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        const val APP_HOST = "appassets.androidplatform.net"
        const val APP_ORIGIN = "https://$APP_HOST"
        private const val START_URL = "$APP_ORIGIN/assets/pos/pos.html"
    }

    private lateinit var webView: WebView
    private lateinit var imageStore: ProductImageStore
    private lateinit var photos: ProductPhotoManager
    private lateinit var backup: BackupManager
    private lateinit var router: NativeBridgeRouter
    private lateinit var printer: EscPosPrinter
    private lateinit var shares: ReportShareManager
    private lateinit var telegram: TelegramClient
    private lateinit var nativeSettings: NativeSettingsStore
    private lateinit var nativeStorageMirror: NativeStorageMirror
    private lateinit var nativeNetworkTransport: NativeNetworkTransport

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                .onSuccess(photos::accept)
                .onFailure { nativeMessage("Не удалось открыть фотографию") }
        }
    }
    private val backupCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { lifecycleScope.launch(Dispatchers.IO) { backup.writeExport(it) } }
    }
    private val backupPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { lifecycleScope.launch(Dispatchers.IO) { backup.import(it) } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        imageStore = ProductImageStore(this)
        photos = ProductPhotoManager(this, imageStore)
        backup = BackupManager(this, imageStore)
        printer = EscPosPrinter(::printerEvent)
        shares = ReportShareManager(this)
        telegram = TelegramClient(shares::createWarehousePdf, ::telegramResult, ::telegramMonthlyResult)
        nativeSettings = NativeSettingsStore(this, ::nativeSettingsResult)
        nativeStorageMirror = NativeStorageMirror(MPosDatabase.get(this), lifecycleScope, ::nativeStorageResult)
        nativeNetworkTransport = NativeNetworkTransport(lifecycleScope, ::nativeNetworkResult, ::nativeNetworkEvent)
        router = NativeBridgeRouter(this, photos, backup, nativeSettings, nativeStorageMirror, nativeNetworkTransport)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.rgb(18, 18, 18))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = false
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webChromeClient = WebChromeClient()
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        val loader = WebViewAssetLoader.Builder()
            .setDomain(APP_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.webViewClient = LocalContentWebViewClient(this, loader, imageStore)
        setContentView(webView)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "MPosNative", setOf(APP_ORIGIN)) { _, message, sourceOrigin, isMainFrame, _ ->
                if (isMainFrame && sourceOrigin.scheme == "https" && sourceOrigin.host == APP_HOST) router.receive(message.data ?: "")
            }
        } else {
            throw IllegalStateException("Android System WebView не поддерживает безопасный bridge")
        }
        webView.loadUrl(START_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(()=>{if(window._pendingBackupImport){cancelBackupImport();return true}if(document.querySelector('.modal-overlay')){closeModal();return true}if(document.getElementById('warehouse-root')?.children.length){closeWarehousePage();return true}if(document.getElementById('receiving-page-root')?.children.length){finishReceivingPage();return true}return false})()"
                ) { handled -> if (handled != "true") moveTaskToBack(true) }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (::webView.isInitialized) callJavaScript("window._availabilityAppActive=true;window.onAvailabilityAppState&&window.onAvailabilityAppState(true);")
    }

    override fun onPause() {
        if (::webView.isInitialized) callJavaScript("window._availabilityAppActive=false;window.onAvailabilityAppState&&window.onAvailabilityAppState(false);")
        super.onPause()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    fun pickProductPhoto() = runOnUiThread {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun createBackupFile(name: String) = runOnUiThread { backupCreator.launch(name) }
    fun chooseBackupFile() = runOnUiThread { backupPicker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }

    fun callJavaScript(script: String, onError: (() -> Unit)? = null) = runOnUiThread {
        runCatching { webView.evaluateJavascript(script, null) }.onFailure { onError?.invoke() }
    }

    fun nativeMessage(message: String) = callJavaScript("window.flash&&window.flash(${JSONObject.quote(message)});")

    fun handlePrinter(payload: JSONObject) {
        when (payload.optString("action")) {
            "print" -> printer.handle(payload)
            "status" -> printer.ready()
            "shareWarehouseReport" -> payload.optJSONObject("report")?.let(shares::warehousePdf)
            "shareWarehouseExcel" -> payload.optJSONObject("report")?.let(shares::warehouseExcel)
            "sharePurchaseOrder" -> payload.optJSONObject("order")?.let(shares::purchaseOrder)
            "printShiftReport" -> payload.optJSONObject("report")?.let(shares::printShiftReport)
        }
    }

    fun handleTelegram(payload: JSONObject) = telegram.handle(payload)

    fun recreateAfterRendererExit() = runOnUiThread {
        nativeMessage("WebView был перезапущен. Локальные данные сохранены")
        recreate()
    }

    private fun printerEvent(event: JSONObject) = callJavaScript("window.__nativePrinterEvent&&window.__nativePrinterEvent($event);")
    private fun telegramResult(ok: Boolean, message: String) = callJavaScript("window.handleTelegramResult&&window.handleTelegramResult({ok:$ok,message:${JSONObject.quote(message)}});")
    private fun telegramMonthlyResult(result: JSONObject) = callJavaScript("window.onTelegramMonthlyWarehouseResult&&window.onTelegramMonthlyWarehouseResult($result);")
    private fun nativeSettingsResult(result: JSONObject) = callJavaScript("window.__nativeSettingsResult&&window.__nativeSettingsResult($result);")
    private fun nativeStorageResult(result: JSONObject) = callJavaScript("window.__nativeStorageResult&&window.__nativeStorageResult($result);")
    private fun nativeNetworkResult(result: JSONObject) = callJavaScript("window.__nativeNetworkResult&&window.__nativeNetworkResult($result);")
    private fun nativeNetworkEvent(event: JSONObject) = callJavaScript("window.__nativeNetworkEvent&&window.__nativeNetworkEvent($event);")

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
