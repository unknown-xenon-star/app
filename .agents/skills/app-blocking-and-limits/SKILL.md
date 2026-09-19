---
name: app-blocking-and-limits
description: >-
  Use this skill when implementing app lockout screens, system alert overlays (SYSTEM_ALERT_WINDOW),
  background limit monitoring services, PIN validation, strict mode, and emergency grace period timers.
---

# Android App Blocking & Limits Enforcement Skill

This skill provides procedures for evaluating time limits in real-time, displaying lockout screens over restricted apps, and managing anti-bypass/strict mode protections.

---

## 1. Real-Time Background Limit Evaluation Pipeline

When the foreground monitoring service detects a new foreground application:

```
[Foreground App Detected]
         │
         ▼
[Query Active Limit Rules for Package]
         │
    ┌────┴──────────────────────────┐
    │ Is Limit Enabled for Today?   │
    └────┬──────────────────────────┘
         │ Yes
         ▼
[Calculate Daily Usage vs Allowed Limit]
         │
    ┌────┴──────────────────────────┐
    │   Has Usage Exceeded Limit?   │
    └────┬──────────────────────────┘
         │ Yes
         ▼
[Check Temporary Grace Period / Override]
         │ Expired / None
         ▼
[Trigger Lockout: Show Overlay + Go Home]
```

---

## 2. Lockout Enforcement Strategies

### Strategy A: Full-Screen System Alert Overlay (`SYSTEM_ALERT_WINDOW`)
Displays a Compose or View-based full-screen lockout overlay window directly over the blocked app.

#### Window Manager Setup
```kotlin
fun showLockoutOverlay(context: Context, blockedPackage: String, appName: String, timeSpentFormatted: String) {
    if (!Settings.canDrawOverlays(context)) return

    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        layoutType,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    // Attach custom ComposeView or LockoutView
}
```

### Strategy B: Home Screen Redirection + Lockout Activity
In addition to or as a fallback to overlay windows, redirect the user back to their launcher or open the app's dedicated `LockoutActivity`:

```kotlin
fun navigateToHomeScreen(context: Context) {
    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    context.startActivity(homeIntent)
}
```

---

## 3. Strict Mode & Anti-Bypass Mechanisms

1. **PIN / Password Challenge**:
   - Store hashed PIN using salted SHA-256 or Android Keystore / EncryptedSharedPreferences.
   - Any limit modification, whitelist expansion, or override request requires successful PIN authentication.
2. **Device Admin / Uninstall Protection (Optional Strict Mode)**:
   - Implement `DeviceAdminReceiver` to prevent impulsive uninstallation during active focus sessions.
3. **Emergency Override / Grace Period**:
   - Allow a 1-minute or 5-minute emergency extension with a configurable cooldown (e.g., maximum 1 extension per day).
   - Reset grace timers automatically at midnight.
4. **Boot Auto-Start**:
   - Register `BootCompletedReceiver` (`android.intent.action.BOOT_COMPLETED`) to immediately resume the monitoring foreground service upon device reboot.

---

## 4. 24-Hour Uninstall Cooldown Implementation Pattern

To deter impulsive bypasses, uninstallation or disabling protection requires a persistent 24-hour waiting cycle.

### State Model
```kotlin
sealed interface UninstallProtectionState {
    data object None : UninstallProtectionState
    data class Waiting(
        val requestedAt: Long,
        val confirmationAvailableAt: Long
    ) : UninstallProtectionState
    data class Confirmation(
        val confirmationAvailableAt: Long
    ) : UninstallProtectionState
}
```

### Persistence & Time Calculation
```kotlin
// DataStore / Room entity fields:
// - uninstallRequestedAt: Long?
// - uninstallAvailableAt: Long?

fun getUninstallCooldownState(
    requestedAt: Long?,
    availableAt: Long?,
    clock: Clock
): UninstallProtectionState {
    if (requestedAt == null || availableAt == null) {
        return UninstallProtectionState.None
    }
    val now = clock.currentTimeMillis()
    return if (now < availableAt) {
        UninstallProtectionState.Waiting(requestedAt, availableAt)
    } else {
        UninstallProtectionState.Confirmation(availableAt)
    }
}
```

### Decision Window
- If user clicks **"Keep App"**: Reset `requestedAt` and `availableAt` to `null` (return to `UninstallProtectionState.None`).
- If user clicks **"Uninstall"** after 24h: enforce a **10-second reflection countdown**, then fire the system uninstall dialog. The persisted record is cleared and never reused.
- The confirmation window stays open for **24 hours**; if it lapses without confirmation, return to `UninstallProtectionState.None` — a fresh 24-hour cooldown is required to try again.
- The confirmation button on the UI must enforce the **10-second reflection countdown** before the uninstall fires.

