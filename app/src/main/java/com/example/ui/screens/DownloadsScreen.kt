package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analyzer.ApkUtils
import com.example.data.local.DownloadRecordEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allDownloads by viewModel.allDownloads.collectAsState()
    var showUnknownSourcesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("İndirilen APK'lar", color = CndnTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = CndnTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CndnBackground)
            )
        },
        containerColor = CndnBackground
    ) { innerPadding ->
        if (allDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(CndnSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, tint = CndnTextSecondary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("İndirilmiş APK bulunamadı", color = CndnTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Başarılı bir derlemeden sonra oluşturulan APK dosyasını indirerek burada saklayabilirsiniz.", color = CndnTextSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
        ) {
            items(allDownloads, key = { it.id }) { record ->
                val file = File(record.filePath)
                val fileExists = file.exists() && file.isFile && file.length() > 0

                DownloadItemCard(
                    record = record,
                    fileExists = fileExists,
                    onInstall = {
                        if (!file.exists() || file.length() == 0L) {
                            Toast.makeText(context, "APK dosyası cihazda bulunamadı (silinmiş olabilir).", Toast.LENGTH_LONG).show()
                            return@DownloadItemCard
                        }

                        if (!ApkUtils.canRequestPackageInstalls(context)) {
                            showUnknownSourcesDialog = true
                        } else {
                            val installRes = ApkUtils.launchInstallApk(context, file)
                            installRes.onSuccess {
                                Toast.makeText(context, "Paket yükleyici açıldı", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "Kurulum başlatılamadı: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onShare = {
                        if (!file.exists() || file.length() == 0L) {
                            Toast.makeText(context, "Paylaşılacak dosya bulunamadı.", Toast.LENGTH_SHORT).show()
                            return@DownloadItemCard
                        }
                        val shareRes = ApkUtils.launchShareApk(context, file)
                        shareRes.onFailure {
                            Toast.makeText(context, "Paylaşım açılamadı: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCopySha = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SHA256", record.sha256))
                        Toast.makeText(context, "SHA-256 panoya kopyalandı", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        viewModel.deleteDownload(record.id, record.filePath)
                        Toast.makeText(context, "Kayıt silindi", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
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
                        text = "Android güvenlik kuralları gereği, derlenen APK'yı telefona kurabilmek için bu uygulamaya 'Bilinmeyen uygulamaları yükle' izni verilmelidir.\n\nLütfen açılacak sistem ayarlarından bu izni etkinleştirin.",
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
private fun DownloadItemCard(
    record: DownloadRecordEntity,
    fileExists: Boolean,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onCopySha: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(record.downloadedAt) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(record.downloadedAt))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CndnSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
        modifier = Modifier.fillMaxWidth().testTag("download_card_${record.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (fileExists) CndnEmerald.copy(alpha = 0.15f) else CndnCrimson.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (fileExists) Icons.Default.Android else Icons.Default.FilePresent,
                            contentDescription = null,
                            tint = if (fileExists) CndnEmerald else CndnCrimson,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = record.fileName,
                            color = CndnTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (fileExists) {
                                "${ApkUtils.formatFileSize(record.fileSizeBytes)} • $dateStr"
                            } else {
                                "Dosya silinmiş • $dateStr"
                            },
                            color = if (fileExists) CndnTextSecondary else CndnCrimson,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Sil", tint = CndnTextMuted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SHA-256 Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CndnSurfaceVariant)
                    .clickable { onCopySha() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SHA: ${record.sha256.take(24)}...",
                    color = CndnCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CndnTextSecondary, modifier = Modifier.size(14.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onInstall,
                    enabled = fileExists,
                    colors = ButtonDefaults.buttonColors(containerColor = CndnCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color(0xFF002026), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Telefona Kur", color = Color(0xFF002026), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onShare,
                    enabled = fileExists,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CndnTextPrimary),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = CndnTextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paylaş", color = CndnTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}
