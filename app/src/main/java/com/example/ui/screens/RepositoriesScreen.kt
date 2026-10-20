package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GitHubRepo
import com.example.ui.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.navigation.Screen
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoriesScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val repositories by viewModel.repositories.collectAsState()
    val isLoading by viewModel.isLoadingRepos.collectAsState()
    val repoError by viewModel.repoError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("GitHub Depolarım", color = CndnTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                actions = {
                    IconButton(onClick = { viewModel.loadRepositories() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = CndnTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CndnBackground)
            )
        },
        floatingActionButton = {
            if (connectionState is ConnectionState.Connected) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = CndnCyan,
                    contentColor = Color(0xFF002026),
                    modifier = Modifier.testTag("create_repo_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Yeni Depo")
                }
            }
        },
        containerColor = CndnBackground
    ) { innerPadding ->
        if (connectionState !is ConnectionState.Connected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = CndnCrimson, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("GitHub Hesabı Bağlı Değil", color = CndnTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Depolarınızı listelemek için lütfen Ayarlar bölümünden GitHub Personal Access Token (PAT) girin.", color = CndnTextSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onNavigate(Screen.Settings.route) },
                        colors = ButtonDefaults.buttonColors(containerColor = CndnCyan)
                    ) {
                        Text("Ayarlara Git", color = Color(0xFF002026), fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Scaffold
        }

        val filteredRepos = repositories.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.fullName.contains(searchQuery, ignoreCase = true)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Depo ara...", color = CndnTextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CndnTextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CndnCyan,
                        unfocusedBorderColor = CndnBorder,
                        focusedContainerColor = CndnSurface,
                        unfocusedContainerColor = CndnSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CndnCyan)
                    }
                }
            } else if (!repoError.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF281216)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Text(text = repoError!!, color = CndnCrimson, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    }
                }
            } else if (filteredRepos.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Depo bulunamadı.", color = CndnTextSecondary)
                    }
                }
            } else {
                items(filteredRepos, key = { it.id }) { repo ->
                    RepoCard(
                        repo = repo,
                        onOpen = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl))
                            context.startActivity(intent)
                        },
                        onBuild = {
                            onNavigate(Screen.BuildWizard.route)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        if (showCreateDialog) {
            CreateRepoDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, desc, isPrivate ->
                    showCreateDialog = false
                    viewModel.createRepository(
                        name = name,
                        description = desc,
                        isPrivate = isPrivate,
                        onSuccess = {
                            viewModel.loadRepositories()
                        },
                        onError = {}
                    )
                }
            )
        }
    }
}

@Composable
private fun RepoCard(repo: GitHubRepo, onOpen: () -> Unit, onBuild: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CndnSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (repo.private) CndnAmber else CndnEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = repo.name,
                        color = CndnTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Surface(
                    color = if (repo.private) Color(0xFF281E0D) else Color(0xFF10281B),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (repo.private) "Gizli" else "Açık",
                        color = if (repo.private) CndnAmber else CndnEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (!repo.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = repo.description, color = CndnTextSecondary, fontSize = 12.sp, maxLines = 2)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ForkRight, contentDescription = null, tint = CndnTextMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(repo.defaultBranch, color = CndnTextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Default.Star, contentDescription = null, tint = CndnAmber, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${repo.stargazersCount}", color = CndnTextMuted, fontSize = 11.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Aç", tint = CndnTextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Button(
                        onClick = onBuild,
                        colors = ButtonDefaults.buttonColors(containerColor = CndnCyan.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Derle", color = CndnCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRepoDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, isPrivate: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni GitHub Deposu Oluştur", color = CndnTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Depo Adı") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CndnCyan,
                        unfocusedBorderColor = CndnBorder
                    )
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Açıklama (İsteğe bağlı)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CndnCyan,
                        unfocusedBorderColor = CndnBorder
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gizli Depo (Private)", color = CndnTextPrimary, fontSize = 13.sp)
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, desc, isPrivate) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CndnCyan)
            ) {
                Text("Oluştur", color = Color(0xFF002026), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = CndnTextSecondary)
            }
        },
        containerColor = CndnSurface
    )
}
