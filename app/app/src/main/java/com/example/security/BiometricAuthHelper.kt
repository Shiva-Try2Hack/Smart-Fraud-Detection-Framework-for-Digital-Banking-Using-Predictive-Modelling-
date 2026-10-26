package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    NO_HARDWARE
}

object BiometricAuthHelper {

    fun checkAvailability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.NO_HARDWARE
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onErrorOrFallback: (String) -> Unit
    ) {
        val availability = checkAvailability(activity)
        if (availability != BiometricAvailability.AVAILABLE) {
            onErrorOrFallback("Biometric sensor not enrolled on this device; using UPI PIN.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onErrorOrFallback(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use 4-Digit UPI PIN")
            .build()

        prompt.authenticate(promptInfo)
    }

    fun authenticateDeviceLock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onErrorOrFallback: (String) -> Unit
    ) {
        val manager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuth = manager.canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS && canAuth != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            onErrorOrFallback("No device security (screen lock/biometrics) configured on this device.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onErrorOrFallback("Unlock cancelled by user")
                } else {
                    onErrorOrFallback(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }

        try {
            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onErrorOrFallback(e.message ?: "Authentication failed to start")
        }
    }
}
