package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val rikzenId: String = "",
    val rikzenIdLower: String = "",
    val displayName: String = "",
    val displayNameLower: String = "",
    val bio: String = "",
    val photoBase64: String = "",
    val searchablePhone: String = "",
    val isDiscoverableByName: Boolean = true,
    val isDiscoverableByPhone: Boolean = false,
    val isOnline: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val showLastSeen: Boolean = true,
    val photoVisibility: String = "everyone",
    val bioVisibility: String = "everyone",
    val whoCanRequest: String = "everyone",
    val statusSuspended: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PrivateAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val dob: String = "",
    val emailVisible: Boolean = false,
    val readReceiptsEnabled: Boolean = true,
    val blockedUids: List<String> = emptyList(),
    val linkedDevices: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FriendRequest(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val fromName: String = "",
    val fromRikzenId: String = "",
    val fromPhotoBase64: String = "",
    val toName: String = "",
    val toRikzenId: String = "",
    val status: String = "pending",
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Friendship(
    @DocumentId val id: String = "",
    val userA: String = "",
    val userB: String = "",
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val type: String = "dm",
    val title: String = "",
    val description: String = "",
    val photoBase64: String = "",
    val participantUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val createdBy: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageAt: Timestamp? = null,
    val pinnedMessageId: String = "",
    val onlyAdminsCanSend: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = "text",
    val mediaBase64: String = "",
    val mediaName: String = "",
    val mediaMime: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val forwarded: Boolean = false,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val pinned: Boolean = false,
    val starredBy: List<String> = emptyList(),
    val deliveredTo: List<String> = emptyList(),
    val readBy: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TypingIndicator(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val isTyping: Boolean = false,
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StatusUpdate(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerRikzenId: String = "",
    val ownerPhotoBase64: String = "",
    val contentType: String = "text",
    val text: String = "",
    val caption: String = "",
    val backgroundColorHex: String = "#191923",
    val mediaBase64: String = "",
    val audienceUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val reactions: List<String> = emptyList(),
    val mutedByUids: List<String> = emptyList(),
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Community(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val photoBase64: String = "",
    val ownerUid: String = "",
    val adminUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val groupIds: List<String> = emptyList(),
    val announcementText: String = "",
    val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Channel(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val photoBase64: String = "",
    val ownerUid: String = "",
    val adminUids: List<String> = emptyList(),
    val followerUids: List<String> = emptyList(),
    val followerCount: Long = 0L,
    val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChannelPost(
    @DocumentId val id: String = "",
    val channelId: String = "",
    val authorUid: String = "",
    val text: String = "",
    val mediaBase64: String = "",
    val reactions: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallRecord(
    @DocumentId val id: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val callerPhotoBase64: String = "",
    val calleeUid: String = "",
    val calleeName: String = "",
    val calleePhotoBase64: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "voice",
    val status: String = "ringing",
    val mutedByCaller: Boolean = false,
    val mutedByCallee: Boolean = false,
    val cameraOffCaller: Boolean = false,
    val cameraOffCallee: Boolean = false,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AbuseReport(
    @DocumentId val id: String = "",
    val reporterUid: String = "",
    val targetType: String = "user",
    val targetId: String = "",
    val reason: String = "",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val type: String = "message",
    val title: String = "",
    val body: String = "",
    val refId: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)
