package dev.zun.flux.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.zun.flux.R
import dev.zun.flux.ui.common.EmptyState
import dev.zun.flux.ui.common.StatusPill
import dev.zun.flux.ui.common.StatusTone

@Composable
fun LockScreen(
    message: String? = null,
    onUnlockClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        EmptyState(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.auth_locked_title),
            message = stringResource(R.string.auth_locked_message),
        )
        message?.let {
            StatusPill(label = it, tone = StatusTone.Error)
        }
        Button(onClick = onUnlockClick) {
            Text(stringResource(R.string.auth_unlock))
        }
    }
}
