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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.zun.flux.FluxApp
import dev.zun.flux.data.repo.ConnectionMode
import dev.zun.flux.util.normalizeOptionalServerUrl
import kotlinx.coroutines.launch
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    app: FluxApp,
    onSuccess: () -> Unit,
) {
    val settings = app.settingsManager
    var lanUrl by remember { mutableStateOf(settings.lanUrl ?: "http://") }
    var tailscaleUrl by remember { mutableStateOf(settings.tailscaleUrl ?: "http://") }
    var token by remember { mutableStateOf(settings.apiToken ?: "") }

    var isTesting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tokenVisible by remember { mutableStateOf(false) }
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
                value = lanUrl,
                onValueChange = {
                    lanUrl = it
                    error = null
                },
                label = { Text("LAN URL (used at home)") },
                placeholder = { Text("http://192.168.1.15:8080") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = tailscaleUrl,
                onValueChange = {
                    tailscaleUrl = it
                    error = null
                },
                label = { Text("Tailscale URL (used away)") },
                placeholder = { Text("http://100.x.y.z:8080") },
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
                visualTransformation = if (tokenVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (tokenVisible) "Hide token" else "Show token",
                        )
                    }
                },
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
                            val lan = normalizeOptionalServerUrl(lanUrl)
                            val ts = normalizeOptionalServerUrl(tailscaleUrl)
                            require(lan != null || ts != null) {
                                "Enter at least one server URL"
                            }
                            val oldLan = settings.lanUrl
                            val oldTailscale = settings.tailscaleUrl
                            val oldToken = settings.apiToken
                            val oldServerUrl = settings.serverUrl
                            val oldActiveRoute = settings.activeRoute
                            val oldMode = settings.connectionMode

                            try {
                                settings.lanUrl = lan
                                settings.tailscaleUrl = ts
                                settings.connectionMode = ConnectionMode.AUTO
                                settings.apiToken = token.trim()

                                // Resolve the active route once after the user taps Connect.
                                app.networkResolver.refreshNow()

                                // Validate token & connectivity (listPrompts requires auth).
                                app.repository.listPrompts()

                                onSuccess()
                            } catch (t: Throwable) {
                                settings.lanUrl = oldLan
                                settings.tailscaleUrl = oldTailscale
                                settings.connectionMode = oldMode
                                settings.apiToken = oldToken
                                settings.serverUrl = oldServerUrl
                                settings.activeRoute = oldActiveRoute
                                app.rebuildRepository()
                                throw t
                            }
                        } catch (t: Throwable) {
                            error = t.toSetupConnectionMessage()
                        } finally {
                            isTesting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && (lanUrl.isNotBlank() || tailscaleUrl.isNotBlank()) && token.isNotBlank(),
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

private fun Throwable.toSetupConnectionMessage(): String = when {
    this is retrofit2.HttpException && code() == 401 -> "Invalid API token."
    this is retrofit2.HttpException -> "Server responded with HTTP ${code()}. Check that the URL points to the FluxEdit API."
    this is IOException -> "Could not reach the server. Check Wi-Fi, Tailscale, and the server URL."
    else -> message ?: "Connection check failed."
}
