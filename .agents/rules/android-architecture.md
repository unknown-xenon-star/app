# Android Time Use Limiter - Architecture & Pattern Rules

This document outlines the detailed architectural guidelines and data flow models for the Android Time Use Limiter project.

---

## 1. Domain-Driven Design & Layer Structure

The project is structured into modular layers adhering to Clean Architecture:

```
app/
 ├── data/
 │    ├── local/
 │    │    ├── db/              # Room Database, DAOs, Entity definitions
 │    │    │    ├── AppLimitDao.kt
 │    │    │    ├── UsageLogDao.kt
 │    │    │    └── AppDatabase.kt
 │    │    ├── datastore/       # User Preferences & Security Settings
 │    │    │    └── SettingsDataStore.kt
 │    │    └── stats/           # Android System API Data Sources
 │    │         └── UsageStatsDataSource.kt
 │    └── repository/           # Repository Implementations
 │         ├── AppUsageRepositoryImpl.kt
 │         ├── AppLimitRepositoryImpl.kt
 │         └── SettingsRepositoryImpl.kt
 ├── domain/
 │    ├── model/                # Pure Kotlin domain data models
 │    │    ├── AppUsageSummary.kt
 │    │    ├── AppLimitRule.kt
 │    │    ├── LimitType.kt      # DAILY_TIME, LAUNCH_COUNT, SCHEDULED_WINDOW
 │    │    └── LockoutState.kt
 │    ├── repository/           # Abstract Repository Interfaces
 │    │    ├── AppUsageRepository.kt
 │    │    └── AppLimitRepository.kt
 │    └── usecase/              # Single-responsibility business logic
 │         ├── GetDailyUsageStatsUseCase.kt
 │         ├── CheckAppLimitReachedUseCase.kt
 │         ├── SetAppLimitUseCase.kt
 │         ├── ValidateSecurityPinUseCase.kt
 │         └── GrantTemporaryOverrideUseCase.kt
 ├── presentation/
 │    ├── common/               # Shared Compose UI components & design system
 │    │    ├── theme/           # Color, Typography, Shapes
 │    │    └── components/      # AppUsageCard, CircularTimeProgress, PinDialog
 │    ├── dashboard/            # Screen time charts & daily overview
 │    ├── limits/               # App limits management & list
 │    ├── schedule/             # Bedtime / Focus session configuration
 │    ├── lockout/              # Lockout Overlay UI (displayed over blocked apps)
 │    └── onboarding/           # Permission grants wizard
 └── service/
      ├── monitoring/           # Foreground Service for real-time app tracking
      └── receiver/             # BootCompletedReceiver & AlarmReceiver
```

---

## 2. Unidirectional Data Flow (UDF) & MVI State Management

All UI screens must implement Unidirectional Data Flow:

```
        ┌──────────────────────────────────────────────┐
        │                                              │
        ▼                                              │
┌──────────────┐         ┌───────────┐         ┌───────────────┐
│ User Action  │ ──────> │ ViewModel │ ──────> │ Immutable     │
│ (UI Event)   │         │ (Reducer) │         │ StateFlow<S>  │
└──────────────┘         └───────────┘         └───────┬───────┘
                                                       │
                                                       ▼
                                               ┌───────────────┐
                                               │ Jetpack       │
                                               │ Compose Screen│
                                               └───────────────┘
```

### ViewModel Rules:
1. **Single State Source**: A single `StateFlow<UiState>` per screen.
2. **Side Effects**: One-off events (e.g., showing a Toast, navigating to a new screen, prompting for permission) must be modeled using `Channel<UiEffect>` exposed as `ReceiveChannel` or `Flow`.
3. **No Direct Context References**: Use dependency injection to supply system abstractions.

---

## 3. Storage & Database Schema Design

### Room Entities

1. **`AppLimitEntity`**:
   - `packageName` (Primary Key): String
   - `appName`: String
   - `dailyTimeLimitMillis`: Long
   - `hourlyTimeLimitMillis`: Long?
   - `maxLaunchCount`: Int?
   - `isEnabled`: Boolean
   - `blockDaysMask`: Int (bitmask for days of the week)
   - `category`: String (Social, Entertainment, Productivity, Games, Other)

2. **`FocusScheduleEntity`**:
   - `id` (Primary Key, Auto-generate): Long
   - `scheduleName`: String (e.g., "Deep Work", "Bedtime")
   - `startTime`: Int (minute of day, 0-1439)
   - `endTime`: Int (minute of day, 0-1439)
   - `daysOfWeek`: List<DayOfWeek>
   - `blockedPackages`: List<String>
   - `isActive`: Boolean

3. **`UsageHistoryEntity`**:
   - `id`: Long (Auto-generate)
   - `packageName`: String
   - `date`: LocalDate (epoch day)
   - `totalForegroundMillis`: Long
   - `launchCount`: Int

---

## 4. Background Monitoring Strategy

- **Real-Time Limit Detection**:
  - The foreground monitoring service listens to foreground changes via `UsageStatsManager.queryEvents` in high-frequency windows or via an optional `AccessibilityService` (when ultra-low latency blocking is required).
  - When a target app's usage crosses its configured threshold, the service triggers the `LockoutOverlayManager` to render the blocking view immediately and broadcasts an intent to return the user to the Home screen (`Intent.ACTION_MAIN`, `CATEGORY_HOME`).
- **Low Overhead**:
  - Cache calculated usage intervals in memory with a fast invalidation window (e.g. 5-10 seconds) to prevent redundant disk/database hits during continuous usage.
