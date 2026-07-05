package dev.zun.flux.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.zun.flux.BuildConfig
import dev.zun.flux.FluxApp
import dev.zun.flux.R
import dev.zun.flux.Tuning
import dev.zun.flux.ui.common.ScreenPadding
import dev.zun.flux.ui.common.SettingsGroup
import dev.zun.flux.ui.common.StatusPill
import dev.zun.flux.ui.common.StatusTone
import dev.zun.flux.util.normalizeOptionalServerUrl
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    app: FluxApp,
    onSuccess: () -> Unit,
) {
    val settings = app.settingsManager
    var serverUrl by remember { mutableStateOf(settings.serverUrl ?: BuildConfig.DEFAULT_SERVER_URL) }
    var token by remember { mutableStateOf(settings.apiToken ?: "") }

    var isTesting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tokenVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canSubmit = !isTesting && serverUrl.isNotBlank() && token.isNotBlank()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Column(
                modifier = Modifier.widthIn(max = Tuning.MAX_CONTENT_WIDTH),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            error = null
                        },
                        label = { Text(stringResource(R.string.setup_server_url_label)) },
                        placeholder = { Text(stringResource(R.string.setup_server_url_placeholder)) },
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
                                val url = requireNotNull(
                                    normalizeOptionalServerUrl(
                                        serverUrl,
                                        allowHttp = BuildConfig.DEBUG,
                                        blockHost = if (BuildConfig.DEBUG) "zun.h.doremysweet.com" else null,
                                    ),
                                ) {
                                    "Enter a server URL"
                                }
                                val oldServerUrl = settings.serverUrl
                                val oldToken = settings.apiToken

                                try {
                                    settings.serverUrl = url
                                    settings.apiToken = token.trim()
                                    app.rebuildRepository()

                                    // Validate token & connectivity (listPrompts requires auth).
                                    app.repositories.prompts.listPrompts()

                                    onSuccess()
                                } catch (t: Throwable) {
                                    settings.serverUrl = oldServerUrl
                                    settings.apiToken = oldToken
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
}
