package com.example

import com.example.data.analyzer.BuildLogAnalyzer
import com.example.data.model.DiagnosticCategory
import org.junit.Assert.*
import org.junit.Test

class BuildLogAnalyzerTest {

    @Test
    fun `test compilation error categorization`() {
        val sampleLog = """
            > Task :app:compileDebugKotlin FAILED
            e: /home/runner/work/repo/app/src/main/java/com/example/MainActivity.kt: (18, 24): Unresolved reference: NonExistentClass
            e: /home/runner/work/repo/app/src/main/java/com/example/MainActivity.kt: (22, 10): Type mismatch: inferred type is String but Int was expected
            FAILURE: Build failed with an exception.
        """.trimIndent()

        val diag = BuildLogAnalyzer.analyze(sampleLog, "Android Projesini Derle")

        assertEquals(DiagnosticCategory.COMPILATION_ERROR, diag.category)
        assertTrue(diag.primaryErrorMessage.contains("Unresolved reference") || diag.primaryErrorMessage.contains("Type mismatch") || diag.primaryErrorMessage.contains("e: "))
        assertTrue(diag.suggestedFix.contains("Kotlin/Java"))
    }

    @Test
    fun `test Gradle syntax error categorization`() {
        val sampleLog = """
            * Where:
            Build file '/home/runner/work/app/build.gradle.kts' line: 12
            * What went wrong:
            Script compilation error:
              syntax error, unexpected token '}'
            A problem occurred evaluating root project 'my-app'.
        """.trimIndent()

        val diag = BuildLogAnalyzer.analyze(sampleLog, "Android Projesini Derle")

        assertEquals(DiagnosticCategory.GRADLE_SYNTAX_ERROR, diag.category)
        assertTrue(diag.suggestedFix.contains("Gradle yapılandırma"))
    }

    @Test
    fun `test dependency resolution 404 error categorization`() {
        val sampleLog = """
            > Task :app:checkDebugAarMetadata FAILED
            FAILURE: Build failed with an exception.
            * What went wrong:
            Execution failed for task ':app:checkDebugAarMetadata'.
            > Could not resolve all dependencies for configuration ':app:debugRuntimeClasspath'.
               > Could not find com.unknown.lib:cool-ui:1.0.0.
                 Searched in the following locations:
                   - https://dl.google.com/dl/android/maven2/com/unknown/lib/cool-ui/1.0.0/cool-ui-1.0.0.pom
                   - https://repo.maven.apache.org/maven2/com/unknown/lib/cool-ui/1.0.0/cool-ui-1.0.0.pom
        """.trimIndent()

        val diag = BuildLogAnalyzer.analyze(sampleLog, "Android Projesini Derle")

        assertEquals(DiagnosticCategory.DEPENDENCY_ERROR, diag.category)
        assertTrue(diag.suggestedFix.contains("Bağımlılık kütüphanesi"))
    }

    @Test
    fun `test SDK Java incompatibility error categorization`() {
        val sampleLog = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            Android Gradle plugin requires Java 17 to run. You are currently using Java 11.
            Your current JDK is located in /usr/lib/jvm/java-11-openjdk-amd64
        """.trimIndent()

        val diag = BuildLogAnalyzer.analyze(sampleLog, "Android Projesini Derle")

        assertEquals(DiagnosticCategory.SDK_VERSION_ERROR, diag.category)
        assertTrue(diag.suggestedFix.contains("Java"))
    }

    @Test
    fun `test AGP minimum Gradle version mismatch categorization`() {
        val sampleLog = """
            FAILURE: Build failed with an exception.
            * Where:
            Build file '/home/runner/work/app/build.gradle.kts' line: 3
            * What went wrong:
            An exception occurred applying plugin request [id: 'com.android.application', version: '9.1.1']
            > Failed to apply plugin 'com.android.internal.version-check'.
               > Minimum supported Gradle version is 9.3.1. Current version is 8.11.1.
                 Try updating the 'distributionUrl' property in gradle/wrapper/gradle-wrapper.properties to 'gradle-9.3.1-bin.zip'.
        """.trimIndent()

        val diag = BuildLogAnalyzer.analyze(sampleLog, "Android Projesini Derle")

        assertEquals(DiagnosticCategory.SDK_VERSION_ERROR, diag.category)
        assertTrue(diag.primaryErrorMessage.contains("Minimum supported Gradle version is"))
        assertTrue(diag.suggestedFix.contains("gradle-9.3.1-bin.zip"))
    }

    @Test
    fun `test secret token masking in logs`() {
        val rawLog = "Triggered by user with token ghp_1234567890abcdef1234567890 and Bearer github_pat_11AAAAAA22BBBBBB33CCCCCC44"
        val sanitized = BuildLogAnalyzer.sanitizeLogs(rawLog)

        assertFalse("Orijinal ghp_ token logda bulunmamalı", sanitized.contains("ghp_1234567890abcdef1234567890"))
        assertTrue("Token maskelenmiş formatta olmalı", sanitized.contains("ghp_****7890"))
    }
}
