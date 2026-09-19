# Android Time Use Limiter — Workspace Rules & Agent Guidelines

Welcome to the **Android Time Use Limiter** project.

This document defines the architectural rules, coding standards, enforcement model, safety constraints, and development guidelines for all agents and developers working on the codebase.

The project has two distinct enforcement modes:

1. **Standard Limiting** — usage tracking, warnings, lockout screens, and app restrictions.
2. **Strict Kiosk Focus** — a deliberate, user-started focus session where leaving the protected environment is prevented as far as Android's supported APIs allow.

---

# 1. Project Philosophy & Core Values

## 1.1 Privacy First

* All app-usage statistics, screen-time metrics, restriction rules, focus sessions, and configuration MUST remain on the user's device.
* Use **Room** and **DataStore** for persistent local data.
* NEVER transmit application usage telemetry to external servers.
* Do not add analytics SDKs that collect application usage or screen-time information.
* Network access must not be required for core functionality.
* Avoid unnecessary permissions.

## 1.2 Battery & Resource Efficiency

* Background monitoring MUST minimize CPU wakeups and battery consumption.
* Do not use high-frequency polling loops.
* Prefer:

  * `UsageStatsManager`
  * `UsageEvents`
  * lifecycle events
  * foreground-service state
  * `WorkManager`
  * event-driven state transitions
* Active strict-focus sessions may use a higher monitoring frequency when necessary, but the implementation must remain bounded and battery-conscious.

## 1.3 Enforcement Over Convenience

The primary purpose of the application is reliable time-limit enforcement.

Restrictions must survive:

* Activity recreation
* process death
* device reboot
* timezone changes
* midnight rollover
* screen lock/unlock
* app switching
* split-screen
* multi-window
* PiP where applicable
* notification interaction
* configuration changes

However, **never claim that a normal Android application provides absolute/unbreakable enforcement**.

Android security boundaries must be respected.

Where stronger kiosk enforcement is required, use supported Android mechanisms such as **Lock Task Mode / device-owner APIs** rather than attempting unsupported hacks.

## 1.4 User-Controlled Enforcement

Strict mode MUST be explicitly started by the user.

The application must clearly communicate:

* what will be restricted
* how long the session will last
* whether pausing is allowed
* whether exiting is allowed
* what happens when the timer expires
* what Android capabilities are required

Avoid deceptive or hidden lockout behavior.

## 1.5 Modern Android Architecture

Use:

* Kotlin 2.x
* Jetpack Compose
* Material 3
* Coroutines
* Flow
* Room
* DataStore
* Hilt
* Navigation Compose
* Clean Architecture
* MVVM/MVI-style state management

---

# 2. Core Product Model

The application is divided into four major systems.

```text
┌─────────────────────────────────────────────────────────────┐
│                    ANDROID TIME USE LIMITER                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  USAGE TRACKING                                             │
│  ├── UsageStatsManager                                      │
│  ├── UsageEvents                                             │
│  └── Daily/Weekly aggregation                               │
│                                                             │
│  LIMIT ENGINE                                               │
│  ├── Daily limits                                            │
│  ├── Per-app limits                                          │
│  ├── Schedules                                               │
│  └── Limit evaluation                                        │
│                                                             │
│  ENFORCEMENT ENGINE                                          │
│  ├── Warning                                                 │
│  ├── Lockout                                                 │
│  ├── Overlay                                                 │
│  ├── App interception                                        │
│  └── Kiosk Focus Mode                                        │
│                                                             │
│  FOCUS SESSION                                               │
│  ├── Countdown                                               │
│  ├── Protected application/environment                       │
│  ├── Session persistence                                     │
│  └── Strict exit handling                                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

# 3. Strict Kiosk Focus Mode

## 3.1 Definition

**Strict Kiosk Focus Mode** is a user-started timed session designed to keep the user inside a protected application/environment until the configured timer expires.

Example:

```text
User selects:

Duration: 50 minutes
Mode: STRICT KIOSK
Allowed app: Study App

        ↓

CONFIRM

        ↓

┌─────────────────────────────┐
│       STRICT FOCUS          │
│                             │
│          47:32              │
│                             │
│       Session Active        │
│                             │
│    Exit     Pause           │
│    DISABLED DISABLED        │
└─────────────────────────────┘

        ↓

