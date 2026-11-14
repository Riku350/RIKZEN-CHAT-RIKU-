package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.PublicProfile
import com.example.data.util.MediaUtils
import com.example.ui.theme.BrightGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ReadReceiptBlue
import com.example.ui.theme.RikzenEmerald
import com.example.ui.viewmodel.RikzenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    viewModel: RikzenViewModel,
    wallpaperEnabled: Boolean,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onStartCall: (PublicProfile, String) -> Unit
) {
    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = viewModel.currentUserId
    val conversationFlow = remember(conversationId, viewModel) { viewModel.observeConversation(conversationId) }
    val messagesFlow = remember(conversationId, viewModel) { viewModel.observeMessages(conversationId) }
    val typingFlow = remember(conversationId, viewModel) { viewModel.observeTyping(conversationId) }
    val draftFlow = remember(conversationId, viewModel) { viewModel.observeDraft(conversationId) }

    val conversation by conversationFlow.collectAsStateWithLifecycle(initialValue = null)
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val typingList by typingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val draft by draftFlow.collectAsStateWithLifecycle(initialValue = "")
    val peerProfiles by viewModel.peerProfiles.collectAsStateWithLifecycle()
    val allConversations by viewModel.conversations.collectAsStateWithLifecycle()
    val friendships by viewModel.friendships.collectAsStateWithLifecycle()
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    var inputText by remember(conversationId) { mutableStateOf(draft) }
    LaunchedEffect(draft) {
        if (inputText.isEmpty() && draft.isNotEmpty()) inputText = draft
    }

    // Debounced draft persistence & typing indicator to avoid I/O/network lag on every keystroke
    LaunchedEffect(conversationId, inputText) {
        if (inputText.isNotEmpty()) {
            conversation?.let { viewModel.setTyping(it, true) }
        }
        delay(380L)
        viewModel.saveDraft(conversationId, inputText)
        if (inputText.isEmpty()) {
            conversation?.let { viewModel.setTyping(it, false) }
        } else {
            delay(2200L)
            conversation?.let { viewModel.setTyping(it, false) }
        }
    }

    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var actionMenuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }
    var showEmojiBar by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    var showTopMenu by remember { mutableStateOf(false) }
    var showGroupInfoDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    // Audio recording state
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordedFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(conversationId) {
        onDispose {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
            viewModel.markMessagesDeliveredAndRead(conversationId, messages)
        }
    }

    val conv = conversation
    val peerUid = conv?.participantUids?.firstOrNull { it != currentUserId }
    val peerProfile = peerUid?.let { peerProfiles[it] }
    val title = when {
        conv == null -> "Loading..."
        conv.type == "group" -> conv.title
        else -> peerProfile?.displayName ?: "Direct Chat"
    }
    val isAdminInGroup = conv?.type == "group" && conv.adminUids.contains(currentUserId)
    val canSend = conv != null && (conv.type == "dm" || !conv.onlyAdminsCanSend || isAdminInGroup)

    // Launchers for attachments
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val c = conv ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val bmp = MediaUtils.decodeBitmapFromUriAsync(context, uri, 800)
                if (bmp != null) {
                    val b64 = MediaUtils.bitmapToBase64Async(bmp, maxBytes = 240_000, maxDim = 800)
                    bmp.recycle()
                    viewModel.sendMessage(c, text = "", messageType = "image", mediaBase64 = b64)
                }
            }
        }
        showAttachmentSheet = false
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val c = conv ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val encoded = MediaUtils.readFileAsBase64Async(context, uri, maxBytes = 260_000)
                if (encoded != null) {
                    viewModel.sendMessage(
                        c,
                        text = encoded.second,
                        messageType = "document",
                        mediaBase64 = encoded.first,
                        mediaName = encoded.second,
                        mediaMime = encoded.third
                    )
                } else {
                    viewModel.showToast("File is too large (> 260 KB) for inline chat transfer.")
                }
            }
        }
        showAttachmentSheet = false
    }

    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val out = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            recordedFile = out
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setOutputFile(out.absolutePath)
                mr.prepare()
                mr.start()
                recorder = mr
                isRecording = true
            } catch (_: Exception) {
                viewModel.showToast("Microphone unavailable on this device.")
            }
        } else {
            viewModel.showToast("Microphone permission is required for voice messages.")
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val c = conv ?: return@rememberLauncherForActivityResult
        if (granted) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (loc != null) {
                    viewModel.sendMessage(
                        c,
                        text = "Shared Location (${"%.4f".format(loc.latitude)}, ${"%.4f".format(loc.longitude)})",
                        messageType = "location",
                        latitude = loc.latitude,
                        longitude = loc.longitude
                    )
                } else {
                    viewModel.showToast("Unable to determine current location yet.")
                }
            } catch (_: SecurityException) {
            }
        } else {
            viewModel.showToast("Location permission denied.")
        }
        showAttachmentSheet = false
    }

    // Dialogs
    fullscreenPhoto?.let { b64 ->
        Dialog(onDismissRequest = { fullscreenPhoto = null }) {
            val bmp = rememberAsyncBase64Image(b64, maxDim = 1080)
            Box(modifier = Modifier.fillMaxSize().clickable { fullscreenPhoto = null }, contentAlignment = Alignment.Center) {
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = "Photo preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    forwardTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { forwardTarget = null },
            title = { Text("Forward Message") },
            text = {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(allConversations, key = { it.id }) { targetConv ->
                        val tPeerUid = targetConv.participantUids.firstOrNull { it != currentUserId }
                        val tName = if (targetConv.type == "group") targetConv.title else (peerProfiles[tPeerUid]?.displayName ?: "Direct Chat")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.sendMessage(
                                        targetConv,
                                        text = msg.text,
                                        messageType = msg.messageType,
                                        mediaBase64 = msg.mediaBase64,
                                        mediaName = msg.mediaName,
                                        forwarded = true
                                    )
                                    forwardTarget = null
                                    viewModel.showToast("Forwarded to $tName")
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tName, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { forwardTarget = null }) { Text("Cancel") } }
        )
    }

    actionMenuMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { actionMenuMessage = null },
            title = { Text("Message Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(listOf("❤️", "👍", "😂", "😮", "😢", "🙏")) { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier.clickable {
                                    viewModel.toggleReaction(conversationId, msg, emoji)
                                    actionMenuMessage = null
                                }.padding(4.dp)
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    TextButton(onClick = {
                        replyTarget = msg
                        actionMenuMessage = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Reply")
                    }
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Message", msg.text))
                        actionMenuMessage = null
                        viewModel.showToast("Copied")
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Copy")
                    }
                    TextButton(onClick = {
                        forwardTarget = msg
                        actionMenuMessage = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Forward")
                    }
                    TextButton(onClick = {
                        viewModel.togglePin(conversationId, msg)
                        actionMenuMessage = null
                    }) {
                        Icon(Icons.Default.PushPin, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (msg.pinned) "Unpin" else "Pin")
                    }
                    TextButton(onClick = {
                        viewModel.toggleStar(conversationId, msg)
                        actionMenuMessage = null
                    }) {
                        Icon(Icons.Default.Star, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (msg.starredBy.contains(currentUserId)) "Unstar" else "Star")
                    }
                    if (msg.senderId == currentUserId && !msg.deleted && msg.messageType == "text") {
                        TextButton(onClick = {
                            editingMessage = msg
                            inputText = msg.text
                            actionMenuMessage = null
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Edit")
                        }
                        TextButton(onClick = {
                            viewModel.deleteMessage(conversationId, msg.id)
                            actionMenuMessage = null
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionMenuMessage = null }) { Text("Close") } }
        )
    }

    if (showGroupInfoDialog && conv != null && conv.type == "group") {
        GroupInfoDialog(
            group = conv,
            currentUserId = currentUserId,
            peerProfiles = peerProfiles,
            friendProfiles = friendships.mapNotNull { f ->
                f.participantUids.firstOrNull { it != currentUserId }?.let { peerProfiles[it] }
            },
            onDismiss = { showGroupInfoDialog = false },
            onUpdateSettings = { t, d, onlyAdmins -> viewModel.updateGroupSettings(conv.id, t, d, onlyAdmins) },
            onAddMember = { uid -> viewModel.addGroupMember(conv.id, uid) },
            onRemoveMember = { uid -> viewModel.removeGroupMember(conv.id, uid) },
            onToggleAdmin = { uid, make -> viewModel.toggleGroupAdmin(conv.id, uid, make) },
            onLeaveGroup = {
                viewModel.removeGroupMember(conv.id, currentUserId)
                showGroupInfoDialog = false
                onBack()
            }
        )
    }

    if (showReportDialog && conv != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Abuse") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Describe the issue") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitReport(if (conv.type == "group") "group" else "user", conv.id, reason)
                        showReportDialog = false
                    },
                    enabled = reason.isNotBlank()
                ) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("Cancel") } }
        )
    }

    val someoneTyping = remember(typingList) {
        typingList.filter { it.uid != currentUserId && it.isTyping }
    }

    val displayedMessages = remember(messages, chatSearchQuery) {
        val q = chatSearchQuery.trim().lowercase()
        if (q.isBlank()) messages else messages.filter { it.text.lowercase().contains(q) }
    }

    val pinnedMessage = remember(messages, conv?.pinnedMessageId) {
        val pid = conv?.pinnedMessageId.orEmpty()
        if (pid.isBlank()) null else messages.firstOrNull { it.id == pid }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (conv?.type == "group") showGroupInfoDialog = true
                        }
                    ) {
                        RikzenAvatar(
                            photoBase64 = if (conv?.type == "group") conv.photoBase64 else (peerProfile?.photoBase64 ?: ""),
                            name = title,
                            size = 38.dp,
                            isOnline = conv?.type == "dm" && peerProfile != null && peerProfile.isOnline && peerProfile.showOnlineStatus
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val subtitle = when {
                                someoneTyping.isNotEmpty() -> "${someoneTyping.first().displayName} is typing..."
                                conv?.type == "group" -> "${conv.participantUids.size} members • Tap for info"
                                peerProfile?.isOnline == true && peerProfile.showOnlineStatus -> "Online"
                                peerProfile?.showLastSeen == true -> "Last seen recently"
                                else -> peerProfile?.rikzenId ?: ""
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (someoneTyping.isNotEmpty()) RikzenEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (conv?.type == "dm" && peerProfile != null) {
                        IconButton(onClick = { onStartCall(peerProfile, "voice") }, modifier = Modifier.testTag("voice_call_button")) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call")
                        }
                        IconButton(onClick = { onStartCall(peerProfile, "video") }, modifier = Modifier.testTag("video_call_button")) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                        }
                    }
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(Icons.Default.Search, contentDescription = "Search chat")
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                            if (conv?.type == "group") {
                                DropdownMenuItem(
                                    text = { Text("Group Info & Members") },
                                    leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                                    onClick = { showTopMenu = false; showGroupInfoDialog = true }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Report Abuse") },
                                leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) },
                                onClick = { showTopMenu = false; showReportDialog = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (wallpaperEnabled) {
                Image(
                    painter = painterResource(id = R.drawable.rikzen_chat_wallpaper_1794648912873),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.38f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                if (showSearchBar) {
                    OutlinedTextField(
                        value = chatSearchQuery,
                        onValueChange = { chatSearchQuery = it },
                        placeholder = { Text("Search messages...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { chatSearchQuery = ""; showSearchBar = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                if (pinnedMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = BrightGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = pinnedMessage.text.ifBlank { pinnedMessage.messageType },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = displayedMessages,
                        key = { it.id },
                        contentType = { it.messageType }
                    ) { msg ->
                        MessageBubble(
                            message = msg,
                            isMine = msg.senderId == currentUserId,
                            isGroup = conv?.type == "group",
                            totalPeers = (conv?.participantUids?.size ?: 2) - 1,
                            timeFormatter = timeFormatter,
                            onTap = { actionMenuMessage = msg },
                            onImageClick = { fullscreenPhoto = msg.mediaBase64 }
                        )
                    }
                }

                // Reply / Edit banner
                if (replyTarget != null || editingMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (editingMessage != null) "Editing message" else "Replying to ${replyTarget!!.senderName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold
                                )
                                Text(
                                    text = editingMessage?.text ?: replyTarget?.text.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { replyTarget = null; editingMessage = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        }
                    }
                }

                if (showEmojiBar) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(listOf("😀", "😂", "❤️", "🔥", "👑", "🕷️", "👍", "🎉", "🙏", "😎")) { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier.clickable { inputText += emoji }
                            )
                        }
                    }
                }

                if (showAttachmentSheet) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Surface(shape = CircleShape, color = ChampagneGold.copy(alpha = 0.2f), modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Photo", tint = ChampagneGold, modifier = Modifier.padding(12.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Photo", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                docPicker.launch(arrayOf("application/pdf", "text/plain", "*/*"))
                            }
                        ) {
                            Surface(shape = CircleShape, color = RikzenEmerald.copy(alpha = 0.2f), modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Description, contentDescription = "Document", tint = RikzenEmerald, modifier = Modifier.padding(12.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Document", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                } else {
                                    locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            }
                        ) {
                            Surface(shape = CircleShape, color = ReadReceiptBlue.copy(alpha = 0.2f), modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = ReadReceiptBlue, modifier = Modifier.padding(12.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Location", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Composer
                if (!canSend) {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Text(
                            text = "Only group administrators can send messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showEmojiBar = !showEmojiBar }) {
                                Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji")
                            }
                            IconButton(onClick = { showAttachmentSheet = !showAttachmentSheet }) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                            }
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text(if (isRecording) "Recording voice..." else "Message...") },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.weight(1f).testTag("chat_message_input")
                            )
                            Spacer(Modifier.width(6.dp))
                            if (inputText.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val c = conv ?: return@IconButton
                                        val editMsg = editingMessage
                                        if (editMsg != null) {
                                            viewModel.editMessage(c.id, editMsg.id, inputText)
                                            editingMessage = null
                                        } else {
                                            viewModel.sendMessage(c, text = inputText.trim(), replyTo = replyTarget)
                                            replyTarget = null
                                        }
                                        inputText = ""
                                        viewModel.saveDraft(conversationId, "")
                                        viewModel.setTyping(c, false)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(ChampagneGold)
                                        .testTag("chat_send_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = ObsidianBlack)
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (isRecording) {
                                            isRecording = false
                                            runCatching { recorder?.stop() }
                                            runCatching { recorder?.release() }
                                            recorder = null
                                            val file = recordedFile
                                            val c = conv
                                            if (file != null && c != null) {
                                                scope.launch {
                                                    val b64 = withContext(Dispatchers.IO) {
                                                        if (!file.exists()) return@withContext null
                                                        val bytes = file.readBytes()
                                                        if (bytes.size <= 280_000) MediaUtils.encodeBytesToBase64(bytes) else ""
                                                    }
                                                    if (b64 == null) return@launch
                                                    if (b64.isNotEmpty()) {
                                                        viewModel.sendMessage(c, text = "Voice message", messageType = "audio", mediaBase64 = b64)
                                                    } else {
                                                        viewModel.showToast("Voice clip too long (> 280 KB).")
                                                    }
                                                }
                                            }
                                        } else {
                                            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isRecording) MaterialTheme.colorScheme.error else RikzenEmerald)
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "Voice message",
                                        tint = ObsidianBlack
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

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isGroup: Boolean,
    totalPeers: Int,
    timeFormatter: SimpleDateFormat,
    onTap: () -> Unit,
    onImageClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeStr = remember(message.id, message.createdAt) {
        message.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: "..."
    }

    val bubbleColor = if (isMine) Color(0xFF2B230E) else ObsidianSurface
    val borderColor = if (isMine) ChampagneGold.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clickable(onClick = onTap)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isGroup && !isMine) {
                    Text(message.senderName, style = MaterialTheme.typography.labelSmall, color = BrightGold)
                    Spacer(Modifier.height(2.dp))
                }
                if (message.forwarded) {
                    Text("Forwarded", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message.replyToText.isNotBlank()) {
                    Surface(
                        color = ObsidianBlack.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(message.replyToSender, style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                            Text(message.replyToText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                when (message.messageType) {
                    "image" -> {
                        val bmp = rememberAsyncBase64Image(message.mediaBase64, maxDim = 640)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Shared photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onImageClick)
                            )
                        }
                    }
                    "document" -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = ChampagneGold)
                            Spacer(Modifier.width(8.dp))
                            Text(message.mediaName.ifBlank { "Document" }, style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                        }
                    }
                    "audio" -> {
                        var isPlaying by remember { mutableStateOf(false) }
                        var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
                        DisposableEffect(message.id) {
                            onDispose {
                                runCatching { activePlayer?.release() }
                                activePlayer = null
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    if (isPlaying) {
                                        runCatching { activePlayer?.stop() }
                                        runCatching { activePlayer?.release() }
                                        activePlayer = null
                                        isPlaying = false
                                        return@clickable
                                    }
                                    scope.launch {
                                        val tmpPath = withContext(Dispatchers.IO) {
                                            val bytes = MediaUtils.decodeBase64ToBytes(message.mediaBase64) ?: return@withContext null
                                            val tmp = File(context.cacheDir, "play_${message.id}.m4a")
                                            tmp.writeBytes(bytes)
                                            tmp.absolutePath
                                        } ?: return@launch
                                        val mp = MediaPlayer()
                                        runCatching {
                                            mp.setDataSource(tmpPath)
                                            mp.setOnPreparedListener { player ->
                                                player.start()
                                                isPlaying = true
                                            }
                                            mp.setOnCompletionListener { player ->
                                                isPlaying = false
                                                player.release()
                                                if (activePlayer == player) activePlayer = null
                                            }
                                            activePlayer = mp
                                            mp.prepareAsync()
                                        }.onFailure {
                                            mp.release()
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play voice", tint = RikzenEmerald)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPlaying) "Playing voice clip..." else "Voice Message • Tap to play", color = IvoryWhite, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    "location" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = ReadReceiptBlue)
                            Spacer(Modifier.width(6.dp))
                            Text(message.text, color = IvoryWhite, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> {
                        Text(
                            text = message.text,
                            color = IvoryWhite,
                            fontStyle = if (message.deleted) FontStyle.Italic else FontStyle.Normal,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.reactions.isNotEmpty()) {
                        Text(message.reactions.values.distinct().joinToString(" "), fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (message.starredBy.isNotEmpty()) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = BrightGold, modifier = Modifier.size(12.dp))
                    }
                    if (message.edited) {
                        Text("edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isMine) {
                        val readCount = message.readBy.count { it != message.senderId }
                        val deliveredCount = message.deliveredTo.count { it != message.senderId }
                        when {
                            readCount >= 1 && (readCount >= totalPeers || totalPeers <= 1) -> {
                                Icon(Icons.Default.DoneAll, contentDescription = "Read", tint = ReadReceiptBlue, modifier = Modifier.size(15.dp))
                            }
                            deliveredCount >= 1 -> {
                                Icon(Icons.Default.DoneAll, contentDescription = "Delivered", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                            }
                            else -> {
                                Icon(Icons.Default.Done, contentDescription = "Sent", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoDialog(
    group: Conversation,
    currentUserId: String,
    peerProfiles: Map<String, PublicProfile>,
    friendProfiles: List<PublicProfile>,
    onDismiss: () -> Unit,
    onUpdateSettings: (String, String, Boolean) -> Unit,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onToggleAdmin: (String, Boolean) -> Unit,
    onLeaveGroup: () -> Unit
) {
    val isAdmin = group.adminUids.contains(currentUserId)
    var title by remember { mutableStateOf(group.title) }
    var desc by remember { mutableStateOf(group.description) }
    var onlyAdmins by remember { mutableStateOf(group.onlyAdminsCanSend) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            LazyColumn(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Group Settings", style = MaterialTheme.typography.titleLarge)
                }
                if (isAdmin) {
                    item {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Group Name") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Only Admins Can Send", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = onlyAdmins, onCheckedChange = { onlyAdmins = it })
                        }
                        Button(
                            onClick = { onUpdateSettings(title, desc, onlyAdmins) },
                            colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Group Info") }
                    }
                }
                item {
                    Text("Members (${group.participantUids.size})", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                }
                items(group.participantUids, key = { it }) { uid ->
                    val name = if (uid == currentUserId) "You" else (peerProfiles[uid]?.displayName ?: uid.take(8))
                    val memberIsAdmin = group.adminUids.contains(uid)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (memberIsAdmin) Text("Admin", style = MaterialTheme.typography.labelSmall, color = RikzenEmerald)
                        }
                        if (isAdmin && uid != currentUserId) {
                            Row {
                                TextButton(onClick = { onToggleAdmin(uid, !memberIsAdmin) }) {
                                    Text(if (memberIsAdmin) "Demote" else "Make Admin")
                                }
                                TextButton(onClick = { onRemoveMember(uid) }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                if (isAdmin) {
                    val addable = friendProfiles.filter { !group.participantUids.contains(it.uid) }
                    if (addable.isNotEmpty()) {
                        item { Text("Add Friends to Group", style = MaterialTheme.typography.labelMedium, color = ChampagneGold) }
                        items(addable, key = { "add_${it.uid}" }) { f ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(f.displayName)
                                OutlinedButton(onClick = { onAddMember(f.uid) }) { Text("Add") }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onLeaveGroup) {
                            Text("Leave Group", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}
