package com.example.timeuselimiter.domain.time

interface TimeSource {
    fun wallClockMs(): Long
    fun elapsedRealtimeMs(): Long
    fun bootId(): String
}

object SystemTimeSource : TimeSource {
    override fun wallClockMs() = System.currentTimeMillis()
    override fun elapsedRealtimeMs() = android.os.SystemClock.elapsedRealtime()
    override fun bootId(): String = runCatching {
        java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()
    }.getOrDefault("unknown")
}
