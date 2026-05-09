package dev.zun.flux.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.zun.flux.BuildConfig
import dev.zun.flux.FluxApp
import dev.zun.flux.R
import dev.zun.flux.data.net.ServerDiscovery
import dev.zun.flux.data.repo.ConnectionMode
import dev.zun.flux.ui.common.ScreenPadding
import dev.zun.flux.ui.common.SettingsGroup
import dev.zun.flux.ui.common.StatusPill
import dev.zun.flux.ui.common.StatusTone
import dev.zun.flux.util.normalizeOptionalLanServerUrl
import dev.zun.flux.util.normalizeOptionalTailscaleServerUrl
import dev.zun.flux.util.parseDiscoveryHost
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.launch

private sealed interface LanEntryMode {
    data object Discovery : LanEntryMode
    data object Manual : LanEntryMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    app: FluxApp,
    onSuccess: () -> Unit,
) {
    val settings = app.settingsManager
    var lanEntryMode by remember { mutableStateOf<LanEntryMode>(LanEntryMode.Discovery) }
    var hostInput by remember { mutableStateOf("") }
    var manualLanUrl by remember { mutableStateOf(settings.lanUrl ?: "") }
    var discovery by remember { mutableStateOf<DiscoveryState>(DiscoveryState.Idle) }
    var selectedDiscoveredUrl by remember { mutableStateOf<String?>(null) }
    var tailscaleUrl by remember { mutableStateOf(settings.tailscaleUrl ?: "") }
    var token by remember { mutableStateOf(settings.apiToken ?: "") }

    var isTesting by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tokenVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val noResolvedLan = when (lanEntryMode) {
        LanEntryMode.Discovery -> selectedDiscoveredUrl == null
        LanEntryMode.Manual -> manualLanUrl.isBlank()
    }
    val canSubmit = !isTesting && token.isNotBlank() &&
        !(noResolvedLan && tailscaleUrl.isBlank())

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.setup_heading),
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = stringResource(R.string.setup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )

            SettingsGroup(
                title = stringResource(R.string.setup_server_title),
                detail = stringResource(R.string.setup_server_detail),
            ) {
                when (lanEntryMode) {
                    LanEntryMode.Discovery -> DiscoverySection(
                        hostInput = hostInput,
                        onHostInputChange = {
                            hostInput = it
                            error = null
                        },
                        isSearching = isSearching,
                        discovery = discovery,
                        selectedUrl = selectedDiscoveredUrl,
                        onSearch = {
                            val parsed = parseDiscoveryHost(hostInput)
                            if (parsed == null) {
                                discovery = DiscoveryState.Error(
                                    app.getString(R.string.setup_lan_search_invalid_host),
                                )
                                return@DiscoverySection
                            }
                            isSearching = true
                            discovery = DiscoveryState.Searching
                            selectedDiscoveredUrl = null
                            scope.launch {
                                try {
                                    // Discovery uses its own short-timeout client
                                    // — not app.okHttpClient, which has a 30s
                                    // connect timeout, an auth interceptor, and
                                    // cert pinning that doesn't apply to an
                                    // unknown server.
                                    val results = ServerDiscovery().discover(parsed)
                                    discovery = DiscoveryState.Done(parsed.host, results)
                                    if (results.size == 1) selectedDiscoveredUrl = results[0].url
                                } catch (t: Throwable) {
                                    discovery = DiscoveryState.Error(t.toUserMessage("search"))
                                } finally {
                                    isSearching = false
                                }
                            }
                        },
                        onSelectResult = { selectedDiscoveredUrl = it.url },
                        onManualEntry = { lanEntryMode = LanEntryMode.Manual },
                    )

                    LanEntryMode.Manual -> ManualSection(
                        lanUrl = manualLanUrl,
                        onLanUrlChange = {
                            manualLanUrl = it
                            error = null
                        },
                        onBackToSearch = { lanEntryMode = LanEntryMode.Discovery },
                    )
                }

                OutlinedTextField(
                    value = tailscaleUrl,
                    onValueChange = {
                        tailscaleUrl = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.setup_tailscale_url_label)) },
                    placeholder = { Text(stringResource(R.string.setup_tailscale_url_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.setup_token_label)) },
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
                                contentDescription = stringResource(if (tokenVisible) R.string.setup_token_hide else R.string.setup_token_show),
                            )
                        }
                    },
                )

                error?.let {
                    StatusPill(label = it, tone = StatusTone.Error)
                }
            }

            Button(
                onClick = {
                    isTesting = true
                    error = null
                    scope.launch {
                        try {
                            val resolvedLan = when (lanEntryMode) {
                                LanEntryMode.Discovery -> selectedDiscoveredUrl

                                LanEntryMode.Manual -> runCatching {
                                    normalizeOptionalLanServerUrl(manualLanUrl, allowHttp = true)
                                }.getOrElse {
                                    throw IllegalArgumentException("Primary server: ${it.message}")
                                }
                            }
                            val ts = runCatching {
                                normalizeOptionalTailscaleServerUrl(tailscaleUrl, allowHttp = true)
                            }.getOrElse {
                                throw IllegalArgumentException("Fallback server: ${it.message}")
                            }
                            require(resolvedLan != null || ts != null) {
                                "Enter at least one server"
                            }
                            val oldLan = settings.lanUrl
                            val oldTailscale = settings.tailscaleUrl
                            val oldToken = settings.apiToken
                            val oldServerUrl = settings.serverUrl
                            val oldActiveRoute = settings.activeRoute
                            val oldMode = settings.connectionMode

                            try {
                                settings.lanUrl = resolvedLan
                                settings.tailscaleUrl = ts
                                settings.connectionMode = ConnectionMode.AUTO
                                settings.apiToken = token.trim()

                                // Resolve the active route once after the user taps Connect.
                                app.networkResolver.invalidateCache()
                                app.networkResolver.refreshNow()

                                // Validate token & connectivity (listPrompts requires auth).
                                app.repositories.prompts.listPrompts()

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
                            error = t.toUserMessage("connect")
                        } finally {
                            isTesting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit,
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.setup_testing_connection))
                } else {
                    Text(stringResource(R.string.common_connect))
                }
            }
        }
    }
}

