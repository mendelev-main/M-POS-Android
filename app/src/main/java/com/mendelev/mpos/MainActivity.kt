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

@Preview(showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
fun FoundationScreen(fixtureUrl: String? = null) {
    var settings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("debugUpdateFixture", MODE_PRIVATE)
    val fixture = if (BuildConfig.DEBUG) fixtureUrl ?: preferences.getString("url", null) else null
    if (BuildConfig.DEBUG && fixtureUrl != null) preferences.edit().putString("url", fixtureUrl).apply()
    BackHandler(enabled = settings) { settings = false }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (settings) {
                val model: UpdateViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        UpdateViewModel(UpdateRepository(context.applicationContext, fixture)) as T
                })
                UpdatesScreen(model) { settings = false }
            } else Column(
                modifier = Modifier.safeDrawingPadding().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.foundation_title), style = MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.foundation_status), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.foundation_next), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { settings = true }) { Text(stringResource(R.string.settings_title)) }
            }
        }
    }
}