Timer reaches 00:00

        ↓

SESSION COMPLETE

        ↓

Normal Android operation restored
```

## 3.2 Strict Session Requirements

When a strict session starts:

* Persist the session immediately.
* Generate a unique `sessionId`.
* Store the absolute end timestamp.
* Do NOT rely exclusively on a countdown integer.
* Reconstruct remaining time from:

```text
remaining = endTimestamp - currentTimestamp
```

This prevents timer corruption after:

* process death
* Activity recreation
* device sleep
* temporary service termination

## 3.3 Session State

Use a state machine:

```kotlin
sealed interface FocusSessionState {

    data object Idle : FocusSessionState

    data class Preparing(
        val sessionId: String
    ) : FocusSessionState

    data class Active(
        val sessionId: String,
        val startedAt: Long,
        val endsAt: Long
    ) : FocusSessionState

    data class Completing(
        val sessionId: String
    ) : FocusSessionState

    data class Completed(
        val sessionId: String,
        val completedAt: Long
    ) : FocusSessionState

    data class Interrupted(
        val sessionId: String,
        val reason: InterruptionReason
    ) : FocusSessionState
}
```

---

# 4. Kiosk Enforcement Levels

The application MUST distinguish between different enforcement capabilities.

## Level 1 — Normal Restriction

Uses:

* UsageStats
* notifications
* warnings
* lockout Activity
* overlay where permitted

The user can still leave the application through Android system mechanisms.

## Level 2 — Aggressive Restriction

Uses:

* foreground service
* active session monitoring
* overlay
* activity interception where technically possible
* repeated enforcement checks

This provides stronger enforcement but is still subject to Android security boundaries.

## Level 3 — Android Lock Task / Dedicated Device

Where the application has the required device-owner / dedicated-device configuration:

* use Android **Lock Task Mode**
* restrict access to non-approved applications
* restrict navigation according to configured lock-task policies
* keep the device inside the approved task environment

This is the preferred implementation for **true kiosk deployments**.

### Important

Never implement:

* accessibility-service abuse solely to defeat Android navigation
* hidden device-admin behavior
* exploit-based navigation blocking
* root-only assumptions
* undocumented Android APIs
* deceptive permission requests

The application must use supported Android APIs.

---

# 5. Kiosk Session Lifecycle

```text
                  ┌──────────────┐
                  │     IDLE     │
                  └──────┬───────┘
                         │
                  User starts session
                         │
                         ▼
                ┌────────────────┐
                │    PREPARING   │
                └───────┬────────┘
                        │
              Requirements verified
                        │
                        ▼
                ┌────────────────┐
                │     ACTIVE     │
                │                │
                │  Countdown     │
                │  Enforcement   │
                │  Monitoring    │
                └───────┬────────┘
                        │
                Timer reaches zero
                        │
                        ▼
                ┌────────────────┐
                │   COMPLETING   │
                └───────┬────────┘
                        │
                        ▼
                ┌────────────────┐
                │    COMPLETED   │
                └────────────────┘
```

Unexpected termination:

```text
ACTIVE
  │
  ├── process death ───────► restore ACTIVE
  │
  ├── reboot ──────────────► restore ACTIVE if session policy allows
  │
  ├── screen lock ─────────► remain ACTIVE
  │
  └── unsupported exit ────► INTERRUPTED
