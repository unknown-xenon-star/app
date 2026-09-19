package com.example.timeuselimiter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.timeuselimiter.data.local.db.dao.UninstallCooldownDao
import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity

@Database(entities = [UninstallCooldownEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uninstallCooldownDao(): UninstallCooldownDao
}
