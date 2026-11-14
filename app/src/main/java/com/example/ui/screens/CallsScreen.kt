package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CallRecord
import com.example.data.model.PublicProfile
import com.example.ui.theme.BrightGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RikzenEmerald
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CallsScreen(
    currentUserId: String,
    calls: List<CallRecord>,
    friends: List<PublicProfile>,
    peerProfiles: Map<String, PublicProfile>,
    onStartCall: (PublicProfile, String) -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()) }
    var showNewCallDialog by remember { mutableStateOf(false) }

    if (showNewCallDialog) {
        Dialog(onDismissRequest = { showNewCallDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Start a Call", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        if (friends.isEmpty()) {
                            item { Text("Add friends first to place voice or video calls.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(friends, key = { it.uid }) { f ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RikzenAvatar(f.photoBase64, f.displayName, size = 42.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(f.displayName, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { showNewCallDialog = false; onStartCall(f, "voice") }) {
                                        Icon(Icons.Default.Call, contentDescription = "Voice call", tint = RikzenEmerald)
                                    }
                                    IconButton(onClick = { showNewCallDialog = false; onStartCall(f, "video") }) {
                                        Icon(Icons.Default.Videocam, contentDescription = "Video call", tint = ChampagneGold)
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showNewCallDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = { showNewCallDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("new_call_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Place Voice or Video Call", fontWeight = FontWeight.Bold)
            }
        }

        if (calls.isEmpty()) {
            item {
                Text(
                    "No call history yet. Tap the button above or call directly from any friend's chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        } else {
            items(calls, key = { it.id }) { call ->
                val isOutgoing = call.callerUid == currentUserId
                val peerUid = if (isOutgoing) call.calleeUid else call.callerUid
                val peerName = if (isOutgoing) call.calleeName else call.callerName
                val peerPhoto = if (isOutgoing) call.calleePhotoBase64 else call.callerPhotoBase64
                val peer = peerProfiles[peerUid]
                val dateStr = call.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: "Now"

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RikzenAvatar(peerPhoto, peerName, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peerName, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isOutgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                                    contentDescription = null,
                                    tint = if (call.status == "rejected" || call.status == "missed") CrimsonDanger else RikzenEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                val durText = if (call.durationSeconds > 0) " • ${call.durationSeconds}s" else ""
                                Text(
                                    text = "${call.status.replaceFirstChar { it.uppercase() }}$durText • $dateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (peer != null) {
                            IconButton(onClick = { onStartCall(peer, call.callType) }) {
                                Icon(
                                    imageVector = if (call.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Call again",
                                    tint = ChampagneGold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    call: CallRecord,
    currentUserId: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: (Long) -> Unit,
    onMediaStateChange: (Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isIncomingRinging = call.calleeUid == currentUserId && call.status == "ringing"
    val peerName = if (call.callerUid == currentUserId) call.calleeName else call.callerName
    val peerPhoto = if (call.callerUid == currentUserId) call.calleePhotoBase64 else call.callerPhotoBase64

    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(call.callType != "video") }
    var useFrontCamera by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasMicPermission = perms[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
        hasCamPermission = perms[Manifest.permission.CAMERA] ?: hasCamPermission
    }

    LaunchedEffect(call.id, call.status) {
        val needed = buildList {
            if (!hasMicPermission) add(Manifest.permission.RECORD_AUDIO)
            if (call.callType == "video" && !hasCamPermission) add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
        if (call.status == "active") {
            while (true) {
                delay(1000L)
                elapsedSeconds += 1L
            }
        }
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(previewViewRef, useFrontCamera, isCameraOff, hasCamPermission, call.status) {
        val pv = previewViewRef ?: return@LaunchedEffect
        if (isCameraOff || !hasCamPermission || call.status != "active") return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = pv.surfaceProvider
                }
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Real CameraX local preview when video call & permission granted
        if (!isCameraOff && hasCamPermission && call.status == "active") {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewViewRef = it }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SpiderWebPattern(modifier = Modifier.fillMaxSize(), alpha = 0.16f)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 36.dp)) {
                Text(
                    text = if (call.callType == "video") "RIKZEN VIDEO CALL" else "RIKZEN VOICE CALL",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
                Spacer(Modifier.height(22.dp))
                RikzenAvatar(peerPhoto, peerName, size = 104.dp)
                Spacer(Modifier.height(16.dp))
                Text(peerName, style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
                Spacer(Modifier.height(6.dp))
                val statusLabel = when {
                    isIncomingRinging -> "Incoming ${call.callType} call..."
                    call.status == "ringing" -> "Ringing..."
                    call.status == "active" -> "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
                    else -> call.status
                }
                Text(statusLabel, style = MaterialTheme.typography.titleMedium, color = BrightGold)
            }

            if (isIncomingRinging) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = onReject,
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(CrimsonDanger)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Decline call", tint = IvoryWhite, modifier = Modifier.size(30.dp))
                    }
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(RikzenEmerald)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Accept call", tint = ObsidianBlack, modifier = Modifier.size(30.dp))
                    }
                }
            } else {
                Surface(
                    color = ObsidianBlack.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(bottom = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                onMediaStateChange(isMuted, isCameraOff)
                            },
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(if (isMuted) ChampagneGold else ColorOverlay)
                        ) {
                            Icon(
                                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute microphone",
                                tint = if (isMuted) ObsidianBlack else IvoryWhite
                            )
                        }
                        if (call.callType == "video") {
                            IconButton(
                                onClick = {
                                    isCameraOff = !isCameraOff
                                    onMediaStateChange(isMuted, isCameraOff)
                                },
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(if (isCameraOff) ChampagneGold else ColorOverlay)
                            ) {
                                Icon(
                                    if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                    contentDescription = "Toggle camera",
                                    tint = if (isCameraOff) ObsidianBlack else IvoryWhite
                                )
                            }
                            IconButton(
                                onClick = { useFrontCamera = !useFrontCamera },
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(ColorOverlay)
                            ) {
                                Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = IvoryWhite)
                            }
                        }
                        IconButton(
                            onClick = { onEnd(elapsedSeconds) },
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(CrimsonDanger)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = IvoryWhite)
                        }
                    }
                }
            }
        }
    }
}

private val ColorOverlay = androidx.compose.ui.graphics.Color(0xFF232330)
