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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()

    var inputToken by remember { mutableStateOf(viewModel.prefs.getGitHubToken() ?: "") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var showConfirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Ayarlar ve GitHub", color = CndnTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
        ) {
            // SECTION: GITHUB CONNECTION
            item {
                Text(
                    text = "GİTHUB KİMLİK DOĞRULAMA",
                    color = CndnCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = CndnSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        when (val state = connectionState) {
                            is ConnectionState.Connected -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!state.user.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = state.user.avatarUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .border(2.dp, CndnEmerald, CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(CndnEmerald.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Person, contentDescription = null, tint = CndnEmerald)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = state.user.name ?: state.user.login,
                                                color = CndnTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "@${state.user.login}",
                                                color = CndnCyan,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "Açık Depolar: ${state.user.publicRepos}",
                                                color = CndnTextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.disconnect() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CndnCrimson),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CndnCrimson.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Çıkış Yap", color = CndnCrimson, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Aktif Token: ${viewModel.prefs.getMaskedToken()}",
                                    color = CndnTextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            is ConnectionState.Connecting -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(color = CndnCyan, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("GitHub bağlantısı doğrulanıyor...", color = CndnCyan, fontSize = 14.sp)
                                }
                            }

                            else -> {
                                Text(
                                    text = "Personal Access Token (PAT)",
                                    color = CndnTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Depo oluşturma, dosya yükleme ve Actions tetikleme için 'repo' ve 'workflow' yetkilerine sahip GitHub PAT girin.",
                                    color = CndnTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = inputToken,
                                    onValueChange = { inputToken = it },
                                    placeholder = { Text("ghp_... veya github_pat_...", color = CndnTextMuted) },
                                    singleLine = true,
                                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                            Icon(
                                                imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = CndnTextSecondary
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("token_input_field"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CndnCyan,
                                        unfocusedBorderColor = CndnBorder,
                                        focusedContainerColor = CndnSurfaceVariant,
                                        unfocusedContainerColor = CndnSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                if (connectionState is ConnectionState.Error) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = (connectionState as ConnectionState.Error).message,
                                        color = CndnCrimson,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (inputToken.isNotBlank()) {
                                            viewModel.connectWithToken(inputToken.trim())
                                        }
                                    },
                                    enabled = inputToken.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CndnCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_token_btn")
                                ) {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF002026), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Bağlantıyı Doğrula ve Kaydet", color = Color(0xFF002026), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: STEP BY STEP GUIDE
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "GİTHUB TOKEN NASIL ALINIR?",
                    color = CndnTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = CndnSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GuideStep(
                            number = "1",
                            text = "GitHub hesabınıza giriş yapıp 'Settings > Developer Settings > Personal access tokens' sayfasına gidin."
                        )
                        GuideStep(
                            number = "2",
                            text = "'Tokens (classic)' seçeneğini seçin ve 'Generate new token' butonuna tıklayın."
                        )
                        GuideStep(
                            number = "3",
                            text = "İzinler listesinden 'repo' (tüm depo izinleri) ve 'workflow' (Actions iş akışları) kutularını işaretleyin."
                        )
                        GuideStep(
                            number = "4",
                            text = "'Generate token' butonuna basın ve oluşan 'ghp_...' belirtecini yukarıdaki alana yapıştırın."
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CndnCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CndnCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = CndnCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GitHub Token Sayfasına Git", color = CndnCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // SECTION: GÜVENLİK VE MİMARİ NOTLARI
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "GÜVENLİK VE MİMARİ",
                    color = CndnTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = CndnSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = CndnEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Token'lar asla APK kaynak koduna gömülmez, sunucu loglarına yazılmaz veya üçüncü taraflarla paylaşılmaz. Yalnızca cihazınızdaki yerel güvenli depolamada saklanır.",
                                color = CndnTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CndnCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Zip Slip ve path traversal engelleme filtreleri ile arşiv içerikleri güvenli biçimde taranır ve zararlı komut dosyaları engellenir.",
                                color = CndnTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // SECTION: BUILD YML VIEW & COPY
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GİTHUB ACTIONS BUILD.YML",
                        color = CndnTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    TextButton(
                        onClick = {
                            val yaml = com.example.data.analyzer.WorkflowGenerator.generateWorkflowYaml()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Build YML", yaml))
                            Toast.makeText(context, "build.yml panoya kopyalandı", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CndnCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kopyala", color = CndnCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = CndnSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CndnBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Dosya Yolu: .github/workflows/cndn_android_build.yml",
                            color = CndnTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CndnSurfaceVariant)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = com.example.data.analyzer.WorkflowGenerator.generateWorkflowYaml(),
                                color = CndnCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // SECTION: GEÇMİŞİ TEMİZLE
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "VERİ VE GEÇMİŞ YÖNETİMİ",
                    color = CndnTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showConfirmClear = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381418)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = CndnCrimson, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tüm Derleme ve İndirme Geçmişini Temizle", color = CndnCrimson, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "CNDN APK Derleme Merkezi • Sürüm 1.0 (Native Android)",
                        color = CndnTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (showConfirmClear) {
            AlertDialog(
                onDismissRequest = { showConfirmClear = false },
                title = { Text("Geçmişi Temizle", color = CndnTextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Tüm yerel derleme kayıtları ve indirme listesi silinecektir. GitHub üzerindeki depolarınız etkilenmez.", color = CndnTextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllHistory()
                            showConfirmClear = false
                            Toast.makeText(context, "Geçmiş temizlendi", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CndnCrimson)
                    ) {
                        Text("Temizle", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClear = false }) {
                        Text("İptal", color = CndnTextSecondary)
                    }
                },
                containerColor = CndnSurface
            )
        }
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(CndnSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = CndnCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = CndnTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
