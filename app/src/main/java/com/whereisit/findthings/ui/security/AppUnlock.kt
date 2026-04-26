package com.whereisit.findthings.ui.security

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricStateStore {
    private const val PREFS_NAME = "app_lock"
    private const val KEY_DISABLED = "biometric_disabled"

    fun setDisabled(context: Context, disabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISABLED, disabled)
            .apply()
    }

    fun isDisabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISABLED, false)
    }
}

object AppLockSession {
    @Volatile
    var isUnlocked: Boolean = false

    @Volatile
    var lastBackgroundTime: Long = 0L

    @Volatile
    var isAuthenticating: Boolean = false

    @Volatile
    var biometricFailureCount: Int = 0

    @Volatile
    var biometricDisabledForThisLock: Boolean = false

    const val BIOMETRIC_MAX_FAILED_PER_LOCK = 2
    const val LOCK_TIMEOUT_MS = 30_000L

    fun markLocked() {
        isUnlocked = false
        isAuthenticating = false
        biometricFailureCount = 0
        biometricDisabledForThisLock = false
    }

    fun markUnlockSucceeded() {
        isUnlocked = true
        isAuthenticating = false
        biometricFailureCount = 0
        biometricDisabledForThisLock = false
        lastBackgroundTime = 0L
    }

    fun disableBiometricForCurrentLock() {
        isAuthenticating = false
        biometricDisabledForThisLock = true
    }

    fun markBackgrounded(now: Long = System.currentTimeMillis()) {
        lastBackgroundTime = now
    }

    fun applyLockTimeout(now: Long = System.currentTimeMillis()) {
        if (lastBackgroundTime == 0L) return
        if (now - lastBackgroundTime > LOCK_TIMEOUT_MS) {
            markLocked()
        }
    }

    fun clearUnlockState() {
        markLocked()
        lastBackgroundTime = 0L
    }
}

fun syncBiometricDisabledState(context: Context) {
    AppLockSession.biometricDisabledForThisLock = BiometricStateStore.isDisabled(context)
}

fun persistBiometricDisabledState(context: Context, disabled: Boolean) {
    BiometricStateStore.setDisabled(context, disabled)
    AppLockSession.biometricDisabledForThisLock = disabled
}

fun biometricAuthenticators(): Int {
    return BiometricManager.Authenticators.BIOMETRIC_STRONG
}

fun biometricAvailability(context: Context): Int {
    return BiometricManager.from(context).canAuthenticate(biometricAuthenticators())
}

fun canShowAppUnlockSwitch(context: Context): Boolean {
    val result = biometricAvailability(context)
    return result == BiometricManager.BIOMETRIC_SUCCESS ||
        result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
}

fun isPromptNoBiometricsError(errorCode: Int): Boolean {
    return errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS
}

fun openSystemSecuritySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

fun launchAppBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (Int, String) -> Unit,
    onFailed: (() -> Unit)? = null,
    cancelOnFailed: Boolean = false,
    cancelOnFailedWhen: (() -> Boolean)? = null
): Boolean {
    if (AppLockSession.isAuthenticating || AppLockSession.biometricDisabledForThisLock) return false

    AppLockSession.isAuthenticating = true

    var ignoreNextError = false
    lateinit var prompt: BiometricPrompt

    prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLockSession.isAuthenticating = false
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (ignoreNextError) {
                    ignoreNextError = false
                    AppLockSession.isAuthenticating = false
                    return
                }

                AppLockSession.isAuthenticating = false
                onError(errorCode, errString.toString().ifBlank { "认证未完成，请重试。" })
            }

            override fun onAuthenticationFailed() {
                onFailed?.invoke()
                val shouldCancel = cancelOnFailed || cancelOnFailedWhen?.invoke() == true
                if (shouldCancel) {
                    ignoreNextError = true
                    AppLockSession.isAuthenticating = false
                    prompt.cancelAuthentication()
                }
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("验证身份")
        .setSubtitle("请使用指纹或面容解锁")
        .setNegativeButtonText("使用密码")
        .setAllowedAuthenticators(biometricAuthenticators())
        .build()

    prompt.authenticate(promptInfo)
    return true
}
