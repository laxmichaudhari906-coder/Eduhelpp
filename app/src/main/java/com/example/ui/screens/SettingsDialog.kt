package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.repository.EduHelpRepository

@Composable
fun SettingsDialog(
    repository: EduHelpRepository,
    pairingStatus: Any? = null,
    onDismiss: () -> Unit,
    onEnterPrivateMessenger: () -> Unit
) {
    EduHelpSettingsScreen(
        repository = repository,
        onDismiss = onDismiss,
        onEnterPrivateMessenger = onEnterPrivateMessenger
    )
}
