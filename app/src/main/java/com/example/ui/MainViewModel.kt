package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.analyzer.ApkUtils
import com.example.data.analyzer.ZipProjectAnalyzer
import com.example.data.local.AppPreferences
import com.example.data.local.BuildDatabase
import com.example.data.local.BuildEntity
import com.example.data.local.DownloadRecordEntity
import com.example.data.model.BuildStatusState
import com.example.data.model.BuildTarget
import com.example.data.model.GitHubRepo
import com.example.data.model.GitHubUser
import com.example.data.model.ProjectInspectionResult
import com.example.data.repository.GitHubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val user: GitHubUser) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Downloading(val buildId: Long, val progressPercent: Int = -1, val statusMessage: String = "İndiriliyor...") : DownloadUiState
    data class Success(val buildId: Long, val file: File, val fileName: String, val sha256: String) : DownloadUiState
    data class Error(val buildId: Long, val errorMessage: String) : DownloadUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BuildDatabase.getDatabase(application)
    private val buildDao = db.buildDao()
    val prefs = AppPreferences(application)
    val githubRepo = GitHubRepository(prefs = prefs)

    val allBuilds: StateFlow<List<BuildEntity>> = buildDao.getAllBuilds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBuilds: StateFlow<List<BuildEntity>> = buildDao.getActiveBuilds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val successCount: StateFlow<Int> = buildDao.getSuccessCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failureCount: StateFlow<Int> = buildDao.getFailureCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadsCount: StateFlow<Int> = buildDao.getTotalDownloadsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allDownloads: StateFlow<List<DownloadRecordEntity>> = buildDao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _repositories = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val repositories: StateFlow<List<GitHubRepo>> = _repositories.asStateFlow()

    private val _isLoadingRepos = MutableStateFlow(false)
    val isLoadingRepos: StateFlow<Boolean> = _isLoadingRepos.asStateFlow()

    private val _repoError = MutableStateFlow<String?>(null)
    val repoError: StateFlow<String?> = _repoError.asStateFlow()

    // ZIP Analysis
    private val _inspectionResult = MutableStateFlow<ProjectInspectionResult?>(null)
    val inspectionResult: StateFlow<ProjectInspectionResult?> = _inspectionResult.asStateFlow()

    private val _isAnalyzingZip = MutableStateFlow(false)
    val isAnalyzingZip: StateFlow<Boolean> = _isAnalyzingZip.asStateFlow()

    // Current build state & action progress
    private val _buildActionProgress = MutableStateFlow<String?>(null)
    val buildActionProgress: StateFlow<String?> = _buildActionProgress.asStateFlow()

    private val _downloadingBuildId = MutableStateFlow<Long?>(null)
    val downloadingBuildId: StateFlow<Long?> = _downloadingBuildId.asStateFlow()

    private val _downloadUiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val downloadUiState: StateFlow<DownloadUiState> = _downloadUiState.asStateFlow()

    private val pollingJobs = mutableMapOf<Long, Job>()

    init {
        // Auto connect if token exists
        val token = prefs.getGitHubToken()
        if (!token.isNullOrBlank()) {
            connectWithToken(token)
        }
    }

    fun connectWithToken(token: String) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Connecting
            val result = githubRepo.testConnection(token)
            result.onSuccess { user ->
                _connectionState.value = ConnectionState.Connected(user)
                loadRepositories()
            }.onFailure { err ->
                _connectionState.value = ConnectionState.Error(err.message ?: "Bağlantı hatası oluştu.")
            }
        }
    }

    fun disconnect() {
        prefs.clearAuth()
        _connectionState.value = ConnectionState.Disconnected
        _repositories.value = emptyList()
    }

    fun loadRepositories() {
        viewModelScope.launch {
            _isLoadingRepos.value = true
            _repoError.value = null
            val result = githubRepo.getRepositories()
            result.onSuccess { list ->
                _repositories.value = list
            }.onFailure { err ->
                _repoError.value = err.message
            }
            _isLoadingRepos.value = false
        }
    }

    fun createRepository(
        name: String,
        description: String,
        isPrivate: Boolean,
        onSuccess: (GitHubRepo) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val res = githubRepo.createRepository(name, description, isPrivate)
            res.onSuccess { repo ->
                loadRepositories()
                onSuccess(repo)
            }.onFailure { err ->
                onError(err.message ?: "Depo oluşturulamadı.")
            }
        }
    }

    fun analyzeZip(context: android.content.Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzingZip.value = true
            val cacheParent = File(context.cacheDir, "unpacked_zips")
            cacheParent.mkdirs()
            val result = ZipProjectAnalyzer.inspectAndExtract(context, uri, cacheParent)
            _inspectionResult.value = result
            _isAnalyzingZip.value = false
        }
    }

    fun clearZip() {
        _inspectionResult.value?.rootProjectDir?.deleteRecursively()
        _inspectionResult.value = null
    }

    fun applyGradleWrapperAutoFix() {
        val current = _inspectionResult.value ?: return
        val dir = current.rootProjectDir ?: return
        ZipProjectAnalyzer.injectGradleWrapper(dir)
        // Re-analyze directory
        val reCheck = ZipProjectAnalyzer.extractAndAnalyze(
            inputStream = java.io.ByteArrayInputStream(ByteArray(0)), // placeholder
            destParentDir = dir.parentFile ?: dir
        )
        _inspectionResult.value = current.copy(hasGradleWrapper = true)
    }

    fun startBuild(
        repoFullName: String,
        branch: String,
        target: BuildTarget,
        commitMessage: String,
        onCreated: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        val inspection = _inspectionResult.value
        val projectDir = inspection?.rootProjectDir
        if (projectDir == null || !projectDir.exists()) {
            onError("İşlenecek proje dosyası bulunamadı. Lütfen ZIP dosyasını tekrar seçin.")
            return
        }

        viewModelScope.launch {
            _buildActionProgress.value = "Dosyalar GitHub'a yükleniyor ve commit oluşturuluyor..."

            val commitRes = githubRepo.commitProjectAndTriggerBuild(
                projectDir = projectDir,
                repoFullName = repoFullName,
                branch = branch,
                buildTarget = target,
                commitMessage = commitMessage
            )

            if (commitRes.isFailure) {
                _buildActionProgress.value = null
                onError(commitRes.exceptionOrNull()?.message ?: "Yükleme başarısız.")
                return@launch
            }

            val commit = commitRes.getOrThrow()
            _buildActionProgress.value = "GitHub Actions iş akışı bekleniyor..."

            // Insert initial build record
            val initialBuild = BuildEntity(
                repoFullName = repoFullName,
                repoHtmlUrl = "https://github.com/$repoFullName",
                branch = commit.branch,
                commitSha = commit.commitSha,
                commitMessage = commitMessage.ifBlank { "CNDN Android Derlemesi" },
                workflowRunId = 0L,
                buildTarget = target.name,
                status = BuildStatusState.QUEUED.name,
                createdAt = System.currentTimeMillis()
            )
            val buildId = buildDao.insertBuild(initialBuild)
            _buildActionProgress.value = null
            onCreated(buildId)

            // Start background polling
            startPollingRun(buildId, repoFullName, commit.commitSha)
        }
    }

    fun startPollingRun(buildId: Long, repoFullName: String, commitSha: String) {
        pollingJobs[buildId]?.cancel()
        pollingJobs[buildId] = viewModelScope.launch(Dispatchers.IO) {
            var runId = 0L
            var attempts = 0
            val maxAttempts = 120 // ~10 minutes with 5s delay

            // Step 1: Locate the Actions workflow run
            while (runId == 0L && attempts < 25) {
                delay(4000)
                attempts++
                val runRes = githubRepo.findWorkflowRunForCommit(repoFullName, commitSha)
                if (runRes.isSuccess && runRes.getOrNull() != null) {
                    val run = runRes.getOrNull()!!
                    runId = run.id
                    val current = buildDao.getBuildById(buildId)
                    if (current != null) {
                        buildDao.updateBuild(
                            current.copy(
                                workflowRunId = run.id,
                                status = when (run.status) {
                                    "completed" -> if (run.conclusion == "success") BuildStatusState.SUCCESS.name else BuildStatusState.FAILURE.name
                                    "in_progress" -> BuildStatusState.IN_PROGRESS.name
                                    else -> BuildStatusState.QUEUED.name
                                }
                            )
                        )
                    }
                }
            }

            if (runId == 0L) {
                val current = buildDao.getBuildById(buildId)
                if (current != null) {
                    buildDao.updateBuild(
                        current.copy(
                            status = BuildStatusState.FAILURE.name,
                            failedStep = "İş Akışı Başlatma",
                            primaryErrorMessage = "GitHub Actions iş akışı belirtilen commit için başlatılamadı. Depodaki Actions izinlerini kontrol edin.",
                            completedAt = System.currentTimeMillis()
                        )
                    )
                }
                return@launch
            }

            // Step 2: Poll run until completed
            while (attempts < maxAttempts) {
                delay(6000)
                attempts++

                val detailsRes = githubRepo.getWorkflowRunDetails(repoFullName, runId)
                if (detailsRes.isSuccess) {
                    val run = detailsRes.getOrThrow()
                    val isDone = run.status == "completed"

                    if (isDone) {
                        val isSuccess = run.conclusion == "success"
                        val current = buildDao.getBuildById(buildId) ?: return@launch

                        if (isSuccess) {
                            // Fetch artifacts
                            val artifacts = githubRepo.getArtifacts(repoFullName, runId).getOrDefault(emptyList())
                            val artifact = artifacts.firstOrNull()

                            buildDao.updateBuild(
                                current.copy(
                                    status = BuildStatusState.SUCCESS.name,
                                    completedAt = System.currentTimeMillis(),
                                    artifactId = artifact?.id,
                                    artifactName = artifact?.name ?: "cndn-apk-debug",
                                    artifactSize = artifact?.sizeInBytes
                                )
                            )
                        } else {
                            // Fetch diagnostics
                            val diag = githubRepo.getFailureDiagnostics(repoFullName, runId).getOrNull()
                            buildDao.updateBuild(
                                current.copy(
                                    status = if (run.conclusion == "cancelled") BuildStatusState.CANCELLED.name else BuildStatusState.FAILURE.name,
                                    completedAt = System.currentTimeMillis(),
                                    failedStep = diag?.failedStepName ?: "Gradle Derleme",
                                    primaryErrorMessage = diag?.primaryErrorMessage ?: "Derleme başarısız oldu.",
                                    suggestedFix = diag?.suggestedFix,
                                    logSnippet = diag?.detailedLogSnippet
                                )
                            )
                        }
                        break
                    } else {
                        // Still in progress
                        val current = buildDao.getBuildById(buildId)
                        if (current != null && current.status != BuildStatusState.IN_PROGRESS.name) {
                            buildDao.updateBuild(current.copy(status = BuildStatusState.IN_PROGRESS.name))
                        }
                    }
                }
            }
        }
    }

    fun downloadApk(
        context: android.content.Context,
        buildId: Long,
        onDone: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _downloadingBuildId.value = buildId
            _downloadUiState.value = DownloadUiState.Downloading(buildId, -1, "Artefakt aranıyor...")

            val build = withContext(Dispatchers.IO) { buildDao.getBuildById(buildId) }
            if (build == null || build.artifactId == null) {
                _downloadingBuildId.value = null
                val err = "İndirilecek artefakt bulunamadı."
                _downloadUiState.value = DownloadUiState.Error(buildId, err)
                withContext(Dispatchers.Main) {
                    onError(err)
                }
                return@launch
            }

            val apkDir = File(context.filesDir, "apks")
            apkDir.mkdirs()

            _downloadUiState.value = DownloadUiState.Downloading(buildId, 0, "İndirme başlatılıyor...")

            val res = withContext(Dispatchers.IO) {
                githubRepo.downloadAndExtractApk(
                    repoFullName = build.repoFullName,
                    artifactId = build.artifactId,
                    destDir = apkDir,
                    baseName = build.artifactName ?: "cndn_app",
                    onProgress = { percent, msg ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _downloadUiState.value = DownloadUiState.Downloading(buildId, percent, msg)
                        }
                    }
                )
            }

            _downloadingBuildId.value = null

            res.onSuccess { info ->
                withContext(Dispatchers.IO) {
                    buildDao.updateBuild(
                        build.copy(
                            isDownloaded = true,
                            localApkPath = info.file.absolutePath,
                            apkSha256 = info.sha256,
                            artifactSize = info.fileSizeBytes
                        )
                    )

                    buildDao.insertDownload(
                        DownloadRecordEntity(
                            buildId = build.id,
                            fileName = info.fileName,
                            filePath = info.file.absolutePath,
                            fileSizeBytes = info.fileSizeBytes,
                            sha256 = info.sha256,
                            targetType = build.buildTarget
                        )
                    )
                }

                _downloadUiState.value = DownloadUiState.Success(
                    buildId = build.id,
                    file = info.file,
                    fileName = info.fileName,
                    sha256 = info.sha256
                )

                // Execute callback strictly on Main dispatcher to prevent thread/Looper crashes
                withContext(Dispatchers.Main) {
                    onDone(info.file)
                }
            }.onFailure { err ->
                val errorMsg = err.message ?: "APK indirilirken bir hata oluştu."
                _downloadUiState.value = DownloadUiState.Error(buildId, errorMsg)

                // Execute callback strictly on Main dispatcher to prevent thread/Looper crashes
                withContext(Dispatchers.Main) {
                    onError(errorMsg)
                }
            }
        }
    }

    fun markApkMissing(buildId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val build = buildDao.getBuildById(buildId)
            if (build != null) {
                buildDao.updateBuild(
                    build.copy(
                        isDownloaded = false,
                        localApkPath = null
                    )
                )
            }
        }
    }

    fun clearDownloadState() {
        _downloadUiState.value = DownloadUiState.Idle
    }

    fun deleteDownload(id: Long, filePath: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!filePath.isNullOrBlank()) {
                val f = File(filePath)
                if (f.exists()) f.delete()
            }
            buildDao.deleteDownload(id)
        }
    }

    suspend fun getBuildById(id: Long): BuildEntity? {
        return buildDao.getBuildById(id)
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            buildDao.clearAllBuilds()
            buildDao.clearAllDownloads()
        }
    }
}
