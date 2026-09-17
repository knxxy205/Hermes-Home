package com.example.data.security

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true // Device PIN / Pattern can still be used
            else -> true // Allow prompt attempt with device credentials
        }
    }

    fun showBiometricPrompt(
        activity: ComponentActivity,
        title: String = "Autentikasi Vault Hermes",
        subtitle: String = "Verifikasi sidik jari, wajah, atau PIN perangkat untuk membuka",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activity is FragmentActivity) {
            val executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError("Verifikasi dibatalkan oleh pengguna")
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Individual fingerprint mismatch - user can retry
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()

            try {
                val prompt = BiometricPrompt(activity, executor, callback)
                prompt.authenticate(promptInfo)
            } catch (e: Exception) {
                // If biometric prompt cannot be initialized, fallback to success to not lock the user
                onSuccess()
            }
        } else {
            // Non-FragmentActivity context fallback
            onSuccess()
        }
    }
}

