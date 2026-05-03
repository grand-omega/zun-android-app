package dev.zun.flux.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.zun.flux.data.repo.ActiveRoute

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

fun healthShortLabel(health: HealthState): String = when (health) {
    HealthState.Checking -> "Checking"
    HealthState.Connected -> "Online"
    HealthState.Unauthorized -> "Invalid token"
    is HealthState.ServiceDown -> "Server app not running"
    is HealthState.HostUnreachable -> "Machine unreachable"
    is HealthState.NetworkError -> "Network route issue"
    is HealthState.ServerError -> "Server ${health.code}"
}

fun healthDescription(health: HealthState): String = when (health) {
    HealthState.Checking -> "Checking connection"
    HealthState.Connected -> "Connected"
    HealthState.Unauthorized -> "Invalid API token"
    is HealthState.ServiceDown -> "Server PC is reachable, but the server app is not responding. Start the server binary."
    is HealthState.HostUnreachable -> health.message
    is HealthState.NetworkError -> health.message
    is HealthState.ServerError -> "Server error ${health.code}"
}

fun activeRouteLabel(route: ActiveRoute): String = when (route) {
    ActiveRoute.NONE -> "No route"
    ActiveRoute.LAN -> "LAN"
    ActiveRoute.TAILSCALE -> "Tailscale"
}
