package com.whereisit.findthings

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.whereisit.findthings.ui.security.AppLockSession
import com.whereisit.findthings.ui.security.BiometricStateStore

class FindThingsApplication : Application(), DefaultLifecycleObserver {
    override fun onCreate() {
        super<Application>.onCreate()
        AppLockSession.biometricDisabledForThisLock = BiometricStateStore.isDisabled(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        AppLockSession.applyLockTimeout()
        if (!AppLockSession.biometricDisabledForThisLock) {
            BiometricStateStore.setDisabled(this, false)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        AppLockSession.markBackgrounded()
    }
}
