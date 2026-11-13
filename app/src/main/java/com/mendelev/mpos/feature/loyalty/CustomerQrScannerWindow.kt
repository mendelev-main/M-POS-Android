package com.mendelev.mpos.feature.loyalty

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.*
import com.mendelev.mpos.core.ui.*

/** Camera exists only while this window is visible; no frames or QR values are stored. */
@Composable internal fun CustomerQrScannerWindow(onClose: ()->Unit,onResult: (String)->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var permitted by remember {mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    val cameraAvailable=remember {context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)}
    var error by remember {mutableStateOf<String?>(null)}
    var finished by remember {mutableStateOf(false)}
    val result by rememberUpdatedState(onResult)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted=it;error=if(it) null else "Разрешите доступ к камере или найдите клиента по номеру телефона."
    }
    val view=remember(context) {
        DecoratedBarcodeView(context).apply {
            statusView.text=""
            barcodeView.decoderFactory=DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            barcodeView.addStateListener(object: CameraPreview.StateListener {
                override fun previewSized()=Unit
                override fun previewStarted()=Unit
                override fun previewStopped()=Unit
                override fun cameraClosed()=Unit
                override fun cameraError(cause: Exception?) {
                    error="Не удалось открыть камеру. Найдите клиента по номеру телефона или повторите сканирование."
                }
            })
        }
    }
    fun stop() {view.barcodeView.pauseAndWait()}
    DisposableEffect(view,owner,permitted) {
        fun resume() {
            if(cameraAvailable && permitted && !finished && ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) {
                try {view.resume()} catch(_:Exception) {error="Не удалось открыть камеру. Закройте другие приложения с камерой и повторите."}
            }
        }
        val observer=LifecycleEventObserver {_,event->
            when(event) {Lifecycle.Event.ON_RESUME->resume();Lifecycle.Event.ON_PAUSE->view.pause();else->Unit}
        }
        owner.lifecycle.addObserver(observer)
        view.decodeSingle(object: BarcodeCallback {
            override fun barcodeResult(value: BarcodeResult) {
                if(finished) return
                finished=true
                stop()
                result(value.text)
            }
            override fun possibleResultPoints(points: MutableList<ResultPoint>?)=Unit
        })
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {owner.lifecycle.removeObserver(observer);stop()}
    }
    LaunchedEffect(Unit) {if(!cameraAvailable) error="Камера недоступна. Найдите клиента по номеру телефона." else if(!permitted) permission.launch(Manifest.permission.CAMERA)}
    MposDialog("Сканировать QR",{stop();onClose()},maxWidth=600.dp) {
        Text("Наведите камеру на QR-код клиента в личном кабинете.")
        if(cameraAvailable && permitted) AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(300.dp))
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(cameraAvailable && !permitted) MposButton("Разрешить камеру",{permission.launch(Manifest.permission.CAMERA)},Modifier.fillMaxWidth(),style=ActionStyle.Outline)
        MposButton("Отмена",{stop();onClose()},Modifier.fillMaxWidth(),style=ActionStyle.Secondary)
    }
}