```

---

# 6. Timer Architecture & Clock Tamper Resistance

Never make the UI countdown the source of truth, and **never allow the user to bypass countdowns by altering the Android device clock / date in Settings**.

### 6.1 The Device Clock Tampering Vulnerability
If an application calculates remaining time exclusively using wall-clock time:
```kotlin
// VULNERABLE TO DEVICE CLOCK TAMPERING:
val remaining = endsAt - System.currentTimeMillis()
```
A user can bypass a 50-minute focus session or 24-hour uninstall cooldown simply by setting their device date/time forward in Android Settings.

### 6.2 Dual-Anchored Monotonic Time Architecture
The application MUST use a **dual-anchored time model**:

1. **Monotonic Realtime Clock (`SystemClock.elapsedRealtime()`)**:
   - Measures milliseconds since device boot, **including CPU deep sleep**.
   - **CANNOT be modified or altered by user setting changes in Android Settings**.
   - Serves as the authoritative source of truth for elapsed time during the active device boot cycle.

2. **Dual-Anchored Remaining Time Calculation**:
   ```kotlin
   val elapsedSinceStart = timeProvider.elapsedRealtimeMillis() - session.startElapsedRealtime
   val remainingMonotonic = (session.targetDurationMillis - (session.accumulatedElapsedMillis + elapsedSinceStart))
       .coerceAtLeast(0L)
   ```

3. **Time-Travel & Clock Manipulation Detection**:
   The engine continuously verifies clock consistency:
   - **Clock Roll-Backward**: If `currentTimeMillis() < lastCheckpointWallMillis`, a backward shift occurred. Restrictions remain active; elapsed time continues accumulating monotonically.
   - **Clock Roll-Forward (Time Leap)**: If `(currentWallTime - lastCheckpointWallMillis) > (currentElapsed - lastCheckpointElapsed) + THRESHOLD`, a manual forward clock jump occurred. **The timer MUST NOT expire prematurely.** Monotonic elapsed time must be satisfied.

### 6.3 Database Schema (Room)

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

    val status: FocusSessionStatus,

    val mode: FocusMode,

    val protectedPackages: String,

    val createdAt: Long
)
```

The UI derives the countdown purely from the authoritative persisted state and monotonic time provider.

---

# 7. Persistence

## Room

Room stores:

### FocusSession
Stores authoritative dual-anchored session timestamps, target durations, and boot counts.


### AppLimit

Stores:

* package name
* daily limit
* warning threshold
* enabled state
* schedule

### UsageRecord

Stores locally aggregated usage information.

Do not unnecessarily store raw event data indefinitely.

---

# 8. DataStore

DataStore should contain lightweight configuration such as:

* onboarding completion
* user preferences
* selected theme
* notification preferences
* kiosk preferences
* PIN configuration metadata
* enforcement preferences

Do not use DataStore as a replacement for relational usage/session data.

---

# 9. Android Permissions

## 9.1 Usage Access

`PACKAGE_USAGE_STATS`

Usage access is special access and must be enabled through:

```text
Settings.ACTION_USAGE_ACCESS_SETTINGS
```

Check availability through `AppOpsManager`.

The app must explain why this access is required before directing the user to Settings.

---

## 9.2 Overlay

`SYSTEM_ALERT_WINDOW`

Only use overlay functionality when required by the selected enforcement mode.

Check:

```kotlin
Settings.canDrawOverlays(context)
```

Never request overlay permission without explaining its purpose.

---

## 9.3 Foreground Service

If a foreground service is used:

* declare the correct foreground-service type for the actual use case
* satisfy Android version-specific requirements
* display an appropriate persistent notification
* stop the service when no longer required

Do NOT add unsupported or unrelated foreground-service types merely to obtain stronger privileges.

---

## 9.4 Notifications

`POST_NOTIFICATIONS`

On Android 13+:

* request notification permission when appropriate
* explain why notifications are useful
* never make core timer correctness depend on notifications

---

## 9.5 Battery Optimization

Do not automatically assume that battery-optimization exemption is required.

Only guide users toward OEM/system battery settings when the device actually demonstrates background execution problems.

Never present battery optimization exemption as a universal requirement.

---

# 10. Background Architecture

Use the correct mechanism for each task.

| Task                     | Mechanism                          |
| ------------------------ | ---------------------------------- |
| Read app usage           | `UsageStatsManager`                |
| Process usage events     | Repository/DataSource              |
| Active strict session    | Foreground service where justified |
| Persistent session state | Room                               |
| Daily maintenance        | WorkManager                        |
| Configuration            | DataStore                          |
| UI state                 | StateFlow                          |
| Timer display            | UI + persisted end timestamp       |

Do not use WorkManager as a one-second timer.

Do not use a continuously running service when no active enforcement session exists.

---

# 11. Architecture

