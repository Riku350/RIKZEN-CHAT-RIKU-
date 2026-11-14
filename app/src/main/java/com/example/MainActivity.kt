package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.UiState
import com.example.data.repository.ChatRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ProfileAndFriendsRepository
import com.example.data.repository.SocialAndCallsRepository
import com.example.data.repository.UserPreferences
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsTabContent
import com.example.ui.screens.CommunitiesAndChannelsScreen
import com.example.ui.screens.CongratulationsScreen
import com.example.ui.screens.CreateGroupDialog
import com.example.ui.screens.FindPeopleScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.SettingsAndProfileScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.screens.WelcomeAnimationScreen
import com.example.ui.theme.BrightGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RikzenChatTheme
import com.example.ui.viewmodel.RikzenViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val fromSplash = intent?.getBooleanExtra(EXTRA_FROM_SPLASH, false) == true
        setContent {
            val context = LocalContext.current
            val prefsRepo = remember { PreferencesRepository.getInstance(context) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())

            RikzenChatTheme(
                themeMode = prefs.themeMode,
                accentPreset = prefs.accentPreset,
                fontSizePreset = prefs.fontSizePreset
            ) {
                RikzenAppRoot(skipWelcome = fromSplash)
            }
        }
    }

    companion object {
        const val EXTRA_FROM_SPLASH = "extra_from_splash"
    }
}

@Composable
fun RikzenAppRoot(
    auth: FirebaseAuth = Firebase.auth,
    skipWelcome: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    // Welcome plays once per fresh app open via SplashActivity, never again on internal navigation
    var welcomeCompleted by rememberSaveable { mutableStateOf(skipWelcome) }
    var explicitSignOut by rememberSaveable { mutableStateOf(false) }

    Crossfade(
        targetState = welcomeCompleted,
        animationSpec = tween(durationMillis = 450),
        label = "welcome_to_app"
    ) { isWelcomeDone ->
        if (!isWelcomeDone) {
            WelcomeAnimationScreen(onFinished = { welcomeCompleted = true })
        } else {
            val user = currentUser
            if (user == null) {
                AuthScreen(
                    autoPromptGoogleSignIn = !explicitSignOut,
                    onAuthSuccess = { explicitSignOut = false }
                )
            } else {
                androidx.compose.runtime.key(user.uid) {
                    val viewModel: RikzenViewModel = viewModel(
                        key = user.uid,
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val dbId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(dbId)
                                RikzenViewModel(
                                    appContext = app,
                                    currentUserId = user.uid,
                                    profileRepo = ProfileAndFriendsRepository(db, auth),
                                    chatRepo = ChatRepository(db, auth),
                                    socialRepo = SocialAndCallsRepository(db, auth),
                                    prefsRepo = PreferencesRepository.getInstance(app)
                                )
                            }
                        }
                    )
                    AuthenticatedRikzenFlow(
                        userEmail = user.email ?: "",
                        userDisplayName = user.displayName ?: "",
                        viewModel = viewModel,
                        onSignOut = {
                            explicitSignOut = true
                            signOut(credentialManager = credentialManager, scope = scope)
                        }
                    )
                }
            }
        }
    }
}

