package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Conversation
import com.example.data.model.FriendRequest
import com.example.data.model.Friendship
import com.example.data.model.PublicProfile
import com.example.data.util.MediaUtils
import com.example.ui.theme.BrightGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RikzenEmerald
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatsTabContent(
    currentUserId: String,
    conversations: List<Conversation>,
    friendships: List<Friendship>,
    peerProfiles: Map<String, PublicProfile>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedConversationId: String?,
    onOpenConversation: (String) -> Unit,
    onOpenFindPeople: () -> Unit,
    onOpenCreateGroup: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val filteredConversations = remember(conversations, searchQuery, peerProfiles) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) conversations
        else conversations.filter { conv ->
            val peerUid = conv.participantUids.firstOrNull { it != currentUserId }
            val peer = peerUid?.let { peerProfiles[it] }
            val title = if (conv.type == "group") conv.title else (peer?.displayName ?: "Direct Chat")
            val rikzenId = peer?.rikzenId ?: ""
            title.lowercase().contains(q) ||
                conv.lastMessageText.lowercase().contains(q) ||
                rikzenId.lowercase().contains(q)
        }
    }

    val friendProfilesList = remember(friendships, peerProfiles) {
        friendships.mapNotNull { f ->
            val peerUid = f.participantUids.firstOrNull { it != currentUserId }
            peerUid?.let { peerProfiles[it] }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search people, messages and RIKZEN IDs", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("chats_search_input")
        )

        // Quick Friends Row
        if (friendProfilesList.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(friendProfilesList, key = { it.uid }) { friend ->
                    val pairId = listOf(currentUserId, friend.uid).sorted().joinToString("_")
                    val dmId = "dm_$pairId"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onOpenConversation(dmId) }
                            .padding(4.dp)
                    ) {
                        RikzenAvatar(
                            photoBase64 = friend.photoBase64,
                            name = friend.displayName,
                            size = 52.dp,
                            isOnline = friend.isOnline && friend.showOnlineStatus
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = friend.displayName.split(" ").firstOrNull() ?: friend.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (filteredConversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "No Conversations Yet" else "No Matching Chats",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Find friends by their unique RIKZEN ID or create a private group chat to start messaging in real time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onOpenFindPeople,
                            modifier = Modifier.testTag("empty_find_people_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Friends")
                        }
                        OutlinedButton(onClick = onOpenCreateGroup) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Group")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(
                    items = filteredConversations,
                    key = { it.id },
                    contentType = { it.type }
                ) { conv ->
                    val peerUid = conv.participantUids.firstOrNull { it != currentUserId }
                    val peer = peerUid?.let { peerProfiles[it] }
                    val title = if (conv.type == "group") conv.title else (peer?.displayName ?: "Rikzen Member")
                    val photo = if (conv.type == "group") conv.photoBase64 else (peer?.photoBase64 ?: "")
                    val isOnline = conv.type == "dm" && peer != null && peer.isOnline && peer.showOnlineStatus
                    val isSelected = conv.id == selectedConversationId
                    val timeStr = remember(conv.id, conv.lastMessageAt) {
                        conv.lastMessageAt?.toDate()?.let { timeFormatter.format(it) } ?: ""
                    }

                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConversation(conv.id) }
                            .testTag("conversation_row_${conv.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RikzenAvatar(
                                photoBase64 = photo,
                                name = title,
                                size = 52.dp,
                                isOnline = isOnline
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (conv.type == "group") {
                                        Text(
                                            text = "GROUP • ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (peer != null) {
                                        Text(
                                            text = "${peer.rikzenId} • ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        )
                                    }
                                    Text(
                                        text = conv.lastMessageText.ifBlank { "Tap to start chatting" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindPeopleScreen(
    currentUserId: String,
    myProfile: PublicProfile?,
    searchResults: List<PublicProfile>,
    isSearching: Boolean,
    friendRequests: List<FriendRequest>,
    friendships: List<Friendship>,
    peerProfiles: Map<String, PublicProfile>,
    onSearch: (String) -> Unit,
    onSendRequest: (PublicProfile) -> Unit,
    onAcceptRequest: (FriendRequest) -> Unit,
    onRejectRequest: (FriendRequest) -> Unit,
    onBlockUser: (String) -> Unit,
    onOpenDm: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    val received = remember(friendRequests) { friendRequests.filter { it.toUid == currentUserId && it.status == "pending" } }
    val sent = remember(friendRequests) { friendRequests.filter { it.fromUid == currentUserId && it.status == "pending" } }
    val rejected = remember(friendRequests) { friendRequests.filter { it.status == "rejected" } }
    val friendUids = remember(friendships) { friendships.flatMap { it.participantUids }.filter { it != currentUserId }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find & Add People") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("find_people_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (myProfile != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Your Unique RIKZEN ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(myProfile.rikzenId, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Share to connect", style = MaterialTheme.typography.labelSmall, color = RikzenEmerald)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("RIKZEN-XXXXXX, phone, or name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("find_people_query_input")
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSearch(query) },
                    enabled = query.isNotBlank() && !isSearching,
                    modifier = Modifier.height(54.dp).testTag("find_people_search_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                ) {
                    if (isSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ObsidianBlack)
                    else Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Search (${searchResults.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Requests (${received.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Sent (${sent.size})") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Friends (${friendUids.size})") })
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        if (searchResults.isEmpty()) {
                            item {
                                Text(
                                    text = "Search any user across devices by their exact RIKZEN ID (e.g., RIKZEN-RK7M92), opted-in phone number, or public name.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        } else {
                            items(searchResults, key = { it.uid }) { user ->
                                val isFriend = friendUids.contains(user.uid)
                                val isPending = sent.any { it.toUid == user.uid } || received.any { it.fromUid == user.uid }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RikzenAvatar(user.photoBase64, user.displayName, size = 50.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                                            Text(user.rikzenId, style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                                            if (user.bio.isNotBlank() && user.bioVisibility != "nobody") {
                                                Text(user.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                            }
                                        }
                                        when {
                                            isFriend -> {
                                                val dmId = "dm_${listOf(currentUserId, user.uid).sorted().joinToString("_")}"
                                                OutlinedButton(onClick = { onOpenDm(dmId) }) {
                                                    Text("Chat")
                                                }
                                            }
                                            isPending -> {
                                                Text("Pending", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            else -> {
                                                Button(
                                                    onClick = { onSendRequest(user) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                                                ) {
                                                    Text("Add Friend")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (received.isEmpty()) {
                            item { Text("No pending friend requests.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(received, key = { it.id }) { req ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RikzenAvatar(req.fromPhotoBase64, req.fromName, size = 48.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(req.fromName, style = MaterialTheme.typography.titleMedium)
                                                Text(req.fromRikzenId, style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { onAcceptRequest(req) },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = RikzenEmerald, contentColor = ObsidianBlack)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Accept")
                                            }
                                            OutlinedButton(
                                                onClick = { onRejectRequest(req) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Decline")
                                            }
                                            IconButton(onClick = {
                                                onRejectRequest(req)
                                                onBlockUser(req.fromUid)
                                            }) {
                                                Icon(Icons.Default.Block, contentDescription = "Block user", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (rejected.isNotEmpty()) {
                            item {
                                Text("Declined Requests (${rejected.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(rejected, key = { "rej_${it.id}" }) { r ->
                                Text("• ${r.fromName} (${r.fromRikzenId}) — ${r.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    2 -> {
                        if (sent.isEmpty()) {
                            item { Text("No sent requests pending.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(sent, key = { it.id }) { req ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(req.toName, style = MaterialTheme.typography.titleMedium)
                                            Text(req.toRikzenId, style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                                        }
                                        Text("Pending", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        val friends = friendUids.mapNotNull { peerProfiles[it] }
                        if (friends.isEmpty()) {
                            item { Text("No accepted friends yet. Search by RIKZEN ID to connect!", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(friends, key = { it.uid }) { f ->
                                val dmId = "dm_${listOf(currentUserId, f.uid).sorted().joinToString("_")}"
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.clickable { onOpenDm(dmId) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RikzenAvatar(f.photoBase64, f.displayName, size = 46.dp, isOnline = f.isOnline && f.showOnlineStatus)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(f.displayName, style = MaterialTheme.typography.titleMedium)
                                            Text(f.rikzenId, style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                                        }
                                        Button(onClick = { onOpenDm(dmId) }) {
                                            Text("Message")
                                        }
                                    }
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
fun CreateGroupDialog(
    friends: List<PublicProfile>,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, photoBase64: String, memberUids: List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateListOf<String>() }
    var bitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                bitmapToCrop = MediaUtils.decodeBitmapFromUriAsync(context, uri, 512)
            }
        }
    }

    bitmapToCrop?.let { bmp ->
        ImageCropDialog(bitmap = bmp, onDismiss = { bitmapToCrop = null }) { encoded ->
            photoBase64 = encoded
            bitmapToCrop = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Create New Group", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RikzenAvatar(photoBase64, name.ifBlank { "G" }, size = 56.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Group Icon")
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("group_name_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Add Friends (${selectedUids.size} selected)", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.height(160.dp)) {
                    if (friends.isEmpty()) {
                        item {
                            Text("Add friends first to invite them to groups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(friends, key = { it.uid }) { f ->
                            val checked = selectedUids.contains(f.uid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (checked) selectedUids.remove(f.uid) else selectedUids.add(f.uid) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    if (it) selectedUids.add(f.uid) else selectedUids.remove(f.uid)
                                })
                                RikzenAvatar(f.photoBase64, f.displayName, size = 34.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(f.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(name, desc, photoBase64, selectedUids.toList()) },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                    ) {
                        Text("Create Group")
                    }
                }
            }
        }
    }
}
