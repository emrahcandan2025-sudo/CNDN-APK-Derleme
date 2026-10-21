package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.example.data.model.BuildTarget
import com.example.data.model.GitHubRepo
import com.example.ui.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.navigation.Screen
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildWizardScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val inspectionResult by viewModel.inspectionResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingZip.collectAsState()
    val buildProgress by viewModel.buildActionProgress.collectAsState()

    val repositories by viewModel.repositories.collectAsState()
    val isLoadingRepos by viewModel.isLoadingRepos.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Existing repo, 1 = New repo
    var repoSearchQuery by remember { mutableStateOf("") }
    var selectedRepo by remember { mutableStateOf<GitHubRepo?>(null) }
    var selectedBranch by remember { mutableStateOf("main") }

    // New repo fields
    var newRepoName by remember { mutableStateOf("") }
    var newRepoDesc by remember { mutableStateOf("CNDN APK Derleme Merkezi tarafından otomatik oluşturuldu.") }
    var newRepoIsPrivate by remember { mutableStateOf(true) }

    // Build target & commit message
    var selectedTarget by remember { mutableStateOf(BuildTarget.DEBUG_APK) }
    var commitMessage by remember { mutableStateOf("CNDN APK Derleme: Otomatik kaynak kodu commit") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val isBuildingInProgress by viewModel.isBuildingInProgress.collectAsState()
    var lastClickTime by remember { mutableLongStateOf(0L) }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            errorMessage = null
            viewModel.analyzeZip(context, uri)
        }
    }

    // Auto-fill new repo name when project inspected
    LaunchedEffect(inspectionResult) {
        val appName = inspectionResult?.appName
        if (!appName.isNullOrBlank() && newRepoName.isBlank()) {
            newRepoName = appName.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9_-]"), "")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Yeni APK Derlemesi",
                        color = CndnTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = CndnTextPrimary)
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
            // STEP 1: ZIP Selection & Verification
            item {
                SectionTitle(number = "1", title = "Kaynak Kodu ZIP Dosyası")
                Spacer(modifier = Modifier.height(8.dp))

                if (isAnalyzing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CndnSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = CndnCyan, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ZIP açılıyor ve Android proje yapısı doğrulanıyor...",
                                color = CndnTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (inspectionResult == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CndnSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, CndnBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { zipPicker.launch("application/zip") }
                            .testTag("wizard_select_zip_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CndnCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = CndnCyan, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Android Proje ZIP Dosyasını Seçin",
                                color = CndnTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "settings.gradle, build.gradle ve AndroidManifest.xml içeren arşiv (Maks. 100 MB)",
                                color = CndnTextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    val result = inspectionResult!!
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.isValidAndroidProject) Color(0xFF0F241A) else Color(0xFF281417)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (result.isValidAndroidProject) CndnEmerald.copy(alpha = 0.6f) else CndnCrimson
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (result.isValidAndroidProject) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (result.isValidAndroidProject) CndnEmerald else CndnCrimson
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (result.isValidAndroidProject) "Geçerli Android Projesi" else "Doğrulama Başarısız",
                                        color = if (result.isValidAndroidProject) CndnEmerald else CndnCrimson,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                TextButton(onClick = { viewModel.clearZip() }) {
                                    Text("Değiştir", color = CndnCyan, fontSize = 12.sp)
                                }
                            }

                            if (!result.isValidAndroidProject) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = result.validationError ?: "Bilinmeyen arşiv hatası.",
                                    color = CndnCrimson,
                                    fontSize = 13.sp
                                )
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Uygulama: ${result.appName ?: result.rootDirectoryName}",
                                    color = CndnTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Paket Adı: ${result.applicationId} • compileSdk: ${result.compileSdkVersion}",
                                    color = CndnTextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${result.totalFiles} dosya (${ApkUtils.formatFileSize(result.totalSizeBytes)}) • Modüller: ${result.detectedModules.joinToString()}",
                                    color = CndnTextMuted,
                                    fontSize = 11.sp
                                )

                                // Gradle Wrapper Missing Alert & Auto-Fix
                                if (!result.hasGradleWrapper) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2210)),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CndnAmber.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = CndnAmber, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Gradle Wrapper Bulunamadı", color = CndnAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "GitHub Actions derlemesi için 'gradlew' ve wrapper yapılandırması gereklidir. Otomatik olarak oluşturabilirsiniz.",
                                                color = CndnTextSecondary,
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = { viewModel.applyGradleWrapperAutoFix() },
                                                colors = ButtonDefaults.buttonColors(containerColor = CndnAmber),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().testTag("apply_gradle_fix_btn")
                                            ) {
                                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF241500), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Gradle Wrapper Ekle (Otomatik Düzelt)", color = Color(0xFF241500), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: Target Repository Selection
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle(number = "2", title = "Hedef GitHub Deposu")
                Spacer(modifier = Modifier.height(8.dp))

                if (connectionState !is ConnectionState.Connected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF231618)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CndnCrimson.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = CndnCrimson)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("GitHub Bağlantısı Yok", color = CndnCrimson, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Depo seçmek veya oluşturmak için önce token bağlayın.", color = CndnTextSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = { onNavigate(Screen.Settings.route) }) {
                                Text("Bağla", color = CndnCyan)
                            }
                        }
                    }
                } else {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = CndnSurface,
                        contentColor = CndnCyan,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = CndnCyan
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Mevcut Depoyu Güncelle", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Yeni Depo Oluştur", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedTab == 0) {
                        // Existing Repositories
                        OutlinedTextField(
                            value = repoSearchQuery,
                            onValueChange = { repoSearchQuery = it },
                            placeholder = { Text("Depolarda ara...", color = CndnTextMuted) },
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

                        Spacer(modifier = Modifier.height(8.dp))

                        val filteredRepos = repositories.filter {
                            it.fullName.contains(repoSearchQuery, ignoreCase = true)
                        }

                        if (isLoadingRepos) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = CndnCyan, modifier = Modifier.size(24.dp))
                            }
                        } else if (filteredRepos.isEmpty()) {
                            Text(
                                text = "Depo bulunamadı veya arama sonucu boş.",
                                color = CndnTextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                filteredRepos.take(5).forEach { repo ->
                                    val isSelected = selectedRepo?.id == repo.id
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) CndnSurfaceVariant else CndnSurface
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) CndnCyan else CndnBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedRepo = repo
                                                selectedBranch = repo.defaultBranch
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = repo.name,
                                                        color = CndnTextPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = if (repo.private) Color(0xFF2E2214) else Color(0xFF13281E),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = if (repo.private) "Gizli" else "Açık",
                                                            color = if (repo.private) CndnAmber else CndnEmerald,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = repo.fullName,
                                                    color = CndnTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = CndnCyan)
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedRepo != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = selectedBranch,
                                    onValueChange = { selectedBranch = it },
                                    label = { Text("Hedef Dal (Branch)", color = CndnTextSecondary) },
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
                            }
                        }
                    } else {
                        // New Repo Form
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = newRepoName,
                                onValueChange = { newRepoName = it },
                                label = { Text("Depo Adı (Örn: my-android-app)", color = CndnTextSecondary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("new_repo_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CndnCyan,
                                    unfocusedBorderColor = CndnBorder,
                                    focusedContainerColor = CndnSurface,
                                    unfocusedContainerColor = CndnSurface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = newRepoDesc,
                                onValueChange = { newRepoDesc = it },
                                label = { Text("Depo Açıklaması", color = CndnTextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CndnCyan,
                                    unfocusedBorderColor = CndnBorder,
                                    focusedContainerColor = CndnSurface,
                                    unfocusedContainerColor = CndnSurface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Gizli Depo (Private)", color = CndnTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Yalnızca sizin erişebileceğiniz güvenli repo", color = CndnTextSecondary, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = newRepoIsPrivate,
                                    onCheckedChange = { newRepoIsPrivate = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = CndnCyan, checkedTrackColor = Color(0xFF004D5A))
                                )
                            }
                        }
                    }
                }
            }

            // STEP 3: Build Target
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle(number = "3", title = "Derleme Hedefi")
                Spacer(modifier = Modifier.height(8.dp))

                BuildTarget.entries.forEach { target ->
                    val isSelected = selectedTarget == target
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CndnSurfaceVariant else CndnSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) CndnCyan else CndnBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedTarget = target }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedTarget = target },
                                colors = RadioButtonDefaults.colors(selectedColor = CndnCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = target.displayName,
                                    color = CndnTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Gradle Görevi: ./${target.gradleCommand} • Çıktı: ${target.artifactName}",
                                    color = CndnTextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // STEP 4: Commit Message & Start Build
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle(number = "4", title = "Commit ve Derleme")
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit Mesajı", color = CndnTextSecondary) },
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

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = CndnCrimson,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!buildProgress.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = CndnCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = buildProgress!!, color = CndnCyan, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val isReady = inspectionResult?.isValidAndroidProject == true &&
                        connectionState is ConnectionState.Connected &&
                        !isSubmitting &&
                        !isBuildingInProgress &&
                        (selectedTab == 1 || selectedRepo != null)

                Button(
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 1500L) return@Button
                        lastClickTime = currentTime

                        if (isSubmitting || isBuildingInProgress) return@Button

                        errorMessage = null
                        isSubmitting = true

                        if (selectedTab == 1) {
                            // Create new repo first
                            if (newRepoName.isBlank()) {
                                errorMessage = "Lütfen yeni depo adını girin."
                                isSubmitting = false
                                return@Button
                            }
                            viewModel.createRepository(
                                name = newRepoName,
                                description = newRepoDesc,
                                isPrivate = newRepoIsPrivate,
                                onSuccess = { createdRepo ->
                                    viewModel.startBuild(
                                        repoFullName = createdRepo.fullName,
                                        branch = "main",
                                        target = selectedTarget,
                                        commitMessage = commitMessage,
                                        onCreated = { buildId ->
                                            isSubmitting = false
                                            onNavigate(Screen.BuildDetail.createRoute(buildId))
                                        },
                                        onError = { err ->
                                            isSubmitting = false
                                            errorMessage = err
                                        }
                                    )
                                },
                                onError = { err ->
                                    isSubmitting = false
                                    errorMessage = err
                                }
                            )
                        } else {
                            // Existing repo
                            val repo = selectedRepo
                            if (repo == null) {
                                errorMessage = "Lütfen güncellenecek depoyu seçin."
                                isSubmitting = false
                                return@Button
                            }
                            viewModel.startBuild(
                                repoFullName = repo.fullName,
                                branch = selectedBranch.ifBlank { repo.defaultBranch },
                                target = selectedTarget,
                                commitMessage = commitMessage,
                                onCreated = { buildId ->
                                    isSubmitting = false
                                    onNavigate(Screen.BuildDetail.createRoute(buildId))
                                },
                                onError = { err ->
                                    isSubmitting = false
                                    errorMessage = err
                                }
                            )
                        }
                    },
                    enabled = isReady,
                    colors = ButtonDefaults.buttonColors(containerColor = CndnCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_build_btn")
                ) {
                    if (isSubmitting || isBuildingInProgress) {
                        CircularProgressIndicator(color = Color(0xFF002026), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("İşlem Yürütülüyor...", color = Color(0xFF002026), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF002026))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yükle ve Derlemeyi Başlat", color = Color(0xFF002026), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(CndnCyan),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color(0xFF002026), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = CndnTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
