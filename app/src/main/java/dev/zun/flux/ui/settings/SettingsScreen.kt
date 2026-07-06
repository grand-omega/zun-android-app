package dev.zun.flux.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import dev.zun.flux.R
import dev.zun.flux.Tuning
import dev.zun.flux.data.net.captureCertificatePin
import dev.zun.flux.data.worker.JobUploadWorker
import dev.zun.flux.ui.common.BackNavigationIcon
import dev.zun.flux.ui.common.ScreenPadding
import dev.zun.flux.ui.common.SettingsGroup
import dev.zun.flux.ui.common.StatusPill
import dev.zun.flux.ui.common.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val serverHealth by viewModel.serverHealth.collectAsStateWithLifecycle()
    val diagnostics by app.diagnostics.state.collectAsStateWithLifecycle()
    val certPins by app.certPinStore.pins.collectAsStateWithLifecycle()
    val uploadQueueFlow = remember(app) {
        WorkManager.getInstance(app).getWorkInfosByTagFlow(JobUploadWorker.TAG)
    }
    val uploadQueue by uploadQueueFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pinningInProgress by remember { mutableStateOf(false) }
    var pinResult by remember { mutableStateOf<String?>(null) }

    var tokenVisible by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    val lockoutOptions = listOf(
        0L to stringResource(R.string.settings_lockout_always),
        30_000L to stringResource(R.string.settings_lockout_30s),
        60_000L to stringResource(R.string.settings_lockout_1m),
        300_000L to stringResource(R.string.settings_lockout_5m),
        600_000L to stringResource(R.string.settings_lockout_10m),
        1_800_000L to stringResource(R.string.settings_lockout_30m),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    BackNavigationIcon(onBack = onBack, contentDescription = stringResource(R.string.common_back))
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = Tuning.MAX_CONTENT_WIDTH),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsGroup(
                    title = stringResource(R.string.settings_security_title),
                    detail = stringResource(R.string.settings_security_detail),
                ) {
                    OptionDropdown(
                        options = lockoutOptions,
                        selected = lockoutDuration,
                        onSelected = {
                            lockoutDuration = it
                            settingsManager.lockoutDurationMs = it
                        },
                    )
                }

                SettingsGroup(
                    title = stringResource(R.string.settings_connection_title),
                    detail = stringResource(R.string.settings_connection_detail),
                ) {
                    OutlinedTextField(
                        value = connectionDraft.serverUrl,
                        onValueChange = viewModel::updateServerUrl,
                        label = { Text(stringResource(R.string.settings_server_url_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = connectionDraft.error != null,
                    )

                    val connectionError = connectionDraft.error
                    if (connectionError != null) {
                        StatusPill(label = connectionError, tone = StatusTone.Error)
                    }

                    OutlinedTextField(
                        value = connectionDraft.token,
                        onValueChange = viewModel::updateToken,
                        label = { Text(stringResource(R.string.settings_token_label)) },
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
                                    contentDescription = stringResource(if (tokenVisible) R.string.settings_token_hide else R.string.settings_token_show),
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
                            Text(stringResource(R.string.settings_testing_connection))
                        } else {
                            Text(stringResource(R.string.common_connect))
                        }
                    }

                    StatusPill(label = connectionDraft.status, tone = StatusTone.Neutral)
                }

                SettingsGroup(
                    title = stringResource(R.string.settings_offline_cache_title),
                    detail = stringResource(R.string.settings_offline_cache_detail),
                ) {
                    InfoRow(
                        stringResource(R.string.settings_cached_images),
                        pluralStringResource(R.plurals.settings_cached_files_format, offlineCache.stats.fileCount, offlineCache.stats.fileCount),
                    )
                    InfoRow(stringResource(R.string.settings_cache_size), formatBytes(offlineCache.stats.bytes))
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
                                Text(stringResource(R.string.settings_caching))
                            } else {
                                Text(stringResource(R.string.settings_refresh_cache))
                            }
                        }
                        OutlinedButton(
                            onClick = { showClearCacheConfirm = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.common_clear))
                        }
                    }
                }

                SettingsGroup(
                    title = stringResource(R.string.settings_cert_pinning_title),
                    detail = stringResource(R.string.settings_cert_pinning_detail),
                ) {
                    if (certPins.isEmpty()) {
                        StatusPill(label = stringResource(R.string.settings_no_pins_active), tone = StatusTone.Warning)
                    } else {
                        certPins.forEach { (host, pin) ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = pin,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                pinningInProgress = true
                                pinResult = null
                                scope.launch {
                                    val urls = listOfNotNull(
                                        settingsManager.serverUrl?.takeUnless { it.isBlank() },
                                    )
                                    val captured = withContext(Dispatchers.IO) {
                                        urls.mapNotNull { captureCertificatePin(app.okHttpClient, it) }
                                    }
                                    if (captured.isEmpty()) {
                                        pinResult = app.getString(R.string.settings_no_https_certificates)
                                    } else {
                                        captured.forEach { (host, pin) -> app.certPinStore.setPin(host, pin) }
                                        app.rebuildOkHttp()
                                        app.rebuildRepository()
                                        pinResult = app.resources.getQuantityString(R.plurals.settings_pinned_format, captured.size, captured.size)
                                    }
                                    pinningInProgress = false
                                }
                            },
                            enabled = !pinningInProgress,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (pinningInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text(stringResource(R.string.settings_pinning))
                            } else {
                                Text(stringResource(R.string.settings_pin_current))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                app.certPinStore.clearAll()
                                app.rebuildOkHttp()
                                app.rebuildRepository()
                                pinResult = app.getString(R.string.settings_all_pins_cleared)
                            },
                            enabled = !pinningInProgress && certPins.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.common_clear))
                        }
                    }
                    pinResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }

                SettingsGroup(
                    title = stringResource(R.string.settings_diagnostics_title),
                    detail = stringResource(R.string.settings_diagnostics_detail),
                ) {
                    InfoRow(stringResource(R.string.settings_last_successful_request), relativeTimeOrDash(diagnostics.lastSuccessAtMs))
                    serverHealth?.version?.let { version ->
                        InfoRow(stringResource(R.string.settings_server_version), version)
                    }
                    serverHealth?.disk?.data_bytes?.let { bytes ->
                        InfoRow(stringResource(R.string.settings_server_storage), formatBytes(bytes))
                    }
                    val pendingUploads = uploadQueue.count {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                    }
                    InfoRow(stringResource(R.string.settings_upload_queue), pluralStringResource(R.plurals.settings_pending_format, pendingUploads, pendingUploads))
                    if (diagnostics.recentErrors.isNotEmpty()) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val copiedMessage = stringResource(R.string.settings_errors_copied)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_recent_errors),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    val text = diagnostics.recentErrors.joinToString("\n") { err ->
                                        "${formatClock(err.timestampMs)}  ${err.path}  —  ${err.message}"
                                    }
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("FluxEdit errors", text))
                                    android.widget.Toast.makeText(context, copiedMessage, android.widget.Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Text(stringResource(R.string.settings_copy_errors))
                            }
                        }
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
                        StatusPill(label = stringResource(R.string.settings_no_errors_recorded), tone = StatusTone.Success)
                    }
                }

                SettingsGroup(title = stringResource(R.string.settings_app_info_title)) {
                    InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                    InfoRow(stringResource(R.string.settings_build), BuildConfig.VERSION_CODE.toString())
                    InfoRow(stringResource(R.string.settings_package), BuildConfig.APPLICATION_ID)
                    InfoRow(stringResource(R.string.settings_server_url_label), settingsManager.serverUrl ?: stringResource(R.string.settings_value_none), isMonospace = true)
                }
            }
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        viewModel.clearOfflineCache()
                    },
                ) {
                    Text(stringResource(R.string.common_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun <T> OptionDropdown(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    placeholder: String = "Custom",
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selected }?.second ?: placeholder

    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
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
