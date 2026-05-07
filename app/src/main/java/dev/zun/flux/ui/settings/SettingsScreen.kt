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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.zun.flux.BuildConfig
import dev.zun.flux.FluxApp
import dev.zun.flux.data.repo.ActiveRoute
import dev.zun.flux.data.repo.ConnectionMode
import dev.zun.flux.data.worker.JobUploadWorker
import dev.zun.flux.ui.common.ScreenPadding
import dev.zun.flux.ui.common.SettingsGroup
import dev.zun.flux.ui.common.StatusPill
import dev.zun.flux.ui.common.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: FluxApp,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app) }
        },
    )
    val settingsManager = app.settingsManager
    var lockoutDuration by remember { mutableLongStateOf(settingsManager.lockoutDurationMs) }
    val connectionDraft by viewModel.connectionDraft.collectAsStateWithLifecycle()
    val offlineCache by viewModel.offlineCache.collectAsStateWithLifecycle()
    val diagnostics by app.diagnostics.state.collectAsStateWithLifecycle()
    val uploadQueueFlow = remember(app) {
        WorkManager.getInstance(app).getWorkInfosByTagFlow(JobUploadWorker.TAG)
    }
    val uploadQueue by uploadQueueFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var tokenVisible by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

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
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(
                title = "Security",
                detail = "Controls when FluxEdit asks for biometric or device unlock after backgrounding.",
            ) {
                Column(modifier = Modifier.selectableGroup()) {
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
            }

            SettingsGroup(
                title = "Connection",
                detail = "Changes are tested before replacing the active server route.",
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    Text("Mode", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                    connectionModeOptions.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = connectionDraft.connectionMode == mode,
                                    onClick = { viewModel.updateConnectionMode(mode) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = connectionDraft.connectionMode == mode, onClick = null)
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = connectionDraft.lanUrl,
                    onValueChange = viewModel::updateLanUrl,
                    label = { Text("LAN URL (used at home)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = connectionDraft.error?.startsWith("LAN URL:") == true,
                )

                OutlinedTextField(
                    value = connectionDraft.tailscaleUrl,
                    onValueChange = viewModel::updateTailscaleUrl,
                    label = { Text("Tailscale URL (used away)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = connectionDraft.error?.startsWith("Tailscale URL:") == true,
                )

                val connectionError = connectionDraft.error
                if (connectionError != null) {
                    StatusPill(label = connectionError, tone = StatusTone.Error)
                }

                OutlinedTextField(
                    value = connectionDraft.token,
                    onValueChange = viewModel::updateToken,
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
                    onClick = viewModel::connect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connectionDraft.isConnecting,
                ) {
                    if (connectionDraft.isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("Testing connection...")
                    } else {
                        Text("Connect")
                    }
                }

                StatusPill(label = connectionDraft.status, tone = StatusTone.Neutral)
            }

            SettingsGroup(
                title = "Offline Cache",
                detail = "Keeps recently viewed images usable when the server is unavailable.",
            ) {
                InfoRow("Cached Images", "${offlineCache.stats.fileCount} files")
                InfoRow("Cache Size", formatBytes(offlineCache.stats.bytes))
                StatusPill(label = offlineCache.status, tone = StatusTone.Neutral)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = viewModel::refreshOfflineCache,
                        enabled = !offlineCache.isRefreshing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (offlineCache.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("Caching...")
                        } else {
                            Text("Refresh Cache")
                        }
                    }
                    OutlinedButton(
                        onClick = { showClearCacheConfirm = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear")
                    }
                }
            }

            SettingsGroup(
                title = "Diagnostics",
                detail = "Snapshot of recent network activity. Useful when something feels off.",
            ) {
                InfoRow("Active Route", activeRouteLabel(settingsManager.activeRoute))
                InfoRow("Last Successful Request", relativeTimeOrDash(diagnostics.lastSuccessAtMs))
                val pendingUploads = uploadQueue.count {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
                InfoRow("Upload Queue", "$pendingUploads pending")
                if (diagnostics.recentErrors.isNotEmpty()) {
                    Text(
                        text = "Recent Errors",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        diagnostics.recentErrors.forEach { err ->
                            Text(
                                text = "${formatClock(err.timestampMs)}  ${err.path}  —  ${err.message}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    StatusPill(label = "No errors recorded", tone = StatusTone.Success)
                }
            }

            SettingsGroup(title = "App Info") {
                InfoRow("Version", BuildConfig.VERSION_NAME)
                InfoRow("Build", BuildConfig.VERSION_CODE.toString())
                InfoRow("Package", BuildConfig.APPLICATION_ID)
                InfoRow("Mode", connectionModeLabel(settingsManager.connectionMode))
                InfoRow("Active URL", settingsManager.serverUrl ?: "(none)", isMonospace = true)
                InfoRow("LAN URL", settingsManager.lanUrl ?: "(not set)", isMonospace = true)
                InfoRow("Tailscale URL", settingsManager.tailscaleUrl ?: "(not set)", isMonospace = true)
            }
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear offline cache?") },
            text = { Text("Cached image files will be removed from this device. Server history and settings stay unchanged.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        viewModel.clearOfflineCache()
                    },
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
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

private fun relativeTimeOrDash(timestampMs: Long?): String {
    if (timestampMs == null) return "—"
    val deltaSec = (System.currentTimeMillis() - timestampMs) / 1000
    return when {
        deltaSec < 5 -> "just now"
        deltaSec < 60 -> "${deltaSec}s ago"
        deltaSec < 3600 -> "${deltaSec / 60}m ago"
        deltaSec < 86_400 -> "${deltaSec / 3600}h ago"
        else -> "${deltaSec / 86_400}d ago"
    }
}

private fun formatClock(timestampMs: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1024) {
        "%.1f MB".format(mb)
    } else {
        "%.2f GB".format(mb / 1024.0)
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
