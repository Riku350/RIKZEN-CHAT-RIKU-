package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PublicProfile
import com.example.data.model.StatusUpdate
import com.example.data.util.MediaUtils
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RikzenEmerald
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun UpdatesScreen(
    currentUserId: String,
    myProfile: PublicProfile?,
    statuses: List<StatusUpdate>,
    peerProfiles: Map<String, PublicProfile>,
    onPostStatus: (contentType: String, text: String, caption: String, bgHex: String, mediaBase64: String) -> Unit,
    onMarkViewed: (String) -> Unit,
    onReact: (String, String) -> Unit,
    onReplyToStatus: (StatusUpdate, String) -> Unit,
    onMuteStatus: (String) -> Unit,
    onDeleteStatus: (String) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var viewingStatus by remember { mutableStateOf<StatusUpdate?>(null) }

    val myStatuses = remember(statuses) { statuses.filter { it.ownerUid == currentUserId } }
    val friendStatuses = remember(statuses) { statuses.filter { it.ownerUid != currentUserId } }

    if (showCreateDialog) {
        CreateStatusDialog(
            onDismiss = { showCreateDialog = false },
            onPublish = { type, text, caption, bgHex, media ->
                onPostStatus(type, text, caption, bgHex, media)
                showCreateDialog = false
            }
        )
    }

    viewingStatus?.let { st ->
        StatusViewerDialog(
            status = st,
            isMine = st.ownerUid == currentUserId,
            peerProfiles = peerProfiles,
            onDismiss = { viewingStatus = null },
            onViewed = { onMarkViewed(st.id) },
            onReact = { emoji -> onReact(st.id, emoji) },
            onReply = { reply -> onReplyToStatus(st, reply) },
            onMute = {
                onMuteStatus(st.id)
                viewingStatus = null
            },
            onDelete = {
                onDeleteStatus(st.id)
                viewingStatus = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (myStatuses.isNotEmpty()) viewingStatus = myStatuses.first()
                        else showCreateDialog = true
                    }
                    .testTag("my_status_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        RikzenAvatar(
                            photoBase64 = myProfile?.photoBase64 ?: "",
                            name = myProfile?.displayName ?: "Me",
                            size = 56.dp,
                            statusRingState = if (myStatuses.isNotEmpty()) 1 else 0
                        )
                        Surface(
                            shape = CircleShape,
                            color = RikzenEmerald,
                            modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.padding(2.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My Status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (myStatuses.isEmpty()) "Tap to share a 24h photo or text status"
                            else "${myStatuses.size} active update(s) • Expires in 24h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack),
                        modifier = Modifier.testTag("add_status_button")
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        if (myStatuses.isNotEmpty()) {
            item {
                Text("Your Active Updates", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
            }
            items(myStatuses, key = { it.id }) { st ->
                val timeStr = st.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Just now"
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().clickable { viewingStatus = st }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (st.contentType == "image") "Photo: ${st.caption.ifBlank { "Status Photo" }}" else st.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "$timeStr • ${st.viewerUids.size} views",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteStatus(st.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete status", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(4.dp))
            Text("Recent Updates from Friends", style = MaterialTheme.typography.labelMedium, color = RikzenEmerald)
        }

        if (friendStatuses.isEmpty()) {
            item {
                Text(
                    "No recent friend statuses in the last 24 hours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        } else {
            items(friendStatuses, key = { it.id }) { st ->
                val isViewed = st.viewerUids.contains(currentUserId)
                val timeStr = st.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Recently"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewingStatus = st }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RikzenAvatar(
                        photoBase64 = st.ownerPhotoBase64,
                        name = st.ownerName,
                        size = 54.dp,
                        statusRingState = if (isViewed) 2 else 1
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(st.ownerName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${if (st.contentType == "image") "Photo update" else st.text.take(36)} • $timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateStatusDialog(
    onDismiss: () -> Unit,
    onPublish: (type: String, text: String, caption: String, bgHex: String, mediaBase64: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf("#191923") }
    var photoBase64 by remember { mutableStateOf("") }

    val palette = listOf("#191923", "#2B2002", "#0E2B1B", "#3B0D11", "#1E293B")

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = MediaUtils.decodeBitmapFromUriAsync(context, uri, 720)
                if (bmp != null) {
                    photoBase64 = MediaUtils.bitmapToBase64Async(bmp, maxBytes = 220_000, maxDim = 720)
                    bmp.recycle()
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("New 24h Status", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                if (photoBase64.isNotBlank()) {
                    val bmp = rememberAsyncBase64Image(photoBase64, maxDim = 720)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Status photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Caption") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { photoBase64 = "" }) { Text("Switch to Text Status") }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Share a thought (expires in 24h)") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("status_text_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        palette.forEach { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedHex = hex }
                                    .then(
                                        if (selectedHex == hex) Modifier.padding(2.dp) else Modifier
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Photo Instead")
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (photoBase64.isNotBlank()) {
                                onPublish("image", "", caption, selectedHex, photoBase64)
                            } else {
                                onPublish("text", text, "", selectedHex, "")
                            }
                        },
                        enabled = photoBase64.isNotBlank() || text.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                    ) {
                        Text("Publish")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusViewerDialog(
    status: StatusUpdate,
    isMine: Boolean,
    peerProfiles: Map<String, PublicProfile>,
    onDismiss: () -> Unit,
    onViewed: () -> Unit,
    onReact: (String) -> Unit,
    onReply: (String) -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = remember(status.id) { Animatable(0f) }
    var replyText by remember { mutableStateOf("") }
    var showViewers by remember { mutableStateOf(false) }

    LaunchedEffect(status.id) {
        onViewed()
        progress.animateTo(1f, animationSpec = tween(durationMillis = 7000, easing = LinearEasing))
    }

    val bgColor = remember(status.backgroundColorHex) {
        try {
            Color(android.graphics.Color.parseColor(status.backgroundColorHex))
        } catch (_: Exception) {
            ObsidianBlack
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (status.contentType == "image") ObsidianBlack else bgColor)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (status.contentType == "image") {
                val bmp = rememberAsyncBase64Image(status.mediaBase64, maxDim = 1080)
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = status.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = status.text,
                        color = IvoryWhite,
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Top bar
            Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp)) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = ChampagneGold
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RikzenAvatar(status.ownerPhotoBase64, status.ownerName, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(status.ownerName, color = IvoryWhite, style = MaterialTheme.typography.titleSmall)
                            Text(status.ownerRikzenId, color = ChampagneGold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row {
                        if (isMine) {
                            IconButton(onClick = { showViewers = !showViewers }) {
                                Icon(Icons.Default.Visibility, contentDescription = "Viewers", tint = IvoryWhite)
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = IvoryWhite)
                            }
                        } else {
                            IconButton(onClick = onMute) {
                                Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Mute", tint = IvoryWhite)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = IvoryWhite)
                        }
                    }
                }
            }

            // Bottom area: caption + reactions + reply / viewer list
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(ObsidianBlack.copy(alpha = 0.72f))
                    .padding(16.dp)
            ) {
                if (status.caption.isNotBlank()) {
                    Text(status.caption, color = IvoryWhite, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (showViewers && isMine) {
                    Text("Viewed by (${status.viewerUids.size}):", color = ChampagneGold, style = MaterialTheme.typography.labelMedium)
                    status.viewerUids.forEach { vid ->
                        val name = peerProfiles[vid]?.displayName ?: if (vid == status.ownerUid) "You" else vid.take(8)
                        Text("• $name", color = IvoryWhite, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (!isMine) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("❤️", "🔥", "👏", "😂", "👑").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier.clickable { onReact(emoji) }.padding(6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Reply privately...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    onReply(replyText)
                                    replyText = ""
                                    onDismiss()
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply", tint = ChampagneGold)
                        }
                    }
                }
            }
        }
    }
}
