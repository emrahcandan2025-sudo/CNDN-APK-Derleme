package com.example.data.repository

import android.util.Base64
import com.example.data.analyzer.ApkUtils
import com.example.data.analyzer.BuildLogAnalyzer
import com.example.data.analyzer.WorkflowGenerator
import com.example.data.analyzer.ZipProjectAnalyzer
import com.example.data.api.GitHubApiClient
import com.example.data.api.GitHubApiService
import com.example.data.local.AppPreferences
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class GitHubRepository(
    private val api: GitHubApiService = GitHubApiClient.apiService,
    private val prefs: AppPreferences
) {

    private fun getAuthHeader(): String {
        val token = prefs.getGitHubToken()
        if (token.isNullOrBlank()) {
            throw IllegalStateException("GitHub bağlantısı bulunamadı. Lütfen Ayarlar sekmesinden geçerli bir GitHub Token (PAT) girin.")
        }
        return if (token.startsWith("Bearer ") || token.startsWith("token ")) token else "Bearer $token"
    }

    suspend fun testConnection(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val header = if (token.startsWith("Bearer ") || token.startsWith("token ")) token else "Bearer $token"
            val response = api.getAuthenticatedUser(header)
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                prefs.saveGitHubToken(token)
                prefs.saveGitHubUser(user.login, user.avatarUrl, user.name)
                Result.success(user)
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Yetkisiz Erişim (401): Girilen GitHub Token (PAT) geçersiz veya süresi dolmuş."
                    403 -> "Erişim Reddedildi (403): GitHub API istek sınırına ulaşıldı veya token izinleri yetersiz ('repo' ve 'workflow' izinleri gereklidir)."
                    else -> "GitHub API Hatası: Kod ${response.code()} - ${response.message()}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("GitHub sunucusuna bağlanılamadı: ${e.localizedMessage ?: "Ağ bağlantınızı kontrol edin."}"))
        }
    }

    suspend fun getRepositories(): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getUserRepositories(getAuthHeader())
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Depolar listelenemedi (${response.code()}): ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRepository(
        name: String,
        description: String,
        isPrivate: Boolean
    ): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            val req = CreateRepoRequest(
                name = name.trim(),
                description = description.trim(),
                private = isPrivate,
                autoInit = true // creates initial commit with README so default branch exists
            )
            val response = api.createRepository(getAuthHeader(), req)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Depo oluşturulamadı (${response.code()}): ${response.errorBody()?.string() ?: response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBranches(owner: String, repo: String): Result<List<GitHubBranch>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBranches(getAuthHeader(), owner, repo)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Dallar listelenemedi: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Commits all project files and the CNDN Actions workflow into GitHub,
     * which automatically triggers GitHub Actions!
     */
    suspend fun commitProjectAndTriggerBuild(
        projectDir: File,
        repoFullName: String,
        branch: String,
        buildTarget: BuildTarget,
        commitMessage: String
    ): Result<CommitResult> = withContext(Dispatchers.IO) {
        try {
            val parts = repoFullName.split("/")
            if (parts.size != 2) return@withContext Result.failure(IllegalArgumentException("Geçersiz depo adı formatı: $repoFullName"))
            val owner = parts[0]
            val repo = parts[1]

            // 1. Ensure Gradle Wrapper exists in the project
            ZipProjectAnalyzer.injectGradleWrapper(projectDir)

            // 2. Ensure GitHub Actions workflow exists
            val workflowsDir = File(projectDir, ".github/workflows")
            workflowsDir.mkdirs()
            val workflowFile = File(workflowsDir, "cndn_android_build.yml")
            val workflowYaml = WorkflowGenerator.generateWorkflowYaml(buildTarget)
            workflowFile.writeText(workflowYaml)

            // 3. Get latest commit SHA on branch
            val refResp = api.getBranchRef(getAuthHeader(), owner, repo, branch)
            val parentCommitSha = if (refResp.isSuccessful && refResp.body() != null) {
                refResp.body()!!.`object`.sha
            } else {
                // If branch doesn't exist, try getting 'main' or 'master'
                val fallbackRef = api.getBranchRef(getAuthHeader(), owner, repo, "main")
                if (fallbackRef.isSuccessful && fallbackRef.body() != null) {
                    fallbackRef.body()!!.`object`.sha
                } else {
                    val masterRef = api.getBranchRef(getAuthHeader(), owner, repo, "master")
                    if (masterRef.isSuccessful && masterRef.body() != null) {
                        masterRef.body()!!.`object`.sha
                    } else {
                        return@withContext Result.failure(Exception("Hedef dal ('$branch') referansı bulunamadı. Lütfen deponun başlatıldığından (en az 1 commit) emin olun."))
                    }
                }
            }

            // 4. Collect project files to commit
            val treeItems = mutableListOf<TreeItem>()
            val rootCanonical = projectDir.canonicalPath

            projectDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relPath = file.canonicalPath.removePrefix(rootCanonical).trimStart(File.separatorChar).replace("\\", "/")
                    val mode = if (file.name == "gradlew" || file.name.endsWith(".sh")) "100755" else "100644"

                    // Ignore .git, large caches
                    if (!relPath.startsWith(".gradle/") && !relPath.startsWith("build/") && !relPath.contains("/build/")) {
                        val length = file.length()
                        // Small text or binary files: GitHub tree API accepts content directly for text files < 100KB
                        if (length < 90 * 1024 && isTextFile(file.name)) {
                            treeItems.add(
                                TreeItem(
                                    path = relPath,
                                    mode = mode,
                                    type = "blob",
                                    content = file.readText()
                                )
                            )
                        } else {
                            // For files over 90KB or binary assets, write as base64 or upload
                            val content = file.readText(Charsets.ISO_8859_1)
                            treeItems.add(
                                TreeItem(
                                    path = relPath,
                                    mode = mode,
                                    type = "blob",
                                    content = content
                                )
                            )
                        }
                    }
                }
            }

            // 5. Create Git Tree
            val treeReq = CreateTreeRequest(baseTree = parentCommitSha, tree = treeItems)
            val treeResp = api.createTree(getAuthHeader(), owner, repo, treeReq)
            if (!treeResp.isSuccessful || treeResp.body() == null) {
                return@withContext Result.failure(Exception("Git ağacı oluşturulamadı (${treeResp.code()}): ${treeResp.errorBody()?.string() ?: treeResp.message()}"))
            }
            val newTreeSha = treeResp.body()!!.sha

            // 6. Create Git Commit
            val commitReq = CreateCommitRequest(
                message = commitMessage.ifBlank { "CNDN APK Derleme Merkezi: Proje güncellemesi [ci skip: false]" },
                tree = newTreeSha,
                parents = listOf(parentCommitSha)
            )
            val commitResp = api.createCommit(getAuthHeader(), owner, repo, commitReq)
            if (!commitResp.isSuccessful || commitResp.body() == null) {
                return@withContext Result.failure(Exception("Git commit oluşturulamadı (${commitResp.code()}): ${commitResp.errorBody()?.string() ?: commitResp.message()}"))
            }
            val newCommitSha = commitResp.body()!!.sha

            // 7. Update branch reference
            val updateRefReq = UpdateRefRequest(sha = newCommitSha, force = true)
            val updateResp = api.updateRef(getAuthHeader(), owner, repo, branch, updateRefReq)
            if (!updateResp.isSuccessful) {
                return@withContext Result.failure(Exception("Dal güncellenemedi (${updateResp.code()}): ${updateResp.errorBody()?.string() ?: updateResp.message()}"))
            }

            Result.success(CommitResult(commitSha = newCommitSha, branch = branch))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findWorkflowRunForCommit(
        repoFullName: String,
        commitSha: String
    ): Result<GitHubWorkflowRun?> = withContext(Dispatchers.IO) {
        try {
            val parts = repoFullName.split("/")
            val response = api.getWorkflowRuns(getAuthHeader(), parts[0], parts[1])
            if (response.isSuccessful && response.body() != null) {
                val runs = response.body()!!.workflowRuns
                val match = runs.find { it.headSha == commitSha } ?: runs.firstOrNull()
                Result.success(match)
            } else {
                Result.failure(Exception("Workflow run listesi alınamadı: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWorkflowRunDetails(
        repoFullName: String,
        runId: Long
    ): Result<GitHubWorkflowRun> = withContext(Dispatchers.IO) {
        try {
            val parts = repoFullName.split("/")
            val response = api.getWorkflowRun(getAuthHeader(), parts[0], parts[1], runId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Workflow run detayı alınamadı: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtifacts(
        repoFullName: String,
        runId: Long
    ): Result<List<GitHubArtifact>> = withContext(Dispatchers.IO) {
        try {
            val parts = repoFullName.split("/")
            val response = api.getWorkflowArtifacts(getAuthHeader(), parts[0], parts[1], runId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.artifacts)
            } else {
                Result.failure(Exception("Artefaktlar alınamadı: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAndExtractApk(
        repoFullName: String,
        artifactId: Long,
        destDir: File,
        baseName: String,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<DownloadedApkInfo> = withContext(Dispatchers.IO) {
        val tempDownloadFile = File(destDir, "raw_download_${System.currentTimeMillis()}_${artifactId}.tmp")
        val tempApkFile = File(destDir, "extracted_${System.currentTimeMillis()}_${artifactId}.apk")
        try {
            val parts = repoFullName.split("/")
            if (parts.size != 2) {
                return@withContext Result.failure(IllegalArgumentException("Geçersiz depo adı formatı: $repoFullName"))
            }

            onProgress(-1, "GitHub artefakt bağlantısı kuruluyor...")
            val response = api.downloadArtifactZip(getAuthHeader(), parts[0], parts[1], artifactId)
            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = when (response.code()) {
                    401 -> "GitHub Yetki Hatası (401): Personal Access Token geçersiz veya süresi dolmuş."
                    403 -> "Erişim Reddedildi (403): GitHub API indirme izni yetersiz ('repo' ve 'actions:read' yetkileri gereklidir) veya istek sınırı aşıldı."
                    404 -> "Artefakt Bulunamadı (404): GitHub Actions artefaktı henüz hazır değil veya sunucudan silinmiş."
                    410 -> "Artefakt Süresi Dolmuş (410): GitHub bu derleme çıktısını saklama süresi (retention) dolduğu için kaldırmış."
                    else -> "Artefakt indirilemedi: HTTP ${response.code()} - ${response.message()}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            destDir.mkdirs()
            val body = response.body()!!
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            onProgress(0, "İndirme başlatıldı...")

            // 1. Stream response body safely to temporary download file with chunked buffering
            body.byteStream().use { input ->
                FileOutputStream(tempDownloadFile).use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        kotlinx.coroutines.yield()
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress(percent, "İndiriliyor: %$percent (${ApkUtils.formatFileSize(downloadedBytes)})")
                        } else {
                            onProgress(-1, "İndiriliyor: ${ApkUtils.formatFileSize(downloadedBytes)}")
                        }
                    }
                    output.flush()
                }
            }

            if (!tempDownloadFile.exists() || tempDownloadFile.length() < 100L) {
                return@withContext Result.failure(Exception("İndirilen artefakt dosyası boş veya eksik."))
            }

            onProgress(100, "Artefakt içeriği doğrulanıyor...")

            var extractedApkName: String? = null

            // 2. Try extracting APK from the ZIP package
            try {
                java.util.zip.ZipFile(tempDownloadFile).use { zip ->
                    val apkEntry = zip.entries().asSequence().find { entry ->
                        !entry.isDirectory && (entry.name.endsWith(".apk", ignoreCase = true) || entry.name.endsWith(".aab", ignoreCase = true))
                    }
                    if (apkEntry != null) {
                        extractedApkName = apkEntry.name.substringAfterLast("/")
                        zip.getInputStream(apkEntry).use { zipIn ->
                            FileOutputStream(tempApkFile).use { apkOut ->
                                zipIn.copyTo(apkOut)
                                apkOut.flush()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Not a multi-entry ZIP or directly an APK
            }

            // 3. Fallback: if not extracted from ZIP, check if downloaded file itself is a valid APK
            if (extractedApkName == null || !tempApkFile.exists() || tempApkFile.length() == 0L) {
                val directCheck = ApkUtils.validateApk(tempDownloadFile)
                if (directCheck.isSuccess) {
                    tempDownloadFile.copyTo(tempApkFile, overwrite = true)
                    extractedApkName = if (baseName.endsWith(".apk", ignoreCase = true) || baseName.endsWith(".aab", ignoreCase = true)) {
                        baseName
                    } else {
                        "$baseName.apk"
                    }
                } else {
                    return@withContext Result.failure(Exception("İndirilen artefakt içerisinde geçerli bir APK veya AAB dosyası bulunamadı."))
                }
            }

            // 4. Validate the extracted APK file integrity
            val validation = ApkUtils.validateApk(tempApkFile)
            if (validation.isFailure) {
                return@withContext Result.failure(validation.exceptionOrNull() ?: Exception("İndirilen APK dosyası doğrulanamadı."))
            }

            // 5. Safely move to final file
            val rawName = (extractedApkName ?: "$baseName.apk").replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val finalName = if (rawName.endsWith(".apk", ignoreCase = true) || rawName.endsWith(".aab", ignoreCase = true)) {
                rawName
            } else {
                "$rawName.apk"
            }

            val finalFile = File(destDir, finalName)
            if (finalFile.exists()) {
                finalFile.delete()
            }
            tempApkFile.copyTo(finalFile, overwrite = true)

            val sha256 = ApkUtils.calculateSha256(finalFile)

            Result.success(
                DownloadedApkInfo(
                    file = finalFile,
                    fileName = finalFile.name,
                    fileSizeBytes = finalFile.length(),
                    sha256 = sha256
                )
            )
        } catch (e: Exception) {
            val sanitizedMsg = BuildLogAnalyzer.sanitizeLogs(e.localizedMessage ?: "İndirme sırasında ağ hatası oluştu.")
            Result.failure(Exception(sanitizedMsg))
        } finally {
            if (tempDownloadFile.exists()) tempDownloadFile.delete()
            if (tempApkFile.exists()) tempApkFile.delete()
        }
    }

    suspend fun getFailureDiagnostics(
        repoFullName: String,
        runId: Long
    ): Result<BuildDiagnostic> = withContext(Dispatchers.IO) {
        try {
            val parts = repoFullName.split("/")
            var failedStepName = "Android Projesini Derle"

            // 1. Try fetching jobs to locate failed step
            val jobsResp = api.getWorkflowRunJobs(getAuthHeader(), parts[0], parts[1], runId)
            if (jobsResp.isSuccessful && jobsResp.body() != null) {
                for (job in jobsResp.body()!!.jobs) {
                    val step = job.steps?.find { it.conclusion == "failure" }
                    if (step != null) {
                        failedStepName = step.name
                        break
                    }
                }
            }

            // 2. Fetch logs
            val logsResp = api.downloadRunLogs(getAuthHeader(), parts[0], parts[1], runId)
            val rawLogs = if (logsResp.isSuccessful && logsResp.body() != null) {
                readZippedLogsOrStream(logsResp.body()!!)
            } else {
                "Loglar GitHub API üzerinden alınamadı veya çalışma henüz tamamlanmadı."
            }

            val diagnostic = BuildLogAnalyzer.analyze(rawLogs, failedStepName)
            Result.success(diagnostic)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readZippedLogsOrStream(responseBody: ResponseBody): String {
        return try {
            val stream = responseBody.byteStream()
            val zis = ZipInputStream(stream)
            var entry = zis.nextEntry
            val sb = java.lang.StringBuilder()
            while (entry != null) {
                if (entry.name.contains("build") || entry.name.contains("gradle") || entry.name.endsWith(".txt")) {
                    val text = zis.bufferedReader().readText()
                    sb.append("=== Log: ${entry.name} ===\n").append(text).append("\n")
                }
                entry = zis.nextEntry
            }
            if (sb.isNotEmpty()) sb.toString() else "Log dosyası okunamadı."
        } catch (e: Exception) {
            "Log çıkarma hatası: ${e.message}"
        }
    }

    private fun isTextFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".xml") ||
                lower.endsWith(".gradle") || lower.endsWith(".kts") || lower.endsWith(".json") ||
                lower.endsWith(".properties") || lower.endsWith(".txt") || lower.endsWith(".md") ||
                lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".pro") ||
                lower.endsWith(".gitignore") || lower == "gradlew"
    }
}

data class CommitResult(
    val commitSha: String,
    val branch: String
)

data class DownloadedApkInfo(
    val file: File,
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String
)
