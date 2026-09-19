package com.example.timeuselimiter.presentation.uninstall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import java.util.concurrent.TimeUnit

@Composable
fun UninstallScreen(viewModel: UninstallViewModel) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val value = state) {
            UninstallProtectionState.None -> {
                Text("App protection is active", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = viewModel::requestUninstall) { Text("Request uninstall") }
            }
            is UninstallProtectionState.Waiting -> {
                Text("Uninstall requested")
                Text(formatRemaining(value.remainingMs))
                Text("You can decide after 24 hours.")
            }
            is UninstallProtectionState.Confirmation -> {
                Text("Do you still want to uninstall?", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = viewModel::keepApp) { Text("Keep App") }
                Button(onClick = viewModel::continueUninstall) { Text("Continue Uninstall") }
            }
        }
    }
}

private fun formatRemaining(milliseconds: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(milliseconds.coerceAtLeast(0))
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.coerceAtLeast(0)) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0)) % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
