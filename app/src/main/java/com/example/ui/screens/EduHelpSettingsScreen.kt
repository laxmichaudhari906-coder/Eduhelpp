package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.CredentialManager
import com.example.data.repository.EduHelpRepository
import com.example.data.repository.PairingState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EduHelpSettingsScreen(
    repository: EduHelpRepository,
    onDismiss: () -> Unit,
    onEnterPrivateMessenger: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val currentUser by repository.currentUser.collectAsState()
    val pairingState by repository.pairingState.collectAsState()

    var pairInput by remember { mutableStateOf(repository.getSavedPairId() ?: "") }
    var isPairingLoading by remember { mutableStateOf(false) }
    var pairingMessage by remember { mutableStateOf<String?>(null) }
    var isAuthLoading by remember { mutableStateOf(false) }
    var authErrorMsg by remember { mutableStateOf<String?>(null) }

    // Dialog state for normal FAQ
    var showFaqDialog by remember { mutableStateOf(false) }
    // Dialog state for security verification gate (triggered on long press)
    var showVerificationDialog by remember { mutableStateOf(false) }
    var verificationPasscode by remember { mutableStateOf("") }
    var verificationError by remember { mutableStateOf<String?>(null) }

    val isConnected = pairingState is PairingState.Connected

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edu Help Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Real Google Authentication (Firebase Auth)
                Text(
                    text = "Account Authentication",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (currentUser == null) {
                    // Sign in with Google Button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Sign in to synchronize study cards & verify cohort companion pairing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (activity != null) {
                                        isAuthLoading = true
                                        authErrorMsg = null
                                        repository.onGoogleSignInClicked(
                                            activity = activity,
                                            credentialManager = credentialManager,
                                            onAuthSuccess = {
                                                isAuthLoading = false
                                            },
                                            onAuthError = { err ->
                                                isAuthLoading = false
                                                authErrorMsg = err
                                            },
                                            scope = coroutineScope,
                                            onAuthCancelled = {
                                                isAuthLoading = false
                                            }
                                        )
                                    }
                                },
                                enabled = !isAuthLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_sign_in_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isAuthLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (authErrorMsg != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = authErrorMsg!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else {
                    // Authenticated Google User Card
                    val user = currentUser!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (user.displayName ?: user.email ?: "U").take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName ?: user.email?.substringBefore("@") ?: "Student User",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = user.email ?: "Google Account",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "UID: " + user.uid.take(12) + "...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    repository.signOut(context, coroutineScope) {}
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFEF4444)
                                )
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Strict Server-Side Pairing
                Text(
                    text = "Strict Server Companion Pairing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Both students must supply the exact same Pair ID. Access to private channel requires server verification of 2 joined users.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Live Server-Verified Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Server Status:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val (badgeBg, badgeText) = when (val state = pairingState) {
                        is PairingState.NotConnected -> Color(0xFFEF4444) to "🔴 Not Connected"
                        is PairingState.WaitingForPartner -> Color(0xFFF59E0B) to "🟡 Waiting for partner... (1/2 joined)"
                        is PairingState.Connected -> Color(0xFF10B981) to "🟢 Connected (2/2 joined)"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBg.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeBg.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeBg,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input Field for Student Pair ID
                OutlinedTextField(
                    value = pairInput,
                    onValueChange = { pairInput = it },
                    label = { Text("Student Pair ID (e.g. PAIR-2026)") },
                    placeholder = { Text("Enter shared pair code") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_pair_id_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (pairingMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pairingMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (pairInput.isNotBlank()) {
                                isPairingLoading = true
                                coroutineScope.launch {
                                    val res = repository.connectPair(pairInput)
                                    isPairingLoading = false
                                    if (res.isSuccess) {
                                        pairingMessage = "Pair ID registered. Server validating companion connection..."
                                    } else {
                                        pairingMessage = "Pairing error: ${res.exceptionOrNull()?.localizedMessage}"
                                    }
                                }
                            }
                        },
                        enabled = !isPairingLoading && pairInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connect_pair_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isPairingLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Connect Companion")
                        }
                    }

                    if (pairingState !is PairingState.NotConnected) {
                        OutlinedButton(
                            onClick = {
                                repository.disconnectPair()
                                pairInput = ""
                                pairingMessage = "Unlinked."
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444)
                            )
                        ) {
                            Text("Unlink")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: Customer Care / FAQ Gateway
                Text(
                    text = "Support & Verification Gateway",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap for FAQ. Long-press Customer Care for Student Verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = {
                                showFaqDialog = true
                            },
                            onLongClick = {
                                showVerificationDialog = true
                            }
                        )
                        .testTag("customer_care_gateway"),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Customer Care / FAQ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Frequently asked study questions & support",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Edu Help v3.0 (Build 3)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Standard FAQ Dialog
    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = {
                Text("Edu Help - FAQ & Support", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Strict Server Pairing",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Access to secret study channel strictly requires 2 users joined in the Firestore pair document.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "2. Google Sign-In",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Authenticating with Google saves your profile to Firestore so your study partner can see your real display name.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showFaqDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    // Security Verification Gateway Dialog (Triggered by LONG PRESS)
    if (showVerificationDialog) {
        AlertDialog(
            onDismissRequest = {
                showVerificationDialog = false
                verificationPasscode = ""
                verificationError = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Student Verification",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter passcode 4829 to verify cohort credential and open private study channel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = verificationPasscode,
                        onValueChange = {
                            verificationPasscode = it
                            verificationError = null
                        },
                        label = { Text("Passcode") },
                        placeholder = { Text("Enter 4829") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_verification_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (verificationError != null) {
                        Text(
                            text = verificationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val savedPair = repository.getSavedPairId() ?: ""
                        val isPasscodeValid = verificationPasscode.trim() == "4829" ||
                                (savedPair.isNotBlank() && verificationPasscode.trim().equals(savedPair, ignoreCase = true))

                        if (!isPasscodeValid) {
                            verificationError = "Invalid passcode. Please enter 4829."
                            return@Button
                        }

                        // STRICT SERVER PAIRING GUARD: NEVER ALLOW UNLESS SERVER CONFIRMS 2 USERS JOINED
                        if (pairingState !is PairingState.Connected) {
                            verificationError = "Server Error: Pairing incomplete. Your partner has not joined Pair ID ($savedPair) yet."
                        } else {
                            showVerificationDialog = false
                            onDismiss()
                            onEnterPrivateMessenger()
                        }
                    },
                    modifier = Modifier.testTag("verify_passcode_button")
                ) {
                    Text("Verify & Enter")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showVerificationDialog = false
                        verificationPasscode = ""
                        verificationError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
