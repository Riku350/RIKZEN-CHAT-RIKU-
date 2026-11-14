package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PublicProfile
import com.example.data.repository.PreferencesRepository
import com.example.data.util.MediaUtils
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.BrightGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.FontSizePreset
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.RikzenViewModel
import kotlinx.coroutines.launch

private enum class SettingsSubPage {
    HUB, EDIT_PROFILE, PRIVACY, APPEARANCE, NOTIFICATIONS, STORAGE, SECURITY, LANGUAGE, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndProfileScreen(
    viewModel: RikzenViewModel,
    myProfile: PublicProfile?,
    isAdmin: Boolean,
    onOpenAdminDashboard: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var subPage by remember { mutableStateOf(SettingsSubPage.HUB) }
    BackHandler {
        if (subPage == SettingsSubPage.HUB) onBack() else subPage = SettingsSubPage.HUB
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privateAccount by viewModel.privateAccount.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val peerProfiles by viewModel.peerProfiles.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account Data?") },
            text = { Text("This permanently deletes your RIKZEN profile, RIKZEN ID reservation, and private account record.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMyAccount(onLogout)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) { Text("Delete Permanently") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (subPage) {
                            SettingsSubPage.HUB -> "Profile & Settings"
                            SettingsSubPage.EDIT_PROFILE -> "Edit Profile"
                            SettingsSubPage.PRIVACY -> "Privacy"
                            SettingsSubPage.APPEARANCE -> "Appearance & Chats"
                            SettingsSubPage.NOTIFICATIONS -> "Notifications"
                            SettingsSubPage.STORAGE -> "Storage & Data"
                            SettingsSubPage.SECURITY -> "Security & Linked Devices"
                            SettingsSubPage.LANGUAGE -> "App Language"
                            SettingsSubPage.ABOUT -> "Help & About RIKZEN"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (subPage == SettingsSubPage.HUB) onBack() else subPage = SettingsSubPage.HUB
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (subPage) {
            SettingsSubPage.HUB -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (myProfile != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RikzenAvatar(myProfile.photoBase64, myProfile.displayName, size = 68.dp, isOnline = true)
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(myProfile.displayName, style = MaterialTheme.typography.titleLarge)
                                            Text(myProfile.rikzenId, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                            if (myProfile.bio.isNotBlank()) {
                                                Text(myProfile.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("RIKZEN ID", myProfile.rikzenId))
                                                viewModel.showToast("Copied ${myProfile.rikzenId}")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Copy ID")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "Connect with me on RIKZEN CHAT! My permanent RIKZEN ID is: ${myProfile.rikzenId}"
                                                    )
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share RIKZEN ID"))
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Share ID")
                                        }
                                        Button(
                                            onClick = { subPage = SettingsSubPage.EDIT_PROFILE },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                                        ) { Text("Edit") }
                                    }
                                }
                            }
                        }
                    }

                    if (isAdmin) {
                        item {
                            SettingsRow(
                                icon = Icons.Default.AdminPanelSettings,
                                title = "Admin Dashboard",
                                subtitle = "Manage users, moderation reports, and platform metrics",
                                onClick = onOpenAdminDashboard
                            )
                        }
                    }

                    item { SettingsRow(Icons.Default.Lock, "Privacy", "Online status, last seen, discovery & blocked users") { subPage = SettingsSubPage.PRIVACY } }
                    item { SettingsRow(Icons.Default.Security, "Security & Linked Devices", "Active devices, session management & account deletion") { subPage = SettingsSubPage.SECURITY } }
                    item { SettingsRow(Icons.Default.Palette, "Appearance & Chats", "Dark/Light mode, accent gold, font size & wallpaper") { subPage = SettingsSubPage.APPEARANCE } }
                    item { SettingsRow(Icons.Default.Notifications, "Notifications", "Messages, friend requests, groups, calls & status alerts") { subPage = SettingsSubPage.NOTIFICATIONS } }
                    item { SettingsRow(Icons.Default.Storage, "Storage & Data", "Cache usage, auto-download & local cache cleanup") { subPage = SettingsSubPage.STORAGE } }
                    item { SettingsRow(Icons.Default.Language, "App Language", "Current: ${prefs.appLanguage}") { subPage = SettingsSubPage.LANGUAGE } }
                    item { SettingsRow(Icons.AutoMirrored.Filled.HelpOutline, "Help & About RIKZEN CHAT", "CONNECT • CHAT • SHARE") { subPage = SettingsSubPage.ABOUT } }

                    item {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_logout_button"),
                            border = BorderStroke(1.dp, CrimsonDanger)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = CrimsonDanger)
                            Spacer(Modifier.width(8.dp))
                            Text("Log Out", color = CrimsonDanger, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            SettingsSubPage.EDIT_PROFILE -> {
                var name by remember { mutableStateOf(myProfile?.displayName ?: "") }
                var bio by remember { mutableStateOf(myProfile?.bio ?: "") }
                var phone by remember { mutableStateOf(privateAccount?.phoneNumber ?: "") }
                var dob by remember { mutableStateOf(privateAccount?.dob ?: "") }
                var photo by remember { mutableStateOf(myProfile?.photoBase64 ?: "") }
                var bitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }

                val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) {
                        scope.launch {
                            bitmapToCrop = MediaUtils.decodeBitmapFromUriAsync(context, uri, 640)
                        }
                    }
                }
                bitmapToCrop?.let { bmp ->
                    ImageCropDialog(bitmap = bmp, onDismiss = { bitmapToCrop = null }) { encoded ->
                        photo = encoded
                        bitmapToCrop = null
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RikzenAvatar(photo, name, size = 68.dp)
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Change Photo")
                        }
                        if (photo.isNotBlank()) {
                            TextButton(onClick = { photo = "" }) { Text("Remove") }
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("Date of Birth") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            viewModel.updateProfile(
                                displayName = name,
                                bio = bio,
                                photoBase64 = photo,
                                phoneNumber = phone,
                                dob = dob,
                                isDiscoverableByPhone = myProfile?.isDiscoverableByPhone ?: false,
                                isDiscoverableByName = myProfile?.isDiscoverableByName ?: true
                            )
                            subPage = SettingsSubPage.HUB
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Save Changes", fontWeight = FontWeight.Bold) }
                }
            }

