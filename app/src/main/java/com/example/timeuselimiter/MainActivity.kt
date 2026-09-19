package com.example.timeuselimiter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.room.Room
import com.example.timeuselimiter.data.local.db.AppDatabase
import com.example.timeuselimiter.data.repository.UninstallProtectionRepositoryImpl
import com.example.timeuselimiter.domain.time.SystemTimeSource
import com.example.timeuselimiter.presentation.uninstall.UninstallScreen
import com.example.timeuselimiter.presentation.uninstall.UninstallViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "time-use-limiter.db").build()
        val repository = UninstallProtectionRepositoryImpl(database.uninstallCooldownDao())
        val viewModel = UninstallViewModel(repository, SystemTimeSource)
        setContent { MaterialTheme { UninstallScreen(viewModel) } }
    }
}