```text
┌──────────────────────────────────────────────────────────────┐
│                     PRESENTATION                             │
│                                                              │
│ Compose Screens                                               │
│ ViewModels                                                    │
│ UiState / UiIntent                                            │
│ Navigation                                                    │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                        DOMAIN                                │
│                                                              │
│ StartFocusSessionUseCase                                      │
│ EndFocusSessionUseCase                                        │
│ RestoreFocusSessionUseCase                                    │
│ EvaluateAppLimitUseCase                                       │
│ GetDailyUsageUseCase                                          │
│ EnforceKioskSessionUseCase                                    │
│                                                              │
│ Repository Interfaces                                         │
│ Domain Models                                                 │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                         DATA                                 │
│                                                              │
│ Room                                                           │
│ DataStore                                                      │
│ UsageStatsDataSource                                            │
│ KioskController                                                 │
│ FocusSessionRepository                                         │
│                                                              │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                    ANDROID PLATFORM                           │
│                                                              │
│ UsageStatsManager                                              │
│ ActivityManager                                                │
│ DevicePolicyManager                                            │
│ Lock Task Mode                                                 │
│ Foreground Service                                             │
│ NotificationManager                                            │
│ PackageManager                                                 │
└──────────────────────────────────────────────────────────────┘
```

---

# 12. Presentation Rules

ViewModels:

* expose exactly one immutable `StateFlow<UiState>`
* accept explicit `UiIntent` / `UiEvent`
* contain no Android framework calls
* never directly access `Context`
* never directly access `UsageStatsManager`
* never directly access `DevicePolicyManager`

Example:

```kotlin
sealed interface FocusIntent {
    data class Start(val durationMillis: Long) : FocusIntent
    data object Cancel : FocusIntent
    data object Refresh : FocusIntent
}
```

Example:

```kotlin
data class FocusUiState(
    val session: FocusSession? = null,
    val remainingMillis: Long = 0L,
    val isKioskActive: Boolean = false,
    val error: String? = null
)
```

---

# 13. Domain Rules

Business logic belongs in the Domain layer.

Examples:

```text
StartFocusSessionUseCase
RestoreFocusSessionUseCase
CompleteExpiredSessionUseCase
EvaluateAppLimitUseCase
GetRemainingSessionTimeUseCase
CheckKioskCapabilityUseCase
EnterKioskModeUseCase
ExitKioskModeUseCase
```

The Domain layer must remain independent from Android framework APIs.

---

# 14. Kiosk Controller Abstraction

Do not couple business logic directly to `DevicePolicyManager`.

Use an abstraction:

```kotlin
interface KioskController {

    suspend fun canEnterKiosk(): Boolean

    suspend fun enterKiosk(
        allowedPackages: Set<String>
    ): Result<Unit>

    suspend fun exitKiosk(): Result<Unit>

    fun isKioskActive(): Boolean
}
```

Implementations may include:

```text
AndroidLockTaskKioskController
OverlayKioskController
NoOpKioskController
```

This allows the application to gracefully degrade when device-owner capabilities are unavailable.

---

# 15. Enforcement Priority

When multiple restrictions apply:

```text
Strict Focus Session
        ↓
Kiosk enforcement
        ↓
Per-app limits
        ↓
Daily limits
        ↓
Warnings
        ↓
Normal usage
```

A strict focus session must not accidentally get overridden by a lower-priority rule.

---

# 16. Multi-Window / PiP / Split Screen

The enforcement engine must explicitly detect and handle:

* split-screen
* multi-window
* PiP
* external displays where applicable
* task switching

Do not assume that:

```kotlin
Activity.onPause()
```

means the user has exited the protected environment.

Use Android task/activity state and the appropriate platform APIs.

---

# 17. Reboot Recovery & Monotonic Reconciliation

If an active strict session exists when the device reboots:

1. Load the persisted session (`FocusSessionEntity`).
2. Query `timeProvider.getBootCount()`. If boot count increased, `elapsedRealtime` has reset.
3. Compute elapsed time prior to reboot from `accumulatedElapsedMillis`.
4. Check wall-clock delta `(currentWallTime - lastCheckpointWallMillis)`.
5. **Anti-Tamper Validation**: If the wall clock shows that time jumped forward during reboot, verify against boot delta or clamp advancement.
6. If the verified elapsed duration is still less than `targetDurationMillis`:
   * Update `startElapsedRealtime = timeProvider.elapsedRealtimeMillis()`.
   * Update `bootCount = timeProvider.getBootCount()`.
   * Restore strict enforcement immediately.
