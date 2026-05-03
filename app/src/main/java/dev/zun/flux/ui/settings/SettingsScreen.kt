package dev.zun.flux.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.zun.flux.BuildConfig
import dev.zun.flux.FluxApp
import dev.zun.flux.data.repo.ActiveRoute
import dev.zun.flux.data.repo.ConnectionMode
import dev.zun.flux.util.normalizeOptionalServerUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: FluxApp,
    onBack: () -> Unit,
) {
    val settingsManager = app.settingsManager
    var lockoutDuration by remember { mutableLongStateOf(settingsManager.lockoutDurationMs) }

    var lanUrl by remember { mutableStateOf(settingsManager.lanUrl ?: "") }
    var tailscaleUrl by remember { mutableStateOf(settingsManager.tailscaleUrl ?: "") }
    var connectionMode by remember { mutableStateOf(settingsManager.connectionMode) }
    var token by remember { mutableStateOf(settingsManager.apiToken ?: "") }
    var tokenVisible by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var connectionStatus by remember { mutableStateOf("Current settings are active.") }

    val lockoutOptions = listOf(
        0L to "Always lock",
        30_000L to "30 seconds",
        60_000L to "1 minute",
        300_000L to "5 minutes",
        600_000L to "10 minutes",
        1_800_000L to "30 minutes",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Security Section
            Text("Security", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Column(modifier = Modifier.selectableGroup()) {
                Text("Lock app after backgrounding for:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                lockoutOptions.forEach { (duration, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = lockoutDuration == duration,
                                onClick = {
                                    lockoutDuration = duration
                                    settingsManager.lockoutDurationMs = duration
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = lockoutDuration == duration,
                            onClick = null,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }

            HorizontalDivider()

            // Connection Section
            Text("Connection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.selectableGroup()) {
                    Text("Mode", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                    connectionModeOptions.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = connectionMode == mode,
                                    onClick = {
                                        connectionMode = mode
                                        connectionError = null
                                        connectionStatus = "Unsaved changes"
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = connectionMode == mode, onClick = null)
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = lanUrl,
                    onValueChange = {
                        lanUrl = it
                        connectionError = null
                        connectionStatus = "Unsaved changes"
                    },
                    label = { Text("LAN URL (used at home)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = connectionError?.startsWith("LAN URL:") == true,
                )

                OutlinedTextField(
                    value = tailscaleUrl,
                    onValueChange = {
                        tailscaleUrl = it
                        connectionError = null
                        connectionStatus = "Unsaved changes"
                    },
                    label = { Text("Tailscale URL (used away)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = connectionError?.startsWith("Tailscale URL:") == true,
                )

                if (connectionError != null) {
                    Text(
                        text = connectionError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        connectionError = null
                        connectionStatus = "Unsaved changes"
                    },
                    label = { Text("API Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
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

                Button(
                    onClick = {
                        try {
                            val lan = normalizeOptionalServerUrl(lanUrl)
                            val tailscale = normalizeOptionalServerUrl(tailscaleUrl)
                            require(lan != null || tailscale != null) {
                                "Enter at least one server URL"
                            }
                            require(token.isNotBlank()) {
                                "Enter an API token"
                            }
                            settingsManager.lanUrl = lan
                            settingsManager.tailscaleUrl = tailscale
                            settingsManager.connectionMode = connectionMode
                            settingsManager.apiToken = token.trim()
                            connectionError = null
                            connectionStatus = "Saved. Reconnecting..."
                            app.networkResolver.refresh()
                            app.rebuildRepository()
                        } catch (t: Throwable) {
                            connectionError = t.message ?: "Invalid connection settings"
                            connectionStatus = "Not saved"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect")
                }

                Text(
                    text = connectionStatus,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider()

            // App Info Section
            Text("App Info", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Version", BuildConfig.VERSION_NAME)
                InfoRow("Build", BuildConfig.VERSION_CODE.toString())
                InfoRow("Package", BuildConfig.APPLICATION_ID)
                InfoRow("Mode", connectionModeLabel(settingsManager.connectionMode))
                InfoRow("Active Route", activeRouteLabel(settingsManager.activeRoute))
                InfoRow("Active URL", settingsManager.serverUrl ?: "(none)", isMonospace = true)
                InfoRow("LAN URL", settingsManager.lanUrl ?: "(not set)", isMonospace = true)
                InfoRow("Tailscale URL", settingsManager.tailscaleUrl ?: "(not set)", isMonospace = true)
            }
        }
    }
}

private val connectionModeOptions = listOf(
    ConnectionMode.AUTO to "Auto (LAN first, Tailscale fallback)",
    ConnectionMode.LAN_ONLY to "LAN only",
    ConnectionMode.TAILSCALE_ONLY to "Tailscale only",
)

private fun connectionModeLabel(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.AUTO -> "Auto"
    ConnectionMode.LAN_ONLY -> "LAN only"
    ConnectionMode.TAILSCALE_ONLY -> "Tailscale only"
}

private fun activeRouteLabel(route: ActiveRoute): String = when (route) {
    ActiveRoute.NONE -> "(none)"
    ActiveRoute.LAN -> "LAN"
    ActiveRoute.TAILSCALE -> "Tailscale"
}

@Composable
private fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(
            text = value,
            style = if (isMonospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
