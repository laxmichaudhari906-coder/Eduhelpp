package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.repository.EduHelpRepository
import com.example.ui.screens.EduHelpHomeScreen
import com.example.ui.screens.EduHelpSettingsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PrivateChatScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
    ONBOARDING,
    EDU_HELP_HOME,
    PRIVATE_CHAT
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: EduHelpRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = EduHelpRepository(applicationContext)

        checkBatteryOptimizations()
        syncFcmTokenOnLaunch()

        setContent {
            MyApplicationTheme {
                EduHelpApp(repository = repository)
            }
        }
    }

    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val pkg = packageName
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(pkg)) {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$pkg")
                    )
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Battery optimization prompt exception", e)
            }
        }
    }

    private fun syncFcmTokenOnLaunch() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && !task.result.isNullOrBlank()) {
                        val token = task.result
                        Log.d("EduHelpFCM", "App launch FCM token retrieved: $token")
                        repository.updateFcmToken(token)
                    } else {
                        Log.d("EduHelpFCM", "Launch FCM token not available: ${task.exception?.message}")
                    }
                }
        } catch (e: Exception) {
            Log.d("EduHelpFCM", "Launch FCM sync safe catch: ${e.message}")
        }
    }
}

@Composable
fun EduHelpApp(repository: EduHelpRepository) {
    val currentUser by repository.currentUser.collectAsState()

    // Innocent Onboarding & Authentication routing:
    // On launch, check currentUser. If null, route to OnboardingScreen. If logged in, route directly to EduHelpHomeScreen.
    var currentScreen by remember {
        mutableStateOf(
            if (repository.currentUser.value == null) AppScreen.ONBOARDING else AppScreen.EDU_HELP_HOME
        )
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Security & Active Focus Lock:
    // If onPause fires, instantly pop the backstack to EduHelpHomeScreen so chat is never exposed.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (currentScreen == AppScreen.PRIVATE_CHAT) {
                    currentScreen = AppScreen.EDU_HELP_HOME
                    showSettingsDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            AppScreen.ONBOARDING -> {
                OnboardingScreen(
                    repository = repository,
                    onLoginSuccess = {
                        currentScreen = AppScreen.EDU_HELP_HOME
                    }
                )
            }

            AppScreen.EDU_HELP_HOME -> {
                EduHelpHomeScreen(
                    repository = repository,
                    onOpenSettings = { showSettingsDialog = true }
                )
            }

            AppScreen.PRIVATE_CHAT -> {
                BackHandler {
                    currentScreen = AppScreen.EDU_HELP_HOME
                }
                PrivateChatScreen(
                    repository = repository,
                    onExitChat = {
                        currentScreen = AppScreen.EDU_HELP_HOME
                    }
                )
            }
        }

        // Settings Dialog with Student Companion Pairing & Gateway
        if (showSettingsDialog) {
            EduHelpSettingsScreen(
                repository = repository,
                onDismiss = { showSettingsDialog = false },
                onEnterPrivateMessenger = {
                    showSettingsDialog = false
                    currentScreen = AppScreen.PRIVATE_CHAT
                }
            )
        }
    }
}
