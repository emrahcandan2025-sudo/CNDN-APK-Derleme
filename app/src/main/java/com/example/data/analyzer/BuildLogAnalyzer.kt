package com.example.data.analyzer

import com.example.data.model.BuildDiagnostic
import com.example.data.model.DiagnosticCategory

object BuildLogAnalyzer {

    private val TOKEN_REGEX = Regex("(ghp_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|Bearer\\s+[A-Za-z0-9_\\-\\.]+)")

    /**
     * Sanitizes sensitive tokens from log lines.
     */
    fun sanitizeLogs(rawLogs: String): String {
        return rawLogs.replace(TOKEN_REGEX) { match ->
            val token = match.value
            if (token.startsWith("ghp_")) {
                "ghp_****" + token.takeLast(4)
            } else if (token.startsWith("github_pat_")) {
                "github_pat_****" + token.takeLast(4)
            } else {
                "[GİZLİ_BELİRTEÇ_MASKEYLENDİ]"
            }
        }
    }

    /**
     * Analyzes raw build logs or job step failures to categorize the root cause
     * and produce clear, actionable Turkish diagnosis.
     */
    fun analyze(
        rawLogs: String,
        failedStepName: String = "Android Projesini Derle"
    ): BuildDiagnostic {
        val sanitized = sanitizeLogs(rawLogs)
        val lines = sanitized.lines()

        // 1. Check for AGP / Gradle Version Mismatch (e.g. Minimum supported Gradle version is 9.3.1)
        val gradleVersionMismatch = lines.find { it.contains("Minimum supported Gradle version is") }
            ?: lines.find { it.contains("Failed to apply plugin 'com.android.internal.version-check'") }
        if (gradleVersionMismatch != null) {
            val details = extractErrorWindow(lines, gradleVersionMismatch)
            val suggestedVersion = Regex("to ['\"](gradle-[0-9.]+-bin\\.zip)['\"]").find(sanitized)?.groupValues?.get(1) ?: "gradle-9.3.1-bin.zip"
            return BuildDiagnostic(
                category = DiagnosticCategory.SDK_VERSION_ERROR,
                failedStepName = failedStepName,
                primaryErrorMessage = gradleVersionMismatch.trim(),
                suggestedFix = "Android Gradle Eklentisi (AGP) daha yüksek bir Gradle sürümü gerektiriyor. 'gradle/wrapper/gradle-wrapper.properties' dosyasındaki 'distributionUrl' adresini '$suggestedVersion' olarak güncelleyin.",
                detailedLogSnippet = details
            )
        }

        // 2. Check for Gradle Syntax / DSL errors
        val dslError = lines.find {
            it.contains("Script compilation error") ||
            it.contains("Unresolved reference") && (it.contains(".gradle.kts") || it.contains("build.gradle")) ||
            it.contains("Plugin [id:") ||
            it.contains("A problem occurred evaluating root project") ||
            it.contains("A problem occurred evaluating project ':app'")
        }
        if (dslError != null) {
            val details = extractErrorWindow(lines, dslError)
            return BuildDiagnostic(
                category = DiagnosticCategory.GRADLE_SYNTAX_ERROR,
                failedStepName = failedStepName,
                primaryErrorMessage = dslError.trim(),
                suggestedFix = "Gradle yapılandırma dosyanızda (build.gradle veya build.gradle.kts) sözdizimi veya eklenti tanımlama hatası var. Eklenti kimliklerini (plugins) ve bağımlılık sürümlerini kontrol edin.",
                detailedLogSnippet = details
            )
        }

        // 2. Check for Kotlin / Java Compilation errors
        val compileError = lines.find {
            it.contains("e: ") ||
            it.contains("Compilation failed; see the compiler error output") ||
            it.contains("Unresolved reference:") ||
            it.contains("Type mismatch:") ||
            it.contains("cannot find symbol")
        }
        if (compileError != null) {
            val details = extractErrorWindow(lines, compileError)
            return BuildDiagnostic(
                category = DiagnosticCategory.COMPILATION_ERROR,
                failedStepName = failedStepName,
                primaryErrorMessage = compileError.trim(),
                suggestedFix = "Uygulama kaynak kodunda (Kotlin/Java) derleme hatası tespit edildi. Tanımsız bir sınıfa, metoda veya tip uyuşmazlığına yol açan değişkeni düzeltin.",
                detailedLogSnippet = details
            )
        }

        // 3. Check for Dependency Resolution (404, repository not found)
        val depError = lines.find {
            it.contains("Could not find") ||
            it.contains("Could not resolve all dependencies") ||
            it.contains("Failed to resolve:") ||
            it.contains("Received status code 404 from server")
        }
        if (depError != null) {
            val details = extractErrorWindow(lines, depError)
            return BuildDiagnostic(
                category = DiagnosticCategory.DEPENDENCY_ERROR,
                failedStepName = failedStepName,
                primaryErrorMessage = depError.trim(),
                suggestedFix = "Bağımlılık kütüphanesi indirilemedi. 'settings.gradle' içindeki 'repositories' bloğuna 'google()', 'mavenCentral()' veya 'jitpack.io' eklendiğinden ve kütüphane sürümünün geçerli olduğundan emin olun.",
                detailedLogSnippet = details
            )
        }

        // 5. Check for SDK / Java Version Incompatibility
        val sdkError = lines.find {
            it.contains("Unsupported class file major version") ||
            it.contains("has been compiled by a more recent version of the Java Runtime") ||
            it.contains("compileSdkVersion is not specified") ||
            it.contains("installed Build Tools revision") ||
            it.contains("Android Gradle plugin requires Java")
        }
        if (sdkError != null) {
            val details = extractErrorWindow(lines, sdkError)
            return BuildDiagnostic(
                category = DiagnosticCategory.SDK_VERSION_ERROR,
                failedStepName = failedStepName,
                primaryErrorMessage = sdkError.trim(),
                suggestedFix = "Kullanılan Java sürümü ile Android Gradle Eklentisi (AGP) veya compileSdk uyumsuz. Projede AGP 8+ için Java 17 veya 21 kullanıldığını doğrulayın.",
                detailedLogSnippet = details
            )
        }

        // 5. Check for Permission denied on gradlew
        val permError = lines.find {
            it.contains("Permission denied") && it.contains("gradlew") ||
            it.contains("gradlew: not found")
        }
        if (permError != null) {
            val details = extractErrorWindow(lines, permError)
            return BuildDiagnostic(
                category = DiagnosticCategory.PERMISSION_DENIED,
                failedStepName = failedStepName,
                primaryErrorMessage = permError.trim(),
                suggestedFix = "GitHub Actions çalışma ortamında 'gradlew' çalıştırma yetkisi bulunamadı veya dosya eksik. Projenize geçerli bir Gradle Wrapper ekleyin.",
                detailedLogSnippet = details
            )
        }

        // 6. Check for OOM / Daemon killed
        val oomError = lines.find {
            it.contains("OutOfMemoryError") ||
            it.contains("Gradle build daemon disappeared unexpectedly") ||
            it.contains("Exit code 137")
        }
        if (oomError != null) {
            val details = extractErrorWindow(lines, oomError)
            return BuildDiagnostic(
                category = DiagnosticCategory.OOM_ERROR,
                failedStepName = failedStepName,
                primaryErrorMessage = oomError.trim(),
                suggestedFix = "Derleme sırasında bellek yetersizliği oluştu. 'gradle.properties' içine 'org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m' ekleyerek bellek sınırını artırın.",
                detailedLogSnippet = details
            )
        }

        // 7. General Failure Fallback
        val genericFailure = lines.findLast { it.contains("FAILURE: Build failed") || it.contains("Execution failed for task") }
            ?: lines.takeLast(10).joinToString("\n").ifBlank { "Ayrıntılı hata kaydı bulunamadı." }

        return BuildDiagnostic(
            category = DiagnosticCategory.UNKNOWN_ERROR,
            failedStepName = failedStepName,
            primaryErrorMessage = genericFailure.lines().firstOrNull() ?: genericFailure,
            suggestedFix = "Derleme beklenmeyen bir hata ile sonlandı. Ayrıntılı logları inceleyerek projenizin yerel Android Studio ortamında hatasız derlendiğinden emin olun.",
            detailedLogSnippet = lines.takeLast(35).joinToString("\n")
        )
    }

    private fun extractErrorWindow(lines: List<String>, focalLine: String): String {
        val index = lines.indexOf(focalLine)
        if (index == -1) return focalLine
        val start = (index - 5).coerceAtLeast(0)
        val end = (index + 20).coerceAtMost(lines.size)
        return lines.subList(start, end).joinToString("\n")
    }
}
