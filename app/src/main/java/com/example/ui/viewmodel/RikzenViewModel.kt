package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AbuseReport
import com.example.data.model.AppNotification
import com.example.data.model.CallRecord
import com.example.data.model.Channel
import com.example.data.model.ChannelPost
import com.example.data.model.ChatMessage
import com.example.data.model.Community
import com.example.data.model.Conversation
import com.example.data.model.FriendRequest
import com.example.data.model.Friendship
import com.example.data.model.PrivateAccount
import com.example.data.model.PublicProfile
import com.example.data.model.StatusUpdate
import com.example.data.model.TypingIndicator
import com.example.data.model.UiState
import com.example.data.repository.ChatRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ProfileAndFriendsRepository
import com.example.data.repository.SocialAndCallsRepository
import com.example.data.repository.UserPreferences
import com.example.data.util.NotificationHelper
import com.example.data.util.unwrapFirestoreException
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.FontSizePreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class RikzenViewModel(
    private val appContext: Context,
    val currentUserId: String,
    private val profileRepo: ProfileAndFriendsRepository,
    private val chatRepo: ChatRepository,
    private val socialRepo: SocialAndCallsRepository,
    private val prefsRepo: PreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val myProfileState: StateFlow<UiState<PublicProfile?>> = profileRepo.observePublicProfile(currentUserId)
        .map<PublicProfile?, UiState<PublicProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fErr = error.unwrapFirestoreException()
            if (fErr == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", fErr ?: error)
            emit(UiState.Error((fErr ?: error).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val privateAccount: StateFlow<PrivateAccount?> = profileRepo.observePrivateAccount(currentUserId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val friendRequests: StateFlow<List<FriendRequest>> = profileRepo.observeFriendRequests(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val friendships: StateFlow<List<Friendship>> = profileRepo.observeFriendships(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversations: StateFlow<List<Conversation>> = chatRepo.observeConversations(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val statuses: StateFlow<List<StatusUpdate>> = socialRepo.observeStatuses(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val communities: StateFlow<List<Community>> = socialRepo.observePublicCommunities(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val channels: StateFlow<List<Channel>> = socialRepo.observePublicChannels(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val calls: StateFlow<List<CallRecord>> = socialRepo.observeCalls(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> = socialRepo.observeNotifications(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _peerProfiles = MutableStateFlow<Map<String, PublicProfile>>(emptyMap())
    val peerProfiles: StateFlow<Map<String, PublicProfile>> = _peerProfiles.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PublicProfile>>(emptyList())
    val searchResults: StateFlow<List<PublicProfile>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _justCreatedProfile = MutableStateFlow<PublicProfile?>(null)
    val justCreatedProfile: StateFlow<PublicProfile?> = _justCreatedProfile.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<PublicProfile>>(emptyList())
    val adminUsers: StateFlow<List<PublicProfile>> = _adminUsers.asStateFlow()

    private val _adminReports = MutableStateFlow<List<AbuseReport>>(emptyList())
    val adminReports: StateFlow<List<AbuseReport>> = _adminReports.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var seenNotificationIds = mutableSetOf<String>()
    private var initialNotificationsLoaded = false

    private val conversationFlows = ConcurrentHashMap<String, Flow<Conversation?>>()
    private val messageFlows = ConcurrentHashMap<String, Flow<List<ChatMessage>>>()
    private val typingFlows = ConcurrentHashMap<String, Flow<List<TypingIndicator>>>()
    private val draftFlows = ConcurrentHashMap<String, Flow<String>>()
    private val channelPostFlows = ConcurrentHashMap<String, Flow<List<ChannelPost>>>()
    private val lastTypingState = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val markedReceiptsCache = ConcurrentHashMap.newKeySet<String>()

    init {
        viewModelScope.launch {
            _isAdmin.value = socialRepo.checkIsAdmin()
            profileRepo.setOnlineStatus(true)
        }
        // Automatically resolve peer profiles in parallel for friendships, requests, conversations, and calls
        viewModelScope.launch {
            combine(friendships, conversations, friendRequests, calls) { fList, cList, rList, callList ->
                val uids = mutableSetOf<String>()
                fList.forEach { uids.addAll(it.participantUids) }
                cList.forEach { uids.addAll(it.participantUids) }
                rList.forEach { uids.addAll(it.participantUids) }
                callList.forEach { uids.addAll(it.participantUids) }
                uids.remove(currentUserId)
                uids.sorted()
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .collect { peerUids ->
                    val missing = peerUids.filter { !_peerProfiles.value.containsKey(it) }
                    if (missing.isNotEmpty()) {
                        val fetched = coroutineScope {
                            missing.map { uid ->
                                async { uid to profileRepo.getPublicProfile(uid) }
                            }.awaitAll()
                        }
                        val updated = _peerProfiles.value.toMutableMap()
                        var changed = false
                        fetched.forEach { (uid, profile) ->
                            if (profile != null) {
                                updated[uid] = profile
                                changed = true
                            }
                        }
                        if (changed) {
                            _peerProfiles.value = updated
                        }
                    }
                }
        }

        // Trigger Android local notifications on newly arriving unread items
        viewModelScope.launch {
            notifications.collect { list ->
                if (!initialNotificationsLoaded) {
                    seenNotificationIds.addAll(list.map { it.id })
                    initialNotificationsLoaded = true
                } else {
                    val prefs = preferences.value
                    list.filter { !it.isRead && !seenNotificationIds.contains(it.id) }.forEach { notif ->
                        seenNotificationIds.add(notif.id)
                        val shouldNotify = when (notif.type) {
                            "call" -> prefs.notifyCalls
                            "friend_request", "request_accepted" -> prefs.notifyFriendRequests
                            "group_message" -> prefs.notifyGroups
                            else -> prefs.notifyMessages
                        }
                        if (shouldNotify) {
                            NotificationHelper.showNotification(
                                context = appContext,
                                id = notif.id.hashCode(),
                                title = notif.title,
                                body = notif.body,
                                isCall = notif.type == "call"
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun showToast(msg: String) {
        _actionMessage.value = msg
    }

    fun dismissCongratulations() {
        _justCreatedProfile.value = null
    }

    fun createProfile(
        displayName: String,
        email: String,
        bio: String,
        phoneNumber: String,
        dob: String,
        photoBase64: String,
        isDiscoverableByPhone: Boolean,
        isDiscoverableByName: Boolean
    ) {
        if (displayName.isBlank()) {
            _actionMessage.value = "Please enter your full name."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = profileRepo.createAccountProfile(
                displayName = displayName,
                email = email,
                bio = bio,
                phoneNumber = phoneNumber,
                dob = dob,
                photoBase64 = photoBase64,
                isDiscoverableByPhone = isDiscoverableByPhone,
                isDiscoverableByName = isDiscoverableByName
            )
            _isBusy.value = false
            res.onSuccess { profile ->
                _justCreatedProfile.value = profile
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to create profile."
            }
        }
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        photoBase64: String,
        phoneNumber: String,
        dob: String,
        isDiscoverableByPhone: Boolean,
        isDiscoverableByName: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = profileRepo.updateProfile(
                displayName, bio, photoBase64, phoneNumber, dob, isDiscoverableByPhone, isDiscoverableByName
            )
            _isBusy.value = false
            _actionMessage.value = if (res.isSuccess) "Profile updated" else res.exceptionOrNull()?.localizedMessage ?: "Update failed"
        }
    }

    fun updatePrivacy(
        showOnlineStatus: Boolean,
        showLastSeen: Boolean,
        photoVisibility: String,
        bioVisibility: String,
        whoCanRequest: String,
        isDiscoverableByName: Boolean,
        isDiscoverableByPhone: Boolean,
        readReceiptsEnabled: Boolean,
        emailVisible: Boolean
    ) {
        viewModelScope.launch {
            val res = profileRepo.updatePrivacySettings(
                showOnlineStatus, showLastSeen, photoVisibility, bioVisibility,
                whoCanRequest, isDiscoverableByName, isDiscoverableByPhone,
                readReceiptsEnabled, emailVisible
            )
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update privacy"
            }
        }
    }

    fun searchPeople(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            val res = profileRepo.searchPeople(query)
            _isSearching.value = false
            res.onSuccess { _searchResults.value = it }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Search failed" }
        }
    }

    fun sendFriendRequest(target: PublicProfile) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = profileRepo.sendFriendRequest(me, target)
            _actionMessage.value = if (res.isSuccess) {
                "Friend request sent to ${target.displayName}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Could not send request"
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest, onOpenChat: (String) -> Unit = {}) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = profileRepo.acceptFriendRequest(request, me)
            res.onSuccess { dmId ->
                _actionMessage.value = "Connected with ${request.fromName}"
                onOpenChat(dmId)
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to accept request"
            }
        }
    }

    fun rejectFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            profileRepo.rejectFriendRequest(request)
                .onSuccess { _actionMessage.value = "Request declined" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to decline" }
        }
    }

    fun blockUser(targetUid: String) {
        viewModelScope.launch {
            profileRepo.blockUser(targetUid)
                .onSuccess { _actionMessage.value = "User blocked" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Block failed" }
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            profileRepo.unblockUser(targetUid)
                .onSuccess { _actionMessage.value = "User unblocked" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Unblock failed" }
        }
    }

    // --- Chat ---

    fun observeConversation(conversationId: String): Flow<Conversation?> =
        conversationFlows.getOrPut(conversationId) {
            chatRepo.observeConversation(conversationId, currentUserId)
                .catch { emit(null) }
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), replay = 1)
        }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messageFlows.getOrPut(conversationId) {
            chatRepo.observeMessages(conversationId, currentUserId)
                .catch { emit(emptyList()) }
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), replay = 1)
        }

    fun observeTyping(conversationId: String): Flow<List<TypingIndicator>> =
        typingFlows.getOrPut(conversationId) {
            chatRepo.observeTyping(conversationId, currentUserId)
                .catch { emit(emptyList()) }
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), replay = 1)
        }

    fun observeDraft(conversationId: String): Flow<String> =
        draftFlows.getOrPut(conversationId) {
            prefsRepo.observeDraft(conversationId)
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), replay = 1)
        }

    fun saveDraft(conversationId: String, text: String) {
        viewModelScope.launch { prefsRepo.saveDraft(conversationId, text) }
    }

    fun setTyping(conversation: Conversation, isTyping: Boolean) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val now = System.currentTimeMillis()
        val prev = lastTypingState[conversation.id]
        if (prev != null && prev.first == isTyping && (!isTyping || now - prev.second < 2500L)) {
            return
        }
        lastTypingState[conversation.id] = isTyping to now
        viewModelScope.launch { chatRepo.setTyping(conversation, me.displayName, isTyping) }
    }

    fun sendMessage(
        conversation: Conversation,
        text: String,
        messageType: String = "text",
        mediaBase64: String = "",
        mediaName: String = "",
        mediaMime: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        replyTo: ChatMessage? = null,
        forwarded: Boolean = false
    ) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = chatRepo.sendMessage(
                conversation = conversation,
                senderName = me.displayName,
                text = text,
                messageType = messageType,
                mediaBase64 = mediaBase64,
                mediaName = mediaName,
                mediaMime = mediaMime,
                latitude = latitude,
                longitude = longitude,
                replyToId = replyTo?.id ?: "",
                replyToText = replyTo?.text ?: "",
                replyToSender = replyTo?.senderName ?: "",
                forwarded = forwarded
            )
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Message failed to send"
            }
        }
    }

    fun markMessagesDeliveredAndRead(conversationId: String, messages: List<ChatMessage>) {
        val sendRead = privateAccount.value?.readReceiptsEnabled ?: true
        val toMark = messages.filter { msg ->
            msg.senderId != currentUserId && msg.id.isNotBlank() &&
                (!msg.deliveredTo.contains(currentUserId) || (sendRead && !msg.readBy.contains(currentUserId))) &&
                markedReceiptsCache.add("${conversationId}:${msg.id}:$sendRead")
        }
        if (toMark.isEmpty()) return
        viewModelScope.launch {
            chatRepo.markDeliveredAndRead(conversationId, toMark, sendRead)
        }
    }

    fun editMessage(conversationId: String, messageId: String, newText: String) {
        viewModelScope.launch {
            chatRepo.editMessage(conversationId, messageId, newText)
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Edit failed" }
        }
    }

    fun deleteMessage(conversationId: String, messageId: String) {
        viewModelScope.launch {
            chatRepo.deleteMessage(conversationId, messageId)
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Delete failed" }
        }
    }

    fun toggleReaction(conversationId: String, message: ChatMessage, emoji: String) {
        viewModelScope.launch { chatRepo.toggleReaction(conversationId, message, emoji) }
    }

    fun toggleStar(conversationId: String, message: ChatMessage) {
        viewModelScope.launch { chatRepo.toggleStar(conversationId, message) }
    }

    fun togglePin(conversationId: String, message: ChatMessage) {
        viewModelScope.launch { chatRepo.togglePinMessage(conversationId, message) }
    }

    fun createGroup(title: String, description: String, photoBase64: String, memberUids: List<String>, onCreated: (String) -> Unit) {
        if (title.isBlank()) {
            _actionMessage.value = "Group name is required"
            return
        }
        viewModelScope.launch {
            val res = chatRepo.createGroup(title, description, photoBase64, memberUids)
            res.onSuccess { id ->
                _actionMessage.value = "Group \"$title\" created"
                onCreated(id)
            }.onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to create group" }
        }
    }

    fun updateGroupSettings(groupId: String, title: String, description: String, onlyAdminsCanSend: Boolean) {
        viewModelScope.launch {
            chatRepo.updateGroupSettings(groupId, title, description, onlyAdminsCanSend)
                .onSuccess { _actionMessage.value = "Group updated" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update group" }
        }
    }

    fun addGroupMember(groupId: String, memberUid: String) {
        viewModelScope.launch { chatRepo.addGroupMember(groupId, memberUid) }
    }

    fun removeGroupMember(groupId: String, memberUid: String) {
        viewModelScope.launch { chatRepo.removeGroupMember(groupId, memberUid) }
    }

    fun toggleGroupAdmin(groupId: String, targetUid: String, makeAdmin: Boolean) {
        viewModelScope.launch { chatRepo.toggleGroupAdmin(groupId, targetUid, makeAdmin) }
    }

    // --- Statuses ---

    fun postStatus(contentType: String, text: String, caption: String, bgHex: String, mediaBase64: String) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val friendUids = friendships.value.flatMap { it.participantUids }.filter { it != currentUserId }
        viewModelScope.launch {
            val res = socialRepo.postStatus(me, friendUids, contentType, text, caption, bgHex, mediaBase64)
            _actionMessage.value = if (res.isSuccess) "Status published (24h)" else res.exceptionOrNull()?.localizedMessage ?: "Failed"
        }
    }

    fun markStatusViewed(statusId: String) {
        viewModelScope.launch { socialRepo.markStatusViewed(statusId) }
    }

    fun reactToStatus(statusId: String, emoji: String) {
        viewModelScope.launch {
            socialRepo.reactToStatus(statusId, emoji)
            _actionMessage.value = "Sent $emoji reaction"
        }
    }

    fun muteStatus(statusId: String) {
        viewModelScope.launch {
            socialRepo.muteStatus(statusId)
            _actionMessage.value = "Status muted"
        }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            socialRepo.deleteStatus(statusId)
            _actionMessage.value = "Status deleted"
        }
    }

    // --- Communities & Channels ---

    fun createCommunity(name: String, desc: String, announcement: String, groupIds: List<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            socialRepo.createCommunity(name, desc, announcement, groupIds)
                .onSuccess { _actionMessage.value = "Community created" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed" }
        }
    }

    fun joinCommunity(communityId: String) {
        viewModelScope.launch {
            socialRepo.joinCommunity(communityId)
                .onSuccess { _actionMessage.value = "Joined community" }
        }
    }

    fun postCommunityAnnouncement(communityId: String, text: String) {
        viewModelScope.launch {
            socialRepo.postCommunityAnnouncement(communityId, text)
                .onSuccess { _actionMessage.value = "Announcement posted" }
        }
    }

    fun createChannel(name: String, desc: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            socialRepo.createChannel(name, desc)
                .onSuccess { _actionMessage.value = "Channel created" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed" }
        }
    }

    fun toggleFollowChannel(channel: Channel) {
        viewModelScope.launch { socialRepo.toggleFollowChannel(channel) }
    }

    fun observeChannelPosts(channelId: String): Flow<List<ChannelPost>> =
        channelPostFlows.getOrPut(channelId) {
            socialRepo.observeChannelPosts(channelId, currentUserId)
                .catch { emit(emptyList()) }
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), replay = 1)
        }

    fun publishChannelPost(channelId: String, text: String, mediaBase64: String = "") {
        viewModelScope.launch {
            socialRepo.publishChannelPost(channelId, text, mediaBase64)
                .onSuccess { _actionMessage.value = "Update published" }
        }
    }

    fun reactToChannelPost(channelId: String, post: ChannelPost, emoji: String) {
        viewModelScope.launch { socialRepo.reactToChannelPost(channelId, post, emoji) }
    }

    // --- Calls ---

    fun startCall(callee: PublicProfile, callType: String) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            socialRepo.startCall(me, callee, callType)
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Could not place call" }
        }
    }

    fun acceptCall(callId: String) {
        viewModelScope.launch { socialRepo.updateCallStatus(callId, "active", 0L) }
    }

    fun rejectCall(callId: String) {
        viewModelScope.launch { socialRepo.updateCallStatus(callId, "rejected", 0L) }
    }

    fun endCall(callId: String, durationSeconds: Long) {
        viewModelScope.launch { socialRepo.updateCallStatus(callId, "ended", durationSeconds) }
    }

    fun updateCallMedia(call: CallRecord, muted: Boolean, cameraOff: Boolean) {
        viewModelScope.launch { socialRepo.updateCallMediaState(call, muted, cameraOff) }
    }

    // --- Notifications, Settings, Reports & Admin ---

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch { socialRepo.markNotificationRead(currentUserId, notifId) }
    }

    fun submitReport(targetType: String, targetId: String, reason: String) {
        viewModelScope.launch {
            socialRepo.submitReport(targetType, targetId, reason)
                .onSuccess { _actionMessage.value = "Report submitted for review" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to submit report" }
        }
    }

    fun loadAdminDashboard() {
        viewModelScope.launch {
            socialRepo.adminFetchAllUsers().onSuccess { _adminUsers.value = it }
            socialRepo.adminFetchAllReports().onSuccess { _adminReports.value = it }
        }
    }

    fun adminToggleSuspend(targetUid: String, suspend: Boolean) {
        viewModelScope.launch {
            socialRepo.adminToggleUserSuspension(targetUid, suspend)
                .onSuccess {
                    _actionMessage.value = if (suspend) "Account suspended" else "Account reinstated"
                    loadAdminDashboard()
                }
        }
    }

    fun adminResolveReport(reportId: String, status: String) {
        viewModelScope.launch {
            socialRepo.adminResolveReport(reportId, status)
                .onSuccess {
                    _actionMessage.value = "Report marked $status"
                    loadAdminDashboard()
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setAccentPreset(preset: AccentPreset) {
        viewModelScope.launch { prefsRepo.setAccentPreset(preset) }
    }

    fun setFontSizePreset(preset: FontSizePreset) {
        viewModelScope.launch { prefsRepo.setFontSizePreset(preset) }
    }

    fun setChatWallpaperEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setChatWallpaperEnabled(enabled) }
    }

    fun setNotificationPref(key: Preferences.Key<Boolean>, enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setNotificationPreference(key, enabled) }
    }

    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoDownloadMedia(enabled) }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch { prefsRepo.setAppLanguage(lang) }
    }

    fun removeLinkedDevice(device: String) {
        viewModelScope.launch {
            profileRepo.removeLinkedDevice(device)
                .onSuccess { _actionMessage.value = "Device removed" }
        }
    }

    fun deleteMyAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            profileRepo.deleteAccountData()
                .onSuccess { onDeleted() }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Could not delete account" }
        }
    }

    private companion object {
        const val TAG = "RikzenVM"
    }
}
