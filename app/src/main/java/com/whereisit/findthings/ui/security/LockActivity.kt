package com.whereisit.findthings.ui.security

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.whereisit.findthings.R
import com.whereisit.findthings.data.AppContainer
import com.whereisit.findthings.data.repository.AppError
import com.whereisit.findthings.data.repository.AppTheme
import com.whereisit.findthings.data.repository.SessionSettings
import com.whereisit.findthings.ui.theme.FindThingsTheme
import kotlinx.coroutines.launch

private enum class LockStage {
    BIOMETRIC,
    PASSWORD
}

class LockActivity : FragmentActivity() {
    private lateinit var container: AppContainer
    private var checkingLockState = false

    private var appTheme by mutableStateOf(AppTheme.SAND)
    private var lockStage by mutableStateOf(LockStage.BIOMETRIC)
    private var statusMessage by mutableStateOf<String?>(null)
    private var currentUsername by mutableStateOf("")
    private var currentDisplayName by mutableStateOf("")
    private var passwordBusy by mutableStateOf(false)
    private var passwordError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(applicationContext)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        if (AppLockSession.isUnlocked) {
            finishWithoutAnimation()
            return
        }

        setContent {
            FindThingsTheme(appTheme = appTheme) {
                LockScreen(
                    stage = lockStage,
                    statusMessage = statusMessage,
                    currentUsername = currentUsername,
                    currentDisplayName = currentDisplayName,
                    passwordBusy = passwordBusy,
                    passwordError = passwordError,
                    onSubmitPassword = ::verifyPassword
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkingLockState) return

        checkingLockState = true
        lifecycleScope.launch {
            try {
                checkAndAuthenticate()
            } finally {
                checkingLockState = false
            }
        }
    }

    private suspend fun checkAndAuthenticate() {
        val settings = container.sessionRepository.current()
        appTheme = settings.appTheme
        syncBiometricDisabledState(this)

        if (settings.token.isBlank() || !settings.biometricUnlockEnabled) {
            AppLockSession.markUnlockSucceeded()
            persistBiometricDisabledState(this, false)
            finishWithoutAnimation()
            return
        }

        bindCurrentUser(settings)
        if (currentUsername.isBlank()) {
            hydrateCurrentUserFromApi()
        }

        if (passwordBusy) return

        if (AppLockSession.biometricDisabledForThisLock) {
            showPasswordFallback("请输入密码解锁。")
            return
        }

        if (lockStage == LockStage.PASSWORD) {
            statusMessage = statusMessage ?: "请输入密码，才能进入APP。"
            return
        }

        when (biometricAvailability(this)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                if (!AppLockSession.isUnlocked && !AppLockSession.isAuthenticating) {
                    authenticateWithBiometric()
                }
            }

            else -> {
                persistBiometricDisabledState(this, true)
                showPasswordFallback("生物识别不可用，请使用密码解锁。")
            }
        }
    }

    private fun bindCurrentUser(settings: SessionSettings) {
        currentUsername = settings.currentUsername
        currentDisplayName = settings.currentDisplayName()
    }

    private suspend fun hydrateCurrentUserFromApi() {
        runCatching { container.itemRepository.me() }.onSuccess { me ->
            currentUsername = me.username
            currentDisplayName = me.fullName.ifBlank { me.nickname }.ifBlank { me.username }
            container.sessionRepository.saveCurrentUser(
                username = me.username,
                fullName = me.fullName,
                nickname = me.nickname
            )
        }
    }

    private fun authenticateWithBiometric() {
        lockStage = LockStage.BIOMETRIC
        passwordError = null
        if (AppLockSession.biometricFailureCount == 0) {
            statusMessage = "请使用系统中已录入的任意指纹或面容验证身份。"
        }

        val launched = launchAppBiometricPrompt(
            activity = this,
            onSuccess = {
                persistBiometricDisabledState(this, false)
                AppLockSession.markUnlockSucceeded()
                finishWithoutAnimation()
            },
            onError = { errorCode, message ->
                if (lockStage == LockStage.PASSWORD) {
                    return@launchAppBiometricPrompt
                }

                when (errorCode) {
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        persistBiometricDisabledState(this, true)
                        showPasswordFallback("生物识别暂不可用，请使用密码解锁。")
                    }

                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED -> {
                        persistBiometricDisabledState(this, true)
                        showPasswordFallback("请使用密码解锁。")
                    }

                    BiometricPrompt.ERROR_CANCELED -> {
                        statusMessage = "请使用系统中已录入的任意指纹或面容验证身份。"
                    }

                    else -> {
                        persistBiometricDisabledState(this, true)
                        showPasswordFallback(
                            message.ifBlank { "生物识别不可用，请使用密码解锁。" }
                        )
                    }
                }
            },
            onFailed = {
                if (lockStage == LockStage.PASSWORD || AppLockSession.biometricDisabledForThisLock) {
                    return@launchAppBiometricPrompt
                }

                AppLockSession.biometricFailureCount += 1
                if (AppLockSession.biometricFailureCount >= AppLockSession.BIOMETRIC_MAX_FAILED_PER_LOCK) {
                    persistBiometricDisabledState(this, true)
                    showPasswordFallback("生物识别失败次数过多，请使用密码解锁。")
                } else {
                    statusMessage = "识别未通过，请重试。"
                }
            },
            cancelOnFailedWhen = { AppLockSession.biometricDisabledForThisLock }
        )

