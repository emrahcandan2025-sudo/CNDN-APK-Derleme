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
        baseName: String
    ): Result<DownloadedApkInfo> = withContext(Dispatchers.IO) {
        try {
            val parts = repoFullName.split("/")
            val response = api.downloadArtifactZip(getAuthHeader(), parts[0], parts[1], artifactId)
            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.failure(Exception("Artefakt indirilemedi (${response.code()}): ${response.message()}"))
            }

            destDir.mkdirs()
            var extractedApkFile: File? = null

            // GitHub artifacts are zipped archives containing the APK or AAB
            response.body()!!.byteStream().use { stream ->
                val zis = ZipInputStream(stream)
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.endsWith(".apk") || name.endsWith(".aab")) {
                        val targetApk = File(destDir, name.substringAfterLast("/"))
                        FileOutputStream(targetApk).use { fos ->
                            zis.copyTo(fos)
                        }
                        extractedApkFile = targetApk
                        break
                    }
                    entry = zis.nextEntry
                }
            }

            val apkFile = extractedApkFile ?: return@withContext Result.failure(Exception("Artefakt arşivinde APK veya AAB dosyası bulunamadı."))
            val sha256 = ApkUtils.calculateSha256(apkFile)

            Result.success(
                DownloadedApkInfo(
                    file = apkFile,
                    fileName = apkFile.name,
                    fileSizeBytes = apkFile.length(),
                    sha256 = sha256
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
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
