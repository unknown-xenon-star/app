package com.example.timeuselimiter.presentation.uninstall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val value = state) {
                UninstallProtectionState.None -> {
                    Text("App protection is active", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::requestUninstall) { Text("Request uninstall") }
                }
                is UninstallProtectionState.Waiting -> {
                    Text("Uninstall requested", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(formatRemaining(value.remainingMs), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("You can decide after 24 hours.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::keepApp) { Text("Cancel and keep app protected") }
                }
                is UninstallProtectionState.Confirmation -> {
                    Text("Do you still want to uninstall?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Window closes in ${formatRemaining(value.remainingMs)}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("If the window lapses, a new 24-hour cooldown is required.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::continueUninstall) { Text("Continue Uninstall") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::keepApp) { Text("Keep App") }
                }
                is UninstallProtectionState.Reflecting -> {
                    Text("Uninstalling in…", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("${value.remainingMs / 1000}", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Take a breath. Uninstall will start automatically.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun formatRemaining(milliseconds: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
