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
import androidx.compose.ui.unit.dp
import dev.zun.flux.BuildConfig
import dev.zun.flux.FluxApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: FluxApp,
    onBack: () -> Unit,
) {
    val settingsManager = app.settingsManager
    var lockoutDuration by remember { mutableLongStateOf(settingsManager.lockoutDurationMs) }

    var url by remember { mutableStateOf(settingsManager.serverUrl ?: "") }
    var token by remember { mutableStateOf(settingsManager.apiToken ?: "") }

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
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        settingsManager.serverUrl = it.trim().removeSuffix("/")
                        app.rebuildRepository()
                    },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        settingsManager.apiToken = it.trim()
                        app.rebuildRepository()
                    },
                    label = { Text("API Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            HorizontalDivider()

            // App Info Section
            Text("App Info", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Version", BuildConfig.VERSION_NAME)
                InfoRow("Build", BuildConfig.VERSION_CODE.toString())
                InfoRow("Package", BuildConfig.APPLICATION_ID)
                InfoRow("Server URL", BuildConfig.SERVER_URL, isMonospace = true)
            }
        }
    }
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
