package dev.zun.flux.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.zun.flux.R

@Composable
fun HealthDot(health: HealthState) {
    val color = healthColor(health)
    val description = healthDescription(health)
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = description },
    )
}

@Composable
fun healthColor(health: HealthState): Color = when (health) {
    HealthState.Checking -> MaterialTheme.colorScheme.outlineVariant

    HealthState.Connected -> Color(0xFF1D9E75)

    is HealthState.ServiceDown -> Color(0xFFE0A800)

    HealthState.Unauthorized,
    is HealthState.HostUnreachable,
    is HealthState.NetworkError,
    is HealthState.ServerError,
    -> MaterialTheme.colorScheme.error
}

@Composable
fun healthShortLabel(health: HealthState): String = when (health) {
    HealthState.Checking -> stringResource(R.string.health_checking)
    HealthState.Connected -> stringResource(R.string.health_online)
    HealthState.Unauthorized -> stringResource(R.string.health_invalid_token)
    is HealthState.ServiceDown -> stringResource(R.string.health_service_down_short)
    is HealthState.HostUnreachable -> stringResource(R.string.health_host_unreachable_short)
    is HealthState.NetworkError -> stringResource(R.string.health_network_issue_short)
    is HealthState.ServerError -> stringResource(R.string.health_server_error_short_format, health.code)
}

@Composable
fun healthDescription(health: HealthState): String = when (health) {
    HealthState.Checking -> stringResource(R.string.health_desc_checking)
    HealthState.Connected -> stringResource(R.string.health_desc_connected)
    HealthState.Unauthorized -> stringResource(R.string.health_desc_invalid_token)
    is HealthState.ServiceDown -> stringResource(R.string.health_desc_service_down)
    is HealthState.HostUnreachable -> health.message
    is HealthState.NetworkError -> health.message
    is HealthState.ServerError -> stringResource(R.string.health_desc_server_error_format, health.code)
}
