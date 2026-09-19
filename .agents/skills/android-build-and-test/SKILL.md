---
name: android-build-and-test
description: >-
  Use this skill for running Gradle builds, executing Android unit tests (JUnit, MockK, Robolectric),
  running Android Lint, and verifying code quality for the Android Time Use Limiter project.
---

# Android Build & Test Skill

This skill provides step-by-step procedures for building, linting, and testing the Android Time Use Limiter project.

---

## 1. Gradle Build & Assembly Commands

Use the Gradle Wrapper (`./gradlew` on Linux/macOS or `gradlew.bat` on Windows):

- **Compile & Assemble Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Check Build Health / Linting**:
  ```bash
  ./gradlew lintDebug
  ```
- **Clean Workspace**:
  ```bash
  ./gradlew clean
  ```

---

## 2. Unit & Integration Testing Guidelines

### Frameworks & Tools
- **Test Framework**: JUnit 5 / JUnit 4
- **Mocking**: MockK (`io.mockk:mockk`)
- **Coroutines Testing**: `kotlinx-coroutines-test` (`StandardTestDispatcher`, `runTest`, `advanceUntilIdle`)
- **Flow Assertions**: Turbine (`app.cash.turbine:turbine`)
- **Android Framework Shadowing**: Robolectric (for testing components relying on Android `Context`, `AppOpsManager`, `UsageStatsManager` in JVM unit tests)

### Running Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Writing a UseCase Unit Test Example

```kotlin
class CheckAppLimitReachedUseCaseTest {

    private val appLimitRepository = mockk<AppLimitRepository>()
    private val appUsageRepository = mockk<AppUsageRepository>()
    private lateinit var useCase: CheckAppLimitReachedUseCase

    @BeforeEach
    fun setUp() {
        useCase = CheckAppLimitReachedUseCase(appLimitRepository, appUsageRepository)
    }

    @Test
    fun `when usage exceeds limit then return true`() = runTest {
        val packageName = "com.example.socialapp"
        val limit = AppLimitRule(
            packageName = packageName,
            dailyLimitMillis = 3600_000L, // 1 hour
            isEnabled = true
        )
        
        coEvery { appLimitRepository.getLimitForPackage(packageName) } returns limit
        coEvery { appUsageRepository.getTodayUsageMillis(packageName) } returns 3700_000L // 1 hr 1 min

        val result = useCase(packageName)

        assertTrue(result.isBlocked)
        assertEquals(100_000L, result.exceededByMillis)
    }
}
```

---

## 3. UI & Jetpack Compose Testing

- Test composables using `createComposeRule()` from `androidx.compose.ui.test:ui-test-junit4`.
- Validate that the lockout screen renders correct app names, usage metrics, and PIN keypad inputs properly.
