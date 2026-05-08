package dev.zun.flux.data.repo

import dev.zun.flux.data.api.HealthResponse

sealed interface ConnectionDiagnosis {
    data object Reachable : ConnectionDiagnosis

    data object NoServerUrl : ConnectionDiagnosis

    data class InvalidUrl(val message: String) : ConnectionDiagnosis

    data class ServiceNotListening(val message: String) : ConnectionDiagnosis

    data class HostUnreachable(val message: String) : ConnectionDiagnosis

    data class Unknown(val message: String) : ConnectionDiagnosis
}

interface HealthRepository {
    suspend fun health(): HealthResponse

    /** Best-effort TCP diagnosis for the currently active server URL. */
    suspend fun diagnoseConnection(): ConnectionDiagnosis
}
