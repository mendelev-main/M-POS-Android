package com.mendelev.mpos

import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
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
    val shiftModel: com.mendelev.mpos.feature.shift.ShiftViewModel = viewModel(factory=object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T: ViewModel> create(modelClass: Class<T>): T = com.mendelev.mpos.feature.shift.ShiftViewModel(
            com.mendelev.mpos.data.local.ShiftRepository(com.mendelev.mpos.data.local.CatalogDatabase.get(context.applicationContext))) as T
    })
    val shiftUi by shiftModel.state.collectAsStateWithLifecycle()
    val navigation = MposTopNavigation(route, shiftUi.snapshot?.active != null,
        shiftUi.loading, shiftUi.fatal, shiftUi.pending, shiftUi.snapshot?.active?.actorName ?: "Локальный владелец") { route = it }
    com.mendelev.mpos.core.ui.MposTheme {
        CompositionLocalProvider(LocalMposTopNavigation provides navigation) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (route) {
                    "settings" -> {
                        val model: UpdateViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                UpdateViewModel(UpdateRepository(context.applicationContext, fixture)) as T
                        })
                        UpdatesScreen(model, onCatalog={route="catalog"}, onGallery={route="gallery"},onSuppliers={route="suppliers"}) { route = "catalog" }
                    }
                    "suppliers" -> {
                        val model: com.mendelev.mpos.feature.suppliers.SupplierViewModel = viewModel(factory=object: ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T: ViewModel> create(modelClass: Class<T>): T = com.mendelev.mpos.feature.suppliers.SupplierViewModel(
                                com.mendelev.mpos.data.local.SupplierRepository(com.mendelev.mpos.data.local.CatalogDatabase.get(context.applicationContext))) as T
                        })
                        com.mendelev.mpos.feature.suppliers.SupplierScreen(model) {route="settings"}
                    }
                    "shift" -> {
                        com.mendelev.mpos.feature.shift.ShiftScreen(shiftModel,{route="catalog"},{route="workspace"},{route="settings"})
                    }
                    "checkout", "history" -> {
                        val model: com.mendelev.mpos.feature.sale.SaleViewModel = viewModel(factory=object: ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T: ViewModel> create(modelClass: Class<T>): T = com.mendelev.mpos.feature.sale.SaleViewModel(
                                com.mendelev.mpos.data.local.CatalogDatabase.get(context.applicationContext)) as T
                        })
                        val refunds: com.mendelev.mpos.feature.sale.RefundViewModel = viewModel(factory=object: ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T: ViewModel> create(modelClass: Class<T>): T = com.mendelev.mpos.feature.sale.RefundViewModel(
                                com.mendelev.mpos.data.local.CatalogDatabase.get(context.applicationContext)) as T
                        })
                        com.mendelev.mpos.feature.sale.SaleScreen(model,route=="history",{route="workspace"},{route="history"},{route="shift"},refunds)
                    }
                    "workspace" -> {
                        val model: com.mendelev.mpos.feature.workspace.WorkspaceViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                com.mendelev.mpos.feature.workspace.WorkspaceViewModel(com.mendelev.mpos.data.local.WorkspaceRepository(
                                    com.mendelev.mpos.data.local.CatalogDatabase.get(context.applicationContext))) as T
                        })
                        val photos=androidx.compose.runtime.remember(context) { com.mendelev.mpos.data.media.ProductPhotoStore(context.applicationContext.filesDir) }
                        com.mendelev.mpos.feature.workspace.WorkspaceScreen(model,photos,{route="catalog"},{route="settings"},{route="shift"},{route="checkout"},{route="history"})
                    }
                    "gallery" -> com.mendelev.mpos.feature.products.ComponentGallery { route = "catalog" }
                    "purchaseOrders", "receiving", "analytics", "bookings", "events" -> PlannedModuleScreen(route)
                    else -> {
                        val model: com.mendelev.mpos.feature.products.CatalogViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                com.mendelev.mpos.feature.products.CatalogViewModel(com.mendelev.mpos.data.local.CatalogRepository(
                                    com.mendelev.mpos.data.local.CatalogDatabase.get(context.applicationContext),
                                    photos=com.mendelev.mpos.data.media.ProductPhotoStore(context.applicationContext.filesDir))) as T
                        })
                        com.mendelev.mpos.feature.products.CatalogScreen(model, { route = "settings" }, { route = "gallery" }, { route = "workspace" }, {route="shift"})
                    }
                }
            }
        }
    }
}