7. If verified duration has genuinely elapsed:
   * Mark session as `Completed`.

Never restart a session from zero, and **never allow an unverified wall-clock leap during reboot to prematurely end a strict focus session**.

---

# 18. Time, Clock Handling & Tamper Resistance

### 18.1 Clock Manipulation Threat Model
Users seeking to bypass restrictions may attempt:
- **Fast-Forward Clock**: Changing system date/time hours or days ahead to trigger timer expiration.
- **Roll-Backward Clock**: Changing system date/time backwards to avoid midnight reset or prolong limits.
- **Timezone Hopping**: Changing system timezone to shift daily quota boundaries.

### 18.2 Injectable `TimeProvider`
Never use direct calls to static clock methods without abstraction. Use an injectable `TimeProvider`:

```kotlin
interface TimeProvider {
    /** Wall-clock epoch milliseconds (can change on NTP sync or manual user edits) */
    fun currentTimeMillis(): Long

    /** Monotonic milliseconds since boot including deep sleep (tamper-proof) */
    fun elapsedRealtimeMillis(): Long

    /** System boot counter or boot session identifier */
    fun getBootCount(): Int
}
```

Production:
```kotlin
class AndroidTimeProvider(private val context: Context) : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    override fun getBootCount(): Int = Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.BOOT_COUNT,
        0
    )
}
```

Tests:
```kotlin
class FakeTimeProvider : TimeProvider {
    var currentTime: Long = 0L
    var elapsedRealtime: Long = 0L
    var bootCount: Int = 1

    fun advance(millis: Long) {
        currentTime += millis
        elapsedRealtime += millis
    }

    fun tamperWallClockForward(millis: Long) {
        currentTime += millis // Simulates user setting device clock forward
    }
}
```

### 18.3 Clock Tampering Invariant
1. **Active Sessions**: A session countdown MUST decrease ONLY in proportion to true monotonic elapsed time (`timeProvider.elapsedRealtimeMillis()`).
2. **Tampering Detection**: If wall-clock time deviates significantly from elapsed realtime progress, flag a tampering event, maintain active enforcement, and log the checkpoint without expiring the session.
3. **Daily Usage Rollovers**: Daily usage calculations must use the local calendar start-of-day boundary (`LocalDate.now()`) anchored with monotonic verification.


---

# 19. PIN / Unlock Rules

If PIN protection exists:

* never store the raw PIN
* use a secure salted verifier
* rate-limit repeated attempts
* avoid revealing whether a particular credential component is correct
* never log PIN-related information
* keep PIN functionality separate from the timer engine

Strict kiosk mode must not silently provide an emergency bypass through an ordinary UI control.

If an emergency exit mechanism is provided, it must be explicitly defined in the product's enforcement policy.

---

# 20. 24-Hour Uninstall Cooldown

## 20.1 Purpose & User Flow

Add a **24-hour uninstall cooldown** to prevent the user from immediately removing the Time Use Limiter when they are under the application's protection rules.

When the user attempts to uninstall the application:

```text
User selects "Uninstall"
        ↓
┌──────────────────────────────┐
│ Uninstall Protection         │
│                              │
│ Are you sure you want to     │
│ uninstall?                   │
│                              │
│ Uninstall request started.   │
│ You must wait 24 hours       │
│ before uninstalling.         │
│                              │
│ [ Start 24-Hour Countdown ]  │
└──────────────────────────────┘
        ↓
24-hour cooldown
        ↓
┌─────────────────────────────────────┐
│ Uninstall Available                 │
│                                     │
│ 24 hours have passed.               │
│                                     │
│ Do you still want to                │
│ uninstall the application?          │
│  10 seconds cooldown                │
│                                     │
│ [ Keep App ] [ Uninstall ]          │
│(uninstall only avialbe after 10 sec)│
└─────────────────────────────────────┘
```

## 20.2 Decision Branches

### If the User Chooses "Keep App"

