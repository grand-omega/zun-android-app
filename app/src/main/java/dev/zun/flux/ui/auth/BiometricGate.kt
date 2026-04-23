package dev.zun.flux.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed class BiometricResult {
    data object Success : BiometricResult()

    data class Error(val message: String) : BiometricResult()

    data object Unavailable : BiometricResult()
}

fun FragmentActivity.promptBiometric(onResult: (BiometricResult) -> Unit) {
    val manager = BiometricManager.from(this)
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(BiometricResult.Unavailable)
        return
    }

    val prompt =
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(BiometricResult.Success)
                }

                override fun onAuthenticationError(
                    code: Int,
                    msg: CharSequence,
                ) {
                    onResult(BiometricResult.Error(msg.toString()))
                }
            },
        )

    val info =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle("Unlock FluxEdit")
            .setSubtitle("Authenticate to continue")
            .setAllowedAuthenticators(authenticators)
            .build()

    prompt.authenticate(info)
}
