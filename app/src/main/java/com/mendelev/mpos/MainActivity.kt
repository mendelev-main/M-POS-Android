package com.mendelev.mpos

import android.os.Bundle
import android.content.Context.MODE_PRIVATE
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.mendelev.mpos.update.UpdatePolicy
import com.mendelev.mpos.update.UpdateRepository
import com.mendelev.mpos.update.UpdateViewModel
import com.mendelev.mpos.update.UpdatesScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.safeDrawingPadding

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Test fixture override is restricted to loopback/emulator host in DEBUG; release never accepts an endpoint from Intent.
        val fixture = if (BuildConfig.DEBUG) intent.getStringExtra("mpos.testUpdateUrl")
            ?.takeIf(UpdatePolicy::isLocalUrl) else null
        setContent { FoundationScreen(fixture) }
    }
}

@Composable
fun FoundationScreen(fixtureUrl: String? = null) {
    var route by rememberSaveable { mutableStateOf("catalog") }
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("debugUpdateFixture", MODE_PRIVATE)
    val fixture = if (BuildConfig.DEBUG) fixtureUrl ?: preferences.getString("url", null) else null
    if (BuildConfig.DEBUG && fixtureUrl != null) preferences.edit().putString("url", fixtureUrl).apply()
    BackHandler(enabled = route != "catalog") { route = "catalog" }
    com.mendelev.mpos.core.ui.MposTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (route) {
                "settings" -> {
                    val model: UpdateViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            UpdateViewModel(UpdateRepository(context.applicationContext, fixture)) as T
                    })
                    UpdatesScreen(model) { route = "catalog" }
                }
                "gallery" -> com.mendelev.mpos.feature.products.ComponentGallery { route = "catalog" }
                else -> {
                    val model: com.mendelev.mpos.feature.products.CatalogViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            com.mendelev.mpos.feature.products.CatalogViewModel(com.mendelev.mpos.data.local.CatalogRepository(
                                com.mendelev.mpos.data.local.CatalogDatabase.get(context.applicationContext))) as T
                    })
                    com.mendelev.mpos.feature.products.CatalogScreen(model, { route = "settings" }, { route = "gallery" })
                }
            }
        }
    }
}
