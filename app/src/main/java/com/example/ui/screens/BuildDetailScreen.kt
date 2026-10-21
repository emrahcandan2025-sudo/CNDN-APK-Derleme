package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analyzer.ApkUtils
import com.example.data.local.BuildEntity
import com.example.data.model.BuildStatusState
import com.example.ui.MainViewModel
import com.example.ui.components.CndnStatusBadge
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildDetailScreen(
    buildId: Long,
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allBuilds by viewModel.allBuilds.collectAsState()
    val downloadingId by viewModel.downloadingBuildId.collectAsState()
    val downloadUiState by viewModel.downloadUiState.collectAsState()

    var showUnknownSourcesDialog by remember { mutableStateOf(false) }

    val build = allBuilds.find { it.id == buildId }
    val isDownloading = downloadingId == buildId || (downloadUiState is com.example.ui.DownloadUiState.Downloading && (downloadUiState as com.example.ui.DownloadUiState.Downloading).buildId == buildId)

    val status = runCatching {
        BuildStatusState.valueOf(build?.status ?: "QUEUED")
    }.getOrDefault(BuildStatusState.QUEUED)

    val dateFormatted = remember(build?.createdAt) {
        val sdf = SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale.forLanguageTag("tr"))
        if (build != null) sdf.format(Date(build.createdAt)) else ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Derleme #${buildId}",
                        color = CndnTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = CndnTextPrimary)
                    }
                },
                actions = {
                    if (build?.repoHtmlUrl != null) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(build.repoHtmlUrl))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "GitHub'da Aç", tint = CndnTextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CndnBackground)
            )
        },
        containerColor = CndnBackground
    ) { innerPadding ->
        if (build == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Derleme kaydı bulunamadı.", color = CndnTextSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
        ) {
            // HERO CARD: Status Banner
            item {
                when (status) {
                    BuildStatusState.SUCCESS -> SuccessHeroCard(build = build)
                    BuildStatusState.FAILURE -> FailureHeroCard(build = build)
                    BuildStatusState.IN_PROGRESS -> InProgressHeroCard(build = build)
                    BuildStatusState.QUEUED -> QueuedHeroCard(build = build)
                    BuildStatusState.CANCELLED -> CancelledHeroCard(build = build)
                }
            }

            // SUCCESS ACTIONS & ARTIFACT DETAILS (Yeşil Ekran Özellikleri)
            if (status == BuildStatusState.SUCCESS) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CndnSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ÜRETİLEN APK ARTEFAKTI",
                                    color = CndnEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Surface(
                                    color = Color(0xFF132B1E),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = build.buildTarget,
                                        color = CndnEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            InfoRow(
                                label = "Proje Adı",
                                value = build.appName ?: build.repoFullName
                            )
                            if (!build.packageName.isNullOrBlank()) {
                                InfoRow(
                                    label = "Paket Adı",
                                    value = build.packageName,
                                    isMonospace = true
                                )
                            }
                            if (!build.versionName.isNullOrBlank()) {
                                InfoRow(
                                    label = "Sürüm",
                                    value = "v${build.versionName}${if (!build.versionCode.isNullOrBlank()) " (${build.versionCode})" else ""}"
                                )
                            }
                            InfoRow(
                                label = "İmza Durumu",
                                value = if (build.isSignatureVerified) "Doğrulandı (${build.signatureScheme ?: "Release İmzalı"})" else (build.signatureScheme ?: "İmzalı Release APK")
                            )
                            InfoRow(
                                label = "Artefakt Adı",
                                value = build.artifactName ?: "cndn-apk-release.zip"
                            )
                            if (build.artifactSize != null && build.artifactSize > 0) {
                                InfoRow(
                                    label = "Dosya Boyutu",
                                    value = ApkUtils.formatFileSize(build.artifactSize)
                                )
                            }
                            InfoRow(label = "Derleme Tarihi", value = dateFormatted)
                            if (build.workflowRunId > 0) {
                                InfoRow(
                                    label = "Actions Run ID",
                                    value = "#${build.workflowRunId}",
                                    isMonospace = true
                                )
                            }
                            InfoRow(
                                label = "Commit SHA",
                                value = build.commitSha.take(8),
                                isMonospace = true
                            )

                            if (!build.apkSha256.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("SHA-256 Doğrulama Özeti:", color = CndnTextSecondary, fontSize = 11.sp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CndnSurfaceVariant)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("SHA256", build.apkSha256))
                                            Toast.makeText(context, "SHA-256 panoya kopyalandı", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = build.apkSha256,
                                        color = CndnCyan,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = CndnTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Download / Error / Install Actions
                            val currentDownload = downloadUiState
                            val isThisDownloading = currentDownload is com.example.ui.DownloadUiState.Downloading && currentDownload.buildId == build.id
                            val isThisError = currentDownload is com.example.ui.DownloadUiState.Error && currentDownload.buildId == build.id
                            val localFileAvailable = ApkUtils.isFileAvailable(build.localApkPath)

                            // 1. Error Banner with Retry button if download failed
                            if (isThisError) {
                                val errorMsg = (currentDownload as com.example.ui.DownloadUiState.Error).errorMessage
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1317)),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnCrimson.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CndnCrimson, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("İndirme Başarısız Oldu", color = CndnCrimson, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = errorMsg, color = CndnTextSecondary, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                viewModel.downloadApk(
                                                    context = context,
                                                    buildId = build.id,
                                                    onDone = { apkFile ->
                                                        Toast.makeText(context, "APK başarıyla indirildi: ${apkFile.name}", Toast.LENGTH_LONG).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CndnCrimson),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Yeniden Dene", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // 2. Active Download Progress
                            if (isThisDownloading) {
                                val downloadData = currentDownload as com.example.ui.DownloadUiState.Downloading
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CndnSurfaceVariant),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnCyan.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("APK İndiriliyor...", color = CndnCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (downloadData.progressPercent >= 0) {
                                                Text("%${downloadData.progressPercent}", color = CndnCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (downloadData.progressPercent >= 0) {
                                            LinearProgressIndicator(
                                                progress = { downloadData.progressPercent / 100f },
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                                color = CndnCyan,
                                                trackColor = CndnSurface
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                                color = CndnCyan,
                                                trackColor = CndnSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = downloadData.statusMessage, color = CndnTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            } else if (!build.isDownloaded || !localFileAvailable) {
                                // 3. Not yet downloaded or previously downloaded file was deleted from disk
                                if (build.isDownloaded && !localFileAvailable) {
                                    Surface(
                                        color = Color(0xFF261D12),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = CndnAmber, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Önceden indirilen dosya cihazda bulunamadı. Yeniden indirebilirsiniz.",
                                                color = CndnAmber,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                Button(
                                    onClick = {
                                        viewModel.downloadApk(
                                            context = context,
                                            buildId = build.id,
                                            onDone = { apkFile ->
                                                Toast.makeText(context, "APK başarıyla indirildi: ${apkFile.name}", Toast.LENGTH_LONG).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    enabled = !isDownloading,
                                    colors = ButtonDefaults.buttonColors(containerColor = CndnEmerald),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("download_apk_btn")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF00210B))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (build.isDownloaded) "APK'yı Yeniden İndir" else "APK'yı Telefona İndir",
                                        color = Color(0xFF00210B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                // 4. Already downloaded and verified on disk: Install & Share
                                val apkFile = File(build.localApkPath!!)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (!apkFile.exists() || apkFile.length() == 0L) {
                                                Toast.makeText(context, "APK dosyası bulunamadı. Lütfen tekrar indirin.", Toast.LENGTH_LONG).show()
                                                viewModel.markApkMissing(build.id)
                                                return@Button
                                            }

                                            if (!ApkUtils.canRequestPackageInstalls(context)) {
                                                showUnknownSourcesDialog = true
                                            } else {
                                                val installRes = ApkUtils.launchInstallApk(context, apkFile)
                                                installRes.onSuccess {
                                                    Toast.makeText(context, "Paket yükleyici açıldı", Toast.LENGTH_SHORT).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Kurulum başlatılamadı: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CndnCyan),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .testTag("install_apk_btn")
                                    ) {
                                        Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color(0xFF002026))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Telefona Kur", color = Color(0xFF002026), fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val shareRes = ApkUtils.launchShareApk(context, apkFile)
                                            shareRes.onFailure {
                                                Toast.makeText(context, "Paylaşım açılamadı: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CndnTextPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = CndnTextPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Paylaş", color = CndnTextPrimary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Debug vs Release Note
                            Surface(
                                color = CndnSurfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CndnEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Bu APK, CNDN Akıllı İmzalı APK Sistemi tarafından güvenli keystore ile imzalanmıştır. Android telefonlara doğrudan kurulabilir ve mağaza dağıtımına hazırdır.",
                                        color = CndnTextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FAILURE ACTIONS & ERROR LOGS (Kırmızı Ekran Özellikleri)
            if (status == BuildStatusState.FAILURE) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261215)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CndnCrimson.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "HATA VE TEŞHİS ANALİZİ",
                                    color = CndnCrimson,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                if (!build.failedStep.isNullOrBlank()) {
                                    Surface(
                                        color = Color(0xFF3D161B),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = build.failedStep,
                                            color = CndnCrimson,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Hata Mesajı:",
                                color = CndnTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = build.primaryErrorMessage ?: "Bilinmeyen derleme hatası.",
                                color = CndnTextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B0C0E))
                                    .padding(10.dp)
                            )

                            if (!build.suggestedFix.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = CndnAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Önerilen Çözüm:", color = CndnAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = build.suggestedFix, color = CndnTextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                                    }
                                }
                            }

                            if (!build.logSnippet.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ayrıntılı Derleme Logları:", color = CndnTextSecondary, fontSize = 12.sp)
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Loglar", build.logSnippet))
                                            Toast.makeText(context, "Loglar panoya kopyalandı", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CndnCyan, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Kopyala", color = CndnCyan, fontSize = 12.sp)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF13090B))
                                        .border(1.dp, Color(0xFF38151A), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = build.logSnippet,
                                        color = Color(0xFFFFB4AB),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onNavigate(Screen.BuildWizard.route) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CndnCrimson),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Yeniden Dene", color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                if (build.workflowRunId > 0) {
                                    OutlinedButton(
                                        onClick = {
                                            val url = "${build.repoHtmlUrl}/actions/runs/${build.workflowRunId}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CndnTextPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text("Actions'ta Gör", color = CndnTextPrimary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // General Build Summary
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CndnSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "DEPO VE ÇALIŞMA AYRINTILARI",
                            color = CndnTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        InfoRow(label = "Hedef Depo", value = build.repoFullName)
                        InfoRow(label = "Dal (Branch)", value = build.branch, isMonospace = true)
                        InfoRow(label = "Commit Mesajı", value = build.commitMessage)
                        if (build.workflowRunId > 0) {
                            InfoRow(label = "Actions Run ID", value = "#${build.workflowRunId}", isMonospace = true)
                        }
                    }
                }
            }
        }

        if (showUnknownSourcesDialog) {
            AlertDialog(
                onDismissRequest = { showUnknownSourcesDialog = false },
                title = {
                    Text(
                        text = "Uygulama Yükleme İzni Gerekli",
                        color = CndnTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Android güvenlik kuralları gereği, derlenen APK'yı bu telefona kurabilmek için CNDN APK Derleme Merkezi'ne 'Bilinmeyen uygulamaları yükle' izni verilmelidir.\n\nLütfen açılacak sistem ayarlarından bu izni etkinleştirin.",
                        color = CndnTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnknownSourcesDialog = false
                            try {
                                context.startActivity(ApkUtils.createUnknownSourcesSettingsIntent(context))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ayarlar açılamadı: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CndnCyan)
                    ) {
                        Text("Ayarları Aç", color = Color(0xFF002026), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnknownSourcesDialog = false }) {
                        Text("İptal", color = CndnTextSecondary)
                    }
                },
                containerColor = CndnSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = CndnTextSecondary, fontSize = 12.sp)
        Text(
            text = value,
            color = CndnTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
private fun SuccessHeroCard(build: BuildEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2418)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CndnEmerald),
        modifier = Modifier.fillMaxWidth().testTag("build_success_hero")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CndnEmerald.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CndnEmerald, modifier = Modifier.size(38.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("DERLEME BAŞARILI!", color = CndnEmerald, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("GitHub Actions derlemesi hatasız tamamlandı.", color = Color(0xFFA5D6A7), fontSize = 13.sp)
        }
    }
}

@Composable
private fun FailureHeroCard(build: BuildEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1216)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CndnCrimson),
        modifier = Modifier.fillMaxWidth().testTag("build_failure_hero")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CndnCrimson.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, tint = CndnCrimson, modifier = Modifier.size(38.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("DERLEME BAŞARISIZ OLDU", color = CndnCrimson, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("GitHub Actions üzerinde Gradle derlemesi hata verdi.", color = Color(0xFFFFCDD2), fontSize = 13.sp)
        }
    }
}

@Composable
private fun InProgressHeroCard(build: BuildEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2332)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CndnCyan),
        modifier = Modifier.fillMaxWidth().testTag("build_in_progress_hero")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = CndnCyan, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text("DERLEME ÇALIŞIYOR...", color = CndnCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("GitHub sunucusunda Gradle derlemesi ve testleri yürütülüyor.", color = CndnTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun QueuedHeroCard(build: BuildEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF261E0E)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CndnAmber),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = CndnAmber, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text("SIRADA BEKLİYOR", color = CndnAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("GitHub Actions koşucusu hazırlanıyor ve görev sıraya alındı.", color = CndnTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CancelledHeroCard(build: BuildEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CndnSurfaceVariant),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Block, contentDescription = null, tint = CndnTextSecondary, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("DERLEME İPTAL EDİLDİ", color = CndnTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
