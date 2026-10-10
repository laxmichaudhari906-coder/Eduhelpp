package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.MessageStatus
import com.example.data.repository.EduHelpRepository
import com.example.data.repository.PairingState
import com.example.data.repository.PeerStatus
import com.example.service.MyFirebaseMessagingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Telegram Whisper UI Colors
private val OledBackground = Color(0xFF0A0D14)
private val OledSurface = Color(0xFF111625)
private val ReceivedBubbleColor = Color(0xFF1E2433)
private val SentGradientStart = Color(0xFF4F46E5)
private val SentGradientEnd = Color(0xFF4338CA)
private val ReadCyanTick = Color(0xFF38BDF8)
private val SentGrayTick = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PrivateChatScreen(
    repository: EduHelpRepository,
    onExitChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // Security: FLAG_SECURE prevents screenshots & hides recent apps thumbnail
    DisposableEffect(Unit) {
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        MyFirebaseMessagingService.isAppInForeground = true
        repository.startMessageObservation(coroutineScope)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            MyFirebaseMessagingService.isAppInForeground = false
            repository.setTyping(false)
            repository.stopMessageObservation()
        }
    }

    // Active Focus Lock: if onPause fires, instantly pop backstack to EduHelpHomeScreen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                onExitChat()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        onExitChat()
    }

    val currentUserId = remember { repository.getCurrentUserId() }
    val messages by repository.messages.collectAsState()
    val peerStatus by repository.peerActiveStatus.collectAsState()
    val pairingState by repository.pairingState.collectAsState()
    val peerName by repository.peerName.collectAsState()
    val peerIsTyping by repository.peerIsTyping.collectAsState()

    // Periodically update /users/{currentUid} with lastSeen = FieldValue.serverTimestamp() every 25 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            repository.updateUserLastSeen()
            delay(25_000L)
        }
    }

    val isConnected = pairingState is PairingState.Connected
    val displayedPartnerTitle = if (isConnected) peerName else "Not Connected"
    val avatarInitial = if (isConnected && displayedPartnerTitle.isNotBlank() && displayedPartnerTitle != "Not Connected") {
        displayedPartnerTitle.take(1).uppercase()
    } else {
        "?"
    }

    var messageInputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<ChatMessage?>(null) }
    var showPanicConfirmDialog by remember { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }

    // Media Recorder for Voice Notes
    var isRecordingAudio by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordedAudioFile by remember { mutableStateOf<File?>(null) }
    var recordingSeconds by remember { mutableStateOf(0) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingMedia = true
            coroutineScope.launch {
                val downloadUrl = repository.uploadImageCompressed(uri)
                isUploadingMedia = false
                if (downloadUrl != null) {
                    repository.sendMessage(
                        text = "",
                        imageUrl = downloadUrl,
                        replyToSnippet = replyingToMessage?.text?.take(40) ?: (if (replyingToMessage?.imageUrl != null) "Photo" else null),
                        replyToSender = replyingToMessage?.senderName
                    )
                    replyingToMessage = null
                }
            }
        }
    }

    // Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording(
                context = context,
                onStarted = { rec, file ->
                    mediaRecorder = rec
                    recordedAudioFile = file
                    isRecordingAudio = true
                    recordingSeconds = 0
                }
            )
        }
    }

    // Typing debounce
    var typingJob by remember { mutableStateOf<Job?>(null) }

    // Message List State
    val listState = rememberLazyListState()

    // Pagination check when scrolled towards end (older messages in reverseLayout)
    LaunchedEffect(listState.canScrollForward) {
        if (!listState.canScrollForward && messages.size >= 50) {
            repository.loadMoreMessages()
        }
    }

    // Timer loop for recording seconds
    LaunchedEffect(isRecordingAudio) {
        while (isRecordingAudio) {
            delay(1000L)
            recordingSeconds += 1
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(OledBackground)
            .statusBarsPadding(),
        containerColor = OledBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {

            // Whisper Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = OledSurface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onExitChat,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    if (isConnected) listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                    else listOf(Color(0xFFEF4444), Color(0xFF991B1B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarInitial,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Contact Name & Presence / Typing Status
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayedPartnerTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )

                        // Subtitle: Typing... or Presence Heartbeat
                        if (peerIsTyping) {
                            Text(
                                text = "Typing...",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            val subtitleText = if (isConnected) {
                                when (val s = peerStatus) {
                                    is PeerStatus.Active -> "🟢 Active"
                                    is PeerStatus.Offline -> "🔴 ${s.lastSeenText}"
                                }
                            } else {
                                "🔴 Not Connected"
                            }
                            val subtitleColor = if (isConnected) {
                                if (peerStatus is PeerStatus.Active) Color(0xFF10B981) else Color(0xFF94A3B8)
                            } else {
                                Color(0xFFEF4444)
                            }
                            Text(
                                text = subtitleText,
                                fontSize = 11.sp,
                                color = subtitleColor
                            )
                        }
                    }

                    // Call & Video Simulation Icons
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Call, contentDescription = "Encrypted Call", tint = Color(0xFF94A3B8))
                    }

                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Videocam, contentDescription = "Encrypted Video", tint = Color(0xFF94A3B8))
                    }

                    // Red Shield Panic Exit Button (🛡️)
                    IconButton(
                        onClick = { showPanicConfirmDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                            .testTag("panic_red_shield_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Urgent Panic Wipe",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }

            // Message List: LazyColumn with reverseLayout = true (index 0 is newest)
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isSentByMe = msg.senderId == currentUserId
                    SwipeToReplyMessageBubble(
                        message = msg,
                        isSentByMe = isSentByMe,
                        onSwipeReply = {
                            replyingToMessage = msg
                        },
                        onLongClick = {
                            selectedMessageForOptions = msg
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E2433).copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "🔒 End-to-end encrypted study channel",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Replying Quote Bar (anchored above input)
            AnimatedVisibility(visible = replyingToMessage != null) {
                replyingToMessage?.let { replyTarget ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1E2433),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF6366F1))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reply to ${replyTarget.senderName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF818CF8)
                                )
                                Text(
                                    text = replyTarget.text.ifBlank {
                                        if (replyTarget.imageUrl != null) "Photo"
                                        else "Voice Note (${replyTarget.voiceDuration})"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { replyingToMessage = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel Reply",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Audio Recording Bar OR Standard Input Bar (ChatInputBar)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = OledSurface,
                tonalElevation = 8.dp
            ) {
                if (isRecordingAudio) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Text(
                                text = "Recording: ${String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Cancel recording
                            IconButton(
                                onClick = {
                                    stopRecording(mediaRecorder)
                                    isRecordingAudio = false
                                    mediaRecorder = null
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF94A3B8))
                            }

                            // Send voice note
                            IconButton(
                                onClick = {
                                    val durationStr = String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)
                                    stopRecording(mediaRecorder)
                                    isRecordingAudio = false
                                    mediaRecorder = null
                                    val file = recordedAudioFile
                                    if (file != null && file.exists()) {
                                        isUploadingMedia = true
                                        coroutineScope.launch {
                                            val audioUrl = repository.uploadAudioFile(file)
                                            isUploadingMedia = false
                                            if (audioUrl != null) {
                                                repository.sendMessage(
                                                    text = "",
                                                    isVoiceNote = true,
                                                    voiceDuration = durationStr,
                                                    audioUrl = audioUrl,
                                                    replyToSnippet = replyingToMessage?.text?.take(40),
                                                    replyToSender = replyingToMessage?.senderName
                                                )
                                                replyingToMessage = null
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            ) {
                                Icon(Icons.Default.Done, contentDescription = "Send Audio", tint = Color.White)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Image attachment button
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Attach Image",
                                tint = Color(0xFF94A3B8)
                            )
                        }

                        OutlinedTextField(
                            value = messageInputText,
                            onValueChange = { newText ->
                                messageInputText = newText
                                // Typing indicator trigger
                                typingJob?.cancel()
                                repository.setTyping(true)
                                typingJob = coroutineScope.launch {
                                    delay(2000L)
                                    repository.setTyping(false)
                                }
                            },
                            placeholder = {
                                Text(
                                    "Whisper message...",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input"),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E2433),
                                unfocusedContainerColor = Color(0xFF1E2433),
                                focusedBorderColor = Color(0xFF4F46E5),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        if (isUploadingMedia) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(6.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                        } else if (messageInputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val textToSend = messageInputText.trim()
                                    val quote = replyingToMessage
                                    messageInputText = ""
                                    replyingToMessage = null
                                    repository.setTyping(false)
                                    coroutineScope.launch {
                                        repository.sendMessage(
                                            text = textToSend,
                                            replyToSnippet = quote?.text?.take(50),
                                            replyToSender = quote?.senderName
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(SentGradientStart, SentGradientEnd)
                                        )
                                    )
                                    .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Voice note recorder button
                        IconButton(
                            onClick = {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2433))
                                .testTag("chat_voice_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Hold to Record",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Panic Confirmation Dialog (🛡️)
    if (showPanicConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPanicConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Client-Side Chunked Panic Wipe",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            },
            text = {
                Text(
                    text = "This will immediately set isPurged: true to blank the UI on both devices, kick you to the home screen, and delete all messages in background batches of 500.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPanicConfirmDialog = false
                        coroutineScope.launch {
                            repository.executeUrgentPurge()
                            onExitChat()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.testTag("confirm_panic_button")
                ) {
                    Text("Purge & Exit Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Message Options Modal Bottom Sheet
    selectedMessageForOptions?.let { message ->
        val isOwnMessage = message.senderId == currentUserId
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForOptions = null },
            containerColor = OledSurface,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Message Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )

                HorizontalDivider(color = Color(0xFF1E2433))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = {
                            replyingToMessage = message
                            selectedMessageForOptions = null
                        })
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Reply, contentDescription = null, tint = Color(0xFF818CF8))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Reply to message", color = Color.White)
                }

                if (message.text.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {
                                clipboardManager.setText(AnnotatedString(message.text))
                                selectedMessageForOptions = null
                            })
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Copy text", color = Color.White)
                    }
                }

                if (isOwnMessage) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {
                                val msgId = message.id
                                selectedMessageForOptions = null
                                coroutineScope.launch {
                                    repository.deleteMessageForEveryone(msgId)
                                }
                            })
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .testTag("unsend_message_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Unsend / Delete for Everyone", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

private fun startRecording(
    context: Context,
    onStarted: (MediaRecorder, File) -> Unit
) {
    try {
        val audioFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }
        onStarted(recorder, audioFile)
    } catch (e: Exception) {
        android.util.Log.e("EduHelpAudio", "Error starting recording", e)
    }
}

private fun stopRecording(recorder: MediaRecorder?) {
    try {
        recorder?.stop()
        recorder?.release()
    } catch (_: Exception) { }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeToReplyMessageBubble(
    message: ChatMessage,
    isSentByMe: Boolean,
    onSwipeReply: () -> Unit,
    onLongClick: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    val draggableState = rememberDraggableState { delta ->
        val newOffset = (offsetX + delta).coerceIn(0f, 120f)
        offsetX = newOffset
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (offsetX > 60f) {
                        onSwipeReply()
                    }
                    offsetX = 0f
                }
            )
    ) {
        if (offsetX > 10f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = "Reply",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) },
            contentAlignment = if (isSentByMe) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isSentByMe) 18.dp else 4.dp,
                            bottomEnd = if (isSentByMe) 4.dp else 18.dp
                        )
                    )
                    .combinedClickable(
                        onClick = { },
                        onLongClick = onLongClick
                    ),
                color = if (isSentByMe) Color.Transparent else ReceivedBubbleColor
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isSentByMe) {
                                Modifier.background(
                                    Brush.linearGradient(
                                        listOf(SentGradientStart, SentGradientEnd)
                                    )
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        // Embedded Quoted Snippet if reply
                        if (!message.replyToSnippet.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSentByMe) Color(0x33000000) else Color(0x22FFFFFF),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(28.dp)
                                            .background(if (isSentByMe) Color(0xFFC7D2FE) else Color(0xFF818CF8))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = message.replyToSender ?: "Study Partner",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSentByMe) Color(0xFFE0E7FF) else Color(0xFF818CF8)
                                        )
                                        Text(
                                            text = message.replyToSnippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Image Rendering via Coil AsyncImage
                        if (!message.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = message.imageUrl,
                                contentDescription = "Attached Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            if (message.text.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        // Voice Note Waveform Player
                        if (message.isVoiceNote) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isAudioPlaying) {
                                            mediaPlayer?.pause()
                                            isAudioPlaying = false
                                        } else {
                                            val url = message.audioUrl
                                            if (!url.isNullOrBlank()) {
                                                mediaPlayer?.release()
                                                val player = MediaPlayer().apply {
                                                    setDataSource(url)
                                                    prepareAsync()
                                                    setOnPreparedListener { start() }
                                                    setOnCompletionListener { isAudioPlaying = false }
                                                }
                                                mediaPlayer = player
                                                isAudioPlaying = true
                                            } else {
                                                isAudioPlaying = !isAudioPlaying
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSentByMe) Color(0x33FFFFFF) else Color(0xFF6366F1))
                                ) {
                                    Icon(
                                        imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isAudioPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        listOf(8, 14, 20, 10, 18, 24, 12, 16, 22, 14, 18, 12, 8).forEach { h ->
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(h.dp)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(if (isAudioPlaying) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.7f))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = message.voiceDuration.ifBlank { "0:14" },
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        } else if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                color = if (isSentByMe) Color.White else Color(0xFFF4F4F5),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp
                            )
                        }

                        // Timestamp & 3-Tier Tick Receipts
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val timeStr = remember(message.timestamp) {
                                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                                sdf.format(Date(message.timestamp))
                            }
                            Text(
                                text = timeStr,
                                fontSize = 10.sp,
                                color = if (isSentByMe) Color(0xFFC7D2FE) else Color(0xFF94A3B8)
                            )

                            if (isSentByMe) {
                                when (message.status) {
                                    MessageStatus.SENT -> {
                                        // 1 Gray Tick (Sent)
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = "Sent",
                                            tint = SentGrayTick,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    MessageStatus.DELIVERED -> {
                                        // 2 Gray Ticks (Delivered)
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Delivered",
                                            tint = SentGrayTick,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    MessageStatus.READ -> {
                                        // 2 Cyan Ticks (Read)
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Read",
                                            tint = ReadCyanTick,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