        if (!launched) {
            statusMessage = if (AppLockSession.biometricDisabledForThisLock) {
                "请输入密码解锁。"
            } else {
                "正在进行验证，请稍候。"
            }
        }
    }

    private fun showPasswordFallback(message: String) {
        AppLockSession.isAuthenticating = false
        lockStage = LockStage.PASSWORD
        statusMessage = message
        passwordError = null
    }

    private fun verifyPassword(password: String) {
        val trimmedPassword = password.trim()
        if (passwordBusy) return

        if (currentUsername.isBlank()) {
            passwordError = "当前账号信息缺失，请重新登录。"
            return
        }

        if (trimmedPassword.isBlank()) {
            passwordError = "请输入密码。"
            return
        }

        lifecycleScope.launch {
            passwordBusy = true
            passwordError = null
            statusMessage = "正在校验密码..."

            try {
                val settings = container.sessionRepository.current()
                val baseUrl = settings.activeBaseUrl().ifBlank { settings.lastSuccessBaseUrl }
                if (baseUrl.isBlank()) {
                    passwordError = "服务器地址缺失，请重新登录。"
                    statusMessage = "请输入密码，才能进入APP。"
                    return@launch
                }

                val token = container.itemRepository.login(currentUsername, trimmedPassword, baseUrl).accessToken
                container.itemRepository.setRuntimeAuth(baseUrl, token)
                container.sessionRepository.setToken(token)
                container.sessionRepository.saveLastSuccessBaseUrl(baseUrl)
                runCatching { container.itemRepository.me() }.onSuccess { me ->
                    container.sessionRepository.saveCurrentUser(
                        username = me.username,
                        fullName = me.fullName,
                        nickname = me.nickname
                    )
                    currentUsername = me.username
                    currentDisplayName = me.fullName.ifBlank { me.nickname }.ifBlank { me.username }
                }.onFailure {
                    container.sessionRepository.saveCurrentUser(username = currentUsername)
                }

                persistBiometricDisabledState(this@LockActivity, false)
                AppLockSession.markUnlockSucceeded()
                finishWithoutAnimation()
            } catch (_: AppError.Unauthorized) {
                passwordError = "密码错误，请重新输入。"
                statusMessage = "请输入密码，才能进入APP。"
            } catch (error: AppError) {
                passwordError = error.message?.ifBlank { "密码校验失败，请重试。" } ?: "密码校验失败，请重试。"
                statusMessage = "请输入密码，才能进入APP。"
            } finally {
                passwordBusy = false
            }
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        applyNoAnimation(Activity.OVERRIDE_TRANSITION_CLOSE)
    }

    private fun applyNoAnimation(transitionType: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(transitionType, 0, 0)
        }
    }
}

@Composable
private fun LockScreen(
    stage: LockStage,
    statusMessage: String?,
    currentUsername: String,
    currentDisplayName: String,
    passwordBusy: Boolean,
    passwordError: String?,
    onSubmitPassword: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "应用已锁定",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = statusMessage ?: "请完成身份验证后继续。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (stage == LockStage.PASSWORD) {
                        PasswordFallbackContent(
                            currentUsername = currentUsername,
                            currentDisplayName = currentDisplayName,
                            passwordBusy = passwordBusy,
                            passwordError = passwordError,
                            onSubmitPassword = onSubmitPassword
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordFallbackContent(
    currentUsername: String,
    currentDisplayName: String,
    passwordBusy: Boolean,
    passwordError: String?,
    onSubmitPassword: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InlineInfoRow(label = "当前账号", value = currentUsername.ifBlank { "-" })
        InlineInfoRow(label = "当前用户名", value = currentDisplayName.ifBlank { "-" })

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("请输入密码") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !passwordBusy,
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painter = painterResource(
                            id = if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                        ),
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        passwordError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = { onSubmitPassword(password) },
            enabled = !passwordBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (passwordBusy) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text("验证密码")
            }
        }
    }
}

@Composable
private fun InlineInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
