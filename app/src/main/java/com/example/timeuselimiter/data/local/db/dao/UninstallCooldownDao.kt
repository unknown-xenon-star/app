package com.example.timeuselimiter.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity

@Dao
interface UninstallCooldownDao {
    @Query("SELECT * FROM uninstall_cooldown WHERE id = 1")
    suspend fun get(): UninstallCooldownEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(value: UninstallCooldownEntity)

    @Query("DELETE FROM uninstall_cooldown WHERE id = 1")
    suspend fun clear()
}
