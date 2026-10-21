package com.example.data.model

import java.io.File

/**
 * Inspection result from extracting and validating a ZIP archive.
 */
data class ProjectInspectionResult(
    val isValidAndroidProject: Boolean,
    val validationError: String? = null,
    val rootProjectDir: File? = null,
    val rootDirectoryName: String = "",
    val totalFiles: Int = 0,
    val totalSizeBytes: Long = 0,
    val hasSettingsGradle: Boolean = false,
    val hasBuildGradle: Boolean = false,
    val hasGradleWrapper: Boolean = false,
    val hasManifest: Boolean = false,
    val detectedModules: List<String> = emptyList(),
    val appName: String? = null,
    val applicationId: String? = null,
    val versionName: String? = null,
    val versionCode: String? = null,
    val compileSdkVersion: String? = null,
    val isKotlinDsl: Boolean = false,
    val agpVersion: String? = null,
    val gradleVersion: String? = null,
    val hasReleaseSigningConfig: Boolean = false,
    val signingMessage: String? = null,
    val containsSuspiciousPaths: Boolean = false,
    val filesList: List<ProjectFileInfo> = emptyList()
)

data class ProjectFileInfo(
    val relativePath: String,
    val sizeBytes: Long,
    val isExecutable: Boolean = false
)

enum class BuildTarget(val displayName: String, val gradleCommand: String, val artifactName: String) {
    DEBUG_APK("Debug APK (Hemen Telefona Yüklenebilir)", "assembleDebug", "cndn-apk-debug"),
    RELEASE_APK("İmzalı Release APK", "assembleRelease", "cndn-apk-release"),
    BUNDLE_AAB("Google Play App Bundle (AAB)", "bundleRelease", "cndn-aab-release")
}

enum class BuildStatusState {
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    CANCELLED
}

data class BuildDiagnostic(
    val category: DiagnosticCategory,
    val failedStepName: String,
    val primaryErrorMessage: String,
    val suggestedFix: String,
    val detailedLogSnippet: String
)

enum class DiagnosticCategory(val title: String) {
    COMPILATION_ERROR("Kaynak Kod Derleme Hatası"),
    GRADLE_SYNTAX_ERROR("Gradle Yapılandırma Hatası"),
    DEPENDENCY_ERROR("Bağımlılık Çözümleme Hatası"),
    SDK_VERSION_ERROR("SDK veya Java Sürüm Uyumsuzluğu"),
    PERMISSION_DENIED("İzin / Çalıştırma Hatası"),
    OOM_ERROR("Bellek Yetersiz (OOM)"),
    TIMEOUT_ERROR("Zaman Aşımı / Ağ Hatası"),
    UNKNOWN_ERROR("Bilinmeyen Derleme Hatası")
}
