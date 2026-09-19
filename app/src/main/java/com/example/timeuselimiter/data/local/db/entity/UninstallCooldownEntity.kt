package com.example.timeuselimiter.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uninstall_cooldown")
data class UninstallCooldownEntity(
    @PrimaryKey val id: Int = 1,
    val startedAtWallMs: Long,
    val deadlineWallMs: Long,
    val startedAtElapsedMs: Long,
    val deadlineElapsedMs: Long,
    val decisionDeadlineWallMs: Long = 0,
    val decisionDeadlineElapsedMs: Long = 0,
    val bootId: String,
    val lastObservedWallMs: Long
)
