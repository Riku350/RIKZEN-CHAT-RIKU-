package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AbuseReport
import com.example.data.model.AppNotification
import com.example.data.model.CallRecord
import com.example.data.model.Channel
import com.example.data.model.ChannelPost
import com.example.data.model.Community
import com.example.data.model.PublicProfile
import com.example.data.model.StatusUpdate
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class SocialAndCallsRepository(
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

    // --- 24-Hour Statuses ---

    fun observeStatuses(uid: String): Flow<List<StatusUpdate>> = flow {
        val path = "statuses"
        emitAll(
            db.collection("statuses")
                .whereArrayContains("audienceUids", uid)
                .snapshots()
                .map { snap ->
                    val nowMs = System.currentTimeMillis()
                    snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { (it.expiresAt?.toDate()?.time ?: Long.MAX_VALUE) > nowMs && !it.mutedByUids.contains(uid) }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, uid)
                    throw e
                }
        )
    }

    suspend fun postStatus(
        owner: PublicProfile,
        friendUids: List<String>,
        contentType: String,
        text: String,
        caption: String,
        backgroundColorHex: String,
        mediaBase64: String
    ): Result<Unit> {
        val uid = requireUserId()
        val statusId = UUID.randomUUID().toString()
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L))
        return try {
            val audience = (friendUids + uid).distinct().take(50)
            db.collection("statuses").document(statusId).set(
                mapOf(
                    "ownerUid" to uid,
                    "ownerName" to owner.displayName.take(60),
                    "ownerRikzenId" to owner.rikzenId.take(32),
                    "ownerPhotoBase64" to owner.photoBase64,
                    "contentType" to contentType,
                    "text" to text.take(600),
                    "caption" to caption.take(300),
                    "backgroundColorHex" to backgroundColorHex,
                    "mediaBase64" to mediaBase64,
                    "audienceUids" to audience,
                    "viewerUids" to listOf(uid),
                    "reactions" to emptyList<String>(),
                    "mutedByUids" to emptyList<String>(),
                    "expiresAt" to expiresAt,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "statuses/$statusId")
            Result.failure(e)
        }
    }

    suspend fun markStatusViewed(statusId: String) {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            db.collection("statuses").document(statusId)
                .update("viewerUids", FieldValue.arrayUnion(uid)).await()
        }
    }

    suspend fun reactToStatus(statusId: String, emoji: String): Result<Unit> {
        val uid = requireUserId()
        return try {
            db.collection("statuses").document(statusId)
                .update("reactions", FieldValue.arrayUnion("$uid:$emoji")).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "statuses/$statusId")
            Result.failure(e)
        }
    }

    suspend fun muteStatus(statusId: String): Result<Unit> {
        val uid = requireUserId()
        return try {
            db.collection("statuses").document(statusId)
                .update("mutedByUids", FieldValue.arrayUnion(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "statuses/$statusId")
            Result.failure(e)
        }
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> {
        requireUserId()
        return try {
            db.collection("statuses").document(statusId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "statuses/$statusId")
            Result.failure(e)
        }
    }

    // --- Communities ---

    fun observePublicCommunities(uid: String): Flow<List<Community>> = flow {
        emitAll(
            db.collection("communities")
                .whereEqualTo("isPublic", true)
                .limit(50)
                .snapshots()
                .map { snap -> snap.toObjects(Community::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "communities", uid)
                    throw e
                }
        )
    }

    suspend fun createCommunity(
        name: String,
        description: String,
        announcementText: String,
        linkedGroupIds: List<String>
    ): Result<Unit> {
        val uid = requireUserId()
        val id = "comm_${UUID.randomUUID()}"
        return try {
            db.collection("communities").document(id).set(
                mapOf(
                    "name" to name.trim().take(80),
                    "description" to description.trim().take(400),
                    "photoBase64" to "",
                    "ownerUid" to uid,
                    "adminUids" to listOf(uid),
                    "memberUids" to listOf(uid),
                    "groupIds" to linkedGroupIds.take(30),
                    "announcementText" to announcementText.trim().take(1000),
                    "isPublic" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "communities/$id")
            Result.failure(e)
        }
    }

    suspend fun joinCommunity(communityId: String): Result<Unit> {
        val uid = requireUserId()
        return try {
            db.collection("communities").document(communityId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "communities/$communityId")
            Result.failure(e)
        }
    }

    suspend fun postCommunityAnnouncement(communityId: String, announcement: String): Result<Unit> {
        requireUserId()
        return try {
            db.collection("communities").document(communityId).update(
                mapOf(
                    "announcementText" to announcement.trim().take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "communities/$communityId")
            Result.failure(e)
        }
    }

    // --- Channels ---

    fun observePublicChannels(uid: String): Flow<List<Channel>> = flow {
        emitAll(
            db.collection("channels")
                .whereEqualTo("isPublic", true)
                .limit(50)
                .snapshots()
                .map { snap -> snap.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "channels", uid)
                    throw e
                }
        )
    }

    fun observeChannelPosts(channelId: String, uid: String): Flow<List<ChannelPost>> = flow {
        emitAll(
            db.collection("channels").document(channelId).collection("posts")
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChannelPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "channels/$channelId/posts", uid)
                    throw e
                }
        )
    }

    suspend fun createChannel(name: String, description: String): Result<Unit> {
        val uid = requireUserId()
        val id = "chan_${UUID.randomUUID()}"
        return try {
            db.collection("channels").document(id).set(
                mapOf(
                    "name" to name.trim().take(80),
                    "description" to description.trim().take(400),
                    "photoBase64" to "",
                    "ownerUid" to uid,
                    "adminUids" to listOf(uid),
                    "followerUids" to listOf(uid),
                    "followerCount" to 1L,
                    "isPublic" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "channels/$id")
            Result.failure(e)
        }
    }

    suspend fun toggleFollowChannel(channel: Channel): Result<Unit> {
        val uid = requireUserId()
        val isFollowing = channel.followerUids.contains(uid)
        return try {
            val newFollowers = if (isFollowing) channel.followerUids - uid else (channel.followerUids + uid).distinct()
            db.collection("channels").document(channel.id).update(
                mapOf(
                    "followerUids" to newFollowers,
                    "followerCount" to newFollowers.size.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "channels/${channel.id}")
            Result.failure(e)
        }
    }

    suspend fun publishChannelPost(channelId: String, text: String, mediaBase64: String = ""): Result<Unit> {
        val uid = requireUserId()
        val postId = UUID.randomUUID().toString()
        return try {
            db.collection("channels").document(channelId).collection("posts").document(postId).set(
                mapOf(
                    "channelId" to channelId,
                    "authorUid" to uid,
                    "text" to text.trim().take(2000),
                    "mediaBase64" to mediaBase64,
                    "reactions" to emptyMap<String, String>(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "channels/$channelId/posts/$postId")
            Result.failure(e)
        }
    }

    suspend fun reactToChannelPost(channelId: String, post: ChannelPost, emoji: String): Result<Unit> {
        val uid = requireUserId()
        return try {
            val updated = post.reactions.toMutableMap()
            if (updated[uid] == emoji) updated.remove(uid) else updated[uid] = emoji
            db.collection("channels").document(channelId).collection("posts").document(post.id)
                .update("reactions", updated).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "channels/$channelId/posts/${post.id}")
            Result.failure(e)
        }
    }

    // --- Calls (Real Signaling + Call History) ---

    fun observeCalls(uid: String): Flow<List<CallRecord>> = flow {
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "calls", uid)
                    throw e
                }
        )
    }

    suspend fun startCall(caller: PublicProfile, callee: PublicProfile, callType: String): Result<String> {
        val uid = requireUserId()
        val callId = "call_${UUID.randomUUID()}"
        return try {
            db.collection("calls").document(callId).set(
                mapOf(
                    "callerUid" to uid,
                    "callerName" to caller.displayName.take(60),
                    "callerPhotoBase64" to caller.photoBase64,
                    "calleeUid" to callee.uid,
                    "calleeName" to callee.displayName.take(60),
                    "calleePhotoBase64" to callee.photoBase64,
                    "participantUids" to listOf(uid, callee.uid),
                    "callType" to callType,
                    "status" to "ringing",
                    "mutedByCaller" to false,
                    "mutedByCallee" to false,
                    "cameraOffCaller" to (callType == "voice"),
                    "cameraOffCallee" to (callType == "voice"),
                    "durationSeconds" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val notifId = UUID.randomUUID().toString()
            runCatching {
                db.collection("users").document(callee.uid).collection("notifications").document(notifId)
                    .set(
                        mapOf(
                            "recipientUid" to callee.uid,
                            "type" to "call",
                            "title" to "Incoming ${if (callType == "video") "Video" else "Voice"} Call",
                            "body" to "${caller.displayName} is calling you on Rikzen Chat",
                            "refId" to callId,
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }

            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(callId: String, status: String, durationSeconds: Long = 0L): Result<Unit> {
        requireUserId()
        return try {
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun updateCallMediaState(
        call: CallRecord,
        isMuted: Boolean,
        isCameraOff: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        val isCaller = call.callerUid == uid
        val map = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        if (isCaller) {
            map["mutedByCaller"] = isMuted
            map["cameraOffCaller"] = isCameraOff
        } else {
            map["mutedByCallee"] = isMuted
            map["cameraOffCallee"] = isCameraOff
        }
        runCatching {
            db.collection("calls").document(call.id).update(map).await()
        }
    }

    // --- Notifications Inbox ---

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
        emitAll(
            db.collection("users").document(uid).collection("notifications")
                .limit(40)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, "users/$uid/notifications", uid)
                    throw e
                }
        )
    }

    suspend fun markNotificationRead(uid: String, notifId: String) {
        runCatching {
            db.collection("users").document(uid).collection("notifications").document(notifId)
                .update("isRead", true).await()
        }
    }

    // --- Reports & Admin Operations ---

    suspend fun submitReport(targetType: String, targetId: String, reason: String): Result<Unit> {
        val uid = requireUserId()
        val id = UUID.randomUUID().toString()
        return try {
            db.collection("reports").document(id).set(
                mapOf(
                    "reporterUid" to uid,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "reason" to reason.trim().take(500),
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reports/$id")
            Result.failure(e)
        }
    }

    suspend fun checkIsAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.isEmailVerified && user.email == "phonedata9955@gmail.com") return true
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun adminFetchAllUsers(): Result<List<PublicProfile>> {
        requireUserId()
        return try {
            val snap = db.collection("public_profiles").limit(100).get().await()
            Result.success(snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "public_profiles")
            Result.failure(e)
        }
    }

    suspend fun adminFetchAllReports(): Result<List<AbuseReport>> {
        requireUserId()
        return try {
            val snap = db.collection("reports").limit(100).get().await()
            Result.success(
                snap.toObjects(AbuseReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "reports")
            Result.failure(e)
        }
    }

    suspend fun adminToggleUserSuspension(targetUid: String, suspended: Boolean): Result<Unit> {
        requireUserId()
        return try {
            db.collection("public_profiles").document(targetUid).update(
                mapOf(
                    "statusSuspended" to suspended,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$targetUid")
            Result.failure(e)
        }
    }

    suspend fun adminResolveReport(reportId: String, newStatus: String): Result<Unit> {
        requireUserId()
        return try {
            db.collection("reports").document(reportId).update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports/$reportId")
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