The uninstall process is cancelled and the application returns to normal operation.

```text
Keep App
   ↓
Cancel uninstall request
   ↓
Normal operation
```

### If the User Chooses "Uninstall"

Do **not** immediately uninstall.

1. Enforce a mandatory **10-second reflection delay** (countdown visible in the UI).
2. After the reflection delay, fire the system uninstall dialog.
3. The confirmation window stays open for 24 hours. If the user does not confirm within it, the window lapses and the state returns to `None` — a fresh 24-hour cooldown is then required to try again.

```text
Uninstall
    ↓
10-second reflection delay
    ↓
System uninstall dialog

(If the confirmation window lapses instead: state resets to None,
 and a new 24-hour cooldown is required to attempt uninstall again.)
```

This bounds the flow: every uninstall attempt is preceded by a full 24-hour cooldown plus a 24-hour decision window, and the window cannot be reused once spent or lapsed.

## 20.3 State Model & Tamper Resistance

The uninstall protection MUST be represented as a persistent state machine rather than relying on the UI.

```kotlin
sealed interface UninstallProtectionState {

    data object None : UninstallProtectionState

    data class Waiting(
        val requestedAt: Long,
        val targetDurationMillis: Long,
        val startElapsedRealtime: Long,
        val accumulatedElapsedMillis: Long,
        val lastCheckpointWallMillis: Long,
        val lastCheckpointElapsedRealtime: Long,
        val bootCount: Int
    ) : UninstallProtectionState

    data class Confirmation(
        val confirmationAvailableAt: Long
    ) : UninstallProtectionState
}
```

Persist the timestamps in Room / DataStore.

The authoritative remaining time is calculated using monotonic elapsed time:

```kotlin
val elapsedSinceStart = timeProvider.elapsedRealtimeMillis() - state.startElapsedRealtime
val remainingMonotonic = (state.targetDurationMillis - (state.accumulatedElapsedMillis + elapsedSinceStart))
    .coerceAtLeast(0L)
```

**CRITICAL ANTI-TAMPER INVARIANT**:
If the user attempts to bypass the 24-hour cooldown by changing their device clock forward in Android Settings:
- The engine detects the anomaly (`(currentWall - lastCheckpointWall) >> (currentElapsed - lastCheckpointElapsed)`).
- The forward clock jump is ignored.
- The 24-hour cooldown MUST NOT be satisfied until 24 hours of verified monotonic time have elapsed.

## 20.4 Persistence Requirements

The state must survive:

* Activity recreation
* process death
* device reboot
* screen lock
* app restart
* timezone changes
* manual system clock modifications / forward leaps

Store absolute timestamps and monotonic elapsed anchors.

## 20.5 UX Requirements

The user must always be able to see:

* that uninstall protection is active
* when the next confirmation becomes available
* why the delay exists
* the current state of the request

Example:

> **Uninstall protection is active**  
> You requested to uninstall this app. For your protection, you can confirm the decision after 23h 41m.  
>  
> **Available:** Tomorrow at 9:15 PM

At the end of the verified 24 hours:

> **24 hours have passed**  
> You previously requested to uninstall Time Use Limiter.  
> Do you still want to continue?

Buttons:

**Keep App** | **Continue Uninstall** *(enabled only after a mandatory 10-second reflection cooldown)*

## 20.6 Enforcement Rules

* Never reset the cooldown because the application process was killed.
* Never reset the cooldown when the device reboots.
* Never use the UI countdown as the source of truth.
* Never permit a manual device clock fast-forward to bypass the cooldown.
* Never silently extend the cooldown beyond the true 24-hour monotonic requirement.
* Never delete the uninstall state before the user makes a decision.
* All timestamps must use an injectable `TimeProvider` for unit testing time shifts.
* The user must be clearly informed about the delay before the first countdown begins.


---

# 21. Error Handling

Use typed domain errors.

Example:

```kotlin
sealed interface KioskError {

    data object PermissionMissing : KioskError

    data object DeviceOwnerRequired : KioskError

    data object KioskUnavailable : KioskError

    data object AlreadyActive : KioskError

    data object InvalidDuration : KioskError
}
```

