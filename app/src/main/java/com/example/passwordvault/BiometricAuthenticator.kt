package com.example.passwordvault

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Enhanced BiometricAuthenticator supporting both biometric (fingerprint/face)
 * and device credential (PIN/pattern/password) authentication.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    private val executor = ContextCompat.getMainExecutor(activity)

    /** Check if BIOMETRIC_STRONG is available. */
    fun isBiometricAvailable(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Check if device credential (PIN/pattern) is available. */
    fun isDeviceCredentialAvailable(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Authenticate with biometric only (fingerprint/face).
     */
    fun authenticateBiometric(
        title: String,
        subtitle: String,
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onError: (String) -> Unit
    ) {
        authenticateInternal(
            title = title,
            subtitle = subtitle,
            authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
            cryptoObject = cryptoObject,
            negativeButtonText = "Cancel",
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Authenticate with device credential (PIN/pattern) — used for PIN-based 2FA.
     * Device credential prompts don't have a "Cancel" button (OS design).
     */
    fun authenticateDeviceCredential(
        title: String,
        subtitle: String,
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onError: (String) -> Unit
    ) {
        authenticateInternal(
            title = title,
            subtitle = subtitle,
            authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            cryptoObject = cryptoObject,
            negativeButtonText = null, // Device credential doesn't support negative button
            onSuccess = onSuccess,
            onError = onError
        )
    }

    private fun authenticateInternal(
        title: String,
        subtitle: String,
        authenticators: Int,
        cryptoObject: BiometricPrompt.CryptoObject?,
        negativeButtonText: String?,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess(result.cryptoObject)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(true)

        if (negativeButtonText != null) {
            builder.setNegativeButtonText(negativeButtonText)
        }

        val info = builder.build()

        if (cryptoObject != null) {
            prompt.authenticate(info, cryptoObject)
        } else {
            prompt.authenticate(info)
        }
    }
}
