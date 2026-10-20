package com.example.data.analyzer

import android.content.Context
import android.net.Uri
import com.example.data.model.ProjectFileInfo
import com.example.data.model.ProjectInspectionResult
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ZipProjectAnalyzer {

    const val MAX_COMPRESSED_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB
    const val MAX_UNCOMPRESSED_TOTAL_BYTES = 350L * 1024 * 1024 // 350 MB
    const val MAX_TOTAL_FILES = 8000

    /**
     * Inspects and securely extracts a project ZIP from Uri or File into a target workspace directory.
     * Guards against Zip Slip (path traversal), zip bombs, and verifies Android project validity.
     */
    @Throws(SecurityException::class, IOException::class)
    fun inspectAndExtract(
        context: Context,
        zipUri: Uri,
        destParentDir: File
    ): ProjectInspectionResult {
        val contentResolver = context.contentResolver

        // Check compressed file size from content resolver
        var compressedSize = 0L
        contentResolver.query(zipUri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex != -1 && cursor.moveToFirst()) {
                compressedSize = cursor.getLong(sizeIndex)
            }
        }

        if (compressedSize > MAX_COMPRESSED_SIZE_BYTES) {
            return ProjectInspectionResult(
                isValidAndroidProject = false,
                validationError = "ZIP dosyası izin verilen maksimum boyutu (${MAX_COMPRESSED_SIZE_BYTES / (1024 * 1024)} MB) aşıyor: ${compressedSize / (1024 * 1024)} MB"
            )
        }

        val inputStream = contentResolver.openInputStream(zipUri)
            ?: return ProjectInspectionResult(
                isValidAndroidProject = false,
                validationError = "Seçilen ZIP dosyası açılamadı."
            )

        return inputStream.use { stream ->
            extractAndAnalyze(BufferedInputStream(stream), destParentDir)
        }
    }

    /**
     * Core extraction and analysis logic, safe for direct input streams (supports unit testing).
     */
    fun extractAndAnalyze(
        inputStream: InputStream,
        destParentDir: File
    ): ProjectInspectionResult {
        val extractTargetDir = File(destParentDir, "project_${System.currentTimeMillis()}")
        if (!extractTargetDir.exists() && !extractTargetDir.mkdirs()) {
            return ProjectInspectionResult(
                isValidAndroidProject = false,
                validationError = "Çalışma dizini oluşturulamadı: ${extractTargetDir.name}"
            )
        }

        val canonicalDestDirPath = extractTargetDir.canonicalPath
        var totalExtractedBytes = 0L
        var totalFileCount = 0
        val extractedFiles = mutableListOf<File>()

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry

            if (entry == null) {
                return ProjectInspectionResult(
                    isValidAndroidProject = false,
                    validationError = "Geçersiz veya boş ZIP arşivi."
                )
            }

            val buffer = ByteArray(16 * 1024)

            while (entry != null) {
                totalFileCount++
                if (totalFileCount > MAX_TOTAL_FILES) {
                    throw SecurityException("Arşiv çok fazla dosya içeriyor ($MAX_TOTAL_FILES limitini aştı). Olası Zip-Bomb saldırısı!")
                }

                val entryName = entry.name

                // Zip Slip / Path Traversal Guard: Check raw name
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    throw SecurityException("Tehlikeli dosya yolu tespit edildi (Zip Slip): '$entryName'")
                }

                val outputFile = File(extractTargetDir, entryName)
                val canonicalOutputFile = outputFile.canonicalPath

                // Canonical destination check
                if (!canonicalOutputFile.startsWith(canonicalDestDirPath + File.separator) &&
                    canonicalOutputFile != canonicalDestDirPath
                ) {
                    throw SecurityException("Dosya hedef dizinin dışına çıkmaya çalıştı: '$entryName'")
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { fos ->
                        var bytesRead: Int
                        while (zis.read(buffer).also { bytesRead = it } != -1) {
                            totalExtractedBytes += bytesRead
                            if (totalExtractedBytes > MAX_UNCOMPRESSED_TOTAL_BYTES) {
                                throw SecurityException("Arşiv açılmış boyutu sınırı ($MAX_UNCOMPRESSED_TOTAL_BYTES bayt) aşıldı.")
                            }
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                    extractedFiles.add(outputFile)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        } catch (e: Exception) {
            extractTargetDir.deleteRecursively()
            return ProjectInspectionResult(
                isValidAndroidProject = false,
                validationError = "ZIP güvenlik / ayrıştırma hatası: ${e.message}",
                containsSuspiciousPaths = e is SecurityException
            )
        }

        // Detect redundant top-level directory (e.g. MyRepo-main/...)
        val effectiveProjectDir = resolveProjectRoot(extractTargetDir)

        // Android Project Structure Validation
        val filesList = mutableListOf<ProjectFileInfo>()
        var hasSettingsGradle = false
        var hasBuildGradle = false
        var hasGradleWrapper = false
        var hasManifest = false
        val detectedModules = mutableListOf<String>()
        var foundAppName: String? = null
        var foundAppId: String? = null
        var foundCompileSdk: String? = null

        val rootCanonical = effectiveProjectDir.canonicalPath
        effectiveProjectDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relPath = file.canonicalPath.removePrefix(rootCanonical).trimStart(File.separatorChar)
                val isExec = file.name == "gradlew" || file.name.endsWith(".sh")
                filesList.add(ProjectFileInfo(relativePath = relPath, sizeBytes = file.length(), isExecutable = isExec))

                if (relPath == "settings.gradle" || relPath == "settings.gradle.kts") {
                    hasSettingsGradle = true
                }
                if (relPath == "build.gradle" || relPath == "build.gradle.kts") {
                    hasBuildGradle = true
                }
                if (relPath == "gradlew" || relPath == "gradle/wrapper/gradle-wrapper.properties") {
                    hasGradleWrapper = true
                }
                if (file.name == "AndroidManifest.xml") {
                    hasManifest = true
                    // Inspect module name
                    val moduleDir = file.parentFile?.parentFile?.parentFile // e.g. app/src/main/AndroidManifest.xml -> app
                    if (moduleDir != null && moduleDir != effectiveProjectDir) {
                        val modName = moduleDir.name
                        if (!detectedModules.contains(modName)) {
                            detectedModules.add(modName)
                        }
                    }
                    // Try parsing package/namespace from manifest
                    runCatching {
                        val text = file.readText()
                        val pkgMatch = Regex("package=[\"']([^\"']+)[\"']").find(text)
                        if (pkgMatch != null) foundAppId = pkgMatch.groupValues[1]
                    }
                }

                // Check build.gradle for compileSdk and namespace
                if (file.name == "build.gradle" || file.name == "build.gradle.kts") {
                    runCatching {
                        val text = file.readText()
                        val sdkMatch = Regex("compileSdk\\s*[=]?\\s*([0-9]+)").find(text)
                        if (sdkMatch != null) foundCompileSdk = sdkMatch.groupValues[1]
                        val nsMatch = Regex("namespace\\s*[=]?\\s*[\"']([^\"']+)[\"']").find(text)
                        if (nsMatch != null && foundAppId == null) foundAppId = nsMatch.groupValues[1]
                        val appIdMatch = Regex("applicationId\\s*[=]?\\s*[\"']([^\"']+)[\"']").find(text)
                        if (appIdMatch != null) foundAppId = appIdMatch.groupValues[1]
                    }
                }

                // Check strings.xml for app_name
                if (file.name == "strings.xml") {
                    runCatching {
                        val text = file.readText()
                        val nameMatch = Regex("<string\\s+name=[\"']app_name[\"']>([^<]+)</string>").find(text)
                        if (nameMatch != null) foundAppName = nameMatch.groupValues[1]
                    }
                }
            }
        }

        // Validity check: Must have at least (settings.gradle OR build.gradle) AND (AndroidManifest.xml OR build.gradle)
        val isAndroid = (hasSettingsGradle || hasBuildGradle) && (hasManifest || hasBuildGradle)

        val validationError = if (!isAndroid) {
            "Bu ZIP geçerli bir Android projesi içermiyor. 'build.gradle(.kts)', 'settings.gradle(.kts)' veya 'AndroidManifest.xml' dosyaları bulunamadı."
        } else null

        return ProjectInspectionResult(
            isValidAndroidProject = isAndroid,
            validationError = validationError,
            rootProjectDir = effectiveProjectDir,
            rootDirectoryName = effectiveProjectDir.name,
            totalFiles = filesList.size,
            totalSizeBytes = totalExtractedBytes,
            hasSettingsGradle = hasSettingsGradle,
            hasBuildGradle = hasBuildGradle,
            hasGradleWrapper = hasGradleWrapper,
            hasManifest = hasManifest,
            detectedModules = detectedModules.ifEmpty { listOf("app") },
            appName = foundAppName ?: effectiveProjectDir.name,
            applicationId = foundAppId ?: "com.example.app",
            compileSdkVersion = foundCompileSdk ?: "34",
            containsSuspiciousPaths = false,
            filesList = filesList
        )
    }

    /**
     * Automatically strips single top-level wrapper directories if the ZIP was packaged with a root folder.
     */
    fun resolveProjectRoot(extractedDir: File): File {
        val children = extractedDir.listFiles() ?: return extractedDir
        val nonHidden = children.filter { !it.name.startsWith(".") }
        if (nonHidden.size == 1 && nonHidden[0].isDirectory) {
            val candidate = nonHidden[0]
            val subChildren = candidate.listFiles() ?: return extractedDir
            val hasAndroidIndicators = subChildren.any {
                it.name in listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "app", "gradlew")
            }
            if (hasAndroidIndicators) {
                return candidate
            }
        }
        return extractedDir
    }

    /**
     * Auto-fix: Injects missing Gradle Wrapper files into the project directory
     * (gradlew script, gradlew.bat, and gradle/wrapper/gradle-wrapper.properties).
     */
    fun injectGradleWrapper(projectDir: File) {
        val wrapperDir = File(projectDir, "gradle/wrapper")
        wrapperDir.mkdirs()

        val propsFile = File(wrapperDir, "gradle-wrapper.properties")
        if (!propsFile.exists()) {
            propsFile.writeText(
                """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
                networkTimeout=10000
                validateDistributionUrl=true
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists
                """.trimIndent()
            )
        }

        val gradlewFile = File(projectDir, "gradlew")
        if (!gradlewFile.exists()) {
            gradlewFile.writeText(STANDARD_GRADLEW_SCRIPT)
            gradlewFile.setExecutable(true, false)
        }

        val gradlewBat = File(projectDir, "gradlew.bat")
        if (!gradlewBat.exists()) {
            gradlewBat.writeText("@rem Standard Gradle Wrapper Windows Batch Script\r\n@gradle %*\r\n")
        }
    }

    private val STANDARD_GRADLEW_SCRIPT = """
        #!/bin/sh
        # Minimal POSIX Gradle Wrapper Forwarder
        DIRname="${'$'}(dirname "${'$'}0")"
        if [ -x "${'$'}DIRname/gradle/wrapper/gradle-wrapper.jar" ]; then
            exec java -jar "${'$'}DIRname/gradle/wrapper/gradle-wrapper.jar" "${'$'}@"
        else
            exec gradle "${'$'}@"
        fi
    """.trimIndent()
}
