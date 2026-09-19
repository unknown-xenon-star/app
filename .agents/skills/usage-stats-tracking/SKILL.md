---
name: usage-stats-tracking
description: >-
  Use this skill when implementing or modifying Android screen time tracking, querying UsageStatsManager,
  calculating daily/weekly app usage statistics, or handling the PACKAGE_USAGE_STATS permission lifecycle.
---

# Android Usage Stats Tracking Skill

This skill provides step-by-step procedures and design patterns for interacting with Android's `UsageStatsManager` and `AppOpsManager`.

---

## 1. Checking and Requesting Usage Stats Permission

The `PACKAGE_USAGE_STATS` permission is a special app-op permission and cannot be requested via standard runtime dialogs.

### Step 1: Permission Verification
```kotlin
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
```

### Step 2: Redirecting User to Settings
```kotlin
fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    // Fallback if package-specific URI is not supported on OEM OS
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
```

---

## 2. Querying Daily Screen Time

### Method A: `queryUsageStats` (Fast Batch Query)
Use this for aggregating overall daily screen time per app:

```kotlin
fun getDailyUsageStats(context: Context, startTime: Long, endTime: Long): Map<String, Long> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val stats = usageStatsManager.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        startTime,
        endTime
    ) ?: return emptyMap()

    val resultMap = mutableMapOf<String, Long>()
    for (stat in stats) {
        if (stat.totalTimeInForeground > 0) {
            resultMap[stat.packageName] = (resultMap[stat.packageName] ?: 0L) + stat.totalTimeInForeground
        }
    }
    return resultMap
}
```

### Method B: `queryEvents` (Accurate Timeline Reconstruction)
Use `queryEvents` to reconstruct exact app switch timestamps, calculate launch counts, and prevent stale cache issues:

```kotlin
fun getAppForegroundDurationFromEvents(
    context: Context,
    targetPackage: String,
    startTime: Long,
    endTime: Long
): Long {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val events = usageStatsManager.queryEvents(startTime, endTime)
    val event = UsageEvents.Event()

    var totalDuration = 0L
    var lastForegroundTime = 0L

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.packageName != targetPackage) continue

        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED,
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                lastForegroundTime = event.timeStamp
            }
            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED,
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                if (lastForegroundTime > 0L) {
                    totalDuration += (event.timeStamp - lastForegroundTime)
                    lastForegroundTime = 0L
                }
            }
        }
    }

    // If the app is currently in the foreground
    if (lastForegroundTime > 0L) {
        totalDuration += (endTime - lastForegroundTime)
    }

    return totalDuration
}
```

---

## 3. Handling Edge Cases

1. **Midnight Rollover**:
   - `startTime` must be dynamically computed to the start of the current day in the user's local timezone (`ZoneId.systemDefault()`).
   - `val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()`.
2. **System Launcher & Essential Apps Filter**:
   - Filter out the default home launcher, phone dialer, emergency packages, and self (`context.packageName`) from app blocking lists.
3. **Split-Screen & Floating Windows**:
   - In Android 7.0+, multi-window allows multiple apps to be resumed. Track multiple active sessions if `UsageEvents.Event.ACTIVITY_RESUMED` occurs across different packages.