            SettingsSubPage.PRIVACY -> {
                var showOnline by remember(myProfile) { mutableStateOf(myProfile?.showOnlineStatus ?: true) }
                var showLastSeen by remember(myProfile) { mutableStateOf(myProfile?.showLastSeen ?: true) }
                var discoverName by remember(myProfile) { mutableStateOf(myProfile?.isDiscoverableByName ?: true) }
                var discoverPhone by remember(myProfile) { mutableStateOf(myProfile?.isDiscoverableByPhone ?: false) }
                var readReceipts by remember(privateAccount) { mutableStateOf(privateAccount?.readReceiptsEnabled ?: true) }
                var emailVisible by remember(privateAccount) { mutableStateOf(privateAccount?.emailVisible ?: false) }
                val blocked = privateAccount?.blockedUids ?: emptyList()

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ToggleRow("Show Online Status", showOnline) {
                            showOnline = it
                            viewModel.updatePrivacy(showOnline, showLastSeen, myProfile?.photoVisibility ?: "everyone", myProfile?.bioVisibility ?: "everyone", myProfile?.whoCanRequest ?: "everyone", discoverName, discoverPhone, readReceipts, emailVisible)
                        }
                        ToggleRow("Show Last Seen", showLastSeen) {
                            showLastSeen = it
                            viewModel.updatePrivacy(showOnline, showLastSeen, myProfile?.photoVisibility ?: "everyone", myProfile?.bioVisibility ?: "everyone", myProfile?.whoCanRequest ?: "everyone", discoverName, discoverPhone, readReceipts, emailVisible)
                        }
                        ToggleRow("Read Receipts (Blue Ticks)", readReceipts) {
                            readReceipts = it
                            viewModel.updatePrivacy(showOnline, showLastSeen, myProfile?.photoVisibility ?: "everyone", myProfile?.bioVisibility ?: "everyone", myProfile?.whoCanRequest ?: "everyone", discoverName, discoverPhone, readReceipts, emailVisible)
                        }
                        ToggleRow("Discoverable by Name Search", discoverName) {
                            discoverName = it
                            viewModel.updatePrivacy(showOnline, showLastSeen, myProfile?.photoVisibility ?: "everyone", myProfile?.bioVisibility ?: "everyone", myProfile?.whoCanRequest ?: "everyone", discoverName, discoverPhone, readReceipts, emailVisible)
                        }
                        ToggleRow("Discoverable by Phone Number", discoverPhone) {
                            discoverPhone = it
                            viewModel.updatePrivacy(showOnline, showLastSeen, myProfile?.photoVisibility ?: "everyone", myProfile?.bioVisibility ?: "everyone", myProfile?.whoCanRequest ?: "everyone", discoverName, discoverPhone, readReceipts, emailVisible)
                        }
                        ToggleRow("Show Email to Contacts", emailVisible) {
                            emailVisible = it
                            viewModel.updatePrivacy(showOnline, showLastSeen, myProfile?.photoVisibility ?: "everyone", myProfile?.bioVisibility ?: "everyone", myProfile?.whoCanRequest ?: "everyone", discoverName, discoverPhone, readReceipts, emailVisible)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Blocked Users (${blocked.size})", style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                        if (blocked.isEmpty()) {
                            Text("No blocked users.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            blocked.forEach { buid ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(peerProfiles[buid]?.displayName ?: buid)
                                    TextButton(onClick = { viewModel.unblockUser(buid) }) { Text("Unblock") }
                                }
                            }
                        }
                    }
                }
            }

            SettingsSubPage.APPEARANCE -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Theme Mode", style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Text("Accent Color", style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = prefs.accentPreset == preset,
                                onClick = { viewModel.setAccentPreset(preset) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    Text("Font Size (Accessibility)", style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontSizePreset.entries.forEach { fp ->
                            FilterChip(
                                selected = prefs.fontSizePreset == fp,
                                onClick = { viewModel.setFontSizePreset(fp) },
                                label = { Text(fp.label) }
                            )
                        }
                    }
                    ToggleRow("Luxury Spiderweb Chat Wallpaper", prefs.chatWallpaperEnabled) {
                        viewModel.setChatWallpaperEnabled(it)
                    }
                }
            }

            SettingsSubPage.NOTIFICATIONS -> {
                val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            Button(onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("Enable System Push Notifications")
                            }
                        }
                    }
                    ToggleRow("New Messages", prefs.notifyMessages) { viewModel.setNotificationPref(PreferencesRepository.KEY_NOTIFY_MESSAGES, it) }
                    ToggleRow("Friend Requests", prefs.notifyFriendRequests) { viewModel.setNotificationPref(PreferencesRepository.KEY_NOTIFY_REQUESTS, it) }
                    ToggleRow("Group Messages", prefs.notifyGroups) { viewModel.setNotificationPref(PreferencesRepository.KEY_NOTIFY_GROUPS, it) }
                    ToggleRow("Incoming Calls", prefs.notifyCalls) { viewModel.setNotificationPref(PreferencesRepository.KEY_NOTIFY_CALLS, it) }
                    ToggleRow("Status Updates", prefs.notifyStatuses) { viewModel.setNotificationPref(PreferencesRepository.KEY_NOTIFY_STATUSES, it) }
                    ToggleRow("Sound & Vibration", prefs.soundAndVibration) { viewModel.setNotificationPref(PreferencesRepository.KEY_SOUND_VIBRATION, it) }
                }
            }

            SettingsSubPage.STORAGE -> {
                var cacheSize by remember { mutableStateOf("Calculating...") }
                LaunchedEffect(Unit) {
                    val bytes = MediaUtils.calculateDirectorySizeBytesAsync(context.cacheDir)
                    cacheSize = MediaUtils.formatByteSize(bytes)
                }
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Local Media Cache", style = MaterialTheme.typography.titleMedium)
                            Text("Using $cacheSize on device", style = MaterialTheme.typography.bodySmall, color = ChampagneGold)
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val bytes = MediaUtils.clearCacheDirAsync(context.cacheDir)
                                    cacheSize = MediaUtils.formatByteSize(bytes)
                                    viewModel.showToast("Local cache cleared")
                                }
                            }) { Text("Clear Local Cache") }
                        }
                    }
                    ToggleRow("Auto-Download Media", prefs.autoDownloadMedia) { viewModel.setAutoDownload(it) }
                }
            }

            SettingsSubPage.SECURITY -> {
                val devices = privateAccount?.linkedDevices ?: emptyList()
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Verified Email: ${privateAccount?.email ?: ""}", style = MaterialTheme.typography.bodyMedium)
                    Text("Linked Devices (${devices.size})", style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                    devices.forEach { dev ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Devices, contentDescription = null, tint = ChampagneGold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(dev)
                                }
                                TextButton(onClick = { viewModel.removeLinkedDevice(dev) }) { Text("Remove") }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        border = BorderStroke(1.dp, CrimsonDanger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonDanger)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete My Account", color = CrimsonDanger)
                    }
                }
            }

            SettingsSubPage.LANGUAGE -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("English", "Español", "Français", "Deutsch", "हिन्दी", "العربية").forEach { lang ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (prefs.appLanguage == lang) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.setAppLanguage(lang) }
                        ) {
                            Text(lang, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            SettingsSubPage.ABOUT -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(40.dp))
                    Text("RIKZEN CHAT", style = MaterialTheme.typography.headlineLarge, color = BrightGold)
                    Text("CONNECT • CHAT • SHARE", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                    Text("Version 1.0.0 • Native Android Build", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "RIKZEN CHAT provides real-time personal & group messaging, unique permanent RIKZEN IDs, privacy-controlled discovery, 24h status updates, communities, channels, and real-time voice & video call signaling.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
