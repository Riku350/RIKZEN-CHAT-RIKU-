package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BrightGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RikzenEmerald
import com.example.ui.viewmodel.RikzenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: RikzenViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val reports by viewModel.adminReports.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadAdminDashboard()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = ChampagneGold)
                        Spacer(Modifier.width(8.dp))
                        Text("Admin Dashboard")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAdminDashboard() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (!isAdmin) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Access Denied", style = MaterialTheme.typography.headlineMedium, color = CrimsonDanger)
                Spacer(Modifier.height(8.dp))
                Text("Administrator privileges are verified on the backend and restricted to the authorized application owner.")
            }
            return@Scaffold
        }

        val onlineCount = users.count { it.isOnline }
        val openReports = reports.count { it.status == "open" }
        val filteredUsers = remember(users, search) {
            val q = search.trim().lowercase()
            if (q.isBlank()) users else users.filter {
                it.displayNameLower.contains(q) || it.rikzenIdLower.contains(q)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminStatCard("Total Users", "${users.size}", Modifier.weight(1f))
                AdminStatCard("Online", "$onlineCount", Modifier.weight(1f))
                AdminStatCard("Open Reports", "$openReports", Modifier.weight(1f))
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Users (${users.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Reports (${reports.size})") })
            }

            if (selectedTab == 0) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter users by name or RIKZEN ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredUsers, key = { it.uid }) { user ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RikzenAvatar(user.photoBase64, user.displayName, size = 42.dp, isOnline = user.isOnline)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(user.rikzenId, style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                                    Text(
                                        if (user.statusSuspended) "SUSPENDED" else "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (user.statusSuspended) CrimsonDanger else RikzenEmerald
                                    )
                                }
                                OutlinedButton(onClick = { viewModel.adminToggleSuspend(user.uid, !user.statusSuspended) }) {
                                    Text(if (user.statusSuspended) "Reinstate" else "Suspend")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (reports.isEmpty()) {
                        item { Text("No abuse reports filed.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(reports, key = { it.id }) { report ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("${report.targetType.uppercase()} • ${report.targetId}", style = MaterialTheme.typography.labelMedium, color = BrightGold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(report.reason, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Status: ${report.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (report.status == "open") {
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.adminResolveReport(report.id, "resolved") },
                                                colors = ButtonDefaults.buttonColors(containerColor = RikzenEmerald, contentColor = ObsidianBlack)
                                            ) { Text("Resolve") }
                                            OutlinedButton(onClick = { viewModel.adminResolveReport(report.id, "dismissed") }) { Text("Dismiss") }
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
private fun AdminStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = BrightGold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
