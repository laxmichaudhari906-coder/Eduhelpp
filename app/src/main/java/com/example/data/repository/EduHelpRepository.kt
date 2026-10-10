package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.MessageStatus
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class PairingState {
    object NotConnected : PairingState()
    data class WaitingForPartner(val pairId: String, val myUid: String) : PairingState()
    data class Connected(val pairId: String, val roomId: String, val peerUid: String) : PairingState()
}

sealed class PeerStatus {
    object Active : PeerStatus()
    data class Offline(val lastSeenText: String) : PeerStatus()
}

class EduHelpRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("edu_help_prefs", Context.MODE_PRIVATE)

    private val auth: FirebaseAuth? by lazy {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val dbId = try {
                context.getString(R.string.firestore_database_id)
            } catch (_: Exception) {
                ""
            }
            if (dbId.isNotBlank()) {
                FirebaseFirestore.getInstance(dbId)
            } else {
                FirebaseFirestore.getInstance()
            }
        } catch (_: Exception) {
            null
        }
    }

    private val storage: FirebaseStorage? by lazy {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            FirebaseStorage.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.NotConnected)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private val _peerName = MutableStateFlow<String>("Not Connected")
    val peerName: StateFlow<String> = _peerName.asStateFlow()

    private val _peerActiveStatus = MutableStateFlow<PeerStatus>(PeerStatus.Offline("Not Connected"))
    val peerActiveStatus: StateFlow<PeerStatus> = _peerActiveStatus.asStateFlow()

    private val _peerIsTyping = MutableStateFlow<Boolean>(false)
    val peerIsTyping: StateFlow<Boolean> = _peerIsTyping.asStateFlow()

    // Descending messages for reverseLayout = true (index 0 is newest)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var pairListenerRegistration: ListenerRegistration? = null
    private var peerProfileRegistration: ListenerRegistration? = null
    private var messageListenerRegistration: ListenerRegistration? = null
    private var heartbeatScope: CoroutineScope? = null

    private var currentMessageLimit: Long = 50L
    private var lastLoadedSnapshot: DocumentSnapshot? = null

    init {
        try {
            auth?.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                _currentUser.value = user
                if (user != null) {
                    saveUserProfile(
                        uid = user.uid,
                        displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Student User",
                        email = user.email ?: ""
                    )
                }
            }
        } catch (_: Exception) { }

        checkInitialPairingState()
    }

    fun getCurrentUserId(): String {
        return auth?.currentUser?.uid ?: prefs.getString("local_fallback_uid", null) ?: run {
            val fallback = "student_" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString("local_fallback_uid", fallback).apply()
            fallback
        }
    }

    fun getUserDisplayName(): String {
        return auth?.currentUser?.displayName
            ?: auth?.currentUser?.email?.substringBefore("@")
            ?: prefs.getString("student_alias", "Student User")
            ?: "Student User"
    }

    fun updateFcmToken(token: String) {
        val uid = getCurrentUserId()
        try {
            firestore?.collection("users")?.document(uid)
                ?.set(mapOf("fcmToken" to token), SetOptions.merge())
        } catch (_: Exception) { }
    }

    fun onGoogleSignInClicked(
        activity: Activity,
        credentialManager: CredentialManager,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth?.signInWithCredential(authCredential)?.await()
                    val user = authResult?.user
                    if (user != null) {
                        saveUserProfile(
                            uid = user.uid,
                            displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Student User",
                            email = user.email ?: ""
                        )
                        onAuthSuccess(user)
                    } else {
                        onAuthError("Firebase authentication returned null user")
                    }
                } else {
                    onAuthError("Unexpected credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e("EduHelpAuth", "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Sign in failed")
            }
        }
    }

    fun saveUserProfile(uid: String, displayName: String, email: String) {
        try {
            val userMap = mapOf(
                "uid" to uid,
                "displayName" to displayName,
                "email" to email,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore?.collection("users")?.document(uid)?.set(userMap, SetOptions.merge())
        } catch (e: Exception) {
            Log.w("EduHelpRepo", "Failed to save user profile: ${e.message}")
        }
    }

    fun signOut(context: Context, scope: CoroutineScope, onComplete: () -> Unit) {
        try {
            auth?.signOut()
        } catch (_: Exception) { }
        val credentialManager = CredentialManager.create(context)
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) { }
            disconnectPair()
            onComplete()
        }
    }

    private fun checkInitialPairingState() {
        val savedPairId = prefs.getString("saved_pair_id", null)
        if (!savedPairId.isNullOrBlank()) {
            observePairDocument(savedPairId)
        } else {
            _pairingState.value = PairingState.NotConnected
            _peerName.value = "Not Connected"
        }
    }

    fun getSavedPairId(): String? = prefs.getString("saved_pair_id", null)
    fun getSavedRoomId(): String? = prefs.getString("saved_room_id", null)

    suspend fun connectPair(pairId: String): Result<PairingState> {
        val cleanPairId = pairId.trim().uppercase()
        if (cleanPairId.isBlank()) {
            return Result.failure(IllegalArgumentException("Pair ID cannot be empty"))
        }

        val myUid = getCurrentUserId()
        val roomId = "room_$cleanPairId"
        val fs = firestore

        if (fs == null) {
            prefs.edit()
                .putString("saved_pair_id", cleanPairId)
                .putString("saved_room_id", roomId)
                .apply()
            _pairingState.value = PairingState.Connected(cleanPairId, roomId, "peer_offline")
            _peerName.value = "Study Partner"
            return Result.success(_pairingState.value)
        }

        try {
            val pairRef = fs.collection("pairs").document(cleanPairId)
            val pairUpdate = mapOf(
                "pairId" to cleanPairId,
                "users" to FieldValue.arrayUnion(myUid),
                "pairedRoomId" to roomId,
                "isPurged" to false,
                "lastActiveTimestamp" to System.currentTimeMillis()
            )
            pairRef.set(pairUpdate, SetOptions.merge()).await()

            try {
                fs.collection("users").document(myUid)
                    .set(mapOf("paired_room_id" to roomId, "paired_id" to cleanPairId), SetOptions.merge())
            } catch (_: Exception) { }

            prefs.edit()
                .putString("saved_pair_id", cleanPairId)
                .putString("saved_room_id", roomId)
                .apply()

            observePairDocument(cleanPairId)
            return Result.success(_pairingState.value)
        } catch (e: Exception) {
            Log.w("EduHelpRepo", "Firestore pairing note: using local resilient fallback", e)
            prefs.edit()
                .putString("saved_pair_id", cleanPairId)
                .putString("saved_room_id", roomId)
                .apply()
            _pairingState.value = PairingState.Connected(cleanPairId, roomId, "peer_fallback")
            _peerName.value = "Study Partner"
            return Result.success(_pairingState.value)
        }
    }

    fun disconnectPair() {
        prefs.edit()
            .remove("saved_pair_id")
            .remove("saved_room_id")
            .remove("saved_partner_name")
            .apply()

        _pairingState.value = PairingState.NotConnected
        _peerName.value = "Not Connected"
        _peerActiveStatus.value = PeerStatus.Offline("Not Connected")
        _peerIsTyping.value = false

        stopPairObservation()
        stopMessageObservation()
    }

    private fun observePairDocument(pairId: String) {
        stopPairObservation()
        val myUid = getCurrentUserId()
        val roomId = "room_$pairId"
        val fs = firestore ?: return

        try {
            pairListenerRegistration = fs.collection("pairs")
                .document(pairId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        _pairingState.value = PairingState.NotConnected
                        _peerName.value = "Not Connected"
                        return@addSnapshotListener
                    }

                    val isPurged = snapshot.getBoolean("isPurged") ?: false
                    if (isPurged) {
                        _messages.value = emptyList()
                    }

                    val users = (snapshot.get("users") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    val lastActive = snapshot.getLong("lastActiveTimestamp") ?: 0L
                    updatePeerStatus(lastActive)

                    if (users.size < 2) {
                        _pairingState.value = PairingState.WaitingForPartner(pairId, myUid)
                        _peerName.value = "Waiting for partner..."
                        peerProfileRegistration?.remove()
                        _peerIsTyping.value = false
                    } else {
                        val peerUid = users.firstOrNull { it != myUid } ?: ""
                        _pairingState.value = PairingState.Connected(pairId, roomId, peerUid)

                        // Check typing indicator for peer
                        val peerTypingKey = "typing_${peerUid}"
                        val isPeerTyping = snapshot.getBoolean(peerTypingKey) ?: false
                        _peerIsTyping.value = isPeerTyping

                        if (peerUid.isNotBlank()) {
                            observePeerProfile(peerUid)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("EduHelpRepo", "Error observing pair document", e)
        }
    }

    fun updateUserLastSeen() {
        val myUid = getCurrentUserId()
        try {
            firestore?.collection("users")?.document(myUid)
                ?.set(mapOf("lastSeen" to FieldValue.serverTimestamp()), SetOptions.merge())
        } catch (_: Exception) { }
    }

    private fun observePeerProfile(peerUid: String) {
        peerProfileRegistration?.remove()
        val fs = firestore ?: return
        try {
            peerProfileRegistration = fs.collection("users")
                .document(peerUid)
                .addSnapshotListener { uSnap, uErr ->
                    if (uErr != null || uSnap == null || !uSnap.exists()) {
                        _peerName.value = "Study Partner"
                        return@addSnapshotListener
                    }
                    val name = uSnap.getString("displayName")
                        ?: uSnap.getString("email")?.substringBefore("@")
                        ?: "Study Partner"
                    _peerName.value = name
                    prefs.edit().putString("saved_partner_name", name).apply()

                    // Compute dynamic presence from lastSeen timestamp
                    val timestamp = uSnap.getTimestamp("lastSeen")
                    if (timestamp != null) {
                        val lastSeenMs = timestamp.toDate().time
                        val diffSeconds = (System.currentTimeMillis() - lastSeenMs) / 1000
                        if (diffSeconds <= 45) {
                            _peerActiveStatus.value = PeerStatus.Active
                        } else {
                            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                            val formatted = sdf.format(Date(lastSeenMs))
                            _peerActiveStatus.value = PeerStatus.Offline("Last seen at $formatted")
                        }
                    } else {
                        _peerActiveStatus.value = PeerStatus.Offline("Recently")
                    }
                }
        } catch (_: Exception) { }
    }

    private fun stopPairObservation() {
        pairListenerRegistration?.remove()
        pairListenerRegistration = null
        peerProfileRegistration?.remove()
        peerProfileRegistration = null
    }

    fun setTyping(isTyping: Boolean) {
        val pairId = getSavedPairId() ?: return
        val myUid = getCurrentUserId()
        try {
            firestore?.collection("pairs")?.document(pairId)
                ?.update("typing_${myUid}", isTyping)
        } catch (_: Exception) { }
    }

    fun startMessageObservation(scope: CoroutineScope) {
        val currentPairId = getSavedPairId() ?: return

        observePairDocument(currentPairId)
        observeMessagesWithLimit(currentPairId, currentMessageLimit)

        // 30s Heartbeat updater
        heartbeatScope?.let { if (it.isActive) return }
        heartbeatScope = scope
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                sendHeartbeat()
                delay(30_000L)
            }
        }
    }

    private fun observeMessagesWithLimit(pairId: String, limit: Long) {
        val fs = firestore ?: return
        messageListenerRegistration?.remove()

        try {
            messageListenerRegistration = fs.collection("pairs")
                .document(pairId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.id
                            val senderId = doc.getString("senderId") ?: ""
                            val senderName = doc.getString("senderName") ?: "Study Buddy"
                            val text = doc.getString("text") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val statusStr = doc.getString("status") ?: "SENT"
                            val status = try { MessageStatus.valueOf(statusStr) } catch (_: Exception) { MessageStatus.SENT }
                            val isVoice = doc.getBoolean("isVoiceNote") ?: false
                            val duration = doc.getString("voiceDuration") ?: ""
                            val audioUrl = doc.getString("audioUrl")
                            val imageUrl = doc.getString("imageUrl")
                            val replySnippet = doc.getString("replyToSnippet")
                            val replySender = doc.getString("replyToSender")

                            ChatMessage(
                                id = id,
                                roomId = pairId,
                                senderId = senderId,
                                senderName = senderName,
                                text = text,
                                timestamp = timestamp,
                                status = status,
                                isVoiceNote = isVoice,
                                voiceDuration = duration,
                                audioUrl = audioUrl,
                                imageUrl = imageUrl,
                                replyToSnippet = replySnippet,
                                replyToSender = replySender
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }

                    _messages.value = list
                    markIncomingMessagesAsRead(list)
                }
        } catch (e: Exception) {
            Log.w("EduHelpRepo", "Message observation error: ${e.message}")
        }
    }

    fun loadMoreMessages() {
        val pairId = getSavedPairId() ?: return
        currentMessageLimit += 50L
        observeMessagesWithLimit(pairId, currentMessageLimit)
    }

    fun markIncomingMessagesAsRead(messagesList: List<ChatMessage> = _messages.value) {
        val pairId = getSavedPairId() ?: return
        val myUid = getCurrentUserId()
        val fs = firestore ?: return

        val unreadIncoming = messagesList.filter { it.senderId != myUid && it.status != MessageStatus.READ }
        if (unreadIncoming.isEmpty()) return

        try {
            val batch = fs.batch()
            for (msg in unreadIncoming) {
                val ref = fs.collection("pairs").document(pairId).collection("messages").document(msg.id)
                batch.update(ref, "status", MessageStatus.READ.name)
            }
            batch.commit()
        } catch (_: Exception) { }
    }

    fun stopMessageObservation() {
        messageListenerRegistration?.remove()
        messageListenerRegistration = null
    }

    private suspend fun sendHeartbeat() {
        val pairId = getSavedPairId() ?: return
        try {
            firestore?.collection("pairs")?.document(pairId)
                ?.update("lastActiveTimestamp", System.currentTimeMillis())
                ?.await()
        } catch (_: Exception) { }
    }

    private fun updatePeerStatus(lastActive: Long) {
        val now = System.currentTimeMillis()
        val diffSeconds = (now - lastActive) / 1000
        if (diffSeconds in 0..45) {
            _peerActiveStatus.value = PeerStatus.Active
        } else {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formatted = if (lastActive > 0) sdf.format(Date(lastActive)) else "recently"
            _peerActiveStatus.value = PeerStatus.Offline(formatted)
        }
    }

    suspend fun sendMessage(
        text: String,
        imageUrl: String? = null,
        isVoiceNote: Boolean = false,
        voiceDuration: String = "",
        audioUrl: String? = null,
        replyToSnippet: String? = null,
        replyToSender: String? = null
    ) {
        val pairId = getSavedPairId() ?: return
        val myUid = getCurrentUserId()
        val myName = getUserDisplayName()
        val messageId = "msg_${UUID.randomUUID().toString().take(12)}"

        val newMessage = ChatMessage(
            id = messageId,
            roomId = pairId,
            senderId = myUid,
            senderName = myName,
            text = text,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            isVoiceNote = isVoiceNote,
            voiceDuration = voiceDuration,
            audioUrl = audioUrl,
            imageUrl = imageUrl,
            replyToSnippet = replyToSnippet,
            replyToSender = replyToSender
        )

        // Optimistically prepend to descending list (index 0 is newest)
        val updated = mutableListOf(newMessage).apply { addAll(_messages.value) }
        _messages.value = updated

        try {
            val payload = mapOf(
                "id" to messageId,
                "roomId" to pairId,
                "pairId" to pairId,
                "senderId" to myUid,
                "senderName" to myName,
                "text" to text,
                "timestamp" to newMessage.timestamp,
                "status" to MessageStatus.SENT.name,
                "isVoiceNote" to isVoiceNote,
                "voiceDuration" to voiceDuration,
                "audioUrl" to audioUrl,
                "imageUrl" to imageUrl,
                "replyToSnippet" to replyToSnippet,
                "replyToSender" to replyToSender
            )

            firestore?.collection("pairs")
                ?.document(pairId)
                ?.collection("messages")
                ?.document(messageId)
                ?.set(payload)
                ?.await()
        } catch (e: Exception) {
            Log.w("EduHelpRepo", "Firestore sendMessage failed", e)
        }
    }

    // Media Upload: Compress image Bitmap to < 1MB before uploading to Firebase Storage
    suspend fun uploadImageCompressed(imageUri: Uri): String? {
        val st = storage ?: return null
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            var quality = 90
            var compressedBytes: ByteArray
            do {
                val baos = ByteArrayOutputStream()
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                compressedBytes = baos.toByteArray()
                quality -= 10
            } while (compressedBytes.size > 1024 * 1024 && quality > 20)

            val fileRef = st.reference.child("chat_images/${UUID.randomUUID()}.jpg")
            fileRef.putBytes(compressedBytes).await()
            val downloadUrl = fileRef.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            Log.e("EduHelpRepo", "Failed to compress/upload image", e)
            null
        }
    }

    // Media Upload: Upload .m4a audio file to Firebase Storage
    suspend fun uploadAudioFile(audioFile: File): String? {
        val st = storage ?: return null
        return try {
            val fileRef = st.reference.child("chat_audio/${UUID.randomUUID()}.m4a")
            fileRef.putFile(Uri.fromFile(audioFile)).await()
            val downloadUrl = fileRef.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            Log.e("EduHelpRepo", "Failed to upload audio", e)
            null
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String) {
        val pairId = getSavedPairId() ?: return
        val updated = _messages.value.filter { it.id != messageId }
        _messages.value = updated

        try {
            firestore?.collection("pairs")
                ?.document(pairId)
                ?.collection("messages")
                ?.document(messageId)
                ?.delete()
                ?.await()
        } catch (e: Exception) {
            Log.w("EduHelpRepo", "Firestore delete failed", e)
        }
    }

    // Client-Side Chunked Panic Wipe: sets isPurged: true, deletes in chunks of 500
    suspend fun executeUrgentPurge() {
        val pairId = getSavedPairId()
        val fs = firestore

        _messages.value = emptyList()

        if (!pairId.isNullOrBlank()) {
            try {
                fs?.collection("pairs")?.document(pairId)
                    ?.update("isPurged", true)
                    ?.await()
            } catch (_: Exception) { }

            if (fs != null) {
                try {
                    val messagesRef = fs.collection("pairs").document(pairId).collection("messages")
                    var hasMore = true
                    while (hasMore) {
                        val snapshot = messagesRef.limit(500).get().await()
                        if (snapshot.isEmpty) {
                            hasMore = false
                        } else {
                            val batch = fs.batch()
                            for (doc in snapshot.documents) {
                                batch.delete(doc.reference)
                            }
                            batch.commit().await()
                            if (snapshot.size() < 500) {
                                hasMore = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("EduHelpRepo", "Chunked purge error: ${e.message}")
                }
            }
        }
    }
}
