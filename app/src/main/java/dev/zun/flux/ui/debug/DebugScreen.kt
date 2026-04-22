package dev.zun.flux.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.launch

private sealed interface PingState {
    data object Idle : PingState
    data object InFlight : PingState
    data class Result(val text: String) : PingState
    data class Failure(val message: String) : PingState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(repository: JobRepository) {
    var state by remember { mutableStateOf<PingState>(PingState.Idle) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZUN Flux · debug") },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Milestone 1 smoke test")

            Button(
                onClick = {
                    state = PingState.InFlight
                    scope.launch {
                        state = try {
                            val resp = repository.health()
                            PingState.Result("status: ${resp.status}")
                        } catch (t: Throwable) {
                            PingState.Failure(t.message ?: t::class.simpleName.orEmpty())
                        }
                    }
                },
                enabled = state !is PingState.InFlight,
            ) {
                Text("Ping")
            }

            when (val s = state) {
                PingState.Idle -> Text("Tap to call JobRepository.health()")
                PingState.InFlight -> CircularProgressIndicator()
                is PingState.Result -> Text(s.text)
                is PingState.Failure -> Text("error: ${s.message}")
            }
        }
    }
}
