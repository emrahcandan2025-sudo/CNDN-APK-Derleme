package com.example.data.analyzer

import java.io.File

/**
 * Result of analyzing and optionally auto-completing an Android project's signing configuration.
 */
data class SigningAnalysisResult(
    val isKotlinDsl: Boolean,
    val targetBuildFile: File?,
    val hasReleaseSigningConfig: Boolean,
    val wasConfigModified: Boolean,
    val agpVersion: String?,
    val gradleVersion: String?,
    val message: String
)

/**
 * Automates detection and completion of missing release signing configurations
 * for uploaded Android projects.
 *
 * Requirements:
 * 1. Accurately detect Kotlin DSL (.kts) vs Groovy DSL (.gradle).
 * 2. If release signing is already valid, preserve it completely without modifications.
 * 3. If missing, safely inject a release signing configuration and attach it to the release buildType.
 * 4. Strictly idempotent: Never add duplicate blocks across multiple runs.
 * 5. Strictly preserve minifyEnabled, ProGuard, and R8 configurations.
 */
object ProjectSigningAutoCompleter {

    private const val CNDN_SIGNING_MARKER = "// [CNDN-AUTO-SIGNING-CONFIG]"

    /**
     * Inspects project build files, determines DSL type, and completes release signing if absent.
     */
    fun analyzeAndCompleteSigning(projectDir: File): SigningAnalysisResult {
        // 1. Locate root and module build files
        val settingsKts = File(projectDir, "settings.gradle.kts")
        val settingsGroovy = File(projectDir, "settings.gradle")
        val isSettingsKotlin = settingsKts.exists()

        // Locate module build file: usually app/build.gradle.kts or app/build.gradle
        val appDir = File(projectDir, "app")
        val appBuildKts = File(appDir, "build.gradle.kts")
        val appBuildGroovy = File(appDir, "build.gradle")

        val rootBuildKts = File(projectDir, "build.gradle.kts")
        val rootBuildGroovy = File(projectDir, "build.gradle")

        val targetBuildFile: File? = when {
            appBuildKts.exists() -> appBuildKts
            appBuildGroovy.exists() -> appBuildGroovy
            rootBuildKts.exists() -> rootBuildKts
            rootBuildGroovy.exists() -> rootBuildGroovy
            else -> null
        }

        val isKotlinDsl = targetBuildFile?.name?.endsWith(".kts") ?: isSettingsKotlin

        // 2. Detect AGP and Gradle versions
        val agpVersion = detectAgpVersion(projectDir)
        val gradleVersion = detectGradleVersion(projectDir)

        if (targetBuildFile == null || !targetBuildFile.exists()) {
            return SigningAnalysisResult(
                isKotlinDsl = isKotlinDsl,
                targetBuildFile = null,
                hasReleaseSigningConfig = false,
                wasConfigModified = false,
                agpVersion = agpVersion,
                gradleVersion = gradleVersion,
                message = "build.gradle(.kts) dosyası bulunamadı."
            )
        }

        val content = targetBuildFile.readText()

        // 3. Check if release signing is already configured
        val hasExistingReleaseSigning = hasValidReleaseSigning(content)
        if (hasExistingReleaseSigning) {
            return SigningAnalysisResult(
                isKotlinDsl = isKotlinDsl,
                targetBuildFile = targetBuildFile,
                hasReleaseSigningConfig = true,
                wasConfigModified = false,
                agpVersion = agpVersion,
                gradleVersion = gradleVersion,
                message = "Mevcut geçerli release imzalama yapılandırması korundu."
            )
        }

        // Check idempotency marker
        if (content.contains(CNDN_SIGNING_MARKER)) {
            return SigningAnalysisResult(
                isKotlinDsl = isKotlinDsl,
                targetBuildFile = targetBuildFile,
                hasReleaseSigningConfig = true,
                wasConfigModified = false,
                agpVersion = agpVersion,
                gradleVersion = gradleVersion,
                message = "CNDN imzalama yapılandırması zaten mevcut."
            )
        }

        // 4. Inject missing release signing configuration safely
        val modifiedContent = if (isKotlinDsl) {
            injectKotlinDslSigning(content)
        } else {
            injectGroovyDslSigning(content)
        }

        val wasModified = modifiedContent != content
        if (wasModified) {
            targetBuildFile.writeText(modifiedContent)
        }

        return SigningAnalysisResult(
            isKotlinDsl = isKotlinDsl,
            targetBuildFile = targetBuildFile,
            hasReleaseSigningConfig = true,
            wasConfigModified = wasModified,
            agpVersion = agpVersion,
            gradleVersion = gradleVersion,
            message = if (wasModified) "Eksik release imzalama yapılandırması güvenle eklendi." else "Yapılandırma doğrulandı."
        )
    }

    /**
     * Checks if the build file already configures a release signingConfig.
     */
    fun hasValidReleaseSigning(content: String): Boolean {
        // Pattern 1: Kotlin DSL signingConfig = signingConfigs.getByName("release") or getByName("release") inside signingConfigs
        val hasKotlinSigning = (content.contains("signingConfigs.getByName(\"release\")") ||
                content.contains("signingConfigs.findByName(\"release\")")) &&
                content.contains("create(\"release\")")

        // Pattern 2: Groovy DSL signingConfig signingConfigs.release and release { in signingConfigs
        val hasGroovySigning = content.contains("signingConfigs.release") &&
                content.contains("signingConfigs") &&
                Regex("signingConfigs\\s*\\{[^}]*release\\s*\\{", RegexOption.DOT_MATCHES_ALL).containsMatchIn(content)

        return hasKotlinSigning || hasGroovySigning
    }

