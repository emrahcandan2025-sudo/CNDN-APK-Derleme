package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.components.CndnBuildItemCard
import com.example.ui.components.CndnStatCard
import com.example.ui.components.CndnStatusBadge
import com.example.ui.navigation.Screen
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val allBuilds by viewModel.allBuilds.collectAsState()
    val activeBuilds by viewModel.activeBuilds.collectAsState()
    val successCount by viewModel.successCount.collectAsState()
    val failureCount by viewModel.failureCount.collectAsState()
    val downloadsCount by viewModel.downloadsCount.collectAsState()

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.analyzeZip(context, uri)
            onNavigate(Screen.BuildWizard.route)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CndnBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // App Header / Hero
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CNDN APK",
                        color = CndnCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Derleme Merkezi",
                        color = CndnTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                IconButton(
                    onClick = { onNavigate(Screen.Settings.route) },
                    modifier = Modifier.testTag("home_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = CndnTextSecondary
                    )
                }
            }
        }

        // GitHub Connection Status Card
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (connectionState) {
                        is ConnectionState.Connected -> Color(0xFF102319)
                        is ConnectionState.Connecting -> Color(0xFF132230)
                        else -> Color(0xFF231618)
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (connectionState) {
                        is ConnectionState.Connected -> CndnEmerald.copy(alpha = 0.5f)
                        is ConnectionState.Connecting -> CndnCyan.copy(alpha = 0.5f)
                        else -> CndnCrimson.copy(alpha = 0.5f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.Settings.route) }
                    .testTag("github_status_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    when (val state = connectionState) {
                        is ConnectionState.Connected -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                if (!state.user.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = state.user.avatarUrl,
                                        contentDescription = "GitHub Avatar",
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, CndnEmerald, CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(CndnEmerald.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = CndnEmerald)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = state.user.login,
                                            color = CndnTextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(CndnEmerald)
                                        )
                                    }
                                    Text(
                                        text = "${state.user.publicRepos} açık depo • GitHub Bağlı",
                                        color = CndnTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = CndnTextSecondary
                            )
                        }

                        is ConnectionState.Connecting -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = CndnCyan,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "GitHub doğrulanıyor...",
                                    color = CndnCyan,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(CndnCrimson.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = CndnCrimson)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "GitHub Bağlantısı Gerekli",
                                        color = CndnCrimson,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Derleme başlatmak için Token (PAT) girin",
                                        color = CndnTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = CndnCrimson
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Buttons
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onNavigate(Screen.BuildWizard.route) },
                    colors = ButtonDefaults.buttonColors(containerColor = CndnCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("quick_new_build_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Color(0xFF002026),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Yeni Derleme",
                        color = Color(0xFF002026),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                OutlinedButton(
                    onClick = { zipPickerLauncher.launch("application/zip") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CndnTextPrimary),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, CndnBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("quick_select_zip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = CndnAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ZIP Dosyası Seç",
                        color = CndnTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Stat Grid (2x2)
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "DERLEME İSTATİSTİKLERİ",
                color = CndnTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CndnStatCard(
                    title = "Toplam Derleme",
                    value = allBuilds.size.toString(),
                    icon = Icons.Default.Memory,
                    accentColor = CndnCyan,
                    modifier = Modifier.weight(1f)
                )
                CndnStatCard(
                    title = "Başarılı APK",
                    value = successCount.toString(),
                    icon = Icons.Default.CheckCircle,
                    accentColor = CndnEmerald,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CndnStatCard(
                    title = "Hatalı / İptal",
                    value = failureCount.toString(),
                    icon = Icons.Default.Error,
                    accentColor = CndnCrimson,
                    modifier = Modifier.weight(1f)
                )
                CndnStatCard(
                    title = "İndirilen APK",
                    value = downloadsCount.toString(),
                    icon = Icons.Default.Download,
                    accentColor = CndnPurple,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.Downloads.route) }
                )
            }
        }

        // Active Builds Section (if any)
        if (activeBuilds.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = CndnCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEVAM EDEN DERLEMELER (${activeBuilds.size})",
                        color = CndnCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(activeBuilds, key = { "active_${it.id}" }) { build ->
                CndnBuildItemCard(
                    build = build,
                    onClick = { onNavigate(Screen.BuildDetail.createRoute(build.id)) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Recent Builds Section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SON DERLEMELER",
                    color = CndnTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (allBuilds.isNotEmpty()) {
                    Text(
                        text = "${allBuilds.size} kayıt",
                        color = CndnTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (allBuilds.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CndnSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CndnSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = CndnTextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Henüz derleme kaydı yok",
                            color = CndnTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bir Android kaynak kodu ZIP dosyası seçerek ilk derlemenizi GitHub Actions üzerinden başlatın.",
                            color = CndnTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { zipPickerLauncher.launch("application/zip") },
                            colors = ButtonDefaults.buttonColors(containerColor = CndnCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = Color(0xFF002026))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ZIP Seç ve Başlat", color = Color(0xFF002026), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(allBuilds, key = { it.id }) { build ->
                CndnBuildItemCard(
                    build = build,
                    onClick = { onNavigate(Screen.BuildDetail.createRoute(build.id)) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
