package com.example.data.model

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}

data class ChatMessage(
    val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val isVoiceNote: Boolean = false,
    val voiceDuration: String = "",
    val audioUrl: String? = null,
    val imageUrl: String? = null,
    val replyToSnippet: String? = null,
    val replyToSender: String? = null
)

data class StudyPair(
    val pairId: String = "",
    val users: List<String> = emptyList(),
    val status: String = "pending", // "connected", "pending"
    val pairedRoomId: String = "",
    val isPurged: Boolean = false,
    val lastActiveTimestamp: Long = System.currentTimeMillis()
)
