---
name: strict-kiosk-mode
description: >-
  Use this skill when implementing strict focus sessions, Android Lock Task Mode, DevicePolicyManager integration,
  kiosk session state machines, session persistence, and reboot/interruption recovery.
---

# Strict Kiosk Mode & Focus Session Skill

This skill provides step-by-step procedures, state models, and platform integrations for implementing **Strict Kiosk Focus Mode** in the Android Time Use Limiter application.

---

## 1. Focus Session State Machine

Strict kiosk mode relies on an authoritative state machine backed by persistent storage:

```kotlin
sealed interface FocusSessionState {
    data object Idle : FocusSessionState
    data class Preparing(val sessionId: String) : FocusSessionState
    data class Active(val sessionId: String, val startedAt: Long, val endsAt: Long) : FocusSessionState
    data class Completing(val sessionId: String) : FocusSessionState
    data class Completed(val sessionId: String, val completedAt: Long) : FocusSessionState
    data class Interrupted(val sessionId: String, val reason: InterruptionReason) : FocusSessionState
}

enum class InterruptionReason {
    SYSTEM_CRASH,
    DEVICE_REBOOT_EXPIRED,
    USER_EMERGENCY_OVERRIDE,
    DEVICE_ADMIN_DISABLED
}
```

---

## 2. Kiosk Controller Abstraction & Levels

### Interface
```kotlin
interface KioskController {
    suspend fun canEnterKiosk(): Boolean
    suspend fun enterKiosk(allowedPackages: Set<String>): Result<Unit>
    suspend fun exitKiosk(): Result<Unit>
    fun isKioskActive(): Boolean
}
```

### Level 3: `AndroidLockTaskKioskController` (Device Owner / Lock Task Mode)
For devices configured as Device Owner or with Lock Task privileges:

```kotlin
class AndroidLockTaskKioskController(
    private val activity: Activity,
    private val devicePolicyManager: DevicePolicyManager,
    private val adminComponent: ComponentName
) : KioskController {

    override suspend fun canEnterKiosk(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(activity.packageName) ||
               devicePolicyManager.isLockTaskPermitted(activity.packageName)
    }

    override suspend fun enterKiosk(allowedPackages: Set<String>): Result<Unit> = runCatching {
        if (devicePolicyManager.isDeviceOwnerApp(activity.packageName)) {
            val packages = (allowedPackages + activity.packageName).toTypedArray()
            devicePolicyManager.setLockTaskPackages(adminComponent, packages)
            devicePolicyManager.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE
            )
        }
        activity.startLockTask()
    }

    override suspend fun exitKiosk(): Result<Unit> = runCatching {
        activity.stopLockTask()
    }

    override fun isKioskActive(): Boolean {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}
```

### Level 1/2: `OverlayKioskController` (Standard Consumer Devices)
For consumer devices without device-owner permissions:
- Manages full-screen lockout overlay window (`SYSTEM_ALERT_WINDOW`).
- Emits home screen intents when non-whitelisted apps enter the foreground during an active session.

---

## 3. Session Persistence, TimeProvider & Anti-Clock Tampering

### Database Schema (Room)
```kotlin
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val targetDurationMillis: Long,
    val startWallClockMillis: Long,
    val startElapsedRealtime: Long,
    val bootCount: Int,
    val accumulatedElapsedMillis: Long,
    val lastCheckpointWallMillis: Long,
    val lastCheckpointElapsedRealtime: Long,
    val status: String,
    val mode: String,
    val protectedPackages: String, // comma-separated or JSON list
    val createdAt: Long
)
```

### Authoritative Monotonic Time Derivation (Tamper-Resistant)
```kotlin
fun calculateRemainingTime(
    session: FocusSessionEntity,
    timeProvider: TimeProvider
): Long {
    // Monotonic elapsed time cannot be changed by the user in Android Settings
    val currentElapsed = timeProvider.elapsedRealtimeMillis()
    val elapsedInCurrentBoot = (currentElapsed - session.startElapsedRealtime).coerceAtLeast(0L)
    val totalElapsed = session.accumulatedElapsedMillis + elapsedInCurrentBoot

    return (session.targetDurationMillis - totalElapsed).coerceAtLeast(0L)
}
```

### Clock Tamper Detection Heuristic
```kotlin
fun isClockTampered(
    session: FocusSessionEntity,
    timeProvider: TimeProvider,
    toleranceMillis: Long = 60_000L // 1 minute allowable drift
): Boolean {
    val currentWall = timeProvider.currentTimeMillis()
    val currentElapsed = timeProvider.elapsedRealtimeMillis()

    val wallDelta = currentWall - session.lastCheckpointWallMillis
    val elapsedDelta = currentElapsed - session.lastCheckpointElapsedRealtime

    // Backward clock manipulation
    if (wallDelta < -toleranceMillis) return true

    // Forward clock jump manipulation (wall clock jumped far ahead of monotonic clock)
    if (wallDelta > elapsedDelta + toleranceMillis) return true

    return false
}
```

### Reboot Recovery Procedure
1. Receive `android.intent.action.BOOT_COMPLETED` in `BootCompletedReceiver`.
2. Query Room for any session in `Active` status.
3. Compare `bootCount` against `timeProvider.getBootCount()`.
4. If boot count increased:
   - Accumulate prior elapsed time into `accumulatedElapsedMillis`.
   - Reset `startElapsedRealtime = timeProvider.elapsedRealtimeMillis()`.
   - Update `bootCount = timeProvider.getBootCount()`.
5. Check if total verified elapsed time >= `targetDurationMillis`.
6. If completed: Mark session as `Completed` and stop kiosk mode.
7. If active: Re-engage `KioskController` and resume background enforcement.

