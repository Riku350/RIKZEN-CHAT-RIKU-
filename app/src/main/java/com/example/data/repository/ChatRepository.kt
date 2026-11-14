package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.TypingIndicator
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt?.seconds ?: it.updatedAt?.seconds ?: 0L }
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, uid)
                    throw e
                }
        )
    }

    fun observeConversation(conversationId: String, uid: String): Flow<Conversation?> = flow {
        val path = "conversations/$conversationId"
        emitAll(
            db.collection("conversations").document(conversationId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.GET, path, uid)
                    throw e
                }
        )
    }

    fun observeMessages(conversationId: String, uid: String, limit: Long = 100L): Flow<List<ChatMessage>> = flow {
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("participantUids", uid)
                .limit(limit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, uid)
                    throw e
                }
        )
    }

    fun observeTyping(conversationId: String, uid: String): Flow<List<TypingIndicator>> = flow {
        val path = "conversations/$conversationId/typing"
        emitAll(
            db.collection("conversations").document(conversationId).collection("typing")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TypingIndicator::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, uid)
                    throw e
                }
        )
    }

    suspend fun setTyping(conversation: Conversation, displayName: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (conversation.id.isBlank()) return
        val path = "conversations/${conversation.id}/typing/$uid"
        try {
            db.collection("conversations").document(conversation.id)
                .collection("typing").document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "displayName" to displayName.take(60),
                        "isTyping" to isTyping,
                        "participantUids" to conversation.participantUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        senderName: String,
        text: String,
        messageType: String = "text",
        mediaBase64: String = "",
        mediaName: String = "",
        mediaMime: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        replyToId: String = "",
        replyToText: String = "",
        replyToSender: String = "",
        forwarded: Boolean = false
    ): Result<String> {
        val uid = requireUserId()
        val msgId = UUID.randomUUID().toString()
        val path = "conversations/${conversation.id}/messages/$msgId"
        return try {
            val payload = mutableMapOf<String, Any>(
                "conversationId" to conversation.id,
                "senderId" to uid,
                "senderName" to senderName.take(60),
                "text" to text.take(4000),
                "messageType" to messageType,
                "mediaBase64" to mediaBase64,
                "mediaName" to mediaName.take(160),
                "mediaMime" to mediaMime.take(80),
                "latitude" to latitude,
                "longitude" to longitude,
                "replyToId" to replyToId,
                "replyToText" to replyToText.take(300),
                "replyToSender" to replyToSender.take(60),
                "forwarded" to forwarded,
                "edited" to false,
                "deleted" to false,
                "pinned" to false,
                "starredBy" to emptyList<String>(),
                "deliveredTo" to listOf(uid),
                "readBy" to listOf(uid),
                "reactions" to emptyMap<String, String>(),
                "participantUids" to conversation.participantUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("conversations").document(conversation.id)
                .collection("messages").document(msgId)
                .set(payload).await()

            val preview = when (messageType) {
                "image" -> "Photo"
                "document" -> "Document: $mediaName"
                "audio" -> "Voice message"
                "location" -> "Shared location"
                else -> text.take(120)
            }
            db.collection("conversations").document(conversation.id).update(
                mapOf(
                    "lastMessageText" to preview,
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Send notification records to other participants in parallel
            coroutineScope {
                conversation.participantUids.filter { it != uid }.take(10).map { peerUid ->
                    async {
                        val notifId = UUID.randomUUID().toString()
                        runCatching {
                            db.collection("users").document(peerUid).collection("notifications").document(notifId)
                                .set(
                                    mapOf(
                                        "recipientUid" to peerUid,
                                        "type" to if (conversation.type == "group") "group_message" else "message",
                                        "title" to if (conversation.type == "group") "${conversation.title} • $senderName" else senderName,
                                        "body" to preview,
                                        "refId" to conversation.id,
                                        "isRead" to false,
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )
                                ).await()
                        }
                    }
                }.awaitAll()
            }

            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markDeliveredAndRead(
        conversationId: String,
        messages: List<ChatMessage>,
        sendReadReceipt: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        val pending = messages.filter { msg ->
            msg.senderId != uid && msg.id.isNotBlank() &&
                (!msg.deliveredTo.contains(uid) || (sendReadReceipt && !msg.readBy.contains(uid)))
        }
        if (pending.isEmpty()) return

        pending.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { msg ->
                val needsDelivery = !msg.deliveredTo.contains(uid)
                val needsRead = sendReadReceipt && !msg.readBy.contains(uid)
                val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                if (needsDelivery) updates["deliveredTo"] = FieldValue.arrayUnion(uid)
                if (needsRead) updates["readBy"] = FieldValue.arrayUnion(uid)
                val ref = db.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                batch.update(ref, updates)
            }
            runCatching { batch.commit().await() }
        }
    }

    suspend fun editMessage(conversationId: String, messageId: String, newText: String): Result<Unit> {
        requireUserId()
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "text" to newText.trim().take(4000),
                        "edited" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        requireUserId()
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            db.collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "text" to "This message was deleted",
                        "deleted" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleReaction(conversationId: String, message: ChatMessage, emoji: String): Result<Unit> {
        val uid = requireUserId()
        val path = "conversations/$conversationId/messages/${message.id}"
        return try {
            val updated = message.reactions.toMutableMap()
            if (updated[uid] == emoji) updated.remove(uid) else updated[uid] = emoji
            db.collection("conversations").document(conversationId)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "reactions" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleStar(conversationId: String, message: ChatMessage): Result<Unit> {
        val uid = requireUserId()
        val path = "conversations/$conversationId/messages/${message.id}"
        return try {
            val op = if (message.starredBy.contains(uid)) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
            db.collection("conversations").document(conversationId)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "starredBy" to op,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePinMessage(conversationId: String, message: ChatMessage): Result<Unit> {
        requireUserId()
        val path = "conversations/$conversationId/messages/${message.id}"
        return try {
            val newPinState = !message.pinned
            db.collection("conversations").document(conversationId)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "pinned" to newPinState,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "pinnedMessageId" to if (newPinState) message.id else "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Group Chats ---

    suspend fun createGroup(
        title: String,
        description: String,
        photoBase64: String,
        memberUids: List<String>
    ): Result<String> {
        val uid = requireUserId()
        val groupId = "grp_${UUID.randomUUID()}"
        return try {
            val participants = (memberUids + uid).distinct().take(50)
            db.collection("conversations").document(groupId).set(
                mapOf(
                    "type" to "group",
                    "title" to title.trim().take(80),
                    "description" to description.trim().take(300),
                    "photoBase64" to photoBase64,
                    "participantUids" to participants,
                    "adminUids" to listOf(uid),
                    "createdBy" to uid,
                    "lastMessageText" to "Group created",
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "pinnedMessageId" to "",
                    "onlyAdminsCanSend" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "conversations/$groupId")
            Result.failure(e)
        }
    }

    suspend fun updateGroupSettings(
        groupId: String,
        title: String,
        description: String,
        onlyAdminsCanSend: Boolean
    ): Result<Unit> {
        requireUserId()
        return try {
            db.collection("conversations").document(groupId).update(
                mapOf(
                    "title" to title.trim().take(80),
                    "description" to description.trim().take(300),
                    "onlyAdminsCanSend" to onlyAdminsCanSend,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$groupId")
            Result.failure(e)
        }
    }

    suspend fun addGroupMember(groupId: String, memberUid: String): Result<Unit> {
        requireUserId()
        return try {
            db.collection("conversations").document(groupId).update(
                mapOf(
                    "participantUids" to FieldValue.arrayUnion(memberUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$groupId")
            Result.failure(e)
        }
    }

    suspend fun removeGroupMember(groupId: String, memberUid: String): Result<Unit> {
        requireUserId()
        return try {
            db.collection("conversations").document(groupId).update(
                mapOf(
                    "participantUids" to FieldValue.arrayRemove(memberUid),
                    "adminUids" to FieldValue.arrayRemove(memberUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$groupId")
            Result.failure(e)
        }
    }

    suspend fun toggleGroupAdmin(groupId: String, targetUid: String, makeAdmin: Boolean): Result<Unit> {
        requireUserId()
        return try {
            val op = if (makeAdmin) FieldValue.arrayUnion(targetUid) else FieldValue.arrayRemove(targetUid)
            db.collection("conversations").document(groupId).update(
                mapOf(
                    "adminUids" to op,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$groupId")
            Result.failure(e)
        }
    }

    private fun logStreamError(error: Throwable, op: OperationType, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