Do not allow Android exceptions to leak directly through the Domain layer.

---

# 22. Testing Requirements

Every major enforcement component must have tests.

## Unit Tests

Test:

* timer calculations
* midnight rollover
* timezone changes
* expired sessions
* process restoration
* daily limits
* warning thresholds
* overlapping limits
* session state transitions
* 24-hour uninstall cooldown state transitions & repeated cycles

## Integration Tests

Test:

* Room persistence
* DataStore persistence
* repository behavior
* usage aggregation

## Android Tests

Test:

* permission state handling
* Activity lifecycle
* foreground service behavior
* kiosk capability detection
* Lock Task integration where available

## Critical Invariant

The following must always hold:

```text
Persisted session state
        +
Current time
        =
Authoritative session state
```

The UI must never be the source of truth.

---

# 23. Coding Standards

## Immutability

Prefer:

```kotlin
val
```

over:

```kotlin
var
```

Use immutable collections in domain/UI models.

## Coroutines

Never hardcode dispatchers in repositories.

Use:

```kotlin
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}
```

## Logging

Never log:

* package usage history
* sensitive user configuration
* PIN information
* detailed personal usage behavior

Debug logs must be removable/disabled in release builds.

---

# 24. Agent Development Rules

Before implementing a feature, agents MUST determine:

1. Which layer owns the feature.
2. Whether Android framework APIs are required.
3. Whether the feature requires a new repository abstraction.
4. Whether the feature changes enforcement behavior.
5. Whether persistence changes are required.
6. Whether the feature requires migration.
7. Which tests must be added.

Agents MUST NOT:

* bypass Android permission requirements
* invent undocumented Android APIs
* claim an ordinary application is completely un-bypassable
* add unnecessary background services
* add unnecessary permissions
* move business logic into Compose
* access Android services directly from ViewModels
* store secrets in plaintext
* use UI countdown state as the authoritative timer

---

# 25. Available Skills

Refer to `.agents/skills/` for implementation-specific procedures.

### `usage-stats-tracking`

Use for:

* UsageStatsManager
* UsageEvents
* daily usage
* weekly usage
* usage aggregation
* package-level statistics

### `app-blocking-and-limits`

Use for:

* app limits
* lockout
* overlays
* enforcement
* PIN unlock
* 24-hour uninstall cooldown & DeviceAdmin integration
* background restriction evaluation

### `strict-kiosk-mode`

Use for:

* focus sessions
* kiosk lifecycle
* Lock Task Mode
* device-owner integration
* session persistence
* kiosk restoration
* exit handling

### `android-build-and-test`

Use for:

* Gradle builds
* lint
* unit tests
* instrumentation tests
* JUnit
* MockK
* Robolectric

---

# 26. Definition of Done

A feature is not considered complete until:

* [ ] Architecture rules are followed.
* [ ] State is persisted where necessary.
* [ ] Process death has been considered.
* [ ] Reboot behavior has been considered.
* [ ] Timezone/date boundaries have been considered.
* [ ] Android permission requirements are handled.
* [ ] Battery impact is reasonable.
* [ ] No unnecessary telemetry is introduced.
* [ ] Unit tests cover core business logic.
* [ ] Android-specific behavior is tested where practical.
* [ ] No unsupported Android APIs are used.
* [ ] Strict kiosk behavior clearly distinguishes normal-app limitations from device-owner capabilities.
* [ ] 24-hour uninstall cooldown state and cycles are fully covered by tests.
* [ ] UI never becomes the source of truth for enforcement.

---

# 27. Fundamental Design Principle

The application should follow this rule:

```text
             DATABASE
                 │
                 ▼
        ┌─────────────────┐
        │ Enforcement     │
        │ Engine          │
        └────────┬────────┘
                 │
       ┌─────────┴─────────┐
       ▼                   ▼
  Normal Limits       Strict Kiosk
       │                   │
       ▼                   ▼
   Lockout             Lock Task
   Overlay             / Supported
   Warning             Enforcement
```

**The timer is data.**

**The UI displays the timer.**

**The enforcement engine enforces the timer.**

**The Android platform determines the maximum level of kiosk enforcement available to the application.**

Never make the UI responsible for enforcing restrictions.