    /**
     * Safely injects release signing into Kotlin DSL build.gradle.kts
     */
    fun injectKotlinDslSigning(original: String): String {
        var text = original

        val signingConfigBlock = """
        $CNDN_SIGNING_MARKER
        signingConfigs {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_FILE") ?: "../release.keystore")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("KEY_ALIAS") ?: "androidreleasekey"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
        """.trimIndent()

        // 1. Inject signingConfigs inside android { ... }
        if (text.contains("android {")) {
            if (!text.contains("create(\"release\")")) {
                text = text.replaceFirst(
                    "android {",
                    "android {\n$signingConfigBlock"
                )
            }
        } else {
            // Append at top or bottom
            text += "\n$signingConfigBlock\n"
        }

        // 2. Ensure buildTypes { getByName("release") { ... signingConfig = ... } }
        val releaseLinkKts = "signingConfig = signingConfigs.getByName(\"release\")"
        if (!text.contains(releaseLinkKts)) {
            if (text.contains("getByName(\"release\") {")) {
                text = text.replaceFirst(
                    "getByName(\"release\") {",
                    "getByName(\"release\") {\n            $releaseLinkKts"
                )
            } else if (text.contains("release {")) {
                text = text.replaceFirst(
                    "release {",
                    "release {\n            $releaseLinkKts"
                )
            } else if (text.contains("buildTypes {")) {
                text = text.replaceFirst(
                    "buildTypes {",
                    "buildTypes {\n        getByName(\"release\") {\n            $releaseLinkKts\n        }"
                )
            } else {
                // Add buildTypes block into android {
                val buildTypesBlock = """
                    buildTypes {
                        getByName("release") {
                            $releaseLinkKts
                        }
                    }
                """.trimIndent()
                text = text.replaceFirst(
                    "android {",
                    "android {\n    $buildTypesBlock"
                )
            }
        }

        return text
    }

    /**
     * Safely injects release signing into Groovy DSL build.gradle
     */
    fun injectGroovyDslSigning(original: String): String {
        var text = original

        val signingConfigBlock = """
        $CNDN_SIGNING_MARKER
        signingConfigs {
            release {
                storeFile file(System.getenv("KEYSTORE_FILE") ?: "../release.keystore")
                storePassword System.getenv("KEYSTORE_PASSWORD") ?: "android"
                keyAlias System.getenv("KEY_ALIAS") ?: "androidreleasekey"
                keyPassword System.getenv("KEY_PASSWORD") ?: "android"
                v1SigningEnabled true
                v2SigningEnabled true
            }
        }
        """.trimIndent()

        // 1. Inject signingConfigs inside android { ... }
        if (text.contains("android {")) {
            if (!text.contains("release {") || !text.contains("storeFile")) {
                text = text.replaceFirst(
                    "android {",
                    "android {\n$signingConfigBlock"
                )
            }
        } else {
            text += "\n$signingConfigBlock\n"
        }

        // 2. Link in buildTypes
        val releaseLinkGroovy = "signingConfig signingConfigs.release"
        if (!text.contains(releaseLinkGroovy)) {
            if (text.contains("buildTypes {") && text.contains("release {")) {
                // Find release { inside buildTypes
                text = text.replaceFirst(
                    "release {",
                    "release {\n            $releaseLinkGroovy"
                )
            } else if (text.contains("buildTypes {")) {
                text = text.replaceFirst(
                    "buildTypes {",
                    "buildTypes {\n        release {\n            $releaseLinkGroovy\n        }"
                )
            } else {
                val buildTypesBlock = """
                    buildTypes {
                        release {
                            $releaseLinkGroovy
                        }
                    }
                """.trimIndent()
                text = text.replaceFirst(
                    "android {",
                    "android {\n    $buildTypesBlock"
                )
            }
        }

        return text
    }

    /**
     * Extracts AGP version from libs.versions.toml or build files.
     */
    fun detectAgpVersion(projectDir: File): String? {
        // Check gradle/libs.versions.toml
        val tomlFile = File(projectDir, "gradle/libs.versions.toml")
        if (tomlFile.exists()) {
            val content = tomlFile.readText()
            val match = Regex("agp\\s*=\\s*[\"']([^\"']+)[\"']").find(content)
                ?: Regex("androidGradlePlugin\\s*=\\s*[\"']([^\"']+)[\"']").find(content)
            if (match != null) return match.groupValues[1]
        }

        // Check root build.gradle / build.gradle.kts
        for (name in listOf("build.gradle.kts", "build.gradle")) {
            val f = File(projectDir, name)
            if (f.exists()) {
                val content = f.readText()
                val match = Regex("com\\.android\\.tools\\.build:gradle:([0-9.]+)").find(content)
                    ?: Regex("id\\([\"']com\\.android\\.application[\"']\\)\\s+version\\s+[\"']([^\"']+)[\"']").find(content)
                    ?: Regex("id\\s+[\"']com\\.android\\.application[\"']\\s+version\\s+[\"']([^\"']+)[\"']").find(content)
                if (match != null) return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Extracts Gradle version from gradle-wrapper.properties.
     */
    fun detectGradleVersion(projectDir: File): String? {
        val propsFile = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        if (propsFile.exists()) {
            val content = propsFile.readText()
            val match = Regex("gradle-([0-9.]+)-(bin|all)\\.zip").find(content)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
