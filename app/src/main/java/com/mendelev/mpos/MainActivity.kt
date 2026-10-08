package com.mendelev.mpos

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
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
import com.mendelev.mpos.backup.MPosBackupManager
import com.mendelev.mpos.payment.MPosSplitCashDialog
import com.mendelev.mpos.payment.MPosCardConfirmationDialog
import com.mendelev.mpos.bridge.NativeBridgeRouter
import com.mendelev.mpos.data.MPosDatabase
import com.mendelev.mpos.data.MPosStorageMirror
import com.mendelev.mpos.data.MPosRootSessionOwner
import com.mendelev.mpos.diagnostics.MPosDiagnosticBreadcrumbStore
import com.mendelev.mpos.diagnostics.MPosDiagnosticExporter
import com.mendelev.mpos.media.ProductImageStore
import com.mendelev.mpos.network.MPosNetworkTransport
import com.mendelev.mpos.media.ProductPhotoManager
import com.mendelev.mpos.print.EscPosPrinter
import com.mendelev.mpos.print.MPosPrintService
import com.mendelev.mpos.share.ReportShareManager
import com.mendelev.mpos.shift.MPosShiftScreenController
import com.mendelev.mpos.shift.MPosCashMovementDialog
import com.mendelev.mpos.shift.MPosShiftCloseDialog
import com.mendelev.mpos.shift.MPosShiftOpenDialog
import com.mendelev.mpos.workspace.MPosWorkspaceController
import com.mendelev.mpos.settings.MPosSettingsStore
import com.mendelev.mpos.settings.MPosSettingsScreenController
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

    private lateinit var cashInputDialog: MPosSplitCashDialog
    private lateinit var splitCashDialog: MPosSplitCashDialog
    private lateinit var cardConfirmationDialog: MPosCardConfirmationDialog
    private lateinit var shiftOpenDialog: MPosShiftOpenDialog
    private lateinit var shiftCloseDialog: MPosShiftCloseDialog
    private lateinit var cashMovementDialog: MPosCashMovementDialog
    private lateinit var workspace: MPosWorkspaceController
    private lateinit var settingsScreen: MPosSettingsScreenController
    private lateinit var shiftScreen: MPosShiftScreenController
    private lateinit var webView: WebView
    private lateinit var imageStore: ProductImageStore
    private lateinit var photos: ProductPhotoManager
    private lateinit var backup: MPosBackupManager
    private lateinit var router: NativeBridgeRouter
    private lateinit var printer: MPosPrintService
    private lateinit var shares: ReportShareManager
    private lateinit var telegram: TelegramClient
    private lateinit var nativeSettings: MPosSettingsStore
    private lateinit var nativeStorageMirror: MPosStorageMirror
    private lateinit var loyaltyAuthorization: com.mendelev.mpos.employee.MPosEmployeeAuthorizationDialog
    private lateinit var editorAuthorization: com.mendelev.mpos.employee.MPosEmployeeAuthorizationDialog
    private lateinit var catalogAuthorization: com.mendelev.mpos.employee.MPosEmployeeAuthorizationDialog
    private lateinit var employeeAuthorization: com.mendelev.mpos.employee.MPosEmployeeAuthorizationDialog
    private lateinit var rootSession: MPosRootSessionOwner
    private lateinit var nativeNetworkTransport: MPosNetworkTransport
    private lateinit var diagnostics: MPosDiagnosticBreadcrumbStore
    private var diagnosticExportPending = false

    private val diagnosticCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        diagnosticExportPending = false
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = MPosDiagnosticExporter(contentResolver, diagnostics).write(uri)
                nativeMessage(if (ok) "Диагностика сохранена" else "Не удалось сохранить диагностику")
            }
        }
    }

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

        diagnostics = MPosDiagnosticBreadcrumbStore(this)
        diagnosticExportPending = savedInstanceState?.getBoolean("mposDiagnosticExportPending") ?: false
        diagnostics.record("lifecycle", "created")
        imageStore = ProductImageStore(this)
        photos = ProductPhotoManager(this, imageStore)
        backup = MPosBackupManager(this, imageStore)
        val printTransport = EscPosPrinter(::printerEvent)
        printer = MPosPrintService(MPosDatabase.get(this), lifecycleScope, ::printerEvent, printTransport::send, printTransport::close, printTransport::notifySound)
        shares = ReportShareManager(this)
        telegram = TelegramClient(shares::createWarehousePdf, ::telegramResult, ::telegramMonthlyResult, ::telegramShiftResult, ::telegramTestResult)
        nativeSettings = MPosSettingsStore(this, lifecycleScope, ::nativeSettingsResult)
        rootSession = MPosRootSessionOwner(MPosDatabase.get(this), lifecycleScope)
        nativeStorageMirror = MPosStorageMirror(MPosDatabase.get(this), lifecycleScope, rootSession::bootstrap, ::nativeStorageResult)
        nativeNetworkTransport = MPosNetworkTransport(lifecycleScope, ::nativeNetworkResult, ::nativeNetworkEvent, MPosDatabase.get(this))
        employeeAuthorization = com.mendelev.mpos.employee.MPosEmployeeAuthorizationDialog(this, nativeStorageMirror::commitEmployee, { result ->
            callJavaScript("window.__mposEmployeeCommandResult&&window.__mposEmployeeCommandResult(${com.mendelev.mpos.data.MPosBridgeJson.serialize(result)});")
        })
        catalogAuthorization = com.mendelev.mpos.employee.MPosEmployeeAuthorizationDialog(this, nativeStorageMirror::commitCatalogDelete,
            { result -> callJavaScript("window.__mposCatalogDeleteResult&&window.__mposCatalogDeleteResult(${com.mendelev.mpos.data.MPosBridgeJson.serialize(result)});") },
            bridgeAction = "catalogDeleteAuthorize", operations = setOf("delete"), commitPrefix = "native-catalog-delete-",
            description = { "Удаление элемента каталога. Это действие нельзя отменить." },
            requiresPassword = { it.optBoolean("passwordRequired", true) }, resultKeys = setOf("products", "layout", "posNavigation"))
        editorAuthorization = com.mendelev.mpos.employee.MPosEmployeeAuthorizationDialog(this, nativeStorageMirror::authorizeEditor,
            { result -> callJavaScript("window.__mposEditorAuthorizationResult&&window.__mposEditorAuthorizationResult(${com.mendelev.mpos.data.MPosBridgeJson.serialize(result)});") },
            bridgeAction = "editorAuthorize", operations = setOf("stock", "no-stock"), commitPrefix = "native-editor-authorize-",
            description = { if (it.getString("operation") == "stock") "Для изменения остатка требуется пароль администратора." else "Для изменения учёта остатков требуется пароль администратора." },
            resultKeys = setOf("granted", "operation", "productId", "grant"))
        val loyaltyAdjustmentHttp = com.mendelev.mpos.network.MPosLoyaltyAdjustmentHttp()
        loyaltyAuthorization = com.mendelev.mpos.employee.MPosEmployeeAuthorizationDialog(this,
            { input, credential ->
                val id = input.getString("requestId")
                fun reply(ok: Boolean, message: String = "") = runOnUiThread {
                    if (::loyaltyAuthorization.isInitialized) loyaltyAuthorization.result(JSONObject().put("requestId", id).put("ok", ok).put("message", message))
                }
                nativeStorageMirror.prepareLoyaltyAdjustment(input, { prepared ->
                    runOnUiThread {
                        if (loyaltyAuthorization.isPending(id)) lifecycleScope.launch {
                            suspend fun confirmed(){
                                com.mendelev.mpos.data.MPosLoyaltyAdjustmentVerification(com.mendelev.mpos.data.MPosDatabase.get(this@MainActivity))
                                    .finish(prepared,prepared.getString("customerId"),prepared.getJSONObject("body").get("programId"),prepared.getString("verificationToken"))
                            }
                            try { loyaltyAdjustmentHttp.execute(prepared, credential); confirmed(); reply(true) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: java.io.InterruptedIOException) { reply(false, "Сервер не ответил вовремя") }
                            catch (error: IllegalStateException) {
                                if (error is com.mendelev.mpos.network.MPosLoyaltyAdjustmentHttp.Rejected && error.status in 400..499 && error.status != 408 && runCatching { confirmed() }.isSuccess) reply(false, error.message ?: "Не удалось сохранить корректировку")
                                else reply(false, "Перед повторной корректировкой проверьте актуальный баланс клиента")
                            }
                            catch (_: Exception) { reply(false, "Не удалось сохранить корректировку") }
                        }
                    }
                }, { message -> reply(false, message) })
            },
            { result -> callJavaScript("window.__mposLoyaltyAuthorizationResult&&window.__mposLoyaltyAuthorizationResult(${com.mendelev.mpos.data.MPosBridgeJson.serialize(result)});") },
            bridgeAction = "loyaltyAdjustAuthorize", operations = setOf("adjust"), commitPrefix = "native-loyalty-adjust-",
            description = { "Для корректировки лояльности требуется пароль администратора. Его проверит сервер." })
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
        val root = FrameLayout(this)
        root.addView(webView)
        shiftScreen = MPosShiftScreenController(this, root, nativeStorageMirror::handle) { action ->
            callJavaScript("window.__mposShiftScreenAction&&window.__mposShiftScreenAction($action);")
        }
        lifecycleScope.launch {
            var rootRevision: Long? = null
            rootSession.state.collect { state ->
                if (state is MPosRootSessionOwner.State.Ready) {
                    if (rootRevision != state.revision) {
                        rootRevision = state.revision
                        shiftScreen.rootSessionChanged()
                    }
                } else if (state == MPosRootSessionOwner.State.Failed || state == MPosRootSessionOwner.State.AwaitingMigration) {
                    rootRevision = null
                    shiftScreen.rootSessionChanged()
                }
            }
        }
        workspace = MPosWorkspaceController(this, root) { action ->
            val serialized = com.mendelev.mpos.data.MPosBridgeJson.serialize(action)
            callJavaScript("window.__nativeWorkspaceAction&&window.__nativeWorkspaceAction($serialized);")
        }
        settingsScreen = MPosSettingsScreenController(this, root) { action ->
            val serialized = com.mendelev.mpos.data.MPosBridgeJson.serialize(action)
            callJavaScript("window.__nativeSettingsAction&&window.__nativeSettingsAction($serialized);")
        }
        cashMovementDialog = MPosCashMovementDialog(this) { action ->
            callJavaScript("window.MPosCore&&window.MPosCore.NativeCashForms&&window.MPosCore.NativeCashForms.handleAction($action);")
        }
        shiftCloseDialog = MPosShiftCloseDialog(this, nativeStorageMirror::handle) { action ->
            callJavaScript("window.MPosCore&&window.MPosCore.NativeCloseForm&&window.MPosCore.NativeCloseForm.handleAction($action);")
        }
        shiftOpenDialog = MPosShiftOpenDialog(this, nativeStorageMirror::handle, { action ->
            val serialized = com.mendelev.mpos.data.MPosBridgeJson.serialize(action)
            callJavaScript("window.MPosCore&&window.MPosCore.NativeOpenForm&&window.MPosCore.NativeOpenForm.handleAction($serialized);")
        }, nativeStorageMirror::openShift)
        cardConfirmationDialog = MPosCardConfirmationDialog(this) { action ->
            callJavaScript("window.MPosCore&&window.MPosCore.NativeCardConfirmation&&window.MPosCore.NativeCardConfirmation.handleAction($action);")
        }
        splitCashDialog = MPosSplitCashDialog(this) { action ->
            callJavaScript("window.MPosCore&&window.MPosCore.NativeSplitCash&&window.MPosCore.NativeSplitCash.handleAction($action);")
        }
        cashInputDialog = MPosSplitCashDialog(this) { action ->
            callJavaScript("window.MPosCore&&window.MPosCore.NativeCashPayment&&window.MPosCore.NativeCashPayment.handleAction($action);")
        }
        setContentView(root)

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
                if (::settingsScreen.isInitialized && settingsScreen.consumeBack()) return
                webView.evaluateJavascript(
                    "(()=>{if(window._pendingBackupImport){cancelBackupImport();return true}if(document.querySelector('.modal-overlay')){closeModal();return true}if(document.getElementById('warehouse-root')?.children.length){closeWarehousePage();return true}if(document.getElementById('receiving-page-root')?.children.length){finishReceivingPage();return true}return false})()"
                ) { handled -> if (handled != "true") moveTaskToBack(true) }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        diagnostics.record("lifecycle", "foreground")
        if (::rootSession.isInitialized) rootSession.refresh()
        if (::nativeStorageMirror.isInitialized) nativeStorageMirror.handle(JSONObject()
            .put("action", "rootSessionRefresh").put("requestId", "native-root-foreground"))
        nativeNetworkTransport.onForeground()
        if (::webView.isInitialized) callJavaScript("window._availabilityAppActive=true;window.onAvailabilityAppState&&window.onAvailabilityAppState(true);")
    }

    override fun onPause() {
        diagnostics.record("lifecycle", "background")
        nativeNetworkTransport.onBackground()
        if (::webView.isInitialized) callJavaScript("window._availabilityAppActive=false;window.onAvailabilityAppState&&window.onAvailabilityAppState(false);")
        super.onPause()
    }

    override fun onDestroy() {
        if (::rootSession.isInitialized) rootSession.close()
        if (::employeeAuthorization.isInitialized) employeeAuthorization.close()
        if (::catalogAuthorization.isInitialized) catalogAuthorization.close()
        if (::editorAuthorization.isInitialized) editorAuthorization.close()
        if (::loyaltyAuthorization.isInitialized) loyaltyAuthorization.close()
        if (::shiftScreen.isInitialized) shiftScreen.hide()
        if (::workspace.isInitialized) workspace.hide()
        if (::settingsScreen.isInitialized) settingsScreen.dismiss()
        if (::cashInputDialog.isInitialized) cashInputDialog.dismiss()
        if (::splitCashDialog.isInitialized) splitCashDialog.dismiss()
        if (::cardConfirmationDialog.isInitialized) cardConfirmationDialog.dismiss()
        if (::shiftOpenDialog.isInitialized) shiftOpenDialog.dismiss()
        if (::shiftCloseDialog.isInitialized) shiftCloseDialog.dismiss()
        if (::cashMovementDialog.isInitialized) cashMovementDialog.dismiss()
        if (::printer.isInitialized) printer.close()
        nativeNetworkTransport.close()
        if (::nativeStorageMirror.isInitialized) nativeStorageMirror.close()
        if (::nativeSettings.isInitialized) nativeSettings.close()
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

    fun exportDiagnostics() = runOnUiThread {
        if (!diagnosticExportPending) {
            diagnosticExportPending = true
            runCatching { diagnosticCreator.launch("M-POS-diagnostics.json") }.onFailure {
                diagnosticExportPending = false
                nativeMessage("Не удалось открыть сохранение диагностики")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("mposDiagnosticExportPending", diagnosticExportPending)
        super.onSaveInstanceState(outState)
    }

    fun callJavaScript(script: String, onError: (() -> Unit)? = null) = runOnUiThread {
        runCatching { webView.evaluateJavascript(script, null) }.onFailure { onError?.invoke() }
    }

    fun nativeMessage(message: String) = callJavaScript("window.flash&&window.flash(${JSONObject.quote(message)});")

    fun handlePrinter(payload: JSONObject) {
        when (payload.optString("action")) {
            "print", "routePrint" -> printer.handle(payload)
            "status" -> printer.ready()
            "shareWarehouseReport" -> payload.optJSONObject("report")?.let(shares::warehousePdf)
            "shareWarehouseExcel" -> payload.optJSONObject("report")?.let(shares::warehouseExcel)
            "sharePurchaseOrder" -> payload.optJSONObject("order")?.let(shares::purchaseOrder)
            "printShiftReport" -> payload.optJSONObject("report")?.let(shares::printShiftReport)
        }
    }

    fun handlePaymentScreen(payload: JSONObject) = runOnUiThread {
        if (payload.optString("action").startsWith("input")) {
            if (::cashInputDialog.isInitialized) cashInputDialog.handle(payload)
        } else if (payload.optString("action").startsWith("cash")) {
            if (::splitCashDialog.isInitialized) splitCashDialog.handle(payload)
        } else if (::cardConfirmationDialog.isInitialized) cardConfirmationDialog.handle(payload)
    }

    fun handleWorkspace(payload: JSONObject) = runOnUiThread {
        if (::workspace.isInitialized) workspace.handle(payload)
    }

    fun handleSettingsScreen(payload: JSONObject) = runOnUiThread {
        if (payload.optString("action") == "loyaltyAdjustAuthorize") {
            if (::loyaltyAuthorization.isInitialized) loyaltyAuthorization.handle(payload)
        } else if (payload.optString("action") == "editorAuthorize") {
            if (::editorAuthorization.isInitialized) editorAuthorization.handle(payload)
        } else if (payload.optString("action") == "catalogDeleteAuthorize") {
            if (::catalogAuthorization.isInitialized) catalogAuthorization.handle(payload)
        } else if (payload.optString("action") == "employeeAuthorize") {
            if (::employeeAuthorization.isInitialized) employeeAuthorization.handle(payload)
        } else if (::settingsScreen.isInitialized) settingsScreen.handle(payload)
    }

    fun handleShiftScreen(payload: JSONObject) = runOnUiThread {
        if (payload.optString("action").startsWith("openForm")) {
            if (::shiftOpenDialog.isInitialized) shiftOpenDialog.handle(payload)
        } else if (payload.optString("action").startsWith("closeForm")) {
            if (::shiftCloseDialog.isInitialized) shiftCloseDialog.handle(payload)
        } else if (payload.optString("action").startsWith("cashForm")) {
            if (::cashMovementDialog.isInitialized) cashMovementDialog.handle(payload)
        } else if (::shiftScreen.isInitialized) shiftScreen.handle(payload)
    }

    fun handleTelegram(payload: JSONObject) = telegram.handle(payload)

    fun recreateAfterRendererExit() = runOnUiThread {
        diagnostics.record("lifecycle", "renderer-exit")
        nativeMessage("WebView был перезапущен. Локальные данные сохранены")
        recreate()
    }

    private fun printerEvent(event: JSONObject) {
        diagnostics.record("printer", event.optString("status", event.optString("type", "event")), event.optString("type") != "printError")
        callJavaScript("window.__nativePrinterEvent&&window.__nativePrinterEvent($event);")
    }
    private fun telegramResult(ok: Boolean, message: String) = callJavaScript("window.onTelegramResult&&window.onTelegramResult({ok:$ok,message:${JSONObject.quote(message)}});")
    private fun telegramTestResult(result: JSONObject) = callJavaScript("window.__nativeConnectionTestResult&&window.__nativeConnectionTestResult($result);")
    private fun telegramMonthlyResult(result: JSONObject) = callJavaScript("window.onTelegramMonthlyWarehouseResult&&window.onTelegramMonthlyWarehouseResult($result);")
    private fun telegramShiftResult(ok: Boolean, message: String) {
        val result = JSONObject().put("ok", ok).put("message", message)
        callJavaScript("window.onTelegramShiftClosedResult?window.onTelegramShiftClosedResult($result):(window.flash&&window.flash(${JSONObject.quote(message)}));")
    }
    private fun nativeSettingsResult(result: JSONObject) = callJavaScript("window.__nativeSettingsResult&&window.__nativeSettingsResult($result);")
    private fun nativeStorageResult(result: JSONObject) {
        if (result.has("rootSession") && result.optString("requestId").startsWith("native-")) {
            val model = com.mendelev.mpos.data.MPosBridgeJson.serialize(result)
            callJavaScript("window.MPosCore?.RootSession?.receive(($model).rootSession,($model).rootSequence);")
        }
        diagnostics.record("storage", "result", result.optBoolean("ok", false) && result.optBoolean("projectionOk", true))
        if (result.optString("requestId").startsWith("native-editor-authorize-")) {
            runOnUiThread { if (::editorAuthorization.isInitialized) editorAuthorization.result(result) }
        } else if (result.optString("requestId").startsWith("native-catalog-delete-")) {
            runOnUiThread { if (::catalogAuthorization.isInitialized) catalogAuthorization.result(result) }
        } else if (result.optString("requestId").startsWith("native-employee-commit-")) {
            runOnUiThread { if (::employeeAuthorization.isInitialized) employeeAuthorization.result(result) }
        } else if (result.optString("requestId").startsWith("native-shift-open-")) {
            runOnUiThread { if (::shiftOpenDialog.isInitialized) shiftOpenDialog.result(result) }
        } else if (result.optString("requestId").startsWith("native-shift-close-")) {
            runOnUiThread { if (::shiftCloseDialog.isInitialized) shiftCloseDialog.result(result) }
        } else if (result.optString("requestId").startsWith("native-shift-screen-")) {
            runOnUiThread { if (::shiftScreen.isInitialized) shiftScreen.result(result) }
        } else callJavaScript("window.__nativeStorageResult&&window.__nativeStorageResult(${com.mendelev.mpos.data.MPosBridgeJson.serialize(result)});")
    }
    private fun nativeNetworkResult(result: JSONObject) {
        diagnostics.record("network", "result", result.optBoolean("ok", false))
        callJavaScript("window.__nativeNetworkResult&&window.__nativeNetworkResult(${com.mendelev.mpos.data.MPosBridgeJson.serialize(result)});")
    }
    private fun nativeNetworkEvent(event: JSONObject) {
        diagnostics.record("network", event.optString("state", event.optString("type", "event")))
        callJavaScript("window.__nativeNetworkEvent&&window.__nativeNetworkEvent(${com.mendelev.mpos.data.MPosBridgeJson.serialize(event)});")
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
