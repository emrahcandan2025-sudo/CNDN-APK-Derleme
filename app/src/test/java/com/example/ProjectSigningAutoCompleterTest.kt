package com.example

import com.example.data.analyzer.ProjectSigningAutoCompleter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectSigningAutoCompleterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test Kotlin DSL build file gets release signing injected cleanly`() {
        val rootDir = tempFolder.newFolder("sample_kts_project")
        val appDir = File(rootDir, "app").apply { mkdirs() }
        val buildFile = File(appDir, "build.gradle.kts")

        val initialContent = """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.android)
            }

            android {
                namespace = "com.example.sample"
                compileSdk = 34

                defaultConfig {
                    applicationId = "com.example.sample"
                    minSdk = 24
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = false
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }
            }
        """.trimIndent()

        buildFile.writeText(initialContent)

        val result = ProjectSigningAutoCompleter.analyzeAndCompleteSigning(rootDir)
        assertTrue("Should detect Kotlin DSL", result.isKotlinDsl)
        assertTrue("Config should be modified", result.wasConfigModified)
        assertTrue("Should have release signing config", result.hasReleaseSigningConfig)

        val modified = buildFile.readText()
        assertTrue("Must contain signingConfigs create release", modified.contains("create(\"release\")"))
        assertTrue("Must contain storeFile", modified.contains("storeFile = file("))
        assertTrue("Must link signingConfig in buildTypes", modified.contains("signingConfig = signingConfigs.getByName(\"release\")"))
        assertTrue("Must preserve isMinifyEnabled = false", modified.contains("isMinifyEnabled = false"))

        // Idempotency test: Running again should NOT modify file or duplicate blocks
        val secondResult = ProjectSigningAutoCompleter.analyzeAndCompleteSigning(rootDir)
        assertFalse("Second run must not modify again", secondResult.wasConfigModified)
        val secondContent = buildFile.readText()
        assertEquals("Content must be identical after second run", modified, secondContent)
    }

    @Test
    fun `test Groovy DSL build file gets release signing injected cleanly`() {
        val rootDir = tempFolder.newFolder("sample_groovy_project")
        val appDir = File(rootDir, "app").apply { mkdirs() }
        val buildFile = File(appDir, "build.gradle")

        val initialContent = """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion 34

                defaultConfig {
                    applicationId "com.example.groovyapp"
                    minSdkVersion 21
                    targetSdkVersion 34
                    versionCode 2
                    versionName "1.1"
                }

                buildTypes {
                    release {
                        minifyEnabled true
                        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                    }
                }
            }
        """.trimIndent()

        buildFile.writeText(initialContent)

        val result = ProjectSigningAutoCompleter.analyzeAndCompleteSigning(rootDir)
        assertFalse("Should detect Groovy DSL", result.isKotlinDsl)
        assertTrue("Config should be modified", result.wasConfigModified)
        assertTrue("Should have release signing config", result.hasReleaseSigningConfig)

        val modified = buildFile.readText()
        assertTrue("Must contain release signing block", modified.contains("release {"))
        assertTrue("Must contain storeFile", modified.contains("storeFile file("))
        assertTrue("Must link signingConfigs.release", modified.contains("signingConfig signingConfigs.release"))
        assertTrue("Must preserve minifyEnabled true", modified.contains("minifyEnabled true"))

        // Idempotency check
        val secondResult = ProjectSigningAutoCompleter.analyzeAndCompleteSigning(rootDir)
        assertFalse("Second run must not modify again", secondResult.wasConfigModified)
    }

    @Test
    fun `test existing valid release signing is untouched`() {
        val rootDir = tempFolder.newFolder("sample_existing_project")
        val appDir = File(rootDir, "app").apply { mkdirs() }
        val buildFile = File(appDir, "build.gradle.kts")

        val validContent = """
            android {
                signingConfigs {
                    create("release") {
                        storeFile = file("custom-key.keystore")
                        storePassword = "secret-password"
                        keyAlias = "custom-alias"
                        keyPassword = "secret-password"
                    }
                }
                buildTypes {
                    getByName("release") {
                        signingConfig = signingConfigs.getByName("release")
                    }
                }
            }
        """.trimIndent()

        buildFile.writeText(validContent)

        val result = ProjectSigningAutoCompleter.analyzeAndCompleteSigning(rootDir)
        assertFalse("Config should NOT be modified when already valid", result.wasConfigModified)
        assertTrue("Should recognize existing release signing", result.hasReleaseSigningConfig)
        assertEquals("File content must remain completely identical", validContent, buildFile.readText())
    }
}
