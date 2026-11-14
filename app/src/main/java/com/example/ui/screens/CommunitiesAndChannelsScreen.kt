package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Channel
import com.example.data.model.Community
import com.example.data.model.Conversation
import com.example.ui.theme.BrightGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RikzenEmerald
import com.example.ui.viewmodel.RikzenViewModel

@Composable
fun CommunitiesAndChannelsScreen(
    currentUserId: String,
    communities: List<Community>,
    channels: List<Channel>,
    groups: List<Conversation>,
    viewModel: RikzenViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateCommunity by remember { mutableStateOf(false) }
    var showCreateChannel by remember { mutableStateOf(false) }
    var openedChannel by remember { mutableStateOf<Channel?>(null) }

    if (showCreateCommunity) {
        CreateCommunityDialog(
            groups = groups,
            onDismiss = { showCreateCommunity = false },
            onCreate = { name, desc, announcement, groupIds ->
                viewModel.createCommunity(name, desc, announcement, groupIds)
                showCreateCommunity = false
            }
        )
    }

    if (showCreateChannel) {
        CreateChannelDialog(
            onDismiss = { showCreateChannel = false },
            onCreate = { name, desc ->
                viewModel.createChannel(name, desc)
                showCreateChannel = false
            }
        )
    }

    openedChannel?.let { channel ->
        ChannelFeedDialog(
            channel = channel,
            currentUserId = currentUserId,
            viewModel = viewModel,
            onDismiss = { openedChannel = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Communities (${communities.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Channels (${channels.size})") })
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = { showCreateCommunity = true },
                        modifier = Modifier.fillMaxWidth().testTag("create_community_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create New Community", fontWeight = FontWeight.Bold)
                    }
                }
                if (communities.isEmpty()) {
                    item {
                        Text(
                            "Bring related groups together under one RIKZEN Community with broadcast announcements.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                } else {
                    items(communities, key = { it.id }) { comm ->
                        val isMember = comm.memberUids.contains(currentUserId)
                        val isAdmin = comm.adminUids.contains(currentUserId)
                        var newAnnouncement by remember { mutableStateOf("") }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RikzenAvatar("", comm.name, size = 44.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(comm.name, style = MaterialTheme.typography.titleMedium)
                                            Text("${comm.memberUids.size} members • ${comm.groupIds.size} groups", style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                                        }
                                    }
                                    if (!isMember) {
                                        OutlinedButton(onClick = { viewModel.joinCommunity(comm.id) }) { Text("Join") }
                                    }
                                }
                                if (comm.description.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(comm.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (comm.announcementText.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), shape = RoundedCornerShape(10.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Campaign, contentDescription = null, tint = BrightGold, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(comm.announcementText, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                if (isAdmin) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = newAnnouncement,
                                            onValueChange = { newAnnouncement = it },
                                            placeholder = { Text("Post announcement...") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = {
                                            if (newAnnouncement.isNotBlank()) {
                                                viewModel.postCommunityAnnouncement(comm.id, newAnnouncement)
                                                newAnnouncement = ""
                                            }
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Announce", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = { showCreateChannel = true },
                        modifier = Modifier.fillMaxWidth().testTag("create_channel_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Broadcast Channel", fontWeight = FontWeight.Bold)
                    }
                }
                if (channels.isEmpty()) {
                    item {
                        Text(
                            "Create a public Channel to broadcast updates to followers in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                } else {
                    items(channels, key = { it.id }) { chan ->
                        val isFollowing = chan.followerUids.contains(currentUserId)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth().clickable { openedChannel = chan }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RikzenAvatar("", chan.name, size = 48.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(chan.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${chan.followerCount} followers", style = MaterialTheme.typography.labelSmall, color = RikzenEmerald)
                                    if (chan.description.isNotBlank()) {
                                        Text(chan.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.toggleFollowChannel(chan) }) {
                                    Text(if (isFollowing) "Following" else "Follow")
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
private fun CreateCommunityDialog(
    groups: List<Conversation>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var announcement by remember { mutableStateOf("") }
    val selectedGroups = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("New Community", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Community Name *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = announcement, onValueChange = { announcement = it }, label = { Text("Welcome Announcement") }, modifier = Modifier.fillMaxWidth())
                if (groups.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Link Groups:", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.height(110.dp)) {
                        items(groups, key = { it.id }) { grp ->
                            val checked = selectedGroups.contains(grp.id)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (checked) selectedGroups.remove(grp.id) else selectedGroups.add(grp.id)
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    if (it) selectedGroups.add(grp.id) else selectedGroups.remove(grp.id)
                                })
                                Text(grp.title)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onCreate(name, desc, announcement, selectedGroups.toList()) },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                    ) { Text("Create") }
                }
            }
        }
    }
}

@Composable
private fun CreateChannelDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("New Channel", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Channel Name *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onCreate(name, desc) },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBlack)
                    ) { Text("Create") }
                }
            }
        }
    }
}

@Composable
private fun ChannelFeedDialog(
    channel: Channel,
    currentUserId: String,
    viewModel: RikzenViewModel,
    onDismiss: () -> Unit
) {
    val postsFlow = remember(channel.id, viewModel) { viewModel.observeChannelPosts(channel.id) }
    val posts by postsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isAdmin = channel.adminUids.contains(currentUserId)
    var postText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp).height(480.dp)) {
                Text(channel.name, style = MaterialTheme.typography.titleLarge, color = BrightGold)
                Text("${channel.followerCount} followers", style = MaterialTheme.typography.labelSmall, color = RikzenEmerald)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (posts.isEmpty()) {
                        item {
                            Text("No channel broadcasts yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(post.text, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf("❤️", "🔥", "👍").forEach { emoji ->
                                            val count = post.reactions.values.count { it == emoji }
                                            Text(
                                                text = if (count > 0) "$emoji $count" else emoji,
                                                fontSize = 14.sp,
                                                modifier = Modifier.clickable { viewModel.reactToChannelPost(channel.id, post, emoji) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAdmin) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = postText,
                            onValueChange = { postText = it },
                            placeholder = { Text("Publish update...") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (postText.isNotBlank()) {
                                viewModel.publishChannelPost(channel.id, postText)
                                postText = ""
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Publish", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}
