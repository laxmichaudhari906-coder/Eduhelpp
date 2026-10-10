package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.repository.EduHelpRepository
import com.example.data.repository.PairingState
import com.example.data.repository.PeerStatus
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EduHelpViewModel(
    val repository: EduHelpRepository
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = repository.currentUser
    val pairingState: StateFlow<PairingState> = repository.pairingState
    val peerName: StateFlow<String> = repository.peerName
    val peerActiveStatus: StateFlow<PeerStatus> = repository.peerActiveStatus
    val messages: StateFlow<List<ChatMessage>> = repository.messages

    fun onGoogleSignInClicked(
        activity: Activity,
        credentialManager: CredentialManager,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        repository.onGoogleSignInClicked(
            activity = activity,
            credentialManager = credentialManager,
            onAuthSuccess = { onSuccess() },
            onAuthError = onError,
            scope = viewModelScope,
            onAuthCancelled = onCancelled
        )
    }

    fun connectPair(pairId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.connectPair(pairId)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.localizedMessage ?: "Pairing failed")
            }
        }
    }

    fun disconnectPair() {
        repository.disconnectPair()
    }

    fun sendMessage(
        text: String,
        isVoiceNote: Boolean = false,
        voiceDuration: String = "",
        replyToSnippet: String? = null,
        replyToSender: String? = null
    ) {
        viewModelScope.launch {
            repository.sendMessage(
                text = text,
                isVoiceNote = isVoiceNote,
                voiceDuration = voiceDuration,
                replyToSnippet = replyToSnippet,
                replyToSender = replyToSender
            )
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessageForEveryone(messageId)
        }
    }

    fun executePanicPurge(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.executeUrgentPurge()
            onComplete()
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        repository.signOut(context, viewModelScope, onComplete)
    }
}
