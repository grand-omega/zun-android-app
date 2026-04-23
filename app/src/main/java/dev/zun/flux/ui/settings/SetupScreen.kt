package dev.zun.flux.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zun.flux.FluxApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    app: FluxApp,
    onSuccess: () -> Unit,
) {
    val settings = app.settingsManager
    var url by remember { mutableStateOf(settings.serverUrl ?: "http://") }
    var token by remember { mutableStateOf(settings.apiToken ?: "") }

    var isTesting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("FluxEdit Setup") }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Connect to your Workstation",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Text(
                text = "Enter your server details to start editing images.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    error = null
                },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.15:8080") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                    error = null
                },
                label = { Text("API Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null,
            )

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp).align(Alignment.Start),
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    isTesting = true
                    error = null
                    scope.launch {
                        try {
                            // 1. Save temporarily to test
                            settings.serverUrl = url.trim().removeSuffix("/")
                            settings.apiToken = token.trim()

                            // 2. Rebuild repo to point to new URL
                            app.rebuildRepository()

                            // 3. Ping health
                            app.repository.health()

                            // 4. Success!
                            onSuccess()
                        } catch (t: Throwable) {
                            error = "Connection failed: ${t.message ?: "Unknown error"}"
                        } finally {
                            isTesting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && url.isNotBlank() && token.isNotBlank(),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Text("Testing...")
                } else {
                    Text("Connect")
                }
            }
        }
    }
}
