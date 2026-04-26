package com.whereisit.findthings

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.whereisit.findthings.data.AppContainer
import com.whereisit.findthings.data.repository.SessionSettings
import com.whereisit.findthings.ui.FindThingsApp
import com.whereisit.findthings.ui.security.AppLockSession
import com.whereisit.findthings.ui.security.LockActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var container: AppContainer
    private var hasLoadedSettings = false
    private var latestSettings = SessionSettings(
        internalUrl = "",
        externalUrl = "",
        activeEndpoint = com.whereisit.findthings.data.repository.ActiveEndpoint.INTERNAL,
        token = "",
        appTheme = com.whereisit.findthings.data.repository.AppTheme.SAND,
        lastSuccessBaseUrl = ""
    )
    private var launchingLockActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(applicationContext)

        lifecycleScope.launch {
            container.sessionRepository.settings.collectLatest { settings ->
                latestSettings = settings
                hasLoadedSettings = true
                syncUnlockAvailability(settings)
                scheduleLockCheck()
            }
        }

        enableEdgeToEdge()
        setContent {
            FindThingsApp(container = container)
        }
    }

    override fun onResume() {
        super.onResume()
        launchingLockActivity = false
        if (!hasLoadedSettings) return

        AppLockSession.applyLockTimeout()
        syncUnlockAvailability(latestSettings)
        scheduleLockCheck()
    }

    private fun syncUnlockAvailability(settings: SessionSettings) {
        if (settings.token.isBlank() || !settings.biometricUnlockEnabled) {
            AppLockSession.markUnlockSucceeded()
        }
    }

    private fun scheduleLockCheck() {
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                maybeLaunchLockActivity()
            }
        }
    }

    private fun maybeLaunchLockActivity() {
        val shouldLaunch = !AppLockSession.isUnlocked &&
            !AppLockSession.isAuthenticating &&
            latestSettings.token.isNotBlank() &&
            latestSettings.biometricUnlockEnabled

        if (!shouldLaunch || launchingLockActivity) return

        launchingLockActivity = true
        startActivity(
            Intent(this, LockActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
        )
    }
}
