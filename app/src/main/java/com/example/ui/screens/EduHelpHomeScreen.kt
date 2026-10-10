package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.repository.EduHelpRepository

@Composable
fun EduHelpHomeScreen(
    repository: EduHelpRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudyDeckScreen(
        repository = repository,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}
