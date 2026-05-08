package dev.zun.flux.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.zun.flux.R
import dev.zun.flux.data.net.DiscoveredServer

/** UI state for the LAN-discovery section of the setup screen. */
sealed interface DiscoveryState {
    data object Idle : DiscoveryState
    data object Searching : DiscoveryState
    data class Done(val host: String, val results: List<DiscoveredServer>) : DiscoveryState
    data class Error(val message: String) : DiscoveryState
}

@Composable
fun DiscoveryResultsList(
    state: DiscoveryState,
    selectedUrl: String?,
    onSelect: (DiscoveredServer) -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            DiscoveryState.Idle -> Unit

            DiscoveryState.Searching -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.setup_lan_searching),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is DiscoveryState.Done -> if (state.results.isEmpty()) {
                Text(
                    text = stringResource(R.string.setup_lan_search_no_results, state.host),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                TextButton(onClick = onManualEntry) {
                    Text(stringResource(R.string.setup_lan_manual_entry))
                }
            } else {
                state.results.forEach { server ->
                    DiscoveredServerCard(
                        server = server,
                        isSelected = server.url == selectedUrl,
                        onClick = { onSelect(server) },
                    )
                }
                TextButton(onClick = onManualEntry) {
                    Text(stringResource(R.string.setup_lan_manual_entry))
                }
            }

            is DiscoveryState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onManualEntry) {
                    Text(stringResource(R.string.setup_lan_manual_entry))
                }
            }
        }
    }
}

@Composable
private fun DiscoveredServerCard(
    server: DiscoveredServer,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = server.url,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.setup_lan_found_format, server.version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = if (server.comfyOk) {
                    stringResource(R.string.setup_lan_comfy_ok)
                } else {
                    stringResource(R.string.setup_lan_comfy_down)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (server.comfyOk) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
