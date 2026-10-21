package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_records")
data class BuildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repoFullName: String,
    val repoHtmlUrl: String,
    val branch: String,
    val commitSha: String,
    val commitMessage: String,
    val workflowRunId: Long,
    val buildTarget: String, // DEBUG_APK, RELEASE_APK, BUNDLE_AAB
    val status: String, // QUEUED, IN_PROGRESS, SUCCESS, FAILURE, CANCELLED
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val failedStep: String? = null,
    val primaryErrorMessage: String? = null,
    val suggestedFix: String? = null,
    val logSnippet: String? = null,
    val artifactName: String? = null,
    val artifactId: Long? = null,
    val artifactSize: Long? = null,
    val localApkPath: String? = null,
    val apkSha256: String? = null,
    val isDownloaded: Boolean = false,
    val appName: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: String? = null,
    val isSignatureVerified: Boolean = false,
    val signatureScheme: String? = null
)

@Entity(tableName = "download_records")
data class DownloadRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buildId: Long,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val targetType: String
)