private enum class AppSubRoute {
    NONE, FIND_PEOPLE, CHAT_DETAIL, SETTINGS, ADMIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedRikzenFlow(
    userEmail: String,
    userDisplayName: String,
    viewModel: RikzenViewModel,
    onSignOut: () -> Unit
) {
    val profileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val justCreated by viewModel.justCreatedProfile.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (justCreated != null) {
        CongratulationsScreen(
            profile = justCreated!!,
            onContinue = { viewModel.dismissCongratulations() }
        )
        return
    }

    when (val state = profileState) {
        UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Error loading profile: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        ProfileSetupScreen(
                            initialName = userDisplayName,
                            email = userEmail,
                            isBusy = isBusy,
                            onCreateProfile = { name, email, bio, phone, dob, photo, dPhone, dName ->
                                viewModel.createProfile(name, email, bio, phone, dob, photo, dPhone, dName)
                            },
                            onSignOut = onSignOut
                        )
                    }
                }
            } else {
                MainRikzenDashboard(
                    profile = profile,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainRikzenDashboard(
    profile: com.example.data.model.PublicProfile,
    viewModel: RikzenViewModel,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val friendships by viewModel.friendships.collectAsStateWithLifecycle()
    val friendRequests by viewModel.friendRequests.collectAsStateWithLifecycle()
    val peerProfiles by viewModel.peerProfiles.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val communities by viewModel.communities.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var subRoute by remember { mutableStateOf(AppSubRoute.NONE) }
    var activeChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var chatListSearch by rememberSaveable { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val pendingRequestsCount = remember(friendRequests) {
        friendRequests.count { it.toUid == viewModel.currentUserId && it.status == "pending" }
    }
    val unreadNotifCount = remember(notifications) { notifications.count { !it.isRead } }
    val friendProfilesList = remember(friendships, peerProfiles) {
        friendships.mapNotNull { f ->
            f.participantUids.firstOrNull { it != viewModel.currentUserId }?.let { peerProfiles[it] }
        }
    }

    // Active or Incoming Ringing Call Overlay
    val activeCall = remember(calls) {
        calls.firstOrNull { it.status == "ringing" || it.status == "active" }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            friends = friendProfilesList,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { title, desc, photo, members ->
                viewModel.createGroup(title, desc, photo, members) { newGroupId ->
                    showCreateGroupDialog = false
                    activeChatId = newGroupId
                    subRoute = AppSubRoute.CHAT_DETAIL
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (subRoute) {
            AppSubRoute.FIND_PEOPLE -> {
                FindPeopleScreen(
                    currentUserId = viewModel.currentUserId,
                    myProfile = profile,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    friendRequests = friendRequests,
                    friendships = friendships,
                    peerProfiles = peerProfiles,
                    onSearch = { viewModel.searchPeople(it) },
                    onSendRequest = { viewModel.sendFriendRequest(it) },
                    onAcceptRequest = { req ->
                        viewModel.acceptFriendRequest(req) { dmId ->
                            activeChatId = dmId
                            subRoute = AppSubRoute.CHAT_DETAIL
                        }
                    },
                    onRejectRequest = { viewModel.rejectFriendRequest(it) },
                    onBlockUser = { viewModel.blockUser(it) },
                    onOpenDm = { dmId ->
                        activeChatId = dmId
                        subRoute = AppSubRoute.CHAT_DETAIL
                    },
                    onBack = { subRoute = AppSubRoute.NONE }
                )
            }
            AppSubRoute.SETTINGS -> {
                SettingsAndProfileScreen(
                    viewModel = viewModel,
                    myProfile = profile,
                    isAdmin = isAdmin,
                    onOpenAdminDashboard = { subRoute = AppSubRoute.ADMIN },
                    onLogout = onSignOut,
                    onBack = { subRoute = AppSubRoute.NONE }
                )
            }
            AppSubRoute.ADMIN -> {
                AdminDashboardScreen(
                    viewModel = viewModel,
                    onBack = { subRoute = AppSubRoute.SETTINGS }
                )
            }
            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isExpanded = maxWidth >= 840.dp
                    if (!isExpanded && subRoute == AppSubRoute.CHAT_DETAIL && activeChatId != null) {
                        ChatDetailScreen(
                            conversationId = activeChatId!!,
                            viewModel = viewModel,
                            wallpaperEnabled = prefs.chatWallpaperEnabled,
                            showBackButton = true,
                            onBack = { subRoute = AppSubRoute.NONE },
                            onStartCall = { peer, type -> viewModel.startCall(peer, type) }
                        )
                    } else {
                        Scaffold(
                            contentWindowInsets = WindowInsets.navigationBars,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Image(
                                                painter = painterResource(id = R.drawable.rikzen_spider_icon_1794648913961),
                                                contentDescription = "RIKZEN Logo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = "RIKZEN CHAT",
                                                fontFamily = CinzelFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = { subRoute = AppSubRoute.FIND_PEOPLE },
                                            modifier = Modifier.testTag("topbar_find_people_button")
                                        ) {
                                            BadgedBox(
                                                badge = {
                                                    if (pendingRequestsCount > 0) {
                                                        Badge { Text("$pendingRequestsCount") }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.PersonAdd, contentDescription = "Find People")
                                            }
                                        }
                                        IconButton(onClick = { showCreateGroupDialog = true }) {
                                            Icon(Icons.Default.GroupAdd, contentDescription = "New Group")
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { showOverflowMenu = true },
                                                modifier = Modifier.testTag("topbar_more_button")
                                            ) {
                                                BadgedBox(
                                                    badge = {
                                                        if (unreadNotifCount > 0) Badge { Text("$unreadNotifCount") }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = showOverflowMenu,
                                                onDismissRequest = { showOverflowMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Profile & Settings") },
                                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                                    onClick = { showOverflowMenu = false; subRoute = AppSubRoute.SETTINGS }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Find & Add People") },
                                                    leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                                    onClick = { showOverflowMenu = false; subRoute = AppSubRoute.FIND_PEOPLE }
                                                )
                                                if (isAdmin) {
                                                    DropdownMenuItem(
                                                        text = { Text("Admin Dashboard") },
                                                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                                        onClick = { showOverflowMenu = false; subRoute = AppSubRoute.ADMIN }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text("Mark Notifications Read ($unreadNotifCount)") },
                                                    leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        notifications.filter { !it.isRead }.forEach { viewModel.markNotificationRead(it.id) }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                            },
                            bottomBar = {
                                if (!isExpanded) {
                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = selectedTab == 0,
                                            onClick = { selectedTab = 0 },
                                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                                            label = { Text("CHATS") },
                                            modifier = Modifier.testTag("nav_tab_chats")
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == 1,
                                            onClick = { selectedTab = 1 },
                                            icon = { Icon(Icons.Default.RadioButtonChecked, contentDescription = "Updates") },
                                            label = { Text("UPDATES") },
                                            modifier = Modifier.testTag("nav_tab_updates")
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == 2,
                                            onClick = { selectedTab = 2 },
                                            icon = { Icon(Icons.Default.Groups, contentDescription = "Communities") },
                                            label = { Text("COMMUNITIES") },
                                            modifier = Modifier.testTag("nav_tab_communities")
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == 3,
                                            onClick = { selectedTab = 3 },
                                            icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                                            label = { Text("CALLS") },
                                            modifier = Modifier.testTag("nav_tab_calls")
                                        )
                                    }
                                }
                            },
                            floatingActionButton = {
                                if (selectedTab == 0) {
                                    FloatingActionButton(
                                        onClick = { subRoute = AppSubRoute.FIND_PEOPLE },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.testTag("fab_new_chat")
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = "New Chat / Find People")
                                    }
                                }
                            }
                        ) { padding ->
                            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                                if (isExpanded) {
                                    NavigationRail {
                                        NavigationRailItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") }, label = { Text("Chats") })
                                        NavigationRailItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.RadioButtonChecked, contentDescription = "Updates") }, label = { Text("Updates") })
                                        NavigationRailItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Groups, contentDescription = "Communities") }, label = { Text("Groups") })
                                        NavigationRailItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Call, contentDescription = "Calls") }, label = { Text("Calls") })
                                    }
                                    VerticalDivider()
                                }

                                Box(modifier = if (isExpanded && selectedTab == 0) Modifier.weight(0.42f).fillMaxHeight() else Modifier.fillMaxSize()) {
                                    when (selectedTab) {
                                        0 -> ChatsTabContent(
                                            currentUserId = viewModel.currentUserId,
                                            conversations = conversations,
                                            friendships = friendships,
                                            peerProfiles = peerProfiles,
                                            searchQuery = chatListSearch,
                                            onSearchQueryChange = { chatListSearch = it },
                                            selectedConversationId = activeChatId,
                                            onOpenConversation = { cid ->
                                                activeChatId = cid
                                                if (!isExpanded) subRoute = AppSubRoute.CHAT_DETAIL
                                            },
                                            onOpenFindPeople = { subRoute = AppSubRoute.FIND_PEOPLE },
                                            onOpenCreateGroup = { showCreateGroupDialog = true }
                                        )
                                        1 -> UpdatesScreen(
                                            currentUserId = viewModel.currentUserId,
                                            myProfile = profile,
                                            statuses = statuses,
                                            peerProfiles = peerProfiles,
                                            onPostStatus = { type, text, caption, hex, media ->
                                                viewModel.postStatus(type, text, caption, hex, media)
                                            },
                                            onMarkViewed = { viewModel.markStatusViewed(it) },
                                            onReact = { sid, emoji -> viewModel.reactToStatus(sid, emoji) },
                                            onReplyToStatus = { st, reply ->
                                                val dmId = "dm_${listOf(viewModel.currentUserId, st.ownerUid).sorted().joinToString("_")}"
                                                val dmConv = conversations.firstOrNull { it.id == dmId }
                                                if (dmConv != null) {
                                                    viewModel.sendMessage(dmConv, text = "Replied to your status: $reply")
                                                }
                                            },
                                            onMuteStatus = { viewModel.muteStatus(it) },
                                            onDeleteStatus = { viewModel.deleteStatus(it) }
                                        )
                                        2 -> CommunitiesAndChannelsScreen(
                                            currentUserId = viewModel.currentUserId,
                                            communities = communities,
                                            channels = channels,
                                            groups = conversations.filter { it.type == "group" },
                                            viewModel = viewModel
                                        )
                                        3 -> CallsScreen(
                                            currentUserId = viewModel.currentUserId,
                                            calls = calls,
                                            friends = friendProfilesList,
                                            peerProfiles = peerProfiles,
                                            onStartCall = { peer, type -> viewModel.startCall(peer, type) }
                                        )
                                    }
                                }

                                // Tablet / Foldable List-Detail right pane
                                if (isExpanded && selectedTab == 0) {
                                    VerticalDivider()
                                    Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                                        val cid = activeChatId
                                        if (cid != null) {
                                            ChatDetailScreen(
                                                conversationId = cid,
                                                viewModel = viewModel,
                                                wallpaperEnabled = prefs.chatWallpaperEnabled,
                                                showBackButton = false,
                                                onBack = {},
                                                onStartCall = { peer, type -> viewModel.startCall(peer, type) }
                                            )
                                        } else {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                            ) {
                                                Text("Select a conversation to begin", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        if (activeCall != null) {
            ActiveCallOverlay(
                call = activeCall,
                currentUserId = viewModel.currentUserId,
                onAccept = { viewModel.acceptCall(activeCall.id) },
                onReject = { viewModel.rejectCall(activeCall.id) },
                onEnd = { duration -> viewModel.endCall(activeCall.id, duration) },
                onMediaStateChange = { muted, camOff -> viewModel.updateCallMedia(activeCall, muted, camOff) }
            )
        }
    }
}