@Composable
private fun DiscoverySection(
    hostInput: String,
    onHostInputChange: (String) -> Unit,
    isSearching: Boolean,
    discovery: DiscoveryState,
    selectedUrl: String?,
    onSearch: () -> Unit,
    onSelectResult: (dev.zun.flux.data.net.DiscoveredServer) -> Unit,
    onManualEntry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = hostInput,
            onValueChange = onHostInputChange,
            label = { Text(stringResource(R.string.setup_lan_search_label)) },
            placeholder = { Text(stringResource(R.string.setup_lan_search_placeholder)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(
            onClick = onSearch,
            enabled = hostInput.isNotBlank() && !isSearching,
        ) {
            Text(stringResource(R.string.setup_lan_search_button))
        }
    }
    Text(
        text = stringResource(R.string.setup_lan_search_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
    DiscoveryResultsList(
        state = discovery,
        selectedUrl = selectedUrl,
        onSelect = onSelectResult,
        onManualEntry = onManualEntry,
    )
}

@Composable
private fun ManualSection(
    lanUrl: String,
    onLanUrlChange: (String) -> Unit,
    onBackToSearch: () -> Unit,
) {
    OutlinedTextField(
        value = lanUrl,
        onValueChange = onLanUrlChange,
        label = { Text(stringResource(R.string.setup_lan_url_label)) },
        placeholder = { Text(stringResource(R.string.setup_lan_url_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedButton(
        onClick = onBackToSearch,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.setup_lan_manual_back))
    }
}
