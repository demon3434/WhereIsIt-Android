# Android App 级指纹 / 人脸解锁开发文档（2次失败熔断 + 持久化方案）

## 1. 目标

在以下场景触发 App 解锁：

- 用户冷启动打开 App
- App 从后台切回前台
- 会话超时后再次进入 App

认证方式：

- 优先使用系统已录入的强生物识别能力（指纹 / 人脸）
- 不读取、不保存、不比对生物特征数据
- 认证成功后进入 App 主页面
- 连续 2 次失败后熔断，并切换 App 密码
- 熔断状态需跨进程持久化，防止用户通过杀进程绕过限制

---

## 2. 技术方案概述

使用 AndroidX 提供的 BiometricPrompt 实现 App 级认证。

---

## 3. 依赖

```gradle
dependencies {
    implementation "androidx.biometric:biometric:1.2.0"
}
```

---

## 4. 认证策略

```kotlin
BiometricManager.Authenticators.BIOMETRIC_STRONG
```

---

## 5. 持久化熔断状态

```kotlin
object BiometricStateStore {

    private const val KEY_DISABLED = "biometric_disabled"

    fun setDisabled(context: Context, disabled: Boolean) {
        val sp = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_DISABLED, disabled).apply()
    }

    fun isDisabled(context: Context): Boolean {
        val sp = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_DISABLED, false)
    }
}
```

---

## 6. 核心逻辑

- 连续失败 2 次 → 熔断
- 熔断后禁止再次调用生物识别
- 必须输入 App 密码
- 成功后恢复生物识别

---

## 7. LockActivity 核心代码

```kotlin
private val MAX_FAILED = 2

override fun onAuthenticationFailed() {
    AppLockSession.biometricFailureCount++

    if (AppLockSession.biometricFailureCount >= MAX_FAILED) {
        BiometricStateStore.setDisabled(this, true)
        showPasswordFallback()
    }
}
```

---

## 8. 关键规则

- 熔断状态必须持久化
- 不允许通过杀进程绕过
- 不要反复拉起 BiometricPrompt
- 成功登录后才恢复

---

## 9. 最终效果

用户输错 2 次 → 进入密码  
杀进程 → 仍然只能密码  
不会触发系统 30 秒锁定
