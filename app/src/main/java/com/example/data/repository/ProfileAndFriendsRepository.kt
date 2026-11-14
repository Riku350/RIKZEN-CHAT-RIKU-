package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.model.FriendRequest
import com.example.data.model.Friendship
import com.example.data.model.PrivateAccount
import com.example.data.model.PublicProfile
import com.example.data.util.OperationType
import com.example.data.util.RikzenIdGenerator
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ProfileAndFriendsRepository(
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

    fun observePublicProfile(uid: String): Flow<PublicProfile?> = flow {
        val path = "public_profiles/$uid"
        emitAll(
            db.collection("public_profiles").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observePrivateAccount(uid: String): Flow<PrivateAccount?> = flow {
        val path = "users/$uid/private/account"
        emitAll(
            db.collection("users").document(uid).collection("private").document("account")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(PrivateAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun getPublicProfile(uid: String): PublicProfile? {
        val path = "public_profiles/$uid"
        return try {
            val snap = db.collection("public_profiles").document(uid).get().await()
            if (snap.exists()) snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun createAccountProfile(
        displayName: String,
        email: String,
        bio: String,
        phoneNumber: String,
        dob: String,
        photoBase64: String,
        isDiscoverableByPhone: Boolean,
        isDiscoverableByName: Boolean,
        preferredRikzenId: String? = null
    ): Result<PublicProfile> {
        val uid = requireUserId()
        return try {
            val existing = getPublicProfile(uid)
            if (existing != null) {
                return Result.success(existing)
            }

            // Generate and reserve a unique RIKZEN ID atomically
            var rikzenId = preferredRikzenId?.uppercase()?.trim().orEmpty()
            var reserved = false
            for (attempt in 0 until 6) {
                if (rikzenId.isBlank() || attempt > 0) {
                    rikzenId = RikzenIdGenerator.generate()
                }
                val lookupRef = db.collection("rikzen_ids").document(rikzenId)
                val check = lookupRef.get().await()
                if (!check.exists()) {
                    lookupRef.set(
                        mapOf(
                            "uid" to uid,
                            "rikzenId" to rikzenId,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    reserved = true
                    break
                }
            }
            if (!reserved) {
                return Result.failure(IllegalStateException("Could not reserve a unique RIKZEN ID. Please try again."))
            }

            val normalizedPhone = if (isDiscoverableByPhone && phoneNumber.isNotBlank()) {
                RikzenIdGenerator.normalizePhone(phoneNumber)
            } else ""

            if (normalizedPhone.isNotBlank()) {
                db.collection("phone_lookup").document(normalizedPhone).set(
                    mapOf(
                        "uid" to uid,
                        "phone" to normalizedPhone,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val publicPayload = mapOf(
                "uid" to uid,
                "rikzenId" to rikzenId,
                "rikzenIdLower" to rikzenId.lowercase(),
                "displayName" to displayName.trim().take(60),
                "displayNameLower" to displayName.trim().lowercase().take(60),
                "bio" to bio.trim().take(200),
                "photoBase64" to photoBase64,
                "searchablePhone" to normalizedPhone,
                "isDiscoverableByName" to isDiscoverableByName,
                "isDiscoverableByPhone" to isDiscoverableByPhone,
                "isOnline" to true,
                "showOnlineStatus" to true,
                "showLastSeen" to true,
                "photoVisibility" to "everyone",
                "bioVisibility" to "everyone",
                "whoCanRequest" to "everyone",
                "statusSuspended" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("public_profiles").document(uid).set(publicPayload).await()

            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }
            val privatePayload = mapOf(
                "uid" to uid,
                "email" to email.take(128),
                "phoneNumber" to phoneNumber.trim().take(32),
                "dob" to dob.trim().take(32),
                "emailVisible" to false,
                "readReceiptsEnabled" to true,
                "blockedUids" to emptyList<String>(),
                "linkedDevices" to listOf(deviceName),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("private").document("account")
                .set(privatePayload).await()

            val created = getPublicProfile(uid) ?: PublicProfile(
                uid = uid,
                rikzenId = rikzenId,
                displayName = displayName.trim(),
                bio = bio.trim(),
                photoBase64 = photoBase64
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        photoBase64: String,
        phoneNumber: String,
        dob: String,
        isDiscoverableByPhone: Boolean,
        isDiscoverableByName: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        return try {
            val normalizedPhone = if (isDiscoverableByPhone && phoneNumber.isNotBlank()) {
                RikzenIdGenerator.normalizePhone(phoneNumber)
            } else ""

            if (normalizedPhone.isNotBlank()) {
                db.collection("phone_lookup").document(normalizedPhone).set(
                    mapOf(
                        "uid" to uid,
                        "phone" to normalizedPhone,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            db.collection("public_profiles").document(uid).update(
                mapOf(
                    "displayName" to displayName.trim().take(60),
                    "displayNameLower" to displayName.trim().lowercase().take(60),
                    "bio" to bio.trim().take(200),
                    "photoBase64" to photoBase64,
                    "searchablePhone" to normalizedPhone,
                    "isDiscoverableByName" to isDiscoverableByName,
                    "isDiscoverableByPhone" to isDiscoverableByPhone,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("users").document(uid).collection("private").document("account").update(
                mapOf(
                    "phoneNumber" to phoneNumber.trim().take(32),
                    "dob" to dob.trim().take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        showOnlineStatus: Boolean,
        showLastSeen: Boolean,
        photoVisibility: String,
        bioVisibility: String,
        whoCanRequest: String,
        isDiscoverableByName: Boolean,
        isDiscoverableByPhone: Boolean,
        readReceiptsEnabled: Boolean,
        emailVisible: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        return try {
            db.collection("public_profiles").document(uid).update(
                mapOf(
                    "showOnlineStatus" to showOnlineStatus,
                    "showLastSeen" to showLastSeen,
                    "photoVisibility" to photoVisibility,
                    "bioVisibility" to bioVisibility,
                    "whoCanRequest" to whoCanRequest,
                    "isDiscoverableByName" to isDiscoverableByName,
                    "isDiscoverableByPhone" to isDiscoverableByPhone,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("users").document(uid).collection("private").document("account").update(
                mapOf(
                    "readReceiptsEnabled" to readReceiptsEnabled,
                    "emailVisible" to emailVisible,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("public_profiles").document(uid).update(
                mapOf(
                    "isOnline" to online,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun removeLinkedDevice(deviceName: String): Result<Unit> {
        val uid = requireUserId()
        return try {
            db.collection("users").document(uid).collection("private").document("account").update(
                mapOf(
                    "linkedDevices" to FieldValue.arrayRemove(deviceName),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/private/account")
            Result.failure(e)
        }
    }

    suspend fun deleteAccountData(): Result<Unit> {
        val uid = requireUserId()
        return try {
            val profile = getPublicProfile(uid)
            if (profile != null && profile.rikzenId.isNotBlank()) {
                runCatching { db.collection("rikzen_ids").document(profile.rikzenId).delete().await() }
            }
            if (profile != null && profile.searchablePhone.isNotBlank()) {
                runCatching { db.collection("phone_lookup").document(profile.searchablePhone).delete().await() }
            }
            runCatching { db.collection("users").document(uid).collection("private").document("account").delete().await() }
            db.collection("public_profiles").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    // --- User Discovery (Exact RIKZEN ID, Opt-in Phone, Privacy-Controlled Name) ---

    suspend fun searchPeople(query: String): Result<List<PublicProfile>> {
        val uid = requireUserId()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return Result.success(emptyList())
        return try {
            val results = linkedMapOf<String, PublicProfile>()

            // 1. Exact RIKZEN ID lookup (O(1) cross-device get())
            val normalizedId = trimmed.uppercase().let {
                if (it.startsWith("RIKZEN-")) it else "RIKZEN-$it"
            }
            if (normalizedId.matches(Regex("^[A-Z0-9\\-]+$"))) {
                val idDoc = db.collection("rikzen_ids").document(normalizedId).get().await()
                val targetUid = idDoc.getString("uid")
                if (!targetUid.isNullOrBlank() && targetUid != uid) {
                    getPublicProfile(targetUid)?.let { results[it.uid] = it }
                }
            }

            // 2. Exact Phone lookup (O(1) opt-in get())
            val normalizedPhone = RikzenIdGenerator.normalizePhone(trimmed)
            if (normalizedPhone.isNotBlank()) {
                val phoneDoc = db.collection("phone_lookup").document(normalizedPhone).get().await()
                val targetUid = phoneDoc.getString("uid")
                if (!targetUid.isNullOrBlank() && targetUid != uid) {
                    getPublicProfile(targetUid)?.let {
                        if (it.isDiscoverableByPhone) results[it.uid] = it
                    }
                }
            }

            // 3. Name search among discoverable profiles (query aligned with rules!)
            val lower = trimmed.lowercase()
            val nameSnap = db.collection("public_profiles")
                .whereEqualTo("isDiscoverableByName", true)
                .limit(40)
                .get()
                .await()
            nameSnap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.uid != uid && !it.statusSuspended && (it.displayNameLower.contains(lower) || it.rikzenIdLower.contains(lower)) }
                .forEach { results[it.uid] = it }

            Result.success(results.values.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "public_profiles")
            Result.failure(e)
        }
    }

    // --- Friend Requests & Friendships ---

    fun observeFriendRequests(uid: String): Flow<List<FriendRequest>> = flow {
        val path = "friend_requests"
        emitAll(
            db.collection("friend_requests")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FriendRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, uid)
                    throw e
                }
        )
    }

    fun observeFriendships(uid: String): Flow<List<Friendship>> = flow {
        val path = "friendships"
        emitAll(
            db.collection("friendships")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap -> snap.toObjects(Friendship::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    logStreamError(e, OperationType.LIST, path, uid)
                    throw e
                }
        )
    }

    suspend fun sendFriendRequest(myProfile: PublicProfile, target: PublicProfile): Result<Unit> {
        val uid = requireUserId()
        if (target.uid == uid) return Result.failure(IllegalArgumentException("You cannot add yourself."))
        val reqId = "${uid}_${target.uid}"
        val reverseReqId = "${target.uid}_${uid}"
        return try {
            val pairId = listOf(uid, target.uid).sorted().joinToString("_")
            val existingFriend = db.collection("friendships").document(pairId).get().await()
            if (existingFriend.exists()) {
                return Result.failure(IllegalStateException("You are already connected with ${target.displayName}."))
            }
            val existingReq = db.collection("friend_requests").document(reqId).get().await()
            if (existingReq.exists() && existingReq.getString("status") == "pending") {
                return Result.failure(IllegalStateException("Friend request already sent."))
            }
            val reverseReq = db.collection("friend_requests").document(reverseReqId).get().await()
            if (reverseReq.exists() && reverseReq.getString("status") == "pending") {
                return Result.failure(IllegalStateException("${target.displayName} already sent you a request! Check Received Requests."))
            }

            val payload = mapOf(
                "fromUid" to uid,
                "toUid" to target.uid,
                "fromName" to myProfile.displayName.take(60),
                "fromRikzenId" to myProfile.rikzenId.take(32),
                "fromPhotoBase64" to myProfile.photoBase64,
                "toName" to target.displayName.take(60),
                "toRikzenId" to target.rikzenId.take(32),
                "status" to "pending",
                "participantUids" to listOf(uid, target.uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("friend_requests").document(reqId).set(payload).await()

            // Deliver notification to recipient
            val notifId = UUID.randomUUID().toString()
            db.collection("users").document(target.uid).collection("notifications").document(notifId)
                .set(
                    mapOf(
                        "recipientUid" to target.uid,
                        "type" to "friend_request",
                        "title" to "New Friend Request",
                        "body" to "${myProfile.displayName} (${myProfile.rikzenId}) wants to connect on Rikzen Chat.",
                        "refId" to reqId,
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "friend_requests/$reqId")
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(request: FriendRequest, myProfile: PublicProfile): Result<String> {
        val uid = requireUserId()
        return try {
            db.collection("friend_requests").document(request.id).update(
                mapOf(
                    "status" to "accepted",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val sorted = listOf(request.fromUid, request.toUid).sorted()
            val pairId = sorted.joinToString("_")
            db.collection("friendships").document(pairId).set(
                mapOf(
                    "userA" to sorted[0],
                    "userB" to sorted[1],
                    "participantUids" to sorted,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Create/Ensure DM conversation between both users
            val dmId = "dm_$pairId"
            val dmRef = db.collection("conversations").document(dmId)
            val dmSnap = dmRef.get().await()
            if (!dmSnap.exists()) {
                dmRef.set(
                    mapOf(
                        "type" to "dm",
                        "title" to "",
                        "description" to "",
                        "photoBase64" to "",
                        "participantUids" to sorted,
                        "adminUids" to sorted,
                        "createdBy" to uid,
                        "lastMessageText" to "You are now connected on Rikzen Chat",
                        "lastMessageSenderId" to uid,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "pinnedMessageId" to "",
                        "onlyAdminsCanSend" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Notify requester
            val notifId = UUID.randomUUID().toString()
            db.collection("users").document(request.fromUid).collection("notifications").document(notifId)
                .set(
                    mapOf(
                        "recipientUid" to request.fromUid,
                        "type" to "request_accepted",
                        "title" to "Friend Request Accepted",
                        "body" to "${myProfile.displayName} accepted your friend request.",
                        "refId" to dmId,
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            Result.success(dmId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "friend_requests/${request.id}")
            Result.failure(e)
        }
    }

    suspend fun rejectFriendRequest(request: FriendRequest): Result<Unit> {
        requireUserId()
        return try {
            db.collection("friend_requests").document(request.id).update(
                mapOf(
                    "status" to "rejected",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "friend_requests/${request.id}")
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUid: String): Result<Unit> {
        val uid = requireUserId()
        return try {
            db.collection("users").document(uid).collection("private").document("account").update(
                mapOf(
                    "blockedUids" to FieldValue.arrayUnion(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/private/account")
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUid: String): Result<Unit> {
        val uid = requireUserId()
        return try {
            db.collection("users").document(uid).collection("private").document("account").update(
                mapOf(
                    "blockedUids" to FieldValue.arrayRemove(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/private/account")
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
